# 蓝星向导 RC20 — OCR A/B/C 流式响应与朗读速度实验

日期：2026-11-07。用户同意通过同一测试 APK 内 A/B/C 三模式对照分析此前2～6秒识字延迟。用户确认实验成功且不漏字不重复后，再决定正式版是否改动。本实验不把“没改善”直接认作服务器繁忙；还有模型 TTFT、图片处理、网络 RTT 和服务器排队等原因。

## 研发基线
- 原仓库 `vobichngan285-collab/kanzi-assistant`，先读 `main:PROJECT_HANDOFF.md` 并核实 RC19 已成功构建发行；从 RC19 原分支 `fix/bluestar-118-voice-eye-scene-auto-capture-20261107` HEAD `cbe48efce307fabff79beb79d80be5c7f6706a6a` 派生 `exp/bluestar-120-ocr-abc-stream-speed-20261107`；不改原仓库其他分支。
- Qwen OCR 与多模态 OpenAI兼容接口官方文档：`https://help.aliyun.com/zh/model-studio/qwen-vl-ocr`、`https://help.aliyun.com/zh/model-studio/qwen-vl-compatible-with-openai`、`https://help.aliyun.com/zh/model-studio/stream`。在同一 Chat Completions HTTPS 请求里用 `stream:true` 获得增量响应，`stream_options.include_usage` 让服务器尝试报告真实Token用量。不添加第二次API请求，不启用联网检索。
- 用户已对比 OCR 四模型和720p/1080p，主要拖延为 `cloud.network_ms` / 响应头时间，照片新鲜帧/编码大约数十毫秒。日志里 TTS 音频回调不是扬声器确实出声时间。

## 实验功能
- 「设置 → 云端AI → 文字识别速度实验」三种选择，本地持久化 `cloud_ocr_speed_experiment_v1`，**默认 A**，切换后仅影响「朗读文字」OCR任务及语音READ_TEXT。
- **A**：原始 `stream:false`，完整内容返回之后启动TTS。与前版使用相同相机、图片压缩、提示词、模型、非流式网络和TTS路径。
- **B**：`stream:true`，接收模型逐字返回，但是必须等完整结果才进行TTS。记录首个增量字符/首条完整行的到达时间，判断首字与全文是否相隔较远。
- **C**：`stream:true`，当且仅当识别结果中出现换行符，才把前面已经收齐的**完整一行**入队朗读；串流结束后仅将末尾未结束行读一次。如果模型始终不换行，就等全文结束；不猜测标点、不修改识别词、不重复朗读完整全文。使用原有本地语音筛选与队列。若中途流式网络失败，应用不会默默多付费重新上传照片。
- 三档均为一次拍照一次云端调用，无同时调用四个OCR模型。保留用户原批准的提示词 **“识别图片中清晰可见的文字，按正确阅读顺序原样输出，不解释、不补充、不猜测。”**，专用OCR+另三种大模型的独立切换保持原样。
- 诊断新增 `ocr.speed_mode`（0=A、1=B、2=C）、`cloud.response_headers_ms`、`cloud.first_text_ms`、`cloud.first_line_ready_ms`，并保留 `cloud.network_ms`、`capture.total_ms`、`tts.onstart_ms`、`tts.first_audio_callback_ms`，全都是匿名数值且遵循50次操作/7天/每次24事件上限，不保存文字/照片/语音/API Key。
- 新纯 Java `OcrSpeedExperiment` + `OcrLineChunks` + `OcrSpeedExperimentTest`，覆盖三个模式、非法值回退A、按多个SSE碎片合并换行/CRLF、空行、尾行、无换行不提前朗读、重复完成不重复播放。CI复用之前RC19语音转拍照、RC18联网、RC17四OCR模型、RC13 USB方向32路和RC14/15/16 USB帧率的回归。
- 本轮不改原生 USB 视频预览、设备接入、相机转向、朗读提示音、画质调节、图片分辨率、普通视觉模型、联网智能追问、用户任务预设或其他慢速修补逻辑。
- 测试版本 `com.vobich.bluestar.rc20` / versionCode29 / versionName `1.0.19-rc20`。由于历次GitHub runner debug签名不稳定，不能安全承诺覆盖旧版保留数据，故实验包与RC19并存，需要重新单独配置自己的百炼API Key。禁止将API Key贴到聊天里。

## 用户实际 A/B/C 测试要求
选择一个模型（建议先专用 Qwen-VL-OCR），固定720p或1080p，在同一张清楚印有多行文字的菜单或科室牌前测试。按 A/B/C交替顺序（建议 A→B→C→C→B→A）各至少3次；每次正常点击「朗读文字」，不重复手动提问。记录是否读错/漏行/重复以及实际听到第一句的大致时间。结束导出原匿名运行诊断发回。A不包含流式首字时间（为 -1），B/C应有首字和首行到达时刻，C可能提前发声，特别是模型有换行时。是否推广 C 由用户根据**真实的听觉速度和识字质量**确认决定。

## 发行记录
等待 GitHub CI、ARM64 APK真实构建、SHA256、release和用户真机后追加，不可在此之前宣称验证成功。
