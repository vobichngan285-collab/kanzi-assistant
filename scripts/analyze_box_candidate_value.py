#!/usr/bin/env python3
"""Read original exact-model candidate OCR CSV; estimate extra cost and filtering.

Does not re-download images. Filters use only candidate score, recognition
confidence/length (NOT truth labels) as they would be deployable later.
Human GT is ONLY used to evaluate each policy; never to select outputs.

An unmatched candidate might be correct unannotated text. Never label it FP.
"""
import collections,csv,json,statistics,sys
from pathlib import Path

source=Path(sys.argv[1]);image_csv=Path(sys.argv[2]);dest=Path(sys.argv[3])
dest.mkdir(parents=True,exist_ok=True)

def load(f):
    with open(f,encoding="utf-8-sig",newline="") as fd:return list(csv.DictReader(fd))
def quant(a,p):
    if not a:return None
    a=sorted(a)
    return round(a[int((len(a)-1)*p)],2)

boxes=load(source)
images=load(image_csv)
imgs={(r["split"],r["image"]) for r in images if r["threshold"]=="0.45"}
counts=collections.defaultdict(list)
for row in boxes:
    k=(row["split"],row["image"])
    row["box_score"]=float(row["box_score"])
    row["ocr_conf"]=float(row["ocr_conf"])
    row["ocr_ms"]=float(row["ocr_ms"])
    row["text_len"]=int(row["text_len"])
    row["gt_matched"]=int(row["gt_matched"])
    row["is_new_below_045"]=int(row["is_new_below_045"])
    row["ocr_exact_when_matched"]=int(row["ocr_exact_when_matched"])
    counts[k].append(row)
assert len(imgs)>=100, len(imgs)

def sample(selected):
    per=collections.defaultdict(lambda:{"ms":0.,"n":0})
    for r in selected:
        x=per[r["split"],r["image"]]
        x["ms"]+=r["ocr_ms"];x["n"]+=1
    all_ms=[round(per[k]["ms"],3) for k in imgs]
    n_per=[per[k]["n"] for k in imgs]
    exact=sum(r["ocr_exact_when_matched"] for r in selected)
    matched=sum(r["gt_matched"] for r in selected)
    unknown=len(selected)-matched
    highconfwrong=sum(r["gt_matched"] and not r["ocr_exact_when_matched"] and
                      r["ocr_conf"]>=.85 for r in selected)
    return {
      "n_images":len(imgs),"n_extra_boxes":len(selected),"n_images_with_extra":sum(x>0 for x in n_per),
      "n_gt_overlaps":matched,"n_gt_exact":exact,
      "n_gt_overlap_wrong_or_incomplete":matched-exact,
      "n_unmatched_annotation_status_unknown":unknown,
      "n_unknown_ocr_nonempty":sum(not r["gt_matched"] and r["text_len"]>0 for r in selected),
      "n_high_conf_wrong_matched":highconfwrong,
      "additional_rec_cost_p50_ms_per_image":quant(all_ms,.5),
      "additional_rec_cost_p95_ms_per_image":quant(all_ms,.95),
      "additional_rec_cost_max_ms_per_image":max(all_ms),
      "additional_rec_cost_total_ms":round(sum(all_ms),2),
      "additional_rec_calls_p95_per_image":quant(n_per,.95),
    }

policies={
 "all":lambda r:True,
 "len2_conf065":lambda r:r["text_len"]>=2 and r["ocr_conf"]>=.65,
 "len2_conf075":lambda r:r["text_len"]>=2 and r["ocr_conf"]>=.75,
 "len2_conf085":lambda r:r["text_len"]>=2 and r["ocr_conf"]>=.85,
 "len2_conf090":lambda r:r["text_len"]>=2 and r["ocr_conf"]>=.90,
}
out=[]
for low in [.40,.35,.30]:
    for name,func in policies.items():
        selected=[r for rows in counts.values() for r in rows if
                  r["is_new_below_045"] and low<=r["box_score"]<.45
                  and func(r)]
        result={"threshold":low,"policy":name,**sample(selected)}
        out.append(result)
        print("GATE_POLICY_AB",json.dumps(result,ensure_ascii=False),flush=True)

# A high recognition score is not correctness; show specific anonymous examples.
samples=[r for rows in counts.values() for r in rows if
    r["is_new_below_045"] and r["gt_matched"] and
    not r["ocr_exact_when_matched"] and r["ocr_conf"]>=.85]
for r in samples[:10]:
    print("HIGH_CONFIDENCE_WRONG_EXTRA",json.dumps({
      "split":r["split"],"image":r["image"],
      "score":r["box_score"],"conf":r["ocr_conf"],
      "GT":r["gt_text"],"OCR":r["ocr_text"]},ensure_ascii=False),flush=True)

# This is only a crop-level pseudo-time cost on CI CPU. Actual total runtime
# requires Android preprocessing / UVC / TTS measurement.
with (dest/"policies.csv").open("w",newline="",encoding="utf-8-sig") as fd:
    wr=csv.DictWriter(fd,fieldnames=list(out[0]));wr.writeheader();wr.writerows(out)
(dest/"caveats.txt").write_text(
    "True deployed box threshold is 0.45, detector mask threshold is 0.20, unclip=1.40.\n"
    "Unmatched candidate boxes are UNKNOWN; can include unannotated real text.\n"
    "Matched GT is AABB IoU>=0.4 and annotated text exact OCR, not Android guarantee.\n"
    "Confidence gates are diagnostic and not automatically accepted release policy.\n"
    "OCR additional milliseconds: offline cloud CPU recognition inference only.\n",
    encoding="utf-8")
print("REAL_BOX_VALUE_GATE_POLICY_REVIEW=PASS",len(out),flush=True)
