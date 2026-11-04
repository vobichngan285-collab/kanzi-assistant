#!/usr/bin/env python3
"""One-pass crop geometry A/B, real unchanged Small REC on matched 36 real scene photos.
Strict paired baseline vs only 5% quad border expansion, no GT-driven crops.
Not an Android phone test, not page CER, no private source photos.
"""
import collections,csv,hashlib,json,time
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort,yaml
import ocr_box_value_deep_dive as sdk
import ocr_final_error_attribution as forensic

BASE=Path("/tmp/kanzi-crop-geometry")
OUT=BASE/"results"
OUT.mkdir(parents=True,exist_ok=True)
HASHES={
 "det.onnx":"d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e",
 "small_rec.onnx":"5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634",
 "small_rec.yml":"ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"}
for fn,h in HASHES.items():
    assert hashlib.sha256((BASE/"models"/fn).read_bytes()).hexdigest()==h,("SHA_ERROR",fn)
options=ort.SessionOptions();options.intra_op_num_threads=2;options.inter_op_num_threads=1
det=ort.InferenceSession(str(BASE/"models"/"det.onnx"),options,providers=["CPUExecutionProvider"])
rec=ort.InferenceSession(str(BASE/"models"/"small_rec.onnx"),options,providers=["CPUExecutionProvider"])
chars=list(yaml.safe_load((BASE/"models"/"small_rec.yml").read_text())["PostProcess"]["character_dict"])
if chars[-1]!=" ":chars.append(" ")
sdk.THRESHOLDS=[.45];sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40
sdk.SLICES={sp:[(500,12)] for sp in ("ART","RCTW","ReCTS")}

def expanded_quad(box,width,height):
    """Exactly one candidate: expand each side 5% relative to detected quad width/height.
    Does not change any detector thresholds, orientation, text, or recognizer.
    """
    q=np.asarray(box["quad"],np.float32)
    center=np.mean(q,axis=0,keepdims=True)
    candidate=center+(q-center)*1.10
    candidate[:,0]=np.clip(candidate[:,0],0,width-1)
    candidate[:,1]=np.clip(candidate[:,1],0,height-1)
    return {"quad":candidate}

def predict(rgb,box):
    crop=sdk.crop_box(rgb,box)
    if crop is None:return "",0.,0.
    bgr=cv2.cvtColor(crop,cv2.COLOR_RGB2BGR)
    text,conf,ms=sdk.rec(rec,rec.get_inputs()[0].name,chars,bgr)
    return text,conf,ms

def edit(t,p):
    return sdk.distance(t,sdk.normalize(p))

rows=[];photo_rows=[];total_gt=0
photos=sdk.collect_images()
assert len(photos)==36,("Expected fixed photos",len(photos))
expected={(sp,i) for sp in ("ART","RCTW","ReCTS") for i in range(500,512)}
assert {(sp,idx) for sp,idx,*_ in photos}==expected
for split,idx,rgb,texts,polys in photos:
    gt=forensic.gt_items(texts,polys)
    total_gt+=len(gt)
    h,w=rgb.shape[:2]
    raw=det.run(None,{det.get_inputs()[0].name:sdk.det_preprocess(rgb)})[0]
    boxes=[box for box in sdk.detections(raw.reshape(raw.shape[-2:]),h,w) if box["score"]>=.45]
    mapping=forensic.greedy_match(boxes,gt,.40)
    for j,(i,iou) in mapping.items():
        truth=gt[j]["normal"]
        orig,orig_conf,orig_ms=predict(rgb,boxes[i])
        padded,pad_conf,pad_ms=predict(rgb,expanded_quad(boxes[i],w,h))
        rows.append({
            "split":split,"image_idx":idx,"gt_id":j,"gt_chars":len(truth),
            "baseline_edits":edit(truth,orig),"padding5pct_edits":edit(truth,padded),
            "baseline_exact":int(sdk.normalize(orig)==truth),
            "padding5pct_exact":int(sdk.normalize(padded)==truth),
            "original_rec_ms":round(orig_ms,2),"padding_rec_ms":round(pad_ms,2),
            "original_confidence":round(orig_conf,4),"padding_confidence":round(pad_conf,4),
            "quad_width":round(float(np.linalg.norm(boxes[i]["quad"][1]-boxes[i]["quad"][0])),2),
            "quad_height":round(float(np.linalg.norm(boxes[i]["quad"][3]-boxes[i]["quad"][0])),2)})
    photo_rows.append({"split":split,"image_idx":idx,"GT_boxes":len(gt),
                       "DET_boxes":len(boxes),"strict_matched":len(mapping)})
    print("REAL_SCENE_CROP_GEOMETRY",split,idx,"matched",len(mapping),flush=True)

def summary(a):
    chars=sum(r["gt_chars"] for r in a)
    old=sum(r["baseline_edits"] for r in a)
    new=sum(r["padding5pct_edits"] for r in a)
    return {"paired_regions":len(a),"GT_chars":chars,
            "baseline_errors":old,"padding_errors":new,
            "baseline_CER":round(old/max(1,chars),6),"padding_CER":round(new/max(1,chars),6),
            "baseline_exact_regions":sum(r["baseline_exact"] for r in a),
            "padding_exact_regions":sum(r["padding5pct_exact"] for r in a),
            "padding_better":sum(r["padding5pct_edits"]<r["baseline_edits"] for r in a),
            "padding_worse":sum(r["padding5pct_edits"]>r["baseline_edits"] for r in a),
            "unchanged":sum(r["padding5pct_edits"]==r["baseline_edits"] for r in a)}

assert total_gt==244,("GT cohort changed",total_gt)
assert len(rows)==122,("Matching changes",len(rows))
s=summary(rows)
# Strong parity check with independent historical analysis (same Small, same public data).
assert s["GT_chars"]==690,("GT chars changed",s)
assert s["baseline_errors"]==111,("Historical baseline differs! Never report A/B",s)
assert s["baseline_exact_regions"]==89,("Historical exact count differs",s)
report={
    "status":"completed",
    "real_public_photos":len(photos),"total_annotated_GT":total_gt,
    "strict_matched_regions":len(rows),
    "method":"Same DET boxes and Small REC; baseline SDK-like quad crop vs same detected quad expanded 5%/side; one REC per variant, no retries",
    "geometry_not_derived_from_GT":True,
    "candidate":"quad outward 1.10x (5% added per side), original same",
    "model_weights_sha256":HASHES,
    "overall":s,
    "per_dataset":{sp:summary([r for r in rows if r["split"]==sp]) for sp in ("ART","RCTW","ReCTS")},
    "limitations":[
        "Public 36 previously used holdout photos, not vivo X200 NV21",
        "Matched 122/244 GT only; whole-image missed lines and unpaired false positives not scored",
        "Exact historical 111 errors parity checked; Android pipeline may differ",
        "Expanding quad tests crop boundaries, not fixing wrong line grouping/cross-row DET",
        "Linux benchmark timing not phone TTS or temperature",
        "GT used only for evaluating paired CER, never choosing boxes or candidate text"]}
(OUT/"summary.json").write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding="utf-8")
for name,data in [("per_matched_gt_no_text.csv",rows),("per_photo_boxes.csv",photo_rows)]:
    with (OUT/name).open("w",newline="",encoding="utf-8-sig") as f:
        wr=csv.DictWriter(f,fieldnames=list(data[0]));wr.writeheader();wr.writerows(data)
print("SMALL_CROP_PADDING_PAIRED_COMPLETED",json.dumps(report["overall"],ensure_ascii=False),flush=True)
