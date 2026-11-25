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
plan=export(model=source,dry_run=True,task="image_text_to_text",target="cpu_gpu",bundle_litert_lm=False,export_vision_encoder=True,export_audio_encoder=False,quantization_recipe=None,vision_encoder_quantization_recipe=None,use_v2=True,delete_in_memory_params=False,apply_gpu_composites=False,use_sdpa_composite=False,use_sdpa_composite_for_vision=False,use_rope_composite=False,use_swiglu_composite=False,use_qkv_norm_rope_composite=False,use_short_conv_composite=False,gemma4_vision_token_budgets="280,560")
fields={f.name for f in dataclasses.fields(exportable_module.ExportableModuleConfig)}
known={};extra={}
for k,v in plan.export_kwargs.items():
    if k=="extra_kwargs":extra.update(v)
    elif k in fields:known[k]=v
    else:extra[k]=v
for k in ("model","output_dir","work_dir","task"):known.pop(k,None)
cfg=exportable_module.ExportableModuleConfig(model=source,output_dir=str(work),work_dir=str(work),task=exportable_module_config.ExportTask(plan.task),extra_kwargs=extra,**known)
assert cfg.use_v2 and not cfg.use_random_weights
assert not cfg.use_sdpa_composite_for_vision and not cfg.apply_gpu_composites
print('vision attention: standard eager, composite disabled',flush=True)
src=export_lib.load_model(source,cfg,task=cfg.task,trust_remote_code=False)
src.model.set_attn_implementation("eager")
# Inspect actual StableHLO custom-call names before binary serialization.
from collections import Counter
import torch
from litert_torch._convert import converter_v2, core as convert_core, litert_converter, signature as signature_lib
from litert_torch.generative.export_hf.core import quantization_layer, weights_loader as weights_loader_lib
from litert_converter.mlir import ir
original_serialize=converter_v2._serialize_portable_artifact
seen_targets=Counter()
def inspect_and_serialize(module):
    targets=Counter()
    def visit(op):
        if op.name=="stablehlo.custom_call":
            targets[ir.StringAttr(op.attributes["call_target_name"]).value]+=1
        for region in op.regions:
            for block in region.blocks:
                for child in block.operations: visit(child.operation)
    with module.context:
        visit(module.operation)
    seen_targets.update(targets)
    print("actual StableHLO custom-call targets",dict(targets),flush=True)
    return original_serialize(module)
converter_v2._serialize_portable_artifact=inspect_and_serialize

def export_v1_from_same_checkpoint(module,samples,path):
    """Use official direct LiteRT V1 lowering with actual QAT weights.
    Materialize only used tensors; never reinitialize unrelated language tower.
    """
    import gc
    programs=[];signatures=[];cache={}
    with quantization_layer.prepare_quantized_export_modules(src.weights_loader,module):
        with weights_loader_lib.prepare_for_export(src.weights_loader,module) as loader:
            device=next(module.parameters()).device
            for name,(inputs,_) in samples.items():
                sig=signature_lib.Signature(name,module.eval(),(),{k:v.to(device) for k,v in inputs.items()})
                ep=convert_core.export_torch_signature(sig,strict_export=False,run_decompositions=False)
                converter_v2._prune_unused_parameters(ep)
                ep=convert_core._run_convert_passes(ep)
                for spec in ep.graph_signature.input_specs:
                    kind=spec.kind.name
                    if kind not in ("PARAMETER","BUFFER","CONSTANT_TENSOR"):continue
                    key=spec.target
                    mapping=ep.constants if kind=="CONSTANT_TENSOR" else ep.state_dict
                    if key not in mapping:
                        assert key in ep.constants,(kind,key)
                        mapping=ep.constants
                    old=mapping[key]
                    if not isinstance(old,torch.Tensor) or not old.is_meta:continue
                    ident=(key,tuple(old.shape),old.dtype)
                    if ident not in cache:
                        try:actual=loader(key)
                        except KeyError:actual=loader(key.removeprefix("module."))
                        assert isinstance(actual,torch.Tensor) and not actual.is_meta,key
                        # V2 can return packed two's-complement sub-byte values.
                        # V1 uses the equivalent signed, unpacked values.
                        if actual.shape!=old.shape:
                            assert actual.dtype==torch.uint8 and old.dtype==torch.int8,key
                            assert old.numel()%actual.numel()==0,key
                            ratio=old.numel()//actual.numel()
                            assert ratio in (2,4),key
                            bits=8//ratio
                            assert converter_v2._is_prepacked_subbyte_tensor(actual,old,bits),key
                            mask=(1<<bits)-1;sign=1<<(bits-1)
                            values=torch.stack([((actual.to(torch.int16)>>(bits*j))&mask) for j in range(ratio)],dim=-1)
                            actual=((values^sign)-sign).to(torch.int8).reshape(old.shape)
                        assert actual.shape==old.shape,key
                        cache[ident]=actual.detach().cpu().to(old.dtype).contiguous()
                    mapping[key]=cache[ident]
                for spec in ep.graph_signature.input_specs:
                    if spec.kind.name in ("PARAMETER","BUFFER","CONSTANT_TENSOR"):
                        val=ep.state_dict.get(spec.target,ep.constants.get(spec.target))
                        assert isinstance(val,torch.Tensor) and not val.is_meta,spec.target
                signatures.append(sig);programs.append(ep)
            print("V1 verified materialized tensor count",len(cache),flush=True)
            exporter=litert_converter.exported_programs_to_flatbuffer(programs,signatures,lightweight_conversion=False,runtime_constant_folding=False,converter_flags={"strict_qdq_mode":True})
            exporter.to_file(str(path))
    del programs,signatures,cache
    gc.collect()
for cls,name,adapter in [(vision_exportable.LiteRTExportableModuleForGemma4VisionEncoder,"encoder",False),(vision_exportable.LiteRTExportableModuleForGemma4VisionAdapter,"adapter",True)]:
    module=cls(src.model,cfg,src.tokenizer) if adapter else cls(src.model,cfg)
    samples=module.get_sample_inputs(src.model_config,image_processor=src.image_processor,**cfg.extra_kwargs)
    expected={("vision_adapter_" if adapter else "vision_")+str(x) for x in (280,560)}
    assert set(samples)==expected, samples.keys()
    target=work/(name+".tflite")
    seen_targets.clear()
    try:
        export_lib._convert_and_export_module(module,samples,str(target),cfg,src.weights_loader)
    except Exception as exc:
        if "stablehlo.custom_call" not in str(exc):raise
        print("V2 custom-call failure; targets",dict(seen_targets),flush=True)
        assert seen_targets,"No source custom-call targets captured"
        target.unlink(missing_ok=True)
        export_v1_from_same_checkpoint(module,samples,target)
        print("official V1 fallback exported",name,flush=True)
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

