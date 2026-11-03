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



#### 2026-11-03 — 真实公开照片单行白框影子对照：实验代码已提交，结果待核查

- **用户授权：** 现在执行单行白框真实照片对照，并要求每次重要变化都写入原仓库长期交接文件。当前未识别出另外约定的“备份文件夹”准确路径，继续以既定唯一固定位置 main:PROJECT_HANDOFF.md 作为持续备份，保留 Git 历史。
- **工作分支：** test/line-overlay-plan-isolated-20261103；本次新提交 **29bcedd075a731d86a2a55a9d5b85c3f0e36eefe**，以前已通过的 148 项 Java 断言的独立实验结果和已有代码未覆盖。
- **本次新增内容：** scripts/LineOverlayRealPhotoReplay.java、scripts/line_overlay_realphoto_shadow.py、.github/workflows/line-overlay-realphoto-shadow.yml。只新增影子几何对照的 Java 桥接程序、公开图片评测脚本和**非 Android** 专项 CI；没有修改 LineOverlayPlanner、主 App、PaddleOCR/TTS、模型和 APK 签名流程。
- **预先固定样本：** 公开 Yesianrohn/OCR-Data RCTW 数据集索引 1400–1411 的最多 12 张自然场景照片，沿用已验证的原 PP-OCRv6 Small DBNet+Small REC 推理模型、SHA256 与预处理，**不是新的盲测样本**，仅为新的“原始框与逐行候选框”几何对照重新提取必要的源框坐标。
- **对照设计：** 对同一图像先计算模型原始四点框及识别非空标志，再调用未经修改的 LineOverlayPlanner；按每个来源框 ID 保证一一归属，输出无文字的坐标证据；以公开 GT 的 AABB IoU≥0.40、一个 GT 区域被多个源框拆分的合并情况、跨 GT 区域误合并与原框角点越界为**几何代理指标**。GT 可能不是严格的单行标注，不能把该代理当作白框最终准确率。
- **关键限制：** 当前阶段只确认实验代码推送完成，**尚未核实该次新 CI 是否完成及真实数值**；禁止把此条记录表述为测试通过。仅 Linux CPU 的 Python OpenCV DBNet 近似处理，不等于 Android SDK 原生实现、USB 实时画面或 TTS 全链路。
- **实验输出计划：** 只上传汇总指标、逐图指标、无 OCR 文本的源框/GT/候选框坐标和日志；不上传相片、模型文件或 APK。CI 成功/失败及真实数据将在后续追加记录，不覆盖本条。


#### 2026-11-03 — 真实照片单行白框影子对照结果：已完成，尚不具备正式集成依据

- **完成的专项 GitHub Actions：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41607539013 ，Job realphoto-shadow-only 全部 success；没有触发 Android APK 构建、CRAFT 推理、真机负载或 TTS 改动。
- **原实验代码提交：** 29bcedd075a731d86a2a55a9d5b85c3f0e36eefe；**追加正式报告提交：** a9a6c70f46fc5648f509202589d4956a92b4ac06，均位于原有 test/line-overlay-plan-isolated-20261103 分支。对基线 9f861680b0be514b2fa6dd92e1088cbe6a672950 的 diff：仅新增 3 个只用于评测的文件及此实验报告；正式 App、模型及原 LineOverlayPlanner 未改。
- **固定照片与方法：** 复用公开 RCTW 1400–1411 的 12 张真实照片及 GT，不是新盲测集；沿用旧 PP-OCRv6 Small DET/REC 权重与配置，原框与候选框使用同一批模型预测，Java LineOverlayPlanner 不改源码。GT 仅用于评估；Linux Python/OpenCV 检测后处理属于 SDK 近似。
- **本次真实数值：** 12 张照片，71 个 GT 文本实例（并非严格单行 GT），52 个原始 DBNet 框，其中 50 个识别非空；新规划后 51 个候选显示框，仅实际合并 **1 组**；一对一 AABB IoU≥0.4 匹配由原框 **36** 到候选框 **34**；“一个 GT 对应多个源框”的代理统计 0；与两个 GT 实例关联的合并组 1；合并后来源角点越界 >2px 的点 **0**。
- **唯一合并具体说明：** RCTW-1406 原始 ID [3,2] 被合并，两个框在近似同一水平行、但人工 GT 为左右相邻的两个独立实例。该图的一对一 GT 匹配 6→4 主要暴露了实例级匹配与整行白框任务的评价差异，**不能直接宣称跨上下两行误合并或实际视觉质量变差**；需要真实图片可视复核和严格人工单行 GT。
- **结论：** CI 运行成功不代表改善单行白框。现有样本仅一次合并，不能证明收益或安全性；**不将新模块合入正式预览，不生成 APK，不改 OCR/朗读**。下一步建议补充人工严格单行标注及真实图片可视对照，尤其同行碎框/邻行/密集字和竖排。
- **完整长期报告：** https://github.com/vobichngan285-collab/kanzi-assistant/blob/test/line-overlay-plan-isolated-20261103/docs/line-overlay-realphoto-shadow-results-20261103.md
- **短期原始匿名数据包：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41607539013/artifacts/13216669007 （预计 2026-11-13 到期；不能视作永久备份）。包括 per_photo.csv、summary.json、box_replay_no_ocr_text.json、run.log；不含图片/模型或识别文本。Artifact SHA256：cd9aa0a71adffb197c81d14966759c4b760216a549fcbf253e50981d537d44d9。
- **备份与历史：** 此条在 main:PROJECT_HANDOFF.md **追加**，前一条“实验代码提交、结果待核查”及更早所有 CRAFT / 148 断言实验均保留。没有确认除该 GitHub 交接文件之外的专用备份文件夹路径。


#### 2026-11-03 — 下一阶段：真实图片可视化审查，准备阶段

- **用户授权：** 继续原有真实图行框审查；每次重要变化必须及时追加到此文件并提交 GitHub。
- **复用而非重跑：** 沿用已核实的 Run 41607539013 的12张 RCTW 1400–1411坐标产物；先前 52 原框→51候选，仅一处合并（RCTW-1406），代理 GT 36→34，无法判定合并是否符合人眼单行要求。旧结果保持不变。
- **新增隔离审查辅助脚本：** `test/line-overlay-plan-isolated-20261103:scripts/line_overlay_visual_audit.py`；提交 `855f5dbb898128c539eac06319380b7ca31f0b26`。只准备从公开数据集取回原图并叠画 GT/原检测框/候选框，供可视检查；不执行 OCR 推理、不改 `LineOverlayPlanner`、不改正式 App、不做 APK。
- **当前状态：** 可视化任务尚未执行，不宣称已有人工严格单行 GT，也不把旧区域/实例标注冒充严格单行标注；待 CI 和逐图检查完成后继续追加真实结果与风险。


#### 2026-11-03 — 公开图单行白框可视化专项 CI 已提交（待运行结论）

- 原有隔离分支新增最小独立 CI：`.github/workflows/line-overlay-visual-audit.yml`，提交 `3233dd7f2c29c2cc4e67afa9b1052b4a2fd11dc2`；脚本基线提交 `855f5dbb898128c539eac06319380b7ca31f0b26`。
- CI 只读取已存在的 Actions Run 41607539013 几何 JSON，取回固定 12 张公开 RCTW 图片制作原框/GT/候选框叠加图与唯一合并处放大图；没有新的 CRAFT/DBNet/Small 推理、Android 编译、模型参数调整或 TTS 修改。
- 结果待实际查看 Actions 状态与渲染图片，不会把图像绘制成功冒充严格人工单行标注或算法效果提升。


#### 2026-11-03 — 真实照片原框/GT 几何二次复查（无图像像素，非人工严格行标注）

- 新增持久专项报告：`test/line-overlay-plan-isolated-20261103:docs/line-overlay-coordinate-triage-20261103.md`，提交 `abd47a479ffbca70e1234a89dedf2065c13840c2`。只读取之前成功 Run 41607539013 的匿名坐标，**不重跑 OCR**。
- 已有 12 图中仅 RCTW-1406 出现源框 [3,2] 合并；两框分别与不同 GT 实例 3/4 关联，但近乎同一水平位置；候选 AABB x260–425/y402.4–447.6，下缘与另一个 GT 5 (x369–456/y435–454) 有少量交叠（AABB IoU≈0.084）。实际是否跨行**必须看原图，不得定性**。
- 在 RCTW-1402/1403/1406/1409/1411 共有10个原 DBNet 框与至少两个 GT 实例重叠≥原框自身20%面积，属于需要像素检查的候选，可能同一行多标注，也可能检测粘连；不能凭此确定跨行错误。
- 严格行级人工 GT 仍缺，视觉 CI 工作流已提交但本条不宣称完成；没有修改几何算法、正式 App、TTS、模型或构建 APK。旧 GT 代理匹配 36→34 的历史记录保持不变。


#### 2026-11-03 — 可视叠图 CI 初次失败：公开数据集返回 HTTP 502（已核查运行日志）

- 用户允许继续公开真图可视检查。已实际查询到独立工作流 Run **41608904979**：`public-photo-visual-review` = **failure**；链接：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41608904979 。
- 分步日志：复用先前 Run 41607539013 几何 Artifact 和 Python 脚本编译均通过；失败发生在请求 `https://datasets-server.huggingface.co/rows`（RCTW offset=1400, length=12）时：`requests.exceptions.HTTPError: 502 Server Error: Bad Gateway`；**没有生成或验收任何原图叠画图片**。
- 该错误属于公开数据源读取失败，**不证明模型或单行框算法好坏**。失败流程没有 OCR 推理、Android APK、TTS；后续只对图像获取方法做有限重试，不能改变既有盒子、标注与研究范围。


#### 2026-11-03 — 可视图来源 HTTP 502 有限重试修正（未修改 OCR）

- 既有可视化专项 Run **41608904979** 在公开元数据请求 RCTW1400..1411 处被 HTTP 502 中断，已真实读取 job 日志；旧几何 Artifact 下载步骤成功，**未生成可视叠图**。
- 只修改隔离分支 `test/line-overlay-plan-isolated-20261103:scripts/line_overlay_visual_audit.py` 的**公开图片读取**：对网络 502/429 等 `RequestException` 增加最多4次有限退避重试、12行元数据失败时按固定 `4+4+4` 分批请求；仍校验原来的12个 row_idx。新代码提交 `81599b8eb90c0a8bb3e6b420835f96530a6ad698`，远端 blob 已回读匹配。
- 此项**绝不**改变 CRAFT、DBNet、Small、`LineOverlayPlanner`、读取的旧检测框、GT、评估指标、正式 App、TTS 或 APK；新一次工作流状态与图片像素仍须单独验收，不能宣称成功。


#### 2026-11-03 — 真图白框可视检查完成：12张原图与唯一合并的覆盖冲突（已验收并保存）

- **项目与研究分支保持不变**：`test/line-overlay-plan-isolated-20261103`。首个可视工作流 Run **41608904979** 因 Hugging Face RCTW 公开元数据 HTTP 502 失败；只修改取图有限重试（`81599b8eb90c0a8bb3e6b420835f96530a6ad698`），随后 Run **41610542427** 的 `public-photo-visual-review` **success**，Job 全部步骤成功，日志出现12条 `VISUAL_SOURCE` 与 `VISUAL_AUDIT_RENDERED 12 photos`。成功日志与具体产物：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41610542427 。
- 产物 Artifact **13219151561**：12张公开场景叠画图 + RCTW-1406唯一合并的局部放大图 + manifest；ZIP SHA256 `fb6e665787aa87650cc4d1db301783018b780f776fd53ef6874c0bf7f5763507`，约2026-11-13到期，不可当永久图片备份。所有照片像素已逐张查看；无新的 OCR 推理、无 APK、无模型或 TTS 变更。
- **新确认风险**：旧匿名原框 RCTW-1406 的绿色候选由 R3+R2 合并（仅有的一次），但其合并多边形与**未参与合并**的原框 R0/R1 相交面积分别 **740.93 / 96.00 px²**，覆盖后者各自面积 **37.05% / 15.79%**；用原四点与 OpenCV `intersectConvexConvex` 计算。存在候选白框视觉重叠风险，**不等于已证实跨行错误**。
- **目视例子**：RCTW-1402 的同一水平带两个 GT 字区域已经由一个原 DBNet 框覆盖；RCTW-1403 下方LED字带也是一行多个 GT 实例，表明“一个框跨多个 GT”不能直接推断跨行粘连。1406有三列竖排文字和底部近邻横向文字；1409是斜店招，1411为弧形井盖文字，均提示保守处理。旧12图 52→51、71 GT、IoU代理36→34保持不变。
- **永久完整报告**：`test/line-overlay-plan-isolated-20261103:docs/line-overlay-realphoto-visual-review-20261103.md`，提交 `c2747e70481a51aa8b763320cd0811fdc9bdc0f8`（已回读确认）。原先实验、文档和旧结果**未删除**。
- **决策**：暂不更改 `LineOverlayPlanner` 算法阈值、不接入正式预览、不改 Small/DBNet/TTS、不出 APK。下一步可经用户指令在隔离测试中增加“合并候选与未纳入源框显著重叠”审计反例，并采集明确人工单行 GT 后再讨论保护逻辑，避免针对一个样本过拟合。


#### 2026-11-03 — OCR 错误归因去重检查：发现已完成的72图人工框 Oracle 与检测归因实验

- **用户强调先查旧实验，避免任何重复工作。** 已在原仓库历史分支找到 `test/ocr-final-error-attribution-20261103` 的 `docs/ocr-final-error-attribution-results-20261103.md` 和 `scripts/ocr_final_error_attribution.py`，以及 `small_rec_gtbox_oracle_72.py`（同仓库实验代码）。对应 Actions **41566050026**（success）、**41564703130**（success）。
- 旧实验共72张公开真实场景照片，DEV36（217 GT 区域）、HOLDOUT36（244 GT 区域）。HOLDOUT 自动检测框 IoU≥0.4 成功匹配122/244；在这122区域上 Small REC 的 CER **16.087%**（111 编辑距离 / 690 GT 字符），其中89行完全正确。Oracle 在全部244 GT 人工框上 CER **9.734%**（117/1202），不是同一分母，**禁止相减宣称裁剪造成6.35个百分点的错误**。
- HOLDOUT 122 个未匹配区域中 80 个与检测框有交集、42 个没有任何 AABB 交集；该分类只是几何代理，不等同逐行实际读错原因。
- **本轮新的、尚未完成的控制变量问题：** 同一 HOLDOUT 的 **恰好122个已 IoU 配对的 GT 区域**，在不改变 Small REC、预处理、照片与文字真值的条件下，用其 **GT polygon 裁剪**与**已存储的旧自动检测裁剪 REC**逐区域配对比较，避免旧全量 Oracle 与匹配子集样本分母不一致。原来的数据包 Artifact **13199760778** 仍有效、CSV 中有旧每区域 REC 编辑距离；只需对这122个 GT 裁剪做 Small REC，不重跑 DBNet/CRAFT/72图整体检测归因。
- 本节点为**历史实验核实与后续设计**，没有声称新模型实验成功。仍不动正式 App、TTS、检测器或 APK。


#### 2026-11-03 — 同样122个文字区域的 Small 自动框 vs 人工 GT 框配对识别：实验代码已提交，待 CI 核实

- 先核查旧实验：Run **41566050026** 和 **41564703130** 均 success；旧全量 Oracle HOLDOUT244 行的 CER9.734% 与旧自动检测匹配122行的 CER16.087% 分母不同，旧报告禁止直接相减。本轮新增的**唯一问题**是按相同122个已配对 GT 区域逐个计算裁剪影响，不重复旧72图检测实验、CRAFT性能实验或旧全量 Oracle。
- 旧 Run41566050026 Artifact **13199760778** 的 CSV 已实际读取、验证：HOLDOUT 244条 GT，其中122条 `matched_iou_040=1`，合计690正规化字符，旧自动裁剪 Small REC 编辑距离111，完全正确89行；这些**是旧实验数据，不是本次新发现的算法效果**。
- 新隔离脚本 `test/line-overlay-plan-isolated-20261103:scripts/paired_small_crop_attribution_holdout.py`，提交 `1addfcf5f14d3663e4cac400828433b152078a42`，使用旧 CSV 原值，只对同122条人工多边形裁剪做 Small REC；强制旧图像索引、GT ID/文本、归一化字符数/历史编辑距离完全一致，不下载或运行任何 DBNet/CRAFT DET。
- 新单项 Actions 工作流 `.github/workflows/paired-small-crop-attribution.yml`，提交 `856a2ae622e9fa3fc531f1e024446fb0208341ce`，使用旧成功研究 Commit `1e0c227f33fb0fbef4c681024972d107b09bfc99` 的固定原始 `sdk.crop_box`/`sdk.rec`、相同 SHA-pinned Small REC，输出逐配对匿名编辑距离和同分母 CER、四类精确/错误结果，不保存图片/原文字/模型/APK。
- **状态必须区分**：代码和 CI 文件提交、远端回读成功；新实验运行结果和每个识别输出**尚未验收**，暂不宣称改善比例。只操作旧仓库原隔离分支；正式 App、模型、TTS、APK 保持不变。


#### 2026-11-03 — 同一122个区域的自动框/人工框 Small REC 配对实验成功；框裁剪确有明显影响

- **去重前提：** 之前已经完成的 72图检测归因 Run 41566050026 及全量人工框 Oracle Run 41564703130 均未重跑。本次唯一新的推理是在同一 HOLDOUT36张照片里，对原本**已经成功匹配**的122个 GT 区域补做**人工 polygon 裁剪 Small REC**，把它们与旧自动框裁剪原始记录逐区域配对。
- **成功 Actions**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41612404136 ，`paired-crop-small-rec-only` = **success**；Artifact 13218859906（ZIP SHA256 `51449fc6e489b3672df0555edf9fc068b9f2a1b045564df9f0952c9b238126e0`，预计2026-11-13到期）；远端日志显示36张固定HOLDOUT、244个 GT、122个配对，已下载 ZIP 并用每区域 CSV 独立重算核心结果。
- **新证据（**同一122个区域、相同690个字符**）：**既有自动框裁剪 Small REC 编辑距离 **111**、CER **16.087%**、精确89区域；此次**人工正确框裁剪**编辑距离 **40**、CER **5.797%**、精确99区域。净减少71个字符编辑错误，配对 CER 差距 **10.290个百分点**；18个区域人工框错误数更少，3个区域自动框更少，其余101个持平；11个原自动框错、人工框完全正确，1个相反，22个两种都错误。
- **关键限制：** 这**不是**正式手机 OCR准确率提升10个百分点；只有已IoU配对的122/244个区域，而且是同一公开照片 Linux Python/ONNX 的控制变量结果，并不证明真实 Android SDK RGB/BGR、裁剪和预览路径等价。其余122未匹配的 GT 区域仍有漏检/粘连/匹配问题。人工 GT 只能作为实验 Oracle，不能直接上线使用。
- **永久研究报告**：`test/line-overlay-plan-isolated-20261103:docs/paired-small-crop-attribution-holdout-20261103.md`，提交 `dbf6c76a5ac63374464ed4b8ff3337e03410475f`；实验脚本提交 `1addfcf5f14d3663e4cac400828433b152078a42`，工作流提交 `856a2ae622e9fa3fc531f1e024446fb0208341ce`。均属于原仓库隔离分支，不改正式代码、模型或 APK，旧记录保留。
- **下一步应避免重复试验：** 优先核对 Android 真正从 NV21/Bitmap 到 SDK 四点裁剪与 Small REC 的颜色、旋转、归一化及张量输入能否与固定参考对齐；必要时按用户批准做安全的影子诊断，而不是继续广泛重跑同一72图、尝试未经证实的检测阈值修改。


#### 2026-11-03 — Android 原主线图像预处理只读核查（配对实验后的问题收敛）

- 追加到配对实验永久报告的 Android 静态审查，更新 Commit `c1d7373805708284079d675bf41e52df7e8d83d1`，文件仍为 `test/line-overlay-plan-isolated-20261103:docs/paired-small-crop-attribution-holdout-20261103.md`，仅文档更新，未触发新实验或 APK。
- `feature/strict-offline-ocr-order-guard:MainActivity.java:303–311` 原实时画面传 `legacyDecode=false`；`PaddleOcrEngine.kt` 因此走 OpenCV `COLOR_YUV2RGBA_NV21`，而非早期兼容的 JPEG quality82 二次压缩路径。**不能未经核实把手机偏差归因于JPEG质量82。**
- 原构建 `.github/workflows/android.yml:125–160` 明确为识别器 `img_mode:BGR` 在构建时修补 SDK RecPreprocessor，保留 BGR；不能在无真机等价证据下盲目改 RGB/BGR。**构建补丁存在，不代表已经逐字节验证实际手机 REC 张量。**
- 有条件增强路径：`enhancedOcr || (brightness<47 && edgeStrength<7)`，以及非快速模式低置信条件下的额外增强尝试；来源 `MainActivity.java`、`FrameQuality.java` 和 `PaddleOcrEngine.kt`。要核实手机画面及 SDK crop/REC tensor，**先做明确范围的安全单帧诊断**，不得擅自上传用户图像。
- 维持刚验收的配对结果：同122区域自动框 CER16.087% → GT框5.797%，**不是手机准确率提升声明**。正式 App、模型、TTS、APK及原有历史记录均未改变。


#### 2026-11-03 — 配对实验报告校对（仅文档）

- 修正永久研究报告中的剩余样本敏感性分析文字笔误：排除一条52字符的 RCTW-508/GT#1 后剩121区域、638字符，旧自动框85编辑错误、GT框37编辑错误，差距48/638=7.52个百分点；与已核实的数值一致，**没有新实验推理或指标更改**。
- 隔离分支文档校对提交：`eee1e1de7701c67e956c205aeb10cf9ec02e9e31`。保留全部旧实验记录。


#### 2026-11-03 — Android SDK 图像/裁剪链路专项：去重后确立真正缺口（开始前检查）

- 用户允许继续诊断，并特别要求**每次先去重旧实验、变更后追加备份**。核查原仓库 HEAD：`main=0195250777cb481f8715d78b63cc4be06ba53b23`；`feature/strict-offline-ocr-order-guard=c50855877c2d22a52333585e433528830a498e13`；`test/line-overlay-plan-isolated-20261103=eee1e1de7701c67e956c205aeb10cf9ec02e9e31`。
- 已有历史 `android-bgr-model-recheck.yml`、`verify_android_bgr_model_comparison.py` 是**公开图片 BGR/RGB ONNX 模型结果对比**，此前还有72图检测归因和122区域自动框/GT框配对，**不得重复当作新实验**。
- 深入读取正式构建指定上游 **PaddleOCR v3.7.0** 的实际 `OCREngine.kt`、`QuadTextCrop.kt`、`QuadGeometry.kt`、`RecPreprocessor.kt`、`BitmapUtils.kt`。识别链路源码为：Android Bitmap→`BitmapUtils.bitmapToBGRMat`→`QuadTextCrop.crop`（`minAreaRect` + perspective warp, 竖排旋转）→`RecPreprocessor.preprocessBatch`（48高、最长3200、`/127.5-1`、NCHW）。正式 `.github/workflows/android.yml` 构建时会将上游 `RecPreprocessor` 的 BGR→RGB 变换**删除**，使其输入 BGR 与 `inference.yml` 保持一致。
- **真正尚未验收的独立问题：** 在公开图像、同一多边形条件下执行**真实上游 SDK 的裁剪 + 构建补丁后的识别张量**，与前期 Python 参考 `crop_box + BGR` 做数值差异比对；这不需重新运行任何 DET/REC/CRAFT 模型，且不能冒称测试了 Android 真机 NV21→Bitmap。
- 本条仅为开始前的只读源码核查与任务界定。正式 App 不动，未构建 APK；后续代码/CI和数值验收逐阶段追加到本交接文件。


#### 2026-11-03 — Android SDK 真裁剪/张量宿主核对：最小 Kotlin 适配器已写入隔离分支

- `test/line-overlay-plan-isolated-20261103:scripts/AndroidSdkCropTensorProbe.kt` 新增，提交 `06eaa89dec966ef741642e42622ba2511edb7f80`；已从 GitHub 回读源码确认一致。用途是在**Linux Kotlin/JVM** 执行上游 v3.7.0 的 `QuadTextCrop.crop` 和构建期 BGR 补丁后 `RecPreprocessor.preprocessBatch`，用固定公开图片/四点框输出匿名裁剪与张量数值供 Python 比对；不下载模型、无 OCR 推理、无真实 Android Bitmap/手机、无 APK。
- **状态：只有适配器代码已提交，未编译、未执行、没有数值或准确率结果**；后续将追加独立测试驱动与工作流，再核查运行日志。正式 App/CRAFT/DBNet/REC 与历史实验未改。


#### 2026-11-03 — Android SDK 数值对照公开图驱动脚本已写入（未运行）

- 新增隔离脚本 `test/line-overlay-plan-isolated-20261103:scripts/android_sdk_crop_tensor_parity.py`，提交 `ebede3d55c43af874f9a24328ac0f9de163cc36f`，远端已回读一致。只选以前使用过的 RCTW-1406 与 RCTW-1409，至多各6个已标注四点框，准备**相同 BGR 像素输入**；参考使用旧成功研究的 Python `crop_box`；目标运行的是 PaddleOCR v3.7.0 原始 `QuadTextCrop` + 正式 Android CI BGR 补丁后的 `RecPreprocessor`。数值比较仅包含裁剪像素、张量形状、最大差/平均差，不下载/运行任何识别器或检测器。
- **仍属实验准备，尚未执行**：Linux/JVM 宿主测试不是安卓机的真实 NV21/Bitmap 或相机实拍测试；不上传任何用户私有照片。全部原像素、裁剪与张量必须在 CI 临时目录，产物仅匿名数值指标。


#### 2026-11-03 — Android SDK 真源码宿主裁剪 + 张量数值专项 CI 已启动（待验收）

- 在原隔离分支新增只读实验工作流 `.github/workflows/android-sdk-crop-tensor-parity.yml`，提交 `d7a988aaed6a33fcf36e5779889df28141d594e7`，回读核实。仅构建**Linux Kotlin/JVM 最小适配器**，从官方 v3.7.0 SDK 逐字取 `QuadTextCrop`、`QuadGeometry`、`OCRBox`、`RecPreprocessor`，并按正式安卓 workflow 的**同一 BGR 删除转换补丁**运行，比较以前研究的 Python 参考。固定两张公开旧图、共不超过12个 GT 框；不下载 ONNX，不运行 DET/REC/CRAFT，不构建 APK，不采集手机用户照片。
- 工作流尚未做成功/失败验收；需要先检查 GitHub Actions run / Job / artifact 与逐样本形状、像素/张量差数值。主项目算法和正式 APK 不变。


#### 2026-11-03 — 完成上游 PaddleOCR v3.7.0 裁剪/REC 张量**真实源码宿主**数值对照；未发现明显 Python/SDK 实现偏差

- **去重明确**：此前已有 Android BGR/RGB 模型对照、72张图错误归因和同122区域自动/人工框配对；本次**均未重跑**。本次唯一新实验是将真正的上游 Kotlin SDK `QuadTextCrop.crop` + 正式构建所用 BGR 源码补丁后的 `RecPreprocessor.preprocessBatch` 在 Linux JVM 执行，对原 Python 参考的裁剪像素和 NCHW 浮点张量做逐项数值比对，不使用任何 ONNX。
- **成功 GitHub Actions**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41614242483 ，`sdk-crop-and-bgr-tensor-reference` **success**，日志 12 个 `ACTUAL_SDK_CROP_TENSOR` + `ACTUAL_SDK_VS_PYTHON_PARITY_COMPLETE`；首次宿主 Gradle/Kotlin 编译 **success**。结果 Artifact **13220447622**（ZIP SHA256 `3e3b36c3fb58eae7fefb0381edfd15712d7ae6205bb1be5d031c57c2fd60d000`；预计2026-11-13 09:51UTC过期），已下载 ZIP 并逐个读取数值。
- **实际数值**：旧公开 RCTW-1406、RCTW-1409 两张照片，共12个 GT 多边形：裁剪尺寸一致 **12/12**，`[1,3,48,W]` 张量形状一致 **12/12**，其中9个样本裁剪像素和归一化张量**完全一致**；另3个像素最大只差**一个8-bit数值**，归一化张量最大绝对差 **0.00784314**，预设数值容差 **PASS**。这只是旧公开图有限宿主样本，不能声称手机实际 SDK NV21/Bitmap 测过，更不代表手机 OCR 字符准确率提升。
- **重要研究判断**：此前同122 GT区域比较的自动框 CER16.087% vs 人工框 CER5.797% 证据保持；新发现支持此前的**旧 Python 裁剪逻辑本身与正式 SDK 较接近**，不用因此重复全量检测/识别实验。当前尚未验证手机 NV21 YUV色度采样、对焦/运动/曝光、Bitmap/旋转、自动增强与运行版 SDK 二进制；不能盲目修改 RGB/BGR 或检测阈值。
- **永久报告**：`test/line-overlay-plan-isolated-20261103:docs/android-sdk-crop-tensor-parity-20261103.md`，提交 `bac195ff25f16c85d0b694a9641798ed84fc717c`（已远端回读确认）。适配器提交 `06eaa89dec966ef741642e42622ba2511edb7f80`、驱动 `ebede3d55c43af874f9a24328ac0f9de163cc36f`、CI `d7a988aaed6a33fcf36e5779889df28141d594e7`。只写原仓库隔离分支与此交接文件，旧结果全部保留，无正式 App 修改、无 APK。
- **下一步门槛**：如需继续，先在隔离状态设计**用户主动触发、离线、不上传私人图片**的手机单帧真实 NV21/Bitmap/SDK 四点裁剪/张量诊断，再决定是否进行实验 APK 的真机验证；不得将上述 Linux 宿主测试冒充安卓实测。


#### 2026-11-03 — 用户明确要求生成可安装手机单帧 OCR 诊断 APK（实现已隔离提交；暂未构建验收）

- **本次目标**：可在真实 USB UVC 或手机 Camera2 视频源中，由用户**手动**捕获单帧，使用现有已加载 PP-OCR 模型真实推理；保留原始 NV21、解码 PNG、OCR 实际输入 PNG、SDK 原结果四点框/置信度、SDK 真实裁剪 PNG、后处理前 REC NCHW Float32 小端张量及 metadata.json；用户通过 Android SAF `ACTION_CREATE_DOCUMENT` 自行选择导出位置。默认只保存应用私有文件，不自动上传。应用限定最多导出24个框，原始框总数仍写元信息。
- **独立分支**：`test/phone-ocr-single-frame-diagnostic-20261103`，从已验证正式开发基线 `feature/strict-offline-ocr-order-guard@c50855877c2d22a52333585e433528830a498e13` 创建，**不覆盖正式 app**；原正式应用 ID `com.vobich.kanzi` 不变，诊断 debug APK 为 `com.vobich.kanzi.framelab`（通过 debug applicationIdSuffix），单独可共存。
- **已提交源码**：`SingleFrameEvidence.kt` `fe78d813f755d12e6576e4e273f0ac2aeb790cfe`；`PaddleOcrEngine.kt` `1101a95ab8cf05bac64aed5ec6eabc93f9c33248`；`MainActivity.java` `cc8be54400a538313aac4ee57bd307d70a8ddde7`；`app/build.gradle` `41d3c19a11e676e27c8498e0e3fe95707e795394`；`AndroidManifest.xml` `b1111f6da011b1b2325a9080c70a80f69cc82cd7`。已逐一从 GitHub 回读确认。诊断 APK Manifest **移除 INTERNET 权限**，防止此包通过网络上传摄像头文字；某些原来依赖网络下载的语音资源功能在此实验包不可用。
- **范围与风险**：现有诊断按钮只显示文本，本次新增两键“手动采集一帧”“导出诊断 ZIP”与 SDK `QuadTextCrop`/`RecPreprocessor` 真实裁剪/张量调用；仅用户显式采集时执行，不改变正式自动识别或 TTS。ZIP 包含真实拍摄图像及可能的敏感文字，**只有用户自己有意识选择外部导出**。默认本地文件下次重新采集覆盖旧文件。
- **阶段状态**：代码已经提交，**尚未编译、未安装真机、未验证 APK 行为**；后续独立 Actions 构建成功与安全审计必须分开记录，不能预先声称已可用。


#### 2026-11-03 — 单帧手机诊断 APK 独立 Actions 构建已发起

- 独立 Debug 工作流 `test/phone-ocr-single-frame-diagnostic-20261103:.github/workflows/phone-ocr-single-frame-diagnostic.yml`，初次提交 `78afc28754017e9bfb74193e8becb02c8e1c83d9`，随后为纳入已提交的安装/隐私说明文件 `docs/phone-single-frame-diagnostic-20261103.md` 更新为 `3d9a6ad02eb953d5ed6c2c57cd5c3e024eecb5bc`。
- GitHub 已确认开始独立构建 Run `41617248713`（当前提交）；更早的 Run `41616956581` 来自说明文件加入之前的快照，不用于最后分发。新说明文档提交 `cc9f23fd65cc72d86738cefbb1a3a439c32cce15`。
- 工作流使用已验证原正式开发分支的 PaddleOCR v3.7.0、Small/Tiny/v5 模型及 USB/手机摄像头支持，仅以 `:app:assembleDebug` 构建独立包名 `com.vobich.kanzi.framelab`；不读取原正式签名密钥、不生成 Release、不创建 GitHub Release。
- 安全验收门槛：APK 必须真实存在、`aapt` 包名单独、Manifest 无 `INTERNET`、ZIP 内 Small DET/REC SHA 固定、带有 arm64 ONNX Runtime，且上传到 GitHub Actions Artifact 后才可称可下载。此条仅记录**构建已发起，尚未完成**；手机实际安装/USB 实拍仍待用户本人测试。


#### 2026-11-03 — 已完成并下载：独立手机 OCR 单帧诊断 APK（交付锚点）

- **本阶段已成功构建并验收 Debug APK（非正式版更新）**：GitHub Actions Run **41617248713** / Job `build` success；实际 `BUILD SUCCESSFUL` + `PHONE_SINGLE_FRAME_DIAG_APK_AUDIT_PASS`，Run：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41617248713 ，Artifact：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41617248713/artifacts/13222475520 （Actions ZIP临时保留约10天）。
- **安装包**：`Kanzi_OCR_SingleFrame_Diagnostic_debug.apk`，**390367103 字节**，SHA256 **`4b11d1deac9c41cf61bd474dd7ad84e07133db2cd9dd0bff51e1249b85d6adfd`**；已实际下载 GitHub ZIP、解包、重新计算哈希并与 Actions SHA256SUMS 一致。Artifact ZIP SHA256 `d36481d327f51d583cc2becc8df8cda5312ea000d899e696d220a7e9a8666eb3`。
- **应用分离与安全**：`com.vobich.kanzi.framelab`，应用名称“看字助手 · 单帧OCR诊断”，不覆盖原 `com.vobich.kanzi`；最终 APK Manifest 检查**没有 `android.permission.INTERNET`**，模型真实 SHA 匹配 Small DET `d73e0058...` + Small REC `5435fd74...`，含 arm64 原生 ONNX Runtime。保留 USB UVC/手机摄像头识别源码，独立 debug 签名而非正式签名。
- **用户手动调用**：主界面 → 设置 → OCR诊断 → “手动采集一帧：图像＋真实裁剪＋输入张量（仅本机）” → “导出上次单帧诊断 ZIP（手动选择位置）”。内部私有保存 `frame.nv21`、`decoded-frame.png`、`actual-ocr-input.png`、`metadata.json`、SDK `crops/*.png` + `tensors/*.float32le`（最多前24框），不自动上传；ZIP 含可能的用户私人文字。
- **永久安装/使用文档**：`test/phone-ocr-single-frame-diagnostic-20261103:docs/phone-single-frame-diagnostic-20261103.md`，成功构建后报告补充 Commit **`ece7eeec6af6818843279cdb0c4f9ce60f92199b`**（已回读）；工作流成功构建代码 Commit **`3d9a6ad02eb953d5ed6c2c57cd5c3e024eecb5bc`**。只改独立诊断分支与主交接记录，**正式 App、真实朗读/白框逻辑未更新**。
- **注意验收边界**：已完成的是 GitHub CI/安装包静态与编译审核，**尚无用户手机上的 USB 连接、App安装、真实帧保存和 ZIP 文件完整性的验证**。下一步用户用非隐私文字手动采集，在手机上对比截帧/裁剪并自选导出，必要时发回 ZIP供排查。
- 早先 Run **41616956581** 虽实际编译出同一 APK 并通过核心审核，但在拷贝晚于其快照提交的说明文档时失败；它不是交付 Run。已修正并重新构建，最终 Run 41617248713 完整 success。


#### 2026-11-03 — 首份用户真实手机单帧 ZIP 检查：确定跨横行的纵框裁剪是关键问题

- **用户已实测安装和导出诊断工具**并主动上传 `kanzi-ocr-single-frame.zip`；实验 ZIP 完整性通过（52文件、SHA256 `01f6f9e35ca54384ad8dbf5d5a64f6bd1d5c5fae92859ba5b95d56107c1afa9e`），真实手机 NV21帧1080×1920、PP-OCRv6 Small、增强关闭。SDK 返回**最终 OCRResult 36项**（非原始全部 DBNet 提议框），诊断工具仅导出前24项；DET831ms、REC780ms、DET input [1,3,1600,896]。**未上传任何用户拍摄图片、NV21、裁剪、张量或识别文本到 GitHub**。
- **首次真机逐像素/张量独立核对**：`decoded-frame.png` 和 `actual-ocr-input.png` **字节完全一致**；以原 `frame.nv21` 重建 RGB 与图 PNG 比较，全像素通道差最大1个8-bit值（均值0.495）；将24张真实裁剪 PNG 用 Android SDK 相同规则 BGR→48高缩放→NCHW→/127.5−1，逐元素对比24份导出的 Float32 REC tensor，**24/24完全一致，最大绝对差0**。没有再跑任何 ONNX，不能称手机 OCR 准确率提升。
- **新重大错误归因**：原画面中部分项目为从左到右的**横排字符但间距较大**；SDK返回框索引 **15/16/17** 却形成穿越多条水平行的**竖长四点框**，源长短边比 **4.05 / 3.34 / 4.28**。SDK将其按竖排自动旋转成横向 REC 图片，造成不同横行字拼接。三框识别置信度 **0.999893 / 0.999784 / 0.999600**，因此高 confidence 不能保证行归属正确。另有19/21/22号孤立碎字，说明该区域检测布局碎裂。此案例明确指向**检测方向和跨行原框裁剪**，而非本次 NV21 图像转换/BGR/REC tensor 明显异常。
- **永久匿名技术报告**：`test/phone-ocr-single-frame-diagnostic-20261103:docs/first-real-phone-frame-evidence-20261103.md`，Commit **`c5d8c16aae41edb88aba21f8830f0a2a44cede9c`**，已远端回读一致。文件只有技术数值和框号，不含原用户私人图片/识别文字，过去的全部实验与原 APP 版本保留。
- **下一步不重复**：可在隔离测试设计轻量“当前场景以水平行排列、竖长框跨多个行基线”的**只读警报与行归属验证**，额外对真正竖排的文字测试避免误判；若需修复必须实际验证裁剪后的原文保真/实时性，再接入正式版。**现在正式 App 代码、DBNet/Small模型、TTS、APK均未被改动。**


#### 2026-11-03 — 继续推进用户真机跨横行竖框：纯Java影子风险审计完成，发现真竖排反例，尚不安全自动纠错

- **先去重旧实验**：沿用用户已上传的 `kanzi-ocr-single-frame.zip` 前24/36 SDK OCRResult 的几何坐标和已保存的12张公开 RCTW 照片52个检测框；不重做72图 DET、122区域 CER、CRAFT、SDK张量对照、不跑额外 Small REC。私人原图/NV21/框原始坐标/中文识别文字/实际裁剪/张量**未上传至 GitHub**。
- **新增隔离纯Java风险审计器**：`test/phone-ocr-single-frame-diagnostic-20261103:app/src/main/java/com/vobichngan/kanzi/CrossRowOrientationAudit.java`，Commit `e56d784c3774bdf46e23c73ab2d45f5887a149c3`；输入现成四点/置信度/帧尺寸，需至少3个彼此邻近且高度重叠的竖长框、右侧或左侧另有3条独立对齐水平锚点才报告**SUSPECT风险**；不读取/改动 OCR 文字，不改模型、读屏、白框、TTS，超512框安全弃权。**只是在实验分支新增类，尚未接进 App 回调**。
- **真实手机结果**：同一用户真实1080×1920诊断帧里，前24框的15、16、17号竖框同时与右侧18、20、23号横排价格行对齐，在匿名数值回放中 **15/16/17 全部标记为 `SUSPECT_ONLY_NO_TEXT_MUTATION`**；旧12张 RCTW共52框**0个风险标记**，但旧数据框稀疏，不能声称0真实误报率。
- **本地视觉反事实**：对比原 SDK 已旋转的3个纵向 REC crop 与根据三个价格行 y 中心 668/710/753 生成的3条短横向 crop；后三者肉眼呈现横向同一行字符，比把纵向不同横行字符接成错误字符串更合理。但此处**没有跑新 Small REC、没有逐字真值，不能声称 CER 提高或已修复**。本地研究图片仅存在此次用户对话容器中，没有上传仓库或 Actions。
- **严谨的反例**：合成的真竖排3列，如果旁边也有三条 y 对齐的横排价格，同样触发3个风险提示；所以几何上下文**不足以安全自动修改文字**。真竖排但没有行锚点的对照 0 标记；还有锚点不足、竖框不足、NaN/超量输入安全回退。合成 Java **19项断言**。
- **专项 GitHub Actions success**：Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41637797237 ，日志 `CROSS_ROW_ORIENTATION_SHADOW_TEST=PASS assertions=19`；工作流提交 `24bffcd3a325018ea830f351082d40a105a09747`。前 Run `41637703181` 断言虽PASS但工作流最后一行 shell echo 英文撇号未转义导致 Job fail；只修 shell 引号再验证最终绿色。对比旧真机诊断报告 commit `c5d8c16...` 到该工作流，只有3个**新文件**，无正式源码旧行变动、不产 APK。
- **永久实验报告**：`test/phone-ocr-single-frame-diagnostic-20261103:docs/cross-row-orientation-shadow-audit-20261103.md`，Commit **`030bb52eb0526552be3527669db292dce60150ad`**，远端已回读一致，只有匿名数值、测试局限，不含任何原始私人图像/实际字句。
- **下一步必须验证**：真实“竖排文字+右侧水平标签/价格”的反例和同一表格多角度复拍，建立人工行级真值；通过非破坏性 A/B 才能试局部分行裁剪后 Small REC。**现在禁止自动丢弃、旋转或改写识别出的文字**，正式看字助手、原有单帧诊断APK保持不变。


#### 2026-11-03 — 首次真正运行同一 PP-OCRv6 Small REC，手机错误竖框经横向局部重裁剪得到4/4整行正确

- **去重要求满足**：基于已有的同一私人手机单帧 ZIP，旧 72图/122区 CER、12图宿主张量/旧跨行几何风险全部保持不重跑；**本次新完成的关键环节是实际运行固定 Small REC 模型的新横向裁剪 A/B**，不是只画候选白框。
- **隐私安全**：用户 `kanzi-ocr-single-frame.zip`、原始图片、NV21、裁剪 JPG/PNG、实际中文识别字符串、Float32 张量及带明文的本地结果只留在本会话私有容器 `/mnt/data/private_phone_rec_ab`；**绝不提交到 GitHub、Actions 或公网**。云端 CI `41640124305` 只打包**公开**PP-OCRv6 Small REC官方ONNX、YML与 Linux CPython3.13 onnxruntime wheel，不含任何用户输入，成功 Artifact **13231814688**，大小约34.5 MB，可从 Actions下载；工具链在本机通过逐文件SHA检验，REC权重 SHA `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`，YAML SHA `ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1`。
- **A/B基线识别同机匹配核实**：从用户旧ZIP直接加载已有6条`tensors/*.float32le`（包含15/16/17三条错误竖向框以及19/21/22独立碎字框），Linux ONNXRuntime 1.21.1 CPU运行**同一权重、BGR-48高-NCHW和官方CTC字典**，得到的6条中文预测与安卓诊断`metadata.json`**6/6完全相同**，模型环境/输入复现已可靠。没有重新跑整幅图DET或全36框REC。
- **第一步：人工确定横向裁剪**：从原照片目视核对4行中文服务项目（每行4字，总16字，人工GT仅由模型研究者目视转录，**用户尚未正式确认**），原来15/16/17号纵框包含跨行字符；本地将该区域裁成4条横向条带，再实际送入同一Small：**4/4整行逐字正确，16/16字符精确匹配，对人工目视GT的局部编辑数0**。另用窄36px与宽50px裁剪进行几何扰动，每类4/4全正确；4×4独立单字REC 4/4行全正确但多次调用成本更高，不建议手机默认单字重复识别。扰动均是**同一张图的重复方案，不是新的独立样本**。
- **第二步：原几何风险自动推出4个候选**：复用GitHub隔离影子几何审计器输出疑似竖框 **15/16/17** 和横向价格锚点 **18/20/23**；不读取锚点OCR文字，只用三条水平价格框的行中心间隔、字体高度中位数与竖框左右边界，推导3行，再在异常框延伸到第四行时最多外推1行。生成4条约40px高的真实图像横带，送同一Small再次识别，**4/4完整正确，16/16逐字匹配**。这是**本地Python已完成影子自动建议**，不是手机已经自动修正；不能认为整个手机端CER=0。
- **强限制与风险**：原错误竖框3条与候选新横行4条不是同分割单位，不能直接相减得所谓手机CER提升百分点；原始手机仅保留前24/36框，整页漏检尚未验证。**真竖排+横向价格锚点有已构造的误报强反例**，此算法不能安全自动删除、旋转、拆分或覆盖正常真竖排字。因此正式APP和TTS/白框/认字一律不变；尚未做真实手机上的额外REC延迟、热负担与能耗测试。
- **已永久去敏报告**：`test/phone-ocr-single-frame-diagnostic-20261103:docs/phone-small-crop-rec-ab-first-real-frame-20261103.md`，Commit **`cacf91b110e7dd297e7f68a7f4b8de1b683ca1e8`**，从 GitHub 回读核实。仅放匿名指标和限制，不含原照片/中文原文。本会话可分享**仅给用户**的可视化 `/mnt/data/private_phone_rec_ab/private_small_rec_original_vs_auto_rows.jpg`，它包含用户图像，不要推GitHub。
- **继续步骤**：收集至少1张真正竖排字+水平价格锚点的真实反例和另1张同一价目表不同角度，先标注几何方向真值，再做只读A/B；仅在无误伤且手机延迟可接受时考虑独立实验APK的局部第二次Small REC开关。**结论：这张实际手机图首次得到与历史原始手机结果完全复现的同Small真识别证据，错误纵向跨横行改成正确横向裁剪后4/4行真正读对；“有机会提升”已被实验验证，但不能保证其它照片或直接上线。**


#### 2026-11-03 — 第二份真手机竖排照片证明 SDK 顶点按X排序错轴，局部修正后同Small由1字恢复24字

- **样本**：用户主动上传 `kanzi-ocr-single-frame (1).zip`（CRC PASS，30条文件，SHA256 `c95f2e3ceed56a4c8ae8dbc65e9b2e20d8bf5b5dfb28153de0c1253e4569f699`），1080×1920、PP-OCRv6 Small、未增强、DET724ms、REC613ms、13/13最终框全导出；图片为**真实从上到下右到左的竖排中文说明板**，不是上一张“横排被误识别成竖排”的价目表。用户图、原始NV21/四点/全部中文结果/张量**均不上传GitHub**。
- **旧影子横排跨行风险检测器实际回放**：本张13框**0个误报**、`NO_CONTEXTUAL_RISK_DETECTED`，原上一张价目表24框仍标记15/16/17号；并非所有真竖排会被保护，已知“真竖排+横排价格行对齐”合成强反例仍不可自动改文本。NV21转PNG最大像素差1级，解码PNG和实际OCR输入PNG完全相同。
- **同原Small ONNX逐字复现**：新ZIP全部13份真实 Float32 REC tensor用本地已SHA锁定的相同Small权重+`inference.yml`+官方CTC解码重新推理，**13/13预测逐字同手机原metadata**。然后只对源第12号真实长竖框的裁剪图做180°方向对照：手机原输出**1字**（conf0.876877）；反转后同Small输出**24字**（conf约0.988），匹配用户照片实际长竖列的视觉阅读；邻近四条原本正确的真竖列2/3/4/5原输出约24/25/25/21字，强行反转后只读3/0/6/1字，**不能普遍反转所有竖排**。
- **查明SDK源具体失效**：官方 PaddleOCR `v3.7.0` 的 `postprocess/QuadGeometry.kt:orderMinAreaRectPoints` **按X将4顶点分两组，假定左两点为左边缘**；真实第12号长竖列倾斜使**全部顶部两个点的X都位于底部两点右方（X不交叠）**，导致长轴被当作宽度，`QuadTextCrop.kt` 的垂直旋转条件未触发，输出列方向不正确。这是**SDK裁剪顶点排序**具体风险，不是Small中文识别能力必然不足。
- **本地反事实修正同Small**：对minAreaRect4顶点先以Y分组上下端、再以X定每端左右，然后保留官方透视/旋转法，真正SDK原图片、同Small REC重新运行后，异常第12号恢复**24字**（confidence约0.989652），其它四条真竖排2/3/4/5识别文本逐字不变；同旧横排跨行误框15/16/17输出不变（**这是两类不同错误，不能互称已修复**）。对两张手机图9个长竖框按上下X完全不交叠检测，**只命中本张第12号**；数据仍不足以泛化。
- **独立纯Java安全预警器**：只读 `test/phone-ocr-single-frame-diagnostic-20261103:app/src/main/java/com/vobichngan/kanzi/VerticalCropAxisGuard.java`，提交 `6524183bf265af417ae4c84c07caa26d368daa3f`，不接手机OCR、TTS或白框；合成20项安全断言 `scripts/VerticalCropAxisGuardSmokeTest.java`，提交 `178d7ff24d892c1d26ed8c3241775efa93d5c040`。独立纯Java CI `41645471102` https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41645471102 **SUCCESS**，日志 `VERTICAL_CROP_AXIS_SHADOW_TEST=PASS assertions=20`，提交 `5b03a25d6f498772dbb09e4e8139e67b27c368fb`。注意诊断分支的旧`app/**` CI触发器可能在加入只读类时额外启动独立Debug构建Run`41645323181`，**不是已实现新的手机修复，也不是正式版更新**。
- **永久去敏报告**：`test/phone-ocr-single-frame-diagnostic-20261103:docs/second-real-phone-vertical-sdk-crop-axis-20261103.md`，提交 **`0193a3b054b6c192a8dd55973bce23762d1eaf15`**，已经GitHub回读核对。实验照片/裁剪/实际OCR中文不进入公开仓库，本地用户私有图：`/mnt/data/phone_vertical_analysis/phone_vertical_crop12_before_after.jpg`；原用户ZIP仅在对话文件上下文。
- **下一步**：优先设计**独立、保留回退的SDK内部MinAreaRect顶点排序修复实验**，只针对垂直长框上下X不交叠情形，仍使用Small一次REC，避免无必要的新模型或整张二次推理；拿真实竖排不同倾斜角、水平误框、横排文本测试零失字/不倒转，再评估是否授权新实验APK。**当前正式看字助手和用户已安装诊断工具均未被升级/覆盖，也没有自动更改原文。**


#### 2026-11-03 — 用户授权大量公开横排/竖排图 A/B；SDK竖排错轴修复独立留出验证完成（隔离纯Java/CPU实验，正式版未动）

- **用户明确同意**“按照这个方案来，也可以网上多找横排竖排图片大量测试”；开展的是此前**未做过**的“真实竖排SDK顶点排序修复风险”的大规模公开**标注框Crop→PP-OCRv6 Small**同图配对，而非重复历史122框/72图、CRAFT、版面排序或云端生成文字。**横排价目表跨行DBNet误框是独立未解决问题**，本轮不把竖排SDK修复当成它已修复。
- **公开来源及不重叠选择**：`Yesianrohn/OCR-Data` Apache-2.0 的RCTW/ReCTS/ART/CTW/MTWI，每类固定偏移1910、2190、2470各20张，**300张自然场景/3,112个标注区域/3,061个有效多边形**，避开此前实验0–~750附近位置。另`AlroWilde/vertical_annotations` MIT 公开竖排数据**791张**，压缩包实际是791 JPEG + 单个6.6 MB`metadata.jsonl`，成功解析**36,611个竖排文字框GT**；并取**前160张**人工仿射剪切作开发（7,125标注框），取**从第321至480张**不同原图人工剪切作独立留出（7,651标注框）；加上本机新生成100张不同字体大小/倾角的印刷体竖排图（1,200字符）。按不重复的图片实例为791原竖排+300自然+320基于原图的仿射增强+100本机合成=**1,511个图片实例**，**不是1511张独立实拍**；公开底图1,091张。按各几何变体计共约54,499个有GT区域检查，但仅对高风险做Small REC配对，不得冒称54k次识别推理。
- **关键限制**：竖排公开数据原图大多正轴，几何护栏不触发；高度倾斜的测试是人为模拟剪切，不能代替真实手机模糊/透视/发热。全程是**GT已知框的裁剪识别对照，不运行DBNet，也非Android实机**。模型仍为PP-OCRv6 Small REC SHA`5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`，同 YML SHA`ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1`；官方Android v3.7.0 minAreaRect+X-sort基线 vs 仅高细长且顶/底X不重叠时Y-sort候选。人工 GT Levenshtein CER，**不是用置信度冒充准确率**。
- **真实自然场景实测**：300照片/3061有效区域中只**4个**几何风险触发，实际四次配对 REC；仅1框3字有可用人工文字标注，而且旧/新都是3个编辑错误，不存在可证明的本轮自然场景总体收益。不能夸大。
- **DEV人工倾斜竖排**：160张公开底图增强，7,125字区、6,608风险触发；240框随机reservoir做同Small配对，**137条错误减少、17条错误增加、86条编辑数不变**。原编辑4554→无条件1090，GT 8113字符，CER约56.13%→13.44%，**只在高风险框子集，不代表真实手机平均**。另本机100张生成图/1200字，原版448编辑错误→几何护栏候选0；40张改善、0退步；同样仅人工打印体压力。
- **为什么不能无条件改**：开发240样本的17个退步大多是本来已经能读很长的古文列，调整crop后把一个字/标点改坏，违背保真要求。故冻结**保守双REC策略**：`minAreaRect`极细长且上下两端X区间完全分离，**首次Small ≤3个Unicode码点**才尝试对**同一原始画面**重裁剪并再次Small REC；二次输出**至少10个码点、confidence≥0.80**才允许被考虑替换，否则**原始文本完全不改**，每帧最多2次附加REC。这是纯OCR不语义猜字的规则，不可能从样本证明未来绝对零错。
- **独立留出集验证**（不同源图索引321–480、160张增强/7651框/7344几何风险；随机抽240框并真实跑原、新同Small）：无门槛148条改好、19条改差、73同误差；GT8588字，原编辑5231、无门槛1529。**预先冻结的保守门控**：需额外第二REC的首次≤3字共119框，实际接受107框，**107条GT误差更小/0条GT误差更大**，按原风险框同分母编辑5231→**1792**，CER**60.91%→20.87%**。**仅限人工倾斜且被抽中的高风险竖排框，绝不可宣传手机全部识别率提升40个百分点或0误报保证**。多张文字框来自同一本/同页的高度相关数据。
- **性能**：Linux开发/留出组单次Small REC中位约26ms/74ms；保守策略在真正低输出框上额外跑一次，**并非无代价或实时手机测量**；正式手机发热、帧率、时延、热量尚未验证。用户已安装正式App、原单帧诊断APK、TTS、白框和模型参数未更改。
- **实验代码和永久报告**：`test/phone-ocr-single-frame-diagnostic-20261103:scripts/public_axis_guard_heldout_benchmark.py`，固定门控脚本Commit `446402109f9df9a0c881f276e1875a7cfaab09a6`；公开批量成功Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41650574681 / Artifact `13236883266`；独立留出成功Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41651286089 / Artifact `13236639363`；单纯Java风险+安全策略 `VerticalCropAxisGuard.java` 与 `VerticalRecRescuePolicy.java` Commit `6c58a00f2acb00c45d58a31e10b3dcde8c69b442`，29项Java回归CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41652352025 **SUCCESS**；永久完整去敏报告`docs/public-horizontal-vertical-scale-ab-and-independent-gate-20261103.md`，最新Commit `5f9fef02683a1facee2574ee82df8cc3be5ca548`，已回读。
- **交付研究数据**：用户会话本地压缩包 `/mnt/data/kanzi_ocr_public_horizontal_vertical_large_scale_ab_20261103.zip`，已验证CRC，含两轮公开JSON原始指标/案例、合成100图源码和数字总结（不含用户图/字体/模型）；ZIP SHA256`52c82bd11b42104a024eb336b173f663d192b8371ad8315fc7036e5bee5720b9`。**用户上传的两份手机原始ZIP、NV21、图片、实际私字和个人文件不进入GitHub/CI或公开研究包**。
- **下一步**：如需走向产品，必须在**隔离Android试验版**实现按需双REC及原文本保留/退出条件、记录第二次推理单帧耗时/温度与真竖排反例，不可仅凭Linux synthetic直接覆盖正式APK；另开独立样本排查横排跨行DBNet误框。当前安全策略Java类**仅作影子纯Java源码，不接入摄像头/OCR/朗读，不是已可用修复版**。


#### 2026-11-03 22:25（Asia/Taipei）— 用户提供真实目标设备配置：vivo X200 Pro 12GB RAM；后续优化不得对旗舰性能过度保守

- **用户明确硬件**：“我的手机是 vivo X200 Pro，内存12G，所以你要综合我手机的性能，也不能过度地保守”。此为用户实物信息，**以用户的12GB实机为准**，切勿误用vivo台湾官网16GB版本当作这台设备；vivo中国官方X200 Pro规格页 https://www.vivo.com.cn/vivo/param/x200pro 确认 V2405A/V2405DA 12GB+256GB变体，并确认天玑9400 3nm/8核、LPDDR5X四通道、UFS4.0、Immortalis-G925 GPU（NPU 890见官网概览，但ONNX能否调用硬件AI加速必须实测，不能直接声称可用）。12GB RAM不是可供APP全部独占、也不等于推理必然更快；Android系统占用与热调度仍要测。系统/OS以手机日志为准，不要因出厂系统版本而误报用户当前系统。
- **这台手机的已有真机数据**：两次用户诊断原始单帧来自手机，1080×1920，第一次DET831ms+REC780ms、第二次DET724ms+REC613ms（不含其他总开销）；合计已知核心OCR用时约1.34–1.61秒/次。测试时已是大幅超过30fps实时的单帧任务，不可把“旗舰芯片”直接当作已经有30fps识别。新实验必须抓 DET+REC 主瓶颈、额外REC真正成本、稳态功耗，不靠RAM大小猜测。
- **新目标：旗舰自适应“均衡/高质量”档，不再一刀切超保守**。在**独立实验Android APK**上并排对比：1）原基线Small/DBNet，不变；2）均衡模式按异常风险最多额外2个Small REC候选/每次稳定场景；3）高质量模式尝试最多4个Small REC候选/每次稳定场景，前提是已有错误证据与实际耗时预算允许，**不是每一摄像头帧重复做4次**。每种模式必须保留原文字结果、置信度、候选及采纳证据，并在同一输入测试；原来的只读Java policy `MAX_EXTRA_REC_INFERENCES_PER_FRAME=2` 是初始影子安全阈值，**不是最后手机能力上限**。允许实验时探索更高ROI预算，依据实测停止；不能仅为了性能牺牲零语义幻觉和文字原文保真。
- **性能实测组合**：相同角度/拍摄图在2、3、4个CPU intra-op线程、不同REC batch大小（例如1/2/4）、是否支持本机硬件EP（先验证支持与正确性，再基准比较）之间做可复现 A/B。专门分开测DET预处理/推理/后处理、REC各部分、额外候选、内存峰值、p50/p95延时、连续运行5/10/15分钟热状态、跳帧/缓存命中率。线程数或硬件EP未实测前**绝不提前判定4线程/GPU/NPU优于2线程CPU**。
- **动态预算与稳态策略**：允许短暂高质量纠错尝试，但以相同场景首次捕获/新增困难文字优先，低变化画面复用稳定结果；当延迟突增、OS热限制、OOM压力、用户移动/场景变化时退回均衡/基线。针对本机正式建议应基于**同一台手机的端到端实测**，不能用Linux CPU耗时、合成图CER等替代。
- **正确率底线不变**：离线、小模型OCR真实图像输出、绝不语义补字、真竖排不可误裁、正常原文不因更激进模式被覆盖。此前独立留出集107改善/0观测误伤仅是人工仿射竖排高风险框，不等于全局零失误。把“敢于充分利用旗舰手机资源”落实到**更多受控候选、合理batch/线程、动态负载**，不是无条件接受低可信内容。
- **实施状态**：仅更新工程约束和下一阶段试验计划，**没有**更改现有正式APP、任何用户APK、已验证的OCR模型、即时朗读/TTS或上述2次影子Java上限；后续真正做手机实验版时再参数化并验收。


#### 2026-11-03（vivo X200 Pro 12GB）— 首个可安装的四档离线性能＋同Small竖排影子纠错Android独立APK已成功构建/下载/静态验收

- **用户明确授权立即开始**针对真实目标设备vivo X200 Pro 12GB开发旗舰性能适配；此前已经严格确定原则：必须本机离线、绝不语义编造文字、尊重实机发热/延迟，不应过度保守但必须实验验证。工作仅在`test/phone-ocr-single-frame-diagnostic-20261103`隔离分支，正式`com.vobich.kanzi`源码/正式签名/安装包**不改**；旧独立`com.vobich.kanzi.framelab`诊断版本亦不覆盖。
- **确切完成的交付**：独立GitHub Actions run **41656194876** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41656194876 **success**；`BUILD SUCCESSFUL`（Android Gradle）、`X200_SHADOW_SDK_PATCH_PASS`、`X200_OFFLINE_SHADOW_AB_APK_AUDIT_PASS`。本次成功构建的严格源码commit是 **`c11927c1456a784bb73e989442aee7cbf3325192`**。工作流/打包输出`com.vobich.kanzi.x200lab`，标签“看字助手 · X200性能实验”，versionCode41、versionName`0.9.9-x200pro-performance-lab`；与正式版和旧帧诊断共存。
- **下载且实际解包验证的安装文件**：`Kanzi_X200_Offline_OCR_AB_debug.apk`，**390383487字节**，SHA256 **`8448155d5c2e3f9ec6e5f79323d67efd9a1abf4c5761b58594e22a3427792312`**。GitHub Artifact **13239213707**，https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41656194876/artifacts/13239213707 ，Artifact ZIP **194735999字节**；已从GitHub下载、解包重新算APK SHA并核对Actions`SHA256SUMS.txt`、ZIP结构CRC均PASS。此会话文件`/mnt/data/kanzi-x200-pro-ocr-ab-debug-artifact.zip`、直接APK路径`/mnt/data/x200_android_experiment/Kanzi_X200_Offline_OCR_AB_debug.apk`（必须后续会话重新核查路径是否存在才能向用户链接）。**APK Manifest无INTERNET权限**，有arm64 ONNX Runtime，小模型 DET SHA`d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e`、REC SHA`5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`，PP-DocLayout-S沿用原CI哈希验证。
- **确切功能**：可在手机Settings切换4档原始实时Small/DBNet的ONNX CPU线程＋REC批大小：对照`baseline` 2线程/批1/额外候选0；均衡`balanced` 3线程/批2/候选2；高质量`quality` 4线程/批2/候选4；高吞吐`throughput` 4线程/批4/候选4。**默认对照**保证可复现旧2/1行为，用户可主动选择质量。档位切换重新实例化同一Small模型，不默认声称4线程/批4必然更快；仍须用户手机同帧A/B。
- **真正在Android隔离SDK新增的影子功能**：`scripts/patch_x200_shadow_sdk.py`在下载PaddleOCR Android v3.7.0后、构建前对第三方`QuadTextCrop`增加只针对Y轴重排序风险的`cropWithVerticalAxisRepair`，在`OCREngine`和`PaddleOCR`加公开`recognizeVerticalCandidates`：最多4个原始帧BGR ROI，**第二次真正运行同一个Small REC**，不跑第二次DBNet；旧`QuadTextCrop.crop`原有逻辑保持不变，诊断ZIP原始裁剪和张量亦保留。应用`PaddleOcrEngine.kt`只在用户**手动诊断**时筛选顶/底X不相交且极细长、首次REC≤3字的风险框，最多按档位2/4次再识别；若新结果≥10字/conf≥0.80才标记门控“建议”，**绝不替换实时OCR/TTS/白框**，原文保留。此前公开竖排独立留出107改善/0观察退步只证明Linux人工斜竖排**目标风险框**，没有证实手机上这一APK的实际候选改善，不能冒称手机已修复。
- **真实硬件诊断**：`OcrHardwareProbe.java`记录Android热状态等级（不是温度℃）、系统剩余和总RAM、Java及Native heap占用，诊断日志额外报告基线一次OCR的墙钟耗时、检测/识别耗时、每次附加Small REC时长；热状态`SEVERE`或OS内存压力取消附加REC，MODERATE限制最多2，普通状态高质量允许4。整个正式实时OCR仍一次原始Small推理路径，不被影子结果改动。新增单帧ZIP metadata`x200_profile`/`ocr_cpu_threads`/`ocr_rec_batch`/`shadow_rescue_budget`以便对照；用户隐私图片/张量不进公共仓库/CI。
- **独立安全测试**：`OcrPerformanceProfile.java`四档配置；`VerticalRecRescuePolicy.java`原2次默认只读逻辑保持，新加可指定0..4预算的方法；`scripts/X200OfflineProfileSmokeTest.java`、`scripts/VerticalRecRescuePolicySmokeTest.java`；CI run **41656942455** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41656942455 **success**（4档和严格预算、原始文本不被猜字覆盖）。早期由于旧SDK未补接口的中间push产生若干失败CI run，最终独立X200工作流使用可回读的`patch_x200_shadow_sdk.py`后成功编译，不得将中途失败当最终失败。
- **后续隔离代码提交与构建差异**：构建head`c11927c...`以后，`bdd1e9ee5b30f806f0895e8e89210795957f52db`仅将诊断报告换行显示从原输出的字面\\n改成换行，后续增纯Java脚本/测试工作流、实验总结文件，**这些后续源代码改动尚未重新打包入已核实的APK**，不能向用户虚报。APK在当时CI已成功完整可安装，但**没有实际在vivo X200 Pro上安装运行验收**。
- **完整永久去敏交付说明**：`test/phone-ocr-single-frame-diagnostic-20261103:docs/x200-pro-12gb-apk-build-and-test-20261103.md`，commit **`57db4d80f98ed55a6edad4bd03372c3b067592cb`**；安装/测试手册`docs/phone-x200-performance-lab-20261103.md`。用户可直接下载对话里的已核实APK，依次在固定角度横排、真实竖排图上手动跑对照→均衡→高质量→吞吐模式3次一组，对比速度/稳定性/热状态；若支持可导出本机单帧ZIP并自行决定是否分享。
- **产品阶段判断**：这次是实际Android源码实现、SDK二次Small REC接通、独立GitHub Actions安装包构建/模型/Manifest/文件完整性审计完成，**不是手机性能加速已证实或自动竖排修复已在正式版启用**。下一步由用户在真实X200 Pro 12GB上测试CPU线程/REC batch4是否有不兼容或字错、候选是否有误伤、发热/实时延迟，再决定哪一档可做正式版参数，何时允许安全采用候选。
