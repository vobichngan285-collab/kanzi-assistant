#!/usr/bin/env python3
"""Independent synthetic recognition-only A/B. NOT a camera or full detector benchmark."""
import csv, sys, time
from pathlib import Path
import numpy as np
import cv2
import onnxruntime as ort
import yaml
from PIL import Image, ImageDraw, ImageFont
from check_ocr_chinese_samples import choose_font, prepare, decode, cer

root=Path(sys.argv[1]); out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
model=root/"models"; cfg=yaml.safe_load((model/"rec/inference.yml").read_text())
chars=list(cfg["PostProcess"]["character_dict"])
if chars[-1]!=" ": chars.append(" ")
sess=ort.InferenceSession(str(model/"rec/inference.onnx"),providers=["CPUExecutionProvider"])
name=sess.get_inputs()[0].name
font=choose_font()
texts=["中国文字识别测试","生产日期保质期净含量","看字助手北京上海","食品生产日期质量安全检验结果符合国家标准要求","简爱英语文学作品","125毫克蛋白质200毫升"]
rows=[]
for size in (14,20,28,54):
 for case,truth in enumerate(texts):
  f=ImageFont.truetype(font,size)
  tmp=Image.new("RGB",(2000,100),"white");d=ImageDraw.Draw(tmp);b=d.textbbox((0,0),truth,font=f)
  img=Image.new("RGB",(b[2]-b[0]+8,size+8),"white")
  ImageDraw.Draw(img).text((4-b[0],4-b[1]),truth,font=f,fill="black")
  base=np.array(img)
  for margin in (0,5,10,15):
   pad=max(0,round(size*margin/100))
   padded=cv2.copyMakeBorder(base,pad,pad,pad,pad,cv2.BORDER_CONSTANT,value=(255,255,255))
   for treatment in ("original","upscale2x","bilateral"):
    frame=padded
    if treatment=="upscale2x": frame=cv2.resize(frame,None,fx=2,fy=2,interpolation=cv2.INTER_CUBIC)
    elif treatment=="bilateral": frame=cv2.bilateralFilter(frame,7,35,35)
    tensor=prepare(frame,"BGR")
    t=time.perf_counter();logits=sess.run(None,{name:tensor})[0];ms=(time.perf_counter()-t)*1000
    actual,conf=decode(logits,chars)
    rows.append(dict(font_px=size,case=case,truth=truth,margin_pct=margin,treatment=treatment,prediction=actual,cer=cer(truth,actual),confidence=conf,inference_ms=round(ms,2)))
with (out/"results.csv").open("w",newline="",encoding="utf-8-sig") as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
for treatment in ("original","upscale2x","bilateral"):
 subset=[r for r in rows if r["treatment"]==treatment]
 print("RESULT",treatment,"mean_CER",round(sum(r["cer"] for r in subset)/len(subset),4),"mean_inference_ms",round(sum(r["inference_ms"] for r in subset)/len(subset),2),flush=True)
print("AB_SYNTHETIC_COMPLETE=PASS cases=",len(rows),flush=True)
