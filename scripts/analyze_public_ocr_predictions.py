#!/usr/bin/env python3
"""Read per-line PUBLIC annotation-guided inference results from prior CI artifact.

No models, image bytes or user's private photos. Analyze predefined,
blind-to-ground-truth candidate heuristics and subgroup regression counts.
Oracle row is labeled explicitly as unattainable diagnostic upper bound.
"""
import collections,csv,math,re,sys,json,statistics
from pathlib import Path

src=Path(sys.argv[1])
out=Path(sys.argv[2]);out.mkdir(exist_ok=True,parents=True)
rows=list(csv.DictReader(src.open(encoding="utf-8-sig")))
groups=collections.defaultdict(dict)
for r in rows:
  for k in ("edits","chars","truth_has_digit","truth_has_cjk"):
    r[k]=int(r[k])
  for k in ("confidence","inference_ms"):
    r[k]=float(r[k])
  groups[(r["split"],int(r["image_idx"]),int(r["region_idx"]))][r["mode"]]=r
modes=["sdk","quad4","source_expand_3pct","local_clahe"]
paired=[v for v in groups.values() if all(k in v for k in modes)]
print("PAIRED_SAMPLE_COUNT",len(paired),flush=True)

def group_name(base):
    line=base["gt"]
    if re.search(r"\d",line):
        if re.search(r"(¥|￥|元|\$|价格|售价|单价|每份)",line): return "PRICE_LIKE_TEXT"
        if re.search(r"(g|mg|ml|mL|千焦|卡路里|毫升|公克|克|蛋白|脂肪|糖|钠)",line,re.I):return "NUTRITION_LIKE_TEXT"
        return "OTHER_DIGITS"
    if re.search(r"(营养|配料|蛋白质|保质期|净含量|生产日期|原料)",line):return "PACKAGING_LIKE_TEXT"
    if re.search(r"(饭|面|粉|小吃|火锅|餐馆|餐厅|锅|串|麻辣|饮料|套餐|奶茶|烤|饺|包|汤)",line):return "FOOD_RELATED_TEXT"
    if len(line)>=15:return "LONG_TEXT"
    if len(line)<5:return "SHORT_TEXT"
    return "OTHER_TEXT"

def choose(g):
  baseline=g["sdk"]
  if baseline["confidence"]>=.72:return baseline
  choices=[g[m] for m in modes[1:] if g[m]["confidence"]>=baseline["confidence"]+.05]
  if not choices:return baseline
  return max(choices,key=lambda x:x["confidence"])

records=[]
for g in paired:
  s=g["sdk"]
  subgroup=group_name(s)
  chosen=choose(g)
  oracle=min((g[m] for m in modes),key=lambda x:x["edits"])
  records.append({"category":subgroup,
                  "split":s["split"],
                  "gt":s["gt"],
                  "baseline_pred":s["pred"],
                  "selected_pred":chosen["pred"],
                  "mode_chosen":chosen["mode"],
                  "baseline_edits":s["edits"],
                  "selected_edits":chosen["edits"],
                  "oracle_edits":oracle["edits"],
                  "chars":s["chars"],
                  "baseline_conf":s["confidence"],
                  "selected_conf":chosen["confidence"],
                  "extra_inferences":int(chosen["mode"]!="sdk")})

cats=sorted(set(r["category"] for r in records))+["ALL"]
summary=[]
for c in cats:
  arr=[r for r in records if r["category"]==c or c=="ALL"]
  n=sum(r["chars"] for r in arr)
  base=sum(r["baseline_edits"] for r in arr)
  selected=sum(r["selected_edits"] for r in arr)
  oracle=sum(r["oracle_edits"] for r in arr)
  data={"category":c,"n":len(arr),"baseline_CER":round(base/max(1,n),5),
        "confidence_heuristic_CER":round(selected/max(1,n),5),
        "oracle_best_possible_CER":round(oracle/max(1,n),5),
        "heuristic_extra_OCR":sum(r["extra_inferences"] for r in arr),
        "heuristic_improved":sum(r["selected_edits"]<r["baseline_edits"] for r in arr),
        "heuristic_worsened":sum(r["selected_edits"]>r["baseline_edits"] for r in arr),
        "oracle_upper_bound_not_shippable":True}
  summary.append(data)
  print("SUBGROUP_AB",c,json.dumps(data,ensure_ascii=False),flush=True)
print("WORST_BASELINE_CASES")
for r in sorted(records,key=lambda z:(z["baseline_edits"],z["chars"]),reverse=True)[:15]:
  print("EXAMPLE",r["split"],r["category"],"gt",repr(r["gt"]),
        "before",repr(r["baseline_pred"]),"after",repr(r["selected_pred"]),
        "mode",r["mode_chosen"],"edits",r["baseline_edits"],r["selected_edits"],flush=True)
with (out/"subgroups.csv").open("w",encoding="utf-8-sig",newline="") as f:
  wr=csv.DictWriter(f,fieldnames=list(summary[0]));wr.writeheader();wr.writerows(summary)
with (out/"paired_heuristics.csv").open("w",encoding="utf-8-sig",newline="") as f:
  wr=csv.DictWriter(f,fieldnames=list(records[0]));wr.writeheader();wr.writerows(records)
assert len(paired)>=100
print("PUBLIC_SCENE_SUBGROUP_REVIEW=PASS",len(paired))
