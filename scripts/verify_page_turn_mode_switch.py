#!/usr/bin/env python3
"""Read-only checks that mode separation and switch-resume wiring remain intact."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
fast = (root / "app/src/main/java/com/vobichngan/kanzi/FastScanFrameGate.java").read_text()
rule = (root / "app/src/main/java/com/vobichngan/kanzi/ReadingModeRules.java").read_text()

# FAST and LONG can never share the speech-block or the frame fingerprint.
assert "ReadingModeRules.shouldDeferOcrForSpeech(longReadingMode, speechPageActive)" in main
assert "fastScanFrameGate.accept(data, width, height, now)" in main
assert "frameChangeGate.accept(data, width, height, now, samePageLocked)" in main
assert "ReadingModeRules.shouldInterruptSpeech(longReadingMode, force)" in main
assert "ReadingModeRules.isFastDuplicate(" in main
assert "firstFastSpeechNeedsFlush = !longReadingMode;" in main
assert "TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD" in main
assert "if (longReadingMode) lastProcess = 0L;" in main

# Auto/manual switching resets old OCR fingerprints and stale callbacks.
assert "resetRecognitionWindowForSwitch()" in main
assert "readingEpoch.incrementAndGet(); // drop previous mode's asynchronous OCR result" in main
assert "targetModelGeneration != modelLoadGeneration" in main
assert "resumeAfterModelReady = running || resumeAfterModelReady;" in main
assert "running = resumed;" in main
assert "lastProcess = 0L;" in main
assert "fastScanFrameGate.reset();" in main
assert "recognitionIntervalMs = Math.max(1L, Math.min(10000L," in main

# Keep OCR single-slot and never initiate OCR from speech callback or enqueue frame buffers.
assert "busy.compareAndSet(false, true)" in main
assert "byte[] frame = data.clone();" in main
assert "LongReading" not in fast
assert "UNCHANGED_RETRY_MS" in fast
assert "shouldDeferOcrForSpeech" in rule
assert "shouldInterruptSpeech" in rule

# Avoid modifying SDK and current recognition semantics in this branch.
assert "shouldRetryWithEnhancement" in (root / "app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt").read_text()

print("FAST_SCAN_PREEMPTS_LONG_MODE_WAITS=PASS")
print("MODE_SWITCH_AUTO_RESUME_STATE=PASS")
print("CAMERA_SINGLE_OCR_SLOT=PASS")
