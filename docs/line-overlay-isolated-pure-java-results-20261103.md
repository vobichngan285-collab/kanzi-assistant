# 看字助手：单行白框隔离模块与纯 Java 测试（2026-11-03）

## 状态

**纯 Java 几何模块已实现、已编译并通过全部 148 个测试断言。尚未接入正式 OCR 预览白框、没有 Android APK、没有真机图像效果或 TTS 验收。**

- 原仓库：https://github.com/vobichngan285-collab/kanzi-assistant
- 实验分支：`test/line-overlay-plan-isolated-20261103`（从 `feature/strict-offline-ocr-order-guard` 的 `c50855877c2d22a52333585e433528830a498e13` 派生）
- 通过的 GitHub Actions：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41605234334
- 纯 Java 工作流：`.github/workflows/line-overlay-isolated.yml`，只运行 JDK 17 `javac`、`LineOverlayPlannerSmokeTest`；不是安卓构建工作流，也不生成 APK
- 真实运行日志：`LINE_OVERLAY_PLANNER_SMOKE_TEST=PASS assertions=148`

## 修改清单

1. 新增 `app/src/main/java/com/vobichngan/kanzi/LineOverlayPlanner.java`。仅为影子/实验模式规划候选显示框，输入旧 `ReadingOrder.Box`，输出行结构：`sourceIds`、`rawText`、`outline`、`merged`、`drawable`。保留原始框编号，原始坐标和文字仍存留在输入 OCR 列表；本模块只生成显示候选，没有写入任何 TTS/真实 OCR 数据。
2. 仅把 `TextFlowRebuilder.java` 中 `sameVisualRow` 与 `denseTextStrips` 从 `private static` 改为包内可访问 `static`，**函数体和旧算法完全不变**，供本实验模块共享原有行判断规则。
3. 新增 `scripts/LineOverlayPlannerSmokeTest.java`，纯 Java 无安卓依赖。
4. 新增独立 CI `.github/workflows/line-overlay-isolated.yml`，限定此实验分支与相关文件变更；未改动原正式 APK 的 CI 配置。

## 已实测的行为

- 原输入序列打乱的三个相邻中文文字块能够合并为一行，并保留各块原始 ID。
- 上下紧邻的两行不合并；相距较远的区域不合并；高度重叠/可能重复的文字框不贸然合并。
- 轻微倾斜的同一行合并后，候选四边形包住各个来源框角点。
- 竖排小块、空文本、非法坐标和 null 输入采用单独保留/不绘制回退，不凭空编造文字。
- 每个原始 OCR 框 **恰好分配一次**；校验原始数组坐标保持不变，现有 `ReadingOrder.arrangeRealtime` 结果不变。
- 总计 148 个断言通过；该数据是 JVM 几何测试成功数，**不是** 148 张照片或准确率。

## 尚未解决和风险

- **此模块尚未连接 `OcrOverlayView.show`**，不影响现有实时画面、朗读或白框。必须先通过影子模式对照，之后再决定是否接入。
- 单行重建只适用于多个原始检测框实际上属于同一文本行的情况。对于 DBNet 已把两行合成一个大框的场景，此模块不能拆开；对完全漏检文字也无能为力。
- 画框几何未经过真实图片人工 GT 的视觉验证；紧密双栏、强透视、复杂竖排、旋转、伪影、异字号的误合并率仍未知。
- 目前 `Plan.Line.rawText` 只是按几何左右次序拼接来源 OCR 字符，**不是实际朗读文本的绑定**；TTS 仍走老链路，不能声称已实现行级朗读高亮或端到端质量提升。
- 本轮依赖 `ReadingOrder.Box` 中既有四点顺序和斜率定义。原始框若含异常数据，可降级为不可绘制候选；还没有对每种异形点集做完备验证。

## 下一步（需要用户下达下一条单步指令）

在同一隔离分支中增加 **影子模式的真图行框数据对照**：同一批图像保存原始四点框、候选行框、每行原始 ID；与已有的人工单行 GT 对齐，统计同一行碎片合并率、跨行误合并率和角点越界率。**先不启用正式显示、不更换模型、不重做 CRAFT 已有压力实验**。

通过真实样本审查后，才考虑在实验 Android APK 中增设“原框 / 行框”可切换覆盖层，不改变识别和朗读路径。
