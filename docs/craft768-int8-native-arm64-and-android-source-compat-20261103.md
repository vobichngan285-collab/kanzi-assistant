# 看字助手：CRAFT 768 INT8 的 ARM64 原生 CPU 与 Android 代码兼容性实验（2026-11-03）

## 主要结论

**发现有实际价值的原生 ARM64 硬件加速收益，但尚未在 Android 手机加载或运行，不能进入发布。**

- 精确复现上一轮使用的 CRAFT 768 静态 QDQ S8S8 INT8 权重 SHA256 `9ab885d2f6669b77f70e9c8f68acfbe408c0b051501fd02d94bcaa0c7779ab15`，文件 **20,946,608 bytes**；x86 重新校准生成权重通过相同 SHA256 校验。
- 通过 GitHub Actions 临时跨作业模型文件传递，**直接在原生 ARM64 Linux CPU 上运行的是与上一轮 x86 精度实验完全字节相同的 INT8 模型**，不再是 ARM 主机重新量化的另一个 SHA 版本。
- 相同 ARM64 Linux runner / 同一 ONNX Runtime 1.21.1 CPUExecutionProvider / 2 threads / 24 帧 1920×1080 公开真实照片的按比例白边画面，CRAFT768检测中位数 **FP32 2562.73ms → INT8 764.16ms（下降约70.2%）**，P95 **2567.58ms → 767.01ms（下降约70.1%）**。
- Android Kotlin / Java / 飞桨 SDK **源代码编译成功**；实验 Kotlin 加载类内有前置SHA、长边768预处理与 Android PSS/耗时接口。ONNX Runtime 1.21.1 Android AAR 内核实存在 `arm64-v8a` 的 `libonnxruntime.so` 和 `libonnxruntime4j_jni.so`。
- **非常重要：ARM64 Linux ≠ Android ARM 手机。** 尚未进行 Android App 真机运行、真正的 ORT Java/JNI 运行时加载、真实 PSS、热节流、UVC摄像头、Small REC+TTS首字朗读延迟测试。**没有任何 APK，也未更换正式默认 OCR。**

## 一、准确字节版本的 Native ARM Linux 测试

成功 workflow：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41579296606

该 workflow 分为 x86 权重复现、ARM64 本地硬件验证两部分：

1. x86 `ubuntu-22.04` 以固定 original CRAFT FP32 权重 `28c05f8b5e97f3c5765d00bca556ec2397c86c954d4ac5ef73aa10043db16388`、九张固定 DEV 校准照片、相同 ONNX Runtime v1.21.1 + ONNX v1.17.0 重建量化权重，**检查必须与之前 INT8 模型 `9ab885...` 字节 SHA 一致**。
2. GitHub Actions 将该精确量化文件临时保存为一天有效的作业间 artifact，由 Linux `ubuntu-24.04-arm` 直接下载，在 ARM64 上再次 SHA 校验并执行模型；不是安装 Android 应用，也没有发布模型 APK。
3. 原版 FP32 和原版精确 INT8 一起加载、各自预热2帧，再以固定 12 张公开文字照片组成的1080p画面循环24次，按帧交替执行顺序。
4. 测量 CRAFT RGB/ImageNet 前处理、ONNX forward 与文本检测框后处理；**不包含**飞桨 Small REC、语音、USB摄像头取帧、手机温升或系统干扰。

| Native ARM64 Linux 指标 | 原版 CRAFT FP32 | 确切 SHA 版 CRAFT INT8 |
|---|---:|---:|
| 模型文件大小 | 83,169,398 bytes (83.17MB) | **20,946,608 bytes (20.95MB)** |
| ONNX session 加载 | 68.81ms | 51.00ms |
| 1080p检测 P50 | **2562.73ms** | **764.16ms** |
| 1080p检测 P95 | 2567.58ms | **767.01ms** |
| 纯神经网络 forward P50 | 2555.99ms | **757.30ms** |
| 纯神经网络 forward P95 | 2559.59ms | **760.19ms** |
| 24帧期间是否崩溃 | 否 | 否 |

这说明相同 INT8 模型在**原生 ARM64 Linux CPU**上可以加载、输出并相对 FP32 显著提速。**不可用 ARM64 云端 CPU 的绝对值预测用户 Vivo 手机 Snapdragon/MediaTek 芯片的真实速度。**

为了溯源，本轮曾先在 ARM64 重新执行同样9图量化，产生大小相同却 SHA 不同的 `39f6d335...` 文件；由于不是先前准确率实验的完全相同模型，**不能将它的成绩直接视为原量化文件在 ARM 上的证明**。因此补充了 x86 精确模型跨 CI 作业传输测试，最终确实验证了原 SHA `9ab885...` 文件。

原生 ARM64 初次独立研究 run：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41578721845 ，P50为 FP32 2579.00ms、ARM 重新量化变体INT8 795.88ms。**优先使用确切权重跨CI版本的764.16ms作为本轮正式比较。**

### 与此前 x86 及准确率结果的关系

- 此前固定 FP32/INT8 在 x86 Linux CPU 上 1080p检测P50 **1331.23ms vs 966.94ms**（约27.4%提速）。本轮相同 exact INT8 weight 在 ARM64 Linux 的同机相对加速达约70.2%，**显示硬件执行后端对量化性能影响很大**；不同CPU平台绝对耗时不能直接相比。
- 之前72张公开照片小规模精度研究，HOLDOUT 244标注GT行中 CRAFT768 **FP32匹配149行，INT8匹配145行**，两者的Small REC和检测后处理相同，且INT8补回Small原版完全不覆盖的GT行数仍为11。误差代理 FP32 40.765% → INT8 41.265%。精度数据来源：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41577071361
- 本轮ARM64只验证同一权重能否运行及其检测速度，**没有重新做244行人工GT整页文字评估、没有语音朗读，也没有证明Android端精准朗读效果。**

### 内存解读

ARM64实验中进程初始RSS约91.81MiB，同时加载两模型后的整个进程RSS约234.22MiB，运行末全进程RSS约1594.40MiB。末次数据囊括**同时加载FP32和INT8两个模型、全部72张已解码公开原图、12张1080p图片及模拟UVC缓冲区**，因此既不是CRAFT INT8单模型运行内存，也不是Android设备的PSS。连续测量无明显不断增大的泄漏迹象，但仅运行24帧并不能替代长时间真机测试。

## 二、Android 1.21.1 代码和 ABI 检查

成功 Workflow：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41579237312

- 新增独立 `app/src/main/java/com/vobichngan/kanzi/Craft768Int8AndroidProbe.kt`；不接入 MainActivity、PaddleOcrEngine、摄像头或朗读管线。
- `load(privateModel: File)` 只接受应用私有存储中的 `20,946,608` bytes、SHA256 `9ab885...` 的模型，创建ONNX Runtime 1.21.1的 CPU inference session，并设置双线程。
- `forward(Bitmap)` 与 Python 768 对齐的 ImageNet RGB 输入、`[1,H,W,2]` 输出检查，报告模型前处理+推理与原始 inference 时间以及 `Debug.getPss()`、NativeHeap指标。**这些指标接口被编译，但并未在手机上执行**。
- 成功完成Gradle `:ppocr-sdk:compileDebugKotlin`、`:app:compileDebugKotlin`、`:app:compileDebugJavaWithJavac`（`BUILD SUCCESSFUL in 55s`）。
- Android AAR `com.microsoft.onnxruntime:onnxruntime-android:1.21.1` 检查包含 `jni/arm64-v8a/libonnxruntime.so` 和 `jni/arm64-v8a/libonnxruntime4j_jni.so`。
- 该CI仅进行源码编译。因云端 NDK 下载暂时出现 ZIP损坏问题，本轮在**临时第三方源码checkout**中停用 UVC C/C++ native build配置，保留所有需要的 Android/Java/Kotlin 符号与库接口源码编译。**不等于 UVC native C++ 重新编译通过**；以前的 UVC 原版功能未在本次更改。早先几次失败的 CI 已排除Kotlin类型错误与临时NDK依赖，最终编译通过。
- 不生成、打包、安装、签名或发布任何 APK。

## 下一步判定

1. **保留该候选**：从ARM硬件角度看，INT8相比FP32优势明确，而且精度与文件体积的主要优势仍在；相同原量化文件通过ARM CPU模型加载和连续推理。
2. **尚不建议替换正式版**：ARM64 Linux上检测仍约764ms，再加Small REC和语音首字可能不满足快扫需求，且真实手机芯片和ONNX Runtime Android后端可能完全不同。
3. 真正缺少的是 **Android ARM手机/设备真机可运行测试**。需要在取得设备执行环境并获得用户明确测试安装许可后，用孤立的试验性应用/测试构建加载SHA固定模型，记录连续1080p帧延迟P50/P95、Android PSS、温升、崩溃与真实TTS第一字反馈。
4. 若设备测到性能过慢或重读/漏读，保持稳定Small正式版。不要继续堆更多模型。

## 精确边界

实验工作独立分支 `test/craft768-int8-arm64-android-compat-20261103`，没有修改正式发布分支。**“Android源码可编译”不是“Android模型实际加载成功”；“原生ARM64 Linux推理”不是“Android手机实测”；“AABB匹配准确率”不是“实际整页朗读正确率”。**
