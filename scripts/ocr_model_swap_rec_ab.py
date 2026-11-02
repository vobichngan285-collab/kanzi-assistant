#!/usr/bin/env python3
"""Same-real-image, same-GT crop comparison: #315 vs PP-OCRv6 Medium/Tiny and v5 Mobile.

This directly tests recognizer model capacity without confounding different
detectors. All models see EXACT same pixel crop and same source annotation;
each model uses its OWN proper CTC character dictionary (inference.yml).
Loss and exact-match are compared per line, not against model confidence.

Detector and recognition are separate subsystems. Nothing here is evidence
that a phone UVC pipeline would meet the same latency. The experiment uses
the SDK-aligned RGB preprocessing from the corrected 20261103 analysis; older
BGR-oriented #315 crop baseline results are not comparable.

Raw public photo bytes and model weights stay inside ephemeral GitHub runner.
Do not upload user photos; do not save source photo artifacts.
"""
from collections import defaultdict,Counter
from concurrent.futures import ThreadPoolExecutor
import argparse,csv,hashlib,io,json,math,statistics,time,unicodedata
from pathlib import Path
import requests
import cv2
import numpy as np
import onnxruntime as ort
from PIL import Image
import yaml
from ocr_public_real_scene_ab import crop_minrect
from ocr_box_value_deep_dive import rec,normalize,distance,REC_SHA,REC_CONFIG_SHA

DATASET="Yesianrohn/OCR-Data"
COHORTS={
  "DEV_216":{
    "ReCTS":[(0,24),(101,24),(310,24)],
    "RCTW":[(0,24),(135,24),(315,24)],
    "ART":[(0,24),(125,24),(295,24)],
  },
  "HOLDOUT_132":{
    "ReCTS":[(500,22),(720,22)],
    "RCTW":[(500,22),(720,22)],
    "ART":[(500,22),(720,22)],
  }
}
MODELS=["v6_small","v6_medium","v6_tiny","v5_mobile"]
MAX_LINES_PER_IMAGE=12
MAX_LINES_PER_SPLIT_PER_COHORT=450

def file_sha(path):
    h=hashlib.sha256()
    with open(path,"rb") as fp:
        for buf in iter(lambda:fp.read(1<<20),b""):h.update(buf)
    return h.hexdigest()

def download_metadata(s):
    rows=[]
    for cohort,splits in COHORTS.items():
        for split,parts in splits.items():
            for start,count in parts:
                result=None
                for tries in range(5):
                    try:
                        response=s.get("https://datasets-server.huggingface.co/rows",
                            params={"dataset":DATASET,"config":"default","split":split,
                                    "offset":start,"length":count},timeout=90)
                        response.raise_for_status()
                        result=response.json()["rows"]
                        break
                    except Exception as e:
                        if tries==4:raise RuntimeError(
                          f"Cannot load whole annotation cohort {cohort}/{split}/{start}: {e}")
                        time.sleep(3*(tries+1))
                for item in result:
                    data=item["row"]
                    img=data.get("image") or {}
                    if img.get("src") and data.get("texts") and data.get("polygons"):
                        rows.append((cohort,split,int(item["row_idx"]),img["src"],
                                     data["texts"],data["polygons"]))
                print("ANNO_BATCH",cohort,split,start,"count",len(result),flush=True)
                time.sleep(.25)
    return rows

def fetch_photo(row):
    cohort,split,idx,url,texts,polys=row
    for retry in range(4):
        try:
            res=requests.get(url,timeout=65,headers={"User-Agent":"Kanzi-OCR-A-B-research/1.0"})
            res.raise_for_status()
            if len(res.content)>18_000_000:
                raise ValueError("oversize photo")
            photo=np.asarray(Image.open(io.BytesIO(res.content)).convert("RGB"))
            return (cohort,split,idx,photo,texts,polys)
        except Exception as e:
            if retry==3:
                print("MISSING_PUBLIC_PHOTO",cohort,split,idx,str(e)[:100],flush=True)
                return None
            time.sleep(3*(retry+1))

def build_crops(photos):
    selected=[]
    counters=Counter();photo_ids=set()
    for cohort,split,idx,photo,texts,polygons in photos:
        per=0
        for region,(truth,poly) in enumerate(zip(texts,polygons)):
            if counters[cohort,split]>=MAX_LINES_PER_SPLIT_PER_COHORT:break
            if per>=MAX_LINES_PER_IMAGE:break
            if not isinstance(truth,str) or "###" in truth or not truth.strip():continue
            gt=normalize(truth)
            if len(gt)<2 or len(gt)>64 or not isinstance(poly,list) or len(poly)<8 or len(poly)%2:continue
            if not all(isinstance(x,(int,float)) and math.isfinite(x) for x in poly):continue
            try:crop=crop_minrect(photo,poly)
            except Exception:continue
            if crop is None or min(crop.shape[:2])<7 or max(crop.shape[:2])>4000:continue
            if crop.shape[1]/crop.shape[0]>55:continue
            per+=1;counters[cohort,split]+=1;photo_ids.add((cohort,split,idx))
            selected.append((cohort,split,idx,region,truth,crop))
    print("PAIRED_CROPS",len(selected),"source_photos",len(photo_ids),
          "by_cohort_split",dict((str(k),v) for k,v in counters.items()),flush=True)
    return selected,photo_ids

def percentile(x,p):
    if not x:return None
    x=sorted(x)
    return round(x[int((len(x)-1)*p)],3)

def summarize(rows,cohort,split,model):
    arr=[x for x in rows if x["model"]==model and x["cohort"]==cohort and
         (split=="ALL" or x["split"]==split)]
    chars=sum(x["n_chars"] for x in arr)
    return {
      "cohort":cohort,"scene":split,"model":model,"n_lines":len(arr),
      "n_chars":chars,
      "char_error_rate":round(sum(x["edit_distance"] for x in arr)/max(1,chars),5),
      "line_exact_rate":round(sum(x["edit_distance"]==0 for x in arr)/max(1,len(arr)),5),
      "rec_infer_p50_ms":percentile([x["rec_ms"] for x in arr],.5),
      "rec_infer_p95_ms":percentile([x["rec_ms"] for x in arr],.95),
      "total_rec_infer_ms":round(sum(x["rec_ms"] for x in arr),2),
    }

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--models",required=True)
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    dest=Path(args.out);dest.mkdir(parents=True,exist_ok=True)
    root=Path(args.models)
    sess={};chars={};input_name={};hashes={}
    opts=ort.SessionOptions()
    opts.intra_op_num_threads=2
    opts.inter_op_num_threads=1
    opts.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    for model in MODELS:
        onnx=root/model/"inference.onnx"
        yml=root/model/"inference.yml"
        assert onnx.exists() and yml.exists(),model
        sha=file_sha(onnx);yc=file_sha(yml)
        if model=="v6_small":
            assert sha==REC_SHA,(model,sha)
            assert yc==REC_CONFIG_SHA,(model,yc)
        hashes[model]={"onnx_sha256":sha,"dict_sha256":yc,
                        "model_megabytes":round(onnx.stat().st_size/1e6,2)}
        config=yaml.safe_load(yml.read_text())
        assert config["PostProcess"]["name"]=="CTCLabelDecode"
        dictionary=list(config["PostProcess"]["character_dict"])
        if not dictionary or dictionary[-1]!=" ":dictionary.append(" ")
        chars[model]=dictionary
        print("MODEL_INFO",model,json.dumps(hashes[model]),"dict_chars",len(dictionary),flush=True)
        session=ort.InferenceSession(str(onnx),sess_options=opts,
                                     providers=["CPUExecutionProvider"])
        inp=session.get_inputs()[0]
        assert len(inp.shape)==4 and inp.shape[1]==3 and inp.shape[2]==48,(model,inp.shape)
        sess[model]=session
        input_name[model]=inp.name
        print("MODEL_INPUT",model,inp.shape,
              "OUTPUT",session.get_outputs()[0].shape,flush=True)

    requests_session=requests.Session()
    refs=download_metadata(requests_session)
    with ThreadPoolExecutor(max_workers=6) as pool:
        photos=[r for r in pool.map(fetch_photo,refs) if r is not None]
    photos.sort(key=lambda r:(r[0],r[1],r[2]))
    selected,photo_ids=build_crops(photos)
    assert len(selected)>=900, f"Too few real labeled crops: {len(selected)}"
    assert len(set(z[0] for z in selected))==2,"missing independent photo cohort"

    records=[];n=0
    for cohort,split,img_idx,region,ground_truth,crop in selected:
        n+=1;key=(cohort,split,img_idx,region)
        for model in MODELS:
            pred,confidence,elapsed=rec(sess[model],input_name[model],chars[model],crop)
            gt=normalize(ground_truth)
            err=distance(gt,normalize(pred))
            records.append({
               "cohort":cohort,"split":split,"image":img_idx,"region":region,
               "model":model,"truth":ground_truth,"pred":pred,
               "n_chars":len(gt),"edit_distance":err,
               "confidence":round(confidence,5),"rec_ms":round(elapsed,4),
               "has_digit":int(any(c.isdigit() for c in gt)),
               "has_cjk":int(any("\u3400"<=c<="\u9fff" for c in gt)),
               "crop_height":crop.shape[0],"crop_width":crop.shape[1],
            })
        if n%150==0:print("MODEL_SWAP_PROGRESS",n,"of",len(selected),flush=True)
    metrics=[]
    for cohort in COHORTS:
        for category in ["ALL","ReCTS","RCTW","ART"]:
            for model in MODELS:
                result=summarize(records,cohort,category,model)
                metrics.append(result)
                print("MODEL_SWAP_METRIC",json.dumps(result,ensure_ascii=False),flush=True)

    by_key=defaultdict(dict)
    for row in records:
        by_key[(row["cohort"],row["split"],row["image"],row["region"])][row["model"]]=row
    assert len(by_key)==len(selected) and all(len(d)==len(MODELS) for d in by_key.values())
    for cohort in COHORTS:
        subset=[x for key,x in by_key.items() if key[0]==cohort]
        for model in MODELS[1:]:
            result={"cohort":cohort,"versus":"v6_small","alternative":model,
                 "n_paired_lines":len(subset),
                 "improved":sum(x[model]["edit_distance"]<x["v6_small"]["edit_distance"] for x in subset),
                 "worse":sum(x[model]["edit_distance"]>x["v6_small"]["edit_distance"] for x in subset),
                 "equal":sum(x[model]["edit_distance"]==x["v6_small"]["edit_distance"] for x in subset),
                 "small_wrong_to_exact":sum(x["v6_small"]["edit_distance"]>0 and x[model]["edit_distance"]==0 for x in subset),
                 "small_exact_to_wrong":sum(x["v6_small"]["edit_distance"]==0 and x[model]["edit_distance"]>0 for x in subset),
                 "small_total_edits":sum(x["v6_small"]["edit_distance"] for x in subset),
                 "alternative_total_edits":sum(x[model]["edit_distance"] for x in subset)}
            print("PAIRED_MODEL_SWAP",json.dumps(result,ensure_ascii=False),flush=True)

    with (dest/"per_line_predictions.csv").open("w",newline="",encoding="utf-8-sig") as f:
        wr=csv.DictWriter(f,fieldnames=list(records[0]));wr.writeheader();wr.writerows(records)
    with (dest/"metrics.csv").open("w",newline="",encoding="utf-8-sig") as f:
        wr=csv.DictWriter(f,fieldnames=list(metrics[0]));wr.writeheader();wr.writerows(metrics)
    (dest/"metadata.json").write_text(json.dumps({
       "dataset":DATASET,"cohorts":COHORTS,"models":hashes,
       "annotated_source_photos":len(photo_ids),
       "n_identical_crops":len(selected),
       "n_model_inference_calls":len(records),
       "source_image_bytes_uploaded":False,
       "limitations":"Only annotation-guided text crops; not full detection or end-to-end Android USB OCR. CPU timing is GitHub runner.",
       "color_space":"RGB matching corrected #315 SDK (old BGR approximation scores not directly comparable)",
    },indent=2,ensure_ascii=False))
    print("MODEL_SWAP_SAME_REAL_IMAGE_CROPS=PASS",len(selected),len(records),flush=True)

if __name__=="__main__":main()
