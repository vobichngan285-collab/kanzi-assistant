"""Append top-index and top-score projection; preserve original graph and weights.
Full-probability MNN gate from 20261127 remains failed; this is a new projection test.
"""
import hashlib,json,math,subprocess
from pathlib import Path
import numpy as np
import onnx
from onnx import helper,TensorProto,numpy_helper
import onnxruntime as ort
from PIL import Image,ImageDraw,ImageFont
import MNN.expr as F
import MNN.nn as nn
a=Path("openocr-lab/app/src/main/assets")
p=a/"rep.onnx"; q=Path("rep-compact.onnx"); target=a/"rep-compact.mnn"
def sha(f):return hashlib.sha256(f.read_bytes()).hexdigest()
g=onnx.load(p); onnx.checker.check_model(g)
assert len(g.graph.output)==1 and len(g.graph.input)==1
old=g.graph.output[0]; name=old.name; dims=old.type.tensor_type.shape.dim
assert len(dims)==3 and dims[2].dim_value in (0,6625)
opset=next(x.version for x in g.opset_import if not x.domain)
assert opset>=11
before=[x.SerializeToString() for x in g.graph.initializer]
# Lowest index on ties matches frozen Kotlin strict > scan.
g.graph.node.extend([helper.make_node("ArgMax",[name],["compact_idx64"],axis=2,keepdims=1),
 helper.make_node("Cast",["compact_idx64"],["compact_idx"],to=TensorProto.FLOAT)])
if opset>=18:
 g.graph.initializer.append(numpy_helper.from_array(np.array([2],dtype=np.int64),"compact_axes"))
 g.graph.node.append(helper.make_node("ReduceMax",[name,"compact_axes"],["compact_score"],keepdims=1))
else:g.graph.node.append(helper.make_node("ReduceMax",[name],["compact_score"],axes=[2],keepdims=1))
g.graph.node.append(helper.make_node("Concat",["compact_idx","compact_score"],["compact"],axis=2))
shape=[d.dim_value if d.HasField("dim_value") else d.dim_param or None for d in dims[:2]]+[2]
del g.graph.output[:];g.graph.output.append(helper.make_tensor_value_info("compact",TensorProto.FLOAT,shape))
assert [x.SerializeToString() for x in list(g.graph.initializer)[:len(before)]]==before
onnx.checker.check_model(g);onnx.save(g,q)
subprocess.run(["mnnconvert","-f","ONNX","--modelFile",str(q),"--MNNModel",str(target),"--bizCode","bluestar-compact","--keepInputFormat"],check=True)
assert target.stat().st_size>100000
opt=ort.SessionOptions();opt.intra_op_num_threads=4
ref=ort.InferenceSession(str(p),sess_options=opt,providers=["CPUExecutionProvider"])
compact=ort.InferenceSession(str(q),sess_options=opt,providers=["CPUExecutionProvider"])
inp=ref.get_inputs()[0].name
runtime=nn.create_runtime_manager(({"backend":0,"numThread":4,"precision":"high"},))
net=nn.load_module_from_file(str(target),[inp],["compact"],runtime_manager=runtime,shape_mutable=True)
assert net is not None
report={"source_sha256":sha(p),"compact_onnx_sha256":sha(q),"mnn_sha256":sha(target),"opset":opset,"classes":6625,"prior_full_probability_gate":"FAILED (45951226249); projection test does not reverse it","cases":[]}
def save():Path("compact-report.json").write_text(json.dumps(report,indent=2,ensure_ascii=False))
def test(x,label):
 x=np.ascontiguousarray(x,np.float32)
 full=ref.run(None,{inp:x})[0]
 assert full.ndim==3 and full.shape[2]==6625 and np.isfinite(full).all()
 expected=np.stack([full.argmax(-1),full.max(-1)],axis=-1).astype(np.float32)
 y=compact.run(None,{inp:x})[0]
 assert y.shape==expected.shape and np.array_equal(y[:,:,0],expected[:,:,0])
 assert np.allclose(y[:,:,1],expected[:,:,1],rtol=3e-3,atol=5e-4)
 v=F.const(x.__array_interface__["data"][0],list(x.shape),F.NCHW,F.float)
 z=np.asarray(net.forward(v).read(),dtype=np.float32)
 assert z.shape==expected.shape and np.isfinite(z).all()
 assert np.all(z[:,:,0]==np.floor(z[:,:,0])) and np.all((z[:,:,0]>=0)&(z[:,:,0]<6625))
 d={"label":label,"input":list(x.shape),"output":list(z.shape),"ort_index_diff":int(np.count_nonzero(y[:,:,0]!=expected[:,:,0])),"ort_max_score_error":float(np.max(np.abs(y[:,:,1]-expected[:,:,1]))),"mnn_index_diff":int(np.count_nonzero(z[:,:,0]!=expected[:,:,0])),"mnn_max_score_error":float(np.max(np.abs(z[:,:,1]-expected[:,:,1]))),"mnn_score_outside":int(np.count_nonzero(np.abs(z[:,:,1]-expected[:,:,1])>5e-4+3e-3*np.abs(expected[:,:,1]))),"steps":int(expected.shape[0]*expected.shape[1])}
 report["cases"].append(d);save();print(d,flush=True)
rng=np.random.default_rng(19)
for shape in [(1,3,48,320),(6,3,48,320),(2,3,48,348),(6,3,48,759),(6,3,48,939),(1,3,48,320)]:test(rng.uniform(-1,1,shape),"dynamic")
font=ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",26)
for labels in [["刮痧","中医养生","精油开背50分钟","120元","PRICE LIST","充1000送200"],["中华人民共和国中国银行营业时间上午九点至下午五点"]]:
 images=[]
 for t in labels:
  bb=font.getbbox(t);im=Image.new("RGB",(bb[2]-bb[0]+12,bb[3]-bb[1]+10),"white")
  ImageDraw.Draw(im).text((6-bb[0],5-bb[1]),t,font=font,fill="black")
  w,h=im.size;actual=math.ceil(48*w/h);images.append(np.asarray(im.resize((actual,48)),dtype=np.float32)[:,:,::-1]/127.5-1)
 width=max(320,max(x.shape[1] for x in images));x=np.zeros((len(images),3,48,width),np.float32)
 for i,v in enumerate(images):x[i,:,:,:v.shape[1]]=v.transpose(2,0,1)
 test(x,"synthetic")
report["projection_gate_passed"]=all(c["mnn_index_diff"]==0 and c["mnn_score_outside"]==0 for c in report["cases"])
report["scope"]="experimental host CPU compact-output validation; GPU and full probability parity not established"
save()
# A failed MNN projection parity is retained visibly in experimental APK, not a production gate pass.
(a/"compact-report.json").write_bytes(Path("compact-report.json").read_bytes())
print("COMPACT_DIAGNOSTIC",report["projection_gate_passed"],flush=True)
