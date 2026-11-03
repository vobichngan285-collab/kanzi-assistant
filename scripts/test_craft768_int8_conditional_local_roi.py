#!/usr/bin/env python3
"""Synthetic contract tests; no GT or tuned real-photo threshold search."""
import numpy as np
import cv2
import craft768_int8_conditional_local_roi_ab as experiment

def bb(x0,y0,x1,y1):
    return dict(bbox=(x0,y0,x1,y1),score=.9,
                quad=np.float32([[x0,y0],[x1,y0],[x1,y1],[x0,y1]]))

def main():
    assert len(experiment.candidate_windows(1500,2100))==9
    assert all(0<=x0<x1<=1500 and 0<=y0<y1<=2100
        for x0,y0,x1,y1 in experiment.candidate_windows(1500,2100))
    small=[bb(50+i*50,40,80+i*50,67) for i in range(8)]
    assert experiment.redundant(bb(55,43,84,69),small)
    assert not experiment.redundant(bb(850,800,1000,840),small)
    zoomed=bb(200,200,300,250)
    transformed=experiment.roi_to_original(zoomed,(400,100,800,500),2.,(1600,1200))
    assert abs(transformed["bbox"][0]-500)<.01
    assert abs(transformed["bbox"][1]-200)<.01
    blank=np.full((1500,1200,3),255,dtype=np.uint8)
    empty,diag=experiment.choose_rois(blank,small)
    assert len(empty)==0
    assert diag["reason"] in ("no_eligible_edges","image_short")
    too_small=np.full((300,400,3),255,dtype=np.uint8)
    no,diag=experiment.choose_rois(too_small,small)
    assert not no and diag["reason"]=="image_short"
    for i in range(6):
        cv2.putText(blank,"TEXT SMALL 1234",(55,300+i*110),
            cv2.FONT_HERSHEY_SIMPLEX,1.05,(20,20,20),2,cv2.LINE_AA)
    anchor=[bb(75,300+i*110,250,320+i*110) for i in range(6)]
    got,why=experiment.choose_rois(blank,anchor)
    assert len(got)<=2
    assert all(item["anchors"]>=2 for item in got)
    print("CRAFT_LOCAL_ROI_SYNTHETIC_GATE_TEST_PASS",
          len(got),"candidate ROIs",diag["reason"],flush=True)

if __name__=="__main__":main()
