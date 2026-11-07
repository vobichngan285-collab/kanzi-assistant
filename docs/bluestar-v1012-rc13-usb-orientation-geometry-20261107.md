# 蓝星向导 1.0.12 RC13 — USB 方向与 JPEG 一致性专项修复

来源：原仓库 RC12 源码（commit `d896c0970372bda1e9904d73ce7dfc9293a0bacc`）与用户 vivo X200 Pro 截图。用户授权逐项修复、验证、发布 APK。本文件不代替 GitHub Actions/真机验证。

## 截图复现配置与原因

- USB 摄像头：旋转 **90°**，水平镜像**关闭**，垂直翻转**关闭**，USB 左右校正**开启**，缩放 **1.0×**。
- 吉他实际在用户右手侧，却出现在屏幕左侧；用户切换“USB 左右校正”感觉是上下颠倒。
- 旧代码在旋转前对 TextureView 本地 X 轴施加 USB 左右镜像，因此旋转 90°/270° 后效果变成屏幕 Y 轴翻转；另外上传 JPEG 走旋转后的独立 `photoMirror/photoFlip` 公式，不同开关组合还会与预览不一致。

## 源码调整

1. `UsbFrameOrientation.forSettings(rotation,horizontalMirror,verticalFlip,usbCorrection)` 是唯一方向方案：用户的“USB 左右校正”及“水平镜像”均指**最终屏幕 X 轴**，合并为异或；“垂直翻转”指最终屏幕 Y 轴。180°与0°不交换轴，90°/270°交换局部 X/Y 轴。预览在旋转前执行局部变换；JPEG 在旋转后执行最终显示坐标变换。复用同一个方案，不再拼凑分散公式。
2. 独立 JVM 测试 `UsbFrameOrientationTest.java` 遍历四种旋转 × 三开关全部八种组合 = **32 组**，用不对称 NV21 四角与 UV 数据逐字节比较“预览局部翻转后旋转”和“JPEG 旋转后显示翻转”；特测用户实际 90° USB 校正仅移动最终左右位置而不倒置。
3. USB 原始 NV21 的实际年龄记录到 `camera.usb_frame_age_ms`，拒绝年龄超过 800ms 的缓存帧（USB 回调最高约 5Hz）；原先始终为0的假 `fresh_frame_ms` 不再用于 USB。记录源宽高、JPEG 实际宽高、角度、镜像和翻转（仅数值）。
4. 运行诊断新增手动「查看上次发给 AI 的照片（仅本机）」，直接解码**此次准备发送的原始 JPEG bytes**，不重压缩、不额外联网、不增加收费请求、不持久化照片；只在应用存活期间 RAM 保留，退出销毁释放。原匿名诊断仍然仅存数字、阶段和状态。
5. 原本手机 Camera2 路径、UVC 原生预览帧率、百炼 OCR/Max/Flash、ASR/TTS、提示音、任务编辑及排序删除均不更改；图片质量 auto 720/1080 决策保持原值，新增分辨率数值供后续单独验证。
6. 独立新包 `com.vobich.bluestar.rc13` / `1.0.12-rc13` / versionCode 22，与 RC12 并存；需重新填写北京百炼 API Key。

## 严格验证与尚未完成

- Android Actions 必须验证源码/Android 编译、真 NV21 像素32组合、图标、USB arm64库、音效、无本地 OCR 模型、包名版本、APK SHA256。
- 原上传 JPEG 手动检查须由用户在 vivo X200 Pro 验证是否与屏幕预览方向、实际左右符合；同图再对照 USB 自动720与手动1080文字准确率。
- USB 只有最新缓存帧快照，不是拍照时调用 UVC 获取全新帧；通过帧龄限制避免过时而不重写稳定 native UVC 数据链。真机无法从 CI 推定通过。
- 此文在 CI 前建立，最终 build/release 的 run、SHA、APK 大小等在成功后追加，不能预设成功。
