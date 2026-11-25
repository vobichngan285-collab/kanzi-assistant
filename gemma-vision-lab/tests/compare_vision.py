"""Diagnostic only: compare the exact published 280/560 vision files with Gallery 280."""
from pathlib import Path
from collections import Counter
import hashlib, json, shutil, sys, traceback
import numpy as np
import tflite
from litert_lm_builder import litertlm_peek as peek
from ai_edge_litert.interpreter import Interpreter

def sha(p):
    with p.open("rb") as f:return hashlib.file_digest(f,"sha256").hexdigest()
base=Path("gallery.litertlm")
assert base.stat().st_size==2588147712
assert sha(base)=="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
patch=Path("patch-assets")
props=dict(line.split("=",1) for line in (patch/"model.properties").read_text().splitlines())
assert props["source_sha256"]==sha(base) and props["sha256"]=="54ca03d8888d9d2a8284606be35165f47958b00ed548a5cab50994de34dd3952"
assert sum(p.stat().st_size for p in patch.iterdir())==674921419
out=Path("diagnostics");out.mkdir(exist_ok=True)
original={}
sections=peek.read_litertlm_header(str(base),sys.stdout).SectionMetadata()
with base.open("rb") as f:
    for i in range(sections.ObjectsLength()):
        s=sections.Objects(i);typ=peek.get_model_type(s) or ""
        print("source_section",i,typ,s.DataType(),s.BeginOffset(),s.EndOffset(),flush=True)
        if s.DataType()!=peek.schema.AnySectionDataType.TFLiteModel:continue
        if typ not in ("tf_lite_vision_encoder","tf_lite_vision_adapter"):continue
        key=typ.rsplit("_",1)[1]
        dest=out/("original_"+key+".tflite")
        f.seek(s.BeginOffset());n=s.EndOffset()-s.BeginOffset()
        with dest.open("wb") as o:
            while n:
                block=f.read(min(n,8*1024*1024));assert block;o.write(block);n-=len(block)
        original[key]=dest
assert set(original)=={"encoder","adapter"}
ops_enum={v:k for k,v in vars(tflite.BuiltinOperator).items() if isinstance(v,int)}
types_enum={v:k for k,v in vars(tflite.TensorType).items() if isinstance(v,int)}
def describe(path):
    raw=path.read_bytes();model=tflite.Model.GetRootAsModel(raw,0)
    def opname(code):
        c=model.OperatorCodes(code);builtin=max(c.BuiltinCode(),c.DeprecatedBuiltinCode())
        return (c.CustomCode() or b"").decode() if builtin==tflite.BuiltinOperator.CUSTOM else ops_enum.get(builtin,str(builtin))
    def tensor(t):
        q=t.Quantization()
        return {"name":(t.Name() or b"").decode(),"shape":[t.Shape(i) for i in range(t.ShapeLength())],"dtype":types_enum.get(t.Type(),str(t.Type())),"scale_count":q.ScaleLength() if q else 0}
    sigs={}
    for j in range(model.SignatureDefsLength()):
        sig=model.SignatureDefs(j);g=model.Subgraphs(sig.SubgraphIndex())
        sigs[sig.SignatureKey().decode()]={"inputs":{sig.Inputs(i).Name().decode():tensor(g.Tensors(sig.Inputs(i).TensorIndex())) for i in range(sig.InputsLength())},"outputs":{sig.Outputs(i).Name().decode():tensor(g.Tensors(sig.Outputs(i).TensorIndex())) for i in range(sig.OutputsLength())},"ops":dict(Counter(opname(g.Operators(i).OpcodeIndex()) for i in range(g.OperatorsLength()))),"tensor_types":dict(Counter(types_enum.get(g.Tensors(i).Type(),str(g.Tensors(i).Type())) for i in range(g.TensorsLength())))}
    constants={}
    external=[]
    for j in range(model.BuffersLength()):
        buf=model.Buffers(j)
        if hasattr(buf,"Offset") and buf.Offset():
            pos,n=int(buf.Offset()),int(buf.Size())
            if pos+n>len(raw):external.append([j,pos,n]);continue
            if n>=64:constants[hashlib.sha256(memoryview(raw)[pos:pos+n]).hexdigest()]=n
        elif buf.DataLength()>=64:
            data=buf.DataAsNumpy();constants[hashlib.sha256(data).hexdigest()]=len(data)
    result={"bytes":path.stat().st_size,"sha":sha(path),"signatures":sigs,"constant_count":len(constants),"unresolved_external_buffers":external}
    print("structure",path.name,json.dumps(result),flush=True)
    return result,constants
struct={}
for stage in ("encoder","adapter"):
    a,ac=describe(original[stage]);b,bc=describe(patch/(stage+".tflite"))
    common=ac.keys()&bc.keys()
    struct[stage]={"original":a,"converted":b,"identical_constant_buffers":len(common),"identical_constant_bytes":sum(ac[k] for k in common)}
    print("constant_overlap",stage,len(common),sum(ac[k] for k in common),flush=True)
(out/"structures.json").write_text(json.dumps(struct,indent=2))
rng=np.random.default_rng(20261126)
# Both full and padded patch layouts; identical deterministic values and positions.
def encoder_inputs(budget,w,h,case):
    n=budget*9;assert w*h<=n and w%3==0 and h%3==0
    positions=np.full((1,n,2),-1,np.int32)
    yy,xx=np.indices((h,w));positions[0,:w*h,0]=xx.ravel();positions[0,:w*h,1]=yy.ravel()
    pixels=np.zeros((1,n,768),np.float32)
    if case=="pattern":
        pixels[0,:w*h]=rng.uniform(-1,1,size=(w*h,768)).astype(np.float32)
    return {"images":pixels,"positions_xy":positions}
def run(path,key,inputs):
    interpreter=Interpreter(model_path=str(path),num_threads=4)
    sigs=interpreter.get_signature_list()
    assert key in sigs,(path.name,key,sigs)
    runner=interpreter.get_signature_runner(key)
    details=runner.get_input_details()
    assert set(details)==set(inputs),(path.name,key,list(details),list(inputs))
    actual={}
    for k,value in inputs.items():
        assert tuple(details[k]["shape"])==value.shape,(path.name,key,k,details[k]["shape"],value.shape)
        expected=details[k]["dtype"]
        assert expected==value.dtype,(path.name,key,k,expected,value.dtype)
        actual[k]=value
    output={k:np.array(v,copy=True) for k,v in runner(**actual).items()}
    del runner,interpreter
    return output
def safe_run(path,key,inputs,label):
    try:
        result=run(path,key,inputs)
        for k,v in result.items():
            print("tensor",label,k,v.shape,str(v.dtype),"finite",int(np.isfinite(v).sum()),"size",v.size,"min",float(np.min(v)),"max",float(np.max(v)),"std",float(np.std(v.astype(np.float64))),flush=True)
        np.savez_compressed(out/(label+".npz"),**result)
        return result
    except Exception as e:
        print("run_unavailable",label,type(e).__name__,str(e),flush=True)
        traceback.print_exc()
        return None
def compare(a,b,label,mask=None):
    if a is None or b is None:
        print("compare_unavailable",label,flush=True);return
    if set(a)!=set(b):
        print("output_names_differ",label,list(a),list(b),flush=True);return
    for k in a:
        x,y=a[k],b[k]
        if x.shape!=y.shape:
            print("shape_difference",label,k,x.shape,y.shape,flush=True);continue
        if k=="mask" or x.dtype==np.bool_ or y.dtype==np.bool_:
            print("mask_agreement",label,k,float(np.mean(x==y)),flush=True);continue
        if mask is not None and x.ndim==3 and mask.shape==x.shape[:2]:
            x=x[mask];y=y[mask]
        x=x.astype(np.float64).ravel();y=y.astype(np.float64).ravel()
        if not x.size or not np.isfinite(x).all() or not np.isfinite(y).all():
            print("nonfinite_or_empty",label,k,flush=True);continue
        nx=np.linalg.norm(x);ny=np.linalg.norm(y)
        print("numeric",label,k,"cosine",float(np.dot(x,y)/(nx*ny)) if nx*ny else None,"relative_l2",float(np.linalg.norm(x-y)/nx) if nx else None,"mean_abs",float(np.mean(np.abs(x-y))),"max_abs",float(np.max(np.abs(x-y))),"norm_original",float(nx),"norm_converted",float(ny),flush=True)
# First isolate adapter: same independent input for both, no encoder needed.
for case in ("zero","pattern"):
    f=np.zeros((1,280,768),np.float32) if case=="zero" else rng.normal(0,1,size=(1,280,768)).astype(np.float32)
    a=safe_run(original["adapter"],"vision_adapter_280",{"soft_tokens":f},"adapter_original_"+case)
    b=safe_run(patch/"adapter.tflite","vision_adapter_280",{"soft_tokens":f},"adapter_converted_"+case)
    compare(a,b,"adapter_"+case)
# Encoder numerical comparison on identical synthetic patches and valid position IDs.
for case,w,h in (("zero",42,60),("pattern",42,60),("pattern_padded",24,30)):
    inp=encoder_inputs(280,w,h,"zero" if case=="zero" else "pattern")
    a=safe_run(original["encoder"],"vision_280",inp,"encoder_original_"+case)
    b=safe_run(patch/"encoder.tflite","vision_280",inp,"encoder_converted_"+case)
    mask=None
    if a is not None and "mask" in a:
        mask=np.asarray(a["mask"]).astype(bool)
    compare(a,b,"encoder_"+case,mask)
    # Cross the adapter with the same reference encoder feature to localize the failure.
    if a is not None and "features" in a:
        f=a["features"].astype(np.float32)
        c=safe_run(original["adapter"],"vision_adapter_280",{"soft_tokens":f},"chain_original_"+case)
        d=safe_run(patch/"adapter.tflite","vision_adapter_280",{"soft_tokens":f},"chain_new_adapter_"+case)
        compare(c,d,"same_reference_features_"+case,mask)
# 560 itself has no official comparison; sanity only, no fabricated baseline.
inp=encoder_inputs(560,60,84,"pattern")
z=safe_run(patch/"encoder.tflite","vision_560",inp,"encoder_converted_560")
if z is not None and "features" in z:
    safe_run(patch/"adapter.tflite","vision_adapter_560",{"soft_tokens":z["features"].astype(np.float32)},"chain_converted_560")
print("diagnostic complete; unavailable tests are not passes",flush=True)
