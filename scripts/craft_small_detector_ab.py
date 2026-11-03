#!/usr/bin/env python3
"""Pinned CRAFT (character/affinity detector) versus existing PP-OCRv6 Small DET.
A = Small DET + Small REC; B = CRAFT DET + same Small REC;
C = union of Small DET and nonduplicate CRAFT DET + same Small REC.

Same 72 public annotated photos as previous tests. Predeclared thresholds, no
GT-gated box selection, no Medium REC, no APK / app code / release changes.
"""
import argparse, csv, hashlib, json, math, time
from pathlib import Path
from collections import Counter
import cv2
import numpy as np
import onnxruntime as ort
import yaml
import ocr_box_value_deep_dive as sdk

SMALL_DET_SHA="d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"
SMALL_REC_SHA="5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
SMALL_YML_SHA="ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
CRAFT_MODEL_SHA="28c05f8b5e97f3c5765d00bca556ec2397c86c954d4ac5ef73aa10043db16388"
CRAFT_LONG_SIDE=960
CRAFT_TEXT=.7
CRAFT_LINK=.4
CRAFT_LOW=.4
MAX_CRAFT=150
MAX_SMALL=150
MAX_TOTAL=180
MATCH_IOU=.40

def model(path,sha=None):
    path=Path(path);data=path.read_bytes()
    actual=hashlib.sha256(data).hexdigest()
    if sha is not None and actual!=sha:raise ValueError(f"MODEL SHA MISMATCH {path} {actual}")
    print("PINNED_MODEL_SHA256",path.name,actual,"size_bytes",len(data),flush=True)
    o=ort.SessionOptions();o.intra_op_num_threads=2;o.inter_op_num_threads=1
    return ort.InferenceSession(str(path),sess_options=o,providers=["CPUExecutionProvider"])

def get_models(folder):
    p=Path(folder)
    d=model(p/"det.onnx",SMALL_DET_SHA)
    r=model(p/"small_rec.onnx",SMALL_REC_SHA)
    c=model(p/"craft.onnx",CRAFT_MODEL_SHA)
    config=p/"small_rec.yml"
    assert hashlib.sha256(config.read_bytes()).hexdigest()==SMALL_YML_SHA
    chars=list(yaml.safe_load(config.read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    return d,r,c,chars

def gt_rows(texts,polys):
    result=[]
    for label,poly in zip(texts,polys):
        if not isinstance(label,str) or "###" in label:continue
        try:pts=np.asarray(poly,np.float32).reshape(-1,2)
        except (TypeError,ValueError):continue
        if len(pts)<4 or not np.isfinite(pts).all():continue
        bb=sdk.bbox_from_quad(pts)
        if bb[2]-bb[0]<6 or bb[3]-bb[1]<6:continue
        n=sdk.normalize(label)
        if not n:continue
        result.append(dict(normal=n,box=bb,text=label))
    return result

def box_pairs(boxes,gts):
    edges=[]
    for i,b in enumerate(boxes):
        for j,g in enumerate(gts):
            val=sdk.box_iou(b["bbox"],g["box"])
            if val>=MATCH_IOU:edges.append((val,i,j))
    edges.sort(reverse=True)
    seen_i=set();seen_j=set();pairs={}
    for val,i,j in edges:
        if i in seen_i or j in seen_j:continue
        seen_i.add(i);seen_j.add(j);pairs[i]=j
    return pairs

def overlap(a,b):
    return max(0,min(a[2],b[2])-max(a[0],b[0]))*max(0,min(a[3],b[3])-max(a[1],b[1]))

def area(a):
    return max(1.,(a[2]-a[0])*(a[3]-a[1]))

def dup(b,others):
    bb=b["bbox"]
    for obj in others:
        prior=obj["bbox"]
        ov=overlap(bb,prior)
        if sdk.box_iou(bb,prior)>=.50 or ov/min(area(bb),area(prior))>=.70:
            return True
    return False

def small_detect(session,img):
    st=time.perf_counter()
    x=sdk.det_preprocess(img)
    prob=session.run(None,{session.get_inputs()[0].name:x})[0]
    boxes=[b for b in sdk.detections(prob.reshape(prob.shape[-2:]),img.shape[0],img.shape[1])
           if b["score"]>=.45]
    return boxes[:MAX_SMALL],(time.perf_counter()-st)*1000

def craft_preprocess(img):
    """RGB, aspect-preserving resize to multiples of 32; explicit model-card contract."""
    h,w=img.shape[:2]
    ratio=min(1.,CRAFT_LONG_SIDE/max(h,w))
    rh=max(32,int(math.ceil(h*ratio/32))*32)
    rw=max(32,int(math.ceil(w*ratio/32))*32)
    rh=min(CRAFT_LONG_SIDE,rh);rw=min(CRAFT_LONG_SIDE,rw)
    resized=cv2.resize(img,(rw,rh),interpolation=cv2.INTER_LINEAR).astype(np.float32)/255.
    mean=np.asarray([.485,.456,.406],dtype=np.float32)
    std=np.asarray([.229,.224,.225],dtype=np.float32)
    inp=np.ascontiguousarray(((resized-mean)/std).transpose(2,0,1)[None])
    return inp,(w/rw,h/rh)

def craft_regions(score,source_hw,resize_scale):
    """Follow ClovaAI CRAFT connected-components + component dilation + minAreaRect."""
    h,w=source_hw;wx,hy=resize_scale
    score=np.asarray(score)
    assert score.ndim==3 and score.shape[-1]==2,score.shape
    region=score[:,:,0];affinity=score[:,:,1]
    assert np.isfinite(region).all() and np.isfinite(affinity).all()
    # CRAFT ONNX score maps are sigmoid probabilities.
    assert region.min()>=-.001 and region.max()<=1.001,(region.min(),region.max())
    text_binary=(region>CRAFT_LOW).astype(np.uint8)
    link_binary=(affinity>CRAFT_LINK).astype(np.uint8)
    binary=np.clip(text_binary+link_binary,0,1).astype(np.uint8)
    n,labels,stats,_=cv2.connectedComponentsWithStats(binary,connectivity=4)
    boxes=[]
    sh,sw=region.shape
    for k in range(1,n):
        area_cc=int(stats[k,cv2.CC_STAT_AREA])
        if area_cc<10:continue
        xx=int(stats[k,cv2.CC_STAT_LEFT]);yy=int(stats[k,cv2.CC_STAT_TOP])
        ww=int(stats[k,cv2.CC_STAT_WIDTH]);hh=int(stats[k,cv2.CC_STAT_HEIGHT])
        label_crop=labels[yy:yy+hh,xx:xx+ww]
        mask=label_crop==k
        scores=region[yy:yy+hh,xx:xx+ww][mask]
        if len(scores)==0 or scores.max()<CRAFT_TEXT:continue
        dilation=int(math.sqrt(area_cc*min(ww,hh)/max(1,ww*hh))*2)
        sx=max(0,xx-dilation);sy=max(0,yy-dilation)
        ex=min(sw,xx+ww+dilation+1);ey=min(sh,yy+hh+dilation+1)
        segmask=(labels[sy:ey,sx:ex]==k).astype(np.uint8)*255
        # Remove only links that are not also foreground text regions.
        link_only=(link_binary[sy:ey,sx:ex]==1)&(text_binary[sy:ey,sx:ex]==0)
        segmask[link_only]=0
        kernel=cv2.getStructuringElement(cv2.MORPH_RECT,(1+dilation,1+dilation))
        segmask=cv2.dilate(segmask,kernel)
        ys,xs=np.nonzero(segmask)
        if len(xs)<6:continue
        pts=np.column_stack((xs+sx,ys+sy)).astype(np.float32)
        quad=sdk.ordered_quad(cv2.boxPoints(cv2.minAreaRect(pts)))
        quad[:,0]=np.clip(quad[:,0]*2*wx,0,w)
        quad[:,1]=np.clip(quad[:,1]*2*hy,0,h)
        bb=sdk.bbox_from_quad(quad)
        if bb[2]-bb[0]<6 or bb[3]-bb[1]<6:continue
        boxes.append(dict(quad=quad,bbox=bb,score=float(scores.max()),engine="CRAFT"))
    boxes.sort(key=lambda x:x["score"],reverse=True)
    return boxes[:MAX_CRAFT],{"raw_components":n-1,"kept_components":len(boxes)}

def craft_detect(session,img):
    start=time.perf_counter()
    x,ratio=craft_preprocess(img)
    names=[v.name for v in session.get_outputs()]
    raw=session.run(None,{session.get_inputs()[0].name:x})
    scores=np.asarray(raw[0])
    if scores.ndim!=4 or scores.shape[0]!=1 or scores.shape[-1]!=2:
        raise ValueError(f"Incorrect CRAFT scoremap contract: names={names}, output={scores.shape}")
    boxes,diag=craft_regions(scores[0],img.shape[:2],ratio)
    return boxes,(time.perf_counter()-start)*1000,diag

def rec_predictions(session,chars,img,boxes):
    outputs={}
    for i,b in enumerate(boxes):
        crop=sdk.crop_box(img,b)
        if crop is None:continue
        # Use one identical REC preprocessing for all three configurations.
        bgr=cv2.cvtColor(crop,cv2.COLOR_RGB2BGR)
        pred,conf,ms=sdk.rec(session,session.get_inputs()[0].name,chars,bgr)
        outputs[i]=pred
    return outputs

def score_pair(boxes,preds,gt):
    pairs=box_pairs(boxes,gt)
    seen=set(pairs.values())
    total=sum(len(g["normal"]) for g in gt)
    missing=sum(len(g["normal"]) for j,g in enumerate(gt) if j not in seen)
    matched_edit=0
    for i,j in pairs.items():
        matched_edit+=sdk.distance(gt[j]["normal"],sdk.normalize(preds.get(i,"")))
    nonmatched=sum(bool(preds.get(i,"").strip()) for i in range(len(boxes)) if i not in pairs)
    return dict(matched=len(seen),gt=len(gt),gt_chars=total,
       missed_chars=missing,rec_edits=matched_edit,
       proxy_errors=missing+matched_edit,unmatched_text_boxes=nonmatched,
       detected_boxes=len(boxes))

def summarise(rows,cohort,mode):
    a=[x for x in rows if x["cohort"]==cohort]
    sumfield=lambda key:sum(x[mode+"_"+key] for x in a)
    gt=sumfield("gt"); chars=sumfield("gt_chars")
    return dict(cohort=cohort,mode=mode,photos=len(a),
        annotated_lines=gt,matched_lines=sumfield("matched"),
        iou_040_recall=round(sumfield("matched")/max(1,gt),5),
        missed_gt_chars=sumfield("missed_chars"),
        matched_edit_distance=sumfield("rec_edits"),
        coverage_CER_proxy=round(sumfield("proxy_errors")/max(1,chars),5),
        unmatched_recognized_boxes=sumfield("unmatched_text_boxes"),
        total_boxes=sumfield("detected_boxes"),
        mean_frame_ms=round(sumfield("ms")/len(a),2),
        p95_frame_ms=round(sorted(x[mode+"_ms"] for x in a)[int((len(a)-1)*.95)],2))

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--max-photos",type=int,default=0)
    a=p.parse_args();out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    small,rec,craft,chars=get_models(a.models)
    sdk.SLICES={k:[(0,12),(500,12)] for k in ("ReCTS","RCTW","ART")}
    sdk.THRESHOLDS=[.45];sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.40
    photos=sdk.collect_images()
    if a.max_photos:photos=photos[:a.max_photos]
    if len(photos)< (6 if a.max_photos else 65):raise RuntimeError(f"insufficient real photos {len(photos)}")
    output=[];meta={"craft_raw_components":0,"craft_kept_components":0,
                    "craft_novel_boxes":0,"novel_matched_gt":0,
                    "baseline_zero_overlap_gt":0,"zero_overlap_recovered_by_craft_match":0,
                    "zero_overlap_any_craft_intersection":0}
    for idx,(split,photo_idx,rgb,texts,polys) in enumerate(photos,1):
        cohort="DEV" if photo_idx<100 else "HOLDOUT"
        gt=gt_rows(texts,polys)
        if not gt:continue
        started=time.perf_counter()
        boxes_a,dt_a=small_detect(small,rgb)
        preds_a=rec_predictions(rec,chars,rgb,boxes_a)
        ms_a=(time.perf_counter()-started)*1000
        started=time.perf_counter()
        boxes_b,dt_b,diag=craft_detect(craft,rgb)
        preds_b=rec_predictions(rec,chars,rgb,boxes_b)
        ms_b=(time.perf_counter()-started)*1000
        started=time.perf_counter()
        new_boxes=[]
        for b in boxes_b:
            if len(boxes_a)+len(new_boxes)>=MAX_TOTAL:break
            if not dup(b,boxes_a+new_boxes):new_boxes.append(b)
        boxes_c=boxes_a+new_boxes
        # Reuse Small REC outputs only for same original boxes; no GT feedback.
        preds_c=dict(preds_a)
        for j,pred in rec_predictions(rec,chars,rgb,new_boxes).items():
            preds_c[len(boxes_a)+j]=pred
        ms_c=ms_a+dt_b+(time.perf_counter()-started)*1000
        stats=[score_pair(boxes_a,preds_a,gt),score_pair(boxes_b,preds_b,gt),
               score_pair(boxes_c,preds_c,gt)]
        pairs_a=box_pairs(boxes_a,gt);pairs_b=box_pairs(boxes_b,gt);pairs_c=box_pairs(boxes_c,gt)
        miss_a=set(range(len(gt)))-set(pairs_a.values())
        covered_a=set(pairs_a.values())
        extra_matches=set(pairs_c.values())-covered_a
        zero_gt=[j for j in miss_a if not any(overlap(g["bbox"],gt[j]["box"])>0 for g in boxes_a)]
        meta["baseline_zero_overlap_gt"]+=len(zero_gt)
        meta["zero_overlap_recovered_by_craft_match"]+=sum(j in pairs_b.values() for j in zero_gt)
        meta["zero_overlap_any_craft_intersection"]+=sum(any(overlap(b["bbox"],gt[j]["box"])>0 for b in boxes_b) for j in zero_gt)
        meta["novel_matched_gt"]+=len(extra_matches)
        meta["craft_raw_components"]+=diag["raw_components"]
        meta["craft_kept_components"]+=diag["kept_components"]
        meta["craft_novel_boxes"]+=len(new_boxes)
        row=dict(cohort=cohort,split=split,image=photo_idx,
            C_new_boxes=len(new_boxes),C_new_gt_matches=len(extra_matches),
            zero_baseline=len(zero_gt),zero_matched_craft=sum(j in pairs_b.values() for j in zero_gt),
            craft_model_ms=round(dt_b,2),small_det_ms=round(dt_a,2),
            craft_component_count=diag["raw_components"])
        for mode,s,t in zip(("A","B","C"),stats,(ms_a,ms_b,ms_c)):
            for k,v in s.items():row[mode+"_"+k]=v
            row[mode+"_ms"]=round(t,2)
        output.append(row)
        print("CRAFT_OCR_PROGRESS",idx,"/",len(photos),"cohort",cohort,
              "Small",len(boxes_a),"CRAFT",len(boxes_b),"new",len(new_boxes),
              "novel_gt",len(extra_matches),flush=True)
    if not a.max_photos:
        assert len(output)>=65
    for cohort in ("DEV","HOLDOUT"):
        for mode in ("A","B","C"):
            summary=summarise(output,cohort,mode)
            print("CRAFT_SMALL_DET_REC_AB_RESULT",json.dumps(summary),flush=True)
    all_summary=[summarise(output,cohort,mode) for cohort in ("DEV","HOLDOUT")
                 for mode in ("A","B","C")]
    with (out/"per_photo.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(output[0]));w.writeheader();w.writerows(output)
    (out/"summary.json").write_text(json.dumps(all_summary,ensure_ascii=False,indent=2))
    (out/"recovery.json").write_text(json.dumps(meta,ensure_ascii=False,indent=2))
    (out/"limitations.txt").write_text(
      "Same 72 public photos previously used for earlier optimization research, not novel untouched blind cohort. "
      "CRAFT MLT25K ONNX trained on multilingual scenes, not specifically validated for dense Chinese. "
      "Explicit fixed CRAFT 960 max-side, 0.7/0.4/0.4 thresholds and connected-component minRect postprocessing. "
      "No ground truth used to choose boxes; GT only evaluates matched IoU>=0.4. "
      "Real GT text may occur at a different grouping granularity than CRAFT word boxes. "
      "AABB IoU matching can penalize correct split boxes and label coverage. "
      "Coverage-CER is a proxy that counts missed GT chars but does not count "
      "all unmatched predictions as extra insertions. "
      "Per-frame CPU includes Python rec inference, not camera/TTS/Android execution. "
      "No Medium model, no app modifications, no APK.\n")
    print("CRAFT_SMALL_DET_REC_EXPERIMENT_COMPLETE",len(output),flush=True)

if __name__=="__main__":main()
