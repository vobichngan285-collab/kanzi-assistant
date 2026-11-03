# 看字助手｜12GB 手机双模型常驻与扫读性能专项验证（2026-11-03）

## 决策结论

**当前最值得测试的是一套 Small 检测 + 常驻 Small/Medium 两套识别模型，快速扫读每帧最多 1 次 Medium 补救，长文阅读每帧最多 3 次。** 不必立刻量化 Medium，但该方案仍没有经过手机实测，因此不修改正式发布版，也不生成 APK。

本轮使用真实照片先前已获取的配对识别输出测试不同预算策略，然后以三个原始 SHA256 校验的 ONNX 模型做 Linux 持续 CPU 推理/内存压力测试，另外在**完全独立的 Android 实验分支**编写并编译相应的显式 SDK 实验 API。

实验分支：`test/android-hybrid-12gb-performance-20261103`。

## 1. 真实照片的补救次数预算

此前原版 Small DET 生成的**完全相同文字框**已经分别跑过 Small REC 与 Medium REC，本轮只做固定预声明策略评估，无任何新 GT 泄漏：先按 Small 置信度 `<0.95` 筛选，按其置信度升序选择最多 N 框重跑 Medium。文字真值只用于评价，不参与框选择。

| 独立集132张 | 匹配GT字符错误率 CER | 匹配完全正确数量（472处） | 每图P95云端核算CPU | 新增Medium总次数 |
| --- | ---: | ---: | ---: | ---: |
| 原始 Small-only | 12.168% | 352 | 638ms | 0 |
| **置信度排序，每帧最多1次** | **11.439%** | 357 | **657ms** | 82 |
| **置信度排序，每帧最多3次** | **10.856%** | 364 | **693ms** | 162 |
| 所有低置信度框，不设限制 | 10.674% | 364 | 773ms | 223 |

| 开发集216张 | CER | 完全正确数量（785处） | 每图P95云端核算CPU |
| --- | ---: | ---: | ---: |
| 原始 Small-only | 12.897% | 546 | 695ms |
| 最多1次 | 12.522% | 555 | 756ms |
| **最多3次** | **12.346%** | 557 | **772ms** |
| 不限次数 | 12.191% | 566 | 820ms |

这意味着「每帧最多3次」在独立集上取得与直接全量 Medium REC 同水平的匹配 CER 代理指标，并减少了约27%的额外 Medium 调用次数（162 vs 223）；但并非任何场景都胜过不限次数。**该对照仍复用了早期 Python RGB 云端识别输出，不可视为 Android BGR 下的直接准确率证明。**

## 2. 三个原始模型常驻的连续 CPU/RSS

本轮**Linux CI 主机**同时常驻：官方 PP-OCRv6 Small 检测、Small 识别、Medium 识别。用 SHA256 固定的 ONNX 原始权重；模拟多组1080p NV21/彩色缓冲区，同时用 736×1280 的检测输入与10个 48×480 的文字识别输入连续执行32帧。

| 合成工作负载 | 每帧10个Small识别，额外Medium | 云端纯推理P95 | 32帧结束时进程RSS |
| --- | --- | ---: | ---: |
| 不调用 Medium | 0 | 367ms | 638MiB |
| 快速扫描配置 | 1 | 444ms | 650MiB |
| 长文阅读配置 | 3 | 586ms | 650MiB |

模型权重初次常驻后，缓存、缓冲和首次形状推理使RSS上升；随后从快速扫描到长文阅读的32帧运行中，没有看到进一步的持续RSS增长。**这只能排除本轮短时主机测试中明显失控的内存增长，不能证明Android长期运行不会出问题。**

真正手机上的 GPU/NNAPI/CPU、UVC驱动缓冲、Android native 堆、TTS服务、温升、后台应用和系统内存策略都没有纳入测试。12GB 物理内存使方案值得尝试，但不自动保证真实设备的启动、持续运行和热性能。

[真实照片预算对照](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41559847676)

[合成负载持续运行/RSS](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41560142261)

## 3. Android 实验代码：对前一版的改进

实验 SDK 在 CI 里对固定版本 PaddleOCR SDK v3.7.0 进行编译期补丁，正式版 APK 和 release 仍完全不变：

1. **同一帧只运行一次 Small 检测**，先使用 Small REC 识别所有检测框；之后根据 Small 置信度排序，Medium 最多补救1/3处。
2. SDK 公开的**显式测试入口**允许每帧 `mediumBudget=1`（快速）或 `3`（长文），同时在SDK内部限制最高3次；普通 `PaddleOCR.recognize()` 不会自动使用 Medium。
3. 支持从调用方传入页面 `readingEpoch` 的**有效性检查**，在每一次 Medium 推理**开始前**判断是否仍是当前页面。如果页面已变更，直接跳过尚未开始的额外推理。一个已运行中的ONNX native推理**不能被该检查强制中断**。
4. Medium 普通加载失败/识别失败/空输出时仍保留 Small 结果，不因补救导致整个文字消失。OOM不被当成可恢复错误。
5. 仍保留 #315 识别模型官方 YAML 指定的 **BGR** 通道、1600输入检测参数、0.45 box阈值、2线程和 `recBatchSize=1`。
6. `HybridMediumCompileProbe.kt` 仅检查新接口的编译兼容性，**没有接入 MainActivity 的真实取图线程、翻页逻辑、TTS队列或实际启动操作**。

Linux编译代码约束不能直接充当手机运行性能结果；虽然SDK源代码编译通过，但功能仍是实验性实现。

## 4. 真实 Android 手机的明确验收门槛（目前未满足）

下一步只有经过实际手机验证，才能判断是否值得生成可安装测试版：

- 启动及冷加载：原 Small-only 和三模型常驻比较，记录实际 Android PSS/RSS/Java/native heap、峰值及必要时模型回退；
- 同场景快速扫读：对照摄影到首次文本与TTS发声延迟，衡量识别积压、USB预览掉帧和重复播报；
- 连续长文：20分钟以上文字多/少/空页面交替，查看长页P95/99、文字准确率、温升、电池与是否出现内存持续增长；
- 快速翻页：上一页未朗读完的调度规则保持不动，过期帧不能因为Medium补救而被朗读。需实测 epoch 门控、连续翻页及恢复；
- 数字保护：价格、药品剂量、房号单独评估错字/漏字/错数，不能只看整段CER。

**目前未授权生成APK，因此没有执行Android真机加载、USB摄像头性能或TTS测试。**

## 5. 暂时不做的事情

不量化、不换其他OCR模型、不提高默认检测阈值、不接入正式MainActivity、不修改翻页修复、不生成签名/安装包，也不将Linux数字伪装成12GB安卓手机实测。

研究源代码：

- `scripts/hybrid_rescue_budget_sweep.py`：真实文字配对识别结果预算/排序模拟；
- `scripts/profile_sustained_hybrid_load.py`：短时三模型常驻Linux CPU/RSS；
- `scripts/upgrade_hybrid_medium_probe_budget.py`：按Small不确定度排序，SDK显式1/3次回退和epoch跳过机制；
- `.github/workflows/hybrid-budget-realphotos.yml`，`.github/workflows/hybrid-stress-12gb-host.yml`，`.github/workflows/android-hybrid-medium-feasibility.yml`。
