#!/usr/bin/env python3
"""Live OCR speech regression: geometry changes cannot silently eat valid OCR."""
from pathlib import Path

source = Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text(encoding="utf-8")
ocr_callback = source.split("recognizer.recognizeNv21(frame, width, height, enhance, legacyDecode, found -> {",1)[1].split("}, error ->",1)[0]
assert "found != null && recognizedEpoch == readingEpoch.get()" in ocr_callback
assert "onRecognized(found.getText(), oneShot," in ocr_callback
assert "recognizedScene == realtimeScene.generation()" not in ocr_callback, "scene changed during slow OCR; recognized text was discarded"
assert "sceneGeneration != realtimeScene.generation()" not in source, "luma noise silently suppressed successful OCR"
assert 'if (!running || !canSpeak())' in source
assert 'private boolean canSpeak()' in source
assert 'return !voice.isSystem() && voiceStore.isInstalled(voice);' in source
assert "OCR recognized text but speech skipped" in source
assert "speakSystemItem(currentSpeech)" in source, "system voice fallback must stay wired"
assert "tts.speak(item.text, TextToSpeech.QUEUE_FLUSH" in source
assert "offlineVoicePlayback.speak(" in source
assert "finishQueuedUtterance(utteranceId)" in source
assert "recognitionTone.startTone" in source and "catch (RuntimeException error)" in source
assert "showSpeechFailure" in source
assert "实时朗读状态：" in source
print("LIVE_SPEECH_DELIVERY=PASS")
