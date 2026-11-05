#!/usr/bin/env python3
"""Fail closed if the actual v56 live-TTS callback still blocks new OCR."""
from pathlib import Path
main=Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text(encoding="utf-8")
profile=Path("app/src/main/java/com/vobichngan/kanzi/OcrPerformanceProfile.java").read_text(encoding="utf-8")
engine=Path("app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt").read_text(encoding="utf-8")
sdk=Path("third_party/PaddleOCR/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr/engine/OCREngine.kt").read_text(encoding="utf-8")
assert "APP_VERSION = \"Blue Star Guide · 实验版 1.0.7\"" in main
assert "新文字立即打断" in main
assert "if (live && speechPageActive) return;" not in main
assert "liveSpeechGuard.accept(spokenNormalized)" in main
assert "clearSpeechQueue(true);" in main
assert "lastQuickScanGeneration = recognizedScene;" in main
assert "lastQuickScanGeneration = realtimeScene.generation();" not in main
assert "REC_FAST2" in profile
assert "return REC_FAST2.id;" in profile
assert "2, 2, 0" in profile
assert "resultsInReadingOrder[boxIdx] = OCRResult" in sdk
assert "val allResults = resultsInReadingOrder.filterNotNull()" in sdk
assert "val recOrder = if (batchSize == 1)" in sdk
assert "recognitionEngine.recognize(batchCrops)" in sdk
assert "recPreprocessMs" in engine and "recInferenceMs" in engine and "recPostprocessMs" in engine
# The detection model and original recognizer must not be altered.
assert 'detModelAssetPath = if (isCnLite) "models/cnlite/det/inference.onnx"' in engine
assert 'recModelAssetPath = "$modelPrefix/rec/inference.onnx"' in engine
print("V56_REALTIME_PREEMPTION_AND_REC_PERF_SOURCE_AUDIT=PASS")
