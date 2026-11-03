# 看字助手（Kanzi Assistant）安卓 OCR 项目工作交接记录

**整理日期：2026-11-03（Asia/Taipei）**
**GitHub 原仓库完整地址：** https://github.com/vobichngan285-collab/kanzi-assistant
**本交接文件永久链接：** https://github.com/vobichngan285-collab/kanzi-assistant/blob/main/PROJECT_HANDOFF.md
**长期存放位置：** GitHub 原仓库 `main` 分支根目录 `PROJECT_HANDOFF.md`。
**交接目的：** 如果 ChatGPT 对话记录丢失，在新对话中先通过 GitHub 连接读取本文件。必须接手已有仓库与已完成实验，**不要从头建项目或重跑全部测试**。

> 资料来源：用户明确给出的技术约束、截图以及此前助手报告的 GitHub 核查结果。以下“此前已核查”均指此前对话中的报告；恢复工作时应对 GitHub 当前分支、构建与文件状态重新核对。不要把未经当前轮核实的实验结果写成新发现，也不能宣称 CRAFT 已完成最终集成。

## 1. 项目目标与不可改变的约束

- 项目：面向低视力/视障用户的 Android **USB UVC 外接摄像头实时 OCR + 中文 TTS 朗读**工具，对标“豆芽看见”的体验：**精准单行白框、提示叮声、朗读文字**。
- 强制要求：**本地离线、低延迟、实时视频**；不能把每帧送云端视觉大模型。复杂难样本可考虑**用户手动触发** AI 辅助识别。
- 中文、简单易操作的界面；支持多套 OCR 引擎独立切换。
- 基线：**PP-OCRv6 Small**，检测采用 **DBNet**；另有 **PP-OCRv6 Tiny**、**PP-OCRv5 Mobile**。要分别核查三套模型的预处理、字典及 CTC 解码配置，**禁止未经核对共用配置**。
- **PP-DocLayout-S** 已实测只能输出标题/段落等大块区域，不能提供精准单行白框；只可做版面辅助，不能作为主检测器。
- 以前做过的控制变量实验：**人工标注精准单行框裁剪 → 固定 Small 识别**，区分检测定位错误和识别模型能力不足。
- 当前工作方式：**不得启动自动 Agent、自主工作流、自动循环脚本**；用户逐步下达单步指令，按步骤检查、修改、验证。可以按用户明确指令直接通过 GitHub 操作，不要求用户自己编译。
- 不要仅改 UI，不要把 CI 通过、合成样本成功误报为真实摄像头识别准确。

## 2. 原仓库及版本锚点（禁止新建/随意覆盖）

- **GitHub 仓库：** https://github.com/vobichngan285-collab/kanzi-assistant
- 较早交接状态：`0.8.1` / `versionCode 26`，分支 `release/v0.8.0-ocr-engine-rework`，Commit `e9a2574c4122a8b36e8c314db9a19bcb6688ac09`，Release `test-build-172`：https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/test-build-172
- **此前对话报告的同一 release 分支更晚 HEAD：** `47fda297d42d82c24bd55324f853792ce6fd67ef`（2026-11-02）。
- **此前对话报告的较新实验主线：** 分支 `feature/strict-offline-ocr-order-guard`，Commit `c50855877c2d22a52333585e433528830a498e13`。
- **此前找到的已有 APK：** `test-build-315`，看字助手 **0.9.9 / versionCode 40**，发布页 https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/test-build-315 ，直接 APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/test-build-315/app-release.apk
- 此前报告 #315 构建、签名校验及 Release 上传成功；Actions：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41438694722
- **注意：** #315 是已有的实验基线 APK，**不是**本轮 CRAFT 最终集成成果；旧版 #172 交接不能作为当前最新开发状态。

## 3. 已发现并继续关注的 OCR 乱码问题

- 旧版 0.8.1 中 Small、Tiny、v5 Mobile 三模型均可跑、可发声，但中文原始识别结果严重乱码；问题在 OCR 上游而非 TTS。
- 当时 v5 Mobile 实测：画面 1920×1080；19 个检测框；平均置信度 0.232；检测张量 1×3×960×544；识别输入例 48×580、48×603；错误示例：`conf=0.7599 → 品食制号`、`conf=0.6502 → 誉升胎不品产本`。
- 排查方向：RGB/BGR、归一化、识别动态宽度（**超过 320 不自动意味着错误**）、ONNX 输出类别数与字典一致性、CTC blank index、字符映射。
- 此前 CI 已加入模型类别与字典校验；三模型类别数报告：Small **18,710**、Tiny **6,906**、v5 Mobile **18,385**。部分合成中文样本 CER 为 0.000，但**不能证明真实摄像头中文小字准确率已达标**。
- 已有 2026-11-02《看字助手 #315：OCR 优化第二轮验证报告》（原对话提及）：DBNet 检测边长 1600→1920 有得有失且更慢；原图局部裁剪受框准确性影响很大；CLAHE/锐化/双倍插值未证明稳定提升；双边滤波仅对个别难样本有效；裁剪框扩边可能补字也可能引入杂字。**避免重复从零开始做同样试验**。

## 4. 2026-11-03 CRAFT 实验——最重要进展

### 4.1 已创建的实验分支（此前对话报告）

- `test/craft768-int8-standalone-phone-apk-20261103`：768 INT8 独立安卓真机实验 APK
- `test/craft768-int8-small-rec-linkage-20261103`：CRAFT + Small REC 联调
- `test/craft768-int8-qdq-vs-fp32-20261103`：INT8 与 FP32 对照
- `test/craft720-vs768-vs640-20261103`：不同检测输入尺寸对照
- `test/craft768-int8-roi-dense-text-v2-20261103`：密集文字 ROI
- `test/light-dbnet-vs-craft-20261103`：DBNet 与 CRAFT 对照

### 4.2 用户截图中的独立测试软件

- 标题：**CRAFT 768 INT8 真机性能实验**。**独立安装，不替换看字助手**。
- **模型：20.95 MB INT8 量化 CRAFT，固定 768 检测输入**。前述构建报告记录文件字节数 **20,946,608**。
- 仅测试 **CRAFT 找字阶段**，**不包含** PP-OCR Small 文字识别，也不代表完整“叮声+朗读”的时延。模型、图片不上传。
- 8 项操作：①校验加载模型；②内置 1920×1080 模拟文字图；③相册选真实图；④系统相机拍真图；⑤当前图单次推理；⑥连续 24 次（含两次预热）；⑦可选系统语音试听（非 OCR 朗读）；⑧导出匿名性能 JSON。
- 建议真实图片分类：**纸面小字、商品包装、菜单、复杂背景**。

### 4.3 之前已生成、可下载的测试 APK

- 文件：`CRAFT768_INT8_phone_lab_debug.apk`，独立包名 `com.vobich.kanzi.craftprobe`。
- GitHub Actions Run：**41581474629**，此前报告 2026-11-03 13:04（台湾时间）构建成功。
- Artifact ID：**13205587514**，下载 ZIP（含 APK、说明、SHA256）：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41581474629/artifacts/13205587514
- 构建详情：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41581474629
- 先前报告该 Artifact 预计 **2026-11-13** 到期；恢复对话时**重新检查能否下载**，不要保证永远有效。

### 4.4 此前实验结论（只能说“报告显示”，勿当成最终验收）

- 在 **vivo V2405A** 上已有 **6 轮 × 24 次** CRAFT INT8 真机推理记录。
- **纯神经网络推理 P50 ≈ 544–665 ms**，尚未包含完整检测后处理、Small REC 和语音链路；需要警惕实时性的瓶颈。
- 此前记录的 PSS 约 **608 MiB 至 547 MiB**；没有足够证据判断出现持续内存泄漏。
- CRAFT + Small REC 安卓联调的源码编译及基本解码测试通过：
  https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41592177297
- 部分实拍文字 CRAFT 有补漏，也有场景不如现有 Small/DBNet 检测；局部 ROI 补漏可能耗时明显；竖屏图强制横屏白边画布可能损害检测，已有独立对照。
- **关键未证实：** CRAFT 在中文密集小字场景中稳定输出精准独立单行框，并以低延迟完成 CRAFT→Small→TTS 的全链路。**不得称作已实现/已验收**。
- 原版 CRAFT 预训练权重偏英文；对中文书籍、包装必须用实拍照片和标注进行公平对照，不预先断言优于 DBNet。

## 5. 当前最合理的技术决策

- 现有 PP-OCRv6 Small + DBNet 主线必须保持可用；**CRAFT 仍为候选精细文字检测/局部补漏辅助组件**，暂不直接替换正式检测头。
- **下一步不是重做 #172、#315、6×24 次性能测试，也不是重新创建 App。** 应优先读取已有各 CRAFT 分支、已生成的 JSON、后处理代码、真实图片评测输出，把成绩汇总到同一份对照表。
- 后续可按用户单步指令，做固定照片、固定 Small 识别模型下的 **DBNet vs CRAFT vs 人工标注框** 控制变量对照，重点记录：
  1. 单行框召回率与边界质量；跨行粘连、同一行碎片率。
  2. 进入相同 Small REC 后的中文 CER 和可朗读完整度。
  3. 真机预处理、模型推理、后处理、识别、朗读分段耗时与内存。
  4. INT8/FP32、640/720/768、局部 ROI 的收益与代价。
- 若 CRAFT 不能提供足够的准确度/延迟净收益，保留 DBNet 为主，不强行集成；必要时探讨更轻量的单行框后处理。

## 6. 用户要求的工作纪律

- **原 GitHub 账号、仓库、现有分支与提交保留，不新建仓库、不随意覆盖已有代码。**
- 每次开始先核验 GitHub 分支、HEAD、workflow、代码以及 Release；**不要从这份记录推断远端绝不会变化**。
- 经用户单步明确要求后才动手修改；每次修改必须真实检查 diff、Actions 日志、签名和 APK 资产发布情况。不可空口说“修复完成”。
- 尽可能通过 GitHub 直接操作；不要要求用户自己本地构建。
- 严格区分：**合成图测试通过**、**独立 CRAFT 真机推理通过**、**与 Small 基本联调通过**、**真实应用端到端验收通过**，它们是四种不同的结论。

## 7. 新对话恢复时的第一条指令（建议原样复制）

> 请接手我已存在的 GitHub 仓库 https://github.com/vobichngan285-collab/kanzi-assistant 。请先通过 GitHub 读取默认分支 `main` 根目录的 `PROJECT_HANDOFF.md`（本文件）作为最新工作交接记录，不要新建项目或覆盖原有代码。按照文件给出的 Commit、分支、Actions 和实验结果核对当前真实状态。先总结目前进度和未解决问题，等待我下一条单步指令再修改或构建代码。

## 8. 长期更新规则（2026-11-03 建立）

1. **唯一固定位置**：`main:PROJECT_HANDOFF.md`。不要在每个临时实验分支另建互相冲突的交接文件；实验代码可以在各实验分支，交接记录以 `main` 为准。
2. **每次实验/修复完成时**，在用户明确下达的任务范围内，通过 GitHub 重新读取本文件当前 blob SHA，追加记录：日期、目标、分支、Commit、相关 Actions/Release/Artifact、测试环境、结果、失败与风险、下一步。保留历史结论，不用新文本覆盖旧实验记录。
3. **不能虚报**：若某项结果仅来自先前聊天、未读取原始日志，标注“历史报告待复核”；若只有 CI 编译通过，不能写成真机 OCR 成功。
4. **禁止无授权的后台自动操作**：不会自动监视仓库或自动提交。用户在单次会话中授权进行的实验可连同其交接更新一起执行；其他时间须由用户发起。
5. **更新完成后核验远端**：重新通过 GitHub 读取本文件确认内容确实保存，报告新的 Commit SHA 和永久文件链接。更新文档提交尽量加 `[skip ci]`，避免仅修改交接记录却触发构建。

### 更新日志

#### 2026-11-03 — 建立 GitHub 长期交接记录

- 目标：把今天恢复的 CRAFT/DBNet/Small 实验交接情况集中保存到原仓库默认分支。
- 本次只增加项目文档，不修改 App 源代码、模型、分支策略或签名配置，也不启动新的 CRAFT 性能实验。
- 原始历史材料为 `看字助手_CRAFT实验工作交接记录_2026-11-03.md`；为方便未来查找，将其整理进这个固定文件名。

**交接记录持续更新，切勿删除历史实验记录。**


#### 2026-11-03 — DBNet → 单行白框源码专项静态审查（只读代码，不构建）

- **用户批准范围：** 检查现有安卓代码和 SDK 处理链路，提出问题清单与修改建议；本次不修改 App/模型，不启动构建和真机压力测试。
- **被审查的正式实验主线：** `feature/strict-offline-ocr-order-guard`，HEAD `c50855877c2d22a52333585e433528830a498e13`；注意仓库默认 `main` 只统一存放本交接档案，并不是最新安卓程序代码分支。
- **模型与 SDK 路径：** `app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt` 在 202–237 行配置三个独立 DET/REC 资产路径，Small/Tiny 最长检测边 1600、v5 Mobile 960；CI 的 `.github/workflows/android.yml` 23–28 行固定检出官方 PaddleOCR `v3.7.0` SDK，125–160 行在构建时对识别预处理 BGR 做校验/补丁。
- **源码确定的链路：** `MainActivity.java:180–188` USB NV21 相机回调；`MainActivity.java:256–343` 帧门控和单槽推理；`PaddleOcrEngine.kt:276–383` NV21→Bitmap→PaddleOCR→阅读排序文本和原始四点框→`OcrReadResult`；`MainActivity.java:334–343` 分别调用 `OcrOverlayView.show(...)` 与 `onRecognized(text,...)`；`MainActivity.java:1819–1915` TTS去重、音效和分段朗读。
- **P1：白框与文本行分离（已确认的设计事实）：** `TextFlowRebuilder.java:104–231` 将 OCR `ReadingOrder.Box` 进行行/流的几何聚类，最终只返回 **String**；`ReadingOrder.java:170–218` 调用该文本重建；然而 `PaddleOcrEngine.kt:350–383` 返回的盒子是 OCR SDK 原始四边形列表，`OcrOverlayView.java:38–48,90–120` 原样遍历并绘制，**不使用重建后的行分组**。故“朗读合并成一行”不代表“白框也是一行”；原始碎框在视觉上仍碎。
- **P1：输出结构缺少逐行对应关系（已确认的设计事实）：** `OcrReadResult` 当前只有 `text:String`、`boxes:List<FloatArray>`，没有 `lineId`、来源原始框索引集合、逐行文本/四边形/置信度、何行正在朗读等元数据；当前并不具备严格的 TTS 行级高亮绑定。朗读还可能经 `OcrContextRefiner.refine` 修改显示之外的文本（`MainActivity.java:1821–1845`），进一步需要明确原始字符和朗读文本的区别。
- **P2：SDK 的跨行检测粘连不能靠应用层排序自动拆开（已确认的链路限制，非最终根因）：** 官方 `v3.7.0` `DBPostProcessor.kt:60–139` 在预测图上二值化→轮廓→`minAreaRect`→`PolygonUnclip`→再取四点框。官方 `OCREngine.kt:76–151` 对这些原始框排序、四点裁剪、逐框先识别，然后应用端才做文本流重建；如果两行已经落入一条检测轮廓，当前并无跨行拆分步骤。不能声称这是所有中文乱码或白框精度问题的唯一根因。
- **P2：可能出现“无朗读文字却仍画白框”（静态代码路径推断，需样例验证）：** `PaddleOcrEngine.kt:350–373` 过滤条件仅是四点坐标数为 4，未筛掉 `item.text.isBlank()`；而 `toReadingBoxes(...):443–453` 明确过滤空白识别文本。是否真实出现须检查具体 SDK 返回和样本，不能直接说已在真机复现。
- **P3：坐标映射需回归验证，不凭源码断言错位：** USB 模式 `OcrOverlayView.java:62–121` 通过视频视图的实际位置、旋转、缩放映射四点；`MainActivity.java:1183–1222` 尽量按完整画面做 FIT 显示，`MainActivity.java:1227–1235` 请求 1920×1080 MJPEG 原始 NV21 回调。手机 Camera2 模式另有 `PhonePreviewGeometry`；现有 JVM 几何烟雾测试不能替代真实 UVC 叠加层位置检查。
- **已有测试保护：** `.github/workflows/strict-ocr-order-tests.yml` 含 `RealtimeTextFlowSmokeTest`、`RealtimeTextOrientationSmokeTest`、`PhonePreviewGeometrySmokeTest` 等阅读顺序与几何测试，但这次检查没有发现“分组后的白框严格对应一行”的端到端用例。**未运行新测试，未触发 GitHub Actions**。

**下一步唯一建议（等待用户明确单步批准）：** 保持 DBNet/Small 模型、原始 OCR 字符和正式 APK 不变，在隔离实验分支对现有 `TextFlowRebuilder` 提取可测试的“逐行结构输出”（原框ID列表 + 行文本 + 行四边形/可回退多框），用固定人工标注样例检验“同一行碎框合并”“相邻两行不误合并”“旋转/竖排不破坏”“每个原框恰好对应一次”的不变量。先跑纯 Java 静态几何测试，不接入 TTS、不构建 APK；通过后再考虑影子模式叠加对照。

**继续保留的限制：** CRAFT 768 INT8 已有 6×24 真机压力测试，局部补漏在 RCTW1400 36 图净 +2/219 行但 Linux CPU 平均多约 955ms/图；不要重做旧实验，也不要因纯静态代码检查就宣布真实图像框/朗读已修复。历史结论详见实验分支 `test/craft768-int8-roi-dense-text-v2-20261103` 的 `docs/craft768-int8-conditional-local-roi-v1-v2-results-20261103.md`。

**审查使用的源码链接（可复核）：**
- https://github.com/vobichngan285-collab/kanzi-assistant/blob/feature/strict-offline-ocr-order-guard/app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt
- https://github.com/vobichngan285-collab/kanzi-assistant/blob/feature/strict-offline-ocr-order-guard/app/src/main/java/com/vobichngan/kanzi/OcrOverlayView.java
- https://github.com/vobichngan285-collab/kanzi-assistant/blob/feature/strict-offline-ocr-order-guard/app/src/main/java/com/vobichngan/kanzi/TextFlowRebuilder.java
- https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr/postprocess/DBPostProcessor.kt


#### 2026-11-03 — 单行白框隔离开发：纯 Java 几何模块 148 项断言全部通过

- **用户明确批准：** 按上一轮源码审查建议开始隔离式“单行白框分组”模块与纯 Java 测试，不接入正式 App、不生成 APK、不重做现有 CRAFT 压力实验。
- **原仓库地址：** https://github.com/vobichngan285-collab/kanzi-assistant
- **代码基线：** `feature/strict-offline-ocr-order-guard` 的 `c50855877c2d22a52333585e433528830a498e13`。
- **新隔离分支：** `test/line-overlay-plan-isolated-20261103`，验收 HEAD **`9f861680b0be514b2fa6dd92e1088cbe6a672950`**。此分支不覆盖原正式开发分支。
- **提交内容：** 新增 `LineOverlayPlanner.java`（单行候选框 + 原始框 ID/文字的可追溯行结构）；只把 `TextFlowRebuilder.java` 的两个现有行几何判断方法改成 package-private 供同包复用，**没有改函数体**；新增 `LineOverlayPlannerSmokeTest.java` 与单独纯 Java CI；新增实验记录 `docs/line-overlay-isolated-pure-java-results-20261103.md`。
- **真实 GitHub Actions：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41605234334 ，`pure-java-line-overlay` job **success**；取得日志 `LINE_OVERLAY_PLANNER_SMOKE_TEST=PASS assertions=148`。这些是 148 个 Java 断言，**不是** 148 张真实照片。
- **代码提交差异检查：** 对原基线比较后，仅添加两个 Java 文件、一份独立测试 workflow，修改 `TextFlowRebuilder.java` 中两行方法可见性；正式 `MainActivity`、`PaddleOcrEngine`、`OcrOverlayView`、`TTS`、模型及 APK 构建工作流 **完全未修改**。
- **已通过的测试类别：** 乱序碎框同一行合并、紧邻上下两行隔离、远离标签不误合并、重叠框保守隔离、轻微倾斜行角点全覆盖、竖排/空文本/非法坐标/空引用降级、每个原始 OCR 框恰好保留一次、原始四点坐标不被修改、旧朗读文本算法输出不变。
- **真实限制：** 新模块仅在实验分支中存在，尚未进入正式预览绘制、尚未关联 TTS 行高亮；它不能凭空补回完全漏检的字，也不能将 DBNet 已经粘成一个大框的多行自动分开。**没有摄像头实拍 GT 对照，更没有安卓设备或 APK 验收**。
- **完整实验报告：** https://github.com/vobichngan285-collab/kanzi-assistant/blob/test/line-overlay-plan-isolated-20261103/docs/line-overlay-isolated-pure-java-results-20261103.md
- **推荐下一步（待用户下一条单步指令）：** 针对固定真实照片/人工标注单行框开展“原始四点框 vs 新候选行框”的影子模式几何对照，量化碎片合并收益与跨行误合并风险；优先复用已有样本与标注、只做评估，**先不接正式预览、不动识别和 TTS**。

