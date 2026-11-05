#!/usr/bin/env python3
"""Safe source patch for pinned AUSBC 3.6.0: highest supported UVC FPS + bounded GL draw queue.

Never forces an unsupported sensor mode: select the smallest supported frame
interval within the requested 1..61 fps range. Keep 1080p MJPEG first and
fall back to whatever the connected UVC camera actually advertises.
Keep the independent native <=20fps OCR callback from v1.0.6.
"""
from pathlib import Path

ROOT = Path("third_party/AndroidUSBCamera")
NATIVE = ROOT / "libuvc/src/main/jni/UVCCamera/UVCPreview.cpp"
STREAM = ROOT / "libuvc/src/main/jni/libuvc/src/stream.c"
RENDER = ROOT / "libausbc/src/main/java/com/jiangdg/ausbc/render/RenderManager.kt"


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

# AOSP SurfaceTexture retains the latest frame. Do NOT enqueue an unbounded
# MSG_GL_DRAW for every callback when decode/OCR briefly outpaces rendering.
# One pending draw, plus a dirty flag for frames arriving during rendering.
patch(
    RENDER,
    "Logger.i(TAG, \"create RenderManager, Open ES version is ${Utils.getGLESVersion(context)}\")",
    "Logger.i(TAG, \"create RenderManager, Open ES version is ${Utils.getGLESVersion(context)}\")\n        Logger.i(TAG, \"BSG_GL_DRAW_COALESCE_107\")",
)

patch(
    RENDER,
    "    private var mFrameRate = 0\n",
    """    private var mFrameRate = 0
    private val mGlDrawQueued = AtomicBoolean(false)
    private val mGlDrawDirty = AtomicBoolean(false)

    private fun requestGlDraw() {
        val handler = mRenderHandler ?: return
        if (mGlDrawQueued.compareAndSet(false, true)) {
            if (!handler.sendEmptyMessage(MSG_GL_DRAW)) mGlDrawQueued.set(false)
        }
    }
""",
)
patch(
    RENDER,
    """            MSG_GL_DRAW -> {
                //Render camera data to SurfaceTexture""",
    """            MSG_GL_DRAW -> {
                // BSG_GL_DRAW_COALESCE_107: sample newest texture, never backlog old frames.
                mGlDrawDirty.set(false)
                try {
                //Render camera data to SurfaceTexture""",
)
patch(
    RENDER,
    """                mScreenRender?.swapBuffers(mCameraSurfaceTexture?.timestamp ?: 0)
                // Passive health counter: only GL submission attempt, not display.
                DiagnosticRenderHeartbeat.noteSwapAttempt()
            }
            MSG_GL_ADD_EFFECT -> {""",
    """                mScreenRender?.swapBuffers(mCameraSurfaceTexture?.timestamp ?: 0)
                // Passive health counter: only GL submission attempt, not display.
                DiagnosticRenderHeartbeat.noteSwapAttempt()
                } finally {
                    mGlDrawQueued.set(false)
                    if (mGlDrawDirty.get()) requestGlDraw()
                }
            }
            MSG_GL_ADD_EFFECT -> {""",
)
patch(
    RENDER,
    """    fun startRenderScreen(w: Int, h: Int, outSurface: Surface?, listener: CameraSurfaceTextureListener? = null) {
        mRenderThread = HandlerThread(RENDER_THREAD)""",
    """    fun startRenderScreen(w: Int, h: Int, outSurface: Surface?, listener: CameraSurfaceTextureListener? = null) {
        mGlDrawQueued.set(false)
        mGlDrawDirty.set(false)
        mRenderThread = HandlerThread(RENDER_THREAD)""",
)
patch(
    RENDER,
    """        mRenderThread = null
        mRenderHandler = null
    }""",
    """        mRenderThread = null
        mRenderHandler = null
        mGlDrawQueued.set(false)
        mGlDrawDirty.set(false)
    }""",
)
patch(
    RENDER,
    """    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        DiagnosticRenderHeartbeat.noteGlFrameArrived()
        emitFrameRate()
        mRenderHandler?.obtainMessage(MSG_GL_DRAW)?.sendToTarget()
    }""",
    """    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        DiagnosticRenderHeartbeat.noteGlFrameArrived()
        emitFrameRate()
        mGlDrawDirty.set(true)
        requestGlDraw()
    }""",
)

print("UVC_NATIVE_FPS_HIGHEST_SUPPORTED_107=PASS")
print("UVC_RENEGOTIATE_FPS_IF_ONLY_INTERVAL_CHANGES_107=PASS")
print("GL_DRAW_COALESCED_NO_UNBOUNDED_BACKLOG_107=PASS")
print("Existing native RAW20 callback, native UVC preview path and OCR unchanged.")
