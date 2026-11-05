#!/usr/bin/env python3
"""Passive trace for the pinned AndroidUSBCamera 3.6.0 source checkout.
No camera restart, buffer mutation, scheduling, orientation, or OCR modification.
"""
from pathlib import Path

root=Path("third_party/AndroidUSBCamera/libausbc/src/main/java/com/jiangdg/ausbc")
call="com.jiangdg.ausbc.render.DiagnosticRenderHeartbeat"

def patch(rel,old,new,description):
    path=root/rel
    source=path.read_text(encoding="utf-8")
    if source.count(old)!=1:
        raise SystemExit(f"Unexpected AUSBC upstream {description}; abort without guesswork")
    path.write_text(source.replace(old,new,1),encoding="utf-8")

patch("camera/CameraUVC.kt",
    "        frame?.apply {\n            frame.position(0)",
    "        frame?.apply {\n            "+call+".noteNativeFrame()\n            frame.position(0)",
    "native to Java callback")
patch("camera/CameraUVC.kt",
    "        mUvcCamera?.startPreview()",
    "        mUvcCamera?.startPreview()\n        "+call+'.noteEvent("UVCCamera startPreview returned")',
    "startPreview return")
patch("render/RenderManager.kt",
    "    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {\n        emitFrameRate()",
    "    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {\n        DiagnosticRenderHeartbeat.noteGlFrameArrived()\n        emitFrameRate()",
    "GL input arrival")

patch("base/CameraActivity.kt",
    "override fun onAttachDev(device: UsbDevice?) {\n                device ?: return",
    "override fun onAttachDev(device: UsbDevice?) {\n                device ?: return\n                "+call+'.noteEvent("USB ATTACH id=" + device.deviceId)',
    "attach")
patch("base/CameraActivity.kt",
    "override fun onDetachDec(device: UsbDevice?) {",
    "override fun onDetachDec(device: UsbDevice?) {\n                "+call+'.noteEvent("USB DETACH id=" + device?.deviceId)',
    "detach")
patch("base/CameraActivity.kt",
    "override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {\n                device ?: return\n                ctrlBlock ?: return",
    "override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {\n                device ?: return\n                ctrlBlock ?: return\n                "+call+'.noteEvent("USB CONNECT ctrlBlock=present id=" + device.deviceId)',
    "connect")
patch("base/CameraActivity.kt",
    "override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {",
    "override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {\n                "+call+'.noteEvent("USB DISCONNECT id=" + device?.deviceId)',
    "disconnect")
patch("base/CameraActivity.kt",
    "override fun onCancelDev(device: UsbDevice?) {",
    "override fun onCancelDev(device: UsbDevice?) {\n                "+call+'.noteEvent("USB CANCEL id=" + device?.deviceId)',
    "permission cancel")
patch("base/CameraActivity.kt",
    "            ) {\n                registerMultiCamera()",
    "            ) {\n                "+call+'.noteEvent("SURFACE AVAILABLE " + width + "x" + height)\n                registerMultiCamera()',
    "TextureView available")
patch("base/CameraActivity.kt",
    "override fun onSurfaceTextureSizeChanged(\n                surface: SurfaceTexture,\n                width: Int,\n                height: Int\n            ) {\n                surfaceSizeChanged(width, height)",
    "override fun onSurfaceTextureSizeChanged(\n                surface: SurfaceTexture,\n                width: Int,\n                height: Int\n            ) {\n                "+call+'.noteEvent("SURFACE SIZE " + width + "x" + height)\n                surfaceSizeChanged(width, height)',
    "TextureView size changed")
patch("base/CameraActivity.kt",
    "override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {\n                unRegisterMultiCamera()",
    "override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {\n                "+call+'.noteEvent("SURFACE DESTROYED")\n                unRegisterMultiCamera()',
    "TextureView destroyed")
patch("MultiCameraClient.kt",
    "        fun closeCamera() {\n            mCameraHandler?.obtainMessage(MSG_STOP_PREVIEW)?.sendToTarget()",
    "        fun closeCamera() {\n            "+call+'.noteCallSite("UVC closeCamera REQUEST")\n            mCameraHandler?.obtainMessage(MSG_STOP_PREVIEW)?.sendToTarget()',
    "camera close requester")
patch("MultiCameraClient.kt",
    "                    closeCameraInternal()\n                    mRenderManager?.getCacheEffectList()",
    "                    "+call+'.noteEvent("UVC MSG_STOP_PREVIEW handling")\n                    closeCameraInternal()\n                    mRenderManager?.getCacheEffectList()',
    "camera close handling")
print("PASS: native UVC, GL input, USB/Surface events and close request callers")
