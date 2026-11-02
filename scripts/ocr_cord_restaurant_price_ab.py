#!/usr/bin/env python3
"""Human-annotated CORD-v2 restaurant receipt scene OCR A/B (annotation-guided).

Sources:
CORD v2 NAVER CLOVA (CC-BY-4.0); subset source:
Nextia-Intelligence/nextia-learning-labs/C13/read-real-receipts/data/cord-v2-test-sample.zip
The originals are NOT user photos, are not uploaded and are discarded after CI.

The receipts are restaurant/café receipts, NOT menu boards, in Indonesian/English.
Annotations are CORD-v2 words, not automatically generated candidates.
"""
from collections import defaultdict, Counter
import csv
import hashlib
import io
import json
from pathlib import Path
import sys
import time
import zipfile
import numpy as np
import onnxruntime as ort
from PIL import Image
import yaml
from ocr_public_real_scene_ab import crop_minrect, crop_direct4, local_clahe, recognize, norm, edits, summary

ZIP_SHA = "2dd48e2cc1412d4cdcb41314f1ccad70901fc0c3f8126f7cf1554ed3fab3a900"
REC_SHA = "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
YAML_SHA = "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
MODES = ["sdk", "quad4", "source_expand_3pct", "local_clahe"]
CAP_PER_CATEGORY = {"menu.price": 450, "menu.nm": 450, "total.total_price": 200,
                    "sub_total.subtotal_price": 150, "other": 200}


def main():
    import argparse
    p=argparse.ArgumentParser()
    p.add_argument("zip")
    p.add_argument("onnx")
    p.add_argument("yaml")
    p.add_argument("--out", default="/tmp/cord-bench-results")
    args=p.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    zfile=Path(args.zip)
    assert hashlib.sha256(zfile.read_bytes()).hexdigest()==ZIP_SHA
    assert hashlib.sha256(Path(args.onnx).read_bytes()).hexdigest()==REC_SHA
    assert hashlib.sha256(Path(args.yaml).read_bytes()).hexdigest()==YAML_SHA
    chars=list(yaml.safe_load(Path(args.yaml).read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
    session=ort.InferenceSession(args.onnx,sess_options=opts,providers=["CPUExecutionProvider"])
    input_name=session.get_inputs()[0].name
    records=[];counts=Counter();images_used=set()
    with zipfile.ZipFile(args.zip) as arc:
        root="cord-v2-test-sample/"
        labels=arc.read(root+"labels.jsonl").decode().splitlines()
        print("CORD_RECEIPT_PHOTOS",len(labels),flush=True)
        for line in labels:
            entry=json.loads(line)
            if not entry.get("file"):continue
            raw=arc.read(root+entry["file"])
            frame=np.asarray(Image.open(io.BytesIO(raw)).convert("RGB"))
            gt=json.loads(entry["ground_truth"])
            sx=frame.shape[1]/entry["original_size"][0]
            sy=frame.shape[0]/entry["original_size"][1]
            selected=0
            for i,annotation in enumerate(gt.get("valid_line",[])):
                category=annotation.get("category","")
                if category not in CAP_PER_CATEGORY:category="other"
                if counts[category]>=CAP_PER_CATEGORY[category]:continue
                for j,word in enumerate(annotation.get("words",[])):
                    if counts[category]>=CAP_PER_CATEGORY[category]:break
                    truth=word.get("text","").strip()
                    if not (2<=len(norm(truth))<=32):continue
                    quad=word.get("quad")
                    if not quad:continue
                    # Different CORD words have x1/y1 .. x4/y4.
                    try:
                        poly=[float(quad["x"+str(k)])*sx if z==0 else
                              float(quad["y"+str(k)])*sy
                              for k in (1,2,3,4) for z in (0,1)]
                    except (KeyError,TypeError,ValueError):continue
                    try:
                        base=crop_minrect(frame,poly)
                        if base is None or base.shape[0]<6 or base.shape[1]<5:continue
                        crops={
                           "sdk":base,
                           "quad4":crop_direct4(frame,poly),
                           "source_expand_3pct":crop_minrect(frame,poly,expand=.03),
                           "local_clahe":local_clahe(base)}
                        if any(x is None for x in crops.values()):continue
                        pred_by_mode={}
                        for mode,crop in crops.items():
                            pred,conf,elapsed=recognize(session,input_name,crop,chars)
                            pred_by_mode[mode]=(pred,conf,elapsed)
                    except Exception as exc:
                        print("WARN_WORD",entry["id"],category,str(exc)[:150],flush=True)
                        continue
                    selected+=1;counts[category]+=1
                    images_used.add(entry["id"])
                    for mode,(pred,conf,elapsed) in pred_by_mode.items():
                        records.append({
                            "photo":entry["id"],"category":category,
                            "annotation_line":i,"word":j,
                            "mode":mode,"truth":truth,"prediction":pred,
                            "edits":edits(norm(truth),norm(pred)),
                            "chars":len(norm(truth)),
                            "confidence":round(conf,5),
                            "inference_ms":round(elapsed,4)})
            if sum(counts.values())%200<max(1,selected):
                print("CORD_PROGRESS_WORDS",sum(counts.values()),dict(counts),flush=True)
    if not records:raise RuntimeError("No annotated receipt word crops run")
    rows=defaultdict(list)
    for x in records:
        rows[(x["category"],x["mode"])].append(x)
        rows[("ALL",x["mode"])].append(x)
    result=[]
    for cat in ["ALL","menu.price","menu.nm","total.total_price","sub_total.subtotal_price","other"]:
        for mode in MODES:
            sub=rows[cat,mode]
            data={"category":cat,"mode":mode,**summary(sub)}
            result.append(data)
            print("CORD_REAL_PRICE_AB",cat,mode,"n",data["n"],"CER",data["cer"],
                  "exact",data["exact"],"p95_ms",data["inference_p95_ms"],flush=True)
    paired=defaultdict(dict)
    for x in records:paired[(x["photo"],x["annotation_line"],x["word"])][x["mode"]]=x
    for mode in MODES[1:]:
        n=0;improved=0;worse=0
        for g in paired.values():
            if mode not in g or "sdk" not in g:continue
            n+=1
            improved+=g[mode]["edits"]<g["sdk"]["edits"]
            worse+=g[mode]["edits"]>g["sdk"]["edits"]
        print("CORD_PAIRED",mode,"n",n,"improved",improved,"worsened",worse,
              "unchanged",n-improved-worse,flush=True)
    (out/"metrics.csv").write_text("")
    with (out/"metrics.csv").open("w",newline="",encoding="utf-8-sig") as fd:
        writer=csv.DictWriter(fd,fieldnames=list(result[0]))
        writer.writeheader();writer.writerows(result)
    with (out/"word_predictions.csv").open("w",newline="",encoding="utf-8-sig") as fd:
        writer=csv.DictWriter(fd,fieldnames=list(records[0]))
        writer.writeheader();writer.writerows(records)
    metadata={"source":"CORD v2 NAVER CLOVA, CC BY 4.0 / Nextia Learning derivative",
              "source_sha256":ZIP_SHA,"recognizer_sha256":REC_SHA,
              "user_photos_uploaded":False,"photos_redistributed":False,
              "receipt_photos":len(images_used),"word_crops":len(paired),
              "groups":dict(counts),
              "limitation":"restaurant receipts, Indonesian+English, annotation crops, no detector or UVC test"}
    (out/"metadata.json").write_text(json.dumps(metadata,ensure_ascii=False,indent=2))
    print("CORD_BENCHMARK_COUNT",len(paired),"photos",len(images_used),flush=True)
    if len(paired)<100:raise RuntimeError("Insufficient restaurant receipt word samples")
    print("CORD_REAL_RECEIPT_OCR_AB=PASS",flush=True)


if __name__=="__main__":main()
