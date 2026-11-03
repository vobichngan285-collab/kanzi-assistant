#!/usr/bin/env python3
"""Head-to-head lightweight ChineseOCR Lite DBNet versus current Small DET.
Exactly the same public real scenes and PP-OCRv6 Small recognizer as prior study.

A: original Small DET + Small REC
B: ChineseOCR Lite 1.8MB DBNet DET + Small REC
C: original Small DET + unique DBNet boxes + Small REC
No Medium, no CRAFT concurrent detection, no app modification/APK.
"""
import argparse,hashlib,json,csv,math,time
from pathlib import Path
import numpy as np
import onnxruntime as ort
import cv2,yaml
import ocr_box_value_deep_dive as sdk
import craft_small_detector_ab as base

SMALL_DET_SHA=base.SMALL_DET_SHA
SMALL_REC_SHA=base.SMALL_REC_SHA
SMALL_YML_SHA=base.SMALL_YML_SHA
LIGHT_DBNET_EXPECTED_SHA="ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867"  # Verified from successful 72-photo run.
SHORT_EDGE=640
MAX_NEW_BOXES=180
MAX_SMALL=150
MAX_LITE=150

def sess(path,sha=None):
    p=Path(path)
    digest=hashlib.sha256(p.read_bytes()).hexdigest()
    if sha and digest!=sha:
        raise ValueError(f"Model SHA mismatch {p} {digest}")
    if p.stat().st_size<1000000:raise ValueError(f"Binary appears incomplete: {p}")
    print("LIGHT_DB_MODEL_SHA",p.name,digest,"bytes",p.stat().st_size,flush=True)
    opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
    return ort.InferenceSession(str(p),sess_options=opts,providers=["CPUExecutionProvider"])

def init(root):
    p=Path(root)
    small=sess(p/"small_det.onnx",SMALL_DET_SHA)
    rec=sess(p/"small_rec.onnx",SMALL_REC_SHA)
    light=sess(p/"light_dbnet.onnx",LIGHT_DBNET_EXPECTED_SHA)
    cfg=p/"small_rec.yml"
    if hashlib.sha256(cfg.read_bytes()).hexdigest()!=SMALL_YML_SHA:raise ValueError("Small dictionary mismatch")
    chars=list(yaml.safe_load(cfg.read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    print("LIGHT_DB_MODEL_INTERFACE",light.get_inputs()[0].name,light.get_inputs()[0].shape,
          [(x.name,x.shape) for x in light.get_outputs()],flush=True)
    return small,light,rec,chars

def light_preprocess(rgb):
    # chineseocr_lite dbnet_infer.DBNet.process(short_size=640).
    # Caller supplies PIL RGB, original conversion BGR2RGB effectively produces BGR.
    h,w=rgb.shape[:2]
    if h<w:
        new_h=SHORT_EDGE
        new_w=max(32,int((w*SHORT_EDGE/h)//32)*32)
    else:
        new_w=SHORT_EDGE
        new_h=max(32,int((h*SHORT_EDGE/w)//32)*32)
    im=cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR)
    im=cv2.resize(im,(new_w,new_h),interpolation=cv2.INTER_LINEAR).astype(np.float32)
    mean=np.asarray([.485,.456,.406],np.float32)
    std=np.asarray([.229,.224,.225],np.float32)
    x=np.ascontiguousarray(((im/255.-mean)/std).transpose(2,0,1)[None])
    return x

def light_detect(model,rgb):
    started=time.perf_counter()
    x=light_preprocess(rgb)
    raw=model.run(None,{model.get_inputs()[0].name:x})[0]
    heat=np.asarray(raw).reshape(raw.shape[-2:])
    if not np.isfinite(heat).all():raise ValueError("Nonfinite lite DBNet output")
    if heat.min()<-.1 or heat.max()>1.1:raise ValueError(f"Invalid DBNet heatmap range {heat.min()} {heat.max()}")
    sdk.DET_BITMAP_THRESH=.30
    sdk.DET_UNCLIP=2.0
    sdk.THRESHOLDS=[.50]
    boxes=[b for b in sdk.detections(heat,rgb.shape[0],rgb.shape[1]) if b["score"]>=.50]
    return boxes[:MAX_LITE],(time.perf_counter()-started)*1000

def same_gt_zero_overlap(old_boxes,gts):
    matched=base.box_pairs(old_boxes,gts)
    unseen=set(range(len(gts)))-set(matched.values())
    return [j for j in unseen if not any(base.overlap(b["bbox"],gts[j]["box"])>0 for b in old_boxes)]

def evaluate(images,small,light,rec,chars):
    allrows=[]
    for ix,(split,index,rgb,texts,polys) in enumerate(images,1):
        gt=base.gt_rows(texts,polys)
        if not gt:continue
        cohort="DEV" if index<100 else "HOLDOUT"
        started=time.perf_counter()
        sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40;sdk.THRESHOLDS=[.45]
        bxa,small_det_ms=base.small_detect(small,rgb)
        pra=base.rec_predictions(rec,chars,rgb,bxa)
        ams=(time.perf_counter()-started)*1000

        started=time.perf_counter()
        bxb,light_det_ms=light_detect(light,rgb)
        prb=base.rec_predictions(rec,chars,rgb,bxb)
        bms=(time.perf_counter()-started)*1000

        started=time.perf_counter()
        novel=[]
        for b in bxb:
            if len(novel)+len(bxa)>=MAX_NEW_BOXES:break
            if not base.dup(b,bxa+novel):novel.append(b)
        bxc=bxa+novel
        prc=dict(pra)
        extra_preds=base.rec_predictions(rec,chars,rgb,novel)
        for i,p in extra_preds.items():
            prc[len(bxa)+i]=p
        cms=ams+light_det_ms+(time.perf_counter()-started)*1000

        zero=same_gt_zero_overlap(bxa,gt)
        matched_lite=set(base.box_pairs(bxb,gt).values())
        matched_combined=set(base.box_pairs(bxc,gt).values())
        matched_original=set(base.box_pairs(bxa,gt).values())
        row=dict(split=split,image=index,cohort=cohort,
            small_detector_ms=round(small_det_ms,2),
            light_detector_ms=round(light_det_ms,2),
            zero_overlap_gt=len(zero),
            zero_recovered_lite=sum(j in matched_lite for j in zero),
            zero_recovered_combined=sum(j in matched_combined for j in zero),
            new_box_count=len(novel),
            newly_matched_gt_combined=len(matched_combined-matched_original))
        for mode,boxes,preds,ms in (("A",bxa,pra,ams),("B",bxb,prb,bms),("C",bxc,prc,cms)):
            vals=base.score_pair(boxes,preds,gt)
            for k,v in vals.items():row[mode+"_"+k]=v
            row[mode+"_ms"]=round(ms,2)
        allrows.append(row)
        if ix%8==0:
            print("LIGHT_DB_PROGRESS",ix,len(images),
                  "A",len(bxa),"B",len(bxb),"C_new",len(novel),
                  "zero_recovered",row["zero_recovered_lite"],flush=True)
    return allrows

def summary(rows,cohort,mode):
    sel=[x for x in rows if x["cohort"]==cohort]
    sm=lambda field:sum(x[mode+"_"+field] for x in sel)
    chars=sm("gt_chars");n=sm("gt")
    times=sorted(x[mode+"_ms"] for x in sel)
    return dict(cohort=cohort,mode=mode,photos=len(sel),
         gt_lines=n,matched_lines=sm("matched"),
         gt_line_recall=round(sm("matched")/max(1,n),5),
         missed_gt_chars=sm("missed_chars"),
         coverage_cer_proxy=round(sm("proxy_errors")/max(1,chars),5),
         unmatched_recognized_boxes=sm("unmatched_text_boxes"),
         total_boxes=sm("detected_boxes"),
         avg_frame_ms=round(sum(times)/max(1,len(times)),2),
         p95_frame_ms=round(times[int((len(times)-1)*.95)],2))

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--max-photos",type=int,default=0)
    a=p.parse_args();out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    small,light,rec,chars=init(a.models)
    sdk.SLICES={x:[(0,12),(500,12)] for x in ("ReCTS","RCTW","ART")}
    images=sdk.collect_images()
    if a.max_photos:images=images[:a.max_photos]
    if len(images)<(6 if a.max_photos else 65):raise RuntimeError("Too few real photos")
    rows=evaluate(images,small,light,rec,chars)
    if not a.max_photos and len(rows)<65:raise RuntimeError("Too few valid rows")
    summary_rows=[]
    for cohort in ("DEV","HOLDOUT"):
        if not any(x["cohort"]==cohort for x in rows):continue
        for mode in ("A","B","C"):
            x=summary(rows,cohort,mode)
            summary_rows.append(x)
            print("LIGHT_DB_SMALL_REC_RESULT",json.dumps(x),flush=True)
    recovery={}
    for cohort in ("DEV","HOLDOUT"):
        ss=[x for x in rows if x["cohort"]==cohort]
        recovery[cohort]=dict(
           photos=len(ss),zero_overlap_gt=sum(x["zero_overlap_gt"] for x in ss),
           zero_recovered_lite=sum(x["zero_recovered_lite"] for x in ss),
           zero_recovered_combined=sum(x["zero_recovered_combined"] for x in ss),
           new_fused_boxes=sum(x["new_box_count"] for x in ss),
           newly_matched_gt_combined=sum(x["newly_matched_gt_combined"] for x in ss),
           mean_light_det_ms=round(sum(x["light_detector_ms"] for x in ss)/max(1,len(ss)),2))
    print("LIGHT_DB_RECOVERY",json.dumps(recovery),flush=True)
    with (out/"per_photo.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
    (out/"summary.json").write_text(json.dumps(summary_rows,indent=2,ensure_ascii=False))
    (out/"recovery.json").write_text(json.dumps(recovery,indent=2,ensure_ascii=False))
    (out/"limitations.txt").write_text(
      "Research-only use of ChineseOCR Lite detection model (GPL-2.0 upstream). "
      "Check licensing before any Android or product integration. "
      "Same previously reused 72 public natural-scene photos, not fresh independent blind evaluation. "
      "Lite native-like DBNet thresholds .30/.50, unclip 2.0, 640px short side, "
      "compared with original Small native settings .20/.45/unclip1.4/max side 1600. "
      "This evaluates candidate full configurations, NOT isolated parameter effects. "
      "Identical PP-OCRv6 Small REC for all three modes, no Medium or CRAFT model. "
      "IoU>=0.4 GT annotation matching penalizes fragmented/multi-line boxes; "
      "coverage-CER proxy omits insertion errors from all unmatched predictions. "
      "Linux CPU timing does not equal Android, camera/TTS/thermal results. "
      "No changed app, no APK.\n")
    print("LIGHT_DB_A_B_C_COMPLETE",len(rows),flush=True)

if __name__=="__main__":main()
