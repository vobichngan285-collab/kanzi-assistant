#!/usr/bin/env python3
"""Independent synthetic high-resolution ROI v2 contract, no GT."""
import cv2
import numpy as np
import craft768_int8_dense_local_roi_v2 as v2
import craft768_int8_conditional_local_roi_ab as baseline

def bbox(x0,y0,x1,y1):
    return dict(bbox=(float(x0),float(y0),float(x1),float(y1)),
                quad=np.float32([[x0,y0],[x1,y0],[x1,y1],[x0,y1]]),score=.95)

def main():
    assert len(v2.windows(2448,3264))==9
    for ro in v2.windows(2448,3264):
        assert 0<=ro[0]<ro[2]<=2448
        assert 0<=ro[1]<ro[3]<=3264
    # Realistic small text surrounded by uncovered strokes / missing text.
    img=np.full((2048,1536,3),250,np.uint8)
    for i in range(18):
        cv2.putText(img,"Dense text %d abc ABC 123"%(i,),
                    (80,260+i*76),cv2.FONT_HERSHEY_SIMPLEX,1.1,(15,15,15),2,cv2.LINE_AA)
    sparse=[bbox(100,260,330,290)]
    found,diag=v2.select_dense_print(img,sparse)
    assert 0<len(found)<=2,(diag,len(found))
    assert all(x["uncovered_edge_density"]>=.025 for x in found)
    assert all(x["trigger_type"] in ("edge_near_small","very_dense_unmatched_edge") for x in found)
    tiny=np.full((320,480,3),255,np.uint8)
    notfound,diag=v2.select_dense_print(tiny,sparse)
    assert not notfound and diag["reason"]=="source_below_dense_trigger"
    assert baseline.MAX_ROIS==2
    print("CRAFT_DENSE_ROI_V2_SYNTHETIC_PASS",len(found),
          [f["trigger_type"] for f in found],flush=True)

if __name__=="__main__":main()
