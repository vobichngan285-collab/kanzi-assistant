#!/usr/bin/env python3
"""Compare portrait-preserving CRAFT768 INT8 vs legacy forced-landscape 1080p letterbox.

Changes ONLY image padding before the same SHA-pinned CRAFT768 INT8 detector.
Both variants use the original identical PP-OCRv6 Small REC on original image
crops. Scoring uses the same public 72 annotated ReCTS/RCTW/ART real scenes as
previous studies; data has already guided model selection, not fresh blind GT.

Distinct from Android Java CRAFT box parity: this compares the effect of input
geometry using the original OpenCV/Python decoder on a single Linux CPU runner.
"""
import argparse,csv,json,math,time
from pathlib import Path
import cv2
import numpy as np
import craft_small_detector_ab as craft
import ocr_box_value_deep_dive as sdk

MODE_OLD="OLD_1920x1080_letterbox"
MODE_NEW="NEW_native_aspect"
MODES=(MODE_OLD,MODE_NEW)

def letterbox_legacy(rgb):
    src_h,src_w=rgb.shape[:2]
    r=min(1920/src_w,1080/src_h)
    w=max(1,int(round(src_w*r)));h=max(1,int(round(src_h*r)))
    resized=cv2.resize(rgb,(w,h),interpolation=cv2.INTER_LINEAR)
    canvas=np.full((1080,1920,3),255,dtype=np.uint8)
    left=(1920-w)//2;top=(1080-h)//2
    canvas[top:top+h,left:left+w]=resized
    return canvas,left,top,r

def project_back(boxes,left,top,r,source_w,source_h):
    result=[]
    for item in boxes:
        pts=item["quad"].copy().astype(np.float32)
        pts[:,0]=np.clip((pts[:,0]-left)/r,0,source_w)
        pts[:,1]=np.clip((pts[:,1]-top)/r,0,source_h)
        bbox=sdk.bbox_from_quad(pts)
        if bbox[2]-bbox[0]<6 or bbox[3]-bbox[1]<6:continue
        result.append(dict(quad=pts,bbox=bbox,score=item["score"],engine="CRAFT-legacy-letterbox"))
    return result

def stats(rows,cohort,mode,portrait_only=False):
    part=[r for r in rows if r["cohort"]==cohort and
          (not portrait_only or r["is_portrait"])]
    if not part:return None
    sumfield=lambda key:sum(x[f"{mode}_{key}"] for x in part)
    gt=sumfield("gt");chars=sumfield("gt_chars")
    times=sorted(x[f"{mode}_total_ms"] for x in part)
    dtimes=sorted(x[f"{mode}_detector_ms"] for x in part)
    return dict(cohort=cohort,mode=mode,portrait_only=portrait_only,
       photos=len(part),GT_lines=gt,
       GT_matched_lines=sumfield("matched"),
       GT_recall=round(sumfield("matched")/max(gt,1),5),
       unmatched_GT_characters=sumfield("missed_chars"),
       matched_word_edits=sumfield("rec_edits"),
       coverage_CER_proxy=round(sumfield("proxy_errors")/max(chars,1),5),
       unpaired_nonempty_predictions=sumfield("unmatched_text_boxes"),
       detected_boxes=sumfield("detected_boxes"),
       mean_full_ms=round(sum(times)/len(times),2),
       p95_full_ms=round(times[int((len(times)-1)*.95)],2),
       mean_detector_ms=round(sum(dtimes)/len(dtimes),2))

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--quant",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--max-photos",type=int,default=0)
    a=p.parse_args()
    o=Path(a.out);o.mkdir(parents=True,exist_ok=True)
    craft.CRAFT_LONG_SIDE=768
    det,rec,_,chars=craft.get_models(a.models)
    quant=craft.model(a.quant)
    sdk.SLICES={s:[(0,12),(500,12)] for s in ("ReCTS","RCTW","ART")}
    photos=sdk.collect_images()
    assert len(photos)==72,len(photos)
    if a.max_photos:photos=photos[:a.max_photos]
    rows=[]
    for i,(split,index,rgb,texts,polys) in enumerate(photos,1):
        h,w=rgb.shape[:2]
        gt=craft.gt_rows(texts,polys)
        if not gt:continue
        row=dict(cohort="DEV" if index<100 else "HOLDOUT",
                 split=split,index=index,width=w,height=h,
                 is_portrait=bool(h>w),gt_lines=len(gt))
        # Alternate the sequence to reduce CPU run-order bias.
        order=MODES if i%2 else MODES[::-1]
        for mode in order:
            started=time.perf_counter()
            if mode==MODE_OLD:
                canvas,left,top,r=letterbox_legacy(rgb)
                detected,detms,_=craft.craft_detect(quant,canvas)
                boxes=project_back(detected,left,top,r,w,h)
                input_shape=list(craft.craft_preprocess(canvas)[0].shape)
            else:
                boxes,detms,_=craft.craft_detect(quant,rgb)
                input_shape=list(craft.craft_preprocess(rgb)[0].shape)
            pred=craft.rec_predictions(rec,chars,rgb,boxes)
            total=(time.perf_counter()-started)*1000
            metrics=craft.score_pair(boxes,pred,gt)
            for k,value in metrics.items():
                row[f"{mode}_{k}"]=value
            row[f"{mode}_total_ms"]=round(total,2)
            row[f"{mode}_detector_ms"]=round(detms,2)
            row[f"{mode}_input_shape"]=str(input_shape)
        rows.append(row)
        if i%12==0:
            print("CRAFT768_PORTRAIT_PROGRESS",i,"/",len(photos),
                  "portrait",h>w,"GT",len(gt),
                  "legacy",row[MODE_OLD+"_matched"],
                  "native",row[MODE_NEW+"_matched"],flush=True)
    if not a.max_photos and len(rows)!=72:
        raise RuntimeError(f"Expected 72 photos, got {len(rows)}")
    summaries=[]
    for cohort in ("DEV","HOLDOUT"):
        for portrait_only in (False,True):
            for mode in MODES:
                item=stats(rows,cohort,mode,portrait_only)
                if item:
                    summaries.append(item)
                    print("CRAFT768_PORTRAIT_AB_RESULT",json.dumps(item,ensure_ascii=False),flush=True)
    (o/"summary.json").write_text(json.dumps(summaries,ensure_ascii=False,indent=2))
    with (o/"per_photo.csv").open("w",newline="",encoding="utf-8-sig") as file:
        wr=csv.DictWriter(file,fieldnames=list(rows[0]))
        wr.writeheader();wr.writerows(rows)
    (o/"limitations.txt").write_text(
      "Fixed 72 repeated public image photos, not new untouched blind smartphone photos. "
      "Old: white letterbox to 1920x1080 preserving source proportions, then resize to CRAFT long768; "
      "New: original aspect, direct CRAFT long768. "
      "Same SHA-verified INT8 graph, CRAFT confidence thresholds and Python/OpenCV boxes, same Small REC. "
      "The original Android app uses Bitmap interpolation and may differ by rounding/EXIF, "
      "while this experimental Python benchmark uses OpenCV interpolation. "
      "Geometry is scored by GT box IoU>=.4 and coverage CER proxy, not genuine "
      "full-page Chinese transcription/reading order or real phone. "
      "Linux CPU times are not Android benchmarks, and no real user photo was uploaded. "
      "Separate pure Java phone decoder has synthetic tests but is not the OpenCV decoder. "
      "No production Android changes and no APK in this workflow.\n")
    print("CRAFT768_PORTRAIT_AB_COMPLETE",len(rows),flush=True)

if __name__=="__main__":main()
