#!/usr/bin/env python3
"""Inspect the EXACT ONNX/YAML assets bundled into the Android APK.

Runs in GitHub Actions after the models are downloaded. Reports shape,
CTC class/dictionary compatibility and model-specific preprocessing metadata.
A successful build is NOT a Chinese OCR accuracy test.
"""
import argparse
import hashlib
from pathlib import Path
import sys

import numpy as np
import onnxruntime as ort
import yaml

MODELS = [
    ("PP-OCRv6 Small", "models"),
    ("PP-OCRv6 Tiny", "models/ppocrv6_tiny"),
    ("PP-OCRv5 Mobile", "models/ppocrv5_mobile"),
]


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def spec_summary(name, entry):
    return f"{name} name={entry.name} type={entry.type} shape={entry.shape}"


def onnx_session(path):
    return ort.InferenceSession(str(path), providers=["CPUExecutionProvider"],
                                sess_options=ort.SessionOptions())


def verify_one(root, name, relative):
    base = root / relative
    det = base / "det/inference.onnx"
    rec = base / "rec/inference.onnx"
    cfg_path = base / "rec/inference.yml"
    for file in (det, rec, cfg_path):
        if not file.is_file() or file.stat().st_size == 0:
            raise ValueError(f"{name}: missing model file: {file}")
    cfg = yaml.safe_load(cfg_path.read_text(encoding="utf-8"))
    post = cfg.get("PostProcess", {})
    chars = post.get("character_dict")
    if not isinstance(chars, list) or not chars:
        raise ValueError(f"{name}: PostProcess.character_dict is not a nonempty list")
    if not all(isinstance(x, str) and x for x in chars):
        raise ValueError(f"{name}: dictionary contains non-string/empty entries")
    # PaddleOCR v3.7.0 Android ModelConfig.parse unconditionally appends
    # a trailing space if it isn't already the last character.
    sdk_chars = len(chars) + int(chars[-1] != " ")
    expected_classes = sdk_chars + 1  # CTC blank at index zero.
    print(f"MODEL {name}")
    print(f"  det_sha256={sha256(det)} rec_sha256={sha256(rec)} cfg_sha256={sha256(cfg_path)}")
    print(f"  yaml_PostProcess_name={post.get('name')} yaml_use_space_char={post.get('use_space_char')}")
    print(f"  raw_chars={len(chars)} last_char={chars[-1]!r} sdk_chars={sdk_chars} sdk_expected_classes={expected_classes}")
    print(f"  dictionary_first_8={chars[:8]!r} dictionary_last_8={chars[-8:]!r}")
    print(f"  recognition_preprocess_ops={cfg.get('PreProcess', {}).get('transform_ops', [])!r}"[:2200])
    for model_name, path in (("det", det), ("rec", rec)):
        session = onnx_session(path)
        for inp in session.get_inputs():
            print("  "+model_name+"_input "+spec_summary("", inp))
        for out in session.get_outputs():
            print("  "+model_name+"_output "+spec_summary("", out))
        if model_name == "rec":
            inputs = session.get_inputs()
            if len(inputs) != 1:
                raise ValueError(f"{name}: expected exactly one recognition input, got {len(inputs)}")
            meta = inputs[0]
            shape = list(meta.shape)
            if len(shape) != 4 or meta.type != "tensor(float)":
                raise ValueError(f"{name}: SDK expects NCHW Float32 recognition tensor: {meta.type} {shape}")
            for width in (320, 608):
                candidate = [1, 3, 48, width]
                incompatible = [idx for idx, (declared, value) in enumerate(zip(shape, candidate))
                                if isinstance(declared, int) and declared > 0 and declared != value]
                if incompatible:
                    print(f"  runtime_width={width} SKIP static input dimensions incompatible: {shape}")
                    continue
                tensor = np.ones(candidate, dtype=np.float32)
                outputs = session.run(None, {meta.name: tensor})
                dims = [list(out.shape) for out in outputs]
                print(f"  runtime_width={width} recognition_output_shapes={dims}")
                if not outputs or outputs[0].ndim != 3 or outputs[0].shape[0] != 1:
                    raise ValueError(f"{name}: Android SDK CTC expects [N,T,C], observed {dims}")
                actual_classes = outputs[0].shape[-1]
                print(f"  runtime_width={width} actual_classes={actual_classes} SDK_expected_classes={expected_classes}")
                if actual_classes != expected_classes:
                    raise ValueError(f"{name}: CTC class/dictionary mismatch: actual={actual_classes} expected={expected_classes} "
                                     f"(raw YAML chars={len(chars)}, Android appends trailing space={chars[-1] != ' '})")
    print(f"PASS model metadata and SDK CTC mapping: {name}")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("asset_root")
    args = ap.parse_args()
    root = Path(args.asset_root)
    print("Android SDK version: PaddleOCR v3.7.0; RecPreprocessor height=48, RGB and /127.5-1; CTC blank=0")
    ok = True
    for name, path in MODELS:
        try:
            verify_one(root, name, path)
        except Exception as error:
            ok = False
            print(f"FAIL {name}: {error}", file=sys.stderr, flush=True)
    print("METADATA_VERIFICATION="+("PASS" if ok else "FAIL"))
    if not ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
