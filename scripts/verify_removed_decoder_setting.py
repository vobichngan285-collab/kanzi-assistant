#!/usr/bin/env python3
"""Prevent the retired 0.7.0 NV21->JPEG comparison setting from returning."""
from pathlib import Path

source = Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text(encoding="utf-8")
for obsolete in [
    "使用 0.7.0 的 NV21→JPEG 图像解码方式",
    "legacyDecodeCheck",
    "legacyNv21Jpeg",
    "PREF_LEGACY_NV21",
    "legacyNote",
]:
    assert obsolete not in source, f"Obsolete UI setting still present: {obsolete}"
assert "final boolean legacyForDiagnostic = false;" in source
assert "final boolean legacyDecode = false;" in source
assert '图像解码：OpenCV NV21 方式' in source
assert 'recognizer.diagnoseNv21(diagnosticFrame, width, height, enhancedOcr, legacyForDiagnostic,' in source
assert 'recognizer.recognizeNv21(frame, width, height, enhance, legacyDecode,' in source
print("RETIRED_DECODER_SETTING=PASS")
