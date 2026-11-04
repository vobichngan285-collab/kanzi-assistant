#!/usr/bin/env python3
"""Fetch independent, real-life Chinese scene photos via Wikimedia Commons API.
No OCR and no quality-based image exclusion. Fixed manifest first, before benchmark.
All uploaded photos remain in the ephemeral private Actions artifact, not committed.
"""
from __future__ import annotations
import io, json, os, random, time
from pathlib import Path
from collections import defaultdict
import requests
from PIL import Image,ImageDraw,ImageFont,ImageOps

ROOT=Path("/tmp/kanzi-commons-scene")
PHOTOS=ROOT/"photos";OUT=ROOT/"out"
PHOTOS.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
URL="https://commons.wikimedia.org/w/api.php"
SESSION=requests.Session()
SESSION.headers.update({"User-Agent":"kanzi-assistant-scene-OCR-benchmark/0.1 (https://github.com/vobichngan285-collab/kanzi-assistant; Wikimedia Commons attribution preserved)","Accept":"application/json"})
CATS={
 "hospital":[("Category:Emergency_departments_in_Taiwan",7),("Category:Hospital_name_signs_in_Asia",5)],
 "supermarket":[("Category:Price_tags_in_Taiwan",8),("Category:Supermarket_shelves_in_Taiwan",4)],
 "storefront":[("Category:Shop_signs_in_Taipei",9),("Category:Shop_signs_in_Taiwan",3)],
 "restaurant_menu":[("Category:Food_menus_in_Chinese",12)]
}
def get(params):
 for attempt in range(5):
  try:
   res=SESSION.get(URL,params=params,timeout=55)
   res.raise_for_status()
   obj=res.json()
   if "error" in obj:raise RuntimeError(obj["error"])
   return obj
  except Exception as e:
   print("COMMONS_GET_RETRY",attempt,str(e)[:120],flush=True)
   if attempt==4:raise
   time.sleep(2*(attempt+1))
def list_category(cat):
 all_=[];cursor=None
 for _ in range(5):
  params={"action":"query","format":"json","list":"categorymembers",
     "cmtitle":cat,"cmtype":"file","cmlimit":"500"}
  if cursor:params["cmcontinue"]=cursor
  obj=get(params)
  all_.extend([r["title"] for r in obj.get("query",{}).get("categorymembers",[])])
  cursor=obj.get("continue",{}).get("cmcontinue")
  if not cursor:break
 return sorted({t for t in all_ if t.lower().endswith((".jpg",".jpeg",".png"))})
def pick_spread(names,k,filter_fn=lambda s:True):
 names=[n for n in names if filter_fn(n)]
 if not names:return []
 if len(names)<=k:return names
 ids=sorted({min(len(names)-1,round((i+.5)*len(names)/k)) for i in range(k)})
 return [names[i] for i in ids][:k]
def info(titles):
 obj=get({"action":"query","format":"json","prop":"imageinfo","titles":"|".join(titles),
       "iiprop":"url|size|mime|extmetadata","iiurlwidth":"1600","iiurlheight":"1600"})
 result={}
 for pg in obj.get("query",{}).get("pages",{}).values():
  if not pg.get("imageinfo"):continue
  result[pg["title"]]=pg["imageinfo"][0]
 return result
def download(url,path):
 for attempt in range(4):
  try:
   x=SESSION.get(url,timeout=60)
   x.raise_for_status()
   if len(x.content)<2000:raise ValueError("image too tiny")
   img=Image.open(io.BytesIO(x.content)).convert("RGB")
   if min(img.size)<180:raise ValueError("low-res")
   # All images are real photographs, no image-generation substitutes.
   img.save(path,quality=90,optimize=True)
   return img
  except Exception as e:
   print("IMAGE_RETRY",attempt,str(e)[:110],flush=True)
   if attempt==3:return None
   time.sleep(2*(attempt+1))
def thumb_board(category,entries):
 tile=(350,275);cols=4
 board=Image.new("RGB",(cols*tile[0],((len(entries)+3)//4)*tile[1]),"white")
 draw=ImageDraw.Draw(board)
 for ix,r in enumerate(entries):
  im=Image.open(ROOT/r["local_file"]).convert("RGB")
  thumb=ImageOps.contain(im,(330,224))
  x=(ix%4)*350+int((350-thumb.width)/2)
  y=(ix//4)*275+8
  board.paste(thumb,(x,y))
  d=(ix//4)*275+232
  draw.text(((ix%4)*350+7,d),r["id"]+" "+r["title"][-38:],fill="black")
 board.save(OUT/("contact_"+category+".jpg"),quality=83)
rows=[]
for cat,groups in CATS.items():
 got=[]
 for source,k in groups:
  titles=list_category(source)
  print("CATEGORY_DISCOVERY",source,"available",len(titles),flush=True)
  if cat=="hospital" and source.endswith("_Asia"):
   filter_fn=lambda t: any(x in t.lower() for x in ("taipei","taiwan","cathay","sijhih","taoyuan","taichung","chinese","university children's"))
  else:filter_fn=lambda _:True
  chosen=pick_spread(titles,k,filter_fn)
  got.extend([(source,t) for t in chosen])
  print("SELECTED_BEFORE_OCR",source,len(chosen),flush=True)
 assert len(got)>=8,(cat,"Not enough files")
 category_rows=[]
 for j,(source,title) in enumerate(got):
  ii=info([title]).get(title)
  if not ii:print("MISSING_INFO",title,flush=True);continue
  url=ii.get("thumburl",ii.get("url"))
  name=cat+"_"+str(j+1).zfill(2)
  dest=PHOTOS/(name+".jpg")
  img=download(url,dest)
  if img is None:continue
  meta=ii.get("extmetadata") or {}
  rec={
    "id":name,"category":cat,"title":title,"source_category":source,
    "commons_file_page":"https://commons.wikimedia.org/wiki/"+title.replace(" ","_"),
    "url":ii.get("descriptionurl"),"src_thumb":url,
    "license":meta.get("LicenseShortName",{}).get("value","unknown"),
    "artist_html":meta.get("Artist",{}).get("value",""),
    "width":img.width,"height":img.height,
    "local_file":"photos/"+dest.name}
  category_rows.append(rec);rows.append(rec)
  print("DOWNLOADED_REAL_SCENE",name,img.size,title,flush=True)
 if category_rows:thumb_board(cat,category_rows)
 print("CATEGORY_READY",cat,len(category_rows),flush=True)
(OUT/"manifest.json").write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding="utf-8")
assert all(sum(x["category"]==cat for x in rows)>=7 for cat in CATS),("insufficient source categories",[(cat,sum(x["category"]==cat for x in rows)) for cat in CATS])
assert len(rows)>=32,("Not enough diverse scenes",len(rows))
print("FOUR_REAL_SCENE_DOWNLOAD_COMPLETE",len(rows),{c:sum(x["category"]==c for x in rows) for c in CATS},flush=True)
