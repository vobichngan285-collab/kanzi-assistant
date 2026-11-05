# 豆芽看见 APK OCR 静态技术分析（2026-11-05）

## 研究边界

用户提供APK已在本地静态解包并核对ZIP/ELF、模型清单、NCNN param和DEX方法签名，未安装运行，未取得或复制第三方软件源码。APK大小 **299,923,710 bytes**，SHA256 **9e40d9993c12c3b3f3b99b1f9f4d3e3108141c4b6619a21a1aba64d525a7cb38**；`classes.dex`的Android命名空间`com.doyamate.app`，主界面为Flutter AOT(`libapp.so`)，原生OCR由`libpaddle_ocr.so`依赖`libncnn.so`实现。

## 模型清单——已核实APK内部物理文件

| 用途 | 实际内置文件 | 文件字节 |
|---|---|---:|
| 文字区域检测 DET | `assets/flutter_assets/assets/models/ppocrv6/PP_OCRv6_small_det.ncnn.bin` 与同名 `.param` | 4,929,604 |
| 文字内容识别 REC | `assets/flutter_assets/assets/models/ppocrv6/PP_OCRv6_small_rec.ncnn.bin` 与同名 `.param` | 10,562,152 |
| 文字方向二分类 CLS | `assets/flutter_assets/assets/models/ppocrv5/PP_OCRv5_mobile_cls.ncnn.bin` 与同名 `.param` | 260,952 |
| 文档方向四分类 | `assets/flutter_assets/assets/models/ppocrv5/PP_LCNet_x1_0_doc_ori.ncnn.bin` 与同名 `.param` | 3,355,348 |

明确是 **PP-OCRv6 Small 检测＋PP-OCRv6 Small识别**，不是PP-OCRv5 DET/REC。底层C++类型名仍为`PPOCRv5`（历史名称），但`libpaddle_ocr.so`的原生日志字面量为`PPOCR initialized (model=v6)`与`PPOCRv6 load failed`，而且库内直接引用v6模型资产。不可把历史类型名误判为运行v5 DET/REC。

`PP_OCRv6_small_det.ncnn.param`输入`in0`、输出`out0`，末端`Sigmoid`且输出一个通道，符合文本区域概率图检测架构；`PP_OCRv6_small_rec.ncnn.param`末端`Gemm`类数`18710`和`Softmax`，属于文字识别分类输出。方向模型的末端分别是2类和4类。所有NCNN文件都可直接从包中核实，不是网络搜索推测。

**其他检测模型不要混淆：** `detection/document_v2_0_0.tflite`（5,614,000字节）、`crosswalk_v2_0_0.tflite`（5,561,136字节）、`traffic_light_v2_0_0.tflite`（4,964,288字节）属于文档定位/出行或交通信号等其他功能，不应误报为实时文字行检测器。另有Sherpa-ONNX、VAD、声纹及TTS资源，和OCR行框效果无直接等价关系。

## 底层调用链——证据分层

### 已在DEX或ELF中实际验证的调用接口
- Java类`com.doyamate.app.PaddleOcrPlugin`注册Flutter通道`paddle_ocr_plugin`，含`initialize`、`recognizeText`、`recognizeTextFromFile`、`classifyDocOrientation`与`release`。
- `recognizeText`参数存在`imageData`、`width`、`height`、`format`（默认值中可见`nv21`）、`rotation`、`detectionBoxThreshold`；文件识别另支持`filePath`和`detectionBoxThreshold`。
- JNI原生方法签名明确：`nativeRecognize(long model, byte[] pixels, int width, int height, String format, int rotation, float detectionBoxThreshold) -> Map[]`；`nativeClassifyDocOrientation(long, byte[], int, int) -> int`。这证明识别结果是逐条结构化对象而不是仅一整段文字；可以把框和文本交给上层。
- `libpaddle_ocr.so`导出`PPOCRv5::detect`、`::classify`、`::recognize`、`::detect_and_recognize`，并含`cv::findContours`、`cv::minAreaRect`等OpenCV实现符号：标准可行流程为概率图→轮廓/斜矩形文字框→按框裁剪校正→字符序列识别。**这不是证明所有结果都天然是完美整行框，更不等于确定应用的具体二次合并策略。**
- Flutter AOT`libapp.so`文本池发现`TextBox`、`ReadingOrderTraversalPolicy`、`OcrReadDecision.`、`deduped=`、`textline_orientation_angles`、`ocr_orientation_angle`等符号，支持上层存在阅读顺序、去重或决策/旋转相关机制；由于其Dart代码已AOT编译，无法单凭字符串确认算法细则、阈值、间隔与每条分支的执行次序。
- AOT同时使用Android CameraX的Flutter插件`camera_android_camerax`，包括`ImageStreamMetadataController.setTargetFps`和图像帧获取接口，支持「实时摄像头取图→Flutter/Android OCR插件」架构。不能据此声称实测帧率或OCR调度频率。

### APP自身对两种读字模式的明确说明
- “Instant text”英文说明原文含“continuously recognizes what the camera sees and reads text aloud when it is clear and stable. Recognition runs locally on your phone and works without an internet connection”，定位为店名、标签、路牌、房号等短文字。此为**产品内部文字声明**，加上本地模型支持离线OCR，但并不证明APP其它功能都不联网。
- “Document reader”说明为书本/文档独立功能，持续语音引导手机移动和倾斜至覆盖整页后自动拍摄，识别结果放入阅读页面，支持复制、继续操作等。独立文档TFLite检测模型可能与页面定位配合，但具体联合调用分支仍需运行期追踪；不要把文档定位模型错称为“文字行画框模型”。
- AOT出现`v1/ocr`、`apiOcr`、`OCR_SERVICE_FAILED`等网络/服务字样，只能说可能存在其他服务调用，不能简单断言APP所有OCR模式一律离线或一律在线。**短文字模式本地OCR有直接支持证据。**

## 给蓝星向导的关键判断

1. 豆芽的文字行框主要不是凭一个全新、未知的“专门画框AI”。安装包**明确就是PP-OCRv6 Small DET**，加上检测概率图后处理、单行裁剪及方向处理。框更完整有可能来自摄像头取景、缩放、阈值、轮廓扩张、旋转裁切、分列行序等工程差异，**不是已证实的单一原因**。
2. 蓝星向导当前主用ChineseOCR-Lite DET（PP-OCRv6 Small DET有回退）、PP-OCRv6 Small REC，经ONNXRuntime；豆芽内置v6 Small DET/REC用NCNN。应先在**同一批图像**上比较DET单行框完整度、漏检、重复、行数、横竖顺序、性能，**先不叠加TTS和复杂修补**。
3. 推荐下一组A/B分离三变量：（A）同一1920×1080帧下 v6 Small DET vs ChineseOCR-Lite DET，固定后处理；（B）保持同模型，比较DB概率阈值、候选框大小、单行裁剪并检查上下左右句子完整度；（C）固定检测输出后单独比较排序/去重及清晰稳定帧判断。对于横排朗读、少小字、低发热和完全离线目标优先保留用户原规则。
4. 不能从第三方已编译APK里合法/可靠地取得其完整Flutter源码或专有行合并实现；应复现观察到的外部行为并独立实现开源/自有算法。不因存在同型号模型就宣称复制其效果，也不将APP其它出行识别/TTS/联网模块引进蓝星向导。

## 研究性质说明

以上是静态反向工程与公开接口/模型结构阅读。**未运行第三方APK，没有获取其OCR实时帧率、准确率、实际平均置信度、行聚类门槛、去重时间、GPU使用率、JNI执行耗时**；不能捏造这些数据。下一步真机A/B可进一步校验。此次只产出报告和交接，不修改生产OCR实现或生成APK。
