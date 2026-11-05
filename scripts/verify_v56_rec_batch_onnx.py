#!/usr/bin/env python3
"""Check that pinned PP-OCRv6 Small REC supports batch=2 with no new text.
Actual vivo latency/heat needs a separate mobile test.
"""
from pathlib import Path
import time
import numpy as np
import onnxruntime as ort

path=Path("third_party/PaddleOCR/deploy/ppocr-android/ppocr-sdk/src/main/assets/models/rec/inference.onnx")
assert path.is_file(),path
opts=ort.SessionOptions()
opts.intra_op_num_threads=2
sess=ort.InferenceSession(str(path),sess_options=opts,providers=["CPUExecutionProvider"])
feed=sess.get_inputs()[0].name
rng=np.random.RandomState(56)
images=rng.uniform(-1,1,(2,3,48,192)).astype(np.float32)

def run(a):
    return sess.run(None,{feed:a})[0]

one0=run(images[0:1])
one1=run(images[1:2])
two=run(images)
assert one0.ndim==3 and two.ndim==3
assert two.shape[0]==2 and one0.shape[0]==1
assert two.shape[1:]==one0.shape[1:]==one1.shape[1:]
assert np.isfinite(two).all()
for i,single in enumerate((one0,one1)):
    # No altered CTC labels/lexicon: identical greedy argmax per timestep.
    if not np.array_equal(two[i].argmax(axis=-1),single[0].argmax(axis=-1)):
        raise AssertionError(f"Batch2 CTC argmax differs for crop #{i}")
    np.testing.assert_allclose(two[i],single[0],rtol=.005,atol=.005)

# Informational Linux CI CPU timings only, not phone performance claims.
for _ in range(2):run(images[0:1]);run(images[1:2]);run(images)
separate=[];paired=[]
for _ in range(3):
    st=time.perf_counter();run(images[0:1]);run(images[1:2]);separate.append((time.perf_counter()-st)*1e3)
    st=time.perf_counter();run(images);paired.append((time.perf_counter()-st)*1e3)
print("SMALL_REC_BATCH2_CTC_PARITY=PASS", "shape=",two.shape,
      "single_2_calls_median_ms=",round(float(np.median(separate)),1),
      "batch2_1_call_median_ms=",round(float(np.median(paired)),1))
