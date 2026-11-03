#!/usr/bin/env python3
"""Ephemeral visual-only audit for fixed public RCTW 1400..1411 samples.
No OCR inference, no APK, no TTS, no source image committed to repository.
"""
import argparse, hashlib, io, json
from pathlib import Path
from urllib.parse import urlparse
import requests
from PIL import Image, ImageDraw

BASE='https://datasets-server.huggingface.co/rows'
IDS=list(range(1400,1412))
COLORS={'raw':'#ef4f44','gt':'#458ef9','merged':'#27b780'}

def outline(draw, quad, color, scale=1, width=3, label=None):
    pts=[(round(quad[i]*scale),round(quad[i+1]*scale)) for i in (0,2,4,6)]
    draw.line(pts+[pts[0]],fill=color,width=width,joint='curve')
    if label:
        x,y=pts[0]
        draw.rectangle([x,y-18,x+8*len(label)+8,y],fill='#171717')
        draw.text((x+3,y-16),label,fill=color)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--geometry',required=True)
    ap.add_argument('--out',required=True)
    a=ap.parse_args()
    out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    evidence=json.loads(Path(a.geometry).read_text())
    assert [z['photo'] for z in evidence]==[f'RCTW-{i}' for i in IDS]
    session=requests.Session()
    j=session.get(BASE,params={'dataset':'Yesianrohn/OCR-Data','config':'default',
                                'split':'RCTW','offset':1400,'length':12},timeout=90)
    j.raise_for_status();rows=j.json()['rows']
    assert [r['row_idx'] for r in rows]==IDS
    report=[]
    for sample,row in zip(evidence,rows):
        img_url=(row['row'].get('image') or {}).get('src','')
        p=urlparse(img_url)
        if p.scheme!='https' or not (p.hostname or '').endswith('huggingface.co'):
            raise RuntimeError('Unexpected public dataset image host')
        resp=session.get(img_url,timeout=80);resp.raise_for_status()
        if len(resp.content)>18000000:raise RuntimeError('Unexpected image size')
        original=Image.open(io.BytesIO(resp.content)).convert('RGB')
        w,h=original.size
        assert (w,h)==(sample['image_width'],sample['image_height']),sample['photo']
        scale=min(1,1250/max(w,h))
        preview=original.resize((round(w*scale),round(h*scale)))
        d=ImageDraw.Draw(preview)
        for k,q in enumerate(sample['gt_quads']):
            outline(d,q,COLORS['gt'],scale,2,f'G{k}')
        for k,r in enumerate(sample['raw_boxes']):
            outline(d,r['quad'],COLORS['raw'],scale,3,f'R{k}')
        for r in sample['planner_rows']:
            if r['merged']:
                outline(d,r['quad'],COLORS['merged'],scale,4,
                        'M'+','.join(map(str,r['source_ids'])))
        preview.save(out/(sample['photo']+'-overlay.jpg'),quality=85)
        report.append(dict(photo=sample['photo'],image_sha256=hashlib.sha256(resp.content).hexdigest(),
                           dimensions=[w,h],source_url=img_url,raw=len(sample['raw_boxes']),
                           candidates=len(sample['planner_rows']),
                           merged=sum(r['merged'] for r in sample['planner_rows'])))
        print('VISUAL_SOURCE',sample['photo'],w,h,report[-1]['image_sha256'],flush=True)
        if sample['photo']=='RCTW-1406':
            roi=(190,355,500,495)
            detail=original.crop(roi).resize(((roi[2]-roi[0])*3,(roi[3]-roi[1])*3))
            dd=ImageDraw.Draw(detail)
            for k,q in enumerate(sample['gt_quads']):
                local=[(q[i]-roi[0])*3 if i%2==0 else (q[i]-roi[1])*3 for i in range(8)]
                outline(dd,local,COLORS['gt'],1,3,f'G{k}')
            for k,r in enumerate(sample['raw_boxes']):
                q=r['quad']
                local=[(q[i]-roi[0])*3 if i%2==0 else (q[i]-roi[1])*3 for i in range(8)]
                outline(dd,local,COLORS['raw'],1,3,f'R{k}')
            for r in sample['planner_rows']:
                if r['merged']:
                    q=r['quad']
                    local=[(q[i]-roi[0])*3 if i%2==0 else (q[i]-roi[1])*3 for i in range(8)]
                    outline(dd,local,COLORS['merged'],1,5,'MERGED')
            detail.save(out/'RCTW-1406-merged-detail.png')
    (out/'manifest.json').write_text(json.dumps(report,indent=2,ensure_ascii=False))
    print('VISUAL_AUDIT_RENDERED',len(report),'photos; not strict row GT',flush=True)

if __name__=='__main__':main()
