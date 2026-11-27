"""MNN feasibility gate. CPU conversion/parity only, never reports GPU acceleration."""
import collections, hashlib, json, math, subprocess, sys, time
from pathlib import Path
import numpy as np
import onnx
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageFont
import MNN
import MNN.expr as F
import MNN.nn as nn

name=sys.argv[1]
assert name in ("rep","v2")
root=Path("gpu-assets")
out=Path("gpu-report")/name
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
report={"model":name,"source_sha256":sha(p),"classes":len(chars),"opsets":{x.domain or "ai.onnx":x.version for x in g.opset_import},"operators":dict(sorted(count(g.graph).items())),"initializers":len(g.graph.initializer),"input":[{"name":v.name,"shape":shape(v)} for v in g.graph.input],"output":[{"name":v.name,"shape":shape(v)} for v in g.graph.output],"mnn_version":"3.5.0","status":"graph_audited","scope":"CPU model conversion parity only; no GPU coverage or speed claim","tests":[]}
def save(): (out/"report.json").write_text(json.dumps(report,indent=2,ensure_ascii=False))
save();print(json.dumps(report,ensure_ascii=False),flush=True)
target=out/(name+".mnn")
# No quantization, FP16 storage, custom-op allowance, fixed input or manual graph surgery.
cmd=["mnnconvert","-f","ONNX","--modelFile",str(p),"--MNNModel",str(target),"--bizCode","ocr-gpu-gate","--keepInputFormat"]
with (out/"convert.log").open("w") as f:
    proc=subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT)
print((out/"convert.log").read_text(),flush=True)
assert proc.returncode==0 and target.exists() and target.stat().st_size>100000
report.update(status="converted",mnn_sha256=sha(target),mnn_bytes=target.stat().st_size)
save()
opts=ort.SessionOptions();opts.intra_op_num_threads=4
sess=ort.InferenceSession(str(p),sess_options=opts,providers=["CPUExecutionProvider"])
inp=sess.get_inputs()[0].name;output=sess.get_outputs()[0].name
rt=nn.create_runtime_manager(({"backend":0,"numThread":4,"precision":"high"},))
net=nn.load_module_from_file(str(target),[inp],[output],runtime_manager=rt,shape_mutable=True)
assert net is not None
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
    # Pointer construction copies from contiguous NCHW float32 input. read() synchronizes output.
    v=F.const(x.__array_interface__["data"][0],list(x.shape),F.NCHW,F.float)
    t=time.perf_counter();y=net.forward(v);b=np.asarray(y.read(),dtype=np.float32);mnnms=(time.perf_counter()-t)*1000
    assert a.shape==b.shape and a.ndim==3 and a.shape[2]==len(chars),(a.shape,b.shape)
    assert np.isfinite(a).all() and np.isfinite(b).all()
    ta,tb=decode(a),decode(b)
    maxerr=float(np.max(np.abs(a-b)))
    d={"case":label,"shape":list(x.shape),"output":list(a.shape),"max_abs":maxerr,"mean_abs":float(np.mean(np.abs(a-b))),"allclose":bool(np.allclose(a,b,rtol=3e-3,atol=5e-4)),"ctc_match":ta==tb,"ort":ta,"mnn":tb,"ort_cpu_ms":round(ortms,3),"mnn_cpu_ms":round(mnnms,3)}
    if truth is not None:d["synthetic_truth"]=truth
    report["tests"].append(d);save();print(json.dumps(d,ensure_ascii=False),flush=True)
    assert d["allclose"] and d["ctc_match"],"FP32 parity failed: "+label
rng=np.random.default_rng(19)
shapes=([(1,3,48,320),(6,3,48,320),(2,3,48,348),(6,3,48,759),(6,3,48,939),(1,3,48,320)] if name=="rep" else [(1,3,64,64),(6,3,64,64),(6,3,48,96),(2,3,40,112),(6,3,32,128),(1,3,32,224),(2,3,32,512),(6,3,32,640),(1,3,64,64)])
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
report["status"]="cpu_parity_passed";save()
print("MNN CPU PARITY PASSED",name,flush=True)
