#!/usr/bin/env python3
"""Secondary A/B: recover clipped text from low-score #315 detector boxes.

Compare ORIGINAL source quad crop against expanding that same source quad
by 2% or 5%, and a small crop-local CLAHE. Only low-score 0.30..0.45 boxes.
No oracle selection: each candidate strategy is evaluated on exact same
human-annotated overlap subset, so regression counts are paired.

This is NOT a valid dynamic selection algorithm and no improvement shall be
claimed without independent holdout.  Unmatched boxes stay unknown.
"""
import argparse,collections,csv,hashlib,json,math,statistics,time
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort,yaml
from ocr_box_value_deep_dive import (
    collect_images,det_preprocess,detections,crop_box,rec,box_iou,
    bbox_from_quad,normalize,distance,DET_SHA,REC_SHA,REC_CONFIG_SHA,EVAL_IOU
)

MODES=("original","source_expand_2pct","source_expand_5pct","local_clahe")

def crop_expanded(original_rgb, candidate, factor):
    clone={"quad": candidate["quad"].copy()}
    center=clone["quad"].mean(axis=0)
    clone["quad"]=center+(clone["quad"]-center)*(1.0+factor)
    return crop_box(original_rgb,clone)

def clahe_crop(crop):
    lab=cv2.cvtColor(crop,cv2.COLOR_RGB2LAB)
    lab[:,:,0]=cv2.createCLAHE(clipLimit=1.6,tileGridSize=(4,4)).apply(lab[:,:,0])
    return cv2.cvtColor(lab,cv2.COLOR_LAB2RGB)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("det");ap.add_argument("rec");ap.add_argument("yml")
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    for p,expected in [(args.det,DET_SHA),(args.rec,REC_SHA),(args.yml,REC_CONFIG_SHA)]:
        with open(p,"rb") as f:got=hashlib.sha256(f.read()).hexdigest()
        if got!=expected:raise ValueError("Wrong model hash: "+str(p))
    opt=ort.SessionOptions();opt.intra_op_num_threads=2;opt.inter_op_num_threads=1
    d=ort.InferenceSession(args.det,sess_options=opt,providers=["CPUExecutionProvider"])
    r=ort.InferenceSession(args.rec,sess_options=opt,providers=["CPUExecutionProvider"])
    di=d.get_inputs()[0].name;ri=r.get_inputs()[0].name
    chars=list(yaml.safe_load(Path(args.yml).read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    source=collect_images()
    cases=[]
    n_low=0;n_usable=0
    for i,(split,image_id,frame,texts,polygons) in enumerate(source):
        h,w=frame.shape[:2]; gt=[]
        for word,p in zip(texts,polygons):
            if not isinstance(word,str) or "###" in word or len(p)<8:continue
            pts=np.asarray(p,np.float32).reshape(-1,2)
            if not np.isfinite(pts).all():continue
            bbox=bbox_from_quad(pts)
            if bbox[2]-bbox[0]<6 or bbox[3]-bbox[1]<6:continue
            gt.append((word,bbox))
        if not gt:continue
        tensor=det_preprocess(frame)
        pred=d.run(None,{di:tensor})[0]
        raw=detections(pred.reshape(pred.shape[-2:]),h,w)
        for box in raw:
            if not (.30<=box["score"]<.45):continue
            n_low+=1
            iou,actual=max(((box_iou(box["bbox"],bb),word) for word,bb in gt),
                           default=(0., ""))
            if iou<EVAL_IOU:continue
            original=crop_box(frame,box)
            if original is None:continue
            crops={"original":original,
                   "source_expand_2pct":crop_expanded(frame,box,.02),
                   "source_expand_5pct":crop_expanded(frame,box,.05),
                   "local_clahe":clahe_crop(original)}
            if any(x is None for x in crops.values()):continue
            case={"split":split,"image":image_id,
                  "box_score":round(box["score"],4),
                  "GT":actual,"IoU":round(iou,3)}
            for method,crop in crops.items():
                text,conf,ms=rec(r,ri,chars,crop)
                case[method+"_pred"]=text
                case[method+"_score"]=round(conf,4)
                case[method+"_ms"]=round(ms,2)
                case[method+"_edits"]=distance(normalize(actual),normalize(text))
            cases.append(case);n_usable+=1
        if (i+1)%36==0:
            print("CROP_RESCUE_PROGRESS",i+1,"low_boxes",n_low,
                  "matched_and_tested",n_usable,flush=True)
    if not cases:raise RuntimeError("no matched annotations for cropped low boxes")
    for mode in MODES:
        base=sum(c["original_edits"] for c in cases)
        total=sum(c[mode+"_edits"] for c in cases)
        improved=sum(c[mode+"_edits"]<c["original_edits"] for c in cases)
        worse=sum(c[mode+"_edits"]>c["original_edits"] for c in cases)
        exact=sum(c[mode+"_edits"]==0 for c in cases)
        ms=sorted(c[mode+"_ms"] for c in cases)
        print("SOURCE_RECROP_AB",json.dumps({
          "mode":mode,"matched_annotated_cases":len(cases),
          "exact":exact,"total_edits":total,"baseline_edits":base,
          "improved":improved,"worsened":worse,
          "p95_rec_infer_ms":ms[int((len(ms)-1)*.95)]},ensure_ascii=False),flush=True)
    for case in cases:
        if case["source_expand_2pct_edits"]<case["original_edits"] or (
            case["source_expand_2pct_edits"]>case["original_edits"]):
            print("RECROP_PAIR",json.dumps({
               "image":case["image"],"GT":case["GT"],
               "original":case["original_pred"],"expand2":case["source_expand_2pct_pred"],
               "old_edits":case["original_edits"],
               "new_edits":case["source_expand_2pct_edits"]
            },ensure_ascii=False),flush=True)
    with (out/"matched_low_boxes_crop_ab.csv").open("w",encoding="utf-8-sig",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(cases[0]))
        writer.writeheader();writer.writerows(cases)
    (out/"limitations.json").write_text(json.dumps({
       "photos":len(source),"low_score_boxes":n_low,
       "annotated_overlap_paired":len(cases),
       "unmatched_boxes_not_scored":n_low-len(cases),
       "no_ground_truth_driven_runtime_decisions":True,
       "caveat":"approx SDK pipeline, public natural scene; NOT device OCR/USB end-to-end",
    },ensure_ascii=False,indent=2))
    print("LOW_SCORE_SOURCE_RECROP_AB=PASS",len(cases),flush=True)
if __name__=="__main__":main()
