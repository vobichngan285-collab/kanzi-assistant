"""Export the official text-only checkpoint; verify end-to-end OBB contract."""
import hashlib,json,shutil
from pathlib import Path
import numpy as np
import onnxruntime as ort
from PIL import Image,ImageDraw,ImageFont
from ultralytics import YOLO
import ultralytics
assert ultralytics.__version__=="8.4.212"
model=YOLO("yolo26n-text.pt")
assert model.task=="obb" and len(model.names)==1 and "text" in str(model.names).lower(),model.names
out=Path(model.export(format="onnx",imgsz=1280,device="cpu",dynamic=False,simplify=False,opset=17,batch=1,nms=False,max_det=300))
sess=ort.InferenceSession(str(out),providers=["CPUExecutionProvider"])
assert sess.get_inputs()[0].shape==[1,3,1280,1280]
im=Image.new("RGB",(1280,1280),(114,114,114));d=ImageDraw.Draw(im)
d.rectangle((350,260,930,1010),fill="white")
import subprocess
font=ImageFont.truetype(subprocess.check_output(["fc-match","-f","%{file}","Noto Sans CJK SC"],text=True),44)
for i,s in enumerate(["价目表 PRICE LIST","中医推拿 90元","精油开背 120元","肠胃调理 120元","艾灸养生 120元","经络刮痧 60元","拔罐热敷 40元","充1000送200"]):d.text((375,285+80*i),s,font=font,fill="black")
arr=np.asarray(im).transpose(2,0,1)[None].astype(np.float32)/255
raw=sess.run(None,{sess.get_inputs()[0].name:arr})[0]
assert raw.ndim==3 and raw.shape[0]==1 and raw.shape[2]==7 and np.isfinite(raw).all(),raw.shape
boxes=raw[0][raw[0,:,4]>=.25]
assert len(boxes)>=5 and np.all(boxes[:,5]==0) and np.all(boxes[:,2:4]>0)
assert np.count_nonzero((boxes[:,0]>=350)&(boxes[:,0]<=930)&(boxes[:,1]>=260)&(boxes[:,1]<=1010))>=5
# Validate actual ONNX OBB output coordinates against the checkpoint prediction.
pred=model.predict(np.asarray(im)[:,:,::-1].copy(),imgsz=1280,conf=.25,device="cpu",rect=False,verbose=False,max_det=300)[0].obb
ref=pred.xywhr.cpu().numpy()
assert len(ref)>=5
for box in boxes:
    assert np.min(np.linalg.norm(ref[:,:2]-box[:2],axis=1))<4,box
target=Path("gemma-vision-lab/app/src/main/assets");target.mkdir(parents=True,exist_ok=True)
shutil.copyfile(out,target/"yolo26n-text.onnx")
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
report=dict(ultralytics=ultralytics.__version__,source_checkpoint_sha256=sha("yolo26n-text.pt"),onnx_sha256=sha(out),onnx_bytes=out.stat().st_size,shape=list(raw.shape),synthetic_boxes=len(boxes),names=model.names)
Path("detector-report.json").write_text(json.dumps(report,indent=2))
print(report)
