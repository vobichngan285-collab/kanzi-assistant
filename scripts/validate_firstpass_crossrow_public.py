#!/usr/bin/env python3
"""Independent public photo trigger audit of a frozen cross-row first-pass rule.
Read-only, NO uploaded private photos, NO rewriting OCR text or candidate scoring by GT.
Model: SHA-fixed PP-OCRv6 Small detection. 36 different street-scene pictures +
8 different vertical archival pages (indices 80..87).
"""
import csv,io,json,zipfile,collections,hashlib
from pathlib import Path
import numpy as np,cv2,onnxruntime as ort
from PIL import Image
import ocr_box_value_deep_dive as sdk
MODEL=Path("/tmp/firstpass-public/models/det.onnx")
VERTICAL=Path("/tmp/firstpass-public/vertical.zip")
OUT=Path("/tmp/firstpass-public/out")
OUT.mkdir(parents=True,exist_ok=True)
assert hashlib.sha256(MODEL.read_bytes()).hexdigest()=="d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"
opt=ort.SessionOptions(); opt.intra_op_num_threads=2;opt.inter_op_num_threads=1
det=ort.InferenceSession(str(MODEL),opt,providers=["CPUExecutionProvider"])
sdk.SLICES={k:[(900,12)] for k in ("ART","RCTW","ReCTS")}
sdk.THRESHOLDS=[.45];sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40

def group(values):
    bins=[]
    for v in sorted(values):
        if bins and abs(v-bins[-1][-1])<=14:bins[-1].append(v)
        else:bins.append([v])
    return [(sum(x)/len(x),len(x)) for x in bins]

def process(boxes,width,height):
    data=[]
    for i,b in enumerate(boxes):
        p=np.asarray(b["quad"],np.float32).reshape(-1,2)
        x1,x2=p[:,0].min(),p[:,0].max();y1,y2=p[:,1].min(),p[:,1].max()
        w=float(np.linalg.norm(p[1]-p[0]));h=float(np.linalg.norm(p[3]-p[0]))
        data.append(dict(id=i,x1=x1,x2=x2,y1=y1,y2=y2,cx=(x1+x2)/2,cy=(y1+y2)/2,w=w,h=h))
    def short(q):return q["h"]<60 and q["w"]<115
    suspects=[]
    for t in data:
        if not (t["h"]/max(1.,t["w"])>2.4 and 60<t["h"]<260 and t["w"]<70):continue
        centers=group([q["cy"] for q in data if q["id"]!=t["id"] and short(q)
              and t["x2"]+4<q["x1"]<t["x2"]+210
              and t["y1"]+4<q["cy"]<t["y2"]-4])
        if len(centers)>=2 and all(y[0]-x[0]>=20 for x,y in zip(centers,centers[1:])):suspects.append(t)
    if len(suspects)<2:return None
    plans=[]
    for i,a in enumerate(suspects):
      for b in suspects[i+1:]:
        if abs(a["cx"]-b["cx"])<max(65,1.8*max(a["w"],b["w"])):continue
        overlap=min(a["y2"],b["y2"])-max(a["y1"],b["y1"])
        if overlap<.60*min(a["h"],b["h"]):continue
        x1=max(0,int(np.floor(min(a["x1"],b["x1"])-6)))
        x2=min(width,int(np.ceil(max(a["x2"],b["x2"])+9)))
        y1=min(a["y1"],b["y1"]);y2=max(a["y2"],b["y2"])
        if x2-x1>230 or y2-y1>270:continue
        cs=group([q["cy"] for q in data if q["id"] not in (a["id"],b["id"])
            and short(q) and x1<=q["cx"]<=x2+10 and y1-8<q["cy"]<y2+38])
        if not 3<=len(cs)<=8:continue
        if not all(22<=y[0]-x[0]<=52 for x,y in zip(cs,cs[1:])):continue
        to_remove=[q["id"] for q in data if q["id"] in (a["id"],b["id"]) or
            (short(q) and x1<=q["cx"]<=x2+10 and any(abs(q["cy"]-cy)<=19 for cy,n in cs))]
        if not 4<=len(to_remove)<=24:continue
        plans.append((a["id"],b["id"],len(cs),len(to_remove)))
    if len(plans)!=1:return None
    return plans[0]

def det_one(rgb):
    h,w=rgb.shape[:2]
    t=sdk.det_preprocess(rgb)
    raw=det.run(None,{det.get_inputs()[0].name:t})[0]
    boxes=[b for b in sdk.detections(raw.reshape(raw.shape[-2:]),h,w) if b["score"]>=.45]
    return len(boxes),process(boxes,w,h)
rows=[]
photos=sdk.collect_images()
assert len(photos)==36 and {(sp,idx) for sp,idx,*_ in photos}=={(sp,k) for sp in ("ART","RCTW","ReCTS") for k in range(900,912)}, "Public street-photo cohort incomplete"
for sp,idx,rgb,labels,polys in photos:
    count,plan=det_one(rgb)
    rows.append(dict(dataset="street",source=sp,index=idx,original_boxes=count,
                     split=1 if plan else 0,new_rows=plan[2] if plan else 0))
    print("INDEPENDENT_STREET_REAL_DET",sp,idx,"box",count,"split",bool(plan),flush=True)
with zipfile.ZipFile(VERTICAL) as z:
    imgs=sorted(n for n in z.namelist() if n.lower().endswith((".jpg",".jpeg",".png")))
    assert len(imgs)==791,("vertical image archive changed",len(imgs))
    for i in range(80,88):
        rgb=np.asarray(Image.open(io.BytesIO(z.read(imgs[i]))).convert("RGB"))
        count,plan=det_one(rgb)
        rows.append(dict(dataset="vertical",source="AlroWilde",index=i,original_boxes=count,
                         split=1 if plan else 0,new_rows=plan[2] if plan else 0))
        print("INDEPENDENT_VERTICAL_REAL_DET",i,"box",count,"split",bool(plan),flush=True)
assert len(rows)==44
s={"status":"completed","new_public_photos":44,"street_new":36,"vertical_new":8,
   "total_boxes":sum(r["original_boxes"] for r in rows),
   "trigger_street":sum(r["split"] for r in rows if r["dataset"]=="street"),
   "trigger_vertical":sum(r["split"] for r in rows if r["dataset"]=="vertical"),
   "rule":"frozen 2 narrow short columns + 3-8 aligned horizontal anchors; no OCR labels involved",
   "limitations":["These images had not been used to choose the rule; the user photos cannot go to public CI.",
       "A no-trigger is evidence against false activation on these pictures, not evidence of recognition improvement.",
       "Python SDK-like DET boxes are not byte-for-byte Android outputs.",
       "The 8 vertical pages are archival public images, not phone camera scenes."]}
(OUT/"summary.json").write_text(json.dumps(s,ensure_ascii=False,indent=2),encoding="utf-8")
with (OUT/"per_image_numeric.csv").open("w",newline="",encoding="utf-8-sig") as f:
    writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
print("INDEPENDENT_CROSSROW_PUBLIC_VALIDATION_COMPLETE",json.dumps(s,ensure_ascii=False),flush=True)
