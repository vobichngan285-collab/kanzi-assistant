# 看字助手｜FAST-T（MSRA-TD500 中文场景）轻量文字检测实验（2026-11-03）

## 本轮结论

**FAST-T-512 的官方权重已成功转为 ONNX，并通过 CPU 输出一致性测试，但本轮没有达到值得替换原版的准确率和速度组合。**

- 单独 FAST-T DET + 原版 PP-OCRv6 Small REC：独立集 90/244 文字行匹配（36.885%），明显低于 Small DET + Small REC 的 122/244（50%）；覆盖感知 CER 代理 64.060%，高于基线 51.830%；平均每图云端 CPU 总耗时 415ms，高于基线 267ms。
- 双检测器融合：独立集 126/244 行匹配（51.639%），只增加 4 行，代理 CER 49.251%，平均耗时升至 640ms。
- 既有 CRAFT 对照：同 36 张独立集照片上，CRAFT DET + 同一 Small REC 曾达到 152/244 行（62.295%），代理 CER 38.103%，平均总时长 817ms（来自另一次 CI，不能将跨运行的耗时视为严格同机 benchmark）。
- 原版 Small 完全无任何几何框重叠的 HOLDOUT 42 行中，FAST-T 仅匹配其中 4 行，而此前 CRAFT 匹配了 12 行。
- 因此 **不建议接入当前 FAST-T 配置，不修改正式版、不生成 APK**。

成功 CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41571297324

## 官方源码、权重与导出验证

- 官方开源项目：https://github.com/czczup/FAST ；Apache-2.0。
- CI 暂存官方源码固定在 commit `9cfeda29bfd16c4bc260740a5e39afb2cfdfd23f`，没有复制其带 GPU/CUDA 依赖的完整后处理到 Android。
- 权重：`fast_tiny_msra_512_finetune_ic17mlt.pth`，官方 GitHub Release。SHA256 `0768532ec495fb58c371eeda32ab3267be0987cca2053c9d0d9cb1860a674f94`；54,334,705 bytes。
- 以官方 FAST NAS TextNet-T 结构、Neck、Head 命名加载全部权重，**missing=0 / unexpected=0**，执行 RepConv 部署融合，导出单输入/单输出原始 logit ONNX，动态图片尺寸。
- ONNX SHA256 `caefe750b1d1f317c72dab4b5af779e07ab93cd1db9371842797c92a8af06355`；大小 34,107,586 bytes（约 34.1MB）。
- 随机 512×512 输入上 Torch/ONNX 输出误差 max absolute **3.8147e-05**，并通过 512×768 非方形推理，证明模型转换和 CPU 运行成功。
- 转换代码：`scripts/fast_t_export_probe.py`；模型仅在 GitHub runner 临时生成，没有分发到 App 或上传 APK。

## 已完成真实照片 A/B/C

固定同 72 张公开场景文字图片：ReCTS/RCTW/ART，每种各 12 张 index=0 起 DEV、12 张 index=500 起 HOLDOUT。与之前 CRAFT 和轻量 DBNet 研究的同一批图片。

- A 原版：PP-OCRv6 Small DET + Small REC。
- B 新模型：FAST-T MSRA512 DET + 同一 Small REC。
- C 融合：先保留 Small DET 框，FAST-T 检测框按 IoU>=0.50 或较小框重叠>=70% 与已有框去重，统一 Small REC；没有加入 Medium 或 CRAFT。
- A 原版 Small DET 参数：最长边1600、bitmap阈值0.20、box阈值0.45、unclip1.40。
- FAST-T 使用官方 MSRA-512 checkpoint、RGB ImageNet 归一化、短边目标512，**为了 CPU 时延将长边限制1024**；这会偏离官方不限制长边的 MSRA 测试配置。官方结构相关后处理用 7 pooling size，score>=0.88，min area>=200，CPU connected-components + minAreaRect + 官方 alpha scaling，单独核验原始输出数值一致。
- GT 只用于评估，不参与框选取和模型决策。匹配规则 AABB IoU>=0.4 一对一。

### DEV 36 张 / 217 行

| 指标 | A Small | B FAST-T | C Small+FAST-T |
|---|---:|---:|---:|
| GT 匹配文字行 | 142 | 103 | 147 |
| GT 行匹配率 | 65.438% | 47.465% | 67.742% |
| 未匹配 GT 字符 | 386 | 491 | 337 |
| 匹配行编辑距离 | 93 | 86 | 101 |
| 覆盖感知 CER 代理 | 42.502% | 51.198% | 38.864% |
| 未配对但识别非空的框 | 43 | 21 | 46 |
| CPU 平均每图全部流程 | 299.57ms | 415.67ms | 667.82ms |
| CPU P95 每图 | 556.10ms | 530.89ms | 915.87ms |

### HOLDOUT 36 张 / 244 行

| 指标 | A Small | B FAST-T | C Small+FAST-T |
|---|---:|---:|---:|
| GT 匹配文字行 | **122** | 90 | 126 |
| GT 行匹配率 | **50.000%** | 36.885% | 51.639% |
| 未匹配 GT 字符 | 512 | 640 | 463 |
| 匹配行编辑距离 | 111 | 130 | 129 |
| 覆盖感知 CER 代理 | **51.830%** | 64.060% | 49.251% |
| 未配对但识别非空的框 | 57 | 35 | 63 |
| CPU 平均每图全部流程 | **266.61ms** | 415.30ms | 639.50ms |
| CPU P95 每图 | 622.56ms | 506.10ms | 899.75ms |

### 真正“零几何重叠漏检”恢复情况

| 指标 | DEV | HOLDOUT |
|---|---:|---:|
| 原 Small 完全零重叠 GT | 22 | 42 |
| FAST 单独匹配其中 GT | 3 | **4** |
| Small+FAST 融合匹配其中 GT | 3 | **4** |
| FAST 相较原 Small 独立新增 IoU>=.4 的 GT 行 | 9 | 12 |
| 融合方案相较原 Small 新增匹配行 | 5 | **4** |
| FAST 模型 ONNX 单次推理平均 | 343.59ms | 345.41ms |
| FAST 检测含 Python 前后处理平均 | 365.43ms | 368.91ms |

FAST 在一些旧 Small 漏检文字上有真实增量，但没有整体竞争力。**它不能被表述成比 CRAFT 更优的低时延中文检测器**。

## 与之前 CRAFT 及轻量 ChineseOCR DBNet 的横向比较

| 原独立集同样 244 行 | Small DET | ChineseOCR Lite DBNet | FAST-T MSRA512 | CRAFT MLT25K |
|---|---:|---:|---:|---:|
| GT 行匹配 | 122 | 88 | 90 | **152** |
| 覆盖 CER 代理 | 51.830% | 59.734% | 64.060% | **38.103%** |
| 检测器 ONNX 尺寸 | 9.88MB | 3.64MB | 34.11MB | 83.17MB |
| 不同 CI 运行的整图 CPU 总耗时 | 本轮267ms | 前轮166ms | 本轮415ms | 前轮817ms |

由于跨运行 CPU 环境可变，速度数字应按**同一运行 A/B/C**差异优先评价。模型效果采用相同固定照片和相似匹配规则，但 **FAST 的输入长边1024 cap、CRAFT 960 cap、DBNet640短边、Small 1600长边**等不同，此表比较的是候选部署配置，而不是纯粹骨干网络优劣。

## 下一步建议

1. **本轮停止 FAST-T/MSRA512 的上线推进**。即使成功导出，它仍大幅低于 CRAFT 的检测指标，并慢于现有 Small OCR。
2. 若目标是“CRAFT 已能找回的文字，但更低耗时”，优先考虑 **CRAFT 的轻量 backbone、ONNX Runtime CPU 性能调优/INT8 量化、或降低 CRAFT 检测输入尺寸后的严格 A/B 复核**。量化不保证加速；也可能掉精度，均须实测。
3. 在下一轮前先定义达标门槛：独立新样本上真正补回可朗读文字、不要大量假框/重复朗读、手机端相机+TTS 首字延时能接受；不要从通用 GPU 论文 FPS 预测 12GB 安卓手机实际耗时。
4. **没有新的方案达到门槛前，保留稳定 Small 正式版**。

## 严格边界

- 72 张真实场景照片来自反复使用的公开资料，HOLDOUT 并非严格新盲测，不应宣称达到已上线的真实阅读表现。
- 文字行匹配使用 GT 多边形外接 AABB 和预测 AABB 的 IoU>=0.4，可能把正确拆分/合并的文字读出误判为未匹配。
- 覆盖感知 CER 是代理：匹配行编辑距离加漏字 GT 字符数，未计所有未匹配 OCR 预测插入字符；不是严格逐页转录 CER。
- 通过 PyTorch→ONNX 数值一致性只验证神经网络原始输出，CPU 后处理是官方 FAST 算法的 Python 近似，并**非**严格原生部署、Android NNAPI/XNNPACK 实测。
- 未修改正式 Android 项目、朗读、摄像头或模型文件，未生成 APK。
