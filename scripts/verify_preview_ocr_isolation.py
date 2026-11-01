#!/usr/bin/env python3
"""Prevent accidental coupling of screen preview transforms and OCR input."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
ui = (root / "app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
ocr = (root / "app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt").read_text()
assert "cameraTextureView.setRotation(previewRotation);" in ui
assert "cameraTextureView.setScaleX(" in ui and "cameraTextureView.setScaleY(" in ui
assert "private volatile int previewRotation = 0;" in ui
assert "ocrRotation" not in ui and "PREF_OCR_ROTATION" not in ui
assert "rotationDegrees" not in ocr and "Core.rotate(" not in ocr and "Matrix" not in ocr
assert re.search(r"recognizer\.diagnoseNv21\(diagnosticFrame, width, height, enhancedOcr, legacyForDiagnostic,", ui)
assert re.search(r"recognizer\.recognizeNv21\(frame, width, height, enhance, legacyDecode,", ui)
assert ocr.count("nv21ToBitmap(data, width, height, legacyNv21Jpeg)") == 2
assert "Imgproc.COLOR_YUV2RGBA_NV21" in ocr
assert "Bitmap.createBitmap(width, height" in ocr
assert "previewRotation" not in ocr
assert "OCR 输入方向：原始摄像头帧（固定 0°）" in ui
print("PASS: preview transforms only affect display; both OCR paths receive raw NV21 orientation")
