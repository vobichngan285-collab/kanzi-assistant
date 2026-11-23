# 蓝星向导 v1.0.80｜手机高清约800万像素单项A/B实验

日期：2026-11-23。仓库：`vobichngan285-collab/kanzi-assistant`。
基线：v1.0.79（`bf05a9521f6243c33f440c9cc586f6348c9a72cb`）；分支：`exp/phone-hd8mp-trial-20261123`。

## 用户授权和最小改动

用户明确表示“可以开始”，要求**仅将手机高清拍照从1200万像素改为约800万像素作实验**，然后依据真实日志比较首次听到第一句话是否改善。不得附带修改流式上传、连续拍照、千问默认图片处理参数、模型、提示词、TTS、C110、USB或其他功能。

- `PhoneStillJpeg.MAX_UPLOAD_PIXELS` 从 `12000000L` 改成 `8000000L`，兼作`InternalCameraController.chooseStillSize`的真实Camera2 JPEG选择目标和高清JPEG上传像素上限。
- `InternalCameraController`仅将选择尺寸相关注释中的“12MP”同步改为“8MP”；`chooseStillSize`已有的评分算法、原生支持尺寸枚举、像素与长宽比偏好均保持原样，不从预览图强行放大。
- v1.0.80版号（`versionCode 89`；`versionName 1.0.80-phone-hd8mp-trial`），使用与上版相同的`com.vobich.bluestar.c110plusquick159` debug应用包名，不生成第二套新应用。
- 手机**快速模式**（选择720P上传），C110原始JPEG透传，USB及C110取帧和原厂硬件控制，云端模型/参数，v1.0.79 `setFixedLengthStreamingMode(request.length)`，v1.0.78旧任务取消/TTS/按钮及UI均不动。

## 为什么可能显示775万，而不是整800万

这里的“800万”是目标，不是伪造的固定输出尺寸。Camera2必须从该手机实际支持的`ImageFormat.JPEG`原生尺寸中选择最接近800万、兼顾16:9的档位。以该设备以前真机确认过的`3712×2088`为例，像素`7,750,656`（约775万），如其评分优于设备当前其它尺寸，则该档会被选择。镜头尺寸、裁剪、旋转后，日志里的raw/upload尺寸须以真机实际数据为准。绝不把1080P小图插值到800万冒充原生照片。
上限为800万像素，若相机只提供略高于目标的原生JPEG，则`PhoneStillJpeg.prepare()`仍按照旧有逻辑等比缩小到最多800万；不改变JPEG质量参数、画幅旋转与镜像。

## A/B真机验收（不要只看上传字段）

- **v1.0.79基线**：原1200万，`camera.raw_width_px=4096`、`camera.raw_height_px=3072`，实际上传`jpeg.width_px=3000`、`jpeg.height_px=4000`；之前连续测试`tts.onstart_ms`约2.3–3.6秒（引擎回调，不等于实际播放），云端具有波动。
- **v1.0.80实验**：相同wifi/移动网络、同一千问模型和识别任务、距离角度/照明与同一段文字，先拍5次，检查`camera.phone_still_jpeg=1`、`camera.raw_width_px / raw_height_px`、`jpeg.width_px / height_px`、`jpeg.bytes`、`camera.phone_still_ms`、`jpeg.encode_ms`、`cloud.first_speakable_ms`、`tts.onstart_ms`、`tts.first_audio_callback_ms`、`capture.total_ms`、以及用户真正听到第一句话的体感。
- 比较应剔除旧任务被连续新拍照取消而缺少完整网络统计的会话；对比**同一时段交替**测试会减少云端波动干扰。若画质/小字漏读变差，记录下来并与12MP基线比较。
- 验证720P快速模式、旧照片等待时二次拍摄、C110真实原图、USB/TTS及正常安装覆盖；不动云端请求；若800万未改善首句或降低识别率，可保留1.0.79旧版本回退，不要为了调试卸载导致Android Keystore密钥丢失。
- GitHub CI能校验精确diff/原有Java回归/Android构建与APK，但无法替代真机验证手机实际支持的JPEG尺寸、首句话真实出声及千问延迟。

