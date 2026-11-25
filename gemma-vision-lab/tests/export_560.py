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
# Unpack and repack with official builder. Verify all non-vision section bytes below.
unpacked=Path("unpacked");unpacked.mkdir()
toml=Path(builder.unpack(str(base),str(unpacked)))
sections=tomllib.loads(toml.read_text())["section"]
changes=[]
for section in sections:
    typ=section.get("model_type","").removeprefix("tf_lite_")
    p=unpacked/section["data_path"]
    if typ in ("vision_encoder","vision_adapter"):
        assert section["section_type"]=="TFLiteModel",section
        shutil.copyfile(work/(("encoder" if typ=="vision_encoder" else "adapter")+".tflite"),p)
        changes.append(typ)
    elif section["section_type"]=="LlmMetadata":
        m=llm_metadata_pb2.LlmMetadata()
        text_format.Parse(p.read_text(),m)
        g=m.llm_model_type.gemma4
        assert g.max_num_patches==2520 and g.pooling_kernel_size in (0,3)
        g.max_num_patches=5040
        p.write_text(text_format.MessageToString(m))
assert sorted(changes)==["vision_adapter","vision_encoder"],changes
out=Path("Gemma_E2B_560.litertlm")
builder.pack(str(toml),str(out))
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
with litert_lm.ModelInfo(out) as m:
    assert m.max_vision_token_budget==560 and {280,560}.issubset(m.vision_signature_selection or [])
    print("verified",m.max_vision_token_budget,m.vision_signature_selection,flush=True)
print("identical non-vision sections",len(old),flush=True)
print("graphs",graphs,flush=True)
Path("model.properties").write_text("size="+str(out.stat().st_size)+"\nsha256="+digest(out)+"\n")
