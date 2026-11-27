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

- v0.2.5 真机GPU验收未通过。用户持久日志三次 CONFIG_OK 后约22ms Java NoSuchElementException: Key Visual token budget is missing in the map，ModelPageAppBar.kt:446，未进入GPU初始化。固定上游源码已核实：TaskConfigDialog LaunchedEffect异步ModelInfo读取后 ModelUtils.configureVisualTokenBudgetForModel 新增配置和值，ConfigDialog的remember无key只初始化一次；DropdownRow可显示default但未写入values；提交时curConfigValues.getValue(key)抛异常。高度支持动态配置与弹窗快照不同步，非三次GPU驱动崩溃，非已证实组件误删。另一次CONFIG_OK返回、CPU会话/引擎close均完成、GPU Engine构造完成，initialize开始后12299ms系统EXIT reason=10 [FORCE STOP] single-cleaner，未记录初始化完成；不能认定GPU初始化失败或成功，需区分用户清理/系统停止。可临时手动重选已有视觉预算280写入map再切GPU；下一版本从真机成功v0.2.0派生，最小修复动态配置合并（保留用户编辑、补模型实际有效值、提交前校验，不能仅吞异常），保留诊断，暂不进一步精简。此次仅分析与记录，未修改源代码或构建。

- v0.2.6 已获用户授权，独立分支 exp/eye-brain-config-v026-20261127，直接派生v0.2.0 08977a0f4e57d02d22a3b85901e39992e6b32b2d；提交 fd31e8db2b85310b2d66bdb2409dc4ab85cd9e59。基线原overlay后注入ConfigDialog SideEffect补新增键且保留已有编辑；ModelPageAppBar提交前以最新模型配置合并用户选择，缺失配置才用原厂默认，过滤已失效键，统一比较/保存/回调；诊断增加CONFIG_SYNC/RESOLVE/SAVED及conversation创建阶段。未继承v0.2.5提示词/指标优化，不改模型、OCR、GPU运行库。实际APK待编译，预期 BlueStar_EyeBrain_Config_0.2.6.apk；原包名系统版本沿用v0.2.0。CI [{"id":45873820865,"status":"in_progress","conclusion":null,"url":"https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45873820865"}]。验收：反复打开配置直接切GPU不闪退，等待初始化完成并实际回答；若失败重开复制日志，单独区分系统清理。当前未验证真机或签名覆盖。

- 用户补充真机验证：v0.2.5 手动重新选择视觉Token预算280，再选择GPU并确认，用户反馈“可以的”。支持配置缺键/同步问题判断；尚未明确是否已完成GPU初始化及实际推理，不宣称完整GPU验收。v0.2.6仍按已提交的最小配置修复路线，不增加变量。

- OpenOCR v0.4.2 RepViT-DB 新真机样本（食品包装实拍，3072×4096，CPU detector4/rec4，mode4全图原框）：detector_init109ms、detect239ms、rec318ms、core558ms、点击至原文691ms、TTS入队756ms/首段开始回调758ms（非实测出声），PSS440511KiB；14框、13非空、1空白待核、0去重。识别得到主要商品字样、生产日期/保质期标签及数字等；无原图不能核实准确率。存在同一倾斜行标签与值被分拆、跨区域阅读顺序、跨行文字碎片；raw1 rec0.509/min0.400未标疑，主标题min0.340未标疑，raw0空白。v0.4.3优先保留原四边形检测及原文TTS，做倾斜行/标签值关联、低置信度与空白诊断、核实漏检小字（需原图同图对照）；不凭文字猜补，不急于压缩速度，不接大脑。此次仅记录样本，未改源码或构建。

- 用户提供同次食品包装原图，视觉核对确认主标题、宣传语、两日期及保质期基本正确，但密集配料/厂家/产品说明、营养表可见项目数值、底部小字大范围漏检，手指遮挡不计。图片在对话中预览1152×1536，运行日志源3072×4096，不上传原图。核查DbLocator.kt：动态输入长边上限960（若ONNX固定shape则以固定尺寸为准），概率阈值0.3、框均分阈值0.6、短边过滤3/扩张后5，存在小字下采样和过滤风险；未确认实际输入shape及各过滤丢框数。v0.4.3优先提高密集小字检测召回：保留960基线，先对照更大输入或局部原图重叠分块，记录实际shape及过滤计数，后续才调整阈值/排序；全局与局部坐标去重，保留原四边形。不能以速度或14框数量代表完整识别。未获本轮源码修改授权，不启动构建。

- v0.4.3 用户已授权实施；独立分支 exp/openocr-db-resolution-v043-20261127 从v0.4.2 e630d3e0267cd311457ffb3d7071ebdf433c4b82 派生，最终提交8800224d1e5667363fe2a4625254faddc16c48fd。DB选择960默认/1280/1600长边，保持原缩放规则及固定shape约束，日志记录实际tensor/概率图尺寸、shape动态性、轮廓数、各原过滤拒绝数及接受数。未改阈值、识别、排序、TTS、线程、YOLO，暂不分块；固定shape可能使高档位无效，尚未取得模型shape报告（release文件直接读取404）。同图全图原框CPU4分别测试三档，比较漏检/耗时而非只比较框数。包名保留fielddb，versionCode12/0.4.3，签名覆盖未确认。CI []；预期BlueStar_OpenOCR_Field_DB_0.4.3.apk，尚未验收。

- v0.4.3 CI 已触发，45874870699 in_progress，https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45874870699 ，对应8800224d1e5667363fe2a4625254faddc16c48fd；APK未发布。

- 同张3072×4096热干面包装补充YOLO26n原版对照(v0.4.2 mode4 CPU4)：init183ms、detect422ms、reader_load239ms、rec778ms、core1202ms、点击原文1659ms、TTS入队1685ms/首段回调1687ms；35原框、去重1、34识别框、2待核、0空白、PSS477751KiB。DB同图14框、core558ms、原文691ms、TTS首段758ms；DB主标题完整/倾斜宣传语完整，YOLO主标题只读干面味、宣传语基本漏；YOLO多识别配料棕榈油/玉米面/辣椒粉、营养2207千焦/蛋白质/9.9克/26%/17%等，但仍漏检、碎框、表格关联错误。速度对比受框数及冷热加载差异影响，框数不等于准确率。YOLO部分有效小字det低于0.3（不同模型分数不可直接等同）。维持DB v0.4.3分辨率单变量对照，不提前调整阈值或混合检测。此次仅记录对照结果。

- v0.2.6 CI 45873820865 completed/success，已验证正式发布非draft，target fd31e8db2b85310b2d66bdb2409dc4ab85cd9e59，APK 430926668 bytes，SHA256 fee666544dcd53f05f68228353c615d676234d8c463eaf726f01db8afe3c3558；下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-config-v026-20261127/BlueStar_EyeBrain_Config_0.2.6.apk 。真机仍需验证直接选GPU、不重选视觉预算及实际推理。v0.4.3 CI 45874870699 查询仍in_progress。

- v0.4.3 CI 45874870699 completed/success，Release已核实非draft，target 8800224d1e5667363fe2a4625254faddc16c48fd；APK 65709570 bytes，SHA256 b71d58adc634dd42ecbea53893d4ebb9e4f70bb8c496e64d8a8eb244713eccd5；下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-field-db-v043-20261127/BlueStar_OpenOCR_Field_DB_0.4.3.apk 。同图全图原框CPU4，DB 960/1280/1600三档对照，核查实际input及过滤统计；模型权重未改变，真机效果待验收。

- v0.4.3 真机同张3072×4096热干面包装DB1600/全图原框/CPU4：model_shape=[-1,3,-1,-1]，实际input/prob_map=1216×1600，动态shape确认；contours78、short<3拒21、invalid0、score<0.6拒0、expanded<5拒0、accepted57；去重1、56识别框、3待核、1空白；det723ms、rec1599ms、core2324ms、点击至原文2357ms、TTS入队2386ms/首段回调2388ms，PSS568759KiB，det/reader加载0。对同图旧DB960 14框/558ms/core、YOLO35框/1202ms/core，框数4.07倍/1.63倍（不等同准确率，冷热加载不同）。新增大量配料、完整致敏原、储存/食用方法、标准、厂家/地址/许可等；仍有倾斜行顺序乱、营养表粘连碎框、2207千集及70/0等误识别，低字符置信未待核。此次有效性证明提高分辨率为主要方向，0.6过滤在此样本1600档并非瓶颈；21个短边轮廓需可视化核实，不能直接判定漏字。先补1280同图测试及必要的960新版基线，不立即增加新变量；下一轮可对营养表做局部切分/原四边形透视及阅读行重建。暂不改源码、不构建。

- v0.4.3 同张热干面包装DB1280/全图原框/CPU4：实际input/prob_map960×1280、contours80、short<3拒46、invalid0/score拒0/expanded拒0、accepted34，34识别框、1待核/空白；det419ms、rec835ms、core1255ms、点击至原文1284ms、TTS入队1350ms/首段1355ms，PSS710195KiB，det/reader加载0。相较1600 57框/2324ms/core，1280漏配料和致敏原但营养表能量/2207千焦/蛋白质/脂肪/饱和脂肪/碳水化合物分框识别明显更佳；1600主标题正确而1280误作武汉渔干面味。1280短边拒46 vs1600拒21，两档0.6 score过滤均0。1280 PSS反高于1600，可能受会话分配器缓存/先后顺序影响，需冷启动同条件复测，不能认定1280实际峰值更高。后续建议保持1600完整阅读对照，另做1280短边3→2独立对照并记录短边被拒轮廓及实际新增有效文本；不降低score阈值，暂不混合双模型/多档全图检测。后续局部复检、透视及行表重建根据实验再定。此次仅记录，不改源码/构建。

- v0.4.3 新价目表场景3072×4096 DB1600 CPU4 mode4：实际1216×1600，contours108、short拒40、score拒0、accepted68，68裁剪、18待核(含2空白)、det734/rec1495/core2231ms、点击原文2535ms、TTS首段2565ms、PSS656276KiB，det init143/reader139ms。主价目表价格多已检测，原框按top排序造成价格先于项目，艾灸/经络刮痧/拔罐热敷碎框乱序，-120元待原图核实（不可自动删负号），背景干扰。focus_reason=dominant_region, groups8,best253.85,rival52.87。核实MainActivity mode4明确绕过FocusPlanner ROI及RowPlanner；现有智能主阅读区mode0已使用ROI+RowPlanner(1.6,true)+ColumnPlanner及识别refine，优先同图测试现成模式，确认主区域、项目价格配对、会员充值是否保留。若不足再改：局部倾斜投影/行中心排序、碎框合并并保护价格列、仅异常裁剪复识别，原框回退/保留完整阅读；不猜补内容，不降阈值，不急于加分辨率。未提供原图，本次仅依据日志与源码，不宣称识别准确率，不修改代码或构建。

- v0.4.3 同张价目表DB1600 CPU4智能主阅读区mode0真机有效，用户反馈好很多。原框68不变，ROI Rect(1062,1313-1719,2285)，选44/区域外24，18行32裁剪，12待核/1空白，det647ms、rec732ms、core1383ms、点击原文1405ms、TTS入队1426ms/首段1429ms/完成13575ms，PSS847701KiB，det/reader加载0。对mode4 68裁剪/rec1495/core2231/首段2565ms，裁剪少52.9%，首段延迟少44.3%（冷热加载不同）。现有FocusPlanner+RowPlanner+ColumnPlanner已复用成功：10项项目价格同行，艾灸、刺络放血、经络刮痧、滑罐走罐、拔罐热敷恢复完整；-120元重裁变120元（需原图确认）；两笔会员充值保留但并为一裁剪；仍有装饰/杂字、6个价格待核，原框部分价格置信度更高。PSS较原框高不能由单次推断峰值。冻结该有效基线，不重写已有效的主区/行重组；下一轮优先保留原四边形独立价格框并对照重裁结果、限制无依据扩框、装饰孤立框诊断及原文/待核TTS一致性；完整阅读保留，短边阈值实验单独做。无原图不能宣称10项绝对准确。本轮仅记录，不改源码或构建。

- 用户归纳v0.4.3实测场景规律：店铺招牌默认全图原框效果较好，价格表/价目表智能主阅读区效果较好。结合已核实源码：mode4保留DB原四边形独立裁剪、按top/left排序；mode0利用FocusPlanner主区及RowPlanner局部方向投影/同行碎框合并、ColumnPlanner重复列间隙推断，再裁剪识别。建议未来单检测器、同次检测框几何特征自动选择后处理：重复行/稳定列间距/主区证据强时结构化，独立多方向/多字号文字保留原框，证据不足保守回退全图；保留手动模式，独立完整价格框不强制扩框。食品包装混合场景不能简单按是否存在主区裁掉全部外围。仅为用户经验及设计建议，尚无自动路由真机验证，不修改源码。

- 用户授权v0.4.4相对优化：从已真机有效v0.4.3 8800224d1e5667363fe2a4625254faddc16c48fd 独立派生 exp/openocr-adaptive-v044-20261127，提交7ac498c1a33d4946dee40e3d3173e510fcc2829c。新增手动选择的智能自适应实验模式，原默认及全部手动模式/分辨率保留；仅DB单次检测，复用FocusPlanner/RowPlanner/ColumnPlanner，要求dominant_region+连续至少4个稳定双列行、行距规律、区域外无明显独立大标题，否则原框。使用用户价目表日志四舍五入行坐标模拟，medianHeight48，支持第4–13行10对，pitch46–49.5，非Android真机验证。自适应结构化右列仅在唯一匹配完整原框时优先原四边形，低置信局部重裁一次；候选文本冲突保留原框并标待核，不擅自改数字/符号。自适应TTS朗读待核提示及区域外未朗读提示；原手动模式行为不变。新增7个几何单测，CI执行待确认。模型、阈值、分辨率及线程未改；不引入分类模型或Gemma，不做区域级混合，暂缓短边阈值实验。包名fielddb、versionCode13/0.4.4。CI 45926712897 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45926712897 ，预期BlueStar_OpenOCR_Field_DB_0.4.4.apk，未发布。验收：同一招牌/价目表/食品包装/混合场景DB1600 CPU4选择自适应，核对AUTO route/support、original_columns/retries/conflicts、实际读音及耗时；规则可能误判，手动回退保留。

- v0.4.4 CI 45926712897 completed/success，Release已核实非draft，target 7ac498c1a33d4946dee40e3d3173e510fcc2829c，APK 65724686 bytes，SHA256 3fa34937770cabd28269e70a2240f9a2baf5fa2c9e29668d1f1377c509d1bed2；下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-field-db-v044-20261127/BlueStar_OpenOCR_Field_DB_0.4.4.apk 。实际自适应路由、价格框及语音待核效果待真机验收。

- v0.4.4 新书籍/诗词正文样本3060×4080，DB1600 CPU4，日志为mode4全图原框且无AUTO route行，不能作为自适应模式验收。实际1216×1600，contours169、short拒98、score拒2、accepted69，69裁剪、17待核/2空白，det680/rec2280/core2962ms、点击原文2993ms、TTS入队3033/首段3037ms，PSS805795KiB，加载0；FocusPlanner whole_image groups3 best475.90 rival3.85。张九龄正文、感遇诗句及注释多条完整长行识别较好，但短碎框/异常长框交叉干扰、正文段落结构缺失，部分低字符置信未标待核。未见原图，不能确定透印/旁注/背景或完整真值。v0.4.4自适应仅dominant_region+重复双列触发，whole_image当前会保守原框，预计不解决此书页；建议增加独立document几何路由（连续长行、左边界/行距/角度与段落分块，保留原四边形，标题/诗词/注释及旁注分块、不无依据删字；疑似干扰分组后提示），不把书页硬套价目表合并、不默认强制裁主区，不改模型/阈值。先核对自适应选择及原图/同图主区对照；本次仅分析记录，未改源码或构建。

- 用户明确研发方向：释放DB画框工具本身性能，避免针对场景不断堆规则。建议冻结v0.4.4自动路由（未完成验收）及保留v0.4.3已有效手动模式；优先对齐OpenOCR原生检测前/后处理，检查当前DbLocator的BGR归一化、resize、minAreaRect、轮廓平均分、短边3/扩张后5、mask0.3/score0.6、矩形近似unclip(1.5，非原多边形offset)及四点排序。不能凭短边拒绝数量判断有效漏字；同概率图离线/单次推理多组参数对照，记录真文字召回、错误框、粘连、碎框、裁剪质量、耗时，覆盖招牌/菜单/包装/书籍，不先增加场景模式。原生实现核对及参数实验尚未开始，本轮无代码/构建修改。

- 用户授权开始底层画框优化。v0.4.5从有效v0.4.3基线8800224d1e5667363fe2a4625254faddc16c48fd派生独立分支exp/openocr-db-detector-v045-20261127，提交42f4acf348bd78c2d15d3d39fa333a7c3530057e；v0.4.4自适应规则不继续叠加。核对OpenOCR官方commit 1ccfc6ee6161f7133b192e16af3a9265273997ba之configs/det/dbnet/repvit_db.yml、opendet/postprocess/db_postprocess.py及opendet/preprocess/db_resize_for_test.py：BGR归一化、slow轮廓均分、mask0.3/score0.6/短边3/扩张后5/unclip1.5/候选1000匹配现有基线；官方quad先minAreaRect再pyclipper圆角偏移，当前连续矩形近似同原理非像素等价（另有resize/取整差异）。新增独立DbProfile五档：基线、小字2/4、紧框1.2、mask0.25、mask0.35；不改score0.6、不改变模型/阅读/线程/TTS。相同Bitmap身份+generationId+长边+同session缓存单张概率图，换图/换session释放，切参数不重复推理；日志拆分pre/infer/post、cache hit/miss、短边六档分布/接受分布及过滤原因。新增5项JVM参数测试，CI待验证。构建45928155495 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45928155495 ，预期APK BlueStar_OpenOCR_Field_DB_0.4.5.apk，未确认发布。验收先选1280全图原框CPU4，原始参数与小字2/4同图对照；必要时1600基线与紧框1.2、mask两档，核对真实文字增量、误框、粘连、截字及耗时；cache hit不能当端到端提速。此版无场景判断新规则，未宣称实际效果提升。

- 用户明确要求保留此前已真机有效的智能主阅读区及价目表行列重组成果；底层DB优化不等于撤销成功的后处理。v0.4.5基于v0.4.3，保留默认全图原框、智能主阅读区、原区域基线、全图列裁剪及同图对照、960/1280/1600和TTS；v0.4.4自适应路由独立存档，不继续叠加规则。今后底层优化须同时检查原框招牌与主区价目表回归，成功功能保留、未验证功能独立实验。此轮仅确认原则，不修改源码。

- v0.4.5 CI 45928155495 completed/success，Release已核实非draft，target 42f4acf348bd78c2d15d3d39fa333a7c3530057e，APK 65718590 bytes，SHA256 f02193fb05f9021519273cbe4a67f18d18eb1af6e187f8d03ada741a0146c633；下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-field-db-v045-20261127/BlueStar_OpenOCR_Field_DB_0.4.5.apk 。五档底层参数及概率图复用待真机验证，保留原框与智能主阅读区成果。

- v0.4.5真机价目表同图3072×4096 DB1600 CPU4 mode4 baseline，map_cache=hit，cached_pre149/cached_infer701ms，pre/infer0，post33/det34ms；108 contours、short拒40、accepted68、68 crops、18 suspect/2 blank，rec1518/core1553/点击原文1574/入队1594/首段1595ms，PSS815118KiB。原始框坐标、数量及主要识别输出与v0.4.3同图基线一致；缓存命中实测成功，不代表新图片检测加速。short_hist=[25,6,9,7,7,54] accepted_hist=[0,0,0,7,7,54]，2≤short<3只有9个潜在新候选，仍受扩张后4和score0.6限制，不能断言有效文字。建议同图保持1600/CPU4/全图原框，仅切换small_text 2/4；价目表再测智能主区回归，不需新构建。尚无新参数实际结果，不改源码。

- v0.4.5价目表同图DB1600 CPU4 mode4 small_text 2/4真机：map_cache hit，108 contours，short拒31/score拒1/expanded拒2，accepted74（原68+6），accepted_hist=[0,0,6,7,7,54]，post35/det36/rec1661/core1698/点击原文1723/TTS入队1742/首段1743ms，20 suspect/2 blank，PSS791191KiB。基线post33/rec1518/core1553/首段1595ms；新增6框均在此前价目表ROI之外，文本及rec：养使用他信支 .6333、m .4627、0912 .8196、隆汇 .3901、3 .1596、dd .4603；主表原框及识别未见改善。未有原图不能断言新增均误检。此样本不支持2/4默认化，保留独立档用于包装小字1280后续验证。下一步同图1600 CPU4原框仅切紧框1.2，核对裁剪质量与-120元等差异，符号须原图确认；暂不改代码。

- v0.4.5价目表同图DB1600 CPU4 mode4 tight_box unclip1.2真机：map_cache hit，108 contours/short拒40/score拒0/expanded拒1/accepted67，accepted_hist=[0,0,0,6,7,54]，post33/det34/rec1472/core1506/点击原文1529/TTS入队1549/首段1553ms，17 suspect/5 blank，PSS806354KiB。相对baseline68框、18 suspect/2 blank、rec1518、首段1595；同轮廓det .99188894对应价格从-120元(rec .8994702/min .5030069)变120元(rec .99918514/min .998689)，精油开背价格rec .8942552→.9994696，肾部保养价格 .98374754→.99946034。代价：det .83373797的小框原识别口被扩张后短边过滤；三个背景框原州/只/M变空，西麦→两麦（rec反升），滑罐走罐最后罐rec .6022→.4318；文字排序和刺络放血碎框仍未解决。无原图不能确认价格符号真值或全部误识，置信度非准确率。紧框1.2为有价值候选但不默认化，保持1.5对照、后续跨图验证；下一步同图只切mask0.25，再0.35；暂无代码修改。

- v0.4.5价目表同图DB1600 CPU4 mode4 mask_low .25真机：cache hit，105 contours/short拒37/score拒0/expanded拒0/accepted68，short_hist=[21,6,10,7,7,54] accepted_hist=[0,0,0,7,7,54]，post35/det36/rec1521/core1557/点击原文1580/TTS入队1599/首段1601ms，18 suspect/2 blank，PSS808878KiB。与baseline相比框数、待核、空白一致；主体文字及价格未见增益，-120元及碎框仍在；PRICE LIST rec .9667341→.98556787，边缘少数文本变化（巴→包、R→8、M→福，均待核）。轮廓数减少可能连接，不可仅据数量判定。此样本不支持mask .25默认化；保留.3基线，下一步同图只切mask .35完成五档比较，再跨图验证紧框及主区回归。无原图真值，暂无源码变更。

- v0.4.5价目表同图DB1600 CPU4 mode4 mask_high .35真机：cache hit，106 contours/short拒38/score拒0/expanded拒0/accepted68，short_hist=[24,5,9,7,7,54] accepted_hist=[0,0,0,7,7,54]，post33/det34/rec1514/core1549/点击原文1572/TTS入队1590/首段1591ms，19 suspect/2 blank，PSS807575KiB。相比baseline，主表价格未改善，-120元仍在；滑罐走罐末字原罐rec .6022227变装rec .24383305，det反升 .9673199→.97959197，四边形旋转明显；无真实准确率增益依据。五档同图比较完成：baseline68/18suspect/2blank/rec1518/TTS1595；small74/20/2/1661/1743；tight67/17/5/1472/1553；mask_low68/18/2/1521/1601；mask_high68/19/2/1514/1591。保持官方默认，紧框1.2为跨图验证候选，短边2/4尚需包装1280验证，mask两档不优先。下一步减少用户重复试验：同一价目表1600 CPU4原始参数切智能主阅读区做成功功能回归，再择包装/书籍做基线与紧框对照；不继续堆规则，不依据单图确定通用参数，不宣称置信度等于准确率。当前无源码修改。

- v0.4.5新增酒店倾斜招牌样本3072×4096，DB1600 CPU4 mode4 tight_box 1.2，cache miss/pre152/infer560/post41/det753ms，55 contours/short拒45/accepted10，short_hist=[24,14,7,0,0,10]，rec274/core1027/原文1065/TTS入队1101/首段1104/完成6258ms，2 suspect/0 blank，PSS888547KiB。输出名人玉岗酒店、4小时仅99元。自助洗衣房、洗浴桑拿、2026年11月26日、星期四、20:49:51等；冷艺人rec .0597/det .9831、日代rec .0805/det .9772，另3 rec .408未标待核。无原图不能确认文字真值及低置信原因。此样本尚缺同图baseline1.5，不能归因紧框改善；优先同图只切原始参数完成对照，不要求重测全部五档。价目表智能主区回归仍待测。暂无源码修改。
