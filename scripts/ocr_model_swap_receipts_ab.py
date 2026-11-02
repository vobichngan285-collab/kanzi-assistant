#!/usr/bin/env python3
"""Repeat #315's CORD-v2 restaurant receipt OCR sample with different recognizers.

Four official ONNX rec models see precisely the SAME CORD word crops.
Original annotation/crop selection is reused from ocr_cord_restaurant_price_ab.
Unlike that old first pilot, ALL four models now use the SDK-correct RGB
recognition preprocess, with each recognizer's OWN character dictionary.
Not Chinese menu boards: English/Indonesian food receipts with prices.
"""
from collections import defaultdict,Counter
from pathlib import Path
import argparse,csv,hashlib,io,json,zipfile
import numpy as np
import onnxruntime as ort
from PIL import Image
import yaml
from ocr_public_real_scene_ab import crop_minrect
from ocr_box_value_deep_dive import rec,normalize,distance
from ocr_model_swap_rec_ab import MODELS,percentile,file_sha

SOURCE_SHA="2dd48e2cc1412d4cdcb41314f1ccad70901fc0c3f8126f7cf1554ed3fab3a900"
CAP={"menu.price":450,"menu.nm":450,"total.total_price":200,
     "sub_total.subtotal_price":150,"other":200}

def collect(zip_path):
    crops=[];counts=Counter();photos=set()
    with zipfile.ZipFile(zip_path) as arc:
        labels=arc.read("cord-v2-test-sample/labels.jsonl").decode().splitlines()
        for line in labels:
            entry=json.loads(line)
            if not entry.get("file"):continue
            rgb=np.asarray(Image.open(io.BytesIO(
                arc.read("cord-v2-test-sample/"+entry["file"]))).convert("RGB"))
            truth=json.loads(entry["ground_truth"])
            sx=rgb.shape[1]/entry["original_size"][0]
            sy=rgb.shape[0]/entry["original_size"][1]
            for i,annotation in enumerate(truth.get("valid_line",[])):
                group=annotation.get("category","other")
                if group not in CAP:group="other"
                for j,word in enumerate(annotation.get("words",[])):
                    if counts[group]>=CAP[group]:break
                    text=word.get("text","").strip()
                    if not 2<=len(normalize(text))<=32:continue
                    q=word.get("quad")
                    if not q:continue
                    try:
                        poly=[float(q["x"+str(k)])*sx if z==0
                              else float(q["y"+str(k)])*sy
                              for k in (1,2,3,4) for z in (0,1)]
                        crop=crop_minrect(rgb,poly)
                        if crop is None or min(crop.shape[:2])<6:continue
                    except Exception:continue
                    counts[group]+=1;photos.add(entry["id"])
                    crops.append((str(entry["id"]),group,i,j,text,crop))
    print("CORD_MODEL_SWAP_CROPS",len(crops),"photos",len(photos),
          "categories",dict(counts),flush=True)
    assert len(crops)>=1000, "not enough aligned word crops"
    return crops,photos

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--zip",required=True)
    ap.add_argument("--models",required=True)
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    out=Path(args.out);out.mkdir(exist_ok=True,parents=True)
    assert file_sha(args.zip)==SOURCE_SHA
    root=Path(args.models)
    opts=ort.SessionOptions();opts.intra_op_num_threads=2
    opts.inter_op_num_threads=1
    opts.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    sessions={};dic={};names={}
    for model in MODELS:
        yml=yaml.safe_load((root/model/"inference.yml").read_text())
        chars=list(yml["PostProcess"]["character_dict"])
        if chars[-1]!=" ":chars.append(" ")
        dic[model]=chars
        session=ort.InferenceSession(str(root/model/"inference.onnx"),
            sess_options=opts,providers=["CPUExecutionProvider"])
        sessions[model]=session;names[model]=session.get_inputs()[0].name
    crops,photos=collect(args.zip)
    records=[]
    for index,(photo,group,i,j,truth,crop) in enumerate(crops,1):
        for model in MODELS:
            pred,conf,ms=rec(sessions[model],names[model],dic[model],crop)
            records.append({
                "photo":photo,"category":group,"line":i,"word":j,
                "model":model,"truth":truth,"pred":pred,
                "n_chars":len(normalize(truth)),
                "edits":distance(normalize(truth),normalize(pred)),
                "confidence":round(conf,5),"rec_ms":round(ms,4),
                "has_digit":int(any(c.isdigit() for c in truth)),
            })
        if index%250==0:print("CORD_SWAP_PROGRESS",index,flush=True)
    report=[]
    for group in ["ALL","menu.price","menu.nm","total.total_price","sub_total.subtotal_price","other","HAS_DIGIT"]:
        for model in MODELS:
            subset=[x for x in records if x["model"]==model and (
                group=="ALL" or (group=="HAS_DIGIT" and x["has_digit"]) or x["category"]==group)]
            chars=sum(x["n_chars"] for x in subset)
            row={"category":group,"model":model,"n":len(subset),
                "CER":round(sum(x["edits"] for x in subset)/max(1,chars),5),
                "exact":round(sum(x["edits"]==0 for x in subset)/max(1,len(subset)),5),
                "rec_p50_ms":percentile([x["rec_ms"] for x in subset],.5),
                "rec_p95_ms":percentile([x["rec_ms"] for x in subset],.95)}
            report.append(row)
            print("CORD_MODEL_SWAP_METRIC",json.dumps(row,ensure_ascii=False),flush=True)
    paired=defaultdict(dict)
    for row in records:paired[(row["photo"],row["line"],row["word"])][row["model"]]=row
    assert len(paired)==len(crops) and all(len(v)==4 for v in paired.values())
    for model in MODELS[1:]:
        sub=list(paired.values())
        print("CORD_MODEL_SWAP_PAIRED",json.dumps({
            "alternative":model,"n":len(sub),
            "improved":sum(x[model]["edits"]<x["v6_small"]["edits"] for x in sub),
            "worsened":sum(x[model]["edits"]>x["v6_small"]["edits"] for x in sub),
            "unchanged":sum(x[model]["edits"]==x["v6_small"]["edits"] for x in sub),
        }),flush=True)
    for path,rows in [("metrics.csv",report),("per_word.csv",records)]:
        with (out/path).open("w",encoding="utf-8-sig",newline="") as f:
            wr=csv.DictWriter(f,fieldnames=list(rows[0]))
            wr.writeheader();wr.writerows(rows)
    (out/"README.txt").write_text(
        "CORD v2 NAVER CLOVA restaurant receipt photos; selected words not whole-image detection.\n"
        "Truth is English/Indonesian; do not treat as Chinese menu boards.\n"
        "All models get the same crops and their own CTC dictionaries, RGB SDK input.\n"
        "Photos/models remain ephemeral; model inference time is cloud CPU only.\n")
    print("CORD_FOUR_RECOGNIZER_MODELS_PASS",len(crops),len(photos),flush=True)
if __name__=="__main__":main()
