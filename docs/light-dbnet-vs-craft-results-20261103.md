# 看字助手：更轻量文字检测器实验｜ChineseOCR Lite DBNet vs CRAFT vs Small（2026-11-03）

## 实验结论

**不推荐集成本次测试的 ChineseOCR Lite DBNet。** 它的 ONNX 模型仅 **3,637,234 bytes（3.64MB）**，与此前 CRAFT ONNX 的 83,169,398 bytes（83.17MB）相比更小，但与相同 PP-OCRv6 Small REC 搭配时，真实公开照片上的独立测试集行匹配率显著下降。与原版 Small DET 合并仅补回 2 行，与本次原版总耗时相比增加约 50% 平均每图 CPU 耗时，不划算。

原版 Android、TTS、模型文件和 APK 未修改。

## 模型、许可证与实验约束

- 新候选：DayBreak-u/chineseocr_lite 仓库 `models/dbnet.onnx`，固定 Git commit `54a3df53af823f3bffdde9393d0c021b4f6d94cd`。SHA256 `ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867`，大小 3,637,234 bytes。
- 项目源仓库声明 **GPL-2.0**；本轮仅作隔离研究。**不得未完成许可证合规评估就直接将该模型/代码打入正式 Android APK**。
- 主基线：原版 PP-OCRv6 Small DET（SHA `d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e`）和 Small REC（SHA `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`）。
- 新候选独立方案使用原项目近似输入/后处理：短边 640，RGB 原图转换 BGR、ImageNet 归一化，DBNet mask threshold .30，box score .50，unclip 2.0。与原版 Small 使用自己的原生参数（最长边 1600、bitmap .20、box .45、unclip1.4）比较。**这比较的是完整候选配置，不是单变量模型体积影响**。
- 每种方案都使用 Small REC，不加入 Medium 或 CRAFT 识别器。
- 固定同一批 72 张公开场景文字照片（DEV 36/HOLDOUT 36），均来自 ReCTS/RCTW/ART；不是新的盲测集。A/B/C 均用同样图片和人工标注评分，GT 只用于评估，不用于选择追加框。A 为原 Small DET + REC，B 为轻 DBNet DET + Small REC，C 为 Small DET 保留优先 + 去重后的轻 DBNet 补框 + Small REC。

## 成功运行结果

完整 Workflow：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41569063415

| DEV 36 张 / 217 GT 行 | A 原版 Small | B 轻 DBNet | C Small + 轻 DBNet |
| --- | ---: | ---: | ---: |
| IoU>=.40 匹配行 | 142 | 91 | 143 |
| 匹配率 | 65.438% | 41.935% | 65.899% |
| 未匹配 GT 字符 | 386 | 625 | 376 |
| 含漏检惩罚的 CER 代理 | 42.502% | 60.515% | 41.615% |
| 未匹配但 OCR 输出非空的框 | 43 | 27 | 47 |
| 完整流水线平均耗时（Linux CPU） | 284.64ms | 180.06ms | 420.58ms |
| P95 | 546.16ms | 321.42ms | 645.58ms |

| HOLDOUT 36 张 / 244 GT 行 | A 原版 Small | B 轻 DBNet | C Small + 轻 DBNet |
| --- | ---: | ---: | ---: |
| IoU>=.40 匹配行 | **122** | **88** | **124** |
| 匹配率 | **50.000%** | **36.066%** | **50.820%** |
| 未匹配 GT 字符 | 512 | 664 | 483 |
| 含漏检惩罚的 CER 代理 | **51.830%** | **59.734%** | **49.418%** |
| 未匹配但 OCR 输出非空的框 | 57 | 24 | 61 |
| 完整流水线平均耗时（Linux CPU） | 252.91ms | 165.74ms | 379.30ms |
| P95 | 608.58ms | 266.55ms | 699.38ms |

独立集原版 Small 没有任何框与 GT 几何重叠的 42 行文字，轻量 DBNet 单独运行能够匹配其中 **2 行**；加入原版 Small 后也只补回 **2 行**。DEV 的零重叠 22 行中，轻模型单独匹配2行，组合匹配1行。

**因此仅增加了轻量检测模型并不能重现 CRAFT 较高的补漏效果。**

## 与前轮 83MB CRAFT 的对照（同一照片，不同 Actions 运行）

前轮成功运行：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41567709428

| HOLDOUT 指标 | 原版 Small DET+REC | 轻 DBNet DET+REC | CRAFT DET+REC |
| --- | ---: | ---: | ---: |
| IoU>=0.40 匹配 GT 行 | 122 | 88 | **152** |
| 含漏检惩罚 CER 代理 | 51.830% | 59.734% | **38.103%** |
| 模型文件尺寸（DET） | 9.88 MB | 3.64 MB | 83.17 MB |
| 云端平均每图完整耗时 | 本轮 253ms | 本轮 166ms | 前轮 817ms |
| CRAFT/新模型找回原版零重叠 GT（独立集） | — | 2 | **12** |

**耗时跨不同 GitHub Actions 运行，只能定性参考；不要把 817/166 当成严格同机同进程 benchmark。** 模型精度结果在相同固定照片与同一 Small REC 的评估逻辑下具有可比性，但依然是简化过的 AABB 匹配和 CER 代理，不是整页实际朗读准确率。

## 下一步取舍

1. **不引入本轮 ChineseOCR Lite DBNet**：精度低，作为补框的独立增益小，软件许可证也不利于不经审查集成。
2. **不要简单地用“比 CRAFT 更小”作为选型条件。** 轻量专用检测模型必须证明真正能找回 Small 的零重叠文字，否则成本没有意义。
3. 如果继续选型，可以调查新的、许可证合适的移动端专用文本检测器（例如 MobileNetV3/ShuffleNet 主干的 text detector），但必须是有验证过的 ONNX/NCNN 产物并且具有中文场景依据。**避免反复测试已验证收益低的 v5 mobile、v6 tiny 和常规裁剪调参**。
4. 另一个更有针对性的低时延技术方向是对 **CRAFT 的推理精简（轻骨干、量化、图像输入缩放、执行提供者加速）**，但上述都需新实测证明准确率与性能，不能从模型文件尺寸推断提速。
5. 继续保持独立实验分支、原版 APK 不动；若未来模型明显领先，才进入 Android 编译加载/连续阅读与 TTS 测试。

## 限制

- 旧数据集反复使用，存在选型阶段对独立集的累积观察偏倚，正式确认需要新的实际阅读照片。
- 模型默认输入分辨率不同（Small max 1600, Lite short 640, CRAFT max 960）。各方案比的是部署候选配置，无法单独隔离 backbone 影响。
- IoU 0.4 配对代理指标不等同真正“读没读出文字”；未配对输出也可能是真文字，而非误检。
- 覆盖 CER 未纳入未配对所有 OCR 文字的插入错误，非严格整页 CER。
- Android 12GB 手机并没有进行本轮模型加载/延迟/功耗的真机测量。
