#!/usr/bin/env python3
"""#315 exact-model, Android-parameter, approximate real-scene DET+REC AB.

Purpose: are NEW text boxes from lowering PP-OCRv6-Small's 0.45 box
confidence actually valuable, or do they add unreadable / misleading speech?

Ground truth: Yesianrohn/OCR-Data, ReCTS/RCTW/ART annotated photos.
Evaluated photo list is fixed before any results; no private user photos.

Model hashes are checked; preprocess reproduces deployed det 1600/max, BGR,
0.20 bitmap mask, 0.45 box confidence, unclip 1.40, no dilation, 2 CPU threads.
Recognition crop emulates Android minAreaRect+warpPerspective, then **RGB**
48px normalization (SDK RecPreprocessor converts BGR crop to RGB), CTC decode.
Previous research's recognition BGR ordering was NOT a faithful SDK replica.

Caveats: pyclipper's rounded polygon offset and Python CV2, GT AABB IoU are
approximations, not literal Android Java implementation. A box unmatched to
annotation is UNKNOWN, not automatically a false positive. The algorithm
cannot establish semantics or whether a real user would want to hear it.

Only cloud CPU, not Android UVC FPS/speech latency or device energy cost.
"""
import argparse,collections,csv,hashlib,json,math,re,statistics,time,unicodedata
from pathlib import Path
import cv2
import numpy as np
import onnxruntime as ort
import pyclipper
import yaml

from ocr_public_real_scene_ab import get_json,read_img

DATASET = "Yesianrohn/OCR-Data"
SLICES = {
    "ReCTS": [(0,24),(101,24),(310,24)],
    "RCTW": [(0,24),(135,24),(315,24)],
    "ART": [(0,24),(125,24),(295,24)],
}
DET_SHA = "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"
REC_SHA = "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
REC_CONFIG_SHA = "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"

# Deployment's actual PP-OCRv6-Small values, from PaddleOcrEngine.kt.
DET_LONG_SIDE = 1600
DET_BITMAP_THRESH = 0.20
DET_UNCLIP = 1.40
DET_MAX_CANDIDATES = 3000
DET_USE_DILATION = False
BASE_BOX_THRESH = 0.45
THRESHOLDS = [0.45, 0.40, 0.35, 0.30]
EVAL_IOU = 0.40
REC_MIN_SCORE = 0.0
MAX_RECOGNITION_CROPS_PER_PHOTO = 160
MAX_RECOGNITION_WIDTH = 3200

def sha(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for b in iter(lambda:f.read(1<<20),b""):h.update(b)
    return h.hexdigest()

def normalize(s):
    return "".join(c for c in unicodedata.normalize("NFKC",str(s)).lower()
                   if not c.isspace())

def distance(a,b):
    last=list(range(len(b)+1))
    for i,ch in enumerate(a,1):
        now=[i]
        for j,other in enumerate(b,1):
            now.append(min(last[j]+1,now[-1]+1,last[j-1]+(ch!=other)))
        last=now
    return last[-1]

def approx_ocr_text(s):
    # A readability proxy ONLY; not a true/false ground-truth signal.
    x=normalize(s)
    if not x:return "empty"
    if len(x)==1:return "single_char"
    if len(x)>=2 and any(c.isalpha() or c.isdigit() for c in x):
        return "nonempty_alnum"
    if len(x)>=2 and any("\u3400"<=c<="\u9fff" for c in x):
        return "nonempty_cjk"
    return "punctuation_or_symbols"

def det_preprocess(rgb):
    h,w=rgb.shape[:2]
    ratio=min(1.0,1600.0/max(h,w))
    # SDK: newH=(h*ratio).toInt; roundHalfToEven(newH/32.0)*32
    nh=max(32,round(int(h*ratio)/32.0)*32)
    nw=max(32,round(int(w*ratio)/32.0)*32)
    # SDK always has detMaxSideLimit=1600 even after quantization.
    if max(nh,nw)>1600:
        ratio2=1600/max(nh,nw)
        nh=max(32,round(int(nh*ratio2)/32.0)*32)
        nw=max(32,round(int(nw*ratio2)/32.0)*32)
    image=cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR)
    image=cv2.resize(image,(nw,nh),interpolation=cv2.INTER_LINEAR).astype(np.float32)
    mean=np.array([.485,.456,.406],dtype=np.float32)
    std=np.array([.229,.224,.225],dtype=np.float32)
    tensor=np.ascontiguousarray(((image/255.-mean)/std).transpose(2,0,1)[None])
    return tensor

def ordered_quad(quad):
    pts=quad[np.argsort(quad[:,0],kind="stable")]
    a,b,c,d=pts
    tl,bl=(a,b) if a[1]<=b[1] else (b,a)
    tr,br=(c,d) if c[1]<=d[1] else (d,c)
    return np.float32([tl,tr,br,bl])

def bbox_from_quad(points):
    return (float(points[:,0].min()),float(points[:,1].min()),
            float(points[:,0].max()),float(points[:,1].max()))

def box_iou(a,b):
    x1=max(a[0],b[0]);y1=max(a[1],b[1])
    x2=min(a[2],b[2]);y2=min(a[3],b[3])
    inter=max(0,x2-x1)*max(0,y2-y1)
    a1=max(0,a[2]-a[0])*max(0,a[3]-a[1])
    b1=max(0,b[2]-b[0])*max(0,b[3]-b[1])
    return inter/max(1e-9,a1+b1-inter)

def score_box(prob,pts):
    h,w=prob.shape
    x0=max(0,min(w-1,int(math.floor(pts[:,0].min()))))
    x1=max(0,min(w-1,int(math.ceil(pts[:,0].max()))))
    y0=max(0,min(h-1,int(math.floor(pts[:,1].min()))))
    y1=max(0,min(h-1,int(math.ceil(pts[:,1].max()))))
    if x1<x0 or y1<y0:return 0.
    mask=np.zeros((y1-y0+1,x1-x0+1),np.uint8)
    cv2.fillPoly(mask,[np.floor(pts-np.array([x0,y0])).astype(np.int32)],1)
    return float(cv2.mean(prob[y0:y1+1,x0:x1+1],mask)[0])

def detections(prob,h,w):
    ph,pw=prob.shape
    binmask=(prob>DET_BITMAP_THRESH).astype(np.uint8)*255
    contours,_=cv2.findContours(binmask,cv2.RETR_LIST,cv2.CHAIN_APPROX_SIMPLE)
    objects=[]
    for ci,contour in enumerate(contours[:DET_MAX_CANDIDATES]):
        if len(contour)<3:continue
        rect=cv2.minAreaRect(contour.astype(np.float32))
        if min(rect[1])<3:continue
        raw=ordered_quad(cv2.boxPoints(rect))
        score=score_box(prob,raw)
        if score<min(THRESHOLDS):continue
        area=abs(cv2.contourArea(raw))
        perimeter=cv2.arcLength(raw,True)
        if perimeter<=1.e-6:continue
        delta=area*DET_UNCLIP/perimeter
        offset=pyclipper.PyclipperOffset()
        offset.AddPath([tuple(map(int,np.rint(p))) for p in raw],
                       pyclipper.JT_ROUND,pyclipper.ET_CLOSEDPOLYGON)
        paths=offset.Execute(delta)
        if not paths:continue
        larger=max(paths,key=lambda p:abs(cv2.contourArea(np.asarray(p,np.float32))))
        finalrect=cv2.minAreaRect(np.asarray(larger,np.float32))
        if min(finalrect[1])<5:continue
        final=ordered_quad(cv2.boxPoints(finalrect))
        final[:,0]=np.clip(np.rint(final[:,0]*w/pw),0,w)
        final[:,1]=np.clip(np.rint(final[:,1]*h/ph),0,h)
        box=bbox_from_quad(final)
        if box[2]-box[0]<=3 or box[3]-box[1]<=3:continue
        objects.append({"id":ci,"score":float(score),"quad":final,
                        "bbox":box})
    return objects

def crop_box(rgb,box):
    # SDK crops via minAreaRect again, then perspective warp and rotate vertical.
    quad=ordered_quad(cv2.boxPoints(cv2.minAreaRect(box["quad"])))
    dstw=max(1,int(max(np.linalg.norm(quad[0]-quad[1]),
                       np.linalg.norm(quad[2]-quad[3]))))
    dsth=max(1,int(max(np.linalg.norm(quad[0]-quad[3]),
                       np.linalg.norm(quad[1]-quad[2]))))
    if dstw<4 or dsth<4 or dstw>3200 or dsth>3200:
        return None
    dst=np.float32([[0,0],[dstw,0],[dstw,dsth],[0,dsth]])
    matrix=cv2.getPerspectiveTransform(quad,dst)
    roi=cv2.warpPerspective(rgb,matrix,(dstw,dsth),flags=cv2.INTER_CUBIC,
                            borderMode=cv2.BORDER_REPLICATE)
    if roi.shape[0]/max(1,roi.shape[1])>=1.5:
        roi=cv2.rotate(roi,cv2.ROTATE_90_COUNTERCLOCKWISE)
    return roi

def rec(session,name,chars,crop):
    # Correct SDK reference: OpenCV crop (BGR) -> RGB before model.
    # Since raw image is PIL converted to RGB, crop already has RGB channels.
    h,w=crop.shape[:2]
    destw=max(1,min(MAX_RECOGNITION_WIDTH,int(math.ceil(48*w/max(1,h)))))
    small=cv2.resize(crop,(destw,48),interpolation=cv2.INTER_LINEAR)
    t=np.ascontiguousarray(small.transpose(2,0,1)[None].astype(np.float32)/127.5-1.)
    start=time.perf_counter()
    result=session.run(None,{name:t})[0][0]
    ms=(time.perf_counter()-start)*1000
    inds=result.argmax(1)
    max_scores=result.max(1)
    letters=[];conf=[];last=-1
    for j,ii in enumerate(inds):
        ii=int(ii)
        if ii!=0 and ii!=last and ii<=len(chars):
            letters.append(chars[ii-1])
            conf.append(float(max_scores[j]))
        last=ii
    return "".join(letters),float(np.mean(conf)) if conf else 0.,ms

def matched_candidates(cands,gt):
    edges=[]
    for i,c in enumerate(cands):
        for j,g in enumerate(gt):
            iou=box_iou(c["bbox"],g["bbox"])
            if iou>=EVAL_IOU:edges.append((iou,i,j))
    edges.sort(reverse=True)
    pi=set();gi=set();matches={}
    for iou,i,j in edges:
        if i in pi or j in gi:continue
        pi.add(i);gi.add(j);matches[i]=j
    return matches

def collect_images():
    rows=[]
    for split,parts in SLICES.items():
        for offset,length in parts:
            try:
                data=get_json("https://datasets-server.huggingface.co/rows",
                    params={"dataset":DATASET,"config":"default",
                            "split":split,"offset":offset,"length":length})
                for record in data.get("rows",[]):
                    v=record["row"]
                    u=(v.get("image") or {}).get("src")
                    if not u:continue
                    x=read_img((split,int(record["row_idx"]),u,
                                  v.get("texts",[]),v.get("polygons",[])))
                    if x is not None:rows.append(x)
            except Exception as e:
                print("WARN_METADATA_OR_IMAGE",split,offset,str(e)[:160],flush=True)
    rows=sorted(rows,key=lambda v:(v[0],v[1]))
    print("SOURCE_PHOTOS",len(rows),flush=True)
    return rows

def percentile(xs,p):
    if not xs:return None
    s=sorted(xs);return round(s[int((len(s)-1)*p)],2)

def aggregate(records,out):
    samples=collections.defaultdict(list)
    for r in records:samples[(r["split"],r["threshold"])].append(r)
    cats=["ALL","ReCTS","RCTW","ART"]
    report=[]
    for group in cats:
        for th in THRESHOLDS:
            r=[x for x in records if x["threshold"]==th and
               (group=="ALL" or x["split"]==group)]
            if not r:continue
            num_gt=sum(x["gt_total"] for x in r)
            match=sum(x["matched"] for x in r)
            predicted=sum(x["predicted"] for x in r)
            row={"category":group,"threshold":th,"images":len(r),
                "ground_truth":num_gt,"matched_gt":match,"boxes":predicted,
                "proxy_recall":round(match/max(1,num_gt),4),
                "proxy_precision":round(match/max(1,predicted),4),
                "model_inference_p95_ms":percentile([x["det_ms"] for x in r],.95),
                "postprocess_p95_ms":percentile([x["post_ms"] for x in r],.95)}
            report.append(row)
            print("CORRECTED_DETECTION_AB",json.dumps(row,ensure_ascii=False),flush=True)
    with (out/"thresholds.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(report[0]));w.writeheader();w.writerows(report)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("det_model")
    ap.add_argument("rec_model")
    ap.add_argument("rec_config")
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    out=Path(args.out);out.mkdir(exist_ok=True,parents=True)
    for p,h in [(args.det_model,DET_SHA),(args.rec_model,REC_SHA),
                (args.rec_config,REC_CONFIG_SHA)]:
        value=sha(p);print("CHECK_MODEL",Path(p).name,value,flush=True)
        if value!=h:raise RuntimeError("Model hash differs from #315")
    opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
    opts.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    det=ort.InferenceSession(args.det_model,sess_options=opts,
                             providers=["CPUExecutionProvider"])
    rec_session=ort.InferenceSession(args.rec_model,sess_options=opts,
                             providers=["CPUExecutionProvider"])
    di=det.get_inputs()[0].name
    ri=rec_session.get_inputs()[0].name
    chars=list(yaml.safe_load(Path(args.rec_config).read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")

    photos=collect_images()
    by_image=[];by_box=[];extra=[]
    total_gt=0
    for k,(split,idx,rgb,texts,polygons) in enumerate(photos):
        h,w=rgb.shape[:2]
        gt=[]
        for text,p in zip(texts,polygons):
            if not isinstance(text,str) or "###" in text or len(p)<8:continue
            pts=np.asarray(p,np.float32).reshape(-1,2)
            if not np.isfinite(pts).all():continue
            b=bbox_from_quad(pts)
            if b[2]-b[0]<6 or b[3]-b[1]<6:continue
            gt.append({"bbox":b,"text":text})
        if not gt:continue
        inp=det_preprocess(rgb)
        start=time.perf_counter()
        output=det.run(None,{di:inp})[0]
        det_ms=(time.perf_counter()-start)*1000
        prob=output.reshape(output.shape[-2:])
        start=time.perf_counter()
        boxes=detections(prob,h,w)
        post_ms=(time.perf_counter()-start)*1000
        groups={th:[b for b in boxes if b["score"]>=th] for th in THRESHOLDS}
        mapgt={th:matched_candidates(groups[th],gt) for th in THRESHOLDS}

        # Account for GT recovered in each threshold vs the true 0.45 baseline.
        base_matched={v for v in mapgt[BASE_BOX_THRESH].values()}
        for th in THRESHOLDS:
            mapped=mapgt[th]
            matched_set=set(mapped.values())
            by_image.append({
                "split":split,"image":idx,"threshold":th,
                "gt_total":len(gt),"matched":len(mapped),
                "matched_new_vs_045":len(matched_set-base_matched),
                "predicted":len(groups[th]),"det_ms":round(det_ms,2),
                "post_ms":round(post_ms,2)})
        base_ids={b["id"] for b in groups[BASE_BOX_THRESH]}
        for cand in boxes:
            if (len(boxes)>MAX_RECOGNITION_CROPS_PER_PHOTO and
                cand["id"] not in base_ids and cand["score"]<.35):
                # cap pathological frames; counts still include candidates.
                continue
            roi=crop_box(rgb,cand)
            if roi is None:continue
            try:text,conf,rec_ms=rec(rec_session,ri,chars,roi)
            except Exception as e:
                print("WARN_REC",split,idx,str(e)[:100],flush=True)
                continue
            gt_i=None
            iou=0.
            for j,g in enumerate(gt):
                overlap=box_iou(cand["bbox"],g["bbox"])
                if overlap>iou:gt_i=j;iou=overlap
            aligned_text=gt[gt_i]["text"] if iou>=EVAL_IOU and gt_i is not None else ""
            exact=bool(aligned_text and normalize(text)==normalize(aligned_text))
            err=distance(normalize(text),normalize(aligned_text)) if aligned_text else None
            recrow={"split":split,"image":idx,"candidate_id":cand["id"],
                "box_score":round(cand["score"],5),
                "is_new_below_045":int(cand["id"] not in base_ids),
                "gt_overlap_iou":round(iou,4),"gt_matched":int(iou>=EVAL_IOU),
                "gt_text":aligned_text,"ocr_text":text,
                "ocr_conf":round(conf,4),"ocr_ms":round(rec_ms,3),
                "text_len":len(normalize(text)),
                "text_kind":approx_ocr_text(text),
                "ocr_exact_when_matched":int(exact),
                "ocr_edits_when_matched":err,
                "bbox":",".join(str(round(z)) for z in cand["bbox"])}
            by_box.append(recrow)
            if recrow["is_new_below_045"]:
                extra.append(recrow)
        if (k+1)%18==0:
            print("PROGRESS_PHOTOS",k+1,"gt",sum(x["gt_total"] for x in by_image if x["threshold"]==.45),
                  "boxes_rec",len(by_box),flush=True)
    if not by_image:raise RuntimeError("No usable photo GT")
    aggregate(by_image,out)
    samples=collections.defaultdict(list)
    for r in extra:
        if r["box_score"]>=.40:bucket=".40-.45"
        elif r["box_score"]>=.35:bucket=".35-.40"
        else:bucket=".30-.35"
        samples[bucket].append(r)
    for bucket in [".40-.45",".35-.40",".30-.35"]:
        arr=samples[bucket]
        mapped=[r for r in arr if r["gt_matched"]]
        print("ADDED_BOX_OCR",json.dumps({
            "score_band":bucket,"n":len(arr),
            "matched_any_gt_proxy":len(mapped),
            "exact_text_when_gt_overlap":sum(r["ocr_exact_when_matched"] for r in mapped),
            "unknown_not_gt_matched":len(arr)-len(mapped),
            "recognizer_nonempty":sum(r["text_kind"]!="empty" for r in arr),
            "recognizer_conf_ge_0_70":sum(r["ocr_conf"]>=.7 and r["text_len"]>=2 for r in arr),
            "one_crop_rec_p95_ms":percentile([r["ocr_ms"] for r in arr],.95)
        },ensure_ascii=False),flush=True)
    # At each threshold, "admitted GT" is matched annotations, not guessed.
    base=[r for r in by_image if r["threshold"]==BASE_BOX_THRESH]
    basegt=sum(r["matched"] for r in base)
    print("COMPARE_TO_TRUE_315",json.dumps({
        "source_images":len(base),"baseline_threshold":BASE_BOX_THRESH,
        "baseline_matched_gt":basegt,
        "base_mask_threshold":DET_BITMAP_THRESH,"unclip":DET_UNCLIP,
        "input_max_side":DET_LONG_SIDE,"color_det":"BGR","color_rec":"RGB",
        "interpretation":"Unmatched candidates are UNKNOWN; no confirmed false-positive rate"}
        ,ensure_ascii=False),flush=True)
    for rows,path in [(by_image,"per_image.csv"),(by_box,"all_boxes_ocr.csv")]:
        with (out/path).open("w",newline="",encoding="utf-8-sig") as f:
            writer=csv.DictWriter(f,fieldnames=list(rows[0]))
            writer.writeheader();writer.writerows(rows)
    (out/"metadata.json").write_text(json.dumps({
        "purpose":"box threshold reduction deep dive; not Android UVC release evidence",
        "image_source":DATASET,"slices":SLICES,"thresholds":THRESHOLDS,
        "actual_deployed_threshold":BASE_BOX_THRESH,"det_mask":DET_BITMAP_THRESH,
        "unclip":DET_UNCLIP,"max_side":DET_LONG_SIDE,
        "model_sha":DET_SHA,"rec_sha":REC_SHA,
        "source_photos":len(photos),
        "n_GT_boxes":sum(r["gt_total"] for r in by_image if r["threshold"]==BASE_BOX_THRESH),
        "n_recognized_proposals":len(by_box),
        "n_below_baseline_recognized":len(extra),
        "important":"Python contour/unclip match approximates Android; no GT unlabeled evaluation",
        "no_user_photos_republished":True},indent=2,ensure_ascii=False))
    if len(base)<100:raise RuntimeError("Not enough usable scene photos")
    print("CORRECT_315_BOX_VALUE_DEEP_DIVE=PASS",len(base),len(extra),flush=True)
if __name__=="__main__":main()
