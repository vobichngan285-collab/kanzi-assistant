#!/usr/bin/env python3
"""Small-first, evidence-triggered local CRAFT768 INT8 OCR rescue A/B.

Test three fixed strategies with IDENTICAL PP-OCRv6 Small REC:
  A: Small DET + Small REC only (production baseline simulation).
  B: full-image CRAFT768 INT8 DET + same Small REC (earlier research candidate).
  C: Small DET+REC, run CRAFT768 INT8 on at most two automatically chosen
     small local ROIs, transfer geometry to original image, suppress overlaps,
     recognize only novel candidates via the same Small REC.

ROI selection is deterministic, uses ONLY source pixels and Small DET boxes:
source resolution, Canny edge energy in uncovered areas, and Small text anchors.
It NEVER looks at GT text/boxes to select a crop or a prediction. C uses an
explicit minimum 2 normalized-character length and geometric duplicate gate.

No Android integration, no APK, no TTS. Same 36 predeclared public photos at
source offset 1100 (new-to-this-study, not a novel blind dataset) plus optional
research-only diagnostic 36 photos from offset 800.
"""
import argparse,collections,csv,hashlib,json,math,statistics,time
from pathlib import Path
import cv2
import numpy as np
import craft_small_detector_ab as craft
import ocr_box_value_deep_dive as sdk
from craft768_int8_smallrec_newscene_fullpage_ab import page_text,ground_truth_text

Q_SHA="9ab885d2f6669b77f70e9c8f68acfbe408c0b051501fd02d94bcaa0c7779ab15"
COHORTS={
    "DIAGNOSTIC_800":{x:[(800,12)] for x in ("ART","RCTW","ReCTS")},
    "CHECK_1100":{x:[(1100,12)] for x in ("ART","RCTW","ReCTS")},
}
MODES=("SMALL","CRAFT_FULL","SMALL_LOCAL_RESCUE")
MAX_ROIS=2
MAX_EXTRA_BOXES=16

def bbox_iou(a,b):
    return sdk.box_iou(a,b)

def median_small_height(boxes):
    return statistics.median(max(0.,b["bbox"][3]-b["bbox"][1]) for b in boxes) if boxes else None

def candidate_windows(width,height):
    """Overlapping windows 60% of source width x 40% of source height."""
    cw=min(width,max(480,int(round(width*.60))))
    ch=min(height,max(480,int(round(height*.40))))
    starts_x=sorted(set(int(round((width-cw)*r)) for r in (0.,.5,1.)))
    starts_y=sorted(set(int(round((height-ch)*r)) for r in (0.,.5,1.)))
    return [(x,y,x+cw,y+ch) for y in starts_y for x in starts_x]

def choose_rois(rgb,small_boxes):
    """GT-FREE predeclared trigger and text-nearby uncovered edge ROI selection."""
    height,width=rgb.shape[:2]
    median_height=median_small_height(small_boxes)
    reason="trigger"
    if max(height,width)<1100:
        reason="image_short"
    elif len(small_boxes)<4:
        reason="too_few_small_boxes"
    elif median_height>55:
        reason="not_small_print"
    if reason!="trigger":return [],dict(reason=reason,median_height=median_height,
                                      small_boxes=len(small_boxes),best_density=0.)
    thumb_ratio=min(1.,720./max(width,height))
    tw=max(1,int(round(width*thumb_ratio)))
    th=max(1,int(round(height*thumb_ratio)))
    small=cv2.resize(rgb,(tw,th),interpolation=cv2.INTER_AREA)
    gray=cv2.cvtColor(small,cv2.COLOR_RGB2GRAY)
    edges=cv2.Canny(gray,50,140)
    coverage=np.zeros((th,tw),dtype=np.uint8)
    for b in small_boxes:
        x0,y0,x1,y1=b["bbox"]
        px0=max(0,int((x0-4)*tw/width))
        py0=max(0,int((y0-3)*th/height))
        px1=min(tw,int(math.ceil((x1+4)*tw/width)))
        py1=min(th,int(math.ceil((y1+3)*th/height)))
        if px1>px0 and py1>py0:coverage[py0:py1,px0:px1]=1
    uncovered=np.logical_and(edges>0,coverage==0)
    candidates=[]
    for roi in candidate_windows(width,height):
        x0,y0,x1,y1=roi
        cx0=max(0,int(x0*tw/width));cy0=max(0,int(y0*th/height))
        cx1=min(tw,int(math.ceil(x1*tw/width)))
        cy1=min(th,int(math.ceil(y1*th/height)))
        coverage_area=max(1,(cx1-cx0)*(cy1-cy0))
        density=float(np.count_nonzero(uncovered[cy0:cy1,cx0:cx1]))/coverage_area
        anchors=sum(
            x0 <= (b["bbox"][0]+b["bbox"][2])*.5 <= x1 and
            y0 <= (b["bbox"][1]+b["bbox"][3])*.5 <= y1
            for b in small_boxes)
        # Only inspect near recognizable text; image texture alone is too noisy.
        if anchors<2 or density<.015:continue
        score=density*(1+min(anchors,8)*.05)
        candidates.append((score,anchors,density,roi))
    candidates.sort(key=lambda e:(-e[0],-e[1],e[3]))
    selected=[]
    for score,anchors,density,roi in candidates:
        if any(bbox_iou(roi,prior["roi"])>.35 for prior in selected):continue
        selected.append(dict(roi=roi,score=round(score,5),anchors=anchors,
                             uncovered_edge_density=round(density,5)))
        if len(selected)>=MAX_ROIS:break
    return selected,dict(reason="trigger" if selected else "no_eligible_edges",
                         median_height=median_height,small_boxes=len(small_boxes),
                         best_density=round(candidates[0][2],5) if candidates else 0.)

def roi_to_original(box,roi,zoom,source_hw):
    img_h,img_w=source_hw
    x0,y0,x1,y1=roi
    quad=box["quad"].copy().astype(np.float32)
    quad[:,0]=np.clip(quad[:,0]/zoom+x0,0,img_w)
    quad[:,1]=np.clip(quad[:,1]/zoom+y0,0,img_h)
    bbox=sdk.bbox_from_quad(quad)
    return dict(quad=quad,bbox=bbox,score=box["score"],engine="CRAFT_LOCAL")

def near_artificial_edge(box,roi):
    x0,y0,x1,y1=roi
    bx0,by0,bx1,by1=box["bbox"]
    margin=min(8,max(3,int(min(x1-x0,y1-y0)*.008)))
    # Do not reject text on actual source image edge; caller supplies extended ROI
    return (bx0<x0+margin or by0<y0+margin or
            bx1>x1-margin or by1>y1-margin)

def redundant(candidate,prior):
    bb=candidate["bbox"]
    for existing in prior:
        old=existing["bbox"]
        overlap=craft.overlap(bb,old)
        if sdk.box_iou(bb,old)>=.30 or overlap/min(craft.area(bb),craft.area(old))>=.50:
            return True
    return False

def local_detect(craft_session,rgb,small_boxes):
    rois,trigger=choose_rois(rgb,small_boxes)
    proposals=[]
    run_ms=0.
    seen=0
    edge_rejected=0
    duplicate_rejected=0
    for spec in rois:
        x0,y0,x1,y1=spec["roi"]
        crop=rgb[y0:y1,x0:x1]
        # Explicit magnification for crops smaller than 768. Larger crops
        # still receive substantially more input pixels per source character.
        factor=max(1.,768./max(crop.shape[:2]))
        if factor>1.0:
            crop=cv2.resize(crop,
                   (max(1,int(round(crop.shape[1]*factor))),
                    max(1,int(round(crop.shape[0]*factor)))),
                   interpolation=cv2.INTER_CUBIC)
        boxes,ms,_=craft.craft_detect(craft_session,crop)
        run_ms+=ms
        seen+=len(boxes)
        for box in boxes:
            projected=roi_to_original(box,spec["roi"],factor,rgb.shape[:2])
            if near_artificial_edge(projected,spec["roi"]):
                edge_rejected+=1;continue
            if redundant(projected,small_boxes+proposals):
                duplicate_rejected+=1;continue
            proposals.append(projected)
    proposals.sort(key=lambda x:x["score"],reverse=True)
    return proposals[:MAX_EXTRA_BOXES],dict(
        trigger=trigger,roi_count=len(rois),ROI_specs=rois,
        local_craft_infer_ms=round(run_ms,2),
        craft_boxes_before_filter=seen,edge_rejected=edge_rejected,
        duplicate_rejected=duplicate_rejected,
        extra_candidates_before_cap=len(proposals),
        extra_candidates_after_cap=min(len(proposals),MAX_EXTRA_BOXES))

def recognized_new(rec,chars,rgb,boxes):
    proposed=craft.rec_predictions(rec,chars,rgb,boxes)
    accepted=[];text=[]
    filtered_short=0
    for i,box in enumerate(boxes):
        pred=proposed.get(i,"")
        if len(sdk.normalize(pred))<2:
            filtered_short+=1
            continue
        accepted.append(box);text.append(pred)
    return accepted,text,filtered_short

def evaluate(boxes,preds,gts):
    score=craft.score_pair(boxes,preds,gts)
    return score,craft.box_pairs(boxes,gts)

def percentile(values,p):
    if not values:return None
    xs=sorted(values)
    return round(xs[int((len(xs)-1)*p)],2)

def stats(rows,mode):
    gtchars=sum(r["gt_chars"] for r in rows)
    gtlines=sum(r["gt_lines"] for r in rows)
    value=lambda suffix:sum(r[mode+"_"+suffix] for r in rows)
    times=[r[mode+"_total_ms"] for r in rows]
    edits=value("fullpage_edits")
    return dict(mode=mode,photos=len(rows),gt_lines=gtlines,gt_chars=gtchars,
        matched_gt_lines=value("matched"),matched_fraction=round(value("matched")/max(1,gtlines),5),
        unmatched_gt_chars=value("missed_chars"),
        coverage_CER_proxy=round(value("proxy_errors")/max(1,gtchars),5),
        wholepage_ordered_edit_rate=round(edits/max(1,gtchars),5),
        wholepage_ordered_edits=edits,
        predicted_characters=value("pred_chars"),
        unmatched_recognized_boxes=value("unmatched_text_boxes"),
        total_detected_boxes=value("detected_boxes"),
        mean_complete_ms=round(sum(times)/len(times),2),
        p50_complete_ms=percentile(times,.5),
        p95_complete_ms=percentile(times,.95))

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--quant",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--cohort",choices=list(COHORTS),default="CHECK_1100")
    p.add_argument("--max-photos",type=int,default=0)
    args=p.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    assert hashlib.sha256(Path(args.quant).read_bytes()).hexdigest()==Q_SHA
    craft.CRAFT_LONG_SIDE=768
    small,rec,_,chars=craft.get_models(args.models)
    craftq=craft.model(args.quant,Q_SHA)
    sdk.SLICES=COHORTS[args.cohort]
    sdk.THRESHOLDS=[.45]
    sdk.DET_BITMAP_THRESH=.20
    sdk.DET_UNCLIP=1.40
    photos=sdk.collect_images()
    if not args.max_photos and len(photos)!=36:
        raise RuntimeError(f"Expected 36 predeclared photos, got {len(photos)}")
    if args.max_photos:photos=photos[:args.max_photos]
    if len(photos)<(3 if args.max_photos else 36):
        raise RuntimeError(f"Not enough input photos {len(photos)}")
    rows=[]
    for counter,(dataset,row_idx,rgb,texts,polys) in enumerate(photos,1):
        gt=craft.gt_rows(texts,polys)
        if not gt:continue
        ground=ground_truth_text(gt)
        record=dict(cohort=args.cohort,dataset=dataset,index=row_idx,
                    image_width=rgb.shape[1],image_height=rgb.shape[0],
                    portrait=rgb.shape[0]>rgb.shape[1],
                    gt_lines=len(gt),gt_chars=len(ground))
        # A: baseline. Record and reuse exactly the same Small predictions.
        start=time.perf_counter()
        small_boxes,small_det_ms=craft.small_detect(small,rgb)
        small_predictions=craft.rec_predictions(rec,chars,rgb,small_boxes)
        small_ms=(time.perf_counter()-start)*1000
        score_a,pairs_a=evaluate(small_boxes,small_predictions,gt)

        # B: full frame CRAFT-only control (no crop, no Small boxes).
        start=time.perf_counter()
        full_boxes,full_det_ms,_=craft.craft_detect(craftq,rgb)
        full_preds=craft.rec_predictions(rec,chars,rgb,full_boxes)
        full_ms=(time.perf_counter()-start)*1000
        score_b,pairs_b=evaluate(full_boxes,full_preds,gt)

        # C: proposed novelty from only source pixels and Small candidate boxes.
        # No GT fed to ROI proposal, filter, or Small REC recognition.
        start=time.perf_counter()
        roi_candidates,roi_meta=local_detect(craftq,rgb,small_boxes)
        novel_boxes,novel_text,short_rejected=recognized_new(rec,chars,rgb,roi_candidates)
        added_base=len(small_boxes)
        merged_boxes=small_boxes+novel_boxes
        merged_preds=dict(small_predictions)
        merged_preds.update({added_base+j:value for j,value in enumerate(novel_text)})
        local_ms=(time.perf_counter()-start)*1000
        score_c,pairs_c=evaluate(merged_boxes,merged_preds,gt)

        before=set(pairs_a.values())
        after=set(pairs_c.values())
        zero_gt=[i for i,g in enumerate(gt) if not any(
            craft.overlap(b["bbox"],g["box"])>0 for b in small_boxes)]
        newly_matched=after-before
        no_longer_matched=before-after
        for mode,boxes,preds,score,elapsed,detms in (
            ("SMALL",small_boxes,small_predictions,score_a,small_ms,small_det_ms),
            ("CRAFT_FULL",full_boxes,full_preds,score_b,full_ms,full_det_ms),
            ("SMALL_LOCAL_RESCUE",merged_boxes,merged_preds,score_c,small_ms+local_ms,
             small_det_ms+roi_meta["local_craft_infer_ms"])):
            for key,value in score.items():record[mode+"_"+key]=value
            predicted=page_text(boxes,preds)
            record[mode+"_fullpage_edits"]=sdk.distance(predicted,ground)
            record[mode+"_pred_chars"]=len(predicted)
            record[mode+"_total_ms"]=round(elapsed,2)
            record[mode+"_detector_ms"]=round(detms,2)
        record.update(
            roi_trigger=roi_meta["trigger"]["reason"],
            small_box_median_height=roi_meta["trigger"]["median_height"],
            roi_count=roi_meta["roi_count"],
            new_boxes_in_roi=roi_meta["extra_candidates_after_cap"],
            newly_recognized_boxes=len(novel_boxes),
            short_text_filtered=short_rejected,
            local_craft_detection_ms=roi_meta["local_craft_infer_ms"],
            roi_total_extra_ms=round(local_ms,2),
            roi_duplicate_rejected=roi_meta["duplicate_rejected"],
            roi_edge_rejected=roi_meta["edge_rejected"],
            rescued_gt_lines=len(newly_matched),
            lost_original_gt_lines=len(no_longer_matched),
            rescued_zero_overlap_gt=sum(i in newly_matched for i in zero_gt),
            zero_overlap_gt_available=len(zero_gt),
            local_extra_matched_characters=sum(len(gt[j]["normal"]) for j in newly_matched),
            source_small_boxes=len(small_boxes))
        rows.append(record)
        if counter%6==0:
            print("CRAFT_LOCAL_ROI_PROGRESS",counter,"/",len(photos),
                  "dataset",dataset,"row",row_idx,
                  "rois",roi_meta["roi_count"],"add",len(novel_boxes),
                  "GT Small/Full/Local",
                  score_a["matched"],score_b["matched"],score_c["matched"],
                  "pageEd Small/Full/Local",
                  record["SMALL_fullpage_edits"],
                  record["CRAFT_FULL_fullpage_edits"],
                  record["SMALL_LOCAL_RESCUE_fullpage_edits"],flush=True)
    if len(rows)<(3 if args.max_photos else 36):
        raise RuntimeError(f"Too few scored photos {len(rows)}")
    summary=[stats(rows,mode) for mode in MODES]
    general=dict(cohort=args.cohort,photos=len(rows),
      roi_triggered_images=sum(r["roi_count"]>0 for r in rows),
      rois_executed=sum(r["roi_count"] for r in rows),
      added_recognized_boxes=sum(r["newly_recognized_boxes"] for r in rows),
      duplicate_boxes_rejected_before_recognition=sum(r["roi_duplicate_rejected"] for r in rows),
      roi_border_boxes_rejected=sum(r["roi_edge_rejected"] for r in rows),
      short_ocr_text_rejected=sum(r["short_text_filtered"] for r in rows),
      gained_gt_lines=sum(r["rescued_gt_lines"] for r in rows),
      lost_gt_lines=sum(r["lost_original_gt_lines"] for r in rows),
      rescued_zero_overlap_gt=sum(r["rescued_zero_overlap_gt"] for r in rows),
      total_zero_overlap_gt=sum(r["zero_overlap_gt_available"] for r in rows),
      average_extra_pipeline_ms=round(sum(r["roi_total_extra_ms"] for r in rows)/len(rows),2),
      dataset_breakdown={dataset:dict(
         photos=sum(r["dataset"]==dataset for r in rows),
         rois=sum(r["roi_count"] for r in rows if r["dataset"]==dataset),
         small_gt=sum(r["SMALL_matched"] for r in rows if r["dataset"]==dataset),
         full_gt=sum(r["CRAFT_FULL_matched"] for r in rows if r["dataset"]==dataset),
         hybrid_gt=sum(r["SMALL_LOCAL_RESCUE_matched"] for r in rows if r["dataset"]==dataset),
         small_page_edits=sum(r["SMALL_fullpage_edits"] for r in rows if r["dataset"]==dataset),
         hybrid_page_edits=sum(r["SMALL_LOCAL_RESCUE_fullpage_edits"] for r in rows if r["dataset"]==dataset))
         for dataset in ("ART","RCTW","ReCTS")}}
    for row in summary:
        print("CRAFT_LOCAL_ROI_AB_RESULT",json.dumps(row,ensure_ascii=False),flush=True)
    print("CRAFT_LOCAL_ROI_META",json.dumps(general,ensure_ascii=False),flush=True)
    (out/"summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2))
    (out/"meta.json").write_text(json.dumps(general,ensure_ascii=False,indent=2))
    with (out/"per_photo.csv").open("w",encoding="utf-8-sig",newline="") as fp:
        wr=csv.DictWriter(fp,fieldnames=list(rows[0]));wr.writeheader();wr.writerows(rows)
    (out/"limitations.txt").write_text(
      "Predeclared 36 scene images at offset 1100 per split, same public dataset used in prior research; "
      "not a new independently curated blind set or real user phone pictures. "
      "No GT is used to choose ROIs. Trigger uses Canny edges, Small boxes, and source resolution only. "
      "CRAFT local crops may be resized relative to the whole image. Max 2 CRAFT ROIs per triggered image, "
      "max 16 non-redundant candidates; proposals require >=2 decoded characters. "
      "Detection coverage is AABB IoU>=.4 proxy and may penalize differently segmented text; "
      "coverage CER proxy omits unmatched insertions. Whole-page normalized Levenshtein includes "
      "unmatched text but is sensitive to simple reading order and annotation style. "
      "Linux x86 CPU timing not Android ARM memory/heat, nor UVC or TTS first word. "
      "No Android modification or APK. Results cannot be post-hoc tuned on this CHECK set.\n")
    print("CRAFT_LOCAL_ROI_AB_COMPLETE",len(rows),flush=True)

if __name__=="__main__":main()
