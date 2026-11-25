"""Test a strictly matched, temporary per-channel weight scale correction.
Never change released model; fail closed on missing or ambiguous matches."""
from pathlib import Path
import gc, shutil, hashlib, json
import numpy as np
p=Path(__file__).with_name("compare_vision.py")
exec(compile(p.read_text().split("rng=np.random.default_rng(20261126)",1)[0],str(p),"exec"))
def sha_array(x):return hashlib.sha256(np.ascontiguousarray(x).view(np.uint8)).hexdigest()
def signature(m,key):
    s=next(m.SignatureDefs(i) for i in range(m.SignatureDefsLength()) if m.SignatureDefs(i).SignatureKey().decode()==key)
    return m.Subgraphs(s.SubgraphIndex())
def fc_weights(m,g):
    for i in range(g.OperatorsLength()):
        op=g.Operators(i);c=m.OperatorCodes(op.OpcodeIndex())
        if max(c.BuiltinCode(),c.DeprecatedBuiltinCode())==tflite.BuiltinOperator.FULLY_CONNECTED:
            yield i,g.Tensors(op.Inputs(1))
def constant(raw,m,t,dtype):
    b=m.Buffers(t.Buffer());shape=tuple(t.ShapeAsNumpy())
    if b.DataLength():
        off=b._tab.Vector(b._tab.Offset(4));n=b.DataLength()
    elif b.Offset() and b.Size():
        off=int(b.Offset());n=int(b.Size())
    else:raise ValueError("weight has no constant buffer")
    assert off+n<=len(raw) and n==np.prod(shape)*np.dtype(dtype).itemsize
    return np.frombuffer(raw,dtype=dtype,count=n//np.dtype(dtype).itemsize,offset=off).reshape(shape),off,n
a=original["encoder"].read_bytes();m=tflite.Model.GetRootAsModel(a,0);g=signature(m,"vision_280")
reference={}
for i,t in fc_weights(m,g):
    if types_enum[t.Type()]!="INT8":continue
    w,_,_=constant(a,m,t,np.int8)
    q=t.Quantization();assert q and q.ScaleLength()
    scales=np.array(q.ScaleAsNumpy(),np.float32);zeros=np.array(q.ZeroPointAsNumpy(),np.float32) if q.ZeroPointLength() else np.zeros(1,np.float32)
    axis=int(q.QuantizedDimension());assert np.isfinite(scales).all() and (scales>0).all()
    key=(tuple(w.shape),sha_array(w))
    item=(scales,zeros,axis,(t.Name() or b"").decode())
    if key in reference:
        prev=reference[key];assert np.array_equal(prev[0],scales) and np.array_equal(prev[1],zeros) and prev[2]==axis
    reference[key]=item
assert len(reference)==112,("expected 16 x 7 unique INT8 weights",len(reference))
print("reference_unique_weights",len(reference),flush=True)
del w,t,m,g,a;gc.collect()
source=patch/"encoder.tflite";raw=source.read_bytes();m=tflite.Model.GetRootAsModel(raw,0)
dest=out/"encoder_scale_experiment.tflite"
plans={};reports=[]
for sig in ("vision_280","vision_560"):
    g=signature(m,sig);found=set();missing=[]
    for i,t in fc_weights(m,g):
        if types_enum[t.Type()]!="FLOAT32":continue
        w,off,n=constant(raw,m,t,np.float32)
        if not np.isfinite(w).all() or w.min()<-128 or w.max()>127:continue
        ints=w.astype(np.int8)
        if not np.array_equal(w,ints):continue
        key=(tuple(w.shape),sha_array(ints))
        if key not in reference:
            missing.append((i,(t.Name() or b"").decode()));continue
        scales,zeros,axis,name=reference[key]
        found.add(key)
        if off in plans:assert plans[off]==key
        else:plans[off]=key
        reports.append({"signature":sig,"op":i,"original":name,"converted":(t.Name() or b"").decode(),"buffer":int(t.Buffer()),"scale_min":float(scales.min()),"scale_max":float(scales.max())})
    print("matched_full_integer_hashes",sig,len(found),"unmatched_integer_constants",missing,flush=True)
    assert found==set(reference),(sig,len(found),len(reference))
    assert not missing,(sig,missing)
assert plans
shutil.copyfile(source,dest)
with dest.open("r+b") as fp:
    for off,key in plans.items():
        shape,_=key;scales,zeros,axis,_=reference[key]
        n=int(np.prod(shape));w=np.frombuffer(raw,dtype=np.float32,count=n,offset=off).reshape(shape)
        dims=[1]*len(shape)
        if scales.size>1:dims[axis]=scales.size
        corrected=(w-(zeros.reshape(dims) if zeros.size>1 else zeros[0]))*scales.reshape(dims)
        fp.seek(off);fp.write(np.ascontiguousarray(corrected,dtype=np.float32).tobytes())
print("patched_unique_buffers",len(plans),"temporary_sha",sha(dest),flush=True)
del raw,m,g,w,ints,corrected;gc.collect()
# Verify exact deterministic comparison with previous experiment inputs.
rest=p.read_text().split("rng=np.random.default_rng(20261126)",1)[1]
exec("rng=np.random.default_rng(20261126)"+rest.split("# First isolate adapter:",1)[0])
# Preserve original RNG order from baseline experiment (adapter pattern).
rng.normal(0,1,size=(1,280,768))
def metric(a,b):
    x=a.astype(np.float64).ravel();y=b.astype(np.float64).ravel()
    if not np.isfinite(x).all() or not np.isfinite(y).all():return {"finite":False}
    nx=np.linalg.norm(x);ny=np.linalg.norm(y)
    return {"finite":True,"cosine":float(x@y/(nx*ny)) if nx*ny else None,"relative_l2":float(np.linalg.norm(x-y)/nx) if nx else None}
results=[]
for case,w,h in (("zero",42,60),("pattern",42,60),("pattern_padded",24,30)):
    inputs=encoder_inputs(280,w,h,"zero" if case=="zero" else "pattern")
    a=run(original["encoder"],"vision_280",inputs)
    b=run(source,"vision_280",inputs)
    c=run(dest,"vision_280",inputs)
    mask=a["mask"].astype(bool)
    row={"case":case,"baseline":metric(a["features"][mask],b["features"][mask]),"scaled":metric(a["features"][mask],c["features"][mask]),"mask_equal":bool(np.array_equal(a["mask"],c["mask"]))}
    results.append(row);print("scale_correction_result",json.dumps(row),flush=True)
inputs=encoder_inputs(560,60,84,"pattern")
c=run(dest,"vision_560",inputs)
print("560_sanity",c["features"].shape,bool(np.isfinite(c["features"]).all()),int(c["mask"].sum()),flush=True)
(out/"scale_correction_report.json").write_text(json.dumps({"weights":reports,"unique_buffers":len(plans),"results":results,"560_finite":bool(np.isfinite(c["features"]).all()),"candidate_sha":sha(dest)},indent=2))
# Do not promote weights, run APK or claim full equivalence.
print("temporary scale correction tested; no release",flush=True)
