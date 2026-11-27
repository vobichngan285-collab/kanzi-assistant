"""Check the Android preprocessor against the exact pinned model implementation."""
import sys, json, hashlib, importlib.util, base64
from pathlib import Path
import numpy as np
import torch
root=Path("v2-upstream")
artifact=Path("v2-export")
report=json.loads((artifact/"validation.json").read_text())
assert report["revision"]=="22e72eb3725290dabdc99c95ef9a72796d71e742"
assert report["classes"]==19685
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(artifact/"openocrv2_ctc.onnx")==report["onnx_sha256"]
for f in ("modeling_openocrv2_rec_ctc.py","rec_ctc/config.json","README.md"):
    assert sha(root/f)==report["upstream_sha256"][f],f
cfg=json.loads((root/"rec_ctc/config.json").read_text())
assert json.loads((artifact/"characters.json").read_text())==cfg["characters"]
spec=importlib.util.spec_from_file_location("v2official",root/"modeling_openocrv2_rec_ctc.py")
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
rng=np.random.default_rng(123)
cases=[]
# Nonvertical crops (shared geometry rotates before the recognizer); includes fractional aspect ratios and long lines.
for w,h in [(19,17),(24,12),(25,10),(35,10),(120,30),(91,18),(130,7),(300,11),(640,32),(51,49),(256,150)]:
    bgr=rng.integers(0,256,(h,w,3),dtype=np.uint8)
    batches=list(m.recognition_batches([bgr],cfg["preprocess"],6))
    assert len(batches)==1
    ids,x=batches[0];x=np.asarray(x)
    assert x.shape[0]==1 and x.shape[1]==3
    _,_,oh,ow=x.shape
    expected=np.rint((x[0]+1)*127.5).clip(0,255).astype(np.uint8)
    cases.append("\t".join(map(str,[w,h,ow,oh,base64.b64encode(bgr.tobytes()).decode(),base64.b64encode(expected.tobytes()).decode()])))
out=Path("openocr-lab/app/src/test/resources/v2-preprocess.tsv")
out.parent.mkdir(parents=True,exist_ok=True);out.write_text("\n".join(cases)+"\n")
print("Generated",len(cases),"official preprocessing fixtures")
