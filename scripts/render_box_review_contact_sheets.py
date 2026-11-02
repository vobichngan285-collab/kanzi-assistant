#!/usr/bin/env python3
"""Ephemeral visual audit plates of selected public scene-text samples.

The plates are for internal manual inspection of low-scored OCR boxes.
Only recognized public-dataset images are used, no personal/private user photos.
No image files are committed to repository. GitHub run artifacts expire in 2d.
Legend: red = extra low-score box, green = GT box, cyan = existing high box.

The visual review is limited to this subset and MUST NOT be presented as
manual certification of every one of the 111 unmatched boxes.
"""
from collections import defaultdict
import argparse,csv,io,json,math,time
from pathlib import Path
import requests
from PIL import Image,ImageDraw,ImageFont,ImageOps

PAGE_W=1000
CARD_H=360
ROWS_PER_SHEET=4
CLASS_ORDER=[
  "annotation_fragment_potential_split",
  "near_annotated_text_no_overlap",
  "far_from_annotated_text",
  "overlaps_multiple_annotations_potential_merge",
  "slight_annotation_overlap",
  "gt_overlap_low_iou_boundary_mismatch"]
CLASS_SHORT={
  "annotation_fragment_potential_split":"FRAGMENT",
  "near_annotated_text_no_overlap":"NEAR GT",
  "far_from_annotated_text":"FAR FROM GT",
  "overlaps_multiple_annotations_potential_merge":"MERGED?",
  "slight_annotation_overlap":"SLIGHT GT",
  "gt_overlap_low_iou_boundary_mismatch":"EDGE?",
}

def get_json(url,params,session):
    for attempt in range(4):
        try:
            r=session.get(url,params=params,timeout=60)
            r.raise_for_status()
            return r.json()
        except Exception:
            if attempt==3:raise
            time.sleep(1.5*(attempt+1))

def get_image(url,session):
    for attempt in range(3):
        try:
            response=session.get(url,timeout=50)
            response.raise_for_status()
            return Image.open(io.BytesIO(response.content)).convert("RGB")
        except Exception:
            if attempt==2:raise
            time.sleep(1.5*(attempt+1))

def read_csv(path):
    with open(path,encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f))

def bounds(r):
    return tuple(int(float(v)) for v in r["bbox"].split(","))

def center(b):
    return ((b[0]+b[2])/2.,(b[1]+b[3])/2.)

def overlapping(a,b):
    return max(0,min(a[2],b[2])-max(a[0],b[0]))*max(0,min(a[3],b[3])-max(a[1],b[1]))

def resize_contain(im,canvas_size):
    return ImageOps.contain(im,canvas_size,Image.Resampling.LANCZOS)

def draw_info(frame, low, gts, baseline, offset=(0,0)):
    rect=ImageDraw.Draw(frame)
    pad=tuple(offset)
    lowbox=bounds(low)
    for label in gts:
        xs=label["poly"][::2];ys=label["poly"][1::2]
        if not xs:continue
        b=(min(xs),min(ys),max(xs),max(ys))
        if overlapping(b,lowbox)==0:
            dx=abs(center(b)[0]-center(lowbox)[0])
            dy=abs(center(b)[1]-center(lowbox)[1])
            if dx>max(130,2*(lowbox[2]-lowbox[0])) or dy>max(120,2*(lowbox[3]-lowbox[1])):
                continue
        rect.rectangle((b[0]-pad[0],b[1]-pad[1],b[2]-pad[0],b[3]-pad[1]),
                       outline=(24,188,78),width=3)
    for item in baseline:
        b=bounds(item)
        if overlapping(b,lowbox)>0:
            rect.rectangle((b[0]-pad[0],b[1]-pad[1],b[2]-pad[0],b[3]-pad[1]),
                           outline=(0,185,255),width=3)
    rect.rectangle((lowbox[0]-pad[0],lowbox[1]-pad[1],
                    lowbox[2]-pad[0],lowbox[3]-pad[1]),
                   outline=(255,28,25),width=4)

def get_gt_row(row):
    data=row["row"]
    return [{"text":t,"poly":p}
            for t,p in zip(data.get("texts",[]),data.get("polygons",[]))
            if isinstance(t,str) and isinstance(p,list)]

def region(frame,box):
    w,h=frame.size
    c=center(box)
    bw=max(320,4*(box[2]-box[0]))
    bh=max(210,4*(box[3]-box[1]))
    xmin=int(max(0,c[0]-bw/2))
    ymin=int(max(0,c[1]-bh/2))
    xmax=int(min(w,xmin+bw))
    ymax=int(min(h,ymin+bh))
    xmin=max(0,xmax-int(bw))
    ymin=max(0,ymax-int(bh))
    return frame.crop((xmin,ymin,xmax,ymax)),(xmin,ymin)

def create_plate(rows,photos,grouped_high,number,out):
    sheet=Image.new("RGB",(PAGE_W,CARD_H*len(rows)),(245,246,248))
    draw=ImageDraw.Draw(sheet)
    font=ImageFont.load_default()
    legends=[]
    for idx,rec in enumerate(rows):
        uid=(rec["split"],str(rec["image"]))
        if uid not in photos:continue
        image,gts=photos[uid]
        original=ImageOps.contain(image,(460,296))
        tmp=image.copy()
        draw_info(tmp,rec,gts,grouped_high.get(uid,[]))
        preview=resize_contain(tmp,(460,296))
        target,origin=region(image,bounds(rec))
        draw_info(target,rec,gts,grouped_high.get(uid,[]),origin)
        detail=resize_contain(target,(480,296))
        yy=idx*CARD_H
        draw.rectangle((5,yy+5,PAGE_W-5,yy+CARD_H-5),
                       outline=(198,202,209),width=2)
        sheet.paste(preview,(10+(460-preview.width)//2,yy+49+(296-preview.height)//2))
        sheet.paste(detail,(510+(480-detail.width)//2,yy+49+(296-detail.height)//2))
        label=f"{rec['split']} row={rec['image']} id={rec['candidate_id']}  {CLASS_SHORT.get(rec['geometric_hypothesis'],'OTHER')}  confidence={float(rec['OCR_confidence']):.3f}  score={float(rec['box_score']):.3f}"
        draw.text((12,yy+12),label,fill=(18,23,32),font=font)
        draw.text((12,yy+32),"RED candidate / GREEN ground truth / CYAN original high-score box",fill=(35,45,67),font=font)
        draw.text((12,yy+347),f"Full photo thumbnail (left)   |   Zoomed source region (right)  -  case {number*ROWS_PER_SHEET+idx+1}",fill=(45,50,65),font=font)
        legends.append({
          "sheet":number+1,
          "row_on_sheet":idx+1,
          "source":rec["split"]+":"+str(rec["image"]),
          "classification":rec["geometric_hypothesis"],
          "OCR_prediction":rec["OCR_result"],
          "nearest_GT_annotation":rec["nearest_GT_text"],
          "existing_high_box_coverage":float(rec["max_overlap_existing_high_box"]),
          "model_confidence":float(rec["OCR_confidence"]),
          "box_score":float(rec["box_score"]),
          "manual_verified":False
        })
    path=out/f"visual_review_sheet_{number+1:02d}.jpg"
    sheet.save(path,quality=85,optimize=True)
    return legends

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--review-queue",required=True)
    parser.add_argument("--baseline",required=True)
    parser.add_argument("--out",required=True)
    args=parser.parse_args()
    dest=Path(args.out);dest.mkdir(parents=True,exist_ok=True)
    items=read_csv(args.review_queue)
    baseline=read_csv(args.baseline)
    high=defaultdict(list)
    for row in baseline:
        if float(row["box_score"])>=.45:
            high[(row["split"],str(row["image"]))].append(row)

    selected=[];used=set()
    for category in CLASS_ORDER:
        match=[x for x in items if x["geometric_hypothesis"]==category]
        # Sample high- and low-confidence to check BOTH true text and obvious junk.
        match.sort(key=lambda x:(-int(x["human_review_priority_score"]),
                                 -float(x["OCR_confidence"])))
        pick=match[:4]
        if len(match)>6:
            pick+=match[-2:]
        for rec in pick:
            key=(rec["split"],str(rec["image"]),str(rec["candidate_id"]))
            if key not in used:selected.append(rec);used.add(key)
    selected=selected[:32]
    session=requests.Session()
    photos={};retained=[]
    for i,rec in enumerate(selected):
        key=(rec["split"],str(rec["image"]))
        try:
            if key not in photos:
                data=get_json("https://datasets-server.huggingface.co/rows",
                    {"dataset":"Yesianrohn/OCR-Data","config":"default",
                     "split":rec["split"],"offset":int(rec["image"]),"length":1},session)
                row=data["rows"][0]
                assert str(row["row_idx"])==rec["image"],key
                url=row["row"]["image"]["src"]
                im=get_image(url,session)
                photos[key]=(im,get_gt_row(row))
            retained.append(rec)
            print("AUDIT_IMAGE_FETCH",key,"pixels",photos[key][0].size,flush=True)
        except Exception as e:
            print("WARN_AUDIT_IMAGE_DOWNLOAD",key,str(e)[:120],flush=True)
    legends=[]
    for i in range(0,len(retained),ROWS_PER_SHEET):
        legends+=create_plate(retained[i:i+ROWS_PER_SHEET],
                              photos,high,i//ROWS_PER_SHEET,dest)
    (dest/"cases.json").write_text(json.dumps(legends,ensure_ascii=False,indent=2))
    (dest/"README.txt").write_text(
      "Ephemeral visual research only. These public-dataset photos are not user photos.\n"
      "Downloaded source images MUST NOT be added to the repository.\n"
      "Red low-score proposal, green human annotation, cyan existing high-score OCR.\n"
      "Manual reviewer must inspect original plates and record any ground-truth verdict separately.\n"
      "Plates illustrate a STRATIFIED SUBSET of 111 unresolved candidates.\n"
    )
    if len(retained)<15:raise RuntimeError("Too few images for meaningful visual audit")
    print("VISUAL_AUDIT_PLATES_CREATED=PASS",len(retained),
          "images",len(photos),"sheets",math.ceil(len(retained)/ROWS_PER_SHEET),flush=True)

if __name__=="__main__":main()
