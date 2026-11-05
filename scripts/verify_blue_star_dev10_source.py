#!/usr/bin/env python3
"""Development 1.0 compilation/source guards for the lean Blue Star Guide."""
from pathlib import Path
a=Path('app/src/main/java/com/vobichngan/kanzi/MainActivity.java').read_text(encoding='utf-8')
o=Path('app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt').read_text(encoding='utf-8')
g=Path('app/build.gradle').read_text(encoding='utf-8')
manifest=Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
sdk=Path('third_party/PaddleOCR/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr/PaddleOCR.kt').read_text(encoding='utf-8')
eng=Path('third_party/PaddleOCR/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr/engine/OCREngine.kt').read_text(encoding='utf-8')
assert 'Blue Star Guide · 实验版 1.0.7' in a and '蓝星向导 实验版' in manifest
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert 'getEngines()' in a and 'initializeSelectedTextToSpeech()' in a
assert 'new TextToSpeech(this,onInit,requested)' in a
assert 'followEngineSpeechRate' in a and 'showSimpleSpeechSpeedDialog()' in a
assert 'private void showSettingsDialog()' in a and 'showAdvancedSettingsDialog()' in a
assert 'clearSpeechQueue(true)' in a and 'FIRST_REAL_TEXT_TTS_EARLY' in a
assert 'appendRestOfRealOcr(' in a
assert 'fun interface OcrEarlyReadCallback' in o and 'onEarly.onEarlyText(text)' in o
assert 'onFirstBatch' in sdk and 'onFirstBatch(ready)' in eng
assert 'val allResults = resultsInReadingOrder.filterNotNull()' in eng
assert 'models/cnlite/det/inference.onnx' in o
assert 'models/rec/inference.onnx' in o or '"$modelPrefix/rec/inference.onnx"' in o
assert 'abiFilters \'arm64-v8a\'' in g
assert 'versionCode 107' in g and "versionName '1.0.7'" in g
assert 'sherpa-onnx' not in g and 'commons-compress' not in g
for bad in ['DownloadableVoiceStore','OfflineVoicePlayback','ppocrv5_mobile','ppocrv6_tiny']:
    assert bad not in a and bad not in o,(bad,'obsolete source ref')
assert Path('app/src/main/res/drawable-nodpi/bluestar_icon.webp').stat().st_size>4000
print('BLUE_STAR_DEV10_SOURCE_GUARDS=PASS')
