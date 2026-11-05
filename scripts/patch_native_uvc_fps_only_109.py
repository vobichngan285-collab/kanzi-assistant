#!/usr/bin/env python3
"""Native-only patch: choose highest advertised UVC FPS for DIRECT preview.

Never forces an unsupported sensor mode: select the smallest supported frame
interval within the requested 1..61 fps range. Keep 1080p MJPEG first and
fall back to whatever the connected UVC camera actually advertises.
Keep the independent native <=20fps OCR callback from v1.0.6.
"""
from pathlib import Path

ROOT = Path("third_party/AndroidUSBCamera")
NATIVE = ROOT / "libuvc/src/main/jni/UVCCamera/UVCPreview.cpp"
STREAM = ROOT / "libuvc/src/main/jni/libuvc/src/stream.c"


def patch(path, old, new):
    text = path.read_text(encoding="utf-8")
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"REFUSE UNSAFE 1.0.7 patch {path}: expected one anchor, found {n}: {old[:90]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Starting in UVCCamera.open(), upstream uses a 1..31 fps probe.  The later
# explicit 1..61 fps request must renegotiate EVEN IF resolution/format match.
patch(
    NATIVE,
    "if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode)) {",
    "if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode)\n"
    "\t\t|| (requestMinFps != min_fps) || (requestMaxFps != max_fps)\n"
    "\t\t|| (requestBandwidth != bandwidth)) {",
)

# Report the control block the camera actually accepted in logcat; a numeric
# 61 maximum alone was never evidence of 60 delivered camera frames.
patch(
    NATIVE,
    """\t\trequestWidth, requestHeight, requestMinFps, requestMaxFps
\t);
\tif (LIKELY(!result)) {""",
    """\t\trequestWidth, requestHeight, requestMinFps, requestMaxFps
\t);
\tif (LIKELY(!result) && ctrl->dwFrameInterval > 0) {
\t\tLOGI("UVC_FPS_NEGOTIATION_107 %dx%d mode=%s interval100ns=%u selectedFps=%.2f",
\t\t\trequestWidth, requestHeight, requestMode ? "MJPEG" : "YUYV",
\t\t\t(unsigned int)ctrl->dwFrameInterval,
\t\t\t10000000.0 / (double)ctrl->dwFrameInterval);
\t}
\tif (LIKELY(!result)) {""",
)

# Upstream picks the first supported interval in the descriptor. Many cameras
# enumerate 30 *before* 60; pick the fastest VALID interval instead.
patch(
    STREAM,
    """\t\tif (frame->intervals) {
\t\t\tfor (interval = frame->intervals; *interval; ++interval) {
\t\t\t\tif (UNLIKELY(!(*interval))) continue;
\t\t\t\tuint32_t it = 10000000 / *interval;
\t\t\t\tLOGV("it:%d", it);
\t\t\t\tif ((it >= (uint32_t) min_fps) && (it <= (uint32_t) max_fps)) {
\t\t\t\t\tctrl->bmHint = (1 << 0); /* don't negotiate interval */
\t\t\t\t\tctrl->bFormatIndex = format->bFormatIndex;
\t\t\t\t\tctrl->bFrameIndex = frame->bFrameIndex;
\t\t\t\t\tctrl->dwFrameInterval = *interval;

\t\t\t\t\tgoto found;
\t\t\t\t}
\t\t\t}
\t\t} else {""",
    """\t\tif (frame->intervals) {
\t\t\tuint32_t fastest_interval = 0;
\t\t\tfor (interval = frame->intervals; *interval; ++interval) {
\t\t\t\tif (UNLIKELY(!(*interval))) continue;
\t\t\t\tuint32_t it = 10000000 / *interval;
\t\t\t\tLOGV("it:%d", it);
\t\t\t\tif ((it >= (uint32_t) min_fps) && (it <= (uint32_t) max_fps)
\t\t\t\t\t&& (!fastest_interval || *interval < fastest_interval)) {
\t\t\t\t\tfastest_interval = *interval;
\t\t\t\t}
\t\t\t}
\t\t\tif (fastest_interval) {
\t\t\t\tctrl->bmHint = (1 << 0); /* don't negotiate interval */
\t\t\t\tctrl->bFormatIndex = format->bFormatIndex;
\t\t\t\tctrl->bFrameIndex = frame->bFrameIndex;
\t\t\t\tctrl->dwFrameInterval = fastest_interval;
\t\t\t\tgoto found;
\t\t\t}
\t\t} else {""",
)

print("UVC_NATIVE_FPS_HIGHEST_SUPPORTED_109=PASS")
print("UVC_RENEGOTIATE_FPS_DIRECT_109=PASS")
print("No application GL render pipeline or GL queue patch is applied.")
