# 蓝星向导 v1.0.68：OCR单一生效选择 + GLM-OCR Layout Parsing + 已知模型快流式

## 用户明确授权范围
用户 v1.0.67 真机发现「自定义OCR选择智谱免费glm-4.6v-flash已打✓，另一页面「文字识别模型选择」qwen-vl-ocr或Qwen3.7也打✓」且操作旧页切换到Qwen3.7仍执行智谱；免费模型非流式导致识别到首次朗读变慢。用户亲测「回到原有百炼，速度马上恢复」，证明旧百炼路径本身仍快。用户同意一次性修复两个设置不同步、恢复兼容Chat SSE快速逐行朗读、并且优先增加智谱glm-ocr真正的layout_parsing专用协议。**只准改云端选择、请求协议、性能默认和对应测试，不能动已经正常工作的相机/声音/TTS/主页/预览/保益**。

## 唯一仓和版本
原仓`vobichngan285-collab/kanzi-assistant`，从 v1.0.67 已发布`exp/custom-cloud-providers-20261120` HEAD `0b81746b9933b95c90c08136c27a89f5d41f0382` 新建独立分支 `exp/cloud-single-model-layout-stream-20261120`。新v1.0.68 `versionCode 77`、`versionName 1.0.68-cloud-routing-layout-fast`，原debug应用ID `com.vobich.bluestar.c110plusquick159` 保持。没有覆盖之前的发布/仓库/分支。

## 实际修复
1. **只有一个入口**：主页→设置→云端AI，直接列出真正生效的「文字识别OCR」「视觉分析/AI追问」「语音识别ASR」的服务商和模型，三类仍独立。点击OCR：同页显示百炼所有旧模型及自定义配置，各自按**同一**`CloudProviderStore.selectedId`决定唯一 ✓，不再两个页面分别显示两个 ✓。选任何百炼OCR，如Qwen3.7，`MainActivity.selectOcrModel`**同时清除自定义OCR的激活选择**，保证下次发送百炼；视觉模型同理。现有Key、已保存自定义配置均未清除。统计页从原仅显示旧OCR选择更正为真正生效的三类模型，并保留「最近一次实际调用」及模型Token。
2. **快流式**：Chat Completions新增配置默认开启SSE，可在每套配置上关闭。v1.0.67旧已保存的`glm-4.6v-flash`和已知Qwen快模型原默认stream=false，在`CloudProviderStore`中**一次性升级为stream=true**；其他厂商旧配置不擅自修改。之后手动关闭SSE会保留，不再每次强开。用户将「文字朗读方式」设成「快速逐行」时，通用自定义Chat的SSE内容走原`OcrLineChunks`、`SpeechChunker`分片朗读，不改原百炼默认流式及云请求/网络策略。**SSE可以更早开始读，但云服务端计算/限流仍会影响实际速度，不保证智谱与百炼同速。** 专用Layout Parsing按服务端同步结果返回，不伪造流式。
3. **真正兼容glm-ocr**：在OCR自定义配置编辑页的「接口协议」增加「OpenAI兼容Chat Completions」「Layout Parsing（专用OCR）」二选一。选择后可自由填写支持该协议的**完整HTTPS API URL/Key/模型**；已存智谱glm-ocr配置可进「编辑配置」改协议，保留原Key。官方智谱示例URL `https://open.bigmodel.cn/api/paas/v4/layout_parsing`，`model=glm-ocr`，请求单个JSON `{"model":"glm-ocr","file":"data:image/jpeg;base64,..."}`（非Chat messages、非stream）；回包读取`md_results`（支持外层data包装）、usage，要求真有可读文字才成功。返回结果仍走现有本机OCR过滤/TTS、阅读模式和C110移动数据绑定。只支持OCR角色选择Layout，视觉/ASR禁选，原保存的20套配置和AES-GCM独立Key不变。同步OCR不保证首行即播。
4. **连接测试不能虚报成功**：新测试在本地生成清晰512×512大字母A图片并发送真实接口，只有返回的OCR文字是字母A（允许全角或「字母A」等），才显示识别通过，不能再只因HTTP 200就提示成功。GLM-OCR走真实`file/md_results`适配。测试小图仍会有少量真实API费用，不能保证所有视觉模型提供的描述恰好是A。
5. **安全/保护**：仅HTTPS、拒绝跳转、不记API Key；旧百炼接口及模型选择/朗读路径保留不变。OCR/视觉/ASR独立选厂商，不改C110 3012/3016/3001/2015/1001/9011/9038，RTSP150ms、C110/USB/手机取帧、JPEG ROI、TTS、拍照金属叮、保益伴随小程序、UI布局。GLM-OCR官方允许JPG、PNG等，单张图片不大于10MB，本次传现有JPEG内存数据，不另存文件/相册。

## 必须验收
静态：所涉及文件完全属于cloud选择/云请求、单测、版本、文档和CI；独立路由唯一✓；百炼3.7点击后 selectedId确为空；Layout消息必须无messages且必须带file、返回md_results；新保存Chat默认SSE、旧glm-4.6v-flash升级SSE且可关闭；A图片测试没有误判；用户Key原样保存。Java单测含角色/协议/HTTPS/SSE升级策略/测试答案；保留旧`TaskOrderTest`、`FollowupPromptTest`、`C110ZoomGeometryTest`、`C110PhotoPolicyTest`；Android Gradle arm64编译、原UVC补丁、TTS音效资源、单LAUNCHER、原包名、APK ZIP/SHA256与唯一Release的GitHub Actions全部通过才发布。
真机：进入OCR仅见**一处生效勾选**。①先选免费GLM Chat，查真正生效，再选旧百炼Qwen3.7，返回OCR页面确认✓仅在Qwen3.7，拍同照片比较首次朗读；②再选智谱免费Chat，开启快速逐行，实测是否更快；③编辑`glm-ocr`选Layout Parsing并填对应endpoint，真实拍照（非A测试）可读出内容；④C110/手机/USB拍照、TTS、本机咔叮及保益不退化。服务器响应、第三方密钥和真机扬声器/性能不能靠CI模拟验证。

