# 蓝星向导1.0.9原生USB直显实验（2026-11-06）

## 研发背景：必须同时解决两种卡顿

用户反复反馈：USB 1920×1080摄像头在**始终处于APP前台**时也会卡顿，以及**切换到其他应用再返回**时也会卡顿。1.0.8日志在正常前台时持续约60次GL提交/秒，T+105374～T+105379后台切换后发生>9s无新帧，OCR=false、EGL错误=0。旧1.0.7也曾在纯前台出现短时0～3FPS，因此禁止把问题简单归为后台焦点恢复。

用户此前上传的USB Camera APK，原生`libUSBVideo.so`中可静态核实`uvc_stream_start`、`ANativeWindow_setBuffersGeometry`、`ANativeWindow_lock`、`ANativeWindow_unlockAndPost`相关实现标识；DEX有`USBCameraService`和nativeStart/StopPreview/SetPreviewDisplay接口。这支持“原生摄像头采集与Android显示分离/直接传递”的架构方向，但不能仅凭静态APK得知其全部实际帧率/生命周期算法。**未提取、复制或分发第三方专有代码/库**；重新使用现有Apache开源AUSBC3.6.0已有的原生直显`RenderMode.NORMAL`通路。

## 1.0.9方案：不是给旧GL层继续打补丁

1. `MainActivity.getCameraRequest()`从`RenderMode.OPENGL`改为`RenderMode.NORMAL`，保留1920×1080 MJPEG和`setRawPreviewData(true)`。
2. 在固定上游`MultiCameraClient.MSG_START_PREVIEW`中，`NORMAL`经`openCameraInternal(TextureView)`直接执行`mUvcCamera.setPreviewTexture(cameraView.surfaceTexture)`；原生UVC/MJPEG通过Android`ANativeWindow`直接显示。整个**应用层RenderManager、额外OES纹理、GL渲染消息队列都不在此路径**。新工作流停止应用1.0.7的GL合并补丁和1.0.8的GL-buffer修正，仅复用原生UVC最高支持FPS选择代码`scripts/patch_native_uvc_fps_only_109.py`和1.0.6原生≤20FPS NV21 OCR回调门控。
3. 仅用`TextureView.SurfaceTextureListener.onSurfaceTextureUpdated`原子计数`DiagnosticRenderHeartbeat.noteDirectSurfaceUpdate`，界面和日志改报`直显更新`，不再把它误称GL提交。**该事件仍不保证真实屏幕面板逐帧刷新**。`uvcJava/appRaw≈17FPS`依旧是OCR侧≤20FPS门控，不代表USB底层真实只有17FPS。
4. `onPause`对已打开UVC发起一次完整关闭，避免保留过期的UVC原生流与TextureView；原版`CameraUVC.closeCameraInternal`原先在`destroy()`**之前**通知`State.CLOSED`，本轮改成`mUvcCamera.destroy()`完成后才发CLOSED。返回前台仅在旧流完整关闭、TextureView可用、USB权限仍在时重新打开，不重复同时创建UVC会话。
5. 前台持续工作时，如果已经打开6.5秒、连续3.5秒没有新TextureView更新，且过去20秒没有做过同类尝试，则走同一条“彻底关闭—完成后重开”的小型恢复路径。它是硬停流下的一次重启，不是伪造帧率或增加复杂帧补救。真正USB底层总线/硬件故障仍可能无法恢复。
6. 暂停实验性UVC自动对焦锁定/解除命令（尤其是resume的`AF REFOCUS`），保留上游在启动时默认开启的相机AF，隔离它可能导致的控制传输干扰。手机内置摄像头Camera2、OCR模型/顺序/TTS不改。

## 源码和构建

- 独立分支：`exp/usb-native-direct-109-20261106`，继承1.0.8实验但**运行时绕过整个旧GL链**。
- 关键文件：`app/src/main/java/com/vobichngan/kanzi/MainActivity.java`、`UsbCameraHealthMonitor.java`、`scripts/DiagnosticRenderHeartbeat.java`、`scripts/patch_native_uvc_fps_only_109.py`、`scripts/patch_usb_native_direct_109.py`、`scripts/test_usb_native_direct_109.py`。
- 固定使用开源`ernestp/AndroidUSBCamera` 3.6.0的`RenderMode.NORMAL`与`UVCCamera.setPreviewTexture`，不替换成未知APK内专有二进制。
- 独立包名 `com.vobich.kanzi.experiment.dev109`、versionCode 109、versionName `1.0.9-development`。
- CI第一次因旧RAW20脚本的UI文案断言失败，第二次因`patch_usb_trace_103`已插入SURFACE AVAILABLE事件导致精确定位锚点失败；两次均拒绝产包并逐项修复。最终全部编译和APK静态质量门槛通过。
- **GitHub Actions run 42069930829 SUCCESS**，构建源commit `88c60d63076dea7e11f5e59790d2d9ebcc76d4a0`：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42069930829 。
- Actions测试产物ID `13437085710`：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42069930829/artifacts/13437085710 。在2026-11-15 UTC 17:03过期。压缩包内`BlueStarGuide_Experimental_1.0.9_arm64.apk`大小 **79,382,560 bytes**，SHA256 **`1d69d648f63689d8008b41051084339d0a826219dcdb7abd7217957b4c677626`**。
- 已重新取回GitHub Actions Artifact ZIP至工作容器，独立解压验证SHA256SUMS、DEX内`BSG_USB_DIRECT_109`、`BSG_USB_DIRECT_CLOSE_BARRIER_109`、`noteDirectSurfaceUpdate`，arm64 `libUVCCamera.so`内`UVC_FPS_NEGOTIATION_107`和`UVC_NATIVE_OCR_RAW20_GATE_ENABLED`；确认**无1.0.7/1.0.8应用GL补丁标记**，全部PASS。构建验证不证明真机显示正常。
- 仅CI artifact提供独立测试APK，没有创建GitHub Release、没有修改或合并稳定版源代码。

## 真机必须验证的两个场景（均未验证）

- **前台持续运行**：同样的vivo X200 Pro、USB摄像头、1920×1080，先关闭OCR持续3～5分钟，再开启OCR持续3～5分钟；观察直显更新帧率、实际画面、是否冻结/灰块及自动恢复事件。真机不应几秒后0FPS。
- **其他APP切回**：启动后切换其他应用再返回（至少重复3次），看`DIRECT_CLOSE`、`BSG_USB_DIRECT_CLOSE_BARRIER_109`、`DIRECT_REOPEN`以及直显更新是否恢复到约60；不可反复黑屏、冻结。
- 对照保留1.0.6（约30FPS但稳定可回退）。如真实60导致更严重的原生解码压力，优先稳定性，不能把60FPS数字当作全部质量指标。
- 用户已提供丰富截图/诊断；**不要重复要求提供相同截图**。如果v1.0.9真机仍失败，需要根据DIRECT与原生UVC计数判断下一步是否改真正视频采集底层，不要再在GL或OCR上反复补丁。
