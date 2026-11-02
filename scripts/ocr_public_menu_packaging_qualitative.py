#!/usr/bin/env python3
"""Qualitative-only public image OCR pilot: a Chinese menu board and snack nutrition label.

These are independently sourced web photos, NOT licensed benchmark gold labels.
Annotations are manually estimated from a 512px preview, so printed results
must NOT be pooled into CER stats or described as verified accuracy.
Images are never committed or uploaded as artifacts.
"""
import io,sys,hashlib,collections
import requests
import numpy as np,cv2,onnxruntime as ort,yaml
from PIL import Image
from ocr_public_real_scene_ab import crop_minrect, local_clahe, recognize, norm, edits

IMAGES=[
 {"group":"Chinese noodle restaurant wall menu",
  "url":"https://assets.st-note.com/img/1722693842975-GFTvw8a4jT.jpg",
  "regions":[
     ("价目表",(151,62,295,96)),
     ("红汤面底 8元",(380,115,490,145)),
     ("白汤面底 8元",(379,143,489,173)),
     ("面底不单点",(379,174,490,205)),
     ("点心小吃",(182,365,216,493)),
   ]},
 {"group":"Taiwan potato-chips nutrition facts, Traditional Chinese",
  "url":"https://i5.walmartimages.com/asr/c7cd8996-fb95-4320-826b-0631d877d79b.5d299bf006c43e29ff75eacfebfd0686.jpeg?odnBg=FFFFFF&odnHeight=768&odnWidth=768",
  "regions":[
     ("營養標示",(186,33,330,69)),
     ("每一份量",(105,63,255,95)),
     ("25公克",(263,61,355,93)),
     ("本包裝含",(107,91,257,124)),
     ("3份",(269,88,330,124)),
     ("熱量",(110,171,208,204)),
     ("141大卡",(263,164,355,197)),
     ("蛋白質",(111,191,210,221)),
     ("1.8公克",(263,193,358,221)),
     ("脂肪",(111,219,210,249)),
     ("8.8公克",(262,215,358,248)),
     ("碳水化合物",(112,282,258,316)),
     ("13.8公克",(263,280,369,319)),
     ("鈉",(110,330,173,364)),
     ("146毫克",(260,328,368,360)),
   ]},
]
def fetch(url):
    r=requests.get(url,timeout=45,headers={"User-Agent":"KanziResearch/1.0"})
    r.raise_for_status()
    return np.asarray(Image.open(io.BytesIO(r.content)).convert("RGB"))

def crop(raw,box):
    h,w=raw.shape[:2]
    x0,y0,x1,y1=box
    x0=int(round(x0*w/512));x1=int(round(x1*w/512))
    y0=int(round(y0*h/512));y1=int(round(y1*h/512))
    x0=max(0,min(w-1,x0));x1=max(x0+1,min(w,x1))
    y0=max(0,min(h-1,y0));y1=max(y0+1,min(h,y1))
    return np.ascontiguousarray(raw[y0:y1,x0:x1])

def main():
    model=sys.argv[1]; yml=sys.argv[2]
    assert hashlib.sha256(open(model,"rb").read()).hexdigest()=="5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
    chars=list(yaml.safe_load(open(yml))["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    options=ort.SessionOptions();options.intra_op_num_threads=2
    sess=ort.InferenceSession(model,sess_options=options,providers=["CPUExecutionProvider"])
    n=0;fail=0
    for group in IMAGES:
        try:frame=fetch(group["url"])
        except Exception as exc:
            print("SOURCE_DOWNLOAD_FAILED",group["group"],str(exc)[:200],flush=True)
            fail+=1;continue
        print("CURATED_SOURCE",group["group"],"image",frame.shape[1],"x",frame.shape[0],flush=True)
        for truth,box in group["regions"]:
            roi=crop(frame,box)
            if min(roi.shape[:2])<4:continue
            padded=cv2.copyMakeBorder(roi, max(1,int(.03*roi.shape[0])),
                max(1,int(.03*roi.shape[0])),max(1,int(.03*roi.shape[0])),
                max(1,int(.03*roi.shape[0])),cv2.BORDER_REPLICATE)
            data={}
            for method,img in [("baseline_box",roi),("border_pad",padded),
                               ("local_clahe",local_clahe(roi))]:
                pred,conf,ms=recognize(sess,sess.get_inputs()[0].name,img,chars)
                data[method]=(pred,round(conf,3),round(ms,2))
            n+=1
            print("CURATED_QUALITATIVE",group["group"],"expected_preview",repr(truth),
                  "variants",repr(data),flush=True)
    print("CURATED_PUBLIC_IMAGE_REVIEW=PASS",n,"download_failed",fail,
          "NO_GOLD_ACCURACY_CLAIM",flush=True)
    if n<10:raise RuntimeError("Too few public photo regions downloaded for qualitative pilot")
if __name__=="__main__":main()
