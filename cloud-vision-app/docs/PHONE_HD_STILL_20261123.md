# 蓝星向导 v1.0.76 — 手机摄像头快速/高清模式
日期：2026-11-23。仅本次功能，不调整首页排版、C110+/C110、USB、云端模型、OCR 或 TTS。

## 用户选择
设置 → 相机 → 手机拍照模式 → 快速模式（默认） / 高清模式。
保存到原 kanzi_settings 的 phone_true_hd_still_v1，首次/旧用户默认 false。保留最后选择，不增加首页按钮。

## 两条通道
- 快速：Camera2 PREVIEW + YUV_420_888，触发后取一张新帧，按原 FrameCompressor、云端分析及 TTS 处理。没有高分辨率的静态图流，完全按原方式运行。
- 高清：Camera2 TEMPLATE_STILL_CAPTURE + ImageReader(JPEG)，选择接近800万像素的硬件支持尺寸，接收到真实JPEG。JPEG_ORIENTATION=0，按原手机方向、用户镜像/翻转设置在 RAM 中做变换，超出800万像素时按比例缩小。上传真实 JPEG 给原 beginCapturedFrame → CloudVisionClient/TargetVisionClient → TTS；不写相册，不启动系统相机，也不更改云端服务商。
- 高分辨率 ImageReader 只在选中高清时创建，切模式时仅重启手机 Camera2。USB 和 C110 接口及原生库完全不修改。
- 快门/取图等待最多8秒，失败明确提示，不偷偷降为720P截帧。Camera2 不支持多流会话时同样需要真机验证并清楚报告。
- 保持原有 cloudBusy、1500ms防误触、任务/AI语音图像问答和当前版本取消令牌逻辑。本版**没有改**之前提出但未授权的 C110 连拍优先逻辑或机型识别。

## 必须核实
1. 旧设置升级后仍为快速模式；截图截图应仍约720×1280（用户实际图像质量策略可能影响尺寸）。
2. 手机后置/前置打开高清模式，真正JPEG分辨率通常为数百万像素；日志 phone_still_jpeg=1，jpeg.width_px/height_px 显著提升；浏览“上次实际发送给 AI 的 JPEG”核实；快速模式不能显示高清通路指标。
3. 手机高清JPEG图片方向正确，镜像/翻转和缩放不改变文字方向；未知相机支持能力和视角需要实机检查。
4. 保存设置并重新进入，仍按用户选择拍照；手机快速/高清能切换；USB及C110无回归。
5. 上传、SSE逐行OCR、AI追问、寻找目标、TTS实际出声、阅读功能不回归。
6. 若高清请求失败，不能重复提交旧帧、不能假报高清成功，必须允许用户手动重试。

CI证明静态约束、纯Java回归和Android构建；无法代替 vivo X200 Pro 真机的静态JPEG尺寸、拍照延迟、AF/AE曝光、图像方向及实际扬声器验收。

原 v1.0.75 发布 SHA be29823b1cc51aa3ccafb25f18f998435cccf24d 完整保留。本分支 exp/phone-hd-still-capture-20261123 从它分叉。注意原仓 main 为独立离线OCR主线，不可覆盖。
