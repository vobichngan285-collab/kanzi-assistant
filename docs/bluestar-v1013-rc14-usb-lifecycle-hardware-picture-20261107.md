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
