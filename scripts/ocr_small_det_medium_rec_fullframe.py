#!/usr/bin/env python3
"""#315 PP-OCRv6 Small DET+REC versus Small DET+Medium REC, real-scene A/B.

Both variants use one and the same Small detector prediction/crop per photo.
One Small and one Medium recognizer inference on each identical source crop.
Ground truth NEVER controls detection/cropping/model selection; only scores results.

Two fixed, DISJOINT 216 / 132 real-scene photo cohorts, original annotations:
https://huggingface.co/datasets/Yesianrohn/OCR-Data (ReCTS, RCTW, ART).

These are Python/OpenCV approximations of Android PaddleOCR postprocessing
and USB-free ONNX CPU runs. Report separate (1) matched GT CER, (2) GT misses,
(3) uncertain unannotated text and (4) total modeled per-photo CPU time.
Unmatched detections are NOT automatically false positive OCR.

No app code, release branch, APK, UVC camera or user photos touched.
"""
import argparse
import collections
import csv
import hashlib
import json
import math
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
import yaml

import ocr_box_value_deep_dive as sdk
from ocr_box_value_deep_dive import (
    DET_SHA, REC_SHA, REC_CONFIG_SHA, normalize, distance,
    bbox_from_quad, box_iou, crop_box, rec,
)

MED_REC_SHA="9c09abf0957f7968c7586464b7397b84ad2387a0497a351af40e9acc71b673ba"
MED_DICT_SHA="991b700facf5b50a7de193468207d5f4255b538dde0d312ae3b7c7a9b6873129"
THRESH=.45
IOU=.40
MODES=("small_det_small_rec","small_det_medium_rec")
CATEGORIES=("ALL","ReCTS","RCTW","ART","HAS_CJK","HAS_DIGIT")
MAX_RECOGNITIONS_PER_FRAME=150


def load_models(dir):
    root=Path(dir)
    options=ort.SessionOptions()
    options.intra_op_num_threads=2
    options.inter_op_num_threads=1
    options.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    spec={
      "small_det":("det.onnx",DET_SHA,None),
      "small_rec":("small_rec.onnx",REC_SHA,("small_rec.yml",REC_CONFIG_SHA)),
      "medium_rec":("medium_rec.onnx",MED_REC_SHA,("medium_rec.yml",MED_DICT_SHA)),
    }
    models={}
    for name,(f,expected,yaml_spec) in spec.items():
        p=root/f
        sha=hashlib.sha256(p.read_bytes()).hexdigest()
        if sha!=expected:raise ValueError(name+" unverified SHA256 "+sha)
        s=ort.InferenceSession(str(p),sess_options=options,providers=["CPUExecutionProvider"])
        entry={"session":s,"input_name":s.get_inputs()[0].name,"bytes":p.stat().st_size}
        if yaml_spec is not None:
            path,sha_expected=yaml_spec
            cfg=root/path
            if hashlib.sha256(cfg.read_bytes()).hexdigest()!=sha_expected:
                raise ValueError(name+" dictionary SHA does not match official")
            decoded=yaml.safe_load(cfg.read_text())["PostProcess"]["character_dict"]
            chars=list(decoded)
            if chars[-1]!=" ":chars.append(" ")
            entry["chars"]=chars
            x=s.get_inputs()[0]
            if len(x.shape)!=4 or x.shape[1]!=3 or x.shape[2]!=48:
                raise ValueError(name+" non 48-height CTC input shape: "+str(x.shape))
        models[name]=entry
        print("CHECK_ORIGINAL_MODEL",name,sha,"bytes",p.stat().st_size,flush=True)
    return models


def gt_from_polygons(texts,polygons):
    gt=[]
    for label,vertices in zip(texts,polygons):
        if not isinstance(label,str) or "###" in label or len(vertices)<8:continue
        try:
            pts=np.asarray(vertices,np.float32).reshape(-1,2)
        except (ValueError,TypeError):
            continue
        if not np.isfinite(pts).all():continue
        b=bbox_from_quad(pts)
        if b[2]-b[0]<6 or b[3]-b[1]<6:continue
        gt.append({"box":b,"text":label,"normal":normalize(label)})
    return gt


def gt_match(boxes,gt):
    # One-to-one greedy pairing, IoU>=0.4, fixed for BOTH recognition models.
    edges=[]
    for i,bb in enumerate(boxes):
        for j,g in enumerate(gt):
            overlap=box_iou(bb["bbox"],g["box"])
            if overlap>=IOU:edges.append((overlap,i,j))
    edges.sort(reverse=True)
    seen_box=set();seen_gt=set();pairs={}
    for overlap,i,j in edges:
        if i in seen_box or j in seen_gt:continue
        seen_box.add(i);seen_gt.add(j);pairs[i]=(j,overlap)
    return pairs


def p(xs,q):
    if not xs:return None
    s=sorted(xs)
    return round(s[int((len(s)-1)*q)],3)


def classify(g):
    label=g["normal"]
    return (int(any("\u3400"<=c<="\u9fff" for c in label)),
            int(any(c.isdigit() for c in label)))


def metrics_by_slice(box_data,frame_data,label,mode):
    selected=[x for x in box_data if x["mode"]==mode and (
        label=="ALL" or x["split"]==label or
        label=="HAS_CJK" and x["gt_has_cjk"] or
        label=="HAS_DIGIT" and x["gt_has_digit"])]
    # Include all GT annotations once. Frame records include missed annotations
    # and THEIR character count. This metric does not add unknown unannotated
    # regions (they may be real text).
    frame=[x for x in frame_data if label=="ALL" or x["split"]==label]
    matched=[x for x in selected if x["gt_matched"]]
    total_chars=sum(x["gt_chars"] for x in matched)
    total_errors=sum(x["edit_distance"] for x in matched)
    num_gt=sum(x["gt_count"] for x in frame)
    num_missed=sum(x["gt_unmatched"] for x in frame)
    if label in ("ALL","ReCTS","RCTW","ART"):
        missing_chars=sum(x["missed_gt_chars"] for x in frame)
        all_gt_chars=sum(x["gt_characters"] for x in frame)
        coverage_cer=round((total_errors+missing_chars)/max(1,all_gt_chars),5)
    else:
        missing_chars=None
        coverage_cer=None
    timings=[x["total_small_ms"] if mode==MODES[0] else x["total_mixed_ms"]
             for x in frame]
    return {
      "subset":label,"model_configuration":mode,
      "photos":len(frame),"annotated_gt":num_gt if label not in ("HAS_CJK","HAS_DIGIT") else "",
      "matched_gt":len(matched),"missed_gt":num_missed if label not in ("HAS_CJK","HAS_DIGIT") else "",
      "matched_characters":total_chars,
      "matched_character_edits":total_errors,
      "matched_CER":round(total_errors/max(1,total_chars),5),
      "matched_exact_text":sum(x["edit_distance"]==0 for x in matched),
      "matched_exact_rate":round(sum(x["edit_distance"]==0 for x in matched)/max(1,len(matched)),5),
      "coverage_aware_CER_proxy":coverage_cer,
      "unmatched_recognized_boxes":sum(x["recognized_unmatched"] for x in frame),
      "total_cpu_frame_p50_ms":p(timings,.50),
      "total_cpu_frame_p95_ms":p(timings,.95),
      "rec_p50_ms":p([x["rec_ms"] for x in selected],.50),
      "rec_p95_ms":p([x["rec_ms"] for x in selected],.95),
    }


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--models",required=True)
    parser.add_argument("--cohort",choices=["dev","holdout"],required=True)
    parser.add_argument("--out",required=True)
    parser.add_argument("--max-photos",type=int,default=0,
                        help="optional smoke subset, NEVER represent as full cohort")
    args=parser.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    models=load_models(args.models)
    sdk.SLICES=sdk.SLICES if args.cohort=="dev" else sdk.HOLDOUT_SLICES
    sdk.THRESHOLDS=[THRESH]
    sdk.DET_BITMAP_THRESH=.20
    sdk.DET_UNCLIP=1.40
    photos=sdk.collect_images()
    if args.max_photos:
        photos=photos[:args.max_photos]
    expected=216 if args.cohort=="dev" else 132
    assert len(photos)>= (min(args.max_photos,12) if args.max_photos else expected-12), (
        "Too few actual real-world images",len(photos),expected)
    det=models["small_det"]
    by_box=[];by_frame=[]
    for count,(split,index,rgb,texts,polys) in enumerate(photos,1):
        gt=gt_from_polygons(texts,polys)
        if not gt:continue
        h,w=rgb.shape[:2]
        t=time.perf_counter()
        inp=sdk.det_preprocess(rgb)
        prep_ms=(time.perf_counter()-t)*1000
        t=time.perf_counter()
        heatmap=det["session"].run(None,{det["input_name"]:inp})[0]
        det_ms=(time.perf_counter()-t)*1000
        t=time.perf_counter()
        boxes=sdk.detections(heatmap.reshape(heatmap.shape[-2:]),h,w)
        boxes=[b for b in boxes if b["score"]>=THRESH]
        post_ms=(time.perf_counter()-t)*1000
        matches=gt_match(boxes,gt)
        available={item[0] for item in matches.values()}
        missed_gt=[g for i,g in enumerate(gt) if i not in available]
        matched_chars=sum(len(gt[item[0]]["normal"]) for item in matches.values())
        timings={m:0. for m in MODES}
        crop_total=0.
        processed=0;errors=0;recognized_unmatched=0
        for i,b in enumerate(boxes[:MAX_RECOGNITIONS_PER_FRAME]):
            t=time.perf_counter()
            crop=crop_box(rgb,b)
            crop_total+=(time.perf_counter()-t)*1000
            if crop is None:continue
            try:
                pred_sm,conf_sm,ms_sm=rec(models["small_rec"]["session"],
                    models["small_rec"]["input_name"],models["small_rec"]["chars"],crop)
                pred_md,conf_md,ms_md=rec(models["medium_rec"]["session"],
                    models["medium_rec"]["input_name"],models["medium_rec"]["chars"],crop)
            except Exception as e:
                print("WARN_PAIRED_BOX_RECOGNITION",split,index,i,str(e)[:120],flush=True)
                errors+=1;continue
            timings[MODES[0]]+=ms_sm
            timings[MODES[1]]+=ms_md
            if i not in matches and (pred_sm.strip() or pred_md.strip()):
                recognized_unmatched+=1
            processed+=1
            for mode,pred,conf,ms in [(MODES[0],pred_sm,conf_sm,ms_sm),
                                      (MODES[1],pred_md,conf_md,ms_md)]:
                j,iou=matches.get(i,(None,0.))
                truth=gt[j] if j is not None else None
                by_box.append({
                  "split":split,"image":index,"box_index":i,"mode":mode,
                  "gt_matched":int(truth is not None),
                  "gt_text":truth["text"] if truth else "",
                  "predicted_text":pred,
                  "gt_chars":len(truth["normal"]) if truth else 0,
                  "edit_distance":distance(truth["normal"],normalize(pred)) if truth else -1,
                  "gt_has_cjk":classify(truth)[0] if truth else 0,
                  "gt_has_digit":classify(truth)[1] if truth else 0,
                  "iou":round(iou,4),
                  "rec_conf":round(conf,4),
                  "rec_ms":round(ms,3),
                  "candidate_bbox":",".join(str(int(round(v))) for v in b["bbox"]),
                  "det_box_score":round(b["score"],4),
                })
        common=prep_ms+det_ms+post_ms+crop_total
        by_frame.append({
          "split":split,"image":index,"photo_height":h,"photo_width":w,
          "gt_count":len(gt),"gt_characters":sum(len(g["normal"]) for g in gt),
          "gt_matched_geometry":len(matches),
          "gt_unmatched":len(missed_gt),
          "missed_gt_chars":sum(len(g["normal"]) for g in missed_gt),
          "detected_boxes":len(boxes),"recognized_boxes":processed,
          "recognition_errors":errors,
          "recognized_unmatched":recognized_unmatched,
          "det_preprocess_ms":round(prep_ms,3),"det_infer_ms":round(det_ms,3),
          "det_postprocess_ms":round(post_ms,3),"crop_total_ms":round(crop_total,3),
          "small_rec_total_ms":round(timings[MODES[0]],3),
          "medium_rec_total_ms":round(timings[MODES[1]],3),
          "total_small_ms":round(common+timings[MODES[0]],3),
          "total_mixed_ms":round(common+timings[MODES[1]],3),
        })
        if count%20==0:
            print("HYBRID_PROGRESS",args.cohort,count,"of",len(photos),
                  "gt",sum(x["gt_count"] for x in by_frame),
                  "boxes",sum(x["recognized_boxes"] for x in by_frame),flush=True)
    assert len(by_frame)>= (min(args.max_photos,10) if args.max_photos else expected-14)
    if not by_box:raise RuntimeError("NO OCR paired inference results")
    assert not any(x["recognized_boxes"]>MAX_RECOGNITIONS_PER_FRAME for x in by_frame)
    total_fail=sum(x["recognition_errors"] for x in by_frame)
    total_attempt=sum(x["recognized_boxes"]+x["recognition_errors"] for x in by_frame)
    if total_fail > max(2,total_attempt*.01):
        raise RuntimeError("Too many recognition failures: "+str(total_fail))

    all_report=[metrics_by_slice(by_box,by_frame,c,m) for c in CATEGORIES for m in MODES]
    for r in all_report:
        print("HYBRID_GT_METRIC",json.dumps(r,ensure_ascii=False),flush=True)
    by_key=collections.defaultdict(dict)
    for x in by_box:
        by_key[(x["split"],x["image"],x["box_index"])][x["mode"]]=x
    paired=[x for x in by_key.values() if len(x)==2 and x[MODES[0]]["gt_matched"]]
    paired_result={
      "cohort":args.cohort,
      "paired_gt_regions":len(paired),
      "Medium_improved":sum(x[MODES[1]]["edit_distance"]<x[MODES[0]]["edit_distance"] for x in paired),
      "Medium_worsened":sum(x[MODES[1]]["edit_distance"]>x[MODES[0]]["edit_distance"] for x in paired),
      "Medium_unchanged":sum(x[MODES[1]]["edit_distance"]==x[MODES[0]]["edit_distance"] for x in paired),
      "Small_wrong_to_Medium_exact":sum(x[MODES[0]]["edit_distance"]>0 and
                                     x[MODES[1]]["edit_distance"]==0 for x in paired),
      "Small_exact_to_Medium_wrong":sum(x[MODES[0]]["edit_distance"]==0 and
                                     x[MODES[1]]["edit_distance"]>0 for x in paired),
      "extra_rec_inference_p95_ms_per_photo":p([
          x["medium_rec_total_ms"]-x["small_rec_total_ms"] for x in by_frame],.95),
      "total_pipeline_latency_ratio_sum":round(
          sum(x["total_mixed_ms"] for x in by_frame)/
          max(1,sum(x["total_small_ms"] for x in by_frame)),4),
    }
    print("HYBRID_PAIRED_GT",json.dumps(paired_result,ensure_ascii=False),flush=True)
    for k,rows in [("per_box_ocr.csv",by_box),("per_photo_timing.csv",by_frame),
                   ("summary.csv",all_report)]:
        with (out/k).open("w",encoding="utf-8-sig",newline="") as f:
            writer=csv.DictWriter(f,fieldnames=list(rows[0]))
            writer.writeheader();writer.writerows(rows)
    (out/"paired.json").write_text(json.dumps(paired_result,ensure_ascii=False,indent=2))
    (out/"assumptions.txt").write_text(
      "The exact same PP-OCRv6 Small detector boxes and source crops are used for both recognizers.\n"
      "Ground truth is used ONLY for matched-polygon scores (IoU>=0.40), not box choice.\n"
      "Only one recognition per model per original detected box (no secondary detection).\n"
      "Coverage-aware CER is a proxy: counts missing GT as deletions but not unmatched OCR text as insertions.\n"
      "Non-GT matched box predictions are UNKNOWN; they could include text omitted by dataset labels.\n"
      "Python OpenCV minRect/pyclipper approximates the Android SDK, and all timings are cloud CPU, no USB or TTS.\n"
      "No photo or model binaries uploaded to GitHub artifacts; no release/Android/APK changes.\n"
    )
    print("SMALL_DET_MEDIUM_REC_FULL_FRAME_AB=PASS",args.cohort,len(by_frame),len(paired),flush=True)

if __name__=="__main__":main()
