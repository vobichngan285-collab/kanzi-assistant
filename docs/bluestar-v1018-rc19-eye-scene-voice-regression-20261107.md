# 蓝星向导 RC19 v1.0.18：修复“我眼前是什么”变成无图文字聊天

日期：2026-11-07。用户实测 RC18：按住中间AI按钮说“我眼前是什么”，AI说自己没有视觉能力、要求上传照片；之前期望直接拍摄新照片并识别。

## 经真实源码确认的根因

- RC18 新增了没有先拍照也能进行普通文字聊天的路由，`routeSpokenRequest` 在 `VoiceAction.classify` 返回 `QUESTION` 后，直接调用纯文本 `CloudVisionClient.recognizeText`，不会上传照片。
- `VoiceAction.isObserveRequest` 原 regex 对眼前场景只接受“有什么／是什么样／什么事物”等，遗漏了**“我眼前是什么”**以及“眼前是啥”短语。因此错误落入`QUESTION`，后台模型没有图片才回答“没有视觉能力”。这不是相机失效/视觉模型能力下降。
- 保留“普通问题没照片也能联网查资料”的功能，但**明确要求看眼前**时必须优先落到 `OBSERVE_SCENE`，由原生`captureOneFrame(observe)` 获取新帧；不用补救性图片二次上传，也不额外搜索网络。
- 最小范围改动：`VoiceAction.java` 扩展场景 regex `是什么`／`是啥`，增加“刚才／上一张”等历史照片保护，避免用户问过去的内容时突然拍新照片。原 OCR `READ_TEXT` 优先级、旧照片 `REVIEW_IMAGE`、找目标优先级不改。
- `VoiceFlowTest.java` 新增用户准确原句“我眼前是什么”以及加问号、眼前是啥、前面是什么、帮我看看眼前是什么、历史场景、今日新闻、读眼前文字、找超市等测试；CI通过静态路由检查，确认 `OBSERVE_SCENE` 走 `captureOneFrame(observe)`、而不是普通纯文字`askAboutCurrentImage`。
- 从准确 RC18 发布分支 `feat/bluestar-117-web-search-followup-20261107` HEAD `52c3d41c1ba983a7ba06f7ec08432d09a729b9a3` 建独立同仓库分支 `fix/bluestar-118-voice-eye-scene-auto-capture-20261107`，保持 main/RC18及所有历史完整。
- Android 目标版本 `com.vobich.bluestar.rc19`、versionCode 28、versionName `1.0.18-rc19`，与 RC18独立共存，API Key/摄像头设置需单独输入。保留原 app Cam UVC/Camera2，RC16 native MJPEG FPS优先，RC13 32路USB方向/JPEG正确性，RC17四种文字识别，RC18三个模型联网聊天，USB硬件画质和TTS提示音。
- 计划经 GitHub Actions 全量 Java纯单测、Android ARM64 native CI、APK包名与资源校验，通过后只发布原CI制品、记录 SHA256、追加 main PROJECT_HANDOFF.md。**任何云端模型行为/真机ASR和相机结果须用户实测，不能仅依CI认定。**
