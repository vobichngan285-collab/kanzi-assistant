#!/usr/bin/env python3
"""47 identical public images, 37 frozen rectangles; four DET models; no REC/TTS."""
import csv,json,hashlib,collections,statistics,time,os
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort
from PIL import Image
import ocr_box_value_deep_dive as sdk
ROOT=Path('/tmp/kanzi-det-input');OUT=Path('/tmp/kanzi-det-output')
MODELS=Path('/tmp/kanzi-det-models')
OUT.mkdir(exist_ok=True,parents=True);(OUT/'overlays').mkdir(exist_ok=True)
GT=json.loads(Path('scripts/x200_det_frozen_37_gt.json').read_text())
manifest=json.loads((ROOT/'out/manifest.json').read_text())
assert len(GT)==37 and len(manifest)==47
targets=collections.defaultdict(list)
for index,(image,bbox) in enumerate(GT):targets[image].append((index,bbox))
names=['small','tiny','v5mobile','east']
thresholds={'small':(.20,.45,1.4),'tiny':(.20,.4,1.4),'v5mobile':(.30,.60,1.5)}
o=ort.SessionOptions();o.intra_op_num_threads=2;o.inter_op_num_threads=1
models={};meta={}
for n in names:
 file=MODELS/(n+('.pb' if n=='east' else '.onnx'))
 assert file.is_file(),file
 digest=hashlib.sha256(file.read_bytes()).hexdigest()
 if n=='small': assert digest=='d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e'
 if n=='east': assert digest=='9b486f3c3eee77b4c8cc91a83892c37026cca7d29b79bf3b93772ccd2db58454'
 meta[n]={'sha256':digest,'bytes':file.stat().st_size}
 if n=='east':
  m=cv2.dnn.TextDetectionModel_EAST(str(file))
  m.setInputParams(1.0,(640,640),(123.68,116.78,103.94),True,False)
  m.setConfidenceThreshold(.5);m.setNMSThreshold(.4)
 else:m=ort.InferenceSession(str(file),sess_options=o,providers=['CPUExecutionProvider'])
 models[n]=m
def rect_area(a):return max(0,a[2]-a[0])*max(0,a[3]-a[1])
def intersection(g,p):
 x=max(0,min(g[2],p[2])-max(g[0],p[0]))
 y=max(0,min(g[3],p[3])-max(g[1],p[1]))
 A=x*y;ga=max(1,rect_area(g));pa=max(1,rect_area(p))
 return x/max(1,g[2]-g[0]),y/max(1,g[3]-g[1]),A/ga,A/max(1,ga+pa-A)
def detect(n,rgb):
 h,w=rgb.shape[:2];t=time.perf_counter();m=models[n]
 if n=='east':
  corners,scores=m.detect(cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR))
  boxes=[]
  if corners is not None:
   for c in corners:
    q=np.asarray(c,dtype=np.float32).reshape(4,2)
    boxes.append({'quad':q,'bbox':(float(q[:,0].min()),float(q[:,1].min()),float(q[:,0].max()),float(q[:,1].max()))})
 else:
  x=sdk.det_preprocess(rgb)
  p=m.run(None,{m.get_inputs()[0].name:x})[0]
  assert p.ndim==4 and p.shape[1]==1,(n,p.shape)
  th,boxth,unclip=thresholds[n]
  sdk.DET_BITMAP_THRESH=th;sdk.DET_UNCLIP=unclip;sdk.THRESHOLDS=[boxth]
  sdk.DET_MAX_CANDIDATES=3000;sdk.DET_USE_DILATION=False
  boxes=[b for b in sdk.detections(p[0,0],h,w) if b['score']>=boxth]
 return boxes,round((time.perf_counter()-t)*1000,2)
def save_csv(name,records):
 with open(OUT/name,'w',newline='',encoding='utf-8-sig') as f:
  writer=csv.DictWriter(f,fieldnames=list(records[0]))
  writer.writeheader();writer.writerows(records)
def overlay(rgb,boxes,original_targets,image,model):
 h,w=rgb.shape[:2];r=min(1.,1150/max(h,w))
 small=cv2.resize(cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR),(int(w*r),int(h*r)))
 for b in boxes:
  pts=(np.asarray(b['quad'])*r).astype('int32').reshape(4,2)
  cv2.polylines(small,[pts],True,(65,235,45),2)
 for index,coords in original_targets:
  a,b,c,d=[int(v*k*r) for v,k in zip(coords,(w,h,w,h))]
  cv2.rectangle(small,(a,b),(c,d),(5,30,255),2)
 cv2.putText(small,f'{model} / {image} / {len(boxes)} boxes',(8,28),
             cv2.FONT_HERSHEY_SIMPLEX,.78,(0,0,0),5,cv2.LINE_AA)
 cv2.putText(small,f'{model} / {image} / {len(boxes)} boxes',(8,28),
             cv2.FONT_HERSHEY_SIMPLEX,.78,(255,255,255),2,cv2.LINE_AA)
 cv2.imwrite(str(OUT/'overlays'/f'{image}_{model}.jpg'),small,[cv2.IMWRITE_JPEG_QUALITY,86])
per=[];detail=[];bboxes=[]
draw={'hospital_03','hospital_08','supermarket_02','supermarket_11','storefront_02','storefront_04','restaurant_menu_10','restaurant_menu_12'}
for i,item in enumerate(manifest):
 key=item['id'];rgb=np.asarray(Image.open(ROOT/item['local_file']).convert('RGB'))
 h,w=rgb.shape[:2]
 for n in names[i%4:]+names[:i%4]:
  boxes,ms=detect(n,rgb);hit=full=0
  for index,bnorm in targets[key]:
   gt=[v*k for v,k in zip(bnorm,(w,h,w,h))]
   scores=[intersection(gt,x['bbox']) for x in boxes]
   yes=any(x>=.25 and y>=.25 and area>=.12 for x,y,area,iou in scores)
   one=any(x>=.80 and y>=.60 and iou>=.40 for x,y,area,iou in scores)
   fragment=sum(area>=.08 and y>=.25 for x,y,area,iou in scores)
   detail.append({'model':n,'image':key,'gt_index':index,'has_region':int(yes),
                  'one_complete_box':int(one),'boxes_intersecting':fragment,
                  'best_iou':round(max((s[-1] for s in scores),default=0),4)})
   hit+=yes;full+=one
  per.append({'model':n,'image':key,'category':item['category'],'boxes':len(boxes),
              'gt_total':len(targets[key]),'hits':hit,'complete':full,'elapsed_ms_linux':ms})
  if key in draw: overlay(rgb,boxes,targets[key],key,n)
  for j,b in enumerate(boxes):
   bboxes.append({'image':key,'model':n,'index':j,'bbox':json.dumps(b['bbox'])})
  print('ACTUAL_DET',i+1,'/47',key,n,'det',len(boxes),'target',hit,'complete',full,'ms',ms,flush=True)
save_csv('per_GT_37.csv',detail);save_csv('per_image_47.csv',per);save_csv('all_boxes.csv',bboxes)
summary={}
for n in names:
 d=[x for x in detail if x['model']==n]
 p=[x for x in per if x['model']==n]
 assert len(d)==37 and len(p)==47
 ts=[x['elapsed_ms_linux'] for x in p]
 summary[n]={**meta[n],'complete_37':sum(x['one_complete_box'] for x in d),
  'located_37':sum(x['has_region'] for x in d),
  'fragmented_37':sum(x['boxes_intersecting']>=2 for x in d),
  'missed_37':sum(not x['has_region'] for x in d),
  'all_boxes_47':sum(x['boxes'] for x in p),
  'median_ms_linux':round(statistics.median(ts),2),
  'p90_ms_linux':round(float(np.percentile(ts,90)),2)}
ranking=sorted(names,key=lambda n:(-summary[n]['complete_37'],-summary[n]['located_37'],summary[n]['median_ms_linux']))
report={'status':'complete','ranking':ranking,'models':summary,
 'scope':'Same 47 real Commons photos; 37 GT text boxes on 15 images; detection only',
 'one_box':'single detected box covers >=80% GT width and >=60% height with IoU>=.40',
 'hit':'any detected box covers >=25% GT width, >=25% height, >=12% GT area',
 'limit':['37 annotations do not cover every text region in 47 photos: no false positive precision claims',
 'human annotated GT rectangles have approximate margins','no REC, no speech',
 'GitHub Linux CPU/2 ORT threads, not vivo X200 Pro timing or heating',
 'Paddle models use 1600 max side, EAST uses 640 input; architecture/input unfairness remains']}
(OUT/'summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
lines=['# 文字检测器独立实验：先比完整行框，再比速度','',
 '| 模型 | 单个完整框/37 | 找到目标/37 | 碎框覆盖/37 | 47图总框数 | Linux检测中位ms | 权重MB |',
 '|---|---:|---:|---:|---:|---:|---:|']
for n in ranking:
 s=summary[n]
 lines.append(f'| {n} | **{s["complete_37"]}** | {s["located_37"]} | {s["fragmented_37"]} | {s["all_boxes_47"]} | {s["median_ms_linux"]} | {s["bytes"]/1e6:.1f} |')
lines+=['','37处人工标注来自47张照片中的15张，不能当作全图文字识别准确率，也不能用总框数推断误检率。',
 '完整框要求单个DET框覆盖目标宽度≥80%、高度≥60%，与GT矩形IoU≥0.40。',
 'Paddle three 1600px max side; EAST fixed 640px; each uses original model-specific thresholds.',
 '只有检测（DET），没有识字或朗读。时延是GitHub Linux CPU，不是vivo真机。','',
 '绿色=模型真实检测框，红色=实验前标注的人工位置，见overlays/与同图四模型划框对照.jpg。']
(OUT/'报告_检测器文字框优先.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
samples=['hospital_03','supermarket_02','storefront_02','restaurant_menu_12']
W,H=620,480;canvas=Image.new('RGB',(4*W,4*H),(245,245,245))
for ri,image in enumerate(samples):
 for ci,n in enumerate(names):
  t=Image.open(OUT/'overlays'/f'{image}_{n}.jpg').convert('RGB')
  t.thumbnail((W,H),Image.Resampling.LANCZOS)
  canvas.paste(t,(ci*W+(W-t.width)//2,ri*H+(H-t.height)//2))
canvas.save(OUT/'同图四模型划框对照.jpg',quality=88)
print('REAL_DET_4_MODELS_EXPERIMENT_COMPLETE',json.dumps(summary,ensure_ascii=False),flush=True)
