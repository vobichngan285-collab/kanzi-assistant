#!/usr/bin/env python3
"""Expose read-only UVC control capability flags in the pinned AUSBC 3.6.0 checkout.

The library is checked out into third_party during GitHub CI. No proprietary APK
classes are reused, and no zoom, brightness or exposure command is issued.
"""
from pathlib import Path

root=Path("third_party/AndroidUSBCamera/libausbc/src/main/java/com/jiangdg/ausbc")

def patch(rel,old,new):
    p=root/rel
    s=p.read_text(encoding="utf-8")
    if s.count(old)!=1:
        raise SystemExit(f"Unexpected AUSBC 3.6.0 source {rel}; refusing patch")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

patch("camera/CameraUVC.kt",
    "    fun getAutoFocus() = mUvcCamera?.autoFocus",
    """    fun getAutoFocus() = mUvcCamera?.autoFocus

    /** Device-advertised UVC capabilities: read-only, never assumed supported. */
    fun getHardwareControlsReport(): String {
        val camera = mUvcCamera ?: return "usbCamera=null"
        return try {
            val autoFocus = camera.checkSupportFlag(UVCCamera.CTRL_FOCUS_AUTO)
            val manualFocus = camera.checkSupportFlag(UVCCamera.CTRL_FOCUS_ABS)
            val zoom = camera.checkSupportFlag(UVCCamera.CTRL_ZOOM_ABS)
            val autoExposure = camera.checkSupportFlag(UVCCamera.CTRL_AE)
            val manualExposure = camera.checkSupportFlag(UVCCamera.CTRL_AE_ABS)
            val brightness = camera.checkSupportFlag(UVCCamera.PU_BRIGHTNESS.toLong())
            val saturation = camera.checkSupportFlag(UVCCamera.PU_SATURATION.toLong())
            "afAuto=$autoFocus focusAbs=$manualFocus zoomAbs=$zoom aeAuto=$autoExposure " +
                "exposureAbs=$manualExposure brightness=$brightness saturation=$saturation " +
                "afReadback=" + (if (autoFocus) camera.autoFocus else "unsupported")
        } catch (error: RuntimeException) {
            "queryError=" + error.javaClass.simpleName
        }
    }

    /** Gate ALL AF writes on explicit capability, not on getter defaults. */
    fun supportsUsbHardwareAutoFocus(): Boolean {
        val camera = mUvcCamera ?: return false
        return try { camera.checkSupportFlag(UVCCamera.CTRL_FOCUS_AUTO.toLong()) }
        catch (error: RuntimeException) { false }
    }""")
patch("base/CameraActivity.kt",
    "    protected fun getAutoFocus(): Boolean? {",
    """    protected fun getUsbHardwareControlsReport(): String {
        return (getCurrentCamera() as? CameraUVC)?.getHardwareControlsReport()
            ?: "not-uvc-or-not-open"
    }

    protected fun supportsUsbHardwareAutoFocus(): Boolean {
        return (getCurrentCamera() as? CameraUVC)?.supportsUsbHardwareAutoFocus() ?: false
    }

    protected fun getAutoFocus(): Boolean? {""")
print("UVC_HARDWARE_CAPABILITY_READONLY_PATCH=PASS")
