"""Package the exact encoder already validated by run 45718390348. No re-export."""
from pathlib import Path
import json, shutil
p=Path(__file__).with_name("test_scale_correction.py")
exec(compile(p.read_text().split("# Verify exact deterministic comparison",1)[0],str(p),"exec"))
expected="6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92"
assert sha(dest)==expected
report=json.loads(Path("verified-ocr/diagnostics/real_image_ocr_report.json").read_text())
rows=[r for r in report if r.get("model")=="corrected"]
assert len(rows)==2 and {r["case"] for r in rows}=={"menu","notice"}
assert all(r["cer"]==0 and r["max_budget"]==560 for r in rows)
print("verified prior end-to-end OCR evidence",rows,flush=True)
props_path=patch/"model.properties"
props=dict(line.split("=",1) for line in props_path.read_text().splitlines())
base=Path("gallery.litertlm");assert sha(base)==props["source_sha256"]
assert dest.stat().st_size==(patch/"encoder.tflite").stat().st_size
shutil.copyfile(dest,patch/"encoder.tflite")
target=Path("corrected560.litertlm")
with base.open("rb") as f,target.open("wb") as o:
    for i in range(int(props["count"])):
        k=str(i)+"."
        n=int(props[k+"size"]);asset=props[k+"asset"]
        o.seek(int(props[k+"dest"]))
        if asset:
            path=patch/asset;assert path.stat().st_size==n
            with path.open("rb") as v:shutil.copyfileobj(v,o,8*1024*1024)
        else:
            f.seek(int(props[k+"source"]))
            while n:
                b=f.read(min(n,8*1024*1024));assert b;o.write(b);n-=len(b)
assert target.stat().st_size==int(props["size"])
props["sha256"]=sha(target)
props_path.write_text("\n".join(k+"="+v for k,v in props.items())+"\n")
import litert_lm
with litert_lm.ModelInfo(str(target)) as info:
    assert info.max_vision_token_budget==560 and set(info.vision_signature_selection or [])=={280,560}
print("corrected_package",target.stat().st_size,props["sha256"],"encoder",expected,flush=True)
# Re-verify every unchanged section copied byte-for-byte and every embedded asset.
with base.open("rb") as f,target.open("rb") as o:
    for i in range(int(props["count"])):
        k=str(i)+".";n=int(props[k+"size"]);asset=props[k+"asset"];o.seek(int(props[k+"dest"]))
        if asset: inp=(patch/asset).open("rb")
        else: f.seek(int(props[k+"source"]));inp=f
        try:
            while n:
                count=min(n,8*1024*1024);x=inp.read(count);y=o.read(count);assert len(x)==count and x==y,(i,asset);n-=count
        finally:
            if asset:inp.close()
target.unlink()
assert sum(p.stat().st_size for p in patch.iterdir())<1_000_000_000
print("validated all reconstructed sections and exact tested vision encoder",flush=True)
