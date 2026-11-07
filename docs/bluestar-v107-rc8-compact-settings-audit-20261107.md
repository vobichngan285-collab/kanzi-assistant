# 蓝星向导 RC8 极简设置页：修改、检查和 APK 发布交接

2026-11-07。原 GitHub 仓库：`vobichngan285-collab/kanzi-assistant`。

## 用户诉求与实际范围

用户上传 RC7 两张截图，明确指出设置主页、视觉模型选择页面的**卡片标题下面重复说明过多**，要求删掉下方说明、保留必要设置与可读性。因此新建 **`fix/bluestar-107-compact-settings-20261107`**，从 RC7 确切 HEAD `319041204a071b58a4a75bb82e0e32e6ac670fd8` 分支。未覆盖 RC7/RC6、离线 OCR、USB 研发分支。

## 修改内容

- **`LargeSettingsScreen.java`**：全设置共用 `row(label,description,clicked)` 仍保留签名与触发行为，但不渲染 description；每个可点击卡片统一只渲染大号标题、右箭头，内边距从 15dp 垂直缩到 11dp，最低高度从 78dp 降至 66dp，仍保留明显触摸目标；开关 `toggle` 只显示名称与 Switch，不渲染冗余 desc。
- **设置主页**：语音与朗读、任务管理、摄像头设置、提示音与振动、云端 AI、上次识别结果、运行诊断等仍原位置保留，不再显示一段用途解释；删除页面底部诊断解释文本。
- **视觉模型页**：只显示「日常 · Qwen3.8-Flash」「稳定备用 · Qwen3-VL-Plus」「高精度应急 · Qwen3.8-Max」三张紧凑卡片，当前选中只显示简洁 `✓`；删除上方总说明、各卡片下方说明、连接测试上方和下面的整段文字；连接测试按钮改为「▶ 测试当前模型」。**仅手动点击 Max 的连接测试**时弹出短确认，说明「测试将发送一张小图，可能产生费用」，点击确认才发一次测试请求。Max 正常使用仍是用户显式选择且与 RC7 相同的行为，不添加模型网络请求或自动回退。
- **其余设置**：删除写在设置里的重复性静态说明，包括语速范围大段文字、过滤说明、相机说明、反馈说明、云端 AI 说明、Key 说明、诊断记录静态说明和统计页静态大段说明；**保留运行状态、真实 Token 数字/最近模型状态、最近麦克风诊断、错误信息、删除操作确认、模型测试费用的即时确认**。摄像头方向、镜像与图片分辨率的**当前实际状态改为紧随卡片标题内联显示**，防止删掉描述后无法确认当前选项；语音引擎列表用短 `✓` 显示当前选择。
- **外观边界**：既有深蓝 `BG=0xFF091B32`、不透明卡片 `PANEL=0xFF183553`、原文字色及滚动页结构均保留，绝不生成白底或透明设置页。AppTheme 仍是 `Theme.AppCompat.NoActionBar`，无主题资源更改。
- **技术边界**：除了 `LargeSettingsScreen.java` 的展示与原 Android Gradle 独立版本/CI，**不改 MainActivity、CloudVisionClient、TargetVisionClient、ModelUsageLedger、USB/camera、OCR/ASR/TTS、音效资源、图标和任务路由**。相较 RC7 仅变动设置 UI、Gradle version、CI 和新的发布脚本。因为 RC8 使用独立包名，安装后需要重新配置 API Key，本机旧 RC7 统计不会自动复制。

## 自动化构建与逐项核查

- **编译原码最终提交** `acae013fe90b54a8afcfc52eeab0eb0852b71dd2`。
- GitHub Actions run [42325680471](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42325680471) `completed/success`，包括静态权限/逻辑检查、Java 单元测试（原语音/图像/三档模型）、ARM64 Android Gradle 编译、图标/音效、APK 包名与必备原生 USB 组件验证。
- 原始 Artifact id **13554529621**，名为 `blue-star-guide-1-0-7-rc8-arm64`，是短期 ZIP，不作为用户下载安装地址。
- 从原始 Artifact ZIP 实际取出后单独检查：artifact ZIP CRC 无错；内部 `SHA256SUMS.txt` 与 APK 内容一致；APK ZIP CRC 无错；原 `lib/arm64-v8a/libUVCCamera.so`、`res/raw/c_metal_clack_ding.mp3`、`res/raw/voice_send_whoosh.wav` 均存在；`badging.txt` 为 `com.vobich.bluestar.rc8`、`versionCode=17`、`versionName=1.0.7-rc8`、`Blue Star Guide`。原 APK `BlueStar_Guide_1.0.7_RC8_arm64.apk` **8,567,976 bytes**，**SHA256 `848b6eac3e581761779b8c47f1303808ca5a191e67d4939c28da883f05178278`**。
- GitHub 公网直接 APK 发布 run [42326045851](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42326045851) 状态 `completed/success`，再次按固定 SHA256/文件大小/CRC/资源/badging 核查并上传**同一文件**，未重签名或再编译。Release tag **`blue-star-guide-cloud-rc8-20261107`**，GitHub API 确认 `draft=false`、asset `state=uploaded`、size与digest一致。
- **可复制到手机浏览器的长期公网 APK 直链（不是 ZIP）**：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc8-20261107/BlueStar_Guide_1.0.7_RC8_arm64.apk

## 真机待验收

- CI 验证的是编译和静态功能范围，**没有在用户 vivo X200 Pro 上实际点击所有设置项、对比 RC7 版排版、完整运行 USB 摄像头、听 TTS 或检查密钥权限**；必须用户实际安装后评估大字号布局、三模型名称是否无溢出。
- 真机升级 RC8 可与 RC7 并存，但需重新输入北京百炼 API Key；继续按需验证 Max，连接测试会消耗少量额度；控制台用完即停规则以百炼账户为准。
