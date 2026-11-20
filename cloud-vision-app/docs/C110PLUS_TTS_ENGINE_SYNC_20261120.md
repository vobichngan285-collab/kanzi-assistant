# 蓝星向导 C110+ v1.0.58：主页快拍的广荣微软 TTS 引擎与语音设置同步修复

## 用户反馈与根因
- 用户尚未安装 v1.0.57，要求在其既有**统一设置与「相机」分类**基础上顺便修复：手机/USB预览中选择的「广荣微软 TTS」可以正常朗读，但主页**快捷拍照→C110+真实1001快门→云OCR**无论怎样选择，都不调用用户选择的TTS音色。用户明确授权立即修复，不能重写OEM、UI或OCR。
- 已检查原仓 `vobichngan285-collab/kanzi-assistant` `exp/unified-settings-volume-diag-20261120` 最终HEAD `bb17aca82cdb73ff62492afa166518aef2e3bcb6`。手机/USB `MainActivity.changeSpeechEngine(id)` 保存 `kanzi_settings/app_tts_engine_package`，调用 `initializeSpeech()` 及时重新创建Android TextToSpeech，不会强制更改语言；但主页在首次 `HomeActivity.onCreate` 中初始化 `C110QuickShotController -> C110FrameReader`，仅构造器读取了一次引擎设置，返回统一设置 `onResume` 不会重新读，因此继续使用旧引擎。且旧 `C110FrameReader` 额外执行 `tts.setLanguage(Locale.SIMPLIFIED_CHINESE)`，与手机预览差异明显，并不保证广荣引擎支持。原码还有 `if(!ttsReady)return` 的流式结果无提示丢词风险。以上是**明确源码缺陷**；具体声音仍待 vivo 真机验证。

## 修改清单（只触碰 C110 TTS 路径，不重构相机）
1. `C110FrameReader.syncSpeechSettings()` 重新读取与 `MainActivity` **完全相同**的 `kanzi_settings/app_tts_engine_package`、`local_tts_follow_default`、`local_tts_rate`。若真正的TTS引擎包名变了才停止旧实例并按指定包创建新 TextToSpeech；只有语速改变则直接 setSpeechRate，不重复初始化，避免多余卡顿。
2. 与用户已验证的手机/USB预览 `MainActivity.initializeSpeech()` 保持一致：**不强制对广荣等第三方 TTS 执行 setLanguage(Locale.SIMPLIFIED_CHINESE)**，也**不悄悄回退系统默认引擎**。每轮初始化设代际编号防止旧引擎 OnInit 回调覆盖新引擎；初始化失败或 `tts.speak` 未接受时明确输出状态。
3. `HomeActivity.onResume -> C110QuickShotController.onResume` 与 `trigger` 都会主动同步TTS，用户从「主页→设置→语音与朗读→选择广荣微软语音→返回主页」后不重启 APP、下一次快拍立即使用已选引擎。C110 视频专页恢复前台时也刷新；C110 实时视频点击、高清JPEG识别、重新朗读时再核查当前配置。
4. 若云 OCR 提前返回，所选引擎 `OnInit` 还未报告 SUCCESS，则**仅当前用户发起的这次识别**临时保留不超过100k字符的待播报文字；语音准备就绪后一次播放，切换新一张/停止/销毁时清理。对失败和不支持的引擎显示诊断状态，不能谎称扬声器一定出声。
5. **保持原样**：v1.0.57唯一统一 `BlueStarSettingsScreen` 深蓝色、相机分类、音量双击600ms及原始按键诊断；主页快捷拍照不切页；C110+ `1001` 一次真实快门、原始 JPEG→云端 OCR、150ms LibVLC/RTSP/竖屏 ROI，手机Camera2、USB UVC、9011/9038，原厂四档照片尺寸准确率实验及其它设置均保留。未证实的4003删图禁用、照片尺寸1002不写。
6. 本版 v1.0.58 / versionCode67 / 独立 `com.vobich.bluestar.c110plustts158`，旧 v1.0.57 不覆盖；本版首次启动仍须单独填写百炼 API Key，选定的第三方 TTS 引擎属于**系统安装应用**，但本APP选项仍需在本版设置里选一次。

## CI / 真机验证
- GitHub CI静态+Android编译检查 `C110FrameReader` 显式读共享引擎和速度字段、没有强制 `tts.setLanguage`、只在实际引擎变化时重建 TTS、TTS 新旧回调 generation guard、识别文本在未就绪时保留并在成功初始化后输出、Home恢复与快拍时重复检查选择、原C110相机专页也同步。验证原OEM/150ms/云端模型/音量键诊断/唯一蓝星设置和 APK 内容未退化。CI完成**不等于真机已出声音**。
- vivo X200 Pro 实机应在**同一个 v1.0.58**的设置→语音与朗读里选「广荣微软」并试听听到自选音色；返回主页点击第一项「快捷拍照」，保持主页不切页，观察高清取图、云OCR与是否**由广荣音色朗读**。再换一种已装TTS音色，不退出App重复测；返回手机/USB预览同样声音。若无声记录「云OCR是否有文字、TTS引擎选择包名、初始化失败提示、系统语音试听是否出声」，不要误判为C110 Wi-Fi或云端取图故障。
- 音量键双击是否有效仍由 v1.0.57 `设置→相机→音量键快捷拍照` 的原始DOWN/UP诊断验证，本次**没有**把物理按键宣称已解决。
