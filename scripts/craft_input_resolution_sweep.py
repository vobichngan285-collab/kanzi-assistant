#!/usr/bin/env python3
"""Controlled CRAFT MLT25K ONNX resolution sweep: 960/768/640/512 max side.

The existing original CRAFT weight, postprocessing, confidence thresholds,
same 72 public real images and same PP-OCRv6 Small REC are fixed.
Only predeclared image max-side changes. This is NOT new model tuning on GT.
No Android code changes, Medium, APK, or new model binaries in artifacts.
"""
import argparse,csv,json,time
from pathlib import Path
import numpy as np
import cv2
import craft_small_detector_ab as craft
import ocr_box_value_deep_dive as sdk

SIZES=(960,768,640,512)

def summarize(rows,cohort,mode):
    rr=[r for r in rows if r["cohort"]==cohort]
    if not rr:return None
    s=lambda field:sum(x[f"{mode}_{field}"] for x in rr)
    gt=s("gt");chars=s("gt_chars")
    ts=sorted(x[f"{mode}_ms"] for x in rr)
    return dict(cohort=cohort,configuration=mode,photos=len(rr),
       gt_lines=gt,matched_gt_lines=s("matched"),
       gt_line_recall=round(s("matched")/max(1,gt),5),
       missed_gt_chars=s("missed_chars"),
       matched_edit_distance=s("rec_edits"),
       coverage_aware_CER_proxy=round(s("proxy_errors")/max(1,chars),5),
       unpaired_nonempty_ocr_boxes=s("unmatched_text_boxes"),
       detected_boxes=s("detected_boxes"),
       mean_total_frame_ms=round(sum(ts)/len(ts),2),
       p95_total_frame_ms=round(ts[int((len(ts)-1)*.95)],2),
       mean_detector_ms=round(s("det_ms")/len(rr),2),
       p95_detector_ms=round(sorted(x[f"{mode}_det_ms"] for x in rr)[int((len(rr)-1)*.95)],2),
       zero_overlap_original_gt=s("zero_gt"),
       zero_overlap_recovered=s("recovered_zero"))

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--max-photos",type=int,default=0)
    a=p.parse_args()
    out=Path(a.out);out.mkdir(exist_ok=True,parents=True)
    det,rec,model,chars=craft.get_models(a.models)
    sdk.SLICES={key:[(0,12),(500,12)] for key in ("ReCTS","RCTW","ART")}
    sdk.THRESHOLDS=[.45];sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40
    images=sdk.collect_images()
    if a.max_photos:images=images[:a.max_photos]
    if len(images)<(6 if a.max_photos else 65):
        raise RuntimeError("Insufficient full real-scene photo test set")
    rows=[]
    for k,(split,idx,rgb,texts,polys) in enumerate(images,1):
        gt=craft.gt_rows(texts,polys)
        if not gt:continue
        cohort="DEV" if idx<100 else "HOLDOUT"
        row=dict(cohort=cohort,split=split,image=idx)
        t=time.perf_counter()
        baseline,det_ms=craft.small_detect(det,rgb)
        pbase=craft.rec_predictions(rec,chars,rgb,baseline)
        ms=(time.perf_counter()-t)*1000
        bscore=craft.score_pair(baseline,pbase,gt)
        for key,val in bscore.items():row["Small_"+key]=val
        row["Small_ms"]=round(ms,2)
        row["Small_det_ms"]=round(det_ms,2)
        row["Small_zero_gt"]=0;row["Small_recovered_zero"]=0
        matched_original=set(craft.box_pairs(baseline,gt).values())
        zero=[j for j in range(len(gt)) if j not in matched_original and
          not any(craft.overlap(gt[j]["box"],bb["bbox"])>0 for bb in baseline)]
        row["Small_zero_gt"]=len(zero)
        for size in SIZES:
            craft.CRAFT_LONG_SIDE=size
            t=time.perf_counter()
            boxes,c_ms,diag=craft.craft_detect(model,rgb)
            ps=craft.rec_predictions(rec,chars,rgb,boxes)
            total_ms=(time.perf_counter()-t)*1000
            matched=set(craft.box_pairs(boxes,gt).values())
            vals=craft.score_pair(boxes,ps,gt)
            prefix=f"CRAFT{size}"
            for key,val in vals.items():row[f"{prefix}_{key}"]=val
            row[f"{prefix}_ms"]=round(total_ms,2)
            row[f"{prefix}_det_ms"]=round(c_ms,2)
            row[f"{prefix}_zero_gt"]=len(zero)
            row[f"{prefix}_recovered_zero"]=sum(j in matched for j in zero)
        rows.append(row)
        if k%9==0:
            print("CRAFT_SIZE_SWEEP_PROGRESS",k,"/",len(images),
               "gt",sum(r["Small_gt"] for r in rows),
               "matched",[row[f"CRAFT{x}_matched"] for x in SIZES],flush=True)
    if not a.max_photos and len(rows)<65:raise RuntimeError("Incomplete 72 real photo study")
    summaries=[]
    for cohort in ("DEV","HOLDOUT"):
        for mode in ("Small",)+tuple(f"CRAFT{x}" for x in SIZES):
            summary=summarize(rows,cohort,mode)
            if summary:
                summaries.append(summary)
                print("CRAFT_SIZE_SWEEP_RESULT",json.dumps(summary),flush=True)
    with (out/"per_photo.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
    (out/"summary.json").write_text(json.dumps(summaries,ensure_ascii=False,indent=2))
    (out/"limitations.txt").write_text(
      "Exactly same original CRAFT MLT25k weight SHA256 and same Small REC, "
      "fixed default CRAFT text/link thresholds for all max-side scales. "
      "Input size is capped at 960,768,640,512 (no upscaling), rounded as "
      "the earlier 960px CRAFT baseline; no post hoc threshold selection. "
      "Same 72 public photos used in prior research: HOLDOUT not truly novel. "
      "GT AABB IoU >=0.4 matching is not real-page reading accuracy; "
      "CER proxy penalizes missed GT but not all extra predictions. "
      "One GitHub Linux CPU runner for all scales; not Android/TTS/temperature. "
      "No app updates or APK.\n")
    print("CRAFT_SIZE_SWEEP_COMPLETE",len(rows),flush=True)

if __name__=="__main__":main()
