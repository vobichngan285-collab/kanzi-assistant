# ChineseOCR-Lite 绿色框 → PP-OCRv6 Small REC 离线联调

复用原仓库已验证的 craft768-int8-probe 独立安卓实验工程；这是原仓库的隔离实验分支，不修改正式看字助手 app/。与此前 DET A/B APK 并存；实验包名将在 CI 中更新为独立后缀。

## 操作

1. 使用②系统相机或③相册选择清晰的中文课本、菜单、招牌或包装照片。
2. 原①-⑥双 DET 同帧 A/B 功能不变。按⑦只加载 ChineseOCR-Lite DET + Small REC，并用绿色框逐个裁切识别。
3. 检查绿色框与下方每行 CTC 原始识别文本是否严格对应，特别是小字号、跨行粘连、拆行、竖排。没有补字、自动纠错或 LLM。
4. 如需汇报，按⑧手动导出包含原始识别文字的 JSON。此文件可能包含私人信息，不会自动上传；原⑥纯检测 JSON 不含文字。

## 模型和约束

- ChineseOCR-Lite DET 固定 SHA256: ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867（短边640，RGB后处理按现有独立实验逻辑）。
- 原 Small DET 对照固定 SHA256: d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e（不参与⑦绿色框识字）。
- PP-OCRv6 Small REC 固定 SHA256: 5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634。REC 配套 inference.yml character_dict 的字符顺序在 CI 转换为 JSON，并核对字典长度与 ONNX 输出分类数，严格 CTC blank=0、去重、逐字符输出。
- 使用已核实的 BGR REC 输入、48像素高、动态宽度、(x/127.5)-1 预处理。裁切参考官方 QuadTextCrop：minAreaRect/透视变换、纵长框逆时针旋转。
- 中文框只用于检测，Small REC 只负责识字。框按几何位置粗排序，未实现书页正式阅读顺序，不连接 TTS。
- 当前 Android CN-Lite 后处理仍以矩形扩边近似 Python PyClipper，模型检测准确度、极小字和上下行混框需要真机画面对照验证。
- 100框上限，超过会显式报跳过，不伪造任何文字。无联网权限，静态照片测试非实时摄像头流。
- ChineseOCR-Lite 上游 GPL-2.0；模型权重独立分发许可仍需核实，研究实验不等于允许整合闭源商业版。
- 构建通过只是 APK 审计，不等于 viva X200 Pro 真机识字质量或温控通过。
