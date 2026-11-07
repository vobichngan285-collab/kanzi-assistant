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

## 实际 CI、最终 APK 与验收边界

- 本轮应用构建的**准确源码 SHA**：`2b329b0ef5ae7a93637bd27621b8f2a3bc9d0c9e`；原 GitHub Actions **[42366341112](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42366341112)** = `completed/success`，原始 CI Artifact **13572445061**，名称 `blue-star-guide-1-0-18-rc19-arm64`。
- 实际 CI 日志确认 `VOICE_PRESS_TARGET_AND_WAV_TESTS=PASS`、`RC19_EYE_SCENE_SPEECH_CLASSIFIED_TO_FRESH_CAMERA=PASS`、`RC18_FOLLOWUP_WEB_AND_STANDALONE_PROMPTS=PASS`、`RC18_SEARCH_ONLY_EXPLICIT_FOLLOWUP_THREE_MODELS=PASS`、`RC17_OCR_FOUR_CHOICES_INDEPENDENT_AND_PROMPT=PASS`、`RC13_USB_PREVIEW_JPEG_32_COMBINATIONS_AND_90_CORRECTION=PASS 32`、`RC14_USB_LIFECYCLE_FPS_4_STATES=PASS`、`RC14_USB_PICTURE_CONTROL_BOUNDS=PASS`、`RC15_AUTO_USB_SWITCH_MANUAL_OVERRIDE_AND_ATTACH=PASS`、`BSG_UVC_FPS_61_FIRST=PASS`。ARM64 Native UVC + Android 构建 `BUILD SUCCESSFUL in 2m 3s`。aapt 真实 APK 包名版本 `com.vobich.bluestar.rc19` / versionCode28 / versionName`1.0.18-rc19`，图标/两音效/UVC .so 通过。
- GitHub 验证发行 run **[42366495464](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42366495464)** 最终 `completed/success`（**第2次尝试**）。初始尝试1在原构建尚未结束时提前检查 `conclusion=success`，按安全校验失败；等原 CI 正式完成后，原地重试失败作业得到成功。发行环节以原 CI 准确 source SHA、SHA256SUMS、ZIP CRC、JNI、TTS 音效、图标和包名检查原 bytes，**没有重编译或二次签名**。不隐瞒第一次启动过早的失败。
- 真实公开 GitHub Release `blue-star-guide-cloud-rc19-20261107`，`draft=false`、`prerelease=true`；APK `BlueStar_Guide_1.0.18_RC19_arm64.apk` 状态 uploaded，**8,601,322 字节**，SHA256 **`8d06d3e6925d6bc257d9d55462c95f2da6c30167fadaff39c02ad8e8f186667c`**。
- 手机浏览器可复制 APK 下载直链：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc19-20261107/BlueStar_Guide_1.0.18_RC19_arm64.apk
- **尚需真机**：在 vivo X200 Pro 用 RC19 按住中间 AI 按钮说“我眼前是什么”/“前面是什么”，核查状态确有新照片、收到视觉回答，不再要求上传照片。再试“今天有什么新闻”走普通文字联网聊天，“朗读眼前文字”走独立OCR、“刚才那张照片”不偷偷拍摄新图；核查原 USB 方向与高帧率仍正常。CI静态/Java测试证明路由正确，不等于实际设备ASR对每种口音文字都完全相同，更不能保证云端模型回答正确。
