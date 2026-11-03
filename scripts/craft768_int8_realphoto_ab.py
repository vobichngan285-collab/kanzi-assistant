#!/usr/bin/env python3
"""Matched CRAFT768 FP32 vs calibrated S8S8 QDQ INT8 detection, same Small REC.

72 exactly same public natural-scene OCR photos as previous CRAFT tests.
Score with existing one-to-one AABB IoU >=0.4 and coverage-CER proxy.
Annotator GT is NEVER used for CRAFT quantization thresholds or box selection.
No changes to Android production, no APK, no Medium model.
"""
import argparse,collections,csv,hashlib,json,math,time
from pathlib import Path
import craft_small_detector_ab as craft
import ocr_box_value_deep_dive as sdk

MODES=("FP32","INT8")

def aggregate(rows,cohort,mode):
    rr=[r for r in rows if r["cohort"]==cohort]
    if not rr:return None
    sumfield=lambda key:sum(r[f"{mode}_{key}"] for r in rr)
    gt=sumfield("gt")
    chars=sumfield("gt_chars")
    times=sorted(r[f"{mode}_total_ms"] for r in rr)
    dtimes=sorted(r[f"{mode}_det_ms"] for r in rr)
    return dict(cohort=cohort,model=mode,photos=len(rr),
                annotated_gt_lines=gt,
                matched_gt_lines=sumfield("matched"),
                iou_040_matched_share=round(sumfield("matched")/max(1,gt),5),
                unmatched_gt_characters=sumfield("missed_chars"),
                matched_gt_edit_distance=sumfield("rec_edits"),
                coverage_CER_proxy=round(sumfield("proxy_errors")/max(1,chars),5),
                unpaired_recognized_boxes=sumfield("unmatched_text_boxes"),
                detected_boxes=sumfield("detected_boxes"),
                small_zero_overlap_gt=sumfield("zero_gt"),
                zero_overlap_recovered_by_craft=sumfield("zero_recovered"),
                mean_full_ocr_ms=round(sum(times)/len(times),2),
                p95_full_ocr_ms=round(times[int((len(times)-1)*.95)],2),
                mean_detector_ms=round(sum(dtimes)/len(dtimes),2),
                p95_detector_ms=round(dtimes[int((len(dtimes)-1)*.95)],2))

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--quant",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--max-photos",type=int,default=0)
    a=p.parse_args()
    out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    craft.CRAFT_LONG_SIDE=768
    baseline,rec,fp32,chars=craft.get_models(a.models)
    qmodel=craft.model(a.quant)
    sessions={"FP32":fp32,"INT8":qmodel}
    sdk.SLICES={name:[(0,12),(500,12)] for name in ("ReCTS","RCTW","ART")}
    sdk.THRESHOLDS=[.45];sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40
    photos=sdk.collect_images()
    if a.max_photos:photos=photos[:a.max_photos]
    if len(photos)<(6 if a.max_photos else 70):raise RuntimeError(f"Too few photos: {len(photos)}")
    # Warm the two graphs with the same source photo; performance samples follow.
    warm_input,_=craft.craft_preprocess(photos[0][2])
    for mode in MODES:
        s=sessions[mode]
        s.run(None,{s.get_inputs()[0].name:warm_input})
    rows=[]
    for k,(split,index,rgb,texts,polys) in enumerate(photos,1):
        gt=craft.gt_rows(texts,polys)
        if not gt:continue
        cohort="DEV" if index<100 else "HOLDOUT"
        small_boxes,_=craft.small_detect(baseline,rgb)
        small_matched=set(craft.box_pairs(small_boxes,gt).values())
        zero=[j for j,g in enumerate(gt)
              if j not in small_matched and not any(craft.overlap(g["box"],b["bbox"])>0 for b in small_boxes)]
        row=dict(cohort=cohort,split=split,image=index)
        matches={}
        # Alternate order to minimize a constant first-run advantage.
        modes=MODES if k%2 else MODES[::-1]
        for mode in modes:
            start=time.perf_counter()
            boxes,det_ms,_=craft.craft_detect(sessions[mode],rgb)
            preds=craft.rec_predictions(rec,chars,rgb,boxes)
            total_ms=(time.perf_counter()-start)*1000
            score=craft.score_pair(boxes,preds,gt)
            matches[mode]=set(craft.box_pairs(boxes,gt).values())
            for key,value in score.items():
                row[f"{mode}_{key}"]=value
            row[f"{mode}_total_ms"]=round(total_ms,2)
            row[f"{mode}_det_ms"]=round(det_ms,2)
            row[f"{mode}_zero_gt"]=len(zero)
            row[f"{mode}_zero_recovered"]=sum(j in matches[mode] for j in zero)
        row["INT8_matched_GT_only"]=len(matches["INT8"]-matches["FP32"])
        row["FP32_matched_GT_only"]=len(matches["FP32"]-matches["INT8"])
        rows.append(row)
        if k%9==0:
            print("CRAFT768_INT8_REALPHOTO_PROGRESS",k,"/",len(photos),
                  "fp32",row["FP32_matched"],"int8",row["INT8_matched"],
                  "quant_lost_gt",row["FP32_matched_GT_only"],flush=True)
    if not a.max_photos:
        assert len(rows)==72,len(rows)
    summary=[]
    for cohort in ("DEV","HOLDOUT"):
        for mode in MODES:
            item=aggregate(rows,cohort,mode)
            if item:
                summary.append(item)
                print("CRAFT768_INT8_AB_RESULT",json.dumps(item,ensure_ascii=False),flush=True)
    if not a.max_photos:
        expected={("DEV","FP32"):(217,142),("HOLDOUT","FP32"):(244,149)}
        for row in summary:
            key=(row["cohort"],row["model"])
            if key in expected:
                if (row["annotated_gt_lines"],row["matched_gt_lines"])!=expected[key]:
                    raise RuntimeError(f"FP32 control mismatch {key}: {row}; expected {expected[key]}")
    changes={}
    for cohort in ("DEV","HOLDOUT"):
        rr=[r for r in rows if r["cohort"]==cohort]
        if not rr:continue
        changes[cohort]=dict(fp32_GT_missing_after_quantization=sum(r["FP32_matched_GT_only"] for r in rr),
                            newly_matching_GT_in_int8=sum(r["INT8_matched_GT_only"] for r in rr),
                            images_with_more_quant_matched=sum(r["INT8_matched"]>r["FP32_matched"] for r in rr),
                            images_with_fewer_quant_matched=sum(r["INT8_matched"]<r["FP32_matched"] for r in rr))
    (out/"summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2))
    (out/"paired_gt_changes.json").write_text(json.dumps(changes,ensure_ascii=False,indent=2))
    with (out/"per_photo.csv").open("w",newline="",encoding="utf-8-sig") as file:
        writer=csv.DictWriter(file,fieldnames=list(rows[0]))
        writer.writeheader();writer.writerows(rows)
    (out/"limitations.txt").write_text(
      "One original CRAFT768 FP32 model and one DEV-calibrated QDQ INT8 S8S8 model, "
      "same weights' origin, RGB preprocessing, thresholds, postprocessing, and Small REC. "
      "Same 72 repeatedly evaluated public photos; HOLDOUT is NOT novel prospective blind data. "
      "GT AABB IoU>=0.4 is a line matching proxy, not the true spoken full-page accuracy; "
      "coverage CER proxy omits insertions from unpaired output boxes. "
      "CPU timings are GitHub Actions Linux CPU, not Android ARM, 1080p UVC camera or TTS. "
      "Small DET runs only to identify baseline zero-overlap GT; not used in CRAFT predictions. "
      "No Android program edits, production deployment, or APK.\n")
    print("CRAFT768_INT8_REALPHOTO_COMPLETE",len(rows),flush=True)

if __name__=="__main__":main()
