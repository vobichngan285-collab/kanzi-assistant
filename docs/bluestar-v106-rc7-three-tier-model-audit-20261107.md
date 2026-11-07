# 蓝星向导 Blue Star Guide RC7 — 三模型精简与 Max 应急版逐项核查

日期：2026-11-07。原仓库 `vobichngan285-collab/kanzi-assistant`；源分支 `fix/bluestar-106-three-tier-models-20261107`。

## 用户明确授权与目标

- 用户在 RC6 上已经实际点击过 `qwen3.8-flash` 连接测试，手机截图显示“连接成功：qwen3.8-flash（已计入本机用量）”。
- 用户希望只保留三个视觉选择：**日常** `qwen3.8-flash`、**稳定备用** `qwen3-vl-plus`、**高精度应急** `qwen3.8-max`。移出选择列表 `qwen3.5-flash` 和 `qwen3.7-flash`，同时不让旧版故障型号 `qwen3-vl-flash` 回到 API 路由。
- 尽量只改视觉模型入口和必要的模型兼容分支，保留原有拍照/上传/流式首句朗读、原生 USB/Camera2、OCR、ASR、提示音与本机 Token 统计；旧版不覆盖；完成源码逐条检查、CI 编译和公开可复制 APK 直链。

## 事前原仓库核验

- 确实先读取 `main:PROJECT_HANDOFF.md` 与原 RC6 分支的实际 HEAD。RC6 分支基点为提交 `cd316e7447801bbd9f7ae86d199a823958a70665`，原编译 run **42261149782** 成功、原 Release run **42261788230** 成功。
- 此次从该实际 SHA 创建全新分支 `fix/bluestar-106-three-tier-models-20261107`。没有新建仓库、覆盖 RC6 分支、改变离线看字助手 OCR 或 USB 底层。

## 关键代码逐项结论

1. **模型白名单** `VisionModelCatalog.java`：`IDS` 只含 `FLASH_38`、`PLUS`、`MAX_38` 三个，排列对应三个清晰用途；`DEFAULT=FLASH_38`。旧 `qwen3.5-flash`、`qwen3.7-flash`、`qwen3-vl-flash` 或非法值，在配置恢复阶段只归一到 Flash，**迁移本身不触发网络请求**。保留用户显式选择的 Plus/Max。
2. **应用主入口** `MainActivity.java`：默认选择 Flash、配置持久化、模型显式选择、手动连接测试保留。单次捕获在进入工作线程前冻结 `model`；图片追问也独立冻结 `selectedModel`。设置 UI 使用 `LargeSettingsScreen`；独立包名 `com.vobich.bluestar.rc7`，不覆盖先前 RC6。
3. **视觉正常请求** `CloudVisionClient.java`：朗读文字仍硬编码 `qwen-vl-ocr`；视觉模型必须命中白名单，否则直接报错，不会暗中改用 Plus；`qwen3.8-flash` 关闭深思考以降低日常耗时；**Max 仅手动选中才会被调用**，普通视觉请求 Max 显式 `enable_thinking=true`、`thinking_budget=512`、`max_tokens=2048` 限制极端推理时长/Token；连接测试 Max 关闭深思考，`max_tokens=64`。Plus、专用 OCR 的原有协议不变；非 OCR 连接测试无文字时不会误报成「OCR 未识别到文字」。流式回复、响应 usage 与真实 `model` 提取逻辑继续保留。
4. **寻找目标结构化 JSON** `TargetVisionClient.java`：不改目标判断安全限制和仅见实景才报找到的机制；当手动选择 Max 时仍按该模型发请求，但**显式关闭深度思考**，因为阿里云官方错误码文档说明 JSON Object + 开启思考可能导致请求错误（`Json mode response is not supported when enable_thinking is true`）。Max 在普通场景描述/图片追问可使用限额深思考，在 JSON 寻找任务则使用更稳定的非思考输出。
5. **视觉模型设置** `LargeSettingsScreen.java`：三个大选项分别写「日常」「稳定备用」「高精度应急」，Max 显示高价且待账号 Key 授权验证，绝不默认启用；用户主动点击连接测试才上传64×64程序生成的测试图片，失败不重试/不退回 Plus。原不透明深蓝 `BG #091B32`、`PANEL #183553` 及原文字配色未改，无白底/透明设置页面。
6. **本机 Token 统计** `ModelUsageLedger.java`：活跃统计列表现在对应三视觉模型 + 专用 OCR + ASR（不再以两个已删视觉候选占行）。仍在独立单线程读取响应 `usage`，无新增上传/帧处理/阻塞 TTS；未知费用不估算；ASR 不混入视觉 Token。因 RC7 用独立包名，不能读 RC6 本地已记录数据，用户在 RC7 需要重新输入 API Key。
7. **回归测试** `VisionModelCatalogTest.java`：覆盖仅三模型白名单、显示次序、Flash 新默认、显式 Plus/Max 原值保持、旧3模型迁移、不能把高价 Max 当成低价 Flash、Max 状态分支。原 SpeechFilter/VoiceAction/ImageQualityPolicy/VoicePressState/OcrResponseGate 等 JVM 测试与原有 CI 静态安全审计继续运行；没有改变蓝星图标、金属咔叮、语音嗖声或 UVC 编译参数。

## 已实际完成并回读验证的检查

- **源码范围 diff** 相对 RC6 基点仅修改 9 个文件：工作流、Gradle version、CloudVisionClient、LargeSettingsScreen、MainActivity、ModelUsageLedger、TargetVisionClient、VisionModelCatalog、VisionModelCatalogTest。与用户需求无关的摄像头、录音、图标、音频资源、权限、离线 OCR 模块均未修改。
- **安卓 Actions 完整构建与单元测试**：run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42324116996 状态 `completed/success`，打包源码 commit **`8756eba92d4feb5db52dc1a6ab4127a73123b098`**。静态安全审计、各单元测试（包括新增三模型测试）、USB 原生 ARM64 编译、Android Gradle assemble、launcher 图标检查、提示音核查与 APK badging 均为 success。
- **原始 Actions Artifact**：id **13554661615**、name `blue-star-guide-1-0-6-rc7-arm64`，为 7 天临时 ZIP，仅用于内部验包而非用户下载链接。
- **文件二次实包独立核验**：从上面完全相同的构建 Artifact ZIP 下载，CRC 检查成功、`SHA256SUMS.txt` 校验成功、APK 内 CRC 无错误、两种原音效文件与 `lib/arm64-v8a/libUVCCamera.so` 存在。APK 文件名 **`BlueStar_Guide_1.0.6_RC7_arm64.apk`**，字节数 **8,567,972**，SHA256 **`956de9483da4cd0b8c49087384b4d4d997d3d609228409d56e213bfb4b078152`**。内置包名 **`com.vobich.bluestar.rc7`**，`versionCode=16`、`versionName=1.0.6-rc7`、`application-label=Blue Star Guide`；权限仍包括 CAMERA/RECORD_AUDIO/INTERNET/USB 相关。
- **原字节 GitHub Release 公开发布**：发布 Actions run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42324453496 为 `completed/success`，再次校验原构建 SHA256、字节数、原生库、音效和 badging，未重签名/重编译。GitHub Release API 实际回读确认 tag **`blue-star-guide-cloud-rc7-20261107`**、`draft=false`、asset `state=uploaded`、附件 size 与 digest 完全匹配。公开直接 APK（不是 ZIP、不是聊天附件）：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc7-20261107/BlueStar_Guide_1.0.6_RC7_arm64.apk
- 预发布页 https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc7-20261107 。

## 严格不能声称已验证的事项

- **没有用户自己的百炼 API Key**，无法在 CI 中验证 Max 的华北2模型权限、实际收费额度、429/404/400服务器响应及真实图片识别质量。因此新 Max 为「已编入并通过代码编译与静态检查、尚待用户真机连接测试」，**不是「Max 已真实调用成功」**。
- 真实 vivo X200 Pro 上 USB 长时间运行、首句 TTS 时延、Max 对真实照片的识别精度、连续语音追问、今日 Token 统计均还需用户现场复测；编译通过不等于性能或设备稳定性证明。
- RC7 与 RC6 可并存，但是独立包名意味着 RC7 的密钥与计数是新的本机空间，安装之后需要重新配置一次 API Key，历史 Token 仍在 RC6 应用里；未整合百炼账户账单。
- 真正物理熄屏 USB + 音量键识别没有实现，本轮也没有改动 USB 识别机制。

## 推荐真机验收

1. RC7 安装后打开「设置 → 云端 AI」，配置自己的北京地域百炼 API Key；确认默认日常 `qwen3.8-flash`，拍照和语音追问实际可用。
2. 手动选择 `qwen3.8-max`，仅点击一次「测试当前模型能否看图」；核实收到成功或真实错误信息，不自动切换到 Plus（**连接测试可能计费**）。然后在已确认额度的前提下用一张复杂菜单/医院指示牌做单次对比，检查朗读和 Token 记录。
3. 查看「设置 → 云端 AI → 模型用量统计」，是否记录正确请求 model、返回 model、输入输出 Token 或未知。
4. 先留 RC6 不删，用于重要功能回归对照；验证完再决定是否卸载旧版。
