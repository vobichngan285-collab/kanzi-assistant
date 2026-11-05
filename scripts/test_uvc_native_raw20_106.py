#!/usr/bin/env python3
"""Independent static + numerical smoke check of the experimental native split-rate gate."""
from pathlib import Path

root = Path("third_party/AndroidUSBCamera/libuvc/src/main/jni/UVCCamera")
source = (root / "UVCPreview.cpp").read_text(encoding="utf-8")
header = (root / "UVCPreview.h").read_text(encoding="utf-8")
camera = Path("third_party/AndroidUSBCamera/libausbc/src/main/java/com/jiangdg/ausbc/camera/CameraUVC.kt").read_text(encoding="utf-8")
app = Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text(encoding="utf-8")

assert "int64_t mLastOcrRawCallbackNs;" in header
assert "mLastOcrRawCallbackNs(0)" in source
assert "clock_gettime(CLOCK_MONOTONIC, &callback_clock)" in source
assert "50000000LL" in source
callback = source[source.index("void UVCPreview::do_capture_callback("):]
guard = callback.index("if (mFrameCallbackObj && mFrameCallbackFunc)")
reuse = callback.index("recycle_frame(frame);")
convert = callback.index("callback_frame = get_frame(callbackPixelBytes);")
assert guard < reuse < convert, "Decimate BEFORE native NV21 allocation/conversion"
assert callback.index("return;", reuse) < convert, "Skipped raw frame must be recycled"
assert "mLastOcrRawCallbackNs = now_ns;" in callback
assert "mUvcCamera?.setFrameCallback(frameCallBack, UVCCamera.PIXEL_FORMAT_YUV420SP)" in camera
assert "private const val MAX_FPS = 61" in camera
assert 'setRawPreviewData(true)' in app
assert '原生OCR取帧≤20FPS' in app
assert "recognizer.recognizeNv21(" in app
assert "mCameraSurfaceTexture?.updateTexImage()" in Path(
    "third_party/AndroidUSBCamera/libausbc/src/main/java/com/jiangdg/ausbc/render/RenderManager.kt"
).read_text()

def accepted(source_fps, seconds=15):
    # Deterministic source input spacing, same monotonic timestamp policy.
    last = 0
    count = 0
    all_frames = int(source_fps * seconds)
    for i in range(all_frames):
        now_ns = 1_000_000_000 + (i * 1_000_000_000 // source_fps)
        if last > 0 and 0 <= now_ns - last < 50_000_000:
            continue
        last = now_ns
        count += 1
    return count / seconds

p60 = accepted(60)
p30 = accepted(30)
p15 = accepted(15)
p10 = accepted(10)
assert 14 <= p60 <= 20.1, (p60, p30)
assert 13 <= p30 <= 20.1, p30
assert abs(p15 - 15) <= 0.1, p15
assert abs(p10 - 10) <= 0.1, p10
print(f"UVC_NATIVE_RAW20_AB_PASS input60->{p60:.1f} raw30->{p30:.1f} raw15->{p15:.1f} raw10->{p10:.1f}; preview untouched")
