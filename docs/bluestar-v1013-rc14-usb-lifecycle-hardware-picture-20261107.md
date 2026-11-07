# 蓝星向导 1.0.13 RC14：USB 前后台流畅度诊断 + 硬件画质调节

用户在 RC13 vivo X200 Pro 上确认 USB 方向和文字乱码已解决；新的要求是排查切后台再返回后的 USB 预览卡顿，并增加应对光线略强即画面发白的亮度/饱和度等调节，用户已明确授权执行。

## 本轮范围与架构选择

- **保留** RC13 `UsbFrameOrientation` 32种方向矩阵、NV21/JPEG一致性、USB 画面直接 TextureView、5Hz 用于快照的 UVC NV21回调、云端 OCR/Flash/Max、ASR/TTS、图标/音效、任务删除和独立应用包；**不通过拍照重压缩/屏幕滤镜来模拟 USB 画质**。
- **增加被动诊断，不自动重启 USB**：修改固定上游 `ernestp/AndroidUSBCamera@3.6.0` 中已有的 `CameraActivity.kt` TextureView listener，只添加 `onUsbSurfaceAvailableForDiagnostics`、`onUsbSurfaceDestroyedForDiagnostics`、`onUsbTextureUpdatedForDiagnostics` 的保护性可重载 hooks。绝不替换原 listener（替换会破坏 USB attach/open）；`onSurfaceTextureUpdated` 只做 AtomicLong 增量及毫秒时间戳，不取 Bitmap、不克隆帧、不写日志。 `MainActivity` 每2秒采样 `usbFrameCount` 与 `usbTextureCount`，按开启后3.5秒预热、1.8秒无帧门槛区分正常、原始帧停、预览停、两路停。前3次及状态变化写有限匿名数值事件：`resume/pause/open_requested/close_requested/OPENED/CLOSED/ERROR`、USB帧率×10、预览更新率×10、最后帧距今；`RuntimeDiagnostics`仍限定50次/7天、每次24事件，不含图像/文字/声音/密钥。TextureView更新 ≠ 物理屏幕上屏fps。
- **只做真实 UVC 硬件画质**：固定上游 `UVCCamera` 原有 `set/getBrightness`、`Contrast`、`Saturation`、`Gain`、reset 的百分比接口，`CameraUVC.kt` 仅加 `supportsBlueStarPictureControl`，用 `UVCCamera.checkSupportFlag(PU_*)` 查询真实设备支持。仅在打开相机且支持的项启用滑块，滑动**松手**才通过串行后台 executor 发一次 native USB 控制，不阻塞 UI；读取确认后按 USB VID/PID 保存，后续打开时延迟700ms、核对当前相机 generation 后仅恢复已保存的已支持项目。不受支持的项目不可点击；提供恢复设备默认值并清空对应记忆。
- **曝光说明**：上游该版本未提供简洁的自动曝光/曝光补偿对外稳定 API，因此不臆造“曝光滑块”。强光发白可能是摄像头自动曝光过度、传感器动态范围不足或背光；降低亮度/增益可能改善观感，但不能恢复原图已饱和的信息，真实效果需真机看。原生硬件设置通过后，会作用于 UVC 输出给预览和 NV21 回调的成像源。
- 版本 `com.vobich.bluestar.rc14`，`versionCode 23`，`versionName 1.0.13-rc14`，debug ARM64 独立包；保留 RC13 安装不覆盖。需要在 RC14 重新配置北京百炼 Key/任务。
- **测试计划**：Java `UsbStreamHealthTest` 合成健康、原始帧停、预览停、双路停、预热；`UsbPictureLevelsTest` 检查 0..100 边界；原32组 USB 方向测试原封不动；静态权限/隐私审计；AUSBC 3.6.0原生补丁唯一锚点；完整 arm64 Gradle 编译、图标与音效、APK包名和散列。运行诊断在真机上通过 10 次前后台切换及持续前台 5 分钟比对，按两路计数定位延迟再决定是否修复重连状态机或确认线材/供电问题。
- **CI与真机边界**：此文在 CI 前编写，不冒充已经构建成功。硬件相机是否支持不同画质项必须实际查询，且值可能受自动曝光算法影响。现阶段不修改 USB 开关相机时序、不自动周期重连，避免以前那种越补越复杂的故障链。


## RC14 GitHub Actions 原始日志和发布结果（2026-11-07 完成）

- **原 CI 源码 SHA `98d6996d0c6dd72347b1e763c23922d3d6a8ec2c`**。GitHub Actions **[42340452858](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42340452858)** 已确认 `completed/success`。同一 Job 日志逐条验证：
  - `DUAL_CAMERA_CLOUD_AI_NO_LOCAL_OCR_AUDIT=PASS`，无密钥或本地 OCR 引擎；
  - `RC13_USB_PREVIEW_JPEG_32_COMBINATIONS_AND_90_CORRECTION=PASS 32`，保持上一版方向修复；
  - `RC14_USB_LIFECYCLE_FPS_4_STATES=PASS`，含启动预热、两路健康、两路停帧与单侧停帧；
  - `RC14_USB_PICTURE_CONTROL_BOUNDS=PASS`，百分比和可用控制类型边界；
  - `CLOUD_OPTIMIZATION_UNIT_TESTS=PASS`、`TASK_ORDER_DRAG_RECONCILIATION_TESTS=PASS`，原有行为未被替换；
  - `BSG_USB_HEALTH_114=PASS`、`BSG_UVC_PICTURE_CONTROLS_114=PASS`，钉住的 AUSBC 3.6.0 原生 hook 与硬件支持探测补丁成功；
  - Android ARM64 `BUILD SUCCESSFUL in 2m 26s`，APK 压缩包结构、图标、咔叮和嗖声资源、原生 UVC `.so` 以及 `aapt` 包信息通过。
- **真实 CI Artifact** ID `13561895586`，工件名 `blue-star-guide-1-0-13-rc14-arm64`，包含 APK 与原始 SHA256SUMS、APK 文件列表及包信息。前两个中间 workflow `42340411341`、`42340443811` 因提交补上 Kotlin `Int.toLong()` 原生支持位转换及同步静态测试而被并发机制**取消**，不将其记录为编译失败/成功。
- **Release 独立原件发布**：workflow **[42340716193](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42340716193)** 确认 `completed/success`。发布前严格校验成功的原 CI run 与精确 SHA、`sha256sum -c SHA256SUMS.txt`、完整 APK ZIP CRC、UVC arm64 so、两音效、图标、无本地OCR模型及 Android package `com.vobich.bluestar.rc14` / versionCode23 / versionName`1.0.13-rc14`；**只上传编译通过的原始 APK，没有再次编译、签名或替换**。
- **公开 GitHub Release tag** `blue-star-guide-cloud-rc14-20261107`：`draft=false`、`prerelease=true`、APK asset `state=uploaded`。APK `BlueStar_Guide_1.0.13_RC14_arm64.apk` 大小 **8,584,364 bytes**，GitHub asset digest SHA256 **`0f90ff2e13cb3a0a87fa218e1c5be3a3fb3ff53207c62d53a4f4e2e36c94a44e`**。
- **可直接在手机浏览器下载安装的官方 APK 链接**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc14-20261107/BlueStar_Guide_1.0.13_RC14_arm64.apk
- **未完成/不能冒称**：GitHub 合成测试与 CI 不能证明 vivo X200 Pro 真机 USB 切后台再返回不卡顿，RC14 仅加入必要观测，尚无经确认的底层根因修复；硬件亮度/对比度/饱和度/增益是否支持、是否能降低过曝必须以用户所接 USB 摄像头的实际能力与视效为准。新包不继承 RC13 沙盒的 API Key、自定义任务、摄像头方向设置。
