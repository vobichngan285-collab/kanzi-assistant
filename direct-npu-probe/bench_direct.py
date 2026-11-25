"""Synthetic OCR A/B: report true answer, not thinking text."""
import json,os,pathlib,re,subprocess,sys,time
from PIL import Image,ImageDraw,ImageFont
root=pathlib.Path("probe-models")
out=pathlib.Path("direct-report");out.mkdir(exist_ok=True)
cli=pathlib.Path(sys.argv[1]).resolve()
font=ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",52)
samples=[("a",["BLUE STAR","4827"]),("b",["蓝星向导","药品 5931"]),("c",["营业时间","09:30 - 18:00"])]
for name,lines in samples:
    im=Image.new("RGB",(448,448),"white")
    d=ImageDraw.Draw(im)
    for i,line in enumerate(lines):d.text((224,128+i*90),line,font=font,fill="black",anchor="mm")
    im.save(out/(name+".png"))
def norm(s):return re.sub(r"\s+","",s)
def cer(a,b):
    prev=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        cur=[i]
        for j,y in enumerate(b,1):cur.append(min(cur[-1]+1,prev[j]+1,prev[j-1]+(x!=y)))
        prev=cur
    return prev[-1]/max(1,len(a))
def metric(stderr,name):
    m=re.findall(r"^"+name+r"=(\d+)\s*$",stderr,re.M)
    return int(m[0]) if len(m)==1 else None
rows=[]
for name,lines in samples:
    # Alternate order to reduce systematic thermal/order effects; new process each time.
    for mode in (("on","off") if name!="b" else ("off","on")):
        prompt="<__media__>\n请逐字抄写图片中所有可见文字和数字。不要解释。"
        # Actual marker from the engine is checked by prepare before running.
        cmd=[str(cli),"-m",str(root/"MiniCPM-V-4_7-1B-Q4_K_M.gguf"),"--mmproj",str(root/"mmproj-model-f16.gguf"),"--image",str(out/(name+".png")),"-p",prompt,"-t","6","-ngl","0","--no-mmproj-offload","-c","4096","-n","128","--temp","0","--seed","42"]
        started=time.monotonic()
        run=subprocess.run(cmd,env={**os.environ,"LAB_THINKING":mode},capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=600)
        raw=run.stdout.strip()
        # The open mode has a prefilled opening tag, so the emitted text can
        # begin with thought content. Require its closing tag before scoring.
        if "</think>" in raw:
            answer=raw.rsplit("</think>",1)[1].strip()
        elif mode=="on" or "<think>" in raw:
            answer=""
        else:answer=raw
        row=dict(mode=mode,sample=name,exit_code=run.returncode,vision_ms=metric(run.stderr,"LAB_VISION_ENCODE_MS"),prefill_ms=metric(run.stderr,"LAB_PREFILL_MS"),prefill_generate_ms=metric(run.stderr,"LAB_PREFILL_GENERATE_MS"),tokens_including_eog=metric(run.stderr,"LAB_GENERATED_TOKENS"),host_total_s=round(time.monotonic()-started,3),answer=answer,cer=cer(norm("".join(lines)),norm(answer)),think_tag_generated="<think>" in raw or "</think>" in raw)
        rows.append(row)
        (out/f"{name}-{mode}.txt").write_text(run.stdout+"\n"+run.stderr)
        (out/"results.json").write_text(json.dumps(rows,ensure_ascii=False,indent=2))
        print(json.dumps(row,ensure_ascii=False),flush=True)
assert len(rows)==6 and all(r["exit_code"]==0 and all(r[k] is not None for k in ("vision_ms","prefill_ms","prefill_generate_ms","tokens_including_eog")) for r in rows)
