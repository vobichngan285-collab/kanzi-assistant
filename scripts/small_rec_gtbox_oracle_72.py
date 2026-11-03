#!/usr/bin/env python3
"""Small REC exact-GT-box oracle on the SAME 72 photos as the visual A/B.
Oracle crops are diagnostic ONLY, not used for live detection or model selection.
"""
import argparse, hashlib, json, math, time
from pathlib import Path
import cv2, numpy as np, onnxruntime as ort, yaml
import ocr_box_value_deep_dive as sdk

REC_SHA="5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
YML_SHA="ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"

def main():
    p=argparse.ArgumentParser();p.add_argument("--models",required=True);p.add_argument("--out",required=True)
    a=p.parse_args();root=Path(a.models);out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    onnx=root/"small_rec.onnx";yml=root/"small_rec.yml"
    assert hashlib.sha256(onnx.read_bytes()).hexdigest()==REC_SHA
    assert hashlib.sha256(yml.read_bytes()).hexdigest()==YML_SHA
    opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
    sess=ort.InferenceSession(str(onnx),opts,providers=["CPUExecutionProvider"])
    chars=list(yaml.safe_load(yml.read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    sdk.SLICES={split:[(0,12),(500,12)] for split in ("ReCTS","RCTW","ART")}
    imgs=sdk.collect_images()
    if len(imgs)<60:raise ValueError(f"insufficient images: {len(imgs)}")
    results={"DEV":dict(photos=0,gt_lines=0,gt_chars=0,edit_distance=0,
                        exact_lines=0,failed_crops=0,rec_ms=[]),
             "HOLDOUT":dict(photos=0,gt_lines=0,gt_chars=0,edit_distance=0,
                            exact_lines=0,failed_crops=0,rec_ms=[])}
    for count,(split,idx,rgb,texts,polys) in enumerate(imgs,1):
        cohort="DEV" if idx<100 else "HOLDOUT"
        r=results[cohort];r["photos"]+=1
        for label,vertices in zip(texts,polys):
            if not isinstance(label,str) or "###" in label:continue
            try:xy=np.asarray(vertices,np.float32).reshape(-1,2)
            except (ValueError,TypeError):continue
            if len(xy)<4 or not np.isfinite(xy).all():continue
            bb=sdk.bbox_from_quad(xy)
            if bb[2]-bb[0]<6 or bb[3]-bb[1]<6:continue
            truth=sdk.normalize(label)
            if not truth:continue
            r["gt_lines"]+=1;r["gt_chars"]+=len(truth)
            crop=sdk.crop_box(rgb,{"quad":xy})
            if crop is None:
                r["failed_crops"]+=1
                r["edit_distance"]+=len(truth)
                continue
            # App's inference YAML is BGR; no Medium recognizer, no GT-driven selection.
            bgr=cv2.cvtColor(crop,cv2.COLOR_RGB2BGR)
            pred,confidence,ms=sdk.rec(sess,sess.get_inputs()[0].name,chars,bgr)
            edits=sdk.distance(truth,sdk.normalize(pred))
            r["edit_distance"]+=edits
            r["exact_lines"]+=int(edits==0)
            r["rec_ms"].append(ms)
        if count%12==0:
            print("ORACLE_SMALL_REC_PROGRESS",count,len(imgs),flush=True)
    summary=[]
    for cohort,r in results.items():
        v=dict(cohort=cohort,photos=r["photos"],gt_lines=r["gt_lines"],
            gt_chars=r["gt_chars"],edits=r["edit_distance"],
            oracle_gt_crop_CER=round(r["edit_distance"]/max(1,r["gt_chars"]),5),
            exact_line_rate=round(r["exact_lines"]/max(1,r["gt_lines"]),5),
            failed_crops=r["failed_crops"],
            mean_rec_infer_ms=round(sum(r["rec_ms"])/max(1,len(r["rec_ms"])),3))
        print("SMALL_REC_GT_BOX_ORACLE",json.dumps(v),flush=True)
        summary.append(v)
    (out/"summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2))
    (out/"limits.txt").write_text(
        "This is a GT box oracle, deliberately impossible in production without annotations. "
        "Use as an approximate recognition-only ceiling for the SAME DEV/HOLDOUT 72 public images. "
        "Strict CER on ALL eligible annotated GT crops, with the same BGR Small REC. "
        "Previous full-page baseline measures detection matching and lost GT separately. "
        "Differences between metrics are NOT a causal estimate of model impact because "
        "original DET boxes can cover partial lines and GT may be incomplete. "
        "No layout-guided OCR, no Medium, no Android device or APK.\n")
    print("SMALL_ORACLE_SAME_PHOTOS_COMPLETE",sum(r["gt_lines"] for r in results.values()),flush=True)

if __name__=="__main__":main()
