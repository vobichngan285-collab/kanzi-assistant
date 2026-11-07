# 蓝星向导 v1.0.16 RC17 — 文字识别独立模型选择

2026-11-07。用户明确批准“可以就这样干，你现在就可以修改”，任务范围：云端AI设置增加**独立的文字识别模型**，默认专用OCR，也可在视觉已有3个模型中手动切换。用户亲自确认的唯一短默认提示词：

> 识别图片中清晰可见的文字，按正确阅读顺序原样输出，不解释、不补充、不猜测。

## 源码与行为

- 原仓库 `vobichngan285-collab/kanzi-assistant`，先检查 `main:PROJECT_HANDOFF.md` 以及最后 RC16 实际HEAD `7522c79414cf7fe4342bf99b01303e40c58b79be`，派生 `feat/bluestar-116-independent-ocr-model-choice-20261107`，不建新仓库、不改离线OCR。
- 新 `OcrModelCatalog.java`：严格四项 `qwen-vl-ocr`（默认） / `qwen3.7-flash` / `qwen3.8-flash` / `qwen3.8-max`；文字识别和场景视觉模型分开选择保存，非法旧值回退专用OCR。切换设置不会发云端请求、不新增轮询或自动模型兜底。
- `MainActivity` 本地 `cloud_ocr_model_v1` 独立于 `cloud_vision_model`，抓图及语音 READ_TEXT 的 OCR route 动态选已选文字模型；医院、商品、菜单等视觉任务保持原视觉模型，**即使当前任务是 OCR，后台「寻找目标」仍强制用视觉模型**；文末逐项的 OCR 空结果/错误模型统计使用实际选中的模型。原 TTS/追问单纯文字仍按既有路径；不额外上传图像。
- `TaskCatalog` “朗读文字”默认提示词就是上方一条，不加冗长解释；所有4模型用同一 task.instruction、同一单张JPEG和非流式OCR结果、`OcrResponseGate` 与直接 TTS。任务原有可编辑提示词功能仍可用；**用户自己曾经编辑过的 OCR 预设不会被偷偷覆盖**。若用户想恢复这一句，可在任务管理把「朗读文字」提示词恢复默认。
- `CloudVisionClient` OCR 通过4选白名单，其他视觉任务仍仅3项，识别为一条 HTTP 请求；Flash/Max OCR 用 `enable_thinking=false`，专用 `qwen-vl-ocr` 保留原参数；OCR 最大输出1200 tokens，保持无内容响应直接语音提醒，失败不转选其他模型。实际识别率、费用、延迟需真机云端A/B，不能虚报。
- `LargeSettingsScreen` “云端AI”增加「文字识别模型」入口和四选，已选打勾，说明内置默认提示词和Max收费；原视觉模型设置3项及今日Token账本保留，两组当前选择同时在用量页显示。
- **只修改独立OCR模型路由、默认提示词、UI和错误记账**，不改 RC16真机验证过的近60 TextureView 更新/秒 UVC 预览、原 USB FPS fallback、RC13画面旋转/镜像与上传JPEG一致性、USB 画质滑块（用户仅反馈增益并未授权此次修复）、TTS 音效与语音录音、任务宫格拖动、原生 Camera2、API Key持久化。
- 新纯JVM `OcrModelCatalogTest` 校验4项ID、独立路由、非法值专用OCR默认和提示词逐字一致；保留原30余项回归和Android arm64构建。
- 新隔离包 `com.vobich.bluestar.rc17`, `versionCode26`, `versionName1.0.16-rc17`, 不覆盖 RC16；独立数据需重新填API Key、偏好任务和USB配置。
- 本文先记录范围；CI成功源码SHA、原APK SHA256/Release、构建与真机剩余验证待完成后补录。未经实际CI不得声称成功。


## GitHub CI / 发布后的最终审计（2026-11-07 追加）

- **正式 Android arm64 构建**：用户批准分支 `feat/bluestar-116-independent-ocr-model-choice-20261107` 的准确应用源码 SHA **`c6c00aa170b8a081d8260b500fb184f6df11755f`**。GitHub Actions **[42362900151](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42362900151)** = `completed/success`；原始 CI artifact **13569623900**，名称 `blue-star-guide-1-0-16-rc17-arm64`。
- **逐项真实日志通过**：`DUAL_CAMERA_CLOUD_AI_NO_LOCAL_OCR_AUDIT=PASS`、`RC17_OCR_FOUR_CHOICES_INDEPENDENT_AND_PROMPT=PASS`、`RC12_MODEL_CATALOG_MIGRATION=PASS`、`RC13_USB_PREVIEW_JPEG_32_COMBINATIONS_AND_90_CORRECTION=PASS 32`、`RC14_USB_LIFECYCLE_FPS_4_STATES=PASS`、`RC14_USB_PICTURE_CONTROL_BOUNDS=PASS`、`RC15_AUTO_USB_SWITCH_MANUAL_OVERRIDE_AND_ATTACH=PASS`、`RC15_USB_ROLLING_10S_FPS_STALE_AND_FREEZE=PASS`、`CLOUD_OPTIMIZATION_UNIT_TESTS=PASS`、`TASK_ORDER_DRAG_RECONCILIATION_TESTS=PASS`、`BSG_UVC_FPS_61_FIRST=PASS`，所有原Native UVC打补丁成功，Android/ARM64 Gradle `BUILD SUCCESSFUL in 2m 14s`。原 APK aapt 包信息已确认 `package: name='com.vobich.bluestar.rc17' versionCode='26' versionName='1.0.16-rc17'`，原图标/两音效和arm64 JNI检查通过。
- **发行仓库**：发布 workflow **[42363173778](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42363173778)** = `completed/success`。严查原CI成功及源码SHA、官方 artifact内 SHA256SUMS、ZIP CRC、包名/版本、UVC arm64 .so、图标、金属咔叮和嗖声音效、无意外嵌入本地 OCR 模型，**只上传原CI APK，不重编译或重签**。
- **真实 GitHub Release** `blue-star-guide-cloud-rc17-20261107`，`draft=false`、`prerelease=true`，APK资产 `state=uploaded`，文件名 `BlueStar_Guide_1.0.16_RC17_arm64.apk`，**8,601,318 bytes**，服务器 SHA256 **`8ffa04c4c74932b67b8bc0bd1e80051d65eefe9e1cb8cb92c08fc443d9ad9c24`**，手机浏览器直链：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc17-20261107/BlueStar_Guide_1.0.16_RC17_arm64.apk
- **尚未真机云端验收**：vivo X200 Pro 上选「设置→云端AI→文字识别模型」，依次专用OCR / Qwen3.7-Flash / Qwen3.8-Flash / Qwen3.8-Max，点击同一个「朗读文字」无需语音指令；核查实际输出是否只读照片原文、读序正确、能直接TTS、仅一次请求、耗时与token计费；还应验证语音“朗读文字”复用OCR选择、其他视觉任务不受影响，USB预览仍稳定接近此前60次缓冲更新/秒。模型在用户账号上是否可用、识别准确性均不能仅以本地CI证明。
