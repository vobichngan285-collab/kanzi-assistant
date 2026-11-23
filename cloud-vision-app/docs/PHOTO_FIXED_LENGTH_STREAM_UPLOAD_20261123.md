# 蓝星向导 v1.0.79：图片HTTP固定长度流式上传A/B实验（只改这一项）

日期：2026-11-23，唯一原仓 `vobichngan285-collab/kanzi-assistant`。
基线：已发布v1.0.78 `83077e40dd4cbcc8099635a8167a413a17260648`。
本轮用户明确要求：**不改像素**；仅把此前由 `HttpURLConnection` 默认缓冲的图片JSON POST改为固定长度流式发送，再实机测试是否更快。

## 精确改动与技术边界

1. `CloudVisionClient.java`：原有 `body.toString().getBytes(UTF_8)` 构建的完整JSON `byte[] request` 不变。对 `!textOnly` 图片请求，在 `getOutputStream()` 前调用 `connection.setFixedLengthStreamingMode(request.length)`，用**UTF-8字节长度**而非字符长度；已有 `write(request)`、HTTP status/error解析、SSE流式**响应**、取消标记和`disconnect()`行为原样保留。无图片的纯文字追问保留原传输模式。
2. `TargetVisionClient.java`：将原本直接 `out.write(body.toString().getBytes(UTF_8))` 的字节数组提前生成，同样在 `getOutputStream()` 之前设置 `setFixedLengthStreamingMode(request.length)`。不改寻找目标的JSON内容、照片、模型或解析。
3. `LayoutParsingClient.java`：独立GLM/其他兼容版面解析的图片JSON路径同样先生成UTF-8字节数组，固定长度流式写入；其他接口、10MB限制、解析和云端模型参数不变。
4. `app/build.gradle` 仅版本升级：`versionCode 88`，`versionName 1.0.79-photo-stream-upload`；沿用已有debug安装包名 `com.vobich.bluestar.c110plusquick159` 和唯一启动入口。

**不增加新分辨率；**手机高清仍原生4096×3072拍摄、内部JPEG上传上限1200万，已真机证实的实际上传3000×4000；手机快速仍默认720×1280；C110保持原厂原始JPEG；JPEG画质、旋转、云端图片处理像素上限、Token策略、OCR模型/提示词/思考参数/TTS音色与UI、HTTP域名超时、密钥存储、USB/RTSP、最新照片优先的取消机制均未更改。

本轮**只减少HttpURLConnection内部重复缓冲**，不是将Base64图像JSON变成边编码边上传；完整Base64和请求 `byte[]` 依旧在发送前生成。固定长度不等于能缩小图片、改善服务器推理，也不保证客户端发送后云端不再计费。已知请求体长度适合该模式；跨域重定向/HTTP认证挑战可能无法自动重发，已有 `setInstanceFollowRedirects(false)` 和明确Bearer token，失败应反馈原状态，不应自动尝试第二次付费请求。

## 计时解释

由于固定长度模式由OutputStream直接把字节发送至传输层，新版日志 `cloud.upload_ms` 的口径比此前“缓冲写入”的口径更接近实际发送时间；**新旧版本upload_ms不具备直接可比性**，不能简单把其中差值当成净提速。也不能将OutputStream关闭等同于服务器已收到全部数据。
可靠的用户目标是从拍照触发到第一句听见：参考 `tts.onstart_ms` 和 `tts.first_audio_callback_ms` 两个仅表征引擎回调的值，结合用户真实听感；`cloud.first_speakable_ms` 是云请求开始后首句可读文本时间，不包括本地拍照及JPEG编码。请同时记录 `capture.total_ms` 与失败/状态信息。

## A/B真机验收（需要用户执行）

在vivo X200 Pro Android16，使用同一千问OCR模型、同一任务和同一Wi-Fi/移动网、同一文字近景；保持1200万高清模式不变：
- v1.0.78先拍3～5次，记录按键到真实听见第一句、TTS回调、`cloud.first_speakable_ms`、全流程耗时和JPEG大小；v1.0.79再拍3～5次，最好交替安装/测试或错开网络繁忙时段；**不要**直接比较不同定义的 `cloud.upload_ms`。
- 测试720P快速模式、C110真实照片、用户配置的自定义Chat和Layout Parsing：应成功上传并朗读，不因固定长度POST产生401/重定向/授权异常。若第三方服务商无法接受流式请求，应报告真实错误而非静默重试。
- 继续验证 v1.0.78 的“第二张覆盖旧识别”、取消旧朗读、C110机身快门不重叠；避免回归。
- CI只能证明源码限定/编译/APK，无法验证千问实际网络计时、用户听见首句速度或所有外部服务商兼容；失败则保留旧v1.0.78可回退，安装签名冲突时不得卸载旧包导致Keystore密钥丢失。
