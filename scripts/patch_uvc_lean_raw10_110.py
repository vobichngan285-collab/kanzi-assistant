#!/usr/bin/env python3
"""Experimental v1.0.10: shrink only the native NV21/OCR callback gate.

Apply AFTER the pinned 1.0.6 RAW20 patch; keep full-resolution native
MJPEG -> TextureView preview unchanged. Patch rejects unexpected upstream code.
No JNI runtime on/off toggles: setFrameCallback() waits on the native capture
mutex, so repeatedly invoking it during scanning risks stalls/deadlocks.
"""
from pathlib import Path

source = Path("third_party/AndroidUSBCamera/libuvc/src/main/jni/UVCCamera/UVCPreview.cpp")
text = source.read_text(encoding="utf-8")
old_gate = "now_ns - mLastOcrRawCallbackNs < 50000000LL"
new_gate = "now_ns - mLastOcrRawCallbackNs < 100000000LL"
old_mark = 'LOGI("UVC_NATIVE_OCR_RAW20_GATE_ENABLED");'
new_mark = 'LOGI("UVC_NATIVE_OCR_RAW10_LEAN_110");'

for old, new in ((old_gate, new_gate), (old_mark, new_mark)):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"REFUSE raw10 patch: anchor {old!r} occurred {count} times")
    text = text.replace(old, new, 1)
source.write_text(text, encoding="utf-8")
print("UVC_NATIVE_OCR_RAW10_LEAN_110=PASS")
print("Native callback rate <= 10Hz; 1080p camera MJPEG/TextureView preview unchanged.")
