#!/usr/bin/env python3
"""BSG 1.0.9 native UVC direct-preview / lifecycle smoke guard.

Use pinned AUSBC 3.6.0 directly. Direct path MUST skip extra OpenGL copy,
and the old native close must finish before the Activity receives CLOSED.
These assertions are not a substitute for vivo USB hardware endurance tests.
"""
from pathlib import Path
root=Path("third_party/AndroidUSBCamera")
main=Path("app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
heartbeat=Path("scripts/DiagnosticRenderHeartbeat.java").read_text()
monitor=Path("app/src/main/java/com/vobichngan/kanzi/UsbCameraHealthMonitor.java").read_text()
base=(root/"libausbc/src/main/java/com/jiangdg/ausbc/base/CameraActivity.kt").read_text()
cam=(root/"libausbc/src/main/java/com/jiangdg/ausbc/camera/CameraUVC.kt").read_text()
multi=(root/"libausbc/src/main/java/com/jiangdg/ausbc/MultiCameraClient.kt").read_text()
stream=(root/"libuvc/src/main/jni/libuvc/src/stream.c").read_text()
preview=(root/"libuvc/src/main/jni/UVCCamera/UVCPreview.cpp").read_text()
gradle=Path("app/build.gradle").read_text()

# One native UVC stream -> Android TextureView; NO RenderManager/extra GLSurface queue.
assert "setRenderMode(CameraRequest.RenderMode.NORMAL)" in main
assert "setRenderMode(CameraRequest.RenderMode.OPENGL)" not in main
assert "mCameraRequest!!.renderMode = CameraRequest.RenderMode.OPENGL" in multi
assert "if (! isNeedGLESRender && view != null)" in multi
assert "openCameraInternal(view)" in multi
assert "mUvcCamera?.setPreviewTexture(cameraView.surfaceTexture)" in cam
assert "mUvcCamera?.setFrameCallback(frameCallBack, UVCCamera.PIXEL_FORMAT_YUV420SP)" in cam
assert "setRawPreviewData(true)" in main
assert "setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)" in main
assert "setPreviewWidth(1920)" in main and "setPreviewHeight(1080)" in main

# No use of old GL or screen buffer geometry patches in the selected camera path.
assert "BSG_GL_DRAW_COALESCE_107" not in (root/"libausbc/src/main/java/com/jiangdg/ausbc/render/RenderManager.kt").read_text()
assert "BSG_USB_INPUT_BUFFER_GEOMETRY_108" not in (root/"libausbc/src/main/java/com/jiangdg/ausbc/render/RenderManager.kt").read_text()
assert "BSG_USB_DIRECT_109" in base
assert "DiagnosticRenderHeartbeat.noteDirectSurfaceUpdate()" in base
assert "noteDirectSurfaceUpdate()" in heartbeat
assert "totalDirectSurfaceUpdates()" in heartbeat
assert "lastDirectSurfaceUpdateMs()" in heartbeat
assert "totalDirectSurfaceUpdates()" in main
assert "lastDirectSurfaceUpdateMs()" in main
assert "直显更新" in monitor
assert "GL提交" not in monitor

# Exactly one clean native teardown+open after returning, not UVC AF during resume.
closed=cam.split("override fun closeCameraInternal()",1)[1].split("override fun captureImageInternal",1)[0]
assert closed.index("mUvcCamera?.destroy()") < closed.index("postStateEvent(ICameraStateCallBack.State.CLOSED)")
assert "BSG_USB_DIRECT_CLOSE_BARRIER_109" in closed
assert "private void stopUsbDirectCamera(String reason)" in main
assert "private void resumeUsbDirectCamera()" in main
assert "private void checkUsbDirectStall(long now)" in main
assert "usbDirectForeground = false;" in main
assert "usbDirectForeground = true;" in main
assert "DIRECT_CLOSE " in main and "DIRECT_REOPEN after native CLOSED" in main
assert "FOREGROUND_STALL" in main
assert "age < 3500L" in main and "now - usbDirectLastRecoveryAtMs < 20000L" in main
assert "EXPERIMENT AF REFOCUS after Activity onResume" not in main
assert "usbVerifiedAutoFocus = false;" in main
assert "usbDirectStopPending" in main

# Only native UVC format/size/FPS selection, retaining original NV21 <=20fps OCR.
assert "fastest_interval = *interval" in stream
assert "requestMaxFps != max_fps" in preview
assert "UVC_FPS_NEGOTIATION_107" in preview
assert "UVC_NATIVE_OCR_RAW20_GATE_ENABLED" in preview
assert "Blue Star Guide · 2.0" in main
assert "versionCode 200" in gradle and "versionName '2.0'" in gradle
assert "applicationIdSuffix '.bluestarguide.dev20'" in gradle
print("USB_NATIVE_DIRECT_109_STATIC_GUARDS=PASS")
print("Direct visible updates, foreground stalls, background returns checked; physical A/B unverified")
