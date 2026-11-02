#!/usr/bin/env python3
"""Test whether re-cropping from a full-resolution source can recover clipped glyphs.
Synthetic recognition-only test; does not claim Android detector performance.
"""
import csv,sys,time
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort,yaml
from PIL import Image,ImageDraw,ImageFont
from check_ocr_chinese_samples import choose_font,prepare,decode,cer

root=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
model=root/"models/rec"
chars=list(yaml.safe_load((model/"inference.yml").read_text())["PostProcess"]["character_dict"])
if chars[-1]!=" ":chars.append(" ")
session=ort.InferenceSession(str(model/"inference.onnx"),providers=["CPUExecutionProvider"])
input_name=session.get_inputs()[0].name
font=choose_font()
texts=["生产日期保质期净含量","简爱英语文学作品","食品生产日期质量安全检验结果符合国家标准要求","看字助手北京上海"]
rows=[]
for px in (16,24,36):
 for truth in texts:
  ft=ImageFont.truetype(font,px)
  box=ImageDraw.Draw(Image.new("RGB",(2400,120))).textbbox((0,0),truth,font=ft)
  # Full source has 30px white border on each side; we can recover lost text only from this source.
  pad=30
  full=Image.new("RGB",(box[2]-box[0]+2*pad,px+2*pad),"white")
  ImageDraw.Draw(full).text((pad-box[0],pad-box[1]),truth,font=ft,fill="black")
  full=np.asarray(full)
  left,right=pad,pad+box[2]-box[0]
  top,bottom=pad,pad+px
  for clip in (0,2,4,8):
   # Deliberately move ROI inward on the left: represents a clipped detector box.
   x0=left+clip
   for method in ("clipped","pad_clipped_roi","recrop_full_source"):
    if method=="clipped": frame=full[top:bottom,x0:right]
    elif method=="pad_clipped_roi":
     frame=cv2.copyMakeBorder(full[top:bottom,x0:right],4,4,4,4,cv2.BORDER_CONSTANT,value=(255,255,255))
    else:
     # Expand original coordinates into the full source; actual missing pixels become available.
     frame=full[max(0,top-4):min(full.shape[0],bottom+4),max(0,x0-12):min(full.shape[1],right+4)]
    inp=prepare(frame,"BGR")
    t=time.perf_counter();logits=session.run(None,{input_name:inp})[0];elapsed=(time.perf_counter()-t)*1000
    pred,conf=decode(logits,chars)
    rows.append(dict(font_px=px,truth=truth,clip_px=clip,method=method,prediction=pred,cer=round(cer(truth,pred),5),confidence=round(conf,5),inference_ms=round(elapsed,2)))
with (out/"roi_recovery.csv").open("w",encoding="utf-8-sig",newline="") as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
for clip in (0,2,4,8):
 for method in ("clipped","pad_clipped_roi","recrop_full_source"):
  sub=[r for r in rows if r["clip_px"]==clip and r["method"]==method]
  print("ROI_RESULT",f"clip={clip}",method,"mean_CER",round(sum(r["cer"] for r in sub)/len(sub),5),"mean_ms",round(sum(r["inference_ms"] for r in sub)/len(sub),2),flush=True)
print("ROI_RECOVERY_COMPLETE=PASS",len(rows),flush=True)
