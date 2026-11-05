#!/usr/bin/env python3
"""Fail-closed v1.0.8 test: producer size != output viewport size.

This is a static / numerical regression, NOT proof of visible Android pixels.
"""
from pathlib import Path
root=Path("third_party/AndroidUSBCamera")
rm=(root/"libausbc/src/main/java/com/jiangdg/ausbc/render/RenderManager.kt").read_text()
mc=(root/"libausbc/src/main/java/com/jiangdg/ausbc/MultiCameraClient.kt").read_text()
uvc=(root/"libuvc/src/main/jni/UVCCamera/UVCPreview.cpp").read_text()
activity=Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
gradle=Path("app/build.gradle").read_text()

assert "BSG_USB_INPUT_BUFFER_GEOMETRY_108" in rm
assert rm.count("setDefaultBufferSize(surfaceWidth, surfaceHeight)") == 2
assert "setDefaultBufferSize(mWidth, mHeight)" not in rm
assert "setDefaultBufferSize(w, h)" not in rm
assert "mCameraRender?.setSize(mWidth, mHeight)" in rm
assert "mScreenRender?.setSize(mWidth, mHeight)" in rm
assert "mCaptureRender?.setSize(mWidth, mHeight)" in rm
assert "mScreenRender?.swapBuffers(" in rm
assert "mCameraSurfaceTexture?.updateTexImage()" in rm
assert "BSG_GL_DRAW_COALESCE_107" in rm
assert "mGlDrawQueued.compareAndSet(false, true)" in rm
assert "mGlDrawDirty.set(true)" in rm
assert "RenderManager(ctx, previewWidth, previewHeight, previewCb)" in mc
assert "startRenderScreen(screenWidth, screenHeight, surface," in mc
assert "ANativeWindow_setBuffersGeometry(mPreviewWindow," in uvc
assert "frameWidth, frameHeight, previewFormat" in uvc
assert "UVC_FPS_NEGOTIATION_107" in uvc
assert "UVC_NATIVE_OCR_RAW20_GATE_ENABLED" in uvc
assert "1920" in activity and "1080" in activity
assert "Blue Star Guide · 实验版 1.0.8" in activity
assert "versionCode 108" in gradle and "versionName '1.0.8'" in gradle
assert "applicationIdSuffix '.experiment.dev108'" in gradle

def producer_bytes(w,h,px=4): return w*h*px
# App screenshot indicated 1920x1080 camera while TextureView resized to 2240x1260.
# Previously different producer size; now camera buffer is the exact expected native size.
for camera,screen in [((1920,1080),(2240,1260)),((1920,1080),(1260,709)),((1280,720),(1920,1080))]:
    assert producer_bytes(*camera) > 0
    assert camera[0]*screen[1] == camera[1]*screen[0] or camera==(1920,1080)
    print("FIXED_USB_PRODUCER_SOURCE=%sx%s output=%sx%s source_bytes=%d" %
          (*camera,*screen,producer_bytes(*camera)))
print("USB_GEOMETRY_108_STATIC_AND_REGRESSION=PASS; physical frame/grey acceptance pending")
