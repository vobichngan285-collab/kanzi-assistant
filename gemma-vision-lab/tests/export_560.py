"""Export only official checkpoint vision modules; retain all Gallery text sections byte-for-byte."""
from pathlib import Path
import dataclasses, hashlib, sys, json, shutil, tomllib
import litert_lm
import tflite
from huggingface_hub import snapshot_download
from google.protobuf import text_format
from litert_lm_builder import litertlm_builder as builder, litertlm_peek as peek
try:
    from litert_lm_builder.runtime.proto import llm_metadata_pb2
except ImportError:
    from runtime.proto import llm_metadata_pb2
from litert_torch.generative.export_hf.export import export
from litert_torch.generative.export_hf.core import export_lib, exportable_module, exportable_module_config
from litert_torch.generative.export_hf.model_ext.gemma4 import vision_exportable

def digest(p):
    with open(p,"rb") as f: return hashlib.file_digest(f,"sha256").hexdigest()
base=Path("gallery.litertlm")
assert base.stat().st_size==2588147712 and digest(base)=="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
source=snapshot_download("google/gemma-4-E2B-it-qat-mobile-transformers",revision="dd693ff40353f057ca5f07e945ad867f4afbf2ec",local_dir="checkpoint")
config=json.loads(Path(source,"config.json").read_text())
assert config["quantization_config"]["quant_method"]=="gemma"
work=Path("vision").resolve();work.mkdir(exist_ok=True)
plan=export(model=source,dry_run=True,task="image_text_to_text",target="cpu_gpu",bundle_litert_lm=False,export_vision_encoder=True,export_audio_encoder=False,quantization_recipe=None,vision_encoder_quantization_recipe=None,use_v2=True,delete_in_memory_params=False,gemma4_vision_token_budgets="280,560")
fields={f.name for f in dataclasses.fields(exportable_module.ExportableModuleConfig)}
known={};extra={}
for k,v in plan.export_kwargs.items():
    if k=="extra_kwargs":extra.update(v)
    elif k in fields:known[k]=v
    else:extra[k]=v
for k in ("model","output_dir","work_dir","task"):known.pop(k,None)
cfg=exportable_module.ExportableModuleConfig(model=source,output_dir=str(work),work_dir=str(work),task=exportable_module_config.ExportTask(plan.task),extra_kwargs=extra,**known)
assert cfg.use_v2 and not cfg.use_random_weights
src=export_lib.load_model(source,cfg,task=cfg.task,trust_remote_code=False)
src.model.set_attn_implementation("eager")
for cls,name,adapter in [(vision_exportable.LiteRTExportableModuleForGemma4VisionEncoder,"encoder",False),(vision_exportable.LiteRTExportableModuleForGemma4VisionAdapter,"adapter",True)]:
    module=cls(src.model,cfg,src.tokenizer) if adapter else cls(src.model,cfg)
    samples=module.get_sample_inputs(src.model_config,image_processor=src.image_processor,**cfg.extra_kwargs)
    expected={("vision_adapter_" if adapter else "vision_")+str(x) for x in (280,560)}
    assert set(samples)==expected, samples.keys()
    export_lib._convert_and_export_module(module,samples,str(work/(name+".tflite")),cfg,src.weights_loader)
    del module,samples
    assert (work/(name+".tflite")).stat().st_size>0
    print("exported",name,flush=True)
del src
# Build an exact section-copy plan. Do not decode/recompress the tokenizer or
# touch any language/embedding/audio section. Preserve original header metadata.
import flatbuffers, struct
from litert_lm_builder import litertlm_core as core
schema=peek.schema
raw_header=peek.read_litertlm_header(str(base),sys.stdout)
header=schema.LiteRTLMMetaDataT.InitFromObj(raw_header)
section_info=raw_header.SectionMetadata()
patch=Path("patch-assets");patch.mkdir()
rows=[]
changed=[]
with base.open("rb") as f:
    for i,obj in enumerate(header.sectionMetadata.objects):
        s=section_info.Objects(i)
        typ=peek.get_model_type(s) or ""
        start,end=s.BeginOffset(),s.EndOffset()
        assert 0<=start<end<=base.stat().st_size
        asset=None
        if s.DataType()==schema.AnySectionDataType.TFLiteModel and typ in ("tf_lite_vision_encoder","tf_lite_vision_adapter"):
            name="encoder" if typ.endswith("encoder") else "adapter"
            asset=name+".tflite"
            shutil.copyfile(work/asset,patch/asset)
            changed.append(typ)
        elif s.DataType()==schema.AnySectionDataType.LlmMetadataProto:
            f.seek(start)
            m=llm_metadata_pb2.LlmMetadata.FromString(f.read(end-start))
            g=m.llm_model_type.gemma4
            assert g.max_num_patches==2520 and g.pooling_kernel_size in (0,3)
            g.max_num_patches=5040
            asset="metadata.bin"
            (patch/asset).write_bytes(m.SerializeToString())
            changed.append("metadata")
        rows.append(dict(source=start,n=(patch/asset).stat().st_size if asset else end-start,asset=asset))
assert sorted(changed)==["metadata","tf_lite_vision_adapter","tf_lite_vision_encoder"],changed
assert all(rows[i]["source"]<rows[i+1]["source"] for i in range(len(rows)-1))
def serialize():
    fb=flatbuffers.Builder(core.BLOCK_SIZE)
    fb.Finish(header.Pack(fb))
    return bytes(fb.Output())
def align(n):return (n+core.BLOCK_SIZE-1)//core.BLOCK_SIZE*core.BLOCK_SIZE
initial=serialize()
offset=align(core.HEADER_BEGIN_BYTE_OFFSET+len(initial))
for row,obj in zip(rows,header.sectionMetadata.objects):
    row["dest"]=offset
    obj.beginOffset=offset;obj.endOffset=offset+row["n"]
    offset=align(obj.endOffset)
final=serialize()
assert len(final)==len(initial)
with base.open("rb") as f: prefix=bytearray(f.read(core.HEADER_BEGIN_BYTE_OFFSET))
struct.pack_into("<Q",prefix,core.HEADER_END_LOCATION_BYTE_OFFSET,core.HEADER_BEGIN_BYTE_OFFSET+len(final))
(patch/"header.bin").write_bytes(prefix+final)
rows.insert(0,dict(source=-1,dest=0,n=len(prefix)+len(final),asset="header.bin"))
out=Path("Gemma_E2B_560.litertlm")
with base.open("rb") as f,out.open("wb") as o:
    for row in rows:
        o.seek(row["dest"])
        if row["asset"]:
            with (patch/row["asset"]).open("rb") as inp: shutil.copyfileobj(inp,o,1024*1024)
        else:
            f.seek(row["source"]);n=row["n"]
            while n:
                block=f.read(min(n,1024*1024));assert block;o.write(block);n-=len(block)
    assert o.tell()==rows[-1]["dest"]+rows[-1]["n"]
def scan(path):
    h=peek.read_litertlm_header(str(path),sys.stdout).SectionMetadata()
    result=[];metas=[];graphs={}
    with path.open("rb") as f:
        for i in range(h.ObjectsLength()):
            s=h.Objects(i);typ=peek.get_model_type(s) or ""
            n=s.EndOffset()-s.BeginOffset();f.seek(s.BeginOffset())
            if s.DataType()==peek.schema.AnySectionDataType.LlmMetadataProto:
                metas.append(llm_metadata_pb2.LlmMetadata.FromString(f.read(n)));continue
            if s.DataType()==peek.schema.AnySectionDataType.TFLiteModel and typ in ("tf_lite_vision_encoder","tf_lite_vision_adapter"):
                graph=tflite.Model.GetRootAsModel(f.read(n),0)
                keys={}
                for j in range(graph.SignatureDefsLength()):
                    sig=graph.SignatureDefs(j);sub=graph.Subgraphs(sig.SubgraphIndex())
                    keys[sig.SignatureKey().decode()]=[[sub.Tensors(sig.Inputs(k).TensorIndex()).Shape(x) for x in range(sub.Tensors(sig.Inputs(k).TensorIndex()).ShapeLength())] for k in range(sig.InputsLength())]
                graphs[typ]=keys;continue
            sha=hashlib.sha256()
            while n:
                b=f.read(min(n,8*1024*1024));assert b;sha.update(b);n-=len(b)
            result.append((typ,s.DataType(),sha.hexdigest()))
    return result,metas,graphs
old,oldmeta,_=scan(base);new,newmeta,graphs=scan(out)
assert old==new,"Non-vision section changed"
assert len(oldmeta)==len(newmeta)==1
oldmeta[0].llm_model_type.gemma4.max_num_patches=5040
assert oldmeta==newmeta,"Unexpected metadata change"
assert any(5040 in s for s in graphs["tf_lite_vision_encoder"]["vision_560"])
assert any(560 in s for s in graphs["tf_lite_vision_adapter"]["vision_adapter_560"])
with litert_lm.ModelInfo(str(out)) as m:
    assert m.max_vision_token_budget==560 and {280,560}.issubset(m.vision_signature_selection or [])
    print("verified",m.max_vision_token_budget,m.vision_signature_selection,flush=True)
print("identical non-vision sections",len(old),flush=True)
print("graphs",graphs,flush=True)
props=["source_size="+str(base.stat().st_size),"source_sha256="+digest(base),"size="+str(out.stat().st_size),"sha256="+digest(out),"count="+str(len(rows))]
for i,row in enumerate(rows):
    props.extend([f"{i}.source={row['source'] if not row['asset'] else -1}",f"{i}.dest={row['dest']}",f"{i}.size={row['n']}",f"{i}.asset={row['asset'] or ''}"])
(patch/"model.properties").write_text("\n".join(props)+"\n")
# Independently reconstruct from exactly the packaged assets + original file.
replay=Path("replay.litertlm")
with base.open("rb") as f,replay.open("wb") as o:
    for row in rows:
        o.seek(row["dest"])
        if row["asset"]:
            with (patch/row["asset"]).open("rb") as inp: shutil.copyfileobj(inp,o,1024*1024)
        else:
            f.seek(row["source"]);n=row["n"]
            while n:
                block=f.read(min(n,1024*1024));assert block;o.write(block);n-=len(block)
assert digest(replay)==digest(out)
replay.unlink()
total=sum(x.stat().st_size for x in patch.iterdir())
print("incremental_asset_bytes",total,flush=True)
assert total<1_000_000_000,"Incremental assets too large for single APK"

