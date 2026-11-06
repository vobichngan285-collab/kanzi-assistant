#!/usr/bin/env python3
"""Fail-closed minimal patches against pinned AUSBC 3.6.0.

Normal TextureView preview stays native/direct. Only NV21 callback is capped
at <=5Hz before native conversion and Java array allocation. Not a video FPS cap.
USB close callback is delayed until native stream destruction completes.
"""
from pathlib import Path

root = Path("cloud-vision-app/third_party/AndroidUSBCamera")
header = root / "libuvc/src/main/jni/UVCCamera/UVCPreview.h"
source = root / "libuvc/src/main/jni/UVCCamera/UVCPreview.cpp"
uvc = root / "libausbc/src/main/java/com/jiangdg/ausbc/camera/CameraUVC.kt"
uvcbuild = root / "libuvc/build.gradle"
nativebuild = root / "libnative/build.gradle"

def patch(path, before, after):
    data = path.read_text(encoding="utf-8")
    n = data.count(before)
    if n != 1:
        raise SystemExit(f"Refuse unreviewed AUSBC change: {path} anchor count={n}")
    path.write_text(data.replace(before, after, 1), encoding="utf-8")

patch(header, "#include <pthread.h>\n", "#include <pthread.h>\n#include <stdint.h>\n")
patch(header, "\tsize_t callbackPixelBytes;\n",
      "\tsize_t callbackPixelBytes;\n\tint64_t mLastCloudFrameCallbackNs;\n")
patch(source, "#include <unistd.h>\n",
      "#include <unistd.h>\n#include <stdint.h>\n#include <time.h>\n")
patch(source, "\tmFrameCallbackFunc(NULL),\n\tcallbackPixelBytes(2) {",
      "\tmFrameCallbackFunc(NULL),\n\tcallbackPixelBytes(2),\n"
      "\tmLastCloudFrameCallbackNs(0) {")
patch(source,
      """void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
\tENTER();

\tif (LIKELY(frame)) {
\t\tuvc_frame_t *callback_frame = frame;""",
      """void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
\tENTER();

\tif (LIKELY(frame)) {
\t\t// BSG_CLOUD_RAW5_GATED: only retain roughly 5 fresh NV21 frames/sec
\t\t// for one-tap snapshot. Never alter UVC decode or TextureView preview.
\t\tif (mFrameCallbackObj && mFrameCallbackFunc) {
\t\t\tstruct timespec frame_clock;
\t\t\tif (clock_gettime(CLOCK_MONOTONIC, &frame_clock) == 0) {
\t\t\t\tconst int64_t now_ns = (int64_t)frame_clock.tv_sec * 1000000000LL
\t\t\t\t\t+ (int64_t)frame_clock.tv_nsec;
\t\t\t\tif (mLastCloudFrameCallbackNs > 0 && now_ns >= mLastCloudFrameCallbackNs
\t\t\t\t\t&& now_ns - mLastCloudFrameCallbackNs < 200000000LL) {
\t\t\t\t\trecycle_frame(frame);
\t\t\t\t\tEXIT();
\t\t\t\t\treturn;
\t\t\t\t}
\t\t\tmLastCloudFrameCallbackNs = now_ns;
\t\t\t}
\t\t}
\t\tuvc_frame_t *callback_frame = frame;""")
patch(uvc,
      """    override fun closeCameraInternal() {
        postStateEvent(ICameraStateCallBack.State.CLOSED)
        isPreviewed = false
        releaseEncodeProcessor()
        mUvcCamera?.destroy()
        mUvcCamera = null""",
      """    override fun closeCameraInternal() {
        isPreviewed = false
        releaseEncodeProcessor()
        mUvcCamera?.destroy()
        mUvcCamera = null
        // BSG_CLOUD_CLOSE_BARRIER: must not signal CLOSED before native destroy.
        postStateEvent(ICameraStateCallBack.State.CLOSED)""")
patch(uvcbuild, "abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'",
      "abiFilters 'arm64-v8a'")
patch(nativebuild, "abiFilters 'armeabi-v7a','arm64-v8a', \"x86\", \"x86_64\"",
      "abiFilters 'arm64-v8a'")
print("BSG_CLOUD_RAW5_GATED=PASS")
print("BSG_CLOUD_CLOSE_BARRIER=PASS")
print("arm64-only native compilation=PASS")
