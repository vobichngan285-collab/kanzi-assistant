#!/usr/bin/env python3
"""Identical CRAFT768 INT8 heatmaps -> Python/OpenCV vs Android pure-Java box decoding.

Uses the ORIGINAL exact 20.95MB QDQ artifact, and 18 previously used natural
scene pictures (3 DEV + 3 HOLDOUT per dataset), plus six 90-deg portrait-only
geometry checks. This is a parity test, not a fresh blind accuracy evaluation.

No user photos, no APK execution or actual phone OCR. Creates numeric reports.
"""
import argparse,csv,json,math,subprocess,tempfile,time
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort
import craft_small_detector_ab as craft
import ocr_box_value_deep_dive as sdk

EXACT_SHA="9ab885d2f6669b77f70e9c8f68acfbe408c0b051501fd02d94bcaa0c7779ab15"

def java_decode(classpath, filename, raw, image_shape, input_shape):
    out_h,out_w=raw.shape[:2]
    image_h,image_w=image_shape[:2]
    _,_,input_h,input_w=input_shape
    args=["java","-Xmx384m","-cp",classpath,
          "com.vobich.kanzi.craftprobe.CraftBoxDecoderParityCli",
          str(filename),str(out_h),str(out_w),str(image_w),str(image_h),
          str(input_w),str(input_h)]
    result=subprocess.run(args,capture_output=True,text=True,check=True,timeout=30)
    lines=result.stdout.splitlines()
    meta=lines[0].split()
    if len(meta)!=4 or meta[0]!="META":
        raise RuntimeError(f"Java CRAFT header: {result.stdout[:300]}")
    boxes=[]
    for row in lines[1:]:
        vals=row.split()
        if len(vals)!=10 or vals[0]!="BOX":raise RuntimeError(f"Bad Java result: {row}")
        quad=np.asarray([float(v) for v in vals[2:]],dtype=np.float32).reshape(4,2)
        bbox=sdk.bbox_from_quad(quad)
        boxes.append(dict(quad=quad,bbox=bbox,score=float(vals[1]),engine="CRAFT-Java"))
    return boxes,dict(raw_components=int(meta[1]),surviving=int(meta[2]),kept=int(meta[3]))

def matched_iou(ref_boxes,java_boxes,threshold=.70):
    edges=[]
    for i,old in enumerate(ref_boxes):
        for j,new in enumerate(java_boxes):
            val=sdk.box_iou(old["bbox"],new["bbox"])
            if val>=threshold:edges.append((val,i,j))
    edges.sort(reverse=True)
    ids_a=set();ids_b=set();ious=[]
    for iou,i,j in edges:
        if i in ids_a or j in ids_b:continue
        ids_a.add(i);ids_b.add(j);ious.append(iou)
    return len(ious),ious

def run():
    p=argparse.ArgumentParser()
    p.add_argument("--model",required=True)
    p.add_argument("--classes",required=True)
    p.add_argument("--out",required=True)
    a=p.parse_args()
    out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    import hashlib
    digest=hashlib.sha256(Path(a.model).read_bytes()).hexdigest()
    assert digest==EXACT_SHA, digest
    craft.CRAFT_LONG_SIDE=768
    opt=ort.SessionOptions();opt.intra_op_num_threads=2;opt.inter_op_num_threads=1
    session=ort.InferenceSession(a.model,sess_options=opt,providers=["CPUExecutionProvider"])
    sdk.SLICES={key:[(0,12),(500,12)] for key in ("ART","RCTW","ReCTS")}
    photos=sdk.collect_images()
    assert len(photos)==72,len(photos)
    picked=[]
    for key in ("ART","RCTW","ReCTS"):
        for sub in ("DEV","HOLDOUT"):
            pics=[p for p in photos if p[0]==key and (p[1]<100)==(sub=="DEV")]
            picked.extend(pics[:3])
    assert len(picked)==18
    # Rotate six real scenes to exercise portrait/source-coordinate mapping.
    # These rotations are geometry tests only; do not use their original GT.
    samples=[(p,False) for p in picked]+[(p,True) for p in picked[:6]]
    rows=[]
    with tempfile.TemporaryDirectory() as tmp:
        for i,(item,rotated) in enumerate(samples,1):
            dataset,index,img,texts,polys=item
            if rotated:img=cv2.rotate(img,cv2.ROTATE_90_CLOCKWISE)
            model_input,ratio=craft.craft_preprocess(img)
            raw=session.run(None,{session.get_inputs()[0].name:model_input})[0][0]
            pyboxes,pydiag=craft.craft_regions(raw,img.shape[:2],ratio)
            binfile=Path(tmp)/"craft_heatmap.bin"
            np.asarray(raw,dtype="<f4",order="C").tofile(binfile)
            t=time.perf_counter()
            javaboxes,javadiag=java_decode(a.classes,binfile,raw,img.shape,model_input.shape)
            java_ms=(time.perf_counter()-t)*1000
            count,ious=matched_iou(pyboxes,javaboxes,.70)
            refgt=jgt=0;lost_gt=extra_gt=0
            if not rotated:
                gt=craft.gt_rows(texts,polys)
                refmatched=set(craft.box_pairs(pyboxes,gt).values())
                javamatched=set(craft.box_pairs(javaboxes,gt).values())
                refgt=len(refmatched);jgt=len(javamatched)
                lost_gt=len(refmatched-javamatched)
                extra_gt=len(javamatched-refmatched)
            row=dict(split=dataset,index=index,orientation="ROTATED" if rotated else "NATIVE",
                     source_width=img.shape[1],source_height=img.shape[0],
                     input_width=model_input.shape[-1],input_height=model_input.shape[-2],
                     python_boxes=len(pyboxes),java_boxes=len(javaboxes),
                     matched_boxes_iou070=count,box_recall_at_iou070=round(count/max(1,len(pyboxes)),3),
                     matched_iou_median=round(float(np.median(ious)),3) if ious else None,
                     python_gt_matched=refgt,java_gt_matched=jgt,
                     python_gt_lost_by_java=lost_gt,java_extra_gt=extra_gt,
                     python_components=pydiag["raw_components"],java_components=javadiag["raw_components"],
                     java_decode_subprocess_ms=round(java_ms,2))
            rows.append(row)
            print("CRAFT_BOX_REALPHOTO_PARITY",json.dumps(row),flush=True)
    overall=dict(
        total_scenes=len(rows),native_scenes=sum(r["orientation"]=="NATIVE" for r in rows),
        rotated_geometry_scenes=sum(r["orientation"]=="ROTATED" for r in rows),
        python_boxes=sum(r["python_boxes"] for r in rows),
        java_boxes=sum(r["java_boxes"] for r in rows),
        geometry_matched_at_iou070=sum(r["matched_boxes_iou070"] for r in rows),
        geometry_recall_at_iou070=round(
            sum(r["matched_boxes_iou070"] for r in rows)/max(1,sum(r["python_boxes"] for r in rows)),4),
        python_matched_gt_lines=sum(r["python_gt_matched"] for r in rows),
        java_matched_gt_lines=sum(r["java_gt_matched"] for r in rows),
        python_gt_matched_but_java_lost=sum(r["python_gt_lost_by_java"] for r in rows),
        java_extra_matched_gt=sum(r["java_extra_gt"] for r in rows),
        source_model_sha256=digest,
        not_true_end_to_end_ocr=True,
        python_gt_not_novel_blind=True,
        input_aspect_preserved=True)
    (out/"summary.json").write_text(json.dumps(overall,ensure_ascii=False,indent=2))
    with (out/"per_scene.csv").open("w",encoding="utf-8-sig",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    print("CRAFT_BOX_PARITY_SUMMARY",json.dumps(overall),flush=True)
    if len(rows)!=24:raise RuntimeError("Incomplete parity run")
    if overall["python_boxes"]<20:raise RuntimeError("CRAFT Python reference failed to decode")
    print("CRAFT_BOX_REALPHOTO_PARITY_COMPLETE",len(rows),flush=True)

if __name__=="__main__":run()
