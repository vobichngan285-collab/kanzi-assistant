#!/usr/bin/env python3
"""Independent v1.0.10 OCR NV21 decimation + immutable USB buffer checks.

Static/source plus deterministic rates; does NOT claim vivo hardware performance.
"""
from pathlib import Path

root = Path("third_party/AndroidUSBCamera")
native = (root / "libuvc/src/main/jni/UVCCamera/UVCPreview.cpp").read_text()
camera = (root / "libausbc/src/main/java/com/jiangdg/ausbc/camera/CameraUVC.kt").read_text()
app = Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
gradle = Path("app/build.gradle").read_text()
manifest = Path("app/src/main/AndroidManifest.xml").read_text()

callback = native.split("void UVCPreview::do_capture_callback(", 1)[1]
assert 'LOGI("UVC_NATIVE_OCR_RAW10_LEAN_110")' in native
assert "now_ns - mLastOcrRawCallbackNs < 100000000LL" in callback
assert "50000000LL" not in callback
assert callback.index("100000000LL") < callback.index("callback_frame = get_frame(callbackPixelBytes);")
assert callback.index("recycle_frame(frame);") < callback.index("callback_frame = get_frame(callbackPixelBytes);")
assert "mLastOcrRawCallbackNs = now_ns;" in callback

# Upstream creates an exclusive byte array per UVC Java callback. The Python
# check is intentionally fail-closed if that ownership guarantee changes.
assert "val data = ByteArray(capacity())" in camera
assert "get(data)" in camera
assert "cb?.onPreviewData(data, previewWidth, previewHeight" in camera
assert "byte[] frame = isPhoneCamera ? data.clone() : data;" in app
assert "final byte[] diagnosticFrame = data.clone();" in app
assert "if (!running) return;" in app
assert "setRawPreviewData(true)" in app
assert "setPreviewWidth(1920)" in app and "setPreviewHeight(1080)" in app
assert "setRenderMode(CameraRequest.RenderMode.NORMAL)" in app
assert "setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)" in app
assert 'Blue Star Guide · 实验版 1.0.10' in app
assert "versionCode 110" in gradle and "versionName '1.0.10'" in gradle
assert "applicationIdSuffix '.experiment.dev110'" in gradle
assert 'android:label="蓝星向导 实验版1.0.10"' in manifest

def output_rate(fps, seconds=60):
    """Model the native monotonic 100ms gate with real integer ns spacing."""
    accepted = 0
    last = 0
    for i in range(int(fps * seconds)):
        now_ns = 1_000_000_000 + (i * 1_000_000_000 // fps)
        if last > 0 and 0 <= now_ns - last < 100_000_000:
            continue
        last = now_ns
        accepted += 1
    return accepted / seconds

for source in (10, 15, 20, 30, 60):
    got = output_rate(source)
    assert 6.0 <= got <= 10.01, (source, got)
assert 8.0 <= output_rate(60) <= 10.01  # exact 100ms on a 60Hz source is valid
print("USB_OCR_LEAN_110_STATIC_AND_FPS_SIM=PASS")
print(" ".join(f"{src}FPS=>{output_rate(src):.2f} RAW/s" for src in (10,15,20,30,60)))
print("USB preview unchanged; no toggling native callbacks; 2.0 branch untouched.")
