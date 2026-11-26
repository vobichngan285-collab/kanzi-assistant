# 蓝星飞桨OCR独立实验 0.1.1
仅验证官方 PP-OCRv5_mobile_det + rec ONNX，复用 PaddleOCR 官方 Android SDK dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf，CPU4。不集成 Gemma、YOLO、云端服务或正式蓝星应用。权重直接来自官方文档链接，不转换，实际下载SHA记录并与APK内置资产逐个比对（首次下载无独立固定哈希）。相册原字节，检测上限1280/2560/4000三档，官方识别顺序及原始文字，不做大模型改写；记录行置信度和框坐标、冷加载/热态检测/识别/总耗时，支持复制以及粘贴人工原文计算CER。默认2560；同一价目表每档两次，优先检查中文项目、金额、漏字、顺序；全部测试仅本地执行，结果由用户回传。构建校验不等于真机精度或速度。独立包com.vobich.bluestar.paddlelab.v011，不覆盖正式版或Gemma实验。原厂SDK Apache 2.0；本应用不上传用户照片。


v0.1.0 真机报 Check failed；原代码 OpenCVUtils.init 吞掉 UnsatisfiedLinkError 后返回 false，初步定位原生初始化而非识别错误。v0.1.1 仅升级官方 OpenCV Maven 4.12.0 与 ORT 1.22.0，固定原 det/rec/config 三个 SHA256，记录页大小、OpenCV 实际 JNI 自测、ONNX 会话、识别阶段和完整异常；校验 arm64 ELF LOAD 16KB 对齐、动态依赖闭包及 APK zipalign。独立新包名避免 CI debug 签名冲突。尚未取得真机修复结果。
