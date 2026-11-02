#!/usr/bin/env python3
"""Research-only same-line detector-box stitching; no user data or Android changes.

Only existing PP-OCR detection coordinates are examined. Neither OCR text nor
ground-truth labels participate in choosing pairs; they are ONLY used for
evaluation. Candidate retries are capped at two per frame and are off by default.
"""
import math

def box(item):
    v=item["bbox"]
    return tuple(map(float,v.split(","))) if isinstance(v,str) else tuple(map(float,v))

def bbox_union(a,b):
    return min(a[0],b[0]),min(a[1],b[1]),max(a[2],b[2]),max(a[3],b[3])

def pair_score(first,second,mode="strict"):
    a,b=box(first),box(second)
    wa,wb=a[2]-a[0],b[2]-b[0]
    ha,hb=a[3]-a[1],b[3]-b[1]
    if min(wa,wb)<7 or min(ha,hb)<9:return None
    ratio=ha/hb
    if not (.58<=ratio<=1.72):return None
    yint=max(0,min(a[3],b[3])-max(a[1],b[1]))
    yfrac=yint/min(ha,hb)
    cy=abs((a[1]+a[3])-(b[1]+b[3]))/2/((ha+hb)/2)
    gap=max(0,max(a[0]-b[2],b[0]-a[2]))
    xov=max(0,min(a[2],b[2])-max(a[0],b[0]))/min(wa,wb)
    maxgap,miny,maxcy=(.52,.70,.27) if mode=="strict" else (.85,.55,.38)
    if yfrac<miny or cy>maxcy or gap>maxgap*max(ha,hb) or xov>.12:return None
    united=bbox_union(a,b)
    if united[2]-united[0]>2400 or (united[3]-united[1])/min(ha,hb)>1.8:return None
    return gap/max(ha,hb)+cy+abs(math.log(ratio))*.25

def propose(boxes,mode="strict",max_pairs=2,min_score=.30,include_high_high=True):
    eligible=[b for b in boxes if float(b["box_score"])>=min_score]
    candidates=[]
    for i,a in enumerate(eligible):
        for b in eligible[i+1:]:
            low_a=float(a["box_score"])<.45
            low_b=float(b["box_score"])<.45
            if low_a and low_b:continue
            if not include_high_high and not(low_a or low_b):continue
            s=pair_score(a,b,mode)
            if s is not None:
                candidates.append({"first":a,"second":b,"bbox":bbox_union(box(a),box(b)),
                                   "geometry_score":s,"low_involved":int(low_a or low_b)})
    candidates.sort(key=lambda c:(-c["low_involved"],c["geometry_score"],
                                  c["bbox"][1],c["bbox"][0]))
    used=set();selected=[]
    for c in candidates:
        i=c["first"]["candidate_id"];j=c["second"]["candidate_id"]
        if i in used or j in used:continue
        used.update((i,j));selected.append(c)
        if len(selected)>=max_pairs:break
    return selected

def run_tests():
    def b(i,x0,y0,x1,y1,p=.80):
        return {"candidate_id":str(i),"bbox":f"{x0},{y0},{x1},{y1}",
                "box_score":str(p)}
    a=b(1,10,10,60,31)
    close=b(2,68,11,118,31,.38)
    assert pair_score(a,close) is not None
    assert len(propose([a,close],include_high_high=False))==1
    assert pair_score(a,b(3,60,46,113,66)) is None, "do not merge menu rows"
    assert pair_score(a,b(4,26,10,50,30)) is None, "no nested duplicate"
    assert pair_score(a,b(5,90,10,141,31)) is None, "far apart"
    assert pair_score(a,b(6,63,10,109,61)) is None, "font height mismatch"
    assert not propose([b(7,10,10,60,31,.34),b(8,68,10,118,31,.39)])
    assert len(propose([a,close,b(9,126,10,169,31)],max_pairs=1))==1
    assert not propose([])
    print("LINE_REBUILD_GEOMETRY_SMOKE=PASS")

if __name__=="__main__":run_tests()
