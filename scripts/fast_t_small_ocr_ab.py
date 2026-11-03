#!/usr/bin/env python3
"""FAST-T MSRA-TD500 text detection vs the deployed Small DET and optional union.

Same 72 public GT-labeled natural scene images, exactly one existing Small REC.
FAST head postprocessing follows the official CPU algorithm:
sigmoid + pooled score, connected-components on raw positive kernels,
nearest resize/maxpool of component IDs and minAreaRect with official alpha.
Original official weights exported in separate verified ONNX export step.
"""
import argparse,csv,hashlib,json,math,time
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort,torch
import torch.nn.functional as F
import yaml
import ocr_box_value_deep_dive as sdk
import craft_small_detector_ab as base

MIN_SCORE=.88
MIN_AREA=200
POOL_SIZE=7
FAST_SHORT_SIDE=512
FAST_LONG_CAP=1024
MAX_BOXES=150
MAX_FUSED=180

def checked(path,sha=None):
    path=Path(path)
    if sha and hashlib.sha256(path.read_bytes()).hexdigest()!=sha:
        raise ValueError("Model hash mismatch: "+str(path))
    opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
    return ort.InferenceSession(str(path),sess_options=opts,providers=["CPUExecutionProvider"])

def models(root):
    root=Path(root)
    det=checked(root/"small_det.onnx",base.SMALL_DET_SHA)
    rec=checked(root/"small_rec.onnx",base.SMALL_REC_SHA)
    fast=checked(root/"fast_t_msra512.onnx")
    yamlfile=root/"small_rec.yml"
    if hashlib.sha256(yamlfile.read_bytes()).hexdigest()!=base.SMALL_YML_SHA:raise ValueError("Invalid REC dictionary")
    chars=list(yaml.safe_load(yamlfile.read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    print("FAST_ONNX_INFO",[(i.name,i.shape) for i in fast.get_inputs()],
          [(o.name,o.shape) for o in fast.get_outputs()],flush=True)
    print("FAST_ONNX_CHECKSUM",hashlib.sha256((root/"fast_t_msra512.onnx").read_bytes()).hexdigest(),flush=True)
    return det,rec,fast,chars

def fast_input(rgb):
    h,w=rgb.shape[:2]
    ratio=min(FAST_SHORT_SIDE/min(h,w),FAST_LONG_CAP/max(h,w))
    nh=max(32,int(math.ceil(int(h*ratio+.5)/32))*32)
    nw=max(32,int(math.ceil(int(w*ratio+.5)/32))*32)
    # Avoid exceeding max by one rounding step.
    if max(nh,nw)>FAST_LONG_CAP:
        scale=FAST_LONG_CAP/max(nh,nw)
        nh=max(32,int(nh*scale//32)*32)
        nw=max(32,int(nw*scale//32)*32)
    resized=cv2.resize(rgb,(nw,nh),interpolation=cv2.INTER_LINEAR)
    t=resized.astype(np.float32)/255.
    mean=np.array([.485,.456,.406],np.float32);std=np.array([.229,.224,.225],np.float32)
    return np.ascontiguousarray(((t-mean)/std).transpose(2,0,1)[None]),(nw,nh)

def fast_postprocess(raw,original_hw,input_wh):
    h,w=original_hw;nw,nh=input_wh
    if raw.shape!=(1,5,nh//4,nw//4):
        raise RuntimeError(f"Incorrect raw FAST shape: {raw.shape} for input {(nh,nw)}")
    # Official FASTHead.get_results uses scale=2, max pooling with k=4,pad=1,
    # nearest upsampling of text probabilities and component identifiers.
    kernel_map=np.asarray(raw[0,0],np.float32)
    if not np.isfinite(kernel_map).all():raise ValueError("Invalid raw FAST logits")
    mask=(kernel_map>0).astype(np.uint8)
    components,labels=cv2.connectedComponents(mask,connectivity=8)
    with torch.no_grad():
        logits=torch.from_numpy(kernel_map)[None,None]
        half_logits=F.interpolate(logits,size=(nh//2,nw//2),mode="nearest")
        kernel_size=POOL_SIZE//2+1
        pad=(POOL_SIZE//2)//2
        pooled=F.max_pool2d(half_logits,kernel_size=kernel_size,stride=1,padding=pad)
        scores=F.interpolate(torch.sigmoid(pooled),size=(nh,nw),mode="nearest")[0,0].numpy()
        tensor=torch.from_numpy(labels.astype(np.float32))[None,None]
        tensor=F.interpolate(tensor,size=(nh//2,nw//2),mode="nearest")
        tensor=F.max_pool2d(tensor,kernel_size=kernel_size,stride=1,padding=pad)
        labelmap=F.interpolate(tensor,size=(nh,nw),mode="nearest")[0,0].numpy().astype(np.int32)
    boxes=[]
    for key in range(1,components):
        ys,xs=np.where(labelmap==key)
        if len(xs)<MIN_AREA:continue
        score=float(scores[ys,xs].mean())
        if score<MIN_SCORE:continue
        pts=np.column_stack((xs,ys)).astype(np.float32)
        rect=cv2.minAreaRect(pts)
        area=max(1.,rect[1][0]*rect[1][1])
        alpha=math.sqrt(math.sqrt(len(pts)/area))
        newrect=(rect[0],(rect[1][0]*alpha,rect[1][1]*alpha),rect[2])
        quad=sdk.ordered_quad(cv2.boxPoints(newrect).astype(np.float32))
        quad[:,0]=np.clip(quad[:,0]*(w/nw),0,w)
        quad[:,1]=np.clip(quad[:,1]*(h/nh),0,h)
        bbox=sdk.bbox_from_quad(quad)
        if bbox[2]-bbox[0]<6 or bbox[3]-bbox[1]<6:continue
        boxes.append(dict(quad=quad,bbox=bbox,score=score,engine="FAST-T"))
    boxes.sort(key=lambda x:x["score"],reverse=True)
    return boxes[:MAX_BOXES],components-1

def fast_det(session,rgb):
    st=time.perf_counter()
    x,size=fast_input(rgb)
    out=session.run(None,{session.get_inputs()[0].name:x})[0]
    infer_ms=(time.perf_counter()-st)*1000
    boxes,ncomponents=fast_postprocess(out,rgb.shape[:2],size)
    end_ms=(time.perf_counter()-st)*1000
    return boxes,end_ms,infer_ms,ncomponents

def zero_overlap(gt,boxes):
    matched=set(base.box_pairs(boxes,gt).values())
    return [j for j in range(len(gt))
      if j not in matched and not any(base.overlap(gt[j]["box"],b["bbox"])>0 for b in boxes)]

def run_photos(images,det,rec,fast,chars):
    data=[]
    for photo,(split,idx,rgb,texts,polys) in enumerate(images,1):
        gt=base.gt_rows(texts,polys)
        if not gt:continue
        cohort="DEV" if idx<100 else "HOLDOUT"
        start=time.perf_counter()
        sdk.DET_BITMAP_THRESH=.20;sdk.DET_UNCLIP=1.4;sdk.THRESHOLDS=[.45]
        a,sm_det_ms=base.small_detect(det,rgb)
        pa=base.rec_predictions(rec,chars,rgb,a)
        ma=(time.perf_counter()-start)*1000
        start=time.perf_counter()
        b,fast_det_ms,fast_infer_ms,ncomponents=fast_det(fast,rgb)
        pb=base.rec_predictions(rec,chars,rgb,b)
        mb=(time.perf_counter()-start)*1000
        start=time.perf_counter()
        extra=[]
        for item in b:
            if len(a)+len(extra)>=MAX_FUSED:break
            if not base.dup(item,a+extra):extra.append(item)
        c=a+extra
        pc=dict(pa)
        for k,v in base.rec_predictions(rec,chars,rgb,extra).items():
            pc[k+len(a)]=v
        mc=ma+fast_det_ms+(time.perf_counter()-start)*1000
        matched_a=set(base.box_pairs(a,gt).values())
        matched_b=set(base.box_pairs(b,gt).values())
        matched_c=set(base.box_pairs(c,gt).values())
        zero=zero_overlap(gt,a)
        row=dict(split=split,image=idx,cohort=cohort,
          small_det_ms=round(sm_det_ms,2),fast_det_ms=round(fast_det_ms,2),
          fast_infer_ms=round(fast_infer_ms,2),fast_components=ncomponents,
          zero_gt=len(zero),zero_recovered_by_fast=sum(j in matched_b for j in zero),
          zero_recovered_by_fusion=sum(j in matched_c for j in zero),
          extra_boxes=len(extra),additional_gt_match=len(matched_c-matched_a),
          fast_vs_small_new_matched_gt=len(matched_b-matched_a))
        for mode,boxes,pred,ms in (("A",a,pa,ma),("B",b,pb,mb),("C",c,pc,mc)):
            stats=base.score_pair(boxes,pred,gt)
            for key,val in stats.items():row[f"{mode}_{key}"]=val
            row[f"{mode}_ms"]=round(ms,2)
        data.append(row)
        if photo%6==0:
            print("FAST_T_PROGRESS",photo,len(images),"cohort",cohort,
              "Small",len(a),"FAST",len(b),"novel",len(extra),
              "new_GT",len(matched_b-matched_a),flush=True)
    return data

def aggregate(rows,cohort,mode):
    d=[x for x in rows if x["cohort"]==cohort]
    if not d:return None
    s=lambda name:sum(x[f"{mode}_{name}"] for x in d)
    gt=s("gt");chars=s("gt_chars")
    times=sorted(x[f"{mode}_ms"] for x in d)
    return dict(cohort=cohort,mode=mode,photos=len(d),gt_lines=gt,
        matched_gt_lines=s("matched"),gt_line_recall=round(s("matched")/max(1,gt),5),
        missed_gt_chars=s("missed_chars"),
        matched_char_edits=s("rec_edits"),
        coverage_cer_proxy=round(s("proxy_errors")/max(1,chars),5),
        unpaired_nonempty_rec_boxes=s("unmatched_text_boxes"),
        detected_boxes=s("detected_boxes"),
        mean_frame_ms=round(sum(times)/len(times),2),
        p95_frame_ms=round(times[int((len(times)-1)*.95)],2))

def main():
    torch.set_num_threads(2)
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--max-photos",type=int,default=0)
    a=p.parse_args();o=Path(a.out);o.mkdir(exist_ok=True,parents=True)
    det,rec,fast,chars=models(a.models)
    sdk.SLICES={x:[(0,12),(500,12)] for x in ("ReCTS","RCTW","ART")}
    images=sdk.collect_images()
    if a.max_photos:images=images[:a.max_photos]
    if len(images)<(6 if a.max_photos else 65):raise RuntimeError("Not enough real photos")
    rows=run_photos(images,det,rec,fast,chars)
    if not a.max_photos and len(rows)<65:raise RuntimeError("Insufficient scored photos")
    reports=[]
    for cohort in ("DEV","HOLDOUT"):
        for mode in ("A","B","C"):
            recd=aggregate(rows,cohort,mode)
            if recd:
                reports.append(recd)
                print("FAST_T_OCR_RESULT",json.dumps(recd),flush=True)
    recovery={}
    for cohort in ("DEV","HOLDOUT"):
        rr=[r for r in rows if r["cohort"]==cohort]
        if not rr:continue
        recovery[cohort]=dict(
          zero_overlap_gt=sum(x["zero_gt"] for x in rr),
          zero_recovered_fast=sum(x["zero_recovered_by_fast"] for x in rr),
          zero_recovered_fusion=sum(x["zero_recovered_by_fusion"] for x in rr),
          new_gt_lines_fast_vs_small=sum(x["fast_vs_small_new_matched_gt"] for x in rr),
          new_gt_lines_fused=sum(x["additional_gt_match"] for x in rr),
          mean_fast_model_infer_ms=round(sum(x["fast_infer_ms"] for x in rr)/len(rr),2),
          mean_fast_total_detect_ms=round(sum(x["fast_det_ms"] for x in rr)/len(rr),2))
    print("FAST_T_RECOVERY",json.dumps(recovery),flush=True)
    (o/"summary.json").write_text(json.dumps(reports,ensure_ascii=False,indent=2))
    (o/"recovery.json").write_text(json.dumps(recovery,ensure_ascii=False,indent=2))
    with (o/"per_photo.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
    (o/"limitations.txt").write_text(
      "FAST-T official MSRA512 pretrained checkpoint reparameterized and converted to ONNX, "
      "parity checked on CPU. FAST postprocess emulates get_results at score>=0.88 and area>=200. "
      "Inference resizes short edge up to 512, caps long edge at 1024 for CPU budget; "
      "this differs from uncapped upstream MSRA evaluation. "
      "All candidate boxes and fusion thresholds are fixed in advance, GT only scores them. "
      "72 public real photos were used in prior studies, not fresh untouched blind set. "
      "AABB GT pairing at IoU>=.4 is an imperfect line-level geometry proxy. "
      "Coverage CER proxy counts missed GT characters as deletions but not every "
      "unmatched recognized phrase as insertion, so is not end-to-end page CER. "
      "CPU time is GitHub Actions not Android, no TTS or camera. "
      "No Medium recognition, Android code modifications or APK generation.\n")
    print("FAST_T_REALPHOTO_EXPERIMENT_COMPLETE",len(rows),flush=True)

if __name__=="__main__":main()
