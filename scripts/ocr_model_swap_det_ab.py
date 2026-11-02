#!/usr/bin/env python3
"""Detector-model swap on EXACT same public photos and annotations.

Controlled detector comparison (PP-OCRv6 Small vs Medium vs Tiny):
  * identical BGR input preprocessing (max 1600px), heatmap threshold .20,
    unclip 1.40, box confidence .45 with sensitivity test .35;
  * identical GT IoU>=0.4 greedy one-to-one matching;
  * detector ONNX changed, all else fixed.
PP-OCRv5 Mobile is a separate "native-settings" comparison:
  * max 960, bitmap threshold .30, unclip 1.50, box score .60;
    not an isolated model-size effect, clearly labeled.

All original model files are SHA256-logged. Full detector postprocess
approximates the Java SDK (Python OpenCV + pyclipper): results are proxies.
GT-unmatched boxes may be real unlabeled text (NOT proven false positives).
No OCR recognition, no Android camera or TTS in this experiment.
"""
from collections import Counter,defaultdict
import argparse,csv,hashlib,json,math,time
from pathlib import Path
import cv2
import numpy as np
import onnxruntime as ort
import ocr_box_value_deep_dive as base
from ocr_model_swap_rec_ab import download_metadata,fetch_photo

MODEL_NAMES=["v6_small","v6_medium","v6_tiny","v5_mobile"]
# Model-order is fixed; all original public test photos get every detector.
CONFIG={
    "v6_small":{"side":1600,"bin":.20,"unclip":1.40,"thresholds":[.45,.35]},
    "v6_medium":{"side":1600,"bin":.20,"unclip":1.40,"thresholds":[.45,.35]},
    "v6_tiny":{"side":1600,"bin":.20,"unclip":1.40,"thresholds":[.45,.35]},
    "v5_mobile":{"side":960,"bin":.30,"unclip":1.50,"thresholds":[.60,.45]},
}
MODEL_SHA={
    "v6_small":"d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e",
    "v6_medium":"eb13b44b25bb36f89528b68720af8a61d9cf381176107f465db1757b65d086e1",
}

def preprocess(rgb,side):
    h,w=rgb.shape[:2]
    ratio=min(1.,float(side)/max(h,w))
    nh=max(32,round(int(h*ratio)/32.0)*32)
    nw=max(32,round(int(w*ratio)/32.0)*32)
    if max(nh,nw)>side:
        factor=side/max(nh,nw)
        nh=max(32,round(int(nh*factor)/32.)*32)
        nw=max(32,round(int(nw*factor)/32.)*32)
    img=cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR)
    resized=cv2.resize(img,(nw,nh),interpolation=cv2.INTER_LINEAR).astype(np.float32)
    mean=np.array([.485,.456,.406],dtype=np.float32)
    std=np.array([.229,.224,.225],dtype=np.float32)
    return np.ascontiguousarray(((resized/255.-mean)/std).transpose(2,0,1)[None])

def gt_boxes(texts,polygons):
    gt=[]
    for truth,poly in zip(texts,polygons):
        if not isinstance(truth,str) or "###" in truth or len(poly)<8:continue
        try:
            pts=np.asarray(poly,dtype=np.float32).reshape(-1,2)
            if not np.isfinite(pts).all():continue
            b=base.bbox_from_quad(pts)
            if b[2]-b[0]<6 or b[3]-b[1]<6:continue
            gt.append(b)
        except Exception:continue
    return gt

def matched_metrics(found,gt):
    edges=[]
    for i,b in enumerate(found):
        for j,g in enumerate(gt):
            ov=base.box_iou(b["bbox"],g)
            if ov>=.4:edges.append((ov,i,j))
    edges.sort(reverse=True)
    selected=set();ground=set()
    for ov,i,j in edges:
        if i in selected or j in ground:continue
        selected.add(i);ground.add(j)
    return len(ground)

def p95(v):
    if not v:return None
    arr=sorted(v);return round(arr[int(.95*(len(arr)-1))],3)

def benchmark(model,all_photos,root,output):
    config=CONFIG[model]
    path=root/model/"inference.onnx"
    assert path.exists()
    sha=hashlib.sha256(path.read_bytes()).hexdigest()
    if model in MODEL_SHA:assert sha==MODEL_SHA[model],(model,sha)
    options=ort.SessionOptions()
    options.intra_op_num_threads=2
    options.inter_op_num_threads=1
    options.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    session=ort.InferenceSession(str(path),sess_options=options,providers=["CPUExecutionProvider"])
    inp=session.get_inputs()[0]
    assert len(inp.shape)==4 and inp.shape[1]==3,(model,inp.shape)
    print("DETECTOR_MODEL_INFO",model,sha,"bytes",path.stat().st_size,
          "input",inp.shape,"options",config,flush=True)
    base.DET_BITMAP_THRESH=config["bin"]
    base.DET_UNCLIP=config["unclip"]
    base.THRESHOLDS=config["thresholds"]
    name=inp.name
    rows=[]
    for index,(cohort,split,idx,rgb,texts,polygons) in enumerate(all_photos,1):
        original_h,original_w=rgb.shape[:2]
        gt=gt_boxes(texts,polygons)
        if not gt:continue
        x=preprocess(rgb,config["side"])
        start=time.perf_counter()
        heatmap=session.run(None,{name:x})[0]
        inference_ms=(time.perf_counter()-start)*1000
        prob=heatmap.reshape(heatmap.shape[-2:])
        start=time.perf_counter()
        detected=base.detections(prob,original_h,original_w)
        post_ms=(time.perf_counter()-start)*1000
        for threshold in config["thresholds"]:
            boxes=[b for b in detected if b["score"]>=threshold]
            rows.append({
                "cohort":cohort,"split":split,"image":idx,"model":model,
                "threshold":threshold,"gt_boxes":len(gt),
                "num_detected":len(boxes),
                "num_matched":matched_metrics(boxes,gt),
                "model_inference_ms":round(inference_ms,3),
                "postprocessing_ms":round(post_ms,3),
            })
        if index%45==0:
            print("DETECTOR_MODEL_PROGRESS",model,index,"of",len(all_photos),flush=True)
    del session
    return rows

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--models",required=True)
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    root=Path(args.models)
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    import requests
    source=download_metadata(requests.Session())
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(max_workers=6) as pool:
        photos=[r for r in pool.map(fetch_photo,source) if r is not None]
    photos.sort(key=lambda r:(r[0],r[1],r[2]))
    print("DETECTOR_SAME_PHOTOS",len(photos),flush=True)
    assert len(photos)>=300,"not enough real photos for same-source comparison"
    assert len({r[0] for r in photos})==2
    rows=[]
    for model in MODEL_NAMES:
        rows+=benchmark(model,photos,root,out)
    metrics=[]
    for cohort in ["DEV_216","HOLDOUT_132"]:
        for split in ["ALL","ReCTS","RCTW","ART"]:
            for model in MODEL_NAMES:
                for threshold in CONFIG[model]["thresholds"]:
                    sub=[r for r in rows if r["cohort"]==cohort and r["model"]==model and
                         r["threshold"]==threshold and (split=="ALL" or r["split"]==split)]
                    gt=sum(r["gt_boxes"] for r in sub);matched=sum(r["num_matched"] for r in sub)
                    boxes=sum(r["num_detected"] for r in sub)
                    record={
                        "cohort":cohort,"split":split,"model":model,"box_threshold":threshold,
                        "images":len(sub),"annotated_text_boxes":gt,
                        "matched_text_boxes":matched,"detected_boxes":boxes,
                        "proxy_recall":round(matched/max(1,gt),5),
                        "matched_per_detected":round(matched/max(1,boxes),5),
                        "det_infer_p95_ms":p95([r["model_inference_ms"] for r in sub]),
                        "postprocess_p95_ms":p95([r["postprocessing_ms"] for r in sub]),
                    }
                    metrics.append(record)
                    print("DETECTOR_SWAP_METRIC",json.dumps(record,ensure_ascii=False),flush=True)
    for path,data in [("per_photo.csv",rows),("model_scores.csv",metrics)]:
        with (out/path).open("w",newline="",encoding="utf-8-sig") as f:
            writer=csv.DictWriter(f,fieldnames=list(data[0]))
            writer.writeheader();writer.writerows(data)
    (out/"warnings.txt").write_text(
        "Image source: public GT-annotated ReCTS/RCTW/ART via Yesianrohn/OCR-Data.\n"
        "ONNX GPU/Android timing not measured, and detection postprocess is a Python approximation.\n"
        "GT-unmatched candidates are unknown, not verified false positives.\n"
        "v5 mobile uses native-like 960/.30/.60 default, so cannot isolate size alone.\n"
        "Medium/small/tiny use identical PP-OCRv6 detector params and same pictures.\n"
        "Recognition differences are measured separately in ocr_model_swap_rec_ab.py.\n"
    )
    print("DETECTOR_MODEL_SWAP_SAME_PHOTOS=PASS",len(photos),flush=True)

if __name__=="__main__":main()
