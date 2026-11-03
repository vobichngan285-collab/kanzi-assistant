# 看字助手｜CRAFT 768 Android 编译兼容 + 1080p 连续 CPU 性能预验证（2026-11-03）

## 结论

**Android 代码和原版飞桨 SDK 的源码编译成功，CRAFT 768 ONNX raw forward 的 Android API 调用路径可以编译；但没有在 Android 设备上真实加载或运行模型。** 采用一台 GitHub Linux CPU 主机上的 1920×1080 模拟画面连续检测，24 帧检测中位数 **1328.80ms**、P95 **1337.12ms**，仅模型推理中位数 **1316.89ms**。这一耗时明显不适合作为手机连续扫读默认方案的放行依据。**暂不集成/不生成 APK。**

### 成功 CI

- 分支：`test/craft768-android-compile-host-profile-20261103`，与正式版隔离。
- 双任务全部成功：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41574968468
- Android `ppocr-sdk:compileDebugKotlin`、`app:compileDebugKotlin`、`app:compileDebugJavaWithJavac` 成功（Gradle 输出 `BUILD SUCCESSFUL in 1m 25s`）。
- 由于现有分支包含独立 `HybridMediumCompileProbe`，构建脚本在 **GitHub runner 临时 SDK** 中保留原 Medium 编译探针必需的 SDK 接口补丁。没有把 Medium 权重打进 App，也没有修改任何正式版 SDK/读取流程。
- 新 Kotlin 文件 `app/src/main/java/com/vobichngan/kanzi/Craft768AndroidProbe.kt` 是一个**不被 MainActivity / PaddleOcrEngine 调用**的原型。它接收设备中的 **已预先 SHA 校验模型文件**，执行 session load 和单图原始神经网络 forward，报告模型加载时间、运行时间和 Android PSS/native heap 的指标接口；独立源码能编译，**但没有真机实测数据**，目前没有实现完整 CRAFT 字框后处理或 Small REC 接线。
- CI 强制只调用 compile tasks，不执行 assemble/bundle/sign/publish，也检查不存在 APK 文件。

## 1080p CPU 持续处理测试

脚本：`scripts/profile_craft768_1080p_host.py`。相同官方 CRAFT MLT25K ONNX SHA `28c05f8b5e97f3c5765d00bca556ec2397c86c954d4ac5ef73aa10043db16388`，83,169,398 bytes。Linux `onnxruntime==1.21.1` CPUExecutionProvider、2 intra-op 线程和 1 inter-op 线程，预热 2 帧，后续 24 帧。

- 真实公开文字照片内容来自此前 ReCTS/RCTW/ART 72 图片中的固定 12 张抽样，以保持宽高比的留白方式生成 1920×1080 RGB 内容；不是手机直接拍摄，人工补入空白会改变检测任务。
- 测量包含：ImageNet RGB→Float32/NCHW CRAFT768 输入变换、官方 CRAFT 关联文字的 CPU 连通组件/文字框后处理，以及 ONNX 模型 forward；不包含 Small REC、相机 NV21→Bitmap、Android/USB 开销或 TTS。
- 12 张 1080p 图像和四个 1080p 模拟 NV21 缓冲区在 Python 进程中保持常驻；**测试进程还加载了全部 72 张原始照片**，故总 RSS 不是 CRAFT 模型自身内存，也不是 Android PSS。

| Linux 主机观测量 | 数值 |
|---|---:|
| ONNX 模型加载时长 | 363.95ms |
| Python 进程初始 RSS | 77.88MiB |
| ONNX 加载后进程 RSS | 246.83MiB |
| 第24帧时全进程 RSS | 1165.21MiB |
| 24 帧采样期间首尾 RSS 增长 | +0.03MiB |
| 释放 session / 部分引用并 GC 后 RSS | 587.60MiB |
| 模型纯 forward 中位数 | 1316.89ms |
| 模型纯 forward P95 | 1324.01ms |
| 单帧完整文字检测处理中位数 | **1328.80ms** |
| 单帧完整文字检测处理 P95 | **1337.12ms** |

**谨慎解读内存**：初始RSS与加载后RSS相差约169MiB，但加载后还有图片下载、图片解码、模型运行时 arena 内存分配等。测试过程中达1.17GiB主要因为保留了整套照片和1080p缓冲。不能把这数字用于声称某台 Android 手机上 CRAFT768 需要或只需要1.17GiB。

**谨慎解读速度**：此前在原始大小的自然场景照片中，CRAFT768+Small REC 的整图平均时长约564.52ms（见 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41571860955 ）。本次1080p整图模拟中，**检测单项**达1.33秒；不同图像大小、样本、机器/进程都影响时延。不能把二者当成同测试条件的单变量结果，更不能代替 Android ARM 设备性能。

## 需要进一步确认什么

1. **已有：**精度对照 CRAFT768 在同一36张公开 HOLDOUT照片上 149/244 行匹配，原版 Small 122/244；但都只是几何/代理 CER，不是实际整页朗读效果。
2. **已有：**Android Kotlin 源码可编译，包括原 Paddle SDK 与隔离探针；这不是“安卓模型加载成功”。
3. **缺失：**真实 Android ARM/Android ORT 上 83MB CRAFT 模型的 session 初始化、硬件 CPU/NNAPI/NPU兼容性、真机 PSS、10~30分钟持续推理速度、温升、1080p UVC 摄像头首字朗读 TTS 延迟。没有连接用户手机，也没有通过云端 runner 获得真实用户手机设备。
4. **缺失：**用于用户特定盲人扫读方式的整页漏读/重复/顺序错误实测。
5. 如果希望测试第3项，通常需要一个能够在手机上运行的临时测试构建或通过原生 Android 实验宿主运行探针；当前本轮是**源码编译实验，不生成 APK**。获得用户明确同意再考虑实验性安装包，不应悄悄替换稳定版。

## 决策

**CRAFT768 仍是有效的文字检测补漏候选，但不能因为模型能导出 ONNX/Android API 能编译就判定适合快速离线朗读。** 本轮较高的1080p主机时延提示要谨慎；在设备实测前不应修改正式版、不应生成/推送 APK。

如果用户仍希望推进，下一阶段应集中验证 **真机、1080p、单张耗时和语音首字反馈**，并预先约定性能通过的标准。若手机端确实超过允许的延迟，优先继续保持原 Small 正式版。
