# 看字助手｜真实公开照片单行白框影子对照（2026-11-03）

## 判定

**本次限定范围的真实图片对照已成功执行、数据已核对，但未证明新白框优于原框；暂不接入正式 App。**

- 实验分支：test/line-overlay-plan-isolated-20261103
- 研究代码提交：29bcedd075a731d86a2a55a9d5b85c3f0e36eefe
- Actions Run： https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41607539013
- Job：realphoto-shadow-only = success；所有步骤成功，**没有 Android 构建、APK、真实 UVC 预览或 TTS 测试**。
- 原始指标及匿名坐标 Artifact（2026-11-13 09:01 UTC 预计到期）：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41607539013/artifacts/13216669007
- Artifact ZIP SHA256：cd9aa0a71adffb197c81d14966759c4b760216a549fcbf253e50981d537d44d9
- 使用的基线工具：原仓库 test/craft768-int8-roi-dense-text-v2-20261103 的 Python DBNet 近似推理/Small REC 工具，单独只读 checkout；**未做新的 CRAFT 推理，也未重复原来的 A/B 及 6×24 真机压力实验**。

## 预先限定的设计

从以前已用过的 Yesianrohn/OCR-Data 数据集 RCTW 索引 1400–1411 固定取 12 张公开真实场景照片，故**不是新盲测集**。保持旧的 PP-OCRv6 Small DET/REC 权重及 SHA、1600 DET 最大边长、bitmap 0.20、box score 0.45、unclip 1.40。先通过原 DET+REC 得到四点框和是否有非空识别，再交给**未经修改**的 Java LineOverlayPlanner.plan（仅影子调用）。GT 只用于最后评分，不参与行合并判断。

三个维度：来源 OCR 框每个恰好出现一次；候选框角点是否裁切来源框；候选框与原框对已有 GT 标注框的一对一 AABB IoU≥0.40 匹配和跨多个 GT 标注情况。注意：**此数据集的 GT 多为文本实例/区域，并非人工标注的严格单行显示框**，GT 匹配只是代理指标。

## 本次可核实结果

| 指标 | 结果 |
|---|---:|
| 公开真实照片 | 12 张 |
| GT 文本实例/区域 | 71 |
| 原始 DBNet 四点框 | 52 |
| 有非空识别文字的原框 | 50 |
| 候选显示框数 | 51 |
| 实际合并的候选框 | **1** |
| 原框 GT AABB IoU≥0.40 一对一匹配 | **36** |
| 候选框 GT AABB IoU≥0.40 一对一匹配 | **34** |
| 代理规则中“一个 GT 区域被多个原框碎裂” | 0 |
| 被合并框关联多个 GT 实例 | **1** |
| 被合并框中原始角点越界超过 2 px | **0** |

唯一的合并出现于 **RCTW-1406**：候选框合并原始 ID [3,2]，两者的图像坐标约为 x260–371/y411–444 与 x370–425/y413–435，位于近似相同的视觉水平行，分别关联两个**相邻** GT 标注区域。原框对 GT 的一对一 IoU 匹配在该图为 6，合并后为 4。**这不能直接等价于“把上下两行错误合并”或“白框肉眼更差”**：GT 将并排文本标成两个对象，而用户目标可能希望按一整行合成一个框。必须以图像可视化与严格人工单行 GT 再审才能定性。

逐图公开样本对照（源框数→候选框数；GT 匹配原始→候选）：
- RCTW-1400：4→4；2→2；1401：1→1；1→1；1402：3→3；2→2
- RCTW-1403：4→4；3→3；1404：1→1；0→0；1405：8→8；8→8
- **RCTW-1406：8→7；6→4（唯一合并）**
- RCTW-1407：1→1；0→0；1408：2→2；2→2；1409：11→11；8→8
- RCTW-1410：5→5；1→1；1411：4→4；3→3

## 源码/输出完整性

对初始验收 Commit 9f861680b0be514b2fa6dd92e1088cbe6a672950 与本次研究提交 29bcedd075a731d86a2a55a9d5b85c3f0e36eefe 的 GitHub compare 核查：**仅新增三文件**（Python 影子跑分脚本、Java stdin/stdout 桥接程序、非安卓 CI workflow）。未改 MainActivity、PaddleOcrEngine、OcrOverlayView、TextFlowRebuilder、LineOverlayPlanner、TTS、模型或正式 APK CI。Java 桥接程序逐个检测来源 ID 的缺失和重复，CI 中执行成功。Artifact 内有逐图 CSV、汇总 JSON、无 OCR 文字的原框/GT/候选框坐标 JSON、运行日志；没有图片、模型权重或 APK。

## 局限和技术决策

1. **未证明单行白框效果改善。** 当前样本只发生一次合并，无法估算真实“碎框合并率”；不能把没有观察到跨上下行合并误判为安全性证明。
2. 原框 36 与候选 34 的 GT 匹配变化受标注粒度影响。当前结果不能直接认定真实行框准确率下降 2/71，更不能推导完整识别 CER/语音质量变化。没有逐行人工可视评审，且只测了一个公开图集的 12 张。
3. Linux CPU 上的 Python OpenCV DBNet 后处理近似于原 Small Android SDK 配置，**不是手机 USB UVC 的真实帧源、Java SDK 检测轮廓或叠加位置**。几何运行成功不等于完成 Android 预览集成。
4. **不擅自修改算法，不集成正式 App，不制作 APK。** 合理下一步是用有“严格单行白框”人工标注的图片做逐帧可视复核，尤其是原始 DBNet 同行多碎框、左右相邻文本但应分开、相邻上下两行和竖排场景；在得到明确收益与误合并证据之前保持影子模式。
5. 本记录及主分支 PROJECT_HANDOFF.md 均长期保留。Actions Artifact 为短期保存，恢复工作不能把其下载链接当作永久保证；本报告永久保存了主要数值、结果及基线可复现方法。
