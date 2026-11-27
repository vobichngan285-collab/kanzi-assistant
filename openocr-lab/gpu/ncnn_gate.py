"""Pinned pnnx/ncnn host feasibility gate. No Android or GPU claim."""
import collections, hashlib, json, math, subprocess, sys, time
from pathlib import Path
import numpy as np
import onnx
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageFont
import ncnn
import shutil

name=sys.argv[1]
assert name in ("rep","v2")
root=Path("gpu-assets")
out=Path("ncnn-report")/name
out.mkdir(parents=True,exist_ok=True)
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
p=root/("rep.onnx" if name=="rep" else "openocrv2_ctc.onnx")
chars=([""]+(root/"chars.txt").read_text().splitlines()+[" "] if name=="rep" else json.loads((root/"v2-characters.json").read_text()))
source=json.loads((root/("openocr-report.json" if name=="rep" else "openocrv2-validation.json")).read_text())
assert sha(p)==(source["rep"]["onnx_sha256"] if name=="rep" else source["onnx_sha256"])
assert len(chars)>6000 and (name=="rep" or len(chars)==19685 and chars[0]=="blank")
g=onnx.load(str(p))
onnx.checker.check_model(g)
def count(graph):
    c=collections.Counter()
    for node in graph.node:
        c[(node.domain or "ai.onnx")+":"+node.op_type]+=1
        for attr in node.attribute:
            if attr.type==onnx.AttributeProto.GRAPH:c.update(count(attr.g))
            elif attr.type==onnx.AttributeProto.GRAPHS:
                for x in attr.graphs:c.update(count(x))
    return c
def shape(v):
    return [d.dim_value if d.HasField("dim_value") else d.dim_param for d in v.type.tensor_type.shape.dim]
report={"model":name,"source_sha256":sha(p),"classes":len(chars),"opsets":{x.domain or "ai.onnx":x.version for x in g.opset_import},"operators":dict(sorted(count(g.graph).items())),"initializers":len(g.graph.initializer),"input":[{"name":v.name,"shape":shape(v)} for v in g.graph.input],"output":[{"name":v.name,"shape":shape(v)} for v in g.graph.output],"ncnn_version":"1.0.20261020","pnnx_version":"20261020","ncnn_source_commit":"7e36d25cb153092ba87a60530455c7376fedf452","status":"graph_audited","scope":"CPU model conversion parity only; no GPU coverage or speed claim","tests":[]}
def save(): (out/"report.json").write_text(json.dumps(report,indent=2,ensure_ascii=False))
save();print(json.dumps(report,ensure_ascii=False),flush=True)
# Use a second input shape to ask pnnx to preserve dynamic axes; never assume it succeeded.
anchors=([(1,3,48,320),(6,3,48,939)] if name=="rep" else [(1,3,64,64),(6,3,32,640)])
def argshape(s):return "["+",".join(map(str,s))+"]"
work=Path("ncnn-work")/name
work.mkdir(parents=True,exist_ok=True)
shutil.copyfile(p,work/"model.onnx")
cmd=["pnnx","model.onnx","inputshape="+argshape(anchors[0]),"inputshape2="+argshape(anchors[1]),"fp16=0","device=cpu"]
report.update(status="converting",command=cmd,anchor_shapes=anchors);save()
with (out/"convert.log").open("w") as f:
    proc=subprocess.run(cmd,cwd=work,stdout=f,stderr=subprocess.STDOUT,timeout=900)
print((out/"convert.log").read_text(),flush=True)
report["converter_returncode"]=proc.returncode
save()
param=work/"model.ncnn.param";weights=work/"model.ncnn.bin"
assert proc.returncode==0 and param.is_file() and weights.is_file() and weights.stat().st_size>100000,"conversion failed or no usable ncnn graph"
param=Path(shutil.copyfile(param,out/(name+".ncnn.param")))
weights=Path(shutil.copyfile(weights,out/(name+".ncnn.bin")))
for f in work.glob("*.pnnx.param"):shutil.copyfile(f,out/f.name)
for f in work.glob("*_ncnn.py"):shutil.copyfile(f,out/f.name)
lines=param.read_text().splitlines()
assert lines[0].strip()=="7767517","invalid ncnn param magic"
report.update(status="converted",param_sha256=sha(param),bin_sha256=sha(weights),bin_bytes=weights.stat().st_size,ncnn_layers=dict(collections.Counter(l.split()[0] for l in lines[2:] if l.strip())))
save()
print("NCNN GRAPH",json.dumps(report["ncnn_layers"]),flush=True)
opts=ort.SessionOptions();opts.intra_op_num_threads=4
sess=ort.InferenceSession(str(p),sess_options=opts,providers=["CPUExecutionProvider"])
inp=sess.get_inputs()[0].name;output=sess.get_outputs()[0].name
net=ncnn.Net()
net.opt.num_threads=4
net.opt.use_vulkan_compute=False
for key in ("use_fp16_packed","use_fp16_storage","use_fp16_arithmetic","use_bf16_packed","use_bf16_storage","use_int8_inference","use_int8_packed","use_int8_storage","use_int8_arithmetic"):
    setattr(net.opt,key,False)
assert net.load_param(str(param))==0,"ncnn load_param failed"
assert net.load_model(str(weights))==0,"ncnn load_model failed"
inputs=list(net.input_names());outputs=list(net.output_names())
assert len(inputs)==len(outputs)==1,(inputs,outputs)
report.update(status="loaded",ncnn_inputs=inputs,ncnn_outputs=outputs)
save()
def decode(a):
    texts=[]
    for row in a.argmax(-1):
        last=-1;out=""
        for idx in row:
            idx=int(idx)
            if idx and idx!=last:out+=chars[idx]
            last=idx
        texts.append(out)
    return texts
def check(x,label,truth=None):
    x=np.ascontiguousarray(x,dtype=np.float32)
    t=time.perf_counter();a=sess.run([output],{inp:x})[0];ortms=(time.perf_counter()-t)*1000
    # Native ncnn batch axis, not a loop over samples or an accidental NCDHW tensor.
    print("CASE_BEGIN",label,list(x.shape),flush=True)
    report["active_case"]={"label":label,"shape":list(x.shape)};save()
    t=time.perf_counter()
    v=ncnn.Mat(x,batch_index=0)
    ex=net.create_extractor()
    assert ex.input(inputs[0],v)==0,"ncnn input failed"
    y=ncnn.Mat()
    assert ex.extract(outputs[0],y)==0,"ncnn extract failed"
    b=np.array(y.numpy(batch_index=0),dtype=np.float32,copy=True)
    ncnnms=(time.perf_counter()-t)*1000
    assert a.shape==b.shape and a.ndim==3 and a.shape[2]==len(chars),(a.shape,b.shape)
    assert np.isfinite(a).all() and np.isfinite(b).all()
    ta,tb=decode(a),decode(b)
    maxerr=float(np.max(np.abs(a-b)))
    d={"case":label,"shape":list(x.shape),"output":list(a.shape),"max_abs":maxerr,"mean_abs":float(np.mean(np.abs(a-b))),"allclose":bool(np.allclose(a,b,rtol=3e-3,atol=5e-4)),"ctc_match":ta==tb,"ort":ta,"ncnn":tb,"ort_cpu_ms":round(ortms,3),"ncnn_cpu_ms":round(ncnnms,3)}
    if truth is not None:d["synthetic_truth"]=truth
    report["tests"].append(d);report.pop("active_case",None);save();print(json.dumps(d,ensure_ascii=False),flush=True)
    # Keep the original strict gate, but collect every executable test before rejecting.
    bad=np.abs(a-b)>(5e-4+3e-3*np.abs(b))
    d["outside_tolerance_count"]=int(bad.sum())
    d["probability_count"]=int(a.size)
    d["argmax_step_mismatches"]=int(np.count_nonzero(a.argmax(-1)!=b.argmax(-1)))
    d["max_argmax_score_difference"]=float(np.max(np.abs(a.max(-1)-b.max(-1))))
    save()
    print("PARITY_DIAGNOSTIC",json.dumps(d,ensure_ascii=False),flush=True)
rng=np.random.default_rng(19)
shapes=([(1,3,48,320),(6,3,48,320),(2,3,48,348),(6,3,48,759),(6,3,48,939),(2,3,48,320),(1,3,48,320)] if name=="rep" else [(1,3,64,64),(6,3,64,64),(6,3,48,96),(2,3,40,112),(6,3,32,128),(1,3,32,224),(2,3,32,512),(6,3,32,640),(4,3,48,96),(1,3,32,544),(1,3,32,448),(2,3,64,64),(1,3,32,640),(1,3,32,160),(2,3,32,128),(1,3,64,64)])
for s in shapes:check(rng.uniform(-1,1,s),"dynamic:"+str(s))
font=ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",26)
labels=["刮痧","中医养生","精油开背50分钟","120元","PRICE LIST","充1000送200","中华人民共和国中国银行营业时间上午九点至下午五点"]
groups=collections.defaultdict(list)
for text in labels:
    bb=font.getbbox(text)
    im=Image.new("RGB",(bb[2]-bb[0]+12,bb[3]-bb[1]+10),"white")
    ImageDraw.Draw(im).text((6-bb[0],5-bb[1]),text,font=font,fill="black")
    w,h=im.size
    if name=="rep":
        width=max(320,int(48*max(w/h,320/48)))
        actual=min(width,math.ceil(48*w/h))
        a=np.asarray(im.resize((actual,48),Image.Resampling.BILINEAR),dtype=np.float32)[:,:,::-1]/127.5-1
        x=np.zeros((3,48,width),np.float32);x[:,:,:actual]=a.transpose(2,0,1)
    else:
        ratio=max(1,min(20,round(w/h)))
        width,height={1:(64,64),2:(96,48),3:(112,40),4:(128,32)}.get(ratio,(32*ratio,32))
        x=(np.asarray(im.resize((width,height),Image.Resampling.BICUBIC),dtype=np.float32)/127.5-1).transpose(2,0,1)
    groups[x.shape].append((text,x))
for rows in groups.values():
    check(np.stack([x for _,x in rows]),"synthetic_text",[s for s,_ in rows])
failed=[t["case"] for t in report["tests"] if not(t["allclose"] and t["ctc_match"])]
report["status"]="cpu_parity_failed" if failed else "cpu_parity_passed"
report["failed_cases"]=failed
save()
assert not failed, "FP32 parity failed cases: "+str(failed)
print("NCNN CPU PARITY PASSED",name,flush=True)
