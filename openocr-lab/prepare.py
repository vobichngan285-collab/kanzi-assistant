from pathlib import Path
import hashlib,json,sys,zipfile,os,copy,time
import numpy as np,cv2,torch,yaml,onnx,onnxruntime as ort
from PIL import Image,ImageDraw,ImageFont
root=Path.cwd()
assets=root/"openocr-lab/app/src/main/assets";assets.mkdir(parents=True,exist_ok=True)
up=root/"upstream"
sys.path.insert(0,str(up))
os.chdir(up)
from openrec.modeling import build_model
from openrec.preprocess.resize import RecDynamicResize
from openrec.postprocess.ctc_postprocess import CTCLabelDecode
torch.set_num_threads(4)
dictfile=up/"tools/utils/ppocr_keys_v1.txt"
chars=dictfile.read_text(encoding="utf-8").splitlines()
assert len(chars)>6000 and len(set(chars))==len(chars) and " " not in chars
(assets/"chars.txt").write_bytes(dictfile.read_bytes())
decoder=CTCLabelDecode(str(dictfile),True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def decode(a):return decoder(a,torch_tensor=False)[0][0]
def session(p):
    o=ort.SessionOptions();o.intra_op_num_threads=4
    return ort.InferenceSession(str(p),sess_options=o,providers=["CPUExecutionProvider"])
def inp(text):
    font=ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",40)
    w=int(font.getlength(text))+20
    im=Image.new("RGB",(w,60),"white")
    ImageDraw.Draw(im).text((8,3),text,font=font,fill="black")
    return RecDynamicResize([48,320],False)({"image":np.asarray(im)[:,:,::-1].copy()})["image"][None]
samples=["精油开背50分钟","肠胃调理120元","艾灸养生","充1000送200","PRICE LIST","中医推拿一小时90元","中华人民共和国中国银行营业时间上午九点至下午五点"]
report={}
for name,fn in [("rep","openocr_repsvtr_ch.pth"),("svtr","openocr_svtrv2_ch.pth")]:
    cfg=yaml.safe_load((up/("configs/rec/svtrv2/"+("repsvtr_ch.yml" if name=="rep" else "svtrv2_ch.yml"))).read_text())
    cfg["Architecture"]["Decoder"]["out_channels"]=len(chars)+2
    model=build_model(cfg["Architecture"])
    ck=torch.load(root/fn,map_location="cpu",weights_only=False)
    sd=ck.get("state_dict",ck)
    model.load_state_dict(sd,strict=True);model.eval()
    path=assets/(name+".onnx")
    if name=="rep":
        path.write_bytes((root/"openocr_rec_model.onnx").read_bytes())
    else:
        torch.onnx.export(model,torch.zeros(1,3,48,320),str(path),opset_version=17,dynamo=False,input_names=["input"],output_names=["output"],dynamic_axes={"input":{0:"batch",3:"width"},"output":{0:"batch",1:"steps"}})
    onnx.checker.check_model(str(path))
    sess=session(path)
    results=[]
    for text in samples:
        x=inp(text)
        with torch.no_grad():ref=model(torch.from_numpy(x)).numpy()
        t=time.perf_counter();got=sess.run(None,{sess.get_inputs()[0].name:x})[0];ms=(time.perf_counter()-t)*1000
        assert got.shape==ref.shape and got.shape[2]==len(chars)+2 and np.isfinite(got).all()
        a,b=decode(ref),decode(got)
        assert a==b,(name,text,a,b)
        results.append({"expected":text,"text":b,"width":x.shape[-1],"ms":ms})
    assert sum(v["expected"]==v["text"] for v in results)>=4,(name,results)
    report[name]={"source_sha256":sha(root/fn),"onnx_sha256":sha(path),"bytes":path.stat().st_size,"samples":results}
    del model,sess,sd,ck
os.chdir(root)
with zipfile.ZipFile(root/"previous.apk") as z:
    raw=z.read("assets/yolo26n-text.onnx")
    prior=json.loads((root/"detector-report.json").read_text())
    assert hashlib.sha256(raw).hexdigest()==prior["onnx_sha256"]
    (assets/"yolo26n-text.onnx").write_bytes(raw)
report["detector_sha256"]=sha(assets/"yolo26n-text.onnx")
report["dict_sha256"]=sha(assets/"chars.txt")
(root/"openocr-report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps(report,ensure_ascii=False,indent=2))
