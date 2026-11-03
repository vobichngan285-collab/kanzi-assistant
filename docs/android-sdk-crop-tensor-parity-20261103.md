# 看字助手｜PaddleOCR Android SDK 真源码裁剪及 REC 张量对照（2026-11-03）

## 结论

**这一次宿主数值实验没有发现旧 Python 实验的文字裁剪/REC 张量与正式 Android 构建使用的 PaddleOCR SDK 算法出现大范围差异。** 对两张固定公开实拍场景照片共12个 GT 四点框，在 Linux JVM 中实际执行从官方 **PaddleOCR v3.7.0** 取出的 **`QuadTextCrop.crop` + 按正式 APK CI 同样规则修改 BGR 的 `RecPreprocessor.preprocessBatch`**。旧 Python 参考使用早前控制变量实验的 `sdk.crop_box` 和同一 BGR/48 高/归一化方法。全部12个形状及裁剪尺寸完全一致，绝大部分像素完全一致，少量最大只差1个像素灰度级。

**此结果不能证明 Android 手机上 NV21→RGBA→Bitmap 或 GPU/CPU 推理端到端一致，更不能视为手机 OCR 字符识别率已提升。** 没有运行任何识别或检测 ONNX 模型，没有构建 APK。

## 历史去重前置检查

- 旧 `test/ocr-final-error-attribution-20261103`：72图 Small DET / REC 错误归因，HOLDOUT IoU≥0.4成功配对122/244，旧自动框 CER16.087%。Run 41566050026。
- 旧全量人工框 Oracle：HOLDOUT244 区域 CER9.734%，与上述122分母不同。Run 41564703130。
- 上一轮**新补的同分母**122配对实验：自动框 CER16.087% vs GT框 CER5.797%（690字符）。Run 41612404136，报告 `docs/paired-small-crop-attribution-holdout-20261103.md`。本次**不重做**。
- 旧 `android-bgr-model-recheck.yml` / `verify_android_bgr_model_comparison.py`：BGR/RGB 的 ONNX 模型识别对照，未直接执行真正 Android SDK `QuadTextCrop` 与 `RecPreprocessor` 并比较浮点张量。本次只补这个尚缺的环节。
- 旧 CRAFT、Small/Medium A/B、148断言以及单行白框测试均不重做。

## 输入及严格对照范围

- 源码：PaddlePaddle/PaddleOCR **v3.7.0 tag**，从 `deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr` 取出 `postprocess/QuadTextCrop.kt`、`postprocess/QuadGeometry.kt`、`preprocess/RecPreprocessor.kt`、`model/OCRBox.kt`；识别预处理按照原仓库 `.github/workflows/android.yml` 的相同8处源码替换，**移除 BGR→RGB**，与正式 Small `img_mode:BGR` 保持一致。
- 为了在 Linux JVM 编译 `OCRBox.kt`，仅在**临时宿主项目**中使用只有 `x,y` 两个 Float 字段的 `android.graphics.PointF` 结构桩。其他 SDK裁剪/预处理**执行真实上游 Kotlin 函数**，不通过自己翻译的 Kotlin 仿造逻辑。
- JVM/Kotlin：JDK17, Gradle8.10.2, Kotlin1.9.25, OpenCV原生库 `org.openpnp:opencv:4.9.0-0`；Python参考：OpenCV4.10.0.84、NumPy1.26.4，历史 `ocr_box_value_deep_dive.py` 固定成功 Commit `1e0c227f33fb0fbef4c681024972d107b09bfc99`。
- 固定已用过的公开 RCTW 照片 **1406 和 1409**；各最多6个有效人工四点标注，共12，未读取私人手机照片。这里是**已经用过的旧公开照片，不是新盲测集**。
- 相同 BGR 源像素传给 Kotlin SDK 的 `QuadTextCrop`；Python 用相同图像、同四点框计算历史 `crop_box`，随后转 BGR，用相同的 `48 × ceil(48*w/h)`、上限3200、`/127.5−1` 组成 NCHW张量。
- **没有**调用 DET 推理、REC 推理、模型下载、GPU、TTS、Android Camera2/UVC、Android Bitmap、APP UI 或 APK。Linux JVM只能检查裁剪与张量算法的一致性，不能检查手机实时输入。

## 已验收的实际结果

- Actions Run：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41614242483
- Job `sdk-crop-and-bgr-tensor-reference`：**success**，所有步骤成功；实际 Kotlin/Gradle 编译与执行成功，日志有 `ACTUAL_SDK_CROP_TENSOR_COMPLETE cases=12` 和 `ACTUAL_SDK_VS_PYTHON_PARITY_COMPLETE`。
- Artifact：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41614242483/artifacts/13220447622
- Artifact SHA256 `3e3b36c3fb58eae7fefb0381edfd15712d7ae6205bb1be5d031c57c2fd60d000`，GitHub 显示 `expired=false`、约2026-11-13 09:51 UTC到期；**不得作为永久备份**。

| 核查项 | 实际结果 |
|---|---:|
| 原图照片 | 2张 |
| 公开 GT 四点区域 | 12 |
| SDK与旧 Python裁剪的宽高完全一致 | **12/12** |
| SDK与旧 Python识别输入 NCHW 形状完全一致 | **12/12** |
| SDK/旧Python裁剪像素完全一致 | **9/12** |
| 其余3个文字框最大像素差 | **1/255**（一个8-bit值） |
| 最大归一化张量绝对差 | **0.0078431368** |
| 最大张量各元素平均绝对差（按案例） | **0.000005314** |
| 测试预设容差（像素≤3且张量最大绝对差≤0.02） | **PASS** |

非零微差的3个样本是 `RCTW_1409_gt02`、`RCTW_1409_gt04`、`RCTW_1409_gt06`；其余9个样本裁剪和张量逐值完全一致。差异非常小，与 OpenCV版本/数值舍入细节相容；这里只是可能原因，没有对它作单独机制证明。**不能因容差 PASS 就声称 Android 真机逐字节完全相同**。

本次原始 `parity_summary.json`（12样本数值，完全无文字或照片）已下载 ZIP 并逐项读取，验证了汇总项与逐项数据一致。图片像素、裁剪BGR二进制及 Float 张量仅存在短暂 CI 工作目录，**没有上传到 GitHub**。

## 对项目选择的影响

- **既有122配对实验的关键发现仍成立**：更准确的裁剪能够显著改善这些固定样本上的 Small REC 输出。
- 在这12个固定旧公开标注框上，旧 Python `crop_box` 是**相当接近**正式 SDK `QuadTextCrop` 的裁剪数学实现；因此暂时**没有必要因为“Python和Android裁剪算法可能完全不同”而重复所有72张图准确率实验**。
- 此次**不能排除** Android 实际 NV21 YUV色度采样、摄像头锐度/对焦、曝光、rotation、条件增强、Bitmap转换、模型/字典二进制或手机硬件的其他问题。它们仍可能是手机 OCR 中等准确率的来源。
- 下一真正缺口是**手机端一个单帧离线诊断**：通过用户主动触发，在本机将原 NV21 帧解码后的 Bitmap、裁剪四点、SDK真正裁剪图和 REC 预处理张量形状/通道统计安全地绑定。必须在定义隐私限制和构建范围之后才安排，不能自动上传用户私人图像或声称已完成。
- 本轮不修改 `PaddleOcrEngine.kt`、`MainActivity.java`、`LineOverlayPlanner.java`、TTS 或正式 build.yml；不修改 SDK 原仓库，不生成 APK，不调参数。

## GitHub 保存锚点

- `scripts/AndroidSdkCropTensorProbe.kt`：提交 `06eaa89dec966ef741642e42622ba2511edb7f80`；
- `scripts/android_sdk_crop_tensor_parity.py`：提交 `ebede3d55c43af874f9a24328ac0f9de163cc36f`；
- `.github/workflows/android-sdk-crop-tensor-parity.yml`：提交 `d7a988aaed6a33fcf36e5779889df28141d594e7`；
- 本报告保留核心运行数值/输入边界/限制/链接，**永久保留在当前隔离分支**并将总结追加到 `main:PROJECT_HANDOFF.md`，不覆盖旧实验。

**最终判定：上游 Android SDK 裁剪与旧 Python 参考在这个受限宿主对照中高度一致；下一步要集中检查 Android 真机原帧与可选增强，而不是在模型/裁剪源码上盲改。**
