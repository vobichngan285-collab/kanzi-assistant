# 看字助手：低延迟选择性 OCR 补救（实验阶段）

## 事实核对与边界

- **当前已经存在**的完整 OCR 主流程：检测 + PaddleOCR Android SDK 的 `QuadTextCrop` 文字区域最小外接矩形与 `warpPerspective` 透视裁剪 + 文字识别 + 阅读顺序重建。
- **已有重复帧筛选**：`FrameChangeGate` 通过 NV21 亮度低采样判别画面变化，并避免连续重复识别；引擎有 `busy` 单任务门控。
- **当前也已有低置信度整帧图像增强重试**：`PaddleOcrEngine.shouldRetryWithEnhancement`。这一逻辑仍然可能增加耗时，应单独测量，不能忽略。
- 此分支**只新增决策规则和测试**，不接入新一轮 ONNX 推理、不修改现有热路径或摄像头采集、不生成 APK。
- 外部第三方应用的具体模型、训练集、词典、后处理属于未公开信息，不以传闻作为实施依据。

## 为什么不用「整行框宽/高」单一触发

正常长句的整行宽高比也很大。实验性候选检查采用：
1. 最多前 48 个候选框，检查文本置信度、字符数、**每字符估算宽高比**；
2. 检查检测框顶边倾角和透视形变，倾斜或扭曲的框**拒绝横向拉伸**；
3. 高疑似粘连行先标记为 `REVIEW_SEGMENTATION`，但此阶段**不自动分割或改变文字结果**；
4. 快速扫描、画面不稳定、剩余时间不够、冷却期一律不处理；
5. 每次最多选 1 个候选行；过度耗时的候选触发冷却；
6. `Config.enabled=false` 为默认值；尚未对 UI、正式识别结果、语音播放引入改动。

## 超时保护的重要局限

`evaluate()` 的时钟预算只可阻止**进入**候选分支，`recordExtraWork()` 在结束后触发后续冷却。
**这两者都不能中断已开始的同步 ONNX 模型运行，也不能保证这次额外推理不堵塞 UVC 摄像头。**
在引入真实再识别之前，必须使用与采集线程隔离的可限流执行通道，禁止排队旧帧，并在设备上测试 P50/P95、内存峰值、帧丢失与 TTS 连续性；必要时直接取消整个补救特性。

## 当前接受标准

- Java 独立测试覆盖默认关闭、长句误触发、倾斜透视拒绝、预算不足、冷却、合并行优先级；
- 源码检查确保没有修改已有 `PaddleOcrEngine.kt` 与 `FrameChangeGate.java`；
- 未来真机验证必须证明 **错误率下降 + 延迟、稳定性不退化**，才可以考虑进一步实现候选区域二次推理。

测试：

```sh
javac -d /tmp/kanzi-policy app/src/main/java/com/vobichngan/kanzi/SelectiveOcrRescuePolicy.java scripts/SelectiveOcrRescuePolicySmokeTest.java
java -ea -cp /tmp/kanzi-policy SelectiveOcrRescuePolicySmokeTest
```

## 快速扫读：稳定短文字确认（仅原型，默认关闭）

`StableShortTextGate` 接受**已完成**的 OCR 文字、置信度、归一化中心坐标、帧序号和时间戳。它不抓帧、不调用模型、不能发音。仅在符合以下条件时返回 `SPEAK` 建议：

- 默认关闭，且只考虑短文字；长文阅读直接跳过。
- 同一文字至少在**不同摄像头帧**重复识别两次，文本一致（忽略空格冗余和英文大小写），位置变化不大，且跨越最短稳定时间。
- 相机不稳定、低置信度、TTS 正在朗读、文字过长时跳过。
- 当前场景已经朗读过的相同内容不重复播报，直到用户停止重新启动或能独立确认新场景。
- 不做中文字典纠错：错字不可强行替换成常见商品词。

**部署阻碍：现有 `FrameChangeGate` 在快速扫读时，对未变化的场景约 2800 ms 才重检。** 如果直接使用两帧确认机制，首次朗读可能至少被推迟约 2.8 秒，与实时读字要求矛盾。因此本原型**暂不接入**实际 TTS；下一阶段必须设计与帧队列、CPU 和 UVC 采集隔离的快速确认节奏，并测量真实设备首句朗读延迟。如果不能可靠降低延迟，就不要接入双帧确认，优先保留原来的单次识别朗读。

## 超时与无队列实验执行器（仅原型，默认关闭）

`NoQueueOcrRescueExecutor` 用独立后台线程、最多一个飞行任务、零容量 `SynchronousQueue`、单调时钟时限、过度耗时冷却机制实现**拒绝排队旧帧**。

- 超时、失效帧和任务过度耗时产生的候选结果不向上层发布。
- `invalidate()` 只能防止旧结果发布，**不能停止当前原生 ONNX 推理**。
- 整体 CPU 资源、模型线程池、内存及摄像头驱动仍会互相竞争；一个慢任务可能跑到结束才释放工作线程。
- 工作线程的成功回调不是 Android 主线程；未来任何接入都必须转发主线程，并重新检查朗读轮次 ID。
- 真正的额外识别应共享或受控使用模型实例，**禁止**为每个候选初始化一个 ONNX 会话。
- 必须先完成 USB 真机 P50/P95 延迟、帧丢失、热量、内存、TTS 连续性对照，才允许开放选项。当前所有阈值仅为实验初始值。

## 回归测试

纯 JDK 测试文件：

- `scripts/SelectiveOcrRescuePolicySmokeTest.java` — 形变筛选与预算。
- `scripts/StableShortTextGateSmokeTest.java` — 稳定文字、去重、长文隔离。
- `scripts/NoQueueOcrRescueExecutorSmokeTest.java` — 零队列、过期帧抑制、后台工作与超时冷却。

工作流：`.github/workflows/selective-ocr-rescue-policy.yml`。仅验证 Java 逻辑与未修改现有热路径；**不代表 Android 真实帧率、准确率或摄像头不卡顿的验收结果**。
