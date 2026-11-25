"""Three-way scale audit: exact Gallery, published conversion, fixed official QAT checkpoint.
Read-only. Compare actual stored constants, not arbitrary guessed scale corrections."""
from pathlib import Path
import json, re, gc, math, mmap
import numpy as np
from huggingface_hub import hf_hub_download, list_repo_files
p=Path(__file__).with_name("compare_vision.py")
exec(compile(p.read_text().split("rng=np.random.default_rng(20261126)",1)[0],str(p),"exec"))
import struct as binstruct
def metrics(a,b):
    a=np.asarray(a,dtype=np.float64).ravel();b=np.asarray(b,dtype=np.float64).ravel()
    assert a.shape==b.shape
    if not np.isfinite(a).all() or not np.isfinite(b).all():return {"nonfinite":True}
    na=np.linalg.norm(a);nb=np.linalg.norm(b)
    return {"cosine":float(a@b/(na*nb)) if na*nb else None,"norm_ratio_b_over_a":float(nb/na) if na else None,"relative_l2":float(np.linalg.norm(a-b)/na) if na else None,"least_squares_b_over_a":float(a@b/(a@a)) if na else None}
def sample(a):
    a=np.asarray(a).ravel()
    return a[np.linspace(0,len(a)-1,min(len(a),32768),dtype=np.int64)].copy()
def array(m,t,raw):
    buf=m.Buffers(t.Buffer())
    if buf.DataLength(): data=buf.DataAsNumpy()
    elif hasattr(buf,"Offset") and buf.Offset() and buf.Offset()+buf.Size()<=len(raw):data=memoryview(raw)[buf.Offset():buf.Offset()+buf.Size()]
    else:raise ValueError("no inline or resolved external constant")
    dtype={"FLOAT32":"<f4","FLOAT16":"<f2","INT8":"i1","UINT8":"u1","INT32":"<i4"}[types_enum[t.Type()]]
    return np.frombuffer(data,dtype=dtype).reshape(tuple(t.ShapeAsNumpy()))
def inspect_weights(path):
    raw=path.read_bytes();m=tflite.Model.GetRootAsModel(raw,0)
    sig=next(m.SignatureDefs(i) for i in range(m.SignatureDefsLength()) if m.SignatureDefs(i).SignatureKey()==b"vision_280")
    g=m.Subgraphs(sig.SubgraphIndex())
    def opname(o):
        c=m.OperatorCodes(o.OpcodeIndex());return ops_enum.get(max(c.BuiltinCode(),c.DeprecatedBuiltinCode()),"UNKNOWN")
    groups=[[]]
    for i in range(g.OperatorsLength()):
        o=g.Operators(i);k=opname(o)
        if k=="SOFTMAX":
            if len(groups)>=3:break
            groups.append([])
        if k!="FULLY_CONNECTED":continue
        t=g.Tensors(int(o.Inputs(1)));name=(t.Name() or b"").decode(errors="replace")
        try:
            w=array(m,t,raw);q=t.Quantization()
            scale=np.array(q.ScaleAsNumpy(),np.float32) if q and q.ScaleLength() else np.empty(0,np.float32)
            zp=np.array(q.ZeroPointAsNumpy(),np.float32) if q and q.ZeroPointLength() else np.zeros(1,np.float32)
            axis=int(q.QuantizedDimension()) if q else 0
            deq=w.astype(np.float32)
            if scale.size:
                dims=[1]*w.ndim
                if scale.size>1:dims[axis]=scale.size
                deq=(deq-(zp.reshape(dims) if zp.size>1 else zp[0]))*scale.reshape(dims)
            record={"op":i,"name":name,"shape":list(w.shape),"dtype":str(w.dtype),"scale_count":int(scale.size),"scale_min":float(scale.min()) if scale.size else None,"scale_max":float(scale.max()) if scale.size else None,"scale_median":float(np.median(scale)) if scale.size else None,"quant_axis":axis,"zero_points":np.unique(zp).tolist(),"raw":sample(w),"dequant":sample(deq)}
            groups[-1].append(record)
        except Exception as exc:print("constant_unavailable",path.name,i,name,str(exc),flush=True)
    return groups
old=inspect_weights(original["encoder"]);new=inspect_weights(patch/"encoder.tflite")
def clean(d):return {k:v for k,v in d.items() if k not in ("raw","dequant")}
pairs=[]
for group,(aa,bb) in enumerate(zip(old,new)):
    if group==0:
        # First image projection plus Q/K/V; original position projections have a distinct shape.
        aa=[x for x in aa if x["shape"]==[768,768]]
        if len(aa)!=4 or len(bb)!=4:
            print("first_group_unavailable",len(aa),len(bb));continue
    if len(aa)!=len(bb) or [x["shape"] for x in aa]!=[x["shape"] for x in bb]:
        print("group_unavailable",group,[x["shape"] for x in aa],[x["shape"] for x in bb]);continue
    for idx,(a,b) in enumerate(zip(aa,bb)):
        label=f"group{group}_fc{idx}"
        row={"label":label,"gallery":clean(a),"converted":clean(b),"gallery_dequant_to_converted":metrics(a["dequant"],b["dequant"]),"gallery_raw_to_converted":metrics(a["raw"],b["dequant"])}
        pairs.append((row,a,b));print("deployed_weight",json.dumps(row),flush=True)
assert pairs,"No structural matches"
# Checkpoint weights and exact paired weight_scale from the pinned revision.
# Use the safetensors on-disk header and memory map, avoiding loading language weights.
repo="google/gemma-4-E2B-it-qat-mobile-transformers";revision="dd693ff40353f057ca5f07e945ad867f4afbf2ec"
files=list_repo_files(repo,revision=revision)
def download(name):return Path(hf_hub_download(repo_id=repo,filename=name,revision=revision,local_dir="checkpoint"))
cfg=json.loads(download("config.json").read_text())
assert cfg["quantization_config"]["quant_method"]=="gemma"
print("checkpoint_quant_config",json.dumps(cfg["quantization_config"]),flush=True)
index=next((name for name in files if name.endswith(".safetensors.index.json")),None)
if index:
    weight_map=json.loads(download(index).read_text())["weight_map"]
    keys=[k for k in weight_map if "vision" in k.lower() and k.endswith(".weight_scale") and re.search(r"(?:layers|layer)\.[01]\.",k)]
    print("checkpoint_index_vision_scale_keys",keys,flush=True)
    if not keys:
        keys=[k for k in weight_map if "vision" in k.lower() and k.endswith(".weight_scale")]
        print("checkpoint_fallback_vision_scale_keys",keys[:40],flush=True)
    shards=sorted({weight_map[k] for k in keys}|{weight_map[k.removesuffix("_scale")] for k in keys if k.removesuffix("_scale") in weight_map})
else:
    shards=[name for name in files if name.endswith(".safetensors")]
assert shards,"No vision checkpoint shards found"
locations={}
handles=[]
for name in shards:
    path=download(name)
    fp=path.open("rb"); mm=mmap.mmap(fp.fileno(),0,access=mmap.ACCESS_READ)
    n=binstruct.unpack_from("<Q",mm,0)[0];header=json.loads(mm[8:8+n])
    handles.append((fp,mm))
    for key,h in header.items():
        if key!="__metadata__":locations[key]=(mm,8+n,h)
def read(key):
    mm,base,h=locations[key];a,b=h["data_offsets"];dt=h["dtype"]
    dtypes={"I8":"i1","U8":"u1","F32":"<f4","F16":"<f2","BF16":"<u2","I16":"<i2","I32":"<i4"}
    arr=np.frombuffer(mm,dtype=dtypes[dt],count=(b-a)//np.dtype(dtypes[dt]).itemsize,offset=base+a).reshape(h["shape"])
    if dt=="BF16":return (arr.astype(np.uint32)<<16).view(np.float32)
    return arr
keys=[k for k in locations if "vision" in k.lower() and k.endswith(".weight_scale") and k.removesuffix("_scale") in locations]
first=[k for k in keys if re.search(r"(?:layers|layer)\.[01]\.",k)]
if first:keys=first
print("checkpoint_vision_keys",keys,flush=True)
candidates=[]
for sk in keys:
    wk=sk.removesuffix("_scale")
    try:
        w=read(wk);s=read(sk).astype(np.float32)
        if w.ndim!=2 or s.ndim not in (1,2) or s.shape[0]!=w.shape[0] or w.dtype not in (np.dtype("int8"),np.dtype("uint8")):continue
        for width in sorted({a["shape"][1] for _,a,_ in pairs if a["shape"][0]==w.shape[0]}):
            if width%w.shape[1]:continue
            ratio=width//w.shape[1]
            if ratio==1:q=w.astype(np.int8)
            elif ratio in (2,4):
                bits=8//ratio;u=w.astype(np.uint8)
                q=(np.stack([(u>>(bits*j))&((1<<bits)-1) for j in range(ratio)],axis=-1).astype(np.int16)-(1<<(bits-1))).reshape(w.shape[0],width).astype(np.int8)
            else:continue
            ss=s.reshape(w.shape[0],-1)
            if width%ss.shape[1] or not np.isfinite(ss).all() or np.any(ss<=0):continue
            real=q.astype(np.float32)*np.repeat(ss,width//ss.shape[1],axis=1)
            candidates.append({"key":wk,"scale_key":sk,"shape":list(q.shape),"bits":8//ratio,"scale_min":float(s.min()),"scale_max":float(s.max()),"scale_median":float(np.median(s)),"raw":sample(q),"scaled":sample(real)})
    except Exception as exc:print("checkpoint_tensor_unavailable",wk,str(exc),flush=True)
print("checkpoint_candidate_count",len(candidates),flush=True)
for row,a,b in pairs:
    ranked=[]
    for c in candidates:
        if c["shape"]!=a["shape"]:continue
        x=metrics(c["raw"],b["dequant"]);y=metrics(c["scaled"],a["dequant"]);z=metrics(c["scaled"],b["dequant"])
        ranked.append({"checkpoint":{k:v for k,v in c.items() if k not in ("raw","scaled")},"raw_to_converted":x,"scaled_to_gallery":y,"scaled_to_converted":z})
    ranked.sort(key=lambda x: x["raw_to_converted"].get("cosine") or -2,reverse=True)
    row["checkpoint_candidates"]=ranked[:3]
    print("three_way",json.dumps(row),flush=True)
(out/"weight_scale_report.json").write_text(json.dumps({"checkpoint_revision":revision,"candidate_count":len(candidates),"comparisons":[r for r,_,_ in pairs]},indent=2))
print("weight scale audit complete; matching by structure/cosine is evidence, not full model equivalence",flush=True)
