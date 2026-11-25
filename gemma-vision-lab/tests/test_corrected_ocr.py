"""Real-image CPU OCR smoke test of exact corrected 560 candidate, no release."""
import json, inspect, shutil, subprocess, sys, time
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import litert_lm
# Reproduce the hash-checked per-channel correction, keeping the candidate local.
p=Path(__file__).with_name("test_scale_correction.py")
exec(compile(p.read_text(),str(p),"exec"))
assert sha(dest)=="6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92"
base=Path("gallery.litertlm")
props=dict(line.split("=",1) for line in (patch/"model.properties").read_text().splitlines())
def assemble(target,corrected):
    with base.open("rb") as f,target.open("wb") as o:
        for i in range(int(props["count"])):
            k=str(i)+"."
            n=int(props[k+"size"]); asset=props[k+"asset"]
            o.seek(int(props[k+"dest"]))
            if asset:
                source=dest if corrected and asset=="encoder.tflite" else patch/asset
                assert source.stat().st_size==n
                with source.open("rb") as v:shutil.copyfileobj(v,o,8*1024*1024)
            else:
                f.seek(int(props[k+"source"]))
                while n:
                    b=f.read(min(n,8*1024*1024));assert b;o.write(b);n-=len(b)
    assert target.stat().st_size==int(props["size"])
    if not corrected:assert sha(target)==props["sha256"]
fontpath=subprocess.check_output(["fc-match","-f","%{file}","Noto Sans CJK SC"],text=True)
assert fontpath and Path(fontpath).exists()
examples=[
    ("menu",["商品名称  价格","苹果  12元","牛奶  8元","面包  15元","鸡蛋  26元","大米  49元","合计  110元"]),
    ("notice",["营业时间","上午九点至晚上八点","请在入口处领取号码","取餐编号  7362","联系电话  583901","今日特价  39元"])
]
for name,lines in examples:
    im=Image.new("RGB",(1512,1512),"white");draw=ImageDraw.Draw(im)
    font=ImageFont.truetype(fontpath,92)
    for i,line in enumerate(lines):draw.text((70,65+i*194),line,font=font,fill="black")
    im.save(out/(name+".png"))
def normalize(s):return "".join(ch for ch in s if ch.isalnum())
def cer(a,b):
    a,b=normalize(a),normalize(b);prev=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        curr=[i]
        for j,y in enumerate(b,1):curr.append(min(curr[-1]+1,prev[j]+1,prev[j-1]+(x!=y)))
        prev=curr
    return prev[-1]/max(1,len(a))
def extract(msg):
    if isinstance(msg,dict):
        content=msg.get("content",[])
        if isinstance(content,str):return content
        return "".join(str(x.get("text","")) for x in content if isinstance(x,dict))
    return str(msg)
print("engine_signature",str(inspect.signature(litert_lm.Engine)),flush=True)
records=[]
for label,budget in (("original",280),("unfixed",560),("corrected",560)):
    target=base if label=="original" else Path(label+".litertlm")
    try:
        if label!="original":assemble(target,label=="corrected")
        with litert_lm.ModelInfo(str(target)) as info:
            print("model_info",label,info.max_vision_token_budget,info.vision_signature_selection,flush=True)
            assert info.max_vision_token_budget==budget
        assert "max_vision_tokens_per_image" in inspect.signature(litert_lm.Engine).parameters,"Cannot explicitly select budget"
        t=time.monotonic()
        with litert_lm.Engine(str(target.resolve()),backend=litert_lm.Backend.CPU(),vision_backend=litert_lm.Backend.CPU(),max_num_tokens=2048,max_vision_tokens_per_image=budget) as engine:
            print("engine_ready",label,round(time.monotonic()-t,3),flush=True)
            for name,lines in examples:
                with engine.create_conversation() as conv:
                    message={"role":"user","content":[{"type":"image","path":str((out/(name+".png")).resolve())},{"type":"text","text":"逐行准确抄录图片中的全部文字和数字。只输出原文，不解释，不猜测。"}]}
                    t=time.monotonic();res=conv.send_message(message);elapsed=time.monotonic()-t
                    content=extract(res)
                    row={"model":label,"budget":budget,"case":name,"expected":"\n".join(lines),"output":content,"cer":cer("".join(lines),content),"seconds":elapsed}
                    records.append(row);print("ocr_result",json.dumps(row,ensure_ascii=False),flush=True)
    except Exception as exc:
        records.append({"model":label,"error":repr(exc)});print("ocr_unavailable",label,repr(exc),flush=True)
    finally:
        if label!="original":target.unlink(missing_ok=True)
    (out/"real_image_ocr_report.json").write_text(json.dumps(records,ensure_ascii=False,indent=2))
assert any(x.get("model")=="corrected" and "cer" in x for x in records),"No corrected OCR result; not a pass"
print("OCR smoke test complete; desktop CPU only, no phone/GPU validation",flush=True)
