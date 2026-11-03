#!/usr/bin/env python3
"""Second predeclared GT-blind ROI selector: high-resolution dense-text focus.

Run the IDENTICAL baseline/full/ROI Small REC evaluation pipeline with only
the ROI selector changed. It is declared BEFORE reading the new RCTW offset1400
held cohort (36 images); old diagnostic offset800 can be examined separately.

Unlike v1, this selector accepts high-res source even when Small found <4
boxes. This is essential for missing entire dense-print clusters. Canny edges
near known text anchor windows or exceptionally dense uncovered edges propose
up to two ROIs. No ground-truth boxes/strings or post-hoc GT gating.
"""
import math
import cv2
import numpy as np
import craft768_int8_conditional_local_roi_ab as core

MAX_SIDE_TRIGGER=1280
ROI_WIDTH_FRAC=.52
ROI_HEIGHT_FRAC=.38
MAX_ROIS=2
ANCHOR_MIN_EDGE=.025
NO_ANCHOR_MIN_EDGE=.055

def windows(w,h):
    cw=min(w,max(640,int(round(w*ROI_WIDTH_FRAC))))
    ch=min(h,max(640,int(round(h*ROI_HEIGHT_FRAC))))
    xs=sorted(set(int(round((w-cw)*r)) for r in (0.,.5,1.)))
    ys=sorted(set(int(round((h-ch)*r)) for r in (0.,.5,1.)))
    return [(x,y,x+cw,y+ch) for y in ys for x in xs]

def select_dense_print(rgb,small_boxes):
    h,w=rgb.shape[:2]
    median=core.median_small_height(small_boxes)
    if max(h,w)<MAX_SIDE_TRIGGER:
        return [],dict(reason="source_below_dense_trigger",
                       median_height=median,small_boxes=len(small_boxes),best_density=0.)
    ratio=min(1.,720./max(h,w))
    tw=max(1,int(round(w*ratio)));th=max(1,int(round(h*ratio)))
    thumbnail=cv2.resize(rgb,(tw,th),interpolation=cv2.INTER_AREA)
    gray=cv2.cvtColor(thumbnail,cv2.COLOR_RGB2GRAY)
    edges=cv2.Canny(gray,50,140)>0
    covered=np.zeros((th,tw),dtype=np.uint8)
    for box in small_boxes:
        x0,y0,x1,y1=box["bbox"]
        ax=max(0,int((x0-5)*tw/w))
        bx=min(tw,int(math.ceil((x1+5)*tw/w)))
        ay=max(0,int((y0-4)*th/h))
        by=min(th,int(math.ceil((y1+4)*th/h)))
        if bx>ax and by>ay:covered[ay:by,ax:bx]=1
    missing=edges & (covered==0)
    options=[]
    for roi in windows(w,h):
        x0,y0,x1,y1=roi
        sx=max(0,int(x0*tw/w));ex=min(tw,int(math.ceil(x1*tw/w)))
        sy=max(0,int(y0*th/h));ey=min(th,int(math.ceil(y1*th/h)))
        density=float(np.count_nonzero(missing[sy:ey,sx:ex]))/max(1,(ex-sx)*(ey-sy))
        anchors=sum(x0 <=(b["bbox"][0]+b["bbox"][2])*.5<=x1 and
                    y0 <=(b["bbox"][1]+b["bbox"][3])*.5<=y1
                    for b in small_boxes)
        if anchors>=1 and density>=ANCHOR_MIN_EDGE:
            why="edge_near_small"
        elif anchors==0 and density>=NO_ANCHOR_MIN_EDGE:
            why="very_dense_unmatched_edge"
        else:
            continue
        # Preserve recall when Small has missed the whole cluster.
        score=density*(1+.025*min(anchors,8))
        options.append((score,anchors,density,why,roi))
    options.sort(key=lambda v:(-v[0],-v[1],v[4]))
    chosen=[]
    for score,anchors,density,why,roi in options:
        if any(core.bbox_iou(roi,old["roi"])>.25 for old in chosen):continue
        chosen.append(dict(roi=roi,score=round(score,5),anchors=anchors,
                           trigger_type=why,uncovered_edge_density=round(density,5)))
        if len(chosen)>=MAX_ROIS:break
    return chosen,dict(reason="trigger" if chosen else "no_eligible_dense_edge",
                       median_height=median,small_boxes=len(small_boxes),
                       best_density=round(options[0][2],5) if options else 0.)

# The full pipeline's reference branch already guards model SHA and uses
# identical postprocessing/Small REC. Swap only the GT-blind proposal selector.
core.choose_rois=select_dense_print
core.COHORTS={
    "DIAGNOSTIC_800":{name:[(800,12)] for name in ("ART","RCTW","ReCTS")},
    "CHECK_RCTW_1400":{"RCTW":[(1400,36)]}
}

if __name__=="__main__":
    core.main()
