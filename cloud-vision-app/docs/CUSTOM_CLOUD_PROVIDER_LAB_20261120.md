# 蓝星向导 v1.0.67｜开放式云端服务商实验版（2026-11-20）

## 用户授权、范围与不变项
用户要求以后无需找开发者重新修改 APK：在 App 设置自行填 **完整HTTPS接口URL、API Key、模型ID**，自由选择国内外厂商，并且 **OCR、视觉分析（含AI追问/找目标）、ASR云端语音识别分别选择不同厂商**。要求独立实验包，之前已验证手机/USB/C110相机、原厂指令、声音、UI、广荣微软TTS、保益触发器和其他功能均不能改；完成逐项代码与构建复核，发布供用户申请新厂商服务后真机测试。

原仓 `vobichngan285-collab/kanzi-assistant`，从唯一已发布v1.0.66分支 `exp/phone-preview-clean-c110-ding-20261120` HEAD `a79d5c5684dcacd24f92cfcb79f846065eba9d8d` 开发新隔离实验分支 `exp/custom-cloud-providers-20261120`。版本 `versionCode 76` / `versionName 1.0.67-cloud-provider-lab`，debug app ID `com.vobich.bluestar.c110plusquick159`，旧Release及独立保益启动器不变。

## 用户操作
主页 → 设置 → 云端 AI → **自定义服务商 · OCR / 视觉 / 语音** → 选择三种独立任务之一 → 「＋新建」 → 填名称、**完整 HTTPS API URL**、模型ID、API Key → 保存并启用。可保存20套自定义配置，每套密钥单独 AES-256-GCM/AndroidKeystore 加密，列表中不回显Key；编辑时 Key 留空保留现有密钥；可分别选择同一套可兼容的视觉配置供OCR/视觉使用。OCR和视觉有「小图测试」（实际发带字母A的64x64 JPEG，会有少量Token成本）；ASR不能用无声文件测试，直接按住主页或普通预览底部AI说话松开测试。单个配置可编辑、删除；删除时占用该配置的任务回到原百炼。原有百炼 Key 仍从旧 `CloudKeyStore` 读取，没有迁移、覆盖、清空；任一任务没有自定义选择时使用原百炼模型及原传输行为。

支持的自填协议：
- **图文OCR/视觉**：OpenAI-compatible `POST /chat/completions`，支持 `messages[0].content` 的文本和 `image_url` Base64；默认非流式，可勾选SSE；服务商实际支持图片/格式/权限需自行确认。兼容字段`max_tokens`；关闭百炼专属`enable_thinking`/`enable_search`及`stream_options`以避免新厂商拒收。新厂商不承诺自动联网搜索。
- **ASR（语音转文字）**：可选（1）OpenAI-compatible `POST /audio/transcriptions` multipart/form-data `model + file=voice.wav`，取JSON `text`；（2）百炼兼容 `POST /chat/completions`的 `type=input_audio,data=data:audio/wav;base64` + `asr_options`，取choices message.content；（3）OpenAI Chat audio `type=input_audio, data=<base64>,format=wav`，取choices message.content。不是所有厂商都提供这三种格式；专用WebSocket/签名认证协议需要另做协议适配。
- **统一密钥/权限安全**：一律要求HTTPS、不使用明文HTTP或自动跳转、不允许URL中包含账号密码；Bearer密钥不打印、不导出；保存后本地密文；用户选择错误/删除配置不静默切换到其他付费配置。临时手机/USB/C110图像与音频仍按原流程传输，C110热点中的云端走移动数据绑定方式不变。

## 调用路径核验清单
1. `MainActivity.beginCapturedFrame`：手机Camera2、USB UVC、C110静态高清/实时取帧都根据当前Task OCR/视觉选择`CloudProviderStore.resolve`，独立视觉寻找由`TargetVisionClient`同样走视觉配置。
2. `MainActivity.beginReadingCapturedFrame`：阅读模式强制选 OCR 配置。
3. `MainActivity.askAboutCurrentImage`：语音追问/文字追问和历史照片复看选视觉配置。
4. `MainActivity.handleVoiceButtonTouch`：云端ASR只选语音配置；之后根据用户意图调用视觉或OCR，TTS不改。
5. `HomeVoiceAssistant.requestAI`：主页按住AI独立ASR配置，后续文本问答/照片识别选视觉配置，仍不从C110 AP向公网发请求。
6. `C110FrameReader.recognizeJpeg`：C110独立实时预览取帧与1001真实JPEG均按当前任务选OCR/视觉配置，保持原声音引擎/朗读队列。
7. 原有百炼`CloudVisionClient`、`CloudAsrClient`公开方法保留；新接口多态参数`CloudProviderStore.Config`新增，默认请求地址、原有模型白名单、输出限制、提示词与流式行为保留。
8. `ModelUsageLedger`动态模型ID可记录，未知厂商不估算token。图文小图测试单独完成后才显示成功，ASR真实讲话测试不伪报成功。
9. `BlueStarSettingsScreen`仅增加云端 AI 下的独立服务配置页面，原百炼API Key入口、视觉/文字模型、用量、其他设置原样。
10. **不触碰** `C110WifiCamera/C110WifiBootstrap/C110LivePreview/C110DeviceControls/C110CameraActivity/CaptureDingFeedback` OEM和UI、`Camera2`/USB采集、RTSP 150ms、1001快门、9011静音、9038灯光、TTS引擎、保益小APK、主包ID、主页和底栏；需要CI changed-file白名单验证。

## 限制与真机验证
兼容OpenAI格式的厂商可通过自填接入；不支持“世界所有专用云API”的零修改承诺，尤其火山不同ASR产品、千问专用识别、非Base64视觉接口可能需转换格式。API URL是完整接口不是只填域名，例如火山图文 `https://ark.cn-beijing.volces.com/api/v3/chat/completions`，模型 ID 从自己的控制台复制。ASR multipart服务应填其 `.../audio/transcriptions` 地址。
真机先不改配置，升级包后确认原百炼的手机/USB/C110拍照→云OCR/TTS、AI追问、本地麦克风与语音识别、照片“叮”声全部不回归；然后以自申请的服务商填各接口，小图OCR测试→视觉测试→ASR按住说话，各别分配；最后确认切回百炼仍正常。GitHub CI只能验证代码、编译和静态测试；不能假称已使用第三方真实API/实机扬声器或相机验证。

