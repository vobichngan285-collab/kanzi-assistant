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


#### 2026-11-03（用户明确授权重新做“一个APK全部整合”，已完成实际打包）— X200横竖排四路同帧离线Small诊断版

- 用户要求**不要下载上个X200性能实验版**，而是重新做一个APP，统一接入**横排跨行错误框恢复＋真竖排SDK裁剪方向恢复＋4种同帧A/B＋X200 Pro 12GB旗舰性能配置**，并立即提供**直接APK链接而非压缩包**。不重跑历史模型参数/122框/791张竖排实验，复用已验证成果。
- 创建新的隔离分支 `test/x200-combined-horizontal-vertical-ab-20261103`，Android新独立包 `com.vobich.kanzi.x200combined`，versionCode42、versionName `0.9.9-x200-combined-ab`，应用名“看字助手 · 横竖排综合实验”。与正式`com.vobich.kanzi`、旧`com.vobich.kanzi.framelab`、上个`com.vobich.kanzi.x200lab`**共存**，不覆盖正式原App及TTS通路。
- **已完成SDK真实同帧Small A/B**：在已锁定PaddleOCR v3.7.0、同SHA的PP-OCRv6 Small DET+REC里，第一次只调用一遍原版`current.recognize(bitmap)`，保留全部SDK原框/原文；只在用户手动诊断时从**同一帧**调用`recognizeHorizontalCandidates`（新增Android SDK水平矩形裁剪真实Small REC，绝不跑第二次DBNet）及旧`recognizeVerticalCandidates`（异常倾斜真竖框修正原SDK角点X排序错轴、同Small局部二次REC）。两者均不编造/语义改写字符。
- **新横排算法`HorizontalCrossRowProposal.java`**：读取原有只读`CrossRowOrientationAudit`同时命中≥3狭长框/≥3条对齐横排价格的几何警报，验证行高/横向隔离/上下间距一致后生成3–4条ROW ROI；此前价目表15/16/17及价格18/20/23可以按该方法导出对应横行，不用人工选择crop；**针对这张照片的过拟合风险存在，真竖排紧邻横排价格的强反例仍会触发**。因此新横排候选仅供影子对照，不会自动替换原文；CI合成16项测试还刻意证明强反例。
- **四路展示`fourWayShadowComparison`**：A原版、B横排候选、C竖排方向保守候选、D综合候选；D对两者同源框ID冲突时回退原文。候选同一帧、无全图额外DET、无云端，四路结果只在“设置→OCR诊断→开始OCR诊断”展示。**真实实时扫描和自动朗读仍使用原版OCR文字，不自动应用B/C/D**。这不是已经把两条修复安全上正式产品；没有在X200上测真实CER/速度/发热。
- **旗舰性能配置/成本**：沿用原X200方案2线程/batch1/无追加，均衡3线程/batch2/每模块最多2ROI，高质量4线程/batch2/每模块最多4ROI，吞吐4线程/batch4/每模块最多4ROI。若两个模块同帧都触发，质量档最多8次额外Small REC，**仅手动诊断**，绝不可宣传免费或完全实时；Android热严重/内存压力则附加预算0，中等热限制每模块最多2。记录原版DET/REC、基线OCR墙钟、横排/竖排真实附加次数和时长、Android热状态、剩余RAM及Java/Native堆。
- **更完整的本机导出**：单帧`SingleFrameEvidence`原始框数量上限24→96；原始NV21、原始SDK crops+FP32 tensor/四点保真不改；新增`diagnostic/four-way-ab.txt`包含四路诊断和成本（只是本机文件）；ZIP仍需用户主动点击导出并选择保存路径，用户私人原图和实际中文识别从不上传Github/CI。功能在APK的实际源码commit中已纳入，不是仅计划。
- **测试结果**：纯Java CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41661045556 **success**（原跨行19断言、新四路水平16断言、原竖排20断言、保守门控29断言、X200性能档兼容性检查）；最终**Android APK GitHub Actions run 41661796519 SUCCESS** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41661796519 ，构建head **`c727566272664ac19e24806698ea92e8eb08d969`**；日志`COMBINED_HORIZONTAL_VERTICAL_SHADOW_SDK_PATCH_PASS`、`X200_COMBINED_SHADOW_GEOMETRY=PASS assertions=16`、`BUILD SUCCESSFUL`、`COMBINED_OFFLINE_OCR_APK_AUDIT_PASS`。APK静态检查：**无INTERNET权限**、独立包名、Small DET SHA`d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e`、Small REC SHA`5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`、ARM64 libonnxruntime、DEX内`HorizontalCrossRowProposal`/`recognizeHorizontalCandidates`/`fourWayShadowComparison`/旧竖排Guard均实装。老版源后续打包可能发生过中间失败，**最终RUN成功**才是验收依据。
- **已从GitHub Artifact下载并本地提取完整可直接安装APK**：GitHub Artifact ID **13242921453**，名`vivo-x200-horizontal-vertical-combined-ab-debug-apk`；实际APK大小 **390,399,875 bytes**，SHA256 **`d1ad468866507f116676415ddc8b6743db4a99c0c00026f562bbbd01ac32556b`**，与CI带的`SHA256SUMS.txt`一致；APK本身内部ZIP CRC完整验证通过，所需4个关键类函数从DEX bytes回验。**本对话精确sandbox路径** `/mnt/data/kanzi_x200_combined_four_way/Kanzi_X200_Horizontal_Vertical_Combined_AB_debug.apk` 由容器实际创建、确认存在。若未来会话引用必须重新查路径不可猜。GitHub Artifact页 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41661796519/artifacts/13242921453 （GitHub下载的是ZIP, 给用户优先直接sandbox APK）。
- **安装与使用**：直接点APK安装（不是压缩包）；设置里选择高质量4线程/批2，在同一固定画面（横排价目表、真正竖排介绍牌）点击“开始OCR诊断”，复制/导出结果重点查看A/B/C/D；选“手动采集一帧”后导出ZIP可带原图+本地四路对照。**只要没有实机验证就不能宣称整机提速/功耗提升/误伤为零。**
- 代码文件：`HorizontalCrossRowProposal.java`、`PaddleOcrEngine.kt`、`scripts/patch_x200_shadow_sdk.py`、`SingleFrameEvidence.kt`及Android四档设置UI，隔离分支均已推送；安装指引`docs/phone-x200-combined-horizontal-vertical-20261103.md`。


#### 2026-11-03（安装文件直链用户偏好，已真正公开发布APK，而非聊天内sandbox或Actions ZIP）

- **用户新明确永久交付要求**：“给我能复制到浏览器下载的下载链接，以后只要是安装文件都按这个套路”。**从今每次发布安卓APK必须同时主动提供已真正发布、经过URL和资源验证、可复制的`https://`直达`.apk`链接**，不能只给`sandbox:/...`、ChatGPT附件、GitHub Actions ZIP Artifact 或仅给Github运行页面。先发布/验证真实独立APK再告诉用户；不要提前猜链接、不要用压缩包充当APK。
- **已实际完成首个直接HTTPS APK发布**，使用公共GitHub仓库Release（pre-release）与独立GitHub Actions上传校验，成功 workflow run **41666779621**、final status SUCCESS。Tag `x200-combined-ocr-ab-20261103-v42`，资产 `Kanzi_X200_Horizontal_Vertical_Combined_AB_debug.apk`，大小 **390399875 bytes**，状态 `uploaded`，公开release文件API回读真实 `browser_download_url`：
  **https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-combined-ocr-ab-20261103-v42/Kanzi_X200_Horizontal_Vertical_Combined_AB_debug.apk**
- 原APK SHA256 **d1ad468866507f116676415ddc8b6743db4a99c0c00026f562bbbd01ac32556b**，publish CI从此前完成并审计过的run `41661796519`官方Artifact下载，重新校验哈希、实际390399875 bytes及ZIP CRC，再通过`gh release upload`在GitHub公开Prerelease上传，已从`api.github.com/.../releases/tags/...`读取该资产`state=uploaded`/实际直链，**无需用户GitHub登录的公开APK托管地址**。工作流在隔离分支：`.github/workflows/publish-x200-combined-direct-apk.yml`，构建head对应集成版`c727566272664ac19e24806698ea92e8eb08d969`；并没有为发布再次编译其他不同APK。
- 今后如果GitHub发布公共Release需要文件小于2GB，仍要用已成功的Actions产物校验/上传，再读回实际API资产，不可仅通过预设tag推测直链；最终回应应包含纯文本可复制`https://`完整URL，最好附“复制链接”控制，必要时还有仓库Release页，明确安装包包名/版本/功能。不需要提及旧sandbox内文件。
---

#### 2026-11-04 真机测试与自动方向纠正、真实竖排候选（v43）

> 本节为用户在2026-11-04前后提供的照片、安卓诊断文本和实际安装反馈的延续记录。手机真实取景不完全相同的各轮数据**不是严格同帧A/B或准确率GT**；之前助手作出的“成功/准确率”判断要按证据重新审视。绝不以OCR置信度当做真实准确率。

**交接时项目状态**
- 设备：vivo X200 Pro，型号V2405A，12GB RAM；目标仍然是**手机端完全离线**、识别原文保真（严禁根据语义编字）、尽量低发热低延迟，以及正确的阅读区域与朗读顺序。
- 原X200隔离实验应用包名：\`com.vobich.kanzi.x200combined\`（不等于正式 \`com.vobich.kanzi\`）。v42是四路横排/真竖排候选实验版；v43加入**自动整页方向纠正**的保守实验路径（初始0°OCR，再根据证据决定是否额外旋转复识）。四路B/C/D候选并不自动覆盖正式朗读文本。
- **v43公开GitHub Release已存在**：tag \`x200-auto-page-rotation-ab-20261104-v43\`，目标提交 \`40737516dd8ae451104dfe758ee5df5f9ee5b047\`，\`app/build.gradle\` 的 versionCode=43、versionName=0.9.10。已核对Release资产 \`Kanzi_X200_AutoPageRotation_AB_v43_debug.apk\`，大小390399871字节，sha256 \`df6bf6b788b925cdcb9e16a3f7d920e66a344819501be09c2d9778f8a8d80f84\`，直接浏览器下载地址：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-auto-page-rotation-ab-20261104-v43/Kanzi_X200_AutoPageRotation_AB_v43_debug.apk
- 构建记录 \`https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41683998618\`：**整个workflow最终是failure**，但其\`assembleDebug\`编译、APK静态验证、Artifact上传步骤为success，失败发生在末尾“比较v42证书并发布v43”步骤。**不能把整个workflow记为SUCCESS，也不能把GitHub Release文案中“签名已匹配”的描述当成已通过手机覆盖安装的证据。** Release资产单独可见，属独立事实。
- 用户在vivo安装v43时曾出现明确的安卓提示：“应用未安装：软件包与现有软件包存在冲突”“安装包的开发者签名有异常”。这证明**在当时用户手机上不能直接覆盖既有测试包**，需检查两包实际签名证书/安装路径；不要再次承诺可以覆盖。用户后来反馈“装上了”，但没有记录其采用的安装处理步骤；不假定已修复签名流程，也不要再要求其无必要地卸载应用。
- 用户展示过**正式版**“识别与朗读设置”界面，已有“自动纠正整页方向（默认；仅明确异常时追加OCR）”，以及手动向右90°、180°、向左90°；证明正式版已存在同类能力，不能再宣称从零新增。需区分正式实时扫描设置与X200实验版的**诊断固定原版0°**路径。

**实验记录：横排书页与整页旋转**
- 用户上传九年级语文教材照片，并提供多轮X200高质量档诊断（4线程/REC批2，ppocrv6_small）。四次v42记录：第一次21框/均置信度0.943/整图2194ms；第二次34框/0.862/2074ms；单页第81页第三次28框/0.969/3183ms；书页整体旋转90°第四次48框/0.694/3427ms。这些场景的文字数量、页面范围不同，不能据数字大小证明算法优劣。四路A/B/C/D在这四轮均无有效采纳；真正竖排修复0次、横排跨行0次；PP-DocLayout-S当时关闭，AI归属0。
- 单页第81页出现真实原文与OCR错字示例：\`芦苇\`→\`产苇\`、\`高贵\`→\`高费\`、\`神话\`→\`种话\`；部分正文阅读顺序、页码及相邻页面归属混乱。高置信度不保证字字正确。页面右/左侧被截断（检测框x=0）时不应把可见缺字直接归咎模型。
- **v43带照片的旋转双页实测**：诊断记录检测22框（其中非空18框），平均置信度0.610；原始0°整图OCR墙钟1816ms；原有“自动整页方向纠正A/B”结果明确为**“未触发：无可靠的整页方向候选，保留0°原版”**；额外真正竖排局部REC 2次/74ms，源框#13 \`无乙\`→\`无梁乙\` conf≈0.557与源框#14 \`①\`→\`①\` conf≈0.490均因\`CANDIDATE_INSUFFICIENT\`未采纳；实验阶段墙钟1898ms；诊断总用时2650ms；热状态0级。检测框多为竖向细长、内容有大量错乱，且背景书/试卷方向混杂。
- 用户随后直接反馈：**“旋转90之后可以”**，即通过设置中的手动90°方式能识别/使用；未提供该次实时路径原始文本与耗时，不宣称具体CER提升或100%成功，也尚不能严格证明究竟是方向判定、页面分割、阈值门控哪个条件阻止自动路径。技术结论：**优先调查自动方向候选/主阅读区域分离的漏触发；保留手动方向能力作为对照与临时可行方案；先别急于更换PP-OCRv6 Small**。

**另一个不同任务：真正竖排中文样本（不要与横排旋转90°混淆）**
- 用户随后提供\`kanzi-ocr-single-frame (2).zip\`，此前助手读取并报告其为竖排中医养生/刮痧材料，单帧OCR约14框、原始整图约1102ms，真正竖排候选触发1次、额外REC约63ms；原框#13 \`有叠\`，候选为\`压、中暑、肌肉酸疼等所致的风寒痹症都有立竿见影之\`，候选置信度≈0.985，门控\`STRONG_OCR_CANDIDATE\`。这组**为此前助手据上传ZIP作出的报告**，交接后若做正式验收应重新读取ZIP中的原始帧、框坐标、裁剪/识别结果并按原图逐字核对，不得仅凭报告和置信度认定逐字无误。
- 该改善当前只在**手动诊断的影子候选**，并未进入实时TTS。真竖排应另测从右到左列排序、每列从上到下顺序、误修复率；不能把横排文字整体旋转的例子当作本模块应当触发的GT。用户已同意后续采集其他真竖排样本（诊断ZIP+原文照片）；但若已有样本可先复核，避免重复让用户拍摄。

**下一阶段明确优先级和严谨验收**
1. 核查v43 \`AutoPageRotation\` 的代码及真正工作路径：0°初识、主要阅读页/文字方向几何证据、90°/270°比较与触发、拒绝原因日志、方向缓存和跨帧稳定；注意正式版已有方向纠正，与诊断原版0°的路径差异。不要为了提高触发率而盲目降低阈值，以免误旋转正常文本。
2. 最好对**同一原始NV21帧**跑原0°、手动/强制正确90°、自动候选的可比A/B；以照片/人工GT统计漏字、错字、顺序与推理成本，保留原始框和候选追溯信息。真机内存、持续发热和时延需多轮观察。
3. 保留独立的真正竖排修复，额外REC必须基于像素、严谨门控，任何合并/纠错不得编字。先验证不同真竖排材料上的逐字准确、误采纳及列序，再考虑接入正式TTS。
4. 安装与发布必须检查**与用户已安装版本**相同applicationId与实际签名SHA256、递增versionCode、Android验证结果；必要时采用独立包名让两个实验版共存。不能仅依据Release注释宣称“可覆盖安装”。
5. 用户希望**每次APK都提供真实、可复制到浏览器直接下载的HTTPS .apk链接**；先实测发布资产存在，不能只给zip、Actions Artifact或推测URL。勿在仓库上传用户私人照片/单帧诊断ZIP；仅保存匿名技术结论和用户主动授权的适当资料。

**交接提醒**：以上用户图片和ZIP只在相关对话附件中出现，不保证新会话自动拿到；新会话应先查GitHub代码与交接文件，需重新拿原始测试证据时再请用户提供。状态是“已观察到问题和候选改善，仍待严格验证”，**不是算法已全自动解决、不是可靠率/性能已验收**。

#### 2026-11-04 竖排长列诊断 v44 真机构建与后续任务

- 现象：用户真机反馈中医养生刮痧宣传牌的竖排文字，部分OCR识别正确，但实际连续TTS朗读顺序混乱。不能把OCR正确等同于朗读正确。
- 原因：现有 ReadingOrder.verticalColumns() 主要处理单汉字OCR框；这组样本的正文通常是一整个长文字框代表一列。正式快速扫描另外使用 RealtimeTextOrientation，不会自动采用诊断候选。
- 独立实验分支 test/x200-vertical-long-column-shadow-20261104，从v43提交 40737516dd8ae451104dfe758ee5df5f9ee5b047 建立；不改正式包。
- 新增 VerticalLongColumnShadow.java 纯几何候选，在手动 OCR 诊断中展示长竖列从右到左排序，原文字、框编号和其余背景文字完整保留。遇多区域、重叠列或不可靠坐标则拒绝。无额外OCR推理。几何不能单独证明真竖排，故绝不自动接入实时TTS。
- 真实照片元数据校对：前述第4份单帧 ZIP 长列框候选约 #7→#5→#4→#3→#2，第2份约 #6→#5→#4→#3。只是原框几何顺序，非人工逐字GT；没有将用户私人ZIP或照片上传GitHub。
- 静态测试 GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41775446668 已成功，涵盖候选从右向左、完整框编号、普通横排不误触发、多区域不乱拼、坐标异常拒绝、现有实时方向回归。
- v44 安卓编译及发布 GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41775818985 结果 SUCCESS；新版看字助手·竖排顺序诊断，versionCode44/versionName0.9.11，包名 com.vobich.kanzi.x200verticalshadow，与v43独立共存；实测前不得宣称朗读已改善。Android静态审查无INTERNET权限、离线OCR模型/ARM64和APK签名检查通过。Debug签名不保证后续同包名覆盖。
- 已核实GitHub Release资产存在且上传完成： https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-vertical-column-shadow-20261104-v44/Kanzi_X200_VerticalColumnShadow_v44_debug.apk
  大小390399875字节；SHA256 dba09be6147001221747209721e90c0a43219923300f0985b1826d10d254f7e1。
- 待办：用户安装v44后在真竖排材料上手动OCR诊断并核对“长列竖排阅读顺序候选”；若人工逐框顺序可靠，再考虑有门控地接入实时朗读并用更多横排旋转反例、TTS连续性、热/内存/延迟进行A/B验证。现有v44不改变实时朗读文字或顺序。

#### 2026-11-04 USB预览左右镜像与竖排影子诊断合并 v45

- 用户新提供两张手机/USB预览截图：同一视角，用户实际对面吉他在右侧，手机预览在右侧，USB视频预览却在左侧，说明USB显示疑似左右镜像。用户反馈现有“水平镜像”设置无法纠正，并明确要求把上个尚未下载的v44竖排长列诊断与USB画面修正合为一次下载安装。
- 源码隔离分支：test/x200-vertical-usb-preview-v45，基于v44 shadow诊断分支，不修改正式安装包/正式朗读逻辑。
- v45（versionCode45，versionName0.9.12）保留上一版 VerticalLongColumnShadow 的诊断专用长列从右到左排序候选，仍不接入正式实时TTS，防止横排旋转误判。
- USB修正：新增 USB-only display mirror calibration，设置中的“USB画面左右校正”默认开启；现有“附加水平镜像”可反向切换。实现以 UsbPreviewMirror.shouldMirror(usbCorrect, extraMirror) 控制 USB TextureView.setTransform(Matrix.setScale(-1,1,pivot...))；原 cameraTextureView.setScaleX 翻转被替代以避免GL USB渲染不响应，USB OCR检测框通过 OcrOverlayView.setUsbDisplayMirror 同步镜像可视化坐标。手机 Camera2 预览未更改，USB NV21 OCR输入、原始OCR识别和正式TTS未镜像或修改。camera view重布局/连接/切换时重复应用矫正。
- 新增 UsbPreviewMirrorSmokeTest.java：测试默认左右反转/可取消、边界映射与中心不移、其他摄像头保留、异常坐标拒绝；原 v44 竖排诊断与现有真实横排方向回归并行。CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41777391529 SUCCESS。
- 完整 Android CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41777499087 SUCCESS：离线模型核验、Android版本/包名、无INTERNET权限、签名验证、独立APK公开发布均成功。签名为临时Debug，不保证未来同包名可覆盖；应用包名 com.vobich.kanzi.x200verticalshadow，与v43 com.vobich.kanzi.x200combined 不同，可共存。v44用户尚未安装，因此只需下载v45，不必下载v44。
- 真实GitHub Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-vertical-usb-preview-20261104-v45 ，已经从GitHub API核对资产 uploaded。独立APK链接： https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-vertical-usb-preview-20261104-v45/Kanzi_X200_VerticalUSBPreview_v45_debug.apk ；大小390399875 bytes；SHA256 d4fcbf15292885b359edf2c1b53eb2845eee0dd289994f07ac6f23fe74598261。
- **未验收内容（必须讲清楚）**：上述USB左右修正基于截图和确定的TextureView内容变换，尚无用户安装后同机同视角验证；不能把编译成功当作实机已修好。下一步安装后选择USB摄像头，观察吉他位置是否在右边，若仍反则确认“USB画面左右校正”已勾选且“附加水平镜像”未勾选，重新截图反馈。之后再用真竖排宣传牌查看新章节“长列竖排阅读顺序候选”。正式TTS仍沿用旧版本，仍须另行严格真机A/B验收。

#### 2026-11-04 第5份单帧诊断：USB原始帧旋转90°导致真正竖排乱码

- 用户在v45真机反馈：“横向的文字读的还行，竖向的文字目前还是不行，读的乱七八糟”，上传第5份原始单帧诊断ZIP（未将私人照片/ZIP上传GitHub）。
- 已检查 ZIP metadata + decoded-frame：原始USB相机帧 **1920×1080**, OCR输入角度 **0°**, PP-OCRv6 Small，配置 baseline 2线程/REC批1，识别结果10框；det 677ms，rec 718ms，整图墙钟1405ms，热状态0级。 shadow_rescue_budget=0；竖排附加REC 0，长竖列排序提示“长竖列不足4条”。
- 原始画面为“刮痧·中醫養生”真正竖排宣传牌，但在未转正的1920×1080 RGB帧中整张宣传牌相对画面**横着90°**。将上传照片逆时针旋转90°后，原版面才回到正常竖排（正文从右向左逐列排列）。现有0° OCR错误地将正文五列检测成五条极长的**横向**框，只识别出“有”“科进期”“。花”“中为理部”“一”；并非只需重新排序即可纠正。必须先正确旋转像素，再尝试真正竖排文字识别及列序。
- v45现有USB左右镜像修复只针对**显示预览与显示的OCR框**，不影响NV21原始帧，因此不能解决原始帧90°方向问题。用户在设置中已经有“实时文字识别方向：手动将OCR输入向左旋转90°”，代码 `PaddleOcrEngine.recognizeNv21` 只在fastScanMode应用此旋转；**独立手动诊断仍固定0°**，不能靠诊断报告验收手动实时旋转。
- 另一个后续缺口：即使手动270°让竖列字符回正，v45新的 VerticalLongColumnShadow 仍**仅用于手动诊断**、不进入正式TTS；当前 ReadingOrder.verticalColumns() 主要重建单字框，未保证一框一竖列能够按从右到左顺畅播报。
- **建议先做现场实测**：保持USB相机的预览左右校正设置独立；进入“实时文字识别方向”选择“手动向左旋转90°”并点击开始识别（非手动OCR诊断），用同一刮痧宣传牌听识别是否变成大段正确中文。用户若确认文字识别改善但朗读仍乱，再优先将经校验的长列排序接入实时语音；若仍不能识别，则查真竖排SDK REC处理，不可凭上下文补字。
- 下一版工程方案需将**USB传感器横竖方向**与**真正的竖排排版顺序**分离，并对同一NV21帧提供0°/270°真实推理A/B；恢复原文必须来自真实像素证据，避免盲目四向全帧OCR和高发热。当前不是修复已完成。

#### 2026-11-04 v46 USB预览和真实OCR输入彻底独立

- 用户强烈指出：不应将手机上看到的USB预览图像的旋转/镜像与实际送给OCR的像素混在一起；过去多版诊断仍固定使用0°，与实时角度及预览不一致，导致故障归因混乱。纠错原则：单帧原始NV21先复制成独立输入；预览仅负责显示镜像/旋转，OCR仅根据自己的明确方向设置旋转解码Bitmap，两路互不影响。
- 本次新分支 `test/x200-independent-preview-ocr-v46` 基于v45分离测试版，仅修改摄像头->OCR方向一致性和诊断证据，不更换PP-OCRv6 Small/布局模型、不更改文字修复或实时竖排列序。独立包名 `com.vobich.kanzi.x200isolatedio`，versionCode46、versionName0.9.13、APP_VERSION0.9.13。
- 新增 `OcrInputOrientation.java`：只做与预览矩阵无关的0/90/180/270输入尺寸和坐标映射；USB预览显示继续由 `UsbPreviewMirror` 和 `TextureView` 管理，输入OCR不读取/使用预览的旋转或镜像。`MainActivity`新增单独保存的“USB摄像头→OCR输入方向”，默认0°以保留原行为，允许手动90°、180°、270°。在同一个USB采集帧上，实时`recognizeNv21`和手动`diagnoseNv21`都接收该明确的USB OCR旋转值，手机摄像头控制保持独立（手机诊断仍保留0°基线）。
- `PaddleOcrEngine`的手动诊断现在会把原始`decoded-frame.png`及真正旋转过的`actual-ocr-input.png`写入诊断ZIP，且`SingleFrameEvidence`的`ocr_bitmap_rotation`字段记录真实旋转角度（之前硬编码为0）。诊断文本打印原始1920×1080及实际旋转、模型Bitmap尺寸。正向与反向坐标映射保持原始框位置，不修改用户原始NV21。手动使用非0°时不再叠加自动旋转候选，避免两次错误纠正。
- 边界：v46**没有**把`VerticalLongColumnShadow`接入实时TTS，尚不能宣称解决真竖排“读乱”；它只先保证所见预览与模型实际输入能够分别控制并核对。针对用户第5份USB 1920×1080横放刮痧宣传牌的原始图像，建议单独将“USB OCR输入方向”设为**向左270°**（屏幕USB预览角度不改变），随后分别进行手动诊断和正式朗读。之后若识字恢复、顺序仍乱，需验证竖排列序门控并接入实时TTS。
- CI快速方向独立性回归：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41780298333 成功，包含镜像开关正交、角度变换逆映射与原图尺寸；旧的phone preview、book reading、speech、decoder核验脚本已更新为新的显式输入角度接口，不再错误强制0°。
- **完整Android编译/离线模型/签名/公开APK发布已通过**：GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41780769705 = SUCCESS；release tag `x200-independent-preview-ocr-20261104-v46`，资产 `Kanzi_X200_PreviewOCRIndependent_v46_debug.apk`，大小**390416263** bytes，SHA256 **0bb28603cdcd0d7f5b82293c5c6a875734931a7526c218578a809f13a8425f07**，独立APK浏览器下载链接：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-independent-preview-ocr-20261104-v46/Kanzi_X200_PreviewOCRIndependent_v46_debug.apk
- 安装及验证：本包独立包名，不覆盖v43或v45，也不要求用户卸载旧版。Debug临时签名不保证未来同包名覆盖。首先验证USB预览吉他在右侧，再把USB OCR设向左90°；运行一次手动诊断，核查“本次实际OCR输入旋转=270°”和实际OCR输入1080×1920，再点正式开始识别。任何真机测试未发生之前，不要宣称用户朗读已修复，不要上传用户的私人诊断ZIP到GitHub。

#### 2026-11-04 v48 真竖排长列裁剪修复＋显式开启的实时朗读（已发布）

- 用户上传了第6份单帧诊断ZIP与一张清楚的“刮痧·中醫養生”真竖排宣传牌照片。**原始私有图像/逐字内容/ZIP、裁剪张量不得上传GitHub**。本地分析事实：原始帧1080×1920、OCR输入0°，13个框、DET约546ms、REC约848ms，质量档4线程/批2；5个正文长列中的3列已能正常识别，而另外2个极细长歪斜框原始REC仅得1字或短乱码。对这2个框求OpenCV minAreaRect，发现其上/下端X区间完全分离，gap约9px/4px，普通竖列X区间重叠（gap<=0）。其SDK上游QuadGeometry按X排序会把长轴错判成宽，造成裁剪倒向；这两框的校正应在**首次裁剪**完成，不需要再次整图OCR。
- 新实验分支 `test/x200-vertical-live-gated-v48`，基于v46预览/OCR真正独立版本，不覆盖正式包或之前测试版。保留全部摄像头预览镜像与USB、手机OCR方向设置，**不**通过旋转预览或全页来修复局部竖列。
- 对 `scripts/patch_x200_shadow_sdk.py` 增加严格首裁剪自动轴风险判断：SDK `QuadTextCrop` 仅在竖向跨度>=160、宽>=8、长宽比>=4、minAreaRect上下两点组X区间完全不重合且间隔>2px时改用Y分组顶点顺序，其余全部保留官方裁剪。DBNet和OCR模型权重不变。该项为零额外REC/DET设计，但**尚未真实手机逐字验收**，不能断言所有竖排错字都修好。
- 新增 `VerticalLongColumnLive.java`：只有用户主动勾选“真正竖排中文：按右→左逐列朗读（实验，默认关闭）”，至少4个邻接长列、足够长的中文正文、每列conf>=0.8、横纵覆盖/字符和编号完整性检查通过，且邻区不存在仍乱码的长竖列时才将OCR原文按右→左列序拼入**正式TTS**（非仅诊断）；不能凭上下文补字。小续字必须有严格坐标证据，若歧义则不并入，所有其他原框按原SDK顺序保留为单独片段。不满足任何条件即按原实时排序回退，正常横排模式默认不变。
- `MainActivity`保存独立竖排模式开关，变更后重置语音缓存；`PaddleOcrEngine`的diagnose与live都调用同一竖排门控，以便在诊断里看真实会被采纳的朗读文本/拒绝理由。`scripts/VerticalLongColumnLiveSmokeTest.java`只用匿名合成几何验证5列顺序、字符逐字完整、不重不漏、正常横排拒绝、未恢复残余竖列拒绝、歧义续字回退。私有图片未用于公开CI。
- **完整GitHub Actions Android工作流SUCCESS**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41784901771 。模型SHA、手机预览与音频/书页回归、竖排合成几何回归、无INTERNET权限、ARM64、APK v2签名、发行资产检查通过。
- **已核实GitHub Release真实上传资产**：tag `x200-vertical-live-gated-20261104-v48`，包名 `com.vobich.kanzi.x200verticallive`，versionCode48、versionName0.9.15，安装包 `Kanzi_X200_VerticalLiveGated_v48_debug.apk`，大小 **390416247 bytes**，sha256 `36eb691d838b7097d4ca80ccfa7cb6cb9232514d924beb5e8fec065b34191fb4`。可复制到浏览器直接下载：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-vertical-live-gated-20261104-v48/Kanzi_X200_VerticalLiveGated_v48_debug.apk
- **关键安装及手机测试**：是独立测试App，用户不必卸载v43/v46。打开“看字助手·竖排朗读实验”，保持正确实际OCR输入角度（第6份手机诊断原始0°已经识字，**不能按用户之前手动270°的UI设置擅自假定手机实时角度也正确**）。到设置中主动开启“真正竖排中文”实验开关；对准同一竖排宣传牌，先手动OCR诊断看列数/实际输入方向和“竖排实时朗读门控”，然后点“开始识别”听真实TTS是否按右→左。若仍拒绝请导出同帧诊断ZIP对照；横排建议关闭实验竖排模式。此阶段**没有完成用户手机实际朗读验收**，不要宣称已完全修好。
- Debug签名为CI临时签名：不承诺相同包名后续可直接覆盖；每次测试新包名和直接HTTPS链接必须验证后提供。

#### 2026-11-04 USB竖排误识别：零旋转的横向1920×1080帧

- 用户再次实测外接USB摄像头对同一竖排“刮痧”宣传牌，诊断帧**1920×1080、实际送入OCR的Bitmap=1920×1080、OCR方向0°**，13框但仅11框非空，平均conf=0.601；对应正文 #2~#6 为5条横向长框（x约1084~1725，y约329~593），逐框仅识别“中有”“换进期”“中在为部”“一”，框#4空，实时TTS乱读。DET461ms、REC890ms、总1355ms，热状态0。与手机摄像头0°输入1080×1920、竖排5列几乎全文识别/用户确认真实朗读顺序正确、conf0.943形成直接对照。
- **必须优先修复USB原始帧方向**，不是继续改竖排列序/TTS。USB画面预览镜像与真实送入OCR的bitmap仍是两个独立通道，不能用改变TextureView矩阵替代真正OCR输入旋转。当前自动整页方向候选报告“未触发”，因原版OCR本身误识字，后处理自动竖排分类也不可能恢复。诊断仅有日志，没有用户此次USB原图，方向很可能需要逆时针90°(270°)，但仍需对具体相机取景验证旋向。
- 当前v46/v48设置支持单独USB OCR输入旋转0/90/180/270，且保存于Prefs（不需要每次切换）。紧急复现建议USB OCR方向设270°并执行独立诊断，查看真正OCR输入Bitmap是否1080×1920、是否恢复正文；如果不对，试90°。手机摄像头方向和预览方向都保持不变。
- 用户明确希望如“豆芽看见”那样无需每次手动选择横竖。长期工程必须在**USB OCR输入层**新增低频、有预算的旋转方向探测/校准（对0/90/270的真实像素证据做对照，防止仅按1920×1080推断方向），为USB设备保存旋向并在静止场景复用，失败回退原始像素；然后再用横竖列几何选择阅读顺序，保持离线、控制CPU/温度、零增补字。v49正在运行的auto-vertical reading仅尝试在已有OCR文字框上决定横/竖排序，**没有解决错误的USB 0°输入**，不可将v49宣传成此故障已修复。

#### 2026-11-04 v50 USB真实OCR方向自动校准＋横竖朗读全自动（已发布）

- 用户进一步明确真正目标：不要每次在繁杂设置里手动选择手机/USB横竖方向。**同一外接USB相机的竖排宣传牌，在将“USB OCR输入方向”手动选“向左旋转90°/270°”后，用户确认能达到与手机摄像头基本相同的识别/朗读效果**；原始USB1920×1080、默认0°时OCR只有零碎错字，平均conf≈0.601；同一材料手机1080×1920、0°时可清楚识别5列，平均conf≈0.943。这证明自动方向修复必须进入**USB原始帧→OCR的bitmap**，而非只改预览TextureView或竖排列序。
- 从已成功发布的v49 `test/x200-auto-vertical-live-v49` 派生独立测试分支 `test/x200-usb-auto-calibration-v50`。v49已包含可信长竖列自动排序；v50新增真正**USB OCR方向自动校准**，并将繁杂的手机/USB手动OCR方向radio组折叠进高级设置，普通使用无需触碰。新包名 `com.vobich.kanzi.x200usbautocal`，versionCode50，versionName0.9.17，APP_VERSION0.9.17，旧v43/v48/v49不覆盖。
- 新增 `UsbAutoOcrOrientation.java`（纯Java、无Android/模型依赖）：`-1`为默认自动设置，若1920×1080景象原始0°的OCR产生至少3个长而横的文字框、每框仅1~8个OCR字符，低置信度破碎画面才启动额外真实OCR对照；先把该帧真实Bitmap旋转270°，如未可靠改善再试90°。必须候选真实识别出3列以上高可信长正文、字符量/加权置信度显著提升，或对横排的可读真实文本有足够显著改善，才接受候选；绝不根据文本上下文补字。自动结果每引擎会话缓存，普通扫描每帧仍1次OCR；缓存方向长时间连续失效才重置并重校准。温控>=2、内存压力或最近校准冷却期都会跳过额外推理，保留原版。
- `PaddleOcrEngine.kt`新增共享`recognizeUsbAutomatically`，单帧独立诊断和实时扫描**共享同一个USB方向缓存**，并按实际旋转后的Bitmap运行模型；报告列明“USB自动方向决策”和实际输入旋转角，ZIP证据保存原始NV21以及真正模型输入。**手机相机方向与显示预览不受此校准影响**。初次校准最多额外2次整图推理，后续仅1次，故不得宣称没有任何校准耗时；失败保留0°并冷却，不能保证所有相机自动成功。
- `MainActivity` USB OCR方向默认-1自动，手动保留0/90/180/270用于高级排障，手机/USB手动radio全部默认折叠；自动横竖排已沿用v49默认开启与严格几何证据门控。改变具体OCR方向不触发改变TextureView预览矩阵；USB图像左右镜像设置独立。
- 纯Java匿名测试 `scripts/UsbAutoOcrOrientationSmokeTest.java`：模拟5条长横错字，5条清晰正文列、正常横排，核验自动探测只在错向场景触发、真实OCR改善才采纳、无改善不采纳、冷却门控/角度设置正常。原有phone preview、book reading、text integrity、reading flow、离线Small/Tiny/v5模型校验均保留。未将用户照片或实际OCR文字上传公开GitHub。
- **完整CI已完成SUCCESS**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41789772592 ，确认安卓编译、模型SHA、无INTERNET权限、ARM64、APK v2签名、自动USB离线模拟回归、发布动作成功。
- **公开Release经GitHub API确认**：tag `x200-usb-full-auto-20261104-v50`，APK `Kanzi_X200_USBFullAuto_v50_debug.apk`，**390416255 bytes**，SHA256 `48437a416a4966ed60416915f6df817ea6b6391bdbefc9ab91ac8d647a7b5540`，直接浏览器下载：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-usb-full-auto-20261104-v50/Kanzi_X200_USBFullAuto_v50_debug.apk
- 真实测试：用户只需安装v50独立APP，确保USB“自动校准”默认选中，连接之前同一USB摄像头对准刮痧宣传牌、**无需手动选择270°**，点击开始识别和单帧诊断，查看`USB自动方向决策`是否显示采纳270°、实际Bitmap=1080×1920、OCR长列是否恢复，试听竖排。再测试正常横排材料无误判。构建成功不代表**用户手机自动校准已验收**。如果自动未触发/无法确认，先不要强行切换；发来新诊断数据核查。README/用户交接中不要宣传成已在手机成功自动识别。

#### 2026-11-04 v51 模块化阅读/中央目标优先（先公开素材实验再构建APK）

- 用户要求将看字助手的文字处理像医院科室那样清晰分类，每个模块有明确职责，避免一锅粥式迭代及额外算力；用户确认“镜头中央科室门牌优先朗读”，强调单列竖排（“耳鼻喉科”、甚至“眼科”两字）、多列传统竖排、普通横排、混排在一个界面自动运行、无需手动模式。明确工作顺序：**先写代码→自行寻找网上公开素材→完成针对性实验并报告结果→再生成测试APK**。所有数据须原文保真、离线、低发热。
- 分支 `test/x200-modular-reading-center-v51` 基于已成功发布的v50 USB真实OCR方向自动校准版本，仅改OCR推理**后的阅读策略**，不改USB/手机NV21输入、预览变换、OCR权重、温控和TTS队列。新纯Java职责模块：
  1. `TextRegionPartition.java`: 对已有文字框作O(n²)保守几何连通归区，横排段、竖排长列、竖排单字分组。每个区域维护源框ID/置信度/位置；不能删除原始OCR诊断框。
  2. `SingleVerticalFlow.java`: 对单框竖排OCR文本直接保留；对2~18个按竖直位置排列的高可信单字框，经过中心X、行距和汉字属性校验后，从上到下连接，原文不变。特别加入“眼科”**两字**边界回归（第一轮发现3字阈值漏报，已修复）。
  3. `CenterTargetTracker.java`: 对互不相连的区域按光学中心距离评分，保留上一目标轻度抖动的连续性；无法证明中心区域时不强行猜测。
  4. `ReadingStrategyRouter.java`: 优先选择通过原保守校验的整篇传统多列竖排；强横排长文维持原完整文字流；多独立门牌/标签场景只在**快速实时扫读**时选择中央完整区域；单列2/4字走单列模块；分区不稳回退v50算法。所有选中文字通过字符**多重集相等**校验确保既不增字也不漏字。诊断保留全框，选中不是删除其它OCR数据。
- `PaddleOcrEngine.kt` 快扫调用模块化路由和持久中央目标tracker，手动冻结帧诊断通过新的单独tracker展示首次快扫预计的“分区/中央目标/阅读策略/候选文本”，不干扰正在播报的焦点。BOOK长文路径仍原样保留。`MainActivity.java` 调整设置说明、保留v50 USB自动方向/预览独立行为。新App versionName0.9.18、versionCode51，独立debug包名`com.vobich.kanzi.x200modularcenter`。
- **在线真实公开素材**：HuggingFace `sdcb/simdpaddleocr-dataset-v1`（Apache-2.0），来源 https://huggingface.co/datasets/sdcb/simdpaddleocr-dataset-v1 ，取100张公开发布的合成OCR图像、1036条文字行与bbox/orientation标注。下载16.3MB parquet；对嵌入的每张JPEG按公开`sha256`逐张校验100/100通过；包含185条90°或-90°横侧文字行，分布在74张图。**注意：这些图片本身是公开发布的合成图片，不是真实医院手机拍摄图；这次使用官方完美GT行文本与坐标测的是阅读区域/排序策略，绝不声称是对这100张图做了真实PP-OCR模型端到端准确率测试。** Python导出`scripts/export_public_ocr_layout_cases.py`，Java实验`scripts/PublicOcrLayoutCorpusTest.java`。真实输出：`PUBLIC_LAYOUT_GROUND_TRUTH_VERIFIED images=100 lines=1036 rotated_lines=185`，`PUBLIC_ONLINE_LAYOUT=PASS images=100 gt_lines=1036 image_with_90deg_lines=74 center_focused=47 full_page=53 multi_vertical=0 multi_vertical_on_rotated=0 all_original_ids_preserved=true no_char_invention=true`。47/53是策略分流数量，不是正确率。
- **匿名合成几何场景回归**：`scripts/ModularReadingRoutingSmokeTest.java`，结果`MODULAR_READING_ROUTER=PASS assertions=29`，涵盖3个医院科室竖排（中央耳鼻喉科优先、左右内科/眼科不串读）；一OCR框整列；2字竖排眼科；正常横排7行不截断；混合横排302房号+竖排门牌；5列长文章按右到左全篇保留、无漏字重字；低置信度回退；镜头轻微晃动与明显移向新标牌。原v49自动竖排9项、竖排长列27项、竖排长列shadow34项均通过。
- **先实验后APK证据**：纯Java/公开图片政策专用workflow `.github/workflows/x200-modular-reading-policy-tests.yml` 首先SUCCESS https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41793542008 ，同时执行旧手机预览独立、USB输入方向、书籍阅读保护源码脚本。确认成功后才创建构建workflow `.github/workflows/x200-modular-reading-apk.yml`。完整Android独立APK工作流SUCCESS https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41793684142 ，JDK17、3个模型真实ONNX中文测试、SDK编译、手机摄像头预览回归、书页回归、签名/包名/ARM64/模型SHA256/无INTERNET权限审计通过。
- GitHub Release已通过API核实：tag `x200-modular-center-20261104-v51`，安装APK `Kanzi_X200_ModularCenter_v51_debug.apk`，大小 **390432643 bytes**，sha256 `88d6b55176630eeaf40e4c17480c84a9393ccf28e4b7dca83bfc0b9a6d7db678`，有效下载地址：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-modular-center-20261104-v51/Kanzi_X200_ModularCenter_v51_debug.apk
- **未做用户vivo真机新v51的实际拍照OCR/TTS、功耗与高温长时间实验；绝不宣布真机通过。** 建议用户安装独立v51后，保持默认快扫自动，先对准多个医院科室标牌的正中央，检查先读中央完整门牌、左右不插话，轻微手抖不跳目标；移动相机至另一个门牌检查切换。再测试普通横排整页，五列真竖排和USB自动270°朝向是否仍然正常。不必设置旋转或开启竖排模式。失败反馈一帧诊断即可按模块定位，绝不能擅自改OCR主模型。

#### 2026-11-04 v51真机价目表漏读：待独立表格策略验证

- **当前最紧急待解决问题**：用户安装v51（`com.vobich.kanzi.x200modularcenter`）后用**手机摄像头**对着「益康缘推拿」价目表，独立诊断：原始NV21=1080×1920、OCR=0°，ppocrv6_small，对照2线程/REC批1，检测36框/35个有字符，平均confidence=0.895，DET891ms、REC887ms、总1788ms、诊断1869ms，Android热状态1。大部分标题、各服务名称、价格、会员充值都被OCR正确识别。例如「价目表」「PRICELIST」「中医推拿一小时」「半小时」「精油开背50分钟」「肠胃调理50分钟」「肾部保养40分钟」「90元」「50元」「120元」「60元」「40元」「会员卡充值」「充1000送200」「充2000送600」「益康缘推拿」。
- 但**v51快速实时朗读中央优先严重截断**：`【分区/中央目标/阅读策略（冻结帧候选）】中央优先：23个独立区域；选中4/35个源框；CENTER_REGION`，输出仅 `刺经滑拔络络罐罐放刮走血痧罐敷`，没有项目/价格。其源框 #23~#26 分别是大致并列的**四条竖长框**：#23「刺经滑拔」、#24「络络罐罐」、#25「放刮走」、#26「血痧罐敷」，坐标大约x=410~585、y=923~1084，这些不是应按传统竖排四列连读的文章，而很可能是价目表中多个横排服务项目被OCR纵向裁进长条框。#30另有「热」。因此不应只对这个片段进行四框串读。
- **根据框坐标的未核实候选**是逐行组合：`刺络放血`、`经络刮痧`、`滑罐走罐`、`拔罐热敷`，对应右侧4行价格可能为120元、60元、60元、40元。但这是从OCR坐标推测的阅读顺序，**用户未提供对应原始照片以确认真实字序，不得当作已验证GT，更不能据此直接自动替换OCR文字**。
- 当前v51核心问题不在USB方向也不在TTS语音引擎，而在**阅读策略调度**：把密集价目表误当作23个彼此独立的区域，对中央4框选取，丢失整表其他内容。之前的100图公开资料策略实验是图像上已有GT文字框的布局测试，并不等于已覆盖价目表这种特殊混排结构。用户已被明确告知v51这个样本失败；不要把测试PASS误称为真机所有场景通过。
- **下一步建议（尚未获得用户明确修改授权）**：保留v51稳定基线；先增加独立 `TABLE/PRICE_LIST` 策略并构建真实价目表样本及人工核实的GT，再对策略模块安排「书籍完整正文、单列医院科室门牌、独立多门牌中央优先、5列传统竖排、价目表按行项目价格」的回归。只有证明表格判断可靠时才启用，且不得依赖文本联想补字；不强行将列框重排。**中央优先不能一概作用于所有页面**。用户倾向架构专业分工、少算力，不要把所有新增场景塞进一条大函数。
- 项目当前源码/安装包：v51分支 `test/x200-modular-reading-center-v51`；已成功构建的v51独立APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-modular-center-20261104-v51/Kanzi_X200_ModularCenter_v51_debug.apk 。此为**直接APK**，非ZIP；该版对价目表有明确不足。不可以继续宣称v51能正确读所有结构化表格。

#### 2026-11-04 — v52 极简全文阅读：先隔离实验、再构建独立测试 APK（GitHub 已核实）

- **用户明确授权范围：** 用户要求删除 v51 的中央目标优先和复杂场景选择，保留 USB 输入方向自动校准，只需完成横排左→右上→下、单列竖排上→下、传统多列竖排右→左；文字、数字、价格不能被策略丢弃。按「先实验给结果、再生成测试包」执行。本次没有改原 v51、没有新建仓库，也没有改 USB 方向算法、相机 NV21/预览、模型权重或温控。
- **隔离开发分支：** `test/x200-simple-full-reading-v52`，基于 v51 HEAD `57249fbcc2a770f0165fc95839c8e298e975d2f1`；本次最终 v52 HEAD `fda80c751901d317e19dbd65df925ebf3dd0f12c`。Android `versionName=0.9.19`、`versionCode=52`、独立 debug 包名 `com.vobich.kanzi.x200simpleflow`。
- **实际代码改动：** `ReadingStrategyRouter.java` 默认输出完整旧版上优先阅读流，不再选取中央部分；只有可信传统长列走原 `VerticalLongColumnLive`（全文），独立竖排标牌走 `SingleVerticalFlow`（多个标牌左→右逐列向下），混排/价目表走全文回退。全局对 OCR 文字做字符多重集一一相等校验，不通过则使用逐框原文字全文回退。独立标牌门控最多24框且必须相互足够分离，避免四条紧密竖长价目表误当成独立门牌。原 `PaddleOcrEngine.kt` 取消 `CenterTargetTracker` 实时持久跟踪及诊断调用参数，保留已有 USB 自动校准函数 `recognizeUsbAutomatically` 和现有 BOOK 模式。正式 `MainActivity.onRecognized` 不再调用 `OcrContextRefiner.refine(found.trim())`，默认播报原始 OCR 识别文字。数字/英文过滤仍为用户手动可选，默认关闭；不承诺文本排序一定能纠正上游 DBNet 跨行/竖向错误框。
- **纯 Java / USB 回归先于 APK 运行：** `X200 v52 minimal complete-text reading regression (NO APK)` GitHub Actions **SUCCESS**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41801650819 。真实日志包括 `SIMPLE_COMPLETE_READING=PASS assertions=56`、`AUTO_VERTICAL_HAN_READING=PASS checks=9`、`VERTICAL_LONG_COLUMN_LIVE=PASS checks=27`、`VERTICAL_LONG_COLUMN_SHADOW_SMOKE=PASS checks=34`、`USB_AUTO_OCR_DIRECTION=PASS checks=14`、`PREVIEW_AND_OCR_CHANNELS_INDEPENDENT=PASS checks=42`；书籍/手机预览/USB预览与真实OCR输入独立源码检查通过。实验包含「35框混排价目表完整保留」、数字价格、多个横竖标牌、两字眼科、五列传统竖排。以上是匿名合成 OCR 框/几何断言，不是真实手机 OCR 精度证明。
- **100图公开材料复核：** GitHub workflow 下载并核对 Apache-2.0 `sdcb/simdpaddleocr-dataset-v1` 发布合成图和标注，日志 `PUBLIC_LAYOUT_GROUND_TRUTH_VERIFIED images=100 lines=1036 rotated_lines=185`，以及 `PUBLIC_ONLINE_LAYOUT=PASS images=100 gt_lines=1036 image_with_90deg_lines=74 complete_pages=100 multi_vertical=0 multi_vertical_on_rotated=0 all_original_ids_preserved=true no_char_invention=true`。只验证已有 GT 框文字阅读策略**字符全集保留**，不是进行 PP-OCRv6 的真机拍摄端到端准确率测试，更不是 100% 识别正确率。
- **版本检查脚本兼容修正：** 原 `verify_phone_camera_preview.py`、`verify_book_reading_fast_path.py` 中固定的 0.9.18 版本断言改成 0.9.19，不删其预览和书页保护断言。部分中途 CI 因旧版本硬编码或旧 tracker 残留而失败，已经修复；不得以中途失败混淆最终结果。
- **完整 APK 构建与验证：** GitHub Actions **SUCCESS** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41802124611 ，包括 3 OCR ONNX 实际中文测试/字典校验、Paddle SDK Android 编译、手机与书页回归、USB与竖排回归、独立包名和 versionCode52、无 INTERNET 权限、ARM64 模型 SHA256、APK Debug v2 签名及 Release 上传。CI 不是用户 vivo 真机读字/功耗验证。
- **真实 GitHub Release 上传后经 API 再次核实：** tag `x200-simple-full-20261104-v52`，APK `Kanzi_X200_SimpleFull_v52_debug.apk`，大小 **390432639 bytes**，SHA256 `fb9b108ba2ddf048066e5c401f406cb31b3834fc01a88b541b288af4c0926dd1`。**直接 HTTPS APK 下载：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-simple-full-20261104-v52/Kanzi_X200_SimpleFull_v52_debug.apk ；发布页 https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-simple-full-20261104-v52 。v51 原包独立保留；CI debug 签名临时，不承诺未来同包名直接覆盖。
- **尚未完成/不能宣称：** vivo X200 Pro 真机 USB 0°→270° 自动方向校准是否在该相机所有横竖场景成功；真实价目表项目与价格是否同行精确排序；大批量错竖框能否从上游 REC 中恢复；长时间发热和实时 TTS 完整度。下一步用户装 v52 用同一 USB 摄像头真实竖排宣传牌（默认自动，不手选270°）做单帧诊断，观察 `USB自动方向决策`、实际 OCR Bitmap 旋转与逐框原文；再用价目表、眼科/耳鼻喉科多个牌子、普通横排与多列竖排确认实读。若有误差，只上传必要的匿名日志；未经用户授权不要上传私有原图或猜测字符。

#### 2026-11-04 — v53 USB 默认自动0°缓存误判修复 + 完整横排行排序（已发布，待真机验收）

- **用户授权与实际输入：** 在 v52 实测后，用户明确要求先补充 USB 同场景数据再综合修复，并提供两份 **默认设置** 的外接 USB 相机原始诊断 ZIP（#9 价目表、#10 传统竖排「刮痧」海报）。用户没有手动改 USB OCR 旋转。私有照片、NV21、识别张量、逐字细节与 ZIP **只在私有会话中本地核查，未上传到公开仓库**。
- **真实 USB 证据（不可当作新 v53 验收）：** 两份原始画面都为 1920×1080 的横置图，原始帧和真实 OCR 输入 Bitmap 均 1920×1080、实际旋转 **0°**，均记录 `USB自动方向：复用已校准0°（本帧仅1次OCR）`。USB #9 有29个检测框（DET446ms、REC607ms），错向0°仍识别133字符；本地按 v53 新几何判据重放，发现15条可信的横置短长框、0条原版异常宽碎框，原 v52 `needsProbe` 未触发。USB #10 有11个框（DET429ms、REC893ms），原始0°只有38 OCR字符，5条超宽短字框且主体错乱；原 v52 继续复用已缓存的错误0°，无同帧重校准。两张原始帧仅做**本地物理旋转预览**，逆时针90°（模型角270°）后文字恢复视觉正向；**没有在上传的两张 USB 图片上离线运行真实 Android 270° OCR 结果对照，因此不能宣称 v53 已完成 USB 真机验收**。
- **根因经源码和上传 ZIP 联合确认：** `PaddleOcrEngine.kt` 在初帧0°文本可读但未被判成超宽碎字时，将 `usbAutoResolvedAngle=0` 视为成功缓存；后来切换到真正竖排宣传牌仍走 cached≥0 的早返回，只检查是否需要以后重新校准，未对当前帧运行270°候选。且 `UsbAutoOcrOrientation.quality` 原策略只看3条以上超宽短乱码，忽略了 **错向0°下 OCR 仍高置信读出文本、但几何方向横置**的情况。
- **v53 隔离分支：** `test/x200-usb-zero-cache-fix-v53`，基于 v52 HEAD `fda80c751901d317e19dbd65df925ebf3dd0f12c`，当前 HEAD `c63737365ed648fcaae935a53469bd039215ca6e`（在发布后另修复原构建工作流的 README/Release heredoc 换行错误，提交 `c63737365ed648fcaae935a53469bd039215ca6e`）。App `versionCode=53`，`versionName=0.9.20`，独立包名 `com.vobich.kanzi.x200usbdirection53`，不覆盖 v52。
- **源码实改：** `UsbAutoOcrOrientation.java` 新增低成本 `sidewaysTallRows/uprightWideRows` 证据；当 USB 原始横幅里出现至少6个“文字可读但高瘦横置短列”或至少3个异常超宽碎字框时，触发真实像素方向候选。`PaddleOcrEngine.kt` 将**0°视为临时默认、不是永久锁定**；每帧仅复查已有 OCR 框几何，只有疑似横置、且温度/内存及20秒冷却准许时，使用同一原始帧额外执行 **270° 优先、90° 备选**的真实本地 OCR；仅在结果字符/置信度/可读行几何足够改善时采纳并缓存可信非零角度，正常后续每帧仍只跑1次 OCR。若证据不够，保留原始文字与0°，不凭上下文猜字；非零缓存持续异常时才允许低频复测。手机相机预览、USB预览矩阵、NV21原始字节、OCR权重、温控/TTS不动。
- **横排小范围修复：** 在 `ReadingStrategyRouter.java` 增加纯已有框的行排序 `horizontalRows`，仅当6个以上可信横排框时把所有现有源框按 y 分行、同行按 x 左→右，防止整张价目表价格先于服务名；一律逐字符全集校验，失败回退旧全文文字流。它**不会**把 DBNet 已经跨3行错检成一条纵向框的汉字自动拆出，也无法补还从没检出的汉字。
- **用户私有证据本地重放（只验证触发条件）：** v53 几何政策对 USB #9（29框，15条横置短列）、USB #10（11框，5条错误宽框）均判定应启动方向对照；旧 v52 #9 根本没有触发。使用本地 Python 读取 ZIP 做几何重放，**没有将用户私有 OCR 文本用于公开 CI**。
- **实验先于 APK：** GitHub Actions `X200 v53 USB zero-cache direction and full-text regression (NO APK)` SUCCESS https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41807523022 ；实际日志 `SIMPLE_COMPLETE_READING=PASS assertions=57`，`USB_AUTO_OCR_DIRECTION=PASS checks=25`，`PREVIEW_AND_OCR_CHANNELS_INDEPENDENT=PASS checks=42`，竖排原有9/27/34项回归通过；100张公开 Apache-2.0 合成图的1036条GT框字符完整保留。以上都是源码/纯 Java/公开已标注布局检验，不是 vivo 对用户原图真正旋转后的 OCR 验收。
- **独立 Android APK 构建：** GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41807827975 **SUCCESS**，source commit `1e26b6d3b2d21793c73878608a9cc7ff0dd1d42c`；Gradle 编译、三套模型真实ONNX中文测试和字典校验、书页和手机预览、USB和竖排模拟、无INTERNET权限、ARM64、Small模型SHA256、Android debug v2签名、APK独立包名与versionCode53均通过。该 run 的 APK Artifact ID **13310743009**（预计2026-11-14到期），原工作流生成时存在 heredoc 结束符换行错误，导致 `Release` 步被 shell 当成文本吞掉而未真正上传；随后改为从**同一已验签的 run artifact**单独发布，并修复原工作流便于未来复现。不能把最初 build workflow 的 Release step 名字为 SUCCESS 等同于实际曾发布。
- **实际已验收 APK 发布：** 轻量发布 Actions SUCCESS https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41808806240 ，读取并校验上一个已签名成功的构建 Artifact，再建立 `x200-usb-direction-20261104-v53` Release，GitHub API 确认 APK `Kanzi_X200_USBAutoFull_v53_debug.apk`，大小 **390432651 bytes**，SHA256 `752ac4ec0cfb933519f4eac3be4877631c2b78d6d029fbeb25e1f32be58dd93c`，直接 HTTPS 文件地址：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-usb-direction-20261104-v53/Kanzi_X200_USBAutoFull_v53_debug.apk ，发布页 https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-usb-direction-20261104-v53 。发布资产以此 API 结果为准。
- **下一轮真机验收：** 安装 v53 独立包，USB设置保持 **自动-1**，对准同两张材料分别启动实时识别＋单帧诊断：观察 `USB自动方向：真实旋转OCR校验通过，采用270°` 是否出现、`真正OCR输入旋转=270°`、`模型输入Bitmap=1080×1920`，且校准后每帧仅1次 OCR；同时查看价目表服务名-价格同行读序、传统竖排5列右→左、所有汉字/数字与实际 TTS。若 270°候选被拒绝，先提取原因和诊断再定是否微调门控，不强行旋转。对正常横排USB/手机做防误判回归，观察连续数分钟发热。
- **剩余问题明确保留：** 先前手机价目表上游检测跨行错框、手机竖排末尾漏字和标题/正文顺序、真实USB校准接受率与识别耗时/温度 **都未宣称已修复**。当前版本优先修 USB 方向与横排行文字/数字顺序，严格不补造 OCR 字。原用户真实照片与 ZIP 不公开。

#### 2026-11-04 — v54 安静稳定朗读（已发布 APK，仍待 vivo 真机验收）

- **原因：** 用户报告 v53 和此前版本在室外/USB 实时识别时不断重读、语音被打断、屏幕 OCR 频繁更新和反复发出「嘟嘟」提示。源码定位到 `MainActivity.queueSpeechText` 对 OCR 波动执行 `clearSpeechQueue(true)` 并用 `ToneGenerator.TONE_PROP_BEEP` 发声，旧去重策略不能防止少量文字变化导致播报抢占。室外样本置信度约0.720，不到原来0.75稳定阈值。
- **隔离代码：** `test/x200-quiet-speech-stability-v54`，从 v53 `c63737365ed648fcaae935a53469bd039215ca6e` 创建。最终构建源码 commit `6f32b2bd9476ea9206b52c113fb1d95ab21bfb50`。版本 `0.9.21` / versionCode54，独立包 `com.vobich.kanzi.x200quietspeech54`，不覆盖v53。
- **已修：** 去掉识别成功提示音；自动实时朗读 `speechPageActive` 时不再运行完整OCR及刷新OCR框，摄像头视频仍正常显示；新目标需要场景变化及两次相近OCR确认，且未读完的TTS绝不被自动新扫描打断，显式手动操作仍可停止或重播；静止有效文本置信度抑制阈值0.75降到0.55；实时自动扫描不插播语音质量提示、默认关闭质量语音；离线TTS回调主线程串行；UI文字改成「本段读完再更新」。**未改**USB输入自动270°/90°校准、OCR模型、横/竖排排序、原文保真/无联网约束。未引入未验收的独立阅读排序审计分支。
- **通过测试：** 完整模拟帧静止3分钟+中途切换的 `QUIET_SPEECH_STABILITY=PASS checks=215 auto_interruptions=0 beeps=0`，含源码断言；原竖排、USB方向及书页回归通过，100张公开合成图1036条GT文字完整。最终源代码独立CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41816558361 成功。以上是**模拟数据与源码测试，非真机连续3分钟及系统音频测试**。
- **Android成功：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41817127142 ，经过Android编译、ARM64、模型SHA、无INTERNET权限、APK Debug v2签名及GitHub Release实际上传的完整成功记录。前两次APK run 41816405390/41816558358因构建脚本README heredoc多余`TXT`导致审计后失败，已修正，不能将它们当成功发布。
- **已核实APK：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-quiet-speech-20261104-v54/Kanzi_X200_QuietSpeech_v54_debug.apk ，大小390432655 bytes，SHA256 `7278ee0e25e727bbe6d19fe61acee4b888d0979aa3a2d27952eb39dcc6c8f703`，Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-quiet-speech-20261104-v54 。
- **实际待验收：** vivo X200 Pro 手机及外置USB对同一牌静止3分钟，确认无嘟声、语音不中途重启、不重复朗读；换新牌后先读完旧目标再读新目标；USB自动方向保持默认并核对真实角度、速度、温度。新独立包默认PP-OCRv6 Small；与旧Tiny对比须切换相同模型。仍不能保证所有USB场景自动270°成功、错误OCR框被拆开或漏字得到恢复。


#### 2026-11-04 — 极简同帧真实Small 0°/左90°实验 APK（已发布，未做vivo真机准确率验收）

- 用户要求简化OCR技术路线，不继续围绕不清楚小字无限增加补救。此前公开12张自然图同组A/B（Run 41835799303）显示0°在两向都匹配的27区域/124字符上CER7.26%，左转90° CER70.97%；公开竖排8页（Run 41836447600）在两向都匹配的333区域/9310字符上0° CER15.75%，左转90° CER16.58%。这些是Linux公开素材真Small DET+REC对照，**不是用户手机最终CER**，竖排场景不能默认整页旋转。
- 为真正落实「摄像头同一帧→保持正确比例→固定Small只识别→比较原文」实现独立分支 `test/x200-simple-same-frame-phone-ab-20261104`，构建源commit `4fa45fecdeb04023c19ae83fe699c136ee8802a6`，从v54源码fork，v54本身不变。包名 `com.vobich.kanzi.x200simpleorientationab`，v0.1.0/Code1与v54共存。
- 实验版在设置/诊断新增用户手动按钮「实验：同一画面原向0°与左转90°真实OCR对照」。同一NV21帧解码1次，`ppocrv6_small`先0°完整DET+REC，然后同帧向左旋转90°再完整DET+REC，分别输出原始识别文字、框数量、信心分、检测/识别耗时和温控概况。彻底绕开自动定向、版面AI、二次ROI、正文补救与TTS，**不自动根据置信度决定正确角度**；正式实时播放逻辑仍沿v54。
- GitHub Actions完整成功 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41839423818 ，Android assembleDebug成功、模型SHA、签名v2、离线无INTERNET和独立包名审计完成；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-simple-ocr-angle-ab-20261104-experiment/Kanzi_X200_SameFrameOCRAB_experiment.apk ，大小390432655 bytes，SHA256 `0b5e412f963f4e66e9ee8d0310e5e1793f611cceca57c59b40161161e65a14e2`。更完整报告见隔离分支 `docs/x200-simple-sameframe-android-ab-20261104.md`。
- **未验收**：vivo X200 Pro上识别率、0/左90方向差异、真实字级人工GT、热稳定和TTS。当前手动极简按钮不自动导出同一帧图片ZIP，不能把不同时间的普通诊断ZIP冒充精确同帧比较；若需要严格CER还须人工对照真实照片。不能将此诊断实验叫做v55正式识别提升。


#### 2026-11-04 — 裁切及检测框专项：真机原始像素Small重识别+公开A/B（正式版未动）

- 用户重新确定技术优先级：不再围绕不清楚小字无限添加补救，优先区分“检测框把多横行合成一竖框”与“框已正确但实际ROI裁切错轴”的原因。不能把裁切叫做已证实的唯一瓶颈。
- 公开数据单纯扩框验证：隔离分支 test/x200-single-crop-geometry-ab-20261104，固定36张真实公开图片、122个匹配GT区域（690字）、同Small DET/REC，原裁切111编辑错误／CER16.087%，四周各扩大5%后115错误／CER16.667%；6好、7坏、109不变，简单扩框失败。真正推理CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41850767307 SUCCESS。公开证据见 docs/x200_crop_margin_paired_real_public_20261104.md，原图与用户私人文字完全未上传。
- 私人原始手机证据只在本地运行：九份单帧ZIP共188个真实SDK文字框，检测出1帧的2个跨横行误合并竖长框；其余8帧探索规则未触发。第7份价目表这2个误合并导致4行、16字的阅读顺序混乱，原始大部分字形已认对。对原始SDK的12个选定相关框按SDK框顺序串接，人工GT16字的编辑距离7；基于该页短框邻近行对齐自动生成4条正确横行ROI，同一官方Small REC重新推理，4行全部正确／16字编辑距离0。这是很重要的局部因果证据，但不是整页CER，不等于正式APP修复。
- 对九份188个SDK已经导出的真实REC float32张量在Python ONNX Runtime 1.23.2重放，与Android保存输出完全一致176个；异常框旧结果的重放一致。Python运行库与Android 1.21.1不完全等价，不能主张手机平台相同性绝对成立。候选规则参数参考该价目表设置，另8帧不触发不能替代独立留出集，也没有验证长竖列的所有反例。
- 用户私有完整本地报告及复现文件：/mnt/data/kanzi_crop_causality_real_phone_20261104.zip（含私有文字和示例局部裁切、Python代码，不含权重和原始整帧）；禁止发布至GitHub公共repo。会话本地路径非永久公网文件链接。
- 研发下一步：只围绕“同一帧检测框误跨行→一次性正确横行裁切→同Small REC”的单一实验做独立真机A/B，不能以单页成功添加多套复杂补救。尚未接入 Android 正式OCR、版面排序或TTS，未发布v55；正式v54独立保持不动。


#### 2026-11-04 — 自动分行首遍裁切：独立真实APP构建已成功，等待用户vivo X200 Pro真机验收

- 用户确认在其他图片复测后直接生成可用App，不再堆叠多轮OCR补救。新隔离分支 test/x200-firstpass-autoline-ab-20261104 从v54 commit 6f32b2bd9476ea9206b52c113fb1d95ab21bfb50 派生，完全不改v54。
- 从用户之前9份单帧ZIP共188个真实OCR框复核：第7份价目表中2个检测竖长框误跨横行，几何规则将13个跨行或碎片框替换为4条完整横行，单帧47→38框；此前同一Small REC重识别目标16个原文字符全部正确（原拼接错误编辑7→新0），其余8份未触发。样本是已知正例，不是盲测独立正例。
- 真正运行44张**新公开图片**的固定Small DET：36张自然场景 +8张传统竖排，合计548个检测框，首遍规则0次触发。CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41855459247 SUCCESS；只证明该批无误触发，不证明批量识别率提升。与Android SDK并非像素级逐字节一致。此前简单扩框5%的负试验CER16.09%→16.67%，故未采用。
- Android新增功能：在官方SDK原始检测框之后、Small首次识别之前执行 CrossRowFirstPass.repartition()，只在“两个相距足够远的短竖长误框 +3–8条可信横行锚点”唯一确定时重建横行ROI，歧义则原框不动；没有二次全页DET/REC、语义猜字或模型替换。实验App默认开，可在设置→OCR诊断点击“自动分行实验：开启/关闭”当场恢复原版框，诊断报告有“首遍自动分行状态”。
- 独立Android Build https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41855699826 SUCCESS，包括Android Gradle编译、APK签名v2、ARM64、官方Small模型SHA、未声明INTERNET权限与独立包名审计。
- 已发布独立实验APK：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-firstpass-auto-row-20261104-experimental/Kanzi_X200_AutoRow_FirstPass_AB_experimental.apk ，390449039 bytes，SHA256 c4c692f0a5397391f257dc50fc75e7e7cc76dccaebf5655cb34d999ff9a51ffb。独立包名 com.vobich.kanzi.x200autolineab、v0.1.0/versionCode1；不覆盖v54。详尽实验报告见隔离分支 docs/x200-firstpass-autoline-actual-experiment-20261104.md。
- **尚未完成**：用户vivo X200 Pro实机安装后真正横排错框的触发情况、完整主体文字字级CER、真实传统竖排零误伤、语音连续性、延迟/发热。未通过这些就不能声称识别率提升或当正式v55。由于实验开关手动切换后是两次不同帧，不能把它冒充严格同帧A/B。私有照片/ZIP/NV21均没有进入GitHub公开仓库。


#### 2026-11-04 — 清晰短文字＋完整文本行筛选：真正Small REC模糊实验（本地完成，未修改APK）

- 用户确定实时文字新产品边界：主要识别清晰、较短文字；水平阅读从左到右、从上到下；跳过模糊和太小内容，但要求**完整词句与名称/价格成组保留**，不能逐框删字再把碎片拼读。整页书页未来另设独立模式。
- 已使用此前9份真实NV21诊断ZIP重放既有OCR结果，非重新DET/REC。按场景几何和完整行门控，9帧中3帧存在可播的可信短横排文字，6帧暂缓；混合Small和Tiny模型口径总1065原OCR字符中只读232，**不能将输出减少等同于CER下降**，且覆盖面过小，不应直接上线。固定两帧价目表10项人工核对的名称＋价格完整对：跨行错裁帧#7保留5/10项、正常框帧#12保留10/10项。所有丢弃行原OCR仍保存在源ZIP。
- 进一步使用与手机相同SHA的真实PP-OCRv6 Small REC，针对帧#12已经人工核对的10个名称与对应价格共89字符，固定视觉ROI，在原像素基础上添加不同程度**合成Gaussian blur**，每个完整名称＋价格单元按最小识别置信度>=0.90整组保留或整组暂缓（0.90为探索阈值，不是已校准生产阈值）。结果sigma0原识别0/89错、保留10/10；sigma1.3为1/89错、保留10/10且筛选没改善；sigma2.3为2/89错、保留10/10且筛选没改善；**sigma3.5**原识别6/89错（CER6.74%），筛选后只播9/10项、82/89字、2处错误（所播CER2.44%）。这是强模糊下**选择性播报错误率**减少，不是Small模型原始CER提高；因删掉一整项，原文字数覆盖下降至92.13%。
- 必须记下失败例：模型在较高置信度时仍把价格60元识别为20元、40元识别为60元，因此高置信度≠正确答案；无法承诺绝对准确金额。帧#7的4条清晰行，在人工强高斯模糊sigma3.5下Small模型出现9/16编辑错误，而0.90门槛将全部暂缓（覆盖0），说明阈值过严也可能严重漏读。
- 本轮所有私人图片、10项价目/价格逐行GT、模型输出及完整实验脚本**只在会话容器**，保存在 /mnt/data/kanzi_short_text_experiment_20261104.zip；不得上传公开repo。两份价目表属于同一块招牌的不同真实帧，合成模糊条件不是独立真实拍摄场景；不能外推总体准确率及视障人士真实使用。
- 下一阶段正确路线：①首遍检测框跨行修复（前期已证明有修正机会）；②按完整行、完整名称＋金额做可解释质量门控；③低质量整组不读时提示调距离/重新对焦，不能猜字；④收集独立清晰短文字/复杂反例评估整句正确率、原文覆盖率、漏读、延迟、发热。**尚未改v54或自动分行APK，也没有发布新版本。**

 
#### 2026-11-05 — 用户指定极简整行框替换实验，豆芽看见样式为目标

- 用户新的直接要求：按截图中宽而连续的白色横行框，不再叠加修复系统。相似Y位置、字体高度接近、从左到右间距较小的碎框合并为完整横行框，**参与合并的旧框直接删除**；在首次OCR识别前用同一源图重裁切，Small只识别一次。上下明显有行距则分开。正常横排短文字是重点，不追求完美、不解决每种异常。
- 最新**独立实验分支** test/x200-simple-row-replace-20261105，从旧v54系的隔离自动分行测试版派生，把原来狭窄的跨行错框专项规则**整个替换**为单纯水平行合并。原稳定v54代码和APK不变。源文件：scripts/crossrow_sdk/CrossRowFirstPass.kt；SDK patch仍在scripts/patch_x200_firstpass_rows.py，运行路径是官方SDK DET之后、Small REC之前；app/src/main/java/com/vobichngan/kanzi/OcrOverlayView.java 在新规则开启时用真正的模型结果框绘制更醒目的白框；在设置→OCR诊断中按钮「横行合并：开启/关闭」可立即恢复原始检测框。
- A0原框、A1仅合并展示、A2合并后真实新ROI+同Small REC，在47张最近获取的公开医院/超市/店铺/菜单照片和9张原用户vivo实际保存单帧上做了本地复现。第一轮左右可连距离设为1.65倍字体高度，37个事先已标注短语从24正确掉到19正确，是**失败**；随后**改为0.65倍邻近文字高度**，47张共910个原框→815个新框、合并组72，37个GT短语依然24条整句完整（**没有净提升**）。9份私人真机原SDK188框→172框，合并9组，出现「艾灸养生」「滑罐走罐」「拔罐热敷」等局部重新识别成功的案例。用户私人原图未上传GitHub。
- 相同Small REC在Linux CPU ONNX Runtime 1.23.2, 2线程、9份真实手机SDK ROI，4轮配对中位数合计，原版2756.1ms、0.65合并后2791.2ms（约+1.3%）。这**不是vivo真机耗时，也没有证明加速**。
- 此0.65阈值在47张数据上调整得来，属于探索性参数；需另外真机独立场景盲测，且已见未标注多列菜单还有潜在跨项目误并。公开图旧DEΤ原数据保存为AABB，新ROI也是AABB，与Android透视裁切不完全同构。原SDK188条ROI中的169条以Linux重新识别复现安卓文字，绝不能宣称完全平台等价。
- 私有完整实验本地包：/mnt/data/kanzi_simple_full_line_replace_20261105.zip；报告/PNG: /mnt/data/kanzi_simple_row_experiment_20261105/实验报告.md、价目表_一眼看合并效果.png。保留A0/A1/A2三路真实裁切图片、所有数值CSV和脚本。
- 独立实验APK构建已由分支推送触发，**最新CI** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41879102322 （本条写入时仍排队，不能声称成功或提供假APK下载链接）。预定独立包名 com.vobich.kanzi.x200simplelinemerge、标签 x200-simple-line-merge-20261105-experimental。实际验证成功并出现GitHub release asset之前，不能声明新APK已交付。
- 以后注意：不要把「框漂亮」等同于准确率提升，不要引入更多补救层；用户明确优先真实完整行、删旧框避免重复、原文保真、离线、朗读顺序简单、速度实际测量。

#### 2026-11-05 — ChineseOCR-Lite 既有文字检测实验数据与真图专项复核（只读、无模型重跑/构建）

- **用户批准范围：** 先核查 GitHub 已保存的 ChineseOCR-Lite/Small/CRAFT 实验逐框数据和可视叠图，查明漏框、粘连、碎框并判断是否值得进入 vivo X200 Pro 研究测试。本轮**没有修改 App/模型代码、没有新建分支、没有触发模型推理或 GitHub Actions 构建**；本提交只追加交接记录。
- **既有证据与提交锚点：** ChineseOCR-Lite/CRAFT/FAST 独立检测实验分支 `test/x200-independent-text-det-fast-craft-chinese-20261105`，HEAD `f609c9f96da4e6a82cb890ed8dca0483c7ac0a39`，原有成功 Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41889278847 ，Artifact `13351714460`；Small/Tiny/v5/EAST 分支 `test/x200-det4-independent-regions-20261105`，HEAD `88cc2b0843c0e4a091c1a8e589160475a62f580d`，原有成功 Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41886566888 ，Artifact `13351856793`。使用以前已有的47张公开实拍原图 Artifact `13343397779`（Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41869103633），没有使用或上传用户私人照片。
- **评分边界：** 同源47张照片，但37个手工近似矩形GT仅分布在15张图；只评测检测区域，不含Small REC和TTS。完整单框门槛=覆盖GT宽≥80%、高≥60%且IoU≥0.40；最低命中门槛较宽。原始 CSV 交叉复核结果：ChineseOCR-Lite命中37/37、完整30/37、与同GT相交≥2个检测框的目标5/37；Small分别为37/37、32/37、4/37。双方都完整29、仅CN-Lite完整1（supermarket_02 GT#8）、仅Small完整3（supermarket_03 GT#12/#13、storefront_11 GT#29）、双方都不完整4。CN-Lite未完整7处：supermarket_02 #9；supermarket_03 #12/#13/#14/#15；storefront_10 #26；storefront_11 #29。**未完整不等于没检测到**。
- **47图总体检测框数量：** CN-Lite 751、Small 1634。不可凭此判断谁的误检率或漏检率低，因为未对47图全部文字做标注。CN-Lite在本Linux CI中的检测耗时中位109.42ms（3.64MB权重）；Small另一个Linux CI为398.92ms（9.88MB权重）；**不是同一GitHub runner/相同输入尺寸，不能直接宣称实际手机加速倍数**（CN-Lite短边640，Small长边1600）。
- **实图反例1：跨行合框。** `restaurant_menu_12` 的 ChineseOCR-Lite 检测框 `[735,261,1007,699]`（原图1920×1394像素）把左侧相邻的多个`$50`价格行框成一个竖长框；Small对应区域逐行划框。原37个GT未覆盖此连续价格列，说明评分可能漏掉关键失败。
- **实图反例2：整行碎框。** `storefront_10` 「宏碁專售店」招牌，Small在中文正文处画1个长横框（另有logo框），ChineseOCR-Lite将中文划成3个相互交叠的框。该GT粗标矩形延伸至明显空白，导致简单IoU完整得分也可能对两者不公平；不能把分数当作精确真实行检测率。
- **实图反例3：价格行粘连两模型都存在。** `supermarket_03` 中CN-Lite大框把「售價/$570」「會員價/$540」上下文字连在一起，Small也出现跨行大框；不能把问题描述成CN-Lite独有，或断言Small已完全解决。
- **实图反例4：`storefront_11`** 小字号「加盟店」等区域上CN-Lite部分框劣于Small；大招牌两者都能找到。检查了旧实验叠图，并用保存的原图、`all_detector_boxes.csv`/`all_boxes.csv` 和37个冻结GT复原独立对照图，**绘图不是重新执行模型**。
- **新发现的门槛：** 上游 ChineseOCR-Lite 项目 https://github.com/DayBreak-u/chineseocr_lite 标示GPL-2.0；模型权重再分发条件没有单独核实。正式或公开研究APK打包第三方代码/权重之前必须先评估许可适用性，不可忽略。
- **阶段判断：** CN-Lite可作为更轻检测器的**条件性研究候选**，但未证明优于Small完整单行框，更未通过vivo X200 Pro实测。当前不替换正式Small/DBNet、不部署影子模型、不发布APK。若用户下一步明确授权，优先做隔离同帧只检测真机A/B（含预处理/推理/后处理分段耗时、误跨行、碎框和发热），用有完整单行GT的独立中文菜单/招牌/价签材料，之后才考虑固定Small REC的裁切配对。
- **原始材料位置：** 上述三个GitHub Actions artifact与源码分支，已有CSV和图像；此次复核在会话本地保存文字审计报告及4张由原始检测框重画的对照图，不将大型公开图片复制进`main`，更不上传私人诊断ZIP。

**保留原判断：CRAFT历史性能测试、Small REC联调以及极简整行白框独立APK均已存在，勿重复旧实验或把此只读复核称为真机模型验证。**

#### 2026-11-05 — Small DET vs ChineseOCR-Lite DET 独立同帧安卓实验 APK 构建及发布完成（真机待验收）

- **用户授权范围：** 在原 GitHub 仓库内沿用已验证独立 CRAFT Phone Lab 工程，隔离开发同一帧 Small DET / ChineseOCR-Lite DET 划框对照与真机性能、热状态记录。正式看字助手主程序、Small/DBNet 基线、REC、TTS、已有 CRAFT 实验全部保留，**没有重复此前 CRAFT 6×24 性能测试**。本轮成功的是 APK 构建及静态审计，不是 vivo 真机运行验收。
- **分支及代码锚点：** test/x200-cn-lite-small-sameframe-phone-det-20261105；起自旧独立 lab 源码 commit 2fe662dd83c82b74a9021bff0d6ee9aee57fcbfa。实际 APK 对应 commit 9a9b531ae252f35771c070587fd842022624692e。只新增独立检测 Java 引擎 DetCompareEngine.java 和 DetCompareActivity.java，在 craft768-int8-probe 中改实验包名/manifest/Gradle、加入独立 workflow 和文档；不修改正式根 app/src/main。独立包名 com.vobich.kanzi.detcompare，versionCode=2，versionName=0.2.0-same-frame-det-only。
- **模型与 SHA：** Small DET 9,880,512 bytes，SHA256 d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e；ChineseOCR-Lite DET 3,637,234 bytes，SHA256 ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867；上游 CN-Lite 固定 commit 54a3df53af823f3bffdde9393d0c021b4f6d94cd。两模型在 CI 下载并验证 SHA，仅打包进独立 APK；模型二进制未提交到 Git。ONNX Runtime Android 1.21.1 + OpenCV Android 4.10.0，ARM64，两 CPU 线程。
- **真机实验操作：** ①加载两模型并校验 SHA；②系统相机拍照或③相册选取一张图片；同一张已解码及 EXIF 校正的冻结 Bitmap 同时输入两模型；④单次划框，Small 红框、CN-Lite 绿框；⑤12对交替运行、各预热1次，记录预处理/纯推理/后处理/总耗时 P50/P95、PSS、电池温度及 Android 热状态；⑥用户主动导出匿名逐框坐标与时间 JSON，不含文字、照片或本地路径，不自动上传。无 INTERNET 权限、没有识字和 TTS。
- **重要边界：** 这是手机上的静态**单帧配对**，不是连续 Camera2/UVC 实时视频帧率，无法直接代表长时间热稳定、真实 TTS 首字延迟。Small 保留最长边1600原生输入、CN-Lite保留短边640原生输入，非等输入分辨率比较；Android 后处理把 Python PyClipper rounded unclip 以旋转矩形解析扩边近似实现，真实输出与 Linux 原版未进行逐像素等价验收；没有用户真机逐行 GT，不能凭框数或 CI 就宣称某模型更准。
- **第一次 CI（中间提交态）失败**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41897790545 ，因 README 尚未提交、依赖检查未满足，不是模型运行/编译真实性失败。**最终完整独立 Android CI 构建及静态审核成功**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41897878359 ，确认 Gradle assembleDebug、双 ONNX SHA、OpenCV/ONNX ARM64、APK v2 debug 签名、无 INTERNET 权限、独立包名。
- **短期原始 Artifact：** ID 13356392456，名 x200-same-frame-small-vs-cnlite-det-only-apk；包含 APK、使用说明、GPL-2.0 LICENSE 和校验清单。Artifact 内 SHA256SUMS 原写有 GitHub Runner 绝对路径（内容哈希正确，但外部 sha256sum -c 会找不到文件）；下面的正式预发布已将校验清单改为相对路径。
- **不重复构建的发布 Workflow 成功：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41898627152 ，仅从已成功 Artifact 下载、核验同一 APK 哈希/包名/权限并发布新 GitHub 预发布，不重新 Gradle 编译。发布 workflow commit d50e0978ba507f80dd310ccf84212527b062ac46。Tag x200-cnlite-small-sameframe-20261105-det-only-experiment，tag 指向真实构建源码 commit 9a9b531ae252f35771c070587fd842022624692e。
- **经 GitHub Release API 核验的实际 APK：** Kanzi_Small_vs_ChineseOCRLite_SameFrame_DET_only_debug.apk，53,706,403 bytes，SHA256 51f7a27dd78d74ae78f3964708a761f16eb19bb056500d2443f3df3af5df9ac5。直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-cnlite-small-sameframe-20261105-det-only-experiment/Kanzi_Small_vs_ChineseOCRLite_SameFrame_DET_only_debug.apk
- Release 页面：https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-cnlite-small-sameframe-20261105-det-only-experiment ，随包发布上游 ChineseOCR-Lite GPL-2.0 许可证及源码说明。**CN-Lite 模型权重单独许可条款仍未独立确认，禁止直接并入正式或商业版**。
- **下一阶段只等待 vivo X200 Pro 用户真实测试反馈：** 分别拍中文字门牌、含上下相邻价格的菜单、超市价签和商品包装，比较同图两路框（尤其跨行/碎框/漏框）、导出 JSON 里的分段 P50/P95 和温度。与此前 restaurant_menu_12、storefront_10、supermarket_03 的实图反例比对，先验证手机检测后处理的正确性再考虑集成正式 App。**没有任何已经完成的 vivo 手机数据，严禁编造准确率、速度和发热结论。**

#### 2026-11-05 — vivo V2405A 首次真实同帧 DET A/B JSON：Small 979ms / CN-Lite 240ms（单次，非稳态）

- **用户原始证据：** 2026-11-05 03:37:12（UTC+8）手机本地 JSON，设备 vivo V2405A，Android API 36，图片来源“相册真实图片”，同一张解码后的 Bitmap，分辨率1536×2048。JSON 标记 sameDecodedBitmapForBoth=true、isLiveCameraStream=false、recognitionOrTtsIncluded=false。只有一组配对（pairs=1），两模型各执行1次，无预热（warmupPerModel=0）。不将用户原始 JSON 或原图上传 GitHub；只记录聚合指标及代表性少量检测区域坐标。
- **模型固定哈希符合实验 APK：** Small SHA256 d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e；ChineseOCR-Lite SHA256 ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867。Small 模型输入1216×1600，CN-Lite输入640×832，输入规模显著不同，非同分辨率公平架构对比。
- **首次真机单次耗时/检测数量：** Small 前处理75ms、ONNX推理888ms、后处理16ms、总计979ms、54个检测框；CN-Lite前处理10ms、推理223ms、后处理7ms、总计240ms、30框。本次总耗时比值约979/240=4.08、推理比值约888/223=3.98；仅一次非预热样本，不可宣称稳定快4倍或准确率提升。
- **温度和内存：** 电池温度29.5℃→29.5℃；Android thermal status 0→0（正常）。最终整个实验 App PSS=666831 KB（约651MiB）；Small内部样本PSS=585103KB，CN-Lite样本PSS=688311KB；两模型同一进程同时加载，PSS动态变化，不能从这三值估算某模型独立内存占用。短时温度不变不能支持长期发热结论。
- **统计局限：** 导出 JSON 的 P50/P95 因 pairs=1 全是单次耗时的同值，分位数无统计意义。必须使用带预热的12对交替同帧测试再讨论 P50/P95、热漂移；固定照片测试仍不等同实时 Camera2/UVC 帧率。
- **真实划框几何风险1（尚缺实拍底图确认）：** CN-Lite输出大框 x=882..954、y=1084..1188，约72×104像素，覆盖 Small 在相近x范围的多个分离行级横框（如 y=1105..1134、y=1144..1175，并触及 y=1067..1094）。这是上下行被粘连的强烈坐标线索，但不能在未看到照片时直接判定文字内容。
- **真实划框几何风险2：** CN-Lite x=667..860、y=729..817的大框（约193×88）同时覆盖Small x=677..850、y=737..796与x=682..845、y=787..816两块区域；可能多行合框。另有CN-Lite x=569..754、y=1137..1192及x=567..753、y=1096..1157两个框纵向重叠约20像素，应检查碎框/跨行。Small 54框、CN-Lite 30框不能直接说明谁漏检或谁精确率更高，因为没有完整人工文字GT及照片。
- **当前结论与障碍：** 双模型已实际在 vivo 手机上成功推理并导出框坐标和性能记录；CN-Lite本次快但划框正确率未知。实验安卓后处理使用旋转矩形扩边近似替代原Python PyClipper，对跨行合框结论必须区分模型本身和后处理。Small暂不替换；不进入正式TTS/REC链路。
- **下一步最小有效验证：** 用户保留当前实验APK，无需重复构建；在已选同一照片上点击⑤“12对交替运行（含预热）”，导出新JSON，并提供同一图像Small红框、CN-Lite绿框两张真实截图；也可另拍清晰多行价格/价签/包装进行同帧对照。复核逐行完整度、真实漏字、跨行与后处理，再做下一步决定。
- **历史保留：** 前文“当时尚无真机数据”是上一轮构建完成时的状态，不覆盖。此次只追加日志，没有修改App、权重或触发新构建。

#### 2026-11-05 — 第二组课本样张：用户反馈 CN-Lite 20框/265ms、Small 33框/946ms

- **来源与证据级别：** 用户在本轮聊天中提供另一张课本样张的真机 A/B 汇总数据，并报告自己的目视判断；本轮**未附该课本样张的原始照片、红绿框截图、JSON或分段耗时记录**。因此下面的速度和框数按用户报告记载，划框优劣属于用户主观目视评价，不是助手已经逐框复核的实验结论。不得与2026-11-05首次 JSON（54框/979ms vs 30框/240ms）混为同一张图。
- **用户报告数据：** Small DET 33框、946ms；ChineseOCR-Lite DET 20框、265ms；本张课本样张单次报告的总耗时比值 946/265 ≈ 3.57。没有用户提供的预热次数、P50/P95、机身温度、图像像素分辨率等信息，不能补写或推测这些值；此比值只是所报时延之比，不代表稳态长期速度提升。
- **用户目视观察：** CN-Lite 框更整洁、单行文字合并更干净、没有多余碎小噪框；Small 红框仍存在较多单行拆成多块的现象。用户认为在**这张课本样张** CN-Lite 更好。**框数量更少本身不能证明漏检更少或行覆盖更准**，尤其要留意极小字号文字是否漏掉。
- **接下来的样本方向：** 持续收集招牌、菜单、商品包装图，优先排查极小字号漏检、跨行粘连和完整单行被拆框的问题；如取得同帧红绿框截图和 JSON，再验证主观判断并形成可核对的逐行指标。
- **工程保护：** 继续保留 Small 作为对照基线；尚无充分证据允许自动把 CN-Lite 切入正式 APK、REC 或 TTS。这一记录只追加交接文档，不修改模型/应用源码，不触发新的构建。

#### 2026-11-05 — 用户确认优先采用 ChineseOCR-Lite 绿色检测框作为下一版目标方案

- **用户明确技术选择：** “ChineseOCR-Lite 检测框（绿色）就用这个。” 将 ChineseOCR-Lite DET 的绿色框方案确定为后续安卓看字助手下一版的**目标/首选文字检测器**，尤其针对完整单行文字框；不是仅停留在两套 DET 并列比较的候选状态。
- **选择依据：** 已完成的 vivo X200 Pro 对照数据：首份真实 JSON 里 Small 54框/979ms、CN-Lite 30框/240ms；第二份课本样张用户报告 Small 33框/946ms、CN-Lite 20框/265ms，用户目视认为 CN-Lite 单行框更整洁、更少碎噪框。该选择反映用户对实际使用体验的优先级，并非统计证实所有场景均胜出。前述双方分辨率不同、后处理 PyClipper 近似以及极小字漏检/跨行合框等风险保持有效。
- **后续接入技术边界：** ChineseOCR-Lite 负责 **DET 划文字区域**；原有 PP-OCRv6 Small REC 作为识字模块保留；阅读顺序、原文保真、手机纯离线、TTS 行为及原机稳定版均保留，不使用 LLM 补字或按上下文生成文字。初始集成只放原仓库的**隔离实验分支/独立安装包**，使其能够与稳定版并存；保留 Small DET 随时回退，稳定版不直接替换。
- **必要工作：** 先审查 CN-Lite ONNX 模型输入/输出与 Paddle SDK 的 DET 前后处理及裁切坐标契合度，不得只把模型文件改名硬塞入 SDK；核对输入颜色通道、短边 640 预处理、输出概率图、DBNet 阈值/扩框策略，尤其确认 Android 对 PyClipper 的近似后处理影响，严格比对双检测输出的完整行/跨行/小字漏检；再以真实检测框调用 Small REC，进行原文保真、性能及发热端到端真机验证。
- **权利与产品发布门槛：** ChineseOCR-Lite 上游仓库 GPL-2.0，原始模型权重的独立分发条件尚未核实；仅用于隔离研究验证，未经权利核验不得直接发布商业/闭源正式版。当前用户选择不等于已完成生产适配/真机整链路验收。
- **本条记录操作说明：** 仅记录明确选型和技术接入目标；未改写旧实验、未覆盖正式 App、未提交替换后的正式 APK，不虚报已完成集成。

#### 2026-11-05 — ChineseOCR-Lite DET 绿色框 → PP-OCRv6 Small REC 隔离联调研究 APK

- **任务指令与工程边界：** 用户继明确选用 ChineseOCR-Lite 绿色框为下一版 DET 方向后授权“开始”。本轮完成原仓库内的 **隔离真实 DET→REC 实验程序、GitHub Android 编译、模型/包审计和研究预发布**。不覆盖正式看字助手，不修改根 `app/`，不替换稳定 Small DET/REC，不运行历史已完成 CRAFT 或其他旧 A/B；仍未接入稳定 App 的 TTS 或连续摄像头实时识字。
- **独立开发分支与来源：** `test/x200-cnlite-det-small-rec-isolated-20261105`，从之前成功的同帧纯 DET 独立包分支 commit `d50e0978ba507f80dd310ccf84212527b062ac46` 派生，复用仓库既有 `craft768-int8-probe/`。对应**本次实际构建源码 commit** `7b5abcb63fe7004b8e2cb777d24488c3e40096b1`。原①-⑥ Small红框/ChineseOCR-Lite绿框并列纯 DET 对照继续存在，新⑦执行绿色 DET 框→逐框裁图→Small REC CTC 原样识字，新⑧主动导出**包含照片中原始识别文字**的 JSON；原⑥匿名坐标 JSON 与⑧分开。没有云端、纠错LLM、文字推断、自动朗读。
- **文件范围已经比对：** 本轮新建 `craft768-int8-probe/app/src/main/java/com/vobich/kanzi/craftprobe/SmallRecAfterChineseDet.java`，在原独立 `DetCompareEngine.java` 添加只加载绿色检测器的方法，在 `DetCompareActivity.java` 新增读取按钮及原文展示/单独导出；新增加构建/发布工作流和 `DET_TO_SMALL_REC_README.md`，只调整该独立实验模块的 `app/build.gradle` 与 manifest。正式根目录 app/src/main 完全未修改。
- **Android独立包名** `com.vobich.kanzi.cnsmallrec`，`versionCode=3`、`versionName=0.3.0-cn-det-small-rec`；和原正式版以及先前 `com.vobich.kanzi.detcompare` 测试版可并存。仅ARM64，ONNX Runtime Android 1.21.1，OpenCV 4.10.0，CPU 2线程；无INTERNET权限。
- **真实模型哈希**：ChineseOCR-Lite DET 3,637,234 bytes，SHA256 `ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867`；PP-OCRv6 Small REC SHA256 `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`；原 Small DET 对照 SHA256 `d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e`。正式仓库不提交模型二进制，CI按历史 pin 的上游版本下载并校验后仅嵌入独立研究APK。
- **识别字典实际构建核查：** PP-OCRv6 Small REC `inference.yml` 的 `PostProcess.character_dict` 经 CI 按源字表顺序转换成 `small_rec_chars.json`，追加官方要求的末尾空格（如果缺），**18,709个字典项，对应ONNX输出18,710类**，完全吻合 CTC blank=0 规则；JSON字典 SHA256 `15d77eb7bb02d9f03bfd63929922894e5cde92b037c3a82f8d4de423e226b6b2`。BGR输入、固定高度48、动态宽度不超3200、原样CTC greedy argmax、相邻重复消除，禁止补字/润色。其裁切按 minAreaRect + 透视变换与縱长框逆时针旋转，最长100框，多于100明确记遗漏。
- **模型接入尚未证明的细节：** 当前 ChineseOCR-Lite Android DET 后处理仍沿用旧独立程序的旋转矩形扩边近似，不是 PyClipper 完全复现，可能出现小字号漏检、上下行粘连。逐框 REC 结果按框顶/左几何位置粗排序，未完成复杂版面读序与正式书页 TTS。单帧相册/系统相机图片在同一进程内先跑 DET 再逐框REC；非连续 Camera2/UVC 实时帧。任何空白/不合法 CTC 类别均禁止猜测或填字。
- **可核验的 CI 构建成功：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41902561484 ，job全部成功：官方3个ONNX哈希检查；PyYaml字典数与ONNX类别检查；Gradle `craft768-int8-probe :app:assembleDebug`；APK内三个权重、字典与Arm64 OpenCV/ONNX.so；无INTERNET权限；独立包名；v2 Debug签名。**这些只验证构建/静态结构，CI没有真实vivo手机文字内容识别准确率或实机耗时数据**。
- **原始短期 Artifact** `13357819741`，名 `cnlite-det-to-ppocrv6-small-rec-isolated-research-apk`（临时10天左右），包含APK、README、上游GPL-2.0许可证、SHA256SUMS及字符表校验记录。原APK `Kanzi_CnLiteDET_SmallREC_Standalone_v01_debug.apk` 大小 **72,465,379 bytes**，SHA256 **`a62aa5e3c914e96726e86a0a8c7c8b341ef2a9e2dd162b00364fe8054f1ae91e`**。从原GitHub Artifact下载并在独立容器重新核验字节哈希一致。
- **原成功APK零重构建发布：** GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41903527526 已成功：校验早前相同APK SHA、包名、无网络权限、APK v2签名，然后创建研究预发布，不再次编译。发布 workflow commit `a7e9abcbd53bf03de7f77a8aab294d7255785c0d`，tag `x200-cnlite-det-small-rec-20261105-isolated-v01` 指向上面固定编译源码commit。GitHub Release API证实APK资产大小/sha256与原Artifact完全一致。
- **公开研究APK直链：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-cnlite-det-small-rec-20261105-isolated-v01/Kanzi_CnLiteDET_SmallREC_Standalone_v01_debug.apk
- **预发布说明页：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-cnlite-det-small-rec-20261105-isolated-v01
- **原有工作保护与安全：** 不修改正式主线 App 与朗读/TTS；复用并保留 Small DET 对照及全部实验历史。CN-Lite 原项目GPL-2.0，实验附上游许可证，模型权重独立再分发和闭源商业整合条件未证实，严禁以此研究包直接视为正式商业版许可合格。
- **下一步真机验收（待用户照片/导出的⑧ JSON）：** 安卓下载安装新独立包，拍或选一张清晰真实课本、菜单、包装等图片，点击⑦；对照图中绿色框与每一条实际 Small REC 输出文字。优先排查 **绿色框漏了极小字、同一行被碎框、上下两行粘连、裁切误旋转、中文乱码或遗漏**，如有需要才点击⑧主动导出**含原文、可能敏感**的JSON。此时不能声称真机文字准确率或模型融合成功，更不能替换稳定 App，待实机截图/JSON核验后才判断。

#### 2026-11-05 — vivo 真实价目表：首次 ChineseOCR-Lite 绿色 DET → Small REC 原文联调

- **证据与范围：** 用户发送真实拍摄的墙上按摩服务价目表照片（原图1152×1536，价目表位于画面中间）以及实验APK点击⑦后的逐框**原始屏幕文本结果**。此消息没有包含⑧导出的带四点框坐标 JSON，也没有已绘制绿色框的App叠图。**不得把用户照片或识别原文全文上传公开 GitHub**；只记录可核实聚合性能、有限文字差错与下一步复核点。
- **首次实机 DET→REC 工作链实际运行成功：** 用户原始记录为 CN-Lite DET绿色框30个，Small REC实际处理30个，CTC空输出2个，跳过0个。DET=234ms、逐框Small REC=902ms、合计1158ms（两段之和1136ms，其余约22ms计时差/流程开销）。REC约占总计时77.9%，DET约占20.2%；REC分摊约30.1ms/框。此为一张冻结真实照片的一次性运行，不是连续Camera2/UVC实时帧率、P50/P95或长期温控指标。
- **真实照片人工核对，价目表有10项服务名称及10个相应金额：** 用户屏幕输出10项服务文字中9项完整正确，最后“拔罐热敷”只输出“拔罐热”（漏“敷”）；金额输出中有90元、50元、5项120元、1项60元和40元，合计9项，对比价目表上两项60元，**漏了“滑罐走罐”对应的60元**。以上是对用户图片及完整30条逐框输出的直接人工对照，不能推断每个漏字的故障源具体是DET漏框、错误裁剪还是REC误识。
- **进一步确认的文本差错：** 大标题原图“价目表”，输出“价表”（少“目”）；印刷英文小标题“PRICE LIST”没有结果。会员卡充值提示及“充1000送200”“充2000送600”两条充值文案被识别；底部店名基本正确。图像底部还有其他商品包装，被输出包括“电”“西麦好燕麦吃西支”“(”“冒超轻帖土”等有明显错误/不完整文本，不属于价目表内容（部分框可能位于包装/装饰）；**模型不包含生成式LLM不等于OCR绝对零误识**，对实时语音必须保留原图归属与失败提示，不可当作确定文字播报。
- **阅读顺序仍不满足正式播报：** 当前实验页是按每框上边缘Y、左边缘X粗排序，存在同一行的价格被排在项目名称前面（如开头“90元”在“中医推拿一小时”之前），尚未形成项目—价格的可靠成对输出；不要在没确认框关系前随意合并文本或改写金额。
- **最有价值的下一步证据：** 在相同价目表原图、相同⑦输出条件下，用户提供App绿色框叠图和使用⑧手动导出的含四点坐标/裁切宽高/REC原文/分段时延JSON（可能有隐私，用户自行选择要不要发送）。据此逐个定位大标题“目”、最后一项“敷”、第二个“60元”和“PRICE LIST”：先判框是否存在、框是否截断，再判断Small REC；重点排除CN-Lite短边640对于远距小字号的影响。对文本排序另行设计行级关联验证，但不得以猜测价格填补漏项。此时不宜在没有框证据的情况下盲改阈值、补合框或直接上正式TTS。
- **状态与工程保护：** 此轮收到新的用户真机证据并完成人工评估，尚未修改任何检测/识字代码、未构建新APK、没有真机长期热测，也不能声称完成语音验收。本条仅追加main:PROJECT_HANDOFF.md，之前构建成功/风险等历史原样保留。

#### 2026-11-05 — 用户确认转向 CN-Lite+Small REC 单主线优化：不再重复完整新旧 A/B

- **用户最新研发方向：** 用户明确表示不需要再做“旧 Small DET+Small REC vs 新 ChineseOCR-Lite DET+Small REC”的完整识字 A/B；基于目前绿色框更完整、碎框更少和更低DET延迟的实机观察，决定以后聚焦 **ChineseOCR-Lite DET + PP-OCRv6 Small REC** 单一新主线。旧 Small DET 的已验证数据和可回退版本继续保留，但停止主动重复历史对照，不再把旧模型并行评测作为新版本推进的强制前置条件。
- **客观证据边界：** 此方向是用户基于亲眼看到划框质量而作出的产品研发选择，**不等于已严格证明新方案在所有场景的文字识别准确率、漏检率或长时间热稳定性都超过旧模型**；“稍微优化就能大幅改善漏字”属于待测试假设，不能提前宣称已经实现提升。
- **当前唯一优先任务是定位漏字来源而非补丁堆砌：** 使用已提供的真实价目表原图，检查标题“价目表→价表”漏“目”、最后一项“拔罐热敷→拔罐热”漏“敷”、两行60元中只输出一行、英文“PRICE LIST”无输出；按完整原图和绿色框坐标逐项分类为 (1) DET 未检出，(2) 框范围缺失/越界/跨行，(3) 透视裁切/旋转造成截字，(4) 原Small REC在完整正确裁切下仍漏字。没有实际绿色框及逐框JSON时不能武断判定责任模块。对其他商品包装的误识，也必须区分检测到真实文字与REC错读，不能用语言模型脑补。
- **优先优化次序：** 先冻结真实困难样本作为回归集；核对 CN-Lite 输入缩放及手机后处理的矩形扩边近似对完整单行框、极小字和跨行合并的影响；如框正确再检查裁切是否截断；最后才针对 REC 本身的失字做有证据的优化。任何修改一次只触及一个模块，采用同一原图的前后对照，观察漏字/误读、检测和识别耗时及发热。严禁用上下文自动补价格、自动造字或以更多模糊合框补救。
- **工程约束保持：** 继续在原 GitHub 仓库的隔离研究分支迭代，保留旧稳定 APK、Small DET 回退能力和原有日志，不覆盖正式 TTS 朗读链路。手机端严格离线、识别原文保真、控制延迟与发热；ChineseOCR-Lite GPL-2.0 与权重再分发资格仍需正式发布前核查。
- **本次变更：** 只追加这项战略决定到 main:PROJECT_HANDOFF.md；**尚无本次新的模型优化、真机误字修复或重建 APK**，不得在后续交接中误记为技术改善已实现。

#### 2026-11-05 — v55 ChineseOCR-Lite 绿色 DET + Small REC 完整摄像头朗读隔离测试 APK 成功发布

- **用户已授权执行：** 将新 ChineseOCR-Lite DET 绿色划框模型直接接入原完整看字助手安卓实验版，并提供可安装的手机/USB摄像头实时识字+朗读测试APK，供次日 vivo X200 Pro 实测。此次**实际代码、CI构建、模型静态审计和独立 GitHub Release 都已完成**；没有任何真实vivo设备端实时TTS/发热数据，不得把成功编译称为实测成功。
- **源码与继承：** 原仓库 vobichngan285-collab/kanzi-assistant，复用已验证的完整 v54 安静稳定朗读主线 test/x200-quiet-speech-stability-v54，基线 SHA 6f32b2bd9476ea9206b52c113fb1d95ab21bfb50，在独立分支 test/x200-cnlite-live-ocr-v55-20261105 构建。最终构建源码 SHA 23a10e664eb8c290b80d1aa01c175208688b68b2。没有创建新仓库、覆盖正式版本、挪动用户的稳定包或删除旧实验历史。
- **真实新模型接入链路（不是只改显示名称）：** 通过 app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt 选择 cnlite_smallrec：DET 使用独立模型资产 models/cnlite/det/inference.onnx，RGB输入，短边固定640并按32向下对齐，maxSideLimit1600，DBNet DET阈值0.30、box阈值0.50、unclipRatio2.0、maxCandidates1000、轮廓慢速评分；REC 仍然读取 models/rec/inference.onnx 与相同Small inference.yml，沿用此前已验证 BGR/48高/原文CTC。新 v55 工作流在固定 PaddleOCR v3.7.0 Android SDK ImageUtils.kt 内只添加resize_short兼容性（不改其他模型resize方式），DBPostProcessor沿用官方PolygonUnclip方案而非旧拍照诊断包解析矩形近似。模型效果可能与旧诊断叠图略不同，必须在手机上核对。
- **MainActivity 配置：** fresh install 默认 cnlite_smallrec，用户设置新增 ChineseOCR-Lite 绿色 DET + Small REC 选项；旧 PP-OCRv6 Small/Tiny和v5 Mobile仍保留，CN-Lite模型初始化失败则沿用旧主线已有的回退Small逻辑。APP_VERSION 0.9.22-CNLite。保留原完整App手机摄像头、USB UVC、阅读顺序、音频TTS队列、安静稳定朗读、版面AI和原帧方向校准；没有添加LLM、自动猜字、补全价格或后验合框规则。
- **独立包：** app/build.gradle 的 debug applicationIdSuffix 为 .cnlitelive55；安装包实际applicationId com.vobich.kanzi.cnlitelive55、versionCode55、versionName 0.9.22-cnlite-live-ocr；与正式看字助手和旧v54以及此前独立DET/REC诊断包并存。app/src/main/AndroidManifest.xml 的标签为“看字助手 · 绿色框实时朗读v55”。仍无 INTERNET 权限；这是不含外部语音模型下载权限的纯离线安卓测试包，需手机系统有本地可用TTS声音。
- **固定模型核验：** CN-Lite DET 原始项目 DayBreak-u/chineseocr_lite commit 54a3df53af823f3bffdde9393d0c021b4f6d94cd，模型字节3637234，SHA256 ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867；Small REC SHA256 5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634；Small DET 旧模型保持SHA256 d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e作为备用。CI下载模型并严查SHA，不把模型二进制提交源码仓库。ChineseOCR-Lite 上游GPL-2.0许可证随GitHub发布，权重独立再分发及闭源商业许可条件仍待单独核实：本包仅实验研究，不得视为商用许可验收。
- **质量/构建：** 独立构建工作流 .github/workflows/x200-cnlite-liveocr-v55-apk.yml 基于v54已成功工作流，复用了中文Small REC实际ONNX/字典测试、手机Camera2预览、USB原始帧方向、竖排/阅读顺序、无打断语音等原有全部Smoke Test，均通过；另外明确检验CN-Lite SHA、RGB/resize_short SDK映射、旧Small REC引用。由于v54旧测试脚本曾硬编码APP_VERSION=0.9.21，新分支两处验证脚本已仅更新版本断言，不改变测试逻辑。工作流中早期三个中间提交Run曾因SDK补丁字符串、YAML缩进、旧版硬编码脚本失败，最后Run完全成功，不应把这些CI错误当作模型识字失败。
- **最终 GitHub Actions成功 Run：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41907373532；实际 :app:assembleDebug 编译通过，APK签名v2、包名/versionCode、Small DET和REC及CN-Lite DET权重SHA256、ARM64 ONNX、无INTERNET权限均经工作流真实验证；上传Artifact ID 13360553593，名 vivo-x200-cnlite-liveocr-v55-debug-apk（临时约10天）。独立Release发布亦成功，不依赖短期Artifact下载。
- **永久研究预发布版本：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-cnlite-liveocr-20261105-v55； tag x200-cnlite-liveocr-20261105-v55 指向最终构建SHA 23a10e664eb8c290b80d1aa01c175208688b68b2，GitHub Release API证实实际附件 Kanzi_X200_CNlite_LiveOCR_v55_debug.apk，size 393817212 bytes，SHA256 dc29507dedc42ad444dece4f0b24b863f9f3ab64342572b9568bdc3001c7ed9d；另附 ChineseOCR-Lite_GPL-2.0_LICENSE.txt。
- **真实下载直链：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-cnlite-liveocr-20261105-v55/Kanzi_X200_CNlite_LiveOCR_v55_debug.apk
- **待用户实测（真实vivo X200 Pro）：** 下载安装独立v55 APK，选择手机摄像头或USB，检查“当前OCR模型”显示ChineseOCR-Lite绿框+Small REC（可看设置/诊断）。先测试之前的课本、按摩价目表，再用招牌、菜单、商品包装；观察绿色检测框完整单行、小字号是否漏框、价格和项目是否配对、是否重复朗读、快扫/长文模式、UVC旋转是否正确。记录摄像头分辨率、实际检测/识字总时延、延迟和发热。尤其是旧样本“价目表”少目、“拔罐热敷”少敷、缺一个60元，逐项确认是DET、裁切、REC或读序缺陷，不要靠上下文造字。没有真机数据前严禁声称新方案准确率/长期热表现已达标。
- **历史完整保留：** 本次在独立分支修改完整App相关的4个文件、增加工作流并仅更新两个版本号断言脚本；main分支此轮只追加PROJECT_HANDOFF.md，保留所有旧记录。Stable APP不动；用户选择后续主要优化CN-Lite DET+Small REC，不再反复做无价值旧Small全量A/B。

#### 2026-11-05 — vivo X200 Pro v55 实时阅读主观质变反馈

- **用户实测反馈：** 用户在 vivo X200 Pro 安装最新独立 v55 ChineseOCR-Lite DET + PP-OCRv6 Small REC 完整摄像头/TTS 测试版，并上传两张手机摄像头实测截图（书籍《目录》和《编写说明》两页），明确评价：“刚才试了一下朗读有了智的飞跃”（语境为质的飞跃）。这是**用户本人对真实听感/整体使用效果的主观正面反馈**，不是本助手已亲耳听见录音或完成盲测的结论。显示时间09:47（截图状态栏；当日手机当地时间），画面UI为“摄像头：手机”“实时文字｜本段读完再更新”，有真实文字检测矩形覆盖层。
- **截图直观证据：** 《编写说明》页中多个较长、连续的横向文字行被各自的大致完整行框覆盖，较符合用户此前优先要求的完整单行文本区域；《目录》页的标题/条目也有条带形框。相比用户此前报告旧Small DET把单行拆碎，用户主观感觉本次朗读明显改进，符合新DET框更利于交给同一Small REC与既有读序/TTS流程的技术解释。但单凭两张静态图不能证明完整逐字识别率或实际语音连续时间。
- **剩余可见风险：** 《目录》页面内多个框相互重叠、疑似内容条目仍有拆框/粘连；《编写说明》右页有框跨过页面边界进入布面/侧边，页面顶部等位置存在疑似无字框，可能是误检或页面透视/叠加映射问题。书页小字的完全覆盖需原始图像及诊断JSON，不能仅凭白框断言已全部识出。部分画面被手指遮挡。
- **迭代决策：** 继续坚持用户已确认的 ChineseOCR-Lite DET + Small REC 主线，优先保护目前良好的整行划框/朗读体验，不盲改阈值或增加碎框粘连补丁。下一步只基于确实发生的漏字、错读、跨行、无字误检的具体证据定位DET/裁切/REC/顺序；测试中可记录10~15分钟连续朗读是否重复、中断、发热与速度。仅在隔离v55分支验证修改，保留原测试 APK、Small回退和既有稳定版。
- **工程状态：** 本条仅在 main:PROJECT_HANDOFF.md 追加用户真机使用反馈和截图可见问题；没有更改检测模型、后处理、朗读逻辑或构建新 APK。新 v55 APK 的先前 CI 成功与 Release 链接仍有效。

#### 2026-11-05 — v56 用户要求新文字即抢读 + Small REC 900ms 瓶颈审计和批2宽度分组实验

- **用户明确的新规则：** 实时扫码朗读要“像豆芽看见那样，识别到新文字就立马朗读，放弃正在读的那一条”；并明确要求检查此前实测约902ms的REC耗时原因、自行分析优化。不要保留v55自动实时模式中“必须等当前整段读完、且新文字需要两次完整OCR确认”的阻塞规则。用户没有要求修改ChineseOCR-Lite原始绿色检测模型或引入文本猜测。
- **已核实的原v55源码根因：** main: MainActivity.processCameraFrame在实时模式 `if(live && speechPageActive) return` 阻止TTS时完整OCR；queueSpeechText/liveSpeechGuard.acceptStable 又禁止audioBusy抢断并要求两个新文字确认，叠加12秒同场景节流。新的v56仅对默认实时文字/自动模式改为：一轮完整OCR得到与正在读的原文显著不同的新文字（由原RealtimeSpeechGuard.accept/sameTarget判定）后即调用clearSpeechQueue(true)停止系统或离线语音及旧队列，并朗读新文本；不再需要两次OCR确认，也不等旧语音完成。迟到的TTS回调由旧utteranceId检查忽略。亮度帧/同文字OCR噪声本身不触发抢断，仍有单OCR槽和场景/500ms最短间隔。获取新文字仍要完成DET+REC推理，不能声称234ms DET后就立即播报整段。
- **保留原长文模式：** 用户主动切换“长文阅读”仍保持读完整页再更新，默认新安装为“实时文字｜新文字立即打断”。本轮没有破坏已有手机Camera2/USB UVC、原阅读排序或TTS逻辑、候选识别文字原样、不引入LLM补字。
- **REC 900ms根因证据：** 2026-11-05按摩价目表独立诊断用CN-Lite DET 234ms、30个检测框、逐框PP-OCRv6 Small REC合计902ms（平均每框30.1ms），原默认性能档2线程/REC批1；官方固定PaddleOCR v3.7.0安卓OCREngine.kt每框单独执行QuadTextCrop裁切、RecPreprocessor对每一行缩放和分配NCHW张量、调用ONNX Session、从native复制完整 `[1,T,18710]` CTC概率到JVM、逐时间步argmax，30框累积约30次ONNX调用。**没有此前价目表CPU分段日志时，不能断言902ms全部是模型推理**；v56专门添加分段诊断来量化。
- **新REC速度试验（保守且可撤销）：** 独立v56新安装默认 OcrPerformanceProfile.REC_FAST2（2线程、每批2行，zero extra rescue）；原BASELINE批1/2线程仍在设置可选。官方v3.7.0 SDK编译时采用scripts/patch_v56_rec_width_batches.py的断言式小范围补丁，仅按原四点框边长估算裁切后相近行宽分批，减少批间Padding；仍调用原官方QuadTextCrop.crop、原Small REC ONNX与字典/CTC，不改变CN-Lite DET坐标、参数或识字原文。REC结果先按检测阅读顺序原索引存放，最后复原，防止宽度排序影响自然读序。批1时保持原顺序。不能据此推测必然在vivo快几倍，具体增益待实测。
- **可测耗时：** v56在手机手动诊断X200综合实验文本中新增 REC预处理ms、ONNX推理ms、CTC解码ms、批次数及每批输入形状；实时 log 同样显示分段耗时，可以判断先优化哪段而不盲改DET阈值。用户可对同一照片选择批2和批1分别测试、核实文字忠实和手机温度。
- **代码分支与版本：** test/x200-cnlite-live-preempt-recfast-v56-20261105，基于已成功v55源码SHA `23a10e664eb8c290b80d1aa01c175208688b68b2`。独立Android包名com.vobich.kanzi.cnlitelive56、versionCode56、versionName0.9.23-cnlite-immediate-recfast；APP_VERSION=0.9.23-CNLite-Immediate。正式原包和v55仍保留。修改MainActivity、OcrPerformanceProfile、PaddleOcrEngine计时、版本/manifest/静态校验脚本，新增宽度分批SDK补丁、单次新字抢读Java测试、ONNX Batch2 CTC验证脚本和独立Workflow。原Model权重和字典SHA保持不变。
- **CI与实测严格区分：** GitHub Actions成功Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41953023321；构建最终源码commit `578455359ce80d3cbdba653c4d3464cdec838ae8`。先前第一个临时CI因继承v55“必须不抢读”的旧源码断言而失败，随即改断言为本次用户要求新规则；最终成功CI纯Java即时抢读9项测试、Android手机Camera2/USB/读序/语音旧回归、真实PP-OCRv6 Small REC ONNX batch2 CTC argmax逐时间步与单批一致、SDK宽度分组及回原索引检查、Android Gradle编译、模型SHA/无INTERNET/签名和独立包名审计全部成功。
- **Linux CPU批处理结果真实性：** 同一固定Synthetic Model输入、ONNX两次单批 median19.1ms，一次batch2 median18.2ms（小样本runner，差约0.9ms，只能说明模型兼容和这一场景批量优势很小），并非vivo真实小字或30框速度。**禁止声称REC已从902ms降低为某个具体数值**，必须在vivo实拍和用户诊断分段数据才可比较；batch2可能增发热或内存，必要时改回批1。
- **已完成Android/预发布：** 完整Build与模型审计成功，GitHub release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-cnlite-immediate-recfast-20261105-v56 ，附件 Kanzi_X200_CNlite_Immediate_RECfast_v56_debug.apk，393,817,220 bytes，SHA256 `3c6e9e5dd3c1a99ef93c7de63a15ebadc77ee63288ae940bfeb25cd04aaed048`，GitHub API核验size/digest/asset state=uploaded。独立APK长期下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-cnlite-immediate-recfast-20261105-v56/Kanzi_X200_CNlite_Immediate_RECfast_v56_debug.apk 。原 CI 临时 Artifact ID `13381578748`，名vivo-x200-cnlite-immediate-recfast-v56-apk，约10日保留；release资产不受其过期影响。上游GPL-2.0许可证与测试说明随release。
- **真实vivo验收尚未进行：** 请用v56独立包，新字自动抢读模式：把镜头从正在朗读的课本页移到明显不同的第二页，检查是否立即中断旧TTS、读新字、重复或跳读；保持同一页摇晃/曝光变化检测是否频繁误抢。再用同一张价目表做批2 vs 批1手动诊断，记录REC预处理/ONNX/CTC及总耗时、CPU热状态和漏字。音频抢读行为只能手机听测，CI不代替实机验证。
- **风险和保留：** CN-Lite上游 GPL-2.0，模型独立权利仍需进一步核验；此次研究 APK不等于商业发行资质。旧v55好用版本、Small DET故障回退、主线交接历史不覆盖；本次main仅追加本节，稳定app分支不合并v56。

#### 2026-11-05 — Blue Star Guide Development 1.0 独立轻量版完成

- **用户最终确认：** 软件名称「蓝星向导」，桌面英文名 **Blue Star Guide**，版本 **Development 1.0**，桌面图标采用用户上传且确认的蓝色发光地球（地球、导航光环、书本及文字扫描标记）。用户明确授权按先前讨论一次性执行新版，包括简化设置、本机TTS、朗读语速、优先首批真字播报、删除PP-OCRv5 Mobile和不需要的模型、压缩APK；但**左右两页阅读排序本轮明确不动**。
- **独立研究分支：** `dev/blue-star-guide-1.0-20261105`，基于v57已讨论的简化设置与本机TTS源码，最终源码 SHA `adac4328de4350ffe397b1d0bef54c80a3a5b0aa`。本轮旧稳定分支v55/v56均未覆盖。MainActivity APP_VERSION=`Blue Star Guide · Dev 1.0`；AndroidManifest launcher label=`Blue Star Guide`，icon/roundIcon 指向 `@drawable/bluestar_icon`，资源 `app/src/main/res/drawable-nodpi/bluestar_icon.webp`为用户确认的地球图标144px版本。测试版applicationId=`com.vobich.kanzi.bluestarguide.dev10`，versionCode100、versionName`1.0-development`，arm64-v8a，仅供vivo X200 Pro等64位安卓手机测试。
- **真实精简结果：** v56 APK 393,817,220 bytes（393.8MB）；Blue Star Guide Dev 1.0 APK **79,364,942 bytes（79.36MB）**，真实减少314,452,278 bytes（**79.85%**）。实际APK GitHub Release API报告SHA256 `41650489c2b806e724f2f625023c803c72602dfda32ebc422dee40b5917c8bb1`，asset state `uploaded`。成功移除PP-OCRv5 Mobile、PP-OCRv6 Tiny的DET/REC/字典与模型设置/加载引用、Sherpa-ONNX额外音频运行库、Kokoro/Piper/Melo下载播放相关三段Java/Kotlin类、相关Jar压缩依赖、32位/x86 ABIs；严格保留ChineseOCR-Lite DET RGB640、原PP-OCRv6 Small REC 18710字符CTC字典、原Small DET回退、PP-DocLayout-S、UVC摄像头库及安卓端ONNX/OpenCV。不要误称“79MB就一定更快”，包大小与CPU推理耗时独立。
- **用户语音包：** 使用用户已安装的Android「广荣微软tts」服务，用户截图选定Microsoft Xiaoxiao Natural简体中文。App设置中的系统TTS引擎清单可选已安装的引擎（或跟随系统默认），调用`new TextToSpeech(context,onInit,packageName)`指定**仅本App**所用TTS源，不调用修改安卓全局设置接口；为不覆盖语音包里的音色，初始化时不再调用`setLanguage`，速度默认`followEngineSpeechRate=true`不调用`setSpeechRate`，仅在用户主动选自定义语速时调整本App TTS对象。AndroidManifest声明TTS_SERVICE package visibility query，未声明INTERNET权限；**但外部广荣微软TTS自身是否联网不在本APP控制范围，不能承诺Natural声音离线可用。**
- **日常设置UI：** 由旧数十行长设置切换为高对比度深色首页，三项大按钮「语音引擎」「朗读速度」「实时/长文朗读方式」，可以选本地Android TTS与跟随原语速/自定义0.5～2.0倍。旧设备设置、模型诊断等仅通过『故障排查与开发诊断』入口打开，保留必要回退及测试能力但不污染首页，参考用户上传的豆芽看见设置界面。
- **首批真实文字提前朗读：** 保持ChineseOCR-Lite+Small REC模型不变，CI在固定官方PaddleOCR v3.7.0 Android SDK上采用`patch_v56_rec_width_batches.py`按行宽分批并恢复最终原索引，再用`patch_bsg_streaming_sdk.py`让首次REC批量严格先处理原`BoxSorter`阅读顺序的前2行，第一次真实REC返回时调用可选`onFirstBatch`回调给APP TTS，立即打断旧语音并播报识别出的原文。后续完整REC继续工作，返回后按原始真实OCR全文扣除已播的完全相符文本行，再追加余下文字的分段队列。未使用LLM补字或更改用户明确暂缓的左右两页排序。**注意：首次真实文字开口延迟仍取决于DET+首批REC+外部TTS启动，未完成vivo真机计时；遇到OCR顺序及重复现象应以真机证据记录，不能宣称<1秒。**
- **真实CI：** `.github/workflows/blue-star-guide-dev10-arm64-apk.yml` 成功GitHub Actions Run `https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41965300550`（最终成功）；首次因旧TTS guard字符串不匹配，第二次因Java日志`chunks.size`漏`()`，分别修复并重新跑全部流程；最终成功Android Gradle assembleDebug、原模型下载/静态SHA256核验、真实REC batch2 CTC parity、手机Camera2及USB原有回归、TTS与UI源守卫、装载图标文件、APK arm64-only/无INTERNET/模型SHA/签名v2审计、上传Artifact与GitHub预发布。
- **安装下载（长期预发布附件）：** `https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-dev1.0-20261105`；**APK直链** `https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-dev1.0-20261105/BlueStarGuide_Dev_1.0_arm64_debug.apk`；发布附ChineseOCR-Lite_GPL-2.0_LICENSE.txt。GitHub Actions artifact 13387975066（名blue-star-guide-dev10-arm64-apk，临时10天）不应作为长期下载链接。
- **待真机确认（不得把CI当真机）：** vivo X200 Pro安装dev1.0并列旧v55/v56；先在设置选择广荣微软TTS，确认小晓Natural音色与原语速是否保持（且未修改全局默认引擎），主界面拍同一本书，翻页听新首句响应、是否遗漏/重复及长文朗读衔接，切换USB摄像头并运行真机诊断REC分段速度、热状态、内存。老版v55优良检测表现作为回退安全基线。中文OCR文字保持源图像原样；外部TTS服务若联网需另验。
- **许可与风险：** ChineseOCR-Lite上游GPL-2.0、原模型权重再分发与商用闭源集成许可尚未完全核实；本预发布仅科研测试用途。Android发行市场的隐私/签名/无障碍说明和64位覆盖均需单独验收，不可把研究包视为已商用。

#### 2026-11-05 — Blue Star Guide Dev 1.0.1 地球图标损坏已修复

- **用户反馈：** 安装Blue Star Guide Development 1.0后“图标也没变化”。通过实际下载解压 GitHub Actions 1.0 APK发现，AndroidManifest 的 `android:label="Blue Star Guide"`、`android:icon="@drawable/bluestar_icon"` 设置本身存在且生效资源路径正确，但 APK 内 `res/drawable-nodpi-v4/bluestar_icon.webp` 图像与用户确认原稿仅差2字节，SHA256为`050d7b158516af1bda1c5adcd8de0f4edd8f4bfe041b85e628098fe389ad0ad6`，Pillow实际解码报错，导致设备可能无法显示正常图标。根因是人工写入GitHub二进制时一个base64字符从`z`转录为`s`；之前CI错误地只检查WEBP RIFF头与文件存在，没有真实解码。
- **修复内容：** 代码在独立开发分支 `dev/blue-star-guide-1.0-20261105`，新增 `scripts/verify_repair_globe_icon.py` 对已知损坏源资源做仅在SHA256完全匹配时的精准两字节还原（原坏字节 `9b15` → 用户确认原稿字节 `9cd5`），然后用 Pillow 实际读取与固定SHA256 `331e312e838a922292d099809e608c511046b84034b3630b11e0827414b41e96`校验，构建发现其他未知资源即失败。不改OCR/REC/相机/TTS算法。Android资源新增 `@mipmap/ic_launcher` 的Android 8+自适应图标 `res/mipmap-anydpi-v26/ic_launcher.xml`、蓝色背景及为保留完整地球轨道采用18dp inset的foreground。
- **版本/签名：** Dev 1.0.1，versionCode101，versionName `1.0.1-development`，包名仍为 `com.vobich.kanzi.bluestarguide.dev10`。由于GitHub Actions Debug签名在不同构建间可能变化，手机上若已安装原Dev1.0，建议**先卸载原 Blue Star Guide 再安装1.0.1**，不会覆盖看字助手v55/v56；卸载仅会清除Dev1.0自身的偏好设置，故用户需重新选择本机广荣微软TTS。不得建议卸载用户的稳定版或旧看字助手。
- **真实构建与APK二次验证：** GitHub Actions `https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41969773558`全部成功（source SHA `51d4061b9f42434bd797781e408aa6ee87dd8bfa`），不再只验文件存在，而是APK ZIP 内WEBP资源经过Pillow完整解码验证且SHA256匹配，确认APK包含 `res/mipmap-anydpi-v26/ic_launcher.xml`。助手另将成功workflow artifact下载到本轮容器，在ZIP内实测APK的WebP图标(144x144)可被Pillow解码并与原稿hash完全一致。APK 79,366,148 bytes，SHA256 `0246651335f7dff8b619f0382a2476e9045f3c90b3a3be212257d43b8f374130`。
- **修正包下载：** Release `https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-dev1.0.1-globefix-20261105`；直接APK `https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-dev1.0.1-globefix-20261105/BlueStarGuide_Dev_1.0.1_GlobeFixed_arm64.apk`。旧1.0 Release为历史记录，用户应下载1.0.1而不是旧APK。**仍需真机确认vivo桌面图标显示**；新CI和离线图像解码是充分证据但不能代表已经远程操作手机。

#### 2026-11-05 — Blue Star Guide Dev 1.0.1 静态综合审查（仅体检、不改功能）

- **用户原话/验收边界：** 用户认为当前新识别方案已超过“豆芽看见”，要求综合审查不合理之处。此为用户真实使用体验评价；未做同图同设备严密逐字统计，不可写成已测客观识别率排名。本轮只审查GitHub在售Dev1.0.1源码与实际Release/CI；**不改任何代码、算法、模型、APK或稳定分支**。
- **审查固定版：** `dev/blue-star-guide-1.0-20261105` 当前源码 SHA `51d4061b9f42434bd797781e408aa6ee87dd8bfa`，成功编译Run `https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41969773558`，预发布tag `blue-star-guide-dev1.0.1-globefix-20261105`，APK 79,366,148 bytes。审核链路 MainActivity.processCameraFrame → PaddleOcrEngine.recognizeNv21 → patch_bsg_streaming_sdk.py、patch_v56_rec_width_batches.py → queueSpeechText / appendRestOfRealOcr / Android TextToSpeech；检查setting、build.gradle、manifest与workflow。确认model CN-Lite DET + Small REC主线、Small DET可回退、arm64-only、无INTERNET、地球图标已修复，不需要重改成功模型。
- **P0（源码已经确定的设计风险，尚非真机必现）：** `MainActivity.appendRestOfRealOcr` 对首次真实REC已读行使用 `rest.indexOf(row)` 与 substring 从完整OCR文字中删除。完整文本是ReadingStrategyRouter重排后的文字，前两行来自原BoxSorter；行可能分割/合并/被改排序或重复出现，产生找不到原行→重复播报、误删同名行→漏读和串序等。应后续改为按同帧同OCR框ID/已播状态精确续接；严格不猜字且不修改当前检测框和左右页布局规则。
- **P0（源码风险）：** `processCameraFrame` OCR开始时捕获recognizedScene，但 `RealtimeSceneTracker.generation`翻页仅更新scene数字，**不提升readingEpoch、不取消/标记失效旧OCR任务**；早播回调与完整结果只核实readingEpoch,并不对比已确认的当前页面/场景，故翻到第2/3页时上一页已启动的2～4s REC仍可接入旧文字，`busy`同时挡住新的完整OCR。需要可靠新页确认/任务token/过期队列丢弃，并避免把摄像头曝光抖动误当真实翻页（不能仅靠原始luma scene值）。
- **P1（需要真实场景回归）：** `RealtimeSpeechGuard.accept` 的activeText在首两行播报后被更新为短前缀；如果下一页复用相同页眉/章节名/相同前两行，则两次结果的包含关系触发sameTarget，可能连整页变动都被压成重复、没有新页朗读。最好区分候选前缀和整页target身份，使用整页唯一OCR/场景身份而非单一文字前缀进行页面去重。
- **P1（设置不一致）：** early首批回调直接 `queueSpeechText(first,normalizeOcrText(first),...)`，未应用完整onRecognized路径的 `speechNoEnglish`、`speechNoNumbers`、`maxSpeechChars`；如果用户在高级设置启用过滤，前两行与后续行执行不同规则。属于准确回归需要修复的代码路径（默认为false/0，非所有人必现）。
- **P1（长行推理与响应）：** 官方Small REC重度依赖行宽，用户样本43框REC2472ms、33长行REC3926ms且3765ms耗在ONNX推理；CI仅单帧静态/合成CTC批2兼容性，无真正设备翻页到首声延迟指标。首批两行可提前发声，但若正在跑上一帧OCR，受busy与前一帧剩余推理阻塞；需要在真实vivo测试首字、中位/95分位、重复/漏字、热、2线程批2对4线程批2。无需贸然改CN-Lite检测器或继续调高线程。
- **P2（USB/方向）：** earlyListener条件 `fastScanMode && !usbAutomatic`，USB使用默认自动定向时没有首批提前播报；若`AutoPageRotation`在首批已播后又接受整页重新旋转OCR，最初播报与最后结果不是同一套OCR源。属边界场景，需在不改变用户明确冻结的左右阅读排序前提下设计一致性保护。
- **P2（TTS）：** 通过应用内`new TextToSpeech(...,requestedPackage)`选择已安装的广荣微软tts且没有系统global TTS配置写入；不调用setLanguage；`followEngineSpeechRate=true`时不主动覆盖setSpeechRate。正确但仅检查初始化与getVoice日志，**未硬核验证实际引擎是Microsoft Xiaoxiao Natural**；语音引擎是否联网取决于其自身，宿主无INTERNET并不能证明外部Natural TTS离线。
- **P2（发行/界面维护）：** debug applicationId有独立后缀但`app/build.gradle` release的applicationId仍是旧 `com.vobich.kanzi`，未来不经检查直接编Release存在覆盖原稳定包的风险；当前只发debug，不影响现有APK。简洁设置对话框的3个详情行在弹出二级选择器后没有刷新，旧“看字助手”试听文案和历史实验备注仍残存；不是识别瓶颈。PP-DocLayout-S/Small DET仍占据可用空间，但在无真机覆盖测试前不应为追求更小体积直接删除。
- **修复建议优先级与动作界限：** (1) 为首批朗读增可追溯的frameId/boxId，严格续播并独立验证重复/漏读；(2) 为翻页加经过确认的任务版本/旧OCR过期机制，避免旧页文字溢出；(3) 稳定同页/跨页重复判断；(4) 真机检测首声时间、ONNX大宽度与系统TTS；(5) TTS与发行UI琐事。**这次静态源码体检不能替代实机回归，任何更改必须隔离新分支并得到用户明确开发授权。**

#### 2026-11-05 — Blue Star Guide 1.0.1 USB 外接摄像头半屏灰/疑似卡死现场审查

- 用户在真机提供屏幕截图 `/mnt/data/1000036095.jpg`（本会话附件），USB外接模式预览的右半屏灰色固定RGB(128,128,128)，左侧有倾斜的建筑和识字叠框；截图无法单凭静帧判断视频到底有无更新。重要：应用`previewContainer`在源码中背景为黑色，因此右侧完全均匀的灰色**不能简单归因于代码的黑色letterbox留边**，应调查USB GL绘制/表面填充/纹理大小与旋转变换，不可直接宣称OCR或设备断流。
- 用户同时提供单帧证据ZIP `/mnt/data/kanzi-ocr-single-frame (19).zip`（不能上传私密帧到GitHub）。检查metadata.json与decoded-frame.png：USB原始 NV21=1920×1080，bitmap=1920×1080，成功检测6框，DET202ms、REC172ms、OCR实际约381～384ms，Android热状态0，NativeHeap298→303MB，使用4线程/批2，USB方向自动决策暂定0°。该帧画面内容是其他室外楼宇，并非同一个时刻的截图，**只能证明采集和OCR曾正常完成，不能证明截图瞬间USB没有冻结**。
- 仓库独立测试分支`dev/blue-star-guide-1.0-20261105`（Dev1.0.1源码SHA`51d4061b9f42434bd797781e408aa6ee87dd8bfa`），MainActivity.getCameraRequest固定请求1920×1080 MJPEG + OPENGL + rawPreviewData + `setAspectRatioShow(true)`，`applyPreviewTransform`额外直接调整TextureView尺寸、视图旋转、matrix镜像，`getCameraView()`注册LayoutChangeListener，每次预览尺寸改变可能重新布局；这条路径可能造成GL画面只填半屏或不更新，但静态代码尚不足以证明根因。
- MainActivity.onCameraState(ERROR)只写入**不可见**的`status`，没有立即显示USB断流提示；CLOSED只将`previewCallbackAdded=false`，USB链路没有Camera2分支那样独立的帧到达超时watchdog/自动重新连接。`onPreviewData(NV21)`直接进入`processCameraFrame`，主要OCR推理是异步，但帧克隆和轻量评估在回调链路发生，尚无逐阶段UVC帧/GL绘制耗时或掉帧证据。手机预览与USB必须分别验证。
- 本轮结论/验收：**第一优先USB相机预览稳定性**。临时区分测试：USB视频出现停顿时点击「暂停识别」，移动摄像头，若画面仍不更新指向UVC/GL/USB电源或生命周期；若立即恢复则调度/线程干扰。代码修复需隔离新分支验证两类事件（UVC原始帧不更新 vs 原始帧仍更新但GL灰屏/冻结），明确预览真正停止的时间戳、帧计数、USB OPENED/CLOSED/ERROR事件、TextureView尺寸和旋转变换；只针对证据根因修复，不更改ChineseOCR-Lite DET/Small REC、双页排序或TTS；必要时加入有退避限制的手动USB重连。
- **当前仅审查图片、诊断ZIP、源码与写交接文档，尚未修改APP代码或构建新的APK**。

#### 2026-11-05 — 蓝星向导 1.0.2 USB 摄像头卡死定位实验版（实包已发布）

- **用户授权/实际症状：** 用户明确回复“开始”，授权独立 1.0.2 USB相机定位版。已确认截图卡死与先前店铺招牌单帧ZIP是两次独立的操作；卡死时没有按诊断按钮，左半画面彻底静止、右半持续灰，部分自行恢复、部分必须退出App重进，发生时间从刚启动到约2分钟不等。原测试不能据店铺招牌ZIP推断卡死时USB仍送帧。
- **工作起点与隔离：** 从经成功构建的Dev1.0.1 SHA `51d4061b9f42434bd797781e408aa6ee87dd8bfa` 创建实验分支 `exp/blue-star-usb-health-102-20261105`，全程不修改Dev1.0.1分支/已发布 APK。独立应用Id `com.vobich.kanzi.usbhealth.dev102`，`versionCode=102`，`versionName=1.0.2-development`，桌面名称`蓝星向导 USB诊断`。可与Dev1.0.1及旧看字助手并存。
- **实际源码实现（仅监测、不修复/自动重启）：** `app/src/main/java/com/vobichngan/kanzi/UsbCameraHealthMonitor.java` 被动收集USB原生NV21回调计数、最后一帧时间和尺寸、OPENED/CLOSED/ERROR、OCR忙状态。基于固定上游`ernestp/AndroidUSBCamera@3.6.0`，`scripts/patch_usb_gl_health.py` 只在GL `RenderManager.kt`的`mScreenRender?.swapBuffers(...)`后调用 `DiagnosticRenderHeartbeat.noteSwapAttempt()`，并在`EGLEvn.kt`检测eglSwapBuffers失败时调用`noteEglError()`；新静态心跳 class 代码为 `scripts/DiagnosticRenderHeartbeat.java`，编译时复制到libausbc。无改变渲染buffer、相机数据链路或原始图像。GL提交次数**不等于屏幕真正成功显示的新帧**，UI和日志明确如此标注；出现原始帧停滞、GL尝试停滞、EGL错误时显示不同状态。 
- **用户可观察行为：** 连接USB后画面顶部实时显示 `USB原始帧率｜GL提交次数/秒｜OCR状态｜最近帧间隔｜EGL错误`；即使暂停识别，USB采集监测仍继续。点击顶部状态条显示/复制详细最近约一分钟记录。每8秒或状态变更把不含图像/识别文字/音频的轻量记录持久保存到应用私有文件，每次启动产生独立日志文件，保存最近五个会话，查看时包含当前及最多两份保存会话。因此画面卡死需要退出再进时可在1.0.2顶部状态条复制上次记录，但若UI线程长时间冻结可能漏掉最后几秒；准确根因仍需真实故障现场诊断。
- **严格保留：** 与1.0.1两包实际解包逐项SHA对比 `assets/models/cnlite/det/inference.onnx`、`assets/models/det/inference.onnx`、`assets/models/rec/inference.onnx`、`assets/layout/pp_doclayout_s.onnx`、`res/drawable-nodpi-v4/bluestar_icon.webp` **全部完全相同**。未改OCR输入、CN-Lite DET/Small REC参数、阅读顺序、TTS或USB预览旋转/镜像算法，没有自动重启/自动重连；保留arm64、无需应用INTERNET权限。附带`UsbCameraHealthMonitorSmokeTest.java`测试启动缓冲、双路停帧、仅GL停、仅原始回调停、EGL失败、日志限长。
- **CI/实际release验证：** 构建源码SHA `f96d1a8cfe8a1a49f49e5dfd2eb08f5758be3737`，Actions `https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41982844999`全部成功。CI确认最终APK包含`UsbCameraHealthMonitor`、`DiagnosticRenderHeartbeat`，并验证原模型哈希、签名、版本与无旧模型；另从GitHub Actions产物重新下载APK，独立解压验证两种监控类均包含在DEX中，全部模型、图标与1.0.1相同。
- **发布与下载：** `https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-usb-health-dev1.0.2-20261105`；APK `https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-usb-health-dev1.0.2-20261105/BlueStarGuide_Dev_1.0.2_USB_Health_arm64.apk`，GitHub证实bytes=79,366,140, SHA256 `c46a33116f5fe44e9dcbc1cd3202c2d0670f9737d2c6861709b7aca6b8adba29`，Action artifact ID `13394896448`。
- **待用户验收和后续实验：** 安装1.0.2（不要删除1.0.1），选择USB外接摄像头正常使用；卡死时不用触发单帧诊断，直接截取包括顶部监控条的画面。若主界面仍响应，点顶部诊断条复制日志；如整App无响应，重新打开1.0.2后点击顶部状态条复制此前会话日志。对照GL提交和NV21帧心跳判断UVC回调、GL渲染或两侧同步停；如两路均正常但画面冻结需进一步监测TextureView呈现/预览像素的真实更新情况（当前**未**测成功显示FPS），避免基于假指标直接重启或重写相机。**尚无真机卡死现场日志，不宣称已修复卡死。**

#### 2026-11-05 — 后续版本语音过滤新增“不读单字”（仅需求登记，尚未编码）

- **用户新需求：** 现有高级朗读过滤“不读英文”“不读数字”之外，再加一项 **“不读单字”**，目的减少背景杂字、书页边缘、杂散识别框的独立单字播报，减少听觉干扰。当前Dev1.0.2是USB摄像头诊断版本，不得为了该需求修改已发布APK。
- **建议语义：** 过滤仅针对**独立OCR文字框**中去除空格、单纯附属标点后恰好只剩一个汉字/有意义文本字符的片段，默认优先仅单独汉字，不能在完整TTS字符串上全局删除长度1的单字、不能把正常句子里的“我”“你”“一”删掉。已有“不读英文”“不读数字”与该选项独立，避免不清楚的叠加作用；例如独立`中`不读，`请注意安全`继续读，独立`8`由“不读数字”规则处理。必要时可由实测决定是否扩大到其它字符类别。
- **重要取舍：** 有些单字本来就是有效且关键的路牌/药品/货品/页码信息，不能根据单字“无意义”作事实判定。开关建议**默认关闭、用户按需开启**；无论开关状态，OCR原文、逐框diagnostics、叠加框与证据都不删除，仅在**送入TTS队列时**作播报选择，且不得编造/自动补全。
- **统一调用路径：** 后续实现必须涵盖首次真实REC批次提前朗读（目前MainActivity直接queueSpeechText，绕过过滤）、整页onRecognized、appendRestOfRealOcr、长文/手动触发；处理顺序与已讨论的frameId/boxId逐框续播相匹配。不可先把整个页面合并为普通字符串后删除“单字”，否则无法正确区分原始文字框的独立性；可以在未来框级OCR结果与朗读层之间构造派生SpeechSelection。
- **待开发/本次限制：** 本轮只是继续确认方案并追加交接记录，没有修改源码、构建、发包或影响USB稳定性测试。新页面替换旧朗读、单触抓帧、长文一触即切换、成功反馈叮声/轻振均为此前已讨论的下一版交互需求，不将其误宣称为Dev1.0.2现成功能。

#### 2026-11-05 — USB卡死解决后实施：本轮朗读交互与过滤需求冻结清单

> **状态：仅设计确认/归档，功能开发尚未开始。硬前置：先用Dev1.0.2 USB健康实验版收集一次冻帧现场证据，针对性解决USB采集或GL预览问题并经真机稳定性回归，再从稳定基线新开独立分支开发此清单。不得将未实现的功能说成1.0.2已有。**

##### A. 用户明确确认的新操作机制
1. **取消底部“识别一次”按钮。** 在手动识别模式，轻触摄像头预览画面一次，就获取**点击之后的最新有效视频帧**，只执行一次原离线OCR，再播报真实识别文字；不是永久拍照保存到相册。底部的设置/摄像头切换/模式切换按钮不能触发抓帧；为TalkBack提供可用的无障碍操作。用户再次轻触同一页，应允许重新识别/重读，不被自动去重拦截。
2. **长文本模式：自动不会打断当前文章，但用户轻触预览一次就无条件覆盖。** 示例：第1页40行读到第12行，用户翻到第2页并轻触**一次**：马上停止当前TTS、清空旧文剩余队列、让旧页OCR及TTS回调失效；触摸后捕获第2页**新帧**，尽快执行OCR，识别到真实文字后直接朗读新页，后续文字继续追加到本轮长文。不能要求先点一次停止、再点一次拍照（此前两次点击方案已被用户否决）。如果没在播报，轻触立即拍帧并读。不论自动扫描设置，显式用户触摸在长文本模式高于“自动读完才开始下一次”规则。
3. **实时文字模式：新页面已确认真实文字时自动抢占旧朗读。** 新页OCR产生可靠新文本后，立即停旧TTS、清空旧队列，旧页还在计算的结果禁止晚到后再播报；新页第一批真实文字可尽快播，不强制等完整REC。页面变化只作低成本提示，不因手抖、曝光/自动对焦就提前停止；连续两页同页眉但正文不同不能简单被判同一页。实时手动模式则以点击画面为明确的覆盖指令。长文本无用户轻触则仍保持读完整篇。
4. **统一任务身份和取消逻辑。** frame/task generation + OCR frame/box ID + speech utterance ID，用于标记旧任务过期、区分“已入队”与“已播放完成”。推理在ONNX内部未必能硬取消，必须保持单个OCR推理通道；触摸发生时先停止旧语音，再把新识别请求挂到最高优先级，新推理在旧任务释放资源后处理。若忙时连续点击，以最后一次显式请求为准，不能丢请求、不能拿上一次缓存的旧帧冒充最新帧。TTS停止和旧回调到达均不能恢复旧语音。

##### B. 提前朗读衔接的已发现隐患（作为配套可靠性修复，单独回归）
5. 当前第一批真实REC文字提前播报后，以字符串查找/删除方式从完整OCR结果去掉已读内容；重复行、不同空白/换行、重新排序可能出现重读、漏读、删错。建议同一帧用稳定OCR框ID及唯一阅读计划衔接：标记已送入语音队列的框，余下按同一顺序继续播报；不能仅靠字符串内容作为身份。用户此前要求暂时冻结左右文字排序风险：**只修任务归属/续读机制，不顺手改现有左右页阅读排序算法。**

##### C. 朗读前的声音和触觉提示（已确认，具体参数待实测）
6. **本次真实文字已经识别并确认要开始TTS朗读时，先播放清脆短促单声“叮”并让手机轻微震动一次，随后立即播报。** 每个新朗读任务最多提示一次，不能每框/每行/每TTS段重复，不能因重复扫描连续叮；文字为空、过滤后没有可播文字、TTS不可用、用户点击取消旧语音均不发生“成功提示”。使用本地提示、无网络；设置中提示音/振动各自可关闭，尊重系统静音/勿扰/触觉规则。100-150ms音效、20-35ms短震动仅是设计参考，实际由真机调整，不能被宣称已达成定值。

##### D. 过滤设置：新增单字，暂缓智能乱码过滤
7. 原有“不读英文”“不读数字”保持不变；增加可选开关**“不读单字”**。仅过滤原始OCR中**独立成框的单个汉字**所产生的朗读杂音，不能删除正常句子中的单字，更不能对全文字符串做字符全局替换。只影响TTS播放选择，原始OCR内容、检测框、诊断和证据完整保留。首次提前朗读、完整续读、手动与长文本/实时等所有分支统一使用同一规则；逐框结果必须从OCR引擎完整传入朗读过滤层。真实单字也可能重要，如“停”“药”，建议默认关闭、可由用户按需开，默认值作为建议留待实施验收。数字单字符仍由“不读数字”独立负责。
8. **“过滤疑似乱码与零碎文字 / 只读连贯句子”暂缓，不进入下一轮开发。** 用户已同意先放弃，原因是难以避免把“出口”“小心”等真实短词误删、需要额外复杂判断且可能干扰提前朗读/保真/性能。不得增加语言模型、语义猜字、改写OCR。将来只有用户带真实样本重新批准才评估。

##### E. 不可破坏的底线与验收条件
9. **OCR离线、原文保真、低热低延迟。** 不替换ChineseOCR-Lite DET、PP-OCRv6 Small REC、PP-DocLayout-S或既有权重；不改左右排序，不增加重推理，不修改系统TTS默认语速/引擎选择。保持手机/USB相机预览正常与已发布版本可共存。
10. 验收重点：长文朗读中翻页无触摸时继续旧文；轻触一次立即停旧文且同次触摸请求新页OCR，晚到的旧OCR永不重播；实时新页真实文字确认后自动抢占；连续相同页眉不同正文不漏读；同一页重复句、首批早读接续全文都不重复/漏读；忙时多次轻触仅执行最新意图；触碰非预览按钮不会误识别；不读单字开关时独立“中”“人”不读而“请注意安全”“前方施工”“出口”照读；叮+轻震仅在真正开始新的有效播报前一次，可各自关闭；离线真机对照预览、模型哈希、识别准确、首字延迟/温度和第三方TTS不倒退。
11. 推荐分阶段：①优先修USB并真实验收；②隔离实现识别任务版本及可靠的旧语音取消（实时页切换、长文一触切换）；③画面单击识别并取消底部按钮；④逐框续播/“不读单字”统一入口；⑤轻提示音及震动；每阶段自动+真机A/B，问题不混杂。
12. **GitHub交接纪律：** 既有仓库 vobichngan285-collab/kanzi-assistant；main根目录PROJECT_HANDOFF.md追加历史而非覆盖；新功能从USB稳定基线独立分支研发，不覆盖老APK，每阶段需核实branch/commit/Actions/发布包并记录。本节**只锁定需求，不代表启动新功能修改、发布或后台工作**。


#### 2026-11-05 — REC 加速：只读云端 ARM64/x86 基准与混合批处理候选（不纳入下版）

- **用户原话/范围：** 截图确认在vivo X200 Pro上选了“高质量：4线程／批2”，用户授权云端速度测试但明确不涉及当前/下一版更新清单，结果出来后再决定是否安排未来版本。本轮先跑独立基准，绝不修改现有Dev1.0.1、1.0.2软件源码/模型/朗读逻辑或发布新APK。
- **仓库/实验隔离：** 从已成功构建的Dev1.0.1 Git SHA `51d4061b9f42434bd797781e408aa6ee87dd8bfa` 新建 `exp/rec-cloud-ab-20261105`。脚本 `scripts/bench_rec_cloud_profiles.py`，GitHub Actions `.github/workflows/rec-cloud-ab-20261105.yml`。最终详细研究报告：`https://github.com/vobichngan285-collab/kanzi-assistant/blob/exp/rec-cloud-ab-20261105/docs/rec-cloud-ab-20261105.md`。
- **实际云端执行完成：** 前两轮Actions `41989952233`（整页5配置/分桶）和`41990489990`（加测首批时延），第三轮 `https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41991053101`（加测首2框/后续4框混合）两架构job均成功，并存有可下载原始测量JSON/日志。使用原版PP-OCRv6 Small REC ONNX SHA `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`、ONNX Runtime 1.21.1 CPU ALL_OPT；对照2T/B1、2T/B2、3T/B2、4T/B2、4T/B4、宽度分桶与混合4T/首2后4。仅测试REC inference + batch packing；样本框宽来自先前手机诊断中6框/19框的**数字形状**与额外合成33框负载；**没有把用户实际拍照、文字或真实张量上传GitHub**，输入是固定种子合成像素。3次预热后测量中位数。
- **最新ARM64云端结果（同机三次中位数，ms；6/19/合成33框）：** 当前4T/B2整REC为`120.37 / 393.36 / 733.92`，4T/B4为`93.40 / 264.56 / 497.99`，混合首批2框、其余批4为`89.58 / 272.78 / 522.83`。即**混合相对4T/B2仅在这台ARM64云端**总REC推理时间分别少25.6%/30.7%/28.8%，约1.34x/1.44x/1.40x速度。首批4T/B2为`38.48 / 53.20 / 50.05`ms，混合为`26.63 / 50.19 / 49.79`ms；纯4T/B4首批`61.66 / 63.69 / 63.88`ms。**首2后4保持第一批约同级；直接B4可能延迟提前朗读。**（云端秒级波动、首次预热、不是手机首字延迟）。
- **最新x86对照：** 当前4T/B2整REC `62.13 / 210.33 / 413.07`ms，混合首2后4为`68.47 / 215.61 / 424.61`ms，反而更慢；强硬件依赖，**不能宣布“4T/批4必然更快”**。
- **判断/限制：** 现有代码已宽度分桶、已支持批1/2/4和2/3/4线程；候选只是在SDK内部采用**首2框优先识别，余下同帧文字按宽度近似分桶每次4框**，要求保存原OCR框与阅读顺序，对长文/提前朗读/词典零改动。其潜在代价是更多padding、批处理不同可能改变模型的CTC输出。**没有做文字准确率/CER验证，也没有测得真机延迟/发热/USB帧流或模型推理后OCR文字符合度，不能认为优化已经实测成功。**
- **决策边界：** 当前仅保留独立云端研究报告，**不加入已冻结的下一轮更新计划，不实现、不发布、不自动采用新的批处理配置**。先完成并验证Dev1.0.2摄像头卡死修复；将来用户另行授权可做vivo X200 Pro同帧真实样本4T/B2 vs 首2后4严格离线A/B，逐字文本一致和温控/首批/长文延迟门槛通过后再决定是否上线。

#### 2026-11-05 — Dev1.0.2真实USB冻帧日志：onPause之后原始NV21与GL心跳同时停止（待复现生命周期触发）

- **用户实测证据（原始粘贴日志，不是以往ZIP、不是屏幕截图）：** `Blue Star Guide 1.0.2 USB CAMERA HEALTH`。新一轮会话`usb-health-1793865483953.txt`：在`T+77236`记录`STATE OPENED`，随后从`T+77237`到`T+77271` USB原生NV21回调与GL提交均稳定约29.8-30.8次/秒，1920×1080，EGL错误0、`surface=true`、`OCR=false`。**T+77272记录`EVENT Activity onPause`；直到T+77313再次有1秒样本时，USB+GL末帧距今已约35.7秒，随后T+77314到T+77333连续0FPS，末帧间隔超过55.8秒，期间没有新的OPENED/CLOSED/ERROR日志。**前一会话`usb-health-1793865458491.txt`亦稳定约30FPS，`T+77233 onPause`，第二会话`T+77236 OPENED`。强烈提示**应用生命周期 / 返回前台后的USB流恢复问题**，并未证明相机硬件故障或OCR压力造成停帧。
- **关键时间解释：**`T+77313 RAW=4.3 GL=4.3`不能理解为“故障期间仍有4.3FPS”；因为监测ticker在Activity `onPause`时暂停，到恢复后跨约41秒以较长采样间隔计算，且末帧已经在35.7秒前。不能精确断言相机在`onPause`瞬间停了：末次实际raw在约`T+77277`，即onPause若干秒之后，但**回到前台后监控恢复采样而原始流、GL都没有恢复**。
- **源码复核：** `exp/blue-star-usb-health-102-20261105` `MainActivity.onPause`清除监测ticker、停止手机Camera2、清空语音并`super.onPause()`；`onResume`重启监测ticker，**只对手机内置相机显式`startPhoneCamera()`，对USB没有显式确认/补救**。上游AUSBC `ernestp/AndroidUSBCamera@3.6.0` 的`CameraActivity`/`BaseActivity`没有`onPause`/`onResume`的USB重连逻辑；相机注册通常依赖`TextureView.SurfaceTextureListener.onSurfaceTextureAvailable`，注销依赖`onSurfaceTextureDestroyed`；所以**SurfaceTexture对象仍显示available并不能保证UVC视频流仍活跃**。onCameraState只记录OPENED/CLOSED/ERROR；这次停流无新状态事件。`GL提交`监测仅记尝试而非物理屏幕呈现。
- **鉴别诊断尚需用户一个关键信息：** 本次`T+77272`附近是否主动按Home、切换到其他APP、息屏/锁屏、收到电话或弹窗？如果确实切换前后台，则最先单独验证和修复**前后台切换后的UVC恢复与生命周期**；如果当时没有主动切出，需查系统意外进入onPause、窗口焦点/权限变化。另一独立实验是在屏幕常亮、只前台正常使用、不进后台时，是否仍会卡死；不可把所有冻帧都归因于`onPause`，因为尚无未切后台的完整崩溃日志。
- **下一步建议（尚未写任何修复代码）：** 用有明确enter/exit的`onPause/onResume`事件监测并配套原始帧心跳及`isPreviewed`/USB Camera状态，确认前后台转换；查清SurfaceTexture销毁/重建或未销毁时流停止。若真是生命周期原因，设计**恢复前台后、仅当USB连接且监测确认连续缺帧**的受控重建USB采集/Render session，带超时/限次/退避，确保不会在OCR忙或尚在初始化期间循环重启；不要先引入无差别定时重启。留存1.0.2原APK并从独立分支做专项测试。OCR模型/左右阅读顺序/TTS和下一版冻结需求均不改。

#### 2026-11-05 — 用户澄清：USB卡死时没有主动切后台/锁屏，撤回自愿后台切换的因果推断

- **用户明确更正：** 对上一份真实Dev1.0.2 USB冻帧日志，用户回答“没有切换”，即故障过程没有主动切换其他APP/切到后台。不得将`T+77272 EVENT Activity onPause`解释为用户主动离开软件，更不能据此直接断定“后台恢复失败”就是根因；系统层窗口/权限/外部覆盖等也可能触发`onPause`，仍需证据。
- **这份日志的确认事实：** 先前约30fps的NV21及GL提交停止并持续超过55秒；OCR空闲、EGL错误计数0，但0次EGL错误不意味着GL绝对正常，因为没有新帧时可能根本没有交换调用。单一上游UVC停止可导致同源GL提交也变0，两个计数并非严格独立故障。日志先看到`T+77272 onPause`，随后监测ticker自身停止，`T+77313`恢复采样但未收到新帧；缺少`onResume`记录、Activity`onStop`/`onStart`、`onWindowFocusChanged`、`TextureView.SurfaceTextureListener`及USB设备attach/detach等事件。故**现存监测数据不能确定哪一个事件最先导致采集停摆**。
- **下一步分析/诊断修正建议，非已实施：** 在隔离USB专项分支先补全生命周期和Surface/USB控制器事件时间线，重点添加`onResume`和`onStop`事件及USB帧最近时间的连续、低负担取样，避免监测停止导致的约41秒盲区；加入UVC设备attach/detach/权限/控制块事件及USB状态检测，分辨app暂停、USB传输、native callback、GL渲染。必要时单独对屏幕常亮连续前台运行做一次复现验证。只有在证据证明USB会无错误停流时，再考虑受控、限次且有退避的重建UVC会话，绝不一律靠循环重启。尚未改功能APK、OCR模型、下一版已冻结功能清单。 

#### 2026-11-05 — 新对话接手摘要：USB真机双路停帧后的工作优先级（截至16:12 UTC+8）

- **本次交接原始诉求：** 用户要转到新ChatGPT对话，要求总结交接日志。下一位接手**先读本文件的最后四节**，用户已经明确签字确认的下一轮朗读交互需求、REC云端加速实验仅作备忘；当下仍聚焦USB卡死。不要让用户再次回答“当时是否切到后台”；用户已明确回答“**没有切换**”。
- **最新已核实GitHub状态：** 项目 `vobichngan285-collab/kanzi-assistant`；主交接 `main/PROJECT_HANDOFF.md`，追加式保留历史；基础稳定构建Dev1.0.1分支 `dev/blue-star-guide-1.0-20261105` SHA `51d4061b9f42434bd797781e408aa6ee87dd8bfa`；**已实包发布、未修复USB冻帧**的USB监测实验分支 `exp/blue-star-usb-health-102-20261105` SHA `f96d1a8cfe8a1a49f49e5dfd2eb08f5758be3737`，Actions `41982844999`成功；APK `https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-usb-health-dev1.0.2-20261105/BlueStarGuide_Dev_1.0.2_USB_Health_arm64.apk`。OCR云端仅研究分支 `exp/rec-cloud-ab-20261105` SHA `099aa9b3a4ab574bfade800497a5aabd45f3a88c`，最后模型性能Actions `41991053101`成功；具体 `docs/rec-cloud-ab-20261105.md`。所有云端性能数字**不等于vivo X200 Pro真机提升**，用户明确要求**不列入下一版**。
- **已拿到的第一份真实USB故障日志：** 蓝星向导Dev1.0.2健康状态：`T+77237`至`T+77271`，UVC NV21与OpenGL Swap Attempt均约30fps、1920×1080，OCR=false、EGL errors=0、surface=true；`T+77272 EVENT Activity onPause`，**用户没有主动切换软件/锁屏**；监测ticker在pause时停止采样，后续`T+77313`再次采样时两侧已停约35.7秒，`T+77314`至`T+77333`连续0fps，末帧距今超过55秒，未记录新OPENED/CLOSED/ERROR。故能确定原始NV21回调及GL提交同时停止，不能推断是OCR、GL单独故障、用户切后台或USB硬件哪一个先出问题。也不能把`T+77313 RAW=4.3`错读为故障中仍有4.3fps：这是跨长暂停样本的均值。
- **正在等待技术定位，不是等待用户回答旧问题：** 查Android生命周期为何自发`onPause`；源码 `MainActivity.onPause`删监测ticker、停止TTS/手机相机，而`onResume`只针对手机相机做`startPhoneCamera()`，上游AUSBC CameraActivity依赖TextureView surface与USB attach/connect注册；现有日志缺`onResume/onStop/onStart/onWindowFocusChanged`、SurfaceTexture available/destroyed、UVC USB attach/detach/permission/ctrlBlock 等事件，且存在约41秒的监测盲区。建议先在USB**独立**分支补生命周期与UVC心跳，不修改OCR/TTS/阅读顺序。将单一UVC断流导致GL也停的联动与真实GL故障区分开；若充分证明USB无错误停帧，才设计**条件触发、限次退避的受控重连**而非循环补丁。
- **接下来新版本已敲定但未开发**（详见上一节完整规格）：实时识别确认新页立即停止旧页语音，长文本模式不自动打断但用户**轻点预览一次**立即停旧语音、用点击后新帧识别并续播；取消底部“识别一次”按钮；首次真实文字准备播报时短促“叮”+轻微震动一次且可分别关闭；过滤设置新增独立成框单汉字的“不读单字”（仅TTS，不篡改OCR原文）；配套修早读字符串删重导致的重/漏读，使用box/frame ID统一入队状态；**“过滤疑似乱码/零碎文字”已确定搁置**。用户明确要求**USB修复并验证稳定后**才开始这些工作。
- **OCR速度专项研究已完成但只保留：** vivo X200 Pro当前用户所选`高质量4线程/批2`。云端Linux ARM64同模型PP-OCRv6 Small REC CPU研究找到`4线程、首批2框、后续批4框`的候选，6/19/模拟33框REC推理总耗时较云端4T/B2低约25.6%/30.7%/28.8%，x86对照反而略慢，未测准确率、真机、温控，绝不能擅自开发/合并/宣布提升成功。等USB/朗读优先工作完成、用户再次批准才单独真机离线A/B。
- **工作纪律：** 用户要求真实源码核实、逐项可回退、少讲空话；禁AI幻觉、禁改写OCR原文、OCR离线、低延迟低发热、冻结左右串读排序策略，保持CN-Lite DET、PP-OCRv6 Small REC和DocLayout-S权重；不要为了提升性能或新功能破坏现有识别。每次阶段性重要进展追加main根目录`PROJECT_HANDOFF.md`，不得覆盖历史；交付核对commit、Actions、独立APK/版本链接；不许宣称在后台持续工作。

#### 2026-11-05 — USB 专项继续推进：独立1.0.3多级采集与关闭来源追踪版（诊断成功构建；尚非修复）

- **用户现场新证据与对照：** 同一部vivo X200 Pro及同一外接UVC摄像头，第三方“USB摄像头”软件的屏幕显示`60.2 FPS MJPG 1920x1080`，用户多次反馈第三方软件连续运行流畅、未出现卡顿；蓝星向导1.0.2在`1920x1080 MJPEG`、UVC上游经`scripts/patch_uvc_resolution.py`设置最高30 FPS时，正常期raw/GL各约30 FPS，偶发原始回调和GL提交均停/灰屏或局部图像。第三方报告帧率支持其预览更流畅的观感，但不能推断真实上屏帧率或把30 FPS限幅认作停流根因。本轮**不提高帧率**。
- **区分两组相机故障：** 无主动切换软件的早期故障出现异常`onPause`且后续持续0fps；另一轮用户明确主动切到其他软件再返回的日志：`T+79522～79581` raw/GL约30 FPS，`T+79582`和`T+79585`两次`onPause`，`T+79584`仍约30 FPS；`T+79598`记录raw/gl末帧已停约8.3秒（`11.7 FPS`系跨长间隔计算平均，不能当作当时活帧）；`T+79599`约1 FPS，`T+79600` 15.7 FPS，`T+79601`恢复约30 FPS。另一个旧日志出现`STATE CLOSED`，但缺关闭请求前的事件，不能归因于物理USB断开。**有意前后台切换与无操作停流必须分开复现，不可混为同一根因。**
- **源码可核实的性能风险（未确认为根因）：** 固定上游`ernestp/AndroidUSBCamera@3.6.0`的`CameraUVC.kt`原生帧回调每帧`ByteArray(capacity())`并复制NV21，调用上层预览回调，即便APP的OCR状态为闲也可能持续发生分配，存在内存分配、GC/带宽压力假设。尚未进行真实GC、丢帧及温控关联实验；不得以此直接宣称卡死已经定位，更不得在无证据下替换buffer生命周期。
- **严格隔离的开发产物：** 从已证实Dev1.0.2源码SHA `f96d1a8cfe8a1a49f49e5dfd2eb08f5758be3737`建立`exp/blue-star-usb-trace-103-20261105`，最终SHA **`a114e6c6785322f9ac94e2aa2cf9f103c81ac4ec`**；Dev1.0.1、1.0.2原源码和发布APK均未覆盖。独立包`com.vobich.kanzi.usbtrace.dev103`，versionCode103，`1.0.3-development`，桌面名“蓝星向导 USB追踪”。
- **新增纯诊断链路：** `scripts/patch_usb_trace_103.py`对上游AUSBC 3.6.0进行唯一锚点校验后，仅加入原生UVC->Java frame callback入口心跳、RenderManager `onFrameAvailable`（GL输入）心跳，并与已有APP NV21回调及GL swap attempt四级计数对照；增加USB ATTACH/DETACH/CONNECT/DISCONNECT/CANCEL、TextureView Surface AVAILABLE/SIZE/DESTROYED、UVC `closeCamera()`调用栈（调用者和行号）、STOP_PREVIEW接收事件；`MainActivity`独立低负担定时线程每4秒记录raw/uvcJava/glInput/glSwap各自数量/距今间隔并持久保存，贯穿`onPause`期间；记录`onStart/onResume/onPause/onStop/onDestroy/onUserLeaveHint/onWindowFocusChanged`与焦点/surface/设备数量快照。没有相机自动重连或重启，没有更改采集帧buffer、USB预览缩放、原模型权重、OCR分辨率、TTS或冻结的双页排序。日志不含原始图像、文字或声音。
- **真实CI：** 新workflow `.github/workflows/blue-star-guide-usb-trace-103.yml`，最终Actions **`41999402451`成功**（`https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41999402451`）。先前尝试中，旧的版本字面量守卫及工作流APK审计Python缩进导致失败；已针对测试版号、审计缩进修正，最终完整流程通过 Android Gradle assembleDebug、CPU模型及arm64/无旧模型校验、APK签名/模型资产/诊断代码审计，GitHub Release已实际发布。源码静态/CI不能代替vivo真机停流复现或证明摄像头已修好。
- **发布链接：** Release `https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-usb-trace-dev1.0.3-20261105`；独立APK `https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-usb-trace-dev1.0.3-20261105/BlueStarGuide_Dev_1.0.3_USB_Trace_arm64.apk`。实际GitHub发布附件大小79,366,148 bytes，SHA256 `53066385ca627e21ac87858f3eae1dd9a4946c8d36617f84fd7aafde87bde30a`。
- **下一步只做USB真机诊断：** 在1.0.3独立包连接外接摄像头，先保持屏幕常亮/前台、不主动切换，连续运行直至复现；出现停帧/灰屏立刻点击顶部状态条复制当次及历史日志，重点收集`PIPE uvcJava/appRaw/glInput/glSwap`与`AUSBC_EVENT_TIMELINE`的`UVC closeCamera REQUEST`调用来源及SURFACE/USB事件；另独立做“切换其它App再回来”对照，不要混淆。按不同层级何时停来精确定位并提出最小修复，再做真机回归。**不擅自开展冻结朗读需求、REC云端候选或30→60 FPS调优；无异常现场前不宣称USB卡死已解决。**

#### 2026-11-05 — USB Trace 1.0.3 真机反馈：返回前台后四级帧率同步降到约7 FPS（尚未确认根因）

- 用户提交蓝星向导`Blue Star Guide 1.0.3 USB PIPELINE TRACE`完整复制日志。首次本次会话 `T+81924` OPENED、1920×1080，初始`uvcJava/appRaw/glInput/glSwap`持续接近30fps，`EGL errors=0`；`T+81946`、`81956`有`Activity onUserLeaveHint/onPause/onStop`、之后又`onStart/onResume`；`T+81960`再次`onStart/onResume`，`T+81961`窗口焦点回到true，`T+81961/81962/81963`主监测原始帧/GL提交分别为22.5/8.0/7.0 FPS，OCR均空闲。
- 定频独立采样：`T+81960 PIPE appRaw=956 uvcJava=956 glInput=956 glSwap=956`（四项lastAge约277-284ms）；`T+81964 PIPE appRaw=984 uvcJava=984 glInput=984 glSwap=984`（四项lastAge约327-329ms）。**4秒四级各只新增28帧≈7 FPS**，证明确实出现输入侧帧回调吞吐下降，不是仅GL画面提交或UI数字显示。这里`uvcJava`是UVC库Java IFrameCallback入口，**不是USB控制器/本地UVC设备的真正最底层硬件传输计数**，尚不能区分驱动、MJPEG解码/转换、callback处理竞争等更上游环节。
- `AUSBC_EVENT_TIMELINE`记录`USB ATTACH id=1002`、`USB CONNECT ctrlBlock=present id=1002`、`SURFACE AVAILABLE`、`SURFACE SIZE 2240x1260`、`UVCCamera startPreview returned`；在这次降至7FPS附近**没有记录**`USB DETACH`、`USB DISCONNECT`、`SURFACE DESTROYED`、`UVC closeCamera REQUEST`、`STATE CLOSED`或EGL swap failure。因而**本次不支持“因显式close或Surface被销毁导致停流”的判断**；但事件未出现不等于可排除所有USB、内核或解码层异常。
- 注意`T+81948`至`81952`appRaw计数由718增长至753（4秒约8.75fps，发生在不处于前台期间），说明切换软件期间也有减速迹象；`T+81952`至`81956`又增加94帧，恢复过程并非完全连续停流。用户以前的“未主动切换仍卡死”和这一次显示的多次`onUserLeaveHint`必须分开分析，不要推定每次都是人为切后台，也不要推定二者必为同一根因。
- 当前优先排查：1）UVC Java回调上游的采集/解码/帧转换耗时、消息队列/线程调度；2）后台/前台切换后UVC会话继续运行时的资源竞争；3）每帧NV21分配/GC假设，需增加实际耗时/GC或队列压力证据才能采纳；4）局部灰屏单独验证显示层真实画面刷新。注意四级心跳高度同步**只能把首次可观测异常定位在uvcJava入口或更上游，不能进一步断言原生USB带宽、供电、解码还是UVC回调线程**。
- 当前阶段**没有修复**，不改OCR权重、不改冻结的左右排序/朗读计划、不引入REC云端加速、不提高到60fps。仅研究和真机定位。后续捕捉5~10分钟连续前台、出现卡顿立刻保存日志，与主动前后台切换对照。本条追加保留之前日志，未创建新APK。

#### 2026-11-05 — 蓝星向导「实验版1.0.4」：1080p MJPEG最高帧率61＋文字合焦后尝试锁焦＋轻度预览鲜明色彩

- **用户授权与阶段定位：** 1.0.3真机长时间保持前台未复现USB卡顿，用户决定暂放后台切换相关USB异常，明确批准生成独立安装的下一版，桌面名称设为“蓝星向导 实验版”，验证61 FPS上限、文字检测后自动对焦/锁焦，以及更鲜明的预览色彩。此前聊天中讨论的曝光增强策略仍未确定；明确不擅自实施。**此版本是试验版，不等于已解决USB卡顿、真机已稳定60 FPS或已验证硬件锁焦。**
- **来源与隔离：** 从`exp/blue-star-usb-trace-103-20261105`最终验证SHA `a114e6c6785322f9ac94e2aa2cf9f103c81ac4ec`新建`exp/blue-star-experimental-104-20261105`，最终提交SHA `466d01131b7d0f87a7c2aafb79627b0e1581a14a`；`app/build.gradle`中versionCode=104、versionName=1.0.4、debug`applicationIdSuffix '.experiment.dev104'`，APK实际独立包为`com.vobich.kanzi.experiment.dev104`。桌面Android Manifest label为`蓝星向导 实验版`。未覆盖已有Dev1.0.1、1.0.2、1.0.3和原发布APK。保留1.0.3四级视频心跳、UVC关闭调用链记录。
- **61 FPS上限：** `scripts/patch_uvc_resolution.py`仍只在最大1920×1080范围内选择已列出的模式，**不再把固定AUSBC 3.6.0 `CameraUVC.kt` 的 `MAX_FPS` 从原生61降为30**；保留MJPEG请求及原有模式回退。CI显式断言上游`private const val MAX_FPS = 61`，拒绝意外旧版或30上限。61只是与摄像头协商可用模式的上限，非实际每秒必达61；NV21回调、GL和CPU开销可能增加，必须真实设备测fps、耗电、温度及OCR延迟。
- **自动对焦/锁焦（非光学变焦）：** 新增纯Java `UsbTextFocusPolicy.java`及`UsbTextFocusPolicySmokeTest.java`。原UVC启动时已执行`autoFocus=true`；当前实验版只在**真实OCR结果有至少2个框、均值置信度至少0.58、原帧亮度45-225、抽样边缘强度至少8、OCR结果场景与当前场景相符、对焦至少经过650毫秒**后尝试`setAutoFocus(false)`并读取`getAutoFocus()`确认驱动报告关闭AF，从而尝试保持当前镜头焦点；无自动对焦可读状态或写失败时不强制锁。实时场景持续变化600ms且锁定已超过1200ms，或Activity返回前台时，重新设置`setAutoFocus(true)`。所有实际UVC控制操作从UI线程调度，非视频回调线程；有session防旧请求串入；状态变更只记录不含图像或OCR原文的诊断事件。该功能依赖外置设备对UVC AF关闭后保持当前焦点的真实支持；**禁用AF不等于物理防抖**，不能声称无论晃动都不会运动模糊。未主动控制设备光学变焦数值，避免裁掉文字。翻页重对焦当前只在实时自动识别的场景变化观察器可用时生效，长文模式保持既有扫描流程。
- **画质色彩：** Android API31+在USB`TextureView`应用仅显示层的`RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(...))`，饱和度1.07（轻度＋7%）；默认开启，设置界面支持关闭。没有在UVC硬件或原始`NV21`、OCR输入、裁剪、模型、文字结果上加滤镜，未改变手机内置Camera2预览，也没有添加磨皮或合成内容。必须真机确认TextureView滤镜实际生效且不致GL帧率下降；亮度/曝光/增益未改。
- **检验和发布：** GitHub workflow `.github/workflows/blue-star-guide-experimental-104.yml`，Actions run **`42005871188`**：官方PaddleOCR模型审核、原排序/朗读/手机预览守卫、纯Java锁焦策略单测、Gradle assembleDebug、APK签名+包名+无INTERNET权限及模型资产哈希、release upload等关键构建步骤均验证成功；最后需以run终态success核对。原先两个旧测试守卫写死1.0.3名称/方法相邻顺序导致临时失败，已按保留原约束调整，终版重新通过。实际GitHub prerelease标签 `blue-star-guide-experimental-dev1.0.4-20261105`，独立APK下载 `https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-experimental-dev1.0.4-20261105/BlueStarGuide_Experimental_1.0.4_arm64.apk`，GitHub附件已证实上传，大小79,382,532 bytes，SHA256 `b9603ade68fb0c739289a232ff2486a8104c2a7c8cc9d6803f6ca4feea421852`。
- **真机验证计划：** 首测USB预览无OCR，30秒内检查是否稳定约60 FPS而非仅目标上限，并检查是否出现灰屏、停流。再启动OCR，对准连续文字观察是否有“EXPERIMENT AF LOCK”诊断事件；固定镜头下观察有无频繁焦点抽动，翻页后观察“EXPERIMENT AF REFOCUS”，再对比新旧版本识别漏错、帧率和温度。设置中切换轻度鲜明色彩观察仅预览变化、不影响原始OCR；若60FPS使OCR变慢或卡顿，先保持旧版独立包作为回退。勿宣称已通过真实A/B。
- **过去冻结需求仍保留，不能误报已实现：** 新文字确认后立即打断旧TTS、点预览一次识别替代“识别一次”按钮、长文单次操作取消旧页并重新读、可配置朗读前短叮声与震动、仅TTS忽略独立单字汉字、提前朗读采用真实框ID避免重复或漏读；此前明确暂不修改的左右文字串读/排序保持原状。曝光策略及GC/帧buffer方案也未经用户新一轮实验确认。下一轮所有变更需离线、保真、不幻觉、低发热且先A/B，按原有`main/PROJECT_HANDOFF.md`追加，不覆盖历史。

#### 2026-11-05 — 第三方USB摄像头APK采集/MJPEG/帧队列/GL链路对比与1.0.5硬件能力实验状态

- **第三方原始证据**：用户上传独立APK `1f774a753bdeff73ba349aa3dbf3412e.apk`，大小`91,344,733` bytes，SHA256 `9f0568bf00872f42688dfa6926c1a826c08d85f9a753dc850313cbdf9705eda3`。静态解包仅做二进制符号和DEX标识检查，未运行该APK、未获取私有源码、未将其专有代码移植到项目。第三方 `libUSBVideo.so` 动态引用libusb/JPEG/EGL/GLES/ANativeWindow，内含Y/UV纹理的GL shader文本、`nativeSetRawCallback`与`nativeGetCurrentFrameRate`等接口，libjpeg-turbo版本标记3.2.0；另有`USBCameraService`。这些仅证实相关**能力存在**，不足以确认摄像头实跑数据通路、零拷贝、硬解或后台稳定根因。
- **项目固有的可测瓶颈**：固定上游 `ernestp/AndroidUSBCamera@3.6.0` 的`UVCPreview.cpp` 1080p MJPEG路径：`uvc_mjpeg2yuyv`后`uvc_any2rgbx`给预览，`addCaptureFrame`另送原生YUV回调；`CameraUVC.kt` 因APP的`setRawPreviewData(true)`，每个回调均运行 `ByteArray(capacity())`和`get(data)`，放入容量5的`mNV21DataQueue`，再到APP `previewCallback`。哪怕OCR未启动也持续复制。1920×1080 NV21=3,110,400字节/帧；30FPS约93.3 MB/s、60FPS约186.6 MB/s的**理论Java层新数组分配**，未含本地格式转换/GC额外耗费。此为源码证据，不等于已证明造成掉帧。
- **潜在GL任务堆积**：`RenderManager.kt.onFrameAvailable` 每帧发送`MSG_GL_DRAW`，没有可见的待绘任务合并标志。是否积压必须真机测量，不能单靠源码断定。
- **颜色格式严谨核对**：`UVCCamera.java` 常量4注释写NV12，但固定native`UVCPreview.cpp` 对4转发`uvc_yuyv2iyuv420SP`，`frame.c` 确实按VU顺序写出（NV21）；因此当前APP将帧标记NV21**与当前native数据吻合**。不能由Java注释把颜色偏差认定为UV错序，需同场景A/B对比白平衡、YUV->RGB矩阵/范围、饱和度和显示差异。
- **现场与静态证据边界**：用户观察第三方1920×1080 MJPG显示约60.2fps且长时间更流畅，仍需双方相同模式的真实fps、GL上屏、OCR占用、GC、CPU温度测量。第三方原生架构同样含libusb/软件JPEG，不可推断仅凭turbo版本高或Service即领先。**首推独立减负实验：OCR不需要时不要把每帧原生图像都转换成Java NV21；OCR运行时保持有限频率的最新真帧，并保护异步OCR占有的buffer不被覆盖。** 切勿在每帧之间反复设/清原生callback，以免capture同步互锁。其次单独A/B测试GL draw事件合并，勿同时混改。
- **完整证据报告**：`exp/blue-star-hardware-controls-105-20261105` 分支 `docs/usb-thirdparty-pipeline-audit-20261105.md`，已提交SHA `9d0f0254c82ad73ceae35e038338ebfa9e3fe74c`。这轮**只研究和记录，不生成摄像头优化APK**；既有1.0.5实验版独立源码/发布不覆盖。
- **先前1.0.5状态核实**：独立实验版1.0.5 `exp/blue-star-hardware-controls-105-20261105` 的经CI构建源码SHA `0099004c7ef51003a1e5caab9f802440e7c721cf`，Actions `42023363874` 已success；Release `https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-experimental-dev1.0.5-20261105` 实际存在。新增只读UVC AF/focusAbs/zoomAbs/exposure/brightness/saturation能力查询，并只在设备声明自动对焦支持时尝试锁焦。**尚未获得硬件真机能力日志，也未在1.0.5实施帧内存复制优化。** 原OCR、朗读和用户先前冻结需求未改。

#### 2026-11-05 — 蓝星向导「实验版1.0.6」原生NV21回调20FPS减负A/B（已完成CI，真机待测）

- **承接依据：** 用户授权开始对比研究第三方USB摄像头APK与蓝星向导后，进一步授权启动下一步性能实验。前一条`docs/usb-thirdparty-pipeline-audit-20261105.md`发现 `ernestp/AndroidUSBCamera@3.6.0` 每帧原生YUV->NV21转换与Java `ByteArray(capacity())` + `get(data)` 复制，即使OCR不运行也产生不必要的开销。此次**只改一项核心变量：原生OCR支路20FPS上限**，严禁同时改GL消息合并、解码器、曝光、镜头自动变焦、OCR和朗读逻辑。仍要求X200 Pro真机A/B验证。
- **代码与隔离：** 新实验分支 `exp/blue-star-native-raw20-106-20261105` 从已成功构建的1.0.5分支起步；独立实验包 `applicationId=com.vobich.kanzi.experiment.dev106`，versionCode=106，versionName=1.0.6，桌面“蓝星向导 实验版”，可与1.0.5并存，方便AB和回退。源码构建SHA `3a8a718fc91af9c7feaf8a84f8f9f7c2492d8ff3`。分支另有说明文档 `docs/usb-native-raw20-ab-20261105.md`（提交`a296a21903609ef9a378c743244e261b1823e8c1`）。
- **具体实现：** 新`scripts/patch_uvc_native_raw20_106.py` 在CI内严格匹配上游3.6.0的`libuvc/src/main/jni/UVCCamera/UVCPreview.h/.cpp`。在`UVCPreview::do_capture_callback`的**native YUV->NV21转换和Java回调之前**加入按`CLOCK_MONOTONIC`的50,000,000ns间隔闸门，只有`mFrameCallbackObj && mFrameCallbackFunc`时对这个回调支路生效。跳过的帧严格`recycle_frame(frame)`；每个`UVCPreview`实例单独记最后回调时间。源码的MJPEG USB采集、`uvc_mjpeg2yuyv`解码、RGBX屏幕预览与OpenGL绘制不做减帧；仍使用1080p MJPEG、最高61FPS协商上限。**这不意味着手机会实际稳定获得60FPS**，只意味着NV21/OCR支路最多约20FPS；回调时间间隔/源帧率会令实际约15–20FPS。
- **内存收益是理论估算，非实测：** 1920×1080 NV21为3,110,400 bytes/帧；假设原路径60FPS每帧新Java数组约186.6MB/s，实验限流20FPS上限后约62.2MB/s，最多削减三分之二的Java整帧数组分配次数及native NV21转换次数。**仍执行60FPS预览的JPEG解码和RGBX渲染，未实现零拷贝，也不保证耗电、CPU、GC、识别准确率改善**。部分短时快速移动文字可能因取样变稀而错过清晰帧；真机结果若恶化则回退1.0.5。
- **四级监测解读：** 1.0.6 `uvcJava/appRaw`约15–20FPS和`glInput/glSwap`约30–60FPS是实验的预期；相对于1.0.5原始帧和GL接近1:1，不要将其直接判为断流。UI附加`原生OCR取帧≤20FPS（预览仍可到约60FPS）`说明。原1.0.3 USB事件日志、UVC硬件能力查询`USB_CTRL_CAPS`、AF锁焦实验、预览轻度鲜明、既有离线OCR和TTS全保留。
- **CI硬证据**：`.github/workflows/blue-star-guide-native-raw20-106.yml` run `42028438516` 已终态`success`。经过`scripts/test_uvc_native_raw20_106.py`静态与10/15/30/60fps数值模拟测试、完整安卓NDK/Gradle构建、旧OCR排序/TTS和相机预览守卫、arm64单独APK与签名、无INTERNET权限、资产模型SHA256检查。额外在`UVCPreview`构造记录唯一日志标记`UVC_NATIVE_OCR_RAW20_GATE_ENABLED`，CI打开**实际生成APK**中的`lib/arm64-v8a/libUVCCamera.so`断言包含该标记，证明不是只改源码没打进二进制。
- **真实下载**：GitHub prerelease `https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-experimental-dev1.0.6-20261105`，APK `https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-experimental-dev1.0.6-20261105/BlueStarGuide_Experimental_1.0.6_arm64.apk`，已上传资产79,382,528字节，SHA256 `3b769bd8ee11f8d887056eb26b745c3ff7827e6263c23d14d72c67f205181d92`。
- **下一步严格真机A/B**：用户同场景安装并单独运行1.0.5/1.0.6（不能两个APP同时占用同一USB摄像头），观察不开OCR时的GL/RAW帧率，静止书本/商品/招牌识别率、轻晃/翻页时首次读到新字时间、是否漏识、连续5–10分钟热度与卡顿。出现OCR明显掉字或延迟则回退1.0.5；保留原版本独立安装，不替换main的已确认APP。尚无真机实测CPU/GC/FPS结果，不宣称已修复USB后台问题或提高了OCR识别率。下一轮若收益确实存在，再单独设计buffer池或GL绘制合并，不将诸改动混在当前A/B中。

#### 2026-11-05 — 豆芽看见APK底层OCR模型、行框/朗读链路的独立静态取证
- **来源及保存位置：** 用户上传并授权研究的豆芽看见APK：299,923,710字节、SHA256 `9e40d9993c12c3b3f3b99b1f9f4d3e3108141c4b6619a21a1aba64d525a7cb38`。独立研究报告位于`exp/blue-star-native-raw20-106-20261105/docs/douya-kanjian-ocr-static-audit-20261105.md`，提交`3437857fdfb9289cac303f3ef8a37e28573051ea`。仅静态解压/查ELF符号/DEX方法/NCNN模型结构；**没有运行第三方APP、没有提取或移植专有代码，也没有修改蓝星向导原代码**。
- **实锤模型与推理框架：** 安卓包`libpaddle_ocr.so`直接依赖`libncnn.so`；Flutter界面`libapp.so`。本地核心是`PP_OCRv6_small_det.ncnn.bin`（4,929,604字节）负责**先检测文字区域**，`PP_OCRv6_small_rec.ncnn.bin`（10,562,152字节）负责识别；`PP_OCRv5_mobile_cls.ncnn.bin`（260,952字节）做2分类文字方向，`PP_LCNet_x1_0_doc_ori.ncnn.bin`（3,355,348字节）做4分类整页方向。它的JNI/C++内部类虽然叫`PPOCRv5`，ELF日志字面量明示`PPOCR initialized (model=v6)`；**不要据旧类名误判为PP-OCRv5 DET/REC**。v6 DET末端Sigmoid一通道概率图，v6 REC末端Gemm为18710类别＋Softmax。
- **处理链证据：** `com.doyamate.app.PaddleOcrPlugin`注册Flutter `paddle_ocr_plugin`通道，支持`recognizeText`（`imageData,width,height,format=nv21,rotation,detectionBoxThreshold`）、`recognizeTextFromFile`、`classifyDocOrientation`、初始化/释放。JNI`nativeRecognize(long,byte[],int,int,String,int,float)->Map[]`，说明输出是多项结构化文本/框结果。Native导出`PPOCRv5::detect/classify/recognize/detect_and_recognize`且有OpenCV `findContours/minAreaRect`，符合**检测图→轮廓/旋转文字行框→按框裁剪识别**。Dart AOT只露`TextBox`、`ReadingOrderTraversalPolicy`、`OcrReadDecision.`、`deduped=`、`ocr_orientation_angle`等字符串，支持排序、朗读决策、去重/角度机制存在，但**无法证明具体聚类/稳定判定公式和阈值**。
- **功能分工**：APP自带英文“Instant text”说明明确称短文字连续识别、在清晰稳定时朗读、本机离线可运行；另有单独“Document reader”引导整页对准自动拍照，用于长文。其`detection/document_v2_0_0.tflite`、斑马线/交通灯TFLite模型是其他任务，**不是**隐藏的专用文本行画框模型。APP另含`apiOcr`、`v1/ocr`等服务字符串，不把全部APP功能概括为百分百离线。
- **对蓝星向导的最重要启发：** 对方不是靠完全不同的神奇文本检测模型，而是和本项目同一PP-OCRv6家族的DET/REC，但它实际使用**v6 Small DET + v6 Small REC的NCNN实现**。本项目当前主检测是ChineseOCR-Lite DET、v6 Small DET为回退，REC为v6 Small ONNX；差异可能在检测器选择、原图缩放、检测阈值、轮廓扩张、斜框裁剪、整行合并、方向和朗读去重。优先**同图同条件的DET框完整度/漏检/行序A/B**，之后单独比较读取策略，不要一口气替换所有模型/混入复杂修补逻辑。尚无真实速度和效果的运行测量。

#### 2026-11-05 — USB 1080p 60FPS协商与GL绘制短时卡顿实验 1.0.7（仅测试构建，未发布）
- **源分支/版本：** 从实验1.0.6独立分支创建`exp/blue-star-usb60-stutter-107-20261105`；应用`com.vobich.kanzi.experiment.dev107`、versionCode 107、versionName 1.0.7-development；不覆盖旧版。主代码构建SHA `d94a74218ef54c1d0e717ec738090dac7dbbf1ea`。详细实验报告：`docs/usb-fps60-gl-stutter-107-20261105.md`，分支文档commit `bb27c9dfd47ea4568dbc07e01879746a97863f0b`。
- **已证实的底层逻辑缺陷与独立修复：** 上游AUSBC 3.6.0 `UVCPreview::setPreviewSize` 原先只对宽高/格式改变才发起新FPS协商；旧1~31→新1~61FPS但宽高格式相同时可能漏协商。现把min/maxFPS及带宽纳入变更检测；上游`libuvc stream.c`按设备帧间隔列表首次符合即停（如30在60前时误选30），现选择**同一合法MJPEG格式分辨率下最短有效间隔，即最高受支持帧率**，60不支持时仍安全选较低值。实际协商的fps在`logcat`留有`UVC_FPS_NEGOTIATION_107`标记，**最大上限61不等于实际60FPS**。
- **短时卡顿缓解：** 上游`RenderManager.onFrameAvailable`每帧无上限加入`MSG_GL_DRAW`，新版增加`mGlDrawQueued/mGlDrawDirty`单槽排队与画面更新dirty再调度；保留`updateTexImage`、GL swap/错误统计和Surface生命周期，避免处理落后时积压大量无效旧绘图任务。不修改原生MJPEG→RGBX显示算法；不卡顿/达到60仍待用户真机验证。
- **不变项：** 1.0.6原生OCR NV21每50ms限流仍在（USB约30FPS会表现RAW≈15FPS，而GL可独立30或60），PP-OCRv6/ChineseOCR-Lite DET/REC、TTS和阅读顺序不变；不改摄像头旋转/镜像/FIT、自动对焦和曝光，不声称已修复右侧灰色区域。不新增联网能力。
- **构建与验证：** GitHub Actions run **42055034691 SUCCESS**，签名v2、APK实际原生库含旧RAW20和新`UVC_FPS_NEGOTIATION_107`、DEX含`BSG_GL_DRAW_COALESCE_107`、离线模型/许可证检查、Java阅读顺序/静默TTS/手机相机、GL队列状态模拟及UVC合法帧间隔模拟通过。Action artifact `13429561511`：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42055034691/artifacts/13429561511 ，有效期至2026-11-15 UTC。**APK 79,382,564 bytes，SHA256 `26d85b6874019f330b75f3668b2320c64589ec2b20b30a6ec91c7819f489e543`**；已重新取回artifact ZIP并独立解压/校验SHA及二进制。CI中旧版号及RenderManager插桩锚点冲突曾先失败，逐项修复后才构建成功。
- **严格发布约束：** 用户明确要求发布前严格审查及审批；当前**没有GitHub Release，也未覆盖稳定版本**，只是独立测试APK的Actions构建产物。尚无用户在vivo X200 Pro真实USB测试的FPS、卡顿/发热/读字测量。只在用户明确批准后正式发布。下一步同镜头1.0.6/1.0.7逐个真实A/B测试OCR关闭与开启、glInput/glSwap/Java RAW、可见画面，区分UVC协商60/原生解码与GPU丢帧；不要再次让用户重复提交已有截图。

#### 2026-11-06 — Blue Star Guide 1.0.8 USB source/output buffer 几何修复（实验版构建成功，真机未验收）
- **原因/此前失败：** 上一轮`exp/blue-star-usb-buffer-108-20261105`分支创建操作被平台安全检查拦截（不是GitHub权限问题），因此未写入。后来使用用户授权的GitHub插件成功从1.0.7的实验分支创建`exp/usb-display-stability-108-20261106`，继续修复。
- **1.0.7实测反证：** 用户截图实测GL约60FPS启动后很快下降至0~3FPS，发生几秒没有新帧，`OCR=false`、`eglErrors=0`；中央/右侧大面积灰色硬边界画面（旧版已有）。所以1.0.7“达到60FPS”不等于稳定，**真实验收失败**。不再向用户反复索要已有截图。
- **源尺寸错配候选根因：** 在固定的AUSBC 3.6.0 `RenderManager.kt`中，构造器`surfaceWidth/surfaceHeight`是真实视频帧宽高（通常1920×1080），`startRenderScreen(w,h)`及`mWidth/mHeight`为显示`TextureView`宽高（日志可达2240×1260）。旧代码的`SurfaceTexture.setDefaultBufferSize(w,h)`和`setDefaultBufferSize(mWidth,mHeight)`用输出显示尺寸配置视频输入buffer，可能与原生`ANativeWindow_setBuffersGeometry(frameWidth,frameHeight,...)`冲突。v1.0.8仅将这两处buffer设置改为`surfaceWidth,surfaceHeight`，继续让GL screen viewport根据显示尺寸设置，保持FIT、镜像、旋转、OCR输入、焦点、TTS不变。
- **代码/回归：** `scripts/patch_usb_buffer_geometry_108.py`及`scripts/test_usb_buffer_geometry_108.py`（唯一源码锚点/双生产者buffer检查），继承v1.0.7 `UVC_FPS_NEGOTIATION_107`最高受支持FPS协商、`BSG_GL_DRAW_COALESCE_107`绘制积压合并，以及v1.0.6的原生RAW20限流。1.0.8是独立包`com.vobich.kanzi.experiment.dev108`，versionCode108，versionName1.0.8-development。
- **CI实证：** 首轮run `42062243521`原生APK编译成功、但因严格审计脚本旧包名dev107断言而按设计失败，不上传APK；修正包名后run **42063454393 SUCCESS**，实际Android/NDK编译、原生so及DEX修复标记、离线模型校验、APK签名/包名、Java阅读/TTS回归通过，用户可见构建：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42063454393 。主源码CI commit `a527c1751846d3490bf8f9dfb407f7906e8dbaaf`；分支详细报告：`docs/usb-preview-buffer-geometry-108-20261106.md`。
- **实际成品独立核验：** GitHub Actions Artifact **13432599034**，网页 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42063454393/artifacts/13432599034 ，到2026-11-15 UTC过期；APK `BlueStarGuide_Experimental_1.0.8_arm64.apk`，大小**79,382,564 bytes**，SHA256 `021f687ae9671e646822678433425a603bb9c4361a1e7dba3e4071c4b5e49d93`。已将Artifact ZIP下载至工作容器，解压后独立校验sha256及原生FPS/RAW20、DEX GL/Buffer标记全部PASS。只上传测试产物，**未创建正式Release、未改稳定主程序**。
- **真实边界/后续：** v1.0.8当前只确认**修复逻辑已打包**；尚未有用户vivo X200 Pro A/B验证，**不能宣称灰块消失或1080p60FPS稳定**。首要真实目标：OCR关闭持续2–5分钟，灰块消失且glInput/glSwap不掉0；OCR开启持续5–10分钟不冻结、不明显过热、识别/朗读不回归。若仍卡顿则认定实验失败并保留v1.0.6回退，深入UVC摄像头传输/原生MJPEG解码而非继续盲增补救。未经用户明确同意禁止正式发布。

#### 2026-11-06 — 1.0.9原生USB直显独立实验（持续前台卡顿+后台返回卡顿同时处理，未真机验收）
- **用户最新纠正：** 1.0.7/1.0.8不仅在从其他APP返回时会卡，停留在蓝星向导取景页面也会卡。绝对不能继续将卡顿只归为AF恢复或后台生命周期。用户之前已提供第三方USB Camera APK，要求直接学习成熟USB采集/显示方案，**不能继续在旧GL渲染链上层层打补丁**。
- **第三方APK静态证据：** 已存在上传的约88MB USB Camera APK，内含`libUSBVideo.so`，strings可确认`uvc_stream_start`、`ANativeWindow_setBuffersGeometry`、`ANativeWindow_lock`、`ANativeWindow_unlockAndPost`，DEX显示`USBCameraService`和`nativeSetPreviewDisplay/startPreview/stopPreview`相关方法。借鉴其“原生UVC直接写Android显示Surface、采集与业务独立”的思路；不复制或分发其闭源专有so/代码。
- **实际独立架构：** 从实验1.0.8创建`exp/usb-native-direct-109-20261106`，将`MainActivity.getCameraRequest()`的USB路径改成**上游AUSBC 3.6.0内置`RenderMode.NORMAL`**：`UVCCamera.setPreviewTexture(TextureView.surfaceTexture)`，原生UVC/MJPEG直接写Android TextureView。**不再调用应用层RenderManager或旧GL/OES渲染队列**。CI不再施加1.0.7 GL队列合并及1.0.8 GL缓冲区补丁；仅保留原生UVC 1080p支持的最高有效FPS协商及原生OCR回调每50ms至多一次。实际屏幕直显更新由`onSurfaceTextureUpdated`独立原子统计，不冒充GL提交或屏幕面板刷新。
- **两种卡顿同时处理：** 安卓`onPause`关闭旧原生UVC；本轮将上游`State.CLOSED`从`destroy()`之前移到`destroy()`之后，`onResume`仅在彻底关闭、Surface仍可用且USB权限仍在时打开新会话。**持续前台**若启动超过6.5秒且直显回调连续3.5秒停滞，并且至少20秒内未恢复操作，就触发同一条干净关闭—重新打开的有界恢复通道。暂时关闭实验性AF锁定/恢复指令，保留原生摄像头打开时启用的默认AF，以隔离控制传输干扰。是否真正消除故障**尚无用户真机验证**。
- **代码/回归：** `scripts/patch_native_uvc_fps_only_109.py`、`scripts/patch_usb_native_direct_109.py`、`scripts/test_usb_native_direct_109.py`；`scripts/DiagnosticRenderHeartbeat.java`加入DIRECT计数；USB诊断报告`Blue Star Guide 1.0.9 USB NATIVE DIRECT TRACE`。不改OCR模型/框/顺序/朗读及手机Camera2。
- **最终独立编译/审核：** GitHub Actions `42069930829` **SUCCESS**，构建源SHA `88c60d63076dea7e11f5e59790d2d9ebcc76d4a0`：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42069930829 。版本`1.0.9-development`、code109、appId`com.vobich.kanzi.experiment.dev109`。APK `BlueStarGuide_Experimental_1.0.9_arm64.apk`大小**79,382,560 bytes**、SHA256 **`1d69d648f63689d8008b41051084339d0a826219dcdb7abd7217957b4c677626`**，Actions artifact ID `13437085710`，网页 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42069930829/artifacts/13437085710 ，预计2026-11-15 UTC过期。已从Actions下载真实压缩包并独立解压验证SHA及DEX直显/关闭栅栏标记、原生fps/RAW20标记，原GL/Buffer补丁标记不存在，全部通过。CI前两轮因测试文案和精确锚点而失败，按严格审查拒绝产包并修正。
- **发布约束：** 只上传GitHub Actions测试构建，没有建立正式Release；稳定main只追加本交接文字、不合并实验代码。详细报告在分支`docs/usb-native-direct-109-20261106.md`。**构建成功≠真机稳定成功**。必须分别测①保持USB画面前台数分钟（OCR关与开）；②切换其他应用再回来数次；③灰色区域/画面实际流畅度/温升。若失败，保留1.0.6对照并继续底层原因分析，不再对用户重复索取已有图片。
