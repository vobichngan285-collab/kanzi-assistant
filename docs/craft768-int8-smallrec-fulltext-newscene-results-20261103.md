# 看字助手｜CRAFT768 INT8 + PP-OCRv6 Small REC 联调研究（2026-11-03）

## 决策：独立源码联调已成功，**不能因此把 CRAFT 直接升为默认检测器**

本轮实做了 isolated Android 端 CRAFT768 INT8 检测框→四点透视裁剪→PP-OCRv6 Small REC ONNX 推理→CTC解码→基本阅读顺序的联调代码，源码编译通过、纯Java识别解码基础测试通过。**但新抽取的36张公开真实图片上，CRAFT INT8整图文字整体表现比原版Small略差，且Linux全流程耗时约3.5倍；不应生成新APK、不应修改正式默认 OCR 或声称手机端已完成整条链路。**

### 实验 / 工作流

- 分支：`test/craft768-int8-small-rec-linkage-20261103`
- 成功的 Android CRAFT+Small REC源码编译和完整文字评估：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41592177297
- 模型字符表与真实Small REC ONNX输出class数一致性成功验证：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41592627700
- 候选模型 **CRAFT768 INT8 20,946,608 bytes**, SHA256 `9ab885d2f6669b77f70e9c8f68acfbe408c0b051501fd02d94bcaa0c7779ab15`; Small REC SHA256 `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`; Small检测仍用原始权重。
- 早先Android独立裸CRAFT真机压力测：vivo V2405A，连续6轮24次推理，CRAFT纯网络P50从544ms变化到约665ms，PSS从608MiB下降到547MiB，无持续大量泄漏证据；这**不包含REC、图像文字框/阅读顺序和TTS**。新Android联调源码只编译，尚未真机加载Small REC，更不能把Linux REC耗时当手机耗时。

## 一、Android源码联调：实际完成的组件

- `craft768-int8-probe/app/src/main/java/com/vobich/kanzi/craftprobe/CraftSmallRecLinkage.java`
  - 只在独立 `craft768-int8-probe` 实验应用中被引用；
  - 准确校验 Small REC 模型字节数和SHA；只从未来获批准的实验应用资产中加载Small REC和 `small_rec_chars.json`。**当前未打包该权重，也没有提供新APK**；
  - 先用以前通过真实照片与OpenCV对照的Java CRAFT四边形解码器，再四点透视裁剪、竖向裁剪按1.5长高比旋转90度、缩到48px高，按RGB `value/127.5-1` 排列 NCHW动态宽浮点张量；
  - 调用ONNX Runtime `1.21.1` CPU会话 `[1,3,48,W]`，纯Java `SmallRecCtc.java` 贪婪 CTC解码，0是blank，重复字符须有blank分隔，结果按简单行/纵向排序；
  - 研究按钮⑩仅写到该分支，未来单独批准测试APK后才可能在手机运行。只在本机显示识别文字，JSON仅记录匿名数量/耗时，**不上传文字或原图，也不自动TTS**；
  - 阅读顺序为基础行聚类，**并未解决多栏阅读或成段朗读重建**。
- Gradle `:app:compileDebugJavaWithJavac` 成功，原看字助手的`app/src/main`路径没有集成该类。独立 CTC/CRAFT 基础单元测试也成功。
- `scripts/prepare_craft_smallrec_lab_chars.py` 由固定 SHA 的 `inference.yml` 生成Unicode JSON词表；用真实ONNX Runtime CPU执行输入`[1,3,48,160]`得到输出 `[1,20,18710]`，词表 **18,709 个字符+一个blank类**，二者数量严格一致。字符表SHA256 `15d77eb7bb02d9f03bfd63929922894e5cde92b037c3a82f8d4de423e226b6b2`。
- 此阶段的Android代码通过编译，不表示Android实际Small REC模型加载/透视裁剪/整页文字朗读完成。Android Bitmap+Canvas几何与Python OpenCV插值方式有所不同，需经过另行批准的独立测试构建才能完成手机端对照。

## 二、新的一组36张公开照片：实际字词与插入错误评估

固定取公开数据 `Yesianrohn/OCR-Data` 的 `ART`、`RCTW`、`ReCTS` 各从索引800抽取12张；在本轮脚本开始前写死选择，没有根据识别结果挑图。**此前研究已多次使用同一公开数据源，不能声称完全不曾研究过的真正独立盲测**，也没有拍摄用户手机私人照片。

同一 PP-OCRv6 Small REC 对两个检测器的文字框都进行实际文字识别；检测后逐框裁剪，使用同一字符解码方式、相同阈值与Python参考前后处理。GT标签只用于评估，不用于筛选框。时间在GitHub Linux x86 CPU进程，**不对应用户Vivo手机实时延迟**。

| 36张、237 GT标注行、1287个归一化GT字符 | 原版 Small DET+REC | CRAFT768 INT8 DET+Small REC |
|---|---:|---:|
| IoU≥0.4匹配GT行 | **139** | 138 |
| GT行匹配率 | **58.65%** | 58.23% |
| 漏匹配 GT 字符 | **423** | 475 |
| 已匹配文字行的识别编辑距离 | **98** | 107 |
| 漏匹配+已匹配编辑CER**代理** | **40.48%** | 45.22% |
| 有文字但无GT配对的框（不等于误检） | 47 | 59 |
| 检测输出框数 | 186 | 197 |
| 实际识别输出字符 | **1110** | 942 |
| 拼接整图文字序列的编辑距离（含插入惩罚） | **445** | 518 |
| 拼接序列编辑率（依赖简易阅读顺序，非真实CER） | **34.58%** | 40.25% |
| 每图完整DET+REC平均时长Linux CPU | **293.64ms** | 1036.34ms |
| 每图完整DET+REC P95 Linux CPU | **642.12ms** | 1590.72ms |

序列编辑率按简单纵向中点/水平位置排序，含所有模型输出框，因此比只考虑已匹配GT框的代理更接近「误读/漏读/重复」的总量评价，但依然受GT标注切分、旋转文字、多栏排版、标点归一化的影响。它**不代表真实全页语音朗读CER或字词可理解率**。

### 差异分布：不是所有场景都变差

| 公开场景类（各12张） | GT行 | Small匹配 | CRAFT INT8匹配 | 整图序列编辑数Small→CRAFT |
|---|---:|---:|---:|---:|
| ART | 86 | 47 | **60** | 209→208 |
| RCTW | 96 | **57** | 40 | **187→240变差** |
| ReCTS | 55 | 35 | **38** | **49→70变差** |

- 非竖屏25张GT142行：Small匹配86行，CRAFT匹配99行（CRAFT +13）；竖屏11张GT95行：Small匹配53行，CRAFT仅39行（CRAFT -14）。
- 一张RCTW索引810的竖图有32行人工GT，Small匹配19行，CRAFT仅9行；单图整图编辑数 42→92，是整体落后的主要贡献之一，但**即使移除此张图，其余35张的CRAFT代理CER和序列编辑率仍逊于Small**。
- 假设：部分高密度竖屏文字可能更依赖高分辨率检测；原版Small DET最大长边1600，CRAFT768最长边仅768，因缩图产生潜在漏检。但这只是合理的原因线索，**未做严格控制分辨率/设备的因果验证**。
- ART中CRAFT对不规则场景文本仍有补漏收益，因此不能得出“CRAFT无用”或“CRAFT全面优于Small”的结论。小规模不同数据集差异还不能支持一个可以部署的自动场景选择器。

## 三、决策门槛与后续方向

1. **不替换默认版、不生成新APK。** 本轮新增照片上，CRAFT768 INT8整体识别质量不比Small好，延迟大幅增加；仅因为Android能编译/CRAFT单模型能在手机跑不能合理上线。
2. **保留已开发的隔离式 CRAFT+Small REC 联调代码**，便于未来如果验证到明确的专门场景收益再作为可选择实验功能；不要立刻把两个检测器常驻叠加以求补漏，这会增加更高的内存和时延成本。
3. 将后续主要研发关注重新放回**Small版小字漏检与竖屏拍摄细节**：优先研究针对小字的局部ROI裁剪、线重建和按需二次扫描（默认不反复整页重算），在实验分支与已测集/新样本客观对照后，再考虑是否在独立Android实验版测试。
4. 真正的TTS首字延时仍未测。**不得把单纯检测时延估作完整语音朗读时间**。如果要进一步评估CRAFT特殊用例，应选择更大样本与明确的场景定义，避免已用HOLDOUT反复调参导致过拟合。

### 复现文件

- `scripts/craft768_int8_smallrec_newscene_fullpage_ab.py`：固定偏移新抽取公开图+有插入惩罚的整图文本对照。
- `scripts/prepare_craft_smallrec_lab_chars.py`：模型+词表SHA、Unicode词表与ONNX CTC类别数严格校验。
- `craft768-int8-probe/app/src/main/java/com/vobich/kanzi/craftprobe/CraftSmallRecLinkage.java`：实验 Android 图像裁剪与REC推理连接。
- `craft768-int8-probe/app/src/main/java/com/vobich/kanzi/craftprobe/SmallRecCtc.java`：Java CTC字符处理和基本行排序。
- `.github/workflows/craft768-int8-smallrec-linkage.yml`、`.github/workflows/craft768-smallrec-charset-contract.yml`：隔离源码构建和真实OCR实测。报告保留数值CSV/JSON，不保存照片、不生成APK。

**所有新增工作位于 `test/*` 独立分支，未改动正式发布版。**
