#!/usr/bin/env python3
"""Reject incompatible or broken PP-DocLayout-S ONNX exports before APK creation."""
import sys
from pathlib import Path
import numpy as np
import onnxruntime as ort

p = Path(sys.argv[1])
assert p.is_file() and p.stat().st_size > 4_500_000, "layout model absent/truncated"
sess = ort.InferenceSession(str(p),providers=["CPUExecutionProvider"])
inputs = {x.name: x.shape for x in sess.get_inputs()}
assert set(inputs) == {"image","scale_factor"}, inputs
assert inputs["image"][1:] == [3,480,480], inputs
assert inputs["scale_factor"][1:] == [2], inputs
assert isinstance(inputs["image"][0], (int,str)), inputs
outputs = sess.run(None,{
    "image": np.zeros((1,3,480,480),dtype=np.float32),
    "scale_factor": np.array([[1.,1.]],dtype=np.float32)
})
assert len(outputs)==2,(len(outputs),[x.shape for x in outputs])
detections, count = outputs
assert detections.ndim==2 and detections.shape[1]==6,detections.shape
number=int(np.asarray(count).reshape(-1)[0])
assert 0<=number<=detections.shape[0],(number,detections.shape)
assert np.isfinite(detections[:number]).all()
print("PP_DOCLAYOUT_S_ONNX_INFERENCE=PASS")
print("INPUTS",inputs,"DETECTIONS",detections.shape,"VALID",number)
