#!/usr/bin/env python3
"""Audit ALL 137 low-score #315 detector proposals; resolve 111 annotation-unmatched.

Evidence:
- Original #315 model DET+REC candidate predictions from run 41492030817.
- REAL human scene-text annotations (ReCTS/RCTW/ArT) from public HF
  Yesianrohn/OCR-Data in exact image-index slices used in preceding study.
- No new OCR inference and no hidden ground truth used for a production filter.
- No images or user photos uploaded to GitHub artifacts.

"unmatched" is a detection matching property, NOT a false-positive label.
Geometric classes are automated HYPOTHESES, not visual confirmation.
"""
import argparse,collections,csv,json,math,re,time,unicodedata
from pathlib import Path
import requests

DATASET="Yesianrohn/OCR-Data"
SLICES={"ReCTS":[(0,24),(101,24),(310,24)],
        "RCTW":[(0,24),(135,24),(315,24)],
        "ART":[(0,24),(125,24),(295,24)]}
BASE=.45

def norm(s):
    return "".join(x for x in unicodedata.normalize("NFKC",str(s)).lower()
                   if not x.isspace())

def fetch_annotations():
    session=requests.Session()
    out={}
    for split,slices in SLICES.items():
        for start,n in slices:
            params={"dataset":DATASET,"config":"default","split":split,
                    "offset":start,"length":n}
            for attempt in range(4):
                try:
                    r=session.get("https://datasets-server.huggingface.co/rows",
                                  params=params,timeout=90)
                    r.raise_for_status()
                    rows=r.json().get("rows",[])
                    for entry in rows:
                        d=entry["row"]
                        gt=[]
                        for text,poly in zip(d.get("texts",[]),d.get("polygons",[])):
                            if not isinstance(text,str) or "###" in text or len(poly)<8:
                                continue
                            xs=[float(x) for x in poly[0::2]]
                            ys=[float(y) for y in poly[1::2]]
                            if not all(math.isfinite(x) for x in xs+ys):continue
                            bb=(min(xs),min(ys),max(xs),max(ys))
                            if bb[2]-bb[0]<6 or bb[3]-bb[1]<6:continue
                            gt.append({"text":text,"box":bb})
                        out[(split,str(entry["row_idx"]))]=gt
                    print("ANNOTATIONS",split,"offset",start,"read",len(rows),
                          "gt_regions",sum(len(out.get((split,str(x["row_idx"])),[]))
                                          for x in rows),flush=True)
                    break
                except Exception as e:
                    if attempt==3:raise
                    time.sleep(2*(attempt+1))
    return out

def area(b):
    return max(0.,b[2]-b[0])*max(0.,b[3]-b[1])

def intersection(a,b):
    return max(0.,min(a[2],b[2])-max(a[0],b[0]))*\
           max(0.,min(a[3],b[3])-max(a[1],b[1]))

def rectgap(a,b):
    dx=max(0.,max(a[0]-b[2],b[0]-a[2]))
    dy=max(0.,max(a[1]-b[3],b[1]-a[3]))
    return math.hypot(dx,dy)

def parse_bbox(s):
    vals=[float(x) for x in s.split(",")]
    assert len(vals)==4 and vals[2]>vals[0] and vals[3]>vals[1],s
    return vals

def pick_class(max_pred_cover,max_gt_cover,iou,overlaps,near):
    # Exclusive groups; this is solely a diagnostic geometric classification.
    if overlaps>=2:return "overlaps_multiple_annotations_potential_merge"
    if max_pred_cover>=.35 and iou<.4:
        if max_gt_cover<.4:return "annotation_fragment_potential_split"
        return "gt_overlap_low_iou_boundary_mismatch"
    if max_pred_cover>=.10 or max_gt_cover>=.10:
        return "slight_annotation_overlap"
    if near<=.75:
        return "near_annotated_text_no_overlap"
    return "far_from_annotated_text"

def relation(a,b):
    a=norm(a);b=norm(b)
    if len(a)<2 or len(b)<2:return "insufficient_text"
    if a==b:return "exact"
    if a in b:return "ocr_substring_of_near_gt"
    if b in a:return "gt_substring_of_ocr"
    return "different"

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--boxes",required=True)
    p.add_argument("--out",required=True)
    args=p.parse_args()
    dest=Path(args.out);dest.mkdir(parents=True,exist_ok=True)
    with open(args.boxes,encoding="utf-8-sig",newline="") as f:
        raw=list(csv.DictReader(f))
    low=[r for r in raw if int(r["is_new_below_045"])==1]
    assert len(low)==137, "unexpected exact model sample count; audit source mismatch"
    existing_matched=sum(int(r["gt_matched"]) for r in low)
    assert existing_matched==26,existing_matched
    gt=fetch_annotations()
    audited=[]
    for x in low:
        key=(x["split"],str(x["image"]))
        assert key in gt, f"missing GT {key}"
        bbox=parse_bbox(x["bbox"])
        gts=gt[key]
        stats=[]
        for item in gts:
            g=item["box"]
            inter=intersection(bbox,g)
            iou=inter/max(1e-10,area(bbox)+area(g)-inter)
            stats.append({"ref":item,"iou":iou,
                          "pred_cover":inter/max(1e-9,area(bbox)),
                          "gt_cover":inter/max(1e-9,area(g)),
                          "gap_norm":rectgap(bbox,g)/max(1.,g[3]-g[1])})
        best=max(stats,key=lambda z:z["iou"],default=None)
        nearest=min(stats,key=lambda z:z["gap_norm"],default=None)
        max_iou=max((z["iou"] for z in stats),default=0.)
        max_pcover=max((z["pred_cover"] for z in stats),default=0.)
        max_gtcover=max((z["gt_cover"] for z in stats),default=0.)
        # A proposal covers meaningful portions of TWO+ different GT annotations.
        meaningful=sum(z["pred_cover"]>=.08 and z["gt_cover"]>=.10 for z in stats)
        near=min((z["gap_norm"] for z in stats),default=999.)
        # Note an OCR result can be similar to a nearby GT even when box IoU fails.
        nearby=[z for z in stats if z["gap_norm"]<1.5 or z["iou"]>.0]
        relations=[(z["gap_norm"],relation(x["ocr_text"],z["ref"]["text"]),
                    z["ref"]["text"])
                   for z in nearby]
        supporting=[r for r in relations if r[1] in
                    ("exact","ocr_substring_of_near_gt","gt_substring_of_ocr")]
        supporting.sort(key=lambda z:z[0])
        cls=pick_class(max_pcover,max_gtcover,max_iou,meaningful,near)
        pred_len=len(norm(x["ocr_text"]))
        output={
          "split":x["split"],"image":x["image"],
          "candidate_id":x["candidate_id"],"bbox":x["bbox"],
          "box_score":float(x["box_score"]),
          "original_gt_matched":int(x["gt_matched"]),
          "best_iou":round(max_iou,5),
          "max_proposal_coverage_by_one_GT":round(max_pcover,5),
          "max_GT_coverage_by_proposal":round(max_gtcover,5),
          "meaningfully_overlapped_GT_count":meaningful,
          "closest_GT_distance_in_GT_height":round(near,4),
          "nearest_GT_text":nearest["ref"]["text"] if nearest else "",
          "nearest_GT_iou":round(nearest["iou"],4) if nearest else 0,
          "best_iou_GT_text":best["ref"]["text"] if best else "",
          "OCR_result":x["ocr_text"],
          "OCR_confidence":float(x["ocr_conf"]),
          "OCR_len":pred_len,
          "text_kind":x["text_kind"],
          "has_digit":int(any(c.isdigit() for c in x["ocr_text"])),
          "text_match_to_adjacent_GT":supporting[0][1] if supporting else "none",
          "matched_nearby_GT_text":supporting[0][2] if supporting else "",
          "geometric_hypothesis":cls,
          "review_state":"manual_image_review_required",
        }
        audited.append(output)
    unknown=[z for z in audited if not z["original_gt_matched"]]
    assert len(unknown)==111,len(unknown)

    groups=collections.Counter(x["geometric_hypothesis"] for x in unknown)
    gt_ref=collections.Counter(x["text_match_to_adjacent_GT"] for x in unknown)
    print("AUDIT_111_UNMATCHED_GEOMETRY",json.dumps(groups,ensure_ascii=False),flush=True)
    print("AUDIT_111_TEXT_RELATIONS",json.dumps(gt_ref,ensure_ascii=False),flush=True)
    for band,lower,upper in [(".40-.45",.40,.45),(".35-.40",.35,.40),(".30-.35",.30,.35)]:
        xs=[x for x in unknown if lower<=x["box_score"]<upper]
        print("AUDIT_BY_SCORE_BAND",json.dumps({
            "band":band,"count":len(xs),
            "geometric":dict(collections.Counter(x["geometric_hypothesis"] for x in xs)),
            "OCR_nonempty":sum(x["OCR_len"]>0 for x in xs),
            "OCR_ge2_chars":sum(x["OCR_len"]>=2 for x in xs),
            "OCR_ge2_conf75":sum(x["OCR_len"]>=2 and x["OCR_confidence"]>=.75 for x in xs),
            "exact_or_substring_near_GT":
                sum(x["text_match_to_adjacent_GT"] in
                     ("exact","ocr_substring_of_near_gt","gt_substring_of_ocr")
                    for x in xs),
        },ensure_ascii=False),flush=True)
    print("AUDIT_NESTED_OR_NEAR_EXAMPLES")
    for x in sorted(unknown,key=lambda x:(x["geometric_hypothesis"],
                      -x["max_proposal_coverage_by_one_GT"]))[:40]:
        print("UNKNOWN_CASE",json.dumps({
            "image":f'{x["split"]}:{x["image"]}',
            "band":round(x["box_score"],3),
            "class":x["geometric_hypothesis"],
            "iou":x["best_iou"],
            "proposal_covered":x["max_proposal_coverage_by_one_GT"],
            "text_relation":x["text_match_to_adjacent_GT"],
            "OCR":x["OCR_result"],"near_gt":x["nearest_GT_text"]},
            ensure_ascii=False),flush=True)

    for name,rows in [("all_137_audited.csv",audited),("111_unmatched_audited.csv",unknown)]:
        with (dest/name).open("w",newline="",encoding="utf-8-sig") as f:
            wr=csv.DictWriter(f,fieldnames=list(rows[0]))
            wr.writeheader();wr.writerows(rows)
    (dest/"summary.json").write_text(json.dumps({
       "source_run":41492030817,
       "deployed_default_box_threshold":BASE,
       "all_new_boxes":len(audited),"GT_location_matched":len(audited)-len(unknown),
       "GT_unmatched":len(unknown),
       "geometry_hypotheses":dict(groups),
       "text_relations":dict(gt_ref),
       "CAUTION":"Geometry categories are machine hypotheses, not manual true/false judgments. Unmatched != false positives.",
       "image_licensing":"public dataset image bytes never stored in uploaded artifacts",
    },ensure_ascii=False,indent=2))
    print("ALL_111_UNMATCHED_BOXES_AUDITED=PASS",len(unknown),flush=True)

if __name__=="__main__":main()
