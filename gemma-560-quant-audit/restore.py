"""Restore exact original INT8 FC constants in the corrected 280/560 encoder.
Experimental host-only candidate. Fail closed; no automatic APK promotion."""
import copy, gc, hashlib, json, sys, time
from pathlib import Path
from collections import defaultdict
import numpy as np
import flatbuffers
import schema_py_generated as s
from litert_lm_builder import litertlm_peek as peek
from ai_edge_litert.interpreter import Interpreter

out=Path("quant-report");out.mkdir(exist_ok=True)
def sha(p):
    with p.open("rb") as f:return hashlib.file_digest(f,"sha256").hexdigest()
def arrhash(a):return hashlib.sha256(np.ascontiguousarray(a).view(np.uint8)).hexdigest()
base=Path("gallery.litertlm");corrected=Path("assets/encoder.tflite")
assert sha(base)=="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
assert sha(corrected)=="6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92"
sections=peek.read_litertlm_header(str(base),sys.stdout).SectionMetadata()
with base.open("rb") as f:
    matches=[sections.Objects(i) for i in range(sections.ObjectsLength()) if peek.get_model_type(sections.Objects(i))=="tf_lite_vision_encoder"]
    assert len(matches)==1
    sec=matches[0];f.seek(sec.BeginOffset());raw_ref=f.read(sec.EndOffset()-sec.BeginOffset())
raw=corrected.read_bytes()
def load(raw):return s.ModelT.InitFromObj(s.Model.GetRootAsModel(raw,0))
ref=load(raw_ref);m=load(raw)
def sig(model,key):
    matches=[x for x in model.signatureDefs if x.signatureKey.decode()==key]
    assert len(matches)==1
    return model.subgraphs[matches[0].subgraphIndex]
def opname(model,op):
    c=model.operatorCodes[op.opcodeIndex];return max(c.builtinCode,c.deprecatedBuiltinCode)
def content(model,buf,raw):
    b=model.buffers[buf]
    if b.data is not None and len(b.data):return np.asarray(b.data,dtype=np.uint8)
    if b.offset and b.size:
        assert b.offset+b.size<=len(raw)
        return np.frombuffer(raw,dtype=np.uint8,count=b.size,offset=b.offset)
    raise ValueError("missing constant")
def weights(model,t,raw,dtype):
    a=content(model,t.buffer,raw).view(dtype)
    return a.reshape(tuple(t.shape))
def key(a):return (tuple(a.shape),arrhash(a))
refmap={}
g=sig(ref,"vision_280")
for op in g.operators:
    if opname(ref,op)!=s.BuiltinOperator.FULLY_CONNECTED:continue
    t=g.tensors[op.inputs[1]]
    if t.type!=s.TensorType.INT8:continue
    q=t.quantization
    assert q is not None and q.scale is not None and len(q.scale)
    w=weights(ref,t,raw_ref,np.int8)
    scales=np.asarray(q.scale,dtype=np.float32);zeros=np.asarray(q.zeroPoint if q.zeroPoint is not None and len(q.zeroPoint) else [0],dtype=np.float32)
    assert np.all(scales>0) and np.isfinite(scales).all()
    dims=[1]*w.ndim
    if len(scales)>1:dims[q.quantizedDimension]=len(scales)
    dq=(w.astype(np.float32)-(zeros.reshape(dims) if len(zeros)>1 else zeros[0]))*scales.reshape(dims)
    k=key(dq)
    entry=(t,op,np.array(w,copy=True))
    if k in refmap:
        old=refmap[k];assert np.array_equal(old[2],w) and np.array_equal(old[0].quantization.scale,q.scale)
    refmap[k]=entry
assert len(refmap)==112,len(refmap)
report={"matched":{},"baseline_sha":sha(corrected),"source_sha":sha(base)}
def write_report():
    (out/"report.json").write_text(json.dumps(report,indent=2,ensure_ascii=False))
write_report()
newbuf={};oldbuf=set();opcodes={}
for signature in ("vision_280","vision_560"):
    g=sig(m,signature);rg=sig(ref,"vision_280")
    uses=defaultdict(list)
    for i,op in enumerate(g.operators):
        for j,tidx in enumerate(op.inputs):
            if tidx>=0:uses[int(tidx)].append((i,j))
    found=set();plans=[]
    for i,op in enumerate(g.operators):
        if opname(m,op)!=s.BuiltinOperator.FULLY_CONNECTED:continue
        ti=int(op.inputs[1]);t=g.tensors[ti]
        if t.type!=s.TensorType.FLOAT32:continue
        k=key(weights(m,t,raw,np.float32))
        if k not in refmap:continue
        rt,rop,w=refmap[k]
        assert all(j==1 and opname(m,g.operators[ii])==s.BuiltinOperator.FULLY_CONNECTED for ii,j in uses[ti])
        assert ti not in g.inputs and ti not in g.outputs
        assert g.tensors[op.inputs[0]].type==rg.tensors[rop.inputs[0]].type==s.TensorType.FLOAT32
        assert g.tensors[op.outputs[0]].type==rg.tensors[rop.outputs[0]].type==s.TensorType.FLOAT32
        assert op.builtinOptionsType==rop.builtinOptionsType
        a=op.builtinOptions;z=rop.builtinOptions
        assert a is not None and z is not None
        assert a.weightsFormat==z.weightsFormat
        # Keep graph-specific fused activation, shape policy and bias. Only use the source hybrid input quantization mode.
        plans.append((op,t,k,rt,rop,w));found.add(k)
    report["matched"][signature]=len(found);write_report()
    assert found==set(refmap),(signature,len(found))
    for op,t,k,rt,rop,w in plans:
        if k not in newbuf:
            buf=s.BufferT();buf.data=w.reshape(-1).view(np.uint8).copy()
            newbuf[k]=len(m.buffers);m.buffers.append(buf)
        oldbuf.add(t.buffer);t.buffer=newbuf[k];t.type=s.TensorType.INT8;t.quantization=copy.deepcopy(rt.quantization)
        op.builtinOptions.asymmetricQuantizeInputs=rop.builtinOptions.asymmetricQuantizeInputs
        source_code=ref.operatorCodes[rop.opcodeIndex]
        version=source_code.version
        if version not in opcodes:
            opcodes[version]=len(m.operatorCodes);m.operatorCodes.append(copy.deepcopy(source_code))
        op.opcodeIndex=opcodes[version]
# Old float buffers must be unused before removal; preserve any other tensor/metadata references.
used={t.buffer for g in m.subgraphs for t in g.tensors}
used.update(x.buffer for x in (m.metadata or []))
if m.metadataBuffer is not None:used.update(m.metadataBuffer)
for idx in oldbuf-used:
    buf=m.buffers[idx];buf.data=None;buf.offset=0;buf.size=0
# Relocate every remaining externally stored buffer before serializing; never keep stale offsets.
for i,buf in enumerate(m.buffers):
    if buf.offset or buf.size:
        assert buf.offset and buf.size and buf.offset+buf.size<=len(raw)
        buf.data=np.frombuffer(raw,dtype=np.uint8,count=buf.size,offset=buf.offset).copy()
        buf.offset=0;buf.size=0
candidate=Path("candidate.tflite")
builder=flatbuffers.Builder(1024*1024);builder.Finish(m.Pack(builder),file_identifier=b"TFL3")
candidate.write_bytes(builder.Output());del builder,m,ref,refmap,raw,raw_ref;gc.collect()
report.update(candidate_sha=sha(candidate),candidate_bytes=candidate.stat().st_size,baseline_bytes=corrected.stat().st_size,unique_int8_buffers=len(newbuf),removed_float_buffers=len(oldbuf-used));write_report()
assert len(newbuf)==112 and candidate.stat().st_size<corrected.stat().st_size
rng=np.random.default_rng(560)
def inputs(budget,w,h):
    n=budget*9;pos=np.full((1,n,2),-1,np.int32);yy,xx=np.indices((h,w))
    pos[0,:w*h,0]=xx.ravel();pos[0,:w*h,1]=yy.ravel()
    image=np.zeros((1,n,768),np.float32);image[0,:w*h]=rng.uniform(-1,1,size=(w*h,768)).astype(np.float32)
    return {"images":image,"positions_xy":pos}
def run(path,sig,inp):
    it=Interpreter(model_path=str(path),num_threads=4);runner=it.get_signature_runner(sig)
    times=[]
    for _ in range(3):
        start=time.monotonic();res=runner(**inp);times.append(time.monotonic()-start)
    return {k:np.array(v,copy=True) for k,v in res.items()},times
report["comparisons"]=[]
for budget,w,h in [(280,42,60),(560,60,84),(560,42,60)]:
    inp=inputs(budget,w,h);sigkey="vision_"+str(budget)
    a,ta=run(corrected,sigkey,inp);b,tb=run(candidate,sigkey,inp)
    assert a.keys()==b.keys() and a["features"].shape==b["features"].shape
    mask=a["mask"].astype(bool);assert mask.any() and np.array_equal(a["mask"],b["mask"])
    x=a["features"][mask].astype(np.float64);y=b["features"][mask].astype(np.float64)
    assert np.isfinite(x).all() and np.isfinite(y).all()
    cos=float(np.vdot(x,y)/(np.linalg.norm(x)*np.linalg.norm(y)))
    l2=float(np.linalg.norm(x-y)/np.linalg.norm(x))
    row={"budget":budget,"patches":w*h,"cosine":cos,"relative_l2":l2,"fp32_seconds":ta,"int8_seconds":tb}
    report["comparisons"].append(row);write_report();print(json.dumps(row),flush=True)
# Conservative screening only: no claim of OCR or GPU parity from synthetic features.
report["screen_pass"]=all(x["cosine"]>=.98 for x in report["comparisons"]) and all(np.median(x["int8_seconds"][1:])<np.median(x["fp32_seconds"][1:]) for x in report["comparisons"] if x["budget"]==560)
write_report()
assert report["screen_pass"],"feature/speed screen failed; do not promote"
print("Host screen passed; still requires full OCR and actual Android GPU testing",flush=True)
