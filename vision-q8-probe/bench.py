"""Host smoke and OCR sanity test; never interpret host speed as phone speed."""
import json, pathlib, subprocess, time, re, sys
from PIL import Image, ImageDraw, ImageFont
root=pathlib.Path("probe-models")
out=pathlib.Path("probe-report");out.mkdir(exist_ok=True)
cli=pathlib.Path(sys.argv[1]).resolve()
font="/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
samples=[("a",["BLUE STAR","4827"],"4827"),("b",["蓝星向导","药品 5931"],"5931")]
for name,lines,_ in samples:
    im=Image.new("RGB",(448,448),"white")
    d=ImageDraw.Draw(im)
    f=ImageFont.truetype(font,52)
    for i,line in enumerate(lines):
        d.text((224,128+i*90),line,font=f,fill="black",anchor="mm")
    im.save(out/(name+".png"))
rows=[]
for label,proj in [("f16",root/"mmproj-model-f16.gguf"),("q8",root/"mmproj-model-q8-probe.gguf")]:
    for name,lines,expected in samples:
        start=time.monotonic()
        cmd=[str(cli),"-m",str(root/"MiniCPM-V-4_7-1B-Q4_K_M.gguf"),"--mmproj",str(proj),"--image",str(out/(name+".png")),"-p","请逐字抄写图片中所有可见文字和数字。不要解释。","-t","6","-ngl","0","--no-mmproj-offload","-c","4096","-n","96","--temp","0","--seed","42"]
        run=subprocess.run(cmd,capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=600)
        text=run.stdout+"\n"+run.stderr
        (out/f"{label}-{name}.txt").write_text(text)
        encoded=[int(x) for x in re.findall(r"LAB_VISION_ENCODE_MS=(\d+)",run.stderr)]
        row={"variant":label,"sample":name,"exit_code":run.returncode,"host_total_s":round(time.monotonic()-start,3),"vision_encode_ms":encoded,"expected_numeric_text":expected,"expected_digits_present":expected in run.stdout}
        rows.append(row)
        (out/"host_results.json").write_text(json.dumps(rows,ensure_ascii=False,indent=2))
        print(row,flush=True)
        print("Synthetic OCR stdout:",repr(run.stdout[-2000:]),flush=True)
        if run.returncode!=0 or len(encoded)!=1:
            print("Missing single image timing or inference error; raw log retained",flush=True)
assert len(rows)==4 and all(r["exit_code"]==0 and len(r["vision_encode_ms"])==1 for r in rows), "one or more inference/timing checks failed; inspect raw logs"
# Numeric match is deliberately not a CI gate; bad OCR remains visible in the
# report and prevents claiming quality acceptance. Human must review full text.
