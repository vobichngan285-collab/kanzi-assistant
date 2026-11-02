#!/usr/bin/env python3
"""Prevent a working OCR SDK native runtime from being displaced by neural TTS.

#280 Android APK worked: each ONNX Runtime .so had the following exact hash.
#303 APK broke OCR: Gradle pickFirst selected sherpa's incompatible .so.
Every future release must keep the known-good OCR JNI dependency while the
sherpa runtime is renamed to a separate SONAME.
"""
import hashlib
import sys
from zipfile import ZipFile
apk = sys.argv[1]
reference = {
    "arm64-v8a": "67465b2bcf0ad501d389cc2c0f7f04278fb26fcf32e50c16a74deb4472c99706",
    "armeabi-v7a": "9908c373f58d68311f082067e715d0209516f81c8e946119ccd047c164c5b887",
    "x86": "383d22df83cfa4a213b124e3b36ecded52a1d027ce136c2bd9474a77bd848050",
    "x86_64": "682d98a97bd5b3fb87f1f4616fd9dd0afb35c12749856cbf396fc76bc024a60e",
}
with ZipFile(apk) as z:
    names = set(z.namelist())
    for abi, expected in reference.items():
        official = f"lib/{abi}/libonnxruntime.so"
        isolated = f"lib/{abi}/libonnxruntime_sherpa.so"
        if official not in names:
            raise SystemExit(f"Missing PaddleOCR ONNX native runtime: {official}")
        actual = hashlib.sha256(z.read(official)).hexdigest()
        if actual != expected:
            raise SystemExit(f"OCR ONNX runtime was replaced! {abi}: {actual} != {expected}")
        if isolated not in names:
            raise SystemExit(f"Missing isolated neural speech runtime: {isolated}")
        if z.getinfo(isolated).file_size < 2_000_000:
            raise SystemExit(f"Truncated sherpa ONNX runtime: {isolated}")
print("OCR_AND_TTS_NATIVE_RUNTIMES_ISOLATED=PASS")
