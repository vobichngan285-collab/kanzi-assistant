"""Read-only checkpoint probes on exact previously tested model files; no export or APK."""
import os, struct, difflib, gc
from pathlib import Path
import numpy as np
# Reuse exact source/patch verification, extraction and static descriptions.
p=Path(__file__).with_name("compare_vision.py")
exec(compile(p.read_text().split("rng=np.random.default_rng(20261126)",1)[0],str(p),"exec"))
import struct as binary_struct  # shared setup defines a variable called struct
rng=np.random.default_rng(20261126)
images=rng.uniform(-1,1,size=(1,2520,768)).astype(np.float32)
yy,xx=np.indices((60,42))
positions=np.stack((xx.ravel(),yy.ravel()),axis=-1)[None].astype(np.int32)
inputs={"images":images,"positions_xy":positions}
paths={"original":original["encoder"],"converted":patch/"encoder.tflite"}
def info(path):
    raw=path.read_bytes(); m=tflite.Model.GetRootAsModel(raw,0)
    sig=next(m.SignatureDefs(i) for i in range(m.SignatureDefsLength()) if m.SignatureDefs(i).SignatureKey()==b"vision_280")
    gi=sig.SubgraphIndex(); g=m.Subgraphs(gi)
    def shape(t):return tuple(t.ShapeAsNumpy()) if t.ShapeLength() else ()
    def desc(i):
        t=g.Tensors(i)
        return {"id":int(i),"name":(t.Name() or b"").decode(errors="replace"),"shape":[int(x) for x in shape(t)],"dtype":types_enum.get(t.Type(),str(t.Type()))}
    def opname(o):
        c=m.OperatorCodes(o.OpcodeIndex());return ops_enum.get(max(c.BuiltinCode(),c.DeprecatedBuiltinCode()),"UNKNOWN")
    ops=[]; producer={}
    for i in range(g.OperatorsLength()):
        o=g.Operators(i); ins=[int(x) for x in o.InputsAsNumpy() if x>=0]; outs=[int(x) for x in o.OutputsAsNumpy() if x>=0]
        rec={"op":i,"kind":opname(o),"inputs":ins,"outputs":outs}
        ops.append(rec)
        for t in outs:producer[t]=rec
    soft=[r["op"] for r in ops if r["kind"]=="SOFTMAX"]
    assert len(soft)==16,(path,soft)
    def fcs(lo,hi):
        return [r for r in ops[lo:hi] if r["kind"]=="FULLY_CONNECTED"]
    # Group FCs by exact chronological softmax landmarks; no assumption of matching op indices.
    groups=[fcs(lo,hi) for lo,hi in zip([0]+soft,[*soft,len(ops)])]
    def fc_key(r):return (tuple(desc(r["inputs"][1])["shape"]),tuple(desc(r["outputs"][0])["shape"]))
    # Identify potential residual additions; do not automatically assert semantic equivalence.
    adds=[]
    for lo,hi in zip(soft,[*soft[1:],len(ops)]):
        candidates=[]
        for r in ops[lo:hi]:
            if r["kind"]!="ADD" or len(r["inputs"])!=2 or len(r["outputs"])!=1:continue
            shapes=[desc(t)["shape"] for t in r["inputs"]+r["outputs"]]
            if shapes[0]==shapes[1]==shapes[2] and shapes[0] in ([1,2520,768],[2520,768]):candidates.append(r)
        adds.append(candidates)
    return raw,m,g,gi,desc,producer,groups,fc_key,adds,ops
def metrics(a,b):
    if a.shape!=b.shape:return {"shape_mismatch":[list(a.shape),list(b.shape)]}
    a=a.astype(np.float64).ravel();b=b.astype(np.float64).ravel()
    if not np.isfinite(a).all() or not np.isfinite(b).all():return {"nonfinite":True}
    na=np.linalg.norm(a);nb=np.linalg.norm(b)
    return {"cosine":float(a@b/(na*nb)) if na*nb else None,"relative_l2":float(np.linalg.norm(a-b)/na) if na else None,"max_abs":float(np.max(np.abs(a-b))),"norm_a":float(na),"norm_b":float(nb)}
def sample(a):
    a=np.asarray(a)
    flat=a.reshape(-1)
    return flat[np.linspace(0,len(flat)-1,min(len(flat),16384),dtype=np.int64)].copy()
def get_const(m,g,raw,producer,i):
    t=g.Tensors(i); typ=types_enum.get(t.Type())
    b=m.Buffers(t.Buffer())
    if b.DataLength(): data=b.DataAsNumpy()
    elif hasattr(b,"Offset") and b.Offset() and b.Offset()+b.Size()<=len(raw):data=memoryview(raw)[b.Offset():b.Offset()+b.Size()]
    elif i in producer and producer[i]["kind"]=="DEQUANTIZE":return get_const(m,g,raw,producer,producer[i]["inputs"][0])
    else:return None
    dt={"FLOAT32":np.float32,"FLOAT16":np.float16,"INT8":np.int8,"INT32":np.int32,"UINT8":np.uint8}.get(typ)
    if dt is None:return None
    a=np.frombuffer(data,dtype=dt)
    shape=tuple(t.ShapeAsNumpy()) if t.ShapeLength() else ()
    if a.size!=int(np.prod(shape)):return None
    a=a.reshape(shape)
    q=t.Quantization()
    if typ in ("INT8","UINT8","INT32") and q and q.ScaleLength():
        s=np.array(q.ScaleAsNumpy(),np.float32); z=np.array(q.ZeroPointAsNumpy(),np.float32) if q.ZeroPointLength() else np.zeros(1,np.float32)
        if len(s)>1:
            dims=[1]*a.ndim;dims[q.QuantizedDimension()]=len(s);s=s.reshape(dims)
            if len(z)>1:z=z.reshape(dims)
        a=(a.astype(np.float32)-z)*s
    return a
# Inspect both, store only lightweight matching metadata, release large graph buffers.
catalog={}
for label,path in paths.items():
    raw,m,g,gi,desc,prod,groups,key,adds,ops=info(path)
    record={"groups":[[{"op":r["op"],"key":key(r),"inputs":[desc(t) for t in r["inputs"]],"out":desc(r["outputs"][0])} for r in group] for group in groups],
            "adds":[[{"op":r["op"],"out":desc(r["outputs"][0]),"inputs":[desc(t) for t in r["inputs"]]} for r in group] for group in adds]}
    catalog[label]=record
    print("fc_catalog",label,json.dumps(record),flush=True)
    del raw,m,g,gi,desc,prod,groups,key,adds,ops;gc.collect()
(out/"layer_catalog.json").write_text(json.dumps(catalog,indent=2))
pairs=[]
for layer,(aa,bb) in enumerate(zip(catalog["original"]["groups"],catalog["converted"]["groups"])):
    ka=[r["key"] for r in aa];kb=[r["key"] for r in bb]
    if layer==0 and ka and kb and ka[0]==kb[0] and "input_projection" in aa[0]["out"]["name"]:
        matches=[(0,0)]
        confidence="first_projection_candidate_check_input_and_weights"
    elif ka==kb:
        matches=list(zip(range(len(ka)),range(len(kb))));confidence="same_order_and_shapes"
    else:
        # Only unambiguous unique-shape matches in differing groups; repeated shapes are not proof.
        matches=[(i,kb.index(k)) for i,k in enumerate(ka) if ka.count(k)==kb.count(k)==1]
        confidence="unique_shape_in_group"
    print("group_alignment",layer,"original",ka,"converted",kb,"matches",matches,"confidence",confidence,flush=True)
    for i,j in matches:
        a,b=aa[i],bb[j]
        # Prioritize early every projection; later capture each group's endpoints.
        if layer<=2 or i in (0,len(aa)-1):
            pairs.append({"label":f"group{layer}_fc{i}_{j}","confidence":confidence,"a":a,"b":b})
# Candidate residual endpoints; clearly marked as heuristic, not used as first failure proof.
residual_pairs=[]
for layer,(aa,bb) in enumerate(zip(catalog["original"]["adds"],catalog["converted"]["adds"])):
    if len(aa)==len(bb)==2:
        for i,(a,b) in enumerate(zip(aa,bb)):
            if a["out"]["shape"]==b["out"]["shape"]:residual_pairs.append({"label":f"residual_candidate_{layer}_{i}","confidence":"heuristic_two_adds","a":a,"b":b})
print("probe_count",len(pairs),len(residual_pairs),flush=True)
assert pairs,"no compatible FC checkpoints"
samples={}
weights={}
valid={}
def infer(path):
    it=Interpreter(model_path=str(path),num_threads=4)
    run=it.get_signature_runner("vision_280")
    return it,run,{k:np.array(v,copy=True) for k,v in run(**inputs).items()}
for label,path in paths.items():
    side="a" if label=="original" else "b"
    raw,m,g,gi,desc,prod,groups,key,adds,ops=info(path)
    chosen=set()
    weights[label]={}
    for row in pairs:
        r=row[side]; chosen.add(r["out"]["id"])
        if row["label"].startswith(("group0_","group1_")):
            chosen.add(r["inputs"][0]["id"])
            w=get_const(m,g,raw,prod,r["inputs"][1]["id"])
            if w is not None:weights[label][row["label"]]=(list(w.shape),sample(w))
    for row in residual_pairs:chosen.add(row[side]["out"]["id"])
    quant={}
    for idx in chosen:
        t=g.Tensors(idx); q=t.Quantization()
        if q and q.ScaleLength() and types_enum.get(t.Type()) in ("INT8","UINT8","INT32"):
            quant[idx]=(np.array(q.ScaleAsNumpy(),np.float32),np.array(q.ZeroPointAsNumpy(),np.float32) if q.ZeroPointLength() else np.zeros(1,np.float32),q.QuantizedDimension())
    old=[int(x) for x in g.OutputsAsNumpy()]
    # Append a new FlatBuffer vector; all original tensor/weight offsets and signature maps unchanged.
    field=g._tab.Pos+g._tab.Offset(8)
    assert field>g._tab.Pos and g._tab.Vector(g._tab.Offset(8))>field
    patched=out/(label+"_probe.tflite")
    new_outputs=old+sorted(chosen-set(old))
    # Stream the original bytes and patch one unsigned offset on disk.
    with patched.open("wb") as f:
        f.write(raw); f.write(b"\0"*((-len(raw))%4)); pos=f.tell()
        f.write(binary_struct.pack("<I",len(new_outputs)));f.write(binary_struct.pack("<"+"i"*len(new_outputs),*new_outputs))
        f.seek(field);f.write(binary_struct.pack("<I",pos-field))
    del raw,m,g,desc,prod,groups,key,adds,ops;gc.collect()
    it,run,base_out=infer(path);del run,it;gc.collect()
    try:
        it,run,probe_out=infer(patched)
        check=metrics(base_out["features"],probe_out["features"])
        valid[label]=bool(np.array_equal(base_out["mask"],probe_out["mask"]) and check.get("relative_l2",1)<1e-4)
        print("instrumentation_check",label,valid[label],json.dumps(check),flush=True)
        samples[label]={}
        if valid[label]:
            for idx in sorted(chosen):
                a=it.get_tensor(idx,subgraph_index=gi)
                if idx in quant:
                    scale,zero,axis=quant[idx]
                    if len(scale)>1:
                        dims=[1]*a.ndim;dims[axis]=len(scale);scale=scale.reshape(dims)
                        if len(zero)>1:zero=zero.reshape(dims)
                    a=(a.astype(np.float32)-zero)*scale
                samples[label][idx]=(list(a.shape),sample(a))
        del run,it
    finally:
        patched.unlink(missing_ok=True)
    gc.collect()
report=[]
if all(valid.get(k,False) for k in paths):
    for row in pairs+residual_pairs:
        a,b=row["a"],row["b"]
        x,y=samples["original"][a["out"]["id"]],samples["converted"][b["out"]["id"]]
        data={"label":row["label"],"confidence":row["confidence"],"original":a,"converted":b,"sample_output":metrics(x[1],y[1]) if x[0]==y[0] else {"shape_mismatch":[x[0],y[0]]}}
        if a.get("inputs") and a["inputs"][0]["id"] in samples["original"] and b["inputs"][0]["id"] in samples["converted"]:
            u,v=samples["original"][a["inputs"][0]["id"]],samples["converted"][b["inputs"][0]["id"]]
            data["sample_input"]=metrics(u[1],v[1]) if u[0]==v[0] else {"shape_mismatch":[u[0],v[0]]}
        if row["label"] in weights["original"] and row["label"] in weights["converted"]:
            u,v=weights["original"][row["label"]],weights["converted"][row["label"]]
            data["sample_weight_dequantized"]=metrics(u[1],v[1]) if u[0]==v[0] else {"shape_mismatch":[u[0],v[0]]}
        # Quantized activations were dequantized from tensor metadata where available.
        if a["out"]["dtype"]!=b["out"]["dtype"]:data["output_dtype_mismatch"]=True
        report.append(data)
        print("layer_probe",json.dumps(data),flush=True)
else:print("unavailable: instrumentation changed model outputs; do not infer divergence",flush=True)
(out/"layer_report.json").write_text(json.dumps({"instrumentation_valid":valid,"checkpoints":report},indent=2))
print("layer diagnosis complete; FC pairing is structural, residuals heuristic, samples are not full layer equivalence",flush=True)
