#!/usr/bin/env python3
"""Prospective-ish CRAFT768 INT8 boxes + unchanged Small REC versus Small DET+REC.

Predeclare an unused-by-this-program slice: 12 public scene images starting
at offset 800 in ART / RCTW / ReCTS. These datasets have been consulted in
earlier research, so independence from *all* prior exploration is NOT claimed.
No thresholds or grouping are chosen from this evaluation's labels.

Two complementary outcomes:
  (1) published geometric GT IoU>=0.4 / missed + matched-edit CER proxy;
  (2) a stricter concatenated *whole-image* text Levenshtein diagnostic,
      with unmatched predicted text penalized as insertions, sensitive to
      line-order heuristic / columns and annotation grouping. NOT actual TTS.
"""
import argparse, collections, csv, json, statistics, time
from pathlib import Path
import craft_small_detector_ab as craft
import ocr_box_value_deep_dive as sdk

NEW_PREDECLARED={x:[(800,12)] for x in ("ART","RCTW","ReCTS")}
MIN_PHOTOS=20
CRAFT_SHA="9ab885d2f6669b77f70e9c8f68acfbe408c0b051501fd02d94bcaa0c7779ab15"

def page_text(boxes,predictions):
    # Simple deterministic top-to-bottom, left-to-right reading heuristic.
    # Sensitive to multi-column / rotated scenes. No GT information used.
    order=sorted(range(len(boxes)),key=lambda i:(
        (boxes[i]["bbox"][1]+boxes[i]["bbox"][3])/2.,
        boxes[i]["bbox"][0],i))
    return sdk.normalize("".join(predictions.get(i,"") for i in order))

def ground_truth_text(gt):
    ordered=sorted(gt,key=lambda b:(
        (b["box"][1]+b["box"][3])/2.,b["box"][0]))
    return sdk.normalize("".join(g["text"] for g in ordered))

def percentile(vals,p):
    if not vals:return None
    v=sorted(vals);return round(v[int((len(v)-1)*p)],2)

def summarize(rows,mode):
    total_gt_chars=sum(r["gt_chars"] for r in rows)
    total_gt_lines=sum(r["gt_lines"] for r in rows)
    total_real_ed=sum(r[mode+"_fullpage_edits"] for r in rows)
    sumf=lambda suffix:sum(r[mode+"_"+suffix] for r in rows)
    elapsed=[r[mode+"_total_ms"] for r in rows]
    return dict(
      mode=mode,photos=len(rows),annotated_lines=total_gt_lines,
      gt_characters=total_gt_chars,
      matched_lines_iou040=sumf("matched"),
      matched_line_recall=round(sumf("matched")/max(1,total_gt_lines),5),
      unmatched_gt_characters=sumf("missed_chars"),
      matched_gt_edit_distance=sumf("rec_edits"),
      geometric_coverage_cer_proxy=round(sumf("proxy_errors")/max(1,total_gt_chars),5),
      unmatched_boxes_with_text=sumf("unmatched_text_boxes"),
      detected_boxes=sumf("detected_boxes"),
      predicted_nonempty_boxes=sumf("nonempty_boxes"),
      predicted_characters=sumf("pred_chars"),
      whole_image_ordered_edit_distance=total_real_ed,
      whole_image_ordered_edit_rate=round(total_real_ed/max(1,total_gt_chars),5),
      mean_detector_ms=round(sumf("det_ms")/len(rows),2),
      mean_recognition_and_crop_ms=round(sumf("rec_ms")/len(rows),2),
      mean_full_pipeline_ms=round(sum(elapsed)/len(rows),2),
      p50_full_pipeline_ms=percentile(elapsed,.5),
      p95_full_pipeline_ms=percentile(elapsed,.95)
    )

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--quant",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--max-photos",type=int,default=0)
    args=p.parse_args()
    out=Path(args.out);out.mkdir(exist_ok=True,parents=True)
    import hashlib
    sha=hashlib.sha256(Path(args.quant).read_bytes()).hexdigest()
    assert sha==CRAFT_SHA,sha
    craft.CRAFT_LONG_SIDE=768
    small,rec,_,chars=craft.get_models(args.models)
    quant=craft.model(args.quant,CRAFT_SHA)
    sdk.SLICES=NEW_PREDECLARED
    sdk.THRESHOLDS=[.45];sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40
    photos=sdk.collect_images()
    if len(photos)<MIN_PHOTOS:
        raise RuntimeError(f"Predeclared offset 800 scenes unavailable: {len(photos)}. No silent substitution.")
    if args.max_photos:photos=photos[:args.max_photos]
    rows=[]
    for index,(name,rowidx,rgb,texts,polys) in enumerate(photos,1):
        gt=craft.gt_rows(texts,polys)
        if not gt:continue
        record=dict(dataset=name,rowidx=rowidx,
                    original_width=rgb.shape[1],original_height=rgb.shape[0],
                    is_portrait=rgb.shape[0]>rgb.shape[1],
                    gt_lines=len(gt),gt_chars=len(ground_truth_text(gt)))
        gtfull=ground_truth_text(gt)
        # Pair Small baseline and CRAFT INT8 with same identical recognition session.
        for mode in (("Small","CRAFT_INT8") if index%2 else ("CRAFT_INT8","Small")):
            started=time.perf_counter()
            if mode=="Small":
                boxes,det_ms=craft.small_detect(small,rgb)
            else:
                boxes,det_ms,_=craft.craft_detect(quant,rgb)
            crops_at=time.perf_counter()
            predictions=craft.rec_predictions(rec,chars,rgb,boxes)
            elapsed=(time.perf_counter()-started)*1000
            metrics=craft.score_pair(boxes,predictions,gt)
            pred=page_text(boxes,predictions)
            for key,val in metrics.items():record[mode+"_"+key]=val
            record[mode+"_nonempty_boxes"]=sum(bool(t.strip()) for t in predictions.values())
            record[mode+"_pred_chars"]=len(pred)
            record[mode+"_fullpage_edits"]=sdk.distance(pred,gtfull)
            record[mode+"_det_ms"]=round(det_ms,2)
            record[mode+"_rec_ms"]=round((time.perf_counter()-crops_at)*1000,2)
            record[mode+"_total_ms"]=round(elapsed,2)
        rows.append(record)
        if index%6==0:
            print("CRAFT_SMALL_REC_NEW_SCENE_PROGRESS",index,"/",len(photos),
                  "dataset",name,"image",rowidx,"gt",len(gt),
                  "small_matches",record["Small_matched"],
                  "craft_matches",record["CRAFT_INT8_matched"],flush=True)
    if args.max_photos==0 and len(rows)<MIN_PHOTOS:
        raise RuntimeError(f"Insufficient scored scenes: {len(rows)}")
    if not rows:raise RuntimeError("No GT-bearing scene")
    summaries=[summarize(rows,mode) for mode in ("Small","CRAFT_INT8")]
    for row in summaries:
        print("CRAFT_SMALL_REC_NEW_SCENE_RESULT",json.dumps(row,ensure_ascii=False),flush=True)
    (out/"summary.json").write_text(json.dumps(summaries,ensure_ascii=False,indent=2))
    with (out/"per_scene.csv").open("w",encoding="utf-8-sig",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]))
        writer.writeheader();writer.writerows(rows)
    (out/"limits.txt").write_text(
      "Predeclared ART/RCTW/ReCTS offset 800 public scenes. No claim these are untouched by all previous research; "
      "not real user camera pictures. Uses exact SHA-pinned CRAFT768 INT8 and PP-OCRv6 Small REC, "
      "against original Small DET+same REC. No GT-conditioned box selection. "
      "Whole-image ordered text edit rate penalizes insertions/deletions/substitutions "
      "but is extremely sensitive to GT annotation granularity and crude y,x reading order; "
      "not true intelligibility, word-level accuracy, or speech latency. "
      "Other metric geometric coverage CER proxy excludes some unmatched predictions. "
      "Linux CPU times not Android ARM or TTS; Java Bitmap crop warp can differ from OpenCV. "
      "No APK or production app changes.\n")
    print("CRAFT_SMALL_REC_NEW_SCENES_COMPLETE",len(rows),flush=True)

if __name__=="__main__":main()
