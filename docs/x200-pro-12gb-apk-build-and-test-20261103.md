# vivo X200 Pro（12GB RAM）｜离线OCR可安装旗舰性能A/B实验版，已通过GitHub编译及静态安全验收（2026-11-03）

## 可验证交付（实际已成功）

- **成功独立Debug APK**：GitHub Actions Run **41656194876**，Job success，`BUILD SUCCESSFUL`、`X200_SHADOW_SDK_PATCH_PASS`、`X200_OFFLINE_SHADOW_AB_APK_AUDIT_PASS`。
  URL：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41656194876
- **GitHub Artifact**：ID **13239213707**，`vivo-x200-pro-offline-ocr-ab-debug-apk`，下载：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41656194876/artifacts/13239213707 ，临时保留约10天。
- **实际构建APK**：`Kanzi_X200_Offline_OCR_AB_debug.apk`，**390,383,487 bytes**，SHA256 **`8448155d5c2e3f9ec6e5f79323d67efd9a1abf4c5761b58594e22a3427792312`**。已下载 Actions ZIP、解包在对话运行容器并重新计算SHA，匹配 `SHA256SUMS.txt`；ZIP校验与APK ZIP完整性PASS。
- **包名**：`com.vobich.kanzi.x200lab`，应用标签“看字助手 · X200性能实验”；versionCode41、versionName `0.9.9-x200pro-performance-lab`，与正式 `com.vobich.kanzi` 和旧诊断 `com.vobich.kanzi.framelab` **完全隔离**。Debug签名，绝不替换正式Release或需要正式签名密钥。
- **安全/模型验收**：`aapt`确认包名与**不含android.permission.INTERNET**；内含arm64 ONNX Runtime；Small DET SHA `d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e`、Small REC SHA `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`，与旧手机严格离线研究一致。PP-DocLayout-S资产仍按原CI SHA检查，正式不更换模型。
- **构建源码快照**：Build的实际head commit **`c11927c1456a784bb73e989442aee7cbf3325192`**。该快照已经包括4档UI、真实保守竖排二次Small、用户手动单帧导出profile元数据、热/内存诊断、隔离包名和 SDK upstream 补丁。后续`bdd1e9ee...`提交**只修诊断文字换行的视觉格式**，并添加纯Java回归脚本/工作流；**已交付APK严格对应上述构建head，不应伪称包含其后的所有源码提交**。
- **纯Java profile/语义安全独立CI**：Run **41656942455** success；检查2线程/批1/零额外REC、3/2/2、4/2/4、4/4/4，以及0–4预算边界/原文完整保留/不存在语义补字；亦复测早期`VerticalRecRescuePolicySmokeTest`。地址：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41656942455
- **本次没有用户手机设备的真正安装、帧识别A/B、线程对比加速/降温测量。** 只有Android GitHub Actions编译与APK静态安全验证，不能说手机已加速或局部纠错已在真实手机成功。上述之前Linux大规模公开图的独立留出组107改善0观察退步，仍是**非手机且人工倾斜高风险框**，不是X200整机提升率。

## 四档使用方式（适配12GB旗舰，不一刀切过保守）

应用“设置”中的 **X200 Pro / 12GB 离线OCR性能实验**：

| 档位 | ONNX CPU线程 | Small REC batch | 每次手动诊断上限：额外真Small REC |
|---|---:|---:|---:|
| 对照baseline | 2 | 1 | 0 |
| 均衡balanced | 3 | 2 | 2 |
| 高质量quality | 4 | 2 | 4 |
| 吞吐throughput（高压力） | 4 | 4 | 4 |

默认**对照档**，不擅自改变旧2线程/单框基线；用户可以主动切到高质量。修改档位会重新加载引擎，**整个实验APP的首次原始Small/DBNet推理采用该档位的线程与REC batch**，而不是只有UI显示变化。批量2/4是否真正提高X200速度、是否影响某些边缘文字框，尚需同帧真机核查，不可先验断言。

**真实额外同Small二次识别（只在用户点击“开始OCR诊断”时）**：先完整记录原始Small SDK结果，筛出极细长且上/下顶点X不重合的正确竖排风险框，且**第一次Small输出≤3 Unicode字**；每张诊断最多2或4个（按档位），额外从**同一张相机帧**用改过的Y组透视裁剪提取像素送入**原版Small REC**，不额外跑DBNet，不执行任何语言模型补字。候选**≥10字且conf≥0.8**才作为“通过保守门槛的建议”，但**从不替换真实输出/TTS/白框**。热状态严重或Android内存压力时本次额外预算降为0；热状态中等以上时收缩至2。每框候选文字、置信度、耗时和门控原因只在用户手动诊断日志里展示，并可自选复制；不会自动上传。

**诊断另记录**：该档位线程数/REC batch、基线核心OCR墙钟时长、Android当前热状态数字（不是°摄氏度）、系统剩余/总RAM、JavaHeap和NativeHeap估算、全部附加REC的耗时。导出本机ZIP仍为同一真实NV21/原始裁剪+张量，在`metadata.json`新增profile/threads/batch/额外预算字段；额外候选不会混入原始OCR条目，保真和追溯完整。

## 建议同手机真实A/B验收

1. 初始选择“对照2/批1”，在稳定同一场景的手机/USB相机画面手动诊断3次并复制文本，等待模型热身稳定后统计每档DET/REC/总耗时。
2. 依次切“均衡3/批2”“高质量4/批2”“吞吐4/批4”，在同一距离和亮度重复。只有**实测**才能决定4核/大batch究竟快不快。
3. 以前的真实竖排说明牌用于手动观测原1字→额外Small 24字候选是否能手机再现；也测试普通真竖排和横向价目表，确保误报时**从不自动损伤原文**。
4. 连续运行5/10/15分钟、记录Android热状态以及稳定p50/p95，而不是把首次模型加载/摄像头开机计入稳态OCR速度。若高质量档更快且稳定，再考虑把候选修复接入独立真人确认的朗读路线。
5. 现阶段没有Android自动切换3或4线程的持续调速算法；只有**额外REC预算随热状态降级**和用户手动选择档位。不要把它误称完整的硬件NPU加速或自动性能调度。

## 源码/私隐界限

- 核心代码：`OcrPerformanceProfile.java`、`OcrHardwareProbe.java`、`VerticalCropAxisGuard.java`、`VerticalRecRescuePolicy.java`；Android `PaddleOcrEngine.kt`、`MainActivity.java`、`SingleFrameEvidence.kt`只在隔离实验分支改动。外部 SDK 不提交第三方复制版，CI固定 v3.7.0 并运行`scripts/patch_x200_shadow_sdk.py`的**显式安全锚点补丁**，原官方crop函数路径保持不变。
- 工作流：`.github/workflows/phone-ocr-single-frame-diagnostic.yml`只构建独立**Debug**，并严格验证包名、无联网权限、Small DET/REC SHA、ARM64库。
- 使用说明：`docs/phone-x200-performance-lab-20261103.md`。
- **所有用户曾经上传的手机原图/ZIP/个人文字从未推至Github/Actions，本轮CI只有仓库程序源码和公开下载的模型，不含私人图片**。
- GitHub `main`只更新本PROJECT_HANDOFF技术交接内容，不覆盖原正式安装包/源码。

**结论：真正可安装、单机离线的X200专用A/B诊断APK已通过CI打包并由本地校验哈希；下一重要数据是用户在实际12GB vivo X200 Pro上试不同线程/batch及正常/倾斜竖排场景，获得端到端延迟与误伤证据。**
