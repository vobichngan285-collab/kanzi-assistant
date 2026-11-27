"""OpenOCRv2 CTC FP32 export gate. No private images; no Android release."""
import sys, json, hashlib, time, importlib.util
from pathlib import Path
import numpy as np
import torch
import onnx
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageFont
from safetensors.torch import load_file

REV="22e72eb3725290dabdc99c95ef9a72796d71e742"
WEIGHT_SHA="6fc673c155236dcd019e93569ad42a09eebca6f657296afd8d4e61f5a925d2aa"
root=Path("v2-upstream")
out=Path("v2-export"); out.mkdir(exist_ok=True)
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(root/"rec_ctc/model.safetensors")==WEIGHT_SHA
spec=importlib.util.spec_from_file_location("v2official",root/"modeling_openocrv2_rec_ctc.py")
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
cfg=json.loads((root/"rec_ctc/config.json").read_text())
chars=json.loads((root/"rec_ctc/characters.json").read_text())
assert isinstance(chars,list) and chars==cfg["characters"] and len(chars)==cfg["vocab_size"]==19685
assert chars[0]=="blank" and all(isinstance(x,str) for x in chars)
torch.set_num_threads(4)
torch.set_grad_enabled(False)
net=m.CTCRecognizer(cfg["architecture"]).eval()
state=load_file(str(root/"rec_ctc/model.safetensors"),device="cpu")
expected=set(net.state_dict())
if set(state)!=expected:
    assert all(k.startswith("model.") for k in state), "unexpected state namespace"
    state={k[6:]:v for k,v in state.items()}
net.load_state_dict(state,strict=True)
del state
print("strict state load passed",sum(p.numel() for p in net.parameters()),flush=True)
p=out/"openocrv2_ctc.onnx"
torch.onnx.export(net,torch.zeros(1,3,32,160),str(p),input_names=["pixel_values"],output_names=["probabilities"],opset_version=17,dynamo=False,do_constant_folding=True,dynamic_axes={"pixel_values":{0:"batch",2:"height",3:"width"},"probabilities":{0:"batch",1:"steps"}})
onnx.checker.check_model(str(p))
opts=ort.SessionOptions(); opts.intra_op_num_threads=4
sess=ort.InferenceSession(str(p),sess_options=opts,providers=["CPUExecutionProvider"])
def decode(y):
    tokens=y.argmax(2); scores=y.max(2)
    return m.decode_ctc(tokens,scores,chars)[0]
results=[]
def check(x,label,truth=None):
    x=np.ascontiguousarray(x,dtype=np.float32)
    with torch.no_grad(): a=net(torch.from_numpy(x)).numpy()
    start=time.perf_counter(); b=sess.run(None,{"pixel_values":x})[0]; ms=(time.perf_counter()-start)*1000
    assert a.shape==b.shape and a.shape[0]==len(x) and a.shape[2]==len(chars)
    assert np.isfinite(a).all() and np.isfinite(b).all()
    np.testing.assert_allclose(a,b,rtol=2e-3,atol=3e-4,err_msg=label)
    ta,tb=decode(a),decode(b)
    assert ta==tb,(label,ta,tb)
    d={"case":label,"input":list(x.shape),"output":list(b.shape),"max_abs":float(np.abs(a-b).max()),"ort_ms":round(ms,3),"pytorch":ta,"onnx":tb}
    if truth is not None: d["expected"]=truth
    results.append(d); print(json.dumps(d,ensure_ascii=False),flush=True)
rng=np.random.default_rng(17)
for n,h,w in [(1,64,64),(2,48,96),(1,40,112),(2,32,128),(6,32,320),(1,32,640),(6,32,640)]:
    check(rng.uniform(-1,1,(n,3,h,w)),f"dynamic_{n}_{h}_{w}")
# Official preprocessing, independent raw greedy decode, not the optional cleanup.
font=ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",26)
labels=["中医推拿一小时","精油开背50分钟","充1000送200","PRICE LIST","刮痧","中医养生","90元","中华人民共和国中国银行营业时间上午九点至下午五点"]
crops=[]
for i,s in enumerate(labels):
    bb=font.getbbox(s); im=Image.new("RGB",(bb[2]-bb[0]+12,bb[3]-bb[1]+10),"white")
    ImageDraw.Draw(im).text((6-bb[0],5-bb[1]),s,font=font,fill="black")
    if i%3==1: im=im.resize((max(2,im.width//2),max(2,im.height//2)),Image.Resampling.BICUBIC)
    crops.append(np.asarray(im)[:,:,::-1].copy())
for indices,x in m.recognition_batches(crops,cfg["preprocess"],32):
    check(x,"official_preprocess",[labels[i] for i in indices])
report={"revision":REV,"upstream_sha256":{str(f.relative_to(root)):sha(f) for f in root.rglob("*") if f.is_file() and "__pycache__" not in str(f)},"onnx_sha256":sha(p),"onnx_bytes":p.stat().st_size,"opset":17,"torch":torch.__version__,"ort":ort.__version__,"preprocess":cfg["preprocess"],"classes":len(chars),"tests":results,"scope":"FP32 CPU graph parity only; synthetic text not field accuracy, Android not verified"}
(out/"validation.json").write_text(json.dumps(report,ensure_ascii=False,indent=2))
(out/"characters.json").write_text(json.dumps(chars,ensure_ascii=False))
(out/"config.json").write_text(json.dumps(cfg,ensure_ascii=False))
print("EXPORT GATE PASSED",flush=True)
