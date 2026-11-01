#!/usr/bin/env python3
"""Protect against black phone-camera preview after restoring a saved camera source."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
activity=(root/"app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
camera=(root/"app/src/main/java/com/vobichngan/kanzi/InternalCameraController.java").read_text()
assert "APP_VERSION = \"0.9.1\"" in activity
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
assert "recognizer.recognizeNv21(frame, width, height, enhance, legacyDecode," in activity
print("PHONE_CAMERA_PREVIEW_LIFECYCLE=PASS")
