#!/usr/bin/env python3
"""Independent text region detectors only: CnLite Chinese DBNet, FAST Tiny, CRAFT.
Never run recognition, merge after recognition, or any content guessing.
Scores use the same previously frozen 37 text-area boxes on the same 47 photos.
"""
import collections,csv,hashlib,json,math,statistics,sys,time
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort,pyclipper
from PIL import Image

BASE=Path('/tmp/kanzi-det-input');OUT=Path('/tmp/kanzi-independent-det-results')
OUT.mkdir(parents=True,exist_ok=True);(OUT/'overlays').mkdir(exist_ok=True)
WEIGHTS=Path('/tmp/kanzi-independent-weights')
GT=json.loads(Path('scripts/x200_det_frozen_37_gt.json').read_text(encoding='utf-8'))
manifest=json.loads((BASE/'out/manifest.json').read_text())
assert len(GT)==37 and len(manifest)==47
by=collections.defaultdict(list)
for i,(file,coords) in enumerate(GT): by[file].append((i,coords))

opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
opts.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
fast_path=WEIGHTS/'fast_tiny.onnx';craft_path=WEIGHTS/'craft.onnx';cn_path=WEIGHTS/'cnlite.onnx'
expected_fast='2886777913e837ea3a5ab79f7fb9fd5db5897fc6560f21d16a4f54c64fc0b357'
assert hashlib.sha256(fast_path.read_bytes()).hexdigest()==expected_fast,'FAST weights changed'
fast=ort.InferenceSession(str(fast_path),sess_options=opts,providers=['CPUExecutionProvider'])
craft=ort.InferenceSession(str(craft_path),sess_options=opts,providers=['CPUExecutionProvider'])
# This is the official CN-specific project's independent detector implementation,
# not a rewrapped or re-exported PP-OCR model.
sys.path.insert(0,'third_party/chineseocr_lite')
from dbnet.dbnet_infer import DBNET
cnlite=DBNET(str(cn_path))
info={k:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
      for k,p in [('FAST-Tiny',fast_path),('CRAFT',craft_path),('ChineseOCR-Lite',cn_path)]}

def barea(a):return max(0,a[2]-a[0])*max(0,a[3]-a[1])
def metrics(g,b):
 iwidth=max(0,min(g[2],b[2])-max(g[0],b[0]))
 iheight=max(0,min(g[3],b[3])-max(g[1],b[1]))
 inter=iwidth*iheight;ga=max(1,barea(g));ba=max(1,barea(b))
 return iwidth/max(1,g[2]-g[0]),iheight/max(1,g[3]-g[1]),inter/ga,inter/max(1,ga+ba-inter)
def box(points):
 points=np.asarray(points,dtype=np.float32).reshape(4,2)
 return {'quad':points,'bbox':(float(points[:,0].min()),float(points[:,1].min()),
                               float(points[:,0].max()),float(points[:,1].max()))}
def fast_detect(image):
 h,w=image.shape[:2];dst=1024
 ratio=min(dst/w,dst/h);nw=max(1,round(w*ratio));nh=max(1,round(h*ratio))
 work=cv2.resize(image,(nw,nh),interpolation=cv2.INTER_LINEAR)
 px=(dst-nw)//2;py=(dst-nh)//2
 canvas=np.zeros((dst,dst,3),np.uint8);canvas[py:py+nh,px:px+nw]=work
 tensor=((canvas.astype(np.float32)/255.-np.array([.798,.785,.772],np.float32))/
         np.array([.264,.2749,.287],np.float32)).transpose(2,0,1)[None]
 arr=fast.run(None,{fast.get_inputs()[0].name:np.ascontiguousarray(tensor)})[0]
 # OnnxTR FAST returns logits; sigmoid and its documented 0.1/0.1 thresholds.
 if arr.ndim!=4: raise RuntimeError(f'FAST unexpected output {arr.shape}')
 if arr.shape[-1]==1: logits=arr[0,:,:,0]
 elif arr.shape[1]==1: logits=arr[0,0,:,:]
 else: raise RuntimeError(f'FAST output channels {arr.shape}')
 proba=1.0/(1.0+np.exp(-np.clip(logits,-30,30)))
 mask=cv2.morphologyEx((proba>=.10).astype(np.uint8),cv2.MORPH_OPEN,np.ones((3,3),np.uint8))
 contours,_=cv2.findContours(mask,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
 mh,mw=proba.shape
 ans=[]
 for cnt in contours:
  if cnt.shape[0]<3:continue
  q=cnt[:,0,:];lo=q.min(axis=0);hi=q.max(axis=0)
  if np.any(hi-lo<2):continue
  x,y,cw,ch=cv2.boundingRect(cnt)
  score=float(proba[y:y+ch,x:x+cw].mean())
  if score<.1:continue
  corners=np.array([[x,y],[x,y+ch],[x+cw,y+ch],[x+cw,y]],np.int32)
  area=abs(cv2.contourArea(corners));per=cv2.arcLength(corners,True)
  if per<1:continue
  off=pyclipper.PyclipperOffset()
  off.AddPath(corners.tolist(),pyclipper.JT_ROUND,pyclipper.ET_CLOSEDPOLYGON)
  polys=off.Execute(area*1.5/per)
  if not polys:continue
  bx,by,bw,bh=cv2.boundingRect(np.asarray(max(polys,key=len),np.int32))
  # Map detector heatmap -> padded model input -> original camera image.
  x1=np.clip((bx/mw*dst-px)/ratio,0,w)
  y1=np.clip((by/mh*dst-py)/ratio,0,h)
  x2=np.clip(((bx+bw)/mw*dst-px)/ratio,0,w)
  y2=np.clip(((by+bh)/mh*dst-py)/ratio,0,h)
  if x2-x1<3 or y2-y1<3:continue
  ans.append(box([[x1,y1],[x2,y1],[x2,y2],[x1,y2]]))
 return ans
def craft_detect(image):
 h,w=image.shape[:2]
 scale=min(1.,960/max(h,w))
 nw=max(32,int(math.ceil((w*scale)/32)*32))
 nh=max(32,int(math.ceil((h*scale)/32)*32))
 work=cv2.resize(image,(nw,nh),interpolation=cv2.INTER_LINEAR)
 mean=np.array([.485,.456,.406],np.float32)
 std=np.array([.229,.224,.225],np.float32)
 tensor=np.ascontiguousarray(((work.astype(np.float32)/255.-mean)/std).transpose(2,0,1)[None])
 raw=craft.run(None,{craft.get_inputs()[0].name:tensor})[0]
 if raw.ndim!=4 or raw.shape[-1]!=2:
  raise RuntimeError(f'CRAFT heatmap shape mismatch {raw.shape}')
 text=raw[0,:,:,0];links=raw[0,:,:,1]
 low=.4;high=.7;link_threshold=.4
 merged=np.clip((text>low).astype(np.uint8)+(links>link_threshold).astype(np.uint8),0,1)
 labels_count,labels,stats,_=cv2.connectedComponentsWithStats(merged,4)
 ans=[];H,W=text.shape
 for k in range(1,labels_count):
  area=stats[k,cv2.CC_STAT_AREA]
  if area<10:continue
  if text[labels==k].max()<high:continue
  seg=np.zeros((H,W),np.uint8)
  seg[labels==k]=255
  seg[np.logical_and(links>link_threshold,text<=low)]=0
  x=stats[k,cv2.CC_STAT_LEFT];y=stats[k,cv2.CC_STAT_TOP]
  sw=stats[k,cv2.CC_STAT_WIDTH];sh=stats[k,cv2.CC_STAT_HEIGHT]
  niter=int(math.sqrt(area*min(sw,sh)/(max(1,sw*sh)))*2)
  x0=max(0,x-niter);y0=max(0,y-niter)
  x1=min(W,x+sw+niter+1);y1=min(H,y+sh+niter+1)
  patch=seg[y0:y1,x0:x1]
  patch=cv2.dilate(patch,np.ones((1+niter,1+niter),np.uint8))
  seg[y0:y1,x0:x1]=patch
  ys,xs=np.where(seg>0)
  if len(xs)<4:continue
  rect=cv2.minAreaRect(np.stack([xs,ys],axis=1).astype(np.float32))
  q=cv2.boxPoints(rect).astype(np.float32)
  q[:,0]*=w/W;q[:,1]*=h/H
  ans.append(box(q))
 return ans
def cnlite_detect(image):
 # Repository's own ONNX pre/post processor; only DET, no recognizer.
 cv2bgr=cv2.cvtColor(image,cv2.COLOR_RGB2BGR)
 quads,scores=cnlite.process(cv2bgr,640)
 ans=[]
 for q in quads:
  q=np.asarray(q,dtype=np.float32).reshape(4,2)
  if barea(box(q)['bbox'])<10:continue
  ans.append(box(q))
 return ans
detectors={'FAST-Tiny':fast_detect,'CRAFT':craft_detect,'ChineseOCR-Lite':cnlite_detect}
render={'hospital_03','supermarket_02','storefront_02','restaurant_menu_12','storefront_04'}
def output_image(image,regs,gt,name,key):
 h,w=image.shape[:2];ratio=min(1,900/max(w,h))
 dst=cv2.resize(cv2.cvtColor(image,cv2.COLOR_RGB2BGR),(round(w*ratio),round(h*ratio)))
 for r in regs:
  cv2.polylines(dst,[(r['quad']*ratio).astype('int32')],True,(50,240,70),2)
 for _,g in gt:
  a,b,c,d=[round(t*s*ratio) for t,s in zip(g,[w,h,w,h])]
  cv2.rectangle(dst,(a,b),(c,d),(15,45,240),2)
 cv2.putText(dst,f'{name}  {key}  {len(regs)} boxes',(8,25),cv2.FONT_HERSHEY_SIMPLEX,.6,(255,255,255),2)
 cv2.imwrite(str(OUT/'overlays'/f'{key}_{name}.jpg'),dst,[cv2.IMWRITE_JPEG_QUALITY,85])
def savecsv(file,rows):
 with (OUT/file).open('w',encoding='utf-8-sig',newline='') as f:
  wr=csv.DictWriter(f,fieldnames=list(rows[0]));wr.writeheader();wr.writerows(rows)
per=[];detailed=[];boxesdump=[]
for j,m in enumerate(manifest):
 key=m['id'];img=np.asarray(Image.open(BASE/m['local_file']).convert('RGB'))
 h,w=img.shape[:2]
 order=list(detectors);order=order[j%len(order):]+order[:j%len(order)]
 for name in order:
  ts=time.perf_counter();regions=detectors[name](img);ms=(time.perf_counter()-ts)*1000
  hit_count=whole_count=0
  for index,g in by[key]:
   target=[v*s for v,s in zip(g,[w,h,w,h])]
   overlap=[metrics(target,v['bbox']) for v in regions]
   hit=any(x>=.25 and y>=.25 and coverage>=.12 for x,y,coverage,iou in overlap)
   whole=any(x>=.8 and y>=.6 and iou>=.4 for x,y,coverage,iou in overlap)
   count=sum(coverage>=.08 and y>=.25 for x,y,coverage,iou in overlap)
   detailed.append({'model':name,'image':key,'GT_number':index,'hit':int(hit),
                    'complete_one_box':int(whole),'fragment_count':count,
                    'max_iou':round(max((e[-1] for e in overlap),default=0),4)})
   hit_count+=hit;whole_count+=whole
  per.append({'model':name,'image':key,'category':m['category'],
              'total_boxes':len(regions),'GT_total':len(by[key]),
              'GT_hit':hit_count,'GT_complete':whole_count,'total_ms_linux':round(ms,2)})
  if key in render:output_image(img,regions,by[key],name,key)
  for reg in regions:
   boxesdump.append({'model':name,'image':key,'coords':json.dumps(reg['bbox'])})
  print('INDEPENDENT_DET_ACTUAL',j+1,'/',len(manifest),key,name,'boxes',len(regions),
        'hit',hit_count,'complete',whole_count,'total_ms',round(ms,1),flush=True)
savecsv('37_GT_detailed.csv',detailed);savecsv('47_photos.csv',per);savecsv('all_detector_boxes.csv',boxesdump)
result={}
for name in detectors:
 t=[v for v in per if v['model']==name];g=[v for v in detailed if v['model']==name]
 assert len(t)==47 and len(g)==37,(name,len(t),len(g))
 tm=[z['total_ms_linux'] for z in t]
 result[name]={**info[name],
 'complete_one_box_37':sum(v['complete_one_box'] for v in g),
 'hit_37':sum(v['hit'] for v in g),
 'missed_37':sum(not v['hit'] for v in g),
 'fragmented_GT_37':sum(v['fragment_count']>=2 for v in g),
 'all_boxes_47':sum(v['total_boxes'] for v in t),
 'median_ms_linux':round(statistics.median(tm),2),
 'p90_ms_linux':round(float(np.percentile(tm,90)),2)}
ranking=sorted(result,key=lambda k:(-result[k]['complete_one_box_37'],-result[k]['hit_37'],result[k]['median_ms_linux']))
data={'real_test_completed':True,'rank_accuracy_first':ranking,'models':result,
 'prior_Paddle_Small_same_47_photos_reference':{'whole_37':32,'hit_37':37,'note':'prior job not same CPU runner; only compare geometric accuracy'},
 'scoring':'single box >=80% target width, >=60% target height and IoU>=0.4',
 'caveats':['frozen coarse annotations on only 15/47 public scenes, not all text is annotated',
 'CRAFT 960 max input, FAST 1024 padded, CN-Lite 640 short side: not input resolution controlled',
 'FAST is trained primarily for word polygons; word boxes may not span full lines',
 'Linux CPU timing is not vivo phone latency/heat; no REC or TTS',
 'CnLite is non-Paddle independently trained DB architecture but still DB-family; FAST and CRAFT are different architectures',
 'CnLite external GPL-2.0 project license requires separate assessment before app redistribution']}
(OUT/'summary.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
lines=['# 非 Paddle 专项文字检测 AI：实际找字画框对照','',
 '仅测 FAST-Tiny、CRAFT、ChineseOCR-Lite 三套独立权重，使用同一47张公开实景照片及之前固定的37处目标，不做识字。','',
 '| 非Paddle文字检测模型 | 完整单框/37 | 检出/37 | 模型MB | Linux纯检测中位ms | 47图全部框数 |',
 '|---|---:|---:|---:|---:|---:|']
for k in ranking:
 q=result[k];lines.append(f'| {k} | {q["complete_one_box_37"]} | {q["hit_37"]} | {q["bytes"]/1e6:.1f} | {q["median_ms_linux"]} | {q["all_boxes_47"]} |')
lines+=['','前一轮Paddle Small (同37处目标) 完整单框32/37、检测到37/37；该基准来自先前独立运行，不能直接比较CPU耗时。',
 '判定：单个真实检测框覆盖目标至少80%宽度、60%高度且IoU≥0.40。全图文字没有全部标注，不计算误检精确率。',
 '绿色是模型真实文字框，红色是原有人工标注。不同模型使用其合理原生输入尺寸，非同像素公平性。',
 '全部原图来自Wikimedia Commons，私人真机照片未上传。模型权重也未打包到结果ZIP。',
 '']
(OUT/'文字检测_非百度三模型实际结果.md').write_text('\n'.join(lines),encoding='utf-8')
# Composite user-facing picture from the same four actual model frames.
files=['hospital_03','supermarket_02','storefront_02','restaurant_menu_12']
from PIL import Image as PIL
canvas=PIL.new('RGB',(610*3,470*4),(242,243,245))
for a,key in enumerate(files):
 for b,name in enumerate(detectors):
  picture=PIL.open(OUT/'overlays'/f'{key}_{name}.jpg').convert('RGB')
  picture.thumbnail((610,470),PIL.Resampling.LANCZOS)
  canvas.paste(picture,(b*610+(610-picture.width)//2,a*470+(470-picture.height)//2))
canvas.save(OUT/'三个独立AI同图真实画框对照.jpg',quality=89)
print('THREE_INDEPENDENT_DETECTORS_ACTUAL_COMPLETE',json.dumps(result,ensure_ascii=False),flush=True)
