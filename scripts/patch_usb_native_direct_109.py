#!/usr/bin/env python3
"""BSG 1.0.9 minimal UVC->TextureView direct path diagnostic and lifecycle safety.

Reuses the upstream AUSBC NORMAL rendering mode, so the actual capture path
becomes native UVC -> Android TextureView producer (no app RenderManager,
no extra OES texture, no intermediate GL passes).

Applies ONLY to pinned ernestp/AndroidUSBCamera 3.6.0, fail closed if changed.
"""
from pathlib import Path

root = Path("third_party/AndroidUSBCamera/libausbc/src/main/java/com/jiangdg/ausbc")
base = root / "base/CameraActivity.kt"
uvc = root / "camera/CameraUVC.kt"

def patch(path, a, b):
    s=path.read_text(encoding="utf-8")
    if s.count(a)!=1:
        raise SystemExit(f"REFUSE unsafe direct-camera patch in {path}: anchor count={s.count(a)}")
    path.write_text(s.replace(a,b,1), encoding="utf-8")

# One startup marker in DEX, never logged per frame.
patch(
    base,
    """            ) {
                registerMultiCamera()
            }

            override fun onSurfaceTextureSizeChanged(""",
    """            ) {
                Logger.i(TAG, "BSG_USB_DIRECT_109 native TextureView mode available")
                registerMultiCamera()
            }

            override fun onSurfaceTextureSizeChanged(""",
)

# Count genuine view content updates, not GL swaps. This is only active when
# TextureView receives a new native camera buffer.
patch(
    base,
    """            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }""",
    """            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                // BSG_USB_DIRECT_109 - direct native-camera surface update; no GL renders.
                com.jiangdg.ausbc.render.DiagnosticRenderHeartbeat.noteDirectSurfaceUpdate()
            }"""
)

# The original CLOSED callback was posted BEFORE native UVCCamera.destroy().
# For a clean close+reopen lifecycle, issue CLOSED only AFTER the old native
# connection is destroyed so the new session cannot race the old decoder.
patch(
    uvc,
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
        // BSG_USB_DIRECT_CLOSE_BARRIER_109: old native stream is fully torn down.
        Logger.i(TAG, "BSG_USB_DIRECT_CLOSE_BARRIER_109")
        postStateEvent(ICameraStateCallBack.State.CLOSED)"""
)

print("BSG_USB_NATIVE_DIRECT_SURFACE_109=PASS")
print("BSG_USB_NATIVE_DESTROY_BEFORE_CLOSED_CALLBACK_109=PASS")
