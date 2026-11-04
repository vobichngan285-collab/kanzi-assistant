#!/usr/bin/env python3
"""Actual offline Small DET+REC benchmark on new Wikimedia scene photographs.
Question: Does a fast pre-recognition sharpness gate avoid unreliable OCR,
and how many whole short phrases does it withhold? No guessed ground truth.
Photos were selected and saved BEFORE recognition in downloader script.
"""
from __future__ import annotations
import csv, json, collections, hashlib, time, math, statistics
from pathlib import Path
import cv2, numpy as np, onnxruntime as ort, yaml
from PIL import Image
import ocr_box_value_deep_dive as sdk

ROOT=Path("/tmp/kanzi-commons-scene")
OUT=ROOT/"out";MODELS=ROOT/"models"
OUT.mkdir(parents=True,exist_ok=True)
MODEL_SHA={
"det.onnx":"d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e",
"small_rec.onnx":"5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634",
"small_rec.yml":"ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
}
for name,sha in MODEL_SHA.items():
 assert hashlib.sha256((MODELS/name).read_bytes()).hexdigest()==sha,("MODEL_SHA_ERROR",name)
opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
det=ort.InferenceSession(str(MODELS/"det.onnx"),opts,providers=["CPUExecutionProvider"])
rec=ort.InferenceSession(str(MODELS/"small_rec.onnx"),opts,providers=["CPUExecutionProvider"])
chars=list(yaml.safe_load((MODELS/"small_rec.yml").read_text())["PostProcess"]["character_dict"])
if chars[-1]!=" ":chars.append(" ")
rec_name=rec.get_inputs()[0].name;det_name=det.get_inputs()[0].name
sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40;sdk.THRESHOLDS=[.45]
sdk.MAX_RECOGNITION_WIDTH=3200
FRAME_LAP_MIN=85.0
ROI_LAP_MIN=60.0
ROI_HEIGHT_MIN=17.0
EXTREME_LUMA_MAX=.36
VARIANTS={ "original":0., "light_blur":1.3, "moderate_blur":2.6 }

def focus(rgb):
 h,w=rgb.shape[:2]
 y1,y2=h//10,h-h//10;x1,x2=w//10,w-w//10
 gray=cv2.cvtColor(rgb[y1:y2,x1:x2],cv2.COLOR_RGB2GRAY)
 if max(gray.shape)>1000:
  ratio=1000/max(gray.shape)
  gray=cv2.resize(gray,(max(1,int(gray.shape[1]*ratio)),max(1,int(gray.shape[0]*ratio))))
 lap=float(cv2.Laplacian(gray,cv2.CV_64F).var())
 extreme=float(np.mean((gray<=9)|(gray>=246)))
 return lap,extreme

def quality(g_lap,g_ext,c_lap,c_ext,crop_height):
 return (g_lap>=FRAME_LAP_MIN and g_ext<=EXTREME_LUMA_MAX and
         c_lap>=ROI_LAP_MIN and c_ext<=EXTREME_LUMA_MAX and
         crop_height>=ROI_HEIGHT_MIN)

def short_horizontal(box):
 x1,y1,x2,y2=box["bbox"]
 w=x2-x1;h=y2-y1
 return 8<=h<=170 and w/max(1,h)>=1.60 and 10<=w<=1100

def detect(rgb):
 h,w=rgb.shape[:2]
 t=time.perf_counter()
 raw=det.run(None,{det_name:sdk.det_preprocess(rgb)})[0]
 dt=(time.perf_counter()-t)*1000
 boxes=[x for x in sdk.detections(raw.reshape(raw.shape[-2:]),h,w)
        if x["score"]>=.45]
 candidate=[x for x in boxes if short_horizontal(x)]
 # Holdout scope is short phrases, not all text from long documents.
 truncated=max(0,len(candidate)-100)
 return candidate[:100],len(boxes),len(candidate),truncated,dt

def run(rgb,meta,variant):
 glap,gext=focus(rgb)
 if variant=="original":work=rgb
 else:work=cv2.GaussianBlur(rgb,(0,0),VARIANTS[variant])
 if variant!="original":glap,gext=focus(work)
 boxes,all_det,short_det,discarded,det_ms=detect(work)
 rec_ms=0.;records=[]
 for i,box in enumerate(boxes):
  crop=sdk.crop_box(work,box)
  if crop is None:continue
  clap,cext=focus(crop)
  t,confidence,ms=sdk.rec(rec,rec_name,chars,crop) # SDK uses RGB tensors
  rec_ms+=ms
  text=sdk.normalize(t)
  x1,y1,x2,y2=box["bbox"]
  should=quality(glap,gext,clap,cext,float(y2-y1))
  # The additional short-text rule NEVER trims predicted word fragments.
  within=2<=len(text)<=24
  records.append({"image_id":meta["id"],"category":meta["category"],
    "variant":variant,"box_index":i,
    "bbox_x1":round(float(x1),1),"bbox_y1":round(float(y1),1),
    "bbox_x2":round(float(x2),1),"bbox_y2":round(float(y2),1),
    "box_pixel_height":round(float(y2-y1),1),
    "whole_original_text":t,"normalized_text":text,
    "confidence":round(float(confidence),4),
    "frame_lap":round(glap,1),"crop_lap":round(clap,1),
    "frame_extreme_fraction":round(gext,3),"crop_extreme_fraction":round(cext,3),
    "baseline_short_nonempty":int(within),
    "gate_short_whole":int(should and within),
    "withheld_for_quality":int(within and not should),
    "predicted_characters":len(text)})
 stat={"image_id":meta["id"],"category":meta["category"],"variant":variant,
  "frame_laplacian_var":round(glap,2),"frame_extreme_fraction":round(gext,4),
  "frame_gate":int(glap>=FRAME_LAP_MIN and gext<=EXTREME_LUMA_MAX),
  "det_total":all_det,"short_detector_candidates":short_det,
  "candidate_cap_excess":discarded,
  "short_nonempty_baseline":sum(x["baseline_short_nonempty"] for x in records),
  "short_whole_quality_gate":sum(x["gate_short_whole"] for x in records),
  "baseline_chars":sum(x["predicted_characters"] for x in records if x["baseline_short_nonempty"]),
  "quality_gate_chars":sum(x["predicted_characters"] for x in records if x["gate_short_whole"]),
  "det_ms_linux":round(det_ms,2),"rec_ms_linux":round(rec_ms,2)}
 return records,stat

manifest=json.loads((OUT/"manifest.json").read_text(encoding="utf-8"))
assert len(manifest)>=32
bygroup=collections.Counter(x["category"] for x in manifest)
assert all(bygroup[k]>=7 for k in ("hospital","supermarket","storefront","restaurant_menu"))
all_rows=[];per=[]
for meta in manifest:
 image=np.asarray(Image.open(ROOT/meta["local_file"]).convert("RGB"))
 for variant in VARIANTS:
  row,stat=run(image,meta,variant)
  all_rows+=row;per.append(stat)
  print("REAL_SCENE_SMALL",meta["id"],variant,
    "det",stat["det_total"],"short",stat["short_nonempty_baseline"],
    "passed",stat["short_whole_quality_gate"],
    "frame_lap",stat["frame_laplacian_var"],flush=True)
assert len(per)==len(manifest)*3

def group_metrics(rows):
 by=collections.defaultdict(collections.Counter)
 for r in rows:
  by[(r["category"],r["variant"])].update({
   "images":1,"short_candidates":r["short_nonempty_baseline"],
   "gate_passed":r["short_whole_quality_gate"],
   "baseline_predicted_chars":r["baseline_chars"],
   "passed_predicted_chars":r["quality_gate_chars"],
   "frame_rejected":int(not r["frame_gate"]),
   "det_boxes":r["det_total"],
   "short_detection_cap_excess":r["candidate_cap_excess"]})
 return {cat:{
  variant:dict(by[(cat,variant)]) for variant in VARIANTS
 } for cat in ("hospital","supermarket","storefront","restaurant_menu")}
scenarios=group_metrics(per)
totals={var:{
  "images":sum(x["variant"]==var for x in per),
  "short_candidates":sum(x["short_nonempty_baseline"] for x in per if x["variant"]==var),
  "passed_whole_phrases":sum(x["short_whole_quality_gate"] for x in per if x["variant"]==var),
  "frames_rejected":sum(not x["frame_gate"] for x in per if x["variant"]==var),
  "baseline_predicted_chars":sum(x["baseline_chars"] for x in per if x["variant"]==var),
  "passed_predicted_chars":sum(x["quality_gate_chars"] for x in per if x["variant"]==var),
  } for var in VARIANTS}
for v in VARIANTS:
 x=totals[v];x["candidate_phrase_retention"]=round(x["passed_whole_phrases"]/max(1,x["short_candidates"]),4)

summary={"status":"complete","source":"Wikimedia Commons Commons MediaWiki API real photographic files",
 "new_public_photos":len(manifest),"categories":dict(bygroup),
 "params_locked_before_inference":{"frame_laplacian_min":FRAME_LAP_MIN,
  "crop_laplacian_min":ROI_LAP_MIN,"min_crop_height":ROI_HEIGHT_MIN,
  "max_extreme_fraction":EXTREME_LUMA_MAX,
  "short_horizontal_aspect_min":1.6,"short_pred_chars_range":[2,24],
  "blur_sigma":VARIANTS,"det_box_threshold":.45},
 "model_sha256":MODEL_SHA,"actual_pinned_small_det_rec":True,
 "per_category":scenarios,"totals":totals,
 "does_not_measure_accuracy":True,
 "limitations":[
   "No independent ground-truth transcriptions in these Wikimedia scene photos, so predicted text confidence or retention is NOT recognition accuracy or true sentence completeness.",
   "Blur variants are derived from same originals, not independent real shooting conditions.",
   "Whole predicted region is preserved or withheld, but its source sentence may still be clipped by the detector.",
   "Background edges can keep frame Laplacian high even when foreground letters are fuzzy; this is a fallible heuristic.",
   "Conservative gate can hide readable signs; rejected phrases count as coverage loss.",
   "Linux ONNX approximate Android DET+REC, no vivo real-time FPS/heat/TTS result.",
   "Image sampling was fixed by source category and index before seeing model predictions."]}
for name,rows in [("all_real_small_short_predicted_text.csv",all_rows),("per_image_original_vs_blurred.csv",per)]:
 if not rows:continue
 with (OUT/name).open("w",newline="",encoding="utf-8-sig") as f:
  writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
(OUT/"benchmark_summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding="utf-8")
print("FOUR_CATEGORY_ACTUAL_SMALL_OCR_PREFLIGHT_BENCHMARK_COMPLETE",json.dumps(totals,ensure_ascii=False),flush=True)
