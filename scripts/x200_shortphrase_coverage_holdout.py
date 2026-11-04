#!/usr/bin/env python3
"""Frozen short horizontal GT phrases: Small DET+REC, complete-output confidence gating, 45 unseen public photos.
No private images; no TTS or Android claims. Same GT, transformed with pixels.
"""
import csv,hashlib,json,time,collections
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort,yaml
import ocr_box_value_deep_dive as sdk

MODELS={
 'det.onnx':'d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e',
 'small_rec.onnx':'5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634',
 'small_rec.yml':'ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1'}
ROOT=Path('/tmp/kanzi-short-holdout')
OUT=ROOT/'output'
OUT.mkdir(parents=True,exist_ok=True)
for name,sha in MODELS.items():
    assert hashlib.sha256((ROOT/'models'/name).read_bytes()).hexdigest()==sha, ('SHA_MISMATCH',name)
opts=ort.SessionOptions()
opts.intra_op_num_threads=2
opts.inter_op_num_threads=1
det=ort.InferenceSession(str(ROOT/'models'/'det.onnx'),opts,providers=['CPUExecutionProvider'])
rec=ort.InferenceSession(str(ROOT/'models'/'small_rec.onnx'),opts,providers=['CPUExecutionProvider'])
chars=list(yaml.safe_load((ROOT/'models'/'small_rec.yml').read_text())['PostProcess']['character_dict'])
if chars[-1]!=' ': chars.append(' ')
sdk.SLICES={'RCTW':[(920,15)],'ReCTS':[(920,15)],'ART':[(920,15)]}
sdk.THRESHOLDS=[.45]
sdk.DET_BITMAP_THRESH=.20
sdk.DET_UNCLIP=1.40

def labels(texts,polys):
    result=[]
    for text,poly in zip(texts,polys):
        if not isinstance(text,str) or '###' in text:continue
        try:pts=np.asarray(poly,np.float32).reshape(-1,2)
        except (ValueError,TypeError):continue
        if len(pts)<4 or not np.isfinite(pts).all():continue
        bb=sdk.bbox_from_quad(pts)
        if bb[2]-bb[0]<6 or bb[3]-bb[1]<6:continue
        truth=sdk.normalize(text)
        if not truth:continue
        kind='tall_gt' if (bb[3]-bb[1])/max(1,bb[2]-bb[0])>=1.8 and len(truth)>=3 else 'other_gt'
        result.append((truth,pts,kind))
    return result

def transform(truth,pts,kind,angle,original_width):
    if angle==270:
        pts=np.column_stack((pts[:,1],original_width-pts[:,0])).astype(np.float32)
    return {'truth':truth,'kind':kind,'bbox':sdk.bbox_from_quad(pts)}

def run_ocr(rgb,gt):
    h,w=rgb.shape[:2]
    t=time.perf_counter()
    raw=det.run(None,{det.get_inputs()[0].name:sdk.det_preprocess(rgb)})[0]
    det_ms=round(1000*(time.perf_counter()-t),2)
    t=time.perf_counter()
    boxes=[b for b in sdk.detections(raw.reshape(raw.shape[-2:]),h,w) if b['score']>=.45]
    post_ms=round(1000*(time.perf_counter()-t),2)
    assigned=sdk.matched_candidates(boxes,gt)
    rec_ms=0.0
    recognized={}
    for i,box in enumerate(boxes):
        crop=sdk.crop_box(rgb,box)
        if crop is None:continue
        pred,confidence,elapsed=sdk.rec(rec,rec.get_inputs()[0].name,chars,crop)
        rec_ms+=elapsed
        recognized[i]=(pred,confidence,float(box['bbox'][3]-box['bbox'][1]))
    for pred_id,gt_id in assigned.items():
        if pred_id in recognized:
            pred,confidence,box_height=recognized[pred_id]
            gt[gt_id]['prediction']=pred
            gt[gt_id]['confidence']=confidence
            gt[gt_id]['box_height']=box_height
    return {'det_ms':det_ms,'post_ms':post_ms,'rec_ms':round(rec_ms,2),
            'detected_boxes':len(boxes),'nonempty_boxes':sum(bool(x[0].strip()) for x in recognized.values()),
            'matched_gt':len(assigned)}

photos=sdk.collect_images()
expected={(sp,i) for sp in ('RCTW','ReCTS','ART') for i in range(920,935)}
assert {(sp,idx) for sp,idx,*_ in photos}==expected, 'public photo retrieval incomplete'
regions=[];per_photo=[]
for sp,idx,rgb,texts,polys in photos:
    gt=labels(texts,polys)
    gt=[(t,p,k) for t,p,k in gt if
        3<=len(t)<=24 and
        (lambda bb:(bb[2]-bb[0])>=1.8*max(1,bb[3]-bb[1]))(sdk.bbox_from_quad(p))]
    original_width=rgb.shape[1]
    for angle in (0,):
        image=rgb if angle==0 else cv2.rotate(rgb,cv2.ROTATE_90_COUNTERCLOCKWISE)
        target=[transform(t,p,k,angle,original_width) for t,p,k in gt]
        stats=run_ocr(image,target)
        per_photo.append({'split':sp,'image':idx,'rotation':angle,'GT_regions':len(gt),**stats})
        for j,item in enumerate(target):
            prediction=item.get('prediction','')
            edits=sdk.distance(item['truth'],sdk.normalize(prediction))
            confidence=float(item.get('confidence',0.))
            pred=sdk.normalize(prediction)
            height=float(item.get('box_height',0.))
            size_ok=height>=max(14.,.016*min(rgb.shape[0],rgb.shape[1]))
            length_ok=2<=len(pred)<=32
            matched=bool(prediction.strip())
            digit_truth=''.join(ch for ch in item['truth'] if ch.isdigit())
            digit_pred=''.join(ch for ch in pred if ch.isdigit())
            regions.append({'split':sp,'image':idx,'gt_id':j,
              'cohort':'contains_digits' if digit_truth else 'no_digits',
              'GT_chars':len(item['truth']),'pred_chars':len(pred),
              'matched':int(matched),'edits':edits,'exact':int(edits==0),
              'confidence':round(confidence,5),'box_height':round(height,2),
              'size_pass':int(size_ok),'length_pass':int(length_ok),
              'truncated':int(matched and len(pred)<.8*len(item['truth'])),
              'wrong_digits':int(matched and bool(digit_truth) and digit_truth!=digit_pred),
              'all_matched':int(matched),
              'gate_075':int(matched and size_ok and length_ok and confidence>=.75),
              'gate_085':int(matched and size_ok and length_ok and confidence>=.85),
              'gate_090':int(matched and size_ok and length_ok and confidence>=.90)})
    print('SAME_PUBLIC_PHOTO_OCR',sp,idx,'GT',len(gt),flush=True)

assert len(photos)==45,("Unexpected public image count",len(photos))
assert len(regions)>=30,("Too few annotated horizontal short GT lines",len(regions))
def summary(rs,key):
    spoken=[x for x in rs if x[key]]
    nchars=sum(x['GT_chars'] for x in spoken)
    total_chars=sum(x['GT_chars'] for x in rs)
    edits=sum(x['edits'] for x in spoken)
    exact=sum(x['exact'] for x in spoken)
    return {'annotated_short_GT_regions':len(rs),
        'annotated_GT_chars':total_chars,
        'spoken_regions':len(spoken),
        'region_coverage':round(len(spoken)/max(1,len(rs)),4),
        'spoken_GT_chars':nchars,
        'character_coverage':round(nchars/max(1,total_chars),4),
        'spoken_exact_regions':exact,
        'spoken_exact_rate':round(exact/len(spoken),4) if spoken else None,
        'exact_over_all_GT':round(exact/max(1,len(rs)),4),
        'spoken_edits':edits,
        'spoken_CER':round(edits/max(1,nchars),4) if spoken else None,
        'spoken_truncated_80percent':sum(x['truncated'] for x in spoken),
        'spoken_wrong_digit_regions':sum(x['wrong_digits'] for x in spoken),
        'withheld_GT_regions':len(rs)-len(spoken)}
keys=('all_matched','gate_075','gate_085','gate_090')
bygroup={'all':regions,
    'contains_digits':[x for x in regions if x['cohort']=='contains_digits'],
    'no_digits':[x for x in regions if x['cohort']=='no_digits']}
metrics={group:{key:summary(rows,key) for key in keys} for group,rows in bygroup.items()}
report={
 'status':'completed','public_photos':len(photos),
 'same_actual_small_detection_and_recognition':True,
 'model_hashes':MODELS,
 'scope':'public horizontal short annotated GT phrases 3..24 chars, width/height>=1.8; GT used ONLY for evaluation',
 'speech_gate':'One matched full predicted region spoken or entirely withheld; no substring trimming; size>=max(14px,1.6% of shorter frame edge), predicted text length2..32 and confidence .75/.85/.90',
 'metrics':metrics,
 'limitations':['No arbitrary scene-wide human chosen sentence detection; GT phrase labels select evaluation cohort only',
    'Missing or unmatched GT is included as coverage loss; extra false-positive detector regions are not scored',
    'One OCR box can still be an incomplete sentence despite high confidence',
    'True digits may be misrecognized with high confidence; do not treat score as certainty',
    'Independent public photos, Python SDK-like preprocessing, Linux CPU, NOT vivo phone TTS/heat test',
    'Thresholds are exploratory fixed sensitivities, NOT calibrated phone production thresholds']}
for name,records in [('per_gt_no_text.csv',regions),('per_photo.csv',per_photo)]:
    with (OUT/name).open('w',newline='',encoding='utf-8-sig') as f:
        writer=csv.DictWriter(f,fieldnames=list(records[0]))
        writer.writeheader();writer.writerows(records)
(OUT/'summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('INDEPENDENT_SHORT_PHRASE_TRUE_SMALL_AB_COMPLETE',json.dumps(metrics,ensure_ascii=False),flush=True)
