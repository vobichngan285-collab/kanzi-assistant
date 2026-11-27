"""Smoke test both historical ONNX assets, not a quality benchmark."""
from pathlib import Path
import json, hashlib, time
import numpy as np
import onnxruntime as ort
root=Path("openocr-lab/app/src/main/assets")
classes=len((root/"chars.txt").read_text(encoding="utf-8").splitlines())+2
report={}
for name in ("rep","svtr"):
    path=root/(name+".onnx")
    opts=ort.SessionOptions()
    opts.intra_op_num_threads=4
    session=ort.InferenceSession(str(path),sess_options=opts,providers=["CPUExecutionProvider"])
    inp=session.get_inputs()[0]
    runs=[]
    for n,w in ((1,320),(6,320),(1,759),(6,939)):
        x=np.zeros((n,3,48,w),dtype=np.float32)
        t=time.perf_counter()
        y=session.run(None,{inp.name:x})[0]
        assert y.ndim==3 and y.shape[0]==n and y.shape[2]==classes and np.isfinite(y).all(),(name,y.shape)
        runs.append(dict(input=list(x.shape),output=list(y.shape),ms=round((time.perf_counter()-t)*1000,3)))
    report[name]=dict(sha256=hashlib.sha256(path.read_bytes()).hexdigest(),bytes=path.stat().st_size,input=inp.shape,classes=classes,runs=runs)
    del session
Path("rec-arena-validation.json").write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
