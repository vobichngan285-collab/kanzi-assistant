#!/usr/bin/env python3
"""Are new <.45 candidates NEW reading information versus already recognized .45 boxes?

Uses only already computed #315 detector and recognizer boxes, not photos.
The new candidate may overlap an old box or contain the same predicted text.
No GT is used to build the pre-recognition policies; ground truth is used
only for *evaluation*, never to select a candidate. Metrics are proxies.

Pre-recognition policies can reduce ONNX calls when implemented before SDK
recognition, unlike post-hoc recognition confidence filters.
"""
import argparse,collections,csv,json,math
from pathlib import Path

def load(f):
    with open(f,encoding="utf-8-sig",newline="") as fp:
        return list(csv.DictReader(fp))

def bboxes(s):
    a=[float(v) for v in s.split(",")]
    assert len(a)==4
    return a

def area(a):
    return max(0,a[2]-a[0])*max(0,a[3]-a[1])

def overlap(a,b):
    return max(0.,min(a[2],b[2])-max(a[0],b[0]))*max(0.,min(a[3],b[3])-max(a[1],b[1]))

def norm(s):
    return "".join(s.lower().split())

def annotate(proposals):
    grouped=collections.defaultdict(list)
    for row in proposals:
        d=dict(row)
        d["bbox4"]=bboxes(d["bbox"])
        d["score"]=float(d["box_score"])
        d["new"]=bool(int(d["is_new_below_045"]))
        d["exact"]=bool(int(d["ocr_exact_when_matched"]))
        d["matched"]=bool(int(d["gt_matched"]))
        d["rec_ms_num"]=float(d["ocr_ms"])
        grouped[d["split"],d["image"]].append(d)
    low=[]
    for key,rows in grouped.items():
        original=[r for r in rows if not r["new"]]
        for r in rows:
            if not r["new"]:continue
            scores=[]
            for high in original:
                inter=overlap(r["bbox4"],high["bbox4"])
                fraction=inter/max(1e-8,area(r["bbox4"]))
                jaccard=inter/max(1e-8,
                                 area(r["bbox4"])+area(high["bbox4"])-inter)
                text_a=norm(r["ocr_text"]);text_b=norm(high["ocr_text"])
                string_relation=("exact" if len(text_a)>=2 and text_a==text_b else
                   "contained" if len(text_a)>=2 and len(text_b)>=2 and
                      (text_a in text_b or text_b in text_a) else "different")
                if fraction>0 or jaccard>0:
                    scores.append((fraction,jaccard,string_relation,high))
            best=max(scores,key=lambda x:x[0],default=None)
            r["high_box_overlap_max"]=round(best[0],5) if best else 0.0
            r["high_box_IoU_max"]=round(best[1],5) if best else 0.0
            r["high_box_overlapping_text_relation"]=best[2] if best else "none"
            r["high_box_overlap_text"]=best[3]["ocr_text"] if best else ""
            r["already_recognized_overlapping_box"]=int(bool(best and best[0]>=.5))
            r["partial_overlap_existing_box"]=int(bool(best and best[0]>=.1))
            r["spatially_distinct_from_baseline"]=int(not best or best[0]<.1)
            low.append(r)
    return low

def selection(items,threshold,kind):
    # Only geometry + detector output is allowed before OCR.
    group=collections.defaultdict(list)
    for item in items:
        if item["score"]>=threshold:
            group[item["split"],item["image"]].append(item)
    out=[]
    for key,rows in group.items():
        rows.sort(key=lambda r:(-r["score"],r["candidate_id"]))
        if kind=="all":out+=rows
        elif kind=="spatially_distinct":
            out += [r for r in rows if r["high_box_overlap_max"]<.10]
        elif kind=="non_duplicate":
            out += [r for r in rows if r["high_box_overlap_max"]<.50]
        elif kind=="distinct_top1":
            out += [r for r in rows if r["high_box_overlap_max"]<.10][:1]
        elif kind=="distinct_top2":
            out += [r for r in rows if r["high_box_overlap_max"]<.10][:2]
        elif kind=="non_duplicate_top1":
            out += [r for r in rows if r["high_box_overlap_max"]<.50][:1]
        else:raise ValueError(kind)
    return out

def stats(rows,denom):
    images=collections.defaultdict(list)
    for x in rows:images[x["split"],x["image"]].append(x)
    ms=[sum(z["rec_ms_num"] for z in images.get(k,[])) for k in denom]
    n=[len(images.get(k,[])) for k in denom]
    def p95(a):
        a=sorted(a);return round(a[int((len(a)-1)*.95)],3)
    return {
      "selected_new_boxes":len(rows),
      "ground_truth_location_matches":sum(x["matched"] for x in rows),
      "ground_truth_exact_content":sum(x["exact"] for x in rows),
      "unmatched_unknown":sum(not x["matched"] for x in rows),
      "overlap_with_old_boxes_ge_50pct":sum(x["already_recognized_overlapping_box"] for x in rows),
      "overlap_with_old_boxes_ge_10pct":sum(x["partial_overlap_existing_box"] for x in rows),
      "overlapping_same_or_included_text":sum(x["high_box_overlapping_text_relation"] in ("exact","contained") for x in rows),
      "more_than_zero_extra_ocr_images":sum(x>0 for x in n),
      "additional_rec_calls_p95":p95(n),
      "additional_rec_inference_p95_ms":p95(ms),
      "additional_rec_inference_max_ms":round(max(ms),3),
      "total_extra_rec_inference_ms":round(sum(ms),3),
    }

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--boxes",required=True)
    p.add_argument("--per-image",required=True)
    p.add_argument("--out",required=True)
    args=p.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    proposals=load(args.boxes)
    lows=annotate(proposals)
    assert len(lows)==137,len(lows)
    images={(z["split"],z["image"]) for z in load(args.per_image)
            if float(z["threshold"])==.45}
    assert len(images)==216,len(images)
    grouped={}
    for region,lower,upper in [(".40-.45",.40,.45),(".35-.40",.35,.40),(".30-.35",.30,.35)]:
        data=[r for r in lows if lower<=r["score"]<upper]
        grouped[region]=stats(data,images)
        print("EXTRA_BAND_BASELINE_OVERLAP",json.dumps({"band":region,**grouped[region]},
              ensure_ascii=False),flush=True)
    rows=[]
    for thr in [.40,.35,.30]:
        for name in ["all","spatially_distinct","non_duplicate","distinct_top1",
                     "distinct_top2","non_duplicate_top1"]:
            selected=selection(lows,thr,name)
            result={"threshold":thr,"pre_OCR_filter":name,**stats(selected,images)}
            rows.append(result)
            print("PRE_RECOGNITION_SELECTIVE_AB",json.dumps(result,ensure_ascii=False),flush=True)
    # Concrete examples of would-be redundant scan outputs (not declaring actual FP).
    for r in sorted(lows,key=lambda x:x["high_box_overlap_max"],reverse=True)[:12]:
        print("HIGH_BOX_OVERLAP_EXAMPLE",json.dumps({
           "image":r["split"]+":"+r["image"],
           "new_score":r["score"],"covered_by_original":r["high_box_overlap_max"],
           "new_text":r["ocr_text"],"existing_text":r["high_box_overlap_text"],
           "exact_gt":r["exact"],"gt_spatial_match":r["matched"]},
           ensure_ascii=False),flush=True)
    with (out/"pre_recognition_policies.csv").open("w",encoding="utf-8-sig",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]))
        writer.writeheader();writer.writerows(rows)
    output=[{k:v for k,v in x.items() if k!="bbox4"}
            for x in lows]
    with (out/"all_137_candidate_existing_box_overlap.csv").open("w",encoding="utf-8-sig",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(output[0]))
        writer.writeheader();writer.writerows(output)
    (out/"limitations.txt").write_text(
       "Proxy filters use only detector score and overlap with existing high score boxes.\n"
       "Selection metrics are IN-SAMPLE, not validated on a held-out dataset.\n"
       "High-score boxes may also be wrong, so textual overlap does not prove redundancy.\n"
       "GT-unmatched boxes are UNKNOWN; some may contain real unlabeled words.\n"
       "P95 is cloud CPU recognizer inference, not USB Android latency.\n"
       "Threshold changes are not enabled in any software build.\n")
    print("BASELINE_OVERLAP_AND_PRE_REC_FILTER_AB=PASS",len(lows),flush=True)

if __name__=="__main__":main()
