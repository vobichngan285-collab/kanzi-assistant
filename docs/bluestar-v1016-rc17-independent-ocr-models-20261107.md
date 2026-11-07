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
