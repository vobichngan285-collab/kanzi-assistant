#!/usr/bin/env python3
"""Protect one-tap speech setup and no-repeat reading from accidental regressions."""
from pathlib import Path
main = Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
store = Path("app/src/main/java/com/vobichngan/kanzi/DownloadableVoiceStore.java").read_text()
synth = Path("app/src/main/java/com/vobichngan/kanzi/OfflineVoiceSynthesizer.kt").read_text()
playback = Path("app/src/main/java/com/vobichngan/kanzi/OfflineVoicePlayback.java").read_text()
camera = Path("app/src/main/java/com/vobichngan/kanzi/InternalCameraController.java").read_text()
gradle = Path("app/build.gradle").read_text()
workflow = Path(".github/workflows/android.yml").read_text()

# Duplicate checkbox is gone, but the important home mode-switch persists.
assert "queueCheck" not in main
assert "默认使用长文阅读（读完再识别）；快速扫读会自动打断旧内容" not in main
assert "readingModeButton.setOnClickListener(v -> setLongReadingMode(!longReadingMode))" in main
assert ".putBoolean(PREF_LONG_READING, enableLong)" in main
assert "private void setLongReadingMode(boolean enableLong)" in main

# An actual voice-row click (not a separate download button/web link)
# installs the model and switches the selected offline engine.
assert "entry.setOnClickListener" in main
assert "voiceStore.choose(voice, new DownloadableVoiceStore.Listener()" in main
assert "未安装 · 点击自动下载安装" in main
assert "voiceStore.selectedVoice()" in main
assert "offlineVoicePlayback.speak(" in main
assert "voiceStore.forceSystem()" in main
assert "offlineVoicePlayback.stop()" in main
assert "offlineVoicePlayback.shutdown()" in main
assert "downloadBtn" not in main and "下载语音包按钮" not in main

# All three voice families are real sherpa native models, not fake URLs.
for item in ["kokoro-multi-lang-v1_1.tar.bz2",
             "vits-piper-zh_CN-chaowen-medium.tar.bz2",
             "vits-melo-tts-zh_en.tar.bz2"]:
    assert item in store
for pack in ["kokoro", "piper", "melo"]:
    assert '"' + pack + '"' in synth
assert "OfflineTts(config = buildConfig(voice))" in synth
assert "generateWithConfig(text, cfg)" in synth
assert "AudioTrack" in playback and "generation.incrementAndGet()" in playback

# Do not accidentally bundle enormous model archives with the APK.
assert "releases/download/tts-models/" in store
assert "MAX_UNPACKED" in store and "getCanonicalPath" in store
assert "isSymbolicLink()" in store and "isLink()" in store
assert "getSelectedVoice()" not in main
assert "sherpa-onnx-2.0.1.aar" in gradle
assert "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7" in workflow

# Higher detail camera source for dense small print (with 720p fallback).
assert "1920.0 * 1080.0" in camera
assert "CONTROL_AF_MODE_CONTINUOUS_PICTURE" in camera
print("VOICE_ONE_TAP_DOWNLOAD_AND_SETTINGS=PASS")
