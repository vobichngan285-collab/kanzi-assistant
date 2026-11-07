# 蓝星向导 RC21 1.0.20：经真机验证的 OCR C 模式成为默认朗读

日期：2026-11-07（Asia/Taipei）。原仓库：vobichngan285-collab/kanzi-assistant

## 用户授权与真实证据

RC20 1.0.19 实验 APK 的 A/B/C 试验在 vivo X200 Pro 手机后置相机执行；用户提供匿名数值日志并确认「C模式确实快一些」，之后多轮使用明确反馈「结果很让人满意」。这一反馈为真实主观体验，不是 RC21 新包真机验收。

固定 720×1280 的一组记录：
- A 原非流式：云端总 4983ms，TTS onStart 回调 5210ms，音频首回调 5228ms。
- B 流式但全文后读：网络 5040ms，首文本 548ms，首行就绪 5040ms，TTS onStart 5225ms。
- C 流式按完整行朗读：网络 2936ms，首文本 518ms，首行就绪 1488ms，TTS onStart 1650ms，首音频回调 1672ms。
- 单次 A/C 的 TTS onStart 回调差为 3560ms，约 68.3%。TTS onStart、onAudioAvailable **并不等于扬声器实际出声**；缺乏每轮延迟统计和独立 CER/漏行/重复率数据。不能把 C 的总网络耗时单次较短解读为服务器推理加速。

用户随后明确授权：把 C 模式应用到 RC20 实验包之前的完整功能版本，而不继续作为 A/B/C 测试。

## 基线与源码范围

- RC19 已发布完整功能原分支 `fix/bluestar-118-voice-eye-scene-auto-capture-20261107` HEAD `cbe48efce307fabff79beb79d80be5c7f6706a6a`。
- RC20 的 C 模式源码直接派生自 RC19，已通过 CI 及上述真机 A/B/C 使用；此次**复用 RC20 实际编译基线** `7a29d563ab79d94739ab9568dd6e6988270483fb`，没有重新实现相机、网络 SSE 协议或 TTS。
- 新隔离分支 `feat/bluestar-121-default-line-ocr-20261107`；**RC21 真实 Android 编译源码提交 `7cf4640c9f4652b13db2cab1095c88b16deb90be`**；仅修改 `OcrSpeedExperiment.java`、`MainActivity.java`、`LargeSettingsScreen.java`、`CloudVisionClient.java`（注释）、`OcrSpeedExperimentTest.java`、`app/build.gradle` 和专项 Android CI workflow。
- 全新安装默认 `OcrSpeedExperiment.DEFAULT=C`，保持 C: single HTTPS SSE request, newline-delimited complete-line TTS, finish tail line once，检测数据上传/提示词/所选 OCR 模型不变。设置 → 云端 AI → 文字朗读方式：仅显示**快速逐行 C**与**兼容全文 A**；B 仍保留历史实验内部常量及测试但**不可在设置中选用**，非法旧偏好自动回到 C。普通视觉/联网追问/无图聊天不会被 OCR 流模式改变。
- 同一 C 模式首次分行可在未收到整段全文前 TTS；原按行排队/单次仅朗读每行一次、旧 A 回退、匿名首字/首行/网络/回调耗时均保留。非换行输出等待结束后只读尾行；未添加重复上传、重试或联网搜索。
- 新版本：`versionCode=30`，`versionName=1.0.20-rc21`，`applicationId=com.vobich.bluestar.rc21` 独立与 RC19/RC20 并存，不覆盖旧版。新包需自行设置百炼 API Key 和 USB 选项（不要发送密钥）。

## CI 核查（非真实手机新包验收）

- Android GitHub Actions **https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42386658264**：`completed/success`，确切 head SHA `7cf4640c9f4652b13db2cab1095c88b16deb90be`。
- 静态范围/安全审查、`OcrSpeedExperimentTest` 中默认C/全文A兼容/分行守序/CRLF/无换行/重复finish、RC19声控场景、RC18联网追问、RC17四 OCR模型、USB 原生采集和画面方向等沿用专项回归与 ARM64 Android 构建均通过。
- 原始官方 Actions Artifact **13579532310**，名 `blue-star-guide-1-0-20-rc21-arm64`；其中原 APK `BlueStar_Guide_1.0.20_RC21_arm64.apk`，`SHA256SUMS.txt`、badging、apk-entries 存在，APK 校验环节通过。
- Release workflow `d42dfa7f845e54affa54a0cd7862c238e71e1f1d`，独立发行 Actions **https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42387023313** `completed/success`。先核对准确成功 CI run/head SHA，再下载同一 APK，核验 SHA256、ZIP CRC、appId/版本、原 ARM64 UVC JNI、地球图标、金属咔叮及嗖声，**不重新编译、不重新签名**。
- Release：**https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc21-20261107**，`draft=false`、`prerelease=true`，附件 `BlueStar_Guide_1.0.20_RC21_arm64.apk`，`state=uploaded`，大小 **8,601,318 bytes**，官方 SHA256 **`03b8e65d95a069f26d9333b579bde9d3c1531332bd382edc3999759fe4c096cf`**。
- 安卓浏览器 APK 直链：**https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc21-20261107/BlueStar_Guide_1.0.20_RC21_arm64.apk**

## 边界与下一步

RC20 的真机 C 模式已获得用户正面结果，但**RC21 新发布包还没有用户实际安装验证**，不可将通过 CI 写成 RC21 真人真实扬声器播报或云端准确率验收。建议用户在 RC21 保持 OCR 文字识别模型不变，以手机和 USB 各点击「朗读文字」以及按住 AI 说「朗读眼前文字」测试自动逐行读、首句时间、行顺序、漏读/重复、结尾不补发全文；失败时可在设置切换兼容全文 A。若新包表现良好，下一步才考虑后续稳定签名/安装升级策略。主线本地离线 ChineseOCR-Lite+Small REC 和 CRAFT 研究均**未修改、未构建、未重跑实验**。
