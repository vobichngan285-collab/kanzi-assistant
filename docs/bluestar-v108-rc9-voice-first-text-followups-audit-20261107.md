# 蓝星向导 Blue Star Guide 1.0.8 RC9：语音直接拍照与文字智能分析审计

日期：2026-11-07。原仓库：`vobichngan285-collab/kanzi-assistant`。本报告区分 GitHub 自动化实际检查与 vivo X200 Pro 待验证事项。

## 起点、授权、核心缺陷

用户明确批准修复两项：
1. 首次打开 APP，按住中间的 AI 说“帮我看看眼前的事物”，软件不应要求再按一次拍照，更不应随后按原本默认的“朗读文字”任务执行。
2. 普通语音追问/二次计算/总结，只把上一张照片已得到的文字或场景摘要交给文本模型分析，**不要每次重复上传旧 JPEG 和重新计费图片 Token**。只有用户明确要求“重新看刚才那张照片”才允许重传旧图。复用已有 qwen3.8-flash，不新增收费模型和冗余设置。

在用户授权前已只读检查 RC8 真实源码：`MainActivity.routeSpokenRequest` 仅处理寻找目标、停止寻找及泛追问；普通追问缺少初始照片时直接播报“请先拍照”；`captureOneFrame` 使用 `currentTask`，默认 `朗读文字`；`askAboutCurrentImage` 无条件调用 `CloudVisionClient.recognize(jpeg,...)`，每次附加原图。旧的宽泛“帮我看”正则还可能把眼前场景错误识别为持续寻找目标。

始终使用原仓库，先读取 `main:PROJECT_HANDOFF.md`、RC8 最新真实提交、Actions/发布记录，**从 RC8 HEAD `9e5ff56b96ae7179efb41beb9e04d20d1fbbf312` 创建新隔离分支 `fix/bluestar-108-voice-intent-text-followups-20261107`**。旧 RC8 / RC7 / 离线 OCR 以及所有 USB 研发分支原样保留。

## 逐项修改

- `VoiceAction.java`：新增 `OBSERVE_SCENE`、`READ_TEXT` 和 `REVIEW_IMAGE` 明确意图，在原“帮我看XX”宽泛寻找匹配前优先识别；保留 `START_FINDING`、`STOP_FINDING` 和不明确时 `QUESTION`。覆盖“帮我看看眼前的事物”“看看前面有什么”“再看看左边有什么”“读一下眼前的文字”“重新看刚才的照片”。原找超市/药店等持续寻找规则保持。
- `MainActivity.routeSpokenRequest`：场景命令创建临时 `TaskCatalog.Task`（VISION；含用户原话和保守可视说明），文字朗读命令创建临时 `TaskCatalog.Task`（OCR；`qwen-vl-ocr` 路由）；两者**立即请求新鲜相机画面**，不修改持久当前任务，不受持续寻找目标状态劫持。没有历史照片时普通追问仍提示当前没有上下文，但明确眼前观察命令不受这一限制。
- `MainActivity.captureOneFrame(oneShotTask)`：沿用现有手动拍照所用的新帧链路。USB 必须有距今少于 1400ms 的实际 NV21 帧；Phone Camera2 通过 `requestFreshFrame` 获取新帧，`pendingPhoneCaptureTask` 只与本次 token 一起传给 `beginCapturedFrame`；超时、取消、页面退出清理任务，不能回退旧图片。音效、震动、原生摄像头、编码分辨率与 AI 上传路径保留；仅临时覆盖任务，不改变 Task 选择或保存配置。
- `MainActivity.askAboutCurrentImage(question,recheckImage)`：常规追问使用 `FollowupPrompt` 严格限长构建纯文字请求，**直接调用 `CloudVisionClient.recognizeText(...)`**；明确重新检查旧图片才走已有 `CloudVisionClient.recognize(jpeg,...)`。TTS 继续边流式响应边播，依旧记录服务端响应模型及真实 Token。文字分析只使用已在用户 RC6 通过联网连接测试的 `qwen3.8-flash`，不受手动 Max/Plus 视觉模型选择影响，降低费用；不会隐藏重试或自动切换到高价模型。原 `showTextQuestionFallback` 也重新走意图路由，避免手工输入仍无条件上传图片。
- `CloudVisionClient.recognizeText`：沿用同一个北京百炼兼容 API、流式解析与 TTS 分块调用，**但请求内容只有 JSON type=text**，图片编码仅发生于 `if(!textOnly)` 条件内；textOnly 调用传入的 JPEG 参数严格是 `null`，不追加 `image_url` / Base64 数据。正常视觉/OCR 请求逻辑基本不动。
- `FollowupPrompt.java` 新增：用此前已识别文本、用户问题、最多3组短上下文，形成最长约5500字符保守提示词；不猜测照片中未识别的价格、商品或方位。专门将纯文字和用户明确授权旧照片复查的提示词区分，避免“不可看图”与“已上传图”自相矛盾。文字不足时明确不确定。
- 原三视觉模型、OCR、ASR、Token 统计账本、USB 原生库、音效、图片质量选项和 RC8 **不透明深蓝简洁设置页**完全保留，没有引入新文本模型列表或新的拍照按键。

## 测试与 APK 验收

- `VoiceFlowTest`：覆盖10+组新旧语音指令（场景观察、朗读文字、位置观察、旧图明确复查、普通菜单文字计算、持续寻找不误判），以及旧的按住/松开发送/WAV 测试。
- `FollowupPromptTest`：原文价格保真、问题保真、上下文、缺失拒绝、长文本限额、授权旧照片和纯文字之间提示词不矛盾。
- CI 静态审计新增对新鲜 USB/Camera2 一次性任务、文本分支绝不编码 JPEG、Flash 单独文字调用、原三视觉模型/深蓝设置及现有 TTS 的验证。首次 CI 曾发现 `FollowupPrompt` Java 链式调用存在编译语法错误，**已修复并重跑，最终全部测试通过**，不能把首次失败算作成功。
- **最终真实 GitHub Actions CI run**：[42328080189](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42328080189) `completed/success`；源码提交 **`011e4eff29ce75093d7a3a733149aac254a89b23`**；Java 测试、静态源审计、Android USB 原生 ARM64 编译、Gradle assemble、五密度图标、C 金属咔叮、发送嗖声、APK badging、ZIP/校验归档均成功。
- **实际原始 artifact** id **`13555734683`** 名称 `blue-star-guide-1-0-8-rc9-arm64`。已下载其 ZIP 并使用容器独立核对 ZIP CRC、内部 `SHA256SUMS.txt`、APK CRC、`lib/arm64-v8a/libUVCCamera.so`、`res/raw/c_metal_clack_ding.mp3`、`res/raw/voice_send_whoosh.wav`。真实 APK **`BlueStar_Guide_1.0.8_RC9_arm64.apk`** 大小 **8,567,972 字节**，SHA256 **`c2b70d7808403d370384acb38d45edbf79beab6d28a572bc211646562052ac1e`**，包名 `com.vobich.bluestar.rc9`，`versionCode=18`、`versionName=1.0.8-rc9`，桌面名 Blue Star Guide。独立 RC9 包可与 RC8 共存，但用户需重新输入百炼 Key，不能读取 RC8 本机统计。
- **公网直链发布成功**：通过只可单次发布的 `.github/workflows/bluestar-rc9-direct-apk-release.yml`，固定验证完整构建 run、源码 SHA、APK SHA/字节数/CRC/USB/两声效和包名；原字节发布，不重新编译、不重新签名。发布 run [42328455406](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42328455406) `completed/success`。GitHub Release tag `blue-star-guide-cloud-rc9-20261107`，API 实际回读 `draft=false`、资产 `state=uploaded`、大小/服务器 SHA256 完全符合上文。

**给用户复制到手机浏览器的真实 APK 网址（不是 ZIP）**：

https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc9-20261107/BlueStar_Guide_1.0.8_RC9_arm64.apk

## 未完成的真实手机验证

- 未拥有用户百炼 Key，不能从 CI 证明真实 qwen3.8-flash 文本请求、自动相机取帧、USB/Camera2 切换、TTS 物理出声在用户 vivo X200 Pro 上全部成功。**源码已逻辑校验与构建，但真机 A/B 仍需用户安装验证。**
- 测试要点：APP 打开直接说“帮我看看眼前有什么”，应自动获新帧并朗读场景（不是读文字）；说“帮我读一下文字”，应自动拍新帧用 OCR 读字；拍菜单后问“这两样一共多少钱”，应只传文字；说“重新看刚才那张照片”，应再次发送图片；最后对照模型统计 API Token 和既有 RC8。摄影状态没有新帧应正确报告，而不是使用旧图片；此前识别内容不足时文本分析应报告不知道。
- 该版本不承诺完整视觉识别成功率、绝对零延迟/零Token、USB 长时间无卡顿、物理息屏 USB 操作或模型端精度。只减少普通追问的重复图片输入 Token，不影响 ASR 音频计费及文本分析本身的 Token。
