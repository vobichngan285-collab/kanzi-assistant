#!/usr/bin/env python3
"""Fail-closed minimal patches against pinned AUSBC 3.6.0.

Normal TextureView preview stays native/direct. Only NV21 callback is capped
at <=5Hz before native conversion and Java array allocation. Not a video FPS cap.
USB close callback is delayed until native stream destruction completes.
"""
from pathlib import Path

root = Path("cloud-vision-app/third_party/AndroidUSBCamera")
header = root / "libuvc/src/main/jni/UVCCamera/UVCPreview.h"
source = root / "libuvc/src/main/jni/UVCCamera/UVCPreview.cpp"
uvc = root / "libausbc/src/main/java/com/jiangdg/ausbc/camera/CameraUVC.kt"
activity = root / "libausbc/src/main/java/com/jiangdg/ausbc/base/CameraActivity.kt"
uvcbuild = root / "libuvc/build.gradle"
nativebuild = root / "libnative/build.gradle"

def patch(path, before, after):
    data = path.read_text(encoding="utf-8")
    n = data.count(before)
    if n != 1:
        raise SystemExit(f"Refuse unreviewed AUSBC change: {path} anchor count={n}")
    path.write_text(data.replace(before, after, 1), encoding="utf-8")

patch(header, "#include <pthread.h>\n", "#include <pthread.h>\n#include <stdint.h>\n")
patch(header, "\tsize_t callbackPixelBytes;\n",
      "\tsize_t callbackPixelBytes;\n\tint64_t mLastCloudFrameCallbackNs;\n")
patch(source, "#include <unistd.h>\n",
      "#include <unistd.h>\n#include <stdint.h>\n#include <time.h>\n")
patch(source, "\tmFrameCallbackFunc(NULL),\n\tcallbackPixelBytes(2) {",
      "\tmFrameCallbackFunc(NULL),\n\tcallbackPixelBytes(2),\n"
      "\tmLastCloudFrameCallbackNs(0) {")
patch(source,
      """void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
\tENTER();

\tif (LIKELY(frame)) {
\t\tuvc_frame_t *callback_frame = frame;""",
      """void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
\tENTER();

\tif (LIKELY(frame)) {
\t\t// BSG_CLOUD_RAW5_GATED: only retain roughly 5 fresh NV21 frames/sec
\t\t// for one-tap snapshot. Never alter UVC decode or TextureView preview.
\t\tif (mFrameCallbackObj && mFrameCallbackFunc) {
\t\t\tstruct timespec frame_clock;
\t\t\tif (clock_gettime(CLOCK_MONOTONIC, &frame_clock) == 0) {
\t\t\t\tconst int64_t now_ns = (int64_t)frame_clock.tv_sec * 1000000000LL
\t\t\t\t\t+ (int64_t)frame_clock.tv_nsec;
\t\t\t\tif (mLastCloudFrameCallbackNs > 0 && now_ns >= mLastCloudFrameCallbackNs
\t\t\t\t\t&& now_ns - mLastCloudFrameCallbackNs < 200000000LL) {
\t\t\t\t\trecycle_frame(frame);
\t\t\t\t\tEXIT();
\t\t\t\t\treturn;
\t\t\t\t}
\t\t\tmLastCloudFrameCallbackNs = now_ns;
\t\t\t}
\t\t}
\t\tuvc_frame_t *callback_frame = frame;""")
patch(uvc,
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
        // BSG_CLOUD_CLOSE_BARRIER: must not signal CLOSED before native destroy.
        postStateEvent(ICameraStateCallBack.State.CLOSED)""")
# BSG_USB_HEALTH_114: piggyback on the existing TextureView listener.
# Do not install a second listener: it would replace AUSBC USB attach/open logic.
patch(activity,
      "abstract class CameraActivity: BaseActivity(), ICameraStateCallBack {",
      """abstract class CameraActivity: BaseActivity(), ICameraStateCallBack {
    // BSG_USB_HEALTH_114: no buffers/bitmaps, only cheap lifecycle signals.
    protected open fun onUsbSurfaceAvailableForDiagnostics() {}
    protected open fun onUsbSurfaceDestroyedForDiagnostics() {}
    protected open fun onUsbTextureUpdatedForDiagnostics() {}
    // RC15: use the EXISTING AUSBC monitor, never register a duplicate receiver.
    protected open fun onUsbDeviceAttachedForApp(device: UsbDevice) {}
    protected open fun onUsbDeviceDetachedForApp(device: UsbDevice) {}
    protected open fun onUsbDeviceConnectedForApp(device: UsbDevice) {}
    protected open fun shouldAutoOpenUsbCameraForApp(device: UsbDevice): Boolean = true""")
patch(activity,
      """            ) {
                registerMultiCamera()
            }

            override fun onSurfaceTextureSizeChanged(""",
      """            ) {
                onUsbSurfaceAvailableForDiagnostics()
                registerMultiCamera()
            }

            override fun onSurfaceTextureSizeChanged(""")
patch(activity,
      """            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                unRegisterMultiCamera()
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }""",
      """            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                onUsbSurfaceDestroyedForDiagnostics()
                unRegisterMultiCamera()
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                onUsbTextureUpdatedForDiagnostics()
            }""")
# RC15 USB attach/detach/connect hooks. Base cleanup and permission routing stay intact.
patch(activity,
      """                generateCamera(this@CameraActivity, device).apply {
                    mCameraMap[device.deviceId] = this
                }""",
      """                generateCamera(this@CameraActivity, device).apply {
                    mCameraMap[device.deviceId] = this
                }
                onUsbDeviceAttachedForApp(device)""")
patch(activity,
      """            override fun onDetachDec(device: UsbDevice?) {
                mCameraMap.remove(device?.deviceId)?.apply {
                    setUsbControlBlock(null)
                }
                mRequestPermission.set(false)
                try {
                    mCurrentCamera?.cancel(true)
                    mCurrentCamera = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }""",
      """            override fun onDetachDec(device: UsbDevice?) {
                mCameraMap.remove(device?.deviceId)?.apply {
                    setUsbControlBlock(null)
                }
                mRequestPermission.set(false)
                try {
                    mCurrentCamera?.cancel(true)
                    mCurrentCamera = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (device != null) onUsbDeviceDetachedForApp(device)
            }""")
patch(activity,
      """                    mCurrentCamera?.set(camera)
                    openCamera(mCameraView)""",
      """                    mCurrentCamera?.set(camera)
                    onUsbDeviceConnectedForApp(device)
                    if (shouldAutoOpenUsbCameraForApp(device)) openCamera(mCameraView)""")
# Access to hardware capabilities, not software color effects; UVC check occurs only
# on the control executor after OPENED (the library warns that immediate queries fail).
patch(uvc,
      """    fun resetSaturation() {
        mUvcCamera?.resetSaturation()
    }""",
      """    fun resetSaturation() {
        mUvcCamera?.resetSaturation()
    }

    // BSG_UVC_PICTURE_CONTROLS_114: supported bits come from the REAL UVC device.
    // 0=brightness 1=contrast 2=saturation 3=gain. No Java-side pixel filters.
    fun supportsBlueStarPictureControl(kind: Int): Boolean {
        val camera = mUvcCamera ?: return false
        val flag = when(kind) {
            0 -> UVCCamera.PU_BRIGHTNESS
            1 -> UVCCamera.PU_CONTRAST
            2 -> UVCCamera.PU_SATURATION
            3 -> UVCCamera.PU_GAIN
            else -> return false
        }
        return camera.checkSupportFlag(flag.toLong())
    }""")
patch(uvcbuild, "abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'",
      "abiFilters 'arm64-v8a'")
patch(nativebuild, "abiFilters 'armeabi-v7a','arm64-v8a', \"x86\", \"x86_64\"",
      "abiFilters 'arm64-v8a'")
print("BSG_CLOUD_RAW5_GATED=PASS")
print("BSG_CLOUD_CLOSE_BARRIER=PASS")
print("BSG_USB_HEALTH_114=PASS")
print("BSG_UVC_PICTURE_CONTROLS_114=PASS")
print("BSG_USB_AUTOSWITCH_115=PASS")
print("arm64-only native compilation=PASS")
