# 蓝星向导 1.0.17 RC18：三个视觉模型的按需联网追问

## 授权与官方接口核对
2026-11-07 用户明确授权“可以，你现在就可以写代码，把这几个模型都允许联网。检查代码的逻辑性别又搞错了”。

对阿里云百炼官方 <https://help.aliyun.com/zh/model-studio/web-search> 和 <https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions> 核对：`qwen3.7-flash`、`qwen3.8-flash`、`qwen3.8-max` 都在联网支持范围。现有 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` 为原官方 OpenAI兼容接口，直接在 JSON body 顶层设置 `enable_search:true`；`forced_search` 不传，服务端默认 false，搜索由模型判定是否有必要；`search_strategy` 未传，官方默认为 `turbo`。官方明确 **Chat Completions 协议不支持返回结构化搜索来源**，故不能谎称当前新功能能展示已核查的官方链接。调用可能增加搜索费用、Token和时延；真实搜索行为需从用户端网络/回复核实，而不是仅看 enable_search。

## 修复逻辑
- 从主仓库 `vobichngan285-collab/kanzi-assistant` 最新 RC17 完结分支 HEAD `a40fe8244fe9474d833f295566cb03f9300a3047` 新建 `feat/bluestar-117-web-search-followup-20261107`；先读 main `PROJECT_HANDOFF.md` 核验 RC17 构建和发布 SHA。
- **原本非常重要的 Bug**：`MainActivity.askAboutCurrentImage()` 文字追问一律 `VisionModelCatalog.FLASH_38`，而图片重检才用 `visionModel`；即使把 Max 设为视觉模型，普通联网追问仍会使用 Flash38。RC18 统一用用户已选视觉 model，语音追问/相同照片重检均准确记账；OCR独立选择保持不动。
- 新纯JVM `SearchRoutePolicy.java`：只许可 `text-followup` 和 `user-followup` 两种**用户明确提问**的视觉任务使用搜索；仅白名单中上述三种模型，且 `cloud_smart_web_search_v1` 设置开启才会允许。**所有其他**专用OCR/切到Flash文字识别、普通一键拍照识别、菜单/医院/商品/店铺首次拍照、主动找目标（安全输出）、模型连接探针、ASR转写均不会打开网络搜索工具。设定项默认开启、用户可在「云端AI」一键关闭；关闭后只做不联网问答。
- `CloudVisionClient` 增加允许联网参数，不多建网络请求；该次用户主动发起的 Chat Completions 请求顶层按路由加入 `enable_search:true`，原 ASR/OCR/拍照识别、流式回调/TTS/用量计费机制原样不变。联网追问 readTimeout 90s (其它仍45s)，**服务端判断何时搜索，不保证每次都搜，也没有结构化官方来源**；没有 fallback 重试、无后台常驻搜索、无自动双请求。
- `FollowupPrompt`：已经识别的文字/照片依旧为事实来源；用户问到最新营业时间/新闻等可在一个 prompt 中要求按需联网查证，明确要区分网页资料和照片可见事实，无依据不可编造；不假称已联网或有官方来源。普通对话即使尚未拍过照片，也可直接通过按住AI说问题实现无图的一次文字 Chat Completions 请求，且明确说明无图，避免幻觉“看见”并允许保留最多3轮问答。用户明确说“重新看图”而无历史 JPEG 仍提示先拍照，不偷偷拍摄。
- 保留三档视觉选择/四档 OCR 选择及用户简短OCR提示词，照片历史 JPEG 不保存在磁盘/日志，ASR和TTS照旧，本机运行日志只采集数值与结果；兼容 UVC RC16高FPS、RC13 32组方向校正、RC14硬件画质、USB插拔原行为。
- JVM新增 `SearchRoutePolicyTest` 逐项测试3模型、联网开/关、模型探针/普通拍照/OCR/寻找目标绝不触发联网，`FollowupPromptTest` 新增无图和联网提示词的历史对照。保留原全套CI回归。
- 隔离包新 `com.vobich.bluestar.rc18`、versionCode27、versionName `1.0.17-rc18`，不会覆盖 RC17，也不会迁移 API Key 和相机/TTS偏好。

## 交付核验
本文件先记录实现与需要验收的边界；GitHub Actions CI、源码 SHA、发行workflow、原APK校验SHA256、正式 Release 及 main 交接文档追加必须在成功后再完成。不能把CI通过等同于三个模型实际 API Key 权限或每次确实联网。需真机逐一测试3档模型，问“今天的天气/最新营业时间”，观察是否以外部资料回答；再关闭联网确认不再请求搜索；再核查照片重检/直读 OCR 无网络检索、用量和USB 60Hz预览是否完好。


## 实际 CI 与 APK 发行记录（2026-11-07）
- 构建源码准确 commit：**`155668f30190ba770bb4ba58349246b5a6d24b20`**，GitHub Actions **[42365184277](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42365184277)** `completed/success`，原始 artifact **13571770034**，名称 `blue-star-guide-1-0-17-rc18-arm64`。
- 真实日志确认为 `DUAL_CAMERA_CLOUD_AI_NO_LOCAL_OCR_AUDIT=PASS`、`RC18_SEARCH_ONLY_EXPLICIT_FOLLOWUP_THREE_MODELS=PASS`、`RC18_FOLLOWUP_WEB_AND_STANDALONE_PROMPTS=PASS`、`RC17_OCR_FOUR_CHOICES_INDEPENDENT_AND_PROMPT=PASS`、`RC13_USB_PREVIEW_JPEG_32_COMBINATIONS_AND_90_CORRECTION=PASS 32`、`RC14_USB_LIFECYCLE_FPS_4_STATES=PASS`、`RC14_USB_PICTURE_CONTROL_BOUNDS=PASS`、`RC15_AUTO_USB_SWITCH_MANUAL_OVERRIDE_AND_ATTACH=PASS`、`RC15_USB_ROLLING_10S_FPS_STALE_AND_FREEZE=PASS`、`CLOUD_OPTIMIZATION_UNIT_TESTS=PASS`、`TASK_ORDER_DRAG_RECONCILIATION_TESTS=PASS`、`BSG_UVC_FPS_61_FIRST=PASS`； Gradle **`BUILD SUCCESSFUL in 2m 29s`**、JNI原UVC/图标音效/独立包名检测PASS。aapt证实 `com.vobich.bluestar.rc18`、versionCode `27`、versionName `1.0.17-rc18`。
- 发布 workflow **[42365514892](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42365514892)** `completed/success`；确认准确 CI run/source SHA 后下载官方产物，校验 `SHA256SUMS.txt`、ZIP CRC、原生UVC/金属咔叮+嗖声音效、图标和包名/版本，以原字节上传**没有重编译/重签**。
- Release `blue-star-guide-cloud-rc18-20261107`，`draft=false`、`prerelease=true`；APK `BlueStar_Guide_1.0.17_RC18_arm64.apk` asset 已 `uploaded`，**8,601,318 bytes**，GitHub SHA256 **`087715a79a47852a5f5d42efcada6910de7792eab49400c76c6883738c96599e`**。
- 直达手机浏览器 APK 链接：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc18-20261107/BlueStar_Guide_1.0.17_RC18_arm64.apk
- **仍待真机验证**：有了 `enable_search:true` 不代表后台真的进行了联网搜索；不同问题、百炼账户权限、支持地域和接口可能影响行为；联网请求可能需要更久、增加费用，且此 OpenAI兼容 Chat Completions 不返回可验证的结构化来源链接。务必使用同一个问题分别测试 3 模型、在设置中关闭联网复测；检查之前图片的追问、没有照片直接语音聊天、截图 OCR 原样、USB画面方向和约60次/秒预览更新仍然正常。CI和GitHub原APK发布通过，不等于用户百炼服务端所有模型搜索真机通过。
