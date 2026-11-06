# 蓝星向导 Blue Star Guide 1.0.5 RC6 — 视觉模型列表、连接测试与 Token 统计交接

> 2026-11-07；原仓库 `vobichngan285-collab/kanzi-assistant`。仅记录实际 GitHub 源码及 Actions 结果，未经 vivo X200 Pro 真机验证的项目不得表述为已通过。

## 起点与分支隔离

- 基线为同仓库 RC5 独立分支 `fix/bluestar-104-ocr-voice-empty-20261106` 的 `f6e6fd51ebc37aaec20f52c1a23cba558afa710f`。
- 从该 SHA 新建独立修复分支 `fix/bluestar-105-model-options-20261107`；**原 RC5 APK、RC4、离线 OCR、USB 相关研发分支不修改、不覆盖**。
- 本轮用户批准：删除当前账号实际返回404的 `qwen3-vl-flash` 选项；保留已在手机可用的 `qwen3-vl-plus`；加入 `qwen3.7-flash`、`qwen3.5-flash`、`qwen3.8-flash`；加「测试当前模型能否看图」与「今日逐模型真实 Token 统计」列表；设置必须维持原版不透明深蓝背景；逐项审计并生成可复制到浏览器直接下载的 APK。
- 官方资料确认三款新模型有图片输入能力，但**没有用户 API Key，无法代表账号确认北京地域权限、免费额度或实际可用性**。新候选明示「待连接测试」，绝不把存在于模型广场等同于用户已成功调用。

## 实际修改

1. 新增 `VisionModelCatalog.java`：**仅允许四个视觉候选** `qwen3-vl-plus`、`qwen3.7-flash`、`qwen3.5-flash`、`qwen3.8-flash`。移除旧坏选项；旧版已保存 `qwen3-vl-flash` 值时迁移至已知存在的 Plus（没有自动发送请求）。原 `qwen-vl-ocr`（朗读文字）和 `qwen3-asr-flash`（语音转写）不变。
2. `CloudVisionClient.java` 与 `TargetVisionClient.java`：**按实际选中的视觉模型 ID 发请求**，不允许失败后悄悄改用 Plus；Flash 系列传 `enable_thinking=false` 限制冗余思考；Plus 和 OCR 的原有发送方式尽量保持；目标寻找 / 场景分析 / 图片语音追问均使用实际选中的模型；真正错误保留可见失败，不自费重试。
3. `MainActivity.java`：新版模型菜单与本机 Key 配置保持；显式点击连接测试才发送一张程序临时绘制的64×64 JPEG，`model-probe` 非流式请求且 `max_tokens=64`，失败不自动更换模型，不保存照片。注意这项按钮会消耗**一次少量** API 额度；页面有明确提示。API 请求参数未将用户 Key 写入 APK 或日志。
4. 新增 `ModelUsageLedger.java`：独立单线程本机记录；按手机本地自然日、各模型汇总输入 Token、输出 Token、次数、未知和失败；保留最近30天；数据仅从 HTTP 响应 `usage.prompt_tokens / completion_tokens` 读取，无法获取时标记未知**绝不伪造为0**。同样记录最近一次请求模型和服务端返回的模型（若服务器没有回报则直说「未返回」）。识别链路只入队，不等待文件提交；无每帧统计、无统计专用 API 调用，关掉页面仍轻量。
5. Token 数据覆盖普通 OCR、菜单/医院/店铺/商品等视觉任务、图片追问、寻找目标，以及显式测试请求。修正 OCR HTTP 200 空文字依旧要按返回的 `usage` 计入，空文字语音提示/不弹窗的 RC5 行为保留。ASR 仅计本机已知调用次数和「Token 未知」（因 ASR 可能另按录音时长计费），不把它纳入视觉 Token 总和。失败请求的实际计费以百炼服务器为准，本机账本不能替代账单。
6. `LargeSettingsScreen.java`：云端 AI 下新增「视觉模型选择」「模型用量统计」及清空确认页。保留原背景 **`BG=0xFF091B32`**、面板 **`PANEL=0xFF183553`**，沿用原 TEXT/SUB 颜色和 ScrollView，大字并完全不透明；没有加入白色或半透明新设置界面。刷新/清空统计不会删除密钥，也不会改动远端真实使用量。
7. 整体测试：`VisionModelCatalogTest.java` 测完整白名单、非法值/旧 Flash 迁移、保留合法选择；CI 检查所有进入模型调用的相关路径、固定 OCR、深蓝底色、异步 Token、提示音图标资产及无本地 OCR 引擎；原 `VoiceFlowTest`、`CloudOptimizationTest` 继续通过。

## 构建、APK、签名与公网交付（已实际核实）

- 原始最终源码快照：**`5f04753b8ebe28d47a4835b278819df7e16c4eb9`**。后续文档和 Release 工作流的提交不更改已经验包的 APK 源码。
- Android CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42261149782 ，最终 `completed / success`，包括纯 Java 测试、静态安全审计、Android/USB native ARM64 编译、五密度 launcher 图标、音效资源核验、APK badging 与校验并归档。
- 原始 Artifact id **`13525147115`**：`blue-star-guide-1-0-5-rc6-arm64`，有效期约到 `2026-11-13T17:26:03Z`，是 ZIP，不能作为长期用户下载入口。
- 从真实 Artifact ZIP 解压核实的 APK `BlueStar_Guide_1.0.5_RC6_arm64.apk` 大小 **8,567,972 字节**，SHA256 **`91595fd5f42837c64d25fe536992325307130ffa03f145124196a4aef7f0e480`**。`SHA256SUMS.txt` PASS，APK ZIP CRC PASS；`badging.txt` 显示包名 `com.vobich.bluestar.rc6` / versionCode `15` / versionName `1.0.5-rc6` / 桌面英文名 `Blue Star Guide`，含 ARM64 `libUVCCamera.so` 与既有 C 金属咔叮、嗖声资产。**RC5 和 RC6 独立安装，可并存，但新包需自己重新输入百炼 API Key**。
- 通过独立、权限有限的发布 Workflow https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42261788230 ，**completed/success**，重新从上述 CI 成功产物下载并逐字节验证 SHA256/大小/ZIP CRC/音效/ARM64 USB，原封不动上传 GitHub Release，**不重新编译、不重新签名**。
- **用户优先使用的公网直接 APK 地址（不是 ZIP，也不是 ChatGPT 内部下载）**：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc6-20261107/BlueStar_Guide_1.0.5_RC6_arm64.apk
- 预发布页面：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc6-20261107
  GitHub API 回读确认 release 为 `draft=false`、APK asset `state=uploaded`、size=8,567,972、digest SHA256 与原始 APK 完全相同。

## 不可误报的风险 / 接下来实际真机 A/B

- 还**没有** vivo X200 Pro 真机对 qwen3.5/3.7/3.8 Flash 的北京地域 Key 授权或图片上传端到端验证，不能把静态 API 模型列表等同于全部能用；用户必须自行在新版「设置 → 云端 AI → 视觉模型」先选择并点击一次「测试当前模型能否看图」。若返回404，只标记失败、不切换付费更高的 Plus。
- 用户需要用**实际自己的图片**测试 OCR、菜单、店招、商品、图片语音追问、目标寻找；并确认 TTS 不漏句、没有非法重复收费请求；测试过程可能消耗自身额度，提前在百炼确认用完即停。
- 切换旧 RC5 到 RC6 的本机统计不可能追溯用户此前在其他 APK / 其他应用消耗的 Token；今日合计为从 RC6 安装后记录的**已知用量下限**，不能说成百炼账户真实账单。流式响应若未返回 usage 显示未知。
- 新统计完全不在 UVC 每帧采集/图片编码线程里运行，但后台序列化仍有极小系统开销；尚未经过 vivo 实机性能 A/B，不能承诺绝对零影响。
- 真正物理息屏 USB 摄像头和音量键触发识别**未开发**，原有 UVC 在设备上曾有偶发卡顿历史，RC6 没有宣称修复或验证。
