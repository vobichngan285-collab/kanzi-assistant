#!/usr/bin/env python3
"""Check predeclared Medium-when-Small-uncertain gate on food receipt price words.

Uses 100 CORD-v2 restaurant receipts' identical 1,235 annotated word crops,
from the already successful official PP-OCR four-model comparison.
No images, models, internet, or OCR in this analysis.

This is a counterfactual REC-only test: CORD annotation crops bypass detection.
"""
import csv,collections,json,sys
from pathlib import Path

def read(path):
    with open(path,encoding="utf-8-sig",newline="") as f:
        return list(csv.DictReader(f))

def run():
    src=Path(sys.argv[1]);out=Path(sys.argv[2])
    out.mkdir(parents=True,exist_ok=True)
    rows=read(src)
    grouped=collections.defaultdict(dict)
    for row in rows:
        key=row["photo"],row["line"],row["word"]
        grouped[key][row["model"]]=row
    assert len(grouped)==1235
    assert all("v6_small" in v and "v6_medium" in v for v in grouped.values())
    result=[]
    for group in ["ALL","menu.price","menu.nm","total.total_price",
                  "sub_total.subtotal_price","HAS_DIGIT"]:
        words=[v for v in grouped.values()
               if group=="ALL" or group=="HAS_DIGIT" and int(v["v6_small"]["has_digit"]) or
               v["v6_small"]["category"]==group]
        for policy in ["Small_only","Medium_all","Small_then_Medium_if_conf_lt_0_95"]:
            selection=[]
            n_medium=0
            for pair in words:
                a=pair["v6_small"];b=pair["v6_medium"]
                use=policy=="Medium_all" or (policy.endswith("_0_95") and
                     float(a["confidence"])<.95)
                n_medium+=int(use)
                selection.append(b if use else a)
            nchar=sum(int(x["n_chars"]) for x in selection)
            nerr=sum(int(x["edits"]) for x in selection)
            item={
             "category":group,"policy":policy,"n_words":len(words),
             "medium_calls":n_medium,
             "medium_use_fraction":round(n_medium/max(1,len(words)),4),
             "character_error_rate":round(nerr/max(1,nchar),5),
             "complete_word_rate":round(sum(int(x["edits"])==0 for x in selection)/
                  max(1,len(selection)),5)}
            result.append(item)
            print("FOOD_PRICE_MEDIUM_FALLBACK_AB",json.dumps(item),flush=True)
    with (out/"receipt_medium_fallback.csv").open("w",encoding="utf-8-sig",newline="") as f:
        w=csv.DictWriter(f,fieldnames=list(result[0]))
        w.writeheader();w.writerows(result)
    (out/"limitations.txt").write_text(
        "Receipt words Indonesian/English, NOT Chinese menu boards.\n"
        "Annotations directly cropped; no detector. Conditional Medium simulated after Small confidence.\n"
        "High-confidence wrong price/dosage is still possible. No Android camera test.\n")
    print("RECEIPT_CONDITIONAL_MEDIUM_AB=PASS",len(grouped))

if __name__=="__main__":run()
