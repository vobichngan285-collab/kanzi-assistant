#!/usr/bin/env python3
"""Check whether prior RGB-only cloud OCR comparisons transfer to actual BGR Android.

Release Android workflow forcibly removes BGR->RGB conversion because
official PP-OCRv6 inference.yml specifies img_mode: BGR. Older cloud A/B
used PIL RGB crops and was not SDK-channel-identical. This probes BOTH
color orders on the SAME real annotated public photos and BOTH official
SHA-pinned ONNX recognizers. It does not alter the Android app.
"""
from collections import Counter,defaultdict
from concurrent.futures import ThreadPoolExecutor
import argparse,csv,hashlib,io,json,math,statistics,time,unicodedata
from pathlib import Path
import requests
import numpy as np
import cv2
import onnxruntime as ort
from PIL import Image
import yaml

SOURCES={"DEV":{"ReCTS":[(0,12)],"RCTW":[(0,12)],"ART":[(0,12)]},
         "HOLDOUT":{"ReCTS":[(500,12)],"RCTW":[(500,12)],"ART":[(500,12)]}}
SHAS={"small":"5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634",
      "medium":"9c09abf0957f7968c7586464b7397b84ad2387a0497a351af40e9acc71b673ba"}
YMLS={"small":"ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1",
      "medium":"991b700facf5b50a7de193468207d5f4255b538dde0d312ae3b7c7a9b6873129"}

def norm(a):
    return "".join(c for c in unicodedata.normalize("NFKC",a).lower() if not c.isspace())

def edit(a,b):
    dp=list(range(len(b)+1))
    for i,ca in enumerate(a,1):
        ndp=[i]
        for j,cb in enumerate(b,1):
            ndp.append(min(ndp[j-1]+1,dp[j]+1,dp[j-1]+(ca!=cb)))
        dp=ndp
    return dp[-1]

def annotated_rows():
    session=requests.Session();rows=[]
    for cohort,groups in SOURCES.items():
        for split,parts in groups.items():
            for offset,length in parts:
                for attempt in range(5):
                    try:
                        r=session.get("https://datasets-server.huggingface.co/rows",
                            params={"dataset":"Yesianrohn/OCR-Data","config":"default",
                                    "split":split,"offset":offset,"length":length},
                                    timeout=75)
                        r.raise_for_status()
                        for x in r.json()["rows"]:
                            d=x["row"];img=d.get("image") or {}
                            if img.get("src"):
                                rows.append((cohort,split,int(x["row_idx"]),img["src"],
                                             d.get("texts",[]),d.get("polygons",[])))
                        break
                    except Exception as exc:
                        if attempt==4:raise
                        time.sleep(3*(attempt+1))
    return rows

def load_image(row):
    cohort,split,index,url,texts,polys=row
    for retry in range(4):
        try:
            r=requests.get(url,timeout=55)
            r.raise_for_status()
            arr=np.asarray(Image.open(io.BytesIO(r.content)).convert("RGB"))
            return cohort,split,index,arr,texts,polys
        except Exception as exc:
            if retry==3:
                print("MISSING_REAL_PHOTO",cohort,split,index,str(exc)[:100],flush=True)
                return None
            time.sleep(3*(retry+1))

def minrect_crop(photo,coordinates):
    poly=np.asarray(coordinates,np.float32).reshape(-1,2)
    if not np.isfinite(poly).all():return None
    rect=cv2.minAreaRect(poly)
    pts=cv2.boxPoints(rect)
    ix=np.argsort(pts[:,0])
    l=pts[ix[:2]];r=pts[ix[2:]]
    tl,bl=l[np.argsort(l[:,1])]
    tr,br=r[np.argsort(r[:,1])]
    ordered=np.float32([tl,tr,br,bl])
    w=int(max(np.linalg.norm(tl-tr),np.linalg.norm(bl-br)))
    h=int(max(np.linalg.norm(tl-bl),np.linalg.norm(tr-br)))
    if w<5 or h<6 or w>4000 or h>4000:return None
    output=np.float32([[0,0],[w,0],[w,h],[0,h]])
    transform=cv2.getPerspectiveTransform(ordered,output)
    crop=cv2.warpPerspective(photo,transform,(w,h),flags=cv2.INTER_CUBIC,
                             borderMode=cv2.BORDER_REPLICATE)
    if crop.shape[0]/max(1,crop.shape[1])>=1.5:
        crop=cv2.rotate(crop,cv2.ROTATE_90_COUNTERCLOCKWISE)
    return crop

def create_models(root):
    sessions={};dictionaries={}
    opts=ort.SessionOptions()
    opts.intra_op_num_threads=2
    opts.inter_op_num_threads=1
    for mode in SHAS:
        onnx=root/(mode+".onnx")
        yml=root/(mode+".yml")
        assert hashlib.sha256(onnx.read_bytes()).hexdigest()==SHAS[mode]
        assert hashlib.sha256(yml.read_bytes()).hexdigest()==YMLS[mode]
        config=yaml.safe_load(yml.read_text())
        pp=config["PostProcess"]["character_dict"]
        chars=list(pp)
        if chars[-1]!=" ":chars.append(" ")
        assert config["PreProcess"]["transform_ops"],mode
        sessions[mode]=ort.InferenceSession(str(onnx),sess_options=opts,
                providers=["CPUExecutionProvider"])
        dictionaries[mode]=chars
        print("MODEL_BGR_CHECK",mode,"dict_len",len(chars),flush=True)
    return sessions,dictionaries

def do_rec(sess,dictionary,crop):
    h,w=crop.shape[:2]
    out_w=min(3200,max(1,int(math.ceil(48*w/h))))
    data=cv2.resize(crop,(out_w,48),interpolation=cv2.INTER_LINEAR)
    arr=np.ascontiguousarray((data.astype(np.float32)/127.5-1.0).transpose(2,0,1)[None])
    input_name=sess.get_inputs()[0].name
    t=time.perf_counter()
    prediction=sess.run(None,{input_name:arr})[0][0]
    inference_ms=(time.perf_counter()-t)*1000
    inds=prediction.argmax(axis=1)
    chars=[];probs=[];prev=-1
    max_probs=prediction.max(axis=1)
    for t,ii in enumerate(inds):
        ii=int(ii)
        if ii and ii!=prev and ii<=len(dictionary):
            chars.append(dictionary[ii-1])
            probs.append(float(max_probs[t]))
        prev=ii
    # Match PaddleOCR SDK CTCDecoder: average of kept token scores.
    confidence=sum(probs)/len(probs) if probs else 0.0
    return "".join(chars),inference_ms,confidence

def p95(v):
    s=sorted(v)
    return round(s[int(.95*(len(s)-1))],2) if v else None

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--models",required=True)
    parser.add_argument("--out",required=True)
    args=parser.parse_args()
    root=Path(args.models)
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    models,chars=create_models(root)
    annotated=annotated_rows()
    with ThreadPoolExecutor(max_workers=5) as pool:
        imgs=[x for x in pool.map(load_image,annotated) if x is not None]
    print("ANDROID_BGR_PUBLIC_PHOTOS",len(imgs),flush=True)
    if len(imgs)<60:raise RuntimeError("insufficient source images")
    rows=[]
    for cohort,split,index,photo,texts,polys in imgs:
        selected=0
        for region,(truth,poly) in enumerate(zip(texts,polys)):
            if selected>=7:break
            if not isinstance(truth,str) or "###" in truth or not 2<=len(norm(truth))<=65:
                continue
            if not isinstance(poly,list) or len(poly)<8:continue
            try:crop=minrect_crop(photo,poly)
            except (ValueError,cv2.error):continue
            if crop is None:continue
            selected+=1
            for color in ["BGR_ANDROID","RGB_OLD_CLOUD"]:
                test_crop=cv2.cvtColor(crop,cv2.COLOR_RGB2BGR) if color=="BGR_ANDROID" else crop
                for model in ["small","medium"]:
                    pred,ms,confidence=do_rec(models[model],chars[model],test_crop)
                    rows.append({
                       "cohort":cohort,"split":split,"image":index,"region":region,
                       "color":color,"model":model,
                       "truth":truth,"prediction":pred,"chars":len(norm(truth)),
                       "edits":edit(norm(truth),norm(pred)),"rec_ms":round(ms,3),
                       "rec_confidence":round(confidence,5),
                    })
    assert len(rows)>=550,len(rows)
    report=[]
    for cohort in ["DEV","HOLDOUT"]:
        for color in ["BGR_ANDROID","RGB_OLD_CLOUD"]:
            for model in ["small","medium"]:
                a=[r for r in rows if r["cohort"]==cohort and r["color"]==color
                   and r["model"]==model]
                chars=sum(x["chars"] for x in a)
                res={"cohort":cohort,"channel_order":color,"recognizer":model,
                  "lines":len(a),
                  "CER":round(sum(x["edits"] for x in a)/max(1,chars),5),
                  "exact_rate":round(sum(x["edits"]==0 for x in a)/max(1,len(a)),5),
                  "rec_p95_ms":p95([x["rec_ms"] for x in a])}
                report.append(res)
                print("REAL_BGR_VS_RGB_MODEL_AB",json.dumps(res,ensure_ascii=False),flush=True)
    by=defaultdict(dict)
    for r in rows:
        by[r["cohort"],r["color"],r["split"],r["image"],r["region"]][r["model"]]=r
    for cohort in ["DEV","HOLDOUT"]:
        for color in ["BGR_ANDROID","RGB_OLD_CLOUD"]:
            pairs=[x for k,x in by.items() if k[0]==cohort and k[1]==color]
            print("MODEL_SWAP_CORRECT_COLOR_PAIRS",json.dumps({
                "cohort":cohort,"color":color,"lines":len(pairs),
                "medium_improved":sum(x["medium"]["edits"]<x["small"]["edits"] for x in pairs),
                "medium_worsened":sum(x["medium"]["edits"]>x["small"]["edits"] for x in pairs)},
                ensure_ascii=False),flush=True)
    # Predeclared 0.95 Small-confidence gating, budget 1 or 3, and
    # SOURCE-IMAGE GROUPING, not individual GT crop selected by error.
    policy_rows=[]
    grouped=defaultdict(list)
    for key,pair in by.items():
        cohort,color,split,image,region=key
        if color!="BGR_ANDROID":continue
        grouped[cohort,split,image].append((region,pair))
    for cohort in ["DEV","HOLDOUT"]:
        all_photo_groups=[(k,v) for k,v in grouped.items() if k[0]==cohort]
        for cap in [0,1,3,99]:
            chosen=[]
            medium_count=0
            for key,crops in all_photo_groups:
                candidate=sorted(
                    [(int(region),pair) for region,pair in crops if
                     float(pair["small"]["rec_confidence"])<.95],
                    key=lambda x:(float(x[1]["small"]["rec_confidence"]),x[0]))
                selected={region for region,pair in candidate[:cap]}
                medium_count+=len(selected)
                for region,pair in crops:
                    chosen.append(pair["medium"] if int(region) in selected else pair["small"])
            chars_total=sum(x["chars"] for x in chosen)
            row={"cohort":cohort,"channel_order":"BGR_ANDROID",
                 "policy_max_medium_per_photo":cap,
                 "GT_annotated_line_crops":len(chosen),
                 "medium_rec_calls":medium_count,
                 "gt_CER":round(sum(x["edits"] for x in chosen)/max(1,chars_total),5),
                 "gt_exact_rate":round(sum(x["edits"]==0 for x in chosen)/max(1,len(chosen)),5)}
            policy_rows.append(row)
            print("BGR_MODEL_RESCUE_POLICY",json.dumps(row,ensure_ascii=False),flush=True)
    with (out/"bgr_budget_policy.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(policy_rows[0]))
        w.writeheader();w.writerows(policy_rows)
    for path,data in [("per_line.csv",rows),("summary.csv",report)]:
        with (out/path).open("w",newline="",encoding="utf-8-sig") as f:
            w=csv.DictWriter(f,fieldnames=list(data[0]))
            w.writeheader();w.writerows(data)
    (out/"warning.txt").write_text(
      "BGR_ANDROID matches the RELEASE pipeline's scripted SDK patch and inference.yml BGR declaration.\n"
      "This is an annotation crop study, NOT an Android device benchmark; 72 public photos only.\n"
      "The 2026-11-03 earlier 344 photo study used RGB source input and must NOT be asserted Android-exact.\n"
      "The cap1/cap3 policies select annotated source crops, NOT detected OCR boxes; real phone performance remains untested.\n"
      "Model SHA fixed. Pictures are never stored in test artifacts.\n")
    print("RELEASE_BGR_COLOR_MODEL_ALIGNMENT=PASS",len(rows),flush=True)

if __name__=="__main__":main()
