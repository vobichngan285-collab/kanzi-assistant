#!/usr/bin/env python3
"""Isolation study: layout-assisted *local Small DET* + Small REC vs original Small.
Only one recognizer. Fixed real photos and GT; no Android modifications or APK.

Evaluation is an IoU-matched coverage-aware character-error proxy: missed GT
characters count as deletions; unmatched predictions are reported separately.
Layout region models are NOT themselves word/line detectors.
"""
import argparse, csv, hashlib, json, math, time
from collections import Counter
from pathlib import Path
import cv2
import numpy as np
import onnxruntime as ort
import yaml
import ocr_box_value_deep_dive as sdk
from layout_realphoto_diagnostic import detect as layout_detect, regions as layout_regions

DET_SHA="d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"
REC_SHA="5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
REC_YML_SHA="ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
LAYOUT_SHA="33688dbee1c23e34b81777e97cb428eb40f24b242c02b5f623484959e830aec8"
IOU=.40
MAX_ROIS=2
MAX_BOXES=150

def checked(path,sha):
    p=Path(path)
    if hashlib.sha256(p.read_bytes()).hexdigest()!=sha:
        raise ValueError("Model SHA mismatch: "+str(p))
    return p

def models(root):
    root=Path(root)
    o=ort.SessionOptions()
    o.intra_op_num_threads=2
    o.inter_op_num_threads=1
    d=ort.InferenceSession(str(checked(root/"det.onnx",DET_SHA)),o,providers=["CPUExecutionProvider"])
    r=ort.InferenceSession(str(checked(root/"small_rec.onnx",REC_SHA)),o,providers=["CPUExecutionProvider"])
    l=ort.InferenceSession(str(checked(root/"layout.onnx",LAYOUT_SHA)),o,providers=["CPUExecutionProvider"])
    cfg=yaml.safe_load(checked(root/"small_rec.yml",REC_YML_SHA).read_text())
    chars=list(cfg["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    return d,r,l,chars

def small_det(det,rgb):
    t=time.perf_counter()
    tensor=sdk.det_preprocess(rgb)
    heat=det.run(None,{det.get_inputs()[0].name:tensor})[0]
    boxes=sdk.detections(heat.reshape(heat.shape[-2:]),rgb.shape[0],rgb.shape[1])
    return [b for b in boxes if b["score"]>=.45],(time.perf_counter()-t)*1000

def gt_items(texts,polys):
    out=[]
    for label,vertices in zip(texts,polys):
        if not isinstance(label,str) or "###" in label:continue
        try:
            xy=np.asarray(vertices,np.float32).reshape(-1,2)
        except (ValueError,TypeError):continue
        if len(xy)<4 or not np.isfinite(xy).all():continue
        box=sdk.bbox_from_quad(xy)
        if box[2]-box[0]<6 or box[3]-box[1]<6:continue
        norm=sdk.normalize(label)
        if not norm:continue
        out.append(dict(box=box,normal=norm))
    return out

def matched(boxes,gt):
    edges=[]
    for i,b in enumerate(boxes):
        for j,g in enumerate(gt):
            overlap=sdk.box_iou(b["bbox"],g["box"])
            if overlap>=IOU:edges.append((overlap,i,j))
    edges.sort(reverse=True)
    used_b=set();used_g=set();pairs={}
    for overlap,i,j in edges:
        if i in used_b or j in used_g:continue
        used_b.add(i);used_g.add(j);pairs[i]=j
    return pairs

def duplicate(box,other):
    bb=box["bbox"];oo=other["bbox"]
    intersection=max(0,min(bb[2],oo[2])-max(bb[0],oo[0]))*max(0,min(bb[3],oo[3])-max(bb[1],oo[1]))
    minarea=min(max(1,(bb[2]-bb[0])*(bb[3]-bb[1])),
                max(1,(oo[2]-oo[0])*(oo[3]-oo[1])))
    return sdk.box_iou(bb,oo)>=.45 or intersection/minarea>=.72

def layout_local_det(layout,det,rgb,original):
    h,w=rgb.shape[:2]
    raw,size=layout_detect(layout,cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR))
    layouts=layout_regions(raw,size,.53)
    # Fixed policy: score-ranked, max two regions per frame, min 1% of page.
    options=[]
    for category,score,left,top,right,bottom in layouts:
        area=(right-left)*(bottom-top)/max(1,h*w)
        if .01<=area<=.98:options.append((score,category,left,top,right,bottom,area))
    options.sort(reverse=True)
    extras=[];roi_count=0;extra_det_ms=0
    for score,cat,x1,y1,x2,y2,area in options[:MAX_ROIS]:
        pad_x=max(3,int(.02*(x2-x1)));pad_y=max(3,int(.02*(y2-y1)))
        xx1=max(0,int(math.floor(x1))-pad_x);xx2=min(w,int(math.ceil(x2))+pad_x)
        yy1=max(0,int(math.floor(y1))-pad_y);yy2=min(h,int(math.ceil(y2))+pad_y)
        if xx2-xx1<80 or yy2-yy1<60:continue
        if (xx2-xx1)*(yy2-yy1)>.99*h*w:continue
        roi_count+=1
        roi=rgb[yy1:yy2,xx1:xx2]
        boxes,ms=small_det(det,roi);extra_det_ms+=ms
        for b in boxes:
            b=dict(b)
            q=b["quad"].copy()
            q[:,0]+=xx1;q[:,1]+=yy1
            b["quad"]=q
            b["bbox"]=sdk.bbox_from_quad(q)
            if any(duplicate(b,e) for e in original+extras):
                continue
            extras.append(b)
    return extras,layouts,roi_count,extra_det_ms

def recognize(rec,chars,rgb,box):
    crop=sdk.crop_box(rgb,box)
    if crop is None:return "",0.,False
    # App's official recognition img_mode:BGR; both A and B use identical BGR.
    bgr=cv2.cvtColor(crop,cv2.COLOR_RGB2BGR)
    pred,conf,ms=sdk.rec(rec,rec.get_inputs()[0].name,chars,bgr)
    return pred,ms,True

def score(boxes,predictions,gt):
    pairs=matched(boxes,gt)
    matched_gt=set(pairs.values())
    gt_chars=sum(len(g["normal"]) for g in gt)
    missing_chars=sum(len(g["normal"]) for j,g in enumerate(gt) if j not in matched_gt)
    edits=sum(sdk.distance(gt[j]["normal"],sdk.normalize(predictions[i]))
              for i,j in pairs.items() if i in predictions)
    # Any failed crop/recognition yields deletion rather than being silently ignored.
    edits+=sum(len(gt[j]["normal"]) for i,j in pairs.items() if i not in predictions)
    unpaired_with_text=sum(bool(predictions.get(i,"").strip()) for i in range(len(boxes)) if i not in pairs)
    return dict(matched_gt=len(pairs),gt_lines=len(gt),gt_chars=gt_chars,
                missed_chars=missing_chars,edits=edits,
                coverage_errors=edits+missing_chars,
                unmatched_text_boxes=unpaired_with_text)

def aggregate(rows,cohort,mode):
    filtered=[x for x in rows if x["cohort"]==cohort]
    gt=sum(x["A_gt_lines"] for x in filtered)
    chars=sum(x["A_gt_chars"] for x in filtered)
    def s(field):return sum(x[mode+"_"+field] for x in filtered)
    return dict(cohort=cohort,mode=mode,photos=len(filtered),gt_lines=gt,
                matched_lines=s("matched_gt"),line_recall=round(s("matched_gt")/max(1,gt),5),
                coverage_aware_CER_proxy=round(s("coverage_errors")/max(1,chars),5),
                missed_gt_characters=s("missed_chars"),
                unmatched_recognized_boxes=s("unmatched_text_boxes"),
                matched_char_edits=s("edits"),
                avg_full_pipeline_ms=round(s("ms")/len(filtered),2),
                avg_extra_det_calls=round(s("extra_det_calls")/len(filtered),3),
                avg_new_boxes=round(s("new_boxes")/len(filtered),3))

def main():
    p=argparse.ArgumentParser();p.add_argument("--models",required=True)
    p.add_argument("--out",required=True);a=p.parse_args()
    out=Path(a.out);out.mkdir(exist_ok=True,parents=True)
    sdk.SLICES={x:[(0,12),(500,12)] for x in ("ReCTS","RCTW","ART")}
    sdk.THRESHOLDS=[.45];sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40
    det,rec,layout,chars=models(a.models)
    images=sdk.collect_images()
    if len(images)<60:raise RuntimeError("Too few real images: "+str(len(images)))
    records=[];category_counts=Counter()
    for count,(split,index,rgb,texts,polys) in enumerate(images,1):
        cohort="DEV" if index<100 else "HOLDOUT"
        gt=gt_items(texts,polys)
        if not gt:continue
        start=time.perf_counter()
        initial,first_det_ms=small_det(det,rgb)
        initial=initial[:MAX_BOXES]
        recognized={}
        for i,b in enumerate(initial):
            pred,ms,good=recognize(rec,chars,rgb,b)
            if good:recognized[i]=pred
        baseline_ms=(time.perf_counter()-start)*1000
        base=score(initial,recognized,gt)
        start2=time.perf_counter()
        added,layouts,n_roi,extra_det_ms=layout_local_det(layout,det,rgb,initial)
        added=added[:max(0,MAX_BOXES-len(initial))]
        for region in layouts:category_counts[region[0]]+=1
        combined=initial+added
        for i in range(len(initial),len(combined)):
            pred,ms,good=recognize(rec,chars,rgb,combined[i])
            if good:recognized[i]=pred
        added_ms=(time.perf_counter()-start2)*1000
        visual=score(combined,recognized,gt)
        row=dict(cohort=cohort,split=split,image=index,layout_regions=len(layouts),
                 layout_rois=n_roi)
        for mode,result in (("A",base),("B",visual)):
            for key,value in result.items():row[mode+"_"+key]=value
            row[mode+"_ms"]=round(baseline_ms+(added_ms if mode=="B" else 0),2)
            row[mode+"_extra_det_calls"]=n_roi if mode=="B" else 0
            row[mode+"_new_boxes"]=len(added) if mode=="B" else 0
        records.append(row)
        if count%8==0:
            print("LAYOUT_SMALL_PROGRESS",count,len(images),
                  "gt",sum(r["A_gt_lines"] for r in records),
                  "added",sum(r["B_new_boxes"] for r in records),flush=True)
    if len(records)<55:raise RuntimeError("Insufficient valid scored photos: "+str(len(records)))
    result=[aggregate(records,cohort,mode)
            for cohort in ("DEV","HOLDOUT") for mode in ("A","B")]
    for x in result:print("LAYOUT_SMALL_FULLFRAME_RESULT",json.dumps(x),flush=True)
    delta=[x for x in records if x["cohort"]=="HOLDOUT"]
    paired=dict(
       holdout_images=len(delta),
       improved_images=sum(x["B_coverage_errors"]<x["A_coverage_errors"] for x in delta),
       worsened_images=sum(x["B_coverage_errors"]>x["A_coverage_errors"] for x in delta),
       unchanged_images=sum(x["B_coverage_errors"]==x["A_coverage_errors"] for x in delta),
       layout_category_counts=dict(category_counts),
       layout_regions=sum(x["layout_regions"] for x in records),
       local_det_calls=sum(x["layout_rois"] for x in records))
    (out/"paired.json").write_text(json.dumps(paired,indent=2,ensure_ascii=False))
    (out/"summary.json").write_text(json.dumps(result,indent=2,ensure_ascii=False))
    with (out/"per_photo.csv").open("w",newline="",encoding="utf-8-sig") as f:
        wr=csv.DictWriter(f,fieldnames=list(records[0]));wr.writeheader();wr.writerows(records)
    (out/"limits.txt").write_text(
      "Same public real photos and GT for A/B; all GT used only for scoring. "
      "ROI policy declared before scoring: max2 regions, score>=.53, 1-98% area, "
      "2% padding, local Small DET, duplicate IoU>=.45 or overlap smaller-area>=.72. "
      "No Medium recognizer used. Both recognitions use BGR per official YAML. "
      "OCR pipeline uses Python/OpenCV approximate SDK behavior. "
      "Coverage-aware CER is a proxy: misses count as deletions, unmatched recognized "
      "text NOT counted as insertions, so this isn't literal entire-page CER. "
      "Comparing the same photos does not make this Android phone performance. "
      "Model weights/SHA verified; no Android changes or APK.\n")
    print("LAYOUT_SMALL_DET_REC_AB_COMPLETE",len(records),flush=True)

if __name__=="__main__":main()
