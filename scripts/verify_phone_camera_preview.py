#!/usr/bin/env python3
"""Protect against black phone-camera preview after restoring a saved camera source."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
activity=(root/"app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
camera=(root/"app/src/main/java/com/vobichngan/kanzi/InternalCameraController.java").read_text()
assert "APP_VERSION = \"Blue Star Guide · 实验版 1.0.6\"" in activity
start_method = re.search(r"private void startPhoneCamera\(\) \{([\s\S]*?)\n    \}", activity)
assert start_method, "missing phone camera startup method"
assert start_method.group(1).index("phoneTexture.setVisibility(View.VISIBLE);") < start_method.group(1).index("phoneController.start(phoneFrontFacing);"), "camera surface must be made visible before Camera2 start"
assert "ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER" in activity
assert "new FrameLayout.LayoutParams(1, 1" not in activity
assert "phonePreviewStatus" in activity and "手机摄像头启动失败" in activity
assert "onPreviewStarted()" in camera and "onSurfaceTextureUpdated" in camera
assert "onSurfaceTextureAvailable" in camera and "textureView.isAvailable()" in camera
assert "CameraCaptureSession.StateCallback()" in camera
assert "generation++" in camera and "token != generation" in camera
assert 'if (usingPhoneCamera && phoneController != null)' in activity
assert activity.count("@Override protected void onResume()")==1
assert "cameraTextureView.setRotation(previewRotation);" in activity
assert re.search(r"recognizer\.recognizeNv21\(frame, width, height,\s*enhance, legacyDecode, effectiveOcrInputAngle,", activity)
# The upstream AUSBC CameraActivity.initView() calls previewContainer.removeAllViews()
# after getRootView(), before super.onCreate() completes. Never attach phone
# TextureView from getRootView() or it is immediately discarded.
root_view = activity.split("protected View getRootView(", 1)[1].split(
    "private Button createControlButton(", 1)[0]
assert "previewContainer.addView(phoneTexture" not in root_view, "phone view attached too early"
on_create = activity.split("public void onCreate(Bundle state)", 1)[1].split(
    "private void showSettingsDialog(", 1)[0]
assert on_create.index("super.onCreate(state);") < on_create.index(
    "attachPhonePreviewAfterUsbInit();"), "phone view must attach after AUSBC initialization"
assert "previewContainer.addView(phoneTexture, params);" in activity
start_block = activity.split("private void startPhoneCamera()", 1)[1].split(
    "@Override public void onRequestPermissionsResult", 1)[0]
assert start_block.index("mainHandler.postDelayed(") < start_block.index(
    "phoneTexture.post("), "camera timeout must not depend on an attached phone view"
assert "预览控件未挂载" in activity and "手机摄像头启动超时" in activity


# 0.9.3: fixed full-screen TextureView with aspect-preserving content matrix,
# sharing its geometry with the white OCR boxes on the overlay.
overlay=(root/"app/src/main/java/com/vobichngan/kanzi/OcrOverlayView.java").read_text()
geom=(root/"app/src/main/java/com/vobichngan/kanzi/PhonePreviewGeometry.java").read_text()
assert "phoneTexture.setTransform(transform);" in activity
assert "PhonePreviewGeometry.camera2Preview(" in activity
assert "PhonePreviewGeometry.fit(" in overlay
assert "phoneFit.mapNative(nx, ny)" in overlay
assert "previewMirror, previewVerticalFlip);" in activity
assert "applyPreviewTransform();\n                applyUsbPreviewTone();\n                applyPhonePreviewTransform();" in activity
assert "phoneTexture.setOpaque(false);" in activity
assert "phoneTexture.setRotation(0f);" in activity
phone_method=activity.split("private void applyPhonePreviewTransform()",1)[1].split(
    "/** Speak a warning",1)[0]
assert "setLayoutParams(" not in phone_method
assert "setRotation(phoneOcrRotation)" not in phone_method
assert "float scale = Math.min(viewWidth / rotatedW, viewHeight / rotatedH);" in geom
assert "float horizontal = mirror ? -scale : scale" in geom
assert "float vertical = flip ? -scale : scale" in geom
assert "int angle = PhonePreviewGeometry.rotation(previewRotation, -displayDegrees);" in activity
assert "PhonePreviewGeometry.rotation(phoneOcrRotation, previewRotation)" not in activity
assert "phoneSensorNaturalRotation" in activity
assert "phoneOcrRotation" in activity
assert "phoneFit.mapNative(nx, ny)" in overlay
assert "PhonePreviewGeometry.ocrToNatural(" in overlay
assert "PhonePreviewGeometry.naturalBufferSize(" in overlay
assert "public static Layout camera2Preview(" in geom
assert "listener.onOpened(activeWidth, activeHeight, sensor, rotationDegrees)" in camera
assert "rotationDegrees = front ? (sensor - display + 360) % 360" in camera
assert ": (sensor + display) % 360;" in camera


print("PHONE_CAMERA_PREVIEW_LIFECYCLE=PASS")
