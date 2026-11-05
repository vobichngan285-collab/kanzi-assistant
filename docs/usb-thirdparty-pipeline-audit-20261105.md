# USB 摄像头第三方 APK 与蓝星向导采集链路静态对比（2026-11-05）

## 结论和约束

本次分析针对用户提供的独立 APK，SHA256 `9f0568bf00872f42688dfa6926c1a826c08d85f9a753dc850313cbdf9705eda3`（91,344,733 bytes）；与蓝星向导基于 `ernestp/AndroidUSBCamera@3.6.0` 的USB采集路径进行静态对照。没有运行第三方 APK，没有测真实帧延迟、耗电或温度；第三方专有源码不能直接使用。用户此前真机观察其显示约60.2 FPS / MJPG 1920x1080，且主观流畅；这是现场线索，不能由反编译直接推导真实性能指标。

**主要可操作发现：蓝星向导每个视频帧都会进行NV21原生转换、Java `ByteArray` 分配和整帧复制，甚至在OCR未启动时也如此；其1080p/60FPS额外Java层字节分配达到理论约186.6 MB/s。** 这是明确存在的开销，但没有GC trace/性能分析不能称为卡顿根因。另有每个 `onFrameAvailable` 都向GL线程发送绘制消息、无显式合并的潜在排队风险。

## 第三方APK可核实事实

- `lib/arm64-v8a/libUSBVideo.so`：动态依赖 `libandroid.so`、`libEGL.so`、`libGLESv2.so`；导入 `ANativeWindow_*`、`eglSwapBuffers`、`glTexImage2D` / `glTexSubImage2D` 和 `libusb_bulk_transfer`；二进制内包含带Y、UV纹理采样的着色器字符串、`GLRenderer`。这证明具备原生画面渲染能力，但静态信息不足以确定本次MJPG/1080p运行时到底走哪一条渲染支路。
- `libUSBVideo.so` 内包含 `libjpeg-turbo version 3.2.0 (build 20260706)` 和 `jpeg_CreateDecompress` 等符号。版本号比项目固定使用的 `libjpeg-turbo-3.1.3` 新，但**不能仅凭版本号断言更快或实现硬件解码**。
- 二进制能确认JNI注册的 `nativeGetCurrentFrameRate`、`nativeSetPreviewSize`、`nativeSetRawCallback`、`nativeSetFrameCallback`、`nativeSetEnableMJPGOutput`、`nativeGetCtrlSupports`、`nativeGetProcSupports`、`nativeSetAutoFocus`、`nativeSetFocus`、`nativeSetZoom` 等方法。存在这些接口**不能证明**其实际回调采样率、默认是否启用原始帧回调、物理光学变焦支持。
- DEX 中存在 `com/shenyaocn/android/USBVideo/UVCCamera` 以及 `USBCameraService` 类，说明摄像头管理不仅局限于Activity；但不能直接推断前后台稳定性来自Service，更不能证明服务持续活跃或具有某种防卡死机制。
- 该产品公开说明其支持前后台切换，并有由菜单主动启用的后台运行模式；与蓝星向导的无障碍读屏使用目的不同，不宜直接照搬后台摄像头生命周期。参考 <https://play.google.com/store/apps/details?id=com.shenyaocn.android.usbcamera>。

## 蓝星向导源码可核实的具体链路

1. `MainActivity.getCameraRequest()` 请求 **1920x1080 / MJPEG / OPENGL / `isRawPreviewData(true)`**；`onCameraState(OPENED)` 在启动后注册 `addPreviewDataCallBack(previewCallback)`。
2. 原生源代码 `libuvc/src/main/jni/UVCCamera/UVCPreview.cpp` 在MJPEG路径明示 `uvc_mjpeg2yuyv`，随后 `draw_preview_one(..., uvc_any2rgbx, 4)` 供native Surface/GL预览使用，同时 `addCaptureFrame(frame)`，由回调支路转换为半平面YUV。两条输出均建立在已经解码的YUYV上。
3. `CameraUVC.kt` 的 `mUvcCamera?.setFrameCallback(frameCallBack, UVCCamera.PIXEL_FORMAT_YUV420SP)` 在 `isRawPreviewData` 为true时启用；**每帧** `ByteArray(capacity())` + `frame.get(data)`。随后无条件 `mNV21DataQueue.offerFirst(data)`（队列容量5），并广播 `mPreviewDataCbList`；即使APP OCR空闲，原生YUV转换和Java分配仍发生。 `MainActivity.previewCallback` 再按需运行 `processCameraFrame`，实际全OCR只对成功争取到执行槽的帧 `data.clone()`。
4. `RenderManager.kt` 的 `onFrameAvailable` 每次发送一条 `MSG_GL_DRAW`，GL线程进行 `updateTexImage`、绘制效果、屏幕draw和swap。当前此处没有基于 pending 标志的消息合并；**是否在60fps下积压**需要用队列深度/帧时间实测，不可直接断言。
5. YUV格式注意：`UVCCamera.java` 的常量4注释称NV12，但本固定native版本中 `UVCPreview.cpp` 对格式4调用 `uvc_yuyv2iyuv420SP`，`frame.c` 实际输出V、U交错（NV21）；故当前APP把回调标记为NV21与该具体native转换结果一致。**不能仅根据Java注释反推已发生色彩通道错乱**。第三方颜色更浓亦可能来自白平衡、亮度范围或颜色矩阵不同，必须同画面A/B校验。

## 帧开销（理论，不是运行数据）

1920×1080 的NV21每帧 = 1920×1080×1.5 = 3,110,400 bytes。30FPS持续按帧新建Java数组 = 93,312,000 bytes/s；60FPS = 186,624,000 bytes/s。未计本地YUYV/RGBX解码与转换、surface buffer、JNI跨界开销、重分配、缓存命中和GC，也未将整个值等同于真实总内存占用。

## 优先实验与准入门槛

1. **先做同设备A/B，不更改文字保真链路**：第三方APP和蓝星向导分别设置1080p MJPG，区分预览空闲、运行OCR、前后台切换三种工况，记录UVC原生callback FPS、appRaw FPS、GL input/swap、CPU利用率、GC暂停/次数、耗电/温度及OCR延迟。目标是确定60FPS、画面真实更新与OCR的冲突程度。
2. **先减负再追求60FPS**：独立分支原型尝试在OCR无需数据时停止无意义的Java全帧复制/原生NV21转换，识别运行时仍保证以有限频率获取最新真实帧，避免在Java层存储所有帧，且维持翻页检测频率。难点是UVC `setFrameCallback(null)` 可能阻塞捕获同步，需避免在每帧/每次OCR之间反复注册注销。缓存池设计必须确保异步OCR持有的图像不会被覆写。
3. **独立验证GL绘制消息合并**：对比未合并/最新帧优先，检查预览是否延迟积压、是否破坏EGL/Surface尺寸改变。
4. **仅在收益可测时考虑切换解码技术栈**：两软件都含libusb与JPEG解码；第三方的GPU着色器、原生渲染、原始帧回调接口值得做接口设计参考，但其运行路径和实际FPS/延迟尚未证明，不应盲目重写、移植私有代码或声称可零拷贝。
5. **功能保护**：CPU离线OCR原文/顺序、TTS、读屏安全设置保持原样，先不解决背景切换卡顿或调整曝光。帧率必须按实际测量和A/B回退判断。

## 版本状态

截至本次核对，独立硬件能力实验分支 `exp/blue-star-hardware-controls-105-20261105`，Actions run `42023363874` 已success；独立 1.0.5 APK 在 <https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-experimental-dev1.0.5-20261105> 可下载。该版添加只读UVC控制能力报告并在确认硬件支持AF后执行锁焦尝试，**未实施本报告的帧复制/解码优化**，不能以构建成功证明任何性能改进。
