#!/usr/bin/env python3
"""Held-out public Chinese horizontal/vertical OCR crop-axis A/B.

NO private/user images, NO Android deployment, NO generative OCR.
Use official v3.7.0 minAreaRect -> X-sort crop as immutable baseline.
Apply a guarded Y-sort candidate only when the 2 top and 2 bottom
minAreaRect vertices are disjoint on X and the box is very tall.
Evaluate ORIGINAL vs CANDIDATE on identical publicly annotated regions.
This is recognition ON ANNOTATION POLYGON, not an end-to-end DBNet benchmark.
"""
from __future__ import annotations
import argparse, collections, concurrent.futures, hashlib, io, json, math, re, statistics, time, unicodedata, zipfile
from pathlib import Path

import cv2
import numpy as np
import requests
from PIL import Image

REC_SHA = "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
YML_SHA = "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
DATASET = "Yesianrohn/OCR-Data"
# Avoid all 0-~750 source offsets used in earlier exploratory, holdout, oracle tests.
SPLITS = ("RCTW", "ReCTS", "ART", "CTW", "MTWI")
OFFSETS = (1910, 2190, 2470)
PER_BLOCK = 20

def sha(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for b in iter(lambda:f.read(1<<20),b""):h.update(b)
    return h.hexdigest()

def request(url,params=None):
    last=None
    for n in range(4):
        try:
            r=requests.get(url,params=params,headers={"User-Agent":"KanziPublicCropAxisHoldout/1.0"},
                           timeout=(20,90))
            if r.status_code in (429,500,502,503,504):
                time.sleep(n+1); continue
            r.raise_for_status()
            return r
        except requests.RequestException as e:
            last=e
            time.sleep(n+1)
    raise RuntimeError(str(last) if last else "retries exhausted")

def normalize(s):
    return "".join(c for c in unicodedata.normalize("NFKC",str(s)).lower()
                   if not c.isspace())

def edit(a,b):
    # Levenshtein; first arg hypothesis, second reference.
    prev=list(range(len(b)+1))
    for i,ch in enumerate(a,1):
        nxt=[i]
        for j,ref in enumerate(b,1):
            nxt.append(min(prev[j]+1,nxt[-1]+1,prev[j-1]+(ch!=ref)))
        prev=nxt
    return prev[-1]

def poly_np(flat):
    try:
        p=np.asarray(flat,dtype=np.float32).reshape(-1,2)
        if len(p)<4 or len(p)>100 or not np.isfinite(p).all():return None
        if np.max(p[:,0])-np.min(p[:,0])<5 or np.max(p[:,1])-np.min(p[:,1])<5:return None
        return p
    except (ValueError, TypeError):
        return None

def minrect_risk(points):
    rect_pts=cv2.boxPoints(cv2.minAreaRect(points))
    by_y=sorted(rect_pts.tolist(),key=lambda p:(p[1],p[0]))
    top=sorted(by_y[:2], key=lambda p:p[0])
    bottom=sorted(by_y[2:],key=lambda p:p[0])
    x_top_lo,x_top_hi=top[0][0],top[1][0]
    x_bot_lo,x_bot_hi=bottom[0][0],bottom[1][0]
    margin=max(x_top_lo-x_bot_hi,x_bot_lo-x_top_hi)
    minx,miny=np.min(rect_pts,axis=0)
    maxx,maxy=np.max(rect_pts,axis=0)
    w=float(maxx-minx); h=float(maxy-miny)
    risk=bool(w>=2 and h>=60 and h/max(w,1)>2.6 and margin>0)
    return rect_pts,risk,float(margin),h/max(w,1),top,bottom

def sdk_xsort_order(pts):
    # Mirrors upstream PaddleOCR Android v3.7.0 QuadGeometry.
    # Python stable sorting ties; exact ties are tracked as a portability caveat.
    arr=sorted(pts.tolist(),key=lambda p:p[0])
    a,b,c,d=arr
    tl,bl=(a,b) if b[1]>a[1] else (b,a)
    tr,br=(c,d) if d[1]>c[1] else (d,c)
    return np.float32([tl,tr,br,bl])

def ysort_order(top,bottom):
    return np.float32([top[0],top[1],bottom[1],bottom[0]])

def quad_crop(rgb,order):
    w=max(1,int(max(np.linalg.norm(order[0]-order[1]),
                    np.linalg.norm(order[2]-order[3]))))
    h=max(1,int(max(np.linalg.norm(order[0]-order[3]),
                    np.linalg.norm(order[1]-order[2]))))
    if min(w,h)<4 or max(w,h)>3200:return None
    dst=np.float32([[0,0],[w,0],[w,h],[0,h]])
    mat=cv2.getPerspectiveTransform(order,dst)
    image=cv2.warpPerspective(rgb,mat,(w,h),flags=cv2.INTER_CUBIC,
                             borderMode=cv2.BORDER_REPLICATE)
    if h/w>=1.5:
        image=cv2.rotate(image,cv2.ROTATE_90_COUNTERCLOCKWISE)
    return image

def recognize(session,chars,img):
    h,w=img.shape[:2]
    if h<3 or w<3:return None
    # OpenCV ROI is RGB here, as in verified SDK BGR -> RGB preprocessor.
    rw=max(1,min(3200,int(math.ceil(48*w/max(h,1)))))
    resized=cv2.resize(img,(rw,48),interpolation=cv2.INTER_LINEAR)
    tensor=np.ascontiguousarray(resized.transpose(2,0,1)[None].astype(np.float32)/127.5-1.)
    input_name=session.get_inputs()[0].name
    start=time.perf_counter()
    output=session.run(None,{input_name:tensor})[0][0]
    infer_ms=(time.perf_counter()-start)*1000
    inds=output.argmax(1)
    values=output.max(1)
    raw=[];probs=[];previous=-1
    for j,i in enumerate(inds):
        idx=int(i)
        if idx and idx!=previous and idx<=len(chars):
            raw.append(chars[idx-1]);probs.append(float(values[j]))
        previous=idx
    return "".join(raw),float(np.mean(probs)) if probs else 0.0,infer_ms

def get_scene_metadata():
    work=[]
    for split in SPLITS:
        for offset in OFFSETS:
            data=request("https://datasets-server.huggingface.co/rows",{
                "dataset":DATASET,"config":"default","split":split,
                "offset":offset,"length":PER_BLOCK}).json()
            rows=data.get("rows",[])
            print("SCENE_METADATA",split,offset,"fetched",len(rows),"total",data.get("num_rows_total"),flush=True)
            for r in rows:
                item=r["row"];photo=item.get("image") or {}
                if photo.get("src") and item.get("polygons") and item.get("texts"):
                    work.append((split,int(r["row_idx"]),photo["src"],item["texts"],item["polygons"]))
    return work

def fetch_scene(item):
    split,index,url,texts,polys=item
    try:
        r=request(url)
        if len(r.content)>16_000_000: return None
        rgb=np.array(Image.open(io.BytesIO(r.content)).convert("RGB"))
        return (split,index,rgb,list(zip(texts,polys)))
    except Exception as e:
        print("SCENE_IMAGE_SKIP",split,index,str(e)[:100],flush=True)
        return None

def collect_zip(zip_path,max_images):
    # Detection-format vertical archive may have annotations without transcripts.
    z=zipfile.ZipFile(zip_path)
    names=[n for n in z.namelist() if not n.endswith("/")]
    imgs=[n for n in names if re.search(r"\.(jpg|jpeg|png|webp)$",n,re.I)]
    ann=[n for n in names if re.search(r"\.(txt|json)$",n,re.I)]
    print("VERTICAL_ARCHIVE_INVENTORY",json.dumps({
        "files":len(names),"image_files":len(imgs),"annotation_files":len(ann),
        "example_paths":names[:12]},ensure_ascii=False),flush=True)
    # Match by image stem; when a dataset uses train.txt line listing,
    # parse that index as an additional fallback.
    labels=collections.defaultdict(list)
    for path in ann:
        labels[Path(path).stem].append(path)
    count=0; with_labels=0
    for imgpath in sorted(imgs)[:max_images]:
        try:
            data=z.read(imgpath)
            if len(data)>16_000_000:continue
            rgb=np.array(Image.open(io.BytesIO(data)).convert("RGB"))
        except Exception as e:
            print("VERTICAL_DECODE_SKIP",str(e)[:100],flush=True)
            continue
        matches=labels.get(Path(imgpath).stem,[])
        records=[]
        if matches:
            name=matches[0]
            try:
                txt=z.read(name).decode("utf-8-sig")
                entries=json.loads(txt)
                if isinstance(entries,dict):
                    entries=entries.get("annotations",entries.get("labels",[]))
                if isinstance(entries,list):
                    for e in entries:
                        if not isinstance(e,dict):continue
                        coords=e.get("points",e.get("polygon",e.get("quad")))
                        if isinstance(coords,list):
                            records.append((str(e.get("transcription",e.get("text","###"))),coords))
            except Exception:
                pass
        if records:with_labels+=1
        count+=1
        yield "VERTICAL",count,rgb,records
    z.close()
    print("VERTICAL_SCANNED",count,"matched_annotation_files",with_labels,flush=True)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--rec",required=True)
    ap.add_argument("--yml",required=True)
    ap.add_argument("--vertical-zip",required=True)
    ap.add_argument("--out",required=True)
    ap.add_argument("--vertical-limit",type=int,default=791)
    ap.add_argument("--max-rec-affected",type=int,default=240)
    args=ap.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    assert sha(args.rec)==REC_SHA, "weight hash mismatch"
    assert sha(args.yml)==YML_SHA, "dictionary hash mismatch"
    import yaml,onnxruntime as ort
    chars=list(yaml.safe_load(Path(args.yml).read_text(encoding="utf-8"))["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
    session=ort.InferenceSession(args.rec,sess_options=opts,providers=["CPUExecutionProvider"])

    summary={"reference":"PaddleOCR v3.7.0 QuadGeometry X-sort",
             "candidate":"guarded Y-sort when top and bottom X intervals disjoint",
             "data_source":"PUBLIC_ONLY",
             "scene_offsets":list(OFFSETS),
             "source_images":collections.Counter(),"all_annotated_regions":collections.Counter(),
             "eligible_regions":collections.Counter(),"guard_trigger_regions":collections.Counter(),
             "rec_comparisons":collections.Counter(),"scene_errors":0,
             "changed_cases":[],"timings_ms":{"baseline":[],"candidate":[]}}
    risky=[]
    def process(sp,idx,rgb,pairs):
        summary["source_images"][sp]+=1
        for line,(truth,poly) in enumerate(pairs):
            summary["all_annotated_regions"][sp]+=1
            pts=poly_np(poly)
            if pts is None:continue
            summary["eligible_regions"][sp]+=1
            rect,flag,margin,ratio,top,bottom=minrect_risk(pts)
            if flag:
                summary["guard_trigger_regions"][sp]+=1
                if len(risky)<args.max_rec_affected:
                    risky.append((sp,idx,line,rgb,rect,ysort_order(top,bottom),str(truth),margin,ratio))
    for item in collect_zip(args.vertical_zip,args.vertical_limit):
        process(*item)
    scenes=get_scene_metadata()
    print("SCENE_REQUESTED",len(scenes),flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        for i,photo in enumerate(pool.map(fetch_scene,scenes)):
            if photo is None:
                summary["scene_errors"]+=1
            else:process(*photo)
            if (i+1)%60==0:print("SCENE_PROGRESS",i+1,len(scenes),flush=True)

    # A/B only *affected* cases. No-op cases have identical ordering by definition.
    rows=[]
    for k,(sp,idx,line,rgb,rect,newquad,truth,margin,ratio) in enumerate(risky):
        old=quad_crop(rgb,sdk_xsort_order(rect))
        new=quad_crop(rgb,newquad)
        if old is None or new is None:continue
        try:
            oldresult=recognize(session,chars,old)
            newresult=recognize(session,chars,new)
            if oldresult is None or newresult is None:continue
        except Exception as e:
            print("REC_SKIP",sp,idx,line,str(e)[:130],flush=True);continue
        otext,oconf,otime=oldresult
        ntext,nconf,ntime=newresult
        gt=normalize(truth)
        has_gt=bool(gt and gt!="###" and "###" not in gt)
        row={"split":sp,"index":idx,"region":line,"source_text_length":len(gt) if has_gt else None,
             "is_scored":has_gt,"original_prediction":otext,"candidate_prediction":ntext,
             "baseline_conf":round(oconf,4),"candidate_conf":round(nconf,4),
             "baseline_ms":round(otime,2),"candidate_ms":round(ntime,2),
             "margin":round(margin,2),"ratio":round(ratio,2),
             "different_prediction":otext!=ntext}
        if has_gt:
            row["baseline_edits"]=edit(normalize(otext),gt)
            row["candidate_edits"]=edit(normalize(ntext),gt)
            row["reference_text"]=truth
        rows.append(row)
        summary["rec_comparisons"][sp]+=1
        summary["timings_ms"]["baseline"].append(otime)
        summary["timings_ms"]["candidate"].append(ntime)
        if (k+1)%25==0:print("REC_PROGRESS",k+1,len(risky),flush=True)
    scored=[r for r in rows if r["is_scored"]]
    chars_count=sum(r["source_text_length"] for r in scored)
    base_edits=sum(r["baseline_edits"] for r in scored)
    new_edits=sum(r["candidate_edits"] for r in scored)
    summary["totals"]={
        "scene_images_downloaded":sum(v for k,v in summary["source_images"].items() if k!="VERTICAL"),
        "vertical_images_scanned":summary["source_images"].get("VERTICAL",0),
        "all_annotated_regions":sum(summary["all_annotated_regions"].values()),
        "valid_annotation_regions":sum(summary["eligible_regions"].values()),
        "trigger_regions":sum(summary["guard_trigger_regions"].values()),
        "rec_affected_cases":len(rows),"with_annotation_transcripts":len(scored),
        "changed_predictions":sum(r["different_prediction"] for r in rows),
        "improved_gt_regions":sum(r["candidate_edits"]<r["baseline_edits"] for r in scored),
        "worsened_gt_regions":sum(r["candidate_edits"]>r["baseline_edits"] for r in scored),
        "tied_gt_regions":sum(r["candidate_edits"]==r["baseline_edits"] for r in scored),
        "scored_gt_chars":chars_count,
        "baseline_edit_distance":base_edits,
        "candidate_edit_distance":new_edits,
        "baseline_CER_affected":round(base_edits/chars_count,6) if chars_count else None,
        "candidate_CER_affected":round(new_edits/chars_count,6) if chars_count else None
    }
    for k in ("source_images","all_annotated_regions","eligible_regions","guard_trigger_regions","rec_comparisons"):
        summary[k]=dict(summary[k])
    summary["timings_ms"]={k:{"n":len(v),"median":round(statistics.median(v),3) if v else None,
        "sum":round(sum(v),2)} for k,v in summary["timings_ms"].items()}
    # Only public benchmark examples, no user data; store scoreable examples in artifact.
    (out/"affected_cases_public.json").write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding="utf-8")
    summary.pop("changed_cases")
    (out/"summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding="utf-8")
    print("HOLDOUT_CROP_AXIS_AB_COMPLETE",json.dumps(summary["totals"],ensure_ascii=False),flush=True)
    if summary["totals"]["vertical_images_scanned"]<500:raise RuntimeError("vertical archive insufficient")
    if summary["totals"]["scene_images_downloaded"]<170:raise RuntimeError("scene holdout insufficient")
if __name__=="__main__":
    main()
