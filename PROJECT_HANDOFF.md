# 蓝心／蓝星向导 — 当前研发交接（2026-11-27）
新对话先读本页。只恢复以下两条活跃研发线，不自动恢复旧实验。正式APP保持不动。历史流水账已从本页移除，原记录可由 Git 历史追溯（清理前提交 e29ee62f96a5e7fed8f43c2c4558a1ae4b09dc7e），无需日常读取。

## 1. 当前任务 A：眼脑协同 GPU v0.2.4 — 真机GPU验收失败
- 分支 exp/eye-brain-gpu-20261127；构建提交 46fcd2239c95c0d1432c701fc5b60ae8ad20cece；[CI](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45858313534) build/publish success。
- [APK 直接下载](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-gpu-v024-20261127/BlueStar_EyeBrain_GPU_0.2.4.apk)；独立包 com.vobich.bluestar.eyebrain.gallerygpu，versionName 1.0.23（兼容原厂目录），首页标识 v0.2.4。
- 以曾真机GPU正常的 v0.2.0（08977a0f4e57d02d22a3b85901e39992e6b32b2d）为回归基线，恢复原厂目录、ABI、任务注册、无OCR常驻；保留八任务和自动提交。Google AI Edge Gallery 1.0.23 原工程、Gemma原权重与推理不改。v0.2.3 GPU选择后退出，根因尚未定位；v0.2.4 是隔离回归，不保证修复，也未保留全部精简优化。
- 最新用户真机反馈：v0.2.4 GPU加速仍打不开，验收失败；用户明确复现：点击GPU选项并按确定后软件自动退出，重新打开仍默认CPU，与v0.2.3表现一致。用户进一步确认故障发生于模型已加载后切换GPU。用户确认未加载模型时无法选择GPU，空载切换对照不可执行，不再要求重复测试；尚无logcat，不能区分主动重启、Java异常、native崩溃或配置未持久化。优先对照v0.2.0的设置确认回调、配置保存、引擎释放及GPU初始化链。停止盲目打包，优先与曾成功的v0.2.0逐项源码及运行环境对照，取得原始错误后定位根因。核对GPU初始化、实际后端、首字/总耗时、稳定性及崩溃日志；同模型同输入对照CPU/GPU。先取得真机结果再修改。

## 2. 当前任务 B：OpenOCR v0.4.3 原厂 RepViT-DB 画框工具专项
- 尚未开发或构建 v0.4.3。当前 v0.4.2 分支 exp/openocr-field-db-20261127，构建 e630d3e0267cd311457ffb3d7071ebdf433c4b82，[CI](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45855950792) 成功，[APK](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-field-db-v042-20261127/BlueStar_OpenOCR_Field_DB_0.4.2.apk)。默认DB全图原框去重逐框识别，保留YOLO切换、原区域模式、原Reader、RepSVTR、字典、CPU4、原文TTS和诊断；每次只运行一个检测器。不接大脑。
- 用户实地反馈：DB在倾斜店铺招牌、街道自然文字更有优势；YOLO26n在规整价目表、小字可能更准确。**默认YOLO已经多轮专项优化（完整列裁剪、ROI、主阅读区等），原厂DB几乎没有同等专项优化，仅有通用重建/扩框实验；不能将当前结果直接归因于模型优劣。**
- 夜间倾斜招牌单图热启动 n534/s604/m879/l1074/x1854/DB506ms；s、x、DB检出中文主招牌，DB另覆盖小字但漏具体时刻。历史同图layout4 DB1415ms、YOLO2371ms；十项价格首读8/10与10/10，DB两处错误负号的扩框候选经人工核对正确，不代表自动纠正。输入尺寸不同、样本有限，不宣称普遍优越。
- P0 核对官方预/后处理与Android移植；P1 四边形透视矫正及裁剪；P2 DB专属横排行列重建及小字适配；P3 项目金额对应；P4 主阅读区适配；P5 初始化、导入及识别提速。
- 保留原DB和YOLO稳定基线，逐项消融，同图同识别器，记录输入尺寸、漏检、误检、裁剪/识别错误及耗时。不得牺牲街景覆盖、猜测金额或删除负号。暂缓双检测器自动切换，待DB兼顾两类场景再决定。等待用户授权开始源码修改。

## 3. 必须保护的有效基线（非活跃实验）
- OpenOCR v0.4.1 原文TTS：用户明确授权冻结，真机反馈快速（约零点几秒至1秒为主观体感）。分支 exp/openocr-system-tts-20261126，提交 c550509adb2997c5a009a6c5c4a461df4e7f393c，[APK](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-focus-tts-v041-20261126/BlueStar_OpenOCR_Focus_TTS_0.4.1.apk)。原YOLO26n、RepSVTR、CPU4、四区域模式，原文直接系统TTS，无AI修饰。不得覆盖或删除冻结版本及tag。
- OCR v0.1.3 核心固定提交 38df92b5bec824a2abf269e2d775e6efcd241455；眼脑实验按原blob复用。六模型竞技场 exp/openocr-arena-20261126 固定参考 5ac4b9a853a4e2eee539894ac9f76f53ccc3aaaa，供DB原资产与对照复用。
- 正式蓝星向导 v1.0.98：分支 exp/c110-event-listener-v0198-20261125，提交 a1850a37e965e1b9115ca26dd277755749811c03，[APK](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-event-listener-20261125-v0198/BlueStar_Guide_Event_Listener_1.0.98.apk)，CI成功，完整真机验收未确认。C110被动拍照通知、移除自动删图；正式功能保持，不主动恢复硬件研发。早期RC25及其他硬件历史不再列为当前任务。

## 4. 研发纪律
- 每次实质进展立即更新本页，提交main、回读验证、告知提交链接；失败明确报告。保持当前状态简洁，已结束流水账从当前页移除，仍被依赖的成果只保留必要引用。
- 不新建仓库；未经授权不改源码、构建或发行。独立实验分支、独立包及新Release；不动正式APP、不覆盖冻结基线。核对源码、CI、签名、发布及真实设备结果，CI成功不等于真机成功。
- 眼睛OCR、大脑Gemma、嘴巴TTS分开；保留原文快速朗读通道。禁止上传私人照片、原始OCR文本、地址、Key等。
- 旧CRAFT/ChineseOCR-Lite及其他过时实验不主动恢复。历史分支及其他文档尚未物理删除：当前接口不支持删除分支；本次只精简唯一交接入口，不宣称全仓清理完成。

- GPU排查补充：用户截图确认模型配置弹窗内GPU可正常选中，点击OK后退出，再进入仍CPU。可见参数：上下文4000、Top-K 1、Top-P 0.95、温度1、视觉预算280；推测解码状态不可见。源码compare 08977a0...46fcd22仅apply.py及workflow两文件变化，推理补丁无直接差异；原生库实际哈希尚未比较。下一步定位原厂配置确认回调及持久化退出诊断，不修改模型参数。

## 最新决策：放弃 v0.2.4 修补路线
用户明确要求从 Google AI Edge Gallery 原厂源码重新建立独立实验。先纯原厂基线，不接 OCR/ONNX、不改推理或模型配置，真机 GPU 通过后再逐项集成。旧 GPU 实验仅作为失败记录，不再继续修补。

- 新原厂基线分支 exp/gallery-clean-gpu-20261127，构建配置提交 871105a27ae6b2395ff2c1515c29151b7c7725b4；直接检出原厂 9cb31cf47c21b0ed76901ff589a52b509803edfa，不执行任何 overlay，原包名版本不变。预期发布 gallery-clean-gpu-baseline-20261127，APK Gallery_Clean_GPU_Baseline.apk；尚未确认构建成功或真机通过。

- 用户追加硬性要求：原厂重建版必须内置持久化诊断日志、软件内查看和复制；追踪配置确认、后端切换、引擎释放与初始化，尽可能读取 Android 历史进程退出原因。诊断为唯一功能改动，不继承旧补丁。当前纯原厂构建配置不含诊断，不作为最终交付。

- 原厂诊断版构建配置已提交 dc4efaf0600143b5b395dbedb33a26a1cf2f061c。CI 在固定原厂源码上仅注入 MainActivity、ConfigDialog、LlmChatModelHelper 日志及 GpuDiagnostic.kt；同步落盘、启动弹窗复制、Android 11+历史退出原因、Java异常。预期 APK Gallery_GPU_Diagnostic.apk，tag gallery-gpu-diagnostic-20261127。尚未编译验证；未直接追踪配置持久化内部调用或完整 native tombstone，日志窗口仅最近64k字符。原厂包名不变，可能签名冲突。

- 诊断CI 45865517062失败于注入脚本：原厂 instance.conversation.close() 有两处，唯一匹配断言失败，未进入编译。修正提交 40500464343edb422ed3091da291223bf0f6e1b4，明确两处埋点并处理 ActivityManager 可空；不删原厂功能。用户怀疑旧版功能精简破坏GPU，待原厂诊断版真机验证。

## 原厂重建的强制增量验收纪律（用户确认）
先验证完整原厂+持久化诊断的GPU切换及推理。随后每个独立版本只引入一个可归因的变更：品牌外观、逐个隐藏不必要入口（保留注册/生命周期/依赖，禁止直接删组件）、已验证OCR v0.1.3（ONNX依赖、原生库打包与OCR接入应再拆为独立步骤）、眼脑协同、历史自动任务优化逐项恢复，最后才考虑删除确认无依赖的组件。每一步必须真机验证GPU切换、实际推理及已有功能，记录commit、APK、日志、验收结果；失败立即回到最后真机通过的基线，不在失败版本叠补丁。不允许仅CI成功就宣称验收。旧优化从历史提交/旧交接文档逐项核实再移植；尚未完成完整清单，不假定历史记录齐全。用户重点怀疑旧精简误删GPU相关组件，尚待对照证实。保留诊断能力及回退锚点。

## 最新用户纠正：恢复已真机通过的自研版本，停止纯原厂重建路线
用户手机已有原厂 Gallery，无需重复安装纯原厂诊断版。历史交接 e29ee62... 第2429行明确 v0.2.0 GPU运行、截图4.7s；v0.2.1/2优化和精简之后的GPU状态需重新核实。已核实原始v0.2.0 release target 08977a0f4e57d02d22a3b85901e39992e6b32b2d 及原APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-auto-v020-20261126/BlueStar_EyeBrain_Auto_0.2.0.apk 。新分支 exp/eye-brain-v020-diagnostic-20261127 从该提交派生，bccc1d16b475fed562818f497b71cc534420a86d 仅修改workflow，在原v0.2.0 overlay和资产验证后注入诊断；原包名/版本/功能不变，独立release bluestar-eye-brain-v020-diagnostic-20261127，预期 APK BlueStar_EyeBrain_0.2.0_Diagnostic.apk，尚未编译或真机验证。签名可能与旧APK不一致，不卸载旧版或删模型。旧纯原厂CI不作为交付，不再继续该路线。先验证原始v0.2.0及诊断复刻，再逐项恢复后续优化。

## 当前唯一眼脑研发任务：v0.2.5 增量优化（替代上述纯诊断实验）
用户明确放弃仅诊断的安装包，要求从真机成功v0.2.0选择失败v0.2.3的小范围优化并附日志。新分支 exp/eye-brain-increment-v025-20261127 从08977a0f4e57d02d22a3b85901e39992e6b32b2d直接派生，提交660889011cb03f4f15955af8932467b2ba8f3709。CI仅在原overlay后注入精简快速阅读/招牌提示词、原厂实时指标入口、OCR解码/加载/检测/识别分段计时与自动提交日志、持久化GPU/退出诊断及首页标识v0.2.5。原包名/versionName/versionCode与v0.2.0保持（签名可能不兼容）；不改OCR核心、CPU4、会话每次释放、GPU/模型/原生库配置、ABI、任务注册、通知组件及素材。暂缓OCR常驻、取消遮挡弹窗及输入并发改造、本地allowlist、组件精简。验收GPU可选并实际回答，八任务原功能正常，同图同模型比较快速阅读回答内容及耗时；当前仅编译计划，不保证提速。预期APK BlueStar_EyeBrain_Increment_0.2.5.apk，tag bluestar-eye-brain-increment-v025-20261127。旧纯诊断任务弃用，不要求用户测试；接口无取消运行能力。以后真机失败回退最后成功源码，实验包必须有实际优化目标、持久诊断、一键复制，成功冻结，失败记录不继承失败源码；仅编译错误可修正构建。未验证新APK签名或真机，不卸载旧版。

- v0.2.5 CI 45867889988 首轮失败：原overlay和新增诊断/优化注入均通过，Gradle配置阶段无法解析原厂 Hilt plugin 2.60.1（Google/MavenCentral/Gradle Plugin Portal均未解析）；尚未进入Kotlin编译，不能判定代码或GPU故障。已按原提交重试失败任务，不更换依赖、不修改源码。

- v0.2.5 CI 45867889988 第二次attempt completed/success，build与publish均成功；Release bluestar-eye-brain-increment-v025-20261127 非draft，target 660889011cb03f4f15955af8932467b2ba8f3709。实际APK 430926668 bytes，SHA256 06be30aa8063bca877642f133efc36f3576d3b1ed3835567fc44810ab416c1d0，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-increment-v025-20261127/BlueStar_EyeBrain_Increment_0.2.5.apk 。真机GPU与提速未验收；首页v0.2.5，系统包版本沿用v0.2.0，签名覆盖兼容性未知，不卸载已有成功版本。先测试GPU切换与实际回答，再测试选图自动阅读；退出后重开复制日志。
