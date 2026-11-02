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
