#!/usr/bin/env python3
"""Test bounded geometry-based text-line rebuilding on public real photos.

Input comes from exact #315 detector+recognizer's already-computed boxes
(run 41492030817). Candidate pairs use ONLY bbox geometry + box confidence,
never GT or recognized text. Rebuilt union ROI gets ONE extra ONNX recognition.

The baseline is concatenated OCR of the two original boxes in reading order
(zero added inference). New result is OCR of the whole union crop.
These are limited spatial test pairs, not complete end-to-end OCR CER.
GT correspondence may be uncertain; exclude it from CER and report counts.

The detector box proposal code is the same on the untouched held-out 132
photos, and the goal is reducing wrong spoken content, not increasing box count.
"""
import argparse,collections,csv,hashlib,io,json,re,statistics,time,unicodedata
from pathlib import Path
import numpy as np
import onnxruntime as ort
import requests
import yaml
from PIL import Image

from ocr_line_rebuild_geometry import propose,box,bbox_union
from ocr_box_value_deep_dive import rec,normalize,distance,REC_SHA,REC_CONFIG_SHA

def read_csv(path):
    with open(path,encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f))

def get_row(session,split,index,metadata):
    obj=metadata.get((split,str(index)))
    if obj is None:raise RuntimeError("Missing cached public source annotation")
    url=obj["image"]["src"]
    for attempt in range(4):
        try:
            request=session.get(url,timeout=65)
            request.raise_for_status()
            rgb=np.asarray(Image.open(io.BytesIO(request.content)).convert("RGB"))
            return rgb,obj
        except Exception as e:
            if attempt==3:raise
            time.sleep(3*(attempt+1))

def fetch_metadata_bulk(session,keys,holdout):
    """Six bulk requests instead of 34 individual /rows requests.
    Hugging Face dataset viewer rate limits small sequential requests."""
    ranges_by_split = (
      {"ReCTS":((500,22),(720,22)),"RCTW":((500,22),(720,22)),
       "ART":((500,22),(720,22))}
      if holdout else
      {"ReCTS":((0,24),(101,24),(310,24)),
       "RCTW":((0,24),(135,24),(315,24)),
       "ART":((0,24),(125,24),(295,24))})
    needed=set(keys)
    out={}
    for split,ranges in ranges_by_split.items():
        for start,count in ranges:
            if not any(s==split and start<=int(i)<start+count for s,i in needed):
                continue
            for attempt in range(5):
                try:
                    response=session.get("https://datasets-server.huggingface.co/rows",
                        params={"dataset":"Yesianrohn/OCR-Data","config":"default",
                                "split":split,"offset":start,"length":count},
                        timeout=90)
                    if response.status_code==429:
                        raise RuntimeError("HTTP 429 dataset viewer")
                    response.raise_for_status()
                    items=response.json().get("rows",[])
                    for item in items:
                        out[split,str(item["row_idx"])]=item["row"]
                    print("BULK_METADATA",split,start,"rows",len(items),flush=True)
                    break
                except Exception as e:
                    if attempt==4:raise
                    time.sleep(min(75,7*(2**attempt)))
            time.sleep(.8)
    missing=needed-set(out)
    if missing:raise RuntimeError("Missing metadata for "+str(sorted(missing)[:8]))
    return out

def intersection(a,b):
    return max(0,min(a[2],b[2])-max(a[0],b[0]))*max(0,min(a[3],b[3])-max(a[1],b[1]))

def area(a):
    return max(0,a[2]-a[0])*max(0,a[3]-a[1])

def truth_from_annotations(merged,raw_row):
    """Strictly comparable target: one to three GT boxes sufficiently inside
    single horizontal row, with a similar printed height. No GT text used
    to propose or choose crops. Uncertain GT alignment excluded from scoring."""
    gt=[]
    w=merged[2]-merged[0];h=merged[3]-merged[1]
    if h<7 or w<8:return None,"bad_candidate"
    for label,poly in zip(raw_row.get("texts",[]),raw_row.get("polygons",[])):
        if not isinstance(label,str) or "###" in label or len(poly)<8:continue
        pts=np.array(poly,dtype=np.float32).reshape(-1,2)
        b=(float(pts[:,0].min()),float(pts[:,1].min()),
           float(pts[:,0].max()),float(pts[:,1].max()))
        if area(b)<20:continue
        inside=intersection(merged,b)/area(b)
        cy=abs((merged[1]+merged[3])-(b[1]+b[3]))/2/max(1.,h)
        bh=b[3]-b[1]
        if inside>=.70 and cy<=.33 and .40 <= bh/h <= 1.65:
            gt.append((b,label,inside))
    if not gt:return None,"no_stable_gt_match"
    if len(gt)>3:return None,"too_many_gt_labels"
    # A paired union should not accidentally grab characters from TWO rows.
    for i,(a,_,_) in enumerate(gt):
        for b,_,_ in gt[i+1:]:
            yi=max(0,min(a[3],b[3])-max(a[1],b[1]))
            if yi / max(1,min(a[3]-a[1],b[3]-b[1]))<.50:
                return None,"two_different_gt_rows"
    gt.sort(key=lambda r:r[0][0])
    txt="".join(v[1] for v in gt)
    if not 2<=len(normalize(txt))<=90:return None,"gt_length_unusable"
    return txt,"gt_horizontal_line"

def crop_union(rgb,merged):
    h,w=rgb.shape[:2]
    x0,y0,x1,y1=merged
    x0=max(0,min(w-1,int(round(x0))))
    y0=max(0,min(h-1,int(round(y0))))
    x1=max(x0+1,min(w,int(round(x1))))
    y1=max(y0+1,min(h,int(round(y1))))
    if x1-x0<8 or y1-y0<7:return None
    return np.ascontiguousarray(rgb[y0:y1,x0:x1])

def quant(xs,p):
    if not xs:return None
    vals=sorted(xs)
    return round(vals[int((len(vals)-1)*p)],3)

def summarize(cases,mode,group):
    all_=[c for c in cases if c["mode"]==mode and
          (group=="ALL" or c["split"]==group)]
    paired=[c for c in all_ if c["gt_valid"]==1]
    chars=sum(c["gt_chars"] for c in paired)
    base=sum(c["baseline_edits"] for c in paired)
    rebuilt=sum(c["rebuilt_edits"] for c in paired)
    rows={
      "mode":mode,"category":group,"all_candidates":len(all_),
      "gt_comparable":len(paired),"gt_uncertain":len(all_)-len(paired),
      "low_box_involved":sum(c["low_involved"] for c in all_),
      "baseline_CER":round(base/max(1,chars),4) if chars else None,
      "rebuilt_CER":round(rebuilt/max(1,chars),4) if chars else None,
      "baseline_exact":sum(c["baseline_edits"]==0 for c in paired),
      "rebuilt_exact":sum(c["rebuilt_edits"]==0 for c in paired),
      "improved":sum(c["rebuilt_edits"]<c["baseline_edits"] for c in paired),
      "worsened":sum(c["rebuilt_edits"]>c["baseline_edits"] for c in paired),
      "unchanged":sum(c["rebuilt_edits"]==c["baseline_edits"] for c in paired),
      "added_rec_p50_ms":quant([c["rec_ms"] for c in all_],.5),
      "added_rec_p95_ms":quant([c["rec_ms"] for c in all_],.95),
      "extra_inference_calls":len(all_),
    }
    print("LINE_REBUILD_AB",json.dumps(rows,ensure_ascii=False),flush=True)
    return rows

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--boxes",required=True)
    ap.add_argument("--rec",required=True)
    ap.add_argument("--yml",required=True)
    ap.add_argument("--out",required=True)
    ap.add_argument("--holdout",action="store_true")
    args=ap.parse_args()
    output=Path(args.out);output.mkdir(parents=True,exist_ok=True)
    assert hashlib.sha256(Path(args.rec).read_bytes()).hexdigest()==REC_SHA
    assert hashlib.sha256(Path(args.yml).read_bytes()).hexdigest()==REC_CONFIG_SHA
    d=read_csv(args.boxes)
    groups=collections.defaultdict(list)
    for r in d:groups[r["split"],r["image"]].append(r)
    assert len(groups)>=100,"insufficient real-scene photos"
    is_low=sum(int(r["is_new_below_045"]) for r in d)
    assert is_low==(90 if args.holdout else 137),is_low
    options=ort.SessionOptions()
    options.intra_op_num_threads=2;options.inter_op_num_threads=1
    model=ort.InferenceSession(args.rec,sess_options=options,
                               providers=["CPUExecutionProvider"])
    name=model.get_inputs()[0].name
    chars=list(yaml.safe_load(Path(args.yml).read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    print("EVALUATION_DATASET", "UNTOUCHED_HOLDOUT" if args.holdout else "DEVELOPMENT",
          "images",len(groups),"added_boxes",is_low,flush=True)

    policies=("strict","balanced")
    # geometry may generate identical pair in strict and balanced: run model
    # only once; still report separate group results.
    jobs=collections.defaultdict(list)
    raw_counts=collections.Counter()
    for mode in policies:
        for key,rows in groups.items():
            found=propose(rows,mode=mode,max_pairs=2,min_score=.30)
            raw_counts[mode]+=len(found)
            for c in found:
                first=c["first"]["candidate_id"]
                second=c["second"]["candidate_id"]
                pairkey=(*key,*sorted([str(first),str(second)]))
                jobs[pairkey].append((mode,c))
    print("LINE_REBUILD_PROPOSALS",dict(raw_counts),
          "unique_pairs",len(jobs),"unique_images",
          len(set(k[:2] for k in jobs)),flush=True)
    session=requests.Session()
    metadata=fetch_metadata_bulk(session,{k[:2] for k in jobs},args.holdout)
    cache={}
    results=[];failed=0
    for index,key in enumerate(sorted(jobs),1):
        split,imageid=key[:2]
        if (split,imageid) not in cache:
            try:cache[split,imageid]=get_row(session,split,imageid,metadata)
            except Exception as e:
                failed+=1;print("WARN_REAL_PHOTO_FETCH",split,imageid,str(e)[:150],flush=True)
                continue
        rgb,row=cache[split,imageid]
        # Each mode shares EXACTLY the same pair; use one union inference.
        c=jobs[key][0][1]
        roi=crop_union(rgb,c["bbox"])
        if roi is None:continue
        try:
            merged,conf,infer_ms=rec(model,name,chars,roi)
        except Exception as e:
            print("WARN_REBUILD_INFERENCE",key,str(e)[:120],flush=True)
            continue
        truth,reason=truth_from_annotations(c["bbox"],row)
        ordering=sorted((c["first"],c["second"]),key=lambda x:box(x)[0])
        original="".join(x["ocr_text"] for x in ordering)
        for mode,pair in jobs[key]:
            valid=int(truth is not None)
            result={
                "split":split,"image":imageid,"mode":mode,
                "pair_id":key[2]+"+"+key[3],
                "new_low_score_involved":pair["low_involved"],
                "geometry_score":round(pair["geometry_score"],4),
                "union_bbox":",".join(str(round(z)) for z in c["bbox"]),
                "GT_alignment":reason,"gt_valid":valid,
                "gt_text":truth if valid else "",
                "gt_chars":len(normalize(truth)) if valid else 0,
                "baseline_fragment_concat":original,
                "rebuilt_result":merged,
                "baseline_edits":distance(normalize(truth),normalize(original)) if valid else -1,
                "rebuilt_edits":distance(normalize(truth),normalize(merged)) if valid else -1,
                "rec_conf":round(conf,4),"rec_ms":round(infer_ms,3),
                "low_involved":int(pair["low_involved"]),
            }
            results.append(result)
        if index%15==0:print("LINE_REBUILD_PROGRESS",index,"pairs",
                             "retained",len(results),"failed_images",failed,flush=True)
    if len(results)<(12 if args.holdout else 20):
        raise RuntimeError("Insufficient real image coverage for A/B: "+str(len(results)))
    summaries=[summarize(results,mode,cat)
               for mode in policies for cat in ["ALL","ReCTS","RCTW","ART"]]
    for c in results:
        if c["gt_valid"] and c["rebuilt_edits"]!=c["baseline_edits"]:
            print("LINE_REBUILD_CASE",json.dumps({
                "image":c["split"]+":"+c["image"],"mode":c["mode"],
                "GT":c["gt_text"],"baseline":c["baseline_fragment_concat"],
                "after":c["rebuilt_result"],"before_edits":c["baseline_edits"],
                "after_edits":c["rebuilt_edits"]},ensure_ascii=False),flush=True)
    with (output/"all_pairs.csv").open("w",newline="",encoding="utf-8-sig") as f:
        wr=csv.DictWriter(f,fieldnames=list(results[0]))
        wr.writeheader();wr.writerows(results)
    with (output/"summary.csv").open("w",newline="",encoding="utf-8-sig") as f:
        wr=csv.DictWriter(f,fieldnames=list(summaries[0]))
        wr.writeheader();wr.writerows(summaries)
    (output/"limitations.txt").write_text(
      "Only SAME-LINE fragments selected by geometry: not all detected text.\n"
      "Two old OCR box results are concatenated for the zero-added-cost baseline.\n"
      "GT alignment is an imperfect annotation/AABB proxy, and some pairs have no reliable GT.\n"
      "ONNX RGB recognition only; no Android UVC, SDK full-frame, frame queue, TTS, or thermal test.\n"
      "Candidate policy uses bounding boxes only, NOT the ground-truth or recognized texts.\n"
      "Accuracy uplift on IN-SAMPLE proposals alone is insufficient for deployment.\n"
      "APK and app branches are unchanged.\n")
    print("LINE_REBUILD_REAL_IMAGE_AB=PASS",
          "unique_pairs",len(jobs),"evaluated_rows",len(results),
          "download_failures",failed,flush=True)

if __name__=="__main__":main()
