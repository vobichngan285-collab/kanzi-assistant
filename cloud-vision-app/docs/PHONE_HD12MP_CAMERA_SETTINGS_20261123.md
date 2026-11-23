# 蓝星向导 v1.0.77 — 手机高清约1200万像素、上传画质选项归位
日期：2026-11-23；基线：已发布 v1.0.76 SHA `2a560da336223bcf4c6da12075382149a1620e31`。

## 用户本轮明确授权（仅这两项）

1. 手机 Camera2 高清拍照由此前约800万像素改为**优先接近1200万像素的真实静态 JPEG 输出**；不得把低分辨率预览图插值冒充高清。
2. 将“云端AI → 图片分辨率（自动720P/必要时1080P、固定720P、固定1080P）”从云端设置移入**相机设置**并明确标记为“手机/USB预览上传”，避免误以为高清静态 JPEG 会被1080P限制。

## 实际代码及兼容性

- `InternalCameraController.chooseStillSize()` 读取手机实际 `SCALER_STREAM_CONFIGURATION_MAP` 的 `ImageFormat.JPEG` 原生输出列表，优先选择最接近1200万像素者；仍轻微偏好16:9，但不强制。该选择不是虚构的固定4000x3000；若手机设备未暴露近1200万像素尺寸，实际尺寸可能不同。
- `PhoneStillJpeg.MAX_UPLOAD_PIXELS` 从800万提升到1200万像素，并作为 Camera2 选档和上传上限**唯一常量**。若原生 JPEG 像素高于1200万，按比例缩小并转换方向/镜像；如果不超限且无需旋转/镜像，沿用原JPEG字节。不写相册。
- 以前「云端AI」的预览帧画质选项仅**移动显示位置**，并未更改 `MainActivity.imagePolicy`、`cloud_capture_quality` 持久化键、原 `FrameCompressor` 画质算法或其 `auto/720/1080` 语义。新位置「设置 → 相机 → 手机/USB预览上传」。仅影响手机快速拍照与USB预览截帧；不影响手机HD JPEG、C110 OEM JPEG。
- 「设置 → 相机 → 手机拍照模式」原快速/高清两种保留，高清选项注明约1200万像素，偏好键仍 `phone_true_hd_still_v1`；首次仍默认快速。
- 本轮不修改 `MainActivity.java`、`FrameCompressor.java`、`CloudVisionClient.java`、`HomeActivity.java`、C110+与普通C110设备识别、USB驱动、追问、OCR、云端模型/TTS、Keystore密钥、快捷键或主页布局，也不执行此前未获授权的其他待办。

## CI与真机验收

- GitHub Actions CI对比 v1.0.76 实际 SHA，必须精确6文件变更；静态检查常量、档位选择代码、设置入口仅出现在相机、原有共享云端链/相机快捷模式/USB/C110代码保留及用户持久化键不变。
- 继续跑既有Java回归、固定UVC补丁、Android arm64 全量Gradle编译与 APK ZIP、包名、唯一Home Launcher、LibVLC、UVC、音效校验，成功后发布1.0.77 APK。
- **真机尚须验证**：vivo X200 Pro 后置高清模式连续3～5张，日志核对 `camera.phone_still_jpeg=1`、`camera.raw_width_px/height_px`、`jpeg.width_px/height_px`、`jpeg.bytes`、`camera.phone_still_ms`、`cloud.first_speakable_ms`、`capture.total_ms`，与此前775万像素对比小字漏读；看「上次实际发送给 AI 的 JPEG」确认像素、文字方向正确。检查选项自动/720/1080对手机快速和USB生效、对高清不生效；手机快速与C110/USB及声音、追问不回归。
- 不保证目标手机原生恰好1200万，更不能以 CI 成功声称拍照曝光、实际清晰度或扬声器已经真机验收。
- 若覆盖安装签名冲突，不自动卸载旧包，以免丢失Keystore密钥。保留旧 v1.0.76 APK供回退。

