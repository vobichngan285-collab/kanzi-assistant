#!/usr/bin/env python3
"""Approximate #315 Chinese real-scene DETECTOR parameter A/B (not Android SDK).

Uses exact original #315 9.88MB detector model with SDK default BGR input,
minAreaRect, DB threshold mask, box score, pyclipper approximation of unclip,
GT polygon-to-axis-aligned IoU. Single ONNX call per image; only box threshold
changes between schemes. Detection quality != OCR or speech accuracy.

Images/annotations are public Yesianrohn/OCR-Data, ephemeral downloads only.
"""
import collections
import hashlib
import json
import math
from pathlib import Path
import statistics
import sys
import time
import cv2
import numpy as np
import onnxruntime as ort
import pyclipper
from ocr_public_real_scene_ab import get_json,read_img

DATASET="Yesianrohn/OCR-Data"
SLICES={"ReCTS":[(0,12),(101,12)],"RCTW":[(0,12),(135,12)],"ART":[(0,12),(125,12)]}
MODEL_SHA="d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"
BOX_THRESHES=[0.45,0.50,0.60,0.70]
DET_THRESH=0.3
LIMIT_LONG_SIDE=2048 # safety cap for cloud CPU, unlike official 4000 maximum
MATCH_IOU=.4        # overlap proxy for GT polygons -> bounding rectangles

def detector_preprocess(rgb):
    orig_h,orig_w=rgb.shape[:2]
    # This benchmark limits huge web photos for an affordable cloud CPU test;
    # resize preserves aspect and is not an exact end-to-end #315 camera trial.
    ratio=min(1.,LIMIT_LONG_SIDE/max(orig_h,orig_w))
    hh=max(32,round(int(orig_h*ratio)/32)*32)
    ww=max(32,round(int(orig_w*ratio)/32)*32)
    bgr=cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR)
    resized=cv2.resize(bgr,(ww,hh),interpolation=cv2.INTER_LINEAR).astype(np.float32)
    mean=np.array([.485,.456,.406],np.float32)
    std=np.array([.229,.224,.225],np.float32)
    tensor=np.ascontiguousarray(((resized/255.-mean)/std).transpose(2,0,1)[None],dtype=np.float32)
    return tensor,(orig_h,orig_w)


def box_iou(p,g):
    x0=max(p[0],g[0]);y0=max(p[1],g[1])
    x1=min(p[2],g[2]);y1=min(p[3],g[3])
    inter=max(0,x1-x0)*max(0,y1-y0)
    pa=max(0,p[2]-p[0])*max(0,p[3]-p[1])
    ga=max(0,g[2]-g[0])*max(0,g[3]-g[1])
    return inter/max(1e-8,pa+ga-inter)

def rect_score(prob,points):
    h,w=prob.shape
    pp=np.asarray(points,np.int32)
    x0=max(0,min(w-1,int(np.floor(pp[:,0].min()))))
    x1=max(0,min(w-1,int(np.ceil(pp[:,0].max()))))
    y0=max(0,min(h-1,int(np.floor(pp[:,1].min()))))
    y1=max(0,min(h-1,int(np.ceil(pp[:,1].max()))))
    if x1<x0 or y1<y0:return 0
    patch=prob[y0:y1+1,x0:x1+1]
    mask=np.zeros(patch.shape,np.uint8)
    cv2.fillPoly(mask,[np.round(pp-[x0,y0]).astype(np.int32)],1)
    return cv2.mean(patch,mask)[0]

def postprocess(prob,orig_h,orig_w,box_thresh):
    hh,ww=prob.shape
    mask=(prob>DET_THRESH).astype(np.uint8)*255
    contours,_=cv2.findContours(mask,cv2.RETR_LIST,cv2.CHAIN_APPROX_SIMPLE)
    out=[]
    for contour in contours[:3000]:
        if len(contour)<3:continue
        rect=cv2.minAreaRect(contour.astype(np.float32))
        if min(rect[1])<3:continue
        quad=cv2.boxPoints(rect)
        score=rect_score(prob,quad)
        if score<box_thresh:continue
        # Align with SDK unclip's area * 1.5 / perimeter (pyclipper approximation).
        polygon=quad.astype(np.float32)
        area=abs(cv2.contourArea(polygon))
        perimeter=cv2.arcLength(polygon,True)
        if perimeter<1e-6:continue
        delta=area*1.5/perimeter
        offset=pyclipper.PyclipperOffset()
        offset.AddPath([tuple(map(int,v)) for v in polygon],
                       pyclipper.JT_ROUND,pyclipper.ET_CLOSEDPOLYGON)
        expanded=offset.Execute(delta)
        if not expanded:continue
        best=max(expanded,key=lambda p:abs(cv2.contourArea(np.asarray(p,np.float32))))
        new=cv2.minAreaRect(np.asarray(best,dtype=np.float32))
        if min(new[1])<5:continue
        box=cv2.boxPoints(new)
        xx0=max(0,float(box[:,0].min())*orig_w/ww)
        yy0=max(0,float(box[:,1].min())*orig_h/hh)
        xx1=min(orig_w,float(box[:,0].max())*orig_w/ww)
        yy1=min(orig_h,float(box[:,1].max())*orig_h/hh)
        if xx1-xx0<4 or yy1-yy0<4:continue
        out.append([xx0,yy0,xx1,yy1])
    return out

def run():
    model=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
    if hashlib.sha256(model.read_bytes()).hexdigest()!=MODEL_SHA:raise RuntimeError("Not #315 detector")
    opts=ort.SessionOptions()
    opts.intra_op_num_threads=2
    opts.inter_op_num_threads=1
    sess=ort.InferenceSession(str(model),sess_options=opts,providers=["CPUExecutionProvider"])
    name=sess.get_inputs()[0].name
    rows=[]
    for split,requests in SLICES.items():
        for offset,length in requests:
            try:
                data=get_json("https://datasets-server.huggingface.co/rows",params={
                    "dataset":DATASET,"config":"default","split":split,
                    "offset":offset,"length":length})
                for row in data.get("rows",[]):
                    r=row["row"]
                    if (r.get("image") or {}).get("src"):
                        x=read_img((split,row["row_idx"],r["image"]["src"],
                                     r.get("texts",[]),r.get("polygons",[])))
                        if x is not None:rows.append(x)
            except Exception as exc:print("WARN_DET_FETCH",split,offset,str(exc)[:200],flush=True)
    print("DETECTOR_IMAGES_DOWNLOADED",len(rows),flush=True)
    samples=[]
    start=time.monotonic()
    for idx,(split,row_idx,frame,texts,polygons) in enumerate(rows):
        orig_h,orig_w=frame.shape[:2]
        gt=[]
        for text,pp in zip(texts,polygons):
            if not isinstance(text,str) or "###" in text or len(pp)<8:continue
            pts=np.array(pp,dtype=np.float32).reshape(-1,2)
            if not np.isfinite(pts).all():continue
            x0,y0=pts.min(axis=0);x1,y1=pts.max(axis=0)
            if x1-x0<6 or y1-y0<6:continue
            gt.append([float(x0),float(y0),float(x1),float(y1)])
        if not gt:continue
        tensor,_=detector_preprocess(frame)
        t=time.perf_counter()
        preds=sess.run(None,{name:tensor})[0]
        infer=(time.perf_counter()-t)*1000
        prob=preds.reshape(preds.shape[-2:])
        for threshold in BOX_THRESHES:
            tt=time.perf_counter()
            boxes=postprocess(prob,orig_h,orig_w,threshold)
            post=(time.perf_counter()-tt)*1000
            # Greedy one-to-one matching of boxes with GT, no double counting.
            edges=[(box_iou(b,g),i,j) for i,b in enumerate(boxes) for j,g in enumerate(gt)]
            edges.sort(reverse=True)
            used_pred=set();used_gt=set()
            for iou,i,j in edges:
                if iou<MATCH_IOU:break
                if i in used_pred or j in used_gt:continue
                used_pred.add(i);used_gt.add(j)
            samples.append({"split":split,"image":row_idx,"threshold":threshold,
                    "gt":len(gt),"matched":len(used_gt),"predicted":len(boxes),
                    "unmatched_predicted":len(boxes)-len(used_pred),
                    "det_model_ms":round(infer,2),"postprocess_ms":round(post,2)})
        if (idx+1)%12==0:print("DETECTOR_PROGRESS",idx+1,"elapsed_s",round(time.monotonic()-start,1),flush=True)
    if not samples:raise RuntimeError("Detector failed: no images")
    cats=sorted(set(s["split"] for s in samples))+["ALL"]
    summary=[]
    for c in cats:
        for thr in BOX_THRESHES:
            arr=[s for s in samples if s["threshold"]==thr and (c=="ALL" or s["split"]==c)]
            gt=sum(s["gt"] for s in arr);match=sum(s["matched"] for s in arr)
            pred=sum(s["predicted"] for s in arr)
            t=sorted(s["det_model_ms"] for s in arr)
            post=sorted(s["postprocess_ms"] for s in arr)
            row={"dataset":c,"threshold":thr,"images":len(arr),
                 "gt_regions":gt,"matched_regions":match,"detected_boxes":pred,
                 "proxy_recall_iou0.4":round(match/max(1,gt),4),
                 "proxy_precision_iou0.4":round(match/max(1,pred),4),
                 "infer_p95_ms":t[int(.95*(len(t)-1))] if t else None,
                 "post_p95_ms":post[int(.95*(len(post)-1))] if post else None}
            summary.append(row)
            print("DETECTOR_PROXY_AB",json.dumps(row,ensure_ascii=False),flush=True)
    import csv
    with (out/"detector_metrics.csv").open("w",encoding="utf-8-sig",newline="") as fd:
        writer=csv.DictWriter(fd,fieldnames=list(summary[0]))
        writer.writeheader();writer.writerows(summary)
    with (out/"per_image_metrics.csv").open("w",encoding="utf-8-sig",newline="") as fd:
        writer=csv.DictWriter(fd,fieldnames=list(samples[0]))
        writer.writeheader();writer.writerows(samples)
    if len(rows)<20:raise RuntimeError("Insufficient detector scene photos")
    print("REAL_SCENE_DETECTOR_THRESHOLD_AB=PASS",len(rows),flush=True)

if __name__=="__main__":run()
