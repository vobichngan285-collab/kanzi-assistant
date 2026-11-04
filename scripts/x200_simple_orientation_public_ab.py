#!/usr/bin/env python3
"""True Small DET+REC full-image 0 vs CCW90, same 12 public natural-scene photos.
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
ROOT=Path('/tmp/kanzi-simple-orientation')
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
sdk.SLICES={'RCTW':[(1400,6)],'ReCTS':[(500,6)]}
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
        pred,confidence,elapsed=sdk.rec(rec,rec.get_inputs()[0].name,chars,cv2.cvtColor(crop,cv2.COLOR_RGB2BGR))
        rec_ms+=elapsed
        recognized[i]=pred
    for pred_id,gt_id in assigned.items():
        if pred_id in recognized:gt[gt_id]['prediction']=recognized[pred_id]
    return {'det_ms':det_ms,'post_ms':post_ms,'rec_ms':round(rec_ms,2),
            'detected_boxes':len(boxes),'nonempty_boxes':sum(bool(x.strip()) for x in recognized.values()),
            'matched_gt':len(assigned)}

photos=sdk.collect_images()
expected={(sp,start+i) for sp,start in [('RCTW',1400),('ReCTS',500)] for i in range(6)}
assert {(sp,idx) for sp,idx,*_ in photos}==expected, 'public photo retrieval incomplete'
regions=[];per_photo=[]
for sp,idx,rgb,texts,polys in photos:
    gt=labels(texts,polys)
    assert len(gt)>0,(sp,idx)
    original_width=rgb.shape[1]
    for angle in (0,270):
        image=rgb if angle==0 else cv2.rotate(rgb,cv2.ROTATE_90_COUNTERCLOCKWISE)
        target=[transform(t,p,k,angle,original_width) for t,p,k in gt]
        stats=run_ocr(image,target)
        per_photo.append({'split':sp,'image':idx,'rotation':angle,'GT_regions':len(gt),**stats})
        for j,item in enumerate(target):
            prediction=item.get('prediction','')
            edits=sdk.distance(item['truth'],sdk.normalize(prediction))
            regions.append({'split':sp,'image':idx,'gt_id':j,'cohort':item['kind'],
                            'rotation':angle,'GT_chars':len(item['truth']),
                            'matched':int('prediction' in item),'edits':edits,'exact':int(edits==0)})
    print('SAME_PUBLIC_PHOTO_OCR',sp,idx,'GT',len(gt),flush=True)

def aggregate(rs):
    total=sum(r['GT_chars'] for r in rs)
    return {'regions':len(rs),'GT_chars':total,'matched':sum(r['matched'] for r in rs),
            'matched_chars':sum(r['GT_chars'] for r in rs if r['matched']),
            'missed_as_deletion_proxy_CER':round(sum(r['edits'] for r in rs)/max(1,total),5),
            'note':'Missing GT counts as deletion; extra unmatched OCR outputs are NOT scored. Not full-page CER.'}

metrics={}
for cohort in ('all','tall_gt','other_gt'):
    rr=[x for x in regions if cohort=='all' or x['cohort']==cohort]
    a={(r['split'],r['image'],r['gt_id']):r for r in rr if r['rotation']==0}
    b={(r['split'],r['image'],r['gt_id']):r for r in rr if r['rotation']==270}
    assert a.keys()==b.keys()
    both=[(a[k],b[k]) for k in a if a[k]['matched'] and b[k]['matched']]
    chars_shared=sum(x['GT_chars'] for x,y in both)
    metrics[cohort]={
        'zero':aggregate(list(a.values())),'left90':aggregate(list(b.values())),
        'identical_matched_regions':len(both),'identical_matched_GT_chars':chars_shared,
        'zero_same_region_CER':round(sum(x['edits'] for x,y in both)/chars_shared,5) if chars_shared else None,
        'left90_same_region_CER':round(sum(y['edits'] for x,y in both)/chars_shared,5) if chars_shared else None,
        'left90_better':sum(b[k]['edits']<a[k]['edits'] for k in a),
        'left90_worse':sum(b[k]['edits']>a[k]['edits'] for k in a),
        'same':sum(b[k]['edits']==a[k]['edits'] for k in a)}
summary={
 'status':'completed','photos':len(photos),'public_dataset':'Yesianrohn/OCR-Data',
 'method':'Same full image and labels; Small DET+REC original 0° vs 90° counterclockwise, no resizing distortion',
 'model_shas':MODELS,'metrics':metrics,
 'mean_linux_cpu_ms':{str(angle):{'det':round(np.mean([x['det_ms'] for x in per_photo if x['rotation']==angle]),1),
                                'rec':round(np.mean([x['rec_ms'] for x in per_photo if x['rotation']==angle]),1)}
                       for angle in (0,270)},
 'limitations':['Only 12 existing public photos, not user private NV21 or vivo hardware',
                'GT matches use AABB IoU >= .40, not verified line reading or true page CER',
                'Tall GT classification is geometry-only, cannot prove typesetting direction',
                'Unmatched GT treated as deletion, extra OCR false positives not included',
                'Linux CPU timing cannot establish phone latency, heat or voice continuity']}
for name,records in [('regions_no_text.csv',regions),('per_photo.csv',per_photo)]:
    with (OUT/name).open('w',newline='',encoding='utf-8-sig') as f:
        wr=csv.DictWriter(f,fieldnames=list(records[0]));wr.writeheader();wr.writerows(records)
(OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print('SIMPLE_ORIENTATION_TRUE_SMALL_OCR_COMPLETE',json.dumps(metrics,ensure_ascii=False),flush=True)
