"""Reassemble exact screened candidate; compare full-model CPU OCR before promotion."""
import hashlib, json, shutil, struct, time, statistics, subprocess, re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import litert_lm
from litert_lm_builder import litertlm_peek as peek, litertlm_core as core

def sha(path):
    with Path(path).open("rb") as f: return hashlib.file_digest(f,"sha256").hexdigest()
base=Path("gallery.litertlm"); assets=Path("baseline-assets/assets"); target=Path("verified-assets");target.mkdir()
report_dir=Path("e2e-report");report_dir.mkdir(exist_ok=True)
screen=json.loads(Path("screen-report/report.json").read_text())
candidate=Path("screen-candidate/candidate.tflite")
assert screen["screen_pass"] and screen["unique_int8_buffers"]==112
assert screen["matched"]=={"vision_280":112,"vision_560":112}
assert sha(candidate)==screen["candidate_sha"] and candidate.stat().st_size==screen["candidate_bytes"]
assert sha(base)==screen["source_sha"]=="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
assert sha(assets/"encoder.tflite")==screen["baseline_sha"]=="6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92"
p=dict(line.split("=",1) for line in (assets/"model.properties").read_text().splitlines())
assert p["sha256"]=="fb4159987f3cd30aba817ded3b83a04c81a4a09991d88fafee31d1325490c2f8"
assert p["source_sha256"]==sha(base) and int(p["source_size"])==base.stat().st_size
rows=[{k:(p[f"{i}.{k}"] if k=="asset" else int(p[f"{i}.{k}"])) for k in ("source","dest","size","asset")} for i in range(int(p["count"]))]
assert rows[0]["asset"]=="header.bin"
def assemble(path,rr,root):
    with base.open("rb") as source,path.open("wb") as out:
        for row in rr:
            out.seek(row["dest"]);n=row["size"]
            if row["asset"]:
                f=(root/row["asset"]).open("rb");assert (root/row["asset"]).stat().st_size==n
            else: f=source;f.seek(row["source"])
            try:
                while n:
                    v=f.read(min(n,8*1024*1024));assert v;out.write(v);n-=len(v)
            finally:
                if row["asset"]:f.close()
    assert path.stat().st_size==rr[-1]["dest"]+rr[-1]["size"]
baseline=Path("baseline.litertlm");assemble(baseline,rows,assets);assert sha(baseline)==p["sha256"]
for name in ("adapter.tflite","metadata.bin"):shutil.copyfile(assets/name,target/name)
shutil.copyfile(candidate,target/"encoder.tflite")
head=(assets/"header.bin").read_bytes();prefix=head[:core.HEADER_BEGIN_BYTE_OFFSET];raw=bytearray(head[len(prefix):])
assert len(head)==rows[0]["size"]==struct.unpack_from("<Q",prefix,core.HEADER_END_LOCATION_BYTE_OFFSET)[0]
before=bytes(raw);schema=peek.schema
sec=schema.LiteRTLMMetaData.GetRootAs(raw,0).SectionMetadata()
assert sec.ObjectsLength()==len(rows)-1
new=[dict(row) for row in rows];changed=[]
def align(n):return (n+core.BLOCK_SIZE-1)//core.BLOCK_SIZE*core.BLOCK_SIZE
offset=align(len(head))
for i,row in enumerate(new[1:]):
    old=rows[i+1];obj=sec.Objects(i)
    assert obj.BeginOffset()==old["dest"] and obj.EndOffset()==old["dest"]+old["size"]
    if row["asset"]=="encoder.tflite":row["size"]=candidate.stat().st_size
    row["dest"]=offset
    for slot,prior,value in ((6,old["dest"],offset),(8,old["dest"]+old["size"],offset+row["size"])):
        rel=obj._tab.Offset(slot);assert rel>0
        pos=obj._tab.Pos+rel;assert struct.unpack_from("<Q",before,pos)[0]==prior
        assert all(abs(pos-x)>=8 for x in changed)
        struct.pack_into("<Q",raw,pos,value);changed.append(pos)
    offset=align(offset+row["size"])
reverse=bytearray(raw)
for pos in changed:reverse[pos:pos+8]=before[pos:pos+8]
assert bytes(reverse)==before
sec=schema.LiteRTLMMetaData.GetRootAs(raw,0).SectionMetadata()
for i,row in enumerate(new[1:]):
    assert sec.Objects(i).BeginOffset()==row["dest"] and sec.Objects(i).EndOffset()==row["dest"]+row["size"]
(target/"header.bin").write_bytes(prefix+raw)
quant=Path("quant.litertlm");assemble(quant,new,target)
def sectionhash(f,row):
    f.seek(row["dest"]);h=hashlib.sha256();n=row["size"]
    while n:
        data=f.read(min(n,8*1024*1024));assert data;n-=len(data);h.update(data)
    return h.hexdigest()
verified=0
with baseline.open("rb") as a,quant.open("rb") as z:
    for old,row in zip(rows[1:],new[1:]):
        if row["asset"]=="encoder.tflite":assert sectionhash(z,row)==sha(candidate);continue
        assert row["size"]==old["size"] and sectionhash(a,old)==sectionhash(z,row)
        verified+=1
assert verified==len(rows)-2
p.update(size=str(quant.stat().st_size),sha256=sha(quant))
for i,row in enumerate(new):
    for k,v in row.items():p[f"{i}.{k}"]=str(v)
(target/"model.properties").write_text("\n".join(f"{k}={v}" for k,v in p.items())+"\n")
for path in (baseline,quant):
    with litert_lm.ModelInfo(str(path)) as info:
        assert info.max_vision_token_budget==560 and set(info.vision_signature_selection or [])=={280,560}
fontpath=subprocess.check_output(["fc-match","-f","%{file}","Noto Sans CJK SC"],text=True)
assert Path(fontpath).exists()
cases=[
 ("menu",["商品名称  价格","苹果  12元","牛奶  8元","面包  15元","鸡蛋  26元","大米  49元","合计  110元"],92,194),
 ("notice",["营业时间","上午九点至晚上八点","请在入口处领取号码","取餐编号  7362","联系电话  583901","今日特价  39元"],92,194),
 ("dense",["服务价目表 PRICE LIST","中医推拿一小时  90元","半小时  50元","精油开背50分钟  120元","肠胃调理50分钟  120元","肾部保养40分钟  120元","艾灸养生  120元","刺络放血  120元","经络刮痧  60元","滑罐走罐  60元","拔罐热敷  40元","会员充值  充1000送200","充2000送600"],63,104)]
for name,lines,size,step in cases:
    im=Image.new("RGB",(1512,1512),"white");draw=ImageDraw.Draw(im);font=ImageFont.truetype(fontpath,size)
    for i,line in enumerate(lines):draw.text((70,65+i*step),line,font=font,fill="black")
    im.save(report_dir/(name+".png"))
def norm(x):return "".join(c.lower() for c in x if c.isalnum())
def cer(a,b):
    a,b=norm(a),norm(b);prev=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        cur=[i]
        for j,y in enumerate(b,1):cur.append(min(cur[-1]+1,prev[j]+1,prev[j-1]+(x!=y)))
        prev=cur
    return prev[-1]/max(1,len(a))
def extract(msg):
    if isinstance(msg,dict):
        c=msg.get("content",[])
        if isinstance(c,str):return c
        return "".join(str(x.get("text","")) for x in c if isinstance(x,dict))
    return str(msg)
records=[]
summary={"candidate_sha":sha(candidate),"model_sha":sha(quant),"model_bytes":quant.stat().st_size,"baseline_bytes":baseline.stat().st_size,"unchanged_sections":verified,"records":records,"pass":False}
def save(): (report_dir/"ocr.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2))
save()
# Both use the same stock CPU runtime, image dimensions and prompt. The warm-up is excluded.
for label,path in (("fp32",baseline),("int8",quant)):
    with litert_lm.Engine(str(path.resolve()),backend=litert_lm.Backend.CPU(),vision_backend=litert_lm.Backend.CPU(),max_num_tokens=2048,max_num_images=1) as engine:
        for rep in range(3):
            for name,lines,_,_ in (cases[:1] if rep==0 else cases):
                expected="\n".join(lines)
                with engine.create_conversation() as conv:
                    msg={"role":"user","content":[{"type":"image","path":str((report_dir/(name+".png")).resolve())},{"type":"text","text":"逐行准确抄录图片中的全部文字和数字。只输出原文，不解释，不猜测。"}]}
                    t=time.monotonic();res=conv.send_message(msg,max_output_tokens=384);elapsed=time.monotonic()-t
                    answer=extract(res);row={"label":label,"case":name,"rep":rep,"expected":expected,"answer":answer,"cer":cer(expected,answer),"numbers_exact":re.findall(r"[0-9]+",expected)==re.findall(r"[0-9]+",answer),"seconds":elapsed}
                    records.append(row);save();print(json.dumps(row,ensure_ascii=False),flush=True)
checks=[]
for name,*_ in cases:
    a=[x for x in records if x["label"]=="fp32" and x["case"]==name and x["rep"]>0]
    z=[x for x in records if x["label"]=="int8" and x["case"]==name and x["rep"]>0]
    assert len(a)==len(z)==2
    checks.append(max(x["cer"] for x in z)<=max(x["cer"] for x in a)+.01 and all(x["numbers_exact"] for x in z) and (name=="dense" or max(x["cer"] for x in z)<=.01))
a=[x["seconds"] for x in records if x["label"]=="fp32" and x["rep"]>0];z=[x["seconds"] for x in records if x["label"]=="int8" and x["rep"]>0]
summary.update(fp32_median=statistics.median(a),int8_median=statistics.median(z),accuracy_checks=checks,**{"pass":all(checks) and statistics.median(z)<=.95*statistics.median(a)})
save();print(json.dumps({k:v for k,v in summary.items() if k!="records"}),flush=True)
assert summary["pass"],"full-model OCR or speed gate failed; do not publish"
