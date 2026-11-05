#!/usr/bin/env python3
"""Patches pinned AUSBC 3.6.0 to bound only the native OCR NV21 callback.

On an active 1080p/60-FPS preview this avoids most unnecessary native YUV
conversion, Java arrays, and camera-to-Java copies. The independent Surface /
OpenGL preview path and USB stream negotiation are NOT limited.

Build-only A/B variant: older 1.0.5 retains full callback speed.
"""
from pathlib import Path

ROOT = Path("third_party/AndroidUSBCamera/libuvc/src/main/jni/UVCCamera")
HEADER = ROOT / "UVCPreview.h"
SOURCE = ROOT / "UVCPreview.cpp"

def patch(path: Path, before: str, after: str):
    contents = path.read_text(encoding="utf-8")
    count = contents.count(before)
    if count != 1:
        raise SystemExit(
            f"ERROR: expected pinned AUSBC 3.6.0 anchor exactly once in {path}; "
            f"count={count}. Refusing native patch."
        )
    path.write_text(contents.replace(before, after, 1), encoding="utf-8")

patch(
    HEADER,
    "#include <pthread.h>\n",
    "#include <pthread.h>\n#include <stdint.h>\n",
)
patch(
    HEADER,
    "\tsize_t callbackPixelBytes;\n",
    "\tsize_t callbackPixelBytes;\n"
    "\t// Accessed solely by the native capture callback thread; reset per UVCPreview.\n"
    "\tint64_t mLastOcrRawCallbackNs;\n",
)
patch(
    SOURCE,
    "#include <unistd.h>\n",
    "#include <unistd.h>\n#include <stdint.h>\n#include <time.h>\n",
)
patch(
    SOURCE,
    "\tmFrameCallbackFunc(NULL),\n\tcallbackPixelBytes(2) {",
    "\tmFrameCallbackFunc(NULL),\n\tcallbackPixelBytes(2),\n"
    "\tmLastOcrRawCallbackNs(0) {",
)
patch(
    SOURCE,
    "\tmLastOcrRawCallbackNs(0) {\n\n\tENTER();",
    "\tmLastOcrRawCallbackNs(0) {\n\n\tENTER();\n\tLOGI(\"UVC_NATIVE_OCR_RAW20_GATE_ENABLED\");",
)

# This location is within do_capture_callback(): preview has already been
# decoded and displayed by its own independent thread, but this capture thread
# has not yet allocated / converted a new NV21 output or called Java.
patch(
    SOURCE,
    """void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
\tENTER();

\tif (LIKELY(frame)) {
\t\tuvc_frame_t *callback_frame = frame;""",
    """void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
\tENTER();

\tif (LIKELY(frame)) {
\t\t// EXPERIMENT v1.0.6: limit *only* native YUV->OCR callback to 20fps.
\t\t// The full-rate 1080p MJPEG decode / surface / OpenGL preview is separate.
\t\t// The ordinary no-callback & still-capture paths are unchanged.
\t\t// IMPORTANT: recycle skipped frames (owned by capture queue). Never wait.
\t\tif (mFrameCallbackObj && mFrameCallbackFunc) {
\t\t\tstruct timespec callback_clock;
\t\t\tif (clock_gettime(CLOCK_MONOTONIC, &callback_clock) == 0) {
\t\t\t\tconst int64_t now_ns = (int64_t)callback_clock.tv_sec * 1000000000LL
\t\t\t\t\t+ (int64_t)callback_clock.tv_nsec;
\t\t\t\tif (mLastOcrRawCallbackNs > 0 && now_ns >= mLastOcrRawCallbackNs
\t\t\t\t\t&& now_ns - mLastOcrRawCallbackNs < 50000000LL) {
\t\t\t\t\trecycle_frame(frame);
\t\t\t\t\tEXIT();
\t\t\t\t\treturn;
\t\t\t\t}
\t\t\t\tmLastOcrRawCallbackNs = now_ns;
\t\t\t}
\t\t}
\t\tuvc_frame_t *callback_frame = frame;""",
)

print("UVC_NATIVE_OCR_CALLBACK_20FPS_GATE=PASS")
print("OpenGL preview unchanged; NV21 native conversion and Java per-frame copy rate limited.")
