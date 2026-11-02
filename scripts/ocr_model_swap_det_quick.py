#!/usr/bin/env python3
"""Quick independent detector check on balanced subset of same 348 annotated scenes.

This is a fast sentinel for long detector run; not a replacement for all-348
evaluation. Select 9 unique images from each cohort and each scene category
from a fixed sorted list (54 photos). No cherry-picked image content.
"""
import argparse,collections,csv,json
from pathlib import Path
import requests
from concurrent.futures import ThreadPoolExecutor
from ocr_model_swap_rec_ab import download_metadata,fetch_photo
from ocr_model_swap_det_ab import MODEL_NAMES,CONFIG,benchmark,p95

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--models",required=True)
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    refs=download_metadata(requests.Session())
    by=collections.defaultdict(list)
    for r in refs:by[(r[0],r[1])].append(r)
    chosen=[]
    for k in sorted(by):
        items=sorted(by[k],key=lambda r:r[2])
        # Evenly spaced, deterministic, independent of model outputs or GT text.
        ix=[int(round(t*(len(items)-1)/8)) for t in range(9)]
        chosen += [items[i] for i in ix]
    assert len(chosen)==54,len(chosen)
    with ThreadPoolExecutor(max_workers=6) as pool:
        photos=[p for p in pool.map(fetch_photo,chosen) if p is not None]
    assert len(photos)>=50,len(photos)
    print("QUICK_DET_SOURCE_PHOTOS",len(photos),flush=True)
    rows=[]
    for model in MODEL_NAMES:
        rows += benchmark(model,photos,Path(args.models),out)
    results=[]
    for cohort in sorted({x["cohort"] for x in rows}):
        for model in MODEL_NAMES:
            for threshold in CONFIG[model]["thresholds"]:
                a=[r for r in rows if r["cohort"]==cohort and
                   r["model"]==model and r["threshold"]==threshold]
                gt=sum(x["gt_boxes"] for x in a)
                n=sum(x["num_matched"] for x in a)
                m=sum(x["num_detected"] for x in a)
                record={"cohort":cohort,"model":model,"threshold":threshold,
                        "images":len(a),"gt":gt,"matched":n,"detected":m,
                        "proxy_recall":round(n/max(1,gt),5),
                        "matched_per_box":round(n/max(1,m),5),
                        "model_p95_ms":p95([x["model_inference_ms"] for x in a])}
                results.append(record)
                print("QUICK_DETECTOR_MODEL_SWAP",json.dumps(record),flush=True)
    with (out/"quick_det.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(results[0]))
        w.writeheader();w.writerows(results)
    print("QUICK_54_PHOTO_DETECTOR_SWAP=PASS",len(photos),flush=True)

if __name__=="__main__":main()
