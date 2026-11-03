#!/usr/bin/env python3
"""Real-photo layout localization feasibility: no OCR recognizer, no APK.
This is a first-stage experiment, NOT the A/B/C CER comparison.
"""
import argparse, csv, io, json, time
from pathlib import Path
import cv2, numpy as np, onnxruntime as ort, requests
from PIL import Image

def photos():
    session=requests.Session()
    for cohort,offset in (("DEV",0),("HOLDOUT",500)):
        for split in ("ReCTS","RCTW","ART"):
            response=session.get("https://datasets-server.huggingface.co/rows",
                params={"dataset":"Yesianrohn/OCR-Data","config":"default",
                        "split":split,"offset":offset,"length":12},timeout=90)
            response.raise_for_status()
            for entry in response.json()["rows"]:
                row=entry["row"]
                src=(row.get("image") or {}).get("src")
                if src:
                    yield cohort,split,entry["row_idx"],src,row.get("texts",[]),row.get("polygons",[])

def detect(sess,im):
    h,w=im.shape[:2]
    scale=min(480/w,480/h)
    rw,rh=max(1,round(w*scale)),max(1,round(h*scale))
    canvas=np.zeros((480,480,3),dtype=np.uint8)
    canvas[:rh,:rw]=cv2.resize(im,(rw,rh))
    rgb=cv2.cvtColor(canvas,cv2.COLOR_BGR2RGB).astype(np.float32)/255.
    # Explicitly a diagnostic preprocessing assumption; verify against exporter before accuracy claims.
    chw=np.ascontiguousarray(((rgb-np.array([.485,.456,.406],dtype=np.float32))/
         np.array([.229,.224,.225],dtype=np.float32)).transpose(2,0,1)[None])
    outputs=sess.run(None,{"image":chw,"scale_factor":np.array([[1/scale,1/scale]],np.float32)})
    raw=np.asarray(outputs[0]); n=int(np.asarray(outputs[1]).reshape(-1)[0])
    return raw[:n],(w,h)

def regions(detections,size,threshold):
    w,h=size; boxes=[]
    for det in detections:
        # Paddle Detection layout postprocess commonly returns class, score, x1,y1,x2,y2.
        cls,score,x1,y1,x2,y2=map(float,det)
        if score<threshold or not np.isfinite(det).all():continue
        x1,x2=sorted((max(0,min(w,x1)),max(0,min(w,x2))))
        y1,y2=sorted((max(0,min(h,y1)),max(0,min(h,y2))))
        if x2>x1 and y2>y1:boxes.append((int(cls),score,x1,y1,x2,y2))
    return boxes

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--model",required=True);ap.add_argument("--out",required=True)
    a=ap.parse_args();out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    sess=ort.InferenceSession(a.model,providers=["CPUExecutionProvider"])
    rows=[]; errors=[]
    for cohort,split,idx,url,texts,polys in photos():
        try:
            response=requests.get(url,timeout=55);response.raise_for_status()
            im=cv2.imdecode(np.frombuffer(response.content,np.uint8),cv2.IMREAD_COLOR)
            if im is None:raise ValueError("decode failed")
            start=time.perf_counter();raw,size=detect(sess,im)
            ms=round((time.perf_counter()-start)*1000,2)
            found=regions(raw,size,.53)
            gt=0; covered=0
            for truth,polygon in zip(texts,polys):
                if not isinstance(truth,str) or truth=="###":continue
                try:
                    p=np.asarray(polygon,dtype=float).reshape(-1,2)
                    if len(p)<4 or not np.isfinite(p).all():continue
                except (ValueError,TypeError):continue
                x1,y1=p.min(axis=0);x2,y2=p.max(axis=0)
                if x2<=x1 or y2<=y1:continue
                gt+=1
                cx,cy=(x1+x2)/2,(y1+y2)/2
                covered+=int(any(b[2]<=cx<=b[4] and b[3]<=cy<=b[5] for b in found))
            rows.append(dict(cohort=cohort,split=split,image=idx,gt_lines=gt,
                gt_centers_inside_layout=covered,layout_regions=len(found),
                layout_ms=ms))
            print("LAYOUT_REAL_PHOTO",json.dumps(rows[-1]),flush=True)
        except Exception as exc:
            errors.append(f"{cohort}/{split}/{idx}: {exc}")
            print("LAYOUT_PHOTO_ERROR",errors[-1],flush=True)
    if len(rows)<60:raise RuntimeError(f"Only {len(rows)} valid photos; errors={errors[:5]}")
    with (out/"layout_coverage_per_photo.csv").open("w",newline="") as f:
        wr=csv.DictWriter(f,fieldnames=list(rows[0]));wr.writeheader();wr.writerows(rows)
    summaries=[]
    for cohort in ("DEV","HOLDOUT"):
        group=[x for x in rows if x["cohort"]==cohort]
        gt=sum(x["gt_lines"] for x in group)
        summaries.append(dict(cohort=cohort,photos=len(group),gt_lines=gt,
            layout_center_coverage=round(sum(x["gt_centers_inside_layout"] for x in group)/max(1,gt),5),
            avg_regions=round(sum(x["layout_regions"] for x in group)/len(group),2),
            mean_layout_ms=round(sum(x["layout_ms"] for x in group)/len(group),2)))
    (out/"summary.json").write_text(json.dumps(summaries,indent=2))
    (out/"limitations.txt").write_text(
        "Layout center coverage is NOT OCR DET recall or CER. "
        "Layout model preprocessing is provisional and needs exporter parity. "
        "No local Small DET or Small REC ran in this first-stage diagnostic. "
        "No photos or model binaries stored in artifacts. "
        "No Android performance or APK test.\n")
    (out/"errors.txt").write_text("\n".join(errors))
    for x in summaries:print("LAYOUT_DIAGNOSTIC_SUMMARY",json.dumps(x),flush=True)
    print("LAYOUT_FIRST_STAGE_COMPLETED",len(rows),flush=True)

if __name__=="__main__":main()
