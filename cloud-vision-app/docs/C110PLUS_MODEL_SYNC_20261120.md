# C110+ v1.0.50：主页 OCR 模型在 C110+ 综合实验/实时预览中一致生效

## 触发与根因
2026-11-20 用户真机报告：在**同一个新版本**主页选好了朗读模型，手机摄像头能够正常朗读，但 C110+ 相机模块的“照片/OCR 综合实验”依旧报 `HTTP 403 AllocationQuota.FreeTierOnly`。原实验页 `C110ResolutionLabActivity` 将 `CloudVisionClient.recognize` 的模型写死为 `OcrModelCatalog.DEDICATED = qwen-vl-ocr`，且数值诊断也固定显示该模型，不读取主页 `kanzi_settings/cloud_ocr_model_v1`。既有 `C110FrameReader` 实时视频与专页高清拍照实际上已经读取该偏好；必须区分两条请求路径，不得误诊为相机 Wi-Fi 故障。

## 修改
- 从已发布/已CI成功的 `exp/c110plus-portrait-cover-roi-20261119` v1.0.49 派生隔离分支 `exp/c110plus-model-sync-20261120`；v1.0.50 versionCode59 / `com.vobich.bluestar.c110plusmodel150` 单独安装，**不覆盖** v1.0.49、旧版、主线。
- 实验页选择 `OcrModelCatalog.normalize(sharedPrefs("kanzi_settings").getString("cloud_ocr_model_v1",...))`，不再硬编码 qwen-vl-ocr，拍照点按时冻结 `chosenModel` / `chosenSpeed`，只发送**一次**真实1001快门/SD原生JPEG及**一次**CloudVisionClient云端OCR。使用同一默认原文识别提示词、保留准确率 CER 算法和原始 JPEG 不重编码；同一组不同尺寸实验应固定一个模型/模式，否则须按每次记录区分，不能混合比较。
- 相机专页“相机”菜单增加**OCR朗读模型、视觉任务模型**快捷选择，写回主页相同 `kanzi_settings` 字段；不需要退出返回主页。实验页增加当前所选OCR模型明确展示和同步切换入口，在拍照进行中不可更改；日志改为每次真实模型/速度，不再错误标示固定 `qwen-vl-ocr`。
- 主页面从 C110 相机专页返回的 `onResume` 重新读取两类模型偏好，保证显示/选择与相机页一致。C110实时 `C110FrameReader` 原有按任务类型取主页OCR/视觉模型的路径不做重写。
- 继续保留 C110+ 已验证原厂 `3012→3016→3001/par1→2015/par1`、LibVLC 3.6.5 两路150ms缓存、竖屏居中Cover ROI、真实1001快门、9011静音/9038灯光、USB/手机/TTS、原厂照片尺寸与综合实验诊断。C110+ 4003未验证，不放开删图，不改相机照片参数 `1002`。
- 云端 `AllocationQuota.FreeTierOnly` 是模型配额拒绝；模型改成用户所选后不保证每一个模型都有可用额度。严禁偷偷切换收费模型、自动重试或隐藏 HTTP403；仅用户主动拍摄触发请求。

## CI 与真机验收
静态核查：实验页不允许固定 `OcrModelCatalog.DEDICATED` 传参，必须与主页共用同一偏好并按快门冻结快照；数值诊断每次标注模型；原 20M/10M/8M/3M JPEG 不修改；仅一次照片快门与一次云请求；C110+ 150ms 两路保留，原 ROI/竖屏不回退；完整 arm64 APK/签名标识、资源/LibVLC/USB so/Release检查。
真机：在**v1.0.50 自己的主页**切换朗读 OCR 模型→进入 C110+ 综合实验，界面显示同一个模型→主动拍一次→OCR/错误响应对应所选模型；在相机模块切换模型返回主页也保持同步；实时预览点读与高清照片继续可用；无自动重试。新包独立包名需要重新配置 API Key，用户应了解所选云服务模型的额度及费用。CI编译通过不等于真机验证。
