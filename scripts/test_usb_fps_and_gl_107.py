#!/usr/bin/env python3
"""Pre-APK fail-closed checks for EXP107 fps negotiation and GL render backlog."""
from pathlib import Path

root = Path("third_party/AndroidUSBCamera")
preview = (root/"libuvc/src/main/jni/UVCCamera/UVCPreview.cpp").read_text()
stream = (root/"libuvc/src/main/jni/libuvc/src/stream.c").read_text()
render = (root/"libausbc/src/main/java/com/jiangdg/ausbc/render/RenderManager.kt").read_text()
camera = (root/"libausbc/src/main/java/com/jiangdg/ausbc/camera/CameraUVC.kt").read_text()
activity = Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
assert 'UVC_FPS_NEGOTIATION_107' in preview
assert 'requestMaxFps != max_fps' in preview and 'requestMinFps != min_fps' in preview
assert 'requestBandwidth != bandwidth' in preview
assert 'fastest_interval = *interval;' in stream
assert 'ctrl->dwFrameInterval = fastest_interval;' in stream
assert 'if (fastest_interval)' in stream
assert 'if ((it >= (uint32_t) min_fps) && (it <= (uint32_t) max_fps)' in stream
assert 'private const val MAX_FPS = 61' in camera
assert 'UVC_NATIVE_OCR_RAW20_GATE_ENABLED' in preview
assert 'mLastOcrRawCallbackNs = now_ns;' in preview
assert 'mGlDrawDirty.set(true)' in render and 'requestGlDraw()' in render
assert 'mGlDrawQueued.compareAndSet(false, true)' in render
assert 'if (mGlDrawDirty.get()) requestGlDraw()' in render
assert 'BSG_GL_DRAW_COALESCE_107' in render
assert 'mCameraSurfaceTexture?.updateTexImage()' in render
assert 'mScreenRender?.swapBuffers(' in render
assert 'mRenderHandler?.obtainMessage(MSG_GL_DRAW)?.sendToTarget()' not in render
assert 'setPreviewWidth(1920)' in activity and 'setPreviewHeight(1080)' in activity
assert 'setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)' in activity
assert 'setRawPreviewData(true)' in activity
assert 'Blue Star Guide · 实验版 1.0.9' in activity
assert "versionName '1.0.9'" in Path("app/build.gradle").read_text()


def choose(intervals_100ns, min_fps=1, max_fps=61):
    good = [it for it in intervals_100ns
            if it and min_fps <= 10_000_000//it <= max_fps]
    return min(good) if good else None


# 30fps before 60fps, the exact bug in upstream first-match handling.
p30, p60, p15, p5994 = 333333, 166667, 666667, 166833
assert choose([p30, p60]) == p60
assert choose([p5994, p30]) == p5994
assert choose([p30]) == p30
assert choose([p15, p30]) == p30
assert choose([p30], 60, 61) is None
assert choose([]) is None
assert choose([0, p30]) == p30

# Model just the one-draw pending + dirty invariant, not rendering speed.
# Simulates 60 camera callbacks/sec with 30fps render capacity.
def simulate(render_interval_ns, camera_interval_ns=16_666_667, duration_ns=10_000_000_000):
    pending = False
    dirty = False
    queued_at = None
    now = 0
    next_capture = 0
    next_completion = 0
    queued_peak = 0
    draws = 0
    while now < duration_ns:
        times = [next_capture]
        if pending: times.append(next_completion)
        now = min(times)
        if now == next_capture:
            dirty = True
            next_capture += camera_interval_ns
            if not pending:
                pending = True
                dirty = False
                next_completion = now + render_interval_ns
        if pending and now == next_completion:
            draws += 1
            pending = False
            if dirty:
                dirty = False
                pending = True
                next_completion = now + render_interval_ns
        queued_peak = max(queued_peak, int(pending))
    return draws, queued_peak

for gl_ns in [8_333_333, 16_666_667, 33_333_333, 80_000_000]:
    draws, peak = simulate(gl_ns)
    assert peak <= 1, (gl_ns, peak)
    assert draws > 0
    print(f"SIM_GL_DRAW render_interval_ns={gl_ns} draws={draws} max_pending={peak}")
print("EXP107_UVC_FPS_AND_GL_QUEUE_STATIC_SIM_PASS (physical 60fps still unverified)")
