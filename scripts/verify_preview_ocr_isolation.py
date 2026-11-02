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
# A user-selected LIVE TEXT rotation is not a screen preview rotation.
# Long-form/diagnostic OCR still receives the exact unrotated source pixels.
assert "Core.rotate(" not in ocr
assert "val liveAngle = if (fastScanMode) liveTextRotationDegrees else 0" in ocr
assert "transform.postRotate(liveAngle.toFloat())" in ocr
assert "setLiveTextRotationDegrees" in ocr
assert "previewRotation" not in ocr
assert re.search(r"recognizer\.diagnoseNv21\(diagnosticFrame, width, height, enhancedOcr, legacyForDiagnostic,", ui)
assert re.search(r"recognizer\.recognizeNv21\(frame, width, height, enhance, legacyDecode,", ui)
assert ocr.count("nv21ToBitmap(data, width, height, legacyNv21Jpeg)") == 2
assert "Imgproc.COLOR_YUV2RGBA_NV21" in ocr
assert "Bitmap.createBitmap(width, height" in ocr
assert "previewRotation" not in ocr
assert "OCR 输入方向：原始摄像头帧（固定 0°）" in ui
assert ui.count("@Override protected void onResume()") == 1
assert "InternalCameraController" in ui and "OcrOverlayView" in ui
print("PASS: preview stays independent; optional LIVE TEXT OCR correction does not affect book/diagnostic frames")
