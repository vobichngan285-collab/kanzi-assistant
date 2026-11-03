# 看字助手：Small 检测 + 条件 Medium 识别，Android 编译与资源可行性验证

研究日期：2026-11-03。研究分支：`test/android-hybrid-medium-feasibility-20261103`。

**阶段结论：Android 源代码编译通过，三套原始 ONNX 模型能在 Linux 云端同时加载并推理；但三模型常驻的内存开销偏高。没有实际 Android 手机加载、USB 实时识别、发声延迟、功耗或热稳定性数据。当前不建议生成安装包。**

## 1. Android 集成方案（独立试验，不启用）

- 保留 #315 的 PP-OCRv6 Small 检测模型，只运行**一次**全图文字检测。
- 保留原 Small 识别会话，**仅显式启用的实验 API** 才加载额外 Medium 识别会话。
- Small 识别置信度低于 `0.95` 时，尝试 Medium 补救；**每帧最多额外处理 3 个文字框**，防止长页图像触发无限制追加推理。
- Medium 结果为空、普通推理异常时保留 Small 文本；Medium 模型/字典加载发生非致命故障时继续使用 Small。OOM 属于严重异常，不伪装成可恢复错误。
- 原始 `PaddleOCR.recognize()` 仍然保持 Small 行为；新的 `PaddleOCR.createHybrid()`、`recognizeWithMediumRescue()` 必须主动调用。
- `HybridMediumCompileProbe.kt` 编译期检查 Android 调用接口，但**未被 MainActivity 引用或调用**，无菜单、语音控制、UVC、安装包变更。
- 正式发布代码及 `fix/ocr-page-switch-resume-20261103` 未修改。
- Medium 模型只在云端 runner 的测试临时目录下载；未复制到 APK 资源，也未新增 APK。

## 2. Android 编译验证

GitHub Actions 环境：Ubuntu 22.04，JDK 17，Gradle 8.11.1，Android SDK 36，官方 PaddleOCR Android SDK v3.7.0，加原有 AUSBC 3.6.0。

编译目标为：

```
:ppocr-sdk:compileDebugKotlin
:app:compileDebugKotlin
:app:compileDebugJavaWithJavac
```

均运行成功，Gradle 输出 `BUILD SUCCESSFUL`。CI 明确拒绝任何意外的 `*.apk` 产物；未调用 `assemble`、`bundle`、签名及发布操作。

[查看 Android 编译 + 模型加载性能流水线](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41557781461)

**要严格区别：编译成功 ≠ 实际 Android 加载成功。** 目前没有在 Android 运行时执行新增 `createHybrid`，因此没有安卓真机 RAM、初始化时长和兼容性结论。

## 3. 真实 ONNX 模型的云端同时加载

将 SHA256 固定的 #315 检测权重9.88MB、小识别权重21.16MB、中识别权重76.55MB 分别用 ONNX Runtime 1.21.1 的两线程 CPU 会话载入。为了贴近 Android SDK 的 `readBytes()+createSession(byte[])` 行为，实验先读入 ONNX 字节数组，而不是仅以磁盘文件路径打开会话。

两种场景在**独立 Linux 进程**里执行：

| 场景 | 加载后进程 RSS（云端 CPU） | 相比原版 |
| --- | ---: | ---: |
| 一个 Small 检测 + 一个 Small 识别 | 约 **153.4 MiB** | 基线 |
| 同一 Small 检测 + Small 与 Medium 两个识别 | 约 **402.3 MiB** | **增加约248.9 MiB** |

两种模型加载和测试推理均通过。

固定合成张量下的 CPU 推理时间（同一台云端 CI 主机）：

| 模型 | 输入张量 | P50 | P95 |
| --- | --- | ---: | ---: |
| Small 检测 | 1×3×640×640 | 66.7ms | 67.8ms |
| Small 识别（单会话） | 1×3×48×480 | 15.5ms | 15.9ms |
| Medium 识别（两模型同时存在） | 1×3×48×480 | 64.8ms | 67.1ms |

**模型内存与 CPU 都是 Linux 主机结果，不是 Android 手机上的数值。** 摄像头实际图像、OCR 裁剪、运行时内存分配、版面 AI、TTS、并发取帧、热量及系统内存回收未包含在此结果里。双会话实际手机内存可能与云端大幅不同。

## 4. 极重要的 BGR / RGB 差异纠正

现有 #315 `.github/workflows/android.yml` 在编译官方 SDK 时，**把 RecPreprocessor 内的 BGR→RGB 转换去掉**，令裁剪图保持 BGR，与官方 `inference.yml` 的 `img_mode: BGR` 一致。

之前若干云端 Python 模型 A/B 用 PIL RGB 图像直接调用识别器，不能把它们报告的 CER 改善量视作真正的 Android 实测。

本次新研究独立使用 72 张公开实景照片、323 个人工标注文字行对照两种颜色通道。按正式版对应的 BGR：

| 来源 | 文字行 | Small CER | Medium CER |
| --- | ---: | ---: | ---: |
| 开发组 | 170 | **6.21%** | 6.43% |
| 独立组 | 153 | 9.22% | **7.85%** |

结论：更大模型的好处仍有例证，但不能断言它在所有文本上都优于 Small。这次样本数量不大，且对照只分析人工标注文字区域的**识别**，不是安卓整帧检测后终端发声。

[查看 BGR / RGB 真实图片复核](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41558222202)

## 5. 为什么不建议现在生成 APK

最主要原因是 **内存而不是编译**：

1. 双识别常驻可能带来高额内存压力。Linux host 实测增加约249MiB，而安卓还需留出 UVC 图像、TTS、布局分析和其他 App 组件的空间。
2. 条件回退虽然只偶尔调用 Medium，但**双模型常驻仍占据内存**；减少推理次数不会自动减少常驻 RAM。
3. 本次 SDK 实验将 Medium 每帧补救次数限制为 3，不等同于之前云端“对全部低置信度框补救”的准确率模拟，因此不能照搬之前的 10.67% CER 作为新 Android 方案承诺。
4. 手机实际模型冷启动、内存峰值、Android ORT NNAPI/CPU 行为、掉帧与扫码到播报延迟都没有运行测量；Android 执行需有可运行载体，用户要求现阶段不生成 APK，因此**不能冒充已做真机验证**。

## 6. 建议的下一轮可行性工作

在不改变 #315 的前提下，优先研究**不双识别常驻**或压缩 Medium 的方案，例如对 Medium 做模型量化/临时按需载入，并实测该方法是否减少内存与总体耗时。必须承认按需加载可能导致明显的冷启动延迟，且模型量化可能伤害中文/数字准确率。

只有在资源和准确率有独立证据后，才考虑单独在用户愿意测试时生成可安装试验版；再用真实 Android UVC 摄像头验证快扫模式、长文朗读、翻页优先级、TTS 中断与背景资源争用。

## 原始代码与流水线

- `scripts/patch_android_sdk_medium_probe.py`：按 SDK v3.7.0 固定文本做一次性 CI 构建期补丁，失配直接失败；BGR 与当前发布工作流保持一致。
- `app/src/main/java/com/vobichngan/kanzi/HybridMediumCompileProbe.kt`：Android API 编译期引用，不进入实际业务调用。
- `scripts/profile_small_medium_host_load.py`：Linux 隔离进程的模型常驻 RAM/CPU。
- `scripts/verify_android_bgr_model_comparison.py`：公开实景人标文字行的原安卓 BGR 纠正复测。
- `.github/workflows/android-hybrid-medium-feasibility.yml`：只编译，不打包；并行跑主机双会话测量。
- `.github/workflows/android-bgr-model-recheck.yml`：真实文字样本对照官方模型 BGR vs 旧版云端 RGB。
