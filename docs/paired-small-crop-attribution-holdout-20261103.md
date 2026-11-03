# 看字助手｜同一批文字的自动框 vs 人工框 Small REC 配对归因（2026-11-03）

## 结论（本次**新增的**控制变量证据）

**自动框裁剪的几何质量，是当前基线 Small OCR 出错的重要来源；但本次 Linux/公开照片实验证明的是这个固定配对样本中的裁剪因素，并不证明手机上每个场景都会得到同等改善。**

对照对象完全相同：之前 HOLDOUT 36 张自然场景照片中，`IoU≥0.40` 自动检测框一对一配对成功的 **122 个 GT 文字区域（690 个归一化字符）**。人工 GT polygon 仅改变裁剪几何，之后使用**同一份 PP-OCRv6 Small REC** 权重、预处理和 CTC 方法，读取以前成功实验的自动裁剪识别错误数，不重复 DET 推理，也不重新识别自动框。

| **同一组122区域、690字符** | 历史 Small 自动 DET 裁剪 | 本次人工 GT polygon 裁剪 |
|---|---:|---:|
| 字符编辑距离（Levenshtein） | **111** | **40** |
| CER（同分母） | **16.087%** | **5.797%** |
| 完全正确区域数 | **89 / 122** | **99 / 122** |
| 失败裁剪 | 既有实验数据 | **0** |

因此本组 **CER 差距为 10.2899 个百分点**，编辑错误减少 **71/111=63.96%**。这里 16.087% 是**以前已经完成**的旧数据，5.797% 是本次新增的 GT 裁剪 REC；不能称作正式 App 升级后准确率提高10.29个百分点。

配对结果类别（同一 GT 的两种裁剪）：

- 两种均完全正确：**88**；
- 原自动框识别错误，但人工框完全正确：**11**；
- 原自动框完全正确，但人工框出错：**1**；
- 两种都仍有识别错误：**22**；
- 按编辑距离比较：GT 裁剪更好 **18**，自动裁剪更好 **3**，两者持平 **101**。

**不能把同一行的“编辑距离减少”与“从错误变完全正确”混为一谈**；它们是不同计数。

## 分公开数据集的配对结果

| 数据来源 | 区域数 | 字符数 | 自动框 CER | 人工框 CER | 差距（百分点） |
|---|---:|---:|---:|---:|---:|
| ART | 39 | 256 | 39/256 = **15.234%** | 22/256 = **8.594%** | 6.641 |
| RCTW | 40 | 276 | 55/276 = **19.928%** | 16/276 = **5.797%** | 14.130 |
| ReCTS | 43 | 158 | 17/158 = **10.759%** | 2/158 = **1.266%** | 9.494 |
| **全部** | **122** | **690** | **16.087%** | **5.797%** | **10.290** |

旧自动误读集中在一部分长文字：`RCTW-508/GT#1` 含52正规化字符，自动框26处编辑差异，GT框3处，单个区域就贡献**23/71**个净减少编辑错误。因此不能仅看均值断言全部文字均等改善。排除此区域，剩余同117? **121个区域、638字符**：自动错误 **85**，GT框错误 **37**，差距 **48/638=7.52个百分点**；仍存在配对收益。这是对固定样本的敏感性检查，不是新的独立盲测。

## 去重核验及复现锚点

- **旧实验A（实际核查 CI success）：** `test/ocr-final-error-attribution-20261103`，Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41566050026 ，Artifact **13199760778** 的 `per_gt_diagnostics.csv`。旧 HOLDOUT244区域（其中122匹配）、旧自动配对区域编辑距离111、690字符、89行精确正确均已回读校验。
- **旧实验B（实际核查 CI success）：** 72张照片**全部人工框** Oracle，Run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41564703130 。旧 HOLDOUT244 GT 区域上的 CER **9.734%** 与旧自动122匹配区域的 CER 16.087% **不具有相同分母，禁止直接比较因果差距**。这正是本轮仅做配对122区域的原因。
- **本次全新配对实验：** 原隔离分支 `test/line-overlay-plan-isolated-20261103`；脚本 `scripts/paired_small_crop_attribution_holdout.py`，提交 **`1addfcf5f14d3663e4cac400828433b152078a42`**；独立工作流 `.github/workflows/paired-small-crop-attribution.yml`，提交 **`856a2ae622e9fa3fc531f1e024446fb0208341ce`**。
- **实际成功 CI**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41612404136 ，`paired-crop-small-rec-only = success`，日志 `SOURCE_PHOTOS 36`、`PAIRED_SMALL_CROP_ATTRIBUTION_COMPLETE`。新产物：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41612404136/artifacts/13218859906 ；GitHub 暂存 ZIP SHA256 `51449fc6e489b3672df0555edf9fc068b9f2a1b045564df9f0952c9b238126e0`，标记预计2026-11-13 09:37 UTC到期。
- **权重钉住 SHA256** `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`。只下载/运行 Small REC，未下载/运行 DBNet DET，未运行 CRAFT；旧 SDK Python 原帮助代码固定在旧成功 Commit `1e0c227f33fb0fbef4c681024972d107b09bfc99`。
- 严格验证了全部36张固定照片、244原 GT 及同一122个匹配 GT 的 source key、原真值文字/字符数/坐标、自动 REC 旧错误数111（不能用不对齐的图像或不同 GT 来替代）。新匿名结果 `paired_matched_only.csv` 只保存照片ID、GT ID、编辑数、crop大小、历史匹配程度，不保存文本/图片。
- **原始 ZIP 已下载并独立复核**：`summary.json` 与122行 CSV 重算的 `Σauto_edits=111`、`Σgt_crop_edits=40`、`ΣGT chars=690` 一致。

## 这项结果究竟能、不能证明什么

**比较严格控制了文字区域、识别模型、权重、归一化方式和解码，只改变了送入 Small REC 的几何裁剪，因此提供了相对直接的“框/裁剪错误会导致一部分 REC 错字”的证据。** 即使原 DBNet 四点框已满足 IoU≥0.4，它也可能切掉字、拉偏透视、混入其他文字。

但是：

1. **122/244区域是成功 IoU 匹配的偏向简单子集；另122未匹配区域没有相对应的自动识别结果，不能外推这组CER。** 其中前一轮发现 80 个有几何重叠、42 个完全零重叠；漏检及整页漏读风险依然存在。
2. **只控制 Linux/公开照片下的 Python OpenCV 模拟 SDK `crop_box` + BGR `rec`**；与真实 Android `NV21→Bitmap→SDK crop→input tensor` 及字典配置**尚未建立逐字节、逐步骤等价性**，手机的对焦、压缩、摄像头镜头质量也可能造成额外误差。
3. GT polygon 是人工标注，不是生产环境可直接获得的定位算法。**不是一个可上线的修复**，也未验证单行白框/TTS/实时性。
4. GT 框不是永远更好；有3个区域按编辑距离自动框更好、1个区域 GT 框使原完全正确变错。既有公开图和 GT 也可能有歧义，避免机械套用膨胀/缩框阈值。
5. 仍有22个区域两种方法都认错：这部分不能仅归因框，应后续检查图片像素、清晰度、字符集或 REC 能力。
6. 原照片与旧实验反复使用，不是新的独立盲测，不宜推断全部中文小字表现。

## 研发次序（需保持的限制）

- **优先**检验正式 Android 识别链路的四项真实输入：解码后的源帧颜色/方向、DBNet 原四点框、真正送入 Small REC 的裁剪图、RGB/BGR+缩放后的张量，固定原图与 GT 比较。排查 **RGB/BGR、几何/透视、切字、跨行和最终字典/CTC**；不能根据 Linux 曲线盲目改 Android。
- 可在隔离分支做**只读诊断**，保证不上传用户私有照片/完整识别文字；若需要真机用户图，必须有明确的本机导出/隐私约束。
- 不继续无目标调检测阈值、不重做同一批72图完整 A/B 或 CRAFT 压力测试，不更换 Small 模型，不改正式 App、TTS、不出 APK，直到有新的机端证据和明确验收基准。

**本次研究结论：已经用同分母的对照确认，定位/裁剪质量对 Small OCR 错字率影响显著。下一个缺失的证据是 Android 端实际图像/裁剪/张量与这套固定参考实现之间的等价性。**


## 同步只读核查 Android 当前开发主线（不改代码）

为避免看到 Linux 结果后盲目更换通道或反复调阈值，额外读取了 `feature/strict-offline-ocr-order-guard` 的现存源码：

- `MainActivity.java:303–311` 实时帧是 NV21 `data.clone()`，根据 `enhancedOcr || (FrameQuality.dark && FrameQuality.possiblyBlurry)` 决定是否增强，显式 `legacyDecode=false`。因此**此处正常入口不会启用历史兼容的 NV21→JPEG(quality 82)→Bitmap 路径**。
- `PaddleOcrEngine.kt:542–565` 在 `legacyDecode=false` 情况下使用 OpenCV `COLOR_YUV2RGBA_NV21` 转为 Bitmap，而非重新 JPEG 压缩；`250–311` 在此图上直接 OCR，可选旋转/灰度增强，非 fast 模式低置信还可能做第二次增强并选取置信指标较好的结果。
- `FrameQuality.java:14–17` 的“暗且可能模糊”判断阈值是亮度<47、边缘均值<7.0，仅凭源码不能说这种增强一定有害或一定帮助。
- `.github/workflows/android.yml:125–160` 的当前源码明确对官方 Paddle Android SDK `RecPreprocessor.kt` 做 **BGR 预处理保留**补丁，以匹配三份 `inference.yml` 的 `img_mode: BGR`；因此没有证据支持盲目把识别通道“统一改为RGB”。这份 CI 补丁必须在实际设备 APK 上核对所用构建 Commit 和二进制，**源码说明不是手机实测张量等价证明**。
- 当前 `PaddleOcrEngine.kt` 的 SDK `recognize(bitmap)` 只暴露最终 OCR 结果/四点框及推理时间，**没有现成的逐步裁剪图与识别张量导出**。因此下一步真正的手机诊断应以明确的用户授权和隐私保护方式，单帧影子提取 NV21 解码 Bitmap、SDK 四点框、裁剪图和送入 REC 的形状/通道统计，禁止自动上传私人图像。

以上是**2026-11-03 原开发分支的静态源码核查**，不是已执行的 Android 图像等价实验，也不修改以前的结论。
