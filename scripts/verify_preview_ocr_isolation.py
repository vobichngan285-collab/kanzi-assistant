#!/usr/bin/env python3
"""Verify camera preview geometry and camera->OCR bitmap are independent paths."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
ui = (root / "app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
ocr = (root / "app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt").read_text()
evidence = (root / "app/src/main/java/com/vobichngan/kanzi/SingleFrameEvidence.kt").read_text()

assert "cameraTextureView.setRotation(previewRotation);" in ui
assert "cameraTextureView.setTransform(usbDisplay);" in ui
assert "private volatile int previewRotation = 0;" in ui
assert "private volatile int usbOcrInputRotationDegrees = -1;" in ui
assert "UsbAutoOcrOrientation.normalizePreference" in ui
assert 'PREF_USB_OCR_INPUT_ROTATION = "usb_ocr_input_rotation"' in ui
assert "Core.rotate(" not in ocr
# The camera's USB OCR angle is an explicit per-capture value, NOT a view transform.
assert "var liveAngle = OcrInputOrientation.normalize(inputRotationDegrees)" in ocr
assert "val manualAngle = OcrInputOrientation.normalize(inputRotationDegrees)" in ocr
assert "AutoPageRotation.candidate" in ocr
assert "AutoPageRotation.accept" in ocr
assert "transform.postRotate(liveAngle.toFloat())" in ocr
assert "previewRotation" not in ocr
assert "UsbPreviewMirror" not in ocr
assert "previewMirror" not in ocr
assert re.search(
    r"recognizer\.diagnoseNv21\(diagnosticFrame, width, height,\s*"
    r"enhancedOcr, legacyForDiagnostic, effectiveDiagnosticAngle,", ui)
assert re.search(
    r"recognizer\.recognizeNv21\(frame, width, height,\s*"
    r"enhance, legacyDecode, effectiveOcrInputAngle,", ui)
assert ocr.count("nv21ToBitmap(data, width, height, legacyNv21Jpeg)") == 2
assert "Imgproc.COLOR_YUV2RGBA_NV21" in ocr
assert "Bitmap.createBitmap(width, height" in ocr
assert "本次实际OCR输入旋转=" in ui
assert "原版A/B输入方向：以本次实际OCR输入角度为基准" in ui
assert "writeBitmap(zip, \"decoded-frame.png\", rawBitmap)" in evidence
assert "writeBitmap(zip, \"actual-ocr-input.png\", ocrBitmap)" in evidence
assert "OcrInputOrientation.normalize(inputRotationDegrees)" in evidence
assert ui.count("@Override protected void onResume()") == 1
assert "InternalCameraController" in ui and "OcrOverlayView" in ui

print("PASS: preview is display-only; USB OCR angle is explicit, source NV21 preserved, and diagnostic input matches live orientation")
