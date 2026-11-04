# vivo X200 Pro：Small vs ChineseOCR-Lite 同帧 DET-only 实验

此实验借用了原仓库已验证能够编译的独立 CRAFT Phone Lab **工程外壳**，但独立包名改为 `com.vobich.kanzi.detcompare`，主入口是 `DetCompareActivity`，不替换稳定看字助手；实验只调用两套 ONNX DET，不调用 REC、版面模块、TTS 或云端 API。旧 `ProbeActivity` 保留源码用于溯源，非启动入口，也不会装入旧 CRAFT 模型。

## 模型与固定 SHA256

- `small_det.onnx`: PP-OCRv6 Small DET，9880512 字节，SHA256 `d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e`。来自 `PaddlePaddle/PP-OCRv6_small_det_onnx` pinned revision `5994b4f`。
- `cnlite_det.onnx`: ChineseOCR-Lite DBNET 检测模型，3637234 字节，SHA256 `ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867`。来源 `DayBreak-u/chineseocr_lite`，commit `54a3df53af823f3bffdde9393d0c021b4f6d94cd`。上游仓库许可证 GPL-2.0；附带原许可证。模型单独权利声明未找到，**不可默认为可集成至闭源正式产品**。
- 模型文件**不提交 Git**，只在独立 CI 内下载、检验 SHA，然后放进临时调试 APK。不得混入正式 App。
- 工程开发源码保持公开；对重分发该实验程序的许可证义务需进一步检查。

## 真机操作与真实性边界

1. 安装独立调试包（与正式看字助手并存）。点击①载入两个模型并验证 SHA；全部在本机离线进行。
2. 点击②系统相机拍照或③从相册选择**同一张真实照片**。建议四类：清楚的中文门牌、相邻两行价格、超市价签、商品包装。图片冻结在内存中。
3. 点击④对比两模型同一 Bitmap 上的一次划框。Small 红色；ChineseOCR-Lite 绿色。若图片存在方向 EXIF，解码后仅旋正一次，再共享同一 Bitmap。
4. 点击⑤进行12对轮流执行（两模型各预热1次），读取 P50/P95、预处理/纯 ONNX 推理/后处理耗时、内存 PSS、电池温度和热状态。
5. 点击⑥导出匿名 JSON（逐框坐标/分数、时间、模型哈希、设备/热状态）。用户必须手动选择保存路径，原图及文字不会导出，也不会上传。

**必须严格写明：** 本实验是手机上同帧 **固定照片** DET A/B，不能称作实时 Camera2/UVC 连续视频 FPS、长期热稳定、完整 OCR/TTS 延迟；也无法在 GitHub CI 代替真人 vivo X200 Pro 验收。手机上的实际热/速度要等安装测试后记录。

## 评分与可信限制

- Small 原生输入：BGR、归一化、最长边 1600、32 对齐；CN-Lite 原生输入：RGB、短边 640、32 对齐。**不等分辨率**，测速只能体现各模型此配置，不是模型架构在同条件下绝对快慢。
- 两者后处理：轮廓、旋转矩形、置信度筛选、框扩边；使用 OpenCV 标准库并用**旋转矩形解析近似**替代 Python PyClipper 圆角多边形 offset。若出现与既有 GitHub Python 报告差异，应优先检查后处理误差，而不是宣称某模型准确率改变。
- 框没有文字标签，无法直接计算中文 CER；没有人工完整行 GT，不能仅凭“框数少”判定准确率高。
- 建议人工核验每张图：应框的行总数、漏框、上下跨行粘连、同一行碎框、相邻门牌串框、旋转与金额文字。
- 已知必须覆盖的历史难例：`restaurant_menu_12` 跨行价格，`storefront_10` 标题碎框，`supermarket_03` 价格连框；已有资料见 `main:PROJECT_HANDOFF.md` 的 2026-11-05 审计日志。
- CPU 2 threads/1 inter；禁止网络权限；不启动自动扫描或后台任务。

## 成功门槛

此分支完成 GitHub 安卓编译和签名/哈希/权限审计后，结论仅为“可安装 APK 已生成”。真实划框表现、同帧速度/发热及实际输出可靠性均为 **待 vivo 真机验收**。若原算法后处理不符合已有 Linux 几何输出，先修复这个基础差异，再作精度比较，不接入正式 Small REC。
