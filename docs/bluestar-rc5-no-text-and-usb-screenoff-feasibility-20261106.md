# 蓝星向导 RC5：OCR 无文字语音提示修复 + USB 真正息屏识别可行性（2026-11-06）

## 范围与可追溯基线
- 仓库：`vobichngan285-collab/kanzi-assistant`，上一正式候选独立分支 `fix/bluestar-103-demand-capture-diagnostics-20261106` 的只读 HEAD `7ae425de05194e9dfdeb0fef0fce3261e6a5abbf`。
- 本轮独立修复分支：`fix/bluestar-104-ocr-voice-empty-20261106`，不会覆盖 RC4 APK、RC3、离线 OCR 或 USB 核心分支。
- RC5 Debug 包名 `com.vobich.bluestar.rc5` / versionCode 14 / versionName 1.0.4-rc5。由于 CI Debug 签名无法可靠跨构建覆盖，新包与 RC4 并存，API Key 要在新包内单独填写。
- 本轮仅修复正常无文字的 UI/音频行为并添加纯 Java 测试；**不修改 USB、后台、实体键、服务、录音、TTS 引擎或云端模型**。GitHub Actions 的真实构建结果应单独核查，**不能用源码提交等同成功 APK 或真机验收**。

## 现象、源码根因与修复
- 用户 vivo X200 Pro 截图：朗读文字任务中出现阻塞式 `云端识别失败 / 没有识别到清晰内容 / 知道了` 弹窗。
- 原 RC4 `CloudVisionClient.recognize` OCR 非流式 HTTP 200、消息 content 为空时抛普通 `Exception("没有识别到清晰内容")`；`MainActivity.beginCapturedFrame` 的泛化异常处理不区分无文字与 HTTP/密钥异常，统一弹出对话框。
- 新增纯 Java `OcrResponseGate.requireReadable`；对成功响应的空、全空白内容抛独立 `NoReadableTextException`，非空识别文本仅去首尾空白，不修改字符或猜测内容；HTTP != 2xx、无 choices/协议出错等继续按真正云端故障处理。
- `MainActivity` 专门捕获 `NoReadableTextException`，检查请求 token 与活动状态，清除 `cloudBusy`，状态显示 `未识别到文字`，直接用本机 TTS `speakForCapture("未识别到文字，请调整拍摄角度再试。",token,false)`，不弹窗；保留网络错误的独立告警行为。
- 运行诊断仅追加 `cloud.empty_result = 0 [no_text]` 和 `capture.total_ms [no_text]`，不保存图片/识别文字/语音/密钥。TTS 不可用时既有提示仍会出现，**不虚称扬声器确实发声**。
- Java 回归涵盖 null、空串、仅空白、中文换行、带数字的原文，CI 还静态保证无文字专门 catch 无 AlertDialog，真实 HTTP 错误 catch 没有被删除。

## USB 熄屏抓拍：仅研究，未实现、未手机验证

### 当前 RC4 从源码已经确定的限制
1. `MainActivity.onPause` 会 `uiActive=false`、`usbForeground=false`、调用 `stopUsb()`，并取消任务/推理；`previewCallback.onPreviewData` 也在 `!uiActive` 时拒绝 USB NV21。按电源键使 Activity 暂停时，原方案不能继续稳定取图。
2. USB 使用 AUSBC 3.6.0 UVC + `TextureView` NORMAL 直显；锁屏以后 TextureView/Surface 生命周期不同。保留 raw 帧回调须在独立试验中验证 Native UVC 在 **无可见 Surface** 条件下是否仍可持续取帧，不可根据屏幕黑色假定是物理息屏。
3. App Manifest 当前仅有 Camera、Audio、Vibrate、Internet 等基本权限，**没有**已声明的合适 foregroundServiceType 与持续采集服务，亦没有锁屏音量键拦截能力。

### Android 机制与边界（基于 2026-11-06 查阅官方文档）
- USB Host 通常要用户授权访问 UVC 设备；视频 USB 设备访问还受 CAMERA 权限约束。来源： https://developer.android.com/reference/android/hardware/usb/UsbManager
- Android 14+ 前台服务需使用合适类别（视实现可能是 connectedDevice、camera 等），并遵循 camera/microphone 的「使用时」权限限制；必须在 Activity 可见阶段按规定启动服务，不得以偷偷唤醒后台相机作为默认策略。官方： https://developer.android.com/develop/background-work/services/fgs/service-types 及 https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- `PARTIAL_WAKE_LOCK` 能让 CPU 在熄屏后继续工作，但**不保证 UVC、Surface、USB 供电或媒体键投递**；应限定期限并安全释放，避免发热耗电。官方： https://developer.android.com/reference/android/os/PowerManager
- 普通 Activity 的音量键回调不等于屏幕锁定时系统仍会投递音量键。AccessibilityService `FLAG_REQUEST_FILTER_KEY_EVENTS` 可在合规获用户主动开启的前提下争取全局键事件，但不保证拥有事件（其他服务可能占用），且系统版本/厂商可能限制；不应未经用户许可申请无障碍权限或截取音量键。官方： https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo
- Alternative input（可选）：已明确绑定的蓝牙快门/媒体按钮、外接按键、通知明确操作等，必须以真实锁屏测试确认可用；不能把普通媒体音量按键可靠性当作既定事实。

### 后续最小隔离探针（需下一步用户明确授权）
1. 从已知 RC4 底层建立一个**独立诊断 APK**，仅完成「显示屏真实熄灭后 UVC 原生 RAW 有无递增、timestamp_age、USB permission、camera lifecycle」；后台显式持续使用时有前台通知、用户随时停用；**不联网、没有图像上传**。验证手机亮屏、遮罩黑屏、按电源键息屏、锁屏后至少数分钟，USB 拔插回收。
2. 仅当第 1 步通过，再测试屏幕锁定时音量加/减、蓝牙按键、通知触发。逐项记录是否投递而非猜测，并避免误改系统音量；必要时与用户讨论是否愿意明确开启无障碍权限。
3. 仅当真正熄屏下取帧+按键双重通过，再集成「一次按键→抓最新 JPEG→用户选定任务→百炼→本机 TTS」，限制功耗、费用、连续触发频率；预览与截图同向，保持摄像头原生直显和原有 RC4 能力可回退。

**现阶段结论：系统机制存在可能路线，但 vivo X200 Pro Android 16 真正息屏 + USB 持续取帧 + 机身音量键三条件同时满足尚无真机证据。不能宣称已支持。**

## RC5 构建与独立实包复核（2026-11-06）

- **最终构建源码快照**：`eca8c992afff5cc695cae9915b0fd5e5ef3d8b7e`（上述独立分支的 Workflow 与代码）；本报告后续文档提交不更改该打包快照。
- GitHub Actions Run [42244749476](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42244749476) **completed/success**，包含独立纯 Java 回归、静态安全检查、Android ARM64 Gradle 编译、真实 APK 验包、上游 USB native 编译、五密度图标生成、两套原音效校验及 Artifact 上传。编译通过不等于真实 vivo 手机上已听到音效/语音。
- Artifact ID `13516869885`，名称 `blue-star-guide-1-0-4-rc5-arm64`，下载 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42244749476/artifacts/13516869885 ，**到期约 2026-11-13 15:40 UTC**，不要称为永久链接。此版本**未创建公开 GitHub Release**。
- 从实际 Artifact ZIP 提取 APK 并重算 SHA，与 ZIP 内 SHA256SUMS 一致。文件 `BlueStar_Guide_1.0.4_RC5_arm64.apk`，大小 **8,551,588 bytes**，SHA256 **`e4ff506069b0c0592774bccd2f54a1e2de88499930bb9f540e014f1b68bb0d17`**；APK ZIP CRC 通过，包含 `lib/arm64-v8a/libUVCCamera.so`、`res/raw/c_metal_clack_ding.mp3`、`res/raw/voice_send_whoosh.wav` 与签名块；CI 内 `aapt` 查验 `com.vobich.bluestar.rc5`。
- **用户真机仍待测**：实际摄像头拍没有文字的物体，确认只有本机播报，不再出现阻塞弹窗；再拍正常中文文字确认照常识别朗读；断网或故意用无效 Key 验证真正故障仍明确提示。另需复验 USB 帧与前后台稳定，**息屏 USB 和机身音量键没有改代码、更没有通过真机测试**。
