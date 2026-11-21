# 蓝星向导 v1.0.74｜主页/预览按住AI统一＋寻找任务跨页面＋同服务商API Key复用

## 用户授权边界
- 用户在 v1.0.73 实测：主页按住AI说“如果看到医院的招牌立即告诉我”只答“看不到”，预览页按住AI则可设置寻找目标。这是旧版主页 HomeVoiceAssistant 直接走文字聊天、预览 MainActivity 先经 VoiceAction.classify 的分裂逻辑。
- 已明确批准本轮六项：统一主页/预览按住AI；跨页面保存/取消/拍照检查；自然语音寻找；同服务商密钥复用；新建接口留空Key时正确保存；完整回归。**所有主页布局、五行入口、底部三栏、字体严格50%、卡片高度、图标颜色、无障碍功能不改。** 不新增自动连拍/持续摄像。
- 唯一基线：原仓库 `vobichngan285-collab/kanzi-assistant` 已发布 v1.0.73 HEAD `3115fa804298a7a6d8f362ec319a9f9ed8f2a725`，独立分支 `exp/voice-key-reuse-20261121`。主模块 versionCode 83 / versionName `1.0.74-voice-key-reuse`；debug applicationId `com.vobich.bluestar.c110plusquick159` 与唯一 HomeActivity Launcher 不变。

## 统一按住AI与共享寻找目标
1. 两处都使用已有、未改底层结构的 `VoiceAction.classify(识别语音,当前寻找目标)`，不用新造另一套“识别意图的云AI”。新增明确自然句式：“如果看到医院的招牌立马告诉我”、“如果你看到医院招牌就告诉我”、“发现药店提醒我”、“看到医院招牌马上告诉我”，只在明确有“看到/发现 + 目标 + 告诉/提醒”等完整语法时启动寻找；问路、查询和普通聊天保持 text-only。保留原“帮我找医院招牌”处理；招牌现在不被自动剥掉，视觉检查以真实场所门头可见证据判定，广告和孤立指示牌不算。
2. `FindingTargetStore` 统一使用 MainActivity 早已使用的 `kanzi_settings.continuous_find_target`，不创建额外状态副本；主页、MainActivity 和 C110FrameReader 均读取该值，`clearIfCurrent` 防止旧照片识别异步返回误清除用户后来的新目标。只有收到 **found** 且有明确视觉证据才自动结束目标；没找到则保持等待下一次**手动**拍照。
3. HomeVoiceAssistant 保留原 VoiceRecorder、CloudAsrClient、TTS、普通对话和真实旧照片追问；START_FINDING/STOP_FINDING 不向无图的AI发文本Chat，而是本地保存/取消并 TTS 告知“主页快捷拍照或预览拍照后自动检查”；读文字和观察画面的明确语音指令触发原主页同一 C110 1001 单次高清快拍，并对本次照片覆写一次任务，不改变当前用户选定任务。对于“重看照片”但当前无图，直接要求先拍照，不声称看到图。
4. `C110QuickShotController` 保留所有原先 1001 高清拍照、Wi-Fi租约/请求、离线相机指令、失败不重试、单worker忙碌锁、照片到朗读流程；仅增加可选的一次性 `TaskCatalog.Task` 参数。C110FrameReader 在已经取得的真实 JPEG（首页原图或 C110预览点取帧/高清照）按 `FindingTargetStore` 判断是否运行同一个 `TargetVisionClient.inspect`；无目标按老 OCR/视觉/TTS/SSE 流程。**不增加自动拍照、后台摄像头循环或照片持久化。**
5. MainActivity 在 onResume 从共享存储恢复目标，用户进入预览后不需重说；拍照开始时读取最新的共享目标（而不是 Activity 启动时的旧局部字符串）；视觉找到只在目标仍匹配时才清除。已有USB/手机摄像头帧和TTS均不改造。所有页面的原状态提示和读屏控件仍可用。

## 同HTTPS服务商 Key 复用与安全
1. 旧版 `CloudKeyStore` 仍是唯一 Android Keystore AES-GCM 加密层，所有旧Profile Json、模型选择、老百炼Key和现有命名密钥均保留不迁移。每套新Profile仍具有独立 iv/ciphertext；复用时从旧加密源解密后用新 IV 加密一次，**删除源Profile不影响其他Profile**。
2. `ProviderKeyReusePolicy.sameProvider` 明确要求完整 **HTTPS scheme + 精确主机名 + 同端口**。例如智谱`open.bigmodel.cn`的`/chat/completions`与`/layout_parsing`同源可复用；`open.bigmodel.cn.evil.org`、百炼/智谱不同域名、HTTP降级与不一致端口禁止。URI可包含不同路径，但同服务商用户授权复用；不因为名称/模型ID字符串猜服务商。
3. `CloudProviderStore.reusableProfiles` 仅列出能正常用 Keystore 解密的同源配置，不返回Key内容；百炼老单Key如果目标地址与已有原百炼 `BAILIAN_URL` 完全同源也可作为特殊来源。新建时Key留空，**恰好一个匹配来源就自动复用**；多个可选来源必须点击“复用已有密钥（可选）”明确选择；零匹配则要求输入。编辑已有Profile时留空原样保留旧Key，用户主动换Key时覆盖。显式选定来源后若API URL更改到另一服务商，保存时再次严格拒绝跨域。
4. 修改输入界面中 API Key 的框内提示语，并仅增加一个轻量的“复用已有密钥（可选）”操作（48dp点击区域）；不新增额外配置界面，列表、主页/设置原布局都不变。所有日志/错误只能显示来源名称、状态，绝不能含API Key、照片、识别内容或Bearer Token。

## 回归与发布条件
- CI严格限制修改范围：`app/build.gradle`、`VoiceAction.java`、`FindingTargetStore.java`、`ProviderKeyReusePolicy.java`、`HomeVoiceAssistant.java`、`HomeActivity.java`、`MainActivity.java`、`C110FrameReader.java`、`C110QuickShotController.java`、`TargetVisionClient.java`、`CloudProviderStore.java`、`BlueStarSettingsScreen.java`、两份新的纯Java测试 `VoiceActionSharedTest.java` / `ProviderKeyReusePolicyTest.java`、本文和工作流。
- 全部通过单测（既有 Qwen/GLM 允许列表、OCR 行序、任务/zoom、百炼去重、新自然语言寻找与精确URL安全）、保护性源码断言、固定UVC补丁、Android完整ARM64 Gradle编译、APK唯一Launcher/包名/版本/LibVLC/UVC/金属提示音/语音发送音/SHA256校验、发布前验证，才可生成 v1.0.74 Release。
- 真机仍需 vivo X200 Pro 测试两处“按住AI”自然句/取消；主页C1101001和预览手机/USB照片、C110预览点帧查目标；没找到继续寻找/找到后有证据才停止；重新切换页面保持状态；普通OCR和视觉追问/TTS和原有云端参数/SSE不回归；一个/多个/零个可复用密钥、智谱/百炼不同供应商、编辑已有Key留空、删除源配置不影响其他模型。**CI成功不等于真机完全通过。**
- 此为保留老Release的独立试验分支。若Android安装提示不同debug签名，不要卸载旧包导致Keystore密钥丢失；先商量安全迁移。
