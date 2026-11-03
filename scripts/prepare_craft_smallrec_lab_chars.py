#!/usr/bin/env python3
"""Reproducibly prepare exact PP-OCRv6 Small REC labels for future PHONE LAB.

Do NOT package an APK here. Verify matching pinned rec.onnx and inference.yml,
reconstruct Python evaluator's CTC character list, exercise real ONNX output
class count, and emit compact UTF-8 JSON array consumed by Java Small REC.
"""
import argparse,hashlib,json
from pathlib import Path
import numpy as np
import onnxruntime as ort
import yaml

MODEL_SHA="5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
YML_SHA="ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--rec",required=True)
    p.add_argument("--yml",required=True)
    p.add_argument("--out",required=True)
    a=p.parse_args()
    assert sha(a.rec)==MODEL_SHA,"REC model differs from tested Small REC"
    assert sha(a.yml)==YML_SHA,"REC label configuration differs"
    chars=list(yaml.safe_load(Path(a.yml).read_text())["PostProcess"]["character_dict"])
    if chars[-1]!=" ":chars.append(" ")
    assert chars and all(isinstance(x,str) and bool(x) for x in chars)
    opts=ort.SessionOptions();opts.intra_op_num_threads=2
    sess=ort.InferenceSession(a.rec,sess_options=opts,providers=["CPUExecutionProvider"])
    inputInfo=sess.get_inputs()
    assert len(inputInfo)==1,inputInfo
    inputName=inputInfo[0].name
    sample=np.zeros((1,3,48,160),dtype=np.float32)
    raw=sess.run(None,{inputName:sample})[0]
    assert raw.ndim==3 and raw.shape[0]==1 and raw.shape[1]>0,raw.shape
    assert raw.shape[2]==len(chars)+1,(
        f"Unexpected Small CTC output class count {raw.shape[2]} "
        f"versus {len(chars)} dict entries + blank")
    path=Path(a.out)
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(chars,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
    print("SMALL_REC_ANDROID_CHAR_ASSET_VERIFIED",json.dumps(dict(
        ctc_output_shape=list(raw.shape),characters=len(chars),
        dictionary_sha256=sha(path),
        model_sha256=MODEL_SHA,
        inference_yml_sha256=YML_SHA,
        no_android_apk_generated=True),ensure_ascii=False),flush=True)

if __name__=="__main__":main()
