#!/usr/bin/env python3
"""Public real-scene annotation-guided OCR recognition A/B, offline #315 recognizer.

Images: Yesianrohn/OCR-Data (ReCTS/RCTW/ART) using Hugging Face viewer
annotations. No user image uploads; downloads stay in ephemeral GitHub runner.

Important boundaries:
- Ground-truth polygons are used to crop text. This is NOT a detector test.
- SDK reference emulates QuadTextCrop(OpenCV minAreaRect+warpPerspective,
  vertical rotate) and PP-OCRv6 Small BGR H48 recognizer. It is not a measured
  Android UVC camera execution path.
- Strict per-character edit distance with NFKC normalization; skip "###".
- Fixed A/B strategies, selected BEFORE seeing scores; no label-driven choices.
- Per-line inference is timed; model init, remote downloads, USB/TTS not timed.
"""
import argparse
import collections
import csv
import hashlib
import io
import json
import math
import os
from pathlib import Path
import random
import statistics
import time
import unicodedata
from concurrent.futures import ThreadPoolExecutor, as_completed

import cv2
import numpy as np
import onnxruntime as ort
import requests
import yaml
from PIL import Image

DATASET_ID = "Yesianrohn/OCR-Data"
# Deterministic sampling of actual camera-captured / natural-scene datasets.
# The three datasets are a mix of real street signs and natural text scenes;
# there is no claim each image is a restaurant menu or a food package.
SPLITS = {
    "ReCTS": [(0, 38), (97, 38), (302, 38)],
    "RCTW": [(0, 30), (130, 30), (310, 30)],
    "ART": [(0, 25), (120, 25), (270, 25)],
}
MAX_PER_SPLIT = 360   # annotations, not whole images
MAX_PER_IMAGE = 8     # reduce excessive correlation from one signboard
BASE_SEED = 20261103
MODES = ["sdk", "quad4", "source_expand_3pct", "local_clahe"]


def get_json(url, *, params=None, timeout=70):
    for attempt in range(3):
        try:
            res = requests.get(url, params=params, timeout=timeout,
                               headers={"User-Agent": "KanziResearch/1.0 scene-text-benchmark"})
            if res.status_code in (429, 502, 503, 504) and attempt < 2:
                time.sleep(2 * (attempt + 1)); continue
            res.raise_for_status()
            return res.json()
        except Exception as exc:
            if attempt == 2: raise
            time.sleep(1.3 * (attempt + 1))
    raise RuntimeError("requests failed")


def load_rows():
    rows = []
    for split, slices in SPLITS.items():
        for offset, count in slices:
            try:
                data = get_json("https://datasets-server.huggingface.co/rows", params={
                    "dataset": DATASET_ID, "config": "default", "split": split,
                    "offset": offset, "length": count})
                got = data.get("rows", [])
                print(f"FETCH_ROWS split={split} offset={offset} fetched={len(got)} "
                      f"total={data.get('num_rows_total')}", flush=True)
                for item in got:
                    r = item["row"]
                    img = r.get("image") or {}
                    if img.get("src") and r.get("texts") and r.get("polygons"):
                        rows.append((split, int(item["row_idx"]), img["src"],
                                     r["texts"], r["polygons"]))
            except Exception as e:
                print("WARN_METADATA", split, offset, str(e)[:220], flush=True)
    print("PUBLIC_IMAGES_WITH_ANNOTATIONS", len(rows), flush=True)
    return rows


def read_img(req):
    split, idx, url, texts, polys = req
    try:
        r = requests.get(url, timeout=45, headers={"User-Agent": "KanziResearch/1.0"})
        r.raise_for_status()
        if len(r.content) > 18_000_000: return None
        frame = np.asarray(Image.open(io.BytesIO(r.content)).convert("RGB"))
        return split, idx, frame, texts, polys
    except Exception as e:
        print("WARN_IMAGE", split, idx, str(e)[:100], flush=True)
        return None


def crop_minrect(rgb, p, expand=0.0):
    # cv2 minAreaRect (Android SDK's polygon rectangle crop), x-sort geometry.
    poly = np.array(p, np.float32).reshape(-1, 2)
    ((_, _), (_, _), _) = cv2.minAreaRect(poly)
    pts = cv2.boxPoints(cv2.minAreaRect(poly))
    pts = pts[np.argsort(pts[:, 0], kind="stable")]
    l0, l1, r0, r1 = pts
    tl, bl = (l0, l1) if l0[1] <= l1[1] else (l1, l0)
    tr, br = (r0, r1) if r0[1] <= r1[1] else (r1, r0)
    ordered = np.float32([tl, tr, br, bl])
    if expand:
        c = ordered.mean(0)
        ordered = c + (ordered - c) * (1 + expand)
    out_w = max(1, int(max(np.linalg.norm(ordered[0]-ordered[1]),
                           np.linalg.norm(ordered[2]-ordered[3]))))
    out_h = max(1, int(max(np.linalg.norm(ordered[0]-ordered[3]),
                           np.linalg.norm(ordered[1]-ordered[2]))))
    if out_w < 5 or out_h < 5 or out_w > 4000 or out_h > 2500: return None
    dst = np.float32([[0, 0], [out_w, 0], [out_w, out_h], [0, out_h]])
    transform = cv2.getPerspectiveTransform(ordered, dst)
    cropped = cv2.warpPerspective(rgb, transform, (out_w, out_h),
                                  flags=cv2.INTER_CUBIC,
                                  borderMode=cv2.BORDER_REPLICATE)
    if cropped.shape[0] / max(1, cropped.shape[1]) >= 1.5:
        cropped = cv2.rotate(cropped, cv2.ROTATE_90_COUNTERCLOCKWISE)
    return cropped


def crop_direct4(rgb, p):
    pts = np.array(p, dtype=np.float32).reshape(-1, 2)
    if len(pts) != 4: return crop_minrect(rgb, p)
    # Annotation is polygon clockwise but start point may vary; order by xy.
    ordered = pts[np.argsort(pts[:, 0], kind="stable")]
    l0, l1, r0, r1 = ordered
    tl, bl = (l0, l1) if l0[1] <= l1[1] else (l1, l0)
    tr, br = (r0, r1) if r0[1] <= r1[1] else (r1, r0)
    ordered = np.float32([tl, tr, br, bl])
    w = max(1, int(max(np.linalg.norm(ordered[0]-ordered[1]),
                       np.linalg.norm(ordered[2]-ordered[3]))))
    h = max(1, int(max(np.linalg.norm(ordered[0]-ordered[3]),
                       np.linalg.norm(ordered[1]-ordered[2]))))
    if w < 5 or h < 5 or w > 4000 or h > 2500: return None
    crop = cv2.warpPerspective(rgb,
        cv2.getPerspectiveTransform(ordered,
            np.float32([[0,0],[w,0],[w,h],[0,h]])),
        (w,h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
    if h/max(1,w) >= 1.5:
        crop=cv2.rotate(crop,cv2.ROTATE_90_COUNTERCLOCKWISE)
    return crop


def local_clahe(rgb):
    # A minimal line-only alternative; not an expensive whole-frame second OCR.
    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB)
    channel = lab[:,:,0]
    lab[:,:,0] = cv2.createCLAHE(clipLimit=1.6, tileGridSize=(4,4)).apply(channel)
    return cv2.cvtColor(lab, cv2.COLOR_LAB2RGB)


def norm(s):
    return "".join(ch for ch in unicodedata.normalize("NFKC", str(s))
                   if not ch.isspace()).lower().strip()


def edits(a,b):
    p=list(range(len(b)+1))
    for i,ch in enumerate(a,1):
        q=[i]
        for j,c in enumerate(b,1):
            q.append(min(q[-1]+1,p[j]+1,p[j-1]+(ch!=c)))
        p=q
    return p[-1]


def recognize(session, input_name, rgb, chars):
    h,w=rgb.shape[:2]
    out_w = min(3200, int(math.ceil(48*w/max(1,h))))
    if out_w < 8: return "", 0.0, 0.
    img=cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR)
    img=cv2.resize(img,(out_w,48), interpolation=cv2.INTER_LINEAR)
    tensor=np.ascontiguousarray(img.transpose(2,0,1)[None].astype(np.float32)/127.5-1.0)
    start=time.perf_counter()
    prob=session.run(None,{input_name:tensor})[0][0]
    millis=(time.perf_counter()-start)*1000
    idx=np.argmax(prob,axis=1)
    scores=np.max(prob,axis=1)
    result=[];confs=[];prev=-1
    for i,val in enumerate(idx):
        val=int(val)
        if val!=0 and val!=prev and 1<=val<=len(chars):
            result.append(chars[val-1])
            confs.append(float(scores[i]))
        prev=val
    return "".join(result), (float(np.mean(confs)) if confs else 0), millis


def summary(group):
    if not group:
        return {"n":0, "cer":None, "exact":None, "inference_p50_ms":None,
                "inference_p95_ms":None, "total_chars":0}
    chars=sum(x["chars"] for x in group)
    times=sorted(x["inference_ms"] for x in group)
    return {"n":len(group),
            "cer":round(sum(x["edits"] for x in group)/max(1,chars), 5),
            "exact":round(sum(x["edits"]==0 for x in group)/len(group),5),
            "inference_p50_ms":round(times[len(times)//2],3),
            "inference_p95_ms":round(times[int((len(times)-1)*0.95)],3),
            "total_chars":chars}


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("onnx")
    ap.add_argument("yaml")
    ap.add_argument("--out",default="test-results")
    args=ap.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    model=Path(args.onnx)
    print("MODEL_SHA256",hashlib.sha256(model.read_bytes()).hexdigest(), flush=True)
    print("DICTIONARY_SHA256",hashlib.sha256(Path(args.yaml).read_bytes()).hexdigest(),flush=True)
    assert hashlib.sha256(model.read_bytes()).hexdigest()=="5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
    assert hashlib.sha256(Path(args.yaml).read_bytes()).hexdigest()=="ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
    chars=list(yaml.safe_load(Path(args.yaml).read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    opts=ort.SessionOptions()
    opts.intra_op_num_threads=2
    opts.inter_op_num_threads=1
    opts.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    session=ort.InferenceSession(str(model),sess_options=opts,providers=["CPUExecutionProvider"])
    input_name=session.get_inputs()[0].name
    meta={"dataset":DATASET_ID,"slices":SPLITS, "modes":MODES,
          "model_sha":hashlib.sha256(model.read_bytes()).hexdigest(),
          "dictionary_sha":hashlib.sha256(Path(args.yaml).read_bytes()).hexdigest(),
          "note":"annotation-guided line recognition; NOT full detection/Android runtime",
          "records":[]}
    downloaded=load_rows()
    results=[]
    counts=collections.Counter()
    per_image=collections.Counter()
    seen=set()
    # Keep a stable, deterministic order even when network threads finish out-of-order.
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures=[pool.submit(read_img,item) for item in downloaded]
        items=[f.result() for f in futures]
    items=[x for x in items if x is not None]
    items.sort(key=lambda x:(x[0],x[1]))
    print("ACTUAL_IMAGES_DOWNLOADED",len(items),flush=True)
    for split,idx,frame,texts,polys in items:
        attempted=0
        for k,(truth,p) in enumerate(zip(texts,polys)):
            if counts[split]>=MAX_PER_SPLIT or attempted>=MAX_PER_IMAGE:break
            if not isinstance(truth,str) or not isinstance(p,list): continue
            truthNorm=norm(truth)
            if (not truthNorm or truth=="###" or "###" in truth or
                not 2<=len(truthNorm)<=36 or
                len(p)<8 or len(p)%2!=0 or len(p)>40):
                continue
            if not any("\u3400"<=ch<="\u9fff" for ch in truthNorm) and \
               not any(ch.isdigit() for ch in truthNorm) and len(truthNorm)<4:
                continue
            key=(split,idx,k)
            if key in seen:continue
            seen.add(key)
            baseline=crop_minrect(frame,p)
            if baseline is None:continue
            if baseline.shape[0]<7 or baseline.shape[1]<7:continue
            if not (0.25<=baseline.shape[1]/baseline.shape[0]<=32):continue
            candidates={
                "sdk":baseline,
                "quad4":crop_direct4(frame,p),
                "source_expand_3pct":crop_minrect(frame,p,expand=.03),
                "local_clahe":local_clahe(baseline)
            }
            if any(v is None for v in candidates.values()):continue
            attempted+=1
            counts[split]+=1
            per_image[(split,idx)]+=1
            for mode,crop in candidates.items():
                try:
                    pred,conf,ms=recognize(session,input_name,crop,chars)
                except Exception as e:
                    print("WARN_INFER",split,idx,mode,str(e)[:200],flush=True)
                    continue
                expected=truthNorm
                predicted=norm(pred)
                results.append({
                    "split":split,"image_idx":idx,"region_idx":k,
                    "mode":mode,"gt":truth,"pred":pred,
                    "chars":len(expected),"edits":edits(expected,predicted),
                    "confidence":round(conf,4),
                    "inference_ms":round(ms,4),
                    "raw_crop_height":baseline.shape[0],
                    "raw_crop_width":baseline.shape[1],
                    "polygon_vertices":len(p)//2,
                    "truth_has_digit":int(any(ch.isdigit() for ch in truth)),
                    "truth_has_cjk":int(any("\u3400"<=ch<="\u9fff" for ch in truth)),
                })
        if sum(counts.values()) and sum(counts.values())%100==0:
            print("PROGRESS_LINES",sum(counts.values()),dict(counts),flush=True)
    if not results:
        raise RuntimeError("No valid labeled real scene images or model runs")
    # Paired only: never compare methods on unequal selections.
    key=lambda x:(x["split"],x["image_idx"],x["region_idx"])
    grouped=collections.defaultdict(dict)
    for r in results:grouped[key(r)][r["mode"]]=r
    paired=[r for r in grouped.values() if all(m in r for m in MODES)]
    results=[item[m] for item in paired for m in MODES]
    groups=collections.defaultdict(list)
    for r in results:
        groups[(r["split"],r["mode"])].append(r)
        groups[("ALL",r["mode"])].append(r)
        if r["truth_has_digit"]:groups[("WITH_DIGITS",r["mode"])].append(r)
        if r["truth_has_cjk"]:groups[("WITH_CHINESE",r["mode"])].append(r)
    metrics=[]
    for cat in ["ALL","ReCTS","RCTW","ART","WITH_DIGITS","WITH_CHINESE"]:
        for mode in MODES:
            info=summary(groups[(cat,mode)])
            metrics.append({"category":cat,"mode":mode,**info})
            print("AB_REAL_SCENE",cat,mode,"n",info["n"],
                  "CER",info["cer"],"exact",info["exact"],
                  "p50_ms",info["inference_p50_ms"],"p95_ms",info["inference_p95_ms"],
                  flush=True)
    # Each variant's correction-vs-regression from baseline for matched line crops.
    for mode in MODES[1:]:
        improved=sum(int(r[mode]["edits"]<r["sdk"]["edits"]) for r in paired)
        worse=sum(int(r[mode]["edits"]>r["sdk"]["edits"]) for r in paired)
        equal=len(paired)-improved-worse
        print("PAIRED_REGRESSION",mode,"improved",improved,"worsened",worse,
              "unchanged",equal,flush=True)
    print("BENCHMARK_SAMPLES",len(paired),dict(counts),flush=True)
    with (out/"predictions.csv").open("w",newline="",encoding="utf-8-sig") as fd:
        writer=csv.DictWriter(fd,fieldnames=list(results[0]))
        writer.writeheader()
        writer.writerows(results)
    with (out/"summary.csv").open("w",newline="",encoding="utf-8-sig") as fd:
        writer=csv.DictWriter(fd,fieldnames=list(metrics[0]))
        writer.writeheader()
        writer.writerows(metrics)
    meta.update({"downloaded_images":len(items),
                 "sampled_regions":len(paired),
                 "regions_by_split":dict(counts),
                 "images_with_regions":len(per_image),
                 "dataset_originals_not_redistributed":True})
    (out/"run_metadata.json").write_text(json.dumps(meta,indent=2,ensure_ascii=False))
    if len(paired)<100:
        raise RuntimeError("Insufficient real-world annotated samples; no conclusion")
    print("PUBLIC_REAL_SCENE_RECOGNITION_AB=PASS",len(paired),flush=True)


if __name__=="__main__":main()
