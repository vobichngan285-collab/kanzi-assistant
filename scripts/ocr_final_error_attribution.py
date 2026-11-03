#!/usr/bin/env python3
"""Final forensic audit of why Small OCR GT lines fail IoU matching.

Re-run the EXISTING pinned Small DET + Small REC on precisely the 72 public
real-scene photos used in layout-vs-Small A/B. No new detector, ROI, threshold
tuning, Medium, VLM, Android modifications, or APK. Analyze GT-detector box
relationships with several fixed geometric diagnostics. Exact GT labels are
NEVER used to choose detections or crop predictions.
"""
import argparse, collections, csv, hashlib, json, math, time
from pathlib import Path
import cv2
import numpy as np
import onnxruntime as ort
import yaml
import ocr_box_value_deep_dive as sdk

DET_SHA="d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"
REC_SHA="5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
REC_YML_SHA="ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
IOU_THRESHOLD=.40
COMPARE_THRESHOLDS=(.20,.30,.40,.50)

def exact(path, digest):
    path=Path(path)
    actual=hashlib.sha256(path.read_bytes()).hexdigest()
    if actual!=digest:raise ValueError(f"SHA mismatch {path}: {actual}")
    return str(path)

def models(folder):
    p=Path(folder)
    opts=ort.SessionOptions()
    opts.intra_op_num_threads=2
    opts.inter_op_num_threads=1
    d=ort.InferenceSession(exact(p/"det.onnx",DET_SHA),opts,providers=["CPUExecutionProvider"])
    r=ort.InferenceSession(exact(p/"small_rec.onnx",REC_SHA),opts,providers=["CPUExecutionProvider"])
    cfg=yaml.safe_load(Path(exact(p/"small_rec.yml",REC_YML_SHA)).read_text())
    chars=list(cfg["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    return d,r,chars

def gt_items(texts,polys):
    result=[]
    for label,poly in zip(texts,polys):
        if not isinstance(label,str) or "###" in label:continue
        try:points=np.asarray(poly,np.float32).reshape(-1,2)
        except (TypeError, ValueError):continue
        if len(points)<4 or not np.isfinite(points).all():continue
        bb=sdk.bbox_from_quad(points)
        if bb[2]-bb[0]<6 or bb[3]-bb[1]<6:continue
        normalized=sdk.normalize(label)
        if not normalized:continue
        result.append({"box":bb,"truth":label,"normal":normalized})
    return result

def overlap_rect(a,b):
    left=max(a[0],b[0]);top=max(a[1],b[1])
    right=min(a[2],b[2]);bottom=min(a[3],b[3])
    if right<=left or bottom<=top:return None
    return (left,top,right,bottom)

def rect_area(a):
    return max(0.,a[2]-a[0])*max(0.,a[3]-a[1])

def union_covered_gt(gtbox, candidateboxes):
    """Exact intersection-union area of detector axis-aligned boxes within one GT box."""
    clipped=[v for bb in candidateboxes if (v:=overlap_rect(gtbox,bb)) is not None]
    if not clipped:return 0.
    xcoords=sorted(set(x for b in clipped for x in (b[0],b[2])))
    covered=0.
    for a,b in zip(xcoords,xcoords[1:]):
        if b<=a:continue
        intervals=sorted((box[1],box[3]) for box in clipped if box[0]<b and box[2]>a)
        if not intervals:continue
        y0,y1=intervals[0];ysize=0.
        for lo,hi in intervals[1:]:
            if lo>y1:
                ysize+=y1-y0
                y0,y1=lo,hi
            else:y1=max(y1,hi)
        ysize+=y1-y0
        covered+=(b-a)*ysize
    return min(1.,covered/max(1e-9,rect_area(gtbox)))

def greedy_match(boxes,gts,threshold):
    edges=[]
    for i,pred in enumerate(boxes):
        for j,gt in enumerate(gts):
            val=sdk.box_iou(pred["bbox"],gt["box"])
            if val>=threshold:edges.append((val,i,j))
    edges.sort(reverse=True)
    used_i=set();used_j=set();assign={}
    for val,i,j in edges:
        if i in used_i or j in used_j:continue
        assign[j]=(i,val)
        used_i.add(i);used_j.add(j)
    return assign

def heat_response(prob,box,original_hw):
    # Raw DET probability map is a diagnostic of text-like response,
    # not proof that a candidate would survive threshold / postprocess.
    h,w=original_hw
    ph,pw=prob.shape
    x1=max(0,int(math.floor(box[0]*pw/w)))
    x2=min(pw,int(math.ceil(box[2]*pw/w)))
    y1=max(0,int(math.floor(box[1]*ph/h)))
    y2=min(ph,int(math.ceil(box[3]*ph/h)))
    if x2<=x1 or y2<=y1:return 0.,0.
    arr=prob[y1:y2,x1:x2]
    return float(np.percentile(arr,95)),float(np.mean(arr>.20))

def geometry(gtbox,boxes):
    if not boxes:
        return {"max_iou":0.,"max_gt_overlap":0.,"union_gt_overlap":0.,
                "overlap_boxes":0,"center_in_any":False,"large_covering":False}
    overlaps=[]
    max_iou=0.;max_single=0.;center=False;large=False
    area=max(1e-9,rect_area(gtbox))
    cx=(gtbox[0]+gtbox[2])/2;cy=(gtbox[1]+gtbox[3])/2
    for pred in boxes:
        bb=pred["bbox"]
        intersection=overlap_rect(gtbox,bb)
        max_iou=max(max_iou,sdk.box_iou(gtbox,bb))
        if intersection is not None:
            fraction=rect_area(intersection)/area
            overlaps.append((bb,fraction))
            max_single=max(max_single,fraction)
            if fraction>=.70 and rect_area(bb)>2.0*area:large=True
        if bb[0]<=cx<=bb[2] and bb[1]<=cy<=bb[3]:center=True
    return {"max_iou":max_iou,"max_gt_overlap":max_single,
            "union_gt_overlap":union_covered_gt(gtbox,[x[0] for x in overlaps]),
            "overlap_boxes":len(overlaps),"center_in_any":center,
            "large_covering":large}

def diagnostic_bucket(strict_pair, geom):
    if strict_pair:return "matched_iou_040"
    if geom["max_iou"]>=IOU_THRESHOLD:return "one_to_one_matching_conflict"
    if geom["overlap_boxes"]==0:return "no_geometric_overlap"
    if geom["large_covering"] or geom["union_gt_overlap"]>=.80:
        return "mostly_covered_but_iou_mismatch"
    if geom["overlap_boxes"]>=2 and geom["union_gt_overlap"]>=.25:
        return "multiple_fragmented_overlap"
    return "partial_or_weak_overlap"

def aggregate(rows,cohort):
    a=[r for r in rows if r["cohort"]==cohort]
    gt=len(a);matched=[r for r in a if r["matched_iou_040"]]
    missed=[r for r in a if not r["matched_iou_040"]]
    counts=collections.Counter(r["bucket"] for r in missed)
    mismatch_chars=sum(int(r["gt_chars"]) for r in missed)
    edits=sum(int(r["rec_edits"]) for r in matched)
    matched_chars=sum(int(r["gt_chars"]) for r in matched)
    allchars=sum(int(r["gt_chars"]) for r in a)
    return {
      "cohort":cohort,"gt_lines":gt,"strict_iou_040_matched":len(matched),
      "strict_iou_040_unmatched":len(missed),
      "miss_bucket_counts":dict(sorted(counts.items())),
      "unmatched_with_ANY_box_overlap":sum(r["overlap_boxes"]>0 for r in missed),
      "unmatched_with_gt_coverage_ge_025":sum(r["union_gt_overlap"]>=.25 for r in missed),
      "unmatched_with_gt_coverage_ge_050":sum(r["union_gt_overlap"]>=.50 for r in missed),
      "unmatched_with_gt_coverage_ge_075":sum(r["union_gt_overlap"]>=.75 for r in missed),
      "unmatched_with_center_inside_detector":sum(bool(r["center_in_any"]) for r in missed),
      "unmatched_with_heat_p95_ge_020":sum(r["heat_p95"]>=.20 for r in missed),
      "unmatched_with_heat_fraction_ge_005":sum(r["heat_area_above_020"]>=.05 for r in missed),
      "matched_recognizer_exact":sum(r["rec_edits"]==0 for r in matched),
      "matched_recognizer_inexact":sum(r["rec_edits"]>0 for r in matched),
      "matched_rec_CER":round(edits/max(1,matched_chars),5),
      "matched_rec_edits":edits,
      "unmatched_gt_chars":mismatch_chars,
      "gt_chars":allchars,
      "coverage_aware_CER_proxy":round((edits+mismatch_chars)/max(1,allchars),5),
      "unmatched_gt_share_of_proxy_errors":round(mismatch_chars/max(1,edits+mismatch_chars),5),
      "sensitivity_matched_per_iou":{
          str(thresh):sum(bool(r[f"matched_iou_{int(thresh*100):03d}"]) for r in a)
          for thresh in COMPARE_THRESHOLDS
      }
    }

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--models",required=True)
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    det,rec,chars=models(args.models)
    sdk.SLICES={x:[(0,12),(500,12)] for x in ("ReCTS","RCTW","ART")}
    sdk.THRESHOLDS=[.45]
    sdk.DET_BITMAP_THRESH=.20
    sdk.DET_UNCLIP=1.40
    images=sdk.collect_images()
    assert len(images)>=60, f"insufficient public photo cohort: {len(images)}"
    rows=[];photo_metrics=[]
    for n,(split,index,rgb,texts,polys) in enumerate(images,1):
        cohort="DEV" if index<100 else "HOLDOUT"
        gts=gt_items(texts,polys)
        if not gts:continue
        height,width=rgb.shape[:2]
        pre=sdk.det_preprocess(rgb)
        raw=det.run(None,{det.get_inputs()[0].name:pre})[0]
        prob=raw.reshape(raw.shape[-2:])
        boxes=[b for b in sdk.detections(prob,height,width) if b["score"]>=.45]
        pairings={thr:greedy_match(boxes,gts,thr) for thr in COMPARE_THRESHOLDS}
        rec_predictions={}
        for j,(i,iou) in pairings[.40].items():
            crop=sdk.crop_box(rgb,boxes[i])
            if crop is None:
                rec_predictions[j]=("",len(gts[j]["normal"]))
                continue
            # Same BGR recognizer input as the preceding layout A/B study.
            bgr=cv2.cvtColor(crop,cv2.COLOR_RGB2BGR)
            pred,confidence,ms=sdk.rec(
                rec,rec.get_inputs()[0].name,chars,bgr)
            rec_predictions[j]=(pred,sdk.distance(gts[j]["normal"],sdk.normalize(pred)))
        for j,g in enumerate(gts):
            meta=geometry(g["box"],boxes)
            strict=j in pairings[.40]
            hp,hr=heat_response(prob,g["box"],(height,width))
            pred,edits=rec_predictions.get(j,("",-1))
            row={
              "cohort":cohort,"split":split,"image":index,"gt_id":j,
              "gt_text":g["truth"][:80],"gt_chars":len(g["normal"]),
              "gt_box":",".join(str(round(x,2)) for x in g["box"]),
              "detected_box_count":len(boxes),
              "bucket":diagnostic_bucket(strict,meta),
              "heat_p95":round(hp,5),"heat_area_above_020":round(hr,5),
              "rec_text":pred[:120],"rec_edits":edits,
            }
            for th in COMPARE_THRESHOLDS:
                row[f"matched_iou_{int(th*100):03d}"]=int(j in pairings[th])
            for k,v in meta.items():
                row[k]=round(v,5) if isinstance(v,float) else v
            rows.append(row)
        photo_metrics.append(dict(cohort=cohort,split=split,index=index,
                  gt_lines=len(gts),detected_boxes=len(boxes),
                  matched_gt=len(pairings[.40])))
        if n%12==0:
            print("OCR_FINAL_DIAGNOSTIC_PROGRESS",n,"of",len(images),
                  "gt",len(rows),flush=True)
    assert len(rows)>400,len(rows)
    assert len([r for r in rows if r["cohort"]=="HOLDOUT"])>=230
    summaries=[aggregate(rows,cohort) for cohort in ("DEV","HOLDOUT")]
    for x in summaries:
        print("OCR_FINAL_ERROR_ATTRIBUTION",json.dumps(x,ensure_ascii=False),flush=True)
    for name,items in (("per_gt_diagnostics.csv",rows),
                       ("per_photo_summary.csv",photo_metrics)):
        with (out/name).open("w",newline="",encoding="utf-8-sig") as f:
            w=csv.DictWriter(f,fieldnames=list(items[0]))
            w.writeheader();w.writerows(items)
    (out/"summary.json").write_text(json.dumps(summaries,ensure_ascii=False,indent=2))
    for cohort in ("DEV","HOLDOUT"):
        sample={}
        for r in (x for x in rows if x["cohort"]==cohort and x["bucket"]!="matched_iou_040"):
            sample.setdefault(r["bucket"],[])
            if len(sample[r["bucket"]])<4:
                sample[r["bucket"]].append({
                  "split":r["split"],"image":r["image"],
                  "GT":r["gt_text"][:35],"max_iou":r["max_iou"],
                  "union_covered":r["union_gt_overlap"]})
        (out/f"examples_{cohort.lower()}.json").write_text(
            json.dumps(sample,ensure_ascii=False,indent=2))
    (out/"limitations.txt").write_text(
      "Only 72 already-used public scene photos, not an untouched prospective sample.\n"
      "All geometry uses axis-aligned bounding boxes (AABB) of real GT polygons, "
      "not polygon-to-polygon matching. IoU 0.4 is a convention, not a declaration "
      "that the OCR could or could not read the text.\n"
      "No-geometric-overlap means no prediction AABB touches that GT AABB, "
      "not necessarily model failure (e.g. incorrect GT polygon, low-contrast text).\n"
      "Partial/fragmentation/oversized buckets are geometry proxies, not confirmed "
      "causal diagnoses. GT may be missing labels.\n"
      "For BGR input, rec measurements reproduce the prior layout Small A/B's "
      "explicit BGR choice, but don't establish full Java SDK color parity.\n"
      "Coverage-aware CER proxy counts missed GT text as deletions but not all "
      "unmatched OCR predictions as insertions. Not literal page CER.\n"
      "Only exact pinned Small DET/REC and original 0.45 threshold used; no "
      "new model, crop optimization, Android execution, API integration or APK.\n")
    print("OCR_FINAL_ATTRIBUTION_COMPLETE",len(photo_metrics),len(rows),flush=True)

if __name__=="__main__":main()
