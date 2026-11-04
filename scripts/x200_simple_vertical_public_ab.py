#!/usr/bin/env python3
"""End-to-end Small DET+REC 0 vs left90 on public annotated vertical pages.
Reuses the exact pinned model sessions and SDK-like helpers from public A/B.
No private user images, no APK, no TTS.
"""
import csv,io,json,runpy,zipfile
from pathlib import Path
import cv2,numpy as np
from PIL import Image

public=runpy.run_path('scripts/x200_simple_orientation_public_ab.py')
labels=public['labels']
transform=public['transform']
run_ocr=public['run_ocr']
sdk=public['sdk']
out=Path('/tmp/kanzi-simple-orientation/output')
archive=Path('/tmp/kanzi-simple-orientation/vertical_annotations.zip')
with zipfile.ZipFile(archive) as z:
    names=z.namelist()
    assert 'metadata.jsonl' in names,'VERTICAL_ARCHIVE_METADATA_MISSING'
    metadata={}
    for line in z.read('metadata.jsonl').decode('utf-8-sig').splitlines():
        if not line.strip():continue
        item=json.loads(line)
        key=Path(str(item.get('image',''))).stem
        if key:metadata[key]=item.get('annotations',[])
    imgs=sorted(n for n in names if n.lower().endswith(('.jpg','.jpeg','.png')))
    assert len(imgs)==791,('VERTICAL_ARCHIVE_INCOMPLETE',len(imgs))
    per=[];regions=[];selected=0
    for path in imgs:
        ann=metadata.get(Path(path).stem,[])
        texts=[];polys=[]
        for row in ann:
            if not isinstance(row,dict):continue
            coords=row.get('points',row.get('polygon',row.get('quad')))
            if not isinstance(coords,list):continue
            texts.append(str(row.get('transcription',row.get('text','###'))))
            polys.append(coords)
        rgb=np.asarray(Image.open(io.BytesIO(z.read(path))).convert('RGB'))
        gt=labels(texts,polys)
        if len(gt)<5:continue
        selected+=1
        for angle in (0,270):
            image=rgb if angle==0 else cv2.rotate(rgb,cv2.ROTATE_90_COUNTERCLOCKWISE)
            targets=[transform(t,p,k,angle,rgb.shape[1]) for t,p,k in gt]
            stats=run_ocr(image,targets)
            per.append({'image_num':selected,'rotation':angle,'gt_regions':len(targets),**stats})
            for j,g in enumerate(targets):
                edits=sdk.distance(g['truth'],sdk.normalize(g.get('prediction','')))
                regions.append({'image_num':selected,'gt_id':j,'rotation':angle,'GT_chars':len(g['truth']),
                                'matched':int('prediction' in g),'edits':edits,'exact':int(edits==0)})
        print('VERTICAL_TRUE_SMALL_OCR',selected,'regions',len(gt),flush=True)
        if selected>=8:break
    assert selected==8,('INSUFFICIENT_ANNOTATED_VERTICAL_PAGES',selected)
zero={(r['image_num'],r['gt_id']):r for r in regions if r['rotation']==0}
left={(r['image_num'],r['gt_id']):r for r in regions if r['rotation']==270}
assert zero.keys()==left.keys()
shared=[(zero[k],left[k]) for k in zero if zero[k]['matched'] and left[k]['matched']]
shared_chars=sum(a['GT_chars'] for a,b in shared)
def aggregate(items):
    total=sum(x['GT_chars'] for x in items)
    return {'regions':len(items),'characters':total,'matched':sum(x['matched'] for x in items),
            'proxy_miss_as_deletion_CER':round(sum(x['edits'] for x in items)/max(1,total),5)}
result={'status':'completed','source':'AlroWilde/vertical_annotations first 8 labeled images',
        'gt_image_count':selected,'dataset_format':'archive metadata.jsonl, public annotations',
        'zero':aggregate(list(zero.values())),'left90':aggregate(list(left.values())),
        'both_matched_regions':len(shared),'both_matched_chars':shared_chars,
        'zero_shared_CER':round(sum(a['edits'] for a,b in shared)/shared_chars,5) if shared_chars else None,
        'left90_shared_CER':round(sum(b['edits'] for a,b in shared)/shared_chars,5) if shared_chars else None,
        'left90_better':sum(left[k]['edits']<zero[k]['edits'] for k in zero),
        'left90_worse':sum(left[k]['edits']>zero[k]['edits'] for k in zero),
        'same':sum(left[k]['edits']==zero[k]['edits'] for k in zero),
        'mean_linux_det_ms':{str(a):round(np.mean([r['det_ms'] for r in per if r['rotation']==a]),1) for a in (0,270)},
        'mean_linux_rec_ms':{str(a):round(np.mean([r['rec_ms'] for r in per if r['rotation']==a]),1) for a in (0,270)},
        'limitations':['Public archival material, not vivo camera','AABB matching is not full-page CER',
          'Rotating glyphs changes recognition independently of reading order',
          'No phone TTS/thermal test; unpaired OCR false positives unscored']}
(out/'vertical_summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
for name,data in [('vertical_per_region_no_text.csv',regions),('vertical_per_photo.csv',per)]:
    with (out/name).open('w',newline='',encoding='utf-8-sig') as f:
        writer=csv.DictWriter(f,fieldnames=list(data[0]));writer.writeheader();writer.writerows(data)
print('SIMPLE_VERTICAL_TRUE_SMALL_OCR_COMPLETE',json.dumps(result,ensure_ascii=False),flush=True)
