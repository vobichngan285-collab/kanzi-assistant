# 最新｜2026-11-26 蓝星 OpenOCR Focus v0.4.1 原文 TTS 真机成功，用户授权冻结
- 用户明确反馈：「实验圆满成功，反应速度特别快。我哪怕给了他一张书本的密集文字，它也能在零点几秒一秒的样子给我出结果」。随后明确授权保护、冻结该版本并写入交接文档。约零点几秒至1秒为用户主观出结果体感，未提供本轮精确OCR/TTS日志、图像真值、逐字准确率或真实扬声器首声测量，不将其宣称为严格测速或全部场景性能。
- **冻结对象：** 独立 OpenOCR Focus TTS v0.4.1，源码分支 exp/openocr-system-tts-20261126，成功构建提交 c550509adb2997c5a009a6c5c4a461df4e7f393c，基于 Focus v0.4.0 c5e20868f61a1bf41a937d99af4df68ee913188e；CI 45796601073 completed/success（https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45796601073）。独立包名 com.vobich.bluestar.openocrlab.focustts，versionCode10/versionName0.4.1。保留正式APP与历史实验。
- **已回读发布与固定tag：** bluestar-openocr-focus-tts-v041-20261126，非draft、prerelease；tag直接指向上述成功提交。APK BlueStar_OpenOCR_Focus_TTS_0.4.1.apk，43776133 bytes，SHA256 03882dbc9187637fd34d2359f28e18e66ced2f946da547af9cd20dd611821d62。下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-focus-tts-v041-20261126/BlueStar_OpenOCR_Focus_TTS_0.4.1.apk 。
- **成功方案：** 原YOLO26n-Text检测、RepSVTR识别和字典、CPU4默认、Focus v0.4.0四种阅读区域模式及原行列裁剪算法不变；普通模式单轮识别，显示非空原始识别文本，按原行列顺序以空格/换行连接，完成即调用系统默认TTS，不加入诊断或待核标记，不擅自删除可疑原文字，不进行AI修饰；短文本整体入队，超过系统长度限制才无损分段，支持停止和重新朗读。同图对照保留每模式两轮且不自动朗读。记录点击至原文就绪、入队、首段及结束回调；回调不等于物理出声。APP无INTERNET权限，外部系统TTS是否真正离线取决于所选引擎和音色。
- **整体架构不要混淆：** OCR仅是蓝星向导的眼睛，Google Gemma离线模型是独立大脑，系统TTS是嘴巴；本版只验证眼睛直达嘴巴，未接入Gemma。建议保留直接朗读快速通道，理解/推理/任务匹配另走智能通道；后者尚未集成或授权开发。此前v0.1.3为OCR识别/区域算法历史基线，不是当前成功实验APP的版本；当前冻结的是包含原v0.4.0四模式的v0.4.1。
- **冻结规则：** 禁止在本成功分支原地继续实验、移动或删除成功tag、覆盖旧Release/APK、重新选型或擅改模型、字典、推理配置、原始阅读和TTS逻辑；后续经用户授权才从固定成功提交派生新独立分支、独立包名及新Release，保留可回退基线。此为用户授权的研发冻结约定与固定版本引用，并非已启用GitHub服务端强制分支保护或不可变Release。此次仅更新交接文档，不改实验或正式APP源码，不触发新构建，不上传照片、书本原文或私人信息。

---

# 最新｜v0.1.4 560混合视觉实验 APK已发布，等待真机验证
- CI45730569587 全部成功，HEAD b96bc13b451eb421bdea04336c54b84bdeb2835b；native job139611255448：46/46 JNI接口、依赖闭包、AAR原非JNI条目逐个哈希验证通过，hybrid-maven artifact15076283612；build job139611611452：单元测试/编译成功，签名v2验证，独立包名com.vobich.bluestar.gemmalab.vision560.hybrid，versionCode5/versionName0.1.4，离线权限、两个原生库哈希和模型资产校验均成功，APK artifact15077220243；publish job139613110103成功。
- 已回读Release，非草稿、prerelease，tag bluestar-gemma-v014-560-hybrid-20261126；APK 702063949 bytes，SHA256 e8ed82f22e1713e554211f9742d85fd9f1084bf16967b54bc6f09376884842a9，真实下载地址 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-gemma-v014-560-hybrid-20261126/BlueStar_Gemma_560_Hybrid_0.1.4.apk 。
- 原560修复权重不变，需重新导入原厂文件。建议同一价格表、560预算，优先GPU视觉+GPU语言（视觉允许CPU回退），对照CPU视觉+CPU语言；记录冷/热首字、总时长、识别结果及报错。BLUESTAR_VISION_HYBRID_014标记不代表实际GPU占比。构建已完成但真机加速尚未验证；不覆盖正式蓝星或旧实验。PP-OCR/Gemma组合尚未授权开发。

---

# 最新｜v0.1.4 JNI依赖校验通过，修复ZIP元数据复用错误
- CI45730170959 job139609918490：复用JNI及原厂provider哈希校验成功，46/46 JNI接口匹配，provider DT_NEEDED仅libm/libc/libdl/liblog，依赖闭包通过。AAR重写后读取原ZIP失败 BadZipFile，根因writestr复用原ZipInfo修改其header_offset等属性，并非引擎或模型失败。
- 隔离分支HEAD b96bc13b451eb421bdea04336c54b84bdeb2835b：copy.copy(ZipInfo)后写入，原条目按文件名重新读取哈希，增加testzip及条目集合检查。复用已保存JNI，不重编译，不降低校验标准。CI45730569587 in_progress。 APK及真机结果未取得。

---

# 最新｜v0.1.4 JNI已保存，补齐原厂依赖后复用打包
- CI45725894735 native job139595545930 编译4238 actions成功，1725.988s；46个JNI导出全部匹配。新增DT_NEEDED libGemmaModelConstraintProvider.so不在原厂AAR内，依赖保护检查阻止打包，非模型推理失败。已上传JNI artifact15076515697（未过期），JNI SHA256 f0dcf741d49a9a87a0b893513153c0e522d95b1fc679eb0fd54500c2061dafa5，诊断artifact15076182202。
- 隔离分支新HEAD 7d43eb7de17ef9d22443b8a7da49fdb580acdaa6：复用上述JNI，精确上游v0.18.1 bda624851d74be34d4e1f7fac1fc4155939539c7的原厂arm64 provider Git LFS SHA256 c7c7188b831ae75fcc7ec6fe613118365b311995805423a4ca265372f07f4783，校验ELF架构及依赖闭包，保留原AAR其余条目，APK校验两个库哈希。CI45730170959 in_progress。尚未取得APK或真机加速结果。不修改正式APP或模型权重。
- 用户原厂Gallery文字测试：单目标科室/药店/出站口/出租车出口正确；多店铺筛选首次4真2假，严格提示词3真0假、漏沙县小吃且编号无分隔；显式步骤排序完整正确3.3秒，隐式方便面排序5,6,3,2,1,4错误。PP-OCR与Gemma组合仍仅讨论，用户对百度方案有抵触，尚未授权开发。

---

# 最新｜v0.1.4 arm64 JNI源码编译成功，AAR下载错误修复
- CI45723493748 native job139587382686：Bazel 4238 actions编译成功、941.304s，产出bazel-bin/kotlin/java/com/google/ai/edge/litertlm/jni/liblitertlm_jni.so；cquery成功。随后获取Maven Central原厂AAR时HTTP404，未进行JNI ABI核验，APK和发布被跳过，故不能宣称混合视觉真机成功。诊断artifact15072979948；当次JNI未独立上传。
- 隔离分支 exp/gemma4-560-hybrid-20261126 新HEAD 57b57bef5750c300dcefe83d20f4f729a43d3498：将固定v0.18.1 AAR/POM提前通过Google Maven及Central候选源下载，校验ZIP/ELF/POM坐标，再编译；编译成功立即上传独立JNI artifact，以便后续兼容检查失败时复用。不改560模型、正式APP或数值补丁。CI45725894735 in_progress，https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45725894735 尚无APK。
- 用户确认修复560比280识别更准，裁切/整图价格表准确率相近；若560最终速度不可接受倾向放弃Gemma OCR，讨论PP-OCR负责文字、原厂Gemma280负责理解/问答和任务匹配的组合，但尚未授权开发该组合，不修改正式APP。

---

# 最新｜v0.1.4 Bazel依赖冲突修复并重跑
- CI45722867944 native job139585257935 环境准备、固定官方源码及混合补丁均成功，Bazel仓库映射失败：TensorFlow workspace1.bzl load compatibility_proxy_repo，而旧rules_cc无该符号。未进入JNI编译完成。报告artifact15072828212。
- 核对官方v0.18.1 WORKSPACE有两组TensorFlow/LiteRT pin，后面的TF a1179d3c224967bd979950b823d5ce2a7e8f4c94实际生效，workspace1确实调用compatibility_proxy_repo；rules_cc 0.2.9源代码含此函数，并采用Bazel 7兼容native分支。BCR source.json提供固定archive integrity。隔离分支 exp/gemma4-560-hybrid-20261126 HEAD bd81240aff962f887a36f8ff1b9be1ff2572596f 仅在CI内固定rules_cc 0.2.9及完整校验，不改模型、560预算、原修复权重或正式APP；该兼容修复尚未通过完整编译。新增WORKSPACE diff诊断。
- 新CI45723493748 已核实in_progress，尚无JNI/APK。后续先核实真实日志、ABI检查、发布及真机混合视觉准确率和速度。https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45723493748

---

# 最新｜v0.1.4编译环境权限修复并重跑
- CI45722268120 completed/failure，native job139583243019 在Prepare source-build environment失败：服务器已预装Bazelisk，全局npm重复安装触发EACCES，未进入源码编译。原560模型及混合后端补丁未改。
- exp/gemma4-560-hybrid-20261126 新HEAD 9fa1ebdad14638c7bc0ddebfa165f60bcf4ba166，构建脚本优先使用已有Bazelisk，缺失时安装至用户目录。最新CI45722867944已核实in_progress。尚无JNI/APK，需核实实际编译、ABI、发布及真机测试。
- https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45722867944

---

# 最新｜560视觉GPU+CPU混合底层引擎实验 v0.1.4（构建中）
- 用户授权开始。原仓库新隔离分支 exp/gemma4-560-hybrid-20261126 HEAD 0c0e5c413e801caa549ec48feea3ba2ecb04c319，基于v0.1.3成功提交f2d15ae7e47beca2f5afe3489750358c84fe66b4。固定官方 LiteRT-LM v0.18.1源码 bda624851d74be34d4e1f7fac1fc4155939539c7；patch_hybrid_runtime.py只改vision_litert_compiled_model_executor.cc的Encoder/Adapter GPU分支，配置CPU选项及缓存、允许kGpu|kCpu，日志BLUESTAR_VISION_HYBRID_014仅表示请求及执行完成，不代表实际GPU占比；CPU路径不变。
- 独立源码CI以Bazel7.6.1/NDK r28b重编arm64 JNI，repack_hybrid_aar.py替换同版本原厂AAR的JNI，其余Kotlin/GPU库等文件保留，要求原JNI导出子集、ELF架构、动态依赖兼容及APK内JNI SHA/标记一致；重用45719718456的精确修复资产（encoder SHA 6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92，完整模型SHA fb4159987f3cd30aba817ded3b83a04c81a4a09991d88fafee31d1325490c2f8），不重导、不降560、不修改正式APP。独立包com.vobich.bluestar.gemmalab.vision560.hybrid避免旧版签名冲突，需重新导入原厂模型。
- 最新CI45722268120 in_progress，正在准备编译环境；旧HEAD 597f0d79e32934a90e90d295526e36c35d897f79触发45722215596，系旧包名，不能作为最终交付。两者使用同一预期tag，发布脚本的targetCommitish检查会防止覆盖不同提交；如旧任务先发布，需另行使用最新成功artifact并核实新独立tag，不把旧包当新包。源码编译、ABI兼容性、实际GPU分区、真机速度尚未验证；未有APK。最新任务 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45722268120

---

# 最新｜v0.1.3 MT6991真机日志：视觉GPU阻塞、CPU视觉延迟过高
- 用户真机Android16 SDK36，MT6991，导入校验成功，完整模型3034159952字节 SHA256 fb4159987f3cd30aba817ded3b83a04c81a4a09991d88fafee31d1325490c2f8，max_visual=560、options=280,560。CPU/CPU冷3072x4096：load3055ms、首字30082ms、inference_first25953ms、total37536ms，prefill182.47tok/s、decode21.09tok/s；CPU/CPU热844x928：load0、首字26752ms、inference_first26716ms、total31640ms，prefill163.20tok/s、decode20.45tok/s。CPU视觉/GPU语言冷同尺寸844x928：load29300ms、首字57614ms、inference_first26290ms、total61971ms，prefill428.79tok/s、decode20.82tok/s。GPU语言热态未测；视觉CPU是首字瓶颈，不能凭不同冷/热条件断言GPU语言更慢。用户未提供实际识别文本，手机准确率尚待核对。
- GPU视觉/CPU语言、GPU视觉/GPU语言均在createConversation失败：vision_litert_compiled_model_executor.cc:313 -> litert_compiled_model.h:1961 Some ops are not accelerated，要求CPU accelerator。查官方固定v0.18.1源码，VisionEncoder::Initialize GPU分支仅SetHardwareAccelerators(kGpu)，adapter GPU分支也未显式配置混合后端。下一步独立实验可针对encoder允许kGpu|kCpu并配置CPU options，验证真实GPU分区/CPU回退与正确性、速度；不能仅将语言GPU或改UI标签当作视觉加速。官方源构建指南支持Bazel Android arm64 JNI；需同版本匹配原AAR和GPU库，未实际构建或测试混合后端。保留精确560权重和正式APP；若混合后端仍慢，进一步恢复原厂量化/composite结构，避免无依据改变权重或降低Token。
- 本次仅记录真机反馈及源码核查，未提交新APK。

---

# 最新｜560修复实验 v0.1.3 已发布，待真机验证
- 已通过Release API核实 tag bluestar-gemma-v013-560-corrected-20261126 非草稿，APK BlueStar_Gemma_560_Corrected_0.1.3.apk 已上传，693122351字节，README与SHA256SUMS亦已上传。下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-gemma-v013-560-corrected-20261126/BlueStar_Gemma_560_Corrected_0.1.3.apk
- 独立安装包，导入原厂280文件生成修复560，先用默认CPU/CPU验证复杂价格表，然后同图同预算切换CPU视觉/GPU语言做冷热速度对照；GPU视觉两档仅供兼容性实验。服务端识别、构建与签名已验证，手机性能与GPU尚未验收。正式APP不变。

---

# 最新｜v0.1.3 APK构建及校验成功，Release发布中
- CI45719718456 export job139574683342及build job139575215830均completed/success。复现112组修复encoder SHA256 6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92，已校验先前两图OCR报告CER=0；重组完整模型3034159952字节 SHA256 fb4159987f3cd30aba817ded3b83a04c81a4a09991d88fafee31d1325490c2f8，各section及内置资产验证成功。
- Android BUILD SUCCESSFUL，独立包com.vobich.bluestar.gemmalab.vision560.corrected versionCode4/versionName0.1.3，APK v2签名通过、无网络权限、arm64及资产校验通过；构建artifact15071023506。publish job139576434649仍in_progress，Release API暂未找到tag，不宣称已发布；手机560性能/GPU仍待验收。保留旧版与正式APP。
- https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45719718456

---

# 最新｜560修复版真机性能实验APK构建（v0.1.3）
- 复用已通过两张合成中文OCR的精确修复encoder SHA256 6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92，不重导、不重复280实验。原诊断分支 exp/gemma4-vision-numerics-20261126 HEAD f2d15ae7e47beca2f5afe3489750358c84fe66b4：新增package_corrected_560.py及独立构建workflow，先从原始补丁和原厂权重复现全部112组逐通道修正，校验精确encoder哈希及已完成OCR报告中corrected两图CER=0，再重组验证所有section与目标SHA、ModelInfo、资产完整性；之后Android单测/签名/无网络权限/arm64/内置资产哈希检查，通过才发布独立实验预发行。
- v0.1.3独立包 com.vobich.bluestar.gemmalab.vision560.corrected，不覆盖正式APP及旧实验。保持560默认和原图、离线OCR/TTS，增加四档明确的视觉/语言组合：CPU/CPU默认正确性基准、CPU/GPU语言加速（新增，绕过已知GPU视觉编译问题）、GPU/CPU、GPU/GPU实验。各档独立缓存、保持热模型复用、记录请求后端、首字/总耗时/加载、native benchmark及失败原因。CPU/GPU语言组合是待验证方案，不保证性能；GPU视觉兼容性尚未修复，需真实MT6991日志。仅独立实验版本，不宣称最终性能通过。
- CI45719718456已核实in_progress，尚无成功构建/发布，需核实export/build/publish真实结果及Release资产。计划tag bluestar-gemma-v013-560-corrected-20261126，预发行文件 BlueStar_Gemma_560_Corrected_0.1.3.apk。当前不可提供未经验证的下载链接。下一步真机同一复杂价格表/同一560预算对照CPU/CPU、CPU/GPU及GPU视觉实验，核对准确率、首字及热推理速度。
- https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45719718456

---

# 最新｜560修复后真实OCR突破（2026-11-26）
- CI45718390348 completed/success，job139570213658，artifact15072025114。复现112组权重精确反量化；Linux服务器CPU完整LiteRT-LM端到端OCR，1512x1512合成中文菜单及公告，原厂280预处理768x768/2304 patches，未修和修复560预处理1104x1104/4761 patches（超出280容量；560签名存在，按v0.18.1实际patch选择算法使用560容量）。原厂菜单CER0、公告0.04255319，耗时10.445/10.161秒；未修560 CER0.97222222/0.91489362，耗时26.532/25.414秒；修复560两图CER均0（忽略非字母数字字符与空白），耗时26.994/26.188秒。菜单商品与全部价格及合计、公告号码均正确。两张合成图仅证明本次识别恢复，不代表一般图片准确率100%，也不是手机速度。实际有效patch数4761，不宣称每次恰好输出560个有效视觉token。
- 诊断分支exp/gemma4-vision-numerics-20261126 HEAD 370b9733120822186e84648168c619780b3d8cac；修复encoder SHA256 6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92。正式APP与已发布APK不变；560 GPU兼容性、手机速度与实际复杂价格表仍未验收。下一步针对560加速和独立实验APK验证，不重做280。报告 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45718390348/artifacts/15072025114

---

# 最新｜560真实图片识别测试（运行中，尚未验收）
- CI45717784389 completed/failure，job139568197319：112组逐通道修正及560特征数值复验通过；Python litert-lm==0.18.1 Engine没有max_vision_tokens_per_image显式参数，脚本在真实图像推理前主动拒绝执行。三组均无OCR结果，不是560模型识别失败。报告artifact15070397638。
- 原独立诊断分支exp/gemma4-vision-numerics-20261126 HEAD 370b9733120822186e84648168c619780b3d8cac，修正test_corrected_ocr.py：使用0.18.1支持的接口，按实际图像patch数和模型metadata自动选签名；用1512x1512中文价格表及公告合成图，限定max_output_tokens=256，记录实际输出/CER/耗时；自动选择不能只凭ModelInfo当成560签名已证实，需结合运行日志。未改权重、正式APP或发布APK。
- CI45718390348 job139570213658 已核实安装依赖、下载补丁及原厂模型成功，正在执行真实图片OCR对照；运行中日志接口返回404 BlobNotFound，尚不能读取实际OCR结果。完成后必须读报告，核实560文字/数字准确率与实际签名。用户已设自动跟踪任务；目标是560而非重做280。GPU真机尚未验证。
- https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45718390348

---

# 最新｜560权重尺度已修复并通过数值运行，进入真实图片OCR验证
- CI45717336793 completed/success，job139566649978，artifact15070142934。原厂112个INT8权重全量SHA256匹配转换280和560，各112/112，无遗漏；共享112个FP32缓冲区按原厂实际逐通道scale/zero point修正，临时encoder SHA256 6b8caf8e6d95837eb8f0d82ae9aefbe88313e0bb8b23be253822adcfda85dd92。280三输入余弦0.53243/0.64418/0.63836提升至0.92216/0.90580/0.86648，相对L2 0.45750/0.43490/0.50743，仍非等价，mask一致。560特征[1,560,768]全部有限、有效mask560；仅说明数值可运行，不代表OCR已正常。用户明确目标是560，不单独制作280，280仅参考。
- 原独立诊断分支exp/gemma4-vision-numerics-20261126 新提交54711d340f2f9e2650973537ef6f2216b3dec79d，新增test_corrected_ocr.py及独立workflow，复现精确修复并校验SHA，按原有section plan重组临时完整模型，生成两张含中文、价格、号码的合成测试图，服务器CPU用LiteRT-LM Engine对比原厂280、未修560、修复560文本输出与CER、耗时，尝试显式max_vision_tokens_per_image；实际选择签名仍需日志核实。无真机GPU验证、不发布APK。新CI状态需核查，尚无真实文字识别结果。
 
---

# 最新｜已证实视觉FC权重漏反量化；精确修复对照运行中
- CI45716914147 completed/success，job139565208504。三方权重审计报告artifact15069498897。前两层14个Q/K/V/O、MLP gate/up/down INT8权重：抽样原厂raw、HF QAT raw、转换FP32值完全一致(relative L2=0)，HF逐通道乘实际scale后的抽样与原厂反量化完全一致(relative L2=0)。第0层Q/K/V/O范数比517.34/508.91/511.48/513.26；gate/up/down 512.76/512.89/1025.12。第2层QKV仅原厂/转换两方raw抽样一致，HF未下载该层对应权重，不能当三方验证。第一图像投影非量化且输出此前已接近。明确转换FC常量为未反量化整数值；仍需实测正确尺度是否充分恢复整图特征，以及排除图内补偿和激活量化差异。
- 按用户一次性完成的授权，独立诊断分支exp/gemma4-vision-numerics-20261126 HEAD 7ac8ca822b03b6f49fc5ed3c1524ac19c5730999，新增test_scale_correction.py及调整独立workflow。仅对临时encoder副本按全量INT8内容SHA256+shape匹配原厂280的112个独立FC权重，要求转换280/560各完整匹配且无未匹配整数权重；严格使用原厂真实量化轴、scale、zero point写入FP32，不用固定512比例。复验3种280输入的最终特征及mask，560仅有限性检查。保留原文件、正式APP和已发布APK，不将未经验证的临时模型发布。CI45717336793已核实in_progress，尚无修复后数值或安装包。
- https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45717336793

---

# 最新｜Gemma 量化权重三方数值审计（已启动）
- CI45716236089逐层检测completed/success，43个FC检查点及32个候选残差，原厂与转换图各自插桩前后features完全一致、mask一致。首图像投影余弦0.9999997602，相对L2 0.000692702；第一组注意力输出投影候选相对L2 326.57。MLP两门控权重范数约512倍差异，后续多组INT8/FP32投影亦有数百倍尺度差异；原厂首个候选残差相对L2 0.14439，最后一个候选残差1.18763。基于结构匹配和抽样，不能将差异直接等同于漏乘某个固定系数，或断言最早出错的具体算子。
- 核查固定官方LiteRT Torch源码046b0e55333f20adbedff78c43b468005a78118b中core/quantization_layer/gemma_quant.py：Gemma子字节权重使用offset binary，INT8转换为有符号int8，显式使用weight_scale反量化，支持分块或逐输出通道；export_lib._convert_and_export_module使用量化准备与weights loader。源码预期不代表当前导出实测正确。
- 用户授权仅排查量化与权重尺度。原独立分支exp/gemma4-vision-numerics-20261126 HEAD f9fa70c1ad07b67ed2e64814b2f6ea640b1b196c，新增compare_vision_scales.py及调整诊断workflow；沿用已校验原厂280及转换280/560产物，读取原厂FlatBuffer真实量化轴/scale/zero point及常量，匹配前三组FC投影，下载固定官方QAT checkpoint dd693ff40353f057ca5f07e945ad867f4afbf2ec的视觉权重及weight_scale，按真实shape和官方解包规则对比原始整数、反量化浮点与部署常量，记录范数比、余弦、相对L2及候选匹配；若找不到checkpoint匹配不能视为通过。只读实验，无模型重导、无APK、不改正式APP。CI45716914147已核实in_progress，尚无真实报告，不预判根因。
- https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45716914147

---

# 最新｜修复逐层检测CI触发条件
- 上一修复提交3bff27575e483967fca7841a8f62ad4d0ab780b5未触发CI，实际workflow paths只包含原compare_vision.py与workflow本身，遗漏compare_vision_layers.py。已在原独立分支提交f3d71f0764a2484ba14056c68568a11adf04d5af，补齐路径并锁定上轮实际成功安装的ai-edge-litert 2.4.0、tflite 2.18.0、numpy 2.5.5。未修改APP或模型。新运行需查询实际Actions，暂无数值报告。

---

# 最新｜逐层检测脚本修复（2026-11-26）
- CI45687221749 completed/failure，job139462893705：原厂与转换模型下载及结构解析成功，匹配42个投影检查点、32个候选残差；在写临时探针FlatBuffer时，复用脚本定义的struct字典覆盖Python struct模块，触发AttributeError: 'dict' object has no attribute 'pack'，尚未执行中间张量数值对比。不是模型推理失败。
- 首组结构：原厂第一个FC为input_projection且直接接images、有bias，另有两个[768,10240]位置投影FC；转换版第一个FC接tfl.mul835且无独立bias，位置投影实现不同；仅为结构线索，不能判定错误。原厂QKV为INT8，转换为FLOAT32。
- 原诊断分支 exp/gemma4-vision-numerics-20261126 提交3bff27575e483967fca7841a8f62ad4d0ab780b5：在共享初始化后重新导入binary_struct，消除变量冲突；加入首FC候选输入/输出/权重对比，并按实际量化参数反量化中间张量后抽样。仍需验证插桩前后最终输出一致，结构匹配不能当作语义证明。新CI暂未查询到，未获得逐层数值结果，不重新转换、不改APP、不构建APK。
 
---

# 最新｜Gemma 视觉编码器逐层数值定位（已提交，服务器运行中）
- CI45686173505已完成，原厂与转换280视觉编码器在零值/图案/带填充输入的特征余弦分别0.53243/0.64418/0.63836，相对L2为0.96516/1.00830/0.97410，mask完全一致。原厂编码器224137936字节，含INT8及量化/反量化；转换编码器670184208字节，全浮点。适配器同原厂特征输入相对L2约1e-7，投影权重常量4718592字节哈希相同。定位到编码器输出不等价，尚未定位首次错误算子；不能仅凭量化差异断定成因。560仅健全性测试无原厂560基准。
- 用户授权逐层排查，独立分支 exp/gemma4-vision-numerics-20261126 HEAD fa31c2fc2efb14556f3b5c301771a03fcfeb68f5，仅新增compare_vision_layers.py和调整独立诊断workflow。复用同一原厂与已发布转换模型；按16个SOFTMAX位置及FC权重/输出形状匹配检查点，优先检查首批投影输入、输出与可解析反量化权重，后续逐组首尾投影及候选残差。临时FlatBuffer只扩充输出向量，不修改权重，比较插桩前后最终特征/掩码一致性；不一致不解释中间结果。抽样数值、结构匹配与候选残差不是逐算子严格证明，仍需依据实际报告决定下一步。
- CI [45687221749](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45687221749) 已核实 in_progress，尚无逐层数值结果，不宣称已修复。不重新转换模型，不构建APK，不改正式蓝星向导或原厂APP。下一步核查job日志与layer_report.json，依据首个可靠偏差定位具体计算/权重差异。
 
---

# 最新｜560真机异常，转入数值定位实验（不改APP）
- 用户真机v0.1.2 MT6991 SDK36：原厂输入SHA及本地重组验证成功，输出3034159952字节 SHA54ca03d8888d9d2a8284606be35165f47958b00ed548a5cab50994de34dd3952，ModelInfo 560 [280,560]。GPU vision创建conversation报Some ops are not accelerated，官方runtime executor GPU分支仅kGpu，没有kCpu兜底。CPU/CPU 560反复输出50/1/20/1，转换版280反复100/200/10/50；原厂280曾正常输出中文但错漏。故冻结560发布，不再仅修GPU，先诊断实际数值。
- 核查固定官方转换器：实际成功V2路径无quant.* custom_call，移除的是odml.rms_norm和aten.gelu.default的恒等标记；V1备用从未用于此次成功产物，不能将其参数错误归因于已发布版本。官方vision encoder接口images/positions_xy→features/mask，adapter soft_tokens→mm_embedding。原厂文件与HF checkpoint同系列但不能仅凭名称认定内部权重/布局数值完全相同。
- 新独立诊断分支 exp/gemma4-vision-numerics-20261126 HEAD b565367e1a230d176788095087466d86e04ea82f；CI45686173505已启动。仅复用CI45681289795的已发布gemma560-patch artifact、下载并校验原厂280参考，提取原厂encoder/adapter；打印签名、dtype、算子分布、常量buffer hash重合及外部权重情况；CPU同输入对照adapter，encoder满patch/部分padding对照，原厂encoder输出分别喂两个adapter，记录cosine/relative L2/有限值及mask；560仅做数值健全性检查。若原厂图含独立CPU interpreter不支持的算子/外部权重，记unavailable，不当作通过或差异证据。此轮没有转换、APK构建/发布、修改正式APP。后续先看真实报告再决定修复，尚无数值结果。
- https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45686173505

---

# 最新｜560实验版 v0.1.2 已发布
- CI45681289795 export/build/publish 全部 success；APK签名、身份、离线权限、native runtime及内置资产校验通过。Release bluestar-gemma-v012-560-20261126 非draft，目标416717949a90a5e031eb5ca012a8c2affa7c3ed5，APK已上传，693121607字节，SHA256 74430a90ae3f8e5e0db70323512c8a1ff73f3723c08fa225ae0c1d6ca7e7766e。
- 下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-gemma-v012-560-20261126/BlueStar_Gemma_560_Lab_0.1.2.apk
- 用户导入已下载原厂2.59GB模型，APK内置增量重组；额外私有模型空间必需。280/560选档，GPU/GPU请求，先用同一价格表原图连续三轮记录准确率及冷/热启动首字、总耗时。尚无真机运行结果，不能认定精度或速度已提升。正式蓝星及旧实验保持不变。

---

# 最新｜560完整模型及增量校验通过，APK构建中
- CI45681289795 export job139442192634成功：真实encoder签名vision_560 [1,5040,768]/[1,5040,2]、vision_280 [1,2520,768]/[1,2520,2]；adapter签名[1,560,768]/[1,280,768]。官方ModelInfo确认560 [280,560]；原始头24个偏移字段更新校验通过，9个非视觉section逐字节SHA一致，完整模型及重组SHA验证通过。增量资产674921419字节，artifact gemma560-patch ID15053923830成功上传。实际APK大小待构建结果，不必再下载完整2.59GB原厂模型，但手机需空间生成私有副本。
- 独立分支 exp/gemma4-official-560-20261126 HEAD 416717949a90a5e031eb5ca012a8c2affa7c3ed5；build job139446996244正在下载增量资产，尚未完成APK编译/签名/发布，Release查询404。真实手机推理准确率及性能仍待验证。正式蓝星、旧280/1120不改。
- 下一步核查CI45681289795及release bluestar-gemma-v012-560-20261126，核实APK签名、体积、digest、目标commit、发布状态后再提供下载链接。

---

# 最新｜560视觉转换成功，修复封装（用户授权）
- CI45679323992已成功导出encoder及adapter两档真实图，mark_tensor清除后无剩余custom_call；encoder每档清除odml.rms_norm 336、aten.gelu.default 32，adapter每档rms_norm 3。最终失败在生成的FlatBuffer Python object API LiteRTLMMetaDataT.InitFromObj→KeyValuePairT._UnPack调用不存在的VDataCreator（实际VdataCreator）。未产生完整模型或APK，视觉推理未验证。
- 独立分支 exp/gemma4-official-560-20261126 HEAD 416717949a90a5e031eb5ca012a8c2affa7c3ed5，保留已成功视觉导出逻辑，封装改为直接保存原厂头字节，仅按官方schema SectionObject begin_offset/end_offset的vtable槽6/8修改ulong偏移；逐字段对照原getter，重新解析并反向恢复校验，确保其它头字节完全相同。metadata用上一轮已验证的等长varint唯一命中法改2520→5040，protobuf全字段比较。原有非视觉section SHA、真实签名、ModelInfo、最终重组SHA校验不变。若封装仍失败，CI另存已导出的视觉模块供后续复用，避免重复转换。模型仍由APK内置增量＋用户已有原厂2.59GB文件本地重组。
- CI 45681289795 in_progress；尚无已验证APK；继续核查实际转换/封装/构建结果。正式蓝星、280及1120旧包不变。

---

# 最新｜560清除恒等标记算子（用户授权最后一轮针对性修复）
- CI45677884628失败：真实MLIR只记录mark_tensor，280/560各368个；V2最终序列化失败；V1已实际加载211个张量，但PARAMETER需torch.nn.Parameter而非普通Tensor。
- 官方固定转换器源码backend/composite/mark_tensor.py明确mark_tensor eager实现return x，Meta返回empty_like，lowering仅生成单输入单输出同类型标记；是融合边界注释，不是量化或实际计算。新分支HEAD c6504eb91410a484e19623765ddb0ffed7097c0a，在两条转换路径共享的MLIR lowering返回后，仅识别目标mark_tensor、校验输入输出数/类型和backend_config，替换使用关系并擦除标记、verify计算图，打印标记所属融合名称计数；不删除真实数学运算或其他custom_call。V1参数恢复torch.nn.Parameter类型，保留原数据及shape校验。融合优化减少可能影响速度，实际数值和手机性能尚未验证。
- 保持原厂语言section逐字节验证、APK内置增量、原厂模型本地重组方案；正式软件/280/1120旧版不改。CI 尚未出现 未知，当前无可下载APK。若仍失败建议先暂停560，讨论原厂280+YOLO文字区域实验（尚未授权实施YOLO）。

---

# 最新｜560量化算子直接诊断与官方V1备用转换（用户授权）
- CI45674474085失败：关闭SDPA及GPU composite后，V2仍在stablehlo.custom_call FlatBuffer序列化失败，之前扫描二进制mlirbc无法提取目标，不能认定是注意力。官方源码lower_torchao_pass.py明示QAT可生成quant.dequantize/quant.fake_quant等custom_call，具体失败算子尚待实际日志。
- 独立分支 exp/gemma4-official-560-20261126 HEAD 1faf3749865b2782e8382913dd32b0e2f4a5572e。在V2 portable序列化前遍历实际MLIR operation，输出custom-call target计数；仅当已知stablehlo.custom_call失败且确有目标记录时，使用官方V1直接LiteRT converter路径，对同一QAT meta图所用参数/常量从同一checkpoint真实加载，校验shape与非meta；对官方loader预打包的二补码INT2/INT4按实际bit宽解包为等价INT8值，不随机初始化、不跳过算子、不加载无关语言部分。保留QAT算子，V1 strict_qdq请求，具体是否支持及转换结果待验证；失败如实记录，不保证已修复。原厂语言section、APK内置增量、本地导入原厂文件、目标完整SHA校验方案不变。
- CI 尚未出现 未知，尚无560模型或APK。正式软件和旧实验不改。

---

# 最新｜560标准注意力算子修复（用户已授权）
- CI45673141896失败于最终TFLite FlatBuffer序列化：TensorFlow V2能力预检通过，官方权重加载、280/560图MLIR及参数序列化均成功，stablehlo.custom_call 未降级成支持算子，APK未构建。具体custom_call目标尚未确定，融合注意力是待验证假设。
- 独立分支 exp/gemma4-official-560-20261126 HEAD e0893a3269bf6e2313f2bc23aba78e79b58c1beb，关闭视觉SDPA及GPU融合开关，保留标准eager attention，原始权重及V2加载不变。失败时记录MLIR目标名称诊断，不允许跳过算子、不替换权重、不虚报560。GPU性能可能受影响，待实际验证。仍使用APK内置增量、导入现有原厂模型本地重组、完整SHA及非视觉section验证，正式软件和280/1120旧包不改。
- CI 尚未出现 未知，尚无可下载APK。下一步核查实际结果。

---

# 最新｜560改为APK内置增量（用户授权）
- 上轮CI45671855923失败：V2 meta加载成功，280/560均完成PyTorch→MLIR及参数序列化；失败发生在Converter V2的TensorFlow支持检查，环境未安装tf-nightly；未产出模型，未发布APK。新CI显式安装tf-nightly-cpu并预检is_supported，不跳过校验。
- 用户要求只下载APK并导入已下载的原厂2.59GB模型，不再下载完整560文件。独立分支 exp/gemma4-official-560-20261126 HEAD b0fcd00d0c8ae6f14bfe936ec0fdfb06a3d3f861。新方案保留原厂容器元数据、仅重写section偏移，内置新header/metadata/encoder/adapter和精确复制计划。服务器生成目标文件并核验所有非视觉section SHA、protobuf其余字段、真实560图形状和ModelInfo，重组SHA验证后才上传patch-assets（上限1GB）并编译APK，不再上传完整模型artifact。APK逐资产SHA对照验证，大小限制1.9GB。实际APK大小未确定。
- 手机导入原厂文件时单次流式读取、完整源SHA校验，复制不变section至新私有临时文件，写内置视觉等资产，完整目标SHA及真实签名验证后才启用；不修改用户原始文件。需额外本地空间存放新模型副本，不需额外下载整模型。未真机验证。正式软件、280及1120旧版本均未覆盖。
- CI 尚未出现 未知；未有可下载APK，后续核查真实构建及发布，不承诺自动后台交付。

---

# 最新｜原厂560视觉专项实验（用户已授权）
- 用户真机测试第三方1120首次：首字39705ms/inference_first12647ms、load21991ms、conversation5043ms、total90461ms，输出大量英文及虚构重复行；停止该第三方路线，不归因于预算单变量。原厂280热首字1851ms、1024档1627ms保留为成功基线。用户选择原厂560，不加入YOLO（待后续讨论）。
- 独立分支 exp/gemma4-official-560-20261126 HEAD ad277b8d4cc1a33c43c21f869394c0c6750f8fbd；基于原厂280成功提交97378223766d45c9f855ba863335fd12d5808a7c；改动仅实验目录及其CI。原厂560转换不再重导语言部分：上一轮全模型加载失败确认为 embed_tokens_per_layer.embedding_scale checkpoint [262144,35] vs model [262144,1]。新路径使用官方LiteRT Torch固定046b0e55333f20adbedff78c43b468005a78118b 的V2 meta权重加载，仅导出官方移动QAT checkpoint固定dd693ff40353f057ca5f07e945ad867f4afbf2ec的vision encoder/adapter，280/560签名，不再使用第三方权重，不忽略不匹配，不重新生成语言权重。
- 官方Gallery 2.588GB源包固定SHA，使用官方builder解包/替换视觉encoder/adapter/metadata（2520→5040）/重封装；逐section SHA验证非视觉全部字节一致，protobuf其余字段一致，560计算图形状与官方ModelInfo均验证才允许APK编译。新视觉导出与原厂旧视觉权重/量化是否完全相同不保证。实际CPU/GPU推理待真机，不能把元数据核验等同推理成功。
- 独立实验APK v0.1.2 com.vobich.bluestar.gemmalab.vision560，默认560，提供同模型280/560对照，保持原图/1024/512、原推理库、TTS、模型常驻与日志。CI导出成功才生成model.properties，APK锁定该模型SHA及字节；模型作为CI artifact单独提供，APK发布前验证。CI 45671855923 in_progress，暂时没有已验证模型或APK。后续查run/jobs/logs和Release，不提供未验证下载链接。正式蓝星、原厂280、第三方1120旧实验均未覆盖。

---

# 最新｜Gemma 1120 实验 APK 发布成功（待真机）
- CI45667687853 model-check/build/publish全部success，发布非草稿，target 44dfd3718c5e7e6fe4f02b15133cb76b7380ffd5。APK 18207185 bytes SHA256 b612f1cf6ebc8ff9a3d7d8781eb109f70491bf3fab439a96112f03016eaaf96f，asset uploaded。下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-gemma-v011-1120-20261126/BlueStar_Gemma_1120_Lab_0.1.1.apk
- 实际计算图 vision_1120 inputs [1,10080,768],[1,10080,2]；vision_adapter_1120 input [1,1120,768]。原metadata max_num_patches2520,pooling字段0（默认）；只修改offset16531四字节8801d813→8801e04e，补丁后官方ModelInfo AFTER 1120 [1120]，文件SHA256 a356e20927dbb68ebdec29ad59f2a39eeb09942789a7007c420ad110174072e5。APK导入原第三方模型并修正私有副本，保留原文件。实际GPU/CPU推理、识别率及延迟尚待真机，不能把签名校验等同推理成功。
- 官方QAT重新导出CI45667440751失败，环境安装成功但Load source model时RuntimeError ignore_mismatched_sizes=False；未产出官方高预算模型。下一步如继续官方路线，核对权重/转换器加载器版本与具体张量尺寸，不允许忽略不匹配随机初始化。优先让用户真机测试已发布1120包，不修改正式软件及原厂280成功版本。

---

# 最新｜Gemma 高视觉预算双路线（已提交，校验中）
- v0.1.0真机MT6991原厂原图3072×4096 3.28MB/280：冷load25530ms、conversation4072、inference_first2546；两次热first1867/1851ms、total7662/7565ms、TTS入队1948/1937ms；1024长边768×1024热first1627ms（prepare231,inference1389）、total6765ms，部分中文改善但仍错漏；512用户称很差。尚无原图文字GT，不宣称CER。用户授权突破280并行两路线，正式蓝星及成功280版本不改。
- 第三方PenraApp/penra-models revision e7947bb3f9a2960713df011ebca32aedc0f271bb，penra-e2b-1120.litertlm 1708468392 bytes，SHA256 4f52793970e74cae45b971e447cd5834dac6b9a70b687a5fe0f968c6e1c26b9e。链接 https://huggingface.co/PenraApp/penra-models/resolve/e7947bb3f9a2960713df011ebca32aedc0f271bb/penra-e2b-1120.litertlm 。真实下载与SHA在CI45667256562 job139392647154通过，但官方Python LiteRT-LM 0.18.1 ModelInfo实际输出 llm True vision True max 280 signatures [1120] min_runtime None，故断言失败，阻止发布。不是单纯APP下拉限制。第三方量化/词表可能不同，准确率差异不能全归因于视觉预算。
- 独立分支exp/gemma4-high-vision-20261126 初始2387ffe9405b77c3b8b7deed15a21113f3b4f55d，最新44dfd3718c5e7e6fe4f02b15133cb76b7380ffd5。独立包com.vobich.bluestar.gemmalab.highbudget v0.1.1，原厂v0.1.0共存。最新CI先下载原文件验SHA，解析容器及TFLite encoder/adapter签名和形状，要求1120真实存在；解析protobuf仅在Gemma4 max_num_patches=2520且pooling默认/3时，等长改10080、验证其他protobuf字段相同及官方ModelInfo max1120/signatures含1120；生成精确offset/前后字节/全新SHA的patch.properties供APK打包；APP导入原文件后仅在私有临时副本修正且验证完整新SHA，原文件与280版本不改。若校验失败不构建/发布。此修正尚未实际验证。保留原GPU/GPU、context4000、原图输入、TTS、常驻与日志。仅显示实际支持档位，不假造560。新CI尚未查询到run。
- 独立官方路线exp/gemma4-official-export-20261126 HEAD57529081e903534a6dac27513763453c5d956ee5；CI45667440751 in_progress。LiteRT Torch固定046b0e55333f20adbedff78c43b468005a78118b，Google官方移动QAT checkpoint google/gemma-4-E2B-it-qat-mobile-transformers 固定dd693ff40353f057ca5f07e945ad867f4afbf2ec，官方export Python API请求280/560/1120、无额外文字PTQ、视觉动态wi8、4096 cache、图文模式。不是原厂部署语言字节完全一致的承诺。标准CPU runner资源、nightly依赖、转换及runtime兼容性均未验证。仅metadata通过才上传CI artifact，尚未产生模型或安装包。待检查两CI，若成功再真机，失败查具体日志，不回退已验证280版。

---

# 最新｜Gemma 原图实验 v0.1.0 已发布
- CI45664351739 build/publish均success；单元测试、APK签名、独立包身份、ARM64原生库、无INTERNET权限检查通过。Release bluestar-gemma-v010-original-image-20261126 非草稿，target 97378223766d45c9f855ba863335fd12d5808a7c，APK 18203584 bytes，SHA256 af889a24b7b1e4b9ea8b5eab0589a8fb13f4ebefbb43d676910f64a14e3a6952，已核实uploaded。
- 下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-gemma-v010-original-image-20261126/BlueStar_Gemma_Lab_0.1.0.apk
- 用户已下载模型，导入后先测同一价目表原图两次（冷/热），再1024/512对照及系统拍照，复制日志。真机识别率、GPU实际执行与延迟待验证，正式蓝星未改。

---

# 最新｜Gemma 实验编译修复
- CI45662008583失败，job139374032320日志确认唯一Kotlin编译错误：android.os.*和LiteRT-LM.*的Message同名导致onMessage回调签名歧义；依赖下载、SDK、Manifest和原生库合并均已通过。非模型或GPU运行错误。
- 实验分支exp/gemma4-original-image-20261126已提交 97378223766d45c9f855ba863335fd12d5808a7c，只将回调参数限定为com.google.ai.edge.litertlm.Message，已回读源码。模型/推理/图像策略不变。用户已下载模型，保留原文件。新版CI 待出现，未查询到；尚无有效APK发布。

---

# 最新｜Gemma 4 E2B 原图实验 v0.1.0（已提交，构建中）
- 用户真机vivo X200 Pro MT6991 Android16，官方Google AI Edge Gallery 1.0.23通用APK，Gemma-4-E2B-it GPU成功响应。原图默认9.2s错读较多；TopK1/TopP1/temp0/context4000/视觉280后原图8.8s有所改善；裁剪图8.3s仍有错读，用户估计首字2~3s（非精确日志）。直接拍照识别很差，另一次价目表尚可。用户授权独立实验，不改正式蓝星和原厂APP。
- 核查Gallery tag 1.0.23 tree 9cb31cf47c21b0ed76901ff589a52b509803edfa：MessageInputText.kt内置相机首选512x512（实际由CameraX协商），相册decodeSampledBitmapFromUri目标1024x1024（并非严格上限），PNG重新编码；LlmChatModelHelper.kt分别配置视觉与语言backend，默认视觉GPU；依赖LiteRT-LM 0.18.1（源码bda624851d74be34d4e1f7fac1fc4155939539c7）。ModelUtils.kt按模型视觉签名与预算上限过滤；Gemma4 metadata默认2520 patches→280视觉token，不能简单开放560/1120。实际GPU逐算子执行与精度提升仍待测。
- 独立实验分支 exp/gemma4-original-image-20261126，HEAD 0d9d0737dc2160a1fcb62fea6e74e60f3d7a881e，基于main aea7daf9d3a9a55d0af9c92ad52328b71a0d8028；compare仅10个新增gemma-vision-lab及独立workflow文件。包com.vobich.bluestar.gemmalab v0.1.0 versionCode1。固定Google Maven litertlm-android:0.18.1，Kotlin 2.3.20/AGP8.13.2/Gradle8.13，CPU/GPU分离可选，默认GPU/GPU、context4000、TopK1/TopP1/temp0、关闭思考/MTP、模型常驻及新图重置会话、追问、流式离线中文TTS。模型SHA核验及metadata动态读取预算；不改权重、不重编底层。系统相机EXTRA_OUTPUT保存完整文件（分辨率由系统相机决定，不承诺8/12MP档），相册原字节复制，原图正向JPEG/PNG直接传入，EXIF旋转或其他格式用ImageDecoder转PNG。1024/512长边同图对照按比例不放大、不裁剪，不等同Gallery逐字节复刻。记录真实输入尺寸、文件字节、prepare/load/conversation/首字/推理首字/总耗时/TTS入队（非出声）。模型内部仍可能缩图。图片上限50MB/3000万像素，无INTERNET权限，浏览器下模型。
- Gallery原始模型 revision 6e5c4f1e395deb959c494953478fa5cec4b8008f，2588147712 bytes，SHA256 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c（HF文件页已核实）；直链 https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm 。Gallery另有MTP更新revision 7fa1d78473894f7e736a21d920c3aa80f950c0db，SHA未核实，实验暂拒绝。原官方APP私有模型文件通常不可直接跨APP导入，可复用已可访问的同SHA文件，否则需下载原版本。用户已测文件具体revision未知。
- CI [45662008583](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45662008583) in_progress，包含预算/缩图单元测试、Release构建、签名/ARM64/无INTERNET/native库验证、独立发布重试。预定tag bluestar-gemma-v010-original-image-20261126，尚未核实安装包发布。无真机测试；下一步同一价目表原图、1024、512分别独立会话，记录首字/完整响应/错误，先冷加载后热模型；比较原厂APP和系统相机实际照片尺寸。

---

# 最新｜原厂视觉 CPU 性能诊断 v0.2.2（已提交，待构建验证）
- 用户v0.2.1真机224万模式1296×1728，vision2/10 tokens63，本阶段6秒/总40秒；44万模式576×768，vision1/5 tokens63，本阶段17秒/总21秒；均未获得完整结果，不能断言平均块耗时或卡死。已证实缩图和chunk数减少，单块成本仍高。用户授权底层CPU专项。
- 对照原厂OpenBMB/MiniCPM-V-Apps 6665801f45dd17c7664a67469eb7e9013fd81def Android v2.6：CPU4/batch2048、默认视觉图初始化、动态v86显式fp16/bf16、通用视觉4.6非当前4.7 1B；QNN仅特定OCR路径，非MT6991。固定引擎tc-mb/llama.cpp-omni 0c0b1109f2d338acf65f28a7b0a1505da3945286 的KleidiAI F16分支受SME/内核选择等条件约束，ggml-cpu.c在SVE或MATMUL_INT8时明确undef GGML_USE_LLAMAFILE；不盲目开启。clip warmup主要预留计算图/缓冲区及Flash Attention支持检查，并非完成实际视觉编码。
- 原分支exp/minicpm-native-weights-20261125，HEAD 09f69bcdb846e01f7c287b2cda41e7de179c73c7，compare仅8个独立实验文件；正式蓝星与旧APK不改。保留原权重SHA/动态切片/六档分辨率/提示/TTS。显式原厂ARM编译参数armv8.6-a+dotprod+i8mm+fp16+bf16；仅实验构建对固定引擎插入线程0的通用F16 MUL_MAT分支和KleidiAI F16成功返回计数，不改变数学、权重。每个完成视觉块记录wall/process CPU/两个计数并可实时复制；GGML CPU特征/HWCAP。独立无需模型的1152×1152×256 F16×F32矩阵三轮，CPU4/6/高性能4核请求，输出数值校验。OCR模式对照原参数、视觉图预初始化、高性能4核请求（CPU4）、预初始化+batch/ubatch/实际eval2048。高性能核仅当前允许核按capacity或最高频率排序，调用线程回读，不能证明所有运算线程绑核；若失败不宣称启用。模式改变重载，矩阵测试会释放模型。停止仍需块返回。实际kernel计数/提速/识别精度尚未真机验证。
- 版本0.2.2 versionCode3同实验包，旧GGUF无需重下，若签名冲突仅卸载旧实验APK。CI保留缩图测试、原厂权重Host OCR、APK签名/权限/ARM64/JNI检查，发布重试机制。CI [45653847435](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45653847435) in_progress；APK未验证发布。下一步先无模型矩阵测速复制日志，再同图44万CPU6原参数及CPU4等对照，重点看每块通用F16/KAI计数、wall/process CPU；不保证1～2秒。

---

# 最新｜原厂权重分辨率对照 v0.2.1（已发布，待真机）
- 用户v0.2.0真机截图最终显示语言✓、16x✓、4x未导入，3072×4096、3281168 bytes、sha508ae2824466，默认16x/CPU6，进入vision阶段后持续未出字。此前导入SHA失败后来解决，未确认原因；尚无完整推理日志，无法区分视觉编码慢或卡死。用户授权分辨率优化实验，不改正式蓝星或模型。
- 原实验分支 exp/minicpm-native-weights-20261125 从成功v0.2.0提交c9c19ccb7aac92a9843fcc10ec99429f88fbf829推进至2a0511eb4ba89eae96c38e28d1689ce209f25ff9；compare核实仅native-vision-lab及独立workflow共7文件。第一次更新ref服务报错，核查旧HEAD后用精确expected_sha租约完成同一父子提交快进，已回读HEAD。保留模型SHA、原厂引擎固定提交、16x/CPU6默认、原生动态切片上限9、常驻/离线TTS。新增原图/315/224默认/177/79/44万像素上限，按比例不放大，EXIF校正后原生RGB面积加权缩放、无JPEG/PNG重编码。UI每500ms显示阶段/总计、实际视觉chunk序号与总数及token，非内部tile进度；推理时允许复制当前阶段日志。停止仍需等待当前块返回，未实现硬中断。记录实际缩放尺寸、resize_ms、完成chunk数量等。新增Host纯C++像素上限、比例、面积平均测试，CI保留原厂权重简单OCR smoke和APK签名/版本/ARM64/无INTERNET检查。
- 版本0.2.1 versionCode2、原独立包com.vobich.bluestar.nativelab，尝试覆盖安装，若实验签名冲突只卸载旧实验包且保留已下载模型；不需重下权重。CI [45650961218](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45650961218) 已启动，计划tag bluestar-native-vision-v021-resize-20261126，APK尚未验证或发布。真机速度、准确率待测；优先同照片224万两轮（排除冷加载），再79万、315万，比较首字、总时长、视觉耗时/token及文字/金额准确性。不承诺1～2秒。

- CI45650961218 build和smoke均success，APK签名/完整性/缩放测试及原厂OCR通过；artifact15040331648，ZIP SHA256 d77ba3b252934e4967a727618e9a61be87fd726be5f8a56a1841a3d450e2bde1。publish失败于GitHub uploads HTTP500，APK内部SHA校验成功。直接重跑失败任务工具内部报错，run_attempt仍1。已新增仅发布workflow，提交afc5279e24bfc322514fe4378c0dc8c99ad8c109，固定读取原artifact、原构建SHA和成功job，重试上传并验证发布，不重新编译。重发run尚未显示；公开下载未核实。

- 最终重新发布CI45651693591 completed/success，已核实非draft Release bluestar-native-vision-v021-resize-20261126，目标原构建提交2a0511eb4ba89eae96c38e28d1689ce209f25ff9；APK 3515217 bytes SHA256 cb67cfdee80b12d3cfdeea67b084b2be448d70984ef368211265093381686f83，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-native-vision-v021-resize-20261126/BlueStar_Native_Vision_Lab_0.2.1.apk 。保留已下载模型，默认224万/16x/CPU6，同图两轮，记录阶段、识别文本与日志。真机速度/准确率仍待验证。

---

# 2026-11-25｜蓝星原厂权重独立实验 v0.2.0（已发布，待真机）
- 用户真机官方APP v2.6：OCR 1.0逐块识别缓慢且红色装饰误检标题；官方通用视觉APP整图3072×4096、PNG8800KB、所有价格及充值正确、个别专业词错误，图片预填充很慢；切片上限2后严重错读、误判餐厅。源码确认蓝条是prefillImage而非网络上传（MainActivity解码→PNG→native再次解码→mtmd_tokenize→mtmd_helper_eval_chunks）。用户明确要求不改原厂APP、不改正式蓝星主体，只借用原厂模型与必要推理库，授权制作独立实验版。
- 既有GGUF真机原图9切片上限首字41.055s/38.81tok/s；此前单切片CPU6首字约3.42s但精度未证实，CPU8无明显收益，Vulkan4视觉更慢，NPU实际执行未证实。故本次不重做低切片/视觉量化或强制GPU/NPU，保留原生动态切片9、原始4x/16x官方FP16权重与M-RoPE，默认16x CPU6、CPU4对照、4x可选。选择4.7 1B作为既有项目同模型延续，非官方APP截图4.6；官方模型仓 https://huggingface.co/openbmb/MiniCPM-V-4.7-1B-gguf （根目录非thinking），官方Q4_K_M SHA f18f594747e5317ab490cff9c06339c9cfb89acbe2f02ebc48a4e167bc23576d；16x SHA fb9cc047a3cdad5317fd8433a4aeda2d22f00f02ca4bd93b34ad84165b25a900；4x SHA 1e0ab439877e6896ded59ff24cdd242fdffb236394f6fc4a5b019cbd60bb5f4a。引擎固定官方APP v2.6配套tc-mb/llama.cpp-omni 0c0b1109f2d338acf65f28a7b0a1505da3945286，仅链接库，不clone/改造原厂APP。旧模型文件SHA不同，不能自动假定可复用。
- 独立分支 exp/minicpm-native-weights-20261125 基于ec03ee861d8de36c5782adc789307f0b8fa41532，最终HEAD c9c19ccb7aac92a9843fcc10ec99429f88fbf829，compare仅9个新增文件 native-vision-lab/及独立workflow。完全自有Java UI与JNI，独立包com.vobich.bluestar.nativelab v0.2.0，正式APP和原厂App未修改。Android原图SAF字节复制、尺寸检测、native一次解码与EXIF原像素方向修正、无PNG重编码/缩图/自动裁切，CPU armv8.6 dotprod/i8mm、KleidiAI/OpenMP、Flash Attention AUTO，Release优化构建，原生Jinja模板关闭思考+greedy OCR，模型同配置常驻、独立清空上下文，按真实image chunks分别测decode/split/vision/prefill/first/total、token计数、PSS；系统离线中文TTS流式分句、首段入队非出声；无INTERNET、只浏览器下模型、导入SHA校验。当前未验证速度、识别精度及硬件执行，不能承诺快。应用仅用于指定MT6991设备。
- 首轮CI45645658389失败于mtmd CMake set_target_properties缺LLAMA_INSTALL_VERSION，已修正。第二轮45645903170 build失败于Maven Central HTTP429（尚未编译），Host smoke仍运行；第三轮45646198329新增Release优化及TTS首段诊断，最新提交使用v0.1.11已成功的Maven Central镜像并增强Release构建检查。最终CI 45646344811 queued，发布必须Android ARM64签名/包名/无INTERNET/native符号和Release检查、官方两种FP16权重真实SHA与Host简单数字5931 OCR均通过。最终预定tag bluestar-native-vision-v020-optimized-20261125；只交付最终tag，不使用旧中间构建。尚无已核验APK直链，真机效果待测。

- 最终CI [45646344811](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45646344811) completed/success，Host原厂权重及简单OCR、Android Release签名/包名/native检查、发布均成功。Release非draft，实际上传APK 3507969 bytes，SHA256 6a738ac082ce07a9d2223b66bcfaf3ceda8c02c751aec808b4dcdb7decccaeef，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-native-vision-v020-optimized-20261125/BlueStar_Native_Vision_Lab_0.2.0.apk 。首次导入官方Q4_K_M与16x FP16视觉文件，4x可选，旧转换模型不可用。真机性能/密集OCR仍待验证。后续讨论方向：对同一照片做原图及约315/224/177/79/44万像素对照，保留原厂动态切片，不改权重；本版尚未实现自动缩图档位，未授权启动下一轮构建。

---

# 2026-11-25｜448/256混合量化加速专项 v0.1.13（CI已启动）
- 用户v0.1.12 448/4x真机验证：实际模型1997042272 bytes，vision_size448/vision_tokens256，MT6991 CPU6，LiteRT0.20；同价目表原图SHA508ae2824466，PID31171，预热加载14.414s、首字20.972s（含session11.070s、不含load），CER0。常驻detail_compare第1 ROI首字17.066s/总21.614s/预处理1.337s/内存3251011KiB，漏肾部金额、滑罐及店名错；第2 tight首字10.662s/总14.934s/预处理.776s/session4ms/nativeTTFT1.174s/解码31.44tok/s/PSS3407656KiB，温度32.9C/thermal0，所有中文项目、金额、充值和店名正确（缺英文PRICE LIST，CER=NA）。旧v0.1.11同图tight 2.56–2.79s首字、7.12–7.75s总，预处理约.8s；新速度瓶颈主要在首字前路径，send到首字9.882s减nativeTTFT约8.71s是视觉及其他未细分开销估计，不是精确视觉编码耗时；bands日志未完，不判结果。用户授权只优先448，896保留不继续优化。
- 原仓库独立HD分支 exp/minicpm-hd-vision-20261125 基于b41092635f83051a3a98feb929b3dc844fc1dbbb，提交01a676f5e76bce40dda1e58dee89e71ce8f6d17d，仅新增hd-speed-lab/mixed.py、pack.py、configure_apk.py、README.md及独立workflow，旧实验和正式版保持。直接下载已发布448/4x FP16模型SHA0d705d8bba51bae8e10c6bfdfc727a1fcc154dd703d9d2f36e9e8bfadc19ee63作为同一runner真实基线，原始权重和转换环境沿用成功recipe。使用量化器自身get_op_scope和真实FC权重形状/图顺序选择INT8范围（MLP、注意力、中心2/3、后半、前半、中间1/3、MLP weight-only），其他FC和CONV保留FP16，适配器直接保留基线FP16文件；确认实际两类权重，非仅文件名。四张合成图（原合成价目表、简单图、告示、清单）对照，原生价目表特征及各图FP16基线特征cos_mean>=.995/cos_min>=.95，单独计同runner预热后encoder中位耗时至少改善10%且文件更小。按速度最多保留3个候选并自动尝试完整模型；原文本路径和执行器保持逐section SHA一致，模型小于v0.1.12。CPU6/LiteRT0.20流式OCR按相同提示/greedy/关thinking，四图各两轮、预热排除，CER各图不恶化超过.01、简单图全正确、同图一致，常驻首字中位至少提升15%、总时长至少提升10%才允许编译。满足后独立包com.vobich.bluestar.npulab.speed448 versionCode10/v0.1.13、实际模型SHA和大小、原CPU6/离线TTS/裁剪不变，单测+签名+权限+包名验证，直接上传GitHub独立APK及raw模型，发布tag bluestar-speed448-v0113-20261125（尚未生成，勿提前发链接）。CI [45630060533](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45630060533) in_progress。混合量化和真机加速尚未验证，不能保证3秒首字；若候选均未过门槛不发布，报告留档。旧版与正式app不改。
- CI45630060533 job139260517238 全部completed/success；7种方案中仅late_half通过四图门槛（162个FC后81个INT8，其余FC/CONV FP16，适配器原FP16），合成价目表特征cos_mean=.99864797/min=.9658824；同runner单独encoder中位25.320→12.888秒（约49.1%下降）。完整模型四图各2轮，首字中位5.878→4.062秒（30.9%下降），总时长8.985→7.115秒（20.8%下降）；价目表CER2.2556%→3.0075%（多错1字，仍在预设+.01门槛内），简单/告示CER0，收据1.6393%不变（仍有金额错误），重复同图稳定。四个原文本路径section SHA一致。Release bluestar-speed448-v0113-20261125 已非draft且实际资产核验：APK35364653 bytes SHA256 61df0ab578909f427a8c5c3ad9eb442ac60758e0feb8e79654b02aada487aaa2，模型1789506144 bytes SHA256 1006d1ed3623ed65ace7bae01be3356974eb19a2341155fcd2102fe26b83288f。直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-speed448-v0113-20261125/BlueStar-speed448-0.1.13.apk 和 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-speed448-v0113-20261125/BlueStar-speed448.litertlm 。CI误将3个Host xnnpack_cache随candidate目录上传，非安卓可复用资产，不需下载。用户决定本版为最后一次第三方改造真机验证，若仍无法兼顾速度/精度，停止此方向，优先研究原厂完整安卓方案，仅优化vivo X200 Pro/MT6991部署、原厂图像处理、内存/加速/TTS，正式APP主体不变；未经新授权不启动其他实验。手机结果尚待验证。

---

# 2026-11-25｜MiniCPM 高清视觉图重新转换专项（Host实验已启动）
- 用户v0.1.11真机两轮detail_compare及两轮state_compare：同价目表原图508ae2824466，tight共14轮全部160字符/125去空格字符/142token/answer_sha5c0a12b6af63（常驻10轮、全新引擎4轮），roi四轮173字符/145token/hash e6c25f309e53，bands两轮167字符/146token/hash30b92ed4e8a5。强支持LiteRT0.20.0会话状态清零修复有效（仅同图，不保证所有照片），同图原文所有金额正确，仍有艾灸、经络刮痧、拔罐热敷、店名错漏，CER=NA。tight裁剪892x1574，rect995,1089,1887,2663，预处理780–851ms，常驻新裁剪首字2560–2792ms，缓存裁剪1693–1925ms；全新引擎需另外4118–5703ms加载，且结果相同，无需每次重载。第二轮detail新裁剪roi首字3277/总8263，tight2792/7711，bands2719/11611；bands原文未提供，不判准确率。温度34.3→37.0C thermal0→1。v0.1.11固定为基线，旧APK和正式版不改。
- 用户授权重新转换底层视觉图。原转换作者源码 https://github.com/john-rocky/hf-to-litertlm/tree/a395b4967fa9e879a5de548d83399b4be2487b1e/minicpmv47_work ：mv47_vision.py固定448、patch14、32x32网格，中间vit_merger及最终adapter两次2x2合并；vision_export.py固定输入输出；build_mv47_bundle.py固定FastVLM尺寸。核对官方Transformers v5.19.0 modeling_minicpmv4_7.py get_image_features 明确4x跳过中间vit_merger，最终merger权重不变；原作者ship脚本还指出原生canvas M-RoPE与LiteRT FastVLM 1D位置差异，需单独验证。448/4x输出256token；896/16x输出256token（不是原版动态切片）；1344暂不做，注意896全注意力内存开销。
- 原仓库内独立分支 exp/minicpm-hd-vision-20261125 从稳定ec03ee861d8de36c5782adc789307f0b8fa41532建立，提交2edd3e68ba9950745d4b35dc95ae76726a7560b4，compare核实仅新增hd-vision-probe/probe.py、README.md及独立workflow三文件。固定上游转换源码a395b4967fa9e879a5de548d83399b4be2487b1e及原作者shipping recipe的HF源revision984a89aafcd5160164f4f0d04b4be0f4eec03cb8；真实原始权重、固定相同合成中英数字价目表、448/16x控制、448/4x、896/16x三个独立runner。先HF native features、再改造纯Torch、真实fp32 TFLite导出与Interpreter，检查形状、有限数值、最终视觉特征cos_mean>=.999/cos_min>=.98，记录包版本及源权重SHA；只上传报告不上传权重，不使用用户照片。上游环境固定版本、实际依赖安装/运行待CI；896可能内存或超时。未验证量化、整模型解码、视觉token注入、元数据、Android、速度或OCR准确率。通过后再研究保留原decoder及executor metadata重打包和端到端测试，不能把图形验证当APK。
- CI [45609390322](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45609390322) 已触发；尚未完成模型转换，未生成APK。
- CI45609390322 初轮448/4x及896/16x依赖安装和原权重下载均成功，HF全模型加载后runner收到shutdown signal并取消（原因未确认，不能直接断言OOM），尚未进入native_vision或TFLite。为去除无关内存压力，独立probe改用官方MiniCPMV4_7VisionModel/Merger，meta实例化及strict assign加载原视觉权重，直接按官方get_image_features调用链执行，省去语言模型；提交fba73443ac4e11dcd204c6ec5ccf79c0ccbf674c，复验CI [45609879650](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45609879650) 已启动。未有新模型或APK。
- 第二轮CI45609879650 三组均在native reference加载配置阶段失败，日志ValueError Unrecognized model in source；原hf download日志提示忽略--include。已改用snapshot_download明确allow_patterns及config.json存在和vision_config检查，使用具体MiniCPMV4_7Config类而非AutoConfig；提交819dcd213239e52fdaa81b2e48820715fe9688bd；第三轮CI [45610739030](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45610739030) in_progress。无新模型/APK。用户要求一次性完成，不需逐项征询；仍只在独立分支推进。
- 第三轮CI45610739030三组原生视觉参考均成功：448/16x encoder[1,256,1152] adapter[1,64,1024] 3.516s；448/4x encoder[1,1024,1152] adapter[1,256,1024] 6.685s；896/16x encoder[1,1024,1152] adapter[1,256,1024] 19.872s（Host CPU独立机器，仅参考图视觉时间，不可外推手机）。真实原始权重的896输入已验证可行，未验证LiteRT导出。三组均在导出环境pip解析失败，litert-torch0.9.3要求ai-edge-litert>=2.1.6,<2.2.0及torch<2.13.0，而原作者配方指定2.2.0及2.13.0冲突。第四轮改用Python3.12/CPU torch2.12.1/litert-torch0.9.3及其兼容ai-edge-litert范围，让resolver选择converter版本，pip check和freeze留档；提交f7561e8baab09a9ac17701ad3983d7b609f7b65a，CI [45611260641](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45611260641) in_progress。尚未完成TFLite转换、量化、重打包或APK。
- 第四轮CI45611260641三组原生参考及导出依赖安装均通过，实际resolver为torch2.12.1+cpu/litert-torch0.9.3/litert-converter0.3.1/ai-edge-litert2.1.6/jax及jaxlib0.12.1。448/16x控制组改造Torch与原生最终特征cos_mean0.9999999999954184、cos_min0.9999999998960607，已过eager parity；导出encoder失败，JaxRuntimeError StableHLO plugin1.19.0不支持vhlo.fma_v1。其他两组当时仍在运行，未宣称通过。第五轮锁定jax/jaxlib0.7.2及numpy<2.4，保留原模型和验收阈值，尝试避开新JAX序列化算子；提交62c30ded78b6c27957137e701d0ee70dc52a8e05，CI [45611861498](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45611861498) queued。未完成模型或APK。
- 第五轮CI45611861498全部completed/success：448/16x job139197170506 TFLite端到端cos_mean0.9999999999545051、cos_min0.9999999994808252，encoder2.873s；448/4x job139197170093 cos_mean0.9999999997260212、cos_min0.9999999403746146，encoder4.673s；896/16x job139197170683 cos_mean0.9999999999378971、cos_min0.9999999980550149，encoder14.615s；分别64/256/256视觉token。三组真实FP32 TFLite导出及CPU Interpreter成功，单合成图，Host CPU不同runner不能直接外推手机；896 encoder 2,152,374,768字节未量化。第五轮仅上传报告，非完整模型。
- 用户要求一次性搞完，继续原独立HD分支，提交2600aafd90730246d6e5d1bf8a8d45b7fc98e146，仅新增package_model.py/configure_apk.py/DEVICE_README及独立workflow、缩窄原probe触发路径。新CI [45613189990](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45613189990) queued；原probe因workflow改动也触发45613189958。448/4x、896/16x独立运行：重现已通过的FP32导出，原作者int8 encoder/fp16 adapter量化、单合成图特征门槛cos_mean>=.98/cos_min>=.90；校验原1.70GB模型SHA，unpack并仅替换vision encoder/adapter及FastVLM尺寸，保留文本decoder/embedding/tokenizer/executor metadata，重新unpack逐section SHA比对；拒绝外置weights或超2GB包。LiteRT-LM Python0.20 CPU6按原prompt、greedy、关闭thinking跑简单图/密集图/简单图，要求简单图CER<=.25且5931正确、两轮一致、密集图CER不比基线恶化超过.02。门槛通过自动生成对应独立包0.1.12（com.vobich.bluestar.npulab.detail448或hd896），编译前将新模型实际SHA/bytes写入APK、保留CPU6/TTS/常驻，去除旧下载按钮，单测/签名/包名/无INTERNET/ARM64/隔离进程验证后上传配套APK+模型+报告+SHA的Actions artifact。不是已发布Release；模型量化、pack、smoke、APK均尚未完成验证。用户正式蓝星及v0.1.11不变。
- CI45613189990 双组重复FP32转换成功，INT8视觉量化后门槛失败：448/4x最终特征cos_mean=.9878001/cos_min=.7379387；896/16x=.9763433/.677401，均未达预设.98/.90（特征余弦不是OCR准确率）。未封装/未生成APK。修复提交01a01cb8340e02b4c28df84455dc28ec554a342e：独立验证FP16 adapter，先试INT8 encoder+FP32 adapter，若仍未过门槛尝试FP16 encoder+FP16/FP32 adapter，记录精度、字节及耗时；保持原验收门槛不变。提高实验包上限至3GB并将实际模型字节数写入APK导入上限及服务校验，SHA仍固定实际值；清理已不用的原权重和FP32导出文件释放磁盘。新CI [45614328658](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45614328658) queued；新模型/APK均未验证，FP16可能增大体积及运行内存。
- CI45614328658：448/4x job139205712056 completed/success，FP16 encoder+FP16 adapter，模型1997042272 bytes，SHA256 0d705d8bba51bae8e10c6bfdfc727a1fcc154dd703d9d2f36e9e8bfadc19ee63，四个原文路径section SHA一致。Host简单图两轮CER=0，合成密集图CER从12.0301%降至2.2556%，仍有错字与充值数字漏位，密集图总耗时8.725→13.129s（非手机测试）。独立0.1.12 APK签名/单测/包名/权限验证均成功，配套artifact bluestar-detail448-device ID15022053436，ZIP4550975117 bytes，ZIP SHA b1b7c1ab27237acd7d89d94227236c4e02c55f772e78c3c395c4cded21f31f3e，下载页https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45614328658/artifacts/15022053436 。896/16x job139205712524量化/封装/Host OCR/签名编译均已成功，但最新核查时上传未完成，未取得其日志与模型SHA，不能声称已可下载。两版手机实测均待验证；尚无Release独立文件链接。
- 最终核验CI45614328658 completed/success，两组job全部成功。896/16x job139205712524：FP16 encoder+FP16 adapter，量化最终视觉特征cos_mean=.9999997391788009/cos_min=.999991285969614，完整模型2287366752 bytes、SHA256 27a64f0adf35ce708fe6766d1b733feab649b89db074d9b916fbba8f9620ebbb，原四个文本/执行器section逐字节SHA一致。Host简单图两轮CER=0，合成密集图CER从12.0301%降至3.7594%，仍有漏字和充值数字漏位；密集图总耗时9.039→21.348s，非手机速度。APK编译/单测/签名/包名/权限检查通过，artifact bluestar-hd896-device ID15021983283，ZIP5259282094 bytes，SHA bd9e47348c1b51d6f919d7ef172a2b0e5c3a7a55d484755a9ede7abef52e6177，下载页https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45614328658/artifacts/15021983283 。448/4x与896/16x均可下载，均是独立v0.1.12实验版，FP16视觉权重更大、Host推理更慢；目前仅合成图和Host验证，不保证真机性能或准确率。优先在同一真实价目表照片上比较448精细与896高清的原图/紧贴模式、首字/总耗时/PSS/CER，不能预先宣称896更好。尚无Release独立文件链接，Actions artifact需要登录。正式版及v0.1.11稳定版未修改。
- 用户要求直接发布安装包，免去Actions大ZIP及登录操作。原HD分支提交b41092635f83051a3a98feb929b3dc844fc1dbbb，仅新增.github/workflows/minicpm-hd-device-release.yml，CI45624961980已启动，读取原成功run45614328658两个artifact（ZIP SHA固定）、解出原APK及模型、复核内部SHA256SUMS/固定模型SHA+bytes、Host报告、APK签名和包名/权限；分别发布独立实验prerelease，448 raw模型1997042272 bytes可直接上传，896 raw模型2287366752 bytes超过单资产2GiB限制，尝试只压缩模型为单ZIP并验证解压SHA；若ZIP仍超限保留原已验证Actions模型下载渠道，APK仍独立发布。目标tag指向已验证源commit01a01cb8340e02b4c28df84455dc28ec554a342e。发布workflow当前在下载原产物，未验证Release成功或实际直链，不修改正式APP及旧版本。
- 直链发布CI45624961980，448/4x job139242602876 completed/success，prerelease非draft，实际Release API核实上传APK35364621 bytes、SHA256 b31bb6f4b5b3893f1f834e69dd57102cc81ba056a4dfee773529a8ad0bdc123f；配套raw模型1997042272 bytes、SHA256 0d705d8bba51bae8e10c6bfdfc727a1fcc154dd703d9d2f36e9e8bfadc19ee63。APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-hd-lab-detail448-v0112-20261125/BlueStar-detail448-0.1.12.apk ；模型 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-hd-lab-detail448-v0112-20261125/BlueStar-detail448.litertlm 。HD896发布job139242602288仍in_progress，tag API 404，尚无直链。真机效果待验。

---

# 2026-11-25｜精细识别与推理状态专项 v0.1.11（已发布，待真机）
- v0.1.10真机价目表原图同sha508ae2824466，常驻原图首字1627/1671ms、TTS点击1751/1798ms。ROI对照第1原图1631ms/4281ms/94字符；第2 ROI 2916ms/8028ms/192字符，第3复用ROI 1545ms/5735ms/148字符，第4日志未完成。ML Kit检测28行/2组/主25行，rect854,948,2028,2804，1174x1856/17.3%面积，decode109/detect595/crop688/prepare1397ms。ROI明显改善但仍错漏/充值金额错误，同裁剪输出不同；CER=NA。用户怀疑尺寸，手动裁剪曾更好。
- 核实模型卡 https://huggingface.co/litert-community/MiniCPM-V-4.7-1B ：同SHA的1.70GB int8vis bundle固定448×448/64视觉token，不支持原版动态切片及finer detail。模型卡还明确报告该混合GatedDeltaNet模型跨Conversation复用Engine状态异常；上游issue https://github.com/google-ai-edge/LiteRT-LM/issues/3165 ，修复提交e1f86b05bf8a731331622a2622440e9f93d9a9f5；已直接核查v0.20.0 tag runtime/executor/llm_litert_compiled_model_executor.cc Reset()确实清零state_/decode_state_。不宣称真机已修复；升级不改变固定448模型，突破本身须重新转换模型/改运行时或其他模型格式。本轮用按检测行间隙分区提高有效视觉像素，不是原生动态视觉。
- 用户授权。原实验分支exp/minicpm-npu-direct-20261125，从d5bb7492eda14965c9c6158483e568abb74b3b10提交ec03ee861d8de36c5782adc789307f0b8fa41532，依赖固定0.20.0（AAR解析和编译待CI），同模型SHA/CPU6/原提示词/greedy/预热/流式系统离线TTS。ROI旧边距11%保留；紧贴1.8%及至少4检测像素，分区只在检测行的纵向空隙处分成2–3区（不能保障漏检文字完整）。精细对照宽边/紧贴/分区/紧贴/宽边，状态对照同紧贴裁剪常驻3次/新Engine两次（显式close/reinitialize，load独立计时），无声、记录文本哈希/耗时/坐标/排除行。主区域可能漏其他文字。新增单元测试行框完整性和策略。独立包com.vobich.bluestar.npulab.detail versionCode8，无INTERNET，仍导入原模型。正式应用未改。
- CI [45606408906](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45606408906) in_progress，APK未发布验证。
- CI45606408906 completed/success，job139178295914 单测/编译/签名/包验证/上传/发布均通过。Release tag bluestar-npu-lab-v0111-20261125，目标ec03ee861d8de36c5782adc789307f0b8fa41532；APK BlueStar_OCR_Detail_Lab_0.1.11.apk 35365613 bytes SHA256 f411ee3a9aa4e6abd491b24b32744e3119a76732f0fc65c3939240e3aa8c9801，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-npu-lab-v0111-20261125/BlueStar_OCR_Detail_Lab_0.1.11.apk 。用户导入已有模型、预热、选原价目表图，先精细识别对照，另做状态稳定性对照，回传原文和数值日志。运行库0.20.0编译成功不等于真机状态修复已确认，固定448视觉输入未突破；正式应用未修改。

---

# 2026-11-25｜自动文字区域定位与高清裁剪 v0.1.10（已发布，待真机）
- 用户v0.1.9真机同PID21662连续不同截图复用load=0/session1–7ms，最后五轮首字1823/1830/1859/1861/1839ms，TTS onStart从点击2029/2226/2272/2221/2211ms；同图4545f67ea93c输出92/36/75字符，准确率不稳定。随后原进程idle断开，重启预热pid30687，load8463ms。真实手机照片3072x4096、image_sha508ae2824466两轮首字3423/2007ms、TTS点击3563/2155ms、总6682/5352ms、105/107字符；第一轮原文存在严重错读、漏读、幻觉，第二轮未提供文本。用户手动裁剪同价目表后文本所有项目价格及充值金额正确，仅少量错字、字序和重复；未提供裁剪耗时或CER。不把真实照片结果与截图混淆，不宣称固定448唯一原因。
- 用户批准自动定位与裁剪实验；原分支exp/minicpm-npu-direct-20261125，基于v0.1.9 0c01e954edabdb2a91b1203796c4eaca2ada96d5，提交5ddb7dbe44bf189c024104dbc5a72bb26e0a10ba，仅独立litert-npu-lab及workflow 10文件。固定官方bundled ML Kit Chinese 16.0.1（https://developers.google.com/ml-kit/vision/text-recognition/v2/android），只取行框不使用识别文本，手动初始化于隔离进程；邻近行框聚类（横向项目价格、纵向相邻行），选择主群并留边，检测长边2000，按坐标映射回方向已校正的高分辨率图（最多4096长边/16MP）裁剪PNG；无框、弱区、面积过大或检测异常退回原图并明确日志。可能漏掉其他文字区域，UI明确仅主文字区，保留原图入口；不做整图全覆盖承诺。原模型/CPU6/提示词/greedy/常驻/预热/流式TTS不变；新增无声original/roi/roi/original对照，第二次ROI复用裁剪，数值日志含检测耗时、群组、框、排除行数、裁剪耗时，首字/总耗时现含预处理（CPU累计仍不含）；独立包com.vobich.bluestar.npulab.roi versionCode7，无INTERNET，未使用私人照片做构建素材，继续导入原模型不需重新下载。真机自动定位准确率、额外耗时未验证。
- CI [45603143415](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45603143415) in_progress；单测、编译、签名、包名、隔离进程、无INTERNET验证成功才发布tag bluestar-npu-lab-v0110-20261125，尚未核实发布。正式蓝星与旧Release不变。
- 首轮CI45603143415 failure，Gradle插件classpath解析期间 Maven Central repo.maven.apache.org 返回HTTP429，尚未进入Kotlin编译，不能归因ROI代码。仅实验Gradle settings增加Google托管Maven Central镜像及官方repo1端点，保留原google/mavenCentral/Gradle portal和依赖版本，提交79fe2cb700ac94657a6c8dd61a740c80134c7b29，第二轮CI [45603471329](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45603471329) in_progress，APK尚未核实。
- 第二轮CI45603471329 已跨过classpath下载，但ML Kit传递AndroidX依赖触发checkDebugAarMetadata，尚未编译Kotlin。已添加独立子项目android.useAndroidX=true，提交d5bb7492eda14965c9c6158483e568abb74b3b10；第三轮CI [45603738677](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45603738677) in_progress，APK尚未核实。
- 第三轮CI45603738677 completed/success，job139169065198 单测/编译/签名/包版本/隔离进程/无INTERNET/上传/发布均通过。Release tag bluestar-npu-lab-v0110-20261125，目标d5bb7492eda14965c9c6158483e568abb74b3b10，APK BlueStar_OCR_ROI_Lab_0.1.10.apk 31334025 bytes SHA256 c433b380dacaa810221d78962b9b279610df2c5fe4f2254a4d192b8f74076c0b，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-npu-lab-v0110-20261125/BlueStar_OCR_ROI_Lab_0.1.10.apk 。保留旧版，重新导入之前下载的同模型，预热后选未裁剪原照片，运行原图与自动裁剪对照各2次，复制结果和数值日志，再测自动定位并朗读。主文字区自动定位可能漏掉其他区域，实际识别质量和速度尚待真机验收。正式蓝星未修改。

---

# 2026-11-25｜CPU原图预热与流式朗读 v0.1.9（已发布，待真机）
- 用户v0.1.8真机同图fbe2b3e63ba1自动原图/补边/三段切片交叉6轮：原图首字8801/1647ms、总10316/3173ms、71/73字符；首次模型加载18381ms、首次conversation 5317ms后续1ms。补边首字1579/1517ms、总2773/1998ms、50/21字符（实际错漏明显）；切片首字1524/1551ms、总7611/5813ms、148/47字符（第一次重复6行、第二次漏读）。参考CER=NA，不能给精确准确率，原图文本相对稳定。电池34.2C、thermal0。此前v0.1.7已验证跨任务同PID模型复用，非永久后台存活。
- 用户授权继续独立实验到发布，不需再次确认。原分支exp/minicpm-npu-direct-20261125，从v0.1.8固定a317e44efeaf7a80af27453708d631ac308a26d3提交0c01e954edabdb2a91b1203796c4eaca2ada96d5；compare核实仅litert-npu-lab及独立workflow共10文件。v0.1.9保留同模型SHA/LiteRT0.18.1/CPU6/原图/原提示词/greedy/关闭思考，界面移除补边切片GPU入口；内置图无声完整预热（导入后手动，已校验模型后续启动自动），保留隔离引擎直至手动释放/Activity销毁；推理进程逐增量IPC至主进程，本机Android TTS离线中文voice，标点或16字分段、末段flush，下一次识别及停止取消旧语音；日志tts_first_request、tts_first_on_start以job/tap起点分别计时，不等于真实声响。不改正式蓝星/微软云端语音；离线中文voice可用性、预热收益、精度与TTS均待真机，非长期后台常驻。独立包com.vobich.bluestar.npulab.streaming versionCode6，需重新导入已下载模型原文件，不必重新下载。
- CI [45598995337](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45598995337) in_progress，单测/构建/签名/包版本/隔离进程/ARM64库/无INTERNET检查通过才发布tag bluestar-npu-lab-v019-20261125。初始构建记录，最终发布结果如下。
- v0.1.9 Release已核实：CI45598995337 单测/编译、签名/包验证和artifact均success；发布资产已上传，tag bluestar-npu-lab-v019-20261125，目标0c01e954edabdb2a91b1203796c4eaca2ada96d5，APK BlueStar_OCR_Stream_Lab_0.1.9.apk 18520091 bytes SHA256 7d4c4136210a52aee787af8879a59ce6012836f26c2bf58c2c3208f3244baa54，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-npu-lab-v019-20261125/BlueStar_OCR_Stream_Lab_0.1.9.apk 。CI最终状态success。首次导入同模型后手动预热，后续已验证模型启动自动预热。选真实图片原图识别并朗读，换图重复，回传日志。系统离线中文语音可用性与真实首声待验，正式蓝星未修改。
 
---

# 2026-11-25｜关闭思考与天玑9400 NPU视觉 AOT（思考实验通过，NPU真机实验APK已发布）
- 用户明确批准下一阶段研究。固定安卓上游7f2bac650137230539784b794205470077915df7 的 llama_jni.cpp 已有 assistant_turn_prefix()：g_minicpmv_version 47/471 直接用空闭合think scaffold；视觉processUserPrompt走手工ChatML，而先前Host mtmd-cli走common_chat_format_single，默认enable_thinking=true，不能将Host思考归因手机。Android版本识别实际分支、只输出2 token及有效朗读首字仍待核实。OpenBMB官方 https://openbmb.cn/docs/guides/minicpm-v/minicpm-v-4.7/inference 支持enable_thinking=False。无需换权重，不重复修改手机逻辑。
- 独立分支 exp/minicpm-npu-direct-20261125，提交 c66853d751580aaab10279a94268a3c82298f927，基于已验证Host Q8探针894493e57d0c3f54445e6612b9a42732081e956f，仅新增direct-npu-probe/与独立workflow。CI [45581664544](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45581664544) queued。Host F16原视觉+原Q4_K_M，同单切片/CPU6/greedy，3张合成中英文数字文字图各开/关思考1轮，使用同安卓风格ChatML手工前缀（固定上游实际marker <__media__> 已核实），记录编码/prefill/prefill+生成/生成token含EOG、最终回答及去空白CER；不把思考文本当OCR答案，模型冷启动不计入prefill+生成。尚未编译/运行验证，非手机/TTS速度。
- NPU 独立job：Google LiteRT官方 https://developers.google.com/edge/litert/next/mediatek 明确支持MT6991 AOT/JIT；官方AOT notebook 的MediaTek Target调用及LiteRT main源码SocModel.MT6991 已核实。采用社区现成MiniCPM-V-4.7-1B-VL_int8vis.litertlm（固定SHA256 20c73b5a1e48a315974cff1853535ff5d017100b6420d5ef221b9dd1b955dd7c，约1.70GB，https://huggingface.co/litert-community/MiniCPM-V-4.7-1B），通过官方litert-lm unpack拆分，仅明确命名vision_encoder TFLite单图编译MT6991，保留编译报告、包版本、section与输出尺寸，不发布模型权重。官方unpack已核对main实现但pip版本是否提供仍待验；SDK采用官方nightly而非固定版本，实际pip freeze留档。NPU AOT只证明编译/分区可行性，须核对算子覆盖，不等于手机实际NPU执行。该社区图不是现有GGUF，固定448方形、64视觉token，adapter/嵌入与当前引擎兼容尚未验证；不要直接拼接/替换。尚未有NPU成功或提速结论。
- 两路并行，尚未生成APK，不改正式蓝星或旧实验版，用户无需下载权重/安装。待CI结果后再判断真机方案。

- CI45581664544 completed/failure（两路分别核实）：direct_ocr job139092955579 success，三图开/关思考均CER=0（去空白），含EOG生成token on 66/86/88、off 8/11/15；prefill+generate on 7096/7814/7938ms，off 4842/4910/5036ms，均值7616/4929.3ms，下降35.3%，不含加载；视觉编码on 3776/3744/3746ms，off 3786/3741/3727ms，基本不变。Host数据不能外推手机，安卓现有闭合思考前缀需核实实际生效；此前2 token仍未证实OCR准确。artifact15006488145。npu_aot job139092955865 failure：工具litert-lm0.20.0、ai-edge-litert及mediatek sdk nightly2.5.0.dev20261123安装成功，bundle SHA校验与拆包成功，独立vision_encoder 530775664 bytes、adapter51971184 bytes；MediaTek_MT6991经过mlir_transforms后apply_plugin失败，详细错误仅在runner临时.error，首轮未保留，不能定性为算子不兼容。artifact15006592813。
- 已提交仅NPU诊断3dc03f908e4dd31ef553d0575b07295ef2d77d59，新workflow独立，不重跑已通过思考实验；固定首轮成功安装版本及原bundle SHA，分别编译同视觉图和官方LiteRT教程selfie segmentation参考图，使用独立TMPDIR保留*.error和stdout，区分编译环境与模型图失败；参考图来源为官方教程URL，记录下载SHA，不假设必然通过。CI 45586163145 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45586163145。无APK，无真机NPU执行，正式版不改。

- 第二轮诊断CI45586163145 completed/failure，报告artifact15008193306。MiniCPM视觉和官方参考图均成功解析、加载MediaTek编译插件并识别mt6991，均在NeuronAdapterApi加载共享库处失败（Failed to load NeuronAdapter shared library），未证实任何图算子不兼容。核查LiteRT官方main neuron_adapter_api.cc及mediatek_backend.py、SDK包__init__：Linux默认v8 host/lib路径，SDK可能存在路径/依赖/下载问题。用户要求一次完成，已提交122b9f6e48b31b57e6583ee069935914bebe754e，仅新增npu-recovery/与独立workflow；切Ubuntu24.04、固定同SDK版本、无缓存详细安装日志、定位官方v8库、ldd/真实dlopen、LD_LIBRARY_PATH及原生相对路径别名，控制图通过加载关卡才下载真实模型继续AOT；如加载仍失败则保留底层报告并停止，避免无意义下载；不重复已成功的关闭思考实验，不改模型/正式版/旧APK。CI已触发，run待核实。实际修复是否成功尚未验证，不声称已完成NPU适配。

- CI45586980891 completed/failure，官方SDK v8_0_10 x86_64 libneuron_adapter.so 的ldd和独立ctypes.CDLL均成功（DL_OPEN_OK），LiteRT插件内部仍报Failed to load NeuronAdapter，已按关卡停止，未重下MiniCPM。报告artifact15009057429。AOT加载问题未解决，不能归因模型。
- 用户明确授权本次研究→修复→编译→发布APK全流程，无需逐项确认；真机效果、权限及不可解决阻碍仍如实说明。独立真机JIT实验路线已提交bcd31a8f89bf38e641d59d1b43e024a9bc0299bf，仅新增litert-npu-lab/及独立workflow。采用固定LiteRT-LM 0.18.1 + MediaTek runtime 2.3.2（公开MT6989报告可加载，不等于本机MiniCPM兼容；v0.19.1/2.3.x ABI已知问题），核查v0.18.1 EngineConfig、ThinkingConfig、BenchmarkInfo和vision executor NPU|CPU分支。独立包com.vobich.bluestar.npulab，v0.1.4，CPU6/请求NPU视觉+CPU6，同图每组3次新conversation、关闭思考、greedy、first_nonblank/total/原生benchmark/CER/空答案/持久阶段；实际NPU未验证（可能CPU混合/回退），不静默重试CPU。内置合成图或用户图片，数值日志无照片/文本；无TTS、无网络权限。用户需浏览器下载已验证SHA的约1.70GB LiteRT模型并导入，旧GGUF不可复用，保留旧版，导入另需约1.70GB。CI自动单测/构建/签名/包版本/ARM64 JNI/MediaTek dispatch及compiler/无INTERNET权限校验，成功才发布独立实验APK，预计tag bluestar-npu-lab-v014-20261125；CI 45587771687 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45587771687。正式蓝星不改，APK未核实发布，真机效果未验证。

- APK首轮CI45587771687 completed/failure，runner的sdkmanager不在PATH（command not found），尚未开始Gradle编译。已按此前成功GPU workflow使用ANDROID_HOME/cmdline-tools查找真实可执行文件，修复提交800047ec50fc14f19fb2b54f2d06c3fc392637d3，不改模型或APP逻辑。第二轮CI 45588214145 queued https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45588214145。尚未发布APK，未经核实不发直链。

- APK第二轮CI45588214145 completed/success，job139115197966所有构建/单测/验证/发布步骤通过，BUILD SUCCESSFUL。Release tag bluestar-npu-lab-v014-20261125 实际目标800047ec50fc14f19fb2b54f2d06c3fc392637d3，已核实发布资产BlueStar_NPU_Lab_0.1.4.apk 18444531 bytes SHA256 6ab6e9a98464e524c2545f13e781009cd22e0d8edeb61adc6e43343741de9a9a，签名SHA256 d50d9715c17b08c949dfc57228914caaad6ad14350a99c5e9650547396d966d7，包com.vobich.bluestar.npulab versionCode1 versionName0.1.4；arm64 liblitertlm_jni.so、libLiteRtDispatch_MediaTek.so、libLiteRtCompilerPlugin_MediaTek.so全部确认打包。下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-npu-lab-v014-20261125/BlueStar_NPU_Lab_0.1.4.apk 。独立安装，不卸载旧版；首次需下载并导入指定LiteRT模型约1.70GB（与旧GGUF不兼容），内置测试图先做CPU6后做请求NPU视觉+CPU6或自动对比，复制数值日志。实际NPU执行、速度、识别效果均待真机验证，可能CPU回退，未证明NPU成功；无TTS，正式蓝星不改。

- 用户真机回传v0.1.4 MT6991，模型SHA校验成功，1698202736 bytes，dispatch_present=true/compiler_present=true。内置448图（按实验流程推测，待用户确认）image_sha099d308e01e3：LiteRT CPU6加载11625ms，三轮session_ms1571/1/2，first_nonblank1971/1464/1433ms，total2292/1791/1710ms，chars均12、chunks均10、thought_chunks=0、CER=0.0000，native_ttft_s0.9082/0.6145/0.5943，prefill_tokens98/decode_tokens11，native_decode_tps16.50/21.58/26.59；warm首字1448.5ms、全程1750.5ms。模型加载和首次session未计入send指标，native_ttft不能当完整视觉输入首字。旧GGUF 3.42秒测试原图/输出/后端不同，不能直接宣称提速率。请求NPU视觉+CPU6开始且loaded load_ms11855，但尚无任何run完成/失败记录，实际NPU仍unverified，待用户确认界面是否卡住、闪退或仍在运行；如重新打开查看previous_incomplete_stage。不修改或发布新APK，仅记录结果。下一步同图真实密集文字CPU6对比与NPU运行阶段诊断，CPU质量不能凭单图推定。

- 用户确认v0.1.4请求NPU加载后未闪退，未回传完整识别结果；批准一次性增加GPU视觉、GPU全面及NPU卡顿诊断直至APK。已核查固定LiteRT-LM v0.18.1 vision executor 的GPU分支为kGpu，NPU为kNpu|kCpu，保留原运行库版本与原SHA模型。提交1e87fdbe59c5f4a5af525a41f33c28b63c0f5d5a：仅改独立litert-npu-lab及其workflow，v0.1.5四模式（CPU6/GPU视觉+CPU6/GPU全面/NPU视觉+CPU6），同图、每组3轮、greedy、关闭思考；新增LabService :inference 隔离进程、LabController主进程看门狗，按连接/加载/创建会话/发送/首字/关闭阶段记录数值日志与10s心跳，超时或手动停止终止推理进程，退出确认后下组，否则停止批次；CPU累计时间与PSS辅助，硬件执行未验证。旧版debug签名不可保证覆盖，独立包com.vobich.bluestar.npulab.speed，版本Code2，原始LiteRT文件重新导入，无需重新下载；保留旧APK/正式版。CI自动测试、构建、签名、arm64三库、隔离进程声明及无INTERNET校验后发布，tag bluestar-npu-lab-v015-20261125。CI 45590730234 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45590730234。APK尚未核实发布，GPU/NPU速度与超时行为仍需真机测试。

- 发布前复核发现模式切换500ms间隔可能使看门狗停止调度，已修正提交01140fa2bf6d35d8757170a9152c700a70292843，明确使用android.os.Process，版本递增v0.1.6 versionCode3，独立包仍com.vobich.bluestar.npulab.speed，最终tag bluestar-npu-lab-v016-20261125。CI45590953688 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45590953688 已启动；v0.1.5是中间构建不建议使用，即使CI发布也仅交付v0.1.6或后续修正版。模型、原签名保护、正式版均不变。尚未验证最终构建及真机。

- v0.1.5 CI45590730234 failure，Kotlin编译LabService的Message.obtain与LiteRT-LM Message名称冲突。无需再次授权已修复b337fc02094f8c07b7d79257eb1e371474288de0，显式import android.os.Message；v0.1.6最终修正版CI45591118120 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45591118120 in_progress，前一v0.1.6构建45590953688未包含该修复。只交付经核实的最终修正版，未确认发布。

- 最终v0.1.6 CI45591118120 completed/success，job139125274646构建、单测、验证、artifact、Release均成功。前两轮45590730234、45590953688因Message冲突失败，最终b337fc02094f8c07b7d79257eb1e371474288de0修复成功。Release tag bluestar-npu-lab-v016-20261125，目标b337fc02094f8c07b7d79257eb1e371474288de0，APK BlueStar_GPU_NPU_Lab_0.1.6.apk，18474951 bytes，SHA256 83de3904e094f117635438f6a61547b87bf2d1ee9c315f300c168973dab6e0b4。直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-npu-lab-v016-20261125/BlueStar_GPU_NPU_Lab_0.1.6.apk 。包com.vobich.bluestar.npulab.speed，独立安装，保留原模型下载文件重新导入，不必重新下载；若原文件删除，旧app私有模型不能保证直接访问。推荐内置图CPU/GPU自动对比后NPU，发送数值日志。GPU/NPU真实硬件参与及速度、超时隔离行为尚待真机，正式蓝星未修改。用户授权全流程已执行至发布，无中间再次确认。

- 用户v0.1.6 MT6991真机完整自动对比（确认未整体卡住），同一image_sha099d308e01e3、模型1698202736 bytes，三种完成模式每轮chars12/chunks10/thought_chunks0/CER0、prefill98/decode11，硬件实际执行均unverified。CPU6 load18571ms，session6416/1/1，first3148/2764/1454ms，total3438/3033/1724ms，process CPU10136/9622/7722ms，PSS2406677/2581164/2580836KiB；GPU视觉+CPU6 load20918ms，session13431/1/2，first3891/1808/1688ms，total4245/2086/1971ms，process CPU8440/5055/4961ms，PSS2883721/2880023/2879855KiB；GPU全面load超时120135ms，未进入识别，成功隔离终止并进入NPU（不等同确定永久不支持）；NPU视觉+CPU6 load32733ms，session5393/3/1，first17226/5000/6452ms，total17602/5305/7054ms，CPU11364/9600/9553ms，PSS2805410/2763286/2517138KiB，正常mode_complete。第2/3轮平均首字CPU2109/GPU1748/NPU5726ms，GPU比本批CPU少17.12%，暖完整CPU2378.5/GPU2028.5/NPU6179.5ms，暖CPU累计8672/GPU5008/NPU9576.5ms，GPU少42.25%，但同图v0.1.4 CPU暖首字1448.5ms更快，v0.1.6 CPU第3轮1454ms也比GPU两次快，顺序/热状态/系统负载/进程架构/缓存差异未控，不能定论GPU更快或更省电。下一步建议CPU与GPU视觉同图多轮交错/独立重复、温度和PSS与累计CPU诊断、真实密集图CER、模型常驻；NPU与GPU全面暂不作为默认，先不改APK。真实加速器执行和覆盖未证实。

- 用户批准CPU/GPU视觉优化至APK，继续原规则无需逐项确认。提交ab03341235ae1589bb5dd80bfaa605a6db0643ff，compare核实仅litert-npu-lab及独立workflow共9文件，v0.1.7；保留已验证LiteRT运行时/同SHA模型/CPU6/同原提示词/简单图/无思考；暂停GPU全面/NPU入口。单模式4轮后同隔离进程保留Engine，下一次同模式（包括新图片）复用；切换模式/手动释放/Activity销毁终止进程，非后台永久常驻。ABBA CPU/GPU/GPU/CPU四组各4轮、每组新进程、各8轮（2首次6组内暖轮），不双模型并驻；增加448密集文字合成图、继续支持原相册图及用户填写参考CER。数值记录batch/block/resident_reused/电池温度（0.1C，非SoC）/Android thermal status、CPU累计、PSS与分段时间，最多600行、UI最近24行、复制完整缓存；保留隔离看门狗与10s心跳。旧版CI临时debug签名不可保证覆盖，独立包com.vobich.bluestar.npulab.resident versionCode4，重新导入同1.698GB原文件，不重下；正式版不动。CI45593140018 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45593140018 已启动，目标tag bluestar-npu-lab-v017-20261125，待实际编译/发布验证，真机常驻和速度未验证。

- v0.1.7 Release已核实：CI45593140018 job139132243498，构建/单测/签名/包校验/上传/发布全部success，tag bluestar-npu-lab-v017-20261125，目标ab03341235ae1589bb5dd80bfaa605a6db0643ff，APK BlueStar_CPU_GPU_Lab_0.1.7.apk 18480579 bytes SHA256 c7bb26455b558e722b0a9b149bb3f5a51eaaf2deba3c2ba8d4f55fa2275a6ca9，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-npu-lab-v017-20261125/BlueStar_CPU_GPU_Lab_0.1.7.apk 。独立安装保留旧版，导入原始LiteRT文件；先密集图自动ABBA各8轮，再单模式连续两次（换图）核验resident_reused/load_ms。实际手机常驻与提速尚未验收，硬件覆盖未证实，不改正式蓝星。用户授权已执行到实际发布。

- v0.1.7真机batch37615756，MT6991，同密集合成图image_sha00c7f765ff98，ABBA四组各4轮全部完成，chars51/chunks38/decode39/prefill98/thought_chunks0/CER0，硬件执行unverified。CPU block1/4 load21085/12084ms，首次session6179/111ms，首次首字2955/5505ms；GPU视觉 block2/3 load18819/11751ms，首次session13077/2429ms，首次首字3627/1878ms。每组run2-4六个暖轮CPU first1412/1440/1471/1526/1491/1522ms，total2568/2554/2615/2624/2594/2653ms，CPU累计12484/12529/12519/12645/12688/12865ms；GPU first1751/1699/1746/1766/1690/1711ms，total2895/2805/2905/2879/2802/2822ms，CPU累计9701/9673/10022/9813/9730/9668ms。暖均值CPU/GPU first1477/1727.167ms、total2601.333/2851.333ms、CPU累计12621.667/9767.833ms、PSS2547446.833/2840065.667KiB（2.429/2.708GiB）；GPU首字慢16.94%、总耗时长9.61%、CPU累计少22.61%、PSS多11.49%。电池34.9→35.6C、thermal0，不能证明长期温控/功耗。所有resident_reused=false符合ABBA组间新进程设计，engine_retained只证明组内保留；未完成同模式跨任务换图复用验证。建议CPU6默认、GPU视觉可选，GPU全面/NPU暂停；先同模式不同图片连续两次验证resident_reused=true/load_ms近0，并验证真实照片OCR；未授权新修改，不改APK。\n
- v0.1.7 真机常驻验收成功：用户单独CPU batch38010098，pid32422，image_sha00c7f765ff98，load25448ms resident_reused=false，四轮首字5837/2288/1845/1889ms，总7226/3571/3123/3147ms，均51字符/CER0/无思考，完成resident_idle；约79.566s后batch38089664换图cf218729b41a，同pid32422，resident_reuse、load_ms=0、resident_reused=true，session2/1/1/1ms，完成后resident_idle。新图四轮first3124/1955/1906/2105ms（均2272.5，后3轮1988.7ms），total11944/20465/20088/11294ms（均15947.75ms），chars445/866/890/367，decode247/512/512/230，decode_tps27.35/27.42/27.89/24.47；同图输出长度差异明显，第二三轮达到512输出上限，可能截断/重复/幻觉/解码不稳定，因未给实际文本与参考、CER=NA，不可定性或宣称准确。prefill98、thought_chunks0，PSS2329708/2322131/2265867/2267419KiB；CPU累计54304/102184/107503/53826ms；电池36.3→37.4C，thermal从0至1，轻度热状态，非严重限制，不能直接归因。当前仅首个非空文本时间，非实际TTS出声。建议先核实真实OCR输出与参考/是否重复漏字，查greedy设置生效和固定448单图处理对密集文字的影响；后续考虑流式TTS、重复检测与单次真实图测试，勿盲目加大/缩小token上限；未授权新修改，正式蓝星与APK不动。

- v0.1.7 用户再换图测试batch38280402，pid32422继续复用，image_sha=fbe2b3e63ba1，load_ms0/resident_reused=true，session6/1/1/1ms；首字5111/2218/2203/2117ms（后3均2179.3ms），完整6654/3104/3010/2928ms（后3均3014ms），输出60/32/32/32字符，decode42/23/23/23，thought_chunks0、CER=NA，电池37.4C/thermal0。用户提供蓝星向导C110首页截图（聊天附件691×1536，附件SHA前缀7789e5e49631，与手机原始测试文件hash不同，可能为聊天转码，不能证明字节相同）。人工核查正文不计状态栏与空白共50字符，若确为本轮测试图，后三轮32字符无法完整覆盖正文。未提供实际识别文本，不能确定具体漏项、错字或幻觉，亦不能算精确CER。建议先用截图原文作参考或复制最后一轮识别文本，进一步查固定448视觉输入对竖屏小字影响，实验原图整幅与保比例分区裁剪对照，不盲目改token上限；不修改APK/正式版，待用户授权。

- 用户批准图像质量优化从头至APK无需再次确认。核查固定LiteRT-LM v0.18.1 runtime/components/preprocessor/minicpmv_image_preprocess.cc（有MiniCPM等比切片逻辑）、support/preprocessor/stb_image_preprocessor.cc（固定目标直接缩放、无固定目标等比），Config.kt及top_p_cpu_sampler.cc（支持temperature=0及topK）；实际手机bundle走哪条尚未证明，不能将漏字直接归因于拉伸或随机采样。提交a317e44efeaf7a80af27453708d631ac308a26d3，compare确认仅litert-npu-lab及独立workflow12文件，v0.1.8；保留已验证LiteRT 0.18.1/CPU6/关闭思考/greedy/512每会话/同模型SHA/常驻与进程隔离。CPU同engine原图直传、等比448补边、长轴2-4非重叠分区（近边界搜索低视觉边缘能量位置，大方图2横带/小方图1块），ImageDecoder软件解码长边最多2400、EXIF方向由系统解码，逐区新conversation按原图顺序合并，不自动删文字；缝可能切字且总耗时可能增加，不能宣称已经提高准确率。自动original,pad,tiles,tiles,pad,original各2次；单方案1次；GPU视觉分区备用。记录图尺寸、分区坐标、预处理缓存及耗时、各区首字/总耗时、合并首字/总耗时（含session不含预处理）、CER、非空白长度、答案hash、重复行计数、空区、512上限命中、CPU/PSS/温度；可单独主动复制本批识别原文，数值日志仍无文字/图像。可填入用户提供蓝星首页正文参考（不含状态栏，状态栏识别会计作插入），不内置用户截图。独立包com.vobich.bluestar.npulab.quality code5，旧CI临时签名不可保证覆盖，导入原始已下载1.698GB模型无需重下，保留旧版。CI45597292580 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45597292580 构建中，目标tag bluestar-npu-lab-v018-20261125，未核实发布。正式蓝星不改，无TTS，实际效果待真机。

- v0.1.8 Release已核实：CI45597292580 job139146620469，构建/单测/签名/包验证/上传/发布步骤均success，tag bluestar-npu-lab-v018-20261125，目标a317e44efeaf7a80af27453708d631ac308a26d3，APK BlueStar_OCR_Quality_Lab_0.1.8.apk 18505871 bytes SHA256 06653b55773a3efe380679b90fd23422e5f5738b2df0fd1f408e38f561bca09c，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-npu-lab-v018-20261125/BlueStar_OCR_Quality_Lab_0.1.8.apk 。独立安装，保留旧版，导入此前下载的同模型，无需重新下载；选同一首页截图，填入对应首页正文参考，自动精度对照，复制识别原文与数值日志。自动分区可能切字、额外耗时，识别率改善未验收，未确认原生预处理实际分支。正式蓝星未修改。用户授权执行至实际发布。

---

# 2026-11-25｜视觉编码器Q8量化与MT6991 NPU研究（Host实验通过，真机及NPU未验证）
- 用户新日志全部v=0.1.2（不能当v0.1.3验收），同图source_sha=ffe30aa9ef5e、3072x4096、1切片、merge=4、内部448、63视觉token。CPU/GPU各3轮：CPU4首字均值3910ms、视觉编码3200.959ms；Vulkan0实际主视觉GPU标志1，首字5832ms、视觉5053.817ms，慢约49%，CPU累计时间降低但不能证明所有算子GPU。另一同图CPU线程批次：4/6/8线程首字均值4044.3/3420.3/3423ms，视觉3323.16/2713.475/2681.217ms，6线程约79%首字在视觉，8线程收益不明显；所有15轮均2次生成调用，CER=NA，未证实OCR质量。用户批准开展量化/NPU研究。
- 已核查固定引擎a5624430e61f44b4842246b15a352740218a3d49的gguf-py Q8_0、writer/readback及clip-impl实际attention/FFN矩阵命名。独立分支 exp/minicpm-vision-q8-probe-20261125，提交 94a738cae369529c04d8fd9113e5b53d8d927129；仅新增vision-q8-probe/及独立workflow。使用原HF revision 2a3a3ddfbfc3d8e98c1070680b2fa98df88c3452两权重及LFS SHA校验；保留原LLM Q4_K_M，实验性地仅量化视觉块attention/FFN二维对齐矩阵为Q8_0；patch/位置embedding、merger、投影、norm、bias及架构元数据不改（更新file_type/quant版本）；验证tensor名称形状、未变tensor逐字节、metadata及finite解量化。Host CPU CLI固定单切片6线程，2张人工生成中英文数字测试图，F16/Q8各跑两图，记录图像编码时间与完整回答。数字匹配仅辅助，不能替代CER；不发布权重/不生成APK、不使用用户图片、不改正式版或现有实验版。CI [45577368996](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45577368996) queued，尚未实际量化/编译/测速成功；Host性能不能外推真机。
- NPU研究：Google LiteRT CompiledModel 官方明确列MT6991（https://developers.google.cn/edge/litert/next/mediatek）；支持SoC不等于MiniCPM视觉图支持，GGUF不能直接NPU。社区LiteRT-LM https://huggingface.co/litert-community/MiniCPM-V-4.7-1B 的int8视觉版本1.70GB，作者Pixel8a CPU首字2.2–3.0s，S26 Adreno F16视觉GPU报告0.93s；不是同机同图比较，作者还报告Mali内存/GPU问题及Qualcomm动态int8 decoder NPU拒绝，不能据此推定MediaTek。后续先评估视觉单图转换及MediaTek compiler/dispatch、算子支持/真实NPU执行；未开展NPU实测。优先Q8 Host可行性验证，后续才做Android CPU6原F16/Q8对照及识别质量，保留原权重和旧版。
 
- 首轮CI45577368996 completed/failure：原始LFS校验、Q8转换及结构/元数据/未量化张量逐字节回读、Host CLI编译通过。F16视觉文件1108746816 bytes SHA256 febb329c29ec9f1643c9d690ca74d35f486b83fa48c2f54c108b135729f635fd；Q8视觉848872896 bytes SHA256 d8e8545477cc40f4a1c1eaac1a8e6c1928119754487e77b12d3dd0e4af5a4a60，459 tensors总计，体积减少23.44%；不等于速度或内存降幅。首个F16图a native exit=0、stdout含4827、Host总耗时6.085s，但普通INFO编码计时未被捕获，断言停止，Q8尚未运行。报告artifact15004823010。已对实际固定上游mtmd-helper.cpp确认成功encode位置，修复提交894493e57d0c3f54445e6612b9a42732081e956f：仅Host探针显式stderr计时marker，保留四个测试结果再判断单图计时/退出码，输出合成测试回答供核验，不改量化或原有手机逻辑。第二轮CI 45578063940 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45578063940；无APK、不发布权重、NPU未实测。

- 第二轮CI45578063940 completed/success，四轮原生exit=0且各1次视觉编码，Q8权重SHA与首轮一致。合成448×448测试图a/b：F16 vision 4452/4404ms，Q8 4378/4406ms，平均4428/4392ms仅缩短0.81%；Host全程（含进程启动/模型加载/生成）F16 10.864/8.070s，Q8 11.025/11.946s，均值9.467/11.4855s；仅两张图且生成长度、服务器负载未控制，不能外推手机。四次最终回答均含全部可见文字和数字；F16图b顺序颠倒，Q8图b顺序正常，三轮stdout出现<think>推理文字（F16a/Q8a/Q8b），需后续核查Android提示模板/关闭思考模式与首个可朗读文字指标，不能把首token或思考文本当有效OCR首字。未验证真实CER和Android ARM64 Q8速度。报告artifact15005710855，https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45578063940/artifacts/15005710855 。当前Q8仅证明结构兼容、Host运行、体积缩小23.44%，不作为默认模型；暂不发APK，不修改正式版，NPU尚未实测。建议优先核查视觉图的NPU适配及关闭思考的可行性，待用户进一步授权。

---

# 2026-11-25｜GPU实验室 v0.1.3 CPU恢复及Vulkan隔离（已发布，待真机）
- 用户真机v0.1.2日志：ProtocolException、SocketTimeoutException；随后CPU/GPU自动对比、CPU线程自动对比、手动CPU各2次，共6轮均在requested=cpu threads=4 phase=begin后RuntimeException，未见backend_loaded，尚未进入GPU。模型SHA校验在begin之前，不能确定具体原生失败点或GPU不兼容。用户明确授权修复。
- 核查固定引擎a5624430e61f44b4842246b15a352740218a3d49：Vulkan在全局backend registry构造/通用扫描阶段注册，CPU也受影响；原版同时修改model_params.devices/split_mode与offload参数。以上是候选原因，不宣称已证实。
- 独立分支 fix/minicpm-gpu-recovery-v013-20261125，提交 ec30dcbc41ad14af4483900c8893e34b8e15e32f；回读比对仅新增gpu-ai-recovery/prepare.py、README.md、migrate-model.ps1和独立workflow共4文件，旧实验及正式蓝星向导1.0.98不动。以固定v0.1.1基线恢复CPU默认模型设备/上下文参数，禁止Vulkan全局自动注册及通用扫描；仅在GPU视觉模式且LLM成功加载后注册，释放模型/上下文/投影器后删除静态注册项（不dlclose；非独立进程，驱动全局状态可能常驻）。GPU模式LLM禁用KQV/host op offload，拒绝GPU未启用；保留1B、单切片、贪心、CPU4/6/8、GPU视觉、1冷2暖及分段计时。新增llm_load/vision_load/context_prepare/backend_verify/inference阶段和原生返回码，同步保存最后阶段及数值日志；11无GPU、12注册异常、13未编入、14视觉初始化异常。日志不包含图片/提示词/识别文本/私有路径。尚未真机验证。
- CI [45573819723](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45573819723) in_progress；待核实源码补丁、构建、单测、签名、模型元数据、Vulkan及JNI/注册符号、发布资产。签名与v0.1.2一致才升级原.gpu包（versionCode2），否则独立.gpu.recovery；迁移脚本优先GPU旧包、.speed、旧包，需额外空间约1.78GB，未真机验证。权重不变，不卸载旧版。下载异常尚未专项修复。未验证APK不得给直链；计划tag bluestar-gpu-ai-lab-v013-20261125。

- 首轮CI 45573819723 completed/failure：Android/Vulkan工具、签名准备及权重元数据均成功；源码准备在prepare.py第159行失败日志完整行匹配为0，未编译/发布。修复提交 3d28a8b82d76d5a779f10b1c53fb6d3e468f6e12，改为仅匹配唯一phase=failed表达式，移除无意义的自替换断言，不动CPU/GPU逻辑。第二轮push已提交，待查询实际run。首轮已确认签名不同、独立.gpu.recovery模式；下一轮仍以实际签名比对为准。

- 第二轮CI [45574080337](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45574080337) completed/success：源码准备、Android编译及单测、签名、APK身份、原生JNI/注册符号、Vulkan依赖、模型元数据一致性、artifact及发布全通过。Release target 3d28a8b82d76d5a779f10b1c53fb6d3e468f6e12，实际APK 75370312 bytes，SHA256 9adfc3d6aa848b1f8d6f2b8766474dd55b9615f77ad6eb8399a3cb0c0815b4e4，包名com.vobich.bluestar.offlinelab.gpu.recovery，签名85a26a2239282587018619c4dd8e760c9bab5fc7a4e8f38faa68351376a53a01，与旧版不同，独立安装不卸载旧包。APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-gpu-ai-lab-v013-20261125/BlueStar_GPU_AI_Lab_0.1.3.apk 。迁移脚本及INSTALL_MODE.txt已发布；未真机验证。先同图手动CPU4，成功后再CPU4/GPU与CPU4/6/8；若失败回传stage/native_code。CPU恢复、GPU实际可用与速度仍待真机验证。

---

# 2026-11-25｜蓝星GPU实验室 v0.1.2（独立APK，构建中）
- 用户明确批准制作CPU/GPU对比实验版；前置Vulkan探针45566558972成功，不等于天玑9400真机加速。沿用已发布v0.1.1模型及哈希；仅新增gpu-ai-lab/与.github/workflows/minicpm-gpu-lab.yml，不改正式版或既有速度实验。HEAD 900e2fe64d25d5c61ff1fdbd402e8f2a80bb6f68，初次提交8310a3262e30311fa6781d01ec50f80ddc91cc48 / CI45568870729的基准release tag误替换，已修复并增加ELF/JNI校验，当前CI45569008722 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45569008722。
- 复用v0.1.1 prepare.py，固定原引擎a5624430e61f44b4842246b15a352740218a3d49、Vulkan/SPIRV头文件与NDK；独立包com.vobich.bluestar.offlinelab.gpu，Android28+，仅UI显示1B、原两款模型目录元数据完全保持一致。GPU仅视觉编码，LLM固定CPU、禁用KQV/host op offload；CPU4/6/8与GPU视觉+CPU4，同图固定单切片，分别1冷2暖、每次重置上下文；为对照采用贪心采样（不同于v0.1.1温度0.7）。不硬改4x/16x投影器，日志读取真实merge与image_size。
- 新增原生分段：tokenize、vision_encode、image_decode、text_decode微秒，实际image_chunks/visual_tokens/text_tokens、主视觉backend及GPU标记、首字/总耗时、PSS、温度；用原始单chunk helper/encode/decode逻辑分别计时。拒绝无图像编码或GPU请求回退CPU的结果，选中GPU不代表所有算子都GPU，驱动可能native crash，禁止自动重试；日志先保存CPU结果和backend begin。新增代码已复查，实际Kotlin/C++编译和真机未确认。保留可选CER与屏幕文本；避免上次2–3 token误判。
- v0.1.1签名私钥未持久化，新包独立安装，附ADB本地迁移脚本从.speed优先、缺模型时回退旧包；SHA256复核，复制需额外约1.78GB，脚本未真机验证；不要卸载旧版，用户无需必须重新联网下载。可预配置私有LAB_GPU_KEYSTORE_B64，未配置不能承诺未来覆盖。当前尚未发布APK；成功后核实签名、模型元数据和libggml-vulkan、原生JNI符号、Release实际资产，再追加记录。暂不实现实时预览/跨任务常驻，待GPU真机结果。

- GPU APK CI45569008722失败于源码准备：Vulkan工具链、签名、模型元数据已通过，分段计时补丁匹配到两处原生eval代码，唯一性断言正确拦截，尚未编译。修复提交 c22e9ac0eff0f393fb9acade41f578dc63e61681，限定processUserPrompt函数范围，已对固定上游源码核实范围内仅一处、全文件两处，不动其他识别逻辑。新构建 Actions 45569359903 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45569359903。未发布APK。

- GPU APK CI45569359903 completed/success，编译、单元测试、APK签名、包名、原生库/JNI符号、模型元数据一致性和发布均通过。Release bluestar-gpu-ai-lab-v012-20261125，target c22e9ac0eff0f393fb9acade41f578dc63e61681；实际APK 75370304 bytes，SHA256 0d5ce6e9debd39050d9fbc5c4dd7b7fbc663fc20114b0052bd3565215733f65b，包名com.vobich.bluestar.offlinelab.gpu，签名02342bfd6ba378c4f25cc4ed1cee13656a135d56f2af50aa8130514d94e48e64。独立安装，不覆盖旧包；附本地ADB迁移脚本（未真机验证），权重不变。APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-gpu-ai-lab-v012-20261125/BlueStar_GPU_AI_Lab_0.1.2.apk 。先同图自动对比CPU4/GPU视觉，保留屏幕识别内容及日志，然后CPU4/6/8；实际GPU可用性和速度、准确率尚待真机验证。正式蓝星未改。

---

# 2026-11-25｜GPU视觉编码研究及交叉编译探针
- 用户v0.1.1真机同图source_sha8098b89d120d切片1/2/4/6/9 first_ms 4037/12015/20494/29830/44849；单切片五分辨率576×768、768×1024、1152×1536、1536×2048、原图prefill 4002/3717/3839/3926/3943ms，含缩放首字4350/4102/4591/5171/4174ms。2–3生成调用，未验证正确率。用户批准开始下一阶段研究。
- 核查已用JNI固定N_THREADS=4、n_gpu_layers=0、mparams.use_gpu=false；mtmd-image.cpp单切片仅overview、内部按hparams.image_size及patch对齐重采样；clip.cpp支持GPU/IGPU但可能回退CPU，必须验证实际设备。4x/16x由投影器GGUF n_merge=2/4决定，当前文件实际metadata未读，不可硬改。LiteRT社区1B 448×448 S26 GPU 930ms，非天玑实测，Mali驱动有兼容性风险且不同格式模型。
- 先做独立交叉编译探针，未改v0.1.1 APK/模型/正式版，不新增用户安装包、不下载权重、不触发旧APK工作流。HEAD 785a7274b2c43b87718063014e8b2224e227741e，新增gpu-vision-probe/prepare.py、README.md、.github/workflows/minicpm-gpu-probe.yml。原引擎a5624430e61f44b4842246b15a352740218a3d49，固定Khronos头文件及NDK，单切片llama-mtmd-cli，启用Vulkan、保留CPU路径。仅CI编译/ELF及依赖验证，非真机测速。Actions 45566558972 in_progress https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45566558972。
- 后续核实编译；再进行实际设备CPU/GPU同图对比及准确性，保留GGUF权重，分离视觉编码、LLM prefill和首字，测试4/6/8线程。之后再考虑常驻与实时预览最新帧，不声称毫秒级已实现。旧签名问题未解决，不要卸载旧实验版。

- GPU探针 Actions 45566558972 completed/success：NDK交叉编译llama-mtmd-cli及Vulkan后端成功，readelf证实AArch64及libvulkan.so依赖，artifact 15000174008（48654381 bytes，zip SHA256 9fd4c28082691ba821e97c2f6a77915f50bf9b9da1f1c623d70ab1bd8015f0dd）已上传。仅编译和静态检查，不是APK、不代表天玑9400 GPU实际运行或提速。下一步独立真机CPU/GPU对比，验证实际后端及正确性；保留已安装模型及正式软件。

---

# 2026-11-25｜蓝星离线AI实验室 v0.1.1（速度专项，构建中）
- v0.1.0 用户真机1B首轮已完成：3072×4096、9切片、加载2648ms、image_accept264ms、prompt_prefill40738ms、first41055ms、total45047ms、157 token、38.81t/s、PSS2339325KiB、电池34.5℃前后不变；CER未输入、精度及暖机未验证。用户授权增加分辨率和切片实验，不修改正式蓝星，不更换模型。
- 同独立分支 exp/minicpm-offline-lab-20261125，基于已发布7a72cf455a40ff4e68cb984ba8db8e54a668d1d0，HEAD c403aadf9a2263a1fb189f856b9a38423dcb3156。新增1/2/4/6/9切片上限，原图及2048/1536/1024/768最长边；原图切片横向实验、固定切片分辨率实验、单配置1冷2暖及原双模型对比；图像按比例不放大不裁切，缩放使用PNG，保留原图字节；记录输入实际尺寸、缩放耗时、首字及含缩放时间、每轮文本显示，修复系统栏覆盖，增加缩放比例单测。模型源、目录、版本完全不变；原生视觉和提示词仍合并计时。上限不等于实际切片数。
- 上一版使用CI默认debug签名且未保存私钥，不能事先保证覆盖升级；本版CI核验已发布APK SHA256及签名，实际证书相同才保留同包名，否则独立.speed包，不卸载旧版，附Windows ADB本地迁移脚本，仅复制1B模型及可选旧数值日志、哈希核对、需约1.78GB额外空间，不依赖模型网络下载；脚本尚未真机验证。CI另核对新旧APK模型元数据完全一致。不要提示用户先卸载。未来持久签名需配置私有密钥，不公开。
- 已提交且 Actions 45555111990 in_progress，尚未核实完成/发布。链接 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45555111990 。新版本tag计划 bluestar-offline-ai-lab-v011-20261125。发布后查证签名模式、资产、CI及交接；实际速度和准确率需真机复测。

- 速度专项首轮45555111990失败：真实旧APK证书934e8d035239b0f1a164b2a2c49e887eb223b78e5b50be2d6d1858d8ee60164b，与本轮临时新证书不一致，已确认不可直接覆盖，需独立.speed包+本地ADB迁移，勿卸载旧版。模型元数据验证成功；Gradle配置阶段阿里云Maven镜像502，Kotlin/原生编译未执行。修复提交 badd0a1e43fe0350a7a3777c1fd1fc63c160f6b0，仅替换CI Gradle依赖源为官方源，重新构建，run待查。未发布。

- 第二轮45555596729：官方依赖源成功，Gradle编译及单元测试全部通过（BUILD SUCCESSFUL 47任务）；APK有效签名，但实际证书303c5e918da1fda0a88f1fe23f6c7a0858253f0706e01dcfb5a68a1f3fa5e6dd与预设证书不同，末道验证正确拦截，未发布。修复提交 4fa2875c7ef526f9786ef16049e87f8be9faa0a5，显式绑定Gradle debug signer到RUNNER_TEMP内预比较密钥绝对路径，保留签名严格比对，不改识别逻辑；第三轮 Actions 45562187102 in_progress。实时预览搜寻只讨论未授权实现。

- 第三轮45562187102 completed/success，全部编译/单元测试/APK包名签名原生库及与v0.1.0模型元数据一致性验证通过，发布已核实，target 4fa2875c7ef526f9786ef16049e87f8be9faa0a5；APK 22553126 bytes，SHA256 a4fc1eb09fa32cd0d494b8a1ea9530fe6664ea4f4b2aff794c83393a87d93c82。实际包名com.vobich.bluestar.offlinelab.speed，side_by_side，签名394024578471e7252c417a63c767c85cb1bdf9742bbf65d409a71f474b3b96af，与旧版不同；旧版不可卸载，附ADB迁移脚本（未真机验证）或用户自行下载。APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-offline-ai-lab-v011-20261125/BlueStar_Offline_AI_Lab_0.1.1.apk 。先进行1B切片、分辨率真机对照；未实现实时预览搜寻；正式版不变。

---

# 2026-11-25｜蓝星离线AI实验室 v0.1.0（独立实验，CI通过已发布，真机待验）
- 用户批准独立实验、模型分开下载、现有任务及数值对比、逐行检查；用户确认 v1.0.98 C110主动通知实测稳定，25次实体及5次遥控取图日志成功，长期锁屏/待机仍待测；正式版不修改。
- 实际复核原厂 OpenBMB/MiniCPM-V-Apps main 6665801f45dd17c7664a67469eb7e9013fd81def：安卓 v2.6 源码 ModelInfo 有 OCR 1.0，但无4.7两档；官方网页列出4.7支持不能替代安卓源码验证。找到社区安卓适配 mlguyYT/MiniCPM-V-Apps 7f2bac650137230539784b794205470077915df7，原生引擎子模块 a5624430e61f44b4842246b15a352740218a3d49；作者实测S20短文本35B，图像推理、最终1B、天玑9400未证实。第一版仅4.7 1B及35B，OCR 1.0需另行适配官方版面分析/ONNX管线，不假冒已支持；不含TTS、追问或C110。保留五个正式任务原提示词及自定义。
- 分支 exp/minicpm-offline-lab-20261125 基于正式版 a1850a37e965e1b9115ca26dd277755749811c03；初始 ef78194fef3468a85180f9a819f9a2d8bbaddbb8，当前 7a72cf455a40ff4e68cb984ba8db8e54a668d1d0。新增 offline-ai-lab/ 独立实验及 .github/workflows/minicpm-offline-lab.yml；独立包名 com.vobich.bluestar.offlinelab；同图同任务每模型1冷2暖、串行释放、原图JPEG/PNG、下载/导入/删除、SHA256与长度检查、数值日志、可选参考文字CER；首段估计非实际出声，PSS采样非真实峰值，电量计可能缺失。固定模型权重HF revision 2a3a3ddfbfc3d8e98c1070680b2fa98df88c3452，首轮CI已验证1B两文件1780750400 B、35B 43文件16623585508 B及40专家分片尺寸匹配。
- 首轮 Actions 45550533566 源码替换和权重元数据成功，ubuntu-latest映射26.04无sdkmanager失败，未编译/发布；已修正为ubuntu-22.04及完整SDK命令路径、固定权重revision、返回拦截及CER线程，第二轮 45550743418 源码和权重验证成功，SDK安装进行中。真实 CI/Release 待后续核查。预定 tag bluestar-offline-ai-lab-v010-20261125；未验证资产不得发下载链接。原厂源码和社区后端并非全量人工审计；新增代码已检查关键状态、文件校验、取消及 native reset/decode 失败传播，实际编译/真机仍未完成。
- 后续：查询本轮Actions并据实际失败日志修复；核对APK实际包名、launcher、native库、签名及SHA256与Release资产后回写记录。先真机测试1B图像任务和断网；高性能版需足够存储与内存，可能慢/OOM/热，确认后再下载；不把CI通过当真机通过。另研究官方OCR 1.0后端接入；不得回退正式蓝星。

- 第二轮 Actions 45550743418 已 completed/success（SDK、完整编译、单元测试、APK包名/启动Activity/原生库/模型元数据/签名验证、上传、发布均成功）；Release target 7a72cf455a40ff4e68cb984ba8db8e54a668d1d0，资产 22553106 bytes，SHA256 03851ddc853b06d7e6eb70ff768a30dfaf61d08cfcbefad1808d0f8cf34b09df，已验证 uploaded。下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-offline-ai-lab-v010-20261125/BlueStar_Offline_AI_Lab_0.1.0.apk 。真机未验收，先测1B。模型不打包、不进入自有仓库；手动下载HF。

---

# 2026-11-25｜蓝星向导 v1.0.98 C110主动通知监听、移除自动删图（CI通过、已发布，真机待验）
- 独立v0.1.0真机验证：照片模式下用户确认六次实体快门均成功拍JPEG，TCP3333被动收到3020 Status19/20六对，间隔220/197/205/307/223/206ms；录像模式收到3020 8/9及2020计时，正式版忽略。无应用层心跳或主动发送，事件机制已验证；Status20后立即取完整JPEG及长期锁屏尚未验证。用户授权正式修改并彻底移除自动删图。
- 基于v1.0.97发布源码10410e60ca1103ab8799a3ec12a555f45529e525，分支 exp/c110-event-listener-v0198-20261125，初始提交 c329567e39d6ace79a55cc965cf600f6b8adc4ab；修正旧语音测试接口后的 HEAD a1850a37e965e1b9115ca26dd277755749811c03，versionCode107/versionName1.0.98-event-listener，同包名。保留前台服务、Home监听开关、CPU/Wi-Fi锁、AI语音门禁和原图识别。新C110PhysicalWatcher仅一次TCP3333连接，启动/重建会话时一次身份和目录基线，空闲仅查询本机网络/语音状态，收到配对19/20立即读取；19取消旧任务，20触发读取，最新任务优先，最多3次未就绪读取（100/200ms仅失败后），I/O异常或取图未成功关闭监听，不盲目重连/轮询。保留原目录路径排序策略并计数未提交事件，文件名复用或初始化期间拍照可能漏读；Wi-Fi租约、深度休眠及原有电量查询不变。真实锁屏及速度需验收。
- 移除监听间隔、自动删图设置及实际执行代码、C110DeletePolicy、4003发送入口、后台清理线程；保留严格JPEG只读路径校验C110JpegPath，遥控/预览/实体原图均留在相机。诊断poll_interval_ms换为event_capture_ms（19至20间隔），photo_total_ms为20至取图完成。详见cloud-vision-app/docs/C110_EVENT_LISTENER_V0198.md。
- 首轮 Actions 45442310238 静态检查通过，旧语音测试仍引用旧policy接口导致失败（未发布）；已适配测试、增加tests路径触发，修正后 Actions 45442450771 completed/success，所有静态检查、回归测试、完整arm64编译、签名与包名/资源验证、上传、发布均通过。Release target a1850a37e965e1b9115ca26dd277755749811c03，资产53583535 bytes，SHA256 6243df6bb0849edeceae86b305d4050f58170ad0058a70564d0133e5a224b3b5，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-event-listener-20261125-v0198/BlueStar_Guide_Event_Listener_1.0.98.apk。先亮屏连续拍照、快速连拍、语音及手动拍照，然后短锁屏测试；确认原图保留，异常立即停。已核实发布资产；真机尚未验收。 

---

# 2026-11-25｜C110 只接收拍照事件独立实验 v0.1.0（CI通过、已发布，真机待验）
- 用户真机反馈 v1.0.97 锁屏初期拍照朗读正常，随后 Wi-Fi/3012与目录查询失败；C110无明显发热，灯光和按键一度无响应，同时长按Wi-Fi键及电源键后恢复。原因未证实，不再强化轮询或保活。用户要求下一个正式版本完全移除自动删图（尚未实施），批准先研究原厂通知并做独立只接收实验。
- 本轮重新打开两份原厂APK，实际解析classes5.dex：SJCAM X LyCamera构造0xb520c引用3333，SocketHelper.connection 0xac714建立TCP，readWork 0xac9d0识别3020/2020，onReadData$lambda$0 0xb6090处理3020；Zone LYCamera.startSocketHelper 0x18d554固定192.168.1.254:3333，readWork 0x213148、onReadData 0x18ca00、LyDataPack.getStatus 0x18a20c。原厂存在事件通道，但实体快门是否触发尚未真机证实，3020/status3、4按候选记录；X有sendPing路径，不发送任何应用层数据的实验若无事件不代表不支持。
- 基于v1.0.97源码10410e60ca1103ab8799a3ec12a555f45529e525，独立分支 exp/c110-passive-event-probe-20261125，提交68a560ac5e7542e5cc67facfdb316c497fe2daf5。仅新增c110-event-probe独立Gradle项目及CI，applicationId com.vobich.bluestar.c110eventprobe，版本0.1.0。不改正式版。手动连相机系统Wi-Fi，唯一网关匹配，用户启动一次TCP连接，无应用层发送/HTTP/SD查询/下载/删除/自动重连，最多120秒及131072字符，离开界面或锁屏关闭；数值日志与手动拍摄标记。初次实验仅亮屏，先关闭正式蓝星监听和自动删图、原厂App；相机异常立即停止。详见c110-event-probe/README.md。
- Actions 45439941495 completed/success，独立性检查、事件拆包测试、APK编译签名及包名验证、发布通过。Release target 68a560ac5e7542e5cc67facfdb316c497fe2daf5，资产14805 bytes，SHA256 57776f2b134f5e3bcca9e594340d1f0c0aeda31c7b9a2567d906489e27b6ef0b，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/c110-passive-event-probe-20261125-v010/C110_Passive_Event_Probe_0.1.0.apk。验收：连接成功静置10秒，实体快门3次（每次间隔10秒），复制日志确认事件时间/状态；如无事件先排查会话初始化及固件支持，不擅自增加命令。禁止将CI成功当作真机成功。

---

# 2026-11-24｜蓝星向导 v1.0.97 首页监听开关与锁屏拍照（CI通过、已发布，真机待验）
- 用户授权：删除主页重复连接相机图标，原厂式电量图标进一步放大，电量旁放一个实体监听开关；开启监听默认支持锁屏与切后台，不增加锁屏选项，其他功能保持。
- 基于v1.0.96发布源码 `01be96716238eacc7008f6a42af3cf08fa049b45`，分支 `exp/c110-lockscreen-listener-v0197-20261124`，提交 `10410e60ca1103ab8799a3ec12a555f45529e525`，versionCode106/versionName1.0.97-lockscreen-listener，关于软件同步。首页移除重复入口、电池36×20→46×25dp、原厂映射/颜色不变；保留原监听间隔和删图设置、复用原ENABLE设置键，移除设置页重复监听开关。
- 单一用户启动的 connectedDevice 前台服务 C110ListenService 持有原 C110PhysicalWatcher、Context化的原 C110FrameReader/CaptureDingFeedback，开启期间维持CPU partial wakelock及Wi-Fi锁；关闭释放、停止通知可关闭（可见性依赖系统通知权限），不唤醒屏幕、不访问手机摄像头/麦克风、不增加锁屏开关、不在开机自动启动。Home锁屏/外部App不重置基线；蓝星内部非主页Activity可见时暂让出相机和语音，退出后恢复监听。保留Home语音/手动动作门禁、AI寻找、4003删除、原厂电量/轮询与所有原有取图协议。仍依赖原Wi-Fi连接及租约；Home被销毁或系统断网可能需手动重连，深度休眠/vivo省电、第三方TTS和功耗需真机验证。无自动豁免或强行绕过锁屏。
- [Actions 45431795346](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45431795346) completed/success：单监听生命周期静态检查、原厂协议隔离检查、语音/物理快门/删图/电量及其他全部回归测试、完整Android arm64编译、APK签名与资源/包名验证、上传和发布全部成功。Release target `10410e60ca1103ab8799a3ec12a555f45529e525`，真实资产53583535 bytes，SHA256 `3d466f34a6f62905c8923326e34b1bf4eca8527abdd035e8935e16b060efb133`，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-lockscreen-listener-20261124-v0197/BlueStar_Guide_Lockscreen_Listener_1.0.97.apk 。验收：开启并等监听就绪→锁屏1/5/30分钟实体拍照，确认屏幕不亮、取图/朗读/自动删图；切其他App、解锁不重复、关闭无监听；测试AI找目标、手动快照、预览/设置/阅读切换及耗电。详细见 `cloud-vision-app/docs/C110_LOCKSCREEN_LISTENER_V0197.md`。CI成功不等于真机锁屏验收通过。

---

# 2026-11-24｜蓝星向导 v1.0.96 原厂电量档位图标（CI通过、已发布，真机待验）
- 用户要求精确复现原厂电量档位与黑色横向电池外形，只放大、格子绿黄红，取消百分比及多余提示，已授权修改。直接从文件库恢复 SJCAM X.apk（38261685 bytes，SHA256 `a8ebceff0faa8d5b96b4e5f34bf981c932d39bf5070038555f9169edc3cc9b3b`）及 SJCAM Zone 6.7.3.16（197152487 bytes），实际解析 DEX 指令、resources.arsc 与PNG。用户截图黑色横向电池三白格匹配 SJCAM X 的 ic_battery_3（res/_B.png）；Zone 有不同图标档位，不混用。X `LyCamera.getBatteryStatus` 0xb5908 发3019；`callBackBattery` 0xb5398 映射0满四格、1三格、2两格、3一格、4低电量、5充电；`CameraControlActivity.obServerUI$lambda$2` 0x10cf7c 选择实际图标。原厂默认刷新60000ms，C400例外，不涉及本机C110。精确代码映射不代表真机物理百分比或刷新瞬间一致。
- 基于v1.0.95发布源码 `dbda953e9880e9882175431902d319e8343c2bab`，分支 `exp/c110-oem-battery-v0196-20261124`，提交 `01be96716238eacc7008f6a42af3cf08fa049b45`，versionCode105、versionName1.0.96-oem-battery。仅 HomeActivity、C110BatteryPolicy、BlueStarReleaseInfo、build.gradle、单测、说明及独立CI共7文件。原创绘制原厂式深色电池外形36x20dp（原21x14），三四格绿、两格黄、一格/低电红、充电闪电；无可见文字，保留无障碍描述，未知为灰色空图标；不打包原厂PNG。仅解析3019 Value枚举，错误未知，不估算百分比；C110HomeStatus/60秒轮询、AI寻找/取图/自动删图/其他功能不改。详情 `cloud-vision-app/docs/C110_OEM_BATTERY_V0196.md`。
- [Actions 45427621341](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45427621341) completed/success，原厂枚举与UI隔离静态检查、电量解析单测、其他回归、完整Android arm64编译、APK验证及发布步骤全部通过。Release target `01be96716238eacc7008f6a42af3cf08fa049b45`，真实资产53566991 bytes、SHA256 `e25d137f475785c772729bb2b140c8e2a09d26bdb68328d0b16c457a823587c1`，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-oem-battery-20261124-v0196/BlueStar_Guide_OEM_Battery_1.0.96.apk 。下一步覆盖安装、逐档位对照原厂、检查读屏与其他功能；未验真机前不宣称实际显示100%一致。

---

# 2026-11-24｜蓝星向导 v1.0.95 完整取图即删除（CI通过、已发布，真机待验）
- 用户真机确认 v1.0.94 原实体快门自动删除有效，要求改为所有被蓝星完整获取的 C110 原图都删除，不依赖OCR结果，覆盖手机遥控、预览高清、AI寻找、识别失败与任务取消；用户已授权修改。
- 基于正式 v1.0.94 `8ca8aa75d007436ecf3a504c49b8567b8a1aa5fd`，独立分支 `exp/acquired-photo-delete-v0195-20261124`，源码提交 `5787c9f294364a5a870603ed1d0707078ef7047c`，后续纯文档 HEAD `dbda953e9880e9882175431902d319e8343c2bab`。versionCode104、versionName1.0.95-acquired-photo-delete、原包名及设置键不变，关于软件与删除确认说明已同步。
- C110WifiCamera 原生遥控取图和 C110PhysicalWatcher 实体下载成功后统一 enqueueAcquiredDelete；独立串行清理线程，保存精确路径、SHA256、原网络，删除前继续核查开关、同一相机网络、标准C110、4003支持、文件内容，不按识别结果或ASR/取消/新拍照撤销。未获取的旧照片、未下载的连拍照片及实时帧不删；断线、关闭开关或校验/固件失败可能保留。不得宣称每张必删或零网络竞争，预览状态下的删除需真机测试。
- [Actions 45419134274](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45419134274) completed/success，静态检查、纯Java回归、Android完整编译、APK签名、包名版本/资源/ZIP/SHA256及发布步骤全部通过。Release target `dbda953e9880e9882175431902d319e8343c2bab`，实际资产53567007 bytes、SHA256 `f1aefe9a9750caf93afdeca3bcf712540577df930d7a33edfb18345ba73312bd`，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-acquired-photo-delete-20261124-v0195/BlueStar_Guide_Acquired_Photo_Delete_1.0.95.apk 。另一构建45419073683编译与APK检查也通过，仅发布步骤失败；不覆盖成功Release。下一步用户真机验证实体、遥控、预览高清、AI寻找、识别失败、连拍、关闭开关和旧图保留，以及实际延迟。

---

# 2026-11-24｜蓝星向导 v1.0.94 实体快门与AI寻找目标衔接修复（CI通过、已发布，真机待验）
- 用户真机确认v1.0.93实体快门取图速度好，14条200ms档记录，目录通常175–221ms、4320×2432原图146–272ms、编码0–1ms；一轮上传12165ms异常；报告“帮我找厕所”语音确认后实体拍照不响应。源码发现HomeVoiceAssistant借用离页/手动快门的beforeManualAction清空基线，且录音/ASR/TTS isSpeaking均阻塞目录监听，恢复双基线时可能吞新图。寻找目标独立通道旧诊断缺失，不能仅凭缺日志断定全部原因。用户授权修复。
- 从正式v1.0.93 `e6d905a99035c4fa3f5d34113378a4a9b59a440c` 派生 `exp/physical-ai-handoff-v0194-20261124`，最终源码/CI `8ca8aa75d007436ecf3a504c49b8567b8a1aa5fd`，versionCode103、versionName1.0.94-physical-ai-handoff、原正式包名不变。
- Home AI使用独立prepareHomeVoiceSpeech/beforeVoiceAction，取消旧识别/删除候选/待提交旧图，不重置历史集合、不暂停目录查询。单RAM槽C110DeferredPhoto保留最新完整新JPEG；录音/ASR/AI处理期间继续下载，不上传、不朗读，待指令提交/结束后下一轮有效快照交付最新图一次，使用刚保存目标；新图可中断旧AI确认语音，避免第三方TTS isSpeaking无限阻塞。录音pending明确开始/完成/失败/取消，关闭/手动快门/灯光/离页/断线仍重新基线。无锁屏服务。相机协议/轮询间隔/原图/云模型/设置/灯光电量及主页布局保持。
- 目标视觉通道原请求、保守结果found/possible/not_found及找到清除目标不改；新增可选真实上传耗时、匿名target.route、upload_ms（成功）、network_ms、outcome（1找到/2疑似/0未找到/-1失败取消）、capture.outcome取消、cloud.outcome失败或无文字、c110.voice_wait_ms及TTS onStart。目标失败语音提示并保留寻找目标和原图；自动删除仍仅普通任务非空成功，寻找图不删，录音或AI处理不启动清理，原4003精确文件/SHA256/同会话安全门禁不变。日志不含目标/文字/路径/图片/密钥；TTS onStart非实际出声证明。
- compare仅12文件：HomeVoiceAssistant、HomeActivity、C110PhysicalWatcher、C110DeferredPhoto、C110QuickShotController、TargetVisionClient、C110FrameReader、BlueStarReleaseInfo、build.gradle、C110DeferredPhotoTest、docs/PHYSICAL_AI_HANDOFF_V0194.md、.github/workflows/physical-ai-handoff-v0194.yml。CI [45414837544](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45414837544) completed/success；静态约束、最新图缓存单测、旧回归、完整APK检查后发布。关于软件已更新本次版本与变更。预期标签 `bluestar-physical-ai-handoff-20261124-v0194`，资产 `BlueStar_Guide_Physical_AI_Handoff_1.0.94.apk`；全部CI步骤已通过，Release target与最终源码一致，APK真实资产53567003 bytes，SHA256 `8735f14aa601ec479ac90652d2a23ed3449ab051552e7bf31d92d028820a6c67`，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-physical-ai-handoff-20261124-v0194/BlueStar_Guide_Physical_AI_Handoff_1.0.94.apk 。
- 下一步：CI、Release目标和真实APK已核验，覆盖安装保留保益桥接器。关闭自动删除，主页监听就绪，按住AI“帮我找厕所”，分别在确认语音期间及结束后立即拍照，检查目标结果、未找到继续、找到退出、失败有声；测试录音期间连拍/取消录音恢复、保益/灯光/普通拍照、导出匿名诊断。实际设备尚未验收。

---

# 2026-11-24｜蓝星向导 v1.0.93 实体快门自动朗读、可选自动删除、关于软件（CI通过、已发布，真机待验）
- 用户确认标准C110实体按键在Wi-Fi连接时可拍照且不断开，原厂App能显示缩略图；授权增加可选实体监听、100/200/300/500ms、自动删除识别成功的本次原图、设置底部关于软件，今后每次更新显示对应版本信息。基于正式v1.0.92 `04e3d542900e5ae2c9b2e2169c15cfe2ba95b939` 派生 `exp/physical-shutter-v0193-20261124`，最终源码/CI `e6d905a99035c4fa3f5d34113378a4a9b59a440c`，versionCode102、versionName1.0.93-physical-shutter、原包com.vobich.bluestar.c110plusquick159。
- 统一设置→相机→C110：监听默认关，默认300ms；删图默认关且开启前明确不可恢复确认。仅主页前台亮屏，3012确认标准机，两次完整DCIM HTML基线不提交历史图；单worker无积压，HTTP结束后按设置间隔，目录异常/变化、重连或手动快门/AI/其他页面重新基线，可能漏读保护旧图。新路径取消前一实体任务，完整JPEG只读下载校验，复用Home同一个QuickShotController/FrameReader、叮、云模型/任务/上传模式/语音，不发送1001/模式/预览命令，不增权限或租约。多图同轮以文件名排序末项为候选（非保证实际时间最新），不删歧义图。退出主页/熄屏暂停，不承诺锁屏。
- 自动删除：同会话唯一新路径，开关在提交/完成时均开，非空正常云识别成功回调且请求ID仍有效；失败、空文本、找目标、取消、歧义、历史、断线均保留。恢复旧C110DeletePolicy及原厂DEX 4003单文件证据，核验3012/3002声明4003、精确路径/目录存在、重新下载SHA256一致、同Network及当前会话许可后发送一次4003，再只读复核；不批量/格式化/重试。下一轮无新候选才后台执行，可能占用相机IO时间但不等待后才朗读。已发出的删除无法撤回，C110真机能力尚未验证，先关闭删图测试10张，再用不重要照片验证且旧图保持。
- 诊断c110_physical含轮询设置、目录查询、JPEG下载、发现后取图及原云端首字/网络、TTS onStart（仅引擎通知不是实际出声；实体快门绝对触发时刻未知）。关于软件从PackageManager读取真实版本/内部号；新增BlueStarReleaseInfo记录更新日期、版本和变更内容。**以后每次发布同步更新BlueStarReleaseInfo.VERSION/DATE/CHANGES与Gradle及交接，CI核对版本一致。**
- compare已核对仅14文件；新增C110PhysicalPolicy/Watcher/DeletePolicy及测试、BlueStarReleaseInfo和文档/工作流，最小改动HomeActivity、HomeVoiceAssistant、C110QuickShotController、C110FrameReader、C110WifiCamera、BlueStarSettingsScreen、build.gradle。旧1001/预览HTTP路径、六宫格底栏、灯控电量、USB手机、包名/保益Manifest不变。CI [45405666994](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45405666994) completed/success，静态范围/安全、纯Java去重及精确删图路径、既有回归、完整APK检查后发布。预期标签 `bluestar-physical-shutter-20261124-v0193`，资产 `BlueStar_Guide_Physical_Shutter_1.0.93.apk`，全部CI步骤已通过，Release target与最终源码一致，APK真实资产53566999 bytes，SHA256 `91e49e09f83cd961e3cff7849bc81883d2418f62ec1406e4aefe103ac97b3d87`，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-physical-shutter-20261124-v0193/BlueStar_Guide_Physical_Shutter_1.0.93.apk 。
- 下一步：CI与Release及真实APK已核验，覆盖旧正式版并保留保益桥接器。先关闭删图、300ms监听，主页就绪后连续按实体快门10次，比较100/200/500ms日志及原快捷拍照；再明确确认开启删图，测试单张无关照片、旧图不动。检查关于软件版本及正常预览/朗读。实际固件尚待验收。

---

# 2026-11-24｜蓝星向导 v1.0.92 主页灯光开关及电量百分比（CI通过、已发布，真机待验）
- 用户批准示意图：保留v1.0.91六宫格与底部导航，仅在相机卡快速连接右侧增加灯光按钮并改善百分比显示；要求检查后发布。截图显示Wi-Fi未连接、电量未知，不能凭图认定3019不支持。
- 从已发布正式v1.0.91 `7d73c0e9efea5c9ec99913ec14f073d9c8823b64` 派生 `exp/home-light-battery-v0192-20261124`，最终源码/CI `04e3d542900e5ae2c9b2e2169c15cfe2ba95b939`；versionCode101、versionName1.0.92-home-light-battery、原正式包名不变。
- 电量：原3019只读轮询不变，新增明确BatteryPercent/BatteryPercentage及带%值的真实百分比；裸值0–3按公开SJ5000X参考档位显示约100/75/50/25%（不是C110实测值；参考https://aakaashjois.github.io/SJCAM-SJ5000X-Elite-Commands/），4低、5充电中、其他裸值待确认，断网/失败未知。用户仍需连接真机并比对原厂，读屏明确说明估算及实际回执。
- 灯光：快速连接右侧小灯泡按钮，首次未知状态选择开启/关闭，收到9038成功回执后同一连接直接反向切换，失败、换网或离开页面清除已知状态。复用3012标准C110确认及9038/0或1；无自动写、无新权限、无新Wi-Fi请求。成功后同步原灯光策略（开启=始终显示、关闭=立即关闭），设置同源；机身写可能持久化，非临时关闭承诺。回执成功非物理状态检测。拍照进行中拒绝灯光操作，灯光处理中防重复且暂停新电量查询。未改变拍照调度。
- compare仅7文件：HomeActivity.java、C110DeviceControls.java、C110BatteryPolicy.java、C110BatteryPolicyTest.java、app/build.gradle、docs/HOME_LIGHT_BATTERY_V0192.md、.github/workflows/home-light-battery-v0192.yml。CI [45391004192](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45391004192) completed/success，严格范围/原导航及快捷入口不变/灯光安全/电量测试和既有回归，完整APK编译与发布门禁。预期标签 `bluestar-home-light-battery-20261124-v0192`，资产 `BlueStar_Guide_Home_Light_Battery_1.0.92.apk`；全部CI步骤已通过，Release target与最终源码一致，APK真实资产53550615 bytes，SHA256 `ff65a486ff85bdd1c2abe629c22c1251bc9a6ac8c2ad1bbca4d0d1d886a243b8`，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-home-light-battery-20261124-v0192/BlueStar_Guide_Home_Light_Battery_1.0.92.apk 。
- 下一步：CI与Release同提交、资产真实存在已核验；覆盖安装保留旧主程序设置及保益桥接器。实测连接后电量、灯光开关物理状态、读屏、保益快捷拍照、预览、高清拍照及TTS。其他功能不动，画质实验继续暂停。

---

# 2026-11-24｜蓝星向导 v1.0.91 主页六宫格、运动相机Wi-Fi和电量（CI通过、已发布，真机待验）
- 用户批准参考图的深蓝简洁布局：取消“蓝星向导”标题栏，顶部相机连接/电量状态和快速连接，三列两排图标，原底栏主页/预览/按住AI保持；授权开发并检查其他问题。
- 正式 v1.0.90 `e1df4221a99fec313eb4db8545cd7e49c6fc5afc` 派生 `exp/home-grid-camera-battery-20261124`，最终源码/CI提交 `7d73c0e9efea5c9ec99913ec14f073d9c8823b64`，versionCode100/name1.0.91-home-camera-status，包 `com.vobich.bluestar.c110plusquick159` 不变。
- Home顶部无标题，C110状态卡+快速连接；3×2固定图标50dp：快捷拍照/实时预览/朗读任务、阅读模式/连接相机/设置。实际native Button完整点击及无障碍焦点，图标不独立焦点；文字随系统字体缩放、页面可滚动；保留底栏按住AI、原各页面导航、保益QUICK_SHOT action/Manifest/启动器、已验证的单次快门和云/TTS。新连接入口复用Home已有C110WifiBootstrap租约，不另建连接、不强制断开。
- 电量：Novatek NT96660 Wifi Command Application Note §5.3.19（https://www.mputek.store/filedown/Survey3-WIFI-Manual.pdf），3019无参只读GET。现有相机Network上固定网关192.168.1.254，禁重定向、1200ms连接/读超时、8KiB上限；仅Home前台3秒检查连接变化、60秒读取电量，手动刷新至少间隔10秒、单线程单请求、快捷拍照pending/取图期间不新发、离开停止调度且丢弃过期回执。已发起短查询不阻塞快门/UI。验证Cmd/Status，枚举0–5为公开协议参考档位（标“参考”），仅明确百分号显示百分比，裸值6–100显示读数待确认，错误或断网显示未知，绝不伪造。实际C110固件枚举未真机验证。无新权限/云请求/持久化电量；读屏可读设备回执数值。
- GitHub compare仅7文件：HomeActivity.java、C110HomeStatus.java、C110BatteryPolicy.java、C110BatteryPolicyTest.java、app/build.gradle、docs/HOME_CAMERA_STATUS_V0191.md、.github/workflows/home-camera-status-v0191.yml。CI [45386721646](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45386721646) completed/success；静态验证六宫格、点击区域、原dock/导航/快捷拍照代码字节不变、只读安全和电量解析单测，保留既有回归及APK清单/库/资源/包名检查。预期Release `bluestar-home-camera-status-20261124-v0191`，资产 `BlueStar_Guide_Home_Camera_Status_1.0.91.apk`；全部CI步骤已通过，Release target与最终源码一致，APK真实资产53550611 bytes，SHA256 `a7cf810269cbf9b6f7456e4dac34d191a93e484431c25ac0f0b03561736bf07c`，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-home-camera-status-20261124-v0191/BlueStar_Guide_Home_Camera_Status_1.0.91.apk 。
- 下一步：CI、Release与提交、APK资产一致已核验；覆盖正式App不卸载且保留保益桥接器。真机验收六宫格、读屏焦点、Wi-Fi连接/断开、电量返回值、普通打开不拍照、保益快捷拍照、底部导航/按住AI、其他相机和语音。画质实验仍暂停。

---

# 2026-11-24｜蓝星向导 v1.0.90 快捷拍照恢复、预览实验UI清理（CI通过、已发布，真机待验）
- 用户决定暂停C110预览画质研究；保益双击音量快捷拍照失效，截图明确出现旧桥接器Toast“请先安装蓝星向导v1.0.59主程序”。用户授权修复并去掉预览顶部A/B实验选项、右下“查看上传图”，恢复原布局。
- 发现实际代码缺口：旧独立桥接器显式发送 `com.vobich.bluestar.cloudprobe.action.QUICK_SHOT` 至正式包 `com.vobich.bluestar.c110plusquick159/com.vobich.bluestar.cloudprobe.HomeActivity`；Home Manifest只有MAIN/LAUNCHER filter，未声明QUICK_SHOT。Android 13+对外部Intent检查匹配，可能抛ActivityNotFoundException，符合桥接器错误提示；用户手机是否同时缺正式包尚不确定，不能声称已经真机修复。
- 正式v1.0.89 `58963e56aaf772385979acea9d4a86949200f6e5` 派生 `exp/quickshot-restore-clean-preview-20261124`，不合入独立画质实验。最终源码 `e1df4221a99fec313eb4db8545cd7e49c6fc5afc`，versionCode99/versionName1.0.90-quickshot-clean-preview，原正式包名及桥接器包名/组件/action不变，保益无需预先重绑或重装桥接器。
- Manifest增加仅匹配QUICK_SHOT的非Launcher filter；Home的pending request在onResume激活Controller后才消费，onPause清除resumed守卫，避免丢请求；正常打开仍不自动快门。C110CameraActivity删除上传图按钮、弹窗和无用import，底部恢复单一高清拍照按钮；Reader删除仅用于查看上传图的RAM JPEG引用（真实取帧、JPEG100和识别数据不变）。无A/B或原生解码实验代码。保持1001、Wi-Fi、VLC、手机/USB、任务/模型/语音/设置不变。
- compare严格仅7文件：app/build.gradle、AndroidManifest.xml、HomeActivity.java、C110CameraActivity.java、C110FrameReader.java、docs/QUICKSHOT_CLEAN_PREVIEW_V0190.md、.github/workflows/quickshot-clean-preview-v0190.yml。CI [45344358461](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45344358461) completed/success，新增静态外部action/旧bridge/单Launcher/生命周期门禁及APK实际manifest检查，保留既有回归、完整编译、APK校验。预期标签 `bluestar-quickshot-clean-preview-20261124-v0190`，APK `BlueStar_Guide_QuickShot_Clean_Preview_1.0.90.apk`，已核实全部CI步骤通过、Release target与最终源码一致，APK真实资产53550615 bytes，SHA256 `7482011ad0b7dfdf473a1a4685ae215d57ef05498cef39f07b94f10f22c85078`，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-quickshot-clean-preview-20261124-v0190/BlueStar_Guide_QuickShot_Clean_Preview_1.0.90.apk 。
- 下一步：CI+Release同提交及实际APK资产已核验；覆盖正式版不卸载、不卸载旧蓝星一键拍照启动器。若只有独立实验版则需安装正式版，必要时重新填正式版API Key；独立实验包不删除。真机普通打开不拍，保益双击应只执行一次1001→OCR→TTS；检查桌面/前台/预览页以及界面无A/B和查看上传图。其他预览画质实验暂缓。

---

# 2026-11-24｜C110 原始解码帧画质专项独立实验 0.1.0（CI通过、已发布，真机未验）
- 用户实测 v1.0.89 12次 c110_preview：1260×2590完整Surface，JPEG 293005–354898 bytes、编码17–24ms、云端首字799–1194ms。大字准确率明显优于小字，仍不满足；怀疑有效视频细节不足，不能仅凭JPEG像素数确认真实视频分辨率。用户授权独立A/B实验，不修改正式版。
- 从已发布 v1.0.89 `58963e56aaf772385979acea9d4a86949200f6e5` 派生 `exp/c110-native-frame-quality-lab-20261124`，首轮提交 `1d628067bb3ca0d50fef079229f31727c666b713`，修正构建后的源码/CI提交 `745ef83ce931a779c9cbb26428ddeaccd0710000`。范围仅9文件（新增CMakeLists.txt）。独立包 `com.vobich.bluestar.c110qualitylab`、名称蓝星画质实验，正式版保留，实验需手动配置相同API key/模型/任务/语音。
- A保留完整视频Surface PixelCopy，加尽力读取轨道宽高、进程单核CPU估计；B同一RTSP与LibVLC3.6.5，JNI调用 libvlc_video_set_callbacks/format_callbacks 原生RGBA内存输出、软件解码，点击后等待下一解码帧至多2秒，不经过Android Surface，不裁剪缩放、不回退旧图，同一JPEG100和现有云/TTS管线。A/B顺序释放播放器，不同时开两路视频；B无实时显示，先在A固定取景，切换B后约5秒点黑屏取帧，查看上传图；记录实际回调尺寸、帧编号、耗时、JPEG字节及OCR。B是否有真实帧仍待真机验证；CPU为进程均值、两模式渲染路径不同，不能直接比较纯解码性能。
- 首轮 CI [45339429855](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45339429855) failure：范围检查与host回调格式/行距/新帧复制单测通过，手动调用 sdkmanager 失败（command not found），改由AGP externalNativeBuild CMake 自动安装既有NDK28.1；不改变取帧代码。中间CMake提交自动触发旧工作流，范围门禁会拒绝；最终 CI [45339557069](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45339557069) completed/success；全部回归、CMake ARM64 JNI构建、APK libvlc符号/包名/库验证、上传及发布均通过。预期Release标签 `bluestar-c110-native-frame-quality-lab-20261124-v010`，资产 `BlueStar_C110_Native_Frame_Quality_Lab_0.1.0.apk`，Release target 与最终提交一致，真实资产 53567025 bytes，SHA256 `f6f90661a5983efd7b2cf78010da88ddfc801736923e1550b4b280f820eb43bd`，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-native-frame-quality-lab-20261124-v010/BlueStar_C110_Native_Frame_Quality_Lab_0.1.0.apk。详细步骤见实验分支 cloud-vision-app/docs/C110_NATIVE_FRAME_QUALITY_LAB.md。
- 下一步：CI与Release资产提交一致已核验，真机同一小字/距离/光线、同模型A/B各5次。若B无回调、色彩异常、或两路同样模糊，继续按证据研究相机实际RTSP SPS和画质控制，不改正式版，不再反复放大截图。

---

# 2026-11-24｜蓝星向导 v1.0.89 C110 完整视频 Surface 取帧专项；v1.0.88 作废

- **真机反馈与授权**：v1.0.88 窗口截图连续四次 JPEG 均68000字节，用户明确反馈只读出“高清拍照”“查看上传图”等软件控件字样。用户要求作废该版本并以 v1.0.87 重新开发；保留旧发行记录，不删除历史。用户批准先去除预览取帧裁剪和缩图、获取完整视频帧验证正确性。
- **基线/分支**：从已发布 v1.0.87 commit `6925631076faf82e978dff208fe98817b7842bc9`（非 v1.0.88）派生 `exp/c110-full-surface-v0189-20261124`，最终源码 `58963e56aaf772385979acea9d4a86949200f6e5`；versionCode98、versionName 1.0.89-c110-full-surface，包名不变。
- **修改**：C110预览使用 SurfaceHolder.getSurfaceFrame() 读取Surface尺寸，区分View尺寸；PixelCopy.request(surface,image,...) 直接复制完整视频Surface，不截Window、不做ROI裁剪、不显式缩放、不改JPEG100单次编码。Surface尺寸不保证是RTSP解码轨道原始尺寸；实际缓冲区与目标尺寸不同时PixelCopy仍可能插值，不能宣称无损原始视频帧。尺寸变化/无效/取图失败即停止，不使用旧图。保留RAM查看识别用JPEG入口（相同byte[]进入原识别通路，不表示服务器实际收到），不另存或二次上传。预览OCR暂不跟随显示变焦/竖屏裁剪；显示变焦及1001高清照片裁剪不变。
- **范围核实**：GitHub compare仅5文件变化：C110FrameReader.java、C110CameraActivity.java、app/build.gradle、新文档C110_FULL_SURFACE_V0189.md和新workflow。回读核验Reader真实高清识别到shutdown之前的云端/模型/TTS方法完全相同；Wi-Fi、RTSP、LibVLC3.6.5、手机/USB、原厂快门不动。
- **CI**：[Actions 45337702071](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45337702071)，completed/success，全部静态检查、Java回归、ARM64 Gradle构建、APK结构/包名/原生库/音效及SHA256检查、Artifact及Release发布均通过。Release target与源码提交一致：`58963e56aaf772385979acea9d4a86949200f6e5`。APK 53550575 bytes，SHA256 `749c7b0e791294d38d356a319b6eb1c3b079781fdb9e445d0139d6bd4fb6d858`。已验证下载链接 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-full-surface-20261124-v0189/BlueStar_Guide_C110_Full_Surface_1.0.89.apk 。真机识别效果仍待验证。
- **下一步**：先验完整预览帧的“查看上传图”及同一场景OCR，记录view/surface/JPEG尺寸和full_surface=1；如果仍不正确继续查VLC真实输出，不重复Window截图方案。安装新版必须等CI及Release核验后覆盖，勿卸载旧包。真机验收尚未进行。

---

# 2026-11-24｜蓝星向导v1.0.88：C110真实视频轨道分辨率+屏幕实际合成画面取帧、查看真实上云JPEG；CI通过APK已发布，真机待验

- **授权与症状**：用户真机v1.0.87的c110_preview诊断反复报告`surface_width_px=1260`、`surface_height_px=2590`，`visible_width_px=1260`、`visible_height_px=2590`、`jpeg.bytes≈320–340KB`、`PixelCopy.status=0`，云端网络约0.9秒却仅识别或朗读“西施”两个字；同一用途的手机预览720×1280仍能识别，不能把问题归咎于图像像素总量。源码检查发现v1.0.87错误地将SurfaceView的安卓视图尺寸当作RTSP真实图像尺寸，直接用它运算crop ROI；“复制成功”不能证明收到的真实视频内容和屏幕一致。用户明确批准按方案改代码，并在严格验证后发布APK。
- **版本/基线/分支**：基于已发布v1.0.87提交`6925631076faf82e978dff208fe98817b7842bc9`派生`exp/c110-window-visible-preview-v0188-20261124`，功能提交`fb1759f408da9d92952a819687605d8b1f341351`，最终编译发布提交`b31cd1c0b43119c761f0e05aa8333695ff54d781`，CI/release target/分支HEAD三方回查一致；版本versionCode97、versionName `1.0.88-c110-window-visible`，包名`com.vobich.bluestar.c110plusquick159`原样。
- **实际修改**：`C110CameraActivity`使用`MediaPlayer.getCurrentVideoTrack()`取得VLC解码视频轨道宽高，读不到记-1，不用旧版Surface UI尺寸假冒原视频；按真实`cameraRoot`屏幕内容区从`getLocationInWindow`及padding计算窗口矩形，`C110FrameReader`改用`PixelCopy.request(activity.getWindow(),rect,bitmap,...)`获取经过VLC/View缩放和竖屏显示变换的**最终窗口可见画面**，不再对1260×2590之类安卓SurfaceView尺寸推算16:9源裁剪。目标Bitmap与窗口矩形宽高1:1，不额外缩小或重复裁剪；单次JPEG quality100后由原`recognizeJpeg`将**相同byte[]**交给云端任务/朗读管线。注：**窗口截图确实可能包含底部高清拍照/缩放条等UI遮盖，这是本版明示的限制，不能冒称纯视频像素无损；也不解决相机RTSP有损视频自身画质**。
- **图片核验入口**：C110预览页底部现有“高清拍照”按钮右边增加**“查看上传图”**；`C110FrameReader.lastPreviewSubmittedJpeg`仅保留本次运行的最后一张C110预览实际上传JPEG在RAM，点击只在本地Dialog展示、不重复上传、不写存储、不保存相册；关闭视图回收Bitmap，结束Reader清除引用。可直接直观核查文字是否完整、是否黑屏/拉伸、是否存在遮挡。
- **诊断**：c110_preview新增`frame.track_width_px/track_height_px`,`frame.window_left_px/window_top_px`,`frame.visible_width_px/height_px`；原`jpeg.width_px/height_px/bytes`,`cloud.upload_ms/network_ms`及隐私最近50次/7天、最多24事件仍保留。真实视频轨道缺失时数值-1，不假设1920×1080。
- **保护的成功功能**：与v1.0.87相比仅5文件改动：`C110CameraActivity.java`、`C110FrameReader.java`、`cloud-vision-app/app/build.gradle`及新`cloud-vision-app/docs/C110_WINDOW_VISIBLE_V0188.md`和`.github/workflows/c110-window-visible-v0188.yml`。不动C110原厂3012→3016→3001/1→2015/1 RTSP/WiFi及LibVLC3.6.5, 1001高清、前版金属叮音、手机/USB预览、云端模型/任务/TTS、视频缩放UI和原发行包名。
- **CI/Release已验证**：Actions`45336091896` **completed/success**，源码5文件白名单、VLC实际轨道查询、Window PixelCopy精确矩形1:1、JPEG一次编码和内存核验入口、供应商/模型/任务/摄像头放大几何/WiFi及USB回归、原UVC固定补丁、ARM64完整Gradle、APK启动/原生VLC/UVC库/音效资源/SHA256和GitHub release均完成成功。GitHub prerelease tag`bluestar-c110-window-visible-20261124-v0188`，APK`BlueStar_Guide_C110_Window_Preview_1.0.88.apk`，53,550,571字节，官方直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-window-visible-20261124-v0188/BlueStar_Guide_C110_Window_Preview_1.0.88.apk ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45336091896 。
- **真机验收重点**：直接覆盖v1.0.87不要卸载（保留API密钥/C110 Wi-Fi），点击C110实时预览任意画面识别后点“查看上传图”，观察真实图片是不是当前屏幕可见文字、有无黑边/方向错误/UI按钮盖住字，再导出c110_preview诊断（尤其track_width_px/height_px与jpeg.width_px/height_px），核实C110识别率。不要再让用户重复手机vs相机对照实验；先看实际上传图片。CI编译成功不能证明用户手机VLC窗口截图的正确性和识别率。
 
---

# 2026-11-24｜蓝星向导 v1.0.87：C110预览“所见即所得”PixelCopy直接截可见区域，CI通过、APK已发布，真机识别率待验

- **用户授权/目的**：用户已确认手机预览准确但C110运动相机预览屏幕快捷取帧识别差；不再重复做“手机/C110对照”已经验证过的实验。目标：**屏幕看到什么（包括1～4倍放大与竖屏铺满），就截相同可见区域给云端，不在中间额外损失有效像素**；用户明确允许在已讨论的最小范围修改，要求逐项逻辑检查、构建通过才发布。
- **唯一基线和隔离分支**：本次严格继承已发布v1.0.86 commit`7f19414fb9c5b290d18428055dd7f767b606d4cf`，分支`exp/c110-visible-preview-pixelcopy-v0187-20261124`，修复提交`090d1ccb56fac4fc098995d46952dd7eb4e91d44`，CI提交/最终HEAD`6925631076faf82e978dff208fe98817b7842bc9`。仅回写本PROJECT_HANDOFF.md到main，未将实验版代码合并main。
- **根因代码定位**：旧`C110FrameReader.tap`用`video.getWidth/Height`先把整个VLC Surface复制成Bitmap，如果竖屏铺满且超过2,073,600像素，先`sqrt`缩小整帧；然后在`analyze`中调用`C110ZoomFrames.cropPreviewBitmap`按中心放大/竖屏比例再裁剪，JPEG质量90。这条链路可能让小字在取帧前不必要地丢失像素。视频本身RTSP压缩损失不能靠截图修复。
- **v1.0.87精确修改**：`C110ZoomFrames.visiblePreviewRegion`复用现有`C110ZoomGeometry.centered`和`centeredCover`与UI`C110CameraActivity.applyZoomPreview`的中心放大/竖屏填充规则。`C110FrameReader.tap`先计算底层SurfaceView的可见源Rect，检查不越界，然后用API26+`PixelCopy.request(video,sourceRect,destinationBitmap,callback,main)`**直接拷贝该ROI**；目标Bitmap宽高等于Rect宽高，即不创建整帧临时缩图、不限2MP、不第二次Bitmap裁剪或额外插值。保持原最终ROI≤9,000,000像素安全上限，失败不使用旧图。成功仅`Bitmap.compress(JPEG,100,...)`编码一次，沿原`recognizeJpeg`交给云端/任务/模型/TTS，JPEG本质仍是有损格式，不能宣称严格逐像素无损；未变更云端`data:image/jpeg` MIME或引入未经验证的PNG上传。JPEG质量100可能导致更大上传字节、耗时变化，必须真机观察。
- **新增可核验诊断**：`RuntimeDiagnostics.begin("c110_preview")`记录`frame.surface_width_px/surface_height_px`,`frame.crop_left_px/crop_top_px`,`frame.visible_width_px/visible_height_px`,`frame.pixel_copy_status`,`jpeg.encode_ms/width_px/height_px/bytes`,`cloud.upload_ms/network_ms/first_text_ms/first_speakable_ms`和`capture.total_ms`。所有记录都只含匿名数字与阶段，最近50次或7天、单会话24条限制不变。用于检查“预览可见区域=上云JPEG区域”，没有按默认设置伪造尺寸。
- **改动范围/保护基线**：与v1.0.86比仅7文件：`C110FrameReader.java`,`C110ZoomFrames.java`,`C110CameraActivity.java`（仅注释更新）、`cloud-vision-app/app/build.gradle`,`cloud-vision-app/tests/C110ZoomGeometryTest.java`,新增`cloud-vision-app/docs/C110_VISIBLE_PIXELCOPY_V0187.md`和`.github/workflows/c110-visible-pixelcopy-v0187.yml`。**未改**`C110WifiBootstrap`自动连Wi-Fi、`C110WifiCamera`1001硬件快门、`C110QuickShotController`、`InternalCameraController`、`MainActivity`、`CloudVisionClient`、`CaptureDingFeedback`叮音、`C110ZoomGeometry`原算法、LibVLC3.6.5、云端模型/任务/TTS、USB和其UVC补丁。
- **测试/发布**：Actions run `45333611631` **completed/success**。严格7文件diff测试通过，检查直接`PixelCopy.request(SurfaceView,Rect,Bitmap)`、Bitmap与Rect像素1:1、无2MP缩图/二次裁剪、JPEG100仅一次、诊断/云端原字节转发、C110和手机关键功能未改。扩展`C110ZoomGeometryTest`覆盖1920x1080/2560x1440/3840x2160和1x/2x/4x、竖屏9:16/16:9以及其它viewport裁剪边界，原有纯Java供应商/模型/任务/音频/Wi-Fi/上传档位、固定UVC补丁、完整Gradle ARM64 APK构建、签包结构、唯一Home launcher、LibVLC JNI、音效资源、SHA256、Artifact及GitHub prerelease均通过。最终代码HEAD、Workflow、Release target均`6925631076faf82e978dff208fe98817b7842bc9`。
- **安装**：v1.0.87，versionCode96，versionName `1.0.87-c110-visible-pixelcopy`，原Debug包`com.vobich.bluestar.c110plusquick159`不变，可覆盖安装v1.0.86，切勿卸载以免丢云端密钥和C110配对。APK `BlueStar_Guide_C110_Visible_Preview_1.0.87.apk` 53,550,579字节。官方下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-visible-preview-20261124-v0187/BlueStar_Guide_C110_Visible_Preview_1.0.87.apk ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45333611631 。
- **必须真机验收**：在C110视频页面使用1倍/2倍/4倍以及竖屏铺满点击画面；先看是否仍有画面/无黑屏和叮音、云端识别/TTS正常，再在诊断中核`c110_preview`的source/crop/visible/jpeg宽高、bytes、上传ms/总ms。重点验证手机屏幕可见内容是否与上云区域一致以及小字识别是否提升；CI无法用实际vivo/VLC/相机RTSP证明此点。若画面不对应，恢复基线并处理Surface实际图像与View坐标映射，而不盲目继续放大。
 
---

# 2026-11-24｜蓝星向导v1.0.86｜手机真实JPEG与C110预览PixelCopy提示音，双来源真实图像尺寸/耗时、匿名诊断：CI通过已发布，真机声音待验

- **授权与真机症状**：用户在v1.0.85真机点击左侧“手机高清拍照”，看到“正在拍照→识别内容与描述”，但**没有听到原本的金属叮**；此外独立C110实时预览页点击屏幕快速取帧也**没有叮**。运行诊断只有`usb.pause / usb.raw_age_ms=-1 / usb.view_age_ms=-1`，无法判断真实物理拍照、实际JPEG上云尺寸和耗时。用户明确要求底部去掉“已识别：朗读文字，取帧到识别1086ms”等无用描述，只显示**实际拍摄分辨率、云端上传JPEG分辨率、拍照耗时、识别耗时**；没采集的值显示“待测”。用户明确授权“开始修复这些bug，逐项检查成功再发布安装包”。
- **唯一起点/新分支/HEAD**：先读取main:PROJECT_HANDOFF.md与已发布v1.0.85源码；基于成功发布v1.0.85 commit `d27b336e4ec1d37270fa18d48a9412790a5fbd95` 创建独立分支`exp/quick-photo-ding-measurements-v0186-20261124`，不碰main生产源码。修复提交`83fe51779857725efd158a001c1dc595e58e76ef`；CI与发行提交`7f19414fb9c5b290d18428055dd7f767b606d4cf`，已确认分支HEAD==Actions HEAD==Release target。versionCode95，versionName`1.0.86-quick-photo-ding-metrics`，仍是`com.vobich.bluestar.c110plusquick159`唯一debug应用和`HomeActivity`唯一Launcher。
- **两条缺少提示音的实际原因与修复**：
  1. `QuickPhotoActivity`此前手机`InternalCameraController.requestStillPhoto()`成功得到真实Camera2 JPEG时没有执行提示音；现在只在通过`onPhoneJpeg`真实JPEG成功回调后调用原有`CaptureDingFeedback.photoSucceeded()`一次，失败、进入页面、没有拍照均不会响。仍使用原金属`c_metal_clack_ding.mp3`、偏好键`shutter_feedback_sound`、`SoundPool`，与用户已设音效一致。不改真实~8MP JPEG采集、`PhoneStillJpeg.prepare`及1440P上传偏好。
  2. `C110CameraActivity`原本只有1001高清实体快门完成会调用`captureDing.photoSucceeded()`，**点击实时VLC画面`PixelCopy`成功取帧则完全没有调用提示音**。现在给`C110FrameReader`注入仅此页面使用的`setFrameCaptureFeedback`，**只在`PixelCopy.SUCCESS`、token仍有效且确实复制到新画面后**调用一次已有叮声；取帧失败、画面未就绪、旧token以及小于350ms防误触不会响。原实体1001高清快门叮声保持不变，未改变RTSP、LibVLC3.6.5及OEM9011硬件静音。
- **双来源可核实底栏与运行诊断**：`QuickPhotoActivity`仅保留一块简洁测量显示：来源、实际拍摄宽高、发送云端的准备完成JPEG宽高、拍照耗时ms、识别耗时ms；未测到时显示“待测”。必要失败仍显示简短错误。手机实际尺寸直接来自真实`InternalCameraController.StillListener.onJpeg(w,h)`，拍照耗时从手动点击到该JPEG回调的`SystemClock.elapsedRealtime()`，`RuntimeDiagnostics.begin("phone_quick")`写入`camera.raw_width_px/raw_height_px/phone_still_ms`；然后`PhoneStillJpeg.prepare`完成后把**最终交给CloudVisionClient的JPEG实际宽高/字节数**写入同一诊断`jpeg.width_px/height_px/bytes`。手机/相机的云端识别耗时取`CloudVisionClient.Result.networkMs`（云端网络调用时长，不能表示扬声器已经出声），附带`cloud.upload_ms/cloud.network_ms/cloud.first_text_ms/cloud.first_speakable_ms/capture.total_ms`。C110右按钮本身沿用原`C110WifiCamera.shootAfterLivePreview`原厂1001，真实照片源宽高与`Photo.totalMs`经`C110QuickShotController`新重载交给`C110FrameReader`写`c110_photo`会话`c110.photo_total_ms`；其上云JPEG实际宽高、字节数仍来自原`C110ZoomFrames.limitForCloud`。新`PhotoMetricsListener`仅让快速拍照页面选择订阅，不改其他现有预览、书页阅读或设置页显示。云端上传尺寸是最终上云JPEG的准备尺寸，不代表网络请求失败时服务器已经收到照片；云端结果成功时`networkMs`回填。
- **范围与安全边界**：与v1.0.85对比仅**7文件**：改`QuickPhotoActivity.java`、`C110FrameReader.java`、`C110QuickShotController.java`、`C110CameraActivity.java`、`cloud-vision-app/app/build.gradle`；新增`cloud-vision-app/docs/QUICK_PHOTO_DING_METRICS_V0186.md`和`.github/workflows/quick-photo-ding-metrics-v0186.yml`。**未修改**已经真机验证成功的C110WifiBootstrap自动连接、原C110WifiCamera/1001硬件快门、原`CaptureDingFeedback`音频资源、`InternalCameraController`物理JPEG、`PhoneStillJpeg`缩放、模型/任务/USB/云端API/TTS语音逻辑、4列任务桌面和原来的快速拍照页面布局。
- **自动化检验与发布**：Actions`45321699775`已经`completed/success`。严格7文件白名单对照v1.0.85，检查JPEG成功后叮声、PixelCopy.SUCCESS后叮声、C1101001原版不受影响、真实宽高从物理JPEG/真实上云byte[]传递、实测CloudVisionClient networkMs回填、诊断新phone_quick和旧c110_photo、v1.0.84已测WiFi自动连接代码保持不变；已有供应商、任务、模型、上传档位、相机和Wi-Fi策略Java回归、固定UVC补丁、ARM64 Gradle完整构建、APK包名/唯一HomeActivity启动/LibVLC库+UVC库+声音资源+SHA256、Artifact与GitHub prerelease全成功。Release tag`bluestar-quick-photo-ding-metrics-20261124-v0186`；APK`BlueStar_Guide_Quick_Photo_Ding_Metrics_1.0.86.apk`，53,550,575字节；直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-quick-photo-ding-metrics-20261124-v0186/BlueStar_Guide_Quick_Photo_Ding_Metrics_1.0.86.apk ；GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45321699775 。
- **真机验收不能跳过**：覆盖安装v1.0.86（勿卸载，以免密钥和已配对WiFi丢失），首先确认设置里相机拍照反馈音打开、手机能播放系统提示音。测试(1) QuickPhoto手机高清拍成功一次能听到金属叮；(2) C110实时预览点视频快捷取帧成功能听叮；(3) C110原厂1001仍只有一次叮；(4) 双按钮底栏真实`raw_width_px/raw_height_px`、`jpeg.width_px/height_px/bytes`和时长与导出匿名诊断一致，改原图/1440P分别测试；(5)任务选择4列、OCR/TTS、云端服务、C110 WiFi自动连接均正常。CI能证实代码调用、资源和编译，不等于设备扬声器实际出声。若问题持续，请给v1.0.86日志和底栏截图，按真实测量定位声音路由或个别机型SoundPool。
 
---

# 2026-11-24｜蓝星向导 v1.0.85：快速拍照双来源、原四列任务桌面及Wi-Fi页面精简；CI发布成功、真机待验

- **授权与范围**：用户确认方案A的12项更新：删除首页多余的“运动相机 Wi-Fi”行与首页长驻网络状态文字；相机设置中仅保留一个“连接相机 Wi-Fi”入口，原独立“搜索/更换相机”并入该入口；自动记住C110 Wi-Fi、下次打开自动连接**保留此前真机验证成功的v1.0.84实现**。首页“快速拍照”改为点击进入独立页面，顶部当前任务/选择任务，中间左手机高清拍照和右C110高清拍照两大按钮，下方简明状态；移除像素说明和任务点击后的提示文字。点击“选择任务”进入原`TaskDesktopActivity`每行四图标桌面，维持长按编辑、拖动排序、分页和个性化，单击图标选中后`finish()`返回快速拍照页面。页面打开**不自动启动手机摄像头和不自动拍照**；仅点击按钮触发一次相应真实高清拍照，设置过的分辨率/模型/语音/任务不重置。
- **基线/实验分支**：严格从已经CI成功且用户真机确认“可以自动连接了”的v1.0.84 `abcc1e13b0a11236d0fe3c9338e55b645ca7854e` 作为新实验分支 `exp/quick-photo-two-sources-ui-20261124` 起点（不从main过时工程派生）。最终HEAD `d27b336e4ec1d37270fa18d48a9412790a5fbd95`，由2次提交组成：功能`8cd9b5f5c21d571aad65f9c9fc6b08e97699c139`，CI `d27b336e4ec1d37270fa18d48a9412790a5fbd95`。主分支未合并实验代码。versionCode94 versionName `1.0.85-quick-photo-two-sources`，原包名`com.vobich.bluestar.c110plusquick159`和唯一`HomeActivity` Launcher不变，覆盖安装不应丢失已有密钥及Wi-Fi记录。
- **页面与任务**：新增独立`QuickPhotoActivity.java`和Manifest非导出单Activity，首页“快速拍照”点击只跳转此页；保留原HomeActivity外部`ACTION_QUICK_SHOT`明确请求的一次C110原厂快门（兼容旧外部快速启动）。快速拍照页采用单一状态标签、左右大拍照按钮和当前任务显示，任务页面完全沿用既有四列`TaskDesktopActivity`（原页仅将无障碍返回标签从“返回主页”纠正为“返回上一页”），任务成功选择后Android`finish()`自动返回发起任务选择的快速拍照页。任务配置与当前任务Preference均是原`TaskCatalog`，不新建并行任务系统。
- **手机高清真实相机**：左按钮独立实例化原`InternalCameraController`（Camera2真正`TEMPLATE_STILL_CAPTURE`、原生最接近约8MP JPEG，非预览截图），页面刚进入时只挂载微型`TextureView`并**不启动Camera2**，用户点击后才`phone.start(front,true)`并等待相机真实预览首帧及`requestStillPhoto()`一次，收到回调立即关闭摄像头；JPEG准备在单线程`imageWorker`处理，完全复用原`PhoneStillJpeg.prepare`执行原`phone_hd_upload_mode_trial_v1`原图/1440P/1080P设置（默认1440P）、现有旋转/镜像选项，之后调用复用`C110FrameReader`的`recognizePreparedPhoneJpeg`以继续一致的当前任务、云端OCR/视觉模型、过滤及TTS。该Reader增设手机云端网络选择：优先具有VALIDATED互联网的普通Wi-Fi，其次移动数据，排除已连接的C110本地热点；C110拍照仍旧严格`requireCellular()`。
- **C110原厂单张**：右按钮直接复用未经修改的`C110QuickShotController`（1001真实快门，C110原始JPEG下载、按当前`C110PhotoUploadPolicy`选择原图或1440P、原有模型/TTS），不会重新开发RTSP、灯控、提示音或本地相机协议。切换来源停止原先朗读/识别任务，用户下一张可以取消上一张HTTP识别，但不在尚未完成真实JPEG取图时叠加同来源快门。
- **Wi-Fi 精简而非重做连接**：HomeActivity仍在`onCreate`构建原v1.0.84`C110WifiBootstrap`单实例并在`onResume`执行`startAtLaunch()`，保留已验证自动连接；仅从`render()`删除独立“运动相机Wi-Fi”行和长驻状态文字。设置`BlueStarSettingsScreen`删除独立“搜索/更换运动相机”行；统一“连接相机 Wi-Fi”若无已记住SSID直接调用真实扫描，有记录则显示“连接已记住的相机”或“重新搜索相机 Wi-Fi”两项，未修改`C110WifiBootstrap.java`任何网络或凭据持久化逻辑。
- **差异与检查**：与v1.0.84 GitHub Compare严格**只有10个文件变化**：修改`HomeActivity.java`、`MainActivity.java`、`BlueStarSettingsScreen.java`、`TaskDesktopActivity.java`、`C110FrameReader.java`、`AndroidManifest.xml`、`app/build.gradle`；新增`QuickPhotoActivity.java`、`docs/QUICK_PHOTO_TWO_SOURCES_V0185.md`、`.github/workflows/quick-photo-two-sources-v0185.yml`。**未修改**用户真机验证成功的`C110WifiBootstrap`、`C110WifiCamera`、`C110QuickShotController`、`InternalCameraController`、`PhoneStillJpeg`、`PhoneStillUploadPolicy`、`TaskCatalog`、`C110PhotoUploadPolicy`、`C110CameraActivity`、LibVLC/UVC源、模型配置/密钥等文件。
- **构建发布验证**：GitHub Actions `45319472998` `completed/success`。源码白名单范围、原有Java回归（供应商/服务商/任务/手机JPEG缩放/C110原图回退/WiFi自动连接策略）、固定USB补丁、Gradle Android ARM64完整编译、APK包名/唯一启动Home/LibVLC原生动态库/提示音/签名文件SHA256检查、Actions Artifact和GitHub prerelease全部通过。GitHub分支HEAD、CI SHA和Release target均是`d27b336e4ec1d37270fa18d48a9412790a5fbd95`。发布tag `bluestar-quick-photo-two-sources-20261124-v0185`；APK `BlueStar_Guide_Quick_Photo_Two_Sources_1.0.85.apk` 大小53,550,567字节；直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-quick-photo-two-sources-20261124-v0185/BlueStar_Guide_Quick_Photo_Two_Sources_1.0.85.apk；Actions：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45319472998 。
- **真机需验证，不能宣称已通过**：用户在vivo X200 Pro上覆盖安装（不可卸载导致Keystore密钥丢失），实际检查打开蓝星向导时C110自动Wi-Fi是否仍连接，首页多余行是否消失；选择任务弹原有四列桌面并选中回快速拍照；打开页面时手机Camera2不得启动（Android系统相机使用指示灯不可亮），左按钮一次拍真800万JPEG→按原存储偏好1440P上传并正确语音朗读，右按钮C1101001原厂高清取图→按原图/1440P设置识别朗读；拍完仍留在两个按钮页面；相机设置单WiFi入口有旧热点或重新搜索；C110实时预览、USB、主页朗读及手机原有预览页完全正常。尤其要看微小`TextureView`在vivo系统上是否可靠回调预览首帧、C110与手机切换的TTS是否真的出声；CI不能替代硬件验收。若发现问题仅修这条路径，不推翻v1.0.84自动连接稳定代码。

---

# 2026-11-24｜蓝星向导 v1.0.84｜C110相机Wi-Fi首次搜索记住、首页自动连接：CI全绿、APK已发布，真机待验

- **本轮用户明确授权**：用户先在vivo X200 Pro Android16实机试过家用WiFi+相机WiFi双WLAN加速，原厂SJCAM App提示`C110_2e53 相机被其他设备连接，请重试`，因此放弃双WiFi并发和WiFi上云方案。最终要求：在蓝星向导内搜索实际相机WiFi，第一次选择SSID、输入一次密码、验证后记住，下次相机开机并打开App时自动连接；支持立即连接、重新扫描更换相机。用户随后说“可以就这样，现在就开始修改”。
- **原仓/基线/实HEAD**：在唯一原仓`vobichngan285-collab/kanzi-assistant`先读取`main:PROJECT_HANDOFF.md`及v1.0.83最新发布和连接代码；以已发布、手机真8MP→1440P稳定回退且C110原图/1440P开关完整的v1.0.83 SHA `3bb026880e303a1451f2b4906b858117a4035702`新建**独立实验分支**`exp/c110-wifi-pair-autoconnect-20261124`。最终HEAD `abcc1e13b0a11236d0fe3c9338e55b645ca7854e`，由2次提交组成（首个`82d01458be866e3b36d9130f0cb6923dec0fc8e8`），主分支未合并实验代码。versionCode 93, versionName `1.0.84-c110-wifi-pair-auto`；debug唯一包名`com.vobich.bluestar.c110plusquick159`及`HomeActivity`唯一launcher未变。
- **首次手动搜索/配对**：`C110WifiBootstrap`保留原版原厂式`WifiManager.startScan/getScanResults`、Android位置授权与NEARBY_WIFI_DEVICES、`WifiNetworkSpecifier`精确SSID/WPA2、使用`Network.openConnection`访问相机`192.168.1.254/?custom=1&cmd=3012`。新相机SSID/密码/BSSID先只存在内存候选，**仅在3012响应HTTP200、Status=0、设备标识为受支持标准C110时**才覆盖`SharedPreferences c110_selected_wifi_v1`保存SSID/密码/BSSID，并将“下次自动连接”默认为开启。输入错密码、取消连接、错设备或未验证绝不会覆盖旧成功配对；新相机首次密码不再胡乱预填`12345678`。已有旧版保存的有效SSID/密码继续继承。
- **自动连接/手动重选**：`HomeActivity`在`onCreate`实例化唯一长寿命`C110WifiBootstrap`作为共享网络回调的owner，正常App`onResume`有已记住相机时调用`startAtLaunch()`自动发起一次Android连接申请，不会没有配对就弹出网络框、更不会自动触发1001快门。首页增加一行“运动相机Wi-Fi”入口可直接搜索或连接；设置→相机→C110运动相机中增加“立即连接相机Wi-Fi”“搜索/更换运动相机”和“下次打开自动连接”开关；显式搜索在已经连着旧相机时也可以进行。自动重连只以记住的**精确SSID**申请，**不再绑定可能过期的BSSID**；手动扫描新SSID时可用当次真实BSSID。自动失败/断线不会后台无休止重试，下一次进入首页可重试；手动连接也能恢复。
- **多页面统一**：此前MainActivity临时设置页、独立C110CameraActivity和Home快捷拍照可能各自发起`NetworkCallback`请求，意外撤销相互WiFi连接。v1.0.84让Home持有一个callback，其它页面通过`C110WifiBootstrap.requestFromCameraPage/chooseFromCameraPage`借用；`MainActivity`中设置页的扫描权限/附近WiFi授权回调委托Home持有者，状态提示同步回当前设置页；Home销毁时释放request。未修改C110CameraActivity/C110QuickShotController原厂真实快门/预览代码。自动连接只在Home被打开后执行，且由Android决定是否弹出WiFi授权确认；**不能承诺无条件完全静默切换**。
- **网络策略**：用户放弃双WLAN，不再尝试家用WiFi上传千问；相机本地WiFi只负责下载原图/RTSP，云端`C110WifiCamera.requireCellular()`仍只选移动数据，**用户需开启手机移动数据进行云端OCR**。移除此前仅vivo执行的`WifiManager.disconnect()`，避免在无法连接相机时强制断开家庭WiFi；系统如何从家庭WiFi切换至相机WiFi仍依Android权限、系统行为及用户已关闭的双WLAN加速。用户若要强制单WLAN，必要时仍可手动用系统WiFi面板切换。
- **严格对照范围**：GitHub Compare以v1.0.83 `3bb...`为基线，**恰好9个文件变化**：修改`C110WifiBootstrap.java`、`HomeActivity.java`、`MainActivity.java`、`BlueStarSettingsScreen.java`、`app/build.gradle`；新增`C110WifiPairingPolicy.java`、`tests/C110WifiPairingPolicyTest.java`、`docs/C110_WIFI_PAIR_AUTO_20261124.md`、`.github/workflows/c110-wifi-pair-auto-20261124.yml`。`C110WifiCamera.java`、`C110CameraActivity.java`、`C110QuickShotController.java`、`C110FrameReader.java`、`C110ZoomFrames.java`、`CloudVisionClient.java`、`InternalCameraController.java`、`PhoneStillJpeg.java`、USB、TTS、OEM机身灯音与原图/1440P上传逻辑全部无差异。原主分支交接完整保留。
- **构建、验收现状**：GitHub Actions run `45232995994` **completed/success**，严格9文件scope检查、既有供应商/云端/任务/C110/手机图片上传单元回归、新WiFi配对纯Java测试（自动开关、只尝试一次、无SSID不自动、错误设备不覆盖旧配对、再次连接不带旧BSSID等）、固定USB UVC补丁、ARM64完整Gradle编译、debug包名/唯一启动Activity/LibVLC3.6.5/native库/音频资源/SHA256检查、Artifact和Github prerelease全部成功。分支HEAD/CI HEAD/Release target均为 `abcc1e13b0a11236d0fe3c9338e55b645ca7854e`。发布tag `bluestar-c110-wifi-pair-auto-20261124-v0184`；APK `BlueStar_Guide_C110_Wifi_AutoConnect_1.0.84.apk` 大小53,534,159字节；直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-wifi-pair-auto-20261124-v0184/BlueStar_Guide_C110_Wifi_AutoConnect_1.0.84.apk ；Actions：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45232995994 。
- **待真机逐项验证**：vivo X200 Pro开启移动数据、关闭双WLAN加速、打开标准C110热点（用户截图`C110_2e53`），首页点“运动相机 Wi-Fi”→真实扫描→输入实际密码并允许Android系统授权；必须实际显示3012验证并“记住”才能判定配对成功。然后完全退出并重新启动相机和蓝星向导，确认自动尝试C110；Android若仍弹用户授权框则按系统规定确认，不能当作软件私自取消；检查首页快捷拍和独立C110预览页都能连续操作，不出现多NetworkCallback竞争与断线。设置中“搜索/更换相机”选其他热点时核实旧配对在认证失败后仍可使用。手机近800万真实拍照→1440P、C110高清原图/1440P上传、OCR/TTS、USB保持原有表现。测试结束提供连接状态和诊断；**目前仅编译与CI通过，尚不能宣称真机自动配对/相机数据已验证成功**。覆盖安装debug包，不直接卸载导致Android Keystore密钥丢失。

---

# 2026-11-24｜v1.0.83 手机真实800万→1440P稳定回归；C110/C110+上传原图／1440P可手动开关：CI成功、APK已发布、真机待验

- **用户授权和动机**：原生300多万真实JPEG实验（v1.0.82）被用户报告容易数字乱读；用户在相同v1.0.82中重新实测手机真实约775万拍照后等比缩到1440P上云，识别“读得还不错”，最新一次9次手机`camera.phone_native_mode=0`、`camera.phone_hd_upload_mode=1`，`raw=3712×2088`，`jpeg=1440×2560`，TTS首次音频回调平均约1.45秒；音频回调并不等于扬声器真出声。用户明确授权：**全部清除原生300多万选项及代码、手机恢复前一稳定版1440P上传、运动相机保留单独的手动启用1440P且可以彻底恢复默认不受限原图上传**。
- **原始基线**：唯一仓库`vobichngan285-collab/kanzi-assistant`，从v1.0.81已发布源码 `3273c779db21ac2d2647efe335f7bb43385ca843` 新建`exp/phone1440-c110-original-toggle-20261124`，不是在v1.0.82的3MP实验代码上继续打补丁。实验正式commit `3bb026880e303a1451f2b4906b858117a4035702`；`versionCode=92`；`versionName=1.0.83-phone1440-c110-original-toggle`；debug应用包名`com.vobich.bluestar.c110plusquick159`不变，唯一`HomeActivity`启动入口不变。主分支未合并实验代码。
- **手机已真实回滚**：v1.0.81的`InternalCameraController.java`、`PhoneStillJpeg.java`、`PhoneStillUploadPolicy.java`与GitHub基线**完全相同**，继续真`TEMPLATE_STILL_CAPTURE`并选原生近8MP JPEG；没有v1.0.82新加的`PhoneNativeStillPolicy.java`、原生300～400万相机模式入口、选档调用、`camera.phone_native_mode`日志、相关Java单测。旧版本历史保存的3MP偏好即使还在`SharedPreferences`也**没有任何业务代码读取**，无法改变相机档位。手机原有高清上传原图/1440P/1080P三档保留，已有用户配置原样沿用；新安装无偏好值时手机高清上传**默认1440P**。手机快速预览帧模式默认仍为快速档，用户可以进入设置切换真高清。
- **C110独立恢复开关**：“设置→相机→C110运动相机→运动相机高清上传”，原图（默认，**不限像素、不新增解码或JPEG重编码**）／1440P（仅用户手动开启，真实1001快门、Wi-Fi原图下载完成后、已有变焦/取景ROI处理后把云端JPEG等比缩小至不超2560×1440约369万）。设置值持久化于`C110PhotoUploadPolicy.PREF`。下一张相机真高清在改回原图时，正常1×且无其它取景裁剪的情况下，上传与相机JPEG原始`byte[]`是**同一个对象和字节内容**，完全不受1440P压缩限制；切换这个设置不会向相机发送任何硬件命令。既有手动变焦ROI裁剪依然生效，不能混同为云端缩图。独立设置统一影响`MainActivity` C110真实拍照、`C110FrameReader`被首页C110快捷拍与C110相机预览页真高清调用的拍照识别；C110实时VLC画面点击PixelCopy、USB、手机快速取帧不受影响。相机原厂3012、3016、3001、2015、1001、Wi-Fi绑定/移动网络Cloud区分、RTSP/LibVLC3.6.5、声音9011/灯光9038、快门锁和连续任务取消、所有模型/密钥/Base64/HTTP/TTS均未修改。
- **严格9文件diff**：修改`MainActivity.java`（仅C110开关/缩图/日志＋手机新安装1440P默认）、`BlueStarSettingsScreen.java`（C110两选项、手机1440P说明）、`C110FrameReader.java`（首页/C110页真实JPEG共用设置与数值日志）、`C110ZoomFrames.java`（JPEG仅云端缩放）、`app/build.gradle`（版本）；新建`C110PhotoUploadPolicy.java`、`tests/C110PhotoUploadPolicyTest.java`、`docs/PHONE_1440_RESTORE_C110_ORIGINAL_TOGGLE_20261124.md`、`.github/workflows/phone1440-c110-original-toggle-20261124.yml`。对v1.0.81基线的GitHub Compare确认**恰好9文件**；未改变本机Camera2/手机JPEG/GPU/USB/UVC源码、`CloudVisionClient`、C110 OEM`C110WifiCamera`、`C110CameraActivity`、`C110QuickShotController`。不要重新引入3MP控制分支。
- **CI完成并发布**：GitHub Actions run `45222420207` **completed/success**，严格9文件范围、手机3MP功能不存在、真实Camera2仍为8MP，原v1.0.81手机上传三档Java回归、C110原图↔1440P切换恢复纯Java测试、原有供应商/任务/C110回归、固定UVC补丁、ARM64 Android全量编译、APK实际包名/唯一HomeActivity/LibVLC和UVCCamera native so/提示音/SHA256验证、Artifact上传、GitHub Release发布均通过。CI HEAD、分支HEAD、Release target均为`3bb026880e303a1451f2b4906b858117a4035702`。发布tag `bluestar-phone1440-c110-original-toggle-20261124-v0183`；APK`BlueStar_Guide_Phone1440_C110_OriginalToggle_1.0.83.apk` 53,534,167 bytes。下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-phone1440-c110-original-toggle-20261124-v0183/BlueStar_Guide_Phone1440_C110_OriginalToggle_1.0.83.apk ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45222420207 。
- **真机仍待测**：手机真实约800万拍照、上传1440P应核实日志`camera.raw_width_px=3712`、`raw_height_px=2088`、`jpeg.width_px=1440`、`jpeg.height_px=2560`、`ocr.speed_mode=2`及小字识别；在相同文字/光照网络下与以前良好结果对比。运动相机先原图拍5次，再手动开启1440P拍5次，**随后手动切回原图再拍2次**，观察`c110.upload_mode=0→1→0`、原生JPEG/云端上传尺寸、相机Wi-Fi下载速度、首字、真耳听首次发声及小字遗漏率；如用户另外开启画面变焦/裁剪，恢复原图意味着取消新增1440P限制，但已存在的ROI裁剪仍会有效。不可以把CI成功当作C110+或C110标准版真机朗读已验收。手机/运动相机同包覆盖安装，签名冲突时不能直接卸载导致Android Keystore中用户云密钥丢失。
- **结论和下一步**：v1.0.83源码/编译/安装包发布已完成；手机方案已回归实测通过的v1.0.81处理逻辑，C110实验选项可完全关闭，但其新逻辑的真机画质与时延**尚未经用户测试**。下一步看C110真机日志和小字效果，决定是否继续保留1440P作为可选功能，不擅自把C110默认为缩图。

---

# 2026-11-24｜v1.0.82 手机真正原生300多万JPEG与C110/C110+原图vs1440P云端上传实验：CI全通过、正式预发布，真机待验收

- **用户目标/授权**：用户已有v1.0.81真机A/B证实手机720P预览明显比775万高清上传原图快，手机真实775万缩到1440P明显提速但还需保护小字细节，问“直接拍约370万会不会和拍800万缩图同样清晰”，并要求“下个版本直接做成300多万像素，运动相机也顺便测试”，先讨论后明确“可以现在开始”。本轮只比较**手机硬件真实Camera2 JPEG选档**和**运动相机真实原图下载后再缩小云端上传**。
- **原仓及基线**：唯一原仓 `vobichngan285-collab/kanzi-assistant`；基于已发布v1.0.81 SHA `3273c779db21ac2d2647efe335f7bb43385ca843`，新分支 `exp/phone-native3mp-c110-upload-20261124`，最新实验HEAD `2a50838f695cc8257b0e8873e9d1ebb4c16ddfad`，`versionCode 91`，`versionName 1.0.82-native3mp-c110-1440`。原调试应用包名 `com.vobich.bluestar.c110plusquick159`、唯一 `HomeActivity` 启动入口都不变。先读取main:PROJECT_HANDOFF.md、原源码与分支，GitHub compare确认实验改动仅12个文件（详见下述），未在main覆盖实验代码。
- **手机改动（真正JPEG，不是假3MP）**：新增`PhoneNativeStillPolicy.java`，`InternalCameraController.start(frontFacing,enableStillJpeg,nativeMode)`在仍然使用`TEMPLATE_STILL_CAPTURE`的基础上，原默认选接近8MP的真实Camera2 JPEG（约775万；原8MP算法评分不变）；用户在设置→相机→“手机原生拍照像素”可选真实**300万（含）～400万（不含）**支持档位，选择评分最接近2560×1440约369万，原画幅偏好16:9。设备如果没有任何真正的300多万`getOutputSizes(ImageFormat.JPEG)`输出就明确报错，不虚假把8MP缩图或预览帧标作“原生370万”。切换该项重新启动**手机Camera2**以分配新的原生JPEG Surface；保持USB/C110隔离，不更改真正快门、对焦、JPEG质量92。旧“手机高清上传分辨率”3档原图8MP/1440P/1080P原样保留，用于实验公平比较：A真8MP拍→缩到1440P上云 vs B直接真3～4MP拍→最多1440P上云。新数值日志 `camera.phone_native_mode` =0（真8MP）或1（真300多万），`camera.raw_width_px/raw_height_px`依旧为Camera2真尺寸；`jpeg.width_px/height_px/bytes`为上传图。
- **运动相机改动（上传处理独立）**：新增`C110PhotoUploadPolicy.java`、`C110ZoomFrames.limitForCloud(...)`，设置→相机→C110运动相机→“运动相机高清上传”原图（默认）/1440P（至多2560×1440=3686400px、等比缩小、不放大）。只对**1001真实快门已经成功＋通过Wi-Fi下载完成的JPEG**生效；现有缩放/取景ROI先完成，再可选对云端JPEG缩放。原图模式仍原封不动透传byte[]，1440P模式按需`BitmapFactory.inSampleSize=2`避免完整20MP内存分配、JPEG质量90，失败不偷偷改传原图。分别在`MainActivity` C110真实拍照识别通路和`C110FrameReader`真实拍照识别通路接入；后者覆盖Home快捷拍和独立C110预览页“高清拍照”。**C110实时预览PixelCopy、USB、手机快速720P和其他图片通路都不受影响。C110+已验证的原厂3012/3016/3001/2015/1001、Wi-Fi/RTSP/LibVLC/机身灯音控制、快门busy锁及现有网络/取消机制均不更改。**没有核实新购普通C110的真实固件兼容性，不能把C110+结论当作普通C110真机验证。
- **数值日志与比较边界**：MainActivity C110拍照仍保留 `c110.photo_total_ms`、`c110.native_width_px/native_height_px`及旧JPEG/cloud记录，新增`c110.upload_mode`。独立Home快捷/C110预览页由C110FrameReader在用户拍的真原图进入识别后生成`c110_photo`会话，记录原生像素、上传档、JPEG尺寸/体积/预处理时间和部分云端计时，**该入口`capture.total_ms`仅从原图下载完成后起算，不包括相机1001快门和Wi-Fi获取时间，不得与MainActivity完整拍照计时直接横比**。仅记录数值，不保存照片、文字、音频、密钥；单个会话最多24条，频繁切换场景可能截断。首次TTS音频回调不代表人耳真正听到声音。
- **严格12文件scope**：`MainActivity.java`、`InternalCameraController.java`、`BlueStarSettingsScreen.java`、`C110FrameReader.java`、`C110ZoomFrames.java`、新增`PhoneNativeStillPolicy.java`、新增`C110PhotoUploadPolicy.java`、新增`tests/PhoneNativeStillPolicyTest.java`、新增`tests/C110PhotoUploadPolicyTest.java`、`app/build.gradle`、新增`docs/PHONE_NATIVE_3MP_C110_UPLOAD_20261124.md`、新增`.github/workflows/phone-native3mp-c110-upload-20261124.yml`；GitHub compare对照v1.0.81证实恰好12文件。无`CloudVisionClient`/模型/提示词/TTS/USB/C110WifiCamera/C110CameraActivity/QuickShotController原厂代码差异。
- **完整CI、发布回读**：GitHub Actions run `45218330789` completed/success。已逐项通过严格12文件差异与不改OEM/HTTP/TTS断言、现有千问/供应商/语音/C110 Java回归、旧v1.0.81上传三档Java测试、两个新原生像素/C110上传纯Java测试、pinned UVC补丁、ARM64 Android全量Gradle编译、APP包名版本/唯一Launcher/LibVLC3.6.5/USB native库/声音资源/APK ZIP及SHA256；Artifact+GitHub Release发布成功，分支HEAD、工作流SHA、Release tag target全为 `2a50838f695cc8257b0e8873e9d1ebb4c16ddfad`。发布tag `bluestar-native3mp-c110-1440-20261124-v0182`，APK `BlueStar_Guide_Native3MP_C110_1440_1.0.82.apk`，大小53,534,155字节。下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-native3mp-c110-1440-20261124-v0182/BlueStar_Guide_Native3MP_C110_1440_1.0.82.apk 。CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45218330789 。
- **真机仍需执行**：vivo X200 Pro Android16选择“手机拍照模式→高清”后：A手机原生8MP+上云1440P五次，B手机原生300多万+上云1440P五次（若不支持则以错误结果结束该设备的直接370万实验，不要伪称成功）。同画面、光照、距离、网络、千问模型和任务，检查raw/photo JPEG尺寸、上传尺寸、JPEG字节、云端首字/TTS回调、真实首句出声、小字遗漏率和是否尺寸更小却更慢。另以同一个C110+入口分别原图/1440P各拍至少五次，核对相机原JPEG的大小和Wi-Fi photo_total_ms是否不变、上传JPEG变小、云端首字和小字识别精度。C110相机Wi-Fi下载与移动网络上云分开测；缩图本身可能额外耗时，省的网络/模型时间是否抵消需真实日志。旧任务取消导致云端记录缺失的会话勿计入完整云端时间统计。安装debug同包名优先覆盖，**签名冲突时不要直接卸载导致Android Keystore密钥丢失**。
- **结论边界**：代码实现/CI编译/安装包发布已通过，但**没有**用户新机型实际Camera2 3MP档是否存在、设备真实C110+或标准C110联机、首次真发声和识别准确率的现场验收；本版仅独立实验设置，默认仍保留原800万真拍与C110原JPEG，不宣称已经找到最快最佳精度档。

---

# 2026-11-23｜v1.0.81 手机真实约800万拍照＋云端上传分辨率三档实验：CI成功、APK已发布、真机待验收

- **授权与目的**：用户于2026-11-23真机对照确认手机快速720P与手机真实高清约800万有明显速度差：13次快速首个TTS音频回调均值约1574ms、13次高清约2692ms。各自有7/8次完整云端数据时，`cloud.first_text_ms`分别约532ms和2178ms；仍含网络/服务器复杂因素，不可把差额全部算作视觉推理。用户明确表示“可以开始”，授权比较**保持原生800万拍照但缩小上传JPEG**。
- **唯一原仓/完整基线**：`vobichngan285-collab/kanzi-assistant`；从已发布 v1.0.80 分支提交 `ac287edbd352dd090e3e56fa7e8248c080bf4fe2`创建`exp/phone-hd-upload-ab-20261123`，最终实验HEAD `3273c779db21ac2d2647efe335f7bb43385ca843`，`versionCode=90`，`versionName=1.0.81-phone-hd-upload-ab`，唯一Launcher `HomeActivity`，debug包名`com.vobich.bluestar.c110plusquick159`不变。
- **严格8文件变更**：修改`MainActivity.java`（偏好设置与拍照时上传档位快照和仅数值日志）、`BlueStarSettingsScreen.java`（设置→相机三选一）、`PhoneStillJpeg.java`（上传时缩放）、`app/build.gradle`（版本号）；新加`PhoneStillUploadPolicy.java`、`tests/PhoneStillUploadPolicyTest.java`、`docs/PHONE_HD_UPLOAD_AB_20261123.md`与独立 workflow `.github/workflows/phone-hd-upload-ab-20261123.yml`。GitHub compare已确认恰好8文件，无其他源码差异。
- **三档严格限定**：手机Camera2仍按`PhoneStillJpeg.MAX_UPLOAD_PIXELS=8000000`选择原生近8MP的真JPEG，不改变`InternalCameraController`的`TEMPLATE_STILL_CAPTURE`、选择算法和相机拍照。只在手机高清JPEG取得后选择上传图像上限：**0 原图8MP（默认）/1 1440P=2560×1440约369万/2 1080P=1920×1080约207万**；仅等比压小、不放大，不同原生画幅比例不强制拉伸成16:9。`camera.raw_*`仍是原生拍照尺寸，`jpeg.width_px/height_px/bytes`是真正上传值；新日志`camera.phone_hd_upload_mode`=0/1/2。原图默认尽量保留此前JPEG字节，镜像旋转/压缩沿用质量90。选档持久化，不要求摄像头重启。
- **绝不改动**：手机快速预览取帧720P/1080P、USB、C110/C110+原图及OEM命令、LibVLC/原UVC补丁、云端千问/其他模型与提示词、Base64及固定长度HTTP上传、流式SSE、旧请求取消与最新照片优先、TTS、主界面与云API密钥逻辑。UI原先仍写“约1200万”的文字已更正“约800万”。
- **完整CI结果**：workflow run `45211993546` **completed/success**，逐项通过准确8文件范围、现有模型/音频/C110纯Java回归、新增三档分辨率纯Java测试、固定UVC补丁、ARM64完整Gradle编译、APK包名/唯一启动Activity/LibVLC与UVCCamera本地so/提示音素材/ZIP/SHA256校验，Artifact上传与GitHub预发布发布成功。workflow head SHA、实验分支HEAD、tag target均为`3273c779db21ac2d2647efe335f7bb43385ca843`。
- **已发布实验APK**：tag `bluestar-phone-hd-upload-ab-20261123-v0181`，APK `BlueStar_Guide_Phone_HD_Upload_AB_1.0.81.apk`，**53,534,159字节**。直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-phone-hd-upload-ab-20261123-v0181/BlueStar_Guide_Phone_HD_Upload_AB_1.0.81.apk ，CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45211993546 。
- **真机尚未验收**：同一vivo X200 Pro Android16、千问模型/朗读任务、网络、同一段文字/距离/光线，用三档每档至少5次，最好 A/B/C交替。核实真实原始Camera2 JPEG仍`3712×2088`（约775万）、缩图对应约2560×1440/1920×1080（按方向互换）、`jpeg.bytes`、云端`upload_ms/response_headers_ms/first_text_ms/first_speakable_ms`、`tts.first_audio_callback_ms`与真耳听首句时间、小字准确率。中途取消而缺少网络指标的操作不计入云端完成样本。回归快速预览、USB、C110+照片、连续取消旧HTTP/TTS、AI追问及音量键。**回调不代表扬声器发声，CI成功不代表真机速度提升**。若有安装签名冲突，切勿直接卸载导致Android Keystore中密钥丢失。
- **结论及下一步**：已完成实现、编译、打包、源码提交和发布；三档真实速度/识别质量由用户下一次真机日志决定最佳默认设置，**目前未调整默认800万、未更改模型/网络协议，也未宣称1440P一定最快**。

---

# 2026-11-23｜v1.0.80 手机原生约800万像素单项对照实验：CI完成发布，真机首句延迟待验证

- **用户授权与限制**：在v1.0.79流式上传测试后，用户明确要求“800万像素的实验版再来做测试”并确认“可以开始”。本轮**只降低手机高清拍照像素目标**，不降低手机快速720P、不修改云端像素处理上限、不修改模型、提示词、TTS、USB、C110、HTTP流式上传及连续拍照机制。
- **唯一原仓/基线**：`vobichngan285-collab/kanzi-assistant`，从v1.0.79正式发布源码 `bf05a9521f6243c33f440c9cc586f6348c9a72cb` 创建 `exp/phone-hd8mp-trial-20261123`，实验版最终HEAD `ac287edbd352dd090e3e56fa7e8248c080bf4fe2`。版本 `versionCode 89`、`versionName 1.0.80-phone-hd8mp-trial`，debug APK包名仍`com.vobich.bluestar.c110plusquick159`，唯一HomeActivity启动入口不变。
- **最小改动（严格5文件）**：`PhoneStillJpeg.java`将全局唯一用于手机高清原生JPEG选档与实际上传像素上限的`MAX_UPLOAD_PIXELS=12000000L`改为`8000000L`；`InternalCameraController.java`中选择真实Camera2 JPEG尺寸的注释由~12MP改为~8MP，`chooseStillSize`现有选择算法和真实支持尺寸列表**未改**；`cloud-vision-app/app/build.gradle`仅版本号；新增实验说明`cloud-vision-app/docs/PHONE_HD8MP_TRIAL_20261123.md`与独立严格CI workflow `.github/workflows/phone-hd8mp-trial-20261123.yml`。精确diff已由GitHub compare与CI验证，未改`MainActivity`、`CloudVisionClient`、`C110WifiCamera`、`C110FrameReader`、`C110QuickShotController`、`FrameCompressor`、USB/C110原厂协议、模型、TTS及图片上传/压缩算法。
- **重要物理分辨率逻辑**：800万是**上限和原生JPEG选档目标**，不是强制800万的插值。实际所选尺寸由手机Camera2 `getOutputSizes(ImageFormat.JPEG)`返回的支持档位与算法决定；例如已旧版真机验证的`3712×2088=7,750,656`，可能为最佳档，但须以新日志`camera.raw_width_px/raw_height_px`和`jpeg.width_px/height_px`核实。不要把真正没有变的快照或原生尺寸误称“已真机验证800万”。
- **CI记录**：第一次workflow run `45204455600` 在新增静态检查脚本中因`photoCloudExecutor`空格不匹配而失败，尚未执行Android编译；只改**CI断言字符串**，原手机高清实现源码不再修改。第二次 run `45204691881` **completed/success**，通过严格5文件范围与800万目标/固定长度流式上传/旧任务取消/手机与C110硬件安全检查、Java回归、原固定UVC补丁、Gradle完整Android arm64编译、APK真实包名/唯一Launcher/LibVLC3.6.5/UVC native/音效/ZIP/SHA256，Artifact上传及Release发布。分支HEAD、CI head SHA、发布tag target均为`ac287edbd352dd090e3e56fa7e8248c080bf4fe2`。
- **已发布下载**：tag `bluestar-phone-hd8mp-trial-20261123-v0180`，APK `BlueStar_Guide_Phone_HD8MP_1.0.80.apk`（53,534,155 bytes）；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-phone-hd8mp-trial-20261123-v0180/BlueStar_Guide_Phone_HD8MP_1.0.80.apk ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45204691881 。
- **真机待验收**：vivo X200 Pro Android16，固定千问模型/任务、网络和场景，用v1.0.80连续5次高清拍照并比v1.0.79的1200万基线。重点核实`camera.phone_still_jpeg=1`、原始/上传像素、JPEG大小、`camera.phone_still_ms`、`jpeg.encode_ms`、`cloud.first_speakable_ms`、`tts.onstart_ms`、`tts.first_audio_callback_ms`及用户亲耳听到的首句话等待。**TTS回调不是扬声器发声证据**。新旧`cloud.upload_ms`均固定长度模式，仍可能受网络波动影响。记录小字准确率；再验证720P快速、连续新照片取消旧识别、C110原图和USB不回归。若800万无明显首句提速或小字变差，仍留v1.0.79作为回退；安装冲突时不可直接卸载旧版导致Android Keystore密钥丢失。
- **结论边界**：软件修改、CI和APK发布已完成，**真机800万选档、实际首句提速、听觉质量尚未验证**；不得据纯编译认为效果已经提升。后续500万像素、云端上限、其他优化未获授权，不做。

---

# 2026-11-23｜v1.0.79 图片HTTP固定长度流式上传单项实验：CI成功发布，A/B真机待验证

- **明确授权**：用户看到1200万高清拍照时首句话延迟后，确认**先不改像素**，仅把 `HttpURLConnection` 的大图片JSON上传改为固定长度流式发送，保持原有云端识别、TTS和其他功能完全不变，发测试APK用真实首句延迟对照。并非授权800万像素、参数上限、其他优化。
- **唯一原仓与完整基线**：`vobichngan285-collab/kanzi-assistant`，从已发布v1.0.78 `83077e40dd4cbcc8099635a8167a413a17260648` 新建独立分支 `exp/fixed-length-photo-upload-20261123`；最终HEAD `bf05a9521f6243c33f440c9cc586f6348c9a72cb`，`versionCode 88`，`versionName '1.0.79-photo-stream-upload'`；debug安装包ID `com.vobich.bluestar.c110plusquick159` 不变，唯一 HomeActivity Launcher不变。
- **精确6文件改动且比较完成**：只改 `CloudVisionClient.java`、`TargetVisionClient.java`、`LayoutParsingClient.java` 三个图像上传接口、`cloud-vision-app/app/build.gradle`、新增`cloud-vision-app/docs/PHOTO_FIXED_LENGTH_STREAM_UPLOAD_20261123.md`、新增 `.github/workflows/photo-fixed-length-upload-20261123.yml`。未改 `MainActivity.java`、`InternalCameraController.java`、`PhoneStillJpeg.java`、`FrameCompressor.java`、`C110WifiCamera.java`、`C110QuickShotController.java`、`C110FrameReader.java`、`HomeActivity.java`、模型/图片压缩/TTS/USB驱动/LibVLC/云端密钥逻辑。
- **实际代码**：①`CloudVisionClient` 在原 `byte[] request=body.toString().getBytes(StandardCharsets.UTF_8)` 后，仅对`!textOnly`图像请求、在`getOutputStream()`前调用`connection.setFixedLengthStreamingMode(request.length)`，既有`out.write(request)`与SSE响应、旧请求Cancel/断链、超时/HTTP错误处理不变；纯文字追问原路径不变。②目标寻找`TargetVisionClient`和③版面解析`LayoutParsingClient`把原UTF-8 JSON字节数组提前建出，在`getOutputStream()`前设`c.setFixedLengthStreamingMode(request.length)`，仍完整写入原JSON字节，没有改提示词/模型/输出。
- **技术边界**：此处“流式上传”是**已知UTF-8请求体长度的fixed-length streaming**，绕过`HttpURLConnection`自身的完整HTTP request body缓冲；**没有**改变客户端事先生成Base64图片及完整JSON和`byte[]`的事实、没有缩图或压图、没有更改服务器是否缩放图片、也不保证实际首句必定更快。请求上传`cloud.upload_ms`的统计口径可能因原本写入缓冲、现在写入传输层而改变，**不应直接拿该字段对比1.0.78的值**；用户应比较相同模型/照片内容下的`tts.onstart_ms`、`tts.first_audio_callback_ms`（仅回调不等于真正出声）、`cloud.first_speakable_ms`和真实耳朵听到的首句等待。
- **完成CI与Release验证**：Actions run `45201737971` =`completed/success`，分支HEAD、run SHA和GitHub release tag target一致（`bf05a9521f6243c33f440c9cc586f6348c9a72cb`）。通过严格6文件作用域检查、图像请求`setFixedLengthStreamingMode(request.length)`先于`getOutputStream`、保留纯文字旧传输/旧Cancel/SSE、固定1200万像素高精度与原720p、完整原纯Java模型/语音/任务/C110回归、原UVC固定补丁、arm64全量Gradle Debug构建、包名/唯一Launcher/LibVLC3.6.5/UVC native/音效/APK ZIP/SHA256、Artifact与Release发布。
- **已发布**：tag `bluestar-photo-fixed-stream-20261123-v0179`，APK `BlueStar_Guide_Photo_Stream_1.0.79.apk`（53,534,159 bytes），直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-photo-fixed-stream-20261123-v0179/BlueStar_Guide_Photo_Stream_1.0.79.apk ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45201737971 。
- **真机仍待验收**：vivo X200 Pro Android16维持1200万真实高清静拍4096×3072、上传3000×4000，选择相同千问OCR模型和相同文字，v1.0.78、v1.0.79各测至少3～5次；不要拿`upload_ms`新旧直接比较。比按下至第一句真正出声，参考`tts.onstart_ms`和`cloud.first_speakable_ms`，以及是否有HTTP401、失败或明显增时。再检查快速720P、上一张未返回拍第二张、C110原图和自定义云端接口（特别是非2xx/认证重定向兼容）。CI不代表实际网络加速或所有云服务商兼容。如果有兼容问题优先回退v1.0.78，**不要直接卸载旧debug包**导致Keystore中云密钥丢失。
- **本轮没有触及未授权待办**：800万/500万像素、C110+/普通C110机型识别、自动恢复、优化JPEG内存、改变图像视觉Token限额，仍需各自明确授权才可动手。

---

# 2026-11-23｜v1.0.78 两项高优先级首轮优化：最新照片优先+旧云端HTTP取消，CI成功发布，真机待验收

- **明确授权范围**：用户要求“一项一项来”，第一轮**只修两个高优先级问题**：（1）云端处理旧照片时不能拍第二张的忙锁；（2）旧云端请求与拍照/JPEG处理共用单线程、旧网络卡住时挡住新任务。明确不开展其他优化，尤其不改手机1200万像素、云端视觉像素上限、模型、TTS音色、主题UI、USB底层、C110原厂命令、内存优化和自动故障恢复。
- **真实仓库/基线**：唯一原仓 `vobichngan285-collab/kanzi-assistant`，从已发布并已实机验证1200万像素的v1.0.77 SHA `4118b708e17e4fc436f1c03f4b5c515aa792008f` 创建独立分支 `exp/latest-photo-cancel-cloud-20261123`；本轮最终HEAD SHA `83077e40dd4cbcc8099635a8167a413a17260648`，`versionCode 87` / `versionName 1.0.78-latest-photo-cancel`；debug包名仍 `com.vobich.bluestar.c110plusquick159`，启动入口HomeActivity不变。
- **精准10文件范围，GitHub compare已核验**：`MainActivity.java`、`CloudVisionClient.java`、`TargetVisionClient.java`、`LayoutParsingClient.java`、`C110FrameReader.java`、`C110QuickShotController.java`、`C110CameraActivity.java`、`cloud-vision-app/app/build.gradle`、新增`cloud-vision-app/docs/LATEST_PHOTO_CANCEL_20261123.md`、新增`.github/workflows/latest-photo-cancel-20261123.yml`。其余文件字节未变，包括`InternalCameraController.java`、`PhoneStillJpeg.java`、`FrameCompressor.java`、`HomeActivity.java`、`C110WifiCamera.java`、`C110WifiBootstrap.java`、TTS、ASR、USB与JNI/LibVLC。
- **手机/USB/C110 主拍照**：`MainActivity.captureOneFrame`移除云端忙锁；旧任务一旦JPEG已获取允许新照片替换，1500ms防误触改350ms；新照片时旧`requestVersion`失效、停旧TTS、取消旧Future、`CloudVisionClient.Cancellation.cancel`主动断开**其自己的**HTTP连接；`beginCapturedFrame`仅在单线程`encoder`完成JPEG编码后，将识别提交到最多2线程的`photoCloudExecutor`；旧HTTP阻塞不再占用相机/JPEG工作队列。捕获JPEG前保持安全锁：手机静态拍照/快速取帧尚未完成不重复下发；C110独立`c110NativeShutterBusy`确保真快门1001和JPEG下载只串行；在C110机身快门进行中不得取消其拍摄工作。
- **其他C110入口**：主页`C110QuickShotController`拍下一张前可取消`C110FrameReader`旧识别，`shutterPending`依然禁止双快门；独立`C110CameraActivity`按高清拍照先由既有`stop(false)`取消旧Reader并安全释放RTSP后再发真快门；`C110FrameReader`新增`cancelRecognition()`不关闭预览、旧任务标记作废、停TTS与HTTP；`photoCloudIo`有界2线程，JPEG预处理`io`保留原单线程，避免旧网络阻塞新照片识别；原相机1001/网关/VLC逻辑不动。
- **三类图片请求可取消**：`CloudVisionClient.Cancellation`记录单请求连接、提供`attach/check/detach/cancel`，网络长等待期间`disconnect()`＋`Future.cancel(true)`；仅拍照调用传cancel signal，原聊天/书页/音频调用保持旧API。相同signal透传给`TargetVisionClient`和`LayoutParsingClient`，防止非Chat图像通道漏掉；客户端不能保证云服务器已接收请求后不继续计费。旧结果经请求序号过滤，不覆盖新照片/朗读。
- **完整构建/核验**：GitHub Actions run `45197893040` `completed/success`，分支HEAD、CI SHA、Release target精确一致，含严格10文件差异与逻辑边界断言、既有所有模型/voice/任务/C110纯Java回归、固定UVC补丁、Android ARM64完整Gradle debug编译、包名/唯一Launcher/LibVLC3.6.5/UVC native/原提示音/ZIP/SHA256、Artifact上传、Release发布全PASS。
- **已发布APK**：Tag `bluestar-latest-photo-cancel-20261123-v0178`；`BlueStar_Guide_Latest_Photo_1.0.78.apk`（53,534,155 bytes）。直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-latest-photo-cancel-20261123-v0178/BlueStar_Guide_Latest_Photo_1.0.78.apk 。CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45197893040 。
- **真机验收还未完成，不能夸大**：vivo X200 Pro Android16先测试手机高清首张拍完等待云端时再拍不同内容第二张（须非误触、间隔>350ms），确认第二张独立1200万像素、旧文字/TTS不会插入，新请求能在旧网络阻塞时及时开始；快速/USB同理。C110+（用户现有已测试实际设备）真实快门/取图后，分别在首页快捷及独立预览页测试第二张，不得双发1001或误关Wi-Fi；新照片只读新结果。断网及401时不自动重拍/重复计费。运行诊断是回归证据，不含照片/密钥；CI成功不代表硬件通过，也不能保证已提交云端的请求无Token费用。安装debug签名冲突**不可直接卸载原版**，防止Keystore密钥丢失。
- **仍未改动的后续待办**：C110+/普通C110型号识别和原厂协议兼容隐患仍需独立研究；USB与Camera2安全自动恢复、1200万JPEG内存占用优化、云端像素上限调节均不属于本次首轮改动。

---

# 2026-11-23｜v1.0.77 手机高清近1200万像素 + 预览上传画质设置移至相机：CI全部通过，APK发布，待真机验证

- **用户授权范围**：在v1.0.76实机已确认真实Camera2 JPEG 3712×2088（约775万像素）、点击到JPEG仅141～216ms后，用户决定下一版以约1200万像素为高清目标，且提出旧「设置→云端AI→图片分辨率」最多仅1080P、容易误认为限制手机高清。明确授权一起修复，要求保留原快速模式与C110/USB/云AI/TTS。之前“先停”针对上一修改尝试；本轮用户已重新明确授权这两项。
- **仓库与基线**：唯一原仓 `vobichngan285-collab/kanzi-assistant`，从已发布v1.0.76 `2a560da336223bcf4c6da12075382149a1620e31` 新建独立分支 `exp/phone-hd12mp-settings-20261123`，最终HEAD `4118b708e17e4fc436f1c03f4b5c515aa792008f`，`versionCode 86` / `versionName 1.0.77-phone-hd12mp-camera-settings`，debug安装包ID依旧 `com.vobich.bluestar.c110plusquick159`，唯一 HomeActivity Launcher。
- **实际仅6文件修改，GitHub compare已核验**：`InternalCameraController.java`、`PhoneStillJpeg.java`、`BlueStarSettingsScreen.java`、`cloud-vision-app/app/build.gradle`、新增`cloud-vision-app/docs/PHONE_HD12MP_CAMERA_SETTINGS_20261123.md`、新增`.github/workflows/phone-hd12mp-camera-settings-20261123.yml`。未修改`MainActivity.java`、`FrameCompressor.java`、`CloudVisionClient.java`、`HomeActivity.java`、C110识别及拍照、USB驱动、TTS/AI问答/密钥/字体/主页。
- **分辨率方案**：`InternalCameraController.chooseStillSize` 的目标由800万提升到`PhoneStillJpeg.MAX_UPLOAD_PIXELS`，统一常量12000000像素；在设备Camera2真正支持的`ImageFormat.JPEG`尺寸中选最接近1200万的原生输出，略微偏好16:9但不强行放大或固定尺寸。超过1200万才按比例缩小，方向/镜像沿用既有RAM JPEG处理，不写相册。用户实机最终支持的真实尺寸仍待核实，不能保证恰好4000×3000。
- **设置归位而非更改旧快速算法**：原「云端AI→图片分辨率」的自动720P/必要时1080P、固定720P、固定1080P，从`BlueStarSettingsScreen.cloud()`移至`camera()`，显示「手机/USB预览上传」；仅影响手机**快速模式**和USB预览截帧，不限制手机高清真实JPEG或C110 OEM原图。旧`cloud_capture_quality`偏好键及三档行为均保持，升级不重置用户选择。「手机拍照模式」仍快速/高清两种且首次默认快速，高清选项注明约1200万。主页不新增按钮。
- **完整构建证据**：GitHub Actions run `45190939711` 已`completed/success`，任务SHA与分支/发布tag均为`4118b708e17e4fc436f1c03f4b5c515aa792008f`。通过严格6文件静态范围与图像链路校验、既有Qwen/GLM/任务/语音/C110纯Java回归、原UVC补丁、图标素材、全ARM64 Gradle构建、APK包名版本/唯一Launcher/ZIP/LibVLC3.6.5/UVC native/声音资源/SHA256、Artifact上传及GitHub Release全部成功。
- **正式实验包已发布**：Tag `bluestar-phone-hd12mp-camera-20261123-v0177`；APK `BlueStar_Guide_Phone_HD12MP_1.0.77.apk`（53,534,167 bytes）。直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-phone-hd12mp-camera-20261123-v0177/BlueStar_Guide_Phone_HD12MP_1.0.77.apk 。CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45190939711 。
- **真机待验收**：vivo X200 Pro Android16使用已开启高清模式，在「设置→相机」观察手机/USB预览上传三档，云端设置不再显示旧分辨率；拍摄同场景3～5张，日志`camera.phone_still_jpeg=1`、`camera.raw_width_px/raw_height_px`与`jpeg.width_px/height_px`确认尺寸接近1200万，比较此前775万下的拍照毫秒、云端首字、整体时间及小字漏读；验证快速720P与1080P原逻辑、旧偏好值、手机前后置、C110+/普通C110（需独立身份核查）、USB、TTS和AI追问未回归。CI不等于真实像素或扬声器验收。安装如有debug签名冲突**不得直接卸载旧版**，以免丢失 Android Keystore 中密钥。
- **未合并的旧待办**：2026-11-22关于C110+/C110机型标识与协议不统一、上一张云端未返回时连拍锁等讨论仍未改，不能误报已经修复。旧v1.0.76源码和安装包完整保留。

---

# 2026-11-23｜v1.0.76 手机 Camera2 真实高清拍照模式（仅本项）：完整CI成功，已发布，真机待验收

- **明确授权**：用户要求仅实现“手机摄像头拍照模式手动选择：默认原有预览截帧；高清模式用手机原生 Camera2 真正拍照，原JPEG直接接入云端OCR/视觉与现有TTS”，逐项检查构建。不得顺手修C110型号判断、连拍锁、USB、主页UI或离线模型。
- **原仓库与基线**：唯一 `vobichngan285-collab/kanzi-assistant`，从已发布v1.0.75 SHA `be29823b1cc51aa3ccafb25f18f998435cccf24d` 建独立分支 `exp/phone-hd-still-capture-20261123`；最终 SHA `2a560da336223bcf4c6da12075382149a1620e31`，版本 `versionCode 85`/`versionName 1.0.76-phone-hd-still`。原包名debug applicationId `com.vobich.bluestar.c110plusquick159` 与唯一HomeActivity启动入口不变。
- **精确7文件改动且compare已核验**：`MainActivity.java`、`InternalCameraController.java`、新增`PhoneStillJpeg.java`、`BlueStarSettingsScreen.java`、`cloud-vision-app/app/build.gradle`、`cloud-vision-app/docs/PHONE_HD_STILL_20261123.md`、专用CI `.github/workflows/phone-hd-still-20261123.yml`。HomeActivity、C110+/C110协议/设备识别、USB、模型路由、TTS、密钥/布局/主题和其余Java文件未改。
- **功能实现**：设置→相机→手机拍照模式：快速（默认，原Camera2预览YUV取一帧）或高清（Camera2 `TEMPLATE_STILL_CAPTURE`＋单独 JPEG `ImageReader`，约800万像素物理输出，保存偏好 `phone_true_hd_still_v1`；只在高清选择启用JPEG第三Surface），仍使用原云端OCR/视觉、SSE、TTS，不开系统相机、不保存到相册。高清JPEG内存方向/镜像/翻转规范化，超过800万像素按比例压缩；保留C110原生JPEG完全不再压缩。高清超时8秒、取消过时回调且不静默冒充720P；当前旧cloudBusy与误触逻辑保留，非本轮连拍更新。
- **实际CI验证**：GitHub Actions run `45186523945`，`completed/success`，HEAD `2a560da336223bcf4c6da12075382149a1620e31`；严格7文件静态diff／功能保护断言、既有Qwen/GLM/任务/C110 Java回归、UVC补丁/Launcher素材、Android arm64 Gradle完整debug编译、APK ZIP/包名/Version/唯一Launcher/LibVLC3.6.5/UVC native/声音资源/SHA256校验、Artifact上传和GitHub Release全部成功。
- **已发布**：标签 `bluestar-phone-hd-20261123-v0176`，资产 `BlueStar_Guide_Phone_HD_1.0.76.apk`（53,534,151 bytes）。下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-phone-hd-20261123-v0176/BlueStar_Guide_Phone_HD_1.0.76.apk 。CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45186523945 。Tag/Release target/CI HEAD 一致。
- **未完成的真机验证**：vivo X200 Pro Android16 上快速模式应保留原约720×1280及已验证速度；高清模式应在运行诊断出现 `camera.phone_still_jpeg=1` 和显著更大的 `jpeg.width_px/height_px`，通过“上次实际发送给 AI 的 JPEG”核实真实小字细节与方向；重复拍照/前后相机/切模式/旧设置迁移、云端完整朗读和TTS实际扬声器、C110+/普通C110/USB/阅读回归需要用户真机实测。**CI编译通过不等于硬件高分辨率通路已经实机成功。** 遇到debug签名冲突不可自动卸载，防止Keystore密钥丢失。
- **保留此前独立待办**：上方2026-11-22关于C110+/C110识别和不同原厂App的讨论依旧有效，**本次没有修复也不应误报已修复**。

---

# 2026-11-22｜下版待修复（未授权修改代码）：纠正 C110+ / C110 历史机型误标与 3012 身份识别兼容隐患

- **用户最新明确更正（优先于所有旧记录）**：以前实际购买、长期测试并成功运行蓝星向导的是 **SJCAM C110+**，用户曾误认为是普通 C110，导致早期大量口述与交接文档把设备标为“C110 标准版”。过往所有现场成功的 3012→3016→3001/1→2015/1、1001/DCIM JPEG、9011静音、9038灯控等结果应暂归属于**实际设备 C110+**，**不能作为普通 C110 已通过真机验收的证据**。现在用户拟新买/改用的是 **普通 C110**，因为现有 C110+ 镜头视角太广。用户还明确指出两款机型使用不同的原厂 App；需以用户提供的新旧安装包/真实设备数据核实，不能凭名字断定底层完全相同。
- **已从 v1.0.75 源码发现兼容隐患**：`C110WifiBootstrap.verifyCamera` 读取网关 `192.168.1.254` 的 `cmd=3012` 后，要求回执字符串包含 `C110` 且**不包含** `C110_PRO`/`C110+`/`C110 PLUS` 才认为是标准 C110 并登记网络；`C110DeviceControls.verifyStandardCamera` 对 `C110+`/`C110_PRO` 也主动拒绝后续 9011/9038 写入；`C110WifiCamera.c110DeviceKind` 与 `C110CameraActivity.detectFamily` 使用各自略不同的文字识别分支。**多个位置重复、互不完全一致的设备型号判断，存在误拒现有 C110+、误判未知设备或导致不同页面表现不一致的风险。** 原用户旧实验成功并不能证明这些分支在真实固件中怎样触发；当前没有用户实机原始3012完整型号字段、固件版本证明，必须先采集核查。
- **下次 App 更新的明确待办**：① 先对照两款实际设备和各自原厂 App 的型号字符串、HTTP/RTSP/照片/灯控指令及版本差异；② 将上述 3012 设备识别和支持型号判断集中到**唯一受测试约束的设备识别/能力适配逻辑**，别在多处用不同的 `contains("C110")` 字符串猜；③ 对已在 C110+ 真机验证的能力保持兼容，不擅自去掉所有安全检查；④ 普通 C110 必须通过自身只读身份、网关和协议检测，再分阶段验证 3016/3001/2015 预览、1001单次原图、9011/9038写控制，**未验证之前不向未知设备发送写命令**；⑤ 未确认的命令或不支持功能给出明确提示；⑥ 不动 OCR/TTS、云AI、主页和已有稳定 C110+ 原有播放/拍照流程。
- **当前状态与授权边界**：用户要求“把隐患记录下来，下一个App更新修复；**先不要修改**”。本条仅为**待办文档**，不修改任何 Java/Android源码，不新建试验分支，不启动 GitHub Actions，不生成APK，不声称普通 C110 已兼容。等用户明确下次版本开始修复，再执行只读协议核实、最小范围适配和真机回归。
- **参考现有源码（v1.0.75）**：`cloud-vision-app/app/src/main/java/com/vobich/bluestar/cloudprobe/C110WifiBootstrap.java`、`C110WifiCamera.java`、`C110CameraActivity.java`、`C110DeviceControls.java`；当前正式 v1.0.75 版本 SHA `be29823b1cc51aa3ccafb25f18f998435cccf24d`。旧章节中“标准C110已真机验证”的措辞为历史误记，以本条澄清为准，勿删旧测试原始数据。

---

# 2026-11-22｜v1.0.75 全软件 UI 字体与 29 处内部弹窗统一：CI 成功，APK 已发布，真机待验收

- **用户授权**：在看到新版蓝星深蓝色接口用途弹窗效果图后，先表示字体清晰但略小，要求在图上**再放大10%**并确认，再明确说“可以就这样改，但是里面所有的弹窗包括主页包括显示的文字都要改成这样子，不要只改某一个页面”。这是全应用授权，不允许只修截图的2～3页。**始终保留主页五行、底部三按钮、C110/USB、云API/TTS/无障碍操作逻辑不变。**
- **仓库、分支、基线**：唯一原仓库 `vobichngan285-collab/kanzi-assistant`；从已发布 v1.0.74 commit `5627db373d0a41dd7e1bc9578631f5f759874011` 创建独立分支 `exp/unified-ui-typography-dialogs-20261122`。最终 HEAD / Release Tag 同一 SHA **`be29823b1cc51aa3ccafb25f18f998435cccf24d`**。新版本 `versionCode 84` / `versionName 1.0.75-unified-ui`，同一debug applicationId `com.vobich.bluestar.c110plusquick159`；旧版 v1.0.74 APK 与代码完整保留。
- **全 app 字体**：将现有 `BlueStarFontScale` 内原先强制×0.5 改为 `Configuration.fontScale *=0.825f`（相当于先前提议原始75%再增加10%，符合用户最后确认的视觉稿）；四个Activity `HomeActivity`、`MainActivity`、`TaskDesktopActivity`、`C110CameraActivity` 已有 attachBaseContext 全部覆盖，所有 app 内 TextView、输入框/弹窗继承。保留系统用户字体缩放基数，**只改sp显示字号，不变dp几何**；OS状态栏和系统权限/原生授权弹窗不由app控制。
- **深蓝主题全面覆盖**：`res/values/styles.xml` AppTheme 改为标准 Android `sans-serif` 字形、AppCompat/Android native 两种暗蓝 Dialog Theme，共用`res/drawable/bluestar_dialog_background.xml`（#102C4B 背景、22dp 圆角、浅蓝描边、文字高对比、蓝色取消/确定），选项52dp可点。原C110 Activity从 `@android:style/Theme.Material.Light.NoActionBar` 换为以**相同原生主题为parent**的 `BlueStarC110Theme`，只覆盖弹窗主题和字体，不改RTSP/VLC或相机UI控件。
- **29处App内部弹窗真正使用同一组件**：新增 `BlueStarDialogChoices.java`，提供 `AppCompatBuilder` + `PlatformBuilder`，把常规 setItems、单选 setSingleChoiceItems 改成一致暗蓝圆角52dp选项，支持适合OCR/视觉/语音/Wi-Fi/提示音/灯光的淡蓝图标、右箭头和无障碍整行标签；保留原索引/回调/取消/确认/单选行为。改过7个代码文件：`BlueStarSettingsScreen.java`（9处）、`C110WifiBootstrap.java`（3处）、`C110CameraActivity.java`（1处）、`MainActivity.java`（5处）、`TaskDesktopActivity.java`（4处）、`ReadingScreen.java`（4处）、`C110DeviceControls.java`（3处）。**CI逐个把Builder类型替换回旧字符串比对v1.0.74源码，证实这7个文件除此之外没有任何逻辑或布局改动。** `HomeActivity.java`字节级未变。
- **严格差异总共15文件**：`.github/workflows/unified-ui-typography-dialogs-20261122.yml`、`app/build.gradle`、`app/src/main/AndroidManifest.xml`、`BlueStarDialogChoices.java`(新)、`BlueStarFontScale.java`、7个上述UI类、`res/drawable/bluestar_dialog_background.xml`(新)、`res/values/styles.xml`、`docs/UNIFIED_UI_TYPOGRAPHY_DIALOGS_20261122.md`(新)。原 WiFi/OEM/Camera2/USB/C110图像/OCR、云模型、KeyStore、ASR/AI查找/结果朗读等文件无改动。
- **完整验证**：Actions `44981837757` `completed/success`：全15文件差异/主页字节级/7弹窗原回调源代码等价/四activity 0.825字号/2套主题/29调用点及 XML 校验；全部已有 Qwen/Zhipu AllowList、模型去重、VoiceAction、URL密钥复用、OCR行序、任务与C110 Zoom/照片策略纯Java测试PASS；原UVC补丁、ARM64 Gradle 8.11.1 + Java17完整 Android 编译，APK资源/包名/单Launcher/LibVLC3.6.5/UVC/提示音/SHA256及Release发布均PASS。第一轮CI `44981750561` 因断言仍以旧 Builder 名查找而失败，随后只修了测试断言；**第二轮完整成功**，不应误认为业务编译失败。
- **已实际发布**：Release tag `bluestar-unified-ui-20261122-v0175`，资产 `BlueStar_Guide_Unified_UI_1.0.75.apk`，**53,517,767 bytes**，SHA256 `f5d0df606c64344aabca887d01677aee91f05f36cde8423ef729e6c0c71fce43`。GitHub tag与源分支HEAD校验一致。APK下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-unified-ui-20261122-v0175/BlueStar_Guide_Unified_UI_1.0.75.apk 。CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44981837757 。
- **未通过真机验收**：用户需在 vivo X200 Pro Android16 查看主页/预览/任务/阅读/云设置/自定义接口页、C110 WiFi热点与协议选择/灯光等暗蓝选择弹窗：字体是否清晰自然、行高配比、对比度、可点击、单选选择/回退/取消是否正常、TalkBack是否朗读完整SSIDs、C110 RTSP与1001、USB/手机预览及TTS是否不退化。**CI通过不等于视觉上完全吻合生成效果图或已验收**。不要自行重写主页或偷偷覆盖v1.0.74。若覆盖安装提示debug签名冲突，不得直接卸载以免丢失KeyStore密钥。

---

# 2026-11-21｜v1.0.74 主页/预览共用「按住AI」＋寻找任务跨页＋同服务商 API Key 复用｜完整CI成功已发布

- **用户完整授权**：六项：①统一主页与预览按住AI；②寻找目标在主页、预览间共享，拍照检查、取消同步；③识别“如果看到医院的招牌立马告诉我”等自然表达；④同服务商 API Key 复用；⑤新建同服务商配置第二次 Key 留空可保存；⑥完整静态/Java/Gradle/资源回归。用户反复强调 **主页现有五行和底部三按钮、所有布局、字体严格50%、图标、配色、无障碍必须保持不变**，不能另搞界面。不新增自动连拍或持续视频识别。
- **仓库、基线、分支**：唯一仓库 `vobichngan285-collab/kanzi-assistant`；从已发布 v1.0.73 HEAD `3115fa804298a7a6d8f362ec319a9f9ed8f2a725` 创建独立 `exp/voice-key-reuse-20261121`；**最终源HEAD/tag SHA `5627db373d0a41dd7e1bc9578631f5f759874011`**。版本 `versionCode 83`／`versionName '1.0.74-voice-key-reuse'`，debug包名 `com.vobich.bluestar.c110plusquick159`、唯一HomeActivity Launcher不变。
- **主页“看不到”的真实原因及处理**：旧 `HomeVoiceAssistant` 识别语音后未执行 `VoiceAction.classify`，直接在没有照片时调用 CloudVisionClient 的 text-only AI，返回“我看不到画面”；`MainActivity` 预览按住AI已有 `VoiceAction`，能创建`findingTarget`。现在主页使用**同一个`VoiceAction.classify`**；启动/停止寻找只写共享目标并通过原TTS确认，不发送“无图聊天”；普通问题仍保留真实旧照片追问/无图聊天。主页“读文字/观察场景”改为同一 C110 原厂1001手动快拍路径的**一次性任务覆盖**，不改用户当前默认任务、USB/手机预览、相机控制与新的后台摄像工作。
- **自然语言与目标招牌**：扩展 `VoiceAction.naturalReminderTarget`：例如“如果看到医院的招牌立马告诉我”、“如果你看到医院招牌就告诉我”、“发现药店提醒我”；保留老语音寻找/停止语法、不得把“医院在哪里”等查询误认为寻找，保留“医院的招牌”目标文本不再剥掉“招牌”。视觉检查仍只针对用户实际拍下的照片，并允许真实建筑门头招牌作为有效证据，广告、地图标签/孤立指示牌与猜测不算。需要云视觉真实审核 `TargetVisionClient` 返回 found 且有可见证据才停止寻找，未找到继续下次**手动**拍照。
- **跨页面共享寻找**：新 `FindingTargetStore` 原样复用已有 `kanzi_settings.continuous_find_target`，无需迁移或新配置。主页、`MainActivity`（onResume 和拍照时刷新）、`C110FrameReader`（C110 高分辨率1001/预览点击取帧）都读取同一目标。`clearIfCurrent` 在已找到时原子核对当前目标，以免前一张异步照片覆盖或清空后设立的新任务。原原图JPEG/CI缓存/TTS、C110 Wi-Fi租约、PixelCopy、USB、手机Camera2和运行诊断没有重做。
- **同服务商密钥复用**：保留 `CloudKeyStore` 现有 Android Keystore AES-GCM、历史独立Profile JSON、已选模型、原有百炼密钥。新 `ProviderKeyReusePolicy.sameProvider` 限制**HTTPS+真实完整 hostname 严格相等+同端口**，禁止假子域、跨智谱/百炼混用、HTTP降级。新 `CloudProviderStore.reusableProfiles` 只找实际能解密的同源Key，包含满足同源限制的**原有百炼单Key**作为可选来源。新建时 API Key 留空且**恰好一个有效来源**，自动复用；多个来源必须在原编辑页点击新增的“复用已有密钥（可选）”按钮指定；无来源则明确要求手填。源密钥不回显、不写日志，不以明文保存在Profile metadata；复用时用原Keystore密钥给新Profile独立随机IV重新加密，删除源配置不影响新配置。编辑旧配置时Key留空仍保留自己的Key，显式切源又修改URL成不同域会拒绝保存。
- **界面严格保留**：逐段检查 `HomeActivity.render/nav/option` 与 v1.0.73 **字节级一致**（仅constructor与传递一次性拍照callback改变）；其他布局、`BlueStarFontScale`和图标代码未修改。仅在已有服务器编辑表单的 Key 输入框占位字及下方添加 **48dp“复用已有密钥（可选）”** 操作，不改其它设置页。
- **严格差异**：GitHub Compare v1.0.73→v1.0.74 **16个文件**：`HomeVoiceAssistant.java`、`HomeActivity.java`、`MainActivity.java`、`VoiceAction.java`、`FindingTargetStore.java`、`C110FrameReader.java`、`C110QuickShotController.java`、`TargetVisionClient.java`、`CloudProviderStore.java`、`ProviderKeyReusePolicy.java`、`BlueStarSettingsScreen.java`、`app/build.gradle`、两项纯Java测试 `VoiceActionSharedTest.java` / `ProviderKeyReusePolicyTest.java`、`docs/VOICE_KEY_REUSE_20261121.md`、`.github/workflows/voice-key-reuse-20261121.yml`。
- **实测构建证据**：GitHub Actions Run **44879539006** `completed/success`；全十六文件范围、主页字体/布局、AI指令/跨页目标/OCR与新密钥加密调用保护断言PASS；既有 Qwen/GLM、模型去重、任务/OCR行序、Zoom/照片策略和新增自然指令／HTTPS同服务商防混用测试PASS；固定AndroidUSBCamera 3.6.0 UVC补丁/LibVLC3.6.5，完整 Java17/Gradle8.11.1 ARM64 Android compile PASS；APK ZIP/aapt/包名/versionName/唯一HomeLauncher/UVC native/LibVLC native/提示音/语音发出音/SHA256/Artifact/最终Release PASS。
- **已经发布**：`bluestar-shared-ai-key-20261121-v0174`；发布 APK **BlueStar_Guide_Shared_AI_Key_1.0.74.apk**，**53,516,350 bytes**；SHA256 `78d58be94742394383de23a3a2e4f9e4ed70c06497dd735168995bfed794b735`。Tag和发布资产均已核验指向最终HEAD `5627db373d0a41dd7e1bc9578631f5f759874011`。
  APK直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-shared-ai-key-20261121-v0174/BlueStar_Guide_Shared_AI_Key_1.0.74.apk
  CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44879539006
- **下一步真机**（尚未由用户做）：vivo X200 Pro Android16 的主页与预览分别说“如果看到医院的招牌立马告诉我”、再用主页C110快拍、预览手机/USB照片与C110预览取帧，验证目标持续、找到才结束、取消跨页面同步、真实扬声器发声，普通ASR/AI追问/菜单阅读/TTS/无障碍没有回归；智谱FlashX/4.6V同一host首次填Key第二次留空自动保存、多个候选可指定、不同平台不复用、编辑旧配置留空不丢Key、删原来源配置不破坏复制出来的新Key。**CI不是实机验收**，不能声称用户当前已验证。保留 v1.0.73 原版以备回退。若 debug签名冲突不要直接卸载旧包，避免Android Keystore密钥丢失。

---

# 2026-11-21｜v1.0.73 云服务器管理页面紧凑修正版：CI成功，发布已核验，真机待验收

- **用户明确授权的更正**：用户真机截图显示 v1.0.72 云服务器管理里百炼 Qwen 3.7 / 3.8 系列按 OCR 与视觉任务被列了两遍、卡片又高又空、已添加的智谱等自定义服务器排到下方。用户确认“我的服务器”在前、原有百炼默认折叠、内置同型号仅一次、文字在紧凑卡片中间、点击/长按管理，然后说“开始”。**不授权擅改主页、其他设置布局、全局字号或其他拍照/TTS功能。**
- **仓库与基线**：继续唯一原仓库 `vobichngan285-collab/kanzi-assistant`。基于已经成功发布的 v1.0.72 `d5f4aac056904015350ec77acd09618efcd2258a` 创建独立实验分支 `exp/server-manager-compact-20261121`；**最终HEAD `3115fa804298a7a6d8f362ec319a9f9ed8f2a725`**。主APK `versionCode 82`，`versionName 1.0.73-server-manager-compact`，仍使用同一应用ID `com.vobich.bluestar.c110plusquick159` debug和唯一 HomeActivity Launcher。旧 v1.0.72、v1.0.71 APK/源码均保留。
- **唯一 UI 改动**：仅 `BlueStarSettingsScreen.java` 中云服务器管理页面及其下级模型操作菜单。真实 `CloudProviderStore.list(app)` 自定义配置先显示，单条profile只出现一次（相同模型ID但不同profile仍保留以防误删密钥）。再显示“＋ 添加新服务器”。最下面“原有百炼模型（5） · 展开/收起”默认折叠；用 `LinkedHashSet` 汇总 OCR/视觉/ASR，五个唯一模型 `qwen-vl-ocr`、`qwen3.7-flash`、`qwen3.8-flash`、`qwen3.8-max`、`qwen3-asr-flash`；点击展开保留已有滚动位置。管理行/操作行采用 **52dp高、6dp行距、文字垂直居中、箭头独立显示、全行TalkBack无障碍描述**，点击/长按打开相同动作。其它原有设置 `row()`、主页原五行/底部三项、卡片高度、图标颜色位置、全软件强制字体50% **没有改动**。
- **云模型操作保留并纠正用途**：用户自定义Chat配置只有一条列表项，优先按其当前生效OCR或视觉角色管理，ASR单独识别；无论该 Chat 模型当前选为视觉还是OCR，都可进入“自定义OCR提示词”并保存至OCR专属配置。内置百炼模型不可删除，用户配置修改和删除需要有效操作与二次确认，原有模型独立思考开关、原KeyStore API Key、URL/Model/选择、SSE逐行朗读不变。Layout Parsing / ASR 不会误收Chat提示词/思考参数。
- **GitHub严格对照五文件**：相对 v1.0.72 实际仅变更 `BlueStarSettingsScreen.java`、`app/build.gradle`、`docs/SERVER_MANAGER_COMPACT_20261121.md`、`tests/CloudServerListTest.java`、`.github/workflows/server-manager-compact-20261121.yml`。CI的同基线五文件断言已PASS；主程序 `HomeActivity`、`MainActivity`、C110 Camera/USB/CloudVisionClient、CloudProviderStore/CloudThinkingPolicy等全部未修改。
- **构建与发布核验**：GitHub Actions `44873158789` `completed/success`：包含静态范围、字体与主页、云端角色/提示词/思考路径保护断言、Java纯单测（新增内置五型号去重，原Qwen/GLM provider、Task/Zoom/OCR行序）、固定USB补丁、ARM64 Android Gradle build、APK包名/版本/唯一Launcher/LibVLC3.6.5/UVC/音效/SHA256、Artifact上传、最终Release均通过。
- **成功发布**：Tag `bluestar-server-manager-compact-20261121-v0173`，核实tag与分支均指向 `3115fa804298a7a6d8f362ec319a9f9ed8f2a725`；资产 `BlueStar_Guide_Server_Manager_1.0.73.apk`，**53,516,354 bytes**，SHA256 `5c440ec0cfa30674ab93664fe6aec0c48c9a9c716f90cefa6728d490c70eee82`。
  **APK**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-server-manager-compact-20261121-v0173/BlueStar_Guide_Server_Manager_1.0.73.apk
  **CI**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44873158789
- **真机尚未验收**：需要vivo X200 Pro确认云服务器管理首屏现在能直接看到真实已配置模型；百炼折叠默认关闭、点击展开五项、不重复、不跳滚动位置；行居中且上下卡片间距确实缩短；Tap/长按在TalkBack或普通模式能正确操作；模型修改/删除/连接测试/每个模型的深度思考及OCR提示词保持；主页/C110/USB/手机/TTS/音量及所有原有配置继续正常。CI仅证明构建和静态回归，**不能代替手机实际显示和朗读**。请勿擅自执行卸载清除，否则Keystore用户密钥可能丢失，遇到签名冲突需单独妥善处理。

---

# 2026-11-21｜v1.0.72 已通过完整 CI 发布：严格全局字体50%＋云服务器独立管理＋模型专属思考与OCR提示词

- **用户明确批准的9项内容**：全App所有字号严格50%（不是局部）、删掉重复说明、输入框内示例并聚焦清空、语音设置新增“云服务器管理”、已接入模型集中操作、模型选择与管理完全分离、各型号手动开关思考、各型号独立OCR提示词、构建前全面回归。尤其强调 **绝对禁止擅自重做主页**：现有五个主功能行“快捷拍照/C110+实时预览/朗读任务/阅读模式/设置”，底部三个“主页/预览/按住AI”，图标、按钮尺寸、位置、颜色保持原样。用户此前拒绝额外快捷卡片、模型状态区或重新排版。
- **唯一仓库与版本**：`vobichngan285-collab/kanzi-assistant`；从已发布 v1.0.71 HEAD `b5996933d69da71ec198358e0283be49e93c39c7` 创建独立分支 `exp/ui-font50-model-manager-20261121`，最终 HEAD `d5f4aac056904015350ec77acd09618efcd2258a`（CI 与 tag 已核验一致）。主包 `versionCode 81`、`versionName 1.0.72-font50-model-manager`；仍 `com.vobich.bluestar.c110plusquick159` debug applicationId，唯一 HomeActivity Launcher。旧 v1.0.71 正式发布保留。
- **十四文件严格差异，已 GitHub compare 确认**：`.github/workflows/ui-font50-model-manager-20261121.yml`、`cloud-vision-app/app/build.gradle`、`BlueStarFontScale.java`（新）、`BlueStarSettingsScreen.java`、`C110CameraActivity.java`、`CloudModelOptions.java`（新）、`CloudProviderStore.java`、`CloudThinkingPolicy.java`（新）、`CloudVisionClient.java`、`HomeActivity.java`、`MainActivity.java`、`TaskDesktopActivity.java`、`docs/UI_FONT50_MODEL_MANAGER_20261121.md`（新）、`tests/CloudThinkingPolicyTest.java`（新）。原 C110/USB/Wi-Fi/相机TTS核心无其他改动。
- **严格50%全屏字号**：四个当前主包Android Activities在 `attachBaseContext` 使用拷贝后的 `Configuration.fontScale *=0.5f`，不篡改系统设置，也不动任何 dp 按钮大小、排列、颜色或触摸区域。该值会缩小含弹窗、文本框提示在内的 app sp。极小字体有可读性风险但用户明确指定严格50%，不可未经许可添加字号最小值或动排版。
- **云服务器管理**：`设置→语音与提示→云服务器管理` 列出内置OCR/视觉/ASR与所有用户已接入自定义Profile；点击或长按进入同一模型操作菜单：修改配置、思考开关（支持才显示）、OCR专属提示词（Chat OCR才显示）、精准选中型号的付费微图像测试、二次确认删除（仅自定义）。系统内置不可删除。自定义Profile 新建编辑不再偷偷切换当前模型；原来 `云端AI→OCR/视觉/ASR→独立服务选择` 页面只保留模型单选，不放编辑/删除。已有 Profile JSON、KeyStore Key、当前生效选择键完全兼容旧版本；删Profile才删除该ID的模型选项与命名密钥，所选Profile自动切回百炼旧逻辑。
- **输入框瘦身**：云端接口编辑页面仅留配置名称、完整HTTPS地址、模型ID、API Key输入框与必要协议/SSE选择；重复技术描述删掉。EditText 无内容显示灰色说明，获得焦点提示立即消失，空输入失焦后恢复。已保存配置会回填非密钥字段，API Key编辑框继续隐藏且留空保留原Key。
- **独立模型控制**：新增 `cloud_model_options_v1` Prefs；内置同一模型ID共用设置，自定义按ProfileId+ModelId保存。默认关闭思考；仅对官方百炼 `qwen3.7-flash`/`qwen3.8-flash`/`qwen3.8-max` 在 Chat body 设置 `enable_thinking=true/false`，对官方智谱 `glm-4.6v`/`glm-4.6v-flashx`/`glm-4.6v-flash` 设置 `thinking.type=enabled/disabled`；其他模型/未知第三方/ASR/Layout Parsing **绝不猜参数**。只在普通 `task.id=ocr` 的照片 OCR 请求覆写用户设置的专用提示词，书页、视觉、菜单、自定义任务、追问和text-only保持旧任务行为。SSE 只收 `delta.content`，reasoning_content 不进 TTS。若同时设置任务提示词和模型OCR提示词，普通OCR由模型专用提示词优先；清空模型提示词即回到任务规则。Layout Parsing glm-ocr 既不能开启思考也不接受自由Chat提示词，UI不会给虚假设置。
- **构建证据**：GitHub Actions Run **44869321355** `completed/success`，全部环节通过：确切14文件静态差异、四Activity字体钩子/主页布局不增删、接口协议隔离、严格官方模型思考白名单 Java 回归、新旧CloudPolicy与 Task/Zoom/OCR行序测试、既有UVC补丁、全ARM64 Gradle编译、APK ZIP校验、包名/版本/单Launcher/LibVLC/UVC/2种音效、SHA256、Artifact、Release全部成功。Run SHA 和 Release tag 均 `d5f4aac056904015350ec77acd09618efcd2258a`。
- **已实际发布**：Tag `bluestar-ui-font50-model-manager-20261121-v0172`，资产 `BlueStar_Guide_UI_Font50_Model_Manager_1.0.72.apk`，53,516,358 bytes（比v1.0.71新增16,396 bytes，因加入用户授权的新设置），SHA256 `3b1607b50fa677288ff9531a5873a492d99158d7b556ed7b4d0f7af97f03ca93`。
  **APK**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-ui-font50-model-manager-20261121-v0172/BlueStar_Guide_UI_Font50_Model_Manager_1.0.72.apk
  **CI**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44869321355
- **未真机验收**：vivo X200 Pro 安装后必须检查用户真实五行主页和底部3栏不位移（此版仅缩字）、TalkBack能读输入框与按钮、C110标准版实时预览/快拍、USB和手机帧、TTS发声/按住AI/书页阅读，及多次切换千问/智谱/GLM-OCR的API Key、生效Model、深度思考和模型提示词保持。CI不能替代真机 API / OCR准确率与实际出声。**不要未经用户授权再修结构**。
- **潜在安装问题**：构建仍为 GitHub Actions debug签名。不同CI机器默认debug证书可能不同，因此即使相同applicationId，可能无法直接覆盖安装，绝不能为了升级自动卸载旧版本造成Keystore密钥丢失。先备份需要重填的API Key/配置；安装发生证书冲突时再与用户商量安全迁移方案。

---

# 2026-11-21｜v1.0.71 减肥版已成功发布：无效入口、历史实验与 Media3 清理

- **用户授权与目的**：在 v1.0.70 智谱提速初步有正向体感之后，用户要求“简单直接简单粗暴”，明确授权精简无用设置、不可达代码、历史实验及可安全删掉的依赖。本轮以不重造、保留已通过真机验证的手机/USB/C110、Qwen/GLM OCR SSE、TTS 和用户配置为前提。**这是结构减肥，不宣称云端模型推理速度变快**。
- **唯一仓库与基线**：`vobichngan285-collab/kanzi-assistant`；从已发布 v1.0.70 HEAD `12062f26b224d6a63c35384c660861513732fd25` 新建 `exp/lean-cleanup-20261121`，**最终 HEAD `b5996933d69da71ec198358e0283be49e93c39c7`**；主App `versionCode 80`、`versionName '1.0.71-lean-cleanup'`，debug applicationId `com.vobich.bluestar.c110plusquick159` 不变，唯一 Launcher 保留。未覆盖原 v1.0.70 Release 或其源码。
- **功能层级精简**：`BlueStarSettingsScreen.java` 主设置从七入口整合为 **相机、语音与提示、云端AI、任务管理、高级与诊断** 五入口；提示音/振动并入语音页，最近识别/匿名运行诊断归入高级；C110 机身设置保留 Wi-Fi、提示音、指示灯，详细选项弹窗，原有 9011/9038 和可自定义延时不变。删掉没有任何导航入口的 `providers`／`models`／`ocr-models` 旧页面，真正的 `provider-role` OCR/视觉/ASR 模型选择与 API Key 保留。
- **旧代码清理**：`MainActivity.java` 删除未绑定按钮、未赋值播放器的第二套 C110 实时预览和其 PixelCopy/旧任务、旧模型/相机/语音弹窗、隐藏顶部工具栏与从未显示的状态控件；`C110CameraActivity.java` 只移除无入口的任务/模式弹窗及 4003 单照片删除实验；`C110WifiCamera.java` 仅删不可达 4003 删除指令代码，保留真实 1001 拍照、照片索引、原生 JPEG。**C110 独立 VLC 播放器、预览轻触取帧、高清拍照、C110WiFi 管理、USB/Camera2、grtts/本地TTS 原方案保留**。
- **完全删除历史实验源码**：`C110DeletePolicy.java`、`C110LivePreview.java`、`C110PreviewProbe.java`、`C110ResolutionLabActivity.java`、`C110ResolutionScore.java`、`C110RtspSessionProbe.java`、`C110VolumeShortcut.java`、`C110VolumeTiming.java`、`TaskDesktopOverlay.java`；及对应四个已失去源类的测试 `C110DeletePolicyTest.java`／`C110ResolutionScoreTest.java`／`C110RtspSessionProbeTest.java`／`C110VolumeTimingTest.java`。旧分支保留全部历史源码。
- **构建依赖**：全本应用 Java 源文件未出现 Media3/ExoPlayer 使用，移除 app 模块 `media3-exoplayer`、`media3-exoplayer-rtsp` 和 `media3-ui` 三依赖；仍保留已验证 `org.videolan.android:libvlc-all:3.6.5`、`:libausbc`、`:libuvc`，未启用 minify/R8。变更范围严格20文件，CI静态Diff与保护断言通过。
- **构建与修复过程**：首次 GitHub Actions `44856924371` 通过静态、Java测试/UVC补丁后在 Android 编译发现 `MainActivity` 清旧预览时误删的 `c110Camera` 字段。只补回原 `private final C110WifiCamera c110Camera=new C110WifiCamera();`（保留真实 C110 高清 1001），其余逻辑不变。**最终 GitHub Actions `44857208883` 完整成功**：静态严格范围、Java回归（含 OCR A/C、完整行顺序无重复）、固定USB补丁、arm64 Android Gradle 编译、APK ZIP/原生 UVC/LibVLC/声音资源、包名/版本/唯一启动入口、SHA256、上传Artifact、发布Release 均PASS。
- **正式实验预览包**：Release `bluestar-lean-cleanup-20261121-v0171`；GitHub资产 `BlueStar_Guide_Lean_1.0.71.apk` **53,499,962 bytes**；对应 v1.0.70 GitHub资产 56,748,549 bytes，**实际减少 3,248,587 bytes（约5.72%）**。Release tag 解析到最终HEAD `b5996933d69da71ec198358e0283be49e93c39c7`，与成功Actions Run的 HEAD 一致。
- **直接下载**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-lean-cleanup-20261121-v0171/BlueStar_Guide_Lean_1.0.71.apk
  **CI**：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44857208883
- **真机仍需验收**：vivo X200 Pro Android 16：①五类设置均可达，用户API Key / 千问3.7/智谱FlashX/4.6V 与 OCR 快逐行配置完整；②手机后置、USB和C110独立预览帧、真实高清1001拍照/OCR、相机设置 LED与静音及叮声；③主页快拍、追问、阅读及所有已用TTS引擎/音色。检查上一次 1.0.70 的首次朗读体感是否保留。**CI成功不等于真机已通过**；如有回归按场景精确修复，不贸然重写Camera/OCR/TTS，不在下一版本擅改云模型速度与拍照锁。

---

# 2026-11-21｜v1.0.70 智谱 OCR 提速实验已发布标签（真机性能待测）

- **用户明确授权下一版优化**：基于 11月21日 09:27–09:30 手机摄像头分三轮实测，目标对比智谱高速 `glm-4.6v-flashx`、高精度 `glm-4.6v` 与原千问 `qwen3.7-flash` 的首句、整体耗时及完整识别率。凌晨历史样本不参与三模型排名。**不得为了快牺牲漏字率或改坏既有千问流程**。
- **原仓及代码**：`vobichngan285-collab/kanzi-assistant`，从 v1.0.69 已发布源码 `5206ad1c2688c215e0dcfedf55785f4b0de01907` 新建分支 `exp/glm-ocr-speed-20261121`；实验代码最新 HEAD `12062f26b224d6a63c35384c660861513732fd25`，app versionCode79、versionName `1.0.70-glm-ocr-speed-trial`。旧 v1.0.69 完全保留。
- **严格 7 文件差异**：`CloudProviderPolicy.java`、`CloudVisionClient.java`、`MainActivity.java`、`tests/CloudProviderPolicyTest.java`、`app/build.gradle`、`docs/GLM_OCR_SPEED_TRIAL_20261121.md`、`.github/workflows/glm-ocr-speed-20261121.yml`。已经用 GitHub 比较接口核实：源头 v1.0.69，领先7个独立提交，文件名正好是以上7项。
- **实验改动**：仅对智谱官方 Chat Completions 视觉模型、`朗读文字`默认未编辑提示词，尝试更直接的逐行输出要求；不覆盖用户自定义 OCR 提示词。智谱OCR成功完整读取并校验的 HTTP 请求允许 Java HTTPS keep-alive 复用尝试，失败/中断和非智谱通路仍显式断开。智谱 Chat OCR 成功日志增加纯数值 `cloud.output_chars`、`cloud.after_first_text_ms` 用于分析长尾。原千问3.7、GLM-OCR Layout、TTS、SSE分行器、拍照锁、原厂C110/USB、UI、Keystore/声音全部未修改。
- **Release 发布证据与限制**：GitHub 已实际解析新标签 `bluestar-glm-ocr-speed-20261121-v0170` 对应准确源码 HEAD `12062f26b224d6a63c35384c660861513732fd25`，表明该 tag 已成功创建；按专用 GitHub Actions 工作流源码，只有在静态范围检查、Java 单测、ARM64 构建、APK 校验及 Artifact 上传步骤成功后才会执行 `gh release create`，并随 tag 上传 `BlueStar_Guide_GLM_OCR_Speed_1.0.70.apk`。安装包发布路径： https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-glm-ocr-speed-20261121-v0170/BlueStar_Guide_GLM_OCR_Speed_1.0.70.apk 。**当前连接未提供本次 run ID、job 详情或资产独立文件大小/SHA256，因此不要伪称这些详情已独立核验；真机性能也尚未验证。**
- **下一步**：进一步通过 GitHub Actions 页面核对 run job 和资产详情；vivo 真机进行 v1.0.69 → v1.0.70 同场景对照：FlashX 与 GLM4.6V 各5轮，记录 `first_text_ms` / `first_speakable_ms` / `network_ms` / `output_chars` / `after_first_text_ms` 和识别原文完整性。尚不能预先承诺提速幅度。
 
---

# 2026-11-21｜v1.0.69 已构建发布：智谱 4.6V/FlashX 复用千问3.7原图文SSE/OCR/TTS流程

- 用户授权：新视觉模型接入后反馈连续拍照不响应，要求原样复用先前已成功的千问3.7拍照→可编辑OCR提示词→Chat图文SSE→完成行朗读→本地TTS流程；不得新建独立相机、OCR或语音逻辑。
- 分支及基线：已发布v1.0.68 commit ec5ea4ae7039d48c9a188d9d135671b9c005b76d → 隔离分支 exp/glm-qwen-chat-parity-20261121；最终 HEAD 5206ad1c2688c215e0dcfedf55785f4b0de01907。版本Code78，Name 1.0.69-glm-qwen-parity，包名 com.vobich.bluestar.c110plusquick159 保留。
- 精确改动6文件：CloudProviderPolicy.java、CloudVisionClient.java、tests/CloudProviderPolicyTest.java、app/build.gradle版本、专用CI .github/workflows/glm-qwen-chat-parity-20261121.yml 和说明 docs/GLM_QWEN_CHAT_PARITY_20261121.md。除智谱官方 Chat endpoint 上的 glm-4.6v/glm-4.6v-flashx/glm-4.6v-flash 增加唯一厂商参数 thinking.type=disabled 外，沿用原千问 JPEG、用户OCR提示词、Chat messages/image_url、SSE、OcrLineChunks、MainActivity本地TTS；模型必须选 Chat Completions，而非旧 GLM-OCR Layout。
- 特别保护：没有修改千问原路由、Camera2/USB、C110 OEM/LibVLC、TTS/广荣/音效、设置UI、KeyStore、原有MainActivity单线程encoder、1.5秒误触限制和cloudBusy排他锁。重要限制：这次原样复用千问流程，并不等于修复了旧忙碌锁对缓慢网络的等待；实际连续拍照和低延迟仍待真机核对，不能宣称已解决。
- GitHub Actions 44782366386 completed/success：严格六文件差异、vendor-specific thinking不污染其他服务商、GLM-OCR保留、Java回归、固定UVC patch、完整arm64编译、ZIP/包名/单Launcher/LibVLC/UVC/音效资源及Release全成功。Run SHA、Release target、HEAD一致（5206ad1c2688c215e0dcfedf55785f4b0de01907）。无真实智谱Key和真机，尚未验收供应商API成功/速度/识别准确率/扬声器。
- APK：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-glm-qwen-chat-parity-20261121-v0169/BlueStar_Guide_GLM_Qwen_Parity_1.0.69.apk （GitHub资产56,748,545 bytes），CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44782366386
- 真机后续：在vivo X200 Pro，OCR任务'朗读文字'、SSE启用、模式C，对比Qwen3.7与 glm-4.6v、glm-4.6v-flashx 同图、连续拍摄5~10次。若仍发生拍照点击不响应，先检查既存cloudBusy/单线程网络占用，未经新授权不扩改其他已验收功能。

---
# 2026-11-20｜v1.0.68 已成功发布：单一生效OCR模型＋GLM专用Layout Parsing＋快SSE

- **用户真机验证与授权**：在v1.0.67先选智谱免费`glm-4.6v-flash`后，旧「文字识别模型」页面又勾选百炼`qwen-vl-ocr`／`Qwen3.7-Flash`，但实际仍走智谱；免费版首次朗读很慢。用户确认明确切回「原有百炼」后3.7立刻恢复原速度，因此要求只修复两处模型选择不同步、慢的自定义默认非流式，并且优先新增GLM-OCR `layout_parsing`专用协议（地址/Key/模型可自己填）。允许修改发布，禁止碰已好的手机/USB/C110、原厂协议、音效、本地TTS、保益和主界面。
- **原仓与版本**：唯一`vobichngan285-collab/kanzi-assistant`，从v1.0.67实验包正式发布HEAD `0b81746b9933b95c90c08136c27a89f5d41f0382` 建独立分支 **`exp/cloud-single-model-layout-stream-20261120`**，最终 HEAD **`ec5ea4ae7039d48c9a188d9d135671b9c005b76d`**。版本 `versionCode 77`、`versionName '1.0.68-cloud-routing-layout-fast'`，debug主应用ID仍`com.vobich.bluestar.c110plusquick159`；旧release和独立保益小启动器不变。
- **本次代码精确10文件diff**：`BlueStarSettingsScreen.java`、`CloudProviderPolicy.java`、`CloudProviderStore.java`、`CloudVisionClient.java`、`MainActivity.java`、新增`LayoutParsingClient.java`、`tests/CloudProviderPolicyTest.java`、`app/build.gradle`、说明`docs/CLOUD_SINGLE_MODEL_LAYOUT_FAST_20261120.md`、专用CI `.github/workflows/cloud-single-model-layout-fast-20261120.yml`；没有改`C110WifiCamera`、`C110CameraActivity`、`C110FrameReader`、`C110DeviceControls`、`CaptureDingFeedback`、Camera2/UVC/LibVLC/native、原TTS和保益通道、Home/Task/预览布局、`CloudKeyStore`或既有模型API地址。
- **单一生效模型UI**：主页→设置→云端AI直接显示当前真正使用的 OCR/视觉/ASR **服务商+模型**；点击OCR同页选择旧百炼所有模型（含Qwen3.7）和自定义厂商，同角色只显示唯一✓。切回旧百炼模型时，`selectOcrModel`同时`CloudProviderStore.select(...,OCR,"")`，旧OCR模型名与实际API同步；视觉选择同理。旧隐藏的独立模型目录不再作为云端主菜单入口，避免两个勾选互相矛盾。用量页当前模型标题显示真正服务商+模型，最近一次实际调用与token保留。
- **流式速度修复**：`CloudProviderStore`对v1.0.67保存的`glm-4.6v-flash`和已知Qwen Chat模型 **一次性迁移 stream=false → true**，新增 Chat配置默认流式SSE，之后允许手动关闭并保留选择；不擅自打开未知模型的旧配置。不动原百炼`CloudVisionClient`的C快速逐行/流式逻辑。自定义Chat SSE通过既有`OcrLineChunks`和`SpeechChunker`有首行立即朗读；**无法保证服务端实际响应时间**，专用Layout本身同步返回。
- **GLM-OCR适配器**：自定义OCR的协议增加 `Chat Completions` / `Layout Parsing`；仅OCR可以选后者，其他角色拒绝。HTTP POST用户指定完整HTTPS地址（智谱官方 `https://open.bigmodel.cn/api/paas/v4/layout_parsing`），JSON `{"model":"glm-ocr","file":"data:image/jpeg;base64,..."}`，不使用Chat`messages`/`stream`；解析 `md_results`，若文字为空严格判失败，取服务端`usage`，复用旧OCR过滤/广荣语音。支持已有C110绑定的蜂窝网络请求；JPEG大小≤10MB。原有智谱配置可以编辑切Layout，保留密钥。
- **测试判定修复**：人工制造更清晰的512×512大字母A图，API真返回的文字必须识别出A才提示成功，避免只要HTTP200就被误报；单测覆盖OCR-only Layout/拒绝vision和ASR、旧免费glm和Qwen SSE升级策略、取消SSE、真实字母A校验（非A不能过）。
- **逐项CI构建**：第一次`44770737571`静态功能检查已PASS，但GitHub工作流错误地用当前HEAD对比自身，diff检查失败，没进入编译、没发布；已改为正确从published v1.0.67分支比较。最终 GitHub Actions **`44770878509` completed/success**：10文件严格范围、单一模型选择/fastSSE/Layout实际JSON字段和响应解析、旧百炼保护检查全部PASS；`CloudProviderPolicyTest`＋原`TaskOrderTest`／`FollowupPromptTest`／`C110ZoomGeometryTest`／`C110PhotoPolicyTest`、原固定UVC补丁和图标、完整arm64 Gradle APK编译、ZIP/libvlc/UVC/叮和嗖音效/唯一Launcher/包名/版本/SHA256、上传Artifact与独立新的Release全部PASS。**发布tag目标、CI代码SHA、分支HEAD三者一致**，旧版本没有覆盖。
- **APK**：Release `bluestar-cloud-layout-fast-20261120-v0168-lab`；直接下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-cloud-layout-fast-20261120-v0168-lab/BlueStar_Guide_Cloud_Layout_Fast_1.0.68.apk ；56,748,549 bytes；SHA256 `08514a8231a0f95e631160038bef169f29018c7ea4d87244227450d9bd6ef3c6`；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44770878509 。
- **待用户真机验收**：①OCR列表选智谱免费再点百炼3.7，**仅百炼3.7唯一✓**并实际拍照速度恢复；②重新选智谱4.6v-flash，自动SSE流式＋OCR设快速逐行，同图测首句朗读时间；③编辑旧glm-ocr选Layout协议，API地址须指向 /layout_parsing，模型`glm-ocr`，测试字母A以及真实照片；④检查手机/USB/C110快拍／云识别/TTS/金属叮/AI追问依旧正常。**没有第三方密钥和真机，不得将CI成功等同于智谱真实调用成功或性能达标。**

---

# 2026-11-20｜v1.0.67 最终实验包已成功发布：用户自定义 OCR / 视觉 / ASR 三类云端服务商

- **授权与原则**：用户要求以后自己填写服务商 **完整 HTTPS API URL、API Key、模型ID**，不再为接入火山、百炼、其他兼容大模型每次修改App；OCR、视觉分析（包括拍照理解、AI追问、寻找目标）、ASR语音转文字能分别选不同厂商，支持多套配置、无缝回到旧百炼；只做这项云端开放改造，保留相机/OEM/声音/本地TTS/保益，不动其他已验证功能。用户要求逐项检查并发布实验APK。
- **唯一原仓与分支**：`vobichngan285-collab/kanzi-assistant`，从已发布v1.0.66 `exp/phone-preview-clean-c110-ding-20261120` HEAD **`a79d5c5684dcacd24f92cfcb79f846065eba9d8d`** 新建隔离分支 **`exp/custom-cloud-providers-20261120`**，最终 HEAD **`0b81746b9933b95c90c08136c27a89f5d41f0382`**；主APK versionCode **76** / versionName **`1.0.67-cloud-provider-lab`**，debug app ID **`com.vobich.bluestar.c110plusquick159`** 不变。旧Releases/主仓历史/保益独立小APK保留，不新建仓库。
- **用户设置路线**：主页 → 设置 → 云端 AI →「自定义服务商 · OCR / 视觉 / 语音」→ 选择任一独立任务 →＋新建→ 填**完整HTTPS接口地址**、模型ID、API Key、配置名称 →保存即启用。20套可保存配置，可分别选择和复用同一组兼容配置，可编辑、删除及切回「原有百炼（默认）」。新增命名Key经既有 Android Keystore AES-GCM 单独加密，旧百炼 Key 不迁移不覆盖；删除旧百炼Key只删除其自身 `iv/ciphertext`，绝不误清空新厂商Key。OCR/视觉可手动小尺寸字母JPEG连接测试，实际消耗少量Token；ASR必须通过按住AI说话做真实测试（无假测试）。API Key不展示/日志/导出，只接受HTTPS，不自动跟随重定向。
- **兼容协议**：图文两种任务默认通用 OpenAI-compatible `chat/completions` JSON（`messages/content/text/image_url` Base64，非流式默认、可选SSE）；自定义不发送百炼专属 `enable_thinking/enable_search/stream_options`。ASR自选（1）OpenAI `audio/transcriptions` multipart含 `model/file=voice.wav`、（2）百炼音频Chat输入、（3）OpenAI Chat音频输入；并非所有专用厂商协议皆兼容，只提供三种实际实现的协议适配，不猜测回执或自动使用另一个付费API。
- **代码改变（严格差异15文件）**：新增 `CloudProviderPolicy.java`、`CloudProviderStore.java`、`tests/CloudProviderPolicyTest.java`；修改 `CloudKeyStore.java`、`CloudVisionClient.java`、`CloudAsrClient.java`、`TargetVisionClient.java`、`MainActivity.java`、`C110FrameReader.java`、`HomeVoiceAssistant.java`、`BlueStarSettingsScreen.java`、`ModelUsageLedger.java`、`app/build.gradle`；新增`.github/workflows/custom-cloud-providers-20261120.yml`和`cloud-vision-app/docs/CUSTOM_CLOUD_PROVIDER_LAB_20261120.md`。**未改** `C110WifiCamera/C110WifiBootstrap/C110CameraActivity/C110LivePreview/C110DeviceControls/CaptureDingFeedback`、OEM 1001/3012/3016/3001/2015/9011/9038、150ms LibVLC、Camera2/UVC视频采集、本机广荣微软TTS、原界面/底部导航、任务目录、主Manifest/Launcher、保益小启动器。现有原百炼兼容API方法全部保留。
- **逐项代码核对**：OCR角色已贯通手机/USB/C110静态JPEG、C110实时帧和阅读模式；视觉角色已贯通拍照分析、寻找目标（独立TargetVisionClient）、照片追问/重看图及主页连续AI问答；ASR角色已贯通主页按住AI、普通预览底部按住AI。特别修复C110“寻找目标”时即使当前预设OCR，网络/密钥预检也选视觉服务；原百炼删除Key不误删自定义Key；`ModelUsageLedger`保留6个旧模型历史行并加入自定义模型真实token和调用次数统计。
- **CI/发布校验**：最初中间构建/严格检查遇到缺失音频格式静态断言和新增动态用量文件名单变化，已修正并在独立**最终标签**重新全部运行。最终 GitHub Actions **`44754563400` completed/success**：15文件严格diff、独立OCR/视觉/ASR路由、加密命名Key与清理安全、传统百炼原通路、C110/USB/本地语音保护断言全部通过；`CloudProviderPolicyTest`（角色协议/HTTPS/模型名）、旧 `TaskOrderTest`、`FollowupPromptTest`、`C110ZoomGeometryTest`、`C110PhotoPolicyTest` 全部成功；pin 3.6.0原USB补丁/图标生成、完整arm64 Android Gradle编译、ZIP、ARM64 UVC/libvlc音效资源、包名/唯一Launcher/版本与SHA256校验、Artifact上传/独立final Release都通过。最终CI Run HEAD、Release目标SHA、实验分支HEAD **三者完全一致**，旧版本未覆盖。
- **最终唯一建议下载**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-custom-cloud-providers-20261120-v0167-final/BlueStar_Guide_Custom_Cloud_Providers_1.0.67.apk ；文件 **56,748,545 bytes**，SHA256 **`e74414b60e47c94d0ae1278ba8e35670d53d98ae8e0b2f81f73cc1ca27d01709`**；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44754563400 ；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-custom-cloud-providers-20261120-v0167-final 。另有中间测试tag，但以**v0167-final**为准，不要覆盖此前old Release。
- **用户待真机验收**：①不设置新服务商，原百炼OCR/视觉/ASR以及C110/手机/USB图像取景/快拍叮/TTS应不回归；②新增火山/其他VLM图文地址与Key/准确Model ID，先分别进行OCR和视觉小图测试，识别实际照片；③ ASR选具备已支持音频协议的厂商，按住AI真实说话测试，松开发送与本地朗读；④独立选择不同厂商/模型、删除自定义、切回百炼不丢原API Key，核对本机用量统计；⑤C110云端仍需移动数据，HTTP跳转拒绝。**自动CI成功不代表已持有他厂Key或手机实际完成供应商/扬声器验收**。需要在vivo X200 Pro装包之后验证；如debug签名冲突，不要未保存原Key时直接卸载App。

---

# 2026-11-20｜v1.0.66 已发布：普通手机/USB预览极简三按钮 + C110高清成功拍照本机“叮”

- **用户授权/视觉基线**：用户在 v1.0.65 真机普通预览截图发现，主页底部「预览」打开的 `MainActivity` 仍显示顶端Logo/☰/快拍/顶部🎙/倍率与顶部“任务：...”遮挡，以及底栏“设置”；用户明确确认下一张图：整个普通手机/USB预览无顶部工具栏/任务悬浮标签，仅保留底部「主页」「预览」「按住AI」，设置仍在首页竖排；另外C110运动相机拍照后缺少本机金属“叮”，要求补上并逐项核查生效。
- **唯一原仓与基线**：`vobichngan285-collab/kanzi-assistant`，在已发布 v1.0.65 `exp/c110-minimal-controls-20261120` HEAD `c30c033b9f7ec97a351f479a499c6d4ba4c06d31` 之上，创建隔离分支 **`exp/phone-preview-clean-c110-ding-20261120`**。最终 HEAD **`a79d5c5684dcacd24f92cfcb79f846065eba9d8d`**；versionCode75，versionName `1.0.66-clean-preview-c110-ding`，主包仍是 `com.vobich.bluestar.c110plusquick159`，保益独立小启动器包名和动作不改。未创建新仓、未覆盖之前所有发布。
- **普通预览实际修复（本次不是C110独立页）**：`MainActivity.getRootView` 原 title整排保留内部控件引用但 `title.setVisibility(View.GONE)`；画面任务小条 `currentTaskChip.setVisibility(View.GONE)`；底部右侧 `cameraEntryButton`由「⚙ 设置」变为「🎙 按住AI」，复用原 MainActivity 的 `handleVoiceButtonTouch`/`VoiceRecorder`/`VoicePressState`/`CloudAsrClient` 录音云端逻辑，不另建云接口。左「主页」仍 finish 回Home，中「预览」仍模式选择，右AI按住说话松开发送；统一设置只从首页竖排或其他既有设置页进入。原`settingsButton`隐藏后，切换USB/手机的无障碍提示改由可见 rootFrame 发出；`resultChip`成功/错误/录音状态仍限时 4.5s 隐藏，避免永远遮挡。手机/USB采集/画面tap OCR和缩放源码未改。
- **“叮”声修复**：新增 `CaptureDingFeedback.java` 预加载已有 `R.raw.c_metal_clack_ding`，同 MainActivity SoundPool 语音提示属性，遵从现有 `kanzi_settings/shutter_feedback_sound`；完全独立于 C110机身 9011 静音。 `C110CameraActivity.finishPhotoFlow` 在 **真实OEM 1001新 JPEG** 返回成功才 `photoSucceeded()` 一次；`C110QuickShotController.takeOnce` 在主页快拍成功 JPEG 回调也响一次；失败不响，退出释放 SoundPool。 `MainActivity.captureC110OneShot` 已有联网成功`beginCapturedFrame`响一次的逻辑保持不变，只在C110真实JPEG已拍到但云Key/网络不可用时`signalCapture()`一次，防止联网路径重复声音。音效为手机扬声器提示，不依赖 C110 9011 机身快门音。
- **严格改动范围**：`MainActivity.java`、`C110CameraActivity.java`、`C110QuickShotController.java`、新增`CaptureDingFeedback.java`、`app/build.gradle`、新独立CI workflow与说明文档**共7个文件**；没有触碰原`C110WifiCamera`/OEM 3012/3016/3001/2015/1001通信和Wi-Fi切换、150ms LibVLC、`C110FrameReader`/变焦JPEG裁剪、Camera2/UVC原采集、云OCR/翻译/AI路由、grtts TTS或保益入口。详细说明 `cloud-vision-app/docs/PREVIEW_UI_C110_DING_20261120.md`。
- **GitHub CI/Release 已核实**：第一轮 `44736940069` 在查改动基线时因checkout未取得基线SHA而停止、未发布，修复为显式抓取已发布v1.0.65分支；最终 **`44737099261` completed/success**：顶栏+任务小条隐藏、三按钮和复用AI触控、3条成功JPEG beep路线与音效开关、7文件严格边界断言均通过；`TaskOrderTest`、`FollowupPromptTest`、`C110ZoomGeometryTest`、`C110PhotoPolicyTest`，原UVC pinned补丁和图标，完整arm64 Gradle编译、APK ZIP/native libvlc/UVC/叮/发送音、包名/唯一Launcher/版本、SHA256通过，Artifact和 Release均成功。最终分支HEAD、Release目标与Run HEAD三者完全一致。
- **新APK**：`bluestar-preview-ai-c110-ding-20261120-v0166`；用户直接下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-preview-ai-c110-ding-20261120-v0166/BlueStar_Guide_Preview_AI_C110_Ding_1.0.66.apk ；**56,732,157 bytes**；SHA256 **`8e1a80f0b9e93a66a54738f4a6a199c338ca6700f7ce6abf9104eb50e04f7908`**；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44737099261 ；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-preview-ai-c110-ding-20261120-v0166 。
- **必须真机验收（自动构建不能判定扬声器真出声）**：①主页底部「预览」进普通手机/USB画面，顶栏与任务小条完全不见，底栏「主页/预览/按住AI」；②底部右键按住录音、松开发送、AI和TTS正常，主页「设置」仍可进；③主页「快捷拍照」真实C110单次1001成功取到JPEG会叮一次；④主页竖排 `C110+ 实时预览` 的蓝色高清拍照按钮成功取图也叮一次，失败不响、手机提示音开关关闭时不响；⑤ 1–4x变焦裁剪与云OCR/广荣朗读不退化，旧保益快捷拍照不需重装。**此版本已通过静态/编译/资源检查，不代表现场手机音频/布局已实际完成设备验收**。

---

# 2026-11-20｜v1.0.65 已编译发布：C110预览去顶栏、取消变焦和拍照底板（用户最终批准的图片）

- **用户授权与范围**：用户确认最后的黑色取景背景效果图并说「对开始」，明确 C110 页面顶部整排蓝色工具栏去掉；变焦滑动条背后的大块蓝色底板去掉（不是把取景画面变透明），保留 SeekBar 绿滑块/轨道、`1.0×` 倍率和下方「◎ 高清拍照」按钮；高清拍照按钮后面的蓝色整条底板也按最终确认图透明化。只修改 **C110CameraActivity界面层**，不动主页、任务宫格、普通手机/USB预览、C110原厂通信/取图、云OCR/AI、微软广荣语音/保益独立启动器。
- **原仓和基线**：`vobichngan285-collab/kanzi-assistant`；v1.0.64 Release基线分支`exp/task-fullscreen-clean-20261120` HEAD `5dbe3a1c07406eae46f4f7bb375dc28bd1c9460f`；新隔离分支 `exp/c110-minimal-controls-20261120` 最终 HEAD **`c30c033b9f7ec97a351f479a499c6d4ba4c06d31`**；版本`versionCode 74`/`versionName '1.0.65-c110-minimal-controls'`，debug主包 **`com.vobich.bluestar.c110plusquick159`** 原样，保益伴随小APK不需重新安装。
- **代码 diff 仅4个文件**：`C110CameraActivity.java` 12行变更（新增`titleRow.setVisibility(View.GONE)`，包括`setFullscreen`路径`topControls.setVisibility(View.GONE)`；`footer.setBackgroundColor(Color.TRANSPARENT)` 与 `zoomControls.setBackgroundColor(Color.TRANSPARENT)`，保留原控件对象避免开始/拍照流程引用空值）；`app/build.gradle`版本；独立CI工作流`.github/workflows/c110-minimal-controls-20261120.yml`；说明`cloud-vision-app/docs/C110_MINIMAL_CONTROLS_20261120.md`。没有改`C110FrameReader`或`C110WifiCamera`、拍照1001/变焦裁剪/150ms VLC、BLE/Wi-Fi、TTS/手机UVC/按钮回调。
- **构建、Release核验**：GitHub Actions **`44733432876` completed/success**；静态严格范围、顶栏隐藏与底板透明、保留变焦滑条/倍率/单次高清快门、关键C110功能与保益接口检查通过；`TaskOrderTest`/`FollowupPromptTest`/`C110ZoomGeometryTest`/`C110PhotoPolicyTest`、pinned USB补丁、图标生成、完整 Android arm64 Gradle编译、APK ZIP、libvlc/UVC/TTS音效资源、单Launcher、版本包名均通过；上传Artifact和新tag Release成功。分支末端 HEAD/成功run head/Release target **完全一致**。未覆盖旧release。
- **最终APK直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-minimal-controls-20261120-v0165/BlueStar_Guide_C110_Minimal_Controls_1.0.65.apk ；**56,732,165 bytes**；SHA256 **`a352a2a1f3b87fb8565a8b2b54e6386853b768738d29f8a9273a99bbfbd71cd2`**；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44733432876 。
- **真机验收尚未完成**：在vivo X200 Pro按主页竖排`C110+ 实时预览`进入，确认顶部工具栏不显示，视频画面可见且下方只有细滑动条、倍率、蓝色高清拍照按钮，没有它们背后的蓝底；手指滑动倍率与原图 ROI云识别对应，按钮一次执行1001并语音正常，安卓系统返回手势回主页。C110设置仍通过主页设置进入；移除顶栏后不提供页面内“重连/全屏/设置”按钮，这是用户确认的极简视觉取舍。手机/USB普通预览属于另一页面，本次不改。**CI成功不等于真机音频/摄像头验收**。

---

# 2026-11-20｜v1.0.64 用户已确认效果图：朗读任务改为真正独立的纯深蓝全屏；CI/Release成功，待真机

- **用户真机截图暴露问题**：v1.0.63主页「朗读任务」打开的是 TaskDesktopOverlay 半透明Dialog，主页原竖排文字直接透出来与任务图标重叠；顶部还莫名出现「▤ 阅读」、右侧大 X，图标上常驻小铅笔，页面凌乱。用户要求点进去直接换成完全干净的深蓝背景、不出现任何主页元素，顶部仅「返回」与「朗读任务」，每排4个小图标，平时不显示铅笔，长按才编辑/删除/拖动。已使用 ImageGen 生成并由用户明确确认效果图，随后授权执行。
- **原仓/基线**：唯一 `vobichngan285-collab/kanzi-assistant`，从**用户已安装的v1.0.63**隔离分支 `exp/home-task-voice-zoom-20261120` HEAD `7506f53c567ea0946bbe5352b07a878e1a85aa09` 创建 `exp/task-fullscreen-clean-20261120`。**最终HEAD `5dbe3a1c07406eae46f4f7bb375dc28bd1c9460f`**。主APK versionCode73、versionName `1.0.64-task-fullscreen-clean`，debug包名 **`com.vobich.bluestar.c110plusquick159`** 不变；保益独立一键触发器包名未改，旧Release/分支均保留。
- **实际修改**：`HomeActivity.chooseTask()` 不再调用 TaskDesktopOverlay.show 或保留主页任务编辑弹窗，改 `startActivity(new Intent(this, TaskDesktopActivity.class))`；Manifest 注册该独立非导出纵向Activity、不增加Launcher图标。新 `TaskDesktopActivity` 使用真正不透明 `#091B32` 全屏View、原生屏幕安全区域、左侧返回/居中朗读任务，固定4列12格/页，缺省5个任务+添加图标，超过一页才显示分页；无阅读按钮、X、底栏、多余说明和常驻编辑铅笔。单击选择保存任务并返回；长按但不移动显示编辑/隐藏或删除菜单；长按后拖动可通过原生Drag-and-Drop保存新顺序；编辑/新增沿用TaskCatalog旧Stable ID、SharedPreferences和全部自定义提示词，预设可隐藏并通过设置恢复，删除自定义有确认；新增真实Accessibility long-click action使读屏软件能触发任务编辑菜单。**仅修改HomeActivity、Manifest、app/build.gradle、新增TaskDesktopActivity与独立workflow、说明文档；原TaskDesktopOverlay只在非主页旧入口继续保留，不动其他已有组件。**
- **底层保护**：Diff核对没有触及 C110WifiCamera/C110CameraActivity/C110FrameReader、1001真实原厂高清快门、3012/3016/3001/2015 OEM、150ms LibVLC、JPEG变焦裁剪、USB/手机采集、广荣微软TTS、云OCR/语音追问、9011/9038、保益单独启动器或统一设置代码。
- **检查与发布**：新workflow `.github/workflows/task-fullscreen-clean-20261120.yml`。第一次工作流 `44715370131` 因 GitHub checkout 历史不足导致 git diff 对照失败，已修复 `fetch-depth:0`；此前较早SHA有一次成功构建并创建了旧 `v0164` 测试预发布，为避免覆盖在TalkBack补丁之后使用**新的最终标签** `bluestar-task-fullscreen-clean-20261120-v0164-final`。最终Run **`44715977871` completed/success**：独立不透明Activity和首页跳转/删除阅读与铅笔/4列/12格/新增与编辑/拖拽与TalkBack/原包名以及全部C110核心保留断言成功，Java `TaskOrderTest`/`FollowupPromptTest`/`C110ZoomGeometryTest`/`C110PhotoPolicyTest` 成功，UVC原固定补丁、图标生成、完整Android arm64编译、libvlc/UVC/提示音资源、唯一Launcher和版本签名检查成功，Artifact上传和最终Release成功。最终Run SHA、发布目标 SHA、分支最新HEAD三个完全一致。
- **最终用户APK下载**： https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-task-fullscreen-clean-20261120-v0164-final/BlueStar_Guide_Task_Fullscreen_Clean_1.0.64.apk （**56,732,165 bytes**, SHA256 **`3a2a9eb9d628ce6b41711e44330c51253c3d45feec68f9a6504fd8ddeebdda42`**）。CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44715977871 ；最终Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-task-fullscreen-clean-20261120-v0164-final ；细节 `cloud-vision-app/docs/TASK_FULLSCREEN_CLEAN_20261120.md`。
- **待用户真机测试**：打开主页点「朗读任务」→背景必须全蓝且完全看不到原首页文字；标题左侧返回、四列现有任务图标，不出现顶端阅读/叉号/编辑铅笔；单击任务后自动返回并用于下一次OCR；长按菜单、长按后拖动、添加与删除/隐藏和恢复；必要时检查读屏长按是否可达；保益快拍和广荣语音不受影响。CI成功不代表 vivo X200 Pro 真机的 UI/无障碍交互验收，必要时后续按真机反馈小修。若 debug 签名不同，先准备API Key与配置再卸载旧主App，保益独立小触发器不能卸载。

---

# 2026-11-20｜v1.0.63 已完成 CI 和 Release：主页竖排、四列任务宫格、C110极简预览/裁剪及跨相机照片AI语音追问

- **用户授权需求**：v1.0.62 用户报告正常，明确授权下一版实施并要求每项逻辑检查通过再发布。主页仍为纵向列表：快捷拍照、C110+实时预览、朗读任务、阅读模式、设置；原“AI视觉分析”入口去除，设置从底部右移到主页纵排末尾，右下底栏由“设置”换成“按住AI”。只有点击“朗读任务”才显示类似手机桌面的四列小图标任务宫格，支持拖动排序、长按删除/隐藏和编辑、添加。C110预览删除多余按钮、保留取景/变焦滑条/单次高清拍照和机身设置，上传相应变焦裁剪高分辨率图；主页AI按住说话，复用同一API Key和模型，根据最近一张手机/USB/C110实际上传的照片回答并可连续问。
- **唯一原仓和基线**：`vobichngan285-collab/kanzi-assistant`；以已发布v1.0.62分支 `exp/preview-navigation-clean-camera-20261120` HEAD `3d1654ee2c2d75f3c98ad5d17cf872cf05d60df7` 创建隔离分支 `exp/home-task-voice-zoom-20261120`；最终**HEAD `7506f53c567ea0946bbe5352b07a878e1a85aa09`**。主程序 versionCode72 / `1.0.63-home-task-voice-zoom`，**旧安装ID `com.vobich.bluestar.c110plusquick159` 不变**；保益小触发器 `com.vobich.bluestar.boyuebridge` 无需更新，不新建仓库，不覆盖旧Release。
- **源码及行为**：`HomeActivity`保留五个纵向入口、底栏主页/预览/按住AI；`TaskDesktopOverlay`复用原可拖动宫格，4列12格/页，图标上提供编辑入口，预设仅隐藏、自定义可删除，`TaskCatalog`配置/排序不重写；`C110CameraActivity`顶栏返回/重连/全屏/机身设置，底部单个高清1001快门与无加减按钮的1–4×滑条，短暂状态自动隐藏。`C110FrameReader`原`C110ZoomFrames.cropNativeJpeg`在上传之前按显示区域裁剪真实高分辨率JPEG，1×保留原图字节，失败不发送整图。新增`RecentPhotoSession`仅进程内记住最后实际提交的JPEG/文字/云网络；在`MainActivity`手机/USB与C110 JPEG、`C110FrameReader`的高清/实时取帧成功提交时统一更新并在成功识别后附加描述；原`CloudAsrClient`加可选`Network`，C110热点存在时ASR/云视觉可走蜂窝；新增`HomeVoiceAssistant`复用原`VoiceRecorder`、`VoicePressState`、百炼Key/所选视觉模型/TTS引擎、上传最近一张照片的真实JPEG问答（无照片则文字聊天），限制三轮上下文，照片不写相册或日志。
- **不触碰已成功底层**：OEM 3012/3016/3001/2015、原厂1001单次快门、C110 Wi-Fi、150ms LibVLC 3.6.5、9011/9038灯光和声音、C110 原有`C110FrameReader`微软广荣TTS回归修复、手机 Camera2/USB UVC 采集、统一云端AI设置、保益独立小触发器包和唯一Launcher。对比 v1.0.62仅十个文件新增/改动，均为本次范围。
- **检查情况**：新工作流`.github/workflows/home-task-voice-zoom-20261120.yml`。初次工作流 `44711285267` 构建时发现TaskDesktopOverlay缺少FrameLayout导入，故**失败且没有发布**；随后已修复。第二次较早 SHA `44711605213` 编译及APK校验通过但因已存在同名Release未重复发布（属于预期保护）。**最终最新版Actions run `44711612302` completed/success**：`TaskOrderTest`、`FollowupPromptTest`、`C110ZoomGeometryTest`、`C110PhotoPolicyTest` 和新旧链路静态断言通过；pinned UVC 补丁、图标、完整 Android arm64 Gradle 编译成功；APK ZIP、libvlc/UVC、提示音、单Launcher、版本和原包名校验通过；上传Artifact并Release成功。最终Release target SHA与本分支末端严格一致。**CI通过不代表vivo X200 Pro真机的 C110 取景/声音/拖动/按住AI功能已经验收**，必须实测。
- **实际发布**：tag `bluestar-home-task-voice-zoom-20261120-v0163`，**56,732,125 bytes**，SHA256 **`e70d5b76aef5f8153c13e8be7d3f66a904b497192a062233abda89d20701c737`**。APK直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-home-task-voice-zoom-20261120-v0163/BlueStar_Guide_C110_Tasks_AI_Zoom_1.0.63.apk ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44711612302 ；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-home-task-voice-zoom-20261120-v0163 。
- **真机验收顺序**：①打开主程序为纵排且没有AI视觉分析、纵排最下设置可进、底栏右键按住AI；②点朗读任务见4列小图标，排序/隐藏/编辑/添加并跨页面持久保存；③ C110画面可连接，1–4×拖动后单张1001按所见高清区域云OCR/TTS（1×原图），进出设置/相机预览正常；④手机/USB和C110各拍一张后回主页按住说“上一张照片是什么”，松手发送并继续追问，检查广荣微软TTS真有声；⑤保益双击调用主程序正常。临时 debug 签名如不一致，**先准备好 API Key 等设置**再卸载旧蓝星主程序，绝不卸载已绑定保益的独立小启动器。

---

# 2026-11-20｜v1.0.62 已构建发布：修复设置→预览误回主页，清理C110实验/冗余说明，保留广荣正常TTS

- **用户真机结果与授权**：用户确认 v1.0.61 广荣微软 TTS**已恢复正常**，但从设置底栏点击「预览」会跳回主页，且 C110 运动相机设置里实验入口/说明文字臃肿，要求去实验、清理说明。已按用户要求在原仓独立分支修改，不改已真机成功的语音、保益快拍和OEM。
- **根因定位与修复**：MainActivity `launchMode=singleTop`。主页→设置时进入`settingsOnly=true`的MainActivity；旧`openPreviewFromUnifiedSettings`调用`startActivity(MainActivity)+finish()`，会使当前栈顶MainActivity复用随后被关闭，从而露出其下方HomeActivity。新方法**在当前MainActivity内**设置`settingsOnly=false`、清除原Intent的`bluestar_launch_settings`、显示`previewContainer`与黑背景、关闭设置叠层，再调用`resumeForegroundPreview()`。普通onResume和设置→预览调用同一个手机/USB Camera2/UVC启动/权限流程，不再自启动本Activity/finish。主预览→设置仍保留。此项编译通过，需用户真机确认正确显示相机实时画面。
- **精简C110设置**：`BlueStarSettingsScreen`中「相机→C110+」只留连接相机Wi-Fi、机身提示音关/开、指示灯关/开/15/30/60秒自动关闭和自定义时间，底部简短实时连接/命令状态；删除「照片分辨率/OCR准确率实验」入口、冗长说明段。 `C110CameraActivity`预览顶部「相机」/菜单不再弹出中间的实验/删图提示菜单，直接进入原有 `C110DeviceControls.showSettings`；删除原设备控制菜单里「说明」占位项。移除旧`MainActivity.openC110PhotoLabFromUnifiedSettings`方法，Manifest注销`C110ResolutionLabActivity`（实验源代码仍在仓库历史以供追溯）。统一设置的USB画质、云模型、OCR速度、诊断等页静态长文清除，但不删可操作设置、实际状态、必要错误、Max收费确认或日志导出。
- **原仓与版本**：原仓`vobichngan285-collab/kanzi-assistant`；基线v1.0.61分支`exp/c110-tts-regression-20261120` HEAD `e8c5430a5846c58958eec4ef0ae567e8219b2d51`；隔离分支`exp/preview-navigation-clean-camera-20261120` HEAD **`3d1654ee2c2d75f3c98ad5d17cf872cf05d60df7`**。app versionCode71/name`1.0.62-preview-clean-camera`，调试包继续维持`com.vobich.bluestar.c110plusquick159`，用户现有保益独立小触发器`com.vobich.bluestar.boyuebridge`仍然能指定相同Home `ACTION_QUICK_SHOT`；主App只有一枚图标，不需重新下载/绑定小触发器。
- **未修改且必须保持**：v1.0.61真机已恢复的TTS相关`C110FrameReader`/快拍流程、原厂1001只拍一张高清JPEG、共用云OCR/API Key、原3012/3016/3001/2015和150ms LibVLC/竖屏ROI、9011/9038机身控制、USB/手机核心预览摄像头处理、保益快捷入口及蓝星配色。实际差异已与v1.0.61提交比对，未触碰TTS源码/云/OEM原始协议。
- **构建与发布已核验**：新workflow `.github/workflows/preview-navigation-clean-c110-20261120.yml` GitHub Actions run **`44703319132` `completed/success`**，成功包含针对singleTop修复/保益兼容/唯一Launcher/机身指令白名单/保留TTS的静态检查及Java单测、Android arm64完整Gradle编译、原UVC/LibVLC资源APK验证、Release发布。Release `bluestar-preview-clean-c110-20261120-v0162` target与run HEAD均为`3d1654ee2c2d75f3c98ad5d17cf872cf05d60df7`。单个主包`BlueStar_Guide_C110_Preview_Clean_1.0.62.apk`，**56,715,745 bytes**，SHA256 `b5d54dee522f888352560d77808c9b198b6cfe2b3236f8800dcbbca4a535ed6f`。下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-preview-clean-c110-20261120-v0162/BlueStar_Guide_C110_Preview_Clean_1.0.62.apk ；run https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44703319132 。专项说明 `cloud-vision-app/docs/PREVIEW_NAV_CLEAN_C110_20261120.md`。**CI成功不等于vivo真机完成预览和声音验收**。
- **下一步测试最少**：用户安装v1.0.62主APK（不要卸载保益小触发器）；打开蓝星→设置→预览，检查是否直接进入摄像头画面而非返回主页；查看相机→C110设置是否干净；试听广荣或真实拍照 OCR朗读确保v1.0.61正常语音保留。未经用户指令不再加实验页、长说明或额外桌面图标。

---

# 2026-11-20｜v1.0.62：设置→预览不再跳主页、C110设置移除实验与冗长说明；CI/Release成功，待vivo真机

- **用户真机确认上一版**：v1.0.61广荣微软TTS已经恢复正常；当前要求解决从设置底部「预览」直接返回主页的bug，以及移除「相机→C110+运动相机」的实验入口和冗余说明文字。保益独立小启动器继续正常使用，不新增图标。
- **实际bug**：设置从Home启动 `MainActivity(bluestar_launch_settings=true)`，`MainActivity` Manifest为 `singleTop`；旧 `openPreviewFromUnifiedSettings()` 自己 `startActivity(MainActivity)` 后 `finish()`，可能复用并关闭设置Activity，显示下方Home。修复：当前Activity就地设置 `settingsOnly=false`，清除旧设置Intent标记、显示 `previewContainer`、关闭遮罩，调用与onResume共用的 `resumeForegroundPreview()`；**不再启动/关闭第二个MainActivity**，保留原手机Camera2/USB UVC准备、设备权限/补获流程，Manifest singleTop 不变。
- **相机设置精简**：顶层统一设置→相机→C110+保留连接Wi-Fi、9011机身提示音开关、9038指示灯开关和15/30/60秒/自定义关灯；只显示精简网络/指令状态。移除「照片分辨率/OCR准确率实验」入口、C110长段说明，去掉主程序跳实验的方法并从Manifest注销 `C110ResolutionLabActivity`（老实验源码保留在仓库/历史但APP无法从UI进入），C110实时预览里「相机」按钮直接进入原有声音/灯光设置，取消重复的实验/删图安全弹窗；C110DeviceControls移除仅说明、无动作的行。云端AI/USB/OCR/诊断页面删除冗长静态文字，保留操作按钮、真实状态、计费确认和必要错误提醒。
- **明确未动**：v1.0.61 `C110FrameReader`/ `C110QuickShotController`/Home TTS修复原封不动，广荣中文引擎原来已成功仍需新包真机确认；C110 OEM 1001单张SD原图、3012/3016/3001/2015、C110 Wi-Fi与云OCR、150ms VLC/竖屏裁剪、9011/9038机身控制、手机/USB预览、保益原独立129KB小启动器和唯一正常「蓝星向导」主图标未改。**主APP包名仍为 `com.vobich.bluestar.c110plusquick159`**，不需要更新保益小包，也不需重新设置双击快捷键。
- **仓库**：唯一原仓 `vobichngan285-collab/kanzi-assistant`；从v1.0.61分支 `exp/c110-tts-regression-20261120` HEAD `e8c5430a5846c58958eec4ef0ae567e8219b2d51` 派生 `exp/preview-navigation-clean-camera-20261120`，**最终HEAD `3d1654ee2c2d75f3c98ad5d17cf872cf05d60df7`**；versionCode71/versionName `1.0.62-preview-clean-camera`，只发主APP新包，不覆盖旧Release和任何历史实验。
- **完成核验**：新GitHub Actions workflow `.github/workflows/preview-navigation-clean-c110-20261120.yml` Run `44703319132` `completed/success`，包含导航就地切换/不再finish、C110实验入口清除与说明精简、上版TTS不变、1个LAUNCHER、OEM/150ms/USB等自动检查，以及完整Gradle安卓arm64编译、APK完整性/签名/版本/Release。**GitHub构建成功不代表vivo上真实相机画面已验收**。
- **发布**：Release tag `bluestar-preview-clean-c110-20261120-v0162`，主 APK `BlueStar_Guide_C110_Preview_Clean_1.0.62.apk`，56,715,745字节，SHA256 `b5d54dee522f888352560d77808c9b198b6cfe2b3236f8800dcbbca4a535ed6f`；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-preview-clean-c110-20261120-v0162/BlueStar_Guide_C110_Preview_Clean_1.0.62.apk ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44703319132 ；说明 `cloud-vision-app/docs/PREVIEW_NAV_CLEAN_C110_20261120.md`。
- **真机验收优先级**：① 主界面→设置→底栏「预览」显示手机/USB真正画面（不回Home），再按设置可往返；② C110设置只保留声音、灯光、Wi-Fi，预览页「相机」不再有实验/安全提示；③ 设置广荣试听及保益双击一键C110高清拍照OCR/TTS仍正常。如果安装提示签名不一致，先备份/保存API Key，再卸载旧蓝星主APP并安装v1.0.62；**保益独立小启动器不要卸载**。

---

# 2026-11-20｜C110+ OCR 成功但广荣微软 TTS无声：版本回归最小修复 v1.0.61，CI已成功，待真机验证

- **用户真实现象**：v1.0.60 C110+真实高清快拍及OCR均成功，但完全无声；连「设置→语音与朗读→试听当前语音」在C110 Wi-Fi连接时也无声。用户明确说早期曾经正常，授权回查早期代码并定位具体退化。不能仅根据设备接C110热点就猜成移动网络问题。
- **实际对照源码发现两项差异**：旧v1.0.50 `cdd131908fbed3e22c9ee351a707272c60108dce` 的 `MainActivity` 是唯一LAUNCHER，**没有HomeActivity**；新版 `HomeActivity.onCreate` 提前构建 `C110QuickShotController→C110FrameReader→TextToSpeech`，进入蓝星统一设置 `MainActivity` 时又创建第二套 TTS。两套第三方引擎并存是源码级结构变化、可能干扰广荣，但必须真机验证是否唯一根因。另v1.0.50/v1.0.55的C110 FrameReader在语言初始化中执行 `tts.setLanguage(Locale.SIMPLIFIED_CHINESE)`，v1.0.58未经广荣真机验收删掉此调用，v1.0.60继续没有；MainActivity的试听TTS基本保持旧实现。
- **实验范围与提交**：原仓 `vobichngan285-collab/kanzi-assistant`，以v1.0.60 `exp/c110-settings-direct-controls-20261120` HEAD `aae12305e0d7ae908898c284a8ddfe610d8f005d` 派生隔离分支 `exp/c110-tts-regression-20261120`，**最终HEAD `e8c5430a5846c58958eec4ef0ae567e8219b2d51`**。versionCode70/versionName `1.0.61-c110-tts-restore`，debug **继续用旧包名 `com.vobich.bluestar.c110plusquick159`**，保益独立小启动器继续显式调用此包 `HomeActivity ACTION_QUICK_SHOT`，不重新安装小启动器。
- **只修改三处语音所属逻辑**：`C110FrameReader` 构造时不再提前初始化TTS（延迟到用户触发真实C110拍照/实时朗读操作才创建）；恢复旧版C110 OnInit后的 `tts.setLanguage(Locale.SIMPLIFIED_CHINESE)` 并保留错误提示、语速及回调generation安全；新增 `releaseSpeechForOtherScreen()` 忽略过时回调，shutdown Home多余的TTS实例。 `C110QuickShotController.onResume` 不再无条件初始化语音，`trigger` 仍按最新 `app_tts_engine_package/local_tts_rate` 正常创建所选引擎，云OCR结果先于OnInit时仍只缓存本次待播报文本。 `HomeActivity` 显式进入**统一设置、手机/USB预览、C110视频预览、阅读模式**时先释放主页TTS，避免 `MainActivity` 第二实例并存；不会在Android系统Wi-Fi授权弹窗等一般onPause时释放该段语音。
- **行为边界**：用户主动切换到其他页面时如果上一段音频正在播放，将被终止，以释放语音资源；后台C110相机/网络、云端OCR和原生1001指令**没有修改**。设置页的 `MainActivity.initializeSpeech()` 与其语音选择、试听代码保持旧实现。没有重写或引入新TTS模块/增加诊断页面/外部无障碍服务。
- **确认保留原功能**：对照 v1.0.60 的 diff 仅新增这份文档/独立workflow及改动 `C110FrameReader.java`、`C110QuickShotController.java`、`HomeActivity.java` 和 `app/build.gradle`，旧 C110 Wi-Fi OEM 3012/3016/3001/2015、1001单张JPEG、150ms LibVLC/竖屏ROI、共用云OCR模型和API Key、C110机身9011/9038在相机设置中直接控制、手机/USB预览、蓝星单一主图标、保益开启时使用已验证独立129KB小启动器、禁用4003删除/1002写入全部保持。
- **CI+Release已核对**：工作流 `.github/workflows/c110-tts-regression-20261120.yml`，Run `44693385324` 状态 `completed/success`，Gradle Android arm64编译+原OEM/USB/相机/模型/150ms/TTS/原图/单个图标/保益兼容与本次惰性TTS语言初始化静态回归均通过；最终 GitHub Release target和Run HEAD都是 `e8c5430a5846c58958eec4ef0ae567e8219b2d51`。APK `BlueStar_Guide_C110_TTS_Restore_1.0.61.apk` 56,732,165字节，SHA256 `1598133a5809dd4fcb12773fc4c30fe6b8d6dcf2b94452396251b72ede0b8b1e`，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-tts-regression-20261120-v0161/BlueStar_Guide_C110_TTS_Restore_1.0.61.apk ；构建 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44693385324 ；设计对照 `cloud-vision-app/docs/C110_TTS_REGRESSION_RESTORE_20261120.md`。
- **接下来真机**：不必动保益的小启动器；安装v1.0.61主APP（若签名不一致请先准备好API Key，再卸载旧主APP安装本版，切勿卸载独立小启动器），确认统一设置里选广荣后**试听能真正听到音色**；接C110 Wi-Fi再试听；主页/保益一键单次1001照片OCR之后验证同一广荣音色朗读。如果仍无声，下一步仅采TTS实际引擎包名、OnInit状态、speak返回码和OnError并检查广荣独立联网，**不改已成功OEM/云OCR**。**CI成功不等于vivo实际扬声器出声，尚未真机验收。**

---

# 2026-11-20｜蓝星统一「相机」设置直控 C110机身声音/灯光 + 去重复快捷键与图标：v1.0.60 已编译发布，待真机

- **用户真机与授权**：v1.0.59保益悦听成功通过「双击音量上 → 打开应用」启动**独立129KB「蓝星一键拍照」触发器**，但主蓝星APK额外内置的同名一键图标不可用，导致3图标；用户要求去掉无效那个。统一设置→相机→C110+中还重复列OCR/视觉模型和旧实体音量键快捷设置，机身声音9011、指示灯9038必须先打开**实时预览**才能操作。用户明确授权：顶层云端AI统一管理所有OCR/视觉模型/API Key；软件自己的音量键监听/设置直接取消（交由保益实际可用的独立小触发器）；「相机→C110+」直接连接Wi-Fi操作机身提示音/指示灯与定时关灯，不用打开VLC视频；**主页「C110+实时预览」按钮本身仍保留**。不改底层OEM/云OCR/广荣微软语音。
- **分支/真实代码**：唯一仓 `vobichngan285-collab/kanzi-assistant`，以v1.0.59 `exp/boyue-shortcut-photo-entry-20261120` HEAD `02ee5b885536442d73819795b6b341f172fa48a3` 派生 `exp/c110-settings-direct-controls-20261120`；最终 HEAD **`aae12305e0d7ae908898c284a8ddfe610d8f005d`**。主APP versionCode69/versionName `1.0.60-c110-direct-settings`，**保持原包名 `com.vobich.bluestar.c110plusquick159`** 让已经绑定保益的独立小启动器 `com.vobich.bluestar.boyuebridge` 指向不变；只发**一份主APP APK**，不需重新下载触发器。
- **去冗余图标**：删除主包内部的 `QuickShotEntryActivity.java` 和Manifest额外LAUNCHER，主包只有一个正常「蓝星向导」图标；原Home `ACTION_QUICK_SHOT`、`onNewIntent`、仅执行一次的快拍逻辑完整保留；现有单独的「蓝星一键拍照」小APK仍经显式Intent将此action送到主Home，因此两个应用包将显示两个图标。CI AAPT badging/源码检查确认主包仅**一个**launcher。**安装操作重要**：因Actions临时debug签名可能和手机v1.0.59不同，直接覆盖更新不一定成功；若提示「签名不一致/无法安装」，用户须先卸载手机上的**旧v1.0.59蓝星主程序**（会丢失其API Key等本地设置，先备好密钥），再安装v1.0.60并重设API Key/广荣TTS。**不要卸载129KB独立触发器**，它绑定保益的快捷键保留。旧v1.0.59发布文件和分支未覆盖。
- **统一设置直接机身控制**：`BlueStarSettingsScreen.java` 删除音量快捷设置及诊断页/相机的模型重复项/「打开C110+实时预览和机身设置」按钮。顶层「云端AI」仍唯一保存模型与API Key，手机/USB/C110共用。相机→C110+新增**「连接C110相机Wi-Fi」**，使用MainActivity已存在的 `C110WifiBootstrap.requestForCameraMode()` 及系统授权（无VLC/RTSP/相机预览启动）；在同一深蓝页面展示连接、3012身份及9011/9038命令回执。**直接关/开机身提示音**（9011/par0、1）、**灯光关/常亮**（9038/par0、1），**15/30/60秒/自定义5-600秒后关**，复用已有 `C110DeviceControls` 和原倒计时。C110设备设置中保留照片分辨率/OCR准确率实验的显式独立入口；C110CameraActivity预览菜单中的两个云模型重复选择亦移除。
- **硬件安全与已知限制**：`C110DeviceControls.setSoundFromSettings/setLightPolicyFromSettings` 必须先取 `C110WifiBootstrap.connected(activity)` 的**已验证相机专网Network**，对写命令仍再执行3012设备身份校验，严格允许列表仅9011/9038 par0/1；未连接则**只报错，不下发**，绝不误发4003删除或1002变照片尺寸，也不用默认网络连接机身。机身响应状态=0仅表示收到回执，真正音效/指示灯表现需用户真机确认。**原延时关灯本来依赖当前Activity/Network保持存活**：关掉设置、网络断开或Activity被销毁可能取消倒计时，本版保留原行为并在UI提示；避免虚称后台永远可靠。
- **停用已冲突快捷键监听**：HomeActivity/MainActivity/C110CameraActivity均不再创建、调用、消费 `C110VolumeShortcut`，相应设置UI也已删除；历史 `C110VolumeShortcut` 和 `C110VolumeTiming` 测试源码保留存档，不再运行时争夺保益读取的实体音量键。Home手动快捷拍照、手机USB预览手动按键和C110真实快门入口均保留。**音量上双击由保益运行而不是蓝星自身拦截**。
- **成功底层保持**：主页快捷拍照一张原厂1001→完整SD JPEG→共用云OCR→自选广荣微软grtts TTS不跳页，C110原有3012/3016/3001/2015和LibVLC3.6.5两路150ms、竖屏ROI/zoom、手机Camera2/USB UVC、语音/任务/云模型、提示音振动、两导航都进唯一蓝色 `BlueStarSettingsScreen`、相机亮度画质、9011/9038默认机身方法、分辨率AB实验均不重写。
- **真实CI/Release**：新workflow `.github/workflows/c110-settings-direct-controls-20261120.yml` Run **`44680611651` `completed/success`**，最终HEAD与Release target一致 `aae12305e0d7ae908898c284a8ddfe610d8f005d`，旧相机OEM/150ms、云OCR/自选TTS、唯一Launcher/保益兼容、机身3012身份验证及9011/9038指令白名单、唯一云端设置及去掉实体键监听的静态断言、原纯Java测试、完整Android arm64编译、libvlc/UVC音效资源/签名标识/Artifact/独立Release全部通过。新主APK `BlueStar_Guide_C110_Direct_Settings_1.0.60.apk`，**56,732,169 字节**，SHA256 `1bee1690b102e02cd6a940e338b2ffe28993dc18af8ca2eabf87d3c22dd41637`。**不代表vivo C110机身控制已真机验收**。Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-direct-settings-20261120-v0160 ; Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44680611651 ；文档 `cloud-vision-app/docs/C110_SETTINGS_DIRECT_CONTROLS_20261120.md`。
- **唯一v1.0.60主APK直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-direct-settings-20261120-v0160/BlueStar_Guide_C110_Direct_Settings_1.0.60.apk ，已安装的小启动器v1.0.59保留，无需第二次下载。
- **下一步用户真机测试顺序**：先普通开启蓝星不自动拍照、没有额外同包的一键图标；保益小触发器仍保留；设置→云端AI仅有一份模型和API Key；设置→相机→C110+点击连接相机WiFi后，不打开视频即可关/开机身提示音、灯光，提示记录回执和实际硬件变化（定时关灯测试必须保持设置页/连接存活）；主页第二项「实时预览」仍正常；保益双击音量上应仍一回真实高清拍照/云OCR/广荣TTS；若APK签名冲突则卸载旧主程序重装本版并重新设密钥（不要卸载小触发器）。未经用户授权不继续加冗余菜单或新代码。

---

# 2026-11-20｜保益悦听双击音量键→直接触发蓝星C110+单次拍照：v1.0.59 双APK构建发布成功（待vivo真机）

- **用户vivo实际确认**：保益悦听持续开启时会拦截音量键，蓝星原始按下/抬起记录均0；关闭保益读屏后蓝星能记录4按下+4抬起、触发2次，最近间隔82ms，因此**不是蓝星双击600ms算法的故障，不能再重复修改算法或加竞争性无障碍拦截服务**。用户在保益悦听「自定义快捷键功能→音量上键双击→打开应用→蓝星向导 快捷拍照实验」实机证明双击可**自动打开蓝星正常主页**，但目前仅打开首页不自动拍照。用户授权研究并开发一个**特殊启动后一张C110真实1001高清JPEG→云OCR→广荣微软TTS**的入口，保益读屏继续保持正常，普通打开蓝星必须仍只进入主页。
- **基线和源码隔离**：唯一原仓 `vobichngan285-collab/kanzi-assistant`，以已发布 v1.0.58 grtts同步成功CI的 `exp/c110plus-tts-engine-sync-20261120` HEAD `d0e404fb721ccee2a6c2895e9438dfa4018ae4bd` 派生分支 `exp/boyue-shortcut-photo-entry-20261120`，**最终HEAD `02ee5b885536442d73819795b6b341f172fa48a3`**；主程序 versionCode68、versionName `1.0.59-boyue-direct-shot`、独立debug包 `com.vobich.bluestar.c110plusquick159`，不覆盖v1.0.58或更早版本。
- **主APP两个启动入口/仅一条拍照链**：原HomeActivity仍是「蓝星向导」标准 `ACTION_MAIN/LAUNCHER` 入口，普通手动打开只显示深蓝主页，绝不自动拍照。新增同包 `QuickShotEntryActivity`，同样为LAUNCHER但单独名「蓝星一键拍照」，仅将 `ACTION_QUICK_SHOT` 送到原 `HomeActivity`，不涉及新的相机/OCR/诊断页。Home在 `onCreate/onNewIntent/onResume/窗口恢复焦点` 检查显式action，以一次性 `quickLaunchPending` 消费后执行**原有** `quickPhoto()->C110QuickShotController.trigger()`：原C110 1001单次快门/原厂SD JPEG→云端OCR/TTS；许可/Wi-Fi/再resume均不能重复触发。主页按钮手动拍照原逻辑保持。
- **关键现实兼容性**：保益「应用选择」按**应用包名**还是按多个LAUNCHER Activity列出，用户还没有实机证据；绝不宣称两个图标肯定同时可选。因此增加一个**极小的独立启动器APP `:boyuebridge`**，显示名同为「蓝星一键拍照」、独立包 `com.vobich.bluestar.boyuebridge`，版本1.0.59，Release APK **128,523字节**。无相机/录音/网络/无障碍权限、无API Key或图像缓存，仅当前台用户手势启动后（`onWindowFocusChanged`）用显式组件 `com.vobich.bluestar.c110plusquick159/com.vobich.bluestar.cloudprobe.HomeActivity` + `ACTION_QUICK_SHOT` 打开已安装的**主程序**，立即退出。若保益选择器本身能区分同包两个LAUNCHER图标，仅安装主程序并绑定其「蓝星一键拍照」即可；如果它只按应用包列出，就**额外安装小启动器**，绑定独立「蓝星一键拍照」包，普通「蓝星向导」不绑定。主程序和启动器两个APK都是**原仓原实验分支**，不新增任何GitHub仓库或复制原相机实现。
- **保持既有功能**：唯一蓝星深蓝 `BlueStarSettingsScreen`／「主页/预览/设置」与「相机」分类不变，C110 3012/3016/3001/2015、1001真快门、LibVLC3.6.5两条150ms/竖屏ROI、云OCR模型和任务、v1.0.58广荣微软TTS同步、本地语速/音色、USB/手机、9011/9038、分辨率实验不变；4003未验证禁用，1002无写入；音量快捷键600ms原诊断和代码保留，但**保益继续拦截其物理键**，使用的真正触发通道是「保益→打开APP」。不新增无障碍服务、后台监听、自动重复云请求。
- **GitHub CI/发布真实核验**：新工作流 `.github/workflows/boyue-direct-quickshot-20261120.yml` Run `44674925473` `completed/success`，提交/Release target均为 `02ee5b885536442d73819795b6b341f172fa48a3`，原C110 OEM/150ms/TTS/OCR/颜色/启动状态静态断言通过；两APK均Gradle编译、签名标识、LibVLC+UVC资源、zip完整性/sha、Artifact/独立预发布均成功。Release `bluestar-boyue-direct-quickshot-20261120-v0159`：
  - 主APP：`BlueStar_Guide_Boyue_Direct_QuickShot_1.0.59.apk`，56,732,293字节，SHA256 `a62fed3187abade89ec694bfe60901468f312758edfb7e526a7af504af406404`，直接下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-boyue-direct-quickshot-20261120-v0159/BlueStar_Guide_Boyue_Direct_QuickShot_1.0.59.apk
  - 小触发器：`BlueStar_Boyue_QuickShot_Trigger_1.0.59.apk`，128,523字节，SHA256 `0741facf411474974b85d1ee678a375189572cc2ba7ce29076a75ed8912ce955`，直接下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-boyue-direct-quickshot-20261120-v0159/BlueStar_Boyue_QuickShot_Trigger_1.0.59.apk
  - Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-boyue-direct-quickshot-20261120-v0159 ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44674925473 ；设计/真机验证 `cloud-vision-app/docs/BOYUE_DIRECT_QUICKSHOT_20261120.md`。
- **真实待验**：**GitHub CI成功不等于保益选择器能列出多个同包LAUNCHER，也不代表用户vivo真机已自动拍到照片/朗读**。主APP v1.0.59 新包独立，需要在其设置重新填写API Key并选择广荣微软语音（小启动器不用配置）。先正常点「蓝星向导」必须只进主页；手动点主页快捷拍照一张确认。然后保益保持开启，在「上音量键双击→打开应用」选择单独「蓝星一键拍照」；如果找不到则安装小APK再选。双击后应进入主APP并**只执行一次**C110快拍云OCR语音，Wi-Fi许可回到Home不重复快门；如果保益触发了而只进主页，再查是否绑定的是普通「蓝星向导」图标或target版本匹配。没有新授权不要继续加模块和界面，也不要覆盖历史成功包。

---

# 2026-11-20｜修复 C110+ 主页快捷拍照无法调用自选广荣微软 TTS：v1.0.58 CI/Release已成功，待vivo实机验收

- **问题背景/用户授权**：用户尚未下载测试v1.0.57，补报已持续存在的问题：在本版主页「快捷拍照」中，即使选择广荣微软 TTS 也无法以该语音朗读；而选择第二项手机/USB预览后拍照能正常用广荣自选音色。用户要求核查v1.0.57是否真正修复；实际源码检查确认**没有修好**，随后明确授权把语音同步问题**一起修复**，不修改已成功的相机与统一设置。
- **根因从实际源码核实**：`MainActivity.changeSpeechEngine()` 写入 `kanzi_settings/app_tts_engine_package` 之后立即 `initializeSpeech()` 重新创建选中包的 TTS，且**不强制 setLanguage**；但 `HomeActivity.onCreate` 首次构造 `C110QuickShotController -> C110FrameReader`，旧 `C110FrameReader` 仅首次构造时读一个语音引擎包名，用户去统一设置改引擎再回主页，`HomeActivity.onResume` 不会同步已有 Reader，继续使用旧引擎；Reader 还额外执行 `tts.setLanguage(Locale.SIMPLIFIED_CHINESE)`，可能覆盖广荣等第三方引擎语言偏好。此外云OCR流式分段处理有 `!ttsReady` 即直接return的无提示丢词路径。
- **修复范围**：在原仓库 `vobichngan285-collab/kanzi-assistant`、已发布 v1.0.57 分支 `exp/unified-settings-volume-diag-20261120` HEAD `bb17aca82cdb73ff62492afa166518aef2e3bcb6` 派生新隔离 `exp/c110plus-tts-engine-sync-20261120`，**最终源码 HEAD `d0e404fb721ccee2a6c2895e9438dfa4018ae4bd`**；v1.0.58/versionCode67/versionName `1.0.58-c110plus-tts-sync`，独立appId `com.vobich.bluestar.c110plustts158`，**不覆盖**v1.0.57/旧包。
- **准确改动**：只修改 `C110FrameReader.java`、`C110QuickShotController.java`、`C110CameraActivity.java` 三处TTS路径，另增独立文档/CI和包版本；保留已成功所有相机和UI代码。Reader 新 `syncSpeechSettings()` 动态读取 `app_tts_engine_package`、`local_tts_follow_default`、`local_tts_rate`：仅在真实引擎包变化时stop/shutdown并按指定引擎包新建 TextToSpeech，语速变化仅setSpeechRate；与MainActivity工作路径对齐**取消对广荣等自选引擎强制`tts.setLanguage(Locale.SIMPLIFIED_CHINESE)`**。引擎OnInit用generation防旧回调污染；初始化失败、speak失败显示消息、**不暗中回退到系统默认语音**。云OCR返回早于引擎准备时，仅保留本次请求的待朗读文字（最多100k字符），OnInit成功后播放；新请求、停止、销毁或明确初始化失败就清空，绝不缓存跨照片过期内容。
- **同步触发点**：`HomeActivity.onResume → C110QuickShotController.onResume → C110FrameReader.syncSpeechSettings`；每次 `C110QuickShotController.trigger` 再核对一次；C110视频专页 `onResume` 亦同步；视频取帧、原生高清JPEG OCR和再次朗读均检查设置。**主页第一项仍原位单次1001→云OCR→TTS，不进入诊断/实验/独立页面**。
- **前一版全部保留**：唯一 `BlueStarSettingsScreen` 蓝星深蓝配色/相机分类/完整旧设置，主页+预览共享入口，600ms音量双击及原始DOWN/UP诊断（物理按键仍待用户真机确认）；C110 OEM 3012/3016/3001/2015、1001单张高清原图、LibVLC3.6.5两路150ms、竖屏ROI、云OCR自选模型/API Key/本地TTS、USB/手机相机、9011/9038和四档照片准确率实验保持；未验证4003删除保持禁用、照片1002不改。
- **已核实GitHub CI/Release**：独立工作流 `.github/workflows/c110plus-tts-engine-sync-20261120.yml` Run **44669336871** 状态 `completed/success`，全部静态回归/OEM/语音同步断言、原Java测试、Android arm64完整编译、APK签名身份+LibVLC/TTS音效/USB so、Artifact和独立预发布成功；Run HEAD及Release target均 `d0e404fb721ccee2a6c2895e9438dfa4018ae4bd`，Release资产 `56,732,177` 字节，SHA256 `2481e07633d876828fba39d2f24b4b5609772518a55ce7c8e143e214ae71fdd4`。
- **唯一推荐APK浏览器直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-tts-sync-150ms-20261120-v0158/BlueStar_Guide_C110Plus_TTS_Sync_150ms_1.0.58.apk ；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-tts-sync-150ms-20261120-v0158 ；CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44669336871 ；详见 `cloud-vision-app/docs/C110PLUS_TTS_ENGINE_SYNC_20261120.md`。
- **重要真实性边界/下一步**：**CI成功不代表真正听到广荣微软声音**。用户vivo X200 Pro真机须直接安装v1.0.58（不必先装v1.0.57），在**这个独立包的设置**重新配置有效百炼API Key，并选择系统已安装的「广荣微软」语音引擎，先试听正常后返回主页点一次「快捷拍照」，检查C110取图/OCR文字及**实际音色**；不退出App切换另一音色重复，再对比手机/USB预览声音是否一致。云HTTP403/Key缺失不能假装是TTS失效。**音量双击是否识别和是否取到原始按键**仍取决于设备诊断记录，本次未宣称已真机修复。不要再擅自添加页面或新配色。

---

# 2026-11-20｜统一主页/预览设置 +「相机」分类 + vivo 音量键可诊断实验版 v1.0.57：CI/Release成功，待真机

- **用户明确报告并授权**：v1.0.55 在 vivo X200 Pro 中双击音量键仍不触发C110+拍照；主页点设置是 `HomeActivity.settingsTab` 简化页，手机/USB预览点设置则是 `MainActivity → LargeSettingsScreen`，视觉和内容两套重复，首页还有「更多设置」嵌套旧设置。要求**先核查代码，再修改**：只保留一套蓝星深蓝设置，主页和预览入口一致；在统一页面添加「相机」分类，完整归纳旧相机设置并将原语音/任务/云端/提示/诊断各项保留；解决音量键识别但不能凭编译宣称vivo实机有效；不破坏既有的直接C110快拍/150ms/云端OCR/TTS。
- **基线/仓库/版本**：唯一仓库 `vobichngan285-collab/kanzi-assistant`；基于已发布 v1.0.55 `exp/c110plus-inline-quick-photo-20261120` HEAD `613c051de9810ce0b372a2afd725178f9ccec909`；新实验分支 `exp/unified-settings-volume-diag-20261120`；**最终源码 HEAD `bb17aca82cdb73ff62492afa166518aef2e3bcb6`**，versionCode66、versionName `1.0.57-unified-settings-volume`，debug独立安装 `com.vobich.bluestar.c110plussettings157`；不覆盖任何旧包。此前 v1.0.56 处于增量开发过程且曾CI发布/中途又有编译和已存在release冲突，不推荐用户安装；**唯一最终推荐 v1.0.57/v0157**。
- **根因与彻底统一**：历史两套UI的真实根因是 HomeActivity 自建 `settingsTab`+“更多设置”与 MainActivity `LargeSettingsScreen` 并行，且 MainActivity 中还残留无人使用的旧 `showSettingsDialog`/手绘 `addSimpleSettingsRow`。v1.0.57 将原 LargeSettingsScreen 的全部功能迁到**唯一 `BlueStarSettingsScreen.java`**、删除原 LargeSettingsScreen 文件；清除 Home 简化设置/「更多设置」逻辑和 Main 已废弃旧弹窗/重复页面代码。主页底部「设置」进入同一 `MainActivity settingsOnly → BlueStarSettingsScreen`；手机/USB预览顶部/底部设置在当前MainActivity上显示**同一个**蓝色页面，不另起旧界面；底部统一「主页/预览/设置」蓝色导航。蓝星色系 `#091B32` 深蓝底、`#176BAC` 选中、`#2C547D`按钮等保留，不再借用第三方黄黑色。
- **新「相机」分类**：原手机/USB源切换、画面旋转/水平镜像/垂直翻转/USB左右校正、缩放恢复1×、USB硬件亮度/对比度/饱和度/增益读取/写入/复位、USB自动切换和插入启动；新增C110+专页（可明确选择进入其实时视频/机身设置或独立照片分辨率准确率实验），OCR/视觉模型共用；新增**双击音量上/下、长按音量上/下、关闭**与实体键诊断子页。C110+机身9011声音/9038灯光因依赖实际已连接相机，仍由**显式进入C110专页**的硬件控制操作，设置主页不偷偷连接WiFi或发送OEM写命令。C110+未经真机复核的4003删图继续禁止、照片大小1002不动。
- **完整保留旧功能**：本地TTS可选引擎、跟随系统默认语速（中途发现遗漏并补迁）、0.5–3倍语速与试听、英文/数字/符号过滤；任务预设提示词编辑/隐藏恢复、增删自定义；云端OCR/视觉模型、逐行/全文速度、图片分辨率策略、API Key、联网追问与模型用量统计/清除；提示音与振动开关和试听、最近OCR文字/重读、匿名运行诊断/日志复制导出/清除等都留在同一个BlueStarSettingsScreen。Home settings-only 更改手机/USB摄像头来源仅保存 `kanzi_settings`，**下次按「预览」才真启动镜头**；关闭设置返回Home，不暴露默认预览。
- **音量键实际问题的谨慎处理**：原双击窗口360ms过短且没有记录真实键事件。现改为**600ms双击**（长按650ms保持），基于 `KeyEvent.getEventTime()` 和新增 Android-free `C110VolumeTiming`，纯Java `C110VolumeTimingTest` 通过；C110VolumeShortcut记录**两侧音量按键的实际前台DOWN/UP次数、匹配触发计数和间隔**（设置为关闭也能看原始事件），不记录照片、语音、密钥或设备标识。入口「设置→相机→音量键快捷拍照」可选择、刷新、清除。**设置内测试识别成功不执行真实1001**；主页/手机USB预览/C110视频页原拍照快捷入口仍保留；后台/息屏未实现。**这仍不能证明vivo实体键已解决**：真机按键若接收计数为0，需排查Android/vivo/读屏辅助服务截获；有按下/抬起但未触发则依据实际间隔再修。
- **保留已成功相机与云链路**：用户点击首页第一项仍是当前主页原位触发**一次**C110+ 1001 高清原SD JPEG→复用当前选择模型云OCR→TTS，不跳诊断/实验页；C110+ 3012/3016/3001/2015、LibVLC3.6.5 两路150ms、竖屏居中真实ROI、9011/9038已有机身控制、手机Camera2/USB UVC/阅读任务仍保留；不因统一设置而重写底层。
- **GitHub 完整CI核验**：工作流 `.github/workflows/bluestar-unified-settings-volume-20261120.yml`，Run `44667233480` **completed/success**，单元/静态检查包含旧设置文件删除、新设置唯一入口、Camera分类/全部原功能、Home不自动开相机、600ms时间窗纯Java边界、旧OEM/USB/150ms/云模型/资源回归；Android arm64编译、APK AppID/签名/音效+libvlc+UVC资源、Artifact及独立预发布成功。Run HEAD、Release target都是 `bb17aca82cdb73ff62492afa166518aef2e3bcb6`；APK `56,715,797`字节，SHA256 `0fd1ae4caab584ebeba1869231e9e700a922616a383968d9f2027faa6f357288`。**CI成功仅证明程序编译和代码结构检查，不代表vivo实体键已测试或所有设置操作已在真机验收**。
- **唯一v1.0.57直接下载URL**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-unified-settings-volume-150ms-20261120-v0157/BlueStar_Guide_Unified_Settings_Volume_150ms_1.0.57.apk ；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-unified-settings-volume-150ms-20261120-v0157 ；Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44667233480 ；代码说明 `cloud-vision-app/docs/UNIFIED_SETTINGS_VOLUME_DIAG_20261120.md`。
- **接下来真机验收顺序**：本版独立app需要新输入自己的API Key；先点主页设置和手机/USB预览设置对比**是否同一页面、全部设置可访问、相机分类是否完整**，Home修改源不应触发预览；然后在**设置→相机→音量键**，双击已选音量键、点“刷新按键记录”，查看按下/抬起/识别成功次数与间隔（0次则系统没分发，切勿虚构修复），离开设置后测试单次快门；再核对主页快捷拍照不跳页、云OCR朗读、C110视频150ms、USB/手机功能。未经下一步授权不要继续重写或覆盖已成功的版本。

---

# 2026-11-20｜修复主页快捷拍照误跳诊断/单独页面：v1.0.55 已CI发布（待真机）

- **用户真机截图与明确要求**：v1.0.54 主页点「快捷拍照」不是立即拍照朗读并保留主页，而是错误地进入大面积空白的「C110+快捷拍照」独立Activity，显示「取帧已完成，但识别失败：请先在三横杠设置中填写百炼 API Key」、再拍一张与返回主页按钮。用户反复明确：**第一项点击即执行C110+单张原厂高清快门→照片下载→当前模型云OCR→本地朗读，必须停留在原主页，无任何独立拍照/诊断/实验页面**。用户愤怒反馈此设计错误。截图中的「取帧已完成」表明获取照片成功，但由于此包没有配置 API Key，云端识别不能成功；**错误跳页**和**密钥未配置**是两个不同问题。
- **根因核实**：此前代码 `HomeActivity.quickPhoto()` 实际是 `startActivity(new Intent(this,C110QuickPhotoActivity.class))`；`MainActivity.shortcutPhoto()` 在未连接WiFi时也走该新Activity；这些是前几个版本把简单行为错误页面化，不是原相机OEM问题。旧独立包名相互隔离，API Key 无法跨实验包自动迁移。
- **修复/范围**：基于唯一原仓 `vobichngan285-collab/kanzi-assistant` 已发布 v1.0.54 `exp/c110plus-home-bluestar-blue-20261120` HEAD `9a5e243ac1fa3b1dd5bdb52c984bba4021b09401`，新分支 `exp/c110plus-inline-quick-photo-20261120`，最终 HEAD `613c051de9810ce0b372a2afd725178f9ccec909`；versionCode64 / versionName `1.0.55-c110plus-inline-photo` / 独立安装ID `com.vobich.bluestar.c110plusinline155`（不覆盖旧版）。完全**删除** `C110QuickPhotoActivity.java`、移除对应 Manifest Activity。创建**无界面** `C110QuickShotController`，Home主页的 `quickPhoto()` **仅在本页执行** `quickShot.trigger()`，在第一项下面显示小行进度，不调用startActivity/不切页/不启动手机USB或VLC。拍照按钮与已有Home音量快捷键共用此入口；未连C110时启动已有原厂式WiFi申请并等待**同一次触发**，有网时异步执行且只执行一次 `C110WifiCamera.shootAfterLivePreview(owner)` 原厂1001、完整JPEG、`C110FrameReader.recognizeOriginalJpeg`，通过已共享任务、模型、密钥与本地TTS朗读。忙锁不允许重复发送快门或云OCR，不自动重试。
- **预览相关**：MainActivity 手机/USB预览右上「快拍」与音量快捷键不再进入该多余Activity：已有C110网络沿用以前经验证高分辨率真实快门及录像暂停/恢复，尚未连接则复用无界面Controller申请WiFi并同次拍照；主预览保持原逻辑。C110独立视频页此前已成功150ms/竖屏/ROI维持不动。
- **保持原视觉和安全基线**：主页蓝星深蓝背景 `#091B32`、按钮 `#2C547D`、选中 `#176BAC`、中英文无障碍名称及「主页/预览/设置」三栏不改；原OEM 3012/3016/3001/2015、C110真实1001、LibVLC两路150ms、9011/9038、C110原SD完整JPEG、云OCR选定模型/TTS、手机USB预览、任务和20M/10M/8M/3M独立准确率实验保留。不放开未经C110+真机验证的4003删除，不修改1002照片尺寸。OCR缺Key的错误文案改为「请先在主页的设置中填写百炼 API Key」。
- **真实限制**：v1.0.55 独立包名，仍需在**本版设置**输入有效百炼Key才能云端OCR及朗读，不会偷读其他独立安装包的秘密；拍照可完成不等于OCR成功。此前用户反映**双击音量键无效**，本次修复的是不再跳页，实体按键识别算法没有验证修改，不能宣称音量键已修复。通过后台自动构建也不能替代用户 vivo X200 Pro 实测。
- **GitHub CI/Release 已真实核验**：Run `44584835977` `completed/success`，纯Java/静态检查明确验证已删除拍照独立Activity/Manifest入口、Homepage直拍且不含startActivity、单次1001、复用原OCR/TTS、蓝星配色、USB/旧OEM指令/150ms/旧实验回归；完整 Android arm64 APK、签名身份、USB/LibVLC音效资源、上传Artifact、独立Release成功。Run HEAD和Release target都为 `613c051de9810ce0b372a2afd725178f9ccec909`；APK `56,732,185` 字节；SHA256 `27aa702130201017661fec588e9ffe37150c083e2fa783621a6bb01cba7a5254`。
- **APK浏览器直接下载**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-inline-quickphoto-150ms-20261120-v0155/BlueStar_Guide_C110Plus_Inline_QuickPhoto_150ms_1.0.55.apk ；[Release](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-inline-quickphoto-150ms-20261120-v0155)；[Actions](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44584835977)，文档 `cloud-vision-app/docs/C110PLUS_INLINE_QUICK_PHOTO_20261120.md`。
- **下一步真机**：本版主页设置填入有效API Key→已连接C110+条件下点主页第一项一次，**不离开蓝色主页**，应在按钮下简短显示「正在拍摄、已拍摄/识别」并真实朗读；照片/SD完整性及1001单次；无WiFi时同次系统授权后只拍一次；主页/手机USB预览按键快捷键是否真正被系统分发需另外日志证据，不能凭CI自称修好。禁止再新增快速拍照页面。

---

# 2026-11-20｜纠正第三方参考图造成的黑黄主页，恢复蓝星原深蓝UI：v1.0.54已CI发布（待真机）

- **用户明确纠错**：第三方「豆芽看见」截图仅供「大字体、功能键纵向排列」的参考，不代表颜色规范。用户强烈要求蓝星向导主页恢复与原有预览/设置一致的**蓝星深蓝色**，不能把黄黑配色直接照搬。此前 v1.0.53 实际主页使用 `#07090D` + `#F5C543`，同时预览和设置仍使用原有的蓝色，导致两套界面割裂。
- **仓库/基线/安全边界**：唯一原仓 `vobichngan285-collab/kanzi-assistant`；从已发布 v1.0.53 的 commit `52cb9392a1d2991708e1ff2dc0c967246809fb68` 派生 `exp/c110plus-home-bluestar-blue-20261120`；最终 HEAD `9a5e243ac1fa3b1dd5bdb52c984bba4021b09401`；versionCode63、versionName `1.0.54-c110plus-home-blue`、新 `com.vobich.bluestar.c110plushome154`，独立包，**不覆盖**此前任何版本。
- **本版修改范围极小**：仅 `HomeActivity.java` 中 UI 配色常量和文本/分割线/底部三栏按钮颜色；主页深蓝底 `#091B32`、主字 `#F3F8FF`、副字 `#C3D7EF`、图标浅蓝 `#B9DCF8`、菜单选中 `#176BAC`、普通按钮 `#2C547D`、分割线 `#265077`，复用 `LargeSettingsScreen` 的既有蓝星主题；移除原黄黑 `#F5C543`、`#07090D`。保留原有五项功能键顺序、三栏、朗读任务/模型/语音无障碍、图标/按钮实现，**没有修改**C110、相机、云端或音量快捷键代码，不重新开发成功的 OEM/LibVLC 150ms 等功能。
- **必须诚实标记已知待修**：用户之前明确报告主页点“快捷拍照”却进入独立 `C110QuickPhotoActivity` 页面而不是按一次直接在主页后台执行并播报，及 vivo 上“双击音量键无效”。v1.0.54 **仅修颜色，不声称已修复这两处行为**。后续若用户授权相关逻辑修复，要先从最小改动和真机日志入手，不要通过反复做更多冗余界面造成退化。CI成功不代表真机无障碍/物理手势通过。
- **构建/发布核验**：GitHub Actions Run `44581518630` `completed/success`，基于提交 `9a5e243ac1fa3b1dd5bdb52c984bba4021b09401`，编译、旧有 Java/相机集成静态回归、颜色断言、APK身份、LibVLC/USB/TTS资源、上传 Artifact、独立预发布均成功。资产大小 56,732,205 字节，SHA256 `f341e0d0a9c9f2b1b827ef3209ce367f156059eb74d4e4fa43ca22baefcf5503`。
- **唯一 v1.0.54 APK 直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-blue-home-150ms-20261120-v0154/BlueStar_Guide_C110Plus_Blue_Home_150ms_1.0.54.apk ，Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-blue-home-150ms-20261120-v0154 ，Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44581518630 ，技术说明 `cloud-vision-app/docs/C110PLUS_HOME_BLUE_PALETTE_20261120.md`。
- **后续真机**：先看主页与蓝星预览/设置在颜色上是否统一（此版验证范围）；如接下来重做“一键直接快拍”、音量键快捷触发，请在现有已发布基线修改已有单次拍照路径，不新增诊断页、不换配色、不要再次误报通过。

---

# 2026-11-20｜C110+ v1.0.53 主页/快捷拍照/自定义音量键/预览设置不跳转实验版已发布（待真机）

- **明确用户要求**：仿照用户提供的「豆芽看见」风格，蓝星向导打开默认大字体功能主页，**不自动启动手机/USB摄像头**；顶部第一项「快捷拍照」触发一次 **C110+** 原厂1001高清拍照→完整JPEG→按主页OCR/任务模型朗读；底部「主页 / 预览 / 设置」。点击「预览」如果插着USB则选择USB，否则手机；不进入预览时手机摄像头不启动。增加可配置的**双击音量上/下键、长按音量上/下键（可关闭）**，只绑定C110+快捷拍照，首页、普通手机/USB预览及C110+实时视频预览均支持；仅应用在前台、屏幕亮时拦截，单次音量短按继续调节音量，不承诺后台/息屏。用户还反馈C110预览按「☰」设置会立即回到默认页，要求保持在预览里打开设置。用户明确授权开发独立实验版。
- **仓库/版本**：唯一原仓 `vobichngan285-collab/kanzi-assistant`；基于已发布 `exp/c110plus-model-sync-20261120` v1.0.50 HEAD `cdd131908fbed3e22c9ee351a707272c60108dce`，新分支 `exp/c110plus-home-quickkey-preview-settings-20261120`，最终 HEAD `52cb9392a1d2991708e1ff2dc0c967246809fb68`，versionCode62、versionName `1.0.53-c110plus-home-key-preview`，独立包名 `com.vobich.bluestar.c110plushome153`；不覆盖旧版。自动多次CI在改动途中曾对v1.0.51/v1.0.52中间快照启动，不作为最终下载版本；**只交付已验证的v1.0.53/v0153**。
- **实现**：新增 `HomeActivity` 并设为唯一 LAUNCHER、继承普通Activity（非CameraActivity），新 `C110QuickPhotoActivity` 只启动C110 WifiBootstrap、单次1001快门下载及复用C110FrameReader朗读、不初始化手机/UVC/VLC；新 `C110VolumeShortcut` 为前台按键分派，默认双击音量下，360ms双击窗口、650ms长按，单按经 AudioManager 调音量，按键手势在主/预览/C110活动中复用。Home还有模型、API Key/任务、原高级设置入口，设置数据与v1.0.50共享同一新包偏好；`MainActivity` 只由“预览”进入，点击自动选当前插入USB或手机摄像头，保留原 Camera2/AUSBC 识别和AI对话，返回主页释放预览。为避免自动跳起摄像头，此实验版USB自动启动的首次默认改为关闭、设置模式阻止启动摄像头。C110CameraActivity 顶部「☰」不再 `returnToMain("settings")`，改为同页 `showC110Settings()`，设置对话框不结束原VLC活动；C110页新从Home直接启动时，缺少MainActivity热点Owner，已修正独立WifiBootstrap **requestForCameraMode()**，首次连接可打开原厂式热点选择。原C110 OEM 3012→3016→3001/par1→2015/par1、LibVLC 3.6.5两路150ms缓存、竖屏铺满+OCR ROI、1001单张高清、9011静音/9038灯控、共享云模型、TTS/USB路径均未重写；C110+ 4003删图仍不开放，仍不写1002照片尺寸。
- **重要实机验证边界**：在C110+独立快拍新页、Home等前台按键应触发一次原厂C110拍摄；连接未成功时先弹系统WiFi许可，不可能瞬间无连接完成；按键只前台有效，vivo Android 16系统可能拦截某些手势。相机/云端忙锁应防止重复拍照，不能自动重试付费云请求。MainActivity设置专用模式及USB不启动摄像头是否完全符合目标仍须真机检查。首次安装独立包需要另设百炼 API Key；HTTP403为相应模型额度问题，不等于C110拍摄失败。CI成功**不代表真机通过**。
- **最终构建已核实**：工作流 `.github/workflows/c110plus-home-quickkey-preview-20261120.yml`，run `44577677882`，状态 `completed/success`，包括 Java/Android 编译、原有OCR回归静态和纯Java检查、LibVLC/本地TTS/USB so与 APK身份校验、Artifact及独立Release。Run HEAD与Release target均为 `52cb9392a1d2991708e1ff2dc0c967246809fb68`，Release资产 **56,732,217字节**，SHA256 `b4ddc0f00542f4d03f45b1275f2d6859f5fffdd8135862b2d4a6562789018fd6`。
- **推荐唯一APK直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-home-quickkey-preview-150ms-20261120-v0153/BlueStar_Guide_C110Plus_Home_Volume_Key_150ms_1.0.53.apk ，Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-home-quickkey-preview-150ms-20261120-v0153 ，Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44577677882 ，详细说明 `cloud-vision-app/docs/C110PLUS_HOME_KEY_PREVIEW_20261120.md`。
- **下一步**：优先用户vivo X200 Pro真机：打开是否只主页，不启摄像头；“快捷拍照”及Wi-Fi首次连接是否一次1001、朗读有效；进入手机/USB预览自动选择；音量键双击/长按四种模式、单击仍调音量、预览中可快拍C110且无多重1001；C110直播预览点击「☰」是否仍在原页面、设完直接关弹框视频不黑屏/不跳页；再核对150ms及竖屏ROI/20M10M8M3M分辨率实验旧需求。不要擅自再次修改或覆盖已发布成功版本。

---

# 2026-11-20｜C110+ 新版主页朗读模型与相机/综合实验云OCR同步 v1.0.50 已CI发布（待真机）

- **用户现象与明确授权**：用户在**同一版蓝星向导主页**选择好朗读OCR模型，手机摄像头可正常识别朗读；进入 C110+ “照片/OCR 综合实验”却固定返回 `HTTP 403 AllocationQuota.FreeTierOnly`，截图底部一直写 `模型=qwen-vl-ocr`。用户明确这是**本新版内部不一致**，授权“现在就开始做”。已检查实际代码，根因是 `C110ResolutionLabActivity` 独自将模型请求**写死** `OcrModelCatalog.DEDICATED = qwen-vl-ocr`；原 `C110FrameReader` 实时预览及专页高清OCR调用原先**已经读取**共享模型偏好。因此无需重写 Wi-Fi、播放器或相机命令。
- **分支/HEAD/身份**：唯一原仓 `vobichngan285-collab/kanzi-assistant`；从已CI发布 v1.0.49 `exp/c110plus-portrait-cover-roi-20261119` 派生隔离 `exp/c110plus-model-sync-20261120`；最终HEAD `cdd131908fbed3e22c9ee351a707272c60108dce`；完整应用 versionCode59 / versionName `1.0.50-c110plus-model-sync-150ms`，独立 applicationId `com.vobich.bluestar.c110plusmodel150`。**不覆盖** v1.0.49、v1.0.48、四档原厂照片尺寸A/B实验旧包或正式RC25。
- **模型真正同步**：综合实验页通过 `getSharedPreferences("kanzi_settings",MODE_PRIVATE)` 读取与主页同一 `cloud_ocr_model_v1` 并 `OcrModelCatalog.normalize`，点击拍摄时冻结 `chosenModel` 及同源 `OcrSpeedExperiment.PREF`；原SD完整JPEG、`TaskCatalog.defaults()[0]` 的一致朗读原文指令、原 `CloudVisionClient.recognize` **一次**云端请求不变；不再固定 qwen-vl-ocr，不暗中回退到其他模型/自动重试。每次实验记录真实模型ID和速度模式，比较不同照片档位应固定相同模型、相同提示/速度；混模型不可直接合并排名。综合实验入口直接显示当前模型并可手动改（写回主页共享prefs），拍摄中禁止更改。C110专页“相机”菜单新增文字朗读OCR及视觉任务模型选择（与主页共用两项独立偏好）；MainActivity `onResume` 从 C110 返回后刷新缓存模型字段，主页显示与配置同步。
- **保持原已成功功能**：C110+ 3012→3016→3001/par1→2015/par1，150ms `C110CameraActivity` + `C110LivePreview` 两条缓存、LibVLC 3.6.5、**竖屏**官方式居中铺满 + 实际OCR ROI、1001只拍一张、原高清照片、机身静音/灯控、TTS/USB/手机摄像头、四档原厂照片尺寸/准确率实验和诊断日志继续保留；不添加 `1002` 写尺寸、不允许尚未真机验证的 C110+ `4003` 删除。
- **安全与云端费用**：该次截图 403 是 **qwen-vl-ocr 当前免费配额限制**，不是相机取图失败；改用用户主动选择的模型后仍需核实该模型额度及计费，未承诺所有模型无费用或永不403。新独立包需另填百炼 API Key；任何云请求仍仅用户主动触发，不自动换成收费模型。
- **CI/Release已确认**：新增独立工作流 `.github/workflows/c110plus-model-sync-20261120.yml`，源码静态检查验证共享偏好/按快门冻结模型/诊断真实模型/一次拍摄一次云请求、旧OEM与两路150ms及竖屏ROI原有逻辑、纯Java测试通过；完整 arm64 Android APK、App ID/版本签名、LibVLC/USB so/原音效、ZIP/SHA、Artifact/独立预发布均为成功。**Run [44565962487](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44565962487)** `completed/success`，HEAD及Release target均为 `cdd131908fbed3e22c9ee351a707272c60108dce`。
- **APK正式直链（56,715,765字节；SHA256 `64b7a2bf18c25cd2698e57a5c6d602a21ce30f2847edcb66fefd7fd0d9ea4a6d`）**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-model-sync-150ms-20261120-v0150/BlueStar_Guide_C110Plus_Model_Sync_150ms_1.0.50.apk ，Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-model-sync-150ms-20261120-v0150 。源码说明 `cloud-vision-app/docs/C110PLUS_MODEL_SYNC_20261120.md`。
- **下一步真机（未验收）**：v1.0.50**本版自己的主页**选择一项可用 OCR 模型 → 进入 C110+ 专页 → 相机→照片尺寸/OCR综合实验，核对界面当前模型已同步、实际拍一张后所请求模型和诊断记录一致，避免继续走 qwen-vl-ocr 免费配额；切换专页模型后回主页同样生效；实时取帧/高清拍照仍能正确按当前朗读任务和模型朗读。若仍403，先检查新的实际模型ID和该模型配额，不要将网络/相机错误与云配额混淆。此前 20M/10M/8M/3M 四档照片准确率实验仍需用**相同模型**真机完成，v1.0.49 竖屏Cover/ROI 同样待实机检验。CI成功不等于真机效果成功。

---

# 2026-11-19｜C110+ 官方式竖屏铺满预览 + 所见即OCR v1.0.49 已CI发布（待真机）

- **这次用户明确纠正并授权**：用户提供原厂 SJCAM Zone 竖屏预览截图，指出 v1.0.48 的“全屏”错误地强制横屏；明确要求手机**始终竖屏**、视频**等比例铺满屏幕**（可居中裁掉左右两侧，不拉伸）、保留顶部必要操作及底部拍照/变焦；画面所见区域必须与 OCR 提交的区域一致，用户明确“是的，现在改”。**不要再误做强制横屏，不要重写已成功的相机连接/播放器**。
- **仓库/基线**：唯一原仓 `vobichngan285-collab/kanzi-assistant`；从已发布 v1.0.48 分支 HEAD `cf7d525d8e5d92ab93ebad08de719feed5e44f18` 派生独立 `exp/c110plus-portrait-cover-roi-20261119`；最终 HEAD `ac08a2a6811c9d0993d34437ccdc4bf15b3d82fa`；新 `com.vobich.bluestar.c110plusportrait149`，versionCode58，versionName `1.0.49-c110plus-portrait-cover-150ms`。独立 APK 不覆盖 v1.0.48、旧实验或正式 RC25。
- **专页竖屏全屏修复**：`C110CameraActivity` 的 `全屏/退出` 按钮**不再调用 setRequestedOrientation**，页面 portrait Manifest 不变；仅调整原 **同一** `VLCVideoLayout` 的尺寸和显示 transform，16:9 原始视频等比例 `CenterCrop` 到竖屏可见区域，保留必要顶栏、独立底部“高清拍照”、缩放条、退出按钮和系统状态栏；不用重建播放实例/复发 OEM 命令。为避免 vivo 高分屏重新分配超大 Surface 或 `PixelCopy` 超 900 万像素限制，实际 VLC View 尺寸最大按原 2K 1920×1080 安排，**显示层**按宽/高比例补足竖屏 Cover；`C110FrameReader` 对过大的竖屏 PixelCopy 在请求前硬件缩样到至多约 2MP。仅渲染/取帧处理优化，未修改 150ms 缓存。
- **视频OCR/高清照片实际 ROI 与预览同步**：新 `C110ZoomGeometry.centeredCover(w,h,zoom,viewportAspect)` 同时用于视频实时 `PixelCopy` 帧、SD 原生高清JPEG；**普通非全屏 1x**仍原图 byte[] 直接上传；**竖屏铺满 1x**已将原横画面裁成中心竖幅，实际 OCR 就裁相同中心区域；再拨动1～4x时在可见竖幅上继续中心裁剪。高清通道仍仅一次原厂 `1001` 拍照、下载**完整未修改 SD 原始JPEG**，上传云端前使用 `BitmapRegionDecoder` 按 ROI 部分解码、必要时JPEG92编码；失败不伪装已裁切并改传整图。预览与照片的实际 FOV/防抖差异须真机核对。主页内联预览、手机/UVC、四档原厂照片大小准确率实验代码不改。
- **保证既有成功基线**：标准原厂 OEM `3012→3016→3001/par1→2015/par1`、vivo Wi-Fi、LibVLC 3.6.5 + 可见 VLCVideoLayout、两条路径 network/live **150ms**（file/sout 300ms）、`1001` 单次快门、9011 静音/9038 灯控、原云端请求/TTS/USB/手机路径保留；**C110+ 4003 删图未验证，保持隐藏且不自动清理**。150ms 是缓存不是分辨率，不宣称可增强真实光学画质。
- **构建验证及发布**：单元/静态检查通过（portrait 9:16 cover 1x/2x、正常16:9、8M照片ROI、原OEM/150ms/安全回归）；第一批CI曾因测试脚本断言写错、Java onCreate(Bundle b) 内 layout lambda `b` 命名冲突而失败，已纠正并保留历史不冒充成功。**最终 GitHub Actions Run 44562094578** `completed/success`，所有编译、APK身份/LibVLC/USB本地so/音效、Artifact及独立Release成功；运行 HEAD 与 Release target 均为 `ac08a2a6811c9d0993d34437ccdc4bf15b3d82fa`。
- **唯一推荐 APK 直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-portrait-cover-roi-150ms-20261119-v0149/BlueStar_Guide_C110Plus_Portrait_Cover_ROI_150ms_1.0.49.apk ，大小 56,715,769 字节，SHA256 `63ffa114d30c61a138e6c27dcc5d323e4dc3677f8ef01ee6b92308b52f3f2685`。Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-portrait-cover-roi-150ms-20261119-v0149 ，Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44562094578 。
- **下一步真机检查（尚未验收）**：在 vivo X200 Pro 打开C110+专页→点“全屏”，确认**不横转、不拉伸，竖屏画面铺满且可退出、无黑屏/重连**；1x全屏只识别视觉中间竖幅，2x/4x只识别对应放大区域；全屏一次真实高清拍照，原厂JPEG完整、OCR上传区域正确；切普通模式1x应回全幅。核对 RTSP 与 SD 原照片视场角；长时间150ms不卡顿。新 applicationId 需单独填写有效 API Key；`qwen-vl-ocr` 旧免费额度已耗尽可能HTTP403，不应误判相机问题。**CI成功不代表真机成功**。

---

# 2026-11-19｜C110+ 预览数字缩放随OCR、横屏全屏、150ms双路径 v1.0.48 已发布（待真机）

- **本轮用户明确授权**：“结合我们刚才说的几条更新原则做新版本，尤其是画面缩放，还有个全屏，还有一个就是150毫秒。这三个东西你一起做下一个版本，现在开始。”此前暂缓的缩放和全屏此轮已授权开发；原厂照片尺寸8M/10M/20M依然只在SJCAM Zone改，不给蓝星重复造照片设置；正式RC25、v0.1.20、v0.1.21-safe、v0.1.22各旧版均保留。
- **原仓、基线、身份**：`vobichngan285-collab/kanzi-assistant`；原`main/PROJECT_HANDOFF.md`已阅读；以**已CI发布且1001/150ms已真机成功的原C110+集成版本** `exp/c110plus-photo-ocr-upload-ab-20261119` HEAD `0f48823624f64f488852a25106c54a2918b04576` 派生独立`exp/c110plus-zoom-roi-fullscreen-150ms-20261119`，最终HEAD `cf7d525d8e5d92ab93ebad08de719feed5e44f18`；新debug包`com.vobich.bluestar.c110pluszoom150`，versionCode57、versionName`1.0.48-c110plus-zoom-fullscreen-150ms`。没有新仓库、没有覆盖旧分支APK或main软件代码。
- **真正的缩放同步OCR**：`C110CameraActivity` 专用C110相机页增加`− [SeekBar] +`，1.0～4.0倍；对 **VLCVideoLayout** 作显示缩放，不再只是放大显示。每次用户点实时取帧OCR时把此时倍率随`C110FrameReader.tap(video,zoom)`传入，PixelCopy复制的是原始Surface，进入云OCR**前**调用`C110ZoomFrames.cropPreviewBitmap`先对该帧做**中心ROI裁剪**。用户点`高清拍照`时冻结倍率，仍按原厂**恰好一次1001**快门拍得真实相机SD新增完整JPEG，下载完成后再由`C110FrameReader.recognizeOriginalJpeg(...,zoomAtShutter)`按同一中心比例经`BitmapRegionDecoder.decodeRegion`只解码**高分辨率原图的局部区域**并JPEG92编码一次提交云端；原内存卡JPEG不变、无云端重复请求。**1.0倍**直接上传原始完整JPEG的**同一份byte[]**，完全没有新JPEG重编码。任意局部裁剪失败则不偷偷上传整幅画面，避免用户认为已放大却读远处整图；只对真机照片近16:9画幅允许同步裁剪。新纯Java`C110ZoomGeometry`归一化中心坐标覆盖20M(5888×3312)、10M(4320×2432)、8M(3840×2160)、RTSP等尺寸；RTSP与SD JPEG的真实镜头视角/边缘可能不同，须真机验证同一中心是否精确对准。数码裁剪**不会凭空改善硬件拍摄的小字清晰度**，但可减少无关背景。
- **主页内联C110预览也对齐**：`MainActivity` C110+内联VLC流引入独立`c110PreviewZoom`（不污染手机/UVC zoom参数），双指手势缩放C110画面并把此前忽略的倍率用于原实时PixelCopy OCR及真实高清1001照片ROI裁剪。此修改只在C110预览激活时适用，未重构手机/USB画面路径。
- **全屏**：`C110CameraActivity` 预览滑块右有`全屏`；切换**横屏沉浸式全屏**，隐藏普通顶栏、底栏、系统栏，保留缩放操作、`退出`、全屏高清拍照独立按钮，点画面仍做实时OCR；退出返回竖屏。Manifest仅对C110专页添加`configChanges=orientation|screenSize|keyboardHidden|smallestScreenSize`，`onConfigurationChanged`只刷新缩放UI/系统栏，不调用begin/openPlayer、无额外1001快门，旨在保持**原150ms单一VLC实例**。Android16手机上是否真能无黑屏旋转需要用户实测；CI不能证明。
- **硬性保留150ms及原成功逻辑**：`C110CameraActivity` 和 `C110LivePreview` 两路径**分别**保留各自唯一`--network-caching=150`、`--live-caching=150`、`:network-caching=150`、`:live-caching=150`；`:file-caching=300`和`:sout-mux-caching=300`不变；原LibVLC、`VLCVideoLayout`、一次`play`的300ms延迟不变，原OEM`3012→3016→3001/par1→2015/par1`/已验证精确WiFi选热点/原1001单次快门/9011静音/9038关灯/音效/TTS/双路网络/USB/手机等都保留。150ms是播放缓存，不是相机输出分辨率设置。**C110+的4003删图仍未真机验证，UI继续隐藏，不得试删或自动清理。**
- **单独完整APK/证据**：新工作流`.github/workflows/c110plus-zoom-roi-fullscreen-150ms-20261119.yml`；先一次断言把“不要重启播放器”的注释错误匹配为调用而失败，改注释后静态+纯Java算法测试通过、完整arm64 Gradle Android APK编译、确认原UVC/LibVLC本地SO及原TTS音效、APK身份/ZIP/SHA、上传Artifact及GitHub预发布成功。第一次成功时发布标签误取`v0122`，为避免混淆**未覆盖旧包**，工作流改为唯一正确发布`v0148`，最终CI [Run 44552345416](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44552345416) 状态`completed/success`、HEAD与Release目标同为`cf7d525d8e5d92ab93ebad08de719feed5e44f18`。**正式交付直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-zoom-roi-fullscreen-150ms-20261119-v0148/BlueStar_Guide_C110Plus_Zoom_ROI_Fullscreen_150ms_1.0.48.apk （56,715,773字节，SHA256`8beb6418725ab9dc247ac4b3ff9682476b0a92d1b6db17d49298fbd540c3a390`）。Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-zoom-roi-fullscreen-150ms-20261119-v0148 。第一次误标签`...-v0122`仍是历史独立构建，不建议发给用户，后续全部以`v0148`为准。
- **下一步真机验收**：先安装并单独配置密钥（新applicationId）；原设备已有`qwen-vl-ocr` **1M免费Token剩余0且用完即停**，会返回HTTP403，测试时需自选**仍可用的**OCR模型或明确了解计费后才允许付费，不能把配额403归因于C110。确认C110专页1×整图、2×/4×对中心文字实际OCR只识别局部，底部`高清拍照`仍是原厂1001、C110相机SD尺寸8M/10M/20M仍由原厂SJCAM Zone设置，页内全屏横竖切换VLC不黑屏不闪退、手机主页双指C110缩放与实际ROI匹配。长时间测150ms稳定，若照片原始画幅不同于RTSP视角需要真实图像核查；不要谎称CI等于真机验证。源码与验收说明`cloud-vision-app/docs/C110PLUS_ZOOM_ROI_FULLSCREEN_150MS_20261119.md`。

---

# 2026-11-19｜待办：C110+ 预览缩放与 OCR 真实取景同步（用户已认可设计，**暂缓开发**）

- **优先级/指令**：用户已明确“可以把这个更新记录下来，我们把这前面的事情处理好之后就开始改这个。先不要动”。本轮**只记录需求，不修改程序、未构建/发布APK、未改变实验流程**。先完成正在准备的`C110+ 20M/10M/8M/3M 原生照片尺寸 × OCR准确率 × 云端上传综合实验 v0.1.22`实机验证、数据分析和必要修复；用户之后**再次明确授权**时才动手实现。
- **用户场景**：远处空调上的小能效标签/招牌，现有蓝星向导能放大预览，但OCR仍识别远处的原始**全幅画面**，没有跟着用户看到的放大区域走。用户提供了其他看字软件的界面截图，左下方有`− [可拖动滑块] +`的变焦进度条，希望蓝星也这样简洁操作；拖动到多少倍，**画面放大多少、OCR就只读取对应放大范围**，而非仅作UI预览放大；调回1×恢复原全幅识别。
- **目标设计（待实现，非已验收）**：C110+相机预览页面增加简洁的手动变焦条（讨论方案示例为1×～4×，带减/加号；范围和位置可实现时核定）。默认1×；用户拖动后立即更新预览取景，并在“点击识别／真实高清拍照后OCR”时保留同一缩放倍率和相同中心/取景位置，**将OCR实际提交的JPEG裁到所见取景区域**，禁止只放大显示而仍传整张图片；无需独立AI自动找字或额外云端分析请求。注意坐标应与相机真实JPEG的方向、画幅、宽高、预览裁切/缩放比例严格对应，不能直接假定VLC预览像素与真实5888×3312（或8M等档位）逐像素相同。预览取帧识别和原生高分辨率照片识别是**两条不同的数据路径**，需一致验证，不允许误用低分辨率预览画面充当原图。
- **技术边界和风险**：这是数字缩放/ROI裁剪，不是光学变焦，不能凭空恢复原始照片缺失的小字像素；但可减少云端OCR关注的无关背景。需在小字、标签、远处招牌场景测试：1×与多档手动倍率的实际云端**上传图像是否真的对应视图**、裁切正确性、OCR识别率、文件大小、总耗时；确保最小的手机JPEG额外处理，先讨论是否可通过JPEG区域裁剪/最小编码避免大开销。此前用户已偏好**相机原生4K（8M）完整JPEG上传**用于综合实验；该方案与将来“缩放时裁取局部”属于不同阶段，不要在本轮尚未完成的原生分辨率实验中混入裁剪变更。
- **版本和仓库纪律**：原仓`vobichngan285-collab/kanzi-assistant`，本记录仅补充`main/PROJECT_HANDOFF.md`，保留旧历史；不动现有C110+ Wi-Fi控制、1001快门、150ms真实RTSP、LibVLC、机身静音/灯控、云端OCR/TTS，不新建仓库，不覆盖任何已构建版本或自动启动Actions构建。不延伸到此前仍暂缓的全屏和屏幕旋转需求。**后续先核查main handoff、真实分支/HEAD/Actions/Release，再经用户授权开始开发。**

---

# 2026-11-19｜C110+ 四档原厂照片尺寸 × OCR准确率 × 实际上传/总耗时综合实验 v0.1.22（GitHub CI/Release成功，待真机）

- **用户最终确认**：不再用v0.1.0（误标准C110）/v0.2.0（正确C110+但仅调原生2K）的独立相机分辨率实验；完全改为在**原厂 SJCAM Zone 手动切换 Photo Size 20M/10M/8M(4K)/3M(1440p)**，蓝星一次性真实拍照、下载原生JPEG、调用一次千问OCR、核对上传图片、对正确原文计算字符准确率、导出量化日志，之后再由助手比较选出4K或其他档位。全屏/旋转、按模型缩图、相机1002参数写入和删图均暂缓，用户只授权这次综合实验。
- **源仓库隔离**：`vobichngan285-collab/kanzi-assistant`，从已CI发布的`exp/c110-preview150-deleteprobe-20261119` HEAD `664da2f89f068bd767b875ba39954eb5d22a33d6` 新建独立分支`exp/c110plus-photo-ocr-upload-ab-20261119`，最终 HEAD `0f48823624f64f488852a25106c54a2918b04576`，保留原main、正式RC25、C110 Wi-Fi成功v0.1.20、缓存150ms+手动单张删图v0.1.21-safe、独立照片尺寸v0.1.0/v0.2.0等旧APK，均未覆盖。新包`com.vobich.bluestar.c110plusphotolab`（Gradle debug applicationId suffix），versionCode56、versionName`1.0.47-c110plus-photo-ocr-upload-ab`，用户界面标为`蓝星向导 C110+图像实验`。
- **真实现有功能保持**：既有`C110WifiBootstrap` vivo精确SSID/密码WiFi连接、OEM 3012→3016→3001/par1→2015/par1、RTSP/LibVLC 3.6.5/VLCVideoLayout、150ms缓存、原1001高清快门+相机SD新增JPEG下载、9011机身静音、9038指示灯、TTS及USB/手机摄像头、其他云端任务和API Key接口保留。新增`cloud-vision-app/app/src/main/java/com/vobich/bluestar/cloudprobe/C110ResolutionLabActivity.java`（独立实验入口）、`C110ResolutionScore.java`（纯Java字符比对）、`tests/C110ResolutionScoreTest.java`、doc和工作流；在C110页面「相机」菜单增加“照片尺寸·取图·上传·OCR准确率综合实验”，不改视频播放核心。
- **C110+型号纠错/安全**：用户真实设备是C110+，不是标准C110。之前v0.1.21-safe的4003单张删除实验以标准C110为安全白名单，**不可用于C110+的删图**。本v0.1.22把C110相机页「相机」菜单上的单张删除按钮移除，替换为安全提示；后台老删除代码保留但此实验版本用户UI不再可触发，也绝不自动删。每次真实1001快门都会在相机内存卡写入一张新JPEG，仍需用户在原厂App手动管理。
- **最小单次数据流**：原厂App手动Photo Size→蓝星实验页手动选择对应标签（**只做标签，不发1002/修改相机照片分辨率**）→用户一按「拍一张原图→OCR→自动统计」→用已成功验证的`C110WifiCamera.shootAfterLivePreview(this)`，照片模式3001/par0，拍前/拍后DCIM列表差集，恰好一次1001真实快门，下载验证完整JPEG；记录`width,height,jpegBytes,mode/shutter/discover/jpegDownload/photoMs`；云端`C110WifiCamera.requireCellular`保证走移动网络、`CloudVisionClient.recognize`用 **同一份`photo.jpeg` byte[]** 不重采样、不重新JPEG编码、不重复发送，`qwen-vl-ocr`专用模型和`TaskCatalog.defaults()[0]`相同提示词，`OcrSpeedExperiment.A`非流式以获得完整OCR原文，**仅一次云端请求、不额外AI评分**。记录`request_write_ms`（连接+写入JSON，并非纯无线传输）、`cloud_ms`（整次网络调用）、`first_text_ms`、总从按键到结束`end_to_end_ms`、prompt/completion_tokens（未返回为-1）、JPEG尺寸/字节和Base64估算、JPEG字节引用相同标志；有「查看刚才实际送给AI的JPEG」预览，**预览只本机采样显示，实际上传不缩放**；无法测到千问服务端内部最终降采样分辨率，不能宣称客户端能确定。
- **OCR准确率**：实验页提供“正确原文”输入框，用户粘贴测试纸真实文字且同样文字/光线/角度/距离跨档拍摄。C110ResolutionScore纯Java按Unicode码点对参考与OCR原文做NFKC、小写并忽略空白/标点，Levenshtein编辑距离→字符准确率`max(0,100*(1-edits/referenceCodepoints))`，无参考则`unknown`而非100%；这是字符匹配指标，不是语义准确度或AI自评。标准答案及OCR原文与JPEG只存运行内存，数值日志不含照片、API Key、WiFi密码、原始识别文本；用户可选独立查看/复制OCR文字，或复制**仅数值**日志。最多32次，结果贴出可比较每一档实际图片宽高、字节、时延、字符准确率；用户选定档位与真实JPEG尺寸不一致标注`match=NO`，以实测为准。
- **已验证**：[Actions Run 44521725706](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44521725706) **completed/success**；纯Java OCR CER 回归测试（中英文/全半角/Unicode/空答案）、原C110目录/JPEG/删除策略测试、静态回归确保150ms双缓存/OEM VLC未改、1001一次、本实验无cmd1002/4003或删除、CloudVisionClient原JPEG直传、隐藏未验证删除UI、原USB UVC补丁、原图标/音效、完整Gradle arm64-debug APK、LibVLC库/包身份/ZIP/SHA、Artifact/新 GitHub pre-release成功。发布Release：https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-photo-ocr-upload-ab-20261119-v0122 ；**可复制浏览器直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-photo-ocr-upload-ab-20261119-v0122/BlueStar_C110Plus_Photo_OCR_Upload_AB_0.1.22.apk （56,699,357 bytes；SHA256 `0a746c4a96e21e2fe8c49497b2df74ef34db2572b1539baa5cb4026c8e5331bd`）。
- **真机下一步**：这是**完整独立安装包**，新的applicationId，首次需在此实验包单独设置阿里百炼API Key；手机开启移动数据供云OCR，使用原来的C110+已验证原厂WiFi连接路径。到SJCAM Zone选择Photo Size=20M；返回蓝星C110相机页→「相机」→「照片尺寸·取图·上传·OCR准确率综合实验」，选择20M标签粘贴**全部已知正确文字**，单击拍一次；然后原厂App改成10M、8M、3M，返回实验页对应标签继续各一次（若切App后WiFi断线先恢复）。各次点击只会真正拍一张并在SD留下原图，不自动清理，至多一次云请求。回传数值诊断后再决定是否追加重复样本；**CI成功≠真实相机分辨率或识别准确率实测已完成**。详细见`cloud-vision-app/docs/C110PLUS_PHOTO_OCR_UPLOAD_AB_20261119.md`。

---

# 2026-11-19｜重要型号纠正：实体为 SJCAM C110+，专用原生照片写入尺寸实验 v0.2.0 已发布

- **用户明确纠正并提供商品页面**：商品详情显示`SJCAM C110+`（2024-04），用户亲自确认其购买实体**是C110+，不是C110标准版**。此前把相机称为标准C110是错误；今后任何相机型号、照片分辨率映射、权限白名单、删除指令等均按C110+重新对照。既有实测的WiFi视频预览/1001单次高清JPEG/9011静音/9038灯控仍是真机事实，不能因为改了型号称呼就把成功说成未解决；但需检查代码中错误的`C110_PRO`拒绝分支，尤其之前v0.1.21-safe单张删除实验证据只按标准C110门控，**在C110+复核前不要实机试删**。
- **最关键修正**：前一独立照片尺寸包v0.1.0按**标准C110**`assets/cparams/675-C110$1.0.x.json`误用`1002/par=5`作为2K候选。用户**不要继续运行旧v0.1.0的③设置2K**。已直接重新打开用户原始SJCAM Zone 6.7.3.16 APK `bdcc3db17ea0e26aecaf22bc01fe99be.apk`，逐份解析以下四份**真正C110+**配置：
  - `assets/cparams/580-C110+$1.0.7.json`
  - `assets/cparams/580-C110+$1.0.8.json`
  - `assets/cparams/580-C110+$1.1.2.json`
  - `assets/cparams/580-C110+_AIC$1.1.8 AIC.json`
  - 四份的`International[0].section_array[10].row_array[0]` `cmd=1002`选项**完全相同**：index 0:20M 5888×3312；1:16M 5376×3024；2:14M 5008×2816；3:12M 4640×2608；4:10M 4320×2432；5:8M 3840×2160；6:5M 3200×1808；**7:3M 2560×1440（目标2K）**；8:2M 1920×1088（近1080p）。标准版par5实际上可能对应C110+的8M，不可使用。过去用户完整JPEG实测5888×3312，正与C110+原厂20M档相符。但把列表索引按`par`发送仍需**真机尺寸读回验证**，不能仅凭原厂表宣布成功。
- **专用最小实验**：从原独立尺寸试验分支`exp/c110-native-photo-size-lab-20261119` HEAD `75997527c90266fc8e39714e80b1ff1b56254633` 派生新的独立`exp/c110plus-native-photo-size-lab-20261119`，HEAD `a50009467aee0d4cb00012efd553afc5accb7a70`；更改仅该分支`c110photosize`实验module、独立CI与核查文档。独立包`com.vobich.bluestar.c110plusphotosize`、`0.2.0-c110plus-photo-size-lab`，正式RC25、v0.1.20 OEM连接、v0.1.21-safe、其他原版均未覆盖，原GitHub仓库不变。
- **防止误操作**：本实验针对C110+的`3012`返回可能用`C110`简写或`C110_PRO`别名，先要求C110家族且3002确认支持1001/1002；然后用户按②完成一次独立1001快门、读取SD新增**真实完整JPEG**；只有新照片实际`5888×3312`、匹配C110+原厂20M配置时，UI及后端的③设置才解锁；再经用户确认**只发送一次**`/?custom=1&cmd=1002&par=7`，消耗一次授权、不重试，随后④重新一次1001快门，从新SD原始JPEG读出是否`2560×1440`及大小耗时。不读图裁切或重采样、不上传云端、不删除或格式化内存卡、不改灯光、没有视频/全屏等功能。相机照片尺寸设置可能持久保存，实验不能安全自动恢复；结束用原厂App按需恢复20M。Android手机需先手动连C110+ Wi-Fi。
- **验证**：新CI [Actions run 44512880029](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44512880029) **completed/success**，Java图片完整性测试、确保par=7且代码**绝不包含错误par=5**、20M基线一票否决、只有人工确认、Gradle完整APK、仅必要权限、APK完整性、发布结果已核实。Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110plus-native-photo-size-lab-20261119-v020 ；真实APK直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110plus-native-photo-size-lab-20261119-v020/BlueStar_C110Plus_NativePhotoSize_Lab_0.2.0.apk （26,162字节，SHA256 `61fac116bfdb58db916a9946fff409376c81bab9a82df0bd2f0f07aeb3ac583b`）。分支说明 `cloud-vision-app/docs/C110_NATIVE_PHOTO_SIZE_LAB_20261119.md`。
- **下一步**：用户使用**v0.2.0 C110+专用包**①只读检查→②新SD JPEG（若不是20M则程序不允许③，发诊断）→③阅读警告后手动确认一次par7→④新JPEG真实像素核验→复制日志回传。**CI成功≠固件真的拍出了2K**，真机未验证。全屏/旋转仍按用户要求暂缓。

---

# 2026-11-19｜C110 原生照片写入尺寸独立实验 v0.1.0（已成功构建/发布，待真机）

- **明确需求**：用户这轮只验证“相机本身把内存卡新照片写成2K/更低分辨率”，暂缓全屏/旋转，不修改之前v0.1.21-safe及原项目。
- **原始可靠依据**：再次实际打开用户上传的`bdcc3db17ea0e26aecaf22bc01fe99be.apk`（SJCAM Zone 6.7.3.16），直接读取`assets/cparams/675-C110$1.0.1.json`、`675-C110$1.0.4.json`、`675-C110$1.0.7.json`；其标准版C110 `model=0/model_type=2` “Photo Size” 通过`cmd=1002`设置，索引0=15M 5200×2928、1=12M、2=10M、3=8M、4=5M、**5=3M 2560×1440(2K候选)**、6=1M 1440×816；并且再次解码`classes5.dex` `LYCamera.setting` code_off 0x18d2fc/0x18d24c 实际调用`Command.setCmd`、`setPar`、`getRequestUrl`、OkHttp。`getSettingDefaultValue`的只读查询cmd3014 code_off 0x18c3f4 不应直接当作可靠当前照片尺寸。原用户相机已拍出5888×3312，与上列最高15M不一致，因此**必须实机验证参数映射，不能臆断1002/par5一定得2K**。
- **仓库隔离**：原仓`vobichngan285-collab/kanzi-assistant`，沿用main交接纪律；从v0.1.21-safe实验分支`exp/c110-preview150-deleteprobe-20261119` HEAD `664da2f89f068bd767b875ba39954eb5d22a33d6` 派生独立`exp/c110-native-photo-size-lab-20261119`，最终HEAD `75997527c90266fc8e39714e80b1ff1b56254633`。只新增`cloud-vision-app/c110photosize/`新Android module、`settings.gradle`的`include ':c110photosize'`、独立测试/工作流/协议审计文档。正式app、v0.1.20/0.1.21、mini/latency原播放器及历史构建代码未修改覆盖。包名`com.vobich.bluestar.c110photosize`，版本`0.1.0-c110-photo-native-size-lab`；**没有全屏/旋转**。
- **最小真机交互**：需事先在Android**手动连接C110 Wi-Fi**；①只读3012确认**标准C110**（拒绝C110+/PRO）、3002确认1001/1002指令并只读3014设置回执（不导出相机敏感信息）；②以已有真机验证过的 3001/par0 → DCIM前后目录比较 → 单次1001快门 → 下载仅新增一张完整JPEG的流程，BitmapFactory只读取**真实SD原图像素宽高/文件字节/耗时**；③**仅用户再次确认**，且固件确实在3002声明1002时，才允许发送**一次**`/?custom=1&cmd=1002&par=5`（原厂3M/2K候选）；④再真实拍一张，直接比较新SD JPG是否为2560×1440。HTTP200/Status0**不等于成功写出2K**。原JPEG只在内存短暂存在，不保存手机、无云端/模型/音频/RTSP、不压缩、不删除SD、不自动重复快门与设置。日志仅数值和阶段可复制。
- **重要真实副作用**：更改照片档位可能被相机**持久保存**；因无法可靠从当前固件读出可回滚的原`par`，实验程序明确**不尝试猜测原档并自动恢复**。用户测试后可去原厂SJCAM Zone把图片尺寸恢复。只要未点③确认，程序绝不发1002写命令；②与④会真实各拍一张并写入SD卡。第一次只读若1002不在固件命令列表，③禁用；仍可用②拍摄原图做基线。
- **已验证CI**：独立`C110 Native SD JPEG Photo Size Experiment 0.1.0` [Actions Run 44511099567](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44511099567)，**completed/success**；纯Java Jpeg/DCIM路径测试、原厂1002/par5白名单、1001只发送一次、安全禁止删除/其它设置/互联网云请求、Android Gradle完整arm64-debug编译、APK包身份/权限/ZIP/SHA、Artifact、GitHub预发布成功。**CI并不等于C110真实支持2K，尚未真机验收**。
- **最终APK直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-native-photo-size-lab-20261119-v010/BlueStar_C110_NativePhotoSize_Lab_0.1.0.apk ，25,922字节，SHA256 `170080fd47fea9c305b5a2838bf371e8e5e9c30227457a0a2e913431dd1357a1`；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-native-photo-size-lab-20261119-v010 。源分析/实验安全准则`cloud-vision-app/docs/C110_NATIVE_PHOTO_SIZE_LAB_20261119.md`。
- **下一步**：用户装独立APK，系统手动连C110热点，按①②③④四步（③需清楚理解持久更改），复制日志回传。重点比较基线5888×3312与新SD JPEG宽高；如返回未声明1002/Status失败，不绕过。真机验证原生2K成功后，再考虑原生1080或720档映射；目前**未证明标准照片尺寸有720p选项**。全屏/旋转严格后置。

---

# 2026-11-19｜C110默认缓存150ms＋原厂4003单文件删除安全实验 v0.1.21-safe（已CI/Release，待真机）

- **用户授权**：根据已实测缓存A/B（150ms约106.7秒、0丢帧、稳定约30FPS），下一轮默认C110预览为150ms；再针对C110拍照满SD卡的问题，先从原厂SJCAM Zone APK核对单文件删除指令，严格单张验证，确认安全后才加自动后台删除。未经真机确认绝不自动清理。
- **仓库/隔离**：原vobichngan285-collab/kanzi-assistant，从已用vivo真机连通的exp/c110-oem-vivo-wifi-20261119 HEAD d55d7d160a54f5d341d9a7cf01369d4fd559dc12派生独立exp/c110-preview150-deleteprobe-20261119，最终HEAD 664da2f89f068bd767b875ba39954eb5d22a33d6。正式main源码、RC25、C110 v0.1.20、已出画c110mini和延迟实验app均未覆盖；新包com.vobich.bluestar.c110150delete，versionName 1.0.46-c110-preview150-delete-probe。
- **默认缓存**：仅C110CameraActivity.java与C110LivePreview.java两处，将LibVLC全局与Media的--network-caching、--live-caching、:network-caching、:live-caching由300统一到150ms。固定file-caching=300、sout-mux-caching=300、codec=mediacodec,iomx,all、300ms播放延迟、原厂3012/3016/3001par1/2015par1、VLCVideoLayout保持不变。选择该档位基于用户长测稳定性，不能宣称端到端实际延迟=150ms。
- **直接从原厂APK验证删除指令**：原附件bdcc3db17ea0e26aecaf22bc01fe99be.apk仍可于工作容器/附件访问，读取classes5.dex中LYCamera.deleteMediaFile（0x18ad28同步和0x18bce4异步）实际方法体，const/16 4003→LYCamera$Command.setCmd；把相机下载URL的前缀http://192.168.1.254/替换为A:\并将/转\，经StrUtils.getUTF8String传Command.setStr；getRequestUrl前缀http://192.168.1.254/?custom=1，连接cmd/str参数。记录cloud-vision-app/docs/C110_OEM_DELETE_AUDIT_20261119.md。**原厂使用4003已实证，但本C110固件是否接受/删除仍待真机**，不能据Android模拟或CI称成功。
- **安全试删**：独立C110相机页面按一次1001快门并完整下载JPEG后，C110WifiCamera.Photo携带来源路径与严格新文件增量标志；当拍前目录不为空、拍后只有唯一新增JPEG、JPEG头尾/尺寸/路径均校验通过才生成一次性删除候选。无可证明的唯一新增文件则**禁止删除但不影响拍照**，空卡首次拍照可能不产生删除候选；可先拍一张测试图，再拍第二张做实验。相机页「相机」→「试删刚才的高清原图（实验）」→再次用户确认。后台只读3012核查标准C110排除C110+/PRO，只读3002要求固件声明Cmd4003；精确目录核查候选仍存在；C110DeletePolicy严限/DCIM/.../合法jpg/jpeg且禁注入、目录、其他格式；OEM A:\DCIM\... URL编码，**恰好一次4003，不重复写**；只读复核该JPEG是否从准确目录消失，报告HTTP/Status/耗时，之后恢复原始VLC，旧1001高清与OCR/TTS路径不改。候选只保存路径和尺寸元信息、不上传、不写相册、不批量删/格式化。自动删除没有开启选项，现阶段只能手动测试单张。
- **CI**：最早Run 44505118783因原厂审计文件尚未提交导致静态脚本失败，后来完整Run 44505352549成功；随后增强“拍前非空基线+唯一新增”避免误删旧文件，并以全新safe标签发布。最终 [GitHub Actions 44505963131](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44505963131) completed/success，包含C110PhotoPolicy、C110DeletePolicy纯Java注入安全单测、C110RtspSessionProbe测试、OEM播放代码标准化差异核对、150ms双入口断言、真实1001只执行一次与4003只可手动单文件防护、旧USB补丁、Gradle完整arm64 APK、LibVLC/TTS音效、ZIP和包身份、Artifact/独立GitHub Release校验。
- **可直接复制到浏览器下载的最终APK**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-cache150-delete-probe-20261119-v0121-safe/BlueStar_Guide_C110_Cache150_DeleteProbe_0.1.21-safe.apk （56,699,321字节；SHA256 73c71a77880d294ac1910148f18c919cc32619ee93a218907ee9c2b66f7b6bb0），Release页https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-cache150-delete-probe-20261119-v0121-safe。不要给用户旧的同分支v0121非safe标签，因为它未包含最终严格唯一性保护。
- **唯一下一步真机实验**：在相机已联网情况下进入蓝星v0.1.21-safe C110模式，确认预览体感是否更跟手；SD卡中至少已有一张**不重要的测试照片**，再拍另一张真正高清JPEG，等朗读完成→顶部「相机」→「试删刚才的高清原图（实验）」→确认；如果提示固件未声明4003则没有写入；若显示Status0且“文件消失=是”，请打开原厂App相册核实**仅本次新图**被删且旧图仍存在。收集这段诊断以及实际删图耗时。**CI≠真机删除已验证**，未经明确验收不能把它默认切到自动。
---

# 2026-11-19｜C110 独立 LibVLC 预览延迟 A/B 实验 v0.1.0（已构建发布，待真机诊断）

- **用户目的与授权**：C110实时预览“会慢半拍”，希望先做**最小独立实验 APK**，手动切换不同缓存档位，软件低开销收集视频链路统计，复制诊断给助理分析。用户不希望擅自碰当前已连通v0.1.20 Wi-Fi、1001高清快门、9011静音、9038指示灯、任务或TTS。
- **真实代码基线**：原仓库`vobichngan285-collab/kanzi-assistant`；从C110原厂v0.1.20实验分支`exp/c110-oem-vivo-wifi-20261119` HEAD `d55d7d160a54f5d341d9a7cf01369d4fd559dc12` 派生`exp/c110-latency-lab-20261119`。**保留成功的`c110mini/OemVideoActivity.java`、正式app及所有旧实验构建不变**，仅在新分支添加`:c110latency`独立Android模块、Gradle settings.gradle新include、独立CI。CI对应视频实验代码提交`982efe8a86d5fe0ce2c47018cf063e9b42f6a19c`；另有同分支文档`cloud-vision-app/docs/C110_LATENCY_AB_TEST_20261119.md`。
- **功能**：独立包`com.vobich.bluestar.c110latency`，`0.1.0-latency-only`。**仅原厂已真机验证的`3012→3016→3001/par1→2015/par1`、RTSP `rtsp://192.168.1.254:554/xxx.mp4`、LibVLC 3.6.5与一直可见VLCVideoLayout**。用户可点击`300 / 150 / 100 / 50 / 500ms`档位或手输50–1500ms；仅 **`network-caching`/`live-caching` 一对参数**在每档改变，固定其他视频参数如`file-caching=300`、`sout-mux-caching=300`、原`codec=mediacodec,iomx,all`、原一次性延时300ms播放。每次切换释放/重建播放器，避免跨档缓存残留。无自动快门、无云、无OCR、无TTS、无拍照或音视频保存。
- **诊断**：每档事件`SESSION`、`play_call_ms`、`Playing`、首次`Vout`、每2s `SAMPLE`、`SUMMARY`；尽力通过LibVLC `Media.getStats()`反射获取`displayedPictures`、`lostPictures`，由差分推导 **显示帧率/丢帧增量**。若绑定不支持统计则明确记录“帧统计API不可用”，FPS标记未知，不能把`Vout`事件数伪装成帧率；采集缓存回调总数、非播放采样总数、进程CPU单核%估算，并允许用户选择主观延迟标签。所有文字仅存在本机运行内存、长度上限约28k字；点击“复制诊断”后手动发给助理，不含图像、文本识别结果、WiFi密码、音频、密钥或远程上传。
- **严格解释**：仅根据播放器统计 **不能测量真实从C110传感器动作到手机显示像素的端到端glass-to-glass延迟**；`Playing`与`Vout`是播放器启动/输出状态，不是物理像素可见时间。此实验只用于“同环境不同缓存的相对体验、帧率、丢帧和稳定性”验证。如果要客观端到端毫秒数，需外部同步计时/高速相机对照。建议同距离挥手15–30秒每档，并用内置按钮手动标注。太低缓存可能造成卡顿。
- **网络刻意独立**：为保持实验最小、排除SSID/系统权限干扰，此`c110latency`包**不包含自动热点连接**，运行前用户需在系统WiFi手动连C110热点（默认网关192.168.1.254），与v0.1.20正常使用的原厂自动连接是**两个独立安装包**。实验不作为新的正式连接方案；不要误报自动热点功能回退，原版不变。
- **CI/Release**：[Actions 44489398040](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44489398040) `completed/success`：原厂视频命令和起播不变量、新档位/统计/无声画云权限静态检查、Gradle `:c110latency:assembleDebug`成功、LibVLC `arm64-v8a/libvlc.so+libvlcjni.so`、独立包名及版本、ZIP完整性、artifact、发布GitHub prerelease成功。
- **直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-latency-lab-20261119-v010/BlueStar_C110_Preview_Latency_Lab_0.1.0.apk ，`46,109,673`字节，SHA256 `642668583bbce063d2fbc7663a5cb9ef0e2f4cf9a9ed7fdf45ab28e1318a2f0c`。Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-latency-lab-20261119-v010 。
- **下一步**：用户按300→150→100→50→500ms各播放15–30秒，点击“标记体感延迟”，末尾“复制诊断”发送完整文本。根据帧率、丢帧、播放非活跃、主观慢半拍排序选择最小无明显卡顿的档位；**CI通过不等于新实验包已被用户真机验收**，未知时不要声称VLC原生stats一定可用。

---

# 2026-11-19｜原厂SJCAM Zone DEX核查：C110 vivo热点连接 v0.1.20 已编译发布（待真机验收）

- **最新用户真实问题和截图**：手机连家用Wi-Fi、相机Wi-Fi已开时，蓝星C110页面始终提示“正在申请连接C110，请允许Android系统的相机Wi-Fi连接提示”，没有进入预览；必须去手机系统Wi-Fi手动连接。用户明确要求不要再猜，直接按之前给的**原厂SJCAM Zone APK**连接步骤解决。截图只证明停留在申请阶段，不能据此把问题归咎于C110硬件。
- **本轮实际重开原APK，静态解析原始DEX**：文件 `bdcc3db17ea0e26aecaf22bc01fe99be.apk`（SJCAM Zone 6.7.3.16，约189MB），实际读取`classes5.dex`、方法表/方法指令调用，不是以前摘要复述或网搜猜测。证据已整理于本分支 `cloud-vision-app/docs/C110_OEM_WIFI_AUDIT_20261119.md`。核心真实方法：
  - `WifiBottomPopupV2.scanWifi` / `showInputPassword` / `connect`，真实热点扫描/选SSID、密码 → `WifiHelper.connectWifi`。
  - `WifiHelper$1.run` 0x1b1fd4 会处理旧请求和`WifiManager.disconnect`，并**明确调用isViVoDevice()**。对于vivo走`WifiHelper.setCurrentConnectWifiNetWork`，不是对所有安卓厂商统一走Suggestion。
  - `WifiHelper.setCurrentConnectWifiNetWork` 0x1b3264 用 **精确setSsid(实际SSID)**、`setWpa2Passphrase(实际密码)`、有BSSID时`setBssid(MacAddress)`，NetworkRequest添加WiFi+能力13(NOT_RESTRICTED)+14(TRUSTED)、移除12(INTERNET)，再`requestNetwork`。不像此前蓝星用**臆测的固定`C110` SSID前缀+写死12345678**。
  - `WifiHelper$3.onAvailable` 0x1b223c 绑定网络后回调，之后才`SJConnCameraPresenter.connCamera`并进入相机协议。原厂 `LyCameraWifiNetWorkHelper.isTargetGateway` 0x317b40 是**当前默认Wi-Fi网络**网关检测，不能混同Specifier返回的local-only次要Network。
- **代码修复**：从已验收构建分支`exp/c110-homewifi-bootstrap-20261119` HEAD `285a00031b5692ec606a465ce647c061f229fd1b` 建独立 `exp/c110-oem-vivo-wifi-20261119`，最终HEAD `d55d7d160a54f5d341d9a7cf01369d4fd559dc12`。重写**仅连接层**`C110WifiBootstrap.java`：若首次尚未保存相机真实SSID，进入C110页面在**蓝星内**弹出附近Wi-Fi扫描列表（未授权则请求ACCESS_FINE_LOCATION，扫描不成功可在蓝星内手输真实SSID）；用户确认密码（仅预填原厂通用默认值12345678、可修改）后私有存储精确SSID/BSSID/密码；按vivo同类方法构造exact Specifier而非猜SSID前缀，处理Android系统批准、onAvailable、在相机Network上只读3012验证标准C110，验证通过后才打开未改的3012/3016/3001par1/2015par1/LibVLC链。后续启动已有保存的精确SSID自动请求；↻可重新扫描和更换热点。不将Wi-Fi密码加入诊断日志。新增ACCESS_WIFI_STATE/CHANGE_WIFI_STATE/FINE_LOCATION/NEARBY_WIFI_DEVICES权限，MainActivity和C110CameraActivity统一扫码权限回调；避免在已经后台的MainActivity弹被遮住的热点选择框。
- **原厂行为中的合理隔离**：原厂仅管理相机可长期bindProcessToNetwork；蓝星还管理手机/USB/移动数据云识别，所以仍由C110视频页按此前真机成功代码仅在播放时临时bindProcessToNetwork，照片/3012通过指定的相机Network访问，避免给全进程云端互联网默认绑上无外网热点。Android可能要求系统授权、Android16扫描还要求FINE_LOCATION和手机定位开关，双STA与系统静默连通不能保证。
- **严格未触碰功能**：已成功的C110 OEM连续视频、原一次1001完整高清JPEG、9011静音、9038指示灯熄灯、主/USB摄像头、云OCR/TTS和原有按钮绑定都没有改写。v0.1.19、RC25及其他旧APK均保留。仅实验v0.1.20包`com.vobich.bluestar.c110oemvivo`、`1.0.45-c110-oem-vivo-wifi-lab`。
- **CI与发布**：独立Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44483682193 `completed/success`：Java C110协议回归/独立OEM玩家核心逐项静态一致性、原厂Exact SSID+BSSID/vivo分支/权限检查、USB补丁、Gradle/NDK完整构建、arm64 LibVLC/TTS/包名版本/ZIP、artifact与release全部通过。Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-oem-vivo-wifi-20261119-v0120 ；APK直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-oem-vivo-wifi-20261119-v0120/BlueStar_Guide_C110_OEM_VivoWiFi_0.1.20.apk ；56,682,925字节，SHA256 `53f5393c0ee8f9a2f16cf95291adb9c9f9c788b4737f7bb9f520263a7693951c`。
- **只做一次真机验收**：保持手机家用Wi-Fi+相机已开热点→打开v0.1.20→模式→C110→首次应出现本APP内“选择C110实际Wi-Fi热点”列表/密码确认→批准Android系统连接请求→C110页获取3012后启动真实VLC出画。若扫描不到检查手机定位/FINE_LOCATION（或在本App内手填相机实际SSID）；若系统弹框或网络回调失败记录具体阶段。**CI成功≠用户实际连接成功**，不得再以未经验证的SSID前缀或Android网关做最终判断。

---

# 2026-11-19｜C110 连接家用Wi-Fi时自动申请相机连接 v0.1.19（CI/Release验证通过，待手机验收）

- **当前用户问题**：手机仍连接家里的Wi-Fi时打开蓝星向导无法连接C110，必须离开软件去系统Wi-Fi设置里手动切换。用户之前明确已验证C110 **真实视频播放、1001高清JPEG、9011静音、9038指示灯熄灭**，不重复和不破坏。
- **定位的三个代码层问题**：① `C110WifiBootstrap.startAtLaunch()` 在初次 `requestNetwork` 后永久保持`attempted=true`，`onUnavailable`也不释放/重置，之后切到C110页无法重试；② C110页启动就 `begin()`，未等Android系统确认/分配相机Wi-Fi就发3012；③ 旧程序只接受C110网络具有默认路由且网关精确`192.168.1.254`，但Android通过`WifiNetworkSpecifier`将C110作为 **secondary local-only Wi-Fi** 与家用Wi-Fi并存时，C110连接未必有默认路由，导致已接入的C110被误判未连接。官方Android文档明确Local-only Wi-Fi可作为并发第二连接，仅支持的机型能保留家用网络，并且系统弹窗/授权不能跳过。
- **仅修改连接层**：从已发布 `exp/c110-main-hq-button-20261119` HEAD `0e4cd142a4cd63ace12be632c2478060d72df4d8` 创建新隔离分支 `exp/c110-homewifi-bootstrap-20261119`，HEAD **`285a00031b5692ec606a465ce647c061f229fd1b`**。`C110WifiBootstrap` 仍以默认C110 SSID前缀及默认WPA2密码`12345678`向 Android 发起 **用户批准的** `WifiNetworkSpecifier` 局域网连接请求；只读最多三次`3012`确认真实标准C110（排除Pro/+），将 `onAvailable` 所给的可信Network记录为`preferredNetwork`，**无需默认网关**，不把C110流量路由到家用路由器。超时/`onLost`释放请求状态，允许点击C110模式重新申请；底部高清快门发现未连接时先申请，拒绝未连接就发快门。
- **C110相机页面入口**：不再一进页面就发相机命令，而是启动限时35秒、每550ms轻量检测一次已验证相机Network的等待状态。连接成立后才启动**原有未重写**`3012→3016→3001/par1→2015/par1→LibVLC`；相机顶栏刷新按钮可主动撤销陈旧请求并重新调起Android系统连接选择；仍超时会提供“重新请求”或“Wi-Fi快捷面板”两条路径。底层真实高清快门 `C110WifiCamera.findCameraNetwork` 优先读取同一`3012`证实的secondary Network，不再依赖默认网关；旧手动连接网关发现回退路径保留。离开页面取消短时等待，避免后台无限轮询。C110页面恢复RTSP也先确认有网络。
- **未变**：C110单次1001真实JPEG/当前视频画面PixelCopy/手机USB画面/本地TTS/AI/任务/9011静音/9038灯光/移动数据云OCR全不重写；只变C110网络获取、等待和诊断。Android网络授权及连接仍受系统管控，**不能保证不弹系统确认、也不能保证手机同时连两个Wi-Fi**；家用Wi-Fi保留只在手机支持双STA时可能。35秒失败的Wi-Fi快捷面板依旧需要用户选择热点，不声称完全静默自动连通。
- **测试结果（不能冒充真机）**：新Actions `44481118477` [构建日志](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44481118477) `completed/success`：C110PhotoPolicy/RTSP Java单测，OEM LibVLC核心不变量（新入口只等待网络）、local-only无默认路由与重试静态检查、USB补丁、Gradle Java/NDK完整编译、APP签名包名/版本/LibVLC原生库/TTS音效/ZIP检查、Artifact与独立GitHub Release上传均通过。前两次工作流静态检查失败分别为旧包名断言和变量`camera`未提前声明，已修正后第三轮通过。
- **实物APK**：包`com.vobich.bluestar.c110homewifi`，版本`1.0.44-c110-homewifi-bootstrap-lab`；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-homewifi-bootstrap-20261119-v0119 ；浏览器直接APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-homewifi-bootstrap-20261119-v0119/BlueStar_Guide_C110_HomeWiFi_AutoConnect_0.1.19.apk ；56,682,929字节，SHA256 `9ec52c80a405f658c5be8ace85a02d9292bb5dd1b48c7114989cbd0d6e86d9cd`。与已可用的0.1.18及RC25分包共存，不覆盖。
- **真机验收**：先让C110开Wi-Fi，手机继续连接家用Wi-Fi→启动v0.1.19，准许附近设备与Android要求的连接提示→右下角模式选C110，观察是否成功显示连续真实相机视频且无需手动打开设置。如果失败，C110页顶栏`↻`再次请求、观察系统是否出现选网弹窗，35秒后的提示及“Wi-Fi快捷面板”。若不支持本机并发双Wi-Fi，应如实提示需系统选择/切换，而非无休止重试。记录页面状态长按复制诊断与系统弹窗结果，绝不宣称CI等于手机实际成功。

---

# 2026-11-19｜修复主界面“高清拍照”误取手机帧 v0.1.18（构建发布通过、待真机）

- **用户反馈**：在蓝星向导「手机摄像头」模式点击底部“高清拍照”，C110 运动相机没有动作，返回的还是手机画面帧。核查 `MainActivity.getRootView()` 确认实际按钮标成“拍照识别”，`c110Button.setOnClickListener(v->captureOneFrame())`，其代码按手机/USB当前取景源采集，**根本没调用C110真实快门**，直接定位到应用按钮绑定错误；不应误诊为相机硬件失效。
- **本轮仅改主界面按钮和C110拍照调用条件**：从 `exp/c110-inline-hq-photo-20261119` 已成功发布HEAD `e8cf271e380d99d8344effd8df7426fc5d2aeb03` 派生独立 `exp/c110-main-hq-button-20261119`，HEAD `0e4cd142a4cd63ace12be632c2478060d72df4d8`。将第三个底部按钮更名为 **“高清拍照”**，绑定 `captureC110OneShot()`，不再绑定 `captureOneFrame()`；**手机或USB取景界面点击底部高清拍照都直接触发一张C110真1001原图**；点击取景区域的旧 `captureOneFrame()` 保留原来手机/USB帧识别，不受影响。C110独立预览页v0.1.17原已修复的高清按钮路径保持不变。
- **快门被拦截的另一项修正**：旧 `MainActivity.captureC110OneShot()` 把 `requireCellular()`/百炼密钥检查放在调用C110快门前，一旦未开移动数据或未填Key就根本不触发快门。现改为先调用已验证的`C110WifiCamera.shootAfterLivePreview()`，先切`3001/par=0`照片模式、后读取旧目录基线、只发**一次**`1001`命令并下载全新原始JPEG；然后检查独立手机蜂窝网络和API Key，若条件具备则通过既有`beginCapturedFrame`执行云OCR/TTS，不具备则仅提示“高清照片已拍摄但未云端识别”，**不误报相机快门失败，不额外自动拍照**。
- **保护原则**：用户已确认成功的 LibVLC C110实时预览、预览画面点击PixelCopy取帧、C110独立页真实高清拍照、9011提示音、9038熄灯默认30秒、启动主动请求C110热点、手机及USB预览核心逻辑不修改；旧正式RC25、旧 v0.1.17 APK 不覆盖。新独立测试APK包名 `com.vobich.bluestar.c110mainhq`，versionName `1.0.43-c110-main-hq-button-lab`。
- **CI和发布已核实**：[Actions 44477167331](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44477167331) `completed/success`，此前失败 `44476965876` 为工作流静态断言仍旧要求旧变量名 `photo.jpeg,cellular`（现在为`photo.jpeg,cloudNetwork`）而非源码编译错误；修正断言并增加“底部高清按钮必须绑定真实C110、预览画面依旧保留手机/USB帧”的回归检查后，协议单测、OEM视频静态一致性、USB补丁、Gradle APK编译、原生LibVLC+TTS音效+APK身份ZIP校验、Artifact/预发布全部成功。
- **APK**：Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-main-hq-button-20261119-v0118 ；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-main-hq-button-20261119-v0118/BlueStar_Guide_C110_Main_HQPhoto_0.1.18.apk ；56,682,933 字节，SHA256 `5af68c8b9ce66486c7e989e118471a99642c7bab0060fb5550b5ad2fc3684630`。
- **待验收**：手机先连通C110 Wi-Fi或允许启动时系统连接请求，进主界面「手机摄像头」模式→点击底部**高清拍照**→确认C110实体仅拍1张新图、手机预览不被替换、照片是新5888×3312 JPEG而不是手机画面帧；开启蜂窝+填写本实验包的API Key时检查云端OCR/TTS。手机画面**中心区域**点一下仍应识别手机当前画面。新APK真机表现尚待用户验收，不得以CI成功代替。

---

# 2026-11-19｜C110预览内真正高清快门修复 v0.1.17（CI/预发布成功，真机待验）

- **最新用户问题**：C110预览页面下方「高清拍照」点击后直接跳到蓝星主界面，且有时不触发快门。核查上一版 `C110CameraActivity` 按钮直接 `returnToMain("photo")` 调用 `finish()`，再由 `MainActivity.handleC110PageResult` 通过 250ms 延迟回调 `captureC110OneShot()`。该设计引入了页面切换、onPause/onResume、云端状态门控与快门触发时序依赖，确实是结构问题，不是已证实C110相机硬件故障。
- **本轮单项修复**：从已发布 `exp/c110-live-tap-autowifi-20261119` HEAD `5f5a0078acdda41ba53db16321e893b25cfe5f0e` 创建隔离 `exp/c110-inline-hq-photo-20261119`，最终 HEAD `e8cf271e380d99d8344effd8df7426fc5d2aeb03`。修改三个Java类：`C110CameraActivity` 的高清按钮直接绑定 `captureOriginalPhoto()`，仅在已有活动预览、无别的识别/照片任务时单次执行；**不finish、不返回Main、不通过ActivityResult拍照**；本页面释放LibVLC/RTSP后后台工作线程调用 `C110WifiCamera.shootAfterLivePreview()`，无论取得新完整JPEG或失败都恢复原经过验证的3012→3016→3001/1→2015/1→LibVLC预览；全程防双击、防并发，退后台取消旧任务；拍照失败不自动重发1001。
- **相机控制顺序的专项修复**：原 `C110WifiCamera.shoot(Context)` 的成功旧路径原样保留，新入口只在「从RTSP视频转真实照片」时提前执行已经验证的`3001/par=0`并回执成功，**先完成切换到照片模式，然后才读取目录旧照片基线**，接下来仅一次 `1001` 快门→查找新增JPEG→下载并校验完整新原图；可避免视频模式下提前读目录造成拍照无法触发，尚待本C110实机确认。失败不重用旧JPEG、不静默重复快门、不删除照片。
- **云端识别**：`C110FrameReader.recognizeOriginalJpeg(jpeg,width,height)` 复用现有所选任务、OCR/视觉模型、过滤开关与手机TTS，原始完整JPEG不做第二次有损压缩，显式手机蜂窝Network上传云识别；用户始终停在C110预览页面。若无移动数据/Key，快门仍可正常拍摄，云端识别独立报告失败。视频画面单击PixelCopy低分辨率识别方式保持独立不变。
- **其他边界**：C110 OEM真机成功播放核心没有重写，主界面手机/USB/AI对话/阅读代码未变；C110 `9011` 静音、`9038` 指示灯与延时设置、开机申请C110 WiFi保持旧代码。视频→照片→视频期间实时画面可能短暂停止，**不承诺零中断或当前版本已经在用户真机验证**。C110底部AI对话仍返回主界面执行（不属于本次修复）。
- **Actions / APK**：[Actions 44468603534](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44468603534) `completed/success`，包括C110PhotoPolicy及RTSP探针Java单测、OEM播放器核心静态比对、只一次1001命令/照片模式先于旧图基线/原JPEG不重编码/没有拍照页面跳转断言、USB稳定性补丁、Gradle Java/NDK构建、APK包标识/ABI/LibVLC/TTS音效/ZIP与GitHub release验证。实验包 `com.vobich.bluestar.c110inlinehq`，`1.0.42-c110-inline-hq-photo-lab`，独立保留v0.1.16/正式RC25。Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-inline-hq-photo-20261119-v0117 ，APK直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-inline-hq-photo-20261119-v0117/BlueStar_Guide_C110_Inline_HQPhoto_0.1.17.apk ，56,682,933字节，SHA256 `998eec47066551c38277d2fec176ae18520bea26943508ec1fff84100a98fb36`。
- **真机验收重点**：C110热点开、手机移动数据开、实验包单独配置百炼Key→C110模式连续出画→点底部「高清拍照」：页面不跳走，相机实体快门只响/闪一次，取得真实5888×3312新JPEG→原本的预览恢复→当前页朗读所选任务。若失败长按状态行复制诊断，看 `高清拍照未完成` 后的具体报错；勿用多次按键掩盖单次失效。

---

# 2026-11-19｜C110实时画面单击取帧朗读 + 蓝星启动主动申请相机Wi-Fi v0.1.16（构建/发布通过，待真机）

- **用户本轮仅要求两点**：①点击C110模式时与普通蓝星主界面类似的整屏相机预览和底部四按钮，预览区域单击直接快速获取当前画面并调用已有云端OCR/TTS；②蓝星向导打开时若C110已开Wi-Fi，主动向Android系统申请连接而非只检查用户此前手动连接。**不重构声音/灯光/高清快门和原已成功LibVLC流程**。
- **基础/分支**：从已发布`exp/c110-unified-modes-controls-20261119` HEAD `586df77cde42de2842a93bfffcf4a1907f2ed423` 创建隔离实验分支 `exp/c110-live-tap-autowifi-20261119`；最终 HEAD **`5f5a0078acdda41ba53db16321e893b25cfe5f0e`**。不改正式RC25、已成功的OEM mini及旧实验release。
- **预览页面**：`C110CameraActivity`全屏原版VLCVideoLayout保持初始化就VISIBLE，顶栏蓝星logo/标题/刷新/相机设置/三横杠，顶部任务标签和底部四按钮（任务/AI对话/高清拍照/模式）；之前占大块空间的原厂调试输出缩为状态提示，长按状态可复制完整诊断。单击透明触控覆盖层只执行一次 `PixelCopy.request`，从VLC原生SurfaceView复制当前帧→内存JPEG质量90→`CloudVisionClient`沿用用户任务、文字/视觉模型、OCR C逐行/兼容模式、过滤规则、手机本地TTS；走显式蜂窝`Network.openConnection`以避免相机热点无外网。所有耗时云端任务不阻塞视频播放。**单击预览不是1001原图快门、不写照片文件、不重传旧缓存、不重复拍摄**。
- **WIFI启动请求**：`C110WifiBootstrap.java`在`MainActivity.onResume`仅一轮请求：先检查Android已经连接的可信`192.168.1.254`网关；否则以原厂说明书标准C110 SSID前缀`C110`与默认WPA2密码`12345678`构造`WifiNetworkSpecifier`→`ConnectivityManager.requestNetwork`→Android官方系统连接确认弹窗。Android 13+请求`NEARBY_WIFI_DEVICES`、老版兼容定位；首次安装**先完成CAMERA权限，再申请Nearby Wi-Fi**，复用原`onRequestPermissionsResult`，不重写相机/麦克风权限机制。onAvailable和链路变更后再验证网关与只读3012设备身份，排除C110_PRO/C110+/PLUS；维持网络回调期间进入独立播放器，main销毁后释放，断网时反馈状态；不自动操作SD卡/固件/遥控快门。设备密码被改、热点未开、用户拒绝确认、Android不给局域网网络时无法保证连上，不得承诺无弹窗强制自动配对。
- **成功播放器不变量**：CI逐字比较用户真机成功的OEM mini `begin→runWhenCurrent`内原厂3012→3016→3001/par1→2015/par1、LibVLC 3.6.5、VLCVideoLayout始终可见、VideoHelper自身Surface、300ms一次播放，与本版唯一差异是此前已有控制项`deviceControls.onCameraConnected`以及新增原生Vout事件中标记画面已可取帧。手机和USB相机代码核心、9011/9038指令、30秒灯光默认设置、原1001快门和阅读功能均不改。
- **修复记录**：初次CI `44450489780` Java失败，发现主界面已存在`onRequestPermissionsResult`，新增重复定义导致编译错误；已合并到原相机/麦克风回调，并修首装权限弹窗抢占。其他几轮中间版本与最终包区分。最终 [Actions `44450958604`](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44450958604) **completed/success**；旧协议Java测试、OEM核心静态一致性、USB补丁、Gradle编译、APK签名包名/版本/native LibVLC/音效/ZIP、Artifact、GitHub预发布成功。
- **最终APK**：独立包`com.vobich.bluestar.c110livetap2`，`1.0.41-c110-live-tap-wifi-lab`，Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-live-tap-autowifi-20261119-v0116 ，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-live-tap-autowifi-20261119-v0116/BlueStar_Guide_C110_LiveTap_WiFi_0.1.16.apk ，56,682,929字节，SHA256 `f25abb23ae10fd7170a17c2cf3baa5d12303bcf86d7b5896f157934be755841e`。实验包的云端百炼Key需在本包设置内单独配置。
- **仅待真机验证**：相机WiFi开机→蓝星首次启动Android Nearby permission/系统配对→真正收到3012确认且VLC连续出画→单击实时视频一次是否立即成功PixelCopy→蜂窝云识别→本地语音；独立高清1001快门与原灯光/静音也不要回退。不要把CI成功写成真机成功。**依旧存在此前已说明的旧布局边界：C110页“高清拍照/AI对话”返回主界面执行，不在本轮两项修复范围内。**

---

# 2026-11-19｜蓝星向导三种相机模式统一菜单 + C110声音/灯光设置 v0.1.14（CI成功，待真机验收）

- **用户已明确验收的旧成果**：C110独立 OEM Minimal 播放器真机实时出画；`9011/par=0` 真机静音（含开机音）；用户再次确认 `9038/par=0` 实际可使机身指示灯熄灭，`9038/par=1` 回复HTTP200/Status0。已成功的高清拍照与JPEG下载保留，不能误称实时预览或LED熄灭仍未解决。
- **本轮授权**：界面底部统一为任务、AI对话、拍照、模式；最右模式菜单为手机摄像头/C110运动相机/USB摄像头；软件通用设置改右上角三横杠；阅读继续置于任务宫格；相机专属设置收纳于C110相机页；指示灯连接后默认亮30秒再关，可选15秒、60秒、自定义5–600秒、常亮或立即熄灭；C110提示音手动关闭/恢复。
- **代码基线/隔离**：原仓库 `vobichngan285-collab/kanzi-assistant`，从 `exp/c110-portrait-page-20261119` HEAD `e3ff7d075f46abfc3927e0d06cfbf30013021045` 建立 `exp/c110-unified-modes-controls-20261119`，最终 HEAD `586df77cde42de2842a93bfffcf4a1907f2ed423`。新 `C110DeviceControls.java` 独立于RTSP/VLC与照片链：每次9011/9038写前只读3012确认标准C110并排除C110_PRO/C110+/PLUS，白名单禁止9036等未纳入新UI的命令；退出C110页取消待发送的LED延时指令；通用UI新增模式选择和C110 Activity回调。单独测试包 `com.vobich.bluestar.c110modes`，`1.0.39-c110-unified-lab`，不覆盖正式RC25、原mini、其他实验版。
- **重要界面边界（不得夸大）**：依然采用已经出画的独立 `C110CameraActivity`。C110页任务选择直接复用任务宫格；阅读、AI对话和高清拍照目前通过Activity结果返回蓝星主界面，再调用原有阅读/录音/一次1001高清拍照→现有云端识别/TTS逻辑；**不应宣称AI对话可在保持C110视频页面前台时直接按住录音、或拍照后视频无缝维持**。该操作流程属于实验版待用户体验确认；若用户要求真正不离开相机视频界面，需要下一步UI架构调整。
- **视频成功核心审查**：CI静态比对新CameraActivity与成功OEM mini的`private void begin(){ ... private void runWhenCurrent(`段，除`busy=false;started=true;`后唯一调用`deviceControls.onCameraConnected(wifi)`以外保持字节一致。VLCVideoLayout始终可见、LibVLC 3.6.5、原厂3012→3016→3001/1→2015/1与单次延时300ms play未改。仍需新整合版实际真机验证。
- **首次编译修复**：Actions `44445736196` 失败仅因新增重复`onActivityResult`和`onCreate(Bundle b)`内同名局部Button变量；分别合并至原有Activity回调（保留阅读与诊断导出）及重命名变量后重新构建。最终 [Actions 44446181086](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44446181086) `completed/success`，协议单测、播放器不变量、USB补丁、Gradle Java编译、APK包身份/native libvlc/USB/TTS音效/ZIP验证、Artifact和GitHub预发布全部成功。
- **安装包**：Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-unified-controls-20261119-v0114 ；APK直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-unified-controls-20261119-v0114/BlueStar_Guide_C110_Unified_0.1.14.apk ；大小56,666,421字节；SHA256 `acffd039ec77a111702520616f6d4b0f71640aa2113d3849d012062a8d1d4875`。隔离测试包的百炼API Key和原版不共享，真机测试云端朗读需单独配置。
- **下一步真机验收**：安装独立实验版，保持C110热点与手机移动数据，检查右下模式菜单→C110实际连续画面、相机专属设置9011/9038及延时自动熄灯、C110任务宫格、一次高清拍照回到主界面后是否仅执行一次1001快门并云端朗读、切回手机/USB以及右上角软件设置。**CI成功不是C110端到端真机成功，不要覆盖之前可用版。**

---


# 2026-11-19｜C110 声音与灯光独立实验 v0.1.1：9038 指示灯 / 9036 爆闪灯（构建成功，待真机）

- **此前成功状态不得回退**：用户明确纠正：C110 视频实时预览已经在独立 OEM Minimal v0.1.0 真机出画，不再称“实时预览未解决”。成功链为原厂 LY 3012→3016→3001/par1→2015/par1 + 一直可见 VLCVideoLayout + LibVLC 3.6.5 + 一次300ms延迟播放。蓝星内独立竖屏播放器 v0.1.13 已构建但其集成版不等于已验收。单次1001高清拍照下载完整 JPEG 已成功多轮；9011/par0 机身静音已获用户真机验收，包括关闭开机音。
- **直接复核原厂APK新证据**：从用户上传 SJCAM Zone 6.7.3.16 完整 APK 实际提取 assets/cparams/675-C110$1.0.1.json、675-C110$1.0.4.json、675-C110$1.0.7.json，三个标准 C110 配置均包含 9038 = Indicator lights / 指示灯（Off/On），9036 = Strobe light / 爆闪灯（Off/On）。1.0.7 含 9011 = Keypad Tone / 按键声音。原厂配置存在不能等同用户当前 C110 固件已成功执行灯光控制。
- **此次独立实现**：基于已成功的 exp/c110-tone-9011-20261119 分支 HEAD cd0636e75dced9f5172df09449986c1a618647b5，新分支 exp/c110-light-controls-20261119，HEAD 64086642719e3bd611cadc14a65a424ec73241b8。原 ToneProbeActivity 新增 9038/par0 和 par1、9036/par0 和 par1 四个独立手动开关，每次发送前确认对话框与 3012 标准 C110 身份检查，不对 C110+/PRO 盲写；HTTP whitelist 限定3012和9011/9038/9036合法参数。原9011关闭/开启声音、一次1001快门、复制诊断保留。启动只读3012，不自动更改任何设置。照片核心 C110WifiCamera.java / C110PhotoPolicy.java 除package声明外与原成功源码逐行相同（CI校验）；成功 RTSP/LibVLC 播放器、RC25等不修改。灯光回执 Status=0 不等于实际灯已关闭。
- **CI / APK实物**：GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44442129741 completed/success，静态回归、Java/Gradle、APK标识与ZIP校验、Artifact和预发布成功。独立包 com.vobich.bluestar.c110lights，版本 0.1.1-sound-lights；Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-light-controls-20261119-v011 ；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-light-controls-20261119-v011/C110_Sound_Lights_Test_0.1.1.apk ，28978 字节，SHA256 a8cf4792de663fb87f5259eedd3118c97fe6bbf58214664638a8a8f777e381bd。
- **下一步一次真机验收**：连上标准 C110 Wi-Fi，打开独立实验APK，先只按“关闭状态指示灯9038/0”，肉眼看蓝/绿灯是否熄灭；再按“恢复状态指示灯9038/1”确认恢复。红色充电灯可能独立。仅在需要时测试9036爆闪灯，避免混合指令。记录Status及实际灯色变化即可。不重复成功的声音、高清拍照和视频预览验证。

---

# 2026-11-19｜C110 9011 机身拍照声实验：用户真机确认成功

- **真实反馈**：用户使用独立实验 APK `C110_Tone9011_PhotoTest_0.1.0.apk` 连续测试后明确回复“成功了”，这是本轮相机机身拍照提示音控制的用户实机验收，优先保留此成功案例，不再重复无谓协议猜测。
- **实际指令**：`9011&par=0` → `<Status>0</Status>`；`9011&par=1` → `<Status>0</Status>`；随后再次 `9011&par=0` → `<Status>0</Status>`。结合用户明确成功反馈，确认其 C110 实机接受设置、静音效果有效。`par=1` 已被设备接受，但用户尚未单独说明恢复音量后的声音大小或重启后设置是否持久。
- **实际拍照**：实验 App 自带真正的 `1001` 单次快门，三次均下载到全新高清 JPEG：5888×3312/1005317字节/1318ms，5888×3312/2450290字节/1100ms，5888×3312/2444535字节/1102ms（从按拍照到取得JPEG的程序分段总时长，非OCR耗时）。旧开头曾有一次 `未连接到C110 Wi-Fi（网关应为192.168.1.254）`，但后来连接并三次取图都成功，不应误判全程故障。
- **下一步边界**：未来只在用户再次授权时，将已验证的 `9011/0`、`9011/1` 作为相机菜单的“机身拍照提示音”开关，不影响已成功播放器、RTSP/LibVLC或原高清拍照代码；当前仅记日志，未修改代码或构建。相机指示灯设置仍未验证。

---
# 2026-11-19｜C110 机身拍照音 9011 专项实验 v0.1.0（已构建，真机待验证）

- **用户新要求**：相机没有实体拍照键，静音实验工具必须包含蓝星向导已成功的真实远程快门；用户允许独立实验，不修改成功播放器或正式蓝星。
- **原厂协议证据**：用户上传的 SJCAM Zone v6.7.3.16 APK `assets/cparams/675-C110$1.0.7.json`（旧 `1.0.1`、`1.0.4` 不含此项），以及 SJCAM X v1.1.15 APK `assets/cparams/565-C110$1.0.6.json` 均包含 `cmd=9011`, `按键声音`, 列表 `关`、`开`（Off/On）。仍无法预先证明此固件接受命令或它也控制快门的机身蜂鸣。只作一次用户主动的 `9011/par=0` 与 `9011/par=1` 可恢复设置实验。
- **隔离实验分支**：`exp/c110-tone-9011-20261119` HEAD `cd0636e75dced9f5172df09449986c1a618647b5`；新增独立 Gradle 模块 `cloud-vision-app/c110tone`，包名 `com.vobich.bluestar.c110tone`，versionName `0.1.0-9011-photo`。无需移植VLC。界面提供检测、`9011/0` 关闭、`9011/1` 恢复、真实 `拍一张高清照片`、复制诊断、退出。仅手动操作和确认；启动时只读3012；防重入。原 `C110WifiCamera.java` 和 `C110PhotoPolicy.java` 仅改 package，内容逐行复制并被CI静态断言：照片路径3012→3002→必要时3001/par0→**单次1001快门**→识别并下载最新JPEG，照片仅内存验证、不上传云端、不写手机相册、不删除/格式化/升级，照片仍留C110存储卡。标准C110身份校验，不向Plus/Pro盲写。参数回执不等于静音真实成功。
- **CI**：Actions `44438532744` completed/success；源码一致性断言、Java辅助类编译、Gradle `:c110tone:assembleDebug`、APK身份/ZIP、预发布完成。Release：https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-tone-9011-photo-20261119-v010；APK直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-tone-9011-photo-20261119-v010/C110_Tone9011_PhotoTest_0.1.0.apk（26,566 bytes）。**实际相机是否静音仍须用户真机测试**。成功的独立视频播放器 v0.1.13及 mini v0.1.0 不改，main app 其他源码未改。
- **最短验收**：手机连接相机Wi-Fi，C110插可用SD卡；打开实验APP，检测标准C110，依次关闭9011/0→点击工具内拍照（不用相机实体键）→听相机机身声音，再尝试9011/1恢复并拍照对比。若失败复制诊断；不可直接判定固件不支持，需结合HTTP Status/设备回复。相机可能保留修改后的提示音状态。

---
# 2026-11-19｜C110 成功播放器独立竖屏接入蓝星向导 v0.1.13（CI成功，真机待测）

- **授权/边界**：用户明确要求先做一个独立「运动相机」板块，打开自动进入实时预览，手机竖屏；坚决复用已真机成功的 C110 OEM Minimal v0.1.0，不重复折腾RTSP、LibVLC及Surface门控。本轮不加分辨率切换、点击视频帧云OCR、相机电量、自动连接或Wi-Fi倒计时。
- **仓库和代码**：唯一仓库 vobichngan285-collab/kanzi-assistant，新实验分支 exp/c110-portrait-page-20261119，HEAD e3ff7d075f46abfc3927e0d06cfbf30013021045。新增 cloud-vision-app/app/src/main/java/com/vobich/bluestar/cloudprobe/C110CameraActivity.java，来自成功 mini 的 OemVideoActivity，包名/类名/简化顶部诊断和进入自动 begin 调整；begin→runWhenCurrent 间的成功版 HTTP模式指令、WiFi、LibVLC/Media/Surface/单次300ms播放核心逐字相同。独立 portrait Activity、原Android Light NoActionBar主题，不再把C110播放器嵌进手机/USB预览容器。保留原mini及RC25所有版本。
- **主界面**：底部「任务/AI对话/运动相机/高清拍照」，「阅读」放在任务宫格顶部快捷入口，「设置」放右上三点。运动相机按钮只打开独立Activity；原C110高清原图拍照与云端OCR/TTS代码未改。独立相机页现阶段只预览，有重连、返回、复制诊断，尚未接入视频点帧OCR，也没有真正验证全屏铺满和流畅度。
- **CI**：首次Actions 44369842354失败，仅因静态测试的Java字符串转义错误，已修测试；第二轮 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44370200027 completed/success，成功mini核心一致性断言、Java协议单测、USB既有补丁、Gradle :app:assembleDebug、APK包名/版本/native LibVLC/音效/ZIP、预发布全部通过。
- **Release**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-portrait-player-20261119-v0113；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-portrait-player-20261119-v0113/BlueStar_Guide_C110_PortraitPlayer_0.1.13.apk；大小56,666,425字节。隔离包名 com.vobich.bluestar.c110page，版本 1.0.38-c110-portrait-page-lab。CI无法证明真机出画。
- **唯一下一步**：先手动连接C110 Wi-Fi（本版尚无自动WiFi请求），安装隔离v0.1.13，点击底部「运动相机」确认能否自动播放真实连续画面且竖屏显示；若失败请收集页面可复制诊断及截图。未经过真机验证前，勿声称已出画成功；不要先改成功播放器内核，也不重复已经成功的高清照片验证。

---
# 2026-11-19｜C110 原厂成功预览已接入蓝星向导实验版 v0.1.12（CI通过，取帧OCR/TTS待真机）

- **用户正式授权**：在 C110 mini 独立视频首个成功截图后，用户明确说“可以现在接入”。本轮把真正成功的原厂式**最小视频播放流程**接入已有 Blue Star Cloud OCR/TTS 主界面，不改正式RC25，不复活v0.1.11失败的 Surface门控或手写RTSP。
- **GitHub和基线**：唯一原仓库 vobichngan285-collab/kanzi-assistant，新隔离实验分支 `exp/c110-live-frame-cloud-20261119`（从成功mini `6bd85802a31d0535e4209f8e012ba531297032e9` 继承），已发布 HEAD **`3afc788e689244f0cf4ac39c22da99bf8ef0f17e`**。首次代码提交 `adc53df8e60d3c55dc3477703867a4443bcb63a2`，编译错误修复提交 `3afc788e689244f0cf4ac39c22da99bf8ef0f17e`。新的测试包ID `com.vobich.bluestar.c110liveframe`，版本 `1.0.37-c110-liveframe-lab`，绝不覆盖之前RC25和已真机成功的独立mini包。
- **视频复制了真正成功路径**：新C110LivePreview保持Activity创建时VLCVideoLayout始终VISIBLE，并在视频时置顶；Native官方LibVLC3.6.5按成功mini使用网络/直播300ms缓存、原厂LY模式1 RTSP候选地址、单次延迟300ms `play()`、VideoHelper自动管理Surface，**无自定义Surface门控/手写RTSP SETUP/PLAY/自动重试**。相机C110WifiCamera.startPreview不再用旧的预先3002检查；按成功mini只读取 `3012→3016` 再 `3001/par1→2015/par1`；C110_PRO特殊版明确排除。Native进程WiFi临时绑定仅限当前App，停止、后台/切高清拍照时恢复之前网络。
- **独立用户交互与实际OCR链**：顶栏`C110预览`启动实况，用户**点击正在播放的视频画面**（或现有单次任务抓图入口）才通过 Android API26+ `PixelCopy.request`从真实VLC内部SurfaceView取**当前一帧**。非SUCCESS、Surface无效、帧过大、编码失败则明确失败，**不复用过期帧、不重启视频或按相机快门**；成功则内存Bitmap→JPEG质量90、释放Bitmap→现有`Frame.remoteJpeg`→`beginCapturedFrame`→CloudVisionClient/TargetVisionClient→已有本地TTS（保留用户任务、过滤、流式朗读和模型）。新`c110LiveFrame`来源标签与匿名数值诊断便于区分视频截图和高清照片。云端网络仍只使用显式`C110WifiCamera.requireCellular`的Cellular Network；视频WIFI无外网不阻断云请求。
- **高清拍照与其他功能**：旧`C110拍照`顶栏按钮、相机照片模式0→快门→DCIM原始高清JPEG下载→已有识别/TTS完全保留；旧USB/手机摄像头/阅读/模型及RC25本体未修改。新包与旧包偏好及Key分别保存，用户若要测试云端朗读需在**此实验包**内设置百炼Key并开启手机流量。
- **CI和发布实际验证**：第一次运行`44359060535`静态隔离/协议测试通过，但Java编译因新增界面先读取`CloudKeyStore.load()`未捕获checked Exception而失败；修复后第二轮 [Actions 44359584719](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44359584719) **completed/success**，Java协议测试、安全隔离检查、固定USB补丁、ARM64 Gradle`:app:assembleDebug`、APK身份/音效/native libvlc/ZIP验证、Artifacts上传和Release均通过。Release [bluestar-c110-live-frame-cloud-20261119-v0112](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-live-frame-cloud-20261119-v0112)，`BlueStar_Guide_C110_LiveFrame_0.1.12.apk` **56,649,997字节**，SHA256 `0992d5f76a3ad0833453aae1ab982eceb13be39b082b14f6824052d5cf827d78`，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-live-frame-cloud-20261119-v0112/BlueStar_Guide_C110_LiveFrame_0.1.12.apk。
- **唯一尚未验证项、下一步**：用户手机先连接C110 WiFi，打开新隔离v0.1.12→按C110预览，**首先必须确认仍显示真实连续C110画面**。然后确保在新包设置百炼Key并打开移动数据，**点击视频画面一次**，是否真正通过PixelCopy抓到一帧→云端识别→语音朗读，复制最新`c110_frame`数字日志。**仅原成功mini真机出画过，新集成v0.1.12的预览/PixelCopy/云端OCR/TTS尚未真机验证，不得声称端到端已成功。** 如失败只改新取帧接线，不再改已经成功的原厂播放初始化。高清拍照已验证，不要求重复测试。

---

# 2026-11-19｜C110 原厂流程最小播放器首次真机出画（重大突破，保留成功基线）

- **用户实机验证**：用户安装单独的 `BlueStar_C110_OEM_Minimal_0.1.0.apk` 后发来一张实机屏幕照片，并回复“可以”。截图清晰显示原厂式视频区域中拍到的实体键盘、鼠标和桌面，画面下方有 `C110` 水印，**已确认至少一帧来自 C110 的真实视频画面被新 APK 解码/显示**。静态截图本身不能独立证明连续帧率或长期稳定性；用户的“可以”可作为现场正常显示的反馈。**之前集成v0.1.11只有 `vlc_play_gate=0`、完全不出画，现已突破此关。**
- **成功的具体可复用基线**：独立分支 `exp/c110-oem-minimal-20261119`、HEAD `6bd85802a31d0535e4209f8e012ba531297032e9`；独立包 `com.vobich.bluestar.c110mini`，Release `bluestar-c110-oem-minimal-20261119-v010`；首次采用原厂LY相机身份状态查询 `3012 → 3016`，随后用户点击触发 `3001/par1 → 2015/par1`，再由 LibVLC 3.6.5 的**一直可见** `VLCVideoLayout` 和 VideoHelper 自己处理Surface；只进行一次300ms延迟 `play()`；**没有自行增加Surface gate，没有手写RTSP请求/自动重试**。原厂mode1 RTSP候选为 `rtsp://192.168.1.254:554/xxx.mp4`。当前截图不能单独区分上述各个因素的因果贡献；但整个方案已确实显示 C110 画面，不要重新引入失败门控。
- **这次未授权的工作不要做**：用户当前只是报告成功并给图，没有明确要求合并或发布蓝星向导新正式版。保留所有成功代码和当前实验包/Release，**先不改代码、不构建新APK、不覆盖正式RC25或已成功高清JPEG→云OCR/TTS链**。如果用户随后授权集成，应在同一仓库中以此最小成功的播放器为基线，将“取一帧真实相机视频 → 截图/Bitmap → 现有云端OCR/TTS”作为下一阶段；不重复以前已失败的模式/URL/Surface补丁。必须核实实际连续画面和可抽取帧后再宣称视频OCR完整可用。
- **用户 screenshot 的观察限制**：屏幕顶端按钮和诊断文字有遮挡/布局问题，视频画面居中，底部/顶部留黑。能出图是主突破，UI排版不是首要；如用户需要再改。不要从单帧静态截图推断实时FPS、延迟、最佳分辨率或长时间稳定。

---

# 2026-11-19｜SJCAM C110 原厂设备识别→模式→视频最小独立程序完成（源码/CI/Release均核验；尚未真机出帧）

- **用户背景和权限**：v0.1.11 真机 11-19 00:40:34、00:40:56 两次：`live_mode_cmd=0`、`live_cmd=0`、`vlc_wifi_bind=1`，只有`vlc_play_gate=0`，没有本应用放行`play()`。用户明确不接受补丁式、无限实验 APK，授权改为完全以原厂 App 为主，追踪设备身份、实际模式和视频URL，在确认代码路径后做最小视频播放器，不碰既有正式程序和照片链。
- **从用户原厂APK重新实查到的证据**：APK `bdcc3db17ea0e26aecaf22bc01fe99be.apk`，SJCAM Zone 6.7.3.16的`classes5.dex`直接解码方法体和常量：`LYCamera.connection()`→`getCameraVersion()`→`LYCamera$3.run()`发送只读`cmd3012`→`LYCamera$3$1.onResponse`解析包含`<String>`的设备信息，再进入`getCameraInfoByRequest`与`handlerConnection`；`handlerConnection()`读`cmd3016`当前状态，此外在不同条件下可读/写`2016/3014`，不应把所有分支指令当统一启动步骤；`changeCameraModel(1,...)`会将`mVideoUrl`设为`rtsp://192.168.1.254:554/xxx.mp4`、发送`3001/par1`，其`LYCamera$12.onResponse`启动`2015/par1`；`getVideoPlayUrl`对`C110_PRO`有额外固件分支，对普通LY模式1通常使用其`mVideoUrl`。原`VlcLyCameraPlayer.playByUrl()`延迟300ms、`VLCVideoLayout`由LibVLC自身维护Surface。**目前没有连接到用户实机获取具体CameraVersion或原厂成功流抓包**，即使静态分支明确也不能说真实C110已完全复现。
- **本轮变更的隔离策略**：未再修改已失败的集成v0.1.11分支，没有改`main`的任何应用业务代码。仍在**同一GitHub仓库**中新建独立分支`exp/c110-oem-minimal-20261119`，HEAD `6bd85802a31d0535e4209f8e012ba531297032e9`，单独新增Gradle子模块`cloud-vision-app/c110mini`，包名`com.vobich.bluestar.c110mini`、版本`0.1.0-oem-minimal`，唯一单屏App只负责：按用户一次点击→连接网关为192.168.1.254的Wi-Fi→读取`3012`设备类别线索、`3016`当前数值状态→对标准C110走原厂LY视频模式`3001/par1`→`2015/par1`→原厂LY模式1候选RTSP交给官方开源LibVLC 3.6.5播放。特殊`C110_PRO`分支停止，不猜测HTTP8192；未知原厂型号明确标记未识别。无自动重试。
- **不再补丁拦截播放**：新Activity从创建起一直可见`VLCVideoLayout`，用LibVLC`MediaPlayer.attachViews`管理Surface并在OEM约定的300ms后直接单次`play`，**没有自写Surface门控、反复RTSP探针、模拟PLAY、强制RTP/TCP或后台录像**。诊断仅屏幕临时文本，包含3012类别、3016数值、3001/2015状态、VLC事件Opening/Playing/Vout/错误及native状态；可单击复制，但不保存原始XML、设备序列号、照片/视频、API密钥。UI仅【识别C110并预览】【停止】【复制诊断】。VLC原生进程Wi-Fi临时绑定仅限本App，停止/退后台恢复原绑定。
- **安全/保留**：本模块无用户照片、云端OCR/TTS、DCIM、USB、BLE、OTA及固件命令；独立包不覆盖原Blue Star RC25、不动6轮已真机验证的C110 JPEG→蜂窝云OCR/TTS。原厂APK仅用于本地静态研究，未重打包或发布任何专有代码/二进制。没有宣称已在C110实机显示视频。
- **CI已成功且Release实物核验**：[GitHub Actions 44355342018](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44355342018) `completed/success`，GitHub静态命令隔离检查、Gradle`:c110mini:assembleDebug`、APK身份/ARM64原生libvlc.so/libvlcjni.so、ZIP、Artifact上传/Release均成功。Tag`bluestar-c110-oem-minimal-20261119-v010`、Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-oem-minimal-20261119-v010 ；APK`BlueStar_C110_OEM_Minimal_0.1.0.apk` 46,109,677字节，SHA256 `34fc6cc1664493336d13e00b79c6a789824867670254bf7122a6baa1e91d7d98`；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-oem-minimal-20261119-v010/BlueStar_C110_OEM_Minimal_0.1.0.apk。
- **下一步仅一次验收**：手机连接C110标准版的Wi-Fi，只启动该独立App并点击一次“识别C110并预览”，观察相机是否显示真实连续图像，复制它的临时诊断输出。不要再让用户安装多版本或测试已经成功的高清拍照；如未出画，首先以具体`3012/3016`和LibVLC事件结果判定真实固件/模式分支，**不得无证据重启补丁循环**。

---

# 2026-11-19｜停止补丁式修复：重新对原厂 SJCAM Zone 6.7.3.16 完整核查 C110 视频地址/模式/播放链（只读研究，无新APK）

- **用户最新v0.1.11实机反馈**（00:40:34、00:40:56）：`live_mode_cmd=0`、`live_cmd=0`、`vlc_wifi_bind=1`，但只有`vlc_play_gate=0`，无`vlc_play_gate=1`、无VLC Opening/Playing/Vout。**0是蓝星自加的 Surface 条件没有放行；本次甚至没能调用`current.play()`，不是相机RTSP错误的证据**。用户对反复更版严重不满，要求以原厂APP为突破口，不做无依据补丁。
- **本轮亲自重新取原文件验证**：用户上传`bdcc3db17ea0e26aecaf22bc01fe99be.apk`（SJCAM Zone `org.jght.sjcam.zone` 6.7.3.16，197152487字节，SHA256 `a07236df9b97b5a202179ab2bd98cead497bdcae5a6aa32e55113d49b37e1274`），重新从其真正`classes5.dex`枚举46636方法ID、34554实现体，对以下方法直接解析字节码、引用与条件跳转；不是仅靠旧报告/字符串搜索。
- **具体视频URL控制流（DEX事实）**：`LYCamera.<init>()`（code offset `0x18b95c`）初始化`mVideoUrl="rtsp://192.168.1.254:554/xxx.mp4"`；`LYCamera.changeCameraModel(int,...)`（`0x18bb24`）会更新`mVideoUrl`，对于模型/模式参数 **1、3、8、9、10、11** 设置为RTSP（参数2保留已有URL），对于其它值（包含0）设置为`http://192.168.1.254:8192`；同时发送实际模式命令3001。其`handlerConnection`（`0x18c4d8`）也按连接/模式返回设置`mVideoUrl`。**因此普通C110视频模式1实际仍可能按原厂代码使用RTSP xxx.mp4；不能再说mVideoUrl“动态”就证明前8版URL错**。
- **原厂`getVideoPlayUrl()`（`0x18b11c`）**：对一批特殊型号强制返回RTSP；对包含`C110_PRO`的版本条件可走HTTP8192；对没有命中特殊分支的普通C110从`mVideoUrl`返回。**注意“普通C110”是否属于LYCamera及固件分支、实际版本字符串未取得，不能把C110_PRO硬套给标准版**。`LYCamera.getCameraInfoByRequest()`（`0x18be10`）从相机响应设置`SCameraInfo.setCameraVersion`；`ConnectCameraFragment.onConnCameraSuccess$lambda$17`包含`C110`/`C110_PRO`以及`v1.0.4`/`v1.1.2`版本分流至不同控制页面，必须以实际返回信息确认用户设备分支。
- **原厂播放顺序（DEX事实）**：`NewCameraControlActivity.playVideo()`（`0x280a7c`）和`LyCameraControlActivity.playVideo()`（`0x2777bc`）均调用当前`ISCamera.getVideoPlayUrl()`并转交`VlcLyCameraPlayer.playByUrl(String,boolean)`（`0x1db198`）；该播放器用`Handler.postDelayed(...,300)`再执行`VlcLyCameraPlayer$1.run()`。原厂是**先确定具体相机/固件、模式、URL，再交播放器**，不是先硬编码候选RTSP然后在播放器外层无限缝补。
- **这次核查的最大架构差异**：蓝星 v0.1.11 并没有复制原厂“基于实际mode与版本选择URL”的逻辑，而是始终先`3001/par=1`再`2015/par=1`、固定RTSP，此外自定义Surface门控的`vlc_play_gate=0`禁止调用`play()`。**Surface门控未通并不证明原厂存在同样限制**。目前唯一肯定的是自己的门控妨碍了实验；是否因错误选择视频模式/URL导致旧版VLC无Opening，仍没有足够实机证据。
- **下一步的研究优先级**：先确认实际C110连接时返回的`CameraVersion`和`CameraModel`（仅核查原厂读取方式或用户原厂App设备信息；无需再重复拍照）以及原厂成功预览所处的模式和`getVideoPlayUrl`返回值，只有把这三项对齐，才制作一个**只负责视频的最小独立测试播放器**；复用原厂模式与URL逻辑，杜绝额外Surface播放阻断/猜测端口和额外超时补丁。保留正式RC25、旧高清JPEG→蜂窝云OCR/TTS、USB/M02S全部不动。
- **实际操作边界**：本轮为原厂APK只读研究及交接事实归档，没有修改蓝星向导源码、没有触发Actions或构建新版本，原隔离分支仍`0ff86facbe7d34c1c0f3d83c1176cfcedcc20231`。不宣称已拿到真机原厂RTSP抓包，也不宣称已找到最终根因。

---

# 2026-11-19｜C110 v0.1.10仅MediaChanged+Native状态0 → v0.1.11 Surface就绪门控诊断已编译发布，待真机

- **真机现状**：用户在v0.1.10于11-19 00:24:18和00:24:55两次开启C110预览：`live_mode_cmd=0`、`live_cmd=0`、`vlc_wifi_bind=1`、`vlc_phase=1..5`，`vlc_native_state1=0`、`vlc_is_playing1=0`；完整第一轮还含`native_state2=0`、`is_playing2=0`、`event_count=1`、`last_event=256`（MediaChanged，非Opening）、`route_alive=1`，App自己的20s无Vout超时代码2302。第二轮只看到第一次Native采样，**不能断言第二轮超时**。目前没有任何真实C110视频首帧证据。
- **源码依据/假设**：核查VideoLAN开源`MediaPlayer.play()`逻辑：`AWindow.areSurfacesWaiting()`时只保留请求、不立即执行`nativePlay()`；原`VLCVideoLayout`的实际video SurfaceView由VideoHelper通过ViewStub在attachViews时创建。当前v0.1.10未记录Surface生命周期；“Surface尚未就绪导致Native状态0”是**待验证根因假设**而非已确诊。已核对原厂SJCAM Zone 6.7.3.16相机模式3001/par1→启流2015/par1与LibVLC播放路径；固定URL只是曾经OPTIONS/DESCRIBE 200的候选，实机固件版本仍未知，不擅自改成HTTP8192。
- **v0.1.11仅实验播放层更改**：原仓库`vobichngan285-collab/kanzi-assistant`原分支`exp/bluestar-c110-integrated-20261118`，HEAD **`0ff86facbe7d34c1c0f3d83c1176cfcedcc20231`**，核心代码提交`cc172078fedc90083ae562ee9323e984b05e2043`，CI断言修正`0ff86facbe7d34c1c0f3d83c1176cfcedcc20231`。从VLCVideoLayout中找到VideoHelper inflated的实际SurfaceView，安装`SurfaceHolder.Callback`记录surfaceCreated/Changed/Destroyed；仅在Surface附着且`getSurface().isValid()`、宽高正数以及原厂300ms最低延迟同时满足时**单次**`current.play()`，用`playIssued`禁止多次Surface回调重复重播；停止、拍照/暂停时注销callback和delayed callbacks、VLC释放及恢复前一个网络绑定。
- **诊断改进**：增加匿名数值`vlc_surface_event`（1=create，3=destroy，4=已ready）、`vlc_surface_width`、`vlc_surface_height`（**屏幕显示Surface尺寸，不是摄像头流分辨率**）、`vlc_play_gate`（0=等待Surface，1=单次play已触发），超时/错误还记录`vlc_surface_ready`与`vlc_play_issued`。原`vlc_last_phase`、`vlc_event_count/last_event`、`vlc_native_state1/2`、`vlc_is_playing1/2`不变；为适应24事件上限，移除了每次phase1..5重复日志与多余模式时间记录。20s的2302仍是本地无Vout看门狗，不能当RTSP错误码。
- **影响范围严格限制**：仅隔离APK `com.vobich.bluestar.c110surface`，versionName`1.0.36-c110-surface-lab`；C110已成功JPEG→指定蜂窝网络云端OCR/TTS、USB/M02S和正式RC25未修改。未上传原厂APK或复制原厂专有.so；没有硬编码其他端口、强制TCP、重试或新相机指令。用户此前照片报错由于未填测试包Key，与本次预览无关。
- **测试与发布回读**：第一轮工作流`44348668763`仅因旧静态断言仍查`vlc_phase`而失败，**不是Android编译失败**；修正后[GitHub Actions 44348827279](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44348827279) 的Java协议单测/代码隔离、固定USB补丁、ARM64 Gradle、APK包名版本+libvlc原生库+音效、ZIP校验、上传、预发布所有Job步骤均success。发布Release [bluestar-c110-surface-gate-20261119-v0111](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-surface-gate-20261119-v0111)，APK `BlueStar_Guide_C110_Surface_0.1.11.apk` **56,650,001字节**，sha256 `8156865b00e6a6cbd39a456b5fa4cf4f6c3bb3ccfd9aa68fa934b814d45c8a3d`，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-surface-gate-20261119-v0111/BlueStar_Guide_C110_Surface_0.1.11.apk。
- **下一步唯一真机验收**：手机连接C110 Wi-Fi，安装v0.1.11点击一次C110预览。观察有无**真实连续C110画面**，只需提供最新`c110_live`日志，特别`vlc_surface_event`、`vlc_surface_width/height`、`vlc_surface_ready`、`vlc_play_gate`、`vlc_play_issued`以及`vlc_last_event`。Surface未就绪→查视图生命周期；Surface已就绪+play已发但仍只MediaChanged→查LibVLC内部/实际固件视频地址。不必填百炼Key、不再测高清拍照，不得把CI成功宣称成摄像头真实预览成功。

---

# 2026-11-19｜C110 v0.1.9仅MediaChanged(256) → v0.1.10原厂播放时序与Native状态诊断已CI成功发布，待真机

- **最新用户现场**：2026-11-19 00:07:15 v0.1.9一次C110实时预览：`live_mode_cmd=0`、`live_cmd=0`、`vlc_wifi_bind=1`、`vlc_phase=1..5`、`vlc_last_phase=5`、`vlc_event_count=1`、`vlc_last_event=256`、`vlc_route_alive=1`，没有Vout和视频尺寸，最终`vlc_error=2302`。核查VideoLAN事件枚举：`256`=MediaChanged，`258`=Opening，`259`=Buffering，`260`=Playing；**未收到Opening，仅媒体变更，不得据此宣称RTSP PLAY失败或解码失败**。`2302`依然只是App自己的20秒无输出看门狗。
- **本轮真正重新查原厂用户APK**：定位用户上传的`bdcc3db17ea0e26aecaf22bc01fe99be.apk`，SHA256 `a07236df9b97b5a202179ab2bd98cead497bdcae5a6aa32e55113d49b37e1274`，直接解析`classes5.dex`方法体，不是转述旧报告。`LYCamera.getVideoPlayUrl()`确实根据`SCameraInfo.getCameraVersion()`作多段型号/固件分支，方法含`rtsp://192.168.1.254:554/xxx.mp4`和`http://192.168.1.254:8192`两种候选及空返回；**当前C110真机的CameraVersion尚未拿到，不能冒用其他固件分支选地址**。而`VlcLyCameraPlayer.playByUrl()`使用`Handler.postDelayed(...,300ms)`后才跑播放器设置/调用play的Runnable；原厂`VlcLyCameraPlayer$1.run()`使用字段持有`Media`，非此前实验版在`setMedia`后立即释放Java Media。这是两项实际可核验的原厂差异。
- **v0.1.10隔离修正**：继续仅沿用原仓库`vobichngan285-collab/kanzi-assistant`分支`exp/bluestar-c110-integrated-20261118`，**HEAD `308ddf5ba1853ff16e19583154c2f0d29054ad75`**。在完成LibVLC+VLCVideoLayout及Media初始化后通过`Handler.postDelayed(delayedPlay,300L)`执行单次play；停用时移除未执行的回调，防止停止后重启。Media对象保留至stop（先播放器释放后Media释放），按原厂生命周期间隔。保留已经确认的3001/par1→2015/par1、可DESCRIBE200的RTSP候选，不猜测/盲测8192、不强制TCP、不再调用手写SETUP/PLAY探测。
- **Native状态诊断**：新数字字段`vlc_native_state1`/`vlc_is_playing1`为调用play后1.2s时原生`MediaPlayer.getPlayerState()`/`isPlaying()`；`vlc_native_state2`/`vlc_is_playing2`为超时前采样；每个value只记录状态整数，无原始VLC日志、RTSP URL、媒体数据、照片或密钥。保留`vlc_phase`、事件次数与编号、Wi-Fi绑定、输出尺寸及错误码。原生状态枚举整数与Event编号是两个概念，`isPlaying=0`不单独证明网络错误。Native VLC网络临时绑定C110 Wi-Fi仍在停止/失败/进入后台/拍照前恢复原先绑定。
- **隔离保障**：不改正式RC25、旧Release、C110已成功高清JPEG取图→蜂窝百炼OCR/TTS、USB和M02S。相机本身未实际连接于CI。用户先前拍照失败系独立测试包缺API Key，与本轮视频功能无关，不重复实验。新独立包名`com.vobich.bluestar.c110oemstart`，versionName`1.0.35-c110-oemstart-lab`，不会覆盖旧实验版。
- **构建审核**：第一次Actions `44345342317`因工作流**检查脚本提前使用preview变量**失败，非应用源码编译失败；已修复检查脚本并在[GitHub Actions 44345536038](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44345536038) 完成**completed/success**，Java协议/隔离审计、USB固定补丁、ARM64 Gradle、APK身份、VLC原生库/音效/ZIP、Artifact上传及Release全部成功。
- **已验证Release**：[bluestar-c110-oem-playback-start-20261119-v0110](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-oem-playback-start-20261119-v0110)，APK `BlueStar_Guide_C110_OEMStart_0.1.10.apk` **56,649,993字节**；SHA256 `905cfe3caca921d0f34c0f2de07244a0eac8a2c022e573a7ee6413ea44934757`；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-oem-playback-start-20261119-v0110/BlueStar_Guide_C110_OEMStart_0.1.10.apk。
- **下一步真机**：手机连C110 Wi-Fi，安装独立v0.1.10，**只点一次C110预览**；重点发最新`c110_live`看`vlc_native_state1/2`、`vlc_is_playing1/2`、`vlc_last_event`、`vlc_event_count`、`vlc_vout_count`和真实连续视频画面是否可见。无需API Key，不测已成功的照片；**不能仅凭CI构建证明预览成功**。

---

# 2026-11-18｜C110 v0.1.8 双次仅本地超时 → v0.1.9 原厂VLC视图与事件诊断已发布，待真机

- **最新用户真机证据（23:49:12、23:49:25）**：v0.1.8 两次均 `live_mode_cmd=0`、`live_cmd=0`、`vlc_wifi_bind=1`，随后 `vlc_error=2302` / reason2 / stage5，无`vlc_state`、Vout、视频尺寸。**2302 是App自己设的12秒无Vout看门狗，不是相机返回的RTSP错误，更不能断言原生LibVLC已收到404**。用户明确此前两次拍照失败原因是独立测试包未填百炼API Key，禁止把它当相机拍照故障或修改照片路径。
- **本轮原厂APK新增直接方法体核验**：从用户上传的真实 `bdcc3db17ea0e26aecaf22bc01fe99be.apk` (SJCAM Zone v6.7.3.16，sha256 `a07236df9b97b5a202179ab2bd98cead497bdcae5a6aa32e55113d49b37e1274`) 的 `classes5.dex` 静态解析`VlcLyCameraPlayer$1.run` 与构造函数，证实原厂先创建LibVLC/MediaPlayer→`MediaPlayer.attachViews(VLCVideoLayout)`→事件监听→Media→`setMedia`→`play`；构造函数默认 `cache=300`，视频选项含 `:sout-mux-caching`、`:live-caching`、`:file-caching`、`:network-caching` 四组均300及`:avcodec-skiploopfilter=4`、`:avcodec-skip-frame=0`、`:avcodec-skip-idct=0`、`:no-stats`、`:codec=mediacodec,iomx,all`、禁音。**v0.1.8自己手工SurfaceView绑定不同于原厂VLCVideoLayout，且只设两组缓存**。`LYCamera.getVideoPlayUrl()`也根据相机版本选择RTSP/HTTP/mVideoUrl，当前实机固件版本未核实，不能在已获SDP200的情况下凭猜测换URL。
- **v0.1.9独立实验实现**：原仓库 `vobichngan285-collab/kanzi-assistant` 原隔离分支 `exp/bluestar-c110-integrated-20261118`，HEAD `823198c2c01c770acfa9e5ff72b97cc8b07db4e6`。以VideoLAN官方开源LibVLC 3.6.5重建`VLCVideoLayout` + `MediaPlayer.attachViews(view,null,false,false)`，同步原厂300ms四缓存及解码选项，保留真实已验证的3001/par1→2015/par1、候选RTSP 192.168.1.254:554/xxx.mp4、Native预览Wi-Fi临时进程绑定与停用恢复。**不强制TCP、不再调用手写SETUP/PLAY、不重复8192/554端口探测；原照片JPEG→蜂窝云OCR/TTS、USB、M02S及正式RC25不动，未复制/发布原厂APK或专有二进制。**
- **新增匿名数值诊断**：`vlc_phase`=1路由绑定/2 LibVLC创建/3原厂布局attach/4媒体已设置/5 play调用；错误或**本应用20秒无Vout超时**会额外记录`vlc_last_phase`、`vlc_event_count`、`vlc_last_event`（VLC内部事件枚举，0表示未收到事件）、`vlc_route_alive`（仍绑定相机Wi-Fi）；`vlc_state`按状态改变记录，不记录原生原文日志、密钥、URI或视频。即使Vout非零，也还需要用户目视证明真画面。原`vlc_error=2301`是原生EncounteredError；2302仍是本地看门狗，2304表示创建/布局/播放初始化发生本地异常。
- **CI 和真实Release已双重核实**：[GitHub Actions 44342189997](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44342189997) **completed/success**，Java协议测试/代码隔离审计、固定USB补丁、ARM64 Gradle构建、APK身份、原生VLC库、音效、ZIP、上传和预发布全部成功。独立包名`com.vobich.bluestar.c110vlcdiag`，版本`1.0.34-c110-libvlcdiag-lab`，不覆盖旧版。
- **实际Release**：[bluestar-c110-libvlc-oem-diagnostics-20261118-v019](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-libvlc-oem-diagnostics-20261118-v019)，APK `BlueStar_Guide_C110_LibVLCDiag_0.1.9.apk` **56,650,001字节**，sha256 `62ae00bbe5e5e84707d8caa4ed113e2a5a29ddba7f0b7ddc03676445e588e561`；可复制直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-libvlc-oem-diagnostics-20261118-v019/BlueStar_Guide_C110_LibVLCDiag_0.1.9.apk。
- **下一步只有一次用户真机测试**：手机连接C110 Wi-Fi，安装v0.1.9，仅点一次C110预览，观察是否出现相机真实连续画面，复制最新完整`c110_live`；关键字段`vlc_phase`、`vlc_last_phase`、`vlc_event_count`、`vlc_last_event`、`vlc_route_alive`、`vlc_state`、`vlc_vout_count`、`vlc_error`。无需API Key、无需再拍照。**此版本通过构建，但真实视频出帧尚未证实**。

---

# 2026-11-18｜C110 v0.1.8 原厂 LibVLC 视频引擎专项已构建发布，待真机验证

- **用户授权与方向**：用户要求突破口优先放在 C110 原厂 SJCAM Zone App，批准开始移植其播放路线，不再继续凭猜测修手写 PLAY URI。2026-11-18 23:23:19/23:23:37 v0.1.7 实测：录像模式/启流命令均Status0、554可达、DESCRIBE200、SDP flags999、视频轨道1、TCP SETUP200、PLAY404、playTarget1、RTP=-9、视频尺寸0，未出画面。拍照 `photo=0` 是用户明确未填测试包百炼 API Key，**不是相机拍照失败，不纳入修复**。
- **原厂代码依据**：用户提供的 SJCAM Zone 6.7.3.16 APK，LY路径采用 `LYCamera.changeCameraModel(3001)`→`startLiveVideoStream(2015/par1)`→`ISCamera.getVideoPlayUrl`→`VlcLyCameraPlayer.playByUrl`，借助LibVLC处理RTSP并设置网络/直播缓存。原厂播放器包装层没有找到显式强制TCP；**不代表实机一定走UDP**。本轮使用官方VideoLAN开源LibVLC 3.6.5（不是把原厂3.0.x二进制复制进包），按同类架构对照，而非宣称完全复刻原厂所有选项。
- **v0.1.8变更**：原实验分支 `exp/bluestar-c110-integrated-20261118`，HEAD `7beda3274538b1dc556ac0ed2cb240671acce205`。仅主动预览路径从Media3强制TCP改为`org.videolan.android:libvlc-all:3.6.5`，以原已验证候选 `rtsp://192.168.1.254:554/xxx.mp4` 通过LibVLC播放（由VLC选择RTSP传输），保留实际成功的3001/par1→2015/par1启动命令。**移除当前预览路径的手写RTSP SETUP/PLAY调用以及8192/554重复探测**，保留过去源码与测试作实验档案。
- **网络和安全边界**：LibVLC原生Socket无法直接使用Media3的`Network SocketFactory`，因此在点击预览时临时调用`ConnectivityManager.bindProcessToNetwork(cameraNetwork)`；停止/报错/Activity暂停/拍照前先释放VLC后恢复原有进程绑定，并校验当前绑定仍是本次C110网络。实验版Manifest新增`CHANGE_NETWORK_STATE`。云端仍使用蜂窝`Network.openConnection`；正式RC25及旧照片版独立包名、已有相机高清JPEG下载/TTS/USB/M02S均未改，未随APK再发布任何原厂专有APK或`.so`。
- **匿名诊断/验收边界**：`vlc_wifi_bind=1`是Native路由绑定成功，`vlc_state`播放器状态，`vlc_vout_count`和`vlc_vout_ms`说明VLC创建视频输出（**不等于真实首帧已在屏幕渲染**），`live_width_px/height_px`为视频尺寸回调，`vlc_error/reason/stage`为异常分类。12秒没有Vout就结束一次试验；无需API Key，不触发自动拍照或重复PLAY。**真实视频是否显示仍待用户在C110现场目测并发送一次最新c110_live日志**。
- **构建与发布已回查**：[GitHub Actions 44338653872](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44338653872) `completed/success`，Java回归与安全断言、USB固定补丁、ARM64 Gradle构建、APK包名/音效/native `libvlc.so`及`libvlcjni.so`核查、ZIP完整性、上传和Release均通过。独立包名`com.vobich.bluestar.c110vlc`，版本`1.0.33-c110-libvlc-lab`。
- **真实Release**：[bluestar-c110-libvlc-oem-player-20261118-v018](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-libvlc-oem-player-20261118-v018)，APK `BlueStar_Guide_C110_LibVLC_0.1.8.apk` **56,650,005字节**、SHA-256 `2f5836e9cc96225548f777a228db2c427b0d211af6af9020c79601858a6a1f1c`；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-libvlc-oem-player-20261118-v018/BlueStar_Guide_C110_LibVLC_0.1.8.apk。此前所有Release和正式RC25均保留。
- **下一步唯一测试**：手机先连接C110 Wi-Fi，安装独立v0.1.8，点击一次「C110预览」，观察是相机真实连续画面、黑屏还是错误，复制最新`c110_live`（包括`vlc_wifi_bind`/`vlc_state`/`vlc_vout_count`/宽高/错误）。**不填百炼Key、不再测高清拍照、不对出现Vout夸大为已实现预览**。

---
# 2026-11-18｜C110 v0.1.6 真机SETUP200/PLAY404 → v0.1.7 PLAY URI专项已通过CI并发布，待真机

- **最新真机反馈（23:09:54、23:10:19）**：C110 v0.1.6两轮均 `live_mode_cmd=0`、`live_cmd=0`、`tcp554=1`、`OPTIONS=200`、`DESCRIBE=200`、`rtsp_sdp_flags=999`、`rtsp_track=1`、`rtsp_tcp_setup=200`，但 `rtsp_tcp_play=404`，RTP=-9且解码画面宽高为0，**视频仍没有真实首帧**。这个404是PLAY目标请求失败，不代表相机没有视频流，-9是还没发起RTP检查。
- **v0.1.6代码确证问题**：SETUP按SDP解析到的视频轨道URI发送，但PLAY与TEARDOWN仍固定发送 `rtsp://192.168.1.254:554/xxx.mp4`。若SDP没有提供聚合控制URI，PLAY与SETUP会指向不同资源；此是待真机验证的可能原因，非确定根因。按照RTSP 1.0（RFC2326）会话级 `a=control` 表示聚合播放控制，媒体级表示轨道控制。
- **v0.1.7修改严格限制**：原仓库隔离分支 `exp/bluestar-c110-integrated-20261118`，最终 HEAD **`2efa82c1f194d00a6a44902f8def216c3bd06b95`**。增加 `videoTargets`：有会话级SDP控制（含 `*`）时发送聚合PLAY；未声明聚合控制时只对成功SETUP的单视频轨道发送PLAY。相同Session复用一次，TEARDOWN与PLAY一致。不尝试多个URI、无自动第二次PLAY、禁用跨主机/可疑URI；只新增匿名字段 `rtsp_play_target`，1=会话级聚合，2=单轨道，-9=未选出有效目标；保留原track/setup/play/rtp数值。不改Media3主播放器、JPEG拍照、蜂窝OCR/TTS、正式RC25、M02S或固件。
- **回归与真实编译验证**：新增纯Java测试覆盖无聚合控制、`a=control:*`、会话与媒体目标不同、仅会话控制、非法聚合URI。第一次 CI 发现 `Report` 参数数量错误，第二次处理了静态断言，最终改正后 [GitHub Actions 44333672807](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44333672807) **completed/success**：Java协议/隔离安全、固定USB补丁、arm64 Gradle APK、ZIP校验、包名/音效/native libs、上传和预发布全部通过。不要将此前中间失败Runs认作发布。
- **已核实Release**：[bluestar-c110-play-target-diagnostics-20261118-v017](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-play-target-diagnostics-20261118-v017)，APK **`BlueStar_Guide_C110_PlayTarget_0.1.7.apk`**、11,834,143字节、sha256 `0d96409860cc9999b5346353c793708e11e366239dbae30a3e49aa2b2a83289e`；安装直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-play-target-diagnostics-20261118-v017/BlueStar_Guide_C110_PlayTarget_0.1.7.apk。独立包名 `com.vobich.bluestar.c110play`，不覆盖RC25/此前验证成功的静态取图APK。
- **下一步只需一次人工验收**：手机连C110 Wi-Fi，安装 v0.1.7 点一次顶部「C110预览」，复制最新 `c110_live` 整段，重点看 `rtsp_play_target`、`rtsp_tcp_setup`、`rtsp_tcp_play`、`rtsp_tcp_rtp` 和是否有正数首帧/宽高。无需API Key、无需重试已成功的拍照，不应声称本次已实现真实视频预览。若PLAY=200且RTP可见，下一步才去解决Media3播放器显示问题。

---
# 2026-11-18｜C110 v0.1.5 两次RTSP轨道未识别 → v0.1.6 定向修复已发布，待真机

- **用户最新真机证据（22:49:01与22:49:09）**：在v0.1.5两次单击C110预览，`live_mode_cmd=0`、`live_cmd=0`、`tcp554=1`、`rtsp_options=200`、`rtsp_describe=200`、`rtsp_sdp_flags=999`；但`rtsp_track=-2`、`rtsp_tcp_setup=-9`、`rtsp_tcp_play=-9`、`rtsp_tcp_rtp=-9`、视频宽高=0、Media3错误2000/原因5/阶段3。**-9是请求未进行，不是摄像头拒绝TCP；视频依然没有成功出首帧。**
- **源码定位**：v0.1.5的`C110RtspSessionProbe.java`只识别首个video媒体段内的非星号`a=control`，没有解析全局会话级control、通配`*`；当DESCRIBE未提供Content-Length时会把SDP正文当空串；合法无显式554端口的URI也会被拒绝。这些是源码可证实的兼容性限制，但**究竟是哪一条导致用户-2仍需新诊断区分**。
- **v0.1.6 精确修复**：继续原隔离分支`exp/bluestar-c110-integrated-20261118`，HEAD `90ea48562fc006e729362a07fd3019a2441fa3c2`。只改SDP正文读取与首个视频轨道解析：有Content-Length时定长读，没有时受限16KiB并使用短空闲超时；首个video媒体级control优先，缺失时回退会话级control；星号仅映射已有已验证的主RTSP URL；允许合法相对/绝对track URI和省略默认554端口，但始终约束`192.168.1.254:554`，拒绝跨主机、异常权限/查询/片段与路径穿越。`rtsp_track`分类：1=轨道正常，-2=无控制字段，-3=SDP正文空，-4=无video媒体段，-5=非法/不可信控制URI，-6=DESCRIBE非200。SETUP/PLAY/RTP只在成功提取合法轨道后按上一版协议进行，未更改原媒体播放器、快门、完整JPEG→蜂窝百炼OCR/TTS、RC25或M02S。
- **代码检查与构建实证**：新增纯Java`C110RtspSessionProbeTest`，覆盖全局/媒体控制、`*`、相对/绝对轨道、默认端口、安全拒绝及缺Content-Length SDP；[GitHub Actions 44331161234](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44331161234) **completed/success**：Java回归/静态隔离、RC25固定USB补丁、ARM64 Gradle构建、APK身份、原生库、音效、签名包及发布全部成功。独立安装包名`com.vobich.bluestar.c110track`，旧Release不覆盖。
- **真正核实的预发布**：[bluestar-c110-track-parser-diagnostics-20261118-v016](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-track-parser-diagnostics-20261118-v016)，APK `BlueStar_Guide_C110_RtspTrack_0.1.6.apk` **11,834,135字节**，SHA256 `89cd63a4ff6f45f78ff4ea6aef21b686e2055d6a9a841a09c508c3299cabb507`，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-track-parser-diagnostics-20261118-v016/BlueStar_Guide_C110_RtspTrack_0.1.6.apk。
- **下一步唯一真机验收**：手机连接C110 Wi-Fi，安装v0.1.6，点击一次顶部C110预览，不必配置API Key或重复已经成功的高清拍照；复制当次`c110_live`全部数字日志。若`rtsp_track=1`，再看SETUP状态是否200/461、PLAY/RTP是否成功；若仍负数按分类定位。**此版本只是修解析+诊断，实时画面尚未实机成功，不得宣称已修复。**

---

# 2026-11-18｜C110视频模式+RTSP DESCRIBE均成功，v0.1.5 RTSP TCP SETUP/PLAY专项已发布

- **用户v0.1.4实机结果（22:31）**：两次「C110预览」均 `live_mode_cmd=0`（3001/par=1录像模式成功）、`live_cmd=0`（2015/par=1启动流成功）、`tcp554=1`、`rtsp_options=200`、`rtsp_describe=200`、`rtsp_sdp_flags=999`，而 `live_width_px=0, live_height_px=0, live_rtsp_error=2000, live_rtsp_cause=5, live_rtsp_stage=3`，**未出现任何视频首帧**。SDP flags 999 = 512(application/sdp)+256(rtpmap)+128(fmtp)+64(control)+32(sprop SPS/PPS)+4(H264)+2(m=video)+1(v=0)：有H264视频描述和SPS/PPS，优先排查 SETUP/PLAY 而非再猜缺SPS/PPS。8192仍TCP拒绝=-2，不继续重复此端口。截图画面来自手机Camera2回退，不是C110真实帧。
- **成功功能保持**：22:31:23 C110单次高清拍照真正成功：`mode_ms=415, shutter_ms=418, discover_ms=481, jpeg_ms=232, photo_total_ms=1244`，JPEG `1,127,176B`、`5888×3312`，云端network_ms=3078、first_text_ms=2743；手机Wi-Fi仍存在，说明 C110 高清JPEG→蜂窝百炼云端OCR线路继续可用。旧正式RC25及上一独立已成功接入APK不要覆盖。
- **原因判断边界**：Media3 实验播放器 `RtspMediaSource.Factory.setForceUseRtpTcp(true)` 强制 RTP/TCP；按 [Android官方Media3 RTSP文档](https://developer.android.com/media/media3/exoplayer/rtsp) 可同时支持UDP或TCP，但原厂LY控制链用VlcLyCameraPlayer。当前 DESCRIBE200 还不能推断 SETUP/PLAY 或 RTP数据通；流媒体错误是RtspPlaybackException阶段3，具体是否TCP被拒绝（如461）、PLAY拒绝或Media3兼容性问题仍待真机。不能贸然反复更换地址/模式。
- **v0.1.5新隔离专项**：延续 `exp/bluestar-c110-integrated-20261118` 分支，HEAD `0e919dd917386921b96fda14b1ed227d578a1add`。在用户明确点击「C110预览」、原厂模式3001/par1 + 启流2015/par1成功但Media3失败**之后**，先释放Media3播放器，再在同一Wi-Fi绑定Network进行**一次性、限制字节/超时** RTSP TCP协商：DESCRIBE→读取本次SDP第一个video track `a=control`（严格只允许已知IP:554，不回传、不打印SDP或真实轨道URL）→SETUP `RTP/AVP/TCP;unicast;interleaved=0-1`→若200则PLAY并检测是否有interleaved RTP首包→TEARDOWN释放本次RTSP Session。仅匿名记录 `rtsp_track`、`rtsp_tcp_setup`（如200或461）、`rtsp_tcp_play`、`rtsp_tcp_rtp`（1见RTP，-1超时，0非RTP）。不自动重拍/上传视频/删除相机文件，也不改现有高清静态照片路线。支持解析结果决定下一步是否采用原厂VLC兼容内核并解决视频Wi-Fi/蜂窝路由。
- **已核实构建发布**：[GitHub Actions 44326527490](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44326527490) `completed/success`，原USB patch、Java协议单测、arm64构建、APK签名包名/音效/原生库检查全部通过；独立包名 `com.vobich.bluestar.c110tcp`。Release：[bluestar-c110-tcp-session-diagnostics-20261118-v015](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-tcp-session-diagnostics-20261118-v015)，APK `BlueStar_Guide_C110_RtspTcp_0.1.5.apk` **11,834,143B**，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-tcp-session-diagnostics-20261118-v015/BlueStar_Guide_C110_RtspTcp_0.1.5.apk。旧Release和RC25原样保留。
- **下一步唯一验收**：用户连接C110 Wi-Fi，安装v0.1.5，点击一次「C110预览」，等待诊断完成，复制最新 `c110_live` 整段。若 `rtsp_tcp_setup=461` 则优先验证原厂RTP/UDP/VLC路线；若SETUP/PLAY均200且收到真实interleavedRTP，则相机TCP流已成功，需调整Media3兼容或换VLC播放器；如果RTSP轨道解析-2就先核对SDP的a=control语义。实时预览仍未成功，不得宣称已修好。

---

# 2026-11-18｜C110 RTSP 预览 v0.1.2 实测缩小问题；v0.1.4 补模式前置和SDP诊断（已构建）

- **用户 v0.1.2 真机两次完全一致（22:15:28、22:15:35）**：相机 `192.168.1.254` 的 LY `2015/par=1` 报 `Status=-13`（执行失败）；`tcp554=1`、RTSP `OPTIONS=200`、`DESCRIBE=200`，可确认候选 `rtsp://192.168.1.254:554/xxx.mp4` 响应 RTSP 基本请求，但**不能证明SETUP/PLAY/视频帧**；`tcp8192=-2` 表明此刻该候选端口拒绝连接，`http8192=-9` 因端口不开放而跳过。Android Media3 `live_rtsp_error=2000`/原因类别5，视频解码 `width=height=0`，现阶段无实时画面。与此同时C110高清拍照/蜂窝云端正常：`photo_total_ms=902`、JPEG `1,379,123B`/5888×3312、云端 `network_ms=2279`；旧拍照完整链路无须再重复验证。
- **结合原厂 Zone APK DEX 的推断**：其 LY 后端的某条预览调用链是 `changeCameraModel()→3001模式切换→startLiveVideoStream()→2015/par=1→播放器`，而 v0.1.1/0.1.2 只直接发送 2015 未进入视频模式，可能造成 -13。Android Media3 支持 RTSP，但对 SDP/编码有约束，H264 需 SDP 中的 SPS/PPS 初始化参数。此为合理待验证假设，**不能宣称模式切换/SDP已证实是根因**。RTSP DESCRIBE 200 不代表媒体流可播放或代表实际录像分辨率。
- **v0.1.4 隔离修正**：仍在原 GitHub 分支 `exp/bluestar-c110-integrated-20261118`，HEAD `258cbf4a19149718dda704640f369819a9bdb934`。只有用户点击 `C110预览` 才发送一次 `3001&par=1` 切入录像/预览模式；仅确认模式 Status0 后再发送一次 `2015&par=1`，并单独记录 `live_mode_cmd/live_mode_ms/live_cmd`。RTSP端口及 OPTIONS/DESCRIBE 仍为只读诊断；新增受限长度的 SDP `rtsp_sdp_flags`（匿名bitmask：1=v0、2=video、4=H264、8=H265、16=MJPEG、32=SPS/PPS、64=control、128=fmtp、256=rtpmap、512=SDP MIME）和 `live_rtsp_stage`（按解析/RTSP协议/传输/I/O/非法参数等分类），**不记录实际 SDP、视频或 API Key**。中间 commit `821a8dd...` 修正 SDP 探测请求的实际 CRLF 行结束符；因此不能推荐更早的v0.1.3作为最终测试。视频仍为候选URL + Media3；若因兼容性问题失败，再按真实 SDP/协议错误决定是否换原厂使用的VLC，不先乱加新链接。C110 拍照仍通过已证实的 3001/par0、1001、目录新增 JPEG 下载并交由蜂窝云端OCR/TTS。
- **CI 与发布已核实**：[Actions 44323884180](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44323884180) `completed/success`，协议/静态/RC25 USB patches/ARM64构建、原生库/音效/包名验证通过。新包名 `com.vobich.bluestar.c110liveinit4`，版本 `1.0.29-c110-liveinit4-lab`，不覆盖RC25或之前成功照片版。新预发布 [bluestar-c110-liveinit-diagnostics-20261118-v014](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-liveinit-diagnostics-20261118-v014)，APK **BlueStar_Guide_C110_LiveInit_0.1.4.apk**，11,834,143字节，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-liveinit-diagnostics-20261118-v014/BlueStar_Guide_C110_LiveInit_0.1.4.apk 。此前 0.1.3 中间预发布不是推荐真机版本，保留不覆盖。
- **下一步只需单次预览真机实验**：手机连接C110 Wi-Fi后打开v0.1.4，点击一次顶部「C110预览」，复制完整 `c110_live` 匿名日志；重点是 `live_mode_cmd` 是否0、`live_cmd` 是否从-13变0、`rtsp_sdp_flags`、`live_rtsp_stage`，及是否有 `live_first_ms` 和解码尺寸。未成功前不触碰正式RC25、M02S、相机固件或实测良好的高清快门流程。

---

# 2026-11-18｜C110 实机RTSP端口与SDP已应答但视频未出：原厂模式初始化实验版v0.1.4

- **用户最新真机日志（22:15:28、22:15:35重复两次）**：独立集成测试包 v0.1.2 C110 `2015/par=1` 返回 `Status=-13`（相机执行失败），但在 `192.168.1.254:554` 直接 TCP连接成功（tcp554=1），RTSP `OPTIONS=200`、`DESCRIBE=200`；`8192` 端口 TCP拒绝（-2），HTTP8192=-9表示未探测。Media3 `ERROR_CODE_IO_UNSPECIFIED=2000`、内部 `Rtsp` 类异常5，解析出的视频宽高均0，**从未解码出实时视频帧**。后续22:15:40高清静态拍照仍成功：照片5888×3312、1,379,123字节、取图902ms、云端network_ms2279；**原拍照→云端OCR/TTS主链正常，不能因为预览失败修改这条通路**。
- **证据推断边界**：RTSP OPTIONS/DESCRIBE 200 证明原厂DEX的 `rtsp://192.168.1.254:554/xxx.mp4` 是**真实有响应的候选服务与描述路径**，不等于 RTSP SETUP/PLAY 已建立或真实流解码成功；无需盲猜新地址。LY原厂 `LYCamera.changeCameraModel()→3001 mode→2015/par1→VlcLyCameraPlayer`，旧蓝星仅发2015，尚缺录像模式前置；`-13` 可能由未初始化模式或其它预览状态导致，**不能确定必然原因**。Media3当前仅报告内部Rtsp类异常，未区分SDP解析、SETUP/PLAY、RTP/codec。原厂使用VLC，Media3严格要求H264 SDP中可用SPS/PPS（见Android官方Media3 RTSP指南：https://developer.android.com/media/media3/exoplayer/rtsp）。没有理由再测试8192当前拒绝连接端口。
- **本轮 v0.1.4 已实现/编译**：继续原仓库隔离分支 `exp/bluestar-c110-integrated-20261118`，HEAD `258cbf4a19149718dda704640f369819a9bdb934`。只有用户点击顶部「C110预览」才发送一次 `3001&par=1` 切到录像模式；如果返回Status0，才执行一次 `2015&par=1` 启动预览，继续仅针对已确认的554 URL跑播放器；分别记录 `live_mode_cmd/live_mode_ms/live_cmd`。已有只读RTSP 554 OPTIONS、DESCRIBE成功后，增加**有界24KiB的只读DESCRIBE**，匿名记录 `rtsp_sdp_flags` 位掩码，1=v0、2=video、4=H264、8=H265、16=MJPEG、32=sprop-parameter-sets、64=control、128=fmtp、256=rtpmap、512=application/sdp，不记录完整SDP/视频帧/密钥；增加 `live_rtsp_stage`，1=ParserException、2=RTSP transport461、3=RTSP其他、4=IO、5=IllegalArgument、9=other。前期中间的v0.1.3诊断稿曾存在CRLF双反斜线的RTSP探针错误，已在后续提交纠正，并以**v0.1.4作为唯一推荐测试包**，避免把中间构建提供给用户。已存在的旧Releases均不覆盖。
- **构建发布实际证据**：[Actions 44323884180](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44323884180) `completed/success`（静态/Java协议测试、原USB修复脚本、Android arm64 assembleDebug、APK/包名/版本/原生库/语音资源校验及Release发布全部通过）；[Release bluestar-c110-liveinit-diagnostics-20261118-v014](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-liveinit-diagnostics-20261118-v014)，APK `BlueStar_Guide_C110_LiveInit_0.1.4.apk` 11,834,143字节，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-liveinit-diagnostics-20261118-v014/BlueStar_Guide_C110_LiveInit_0.1.4.apk ，测试包名 `com.vobich.bluestar.c110liveinit4`；原RC25、上一版已成功C110拍照集成、M02S均不改。
- **下次只做一次真机预览**：连接C110 Wi-Fi，安装v0.1.4，点「C110预览」一次，反馈完整`c110_live`匿名日志，观察是否 `live_first_ms>0`、解码视频宽高。若 `live_mode_cmd=0` 且 `live_cmd=0` 仍Media3解析失败，根据 `rtsp_sdp_flags/live_rtsp_stage` 决定是否值得使用**与原厂相同的VLC播放内核**而非再试其他URL；如果 mode 仍非0，先读真实模式初始化失败值。**不得宣称C110真实预览已经成功，不能覆盖已可用的C110高清照片→百炼OCR/TTS。**

---

# 2026-11-18｜C110 预览失败真实日志→v0.1.2 无损状态码与RTSP双端口诊断包

- **用户最新现场（22:05）**：蓝星向导 C110 预览0.1.1的高清拍照仍成功：`c110.photo_total_ms=1029`、`mode_ms=17`、`shutter_ms=406`、JPEG **1,256,313 字节、5888×3312**，百炼云端 `cloud.network_ms=3173`，继续证明高清静态拍照→上传云端可用。视频端 `preview_try=1`、`live_cmd=-1`、`live_rtsp_error=2000`、`live_width_px=0/live_height_px=0`、`live_http8192=-1`，没有任何实际解码视频帧。
- **源码精确原因及解释**：v0.1.1 `RuntimeDiagnostics.event()` 将负数状态统一 `Math.max(-1,...)`，`live_cmd=-1` 不能辨认原厂2015是否返回 `-13`、`-256` 或其他状态；旧8192读请求吞掉异常返回-1，也无法区分HTTP/原始码流、TCP拒绝或超时。Media3 `PlaybackException.ERROR_CODE_IO_UNSPECIFIED=2000` 为未分类I/O失败，并不单独证明RTSP地址无效。原厂DEX记录有 `3001 mode→2015启动→获取机型URL→播放器` 链，但本次只发送2015，是否欠缺切录像模式/不同URL或SDP/RTP兼容性仍需真机判别，**不可根据-1武断下结论**。
- **本轮仅做明确诊断修复**：原仓库隔离实验分支 `exp/bluestar-c110-integrated-20261118` 新HEAD `c68f95dff9d140a5adaf4d8b01adda161de3135b`。新增 `C110PreviewProbe.java`，只对已在SJCAM Zone DEX发现的相机 `192.168.1.254:554` / `:8192` 使用正确 Android Wi-Fi `Network.getSocketFactory()` 检测TCP端口，读一次 RTSP `OPTIONS`/`DESCRIBE` 响应码及HTTP 8192响应码，不存储源图/SDP/原始流，不尝试猜测新写命令或陌生地址。保留来自2015的负值状态（未知XML状态使用-4096），增加 Media3异常类别 `live_rtsp_cause` ；如播放器失败自动收起黑视频播放器，原C110高清照片→蜂窝云端OCR/TTS不改。新的诊断字段：`live_cmd, tcp554, rtsp_options, rtsp_describe, tcp8192, http8192, live_rtsp_error, live_rtsp_cause`。TCP探针码：1=开放，-1=超时，-2=拒绝，-3=无路由，-4=安全限制，-5=其他；RTSP返回HTTP式3位状态码、-6=非RTSP首行、-9=端口未开放。Media3根因类别1=timeout、2=connect、3=noRoute、4=security、5=Rtsp内部、6=DNS、7=IO、9=其他。
- **构建与发布已核实**：[Actions 44321365510](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44321365510) `completed/success`（协议测试、静态安全审计、原USB补丁、arm64编译、APK身份/原生库/音效校验、GitHub Release发布成功）。独立新包名 `com.vobich.bluestar.c110probe`，避免不同GitHub runner的调试签名不一致导致覆盖安装失败；不会覆盖正式RC25或上一实验v0.1.1，测试预览不需要设置百炼API Key（如需测试云端另填）。[Release bluestar-c110-preview-diagnostics-20261118-v012](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-preview-diagnostics-20261118-v012)，APK `BlueStar_Guide_C110_PreviewProbe_0.1.2.apk` **11,834,135 字节**；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-preview-diagnostics-20261118-v012/BlueStar_Guide_C110_PreviewProbe_0.1.2.apk 。
- **下一步唯一现场测试**：连接 C110 Wi-Fi，打开新独立测试包，点击一次顶部「C110预览」，复制当次`c110_live`完整匿名诊断；从真实2015返回与554/8192连通和RTSP DESCRIBE码决定是缺前置模式、视频端口关闭、URL不匹配、RTSP不支持TCP还是解码问题。**当前尚未完成真实C110视频显示**。不要重复已成功的六轮纯拍照，也不改相机固件、正式RC25或旧Release。

---

# 2026-11-18｜蓝星向导C110预览 v0.1.1：补实时RTSP视频开关，已构建待真机

- **用户新真机证据（21:48–21:50）**：独立集成0.1.0真实多次C110拍照→云端识别，用户明确“可以，而且速度很快”；日志C110完整JPEG均为5888×3312、1.4–1.8MB，`jpeg.encode_ms=0`，`c110.mode_ms=0`，单轮 `c110.photo_total_ms=1001/1112/1172/1619/1121` 等；蜂窝云端 `cloud.response_headers_ms` 多在1.8–1.96s，TTS onStart仅为回调不等于扬声器真实出声。手机后摄旧识别仍可用。用户提出**当前拍照无法看到C110实时预览**，要求先查原因再修改。
- **源码定位**：旧 C110 接入仅有硬件照片获取 `C110WifiCamera.shoot`、`MainActivity.captureC110OneShot`，主界面依旧展示手机Camera2或USB TextureView。没有C110视频播放器、`2015`启动指令或 RTSP解码器，因此屏幕取景并非C110；不是证明设备本身不支持预览。
- **原厂APK DEX资料**：`experiments/c110-camera-protocol/C110_DEX_RESEARCH_AND_20261118_HANDOFF.md` 原先发现 LY `2015&par=1`、`rtsp://192.168.1.254:554/xxx.mp4` 及另一候选 `http://192.168.1.254:8192`。它们是该相机后端**代码候选**，当前真机采用哪一条仍须实测；2015是从原厂控制预览链提取，并非已在这台C110验证状态。
- **v0.1.1本次修改**：沿用原仓库独立测试分支 `exp/bluestar-c110-integrated-20261118`，新 HEAD `4426df88682a49d028fa4518ad50413e2ddf5027`。新增 `C110LivePreview.java`，Android Media3 RTSP播放 `xxx.mp4`，`Network.getSocketFactory()` 显式使用C110 Wi-Fi，`setForceUseRtpTcp(true)` 避免RTP跑错网络。顶部新增手动「C110预览」开关；只在用户点击预览时校验已知C110设备并发一次 `2015&par=1`，播放失败报告 `c110.live_rtsp_error` 与只读 HTTP 8192探测结果；首帧与解码宽高记录 `c110.live_first_ms/live_width_px/live_height_px`。在预览时点击画面/高清快门，先关闭流、重新进入照片模式、保留旧1001→新增完整JPEG→蜂窝云端识别朗读，取图后尝试恢复用户已开启的预览；退后台释放播放器。不抓屏，不自动拍照、不删照片、不刷固件。独立包名 `com.vobich.bluestar.c110preview`，不覆盖RC25/上一集成0.1.0；新包需自行配置API Key。
- **真实CI/Release**：[Actions 44319037996](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44319037996) `completed/success`（协议单测、静态隔离检查、原RC25 USB patch、Gradle arm64、APK验证及发布成功）。[Release bluestar-c110-live-preview-lab-20261118-v011](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-live-preview-lab-20261118-v011)，APK `BlueStar_Guide_C110_LivePreview_0.1.1.apk`，11,834,143字节，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-live-preview-lab-20261118-v011/BlueStar_Guide_C110_LivePreview_0.1.1.apk 。
- **唯一下一步**：请用户连接C110 Wi-Fi、打开移动数据、安装独立包并配置Key，点击「C110预览」，反馈`live_cmd/live_first_ms/live_width_px/live_height_px` 或 `live_rtsp_error/live_http8192`。若真机出实时画面再点击画面验证拍照及恢复预览；此前RTSP网址只具DEX证据，**不能把构建成功当真机预览成功**。不再重复已成功的六轮纯拍照测试，正式蓝星RC25/M02S保持不变。

---

# 2026-11-18｜蓝星向导 RC25 × C110 取图云端OCR独立集成实验 v0.1.0（已构建，待真机）

- **本轮用户正式授权**：「可以，你可以试着接入一下看看」，将 2026-11-18 真机已验证的 C110 六轮遥控拍照→新增JPEG→原图下载链路试着接入蓝星向导原云端视觉/OCR/TTS。用户并未要求改 RC25 正式包；因此从**真实 RC25 源码分支** `feat/bluestar-125-reading-entry-pages-actions-20261108` HEAD `fe848acf87cec87d8d7cba0c739b7be40e31072b` 创建原仓库隔离测试分支 `exp/bluestar-c110-integrated-20261118`；未把旧 `main` 离线OCR代码误当云端版。
- **代码/功能**：新测试分支 HEAD `a9419d5af14cdf0713df388067d936d33978f094`。新增 `C110PhotoPolicy.java`、`C110WifiCamera.java`、独立Java协议单元测试和 `cloud-vision-app/C110_INTEGRATION_LAB.md`。复用实机验证的 `192.168.1.254`/3012/3002/3001-par0/1001/DCIM原JPEG流程：用户顶部点击**C110拍照**后，单次取最新真实新图（不取旧图），首次进入照片模式，**同一 Network 后续免切模式直接1001**；仅由手动点击触发，不自动重拍，不删相机文件，不保存手机相册、不切回放/OTA。遥控失败时清除模式已就绪标记，长按按钮可手动重初始化。接原 `CloudVisionClient`/ `TargetVisionClient` 云端模型及 C 模式流式中文TTS，**直接用完整JPEG字节**而不重新NV21编码/缩图；用户可选择「朗读文字」任务。原手机/USB相机、阅读书页、语音追问等现有代码保留，**C110尚未接入实时视频预览或书页存档按钮**。
- **网络路径安全**：相机仅使用已知Wi-Fi `Network.openConnection` 访问局域网目标IP，Android `networkSecurityConfig` 仅允许 `192.168.1.254` 明文HTTP、默认禁止其他明文；图像云端请求在 `CloudVisionClient` 和寻找目标分支 `TargetVisionClient` 中只走**显式选中的蜂窝 `Network.openConnection` HTTPS**（不调用全局 `bindProcessToNetwork`），无蜂窝网络则不发快门并提示。只读/快门白名单和本次新图校验已加；API Key仍存Android Keystore；不提交照片、密钥、语音数据到GitHub。此双网络分流功能**尚未在用户vivo/Android16真机验证**，语音ASR网络路由亦未全部专项验证。
- **构建/发布实证**：GitHub Actions [44286786286](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44286786286) `completed/success`：协议纯Java测试、静态白名单/隔离审计、沿用RC25 pinned AUSBC USB patch、Gradle Android ARM64 debug build、APK ZIP/USB native lib/音效/包名/version校验全通过。独立包名 **`com.vobich.bluestar.c110test`**，版本 `1.0.25-c110-lab`，不会覆盖 RC25 `com.vobich.bluestar.rc25`，后者本机的 API Key/书籍不会自动迁移。预发布 [bluestar-c110-integrated-lab-20261118](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-integrated-lab-20261118)；APK `BlueStar_Guide_C110_Integrated_0.1.0.apk` **8634676字节**；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-integrated-lab-20261118/BlueStar_Guide_C110_Integrated_0.1.0.apk 。
- **下一步现场验收**：手机开启移动数据并在Wi-Fi设定里连接C110，**测试版需单独填写百炼API Key**；在蓝星选「朗读文字」，点击顶部「C110拍照」，提供匿名运行诊断，判断手机是否能同时保持C110 Wi-Fi与蜂窝公网、HTTPS云端识别、真实本地TTS出声，并核查首次/第二次耗时、相机重启状态。若需要补救，根据日志逐项修复，不盲目修改 USB、正式版或C110固件。**现阶段不可宣称用户实机已跑通云端识别或实时视频预览。**

---

# 2026-11-18｜C110 v0.1.3 六轮连续拍照与完整JPEG取回成功（含2轮不切模式）

- **现场日志（18:18–18:22，用户真实复测）**：原C110 Wi-Fi设备 `192.168.1.254`；从7张旧JPEG开始，六轮逐次增加为8、9、10、11、12、13张，每轮都是本次新增加一张、完整校验 JPEG、系统相册保存成功。所有成功照片都是 **5888×3312**。
- **六轮具体成绩**（步骤含模式切换时计入总耗时）：① 切模式 `3001/par=0` 442ms、快门 `1001` 499ms、新JPEG 1482700字节、JPEG下载+校验274ms、**总1417ms**；② 切模式18ms、快门551ms、1629129字节、下载+校验312ms、**总1043ms**；③ 切模式15ms、快门531ms、1626420字节、下载+校验269ms、**总956ms**；④ 切模式17ms、快门466ms、1624885字节、下载+校验311ms、**总1012ms**；⑤ **不切模式直接拍照**、快门529ms、1627810字节、下载+校验293ms、**总950ms**；⑥ **不切模式直接拍照**、快门449ms、1627917字节、下载+校验270ms、**总928ms**。后5轮平均 **977.8ms**；纯不切模式两轮平均 **939ms**。本次1001均HTTP200/Status0。最后18:21:58再次下载 **已有** JPEG（1627917字节，用时699ms），是只读对照，**不是第7次新拍照**。
- **决定性结论**：已证实在显式进入照片模式之后，保持相机照片模式时**至少两轮连续不必发送3001**，可以仅发 `1001` 拍照、监测新照片并HTTP下载原图；旧的 `1001=-13` 与非照片模式有关的解释获得进一步支持。第2–4轮虽然再次发送3001，但模式切换只耗15–18ms；**不能据此推出每次都必须切换**。
- **连接边界**：六轮间用户未报告需要手动重连，后续多轮 HTTP 请求都成功，各轮结束时 NetworkCapabilities 非 null，说明这一段连续工作稳定。此证据不等于已证明全天候不断Wi-Fi，也未确认重新上电后的默认模式、直播预览码流能否并存，或Wi-Fi取图与蜂窝云端OCR/TTS能否并行。
- **接下来的研发优先项**：无需重复六轮相同基础测试。单次进入照片模式（或在启动时通过可验证的协议状态判断），保持同一连接循环1001→查询DCIM新图→完整下载→视觉OCR/TTS，省掉每轮冗余3001；同时**先验证相机重启后的照片模式初始化与不断网**。最终目标仍包括独立取相机实时视频流（非原厂App画面截屏）和手机Wi-Fi访问相机/蜂窝上云分流；这两项**尚未实机通过**。截至此记录，仅更新GitHub交接文字，**不修改正式蓝星向导 RC25、不新建仓库、不构建新APK、不覆盖旧版本**。

---

# 2026-11-18｜C110 v0.1.3 真机首次完整成功：遥控拍照→新增JPEG→274ms取图→保存

- **现场证据（18:18:58～18:19:07，用户提供的完整匿名日志）**：v0.1.3 连接C110相机Wi-Fi，网关 `192.168.1.254`，3012 HTTP200/Status0；3024 HTTP200/Status0（未解析出Value）；3002 HTTP200，响应包含可用命令1001和3001；DCIM目录列出7张拍照前旧JPEG。
- **闭环真正成功**：用户明确点击②后 `3001&par=0` 切照片模式 HTTP200/Status0 耗时442ms；`1003`可拍张数返回Status0/Value6408；再发**仅一次** `1001` 快门 HTTP200/Status0 耗时499ms。发现**新增1张** JPEG，首次可见距快门发送565ms；下载并完整校验新增JPEG **1482700字节、5888×3312、274ms**，保存到系统相册 `Pictures/BlueStar_C110_Test`。从快门发送到保存 **959ms**；包含切换照片模式的流程总耗时 **1417ms**。结束时Android Wi-Fi NetworkCapabilities仍非null（**只能证明操作结束网络仍在，不代表三轮连接稳定或已实测真实网络可达**）。
- **原因证据**：此前直接1001返回 `Status=-13`，本次先 `3001/par=0` 再1001返回Status0且产生本次完整新图，强烈支持上次失败由模式前置条件不满足导致（仍可有原厂初始化因素）；3037在当前固件返回 `-256`，已不使用。**不要再当作取图不可能，也不能据一次成功宣称长期稳定、直播码流接管或云端OCR已完成。**
- **下一步无需改代码/重新构建**：用户保持当前相机不关机、同一Wi-Fi不断开，直接使用v0.1.3的③「不切模式直接拍照」连续试两次，每次确认本次新增JPEG成功及真实耗时；如都成功可验证照片模式保持期间1001无需每张先切3001。之后可做相机重启后模式是否恢复及三轮持续性、独立视频预览和Wi-Fi/蜂窝分流。**现有版本仅实验用途，正式蓝星向导 RC25、M02S及相机固件未修改。**

---

# 2026-11-18｜C110 v0.1.2 真机查清 -256 / -13，v0.1.3 照片模式切换实验已发布

- **v0.1.2 用户真实日志（18:10–18:11）**：`192.168.1.254` 的 Wi-Fi、3012 HTTP200/262字节、3037 HTTP200/100字节、3015 HTTP200无JPEG候选，但 DCIM 两次均列出7张旧图。拍照前旧图为7张；3037返回 `Status=-256`，单次 `1001` 两次均返回 HTTP200/`Status=-13`（15ms、14ms）；首次等待20秒没有新JPEG。**发送1001已确认，但拍照尚未成功，旧图读取仍正常；不可把HTTP200当快门成功。**
- **进一步证据（注意是联咏NT96660参考资料，并非本C110固件源码）**：`WIFIAPP_RET_CMD_NOTFOUND=-256` 是“命令不存在”；`WIFIAPP_RET_FAIL=-13` 是“执行失败”。文档明确1001仅允许在照片模式，3001/par=0是照片模式、1录像、2回放；见 https://www.mputek.store/filedown/Survey3-WIFI-Manual.pdf 第5.1.1、5.3.1、8节。v0.1.2误将3037的非零状态仅作模糊“当前模式状态”，本轮已明确更正：不再依赖不支持的3037；-13是否由当前非照片模式引起仍未实机确认。
- **v0.1.3已提交**：原隔离分支 `exp/c110-readonly-jpeg-lab-20261118` HEAD `3cb029beeede614467b03ded25b37f35b51ec375`。①只读3012/3024 SD状态/3002支持命令及旧图基线；②用户必须点击确认才执行一次 `3001&par=0` 切照片模式（可能中断预览），确认返回成功后可选查1003可拍张数，再仅发一次1001快门，原有新增JPEG完整验证、保存相册、耗时诊断保留；③不切模式直接拍照仅用作对照。1001立即返回非零错误时停止，不再白等20秒；不会自动重拍/切回、删除、格式化、OTA或上传。切模式是用户显式确认的实验操作，并非最终产品每次拍照必须切模式的结论。
- **构建/发布**：[Actions 44283665330](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44283665330) `completed/success`（单测+assembleDebug）；独立新预发布 [bluestar-c110-shutter-jpeg-lab-20261118-v013](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-shutter-jpeg-lab-20261118-v013)，APK `BlueStar_C110_Shutter_Lab_0.1.3.apk` 26081字节，直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-shutter-jpeg-lab-20261118-v013/BlueStar_C110_Shutter_Lab_0.1.3.apk。之前 v0.1.0～0.1.2 Release保留，正式蓝星向导/M02S不改。
- **下一步现场唯一动作**：用户在连接C110 Wi-Fi状态下安装0.1.3，点击①，再明确确认②，记录3001/par0的HTTP/Status和1001的HTTP/Status以及新图是否完成下载。若3001失败或1001仍-13，先检查原厂 App 初始化/相机当前模式/卡状态，不盲改固件或发其他写指令。

---

# 2026-11-18｜C110 v0.1.1 真机被3037模式查询误拦截；v0.1.2已修正构建，待1001真机结果

- **v0.1.1实际日志（18:03）**：①已正常检测Wi-Fi网关 `192.168.1.254`，3012 HTTP200/262字节、3037 HTTP200/100字节、媒体索引3015返回0候选但目录GET找到7条JPEG。用户连续两次点击②均被 `3037当前模式查询未通过；没有发送拍照` 拦截。**1001未发，不能把这两次判为相机拒绝遥控拍照。**
- **原因**：旧代码 `if(mode.status!=200||mode.body.length==0||(modeStatus!=null&&modeStatus!=0)) throw` 将 3037 XML Status非零一概当作失败；但这是当前模式查询，真实C110固件的状态码语义未核实。公开Novatek命令表也称3037为 `get current mode status`，不能在没有数值和固件对照的条件下假定所有非零都禁止拍照。
- **v0.1.2修正**：在原隔离分支 `exp/c110-readonly-jpeg-lab-20261118` 提交 `9c2c86967374e551870c8d49c90046274e2525b5`。保留设备3012探测和已真机确认IP检查；3037变成只读诊断并打印HTTP、字节数、数值Status/Value（不记录照片名/SSID或原始响应），不因非零而阻止用户明确确认的单次 `1001` 快门。1001非零Status只记录并观察新增文件，不会自动再次拍摄。随后20秒按旧图基线检测真正新JPEG、校验并保存；旧的3001切模式、2015预览、删除/格式化/OTA仍不使用。v0.1.0原有旧图取图验证及正式蓝星向导/M02S不改。
- **CI/发布**：[Actions 44282141386](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44282141386) `completed/success`，单测+assembleDebug已成功。独立 Release：[bluestar-c110-shutter-jpeg-lab-20261118-v012](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-shutter-jpeg-lab-20261118-v012)；APK `BlueStar_C110_Shutter_Lab_0.1.2.apk` 23973字节，下载地址 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-shutter-jpeg-lab-20261118-v012/BlueStar_C110_Shutter_Lab_0.1.2.apk 。
- **下一步**：用户连接C110 Wi-Fi，先①后②单次快门；反馈完整诊断中 3037 数值、1001 HTTP/Status、是否新增及完整JPEG、快门到完整保存时间。尚未证实遥控拍照成功、免切模式成功或同会话三轮稳定性。

---

# 2026-11-18｜C110 单次遥控拍照 + 自动取新JPEG实验 v0.1.1（已构建，待真机快门验证）

- **用户实际完成的 v0.1.0 真机结果**：设备查询3012与模式查询3037均在相机 IP `192.168.1.254` 返回HTTP200；媒体索引3015 HTTP200但0候选；读取 DCIM 目录共找到7张 JPEG。通过独立 App 成功下载原图 **1523091 字节、5888×3312、下载+校验304ms**，保存至 `Pictures/BlueStar_C110_Test`。首次 `192.168.10.1` 返回0候选，不应认作目标相机。
- **新需求**：用户确认 C110 没有实体拍照按钮，只能由手机软件控制。用户授权在原隔离实验工程中增加一次遥控拍照指令与本次新图自动读取。无需重做 APK 逆向。
- **代码**：沿用原隔离分支 `exp/c110-readonly-jpeg-lab-20261118`，新 HEAD `05346ec6be156d0ace5bac400052452dea91bf0a`。未触碰正式蓝星向导、M02S、Windows、原厂固件以及旧 Release。新版本仅在用户点击②并确认后，在已实测 IP `192.168.1.254` 发单次 LY/Novatek `/?custom=1&cmd=1001`，此前先核对3012/3037 HTTP状态并重新建立拍前照片基线；随后20秒查找真实新增JPEG，验证原图字节、JPEG头尾、响应长度及实际尺寸再存相册。失败不自动重拍、不切模式、不上传、不删照片。返回HTTP200不等于已拍照成功。
- **构建验证**：GitHub Actions [44280361469](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44280361469) **completed/success**，Java 单元测试和 Android assembleDebug 均通过；APK 已成功发布到独立预发布 [bluestar-c110-shutter-jpeg-lab-20261118](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-shutter-jpeg-lab-20261118)。文件 `BlueStar_C110_Shutter_Lab_0.1.1.apk`，23869字节，安装直链： https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-shutter-jpeg-lab-20261118/BlueStar_C110_Shutter_Lab_0.1.1.apk
- **下一步唯一真机验证**：连接 C110 Wi-Fi、打开 v0.1.1、点击①、确保相机进入原厂已知可拍照模式后点击②，反馈**完整匿名诊断日志**。核验1001响应状态、是否新增本次JPEG、从拍照到新图可见和完整保存时间、Wi-Fi是否持续存在；此刻不可宣称拍照链已成功或三轮稳定。若1001失败，先分析实际状态与原厂模式，不盲发3001切模式。

---

# 2026-11-18｜C110 独立只读取图实验 v0.1.0（已构建，待真机）

- **已发布，不等于实机成功**：沿用 11-15～11-17 SJCAM Zone/X 的 C110 DEX 研究与免切模式专项结论，在原仓库隔离分支 `exp/c110-readonly-jpeg-lab-20261118` 新增 `experiments/c110-camera-protocol/c110-readonly-lab/` 独立 Android APK。未改正式蓝星向导 RC25、M02S 代码或相机固件；未把原厂 APK 放入仓库。
- **源码 HEAD**：`e6bcc92dc16cb5356639102a7e4d2d30cccae90a`；GitHub Actions [44274477132](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/44274477132) **completed/success**（Gradle Java 单元测试 + assembleDebug 完成）；前几轮构建仅因 CI Android SDK 环境配置失败，现已修正。
- **独立预发布**：[bluestar-c110-readonly-jpeg-lab-20261118](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-c110-readonly-jpeg-lab-20261118)；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-readonly-jpeg-lab-20261118/BlueStar_C110_ReadOnly_Lab_0.1.0.apk（大小 21857 bytes）。独立包名 `com.vobichngan.bluestar.c110lab`，不会覆盖 RC25。
- **功能**：手机连接 C110 Wi-Fi 后取网关/手工私有 IP，采用 Android 指定 Wi-Fi Network 发只读 `3012`/`3037`/`3015` 和 `/DCIM/PHOTO/` 等目录请求；先建立旧图基线，再比较真实新增 JPEG；读取同源 URL，校验 HTTP 长度、JPEG 头尾、图片解码尺寸，验证通过才保存到手机 `Pictures/BlueStar_C110_Test`；界面有 20 秒观察、已有图测试、日志复制与对照检测。
- **安全边界**：不发 `1001` 遥控拍照、不发 `3001` 切模式、不发 `2015` 预览启动、不发删除/格式化/OTA，不上传云端，无 OCR/TTS。手动使用相机实体快门后检查，查询接口只是针对 LY/Novatek 候选后端，固件若不同应停测而非盲发。不能把“找到了旧图”当“新拍自动取图成功”。本版没有在用户 vivo X200 Pro / 实体 C110 上完成通信验证。
- **下一步只需用户真机日志**：连接实际相机 Wi-Fi，先按①查看基线，再按③并使用相机实体快门，记录是不是新增真正可解码的 JPEG；如 A 组在拍照模式失败，再用原厂 App 自己切到媒体模式，回实验版按④做只读对照，注意 App 页面切换可能影响 Wi-Fi。另需独立验证 C110 预览码流与蜂窝云端分流，仍是最终蓝星视觉方案的主线。未经真机证据不承诺免切模式一定可用，更不承诺 5 秒朗读。

---

# 2026-11-17 澄清｜相机指示灯只是闲聊，不是项目需求

- 用户明确澄清：前面的“所有灯都不亮”只是闲聊，黑色胶带即可解决；**不列为蓝星向导功能需求、研发任务、验收条件或选型门槛**，后续不要继续围绕 LED 研究。
- 当前唯一核心优先事项仍是 C110 通过 Wi-Fi 保持长连接、蓝星向导直接接管视频流、远程拍照并高速获取高清原图，再由云端识别及 TTS 朗读。
- 下方既有 LED 研究和曾误标“硬性要求”的条目均属历史记录，已被本澄清取代，不再执行。

---

# 2026-11-17 用户硬性体验要求：夜间使用时不希望任何状态灯明显亮起

- 用户明确表示：C110 胸前随身使用期间，无论 Wi-Fi 连接、实时预览、拍照，前置灯光都会吸引他人注意，特别是晚上；希望**零明显可见灯光**。这是一项硬件选型/使用体验约束，不能再只当成可选美化项。
- 已知事实：用户已在原厂 App 寻找关灯设置而未找到；此前 APK 分析只在其他协议后端找到 LED 控制相关符号，**没有可验证适配该 C110 固件的官方关闭方法**。此项不保证可实现。
- 后续原则：先咨询厂商是否存在**正式支持且符合法规/隐私要求**的低亮度或关闭状态灯模式；不要为了隐藏拍摄绕过拍摄提示、刷不明固件。若必须完全无灯且厂家不支持，应明确告知 **C110 未满足用户硬条件**，避免将其标记为最终适配成功；同时不影响继续核查 Wi-Fi 连续控制及高清 OCR 的可行性。

---

# 补充｜C110前置指示灯：官方说明与代码核查（2026-11-17）

- C110 前方为状态指示灯，官方说明书确认绿灯 Wi-Fi 连接后常亮、蓝灯待机/拍摄状态变化，**未列官方关闭选项**。直接接管 Wi-Fi 实时视频流也不会自动改变相机固件对灯的控制。
- 用户原版 SJCAM Zone APK 的 AllWinner SDK 中有 `AW_cdr_net_config.led_on_off`、`LED_ON_OFF` 字段；SJCAM X 的 SigmaStar 后端也有 `Stealth` 词条。**二者均不能证明 C110 标准版的实际固件提供关灯接口**，未授权前绝不跨后端盲发写命令。
- 详见 `experiments/c110-camera-protocol/C110_DEX_RESEARCH_AND_20261118_HANDOFF.md` 最新补充；次日只先读设备固件、核对原厂设置是否支持 LED 开关，不作固件改动。

---

# 2026-11-17 用户再次明确 C110 预览最终目标：蓝星直接接管 Wi-Fi 原始视频码流

- 不采用“原厂App预览→豆包屏幕共享→识别”。蓝星**自己连接C110的RTSP/HTTP实时视频流并解码、提帧、云端识别与朗读**；尽量避免中间屏幕采集、二次编码、共享压缩。
- **注意画质边界**：直接取视频流不会把源流720p变成2K。预览实际分辨率未测；拍照的16MP JPEG与录像2K设置不等同于Wi-Fi预览清晰度。需实机核查是否存在可选更清晰预览流；小字仍走在同一持续Wi-Fi连接下拍照、下载原图的第二通道。
- 明天优先测蓝星**独立取流**，不是重新证明原厂App+豆包成功；网络分流要求相机Wi-Fi和蜂窝云端并行。专项报告位于 `experiments/c110-camera-protocol/C110_DEX_RESEARCH_AND_20261118_HANDOFF.md`。

---

# 最新｜C110标准版：2026-11-17 专项深挖，2026-11-18 到机直接复用（只读研究）

- **新增关键技术报告**：[`experiments/c110-camera-protocol/C110_DEX_RESEARCH_AND_20261118_HANDOFF.md`](./experiments/c110-camera-protocol/C110_DEX_RESEARCH_AND_20261118_HANDOFF.md)；本轮从 **SJCAM Zone 6.7.3.16 classes5.dex** 独立核实，而非重做 M02S。原始 APK 只读，**没有 App 代码修改、真机测试或安装包构建**。
- **关键调用链**：`LYCamera.changeCameraModel` → `3001` → `LYCamera$12.onResponse` → `startLiveVideoStream` → **`2015/1`**；视频地址按固件通过 `ISCamera.getVideoPlayUrl` 取得，LY 分支有 RTSP `192.168.1.254:554/xxx.mp4` 与 HTTP `192.168.1.254:8192` 两类候选。未确定 C110 实际协议后禁止盲发。
- **高清链**：`NewCameraControlActivity.takeClick` 检查 Socket/SD 状态 → `ISCamera.take`；LY 拍照候选 `1001`；`LYCameraFileList$1.run` 读取 DCIM/PHOTO 等目录，`getFileListNew` 解析 `a[href]` 原图路径，`SDownloadTask.run` HTTP 下载。下载 HTTP 结束≠断 Wi-Fi，另有连接页 `onDestroy → WifiHelper.forceDisconnect` 的独立断线路径。快门后视频流是否刷新存在固件分支。
- **官方规格澄清**：标准 C110 说明书列 16M 照片模式 **4608×3456**；所谓“预览约720p”**没有实测确认**（720p 录像选项不能证明预览规格）。SJCAM 官网将标准版优先归 Zone，X 主要是升级版 C110 Plus 的适配应用。
- **明天最短验证**：先机型/固件/网关/后端 → 原厂与独立预览实际帧宽高 → 相机 Wi-Fi 与蜂窝云端并行 → 插卡拍新 JPEG、下载、校验真实宽高 → 免切与切回放对照 → **同一 Wi-Fi 会话三轮拍照/下载/恢复**；不重复旧 M02S 失败实验，不将静态代码当作真机成功。

---

# 最新｜C110标准版无卡实时预览接管：SJCAM X原始DEX新证据（2026-11-16）

- **用户新提供的亲测事实**：早前使用 C110（按用户所述为标准版）时**没有插 microSD 卡**，官方 App 仍能以很小的主观延迟实时显示相机移动画面；豆包在手机屏幕共享官方预览画面时，能够识别路标、店铺招牌并语音说明。此为用户实际体验，不是本轮真机复测或量化帧率/分辨率/延迟的结果。
- **路线优先级发生改变**：首选 **蓝星向导自己直连 C110 Wi-Fi 实时视频流 → 手机解码取清晰视频帧 → OCR/云端视觉 → 本地语音播报**；目标是不依赖原厂 App、不插卡、不拍照、不切换回放模式、不下载高分辨率 JPEG。原先的 JPEG 免切模式研究全部保留，用于小字/高清备用，不要删掉。
- **本轮在用户上传原版 `SJCAM X.apk`（`org.jght.sjcam.x` v1.1.15、5DEX）的 classes5.dex 中重新实际定位：**`LyCamera.initIP()` 的方法体含连续构造字符串 **`192.168.1.254`、`rtsp://`、`:554/xxx.mp4`**，表明 LY/Novatek 分支生成类似 `rtsp://192.168.1.254:554/xxx.mp4` 的视频流地址；同时出现 `:8192` 和 `LY_URL_PHOTO`，可能是另一照片预览路径，未实机确认。查到 `LyCamera.startLiveVideoStream()`、`CameraControlActivity.startLivePlay()`/`startPlay()`、`GstLivePreviewView`、`LiveCustomVideoPlayer`，原APK包含 IJK FFmpeg 视频解码与 RTSP/重连逻辑。另存在 `rtsp://192.168.8.1/liveRTSP/av4`，但可能属于 SigmaStar/其他后端，**不可不辨型号地硬套**。
- **外部交叉资料**：联咏预览协议说明 https://www.6zhentan.com/opendoc/pages/base/nvt.html 描述RTSP视频流、`2019`读取 `MovieLiveViewLink`，部分固件固定 `rtsp://192.168.1.254/xxx.mov`；Novatek NT96660指南 https://www.mputek.store/filedown/Survey3-WIFI-Manual.pdf 说明 `2015` 参数1/0控制live view启停。公开旧SJCAM协议 https://github.com/Zsub/SJCam-API 报告RTSP端口554。**以上是相似联咏平台佐证，不是当前C110实测**，不同厂家视频路径可不同；APK实际路径片段 `:554/xxx.mp4` 与开源 `/xxx.mov` 不可混淆。
- **可行性判断**：直接RTSP取帧有实际程序与用户无卡预览经验支持，技术前景比依赖TF卡拍照下载更优；但**能否独立打开/保持流**可能需要会话握手、只读探测/启动视频流（如2015）及Android 16网络绑定。未有直接打开完整RTSP的真机证据，**不要标记已完成/宣称具体耗时或成功率**。
- **等用户拿到 C110 后，独立首个 Android APK 实验（旧主工程暂不动）**：在 vivo X200 Pro 上检查相机实际网关、设备回报型号及固件；无存储卡也先通过原厂 App 证实预览，接着退出原厂 App，用独立小工具绑定相机 Wi-Fi，探测候选RTSP预览流（优先通过设备实际响应得到URL），必要时按已核实固件发送仅启动预览的指令；手机内实时显示、解码缓存最近清晰帧、点击“识别当前画面”保存帧于内存并显示真实分辨率/帧延迟/连接稳定度；初版不发拍照/删除/格式化/OTA、暂不接云端。成功后再增加云端OCR与TTS，并验证相机Wi-Fi与蜂窝数据的分流。失败则复核握手/是否单客户端独占流，再考虑安卓屏幕共享兜底；不把兜底误写成“蓝星独立接管”。
- **仓库纪律**：本条只追加交接，旧2026-11-15 C110模式切换专项结论、M02S、Windows、正式蓝星及全部Release保留；不修改代码，不构建APK，不复制私有原厂APK进GitHub。

---

# 最新｜SJCAM C110 标准版 × SJCAM X：免切模式取高清JPEG交接（2026-11-15，待2026-11-17真机）

> **当前最高优先级：C110 标准版 Wi-Fi 自动获取刚拍的完整 JPEG，唯一关键判断是「下载前是否必须由拍照模式切到媒体/回放模式」。** 用户预计 2026-11-17 收到相机；当日用户回来后以独立 Android 实验 APK 在 vivo X200 Pro / Android 16 实测。**当前没有 C110 实机测试、没有测试 APK、没有发布 APK；不应虚报通过。**

## 对象、来源和旧项目保护

- **本轮唯一主目标**：SJCAM **C110 标准版**（不是 C110+）、用户所示 **SJCAM X** App；之前研究的 **C110+ / SJCAM Zone** 作为旧参考保留，不能混淆。App 兼容多种机型，零售名称及商家视频不能代替手机连接后真实型号/固件回包。
- **分析原始附件**：用户上传 `SJCAM X.apk`，`org.jght.sjcam.x` v**1.1.15**（versionCode **96**），38,261,685 字节，5 个 DEX；SHA256 `a8ebceff0faa8d5b96b4e5f34bf981c932d39bf5070038555f9169edc3cc9b3b`。关键逻辑从可读 DEX 方法体静态检查，不需要先使用 BlackDex/360 脱壳；**未宣称还原完整源码**，不把私人 APK 提交至 GitHub。
- 本轮已在 ChatGPT 完成的独立研究报告：`SJCAM_X_C110plus_通讯与取图专项核查_2026-11-15.md`（早期错误地突出+）；`C110标准版_SJCAM_X_逐层逆向核查_2026-11-15.md`（当前标准版专属研究）；`C110_取图是否必须切换模式_专项结论_2026-11-15.md`（最重要）；同轮离线只读探测样例 `C110_免切模式_只读探测.py`。它们是参考材料，不等于在相机上运行成功。之前 Zone 6.7.3.16 的报告与 APK 保留，不重复从头拆解。
- **GitHub 治理**：原仓库 `vobichngan285-collab/kanzi-assistant`，先读 main/`PROJECT_HANDOFF.md`，核验 HEAD / Actions / Release，保持全部旧记录和 Release 不变；**未修改正式蓝星向导、M02S 眼镜实验、Windows OCR 和相机固件**。

## 已由 SJCAM X 字节码确认的技术事实（静态，不代表真机）

1. `DeviceAdapterFactory.create(String)`: 上报 **`565-C110` → `C110565Adapter`**，`580-C110+` 与 `580-C110+_AIC` → `C110pAdapter`；两个适配器均继承 `NovatekDeviceAdapter`。本轮只沿 `565-C110` 标准版分支研究，实际设备上报型号要现场核实。
2. LY/Novatek 探测调用链 `CameraFactory` → `LYCameraProtocol.sendCmd2Probe(3012)`；候选相机网关 `192.168.1.254`（还存在 `192.168.8.1`），不可预设目标实机一定是该 IP。Android 绑定相机 Wi-Fi 网络影响路由。连接代码还有其他后端，不能盲发跨型号写指令。
3. `LyCamera.shutter()` 的拍照路径用 **HTTP `1001`**；LY SDK 的 HTTP 基址形如 `http://192.168.1.254/?custom=1`；拍照返回成功 **≠** JPEG 已落盘/完成下载。
4. `LyCamera.connection()` 创建额外 **TCP 3333** 事件通道；`LyCamera.onReadData()` 将推送解析成 `cmd/status`，有 **50ms** 去重与 **`6001`** 事件处理；**不能把6001认作已证实的“JPEG写完”事件**，也不清楚 TCP 3333 是否为只读HTTP必要条件。
5. **原厂媒体库确定主动切换模式**：`MediaFragmentViewModel.loadMediaFromCamera` 调用 `ICamera.isInMediaLib()`；LY 实现 `LyCamera.isInMediaLib()` 发送 **`3001, par=2`** 回放模式；`LyCamera.exitMediaLib()` 通过 `3001` 恢复以前模式。先前观察到切换状态短轮询约100ms、上限15s是等待机制，**不等于每次固定耗时15秒**。
6. **底层读取函数不主动切模式**：`LyCamera.getMediaList()` → `LYCameraProtocol.sendMediaListCmd(3015)`；`LYCameraProtocol.getMediaDirectory(type)` → HTTP `GET DCIM/{type}/`，HTML `a[href]` 可生成 JPG 文件路径；`DownloadTask.openConnectionForUrl(URL)` → `WifiManagerHelper.getCameraNetwork()` → Android `Network.openConnection` → `HttpURLConnection` 下载原文件。上述函数**内部没有3001**，但 **C110 固件是否在拍照模式接受这些请求未知**，不可把“客户端未切”当成“固件不要求切”。
7. `LYCamResponse.getFpath()` 可解析 `<FPATH>`；开源项目 `rgov/novatek-api` 的其他型号可从 `1001` 拍照响应得到路径、查询 `3015` 并 HTTP GET JPEG，且部分实例查询列表前未切回放：**仅为交叉参考，并非 C110 真机证据**。SJCAM X 自身快门成功回调尚未发现直接利用该路径下载。
8. Wi-Fi 网络可用于文件HTTP下载，**并不等于 FTP/Samba/远程TF卡直接挂载**，也未证明手机可用OTG模式边拍边直接读取TF卡；不要沿这个错误假设开发。

## 唯一关键的未决问题及判断标准

**目标**：相机维持拍照模式、Wi-Fi 保持连接，能否在不发 `3001` 的前提下获取 **此次新拍、已完整写入** 的 JPEG？

- 若 **A组在拍照模式**中可直接取得本次新 JPEG 的完整实际字节（有有效 JPEG 内容、大小/解码校验，排除旧图），并连续复现，则免切模式取图成立。
- 若 A 失败而 **B组切至原厂回放模式**后对**同一新图**的读取得以成功，说明该固件/当前接口存在模式依赖；但还需要查看错误码，避免把路径错误、缓存或时序误判为固件限制。
- 若 A、B 都失败，则不能得出必须切模式的结论，应先排除协议分流错误、Android Wi-Fi 路由、相机未写完、文件路径错误。
- 需记录每一步单调时钟耗时：连接/探测、快门回执、照片写盘/列表可见、文件首字节、文件完整下载、校验、Wi-Fi 是否断线；**不要只记整个流程一个秒数**。之前建议的免切约2~5秒、切换约4~8秒仅是工程预算，**无真机实测，不保证3秒取图或5秒开始朗读**。

## 2026-11-17 收到设备后：优先交付「独立、可直接安装运行的 C110 免切模式实验 APK」

- 用户已问是否继续按既往方式给其安装包。**计划是届时在原仓库单独的实验目录/分支开发轻量 APK，通过 GitHub Actions 严格检查与构建、创建独立 Release，给 Android 安装直链**；不是现在已经完成，也不能异步自动发送，需用户在收到相机后回到会话启动本轮实验。不得改动正式蓝星向导和已发布 APK，除非另获授权。
- 界面要简单、字体清楚，优先三个操作：**① 连接/型号/版本/网关只读核验；② 「拍照模式不切换取图」只读 A 组；③ 「原厂回放模式取图对照」B 组**（只有 A 失败或用户明确触发才进行）；带「复制诊断」按钮。尽量让用户无需电脑/Python/ADB，真机 App 可显示请求结果和毫秒耗时。
- **首次仅做只读 A 组**：插入空测试 TF 卡；先让原厂 SJCAM X 成功连接、确认相机在拍照模式，用原厂 App 拍一张测试 JPG；再验证独立诊断工具能绑定相机 Wi-Fi，查询设备型号/版本，尝试 `3015`、`GET /DCIM/PHOTO/` 和已确认的精确 JPEG URL；**这组不发3001、不远程拍照、不删除、不格式化、不OTA、不上传照片**。注意两个 App 轮流用同一相机 Wi-Fi 的连接与状态，避免误把切 App 造成的断链算成模式要求。
- 若 A 组没有读到新 JPEG：在用户可见操作中通过原厂 App 合法进入相册/回放做 B 组，**再次用只读请求读取同一文件**；再回到拍照模式做 C 组重复核验，至少3次测相同条件；先得到模式结论再考虑自动快门与无缝轮询。
- 若 A 成功：下一独立版本才考虑主动发送 **一次已验证的1001快门**，同时保存拍前列表、验证拍后新增、HTTP下载真实完整 JPG，排除旧文件/半写图片；最后经用户确认接入蓝星向导云端 OCR/TTS、蓝牙触发。默认不存系统相册、不上传云端、不更改原机文件。
- **验收最低要求**：设备确认为预期 C110 / SJCAM X、手机绑定正确 Wi-Fi；同一模式至少3轮新图可读与校验；记录 Wi-Fi 保活、失败原因、是否发生切换、每段真实毫秒值。只要没有拿到真实原图就如实报失败；不可用预览截图或返回200替代真实 JPEG。

## 交接后下一句话

用户预计 **2026-11-17（后天）** 收到 C110 标准版。收到后先确认真机与 SJCAM X 联网正常、TF卡可拍，再依以上流程开发/发布**独立测试安装包**并直接提供 GitHub Release APK 下载链接；**所有后续改动必须限于实验分支**。本条仅记录研究成果和测试计划，不构建、不刷机、不修改其他项目。

---

# 最新｜SJCAM Zone C110/C110+ 通信链深度静态复核（2026-11-15，无相机在线验证）

- **研究授权**：用户提供SJCAM Zone原厂安装包，并要求定位C110+ Wi-Fi连续拍照、原图下载实际协议；本轮只静态解析，不修改代码、不生成APK、不写相机固件、不联网发送相机指令。仍未购买/连通对应C110+，也没有该相机实机通信日志。
- **来源核实**：用户附件`bdcc3db17ea0e26aecaf22bc01fe99be.apk`，`org.jght.sjcam.zone`版本`6.7.3.16`，5个DEX，SHA-256`a07236df9b97b5a202179ab2bd98cead497bdcae5a6aa32e55113d49b37e1274`。完整逐层静态分析与下载报告见本轮ChatGPT生成的`SJCAM_C110_通信链复核报告_2026-11-15.md`；没有上传用户专有APK到GitHub。
- **新证据：按型号+固件分流，不可直接断言C110+唯一协议**：`ConnectCameraFragment.onConnCameraSuccess$lambda$17` 和 `ConnectCameraDialog.onConnCameraSuccess$lambda$9` 识别`C110_PRO`按`v1.1.2`、`C110`按`v1.0.4`固件字符串门槛分流到旧控制页或`NewCameraControlActivity`。`NewCameraControlActivity.takeClick`检查SDCard后调`ISCamera.take`，吻合用户未插卡不能拍照经历；它还检查`SocketHelper.isConnect`，可能重启TCP会话，不能认为所有固件都是裸HTTP。
- **实际源码通信后端**：`SCamera.getCamera(1)`构造`LYCamera`；其`take`中一条拍照路径使用HTTP GET`http://192.168.1.254/?custom=1`附加`cmd=1001`及`par=1/0`，`getSDCardInfo`查询`cmd=3017`；`LYCameraFileList.getFileListNew`通过Jsoup提取HTML`a[href]`及文件信息构成HTTP原图URL。`SCamera.getCamera(5)`构造`AllWinnerCameraHttp`；其`take`为`http://192.168.10.1:8082/api/setdeviceinfo/?custom=1...`的HTTP POST，命令`1100/1101`，媒体数据库与前者不同。还存在Amba/His/iCatch多栈。**这些均是APK内真实代码，尚非用户具体C110+实机协议**。
- **外部型号分歧**：SJCAM官网`https://www.sjcam.com/download`写明升级版C110 Plus需`SJCAM X`，其他多数含旧C110使用`SJCAM Zone`；老C110+产品页仍列Zone。购买前务必确认实际机型、设备固件、所用原厂App，不能把APK中`580-C110+`、`675-C110`字符串视为协议识别成功。
- **最短后续证据**：须实机在相同Wi-Fi中通过对应原厂App完成一次带microSD的拍照、列表和原图下载，取得脱敏cameraModel、firVersion、camType、网关/端口、照片前后列表及时间、HTTP/TCP请求事件；不解密上传隐私照片。先只读诊断和旧图GET，再单次新拍，最后蓝牙触发/OCR/TTS。**未拿到实时证据前不能承诺5秒，也不应造猜测式安装包**。
- **治理**：只追加研究交接记录；M02S v0.1.29~31、蓝星向导正式版、Windows OCR、原厂APK和全部旧Release均未动。

---

# 最新｜M02S TK8 固件并行拍照可行性深度调查（2026-11-15，**只研究未改软件或镜端**）

- **直接证据**：此前用户真实项目号 **TK8**（BLE`0x64`）、AA12/AA13/AA14、真实拍照/原厂AP导入均已确认；v0.1.31两轮AP在线`0x22/0x31` GATT成功但JPEG列表不变，验证固件`isImport=1`状态阻断普通已知遥控快门的强烈证据。无须重复0x22/0x30、0x22/0x31、卡顿RTSP或0x95支持位实验。
- **重新实际核查私有原厂APK** `com.starglasses.app`：读取用户Library中的同一284656626字节APK，匹配sha256`92f103b9d473d4cdf7424407ddfc851cac1700d15a06e55dfa0009ccf962ad57`；解析ZIP/7个DEX，`classes2.dex SendCommandViaBle.appGetDevicdInfo()`真实代码固定BLE command **`0x55`，参数0x00**，其`DeviceValueBean`确有`btVersion`、`ispVersion`、`deviceVersion`三项，E09逆向文档确认是**只读固件版本查询**。这是目前未在用户本机做过、最有增量价值的安全取证。只是发现了接口，**本轮没向眼镜发送**。
- **主控与同名型号区分**：部分AI眼镜/开发板JL7018F+全志V821L2；全志V821官方`sample_smartIPC_demo`同一IPC方案支持WiFi视频及JPEG抓拍，理论上不排除并行，但部分官方示例抓拍为低清次码流，不能推断用户M02S/TK8能持续Wi-Fi AP同时拍3200×2400。公开另有名为M02S/A02S但BLE用AE30/DE5B的不同固件家族，**不可把其`WIFIA02S_*.swu`刷入TK8**；用户具体主控尚无硬件实物证据。
- **适配厂家证据**：原厂Android软件`com.starglasses.app`对应Google Play开发者**东莞市成星电子科技有限公司**，支持邮件`a1191282346@gmail.com`。应向该支持方询问`TK8`确切BT/ISP版本的镜端固件/SDK及“`isImport=1`且WiFi/AP+原厂HTTP保持时原生快门JPEG”的并行能力和完整恢复路径，而不是依赖相似型号或无关镜片品牌。
- **下一步纪律**：先获得用户授权后在**新隔离只读诊断版**执行一次`GET_DEVICE_INFO 0x55`→回读其BT/ISP/device版本，不开AP、不拍照、不改固件；再比对支持渠道确切固件适配与恢复性。无厂商SDK/签名镜像/已验证恢复办法之前，绝不刷机、发送未知`0x50`模式字节或宣称可以突破限制。
- **仓库材料**：新分析报告 `main:experiments/m02s-wifi-only-lab/M02S_TK8_FIRMWARE_FEASIBILITY_AUDIT_20261115.md`（commit`12b8e9da736744269fc574201fb91f18424b53fb`）。v0.1.28~31实验分支、Release、正式蓝星向导、Windows OCR、私人APK全部保持不变。本次只有报告和本交接记录两项文字。

---

# 最新｜M02S v0.1.31 双轮 AP 在线高质量拍照真机验收：**命令写入成功但无新高清图**（2026-11-15）

- **真实真机结果／否定性证据**：用户提供 v0.1.31 完整日志。第1轮：17:59:24～26 Wi-Fi关闭状态实体快门0x45 1→0正常；18:00:06开启原厂AP；18:00:13 Android眼镜专属Wi-Fi及HTTP列表稳定，旧图 **29** 张、`isImport=1`；18:00:28人工按B发**唯一**原厂高质量命令`0x22/0x31`，AA13 GATT status=0；18:00:28～18:00:40的15次只读HTTP复核始终`NO_CHANGE`、29张、AP保持=true；11秒观察窗口内**没有新3200×2400照片**。18:00:43 BLE后来断开（距命令约15秒），18:02:50 Wi-Fi才断开；**绝不是命令一发送Wi-Fi立刻断线**，断开原因未确定，失联后无法发送原厂ZERO。
- **同结果复现**：第2轮18:03:07～10先在无AP时实体快门再次正常拍照；18:03:31原厂AP已连接/HTTP列表稳定，新基线**30**张（第2轮拍照新增，与AP在线命令无关）；18:03:39第二次独立AP会话唯一`0x22/0x31`命令AA13 GATT status=0；到18:03:50，HTTP复核15次仍`NO_CHANGE`、30张、AP保持=true、无新原图。18:03:53用户按C原厂**ZERO `0x44/0x30/0x01`**，AA13 GATT status=0，旧Android Wi-Fi Network消失，18:03:54眼镜`isImport=0`，正常恢复。两轮都不是BLE提交失败，也没有证据证明固件摄像头在`isImport=1`下接受该命令。
- **结论边界**：原厂手机App静态代码中`isImport=1`主动禁拍已证实；过去 AP 在线普通`0x22/0x30`未生成图，现 AP 在线高质量`0x22/0x31`在**两次独立真机AP会话均未生成新原图**。与用户先前眼镜实体快门在Wi-Fi连接时无法拍照的现象一致，**高度怀疑固件ISP导入工作模式与拍照互斥**；但不能仅从GATT status0/列表NO_CHANGE推断具体固件阻断点或断言所有固件均不可行。第1轮BLE约15秒后断、第2轮正常ZERO退出，说明无须把Wi-Fi延迟断线误称为命令直接造成。
- **后续纪律**：停止重复旧RTSP卡顿测试和AP在线`0x22/0x30`、`0x22/0x31`指令实验；下一阶段需要**针对该副M02S确切固件/主控型号**查找厂家官方SDK、固件模式控制能力、允许Wi-Fi传输与摄像头拍照并行的可信证据，先做静态/只读研究，取得可恢复固件包之前**不刷机/不写未知命令**。如果没有固件开放的并行拍照功能，仅改安卓App不能承诺Wi-Fi常连拍3200×2400或3～5秒OCR/TTS。保留最可靠的**离线实体拍照→原厂AP开关→HTTP高清GET→FULL/ZERO回到拍照**基线（v0.1.29等），用户尚未授权进一步代码修改。
- **仓库不变**：此次只新增 main 的交接结论，不修改v0.1.31或旧分支源码、不构建、不发布、不覆盖。

---

# 最新｜M02S v0.1.31 AP在线高质量0x22/0x31独立真机实验APK（2026-11-15，已发布，待验证真实镜端能力）

- **当前用户核心目标与条件**：不使用昨天已失败且卡顿的RTSP；希望M02S和vivo X200 Pro**Wi-Fi保持连接状态下仍可实体快门拍新3200×2400高清照片**，现有AP HTTP立刻取得新原图，实现未来OCR/TTS**3～5秒，必须≤5秒**。这可能是镜端ISP导入模式互斥；**安卓App单改不一定能突破固件限制**。本次用户在APK静态专项报告之后已明确允许**一项不同于旧失败0x22/0x30的真机探针实验**。不做刷固件/OTA/重复RTSP或隐蔽后台常连改造。
- **先做原厂静态证据核查**：私有 `com.starglasses.app` 的7个DEX直接确认`DeviceFragment$c.onClick`在`SyncValueBean.isImport`→`Lgw6.O`=true时禁用原厂App普通拍照；`SendCommandViaBle.takePhoto(0x30)`为普通参数，`takePhoto(0x31)`为高质量/AI参数，但**没有发现**原厂明确支持`isImport=1`时`0x22/0x31`成功生成高清文件的路径。M02S v0.1.10 AP在线旧 `0x22/0x30`未出现有效新原图，v0.1.12 AP不在线时 `0x22/0x31`仅通过AA15取得320×180 BLE图；**唯一新测试组合是AP确实连接+isImport=1情况下的0x22/0x31**，不能谎称已解除固件禁拍。
- **新隔离分支/源码**：`exp/m02s-ap-online-hq-shot-v0131-20261115`从用户真机已证实连续成功的`exp/m02s-oem-wifi-recover-v0129-20261115`派生；独立APK applicationId`com.vobichngan.bluestar.m02saponlinehq31`、versionCode32、versionName0.1.31。v0.1.28、v0.1.29、v0.1.30、原厂App、正式蓝星向导和Windows OCR均未覆盖。继承已成功的BLE实体快门监听、vivo精准SSID+新鲜唯一BSSID→一次SSID-only备用、Android真实专属Network/网关、OEM HTTP校验、原厂FULL/PARTIAL/ZERO收尾；**普通导入按钮原样保留**。
- **三步专项实验（主动、一次、无未知指令）**：A `AP在线拍照专项准备`：先在**无AP时眼镜实体快门正常拍一张**→BLE待机→用户点A启动原来一次`0x39/0x30` AP，确认真实`onAvailable`+可信IPv4网关、`GET /app/getfilelist` HTTP200、两次完整原始列表连续稳定；将此旧照片列表作为保密的RAM基线，保持Wi-Fi与GATT在线，**不下载整批原图也不自动发送拍照**。B `AP已连接只发一次0x22/0x31`：仅当真实AP仍连接、先前已验证HTTP列表、镜端被动`AA14 0x45 isImport=1`、GATT AA13可写、用户主动点击B时才允许发送唯一**原厂已知字节`AB 55 00 03 22 31 53`**；`apPhotoProbeUsed`和`apPhotoProbeAttempted`禁止同AP重复发送。GATT回执3秒内独立报告（不把ACK当成功），AA14合法校验通过的0x22/0x42命令号匿名观察。原来Wi-Fi Network每约600ms只读HTTP列表，最多约11秒；`M02sApPhotoProbePolicy.compare`要求**原所有图片仍在、总条目只增加1、只出现一张新图片、连续两次完整列表稳定**，然后从**同一Network** GET实际原厂新JPEG并验证格式/Bitmap尺寸。**只有新原图精确3200×2400 + 旧Wi-Fi仍连接**才显示`【AP在线原生高清拍照成立】`。即使远程命令成功，也还需下一阶段单独验证**实体快门**在AP连接下是否能拍，不能混同。C `结束AP专项：原厂ZERO`：没有将读取文件保存到手机相册，明确按原厂0张完成发送`AB 55 00 04 44 30 01 75`，不触发可能清理镜端媒体的FULL；GATT ACK后注销网络回到BLE待机；早期列表HTTP失败但AP仍活跃也可ZERO。若眼镜已断AP不能强称ZERO已执行。
- **保护原则**：新探针绝不自动触发0x22，不发送旧失败的0x22/0x30，不循环重试、不强行修改摄像头ISP/固件、不发送直播/OTA/未知命令；不记录SSID、MAC、照片名或相册原始JSON，不写相册或传云端。全部镜端照片仍遵守用户之前已授权的原厂清理边界；本探针显式选ZERO避免误删。点击“停止并断开”可以强制终止，但正常应点击C进行原厂结束。
- **真实CI/Release**：当前实验分支HEAD `a715961275a5398aae8f354216f1cd330c74d580`；GitHub Actions [43701490882](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43701490882) `completed/success`，静态命令白名单、一轮唯一新原图判定JUnit、原厂FULL/PARTIAL/ZERO和Wi-Fi单次恢复检查均通过，Gradle `testDebugUnitTest`+`assembleDebug` BUILD SUCCESSFUL，APK v2签名/zipalign/aapt包名核对通过。GitHub Release `bluestar-m02s-ap-online-hq-shot-v0131-20261115` asset uploaded：`BlueStar_M02S_AP_Online_HQShot_0.1.31_Android.apk` **147881字节** SHA256 `bfd88e562b5a26d90cae77ce3d6a55242de7e5593b9a9c219f38b982c9a22053`；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-ap-online-hq-shot-v0131-20261115/BlueStar_M02S_AP_Online_HQShot_0.1.31_Android.apk 。
- **下一步真机只需这一项**：先BLE待机→**AP未开时**眼镜实体拍一次→按 A 等 `【AP在线实验准备就绪】`；只点一次 B，实际听有没有快门声、是否仍连Wi-Fi、日志有无AA14合法拍照/计数通知、新原厂HTTP是否新增3200×2400原图；然后点C原厂ZERO退出，发匿名完整日志。如果 B失败但GATT写status0，**证明BLE仅收到命令而固件未证明拍照，不应加大重试力度**。如果第一步AP连接就`onUnavailable`，保留v0.1.29既有ZERO收尾，不把网络失败误认为拍照互斥。真正≤5秒的OCR/TTS还未开发。用户明确要求后续依此结果讨论，再决定是否有必要找厂家固件支持。

---

# 最新｜M02S 原厂APK Wi-Fi在线拍照静态审计（2026-11-15，**没有修改代码/固件或生成安装包**）

- **用户当前实际目标**：Wi-Fi AP与手机连接保持时，眼镜实体快门还能产生新的**3200×2400 JPEG**并立即从原厂HTTP接口读回，之后OCR/TTS目标从按下实体快门到开口**3～5秒、绝不能超过5秒**。此前RTSP直播实验失败/卡顿，不再重复。用户本次授权**先核查原厂App是否存在可用特殊拍照接口或解除互斥的模式命令**；本轮不做固件修改和真机发送。
- **唯一原厂依据**：用户私有 `com.starglasses.app` APK `70465ff3819258a6218e7871da0a23f8.apk` 284656626 bytes SHA256 `92f103b9d473d4cdf7424407ddfc851cac1700d15a06e55dfa0009ccf962ad57`，已在本轮**重新直接解析全部7个DEX的实际字节码和静态命令常量**，没有把文件上传公共仓库。完整报告新增 `experiments/m02s-wifi-only-lab/AP_ONLINE_SHUTTER_STATIC_AUDIT_20261115.md`（main上，已提交）。
- **直接证据一，原厂UI有明确拦截**：`classes5.dex DeviceFragment$c.onClick`在下发0x22拍照前检查 `Lgw6.N/M/O`，`O`真时弹出`string_isp_importing`并返回；`classes2.dex ModBleResponse.onRequestSyncDataResponse`在字节码位置704～707将镜端`SyncValueBean.isImport`直接存入全局`Lgw6.O`。所以**手机原厂App在isImport=1时主动不允许拍照**；然而已知眼镜物理快门在AP连接时也不能拍照，删掉手机端判断**不等于解除镜端固件限制**。
- **直接证据二，仅发现已有拍照两参数，无专门AP在线快门**：原厂`SendCommandViaBle.takePhoto(I)`固定命令号`0x22`，普通按钮 `0x22/0x30`（TAKE_PHOTO_THUMBNAIL），AI识图/AI拍照 `0x22/0x31`（TAKE_PHOTO_HIGH_QUALITY），原厂其它AI/语音路径仍用同0x22并非新Wi-Fi在线快门协议。之前v0.1.10在AP已打开状态`0x22/0x30` GATT status0却无快门声/无新图通知；v0.1.12 **不在AP导入状态**的`0x22/0x31`曾成功通过AA15得到320×180 JPEG、约3061ms，**不能因此把它称为AP在线3200×2400高清拍照**。唯一尚未由等价实机配置核实的是 **`isImport=1`且AP真连状态下的单次`0x22/0x31`**，但仍没有任何成功保证。
- **直接证据三，ISP工作模式分离**：`Command$Param`静态常量`ISP_TAKE_PHOTO=0` / `ISP_TAKE_VIDEO=1` / `ISP_TAKE_AUDIO=2` / `ISP_IMPORTING=3`；`AlbumFragment$k.lambda$onIspWorkState$0`将这4种状态分别显示。原厂还有`SendCommandViaBle.setCameraMode` commandId`0x50`，参数Standard`0x30`/Classic`0x31`，扫描全部7DEX没有找到实际调用者，**不能将风格模式误认成ISP传输状态→拍照状态且AP不断开的命令**。APK内无可辨认的镜端firmware镜像，因此不能仅从Android App安全修改眼镜芯片拍照互斥。
- **审计结论**：已经证实的只是**App侧导入期间禁拍**、同一0x22两种参数和镜端不同ISP状态；**没有证据能保证Android App单独解除物理快门限制**，也不能下结论说固件绝对不可能支持。若用户决定继续，优先在**独立诊断实验版**上只发一次已有原厂明确`0x22/0x31`，条件为镜端BLE有效、`isImport=1`、AP已`onAvailable`且真实HTTP列表可读，**被动对比AP是否保持、是否出现新的3200×2400原图**；不重复已失败的`0x22/0x30`或RTSP、没有危险未知指令或固件刷写，失败即原厂ZERO收尾；再失败才转为向厂商索取具体M02S固件/API与安全恢复方案。**该真机实验尚未得到单独授权，也未做任何代码修改。**
- **项目保全**：v0.1.28原厂FULL导入、v0.1.29 Wi-Fi失败ZERO恢复、v0.1.30最新优先/自动导入性能实验分支、Actions、Release、正式蓝星向导与Windows OCR代码均保持原样。本次只有main文档报告和交接记录更新。

---

# 最新｜M02S v0.1.30 极速首帧：最新JPEG优先交付＋可选快门结束后自动导入（2026-11-15，APK已发布，5秒语音目标待验证）

- **用户明确目标**：从眼镜实体快门按下到听到OCR朗读，**3–5秒，不能超过5秒**。在上一轮建议“先把新照片到手机的传输阶段优化，再考虑接OCR/TTS”的基础上授权改代码。此版只完成**首张高清JPEG提前到手机**和硬件传输计时，**没有OCR或TTS组件**，绝不声称已经实现5秒内开口朗读；还需要真机性能证据。0x45拍照活动开始时间戳不一定等于物理按钮电气触发瞬间。
- **核心基线不可破坏**：v0.1.29用户真机日志16:14–16:19的9次人工导入中，**7次直接成功**，第3次和第8次首次新鲜BSSID Wi-Fi请求以及一次准确SSID-only回退均`onUnavailable`，但已证实原厂**ZERO 44/30/01** GATT status0→眼镜被动isImport=0→**保持BLE、不重新拍照、重新手动导入同一张成功**。成功轮次从15/15一路到21/21张整批GET，原厂FULL状态退出后Network消失；最后16:19:30曾独立出现BLE待机断开，这与两次Wi-Fi失败区别看待。继承v0.1.29，不能再去重做早期常连Wi-Fi或第一轮实验。
- **源码分支**：`exp/m02s-fast-first-frame-v0130-20261115`从`exp/m02s-oem-wifi-recover-v0129-20261115`派生；独立APP包名 `com.vobichngan.bluestar.m02sfastframe30`、versionCode31/versionName0.1.30。v0.1.28、v0.1.29、Android正式蓝星向导、Windows OCR及以前分支/Release**均未覆盖**。
- **速度关键变更**：新增纯逻辑`M02sFastDownloadPlan.plan()`和JUnit，取已经经过**两次原厂HTTP完整稳定列表**选出的唯一最新图片，**首先**原厂只读HTTP GET+校验文件头/真实尺寸+原始高清JPEG直接放RAM预览（未来OCR输入接口），无需等待任何旧图，日志标记`【最新高清首帧已就绪／未来OCR入口】`，分别统计本轮取图启动→高清就绪、Wi-Fi就绪→高清就绪、0x45拍照活动开始/结束→高清就绪；此前v0.1.29是全部历史原图下载完才显示最新JPEG。随后仍单线程按原厂列表顺序下载**其余**旧图，若全部成功仍以真实完成数发已连续实测成功的原厂FULL 0x44结束，保持Wi-Fi断开/BLE待机；若旧图中途失败不冒充FULL，只报真正完成数进入PARTIAL/ZERO。JPEG原图无需写相册/上传云端。
- **可选极速自动实验（每次启动默认为OFF）**：UI新CheckBox“极速自动导入实验”，只有用户主动勾选后且收到眼镜实体快门`0x45 isTakePhoto 1→0`、BLE确实待机、未在传输时，才**自动执行一次**已验证的同一个AP导入入口；不是原来不可靠的250ms从快门开始自动开AP。v0.1.29手动按钮与原来1.8秒新鲜热点唯一BSSID稳定判断完整保留；只在用户开启极速实验时用`M02sApReadinessPolicy.canConnectWithSettling(...,650L)`代替1800ms阈值，仍验证真实BLE SSID、Android onAvailable、IPv4可信网关、原厂HTTP200、两次稳定照片列表。仍保留v0.1.29一次准确SSID-only备用连接与两次失败后的原厂ZERO、BLE与待导入图片恢复。自动模式不发远程0x22快门、不重复BLE开AP、不常连Wi-Fi、不无限重试、不发OTA。
- **边界**：镜端旧图片累计已超过20张，`M02sOemImportPolicy.MAX_OEM_BATCH_IMAGES=24`的全量批次上限未取消；超过24张/混合文件仍沿用原版“只最新一张+原厂PARTIAL”策略，**尚未验证长期超过24张的真机表现**。用户已明确授权可能清理镜端媒体的原厂FULL指令，但对文件真实删除范围不作未经验证的承诺。
- **CI/Release真实核查**：新分支当前HEAD `758389fb84a11d54a0e5ed0b4908de87dcf52685`，GitHub Actions [43694951644](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43694951644) **completed/success**，Gradle`:app:testDebugUnitTest :app:assembleDebug` BUILD SUCCESSFUL、早帧顺序和权限静态断言、最新优先及650ms/1800ms基线分离JUnit、APK v2签名、zipalign及aapt包名均通过。Release `bluestar-m02s-fast-first-frame-v0130-20261115`已返回上传状态`uploaded`；`BlueStar_M02S_FastFirstFrame_0.1.30_Android.apk` 135025字节，SHA256 `65bebe65883e25dda274a2858a6cde9801d14bf9562ca293ca2e5bc5140c0a4c`；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-fast-first-frame-v0130-20261115/BlueStar_M02S_FastFirstFrame_0.1.30_Android.apk 。
- **下一步唯一真机验证**：用v0.1.30先确认默认手动模式仍然连续成功且首图**提前**显示，再开“极速自动导入实验”对照完整物理快门、0x45状态开始→JPEG就绪、Wi-Fi建立、照片开始朗读的潜在预算。重点给`【最新高清首帧已就绪／未来OCR入口】`行的时间、`原厂后台收尾已完成`行的时间，以及成功/失败序列；`≤5000ms`绝非代码已保障。后续按实测瓶颈再决定是否接轻量本地OCR/TTS，不能用JPEG到手机的时间替代语音已经开始的时间。

---

# 最新｜M02S v0.1.29 基于已真机成功6/8轮的v0.1.28：一次SSID备用关联、ZERO结束且BLE不掉线（2026-11-15，已发布APK，待连续真机验证）

- **坚决保留成功基础**：用户v0.1.28真实日志8轮中第1、2、4、5、6、8轮均BLE实体快门→一次开AP→vivo Pattern+新鲜BSSID→HTTP原厂整批GET（6、7、9、10、11、13张）→原厂FULL 0x44 GATT status0→Android旧Network消失→0x45 isImport=0，共**6/8轮成功**。第3、7轮在已通过BLE收到SSID、新鲜唯一BSSID的前提下，Android精确BSSID请求分别3432ms、3152ms返回onUnavailable。**没有证据表明本身是原厂结束帧故障，不重写成功主链**。
- **实际源码**：新分支 `exp/m02s-oem-wifi-recover-v0129-20261115` 从已发布 `exp/m02s-oem-complete-import-v0128-20261115` 创建；v0.1.28首次成功的照片BLE监视0x45、用户手动触发0x39/0x30、0x25 SSID、vivo `PatternMatcher` + 新鲜唯一BSSID与12秒单次扫描回退、原厂HTTP200两次稳定列表+完整GET、原厂FULL 0x44、旧Wi-Fi Network消失后BLE待机全部不变。Android独立包 `com.vobichngan.bluestar.m02soemrecover29`、versionCode30/versionName0.1.29，不能覆盖以前任何版本或正式蓝星向导、Windows OCR项目。
- **仅失败分支改动**：`M02sApStarter.connectWifi(token,observedFreshBeacon,recoverySsidOnly)` 在**首次新鲜BSSID被锁定且onUnavailable**时确认旧NetworkCallback被系统释放/归属置null后，通过`M02sWifiRecoveryPolicy.canTrySsidOnly`只对**同一BLE证实SSID**发**一次**不锁BSSID的SSID Literal Pattern WifiNetworkSpecifier请求；不发送第二次BLE 0x39命令，不无限重试，不猜SSID。首轮本来就是SSID-only失败时不重复同样请求。若备用成功必须走真实IPv4网关和原厂HTTP200验证。
- **失败后镜端正确收尾**：旧v0.1.28 `onUnavailable→fail→stop`会关闭GATT，现在在两次请求均失败时，仍使用有效AA12/AA13 GATT（原厂AP命令已ACK+合法0x25 SSID）发送一次**原厂ZERO结束帧 `AB 55 00 04 44 30 01 75`**（下载完成数0，不发FULL媒体清理），至多3秒等待写回执。然后释放手机临时Wi-Fi请求、保留蓝牙连接、不再立即`stop()`，并调用MainActivity显式`recoverableImportFailure`：恢复**同一拍摄候选**，允许后续用户手动点击「导入高清原图」，无需再次拍照；非自动开AP。若已经被动观察到`isImport=1`，在收到新的`isImport=0`之前导入按钮保持禁用，8秒仍无0则显示设备退出未知后允许用户主动重试，绝不自动连接。
- **安全/边界**：即使用户已授权原厂全量图片清理，本次只在原本整批成功的正常路径继续发送既有FULL，Wi-Fi失败善后仅发ZERO，避免失败状态下误清理原图；不上传、不写手机相册、不刷固件、不远程0x22快门、不改变正式App。老v0.1.28完整可用包继续保留。未证明无限次连续稳定——需要用户实际测试遇到`onUnavailable`时备用SSID是否成功、若两者失败ZERO是否结束后保留BLE/旧照片能否重新手动导入。
- **CI/发布**：已通过原GitHub Actions [43693320602](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43693320602)，真正执行SDK36、Gradle :app:testDebugUnitTest :app:assembleDebug、恢复状态/零文件原厂帧JUnit、完整原厂FULL路径静态断言、APK v2签名/zipalign/aapt包名及权限核验，`BUILD SUCCESSFUL`。APK `BlueStar_M02S_OEM_Recovery_0.1.29_Android.apk` 127473字节 SHA256 `f38fb643fa1eceb30eca6fd08f9cce36fb0304e2e81cc24b53c5ccc237df3adc`，GitHub Release tag `bluestar-m02s-oem-wifi-recover-v0129-20261115`已验证资产uploaded；下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-oem-wifi-recover-v0129-20261115/BlueStar_M02S_OEM_Recovery_0.1.29_Android.apk 。最后修复workflow多次push重复Release后，幂等CI在run [43693446360](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43693446360)复核已完成，状态 completed/success；JUnit、Gradle、APK v2签名、zipalign、独立包名和Release资产复检全部通过，最新APK仍为127473字节并已核对uploaded，新分支最新源码HEAD `77f6a7c1b9bf85a8c3945fcaae767cb76a053f5f`。之前另一个因重复创建同tag Release失败的run并非Gradle失败；最终workflow已改为已存在Release时更新asset。
- **下一次只需要用户测试失败分支**：照旧实体拍照→人工导入继续循环；遇到偶发`onUnavailable`，重点看“首个精确BSSID请求onUnavailable→一次准确SSID Pattern回退→onAvailable”是否成功；如果未成功则看ZERO结束回执、isImport=0、BLE仍待机，以及无新快门直接重新点导入是否成功。不要求重做第一轮已成功的旧实验，不把代码CI成功当真机稳定完成。

---

# 最新｜M02S v0.1.28 真机成功连续两轮；第三轮 Wi-Fi onUnavailable（2026-11-15）

- **真实两轮连续成功（历史第一次）**：第1轮15:56:47用户手动导入→15:56:54 onAvailable（2462ms）→原厂HTTP完整列表6/6张全GET成功、最新3200×2400→15:56:59原厂FULL `0x44/0x30/0x00` GATT status0→15:57:00旧Android Wi-Fi Network消失→15:57:01 BLE被动`isImport=0`。第2轮15:57:45–48实体快门0→1→0，15:57:54用户手动导入→15:57:59 onAvailable（510ms）→完整列表7/7张全GET成功、最新3200×2400→15:58:03原厂FULL 0x44 status0→15:58:04旧Network消失→15:58:05 isImport=0。**从原先第二轮必失败进展到两轮完整成功，验收第二轮已过。**
- **第3轮失败**：15:58:19–22实体拍照正常→15:58:31手动开AP，AA13 `0x39/0x30`写成功，收到镜端`isImport=1`及真实0x25 SSID（545ms），本轮扫描到新鲜唯一BSSID，15:58:35以vivo literal SSID Pattern+准确新鲜BSSID一次性申请Android专属网络；15:58:39 3432ms就`onUnavailable`，Android Wi-Fi Network=0；没有进入HTTP，也没有发送第三轮结束0x44。48s超时尚未到，说明请求提前无法满足，但`onUnavailable`不包含根因，**不能证实是手机、密码、审批还是眼镜AP拒绝**。
- **源码发现一处确定的失败善后缺陷（不能反推第三轮连接失败的根因）**：`M02sApStarter.java`中`onUnavailable`尝试复用已有Wi-Fi未果→`fail(token,...)`→直接`stop()`销毁BLE GATT。这样无机会用原厂的0张下载ZERO结束流程使镜端退出第三轮AP，也损害后续恢复。Android官方文档同时确认，`onUnavailable`时对应网络请求**已经被系统移除**；不能将回调置null称为造成网络请求泄漏。
- **额外事实**：第1轮FULL后第2轮原厂HTTP列表由6增加到7，不支持“FULL必定清空所有原图”的推断；可能只是清缩略图，但真实固件副作用仍未完全验证。按用户授权继续原厂式处理。
- **下一步只处理第三轮`onUnavailable`**：保持v0.1.28两轮已成功的BLE AP/SSID vivo matcher、原厂整批GET和FULL结束流程原样不动；失败时分离Android网络请求失败与镜端AP退出，保留仍有效BLE，按已核实原厂ZERO结束并释放会话，避免失败即销毁GATT；对Android连接拒绝原因增加非隐私观察，再决定是否允许用户显式触发的单次精确SSID、不锁BSSID回退。不要重新做以前第一轮实验，也不把扫描等待加长当修复。当前本次核查**只更新交接文档，不修改代码、不新建APK**。
- **后续标准**：连续3–5轮，以及偶发`onUnavailable`后还能正常收尾并开始新一轮。相关原始实验分支`exp/m02s-oem-complete-import-v0128-20261115`，已发布v0.1.28保持不变。

---

# 最新｜M02S v0.1.28 原厂完整导入+结束0x44+真实断开Wi-Fi等待（2026-11-15，已发布APK，连续第二轮真机待验）

- **用户对安全边界最新明确授权**：用户原话“眼镜里没有任何有价值的照片，新买的；只要按照原厂的来”，准许像原厂一样发送**可能清空镜端媒体**的完整成功结束帧 `0x44/0x30/0x00`。这项授权仅用于原厂式M02S照片导入实验，不等于授权上传云端、远程快门、刷机、清理其它App或修改Windows仓库。已明确告诉用户软件可清空眼镜照片且不可恢复。
- **不再重复第一轮成功的旧实验**：v0.1.26 15:15成功首次BLE AP→vivo `onAvailable`→HTTP200→3200×2400 JPEG，但只释放 Android `NetworkCallback`，没有向固件发原厂结束指令，第二轮仍未真机成功。v0.1.27只读审计确定原厂 `Ldp.onGetDataSuccess` 计数完整`FileItem`列表长度 `l`，`Ldp$e.taskEnd` 每条原图真正完成本地迁移后增计数 `o`，`Ldp.stopP2pWifiConnect` 根据 `o/l` 选择：0成功→`44/30/01`，全部成功→`44/30/00`，部分成功→`44/31/o`，然后 `disconnectAp()`。原厂APK不等于固件源码，M02S具体复位效果仍需硬件。
- **本次一次性开发**：新分支 `exp/m02s-oem-complete-import-v0128-20261115` **基于v0.1.26首次已真机成功的建连源码**，没有把v0.1.27只读审计版当新功能基线；旧v0.1.24/25/26/27分支和Release、原厂HeyCyan、蓝星主APP和Windows OCR不覆盖。保留严格实体快门只监听0x45→用户**手动点击导入**才一次性发送0x39/0x30→vivo PatternMatcher+新鲜BSSID/12s单次回退→私有网关/原厂HTTP200/JPEG。
- **原厂计数真正对齐**：`M02sPhotoListReader.sameSnapshot` 将**完整原始原厂HTTP列表及每个图片候选的顺序/名字/大小/时间/type**两次稳定核对，不再仅对齐最新候选。若整份原厂列表都属于可GET图片且总数不超过24，按**原服务器文件列表顺序逐张GET并校验每张**（仅最新图留RAM预览），只有全部完成后才发送`AB 55 00 04 44 30 00 74` 原厂FULL帧（可能清镜端照片）；若原厂列表含非图片、列表超过24张则只下载最新一张并发送原厂`44/31/01` PARTIAL帧；若完整下载中途失败只发送真实完成数对应的PARTIAL或ZERO帧。所有帧经 `M02sOemImportPolicy.authorizedFinish` 严格校验；没有猜测完成数、没有无效一张被算作全完成。取消/退出App不会补发清理帧。
- **真正退出Android临时Network**：`M02sApStarter.requestOemFinish(total,done,cb)` 只在有效AA12/AA13 BLE GATT+真实AP HTTP会话下调用一次，监听AA13 GATT写回执，最多3秒无回执也不会重复发送。通过`intentionalImportExit`区分原厂结束造成的预期Wi-Fi onLost，防止手机旧回调误停BLE；然后`releasePhotoNetwork`注销对应Android `NetworkCallback`，清旧SSID、网关、会话状态，并以旧`Network`句柄检查确实不再有Wi-Fi capabilities**才允许开启下一轮**（否则保持阻断并提示，不谎称断开；超10秒只读低频监测，无AP重试）。全程不强制保持Wi-Fi连线拍照，BLE继续待机。
- **实机目标**：只看连续两轮：BLE待机→实体快门拍A→人工「导入高清原图」→原厂HTTP清单整批GET→原厂FULL/PARTIAL/ ZERO结束帧→旧Network真正消失→再次实体快门拍B→人工导入B→**新的高清JPEG**。第一轮成功**不算验收**。本软件不自动替代原厂“导入”按钮；先验证退出循环可行。用户已授权镜端媒体清理，无需在每次启动反复勾选。
- **真实构建/发布**：源码/CI HEAD `c7c1233cd976f98cebae0d4c5a60cf3d62a90682`，Actions [43691608764](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43691608764) = completed/success；SDK36、Gradle `:app:testDebugUnitTest :app:assembleDebug`、原厂三种准确帧和数量非法保护JUnit、全列表稳定单测、原建连vivo代码静态校验、签名v2、zipalign、aapt包名、必要权限均通过。独立包`com.vobichngan.bluestar.m02soemfullcycle28`，versionCode29/versionName0.1.28；Release已回读且asset状态uploaded：`BlueStar_M02S_OEM_FullCycle_0.1.28_Android.apk` 120981字节 SHA256 `6c6a280352cecc2f6ec52fd0b0c2f774e57e9cc9907823472f4127d5a49ed2db`。
- **网页直链** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-oem-full-cycle-v0128-20261115/BlueStar_M02S_OEM_FullCycle_0.1.28_Android.apk ；[Release页](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-m02s-oem-full-cycle-v0128-20261115)。**安装包构建成功不等于M02S固件已真正通过连续第二轮测试**。若第二轮失败只需收集v0.1.28的完整匿名日志，不再重做没有退出帧的旧版连接实验。

---

# 最新｜M02S v0.1.27 原厂退出计数审计＋手机/镜端状态分离（2026-11-15，源码与CI已完成；未发布APK，第二轮未修复）

- **用户最新事实**：v0.1.26 第一轮 15:15:14 人工点导入→15:15:31 vivo Pattern回退 `onAvailable`，HTTP200 列表图片数5、15:15:34 3200×2400 JPEG 下载成功，仅显示手机侧 `releasePhotoNetwork()`；没有确认眼镜真正退出 AP 或第二轮成功。用户强调这只是重复昨天已成功的第一轮，不再把它当新突破。
- **已直接重审原厂APK**：用户原始 `com.starglasses.app` 284656626字节，SHA256 `92f103b9d473d4cdf7424407ddfc851cac1700d15a06e55dfa0009ccf962ad57`，7DEX，只在私有执行容器读取（**未提交原厂APK**）。`classes5.dex Lu64$a.onLoadSuccess()` 将 `PhotoApBean.info[].files[]` 展平为原始 `FileListParser.FileItem` 列表，代码内无只限JPG过滤；`Ldp.onGetDataSuccess()` 将该列表 `List.size()` 写入本轮总数 `l`；`Ldp$e.taskEnd(COMPLETED)` 仅在原图完成本地文件处理后增加完成数 `o`；`Ldp.startDownLoadPhotoByWifi()` 结束时约400ms延迟执行 `Ldp.stopP2pWifiConnect()`，按 `o=0` →44/30/01、`0<o<l`→44/31/o、`o=l>0`→44/30/00，随后`Lxxh.disconnectAp()`。原厂此处并没有显式等待0x45 isImport=0或GATT固件复位确认。
- **本轮新增关键风险判断**：蓝星 `M02sPhotoListReader.entriesObserved` 是原始HTTP条目数，`eligibleImages` 是本地过滤后的图片候选数；v0.1.26直接拿5张图片、单张JPEG成功下载推导`44/31/01`的条件，并非已证明与原厂文件列表和原厂顺序一致。协议中的`44/30/00`在E09公开研究中被标注为**删除媒体触发**，M02S具体行为无真机证明，绝不盲发；`44/31/N`在其他硬件上被称为“不删除”，但对M02S固件也未证实。v0.1.20的`44/30/01`已失败过，不重复。
- **独立研究分支**：`exp/m02s-oem-exit-audit-v0127-20261115` 从v0.1.26源码派生；新增只读 `M02sSessionEvidence.java` 和5组JUnit测试，分别记录原始列表总数/可读图片数、手机释放NetworkCallback、0x45 isImport后续变化、释放后实体快门、新的JPEG完成数。**手机网络释放≠镜端退出；0x45 isImport=0与拍照活动都不单独证明第二轮Wi-Fi可连接；不同高清JPEG第二次HTTP成功才是二轮有效证据**。`MainActivity`状态文字已纠正成“镜端退出未确认”，继续保留v0.1.26 vivo Pattern/BSSID连接、实体快门＋手动导入、只读HTTP/JPEG、无自动重试。
- **安全改动**：在本研究分支的 `M02sApStarter.java` 和 `M02sApWire.java` 内**移除了0x44发送和授权实现**，`MainActivity` 也移除原实验勾选及调用；只保留用户主动BLE 0x39/0x30 AP开启和只读0x64项目号，不远程拍照、不删图、不刷固件、不上传。旧v0.1.24/v0.1.25/v0.1.26实验源码/Release原样保留。
- **可核对的审计报告**：[OEM_EXIT_AUDIT_20261115.md](https://github.com/vobichngan285-collab/kanzi-assistant/blob/exp/m02s-oem-exit-audit-v0127-20261115/experiments/m02s-wifi-only-lab/OEM_EXIT_AUDIT_20261115.md)。CI HEAD `5bf0c1775dd244fb536802c4d2fb31fbead2c5b5`，Actions [43690638788](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43690638788) `completed/success`：安全断言、Gradle `:app:testDebugUnitTest :app:assembleDebug`、APK v2签名/zipalign/独立包名 `com.vobichngan.bluestar.m02sexitaudit27` 均通过；只保存JUnit结果Artifact `14152311473`，**没有创建新Release、没有让用户再下载重复测试APK**。
- **下一步边界（未解决）**：必须从M02S固件官方协议或原厂App实际设备通信明确，成功后哪个0x44组合能退出AP且不影响任何眼镜原图，以及原厂对文件序列如何解释成功N。Android APK静态代码不足以推出M02S固件副作用；不得说已修复第二轮，也不建议用户再次重复第一轮真机实验。不得改正式蓝星向导、Windows OCR、旧仓库项目。

---

# 最新｜M02S v0.1.26 恢复v0.1.24原厂式vivo Wi-Fi建连，保留v0.1.25手动导入（2026-11-15，已发布，真机待测）

- **最新真机失败证据（不重做已失败实验）**：用户v0.1.25匿名日志14:10:30～14:10:32实体拍照0x45从0→1→0成功，14:10:39人工点导入并成功写AA13 `0x39/0x30`，14:10:40得到准确AA14 `0x25` SSID（AP指令至SSID=551ms），其后旧直连 `WifiNetworkSpecifier.setSsid()` 在14:10:46 `onUnavailable`（6596ms），根本未拿到Network/HTTP/JPEG，也未进入v0.1.25新的`0x44/0x31/0x01`结束实验。不能把该次失败解释为结束帧无效。
- **确认的版本回退**：v0.1.25从旧v0.1.22手动导入源码派生，连接层丢掉了v0.1.24的已真机用过的vivo PatternMatcher+fresh BSSID模式，回到直接`setSsid()`。用户已证实Wi-Fi**保持连接时实体快门不工作**，所以本轮**只移植v0.1.24的一次性关联机制，绝不复活其常连取图**；v0.1.24首次成功不代表本轮新APK可以保证连接成功。
- **新隔离分支／来源**：`exp/m02s-oem-wifi-restore-v0126-20261115`，基于已发布的v0.1.25 `exp/m02s-oem-partial-finish-v0125-20261115`；在原 `experiments/m02s-wifi-only-lab` 内小范围修改。恢复v0.1.24原版`M02sApReadinessPolicy`/ `M02sApConnectPolicy`（原Git Blob与旧分支相同未重写）、`scanForAp`和测试（`beaconBetweenApWakeAndSsidReplyMustNotBeDiscarded`），从**用户手动导入**的BLE开AP指令时间点取`ScanResult.timestamp`锚点；只认BLE准确SSID、最新唯一BSSID，经过原版1.8秒稳定检查后，vivo一次申请`setSsidPattern(PATTERN_LITERAL)`+`setBssid(唯一新鲜BSSID)`，否则12秒单次BLE确认SSID Pattern回退、不猜BSSID，非vivo维持旧`setSsid()`；Android`requestNetwork`上限恢复v0.1.24的48秒，只改手动任务的安全总上限39→75秒以避免与12+48秒冲突（**上限≠人为等待，不增加重试**）。AA14 0x25先于GATT ACK出现时，等待写入成功后只启动一次扫描；规避重复启动。
- **绝对保留旧状态**：实体快门0x45事件仅标记待导入，不自动连接；用户手动点击才发一条`0x39/0x30` AP指令；原厂HTTP200列表两次稳定、JPEG原图只读GET仅RAM，已有0x45 isImport被动观察、0x64只读查询、Wi-Fi回调归属保护和正常释放网络后BLE保留。v0.1.25`0x44/0x31/0x01`部分导入结束实验**默认OFF，只有用户勾选且本轮仅下载1张、眼镜列表至少2张才发送一次**；该指令在M02S固件上不保证绝对不删图，重要照片须先备份。严禁`0x44/0x30/0x00`完整清理、旧无效`0x44/0x30/0x01`零下载结束、BLE遥控拍照/OTA/P2P、相册落盘或云上传；原厂App、正式蓝星向导RC25、Windows OCR与旧分支/Release均未改。
- **实际源码／CI／APK**：构建 HEAD `ce0581dc054c7bb7e1f33d48890c731c3f5b7803`；Actions [43686225216](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43686225216) `completed/success`，真实Gradle `:app:testDebugUnitTest :app:assembleDebug`、v0.1.24恢复静态检查、严禁自动导入/清理的安全断言、Android SDK36、APK v2签名、zipalign、权限和包名均成功。独立`applicationId com.vobichngan.bluestar.m02soemwifirestore26`、versionCode27/versionName0.1.26；APK `BlueStar_M02S_OEM_WiFi_Restore_0.1.26_Android.apk` **115733 bytes**、SHA256 `c989132c1d0357d305cf6c1727d856fe2c3923e919bc6f098988c822988ac5ff`。
- **Release已回读**：[v0.1.26独立Release](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-m02s-oem-wifi-restore-v0126-20261115)；浏览器直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-oem-wifi-restore-v0126-20261115/BlueStar_M02S_OEM_WiFi_Restore_0.1.26_Android.apk 。GitHub Release有该文件且状态uploaded，并非仅本地路径。
- **最短真机验收（只验证Wi-Fi连接，不再重复失败路线）**：提前备份重要眼镜照片，停止原厂和其它眼镜控制App。启动v0.1.26时**保持0x44部分导入结束复选框关闭**，准备BLE→实体快门拍1张（拍照时Wi-Fi没连）→用户手动点「导入高清原图」→应看到“v0.1.26恢复v0.1.24 OEM建连”→新鲜热点稳定/模式或回退→Android `onAvailable`+真实私有网关→HTTP200/高清JPEG。**第一次失败立刻停**并提供诊断日志，不重复BLE重试、不再测试常连状态按快门。如果成功，再单独讨论下一轮退出状态。CI成功**不等于**真机问题已解决。

---

# 最新｜M02S v0.1.25 原厂「部分导入结束」与预期 Wi-Fi 断开时保留 BLE（2026-11-15；已发布，真机待验收）

- **直接分析用户上传的原厂APK**：重新读取原始 `com.starglasses.app` APK（284656626 bytes，7个DEX）。在 `classes5.dex` 的 `Ldp;->stopP2pWifiConnect()` 中直接定位：成功数为0时发送 `fileDownloadFinish(WIFI_FILE_DOWNLOAD_FINISH, WIFI_FILE_DOWNLOAD_NOT_CLEAR)`（相当于 `44/30/01`）；成功数等于列表总数时发送 `...CLEAR_THUMBNAIL`（`44/30/00`，可能删除镜端媒体，**禁止**）；部分成功时发送 `...FINISH_NUN,successfulCount`（`44/31/N`）。原厂还调用 `Lxxh;->disconnectAp()`，内部清连接会话ID、Handler任务、注销NetworkCallback、解绑进程网络等。此前v0.1.20发送了**零下载**分支`44/30/01`，尽管当次实际下载成功一张，也不对应原厂部分成功分支；此前第二轮失败不能证明不存在其它可行状态退出路线。
- **原协议安全边界**：公开E09逆向文档 `sctg-development/ai-smart-glasses-e09:REVERSE_ENGINEERING_EYEVUE_BLE_PROTOCOL.md` 认为部分成功 `44/31/N` 不删除文件，但 **M02S固件未实测，绝不能承诺不丢图**。v0.1.25绝不发送已知危险的 `44/30/00` 完整清理、旧失败的 `44/30/01`、任何遥控快门/OTA/P2P切换/上传。实验仅用无关紧要且已备份的图，用户需主动勾选（默认OFF、不持久化）。
- **基线与改动**：从已发布的手动导入v0.1.22源码 `1bceb607359e455ea53e29e6ec7b31b240652fc6` 建立**独立实验分支** `exp/m02s-oem-partial-finish-v0125-20261115`，不覆盖v0.1.22、v0.1.24、原厂App、正式RC25或Windows OCR。保留「眼镜实体拍照→手机只标待导入→用户手动点击导入」；接收经AA14确证的SSID、原厂HTTP两次稳定列表、只读GET真实高清JPEG成功解码后，**当列表至少2张图片且本轮仅成功下载1张、用户勾选、本轮BLE有效**，才发送白名单精确帧 `AB 55 00 04 44 31 01 76` 一次，最多3秒等GATT写回执，随后释放手机 `NetworkCallback`；其它情况仍仅释放手机侧Wi-Fi。GATT成功不等于眼镜固件已退出AP。
- **关键二次状态保护**：在主动结束会话时，若Wi-Fi `onLost/onUnavailable` 先到，不应直接将整个BLE `stop()`；本实验对当前会话预期断网与旧回调分别处理，避免清除仍在用的BLE待机；但真实BLE若断开仍需要重新连接（并未保证设备自复位）。重试超时和主动退出后不追加开AP指令。
- **构建实证**：实验代码完成，独立Android SDK36 Gradle/JUnit、精确0x44白名单/默认OFF/禁止0x44清理静态检查、APK v2签名、zipalign、权限和包名检查均成功。源码HEAD `c452040e7fb4b563c36f9ea69fb7116430caaac0`；Actions [43680846609](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43680846609) `completed/success`。APK 独立包名 `com.vobichngan.bluestar.m02spartialexitlab`、versionCode26、versionName0.1.25，**112253 bytes**，SHA256 `4b75751b994c2c7cdde59c629234b3a79e30b5fcc5f1da00c94c6a7fccd47c3b`。
- **Release与直链（已回读）**：[实验Release](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-m02s-oem-partial-finish-v0125-20261115)；APK直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-oem-partial-finish-v0125-20261115/BlueStar_M02S_PartialFinish_0.1.25_Android.apk 。非草稿公开prerelease；release asset状态uploaded；另附SHA256SUMS.txt。
- **真机验证（尚未进行，不得宣称修复）**：事先备份镜端重要照片；关闭其它控制眼镜的App，新目录安装独立v0.1.25，用户自主选择是否承担未知固件副作用并勾选。先按镜端实体快门拍A→人工点击「导入高清原图」→确认高清JPEG已显示、是否发`44/31/01`及是否有`onLost`后BLE保持待机→再实体拍B→人工点导入→观察第二次AA14 0x25 / Android `onAvailable` / HTTP200 / JPEG。第二轮失败即停止并复制匿名日志。**只允许这一个与旧版不同的退出变量，不再建议Wi-Fi常连或换个超时重试。**

---

# 最新｜M02S v0.1.24｜原厂式 AP 关联修复 + Wi-Fi 常连状态下实体快门验证（2026-11-15，已发布，待真机）

- **用户核心纠正**：我们先前的 AP 常连实验 v0.1.7 已验证在 Wi-Fi 已连时用**手机 BLE 遥控拍照**无声/无新图；但**Wi-Fi 已连接时用户亲手按眼镜实体快门**尚无完成的真机验证，不能混为同一实验。v0.1.14/v0.1.15 已写好 AP 常连接、拍前 HTTP 列表基线、手按眼镜实体快门、450ms 监听及自动下载新照片（最多3张／120秒），但 v0.1.14 真机停在热点新扫描误判，根本未进入手动快门验证。用户明确授权**修复旧 Wi-Fi 建连障碍并发布独立实验 APK**，不重做其它功能、不刷眼镜固件。
- **明确来源、边界**：以旧已发布的 `exp/m02s-ap-ssid-fallback-v0115-20261114` HEAD `8916534de2a339cd7a779134cf1416b925b5d85e` 原样保留全部已经实现的实体快门/图片列表/高清 GET 流程，仅修改 AP Wi-Fi 连接及少量诊断/标识。已真机在另一独立 E09 直播 AP v0.1.13 同一 vivo X200 Pro 上确认的连接成功路径使用 `WifiNetworkSpecifier.Builder.setSsidPattern(PatternMatcher.PATTERN_LITERAL)` + 新鲜目标 `setBssid(MacAddress.fromString(...))`；**直播 AP 的连接成功不等于媒体相册 AP 本版已经验证成功**。
- **本次实际修复**：旧 v0.1.15 在 `0x39/0x30` BLE 开 AP 后直到 AA14 `0x25` SSID 返回，才把 `ScanResult.timestamp` 新鲜度时间锚点设为当前时间；若热点广播发生在发开 AP 与收到 SSID 之间，就会被错判缓存。现将新鲜度锚点改为**本轮 BLE 开 AP 指令发出时刻**（严格毫秒→微秒），只接受准确BLE SSID、10秒内、唯一实际BSSID；保留旧1.8秒稳定等待和 bounded 12 秒单次回退。针对 vivo，第一次且唯一 Android 请求改为 OEM 原厂式 SSID 字面量 PatternMatcher；新鲜BSSID存在时 pin 准确BSSID，无可信BSSID时只匹配BLE提供的SSID且**不猜BSSID**；非 vivo 沿用旧精确SSID方案。增加 `onUnavailable` 当前 `NetworkCallback` 身份检查与本轮连接毫秒计时。其余 AA13 精确一次 `0x39/0x30`、AA14 `0x25`、Android `onAvailable`/真实私有网关、原厂 HTTP200 `/app/getfilelist`、图片原图 GET/JPEG解析/仅RAM显示均保留。
- **保护项**：无 BLE 遥控快门、无照片删除、无 `0x44`、无 OTA/固件刷写、无云上传；与正式蓝星向导 RC25、Windows OCR、原厂 HeyCyan、之前所有实验分支及 Release 并存。常连接保留旧版**前台**最多120秒/3张的监听边界：达到界限 `stopWatch()` 只停止轮询，不主动注销 Wi-Fi；但用户退出 App、手动停止、Android `onLost` 或眼镜固件断网仍会释放，**不承诺常连接永不断线**。
- **源码/构建/真实发行**：新隔离分支 `exp/m02s-ap-oemmatch-physical-shutter-v0124-20261115`，构建 HEAD `09ddb0a02448c52c2a38e6066fa63714b3796858`，独立包 `com.vobichngan.bluestar.m02sphysicalkeepap24`，versionCode25/versionName`0.1.24`。GitHub Actions [43673357796](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43673357796) `completed/success`：真实Gradle Android编译、JUnit（包括 AP 指令至SSID应计新广播的回归测试）、静态仅BLE AP白名单、APK v2签名、zipalign、权限/包名核对、Release资源回读通过。Release tag `bluestar-m02s-ap-oemmatch-physical-shutter-v0124-20261115`；APK `BlueStar_M02S_OEMMatch_PhysicalShutter_0.1.24_Android.apk`，94,573字节，SHA256 `8d67361f3b7fc9a65a935c6530bcbdc3ddd5a50392efbdfacdd6b2efe16de276`；浏览器直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-ap-oemmatch-physical-shutter-v0124-20261115/BlueStar_M02S_OEMMatch_PhysicalShutter_0.1.24_Android.apk 。**软件已成功构建≠真机 Wi-Fi 已连上，更不代表常连时实体快门有效。**
- **真机下一步**：关闭原厂及其它控制App，打开 v0.1.24→点「连接M02S并开启Wi-Fi、自动等待照片」一次→一定要看到 `onAvailable`、实际私有网关和 `旧照片基线已建立`，**再仅按眼镜实体快门**，观察有没有快门声及新增高清JPEG。若成功再手按第二张；若卡在SSID/扫描/系统Wi-Fi/HTTP任何环节立即停止，复制匿名完整日志，不要反复发 BLE 和擅自切 P2P。需要分清「未连上 Wi-Fi」与「连上 AP 但固件不允许实体拍照」，不要重复已经成功的高清 GET 旧实验。

---

# 最新｜原厂 M02S 相册自动导入「只替代手动点导入」v0.1.1 安全UI试验（2026-11-15，已发布，待用户真机）

- **用户明确的最终方向**：用户认为既有v0.1.14独立直播偶尔才能接入，虽然真实出现1600×1200画面、与原厂App相同，但离“眼镜拍照→手机自动得到高清图→OCR/TTS”差远。用户在原厂App v1.0.24-c（眼镜固件V1.6.1）观察到：眼镜拍照后原厂相册会出现「发现图片/文件」，必须手动点击「导入」才用原厂稳定的Wi-Fi/AP链把高清图传到手机；要求尝试改造为**拍照后自动导入手机**，不涉及上云。原版App连接更稳定，不能再重做蓝牙/Wi-Fi/RTSP，不能覆盖原厂App。
- **本次已经直接检查真实上传 APK**：用户原始 `com.starglasses.app` APK（当前工作容器 `/mnt/data/files/70465ff3819258a6218e7871da0a23f8.apk`，约284656626字节，7个DEX完整可读）；自己解析DEX method IDs，确认 `classes5.dex` 存在 `Lcom/starglasses/app/ui/activity/album/AlbumFragment;`及 `Ldp;` 方法 `getUnImportNumLiveData`、`getIsImportingLiveData`、`connectP2pWifitoReceivePhoto`、`startDownLoadPhotoByWifi`、`isImportNotFinished`、`stopP2pWifiConnect`；`resources.arsc` 包含原厂UI `发现%1$s个文件`、`导入`、`导入中... (%1$s)`。这支持原厂具有相册候选与手动导入下载流程；单凭方法名不支持揣测事件/线程的真正调用顺序，不能直接把快门通知当文件已可用。
- **为何本轮没有修改原厂APK本体**：原厂是272MiB以上的**闭源编译安装包**，无可验证的Gradle源码和本机安全的APK反编译/重组/签名工具；未经完整解析直接改DEX、重签或替换原厂 App 有破坏其已稳定连接、签名、配置的风险。必须如实表述**这不是已改造完成的原厂APK**。用户允许“先试着修改”，本轮具体用不破坏原厂的UI自动点击辅助实验测试唯一需要改的触发步骤，不能谎称重打包成功。
- **具体实施**：在原仓库新分支 `exp/m02s-oem-autoimport-ui-pilot-20261115`（main先前HEAD `ebf063faf735e50888a69b0daa2ac7e5f5680607`）独立新增 `experiments/m02s-oem-autoimport-helper`，仅做原厂前台UI `AccessibilityService`；`android:packageNames="com.starglasses.app"` 及Java精确包名双重约束。用户须手动在辅助App启用开关和系统无障碍权限，并先观察原厂**相册无待导入**状态，之后在同一原厂前台相册页面出现`发现1个文件`与完全匹配、可点击的`导入`按钮、稳定950ms后，尝试**仅一次**`AccessibilityNodeInfo.ACTION_CLICK`。严禁操作其他App、批量候选（>1）、旧候选、无按钮、其他文本、未知界面。**每次用户手动开启开关整个周期最多一次点击，即使导入失败/候选重现也不自动重试；下一张须关再开开关**，防无限点击。原厂保持 BLE/Wi-Fi/JPEG导入和所有稳定会话，辅助App没有Internet、蓝牙、Wi-Fi、摄像头、存储、悬浮窗权限，也不直接发任何数据或删除命令。
- **潜在风险必须提示**：Android无障碍是高敏感权限，虽本程序只处理原厂前台相册事件、没有其他数据权限，仍需用户明确授权，可随时关闭。原厂自身的导入流程可能在成功下载后发原厂`fileDownloadFinish(CLEAR)`等清理动作，**此辅助App不会发送，但不能阻止/保证原厂不删除眼镜文件**；首次只用不重要的单张照片测试。原厂界面的文字节点若不可被无障碍访问，本试验会安全不点击；不保证后台、熄屏或原厂所有版本可用。试验服务只把状态文字存本机辅助App SharedPreferences，不读取/存储照片、SSID、眼镜原图。
- **版本、安全测试及实际下载**：初始测试版v0.1.0已构建，但最终加入硬性one-shot后以v0.1.1独立新tag发行，**应使用v0.1.1，不要建议用户装早期v0.1.0**。最终分支 HEAD `d4e68c11c7148d22ada2f9d6b776705781b533a0`。GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43628830359 已 `completed/success`：Java JUnit解析策略、AndroidSDK36 Gradle安装包编译、签名v2、zipalign、manifest权限反向安全检查、one-shot/包名约束、Release创建与回读。独立包名`com.vobichngan.bluestar.oemautoimport`、versionCode2、versionName`0.1.1-ui-pilot`。发布tag `bluestar-m02s-oem-autoimport-ui-pilot-v0111-20261115`；浏览器直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-oem-autoimport-ui-pilot-v0111-20261115/BlueStar_M02S_OEM_AutoImport_OneShot_0.1.1.apk ，24259字节，SHA256 `a2fc21ca8cf9d91edecb37bc2ce7be87ccc60c75a3efe2a86d589f0835bb9262`。**没有替换、重签、上传或公开分发用户上传的原厂APK**。
- **用户下一步测试**：保持原厂App不动，手动先导完旧照片；安装新辅助APK，开实验开关，系统无障碍找到「蓝星·仅原厂相册导入」并开启→进入原厂相册（先空候选）→眼镜拍一张不重要的测试图→观察是否自动进入原厂 `导入中` 且手机收到高清JPEG。失败时回到辅助App点「查看匿名状态」提交单行文本；如没有点击，核对原厂相册是否出现 `发现1个文件`、`导入`按钮与系统无障碍能否获取文本。不要宣称后台/自动OCR/一体化已成功。下一步再依据实际结果决定是否尝试在合法授权范围内对原厂APK原始触发方法进行本体patch，**不要在未验证前删原厂核心功能**。
- **仓库纪律**：仅在main追加本段并保留此前所有历史，全部原蓝星向导/原厂HeyCyan/Windows OCR/原v0.1.14实验代码及旧Release均未动。

---

# 最新｜M02S E09 v0.1.14 LibVLC RTSP 真机视频专项实验（2026-11-15，APK已发布，待用户屏幕验收）

- **先看最新真实进展（用户 v0.1.13 日志，02:08～02:09）**：Vivo Android16/M02S-3584 发送 `0x67/0x30`，BLE `0x67`及 `0x25` SSID 回执。**v0.1.13 通过原厂式 `WifiNetworkSpecifier.setSsidPattern(PATTERN_LITERAL)+setBssid(新鲜唯一目标)` 成功 `onAvailable`，收到私有路由网关**，随即 RTSP OPTIONS200=true，DESCRIBE200=true，SDP含video=true，RTSP探测耗时75ms，说明BLE开直播、Vivo Wi-Fi精确连接和RTSP控制服务已全部真机打通。其后系统 `android.media.MediaPlayer` 连续两次错误 `what=1; extra=-2147483648`，*尚没有任何真实视频帧显示/分辨率验收*，不得把SDP或播放成功事件等同OCR可用画面。
- **直接查得原厂播放器差异**：公开反编译 `sctg-development/ai-smart-glasses-e09:reverse/com.eyevue.glassapp/sources/com/eyevue/glassapp/view/live/EyevueTLiveActivity.java` 约230-251行的 `playLive()` 使用 `org.videolan.libvlc.LibVLC` / `org.videolan.libvlc.MediaPlayer` / RTSP `rtsp://192.168.169.1/h264` / `setHWDecoderEnabled(true,false)`、`:rtsp-tcp`、`:network-caching=1000`、`:file-caching=1000`、`:live-caching=100`、`:drop-late-frames=true`、`:skip-frames=true`。而旧实验走Android框架`MediaPlayer.prepareAsync`，可能与设备实时流不兼容，但**错误码不证明具体解码故障**。
- **本次唯一开发改动**：从成功的 `exp/m02s-e09-vivo-oem-ap-matcher-20261115` HEAD `4a8c09989c37a51479c05b1ff7145f14af05a052` 创建隔离分支 `exp/m02s-e09-libvlc-rtsp-player-v0114-20261115`；仅在第⑥部分的`playE09Preview/stopE09Preview`播放与清理路径换成VideoLAN官方Maven Central稳定版 `org.videolan.android:libvlc-all:3.7.7`，`LibVLC+MediaPlayer+IVLCVout SurfaceView`，原厂`rtsp-tcp`及上述全部缓存参数；依旧只通过既有验证的私有 `apNetwork` 做 `bindProcessToNetwork` 并用验证的本地网关 `rtsp://.../h264`。仅用户手动点击播放，无自动重试/自动截图/视频录制。通过播放事件`Opening/Playing/Vout/EncounteredError/EndReached`、轨道宽高记录（若LibVLC有），区分视频轨道元信息/VideoOut通道与实际连续屏幕画面；错误和退出先移除native事件监听、停播、卸载IVLCVout、释放MediaPlayer及LibVLC、解绑进程Wi-Fi，session-token防后续滞留事件。
- **绝对保留**：之前已真机成功的 Vivo `setSsidPattern+fresh-BSSID` 精确Wi-Fi连接（`M02sE09OemMatchPolicy.java`原git blob `810a1c983a32a2365f89224dedde686b5d594479`，CI证明未动）、BLE `0x67/0x30` / `0x25` / `0x95` / AA15低清JPEG及人工导出、RTSP原单独OPTIONS/DESCRIBE文件 `M02sE09RtspProbe.java`原blob `bec97b0f0ca5099e9fcfa4babd466409187c3097`，E09 JPEG assembler blob `3be7b6c1b485d1766a9f0ebb5c65cbce2a0df0df`，原安全白名单、普通相册AP，不触碰正式蓝星向导、Windows OCR、眼镜固件、原厂HeyCyan、老APK，不发删除命令、不新增云上传。
- **独立ARM64发行及许可证**：官方LibVLC 3.7.7经过Maven Central实际解析，APK仅`arm64-v8a`针对用户 vivo X200 Pro；完整LGPL2.1许可在APK `assets/LICENSE-LGPL-2.1.txt`，上游源码及归属 `assets/LIBVLC_THIRD_PARTY_NOTICE.txt`，旧E09 MIT归属保留。独立包名 `com.vobichngan.bluestar.m02se09vlc`，versionCode17/versionName`0.1.14`，不覆盖v0.1.13和以前任何版。
- **构建实证**：独立源码HEAD `86c242ae9c3aa923d44d078d79659bb719e26312`。GitHub Actions [43625754710](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43625754710) **completed/success**：Gradle `:app:testDebugUnitTest :app:assembleDebug`、静态源回归、LibVLC API编译、APK v2签名、zipalign、精确包名和所有权限、原生`arm64-v8a/libvlc.so` & `libvlcjni.so`、许可证资产、GitHub Release资产回读全部通过。之前run 43625726460仅因CI中手写的v0.1.13 policy blob哈希错误，新的静态检查报错（App编译测试已通过）；以实际Git blob `810a1c9...` 修复检查后最终全部成功。
- **真实Release与可复制浏览器APK直链**：tag `bluestar-m02s-e09-libvlc-rtsp-player-v0114-20261115`。APK `BlueStar_M02S_E09_LibVLC_RTSP_0.1.14_arm64.apk` **59,812,149字节**，SHA256 `49532b3da9a0c0f91b5c4811220f4a6a563558065f8c8eef719edf1d6e8808e0`，网址 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-e09-libvlc-rtsp-player-v0114-20261115/BlueStar_M02S_E09_LibVLC_RTSP_0.1.14_arm64.apk 。Release manifest和APK大小已通过真实GitHub回读。
- **真机下一步（只需一次）**：关闭其他眼镜APP→BLE LE1M连接并完成AA14通知订阅→跳过第②「普通AP相册连接」，只点击第⑥「E09 RTSP直播建连」，等待 `onAvailable` 与 RTSP OPTIONS/DESCRIBE200 → 点一次「播放RTSP画面」→回传包含`E09 LibVLC`及`Playing/Vout/视频轨道分辨率/EncounteredError`的日志，同时人工确认是否出现**持续更新的视频画面**、视频是否清晰可看菜单字。仅SDP/Playing/Vout/轨道尺寸不能算帧到达。按「停止直播并安全退出」释放。若仍无视频，先分析RTSP SETUP/PLAY、编解码与视频数据通路，不再触碰已真机成功的Wi-Fi连接代码。CI成功不代表实际视频已通过。
- **仓库纪律**：main仅在旧交接文档前追加本条并保留所有历史，旧分支和已发布实验包不覆盖；用户对修改已授权，本轮已执行，等待真机日志下一步授权。

---

# 最新｜M02S v0.1.13 Vivo原厂式E09直播AP首次连接匹配（2026-11-15，独立APK成功发布，待真机）

- **用户授权和当前目标**：M02S 蓝牙小图320×180实际看不清字，决定优先探索Wi-Fi RTSP高清预览。用户前版在Android16通过 `0x95/0x00` 得到固件功能掩码 `0x000012AB`，**bit0 supportLive=true**；另一次实际直播 `0x67/0x30` BLE写成功/收到0x67回执及0x25 SSID/新鲜AP扫描，但Android单次精准SSID连接报**关联失败code1**，从未到RTSP服务。用户明确「再修改一次」，要求停止猜测式超时/重试循环，优先对照原厂真实代码。
- **新确认源证据**：重新直接读取公开反编译 EyeVue OEM [WifiApConnector.java](https://github.com/sctg-development/ai-smart-glasses-e09/blob/main/reverse/com.eyevue.glassapp/sources/com/eyevue/glassapp/helper/WifiApConnector.java) 的 `connectWithSpecifier`（行340-381）和 `connectApwifi`（行853-902）；公开原厂对非Harmony/ColorOS/Xiaomi设备首次使用 `new WifiNetworkSpecifier.Builder().setWpa2Passphrase(...).setSsidPattern(new PatternMatcher(ssid, 0))`，如果扫描到目标热点就 `setBssid(MacAddress.fromString(targetResult.BSSID))`，这与之前我们的 E09 实验版只用 `setSsid(ssid)`、没有 BSSID pin 的**实际源码差异**清楚对应。该开源反编译属于 `com.eyevue.glassapp`，不能冒称与用户上传的 `com.starglasses.app` APK逐行完全相同；而同源 [EyevueTLiveActivity.java](https://github.com/sctg-development/ai-smart-glasses-e09/blob/main/reverse/com.eyevue.glassapp/sources/com/eyevue/glassapp/view/live/EyevueTLiveActivity.java) 确认T型直播使用 `startLive(LIVE_TYPE_AP)`、`WifiApConnector.connectApwifi`、LibVLC RTSP。**原厂成功取图并非该直播实验已成功的证据**。
- **本轮唯一连接策略变化**：从 `exp/m02s-e09-live-capability-readonly-20261115` HEAD `7be89dc142ffe37ad47bcd226ce8c548c9fcf0e3` 新建独立分支 `exp/m02s-e09-vivo-oem-ap-matcher-20261115`，只在 `e09LiveRequested && Build.BRAND.equalsIgnoreCase("vivo")` 的首次且**唯一一次**Android`WifiNetworkSpecifier`申请，使用 `setSsidPattern(new PatternMatcher(verifiedBleSsid, PatternMatcher.PATTERN_LITERAL))` + `setBssid(MacAddress.fromString(唯一、新鲜、稳定1.8秒的扫描BSSID))` + 原厂WPA2；先重新验证新扫描、BSSID和稳定时间，失败直接报`TARGET_NOT_READY`不误连、不拿旧热点代替。不改任何BLE命令、扫描时序/等待/重试次数、网络回调、网关RTSP探测/播放器。原普通相册AP及非Vivo手机仍是旧SSID-only；第②按钮改叫「普通AP相册连接（非直播）」防误操作。本版不是v0.1.23的“SSID-only失败后可选第二次BSSID-pin重试”：本版**第一次**就用公开OEM LiteralPattern+BSSID且不再重试。
- **安全与回归**：保留原E09 AA15蓝牙图片/原样JPEG导出、0x95只读支持位、0x67/0x30直播指令、原RTSP OPTIONS/DESCRIBE与MediaPlayer，未更改 `M02sE09PhotoAssembler.java` Git blob `3be7b6c1b485d1766a9f0ebb5c65cbce2a0df0df` 和 `M02sE09RtspProbe.java` blob `bec97b0f0ca5099e9fcfa4babd466409187c3097`。新增纯Java策略`M02sE09OemMatchPolicy.java`及JUnit验证Vivo首次直播/普通相册不变/非Vivo不变/扫描旧缓存、BSSID变化、同名多热点及稳定不足时拒绝。无需系统额外权限，无删照片、OTA、眼镜固件修改、云上传或后台无限连接。
- **真实发布状态**：源码HEAD `4a8c09989c37a51479c05b1ff7145f14af05a052`。GitHub Actions [43624603570](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43624603570) 已`completed/success`：JUnit + Gradle Android SDK36 APK、签名、zipalign、权限、v16版号、静态验证E09只改OEM匹配及旧BLE/RTSP不变、Release发布及资产回读通过。新包 `com.vobichngan.bluestar.m02se09oemrtsp` / versionName`0.1.13`/versionCode16，tag `bluestar-m02s-e09-vivo-oem-ap-matcher-20261115`；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-e09-vivo-oem-ap-matcher-20261115/BlueStar_M02S_E09_VivoOEM_RTSP_0.1.13_Android.apk ，195497字节，SHA256 `5b9646458fbc2a2f0b28c160ae009fc4a482e02b5e73f37d3ea0e8957dd3247a`。全新独立安装，不覆盖v0.1.12及后续、正式蓝星向导、Windows OCR、原厂HeyCyan。
- **下一步只验证一次**：安装v0.1.13→退出官方HeyCyan和旧实验软件→连接BLE LE1M、AA14/AA15通知status0→**跳过第②普通AP相册连接**，直接点第⑥「E09 RTSP直播建连」→检查 `E09直播原厂式AP：Vivo首次请求采用SSID字面量PatternMatcher + 本轮唯一新鲜BSSID` → Android`onAvailable`还是关联失败code1。如果连上且网关探测出现再手动「播放RTSP画面」判断视频帧。如果仍关联失败，不继续改扫描等待或发自动重试；说明此公开OEM匹配策略未修复此机关联问题，下一步应对照系统Wi-Fi直接连接/硬件广播安全能力，而不是声称直播已成功。CI通过不代表真机通过。
- **纪律**：main只追加本段交接，不覆盖老记录；各分支和旧Release均保留；严禁自行触碰正式OCR或固件。无需重复成功蓝牙直传、AP导图及0x95直播支持测试。

---

# 最新｜M02S E09/原厂直播能力支持位只读查询 v0.1.12-livecheck（2026-11-15，已构建发布，待真机）

- **需求/实测锚点**：用户2026-11-15运行前版 v0.1.12-export，01:41:49手动发送E09 `APP_LIVE 0x67/0x30`，AA13 GATT写status0，01:41:50 收到 AA14 `0x67`及有效 `0x25` SSID，01:41:51看到本次扫描的新鲜AP广播，01:41:53 Android精确SSID+WPA2请求，01:41:56本地连接失败原因=**association关联失败 code 1**，01:41:57 `onUnavailable`，未建立 Network/网关，**RTSP服务/帧/MediaPlayer尚无实测**。本轮不能再归咎于用户先点相册AP；BLE和SSID链可用，但AP能否接受关联仍待判定。用户明确授权开始核查原厂直播支持和启动流程。
- **原厂/开源源证据**：重新检查用户上传原厂 `com.starglasses.app` v1.0.24-c APK DEX，存在 `APP_LIVE`、`GET_SUPPORT_FUNCTION`、`getSupportFunction`、`SupportFunctionBean`、`StarLiveActivity`、`StarTLiveActivity`、`rtsp://192.168.169.1/h264`，**原厂包含直播代码但不证明用户眼镜TK8有直播能力**。公开逆向原厂 `sctg-development/ai-smart-glasses-e09:reverse/com.eyevue.glassapp/.../SendCommandViaBle.java` 方法 `getSupportFunction()` 实际发送 `configSendCommand(149,0)`；同仓库 `SupportFunctionBean.java/DeviceCapability.java` 指明功能位**bit0 = supportLive**、四字节大端。E09开源Swift `BleProbe.validateSupportFunction` 同样用 `CMD_GET_SUPPORT_FUNCTION=0x95` payload `0x00` 查询5秒。原厂选择包含T的项目走AP，用户先前实测项目号 `TK8` 属此类；这只证明选路，不是直播支持证明。Android官方`WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_ASSOCIATION=1`，**不等于密码认证失败（2）或IP阶段失败（3）**。
- **唯一新增实验**：以已验证 v0.1.12-export 分支HEAD `3709be561d13c483b2acd33be4df8b26c34976ba` 独立创建 `exp/m02s-e09-live-capability-readonly-20261115`。仅新增用户点击「检测眼镜直播功能支持位（只读 BLE 0x95）」后，蓝牙已连接且AA14 CCCD成功、无相册/拍照/直播进行时，允许**一次精确白名单** `AB 55 00 03 95 00 95`，最多5秒，绝不自动重试；严格验AC55帧校验和、恰好4字节大端掩码，返回 `bit0 supportLive=true/false` 或无效/超时显示**未知**。即使 true也仅表示固件宣告具备能力，不代表RTSP播放成功。原E09 BLE AA15/JPEG组包和原RTSP OPTIONS/DESCRIBE/MediaPlayer代码完全保留，原JPEG组包git blob `3be7b6c1b485d1766a9f0ebb5c65cbce2a0df0df`、RTSP探测blob `bec97b0f0ca5099e9fcfa4babd466409187c3097`均通过CI校验未动；导出JPEG原字节功能保留。不增加其他BLE模式、OTA、照片删除、自动Wi-Fi重连或外网传输。
- **源码/CI/Release真值**：独立实验分支构建HEAD `7be89dc142ffe37ad47bcd226ce8c548c9fcf0e3`，Android包 `com.vobichngan.bluestar.m02se09livechecklab` 版本 `0.1.12-livecheck`。GitHub Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43623287196 **completed/success**，Gradle JUnit增加0x95精确白名单/CRC/大小端/无效回应/无照片删除的测试，Android APK v2签名、zipalign、权限、版号、原BLE/RTSP未改静态检查、Release资产回读全过。Tag `bluestar-m02s-e09-live-capability-readonly-20261115` APK `BlueStar_M02S_E09_LiveCapability_ReadOnly_Android.apk` **193065字节**，SHA256 `f3d6290c44c9852247c442a226a0cb6bafbcb757409e838828bcf22e7bd2a489`；浏览器直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-e09-live-capability-readonly-20261115/BlueStar_M02S_E09_LiveCapability_ReadOnly_Android.apk 。独立包不覆盖已成功v0.1.12、导出版、正式蓝星向导RC25、Windows OCR、原厂HeyCyan。
- **最短下一次真机测试**：退出官方HeyCyan和其它眼镜控制程序→安装 v0.1.12-livecheck →蓝牙扫描并连接LE1M，开启监听等AA14和AA15 CCCD status=0→**不要点击AP/P2P热点或RTSP**，只点第⑥「检测眼镜直播功能支持位（只读 BLE 0x95）」一次→回传`E09直播支持查询：设备能力掩码=0x...；bit0 supportLive=...`或超时/无效日志。若bit0=0，固件未报告直播支持，不盲改Wi-Fi；若bit0=1，再聚焦AP实际关联与RTSP服务分辨率，不重做BLE已成功实验。测试日志不含SSID/密码/图片。
- **仓库纪律**：main仅前置本条并保留旧历史，原实验分支/Release全部保留；任何新的直播/ Wi-Fi 连接逻辑修改都要有新增证据，不得以CI成功充当真机能力确认。

---

# 最新｜M02S E09 蓝牙直传 JPEG 手动导出相册（2026-11-15，已发布，待用户真机查看）

- **用户要求**：回到2026-11-14约20点的 GitHub E09 开源照片 + RTSP 实验，以当晚 v0.1.12 真实蓝牙解码成功为基线，增加手动导出图片到手机图库，用于放大判断模糊程度；不再重写 Wi-Fi 相册连接。
- **已证实基线**：`exp/m02s-e09-jpeg-tail-fix-v0112-20261114` HEAD `b7f42f8ce8887c7417426f1b8a1ad6c8e584cd66`；M02S 真机 AA15 BLE JPEG 320×180，AA15实收8988字节/21分片、图像解码后JPEG约8987字节，从0x22/0x31指令至解码3061ms。**RTSP仅有实验入口，M02S真实画面未验证**。
- **新隔离分支**：`exp/m02s-e09-ble-jpeg-gallery-export-20261115`；独立APK成功构建源码HEAD `3709be561d13c483b2acd33be4df8b26c34976ba`。只改实验版 MainActivity.java、app/build.gradle、AndroidManifest 标签和README，新增独立 workflow；原版 AA15 接收器文件的 Git blob `3be7b6c1b485d1766a9f0ebb5c65cbce2a0df0df` 未改变，0x22/0x31、RTSP 0x67/0x30、播放和BLE白名单均保留。
- **新按钮**：第⑥ E09区「保存刚收到的蓝牙JPEG到手机相册（原始画质）」，只在真实E09 JPEG经Bitmap解码成功后保留*单独*内存候选。必须用户点击，才通过 Android 10+ MediaStore 把 JPEG 原样字节写入 `Pictures/BlueStarE09`，不使用Bitmap.compress、不缩放、不锐化、不上传云端。若v0.1.12原接收器已剪去JPEG EOI后的非图像尾部字节，保持原图像像素不变。无有效E09图不允许保存；新E09拍照、清空预览、HTTP原图覆盖时清空候选，防止旧图混淆；IS_PENDING=1/0保证不会呈现半文件，失败时删除本次未完成的相册新文件，不删除眼镜照片。
- **验证与发布**：[Android Actions #43621386701](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43621386701) `completed/success`：Gradle JUnit、Android SDK36 APK、AA15文件Git blob一致性、原BLE/RTSP入口、手动保存和不压缩静态检查、APK v2签名、zipalign、权限、Release创建和资产回读。此前run 43621351804仅因新加的CI脚本用 shallow checkout 无法git show基线提交而失败，修改为直接校验已知blob后成功。
- **新正式可复制浏览器链接**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-e09-gallery-export-20261115/BlueStar_M02S_E09_BLE_JPEG_Export_Android.apk ；189377字节，SHA256 `f68137b3404ad8f83f9b61d509142b594cf46816b33a75e97aa8be5ee7ecf857`。独立包名 `com.vobichngan.bluestar.m02se09exportlab`，versionName `0.1.12-export`；没有覆盖原版v0.1.12/官方HeyCyan/正式RC25/Windows OCR。
- **待用户实际检查**：在vivo Android16连接BLE、订阅AA14+AA15，E09直传成功解码后手动点保存，在手机相册BlueStarE09放大看文字。确认按需导出正常及实际清晰度；不宣称320×180能识别小字，不宣称RTSP已跑通，更不等同于已实现AI/OCR/TTS。

---

# 最新｜M02S v0.1.23 新鲜热点校验 + 单次有条件 Wi-Fi 恢复（2026-11-15，CI/Release 成功，待真机）

- **独立实验，不改旧产品：** 基于已成功发布的 v0.1.22 `exp/m02s-manual-import-compare-v0122-20261115` 源码HEAD `1bceb607359e455ea53e29e6ec7b31b240652fc6`，新增分支 `exp/m02s-oem-fresh-ap-bounded-recovery-v0123-20261115`。只修改实验 `experiments/m02s-wifi-only-lab` 的 Android Wi-Fi 连接路径、UI版本/说明、增加纯Java有界恢复策略及单测、专用CI；未触碰蓝星向导正式版、Windows OCR、官方眼镜App或旧v0.1.22包。
- **精确保留已验证的媒体链：** 实体快门与 AA14 `0x45` 仅提供拍照结束候选；**用户单独点击导入**后仅一次精确 AA13 `0x39/0x30` 开AP、AA14 `0x25` BLE SSID，沿用原WPA2、私有IPv4网关、原厂HTTP200 JSON `/app/getfilelist`、两次最新候选稳定比对和原始JPEG只读GET到RAM，另保留被动可选 `isImport`、手动只读设备 `0x64` 与匿名日志。不使用 `0x44` 清除/结束、`0x22` 快门、P2P/OTA、删除、相册写入或云端上传。
- **连接策略唯一实验变量：** 收到本轮准确SSID后，若Android已有完全匹配且有私有网关的Wi-Fi Network则先复用并用原厂HTTP验真；否则Android仅主动扫描一次，必须发现**BLE开AP后时间戳新鲜且目标SSID唯一BSSID**的热点，经过1.8秒观察，再用原有SSID+WPA2请求第一次Android `WifiNetworkSpecifier`；如果 `onUnavailable`，重新检查已有Network，没有则**至多再扫描一次**，仅当新广播确实在失败后产生、仍为唯一BSSID并能改变为BSSID-pinned请求时，才允许总次数不超过2的第二次Android请求。每轮扫描最多7秒，Android请求仍每次25秒，手动导入总体上限80秒。无新鲜广播/扫描受系统限流只报告**未确认**，不把旧缓存/Android onUnavailable当眼镜损坏证明；不无限重复BLE/Android请求。
- **原厂源码证据边界：** `FerSaiyan/Alternative-HeyCyan-App-and-SDK/android/DATA_SYNC_COMMAND_AUDIT_20260909.md` 可核对媒体AP命令；`EyevueWifiTransport.kt` 可核对Android精准SSID/WPA2连接方式。**新鲜扫描与BSSID单次重试是本实验假设，不是已证实的原厂逐行算法**，也不能宣称当前手机安装的原厂App与公开反编译版本完全一致。仍须与v0.1.22同机A/B测试。
- **已完成CI和真实发行：** 构建源码HEAD `840b127d218ccaf0aa9071f780e5a00641df6d38`；[Actions 43619585072](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43619585072) `completed/success`，JUnit/Gradle Android APK、精确BLE白名单、单次手动AP入口、扫描与第二次请求静态断言、APK v2签名、zipalign、版本/权限通过。独立包名 `com.vobichngan.bluestar.m02sfreshaplab`、versionCode24、versionName0.1.23。公开 [Release](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-m02s-fresh-ap-bounded-recovery-v0123-20261115)；[APK直链](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-fresh-ap-bounded-recovery-v0123-20261115/BlueStar_M02S_FreshAP_BoundedRecovery_0.1.23_Android.apk)，真实大小109497 bytes、SHA256 `c9eba11ba0f5f994fb7b1781c23f06a61b3d252cf07183151d808758c711e965`；未覆盖旧Release。
- **待真人验收：** 先拍实体快门→再点击导入，观察连续至多5轮中Android系统扫描是否返回新鲜AP、首次请求是否onAvailable、第二轮是否真的满足恢复策略、HTTP200和原图是否出现。系统可能限流扫描，导致本版比v0.1.22更容易停在“无法验证广播”，这不等于眼镜硬件差；若失败立即停止并复制匿名完整诊断，不连续猛试。不把CI成功等同于真机5轮成功。
- **接手纪律：** main只更新本交接文字、旧历史保持原样，不自动再次构建或修改正式版。下次先查当前实际HEAD和Release，优先分析用户提供的v0.1.23真机日志。

---

# 最新｜M02S v0.1.22 原厂式「先实体拍照→再手动导入」对照实验（2026-11-15，已发布待真机）

- **用户新证据与授权**：用户多次实测眼镜原厂App**稳定重复导入**：按实体快门，原厂相册先显示一个文件，**手动点「导入」后才开启眼镜网络、下载成功**，没有第二次常失效的问题。v0.1.21曾在真实M02S读取只读设备项目号`TK8`、AA14`0x45`可选`isImport`确有0→1→0转变；曾有一次连续取两张成功，但后来同样操作第二次失败，不能归咎于等待20秒或蓝牙重连。用户授权**采用原厂用户动作顺序，先实验手动导入、随后若稳定再考虑恢复自动化**，不得继续凭猜测改连接超时或加未知协议。
- **保留协议真实依据/边界**：公开原厂Eyevue逆向审计`FerSaiyan/Alternative-HeyCyan-App-and-SDK/android/DATA_SYNC_COMMAND_AUDIT_20260909.md`指出媒体导入`connectP2pWifitoReceivePhoto()` AP模式使用`0x39/0x30`，与本机v0.1.18～21已真机成功的AA13开AP指令一致；它不能证明我们已复制原厂完整连接/结束状态机。原厂相册拍照后能看到文件是**用户实际描述**，独立实验App**在连上眼镜HTTP之前拿不到OEM照片列表**，因此v0.1.22只显示本地“待导入候选”，不伪称已看到眼镜相册或保证照片已写盘。
- **具体修改**：从已发布v0.1.21分支HEAD`b32b4982581cf1190b8e9e7a741475a594f3d928`派生独立分支`exp/m02s-manual-import-compare-v0122-20261115`，仅改原`experiments/m02s-wifi-only-lab`内`MainActivity.java`、Gradle、Manifest、README和新独立GitHub Actions工作流，不动BLE底层/HTTP/OCR/TTS/正式蓝星向导/Windows OCR/既有v0.1.12～0.1.21分支与发布。已移除原`photoActionEnded()` 250ms后`startPhotoTransfer(true)`自动开AP任务、删除`PHOTO_DONE_SETTLE_MS`与`pendingPhotoAction`。现在0x45`1→0`只设本地`photoReadyForManualImport=true`、显示待导入，不发蓝牙/WLAN；缺失结束信号时，**用户亲自确认确实已实体拍照后**点「已拍照但没收到结束通知？手动标记待导入」，仅改RAM UI标记。新增「导入高清原图（手动连接眼镜）」按钮，必须BLE已待机、有待导入标记、无正在拍照/下载时才能点，**是`MainActivity.startPhotoTransfer()`唯一调用入口**；只发送原白名单`39/30`一次，AA14 0x25准确SSID→单次Android `WifiNetworkSpecifier`→真实私有网关/原厂HTTP200 JSON→2次稳定目录→安全JPEG原图GET到RAM。下载成功/失败释放手机Network，保留BLE待机。下一张需新实体快门结束及用户再点击导入。还保留v0.1.21`isImport`、手动只读0x64查询、旧扫描缓存年龄和网络回调日志；增加第N次手动导入及“拍照结束候选→用户手动点击”的时间。速度统计新口径是**手动点导入→解码**，不可与老版拍照完成→解码直接比较。
- **绝对安全限制**：此实验仅允许已验证`AB 55 00 03 39 30 69` AP及用户手动只读`AB 55 00 02 64 64`项目号；**不存在0x44任何结束/清除指令**（v0.1.20的不删除也不沿用）、0x22遥控快门、P2P切换、OTA、文件删除、云端上传、系统相册写入、额外Wi-Fi扫描或无限重连；JPEG只存RAM。点击「准备蓝牙待机」若已连接不会实际重连，UI明确提示。真实`isImport=0`不是AP已完全复位的保证。**本版本只分离触发时机，不保证解决二次失败；下一步连续5次独立导入检验**。
- **构建/核验**：独立包名`com.vobichngan.bluestar.m02smanualimportlab`，versionCode23/versionName0.1.22；CI`.github/workflows/m02s-manual-import-compare-v0122.yml`新增静态防自动导入断言，严格验证唯一手动AP入口及原BLE指令白名单，继续JUnit、Gradle编译、v2签名、zipalign、权限与包名检查。首次CI [43616557694](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43616557694) **失败仅因YAML将期望versionCode写成22，实际APK正确为23**；修正唯一检查后重跑 [43616716344](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43616716344) **completed/success**；Release tag`bluestar-m02s-manual-import-compare-v0122-20261115`，源HEAD`1bceb607359e455ea53e29e6ec7b31b240652fc6`，APK`BlueStar_M02S_Manual_Import_Compare_0.1.22_Android.apk`，105029字节，SHA256`ce7bc01a4271218ff9994c93ed4f6387194b682a24a36841d3a1d82907eed0dd`，已查GitHub Release API真实文件URL：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-manual-import-compare-v0122-20261115/BlueStar_M02S_Manual_Import_Compare_0.1.22_Android.apk 。GitHub CI成功**不等于**实物连续五次稳定，待真机确认。
- **真机步骤**：安装v0.1.22独立APK，准备BLE待机→按实体快门→确认收到待导入提示，此时不应有0x39指令或Wi-Fi连接→手动点「导入高清原图」→看到真实JPEG或失败。成功后重复，目标连续5张；若一次失败，停止并复制完整匿名日志，重点`isImport`、是否在按导入前已开AP、安卓网络申请结果/缓存年龄、每轮JPEG尺寸及等待时间。不盲重试、不改原仓、不给假成果。
- **仓库纪律**：这段只加在main`PROJECT_HANDOFF.md`顶部保留全部原历史；之后接手应先读文档、核对真实HEAD和Release再行动。未经用户允许不修改主软件/其他项目。

---

# 最新｜M02S v0.1.21 只读状态诊断已发布（2026-11-15；待真机验证）

- **原因与现状**：v0.1.18/v0.1.20第一张实测可正常3200×2400高清原图下载，最好一次取图触发→解码4584ms；然而第二张Android`WifiNetworkSpecifier`常返回`onUnavailable`，v0.1.20试用E09不删除结束帧`44/30/01`（用户开启）GATT写回执成功后第二张仍失败；用户直接在Android WLAN设置中尝试连接眼镜热点`DH-Twl-725B38`也显示拒绝请求，不能仅归咎于本App API。眼镜是否真正复位、AP是否当前可关联、设备固件状态与系统路由都仍未证实。用户授权**先增加只读证据，不继续盲改超时、强制重连或Wi-Fi Direct模式**。
- **源码依据已核实**：E09 MIT公共逆向 `sctg-development/ai-smart-glasses-e09-reverse/Sources/e09_reverse/AdditionalValidations.swift` 的 `ACTION_SYNC/0x45` 含前9状态位及可选第10位`isImport`；**若第10位不存在，不可当作isImport=0**。另核查 `FerSaiyan/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/devices/eyevue/EyevueProtocol.kt`：**同AA12/AA13/AA14协议**，存在只读`GET_CUSTOMER=0x64`项目号查询，0x64回执前4字节为项目、后4字节为客户标识；只解前4字节。其 `EyevueMediaSync.kt/EyevueMediaProfile.fromProject` 将SK和T类型映射AP、其他默认P2P，**只是开源参考，不能证明当前M02S支持Wi-Fi Direct，更不能据此改变传输路径**；其`EyevueWifiTransport.kt` AP也使用`WifiNetworkSpecifier`，不构成API用错的证据。
- **实现与安全**：从**无0x44功能的已发布成功v0.1.19分支**HEAD`d6befdb9343d4e55e8f34d85904b1769c94f8805`新建`exp/m02s-passive-state-diagnostics-v0121-20261115`。仅修改该实验目录中的 `M02sApWire.java/M02sApStarter.java/MainActivity.java/M02sApWireTest.java`、gradle/manifest/README及独立工作流。BLE严格校验AA14`0x45`的包头、长度、校验和，**被动解析可选`isImport`第10位**；只有首次检测到0/1或状态变化才记一次，缺失只记录未知，不伪称眼镜已退出导入模式。BLE待机且未拍照/联网时，可**由用户主动点击一次只读设备项目查询**，精确白名单帧`AB 55 00 02 64 64`，最多3秒等待；仅解析安全ASCII项目码，客户号/SSID/BLE地址/密码都不记日志；超时未知，不重试。JUnit检查0x45第10位长度/checksum/缺失、0x64准确白名单与回执解析并拒绝`0x22`/`0x44`任意写变体。当前允许输出的指令**只有已真机证实的AP`AB 55 00 03 39 30 69`和主动用户触发的只读项目`AB 55 00 02 64 64`**，没有v0.1.20中的`44/30/01`实验，不删眼镜照片、不写相册、不上传云端、也不OTA或接OCR/TTS。
- **Wi-Fi只读观察**：在开AP前、收到准确0x25 SSID、精准Android请求前、onAvailable/onUnavailable、已验证网关及释放手机Network前/后的阶段，取**现有**`ConnectivityManager.getAllNetworks`Wi-Fi数量，并只读取**系统现存**`WifiManager.getScanResults`缓存中与BLE校验SSID一致记录的最小年龄（毫秒），不触发`startScan()`、不输出SSID/BSSID。**扫描缓存可能过期，不能证明热点此刻可连接，onUnavailable本身也不提供失败原因**。每次取图记录本进程第N轮以便对照第1/2张。保留v0.1.19旧NetworkCallback归属防护和分阶段耗时。
- **完全保留已成功拍照与高清下载**：AA14`0x45 0→1→0`拍照结束后250ms单次发`39/30`，AA14`0x25` SSID且AA13写成功后一次精准WPA2 WiFiNetworkSpecifier请求，不加入12秒扫描、2.2秒强制开AP或盲重试；确认Android Network+私有网关+原厂`GET /app/getfilelist` HTTP200/JSON后两次间隔350ms读取稳定最新候选，原图到RAM解码，所有图片安全上限保留。当前最新文件仍不能严格认定为刚拍摄的这一张，真机效果待验证。
- **构建及发布核验**：工作流`.github/workflows/m02s-passive-state-diagnostics-v0121.yml` JUnit/Gradle/签名/静态禁止新清理指令/包名检查已成功。Actions [43614099674](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43614099674) `completed/success`，源码HEAD`b32b4982581cf1190b8e9e7a741475a594f3d928`，独立包名`com.vobichngan.bluestar.m02sstatereadonlylab`、versionCode22/versionName0.1.21。Release tag`bluestar-m02s-passive-state-diagnostics-v0121-20261115`，APK`BlueStar_M02S_Passive_State_Diagnostics_0.1.21_Android.apk`大小103421 bytes，SHA256`f33f330ccf842f8cfaf3fdd760ce1d30a9932f51ec9b614c8c97340bd54fcba6`，浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-passive-state-diagnostics-v0121-20261115/BlueStar_M02S_Passive_State_Diagnostics_0.1.21_Android.apk。旧v0.1.12～v0.1.20 Release、正式蓝星向导、Windows OCR和HeyCyan均没有被修改。CI成功不等于已真机验证0x64回执或第10位isImport存在。
- **下一步最短真机**：安装独立v0.1.21，关其它控制App、准备BLE待机；可先主动点一次「只读查询设备项目号」（不一定回复；不影响拍照）；实体快门第1张等待完整原图，第2张再拍一次（失败后不要重复重试），复制**整段匿名日志**重点是0x45的`isImport`是否提供与变化、项目只读码、两次`Wi-Fi只读快照`缓存年龄、精确Android请求到onAvailable/onUnavailable的毫秒耗时。不要只发顶部失败截图。如果isImport缺失，报告“未知”，绝不将其作为眼镜处于闲置的证据。
- **治理纪律**：本次只追加本节到main`PROJECT_HANDOFF.md`顶部并保留v0.1.20及所有旧历史，每次接手先读main文档并核实真实HEAD/Actions/Release，未经用户授权不动旧实验分支及正式产品。

---

# 最新｜M02S v0.1.20 GitHub原厂协议“不删除退出”独立对照实验（2026-11-14，已发布，待真机）

- **用户授权与最新诊断**：用户指出之前只追着Android`WifiNetworkSpecifier.onUnavailable`打补丁太钻牛角尖，要求核查GitHub现有智能眼镜项目并修改。旧v0.1.18已真机成功取3200×2400高清原图（触发→解码6188ms）；v0.1.19用户两轮都能在开AP后511/505ms收到真实AA14 `0x25` SSID，但Android`onUnavailable`发生在6761/4182ms后，**均未建立Network，原因未证实**。因此重点转到**下载后眼镜是否真正退出传输模式**：旧代码仅释放**手机**`NetworkCallback`并保留BLE，未向眼镜发送结束指令。
- **开源证据与安全边界**：重新阅读MIT `sctg-development/ai-smart-glasses-e09-reverse/Sources/e09_reverse/{Constants.swift,WifiMediaManager.swift}`：反编译原厂`iec.java:stopP2pWifiConnect()`含`0x44/0x30/0x01` **NOT_CLEAR** 结束会话，**`0x44/0x30/0x00` CLEAR会删除眼镜媒体，必须禁止**。当前M02S实物未实测NOT_CLEAR含义，故新实验开关**默认关闭、每次APP进程启动不记忆状态**，只能用户明确勾选后发送完整、精确白名单帧`AB 55 00 04 44 30 01 75`。严格JUnit检验只允许该帧且拒绝`44 30 00 74`删除版本、`44 31`等变种；`M02sApWire.authorized()`继续只允许既有`39/30`开AP，不允许新帧越权。**不能仅凭GATT写状态0宣称固件已退出、没有照片被处理或后续连接已恢复**。
- **实现**：从v0.1.19已发布HEAD`d6befdb9343d4e55e8f34d85904b1769c94f8805`创建唯一**隔离分支**`exp/m02s-optin-noclear-exit-v0120-20261114`，不创建新仓库；只改`experiments/m02s-wifi-only-lab`和独立CI。新UI勾选框「启用实验：每次高清取图成功后发送一次不删除结束帧」，默认不选；**只有一张真实高清JPEG通过HTTP200/签名/尺寸检查并解码成功**时，且用户已勾选，才BLE AA13发送**单次**`44/30/01`，等待GATT status0或3秒超时，之后释放手机Network，BLE保留待机。不勾选时跟v0.1.19操作完全一致。另新增需要勾选的**手动「尝试退出传输模式」按钮**，用于前次Wi-Fi连接失败后用户重新准备BLE待机、在未开始拍照/下载时手动尝试一次该原厂不删除退出信号；未连BLE/正在传输/正在写入时拒绝。不在失败时盲目自动重复发送，不发未知固件指令。
- **保留成功基线**：原实体快门AA14 `0x45 0→1→0`、250ms后一次开AP、AA14 `0x25`准确SSID、5秒SSID回执上限、Android单次`WifiNetworkSpecifier`精准连接、真实`onAvailable`/私有网关/原厂HTTP200 JSON、两次稳定照片列表、原始JPEG只读GET到RAM、32MB/文件名和图片校验、旧WiFi回调归属及毫秒计时全保留。禁止12秒WiFi扫描、2.2秒强制开AP、无休止轮询、`0x22`遥控拍照、潜在删除`0x44/0x30/0x00`、OTA、云端上传、相册写入和眼镜照片删除。对GitHub开源 `FerSaiyan/Alternative-HeyCyan-App-and-SDK`（CyanBridge/HeyCyan Wi-Fi Direct，`glassesControl 02 01 04/09`）及`pepebot-space/nisaetus`做过真实源码对照，但**它们是另一套BLE协议，没有实物兼容依据，绝未复制这些控制指令**。
- **已发布并核查**：独立`applicationId com.vobichngan.bluestar.m02snoclearexitlab`，versionCode21/versionName0.1.20；CI `.github/workflows/m02s-noclear-exit-v0120.yml` 添加严格白名单/删除变体JUnit/开关默认关/无拍照兜底/无Wi-Fi扫描/Gradle/签名检查。Actions [43611434596](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43611434596) **completed/success**，构建HEAD `667c34420ecccbf98d677236c968c57e1132054a`。Release tag `bluestar-m02s-optin-noclear-exit-v0120-20261114`，真实已核验APK `BlueStar_M02S_OptIn_NoClear_Exit_0.1.20_Android.apk`，104629字节，SHA256 `95a607a9ab365bba58354ab6d7b4b76bc044a284bb7d9dd69722d3cf9086f483`。浏览器直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-optin-noclear-exit-v0120-20261114/BlueStar_M02S_OptIn_NoClear_Exit_0.1.20_Android.apk 。v0.1.12～v0.1.19旧实验APK/Release、正式蓝星向导RC25、Windows OCR、原厂HeyCyan均未修改。
- **最短真机对照**：安装v0.1.20保持开关关闭可以先验证旧流程；如遇到SSID已响应但Android连接失败，**不要连续重发Wi-Fi申请**；重新「准备蓝牙待机」，主动勾选实验并**手动点一次不删除退出**（观察GATT写回执），然后尝试正常实体拍照、看下次AA14 SSID及Android`onAvailable`是否恢复；若恢复再开勾选连续拍两张验证下载后自动退出效果。特别注意**本机协议未验证**，重要照片建议预先备份，任何照片异常/拍照失效就停止实验并报告，不盲重试。CI成功不等于协议行为已真机通过；接手后优先检查实测日志再决定下一步。
- **仓库纪律**：此段只追加到main`PROJECT_HANDOFF.md`顶部并保留全部旧历史，回读确认；后续接手必须先读取该文件，核对实际分支HEAD/Actions/Release，未经用户授权不动正式软件/其他项目。

---

# 最新｜M02S v0.1.19 多张连续取图时 Android Wi-Fi 回调归属修复与精准耗时（2026-11-14，已构建发布，待真机）

- **最新用户现象**：v0.1.18已实测至少一次成功（用户23:08:05发AP，23:08:09 Android onAvailable，触发取图→Wi-Fi真实网关4648ms，23:08:11读取3200×2400 JPEG331332字节，HTTP GET955ms，取图→JPEG解码6188ms）。用户之后v0.1.18截图：**“成功下载2张；最近原图3200×2400”仍保留，但状态变成“取图失败：Android未能连接眼镜AP，拒绝反复申请或猜测热点。BLE已断开”**。此前版本也出现过同样“不能反复连接”提示。截图**没有该失败轮的完整日志**；“拒绝反复申请或猜测热点”只是旧源码onUnavailable自己写的失败消息，并非Android证明被连接次数限流。用户要求修改整体方案/连接稳定性，避免旧方案复杂化、避免暴力重试，保留成功高清取图。
- **源码已确认的一个独立竞态漏洞**：v0.1.18中 `M02sApStarter` 的`ConnectivityManager.NetworkCallback.onLost/onAvailable/onLinkPropertiesChanged` 已检查 `networkCallback == this`，但 `onUnavailable` **缺少当前NetworkCallback归属检查**，直接调用`fail(token, ...)`使BLE断开；每张下载后通过`releasePhotoNetwork()`释放旧`WifiNetworkSpecifier`请求却保留BLE`generation`，若此前已注销的请求在新会话里递送排队的onUnavailable，就存在旧失败误终止新会话的可能。**该漏洞确实存在，但还未证明用户截图的实际故障就是由它引起**。Android官方API文档：`onUnavailable`仅表示requestNetwork没有在指定时间找到网络或本次请求无法满足，不能据此判断系统限制连续连接。
- **仅改连接层，不重造已成功路径**：由已发布v0.1.18成功HEAD `de927a30174d542484f73e1d06e2ffc268bc32a7` 新建**独立分支** `exp/m02s-network-callback-guard-v0119-20261114`。修改该实验的`M02sApStarter.java`，为`onUnavailable()`补`if (!valid(token) || networkCallback != this) return;`，`releasePhotoNetwork()`和`stop()`中**先置空持有的回调再调用`unregisterNetworkCallback`**。引入RAM局部毫秒时间：从BLE开AP命令到`AA14 0x25`校验SSID、从准确SSID的Android requestNetwork到`onAvailable`、或到`onUnavailable`；日志不写SSID/密码/BLE MAC/照片文件名。将原误导性“拒绝反复申请”替换为“本次Android精准Wi-Fi请求未成功（onUnavailable，耗时xxms），未重复申请；原因待查”；如Android本次`onUnavailable`后**现有** `Network` 刚好已经连接到BLE确认精确SSID+私有IPv4网关，检查/复用现有网络并继续**原厂HTTP200 JSON**身份验证；绝不发送第二次Wi-Fi请求，也绝不猜其它网络。
- **保持v0.1.18成功拍照/传图逻辑**：被动AA14`0x45 isTakePhoto 0→1→0`完整结束后250ms才发一次白名单`AB 55 00 03 39 30 69`，**无2.2秒自动抢先开AP**；保留用户手动确认已拍好和取消按钮。AA13写回执状态0后5秒内等待`AA14 0x25`，否则明确失败不重发。收到合法SSID立刻一次`WifiNetworkSpecifier`精确匹配原厂已验证WPA2，不做12秒扫描、1.8秒等待，Android系统可能弹出本次授权；要求onAvailable、真实私有网关、原厂只读GET`/app/getfilelist` HTTP200/result0/info，最新可排序唯一图片两次约350ms稳定列表，原始3200×2400 JPEG GET/解码到手机RAM，32MB/图片格式/路径限额不变。每张原图成功后**仍解除手机Wi-Fi请求，保留BLE待机**；眼镜AP是否自己退出未证实，但先前AP常连时快门可能无响应，绝不为减少申请次数而强行保持AP不断。无`0x22`遥控快门/`0x44`可能删除文件/OTA；不删眼镜照片，不写系统相册，不上传云端，OCR/TTS未接。
- **独立发布核验**：包名`com.vobichngan.bluestar.m02scallbackguardlab`、versionCode20/versionName0.1.19，v0.1.12～v0.1.18各旧版本、正式蓝星向导、Windows OCR、HeyCyan不变。独立CI `.github/workflows/m02s-network-callback-guard-v0119.yml` 保护精确BLE命令、回调身份校验、无扫描无旧拍照兜底、版本/权限/签名；第一次CI [43609396816](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43609396816) **失败**原因仅是静态检查把日志文案“无2.2秒拍照兜底”误判为存在旧逻辑，实际无兜底代码；修正文案后最终 [43609518479](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43609518479) **completed/success**，真正构建源码HEAD `d6befdb9343d4e55e8f34d85904b1769c94f8805`。Release tag `bluestar-m02s-network-callback-guard-v0119-20261114`，已核实APK `BlueStar_M02S_Network_Callback_Guard_0.1.19_Android.apk` 96249 bytes, SHA256 `c714d320b0bb83a4bcf07fb2d9ea316ed4c30a3bfd5b7e7a768e6b878e61aa50`。浏览器直接下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-network-callback-guard-v0119-20261114/BlueStar_M02S_Network_Callback_Guard_0.1.19_Android.apk 。CI成功只证明可编译/签名/发布，**不保证Android16每次连接成功或固件AP状态正确**。
- **下步最短真机动作**：安装v0.1.19独立APK，关闭并行原厂控制APP，手机蓝牙和WLAN打开；点“准备蓝牙待机”，按实体拍照键一次/再拍一次，观察是否继续成功取图。重点看**失败那一轮的完整匿名日志**：`0x45 1→0`→AA13写成功→AA14有效SSID和“开AP至SSID耗时”→“精确Wi-Fi请求→onAvailable耗时”或者`onUnavailable`及等待ms → 私有网关/原厂HTTP/原图。不要只看顶部状态或只拍一张的最后结果。若本轮`onUnavailable`是瞬时拒绝与接近25秒请求超时，需要分开判断真正成因；绝不因为这次成功两张就擅自断言系统限流。**用户要求简单粗暴，不加未知P2P/0x44指令，也不要重新做已成功的原图下载实验。**
- **交接治理**：把本节追加在main`PROJECT_HANDOFF.md`最前面，保留v0.1.18及全部旧历史；未经用户新指令不更改正式版、不覆盖旧分支/Release。

---

# 最新｜M02S v0.1.18 严格等实体拍照结束再开Wi-Fi（2026-11-14，已发布，待真机）

- **直接真机依据**：v0.1.17 用户在22:58:17 BLE待机已就绪，22:58:22收到有效`0x45` `isTakePhoto 0→1`，但软件原来的**2.2秒强制兜底**在22:58:24自动发送唯一`0x39/0x30`开AP，22:58:24稍后才收到`0x45 1→0`。AA13写入成功但22:58:29仍无有效`AA14 0x25` SSID，此次**始终没进入Android Wi-Fi阶段**。这是真实的时序竞争；提前切模式是怀疑原因，**尚未真机证实是唯一原因**。用户明确授权改为**“彻底取消2.2秒自动强制打开Wi-Fi，没有收到1→0就保持BLE待机、只允许手动确认拍完取图”**。不要再把自动倒计时加回来，也不重新试验已经成功的AP高速HTTP原图GET。
- **唯一代码行为改动**：以已构建的v0.1.17 HEAD `efc51242d2be01c4a9dbd823228f6ad84126e396` 创建独立分支 `exp/m02s-photo-end-only-v0118-20261114`；只修改该分支 `experiments/m02s-wifi-only-lab/app/src/main/java/com/vobichngan/bluestar/m02slab/MainActivity.java`：删去常量`PHOTO_DONE_FALLBACK_MS=2200L`、`photoActionCandidate()`中的`ui.postDelayed(2.2s)`和所有由固定时间自动抢先开AP的路径；收到严格校验的`AA14 0x45 0→1`仅置等待标志并提示，不发送命令；**仅收到相应`1→0`**，才安排250ms后一次`startPhotoTransfer(true)`。无结束通知则保持BLE待机、无任何自动AP定时任务。用户确定已拍好可点`我已经拍照，立即取高清原图`取消等待、主动取图；已有任务只反馈当前进度、不能重复发AP。取消按钮/断连销毁时清理250ms任务，保留原安全逻辑。`1→0`来自MIT开源E09`isTakePhoto`状态定义，在M02S上已真机观察到，但仍**不等于JPEG已完成写盘的严格保证**。
- **未改的关键能力**：蓝牙预连接AA14通知监听；允许发送的BLE指令只有原厂确证`AB 55 00 03 39 30 69`开Wi-Fi AP，绝不发0x22遥控快门、0x44可能删除媒体的完成/清理命令或OTA。保持AA13写状态0后最多5秒等待`AA14 0x25`有效SSID（没收到就提示AP/固件不返回，停止且不重发）。若收到SSID，仍先尝试复用同SSID真实网络，否则仅一次精确SSID+`WifiNetworkSpecifier`直连，不进行12秒扫描、1.8秒稳定等待或任何猜测热点；必须Android `onAvailable`+私有网关+原厂`GET /app/getfilelist` HTTP200/JSON result0/info验证。选取两次350ms间隔稳定、唯一可排序的最新文件候选，只读GET高清原始JPEG到手机RAM，仍有32MB、图片签名、路径校验，首次无拍前列表故**不能保证选出的最新文件就来自本次实体快门**。无450ms持续轮询，不写相册、不上传云端、不删除眼镜文件、不接OCR/TTS。手机释放Network不等于眼镜已安全退出传输模式，未擅自发未知指令。
- **独立分支与可下载APK已核实**：新包名`com.vobichngan.bluestar.m02sphotoendonlylab`、versionCode19/versionName`0.1.18`、App显示“蓝星 M02S 仅拍照结束取图 v0.1.18”。独立工作流 `.github/workflows/m02s-photo-end-only-v0118.yml` 附静态回归防护：有0x45结束及250ms安排，**源码里没有2.2秒兜底或2200L**；JUnit、Gradle `:app:assembleDebug`、zipalign、APK v2签名、manifest包名和权限检查均通过。GitHub Actions [43608149843](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43608149843) `completed/success`，源码HEAD `de927a30174d542484f73e1d06e2ffc268bc32a7`。Release tag `bluestar-m02s-photo-end-only-v0118-20261114` 已发布并核对真实资源，APK `BlueStar_M02S_Photo_End_Only_0.1.18_Android.apk` 94877字节，SHA256 `195377ed70f3b077c0420dbdd1e0e57a371fdcd98524ef1db10e7ab67baa2207`。浏览器直接下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-photo-end-only-v0118-20261114/BlueStar_M02S_Photo_End_Only_0.1.18_Android.apk 。旧v0.1.12～v0.1.17、正式蓝星向导、Windows OCR、HeyCyan全部保持不变。**CI成功不是M02S新模式已经收到SSID或真实高速取图成功**。
- **下次真机只需一次**：安装v0.1.18→关闭其他控制APP、BLE/WLAN开启→点“准备蓝牙待机”→实体快门一次→检查`0x45 0→1`和`1→0`，以及250ms后是否写原厂AP→**5秒内是否有`AA14 0x25`**；如果无结束信号，**应该一直保持待机，绝不自动开AP**，用户确认已拍完才手动点取图。若有SSID再看Android网络/HTTP/高清尺寸与时间；如果仍无SSID，不要盲目重发AP、重加扫描或重复旧GET实验。用户发新日志再定位。
- **项目纪律**：此节追加在main`PROJECT_HANDOFF.md`最前面，必须保留v0.1.17及以前所有历史；每次先读main交接、核实际分支/HEAD/Actions/Release，未经用户授权不动正式版或已发布旧实验。

---

# 最新｜M02S v0.1.17 拍照结束触发、SSID 5秒超时、取消按钮修复（2026-11-14，已发布，待真机）

- **用户最新实测与授权**：v0.1.16 2026-11-14 22:45:44 BLE待机开始、22:45:46 AA14被动监听就绪、22:45:54收到有效`0x45`照片活动标志0→1后约350ms就发`0x39/0x30`开热点、22:45:55 AA13写入成功，但之后**始终未收到AA14 `0x25` 的SSID**，所以未进入Android Wi-Fi建立，也没有GET高清图片；22:46:19再次观测`0x45`标志。用户点「取原图」没有反应，经源码确认v0.1.16 `if(inTransfer) return` 静默忽略。用户明确授权按前述三个建议立即修复。提前开热点可能打断眼镜JPEG写盘只是**假设**，不是已验证结论，不应宣称本版已修好眼镜固件。
- **这次只修3项**：① `M02sApStarter.java` 在`AA14`校验正确的 `ACTION_SYNC 0x45` 照片标志1→0时增加 `photoActionEnded()` 回调及匿名日志，`MainActivity.java` 收到0→1只设等待，不再350ms直接发AP；如果收到1→0，在250ms后仅发一次原厂`0x39/0x30`；如果M02S不发1→0，2.2秒后有限兜底一次。**1→0只说明E09推断的活动标志回落，仍不保证照片文件已落盘**。② 点击「我已经拍照，立即取高清原图」若正在等待结束标志，用户确认已拍好则取消2.2秒等待、立即一次开AP；若本轮已经在等待SSID/AndroidWi-Fi/HTTP，则显示当前阶段、日志标记拒绝重复命令，不再静默忽略；新增「取消当前取图（不重试、不删除照片）」按钮：纯等待时取消延迟任务保留BLE待机，已发送AP时取消请求并停止BLE，避免下轮误认迟到旧SSID。断连/销毁时清理待执行任务。③ AA13 GATT状态0后立即设5秒SSID计时器，收不到校验正确`AA14 0x25`则报出「眼镜未返回Wi-Fi SSID（0x25），不是Android连接慢」，调用`fail/stop`终止、**不自动重发**；验证收到SSID时取消计时器。原来Android精确SSID WiFiNetworkSpecifier直接请求、无12秒扫描、真实网关+原厂JSON/HTTP200鉴别、两次350ms间隔稳定候选/高分辨率JPEG只读GET到手机RAM、最大32MB及安全限制全部不动。
- **隔离与源码**：原仓 `vobichngan285-collab/kanzi-assistant`，从已成功v0.1.16源码HEAD `e6bc8a763d964a1f9bb5d24152810370a819418b` 新建分支 `exp/m02s-photo-finish-ssid-timeout-v0117-20261114`。仅改 `experiments/m02s-wifi-only-lab/app/src/main/java/com/vobichngan/bluestar/m02slab/{M02sApStarter.java,MainActivity.java}`、该实验的README/gradle/manifest与独立CI。独立APK包名 `com.vobichngan.bluestar.m02sshutterfinishlab`、versionCode18/versionName0.1.17；v0.1.12～v0.1.16、正式蓝星向导、Windows OCR和HeyCyan均不改不覆盖。仍然只可发已经验证的BLE AP命令`AB 55 00 03 39 30 69`，不发0x22遥控拍照、可能删除文件的0x44命令、OTA，不上传云端、不写相册、不删除眼镜文件。0x45标志来源公共MIT E09 `AdditionalValidations.swift`，本机M02S **已经真机看到0→1**，是否有1→0尚未证实；所有异步动作需依据M02S日志确认。
- **CI/Release已实查**：GitHub Actions [43607302469](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43607302469) 状态 `completed/success`，源码构建HEAD `efc51242d2be01c4a9dbd823228f6ad84126e396`，Gradle `:app:testDebugUnitTest :app:assembleDebug`、精确BLE白名单/无Wi-Fi扫描/等待拍照结束/SSID5秒计时/取消按钮静态安全检查、APK v2签名、zipalign、独立包名与版本验证、Release创建和回读全部通过。Tag `bluestar-m02s-photo-finish-v0117-20261114`，APK `BlueStar_M02S_Photo_Finish_WiFi_0.1.17_Android.apk` 95709字节，SHA256 `4c639986300ff3110999cd6e73e9610082106f396b231bc2d8b6c385456d7f5c`。浏览器直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-photo-finish-v0117-20261114/BlueStar_M02S_Photo_Finish_WiFi_0.1.17_Android.apk 。CI文件 `.github/workflows/m02s-photo-finish-v0117.yml`。**CI成功不等于实体快门拍完后固件一定返回SSID/传图成功**。
- **下一次真机唯一实验**：关其它眼镜控制App，安装v0.1.17→点「准备蓝牙待机」并确认AA14订阅完成→按实体快门一次，观测`0x45`活动0→1后**是否能收到1→0**；若有则250ms后AP，若没有则最多2.2秒兜底AP；亦可按「我已经拍照，立即取高清原图」跳过等待（当前任务若已开AP则只反馈阶段，不重复发）；观察`AA13`status0后**5秒内能否收到有效`AA14 0x25`**，随后才看AndroidWi-Fi和原厂JPEG耗时；若仍无SSID，重点调查M02S固件是否在拍后因忙而拒绝开AP，**不要重复改Android连接或重做旧的高清GET实验**。把匿名日志交接。不再把拍前后反复打开蓝牙、扫描12秒、每450ms持续轮询的慢路径加回来。
- **治理**：main `PROJECT_HANDOFF.md` 在开头新增这一节，保留v0.1.16/v0.1.15及以前完整历史，每次开发先读main交接、核实实际HEAD/Actions/Release，未经授权不改任何正式产品及旧实验。

---

# 最新｜M02S v0.1.16 实体快门后极速Wi-Fi高清取图（2026-11-14；已构建发布，待真机）

- **本轮指令/优先级**：用户指出原方案拍完再一轮轮开蓝牙、扫热点、Wi-Fi连接太慢（可能几分钟），要求立即做**最短路径高速Wi-Fi原图传输**。之前v0.1.15真机在2026-11-14 22:26:14发0x39/0x30、22:26:27等足12秒才请求Android Wi-Fi、22:26:29建立网络并读到23张；其它日志也证明原厂高清原图HTTP GET单次仅475–758ms，瓶颈是连接前的12秒扫描/重复建连。用户强调“简单粗暴、别无限折腾”。新方案：**蓝牙先待机（不打开AP）→眼镜实体手动拍照→手机发现照片状态候选或用户点立即取图→BLE只发一次原厂0x39/0x30→收到AA14 0x25真实SSID立即申请Wi-Fi（不扫描/不等12秒）→原厂GET原始高清JPEG到手机RAM**；不重复旧蓝牙遥控拍照/无法在AP模式拍照的失败实验。
- **开源参考与可靠性边界**：重新读取公共MIT开源 `sctg-development/ai-smart-glasses-e09-reverse/Sources/e09_reverse/AdditionalValidations.swift`，其 `decodeActionSyncFlags` 将**AA14 0x45 ACTION_SYNC payload第0字节**解释为 `isTakePhoto`，并声明可监听spontaneous 0x45；`Constants.swift` `OPEN_WIFI 0x39/0x30`、`WifiMediaManager.swift` `GET /app/getfilelist` 同已成功的M02S原厂HTTP逻辑。**M02S尚未真机证明实体快门确实会发0x45**，所以只在完整AC55长度+checksum正确、0x45的首标志0→1上升沿后（350ms）**尝试**自动取图，日志注明“E09候选、待验证”；另有用户显式按钮 **「我已经拍照，立即取高清原图」** 无任何拍照通知也能马上开始单次Wi-Fi取图。未使用0x42含义不明指令/事件作为可靠快门证明，也没有发送BLE 0x22远程拍照命令、0x44潜在删除媒体命令或任何OTA。
- **新分支/APK**：以已发布v0.1.15 HEAD `8916534de2a339cd7a779134cf1416b925b5d85e` 新建**独立分支** `exp/m02s-shot-wifi-fastpath-v0116-20261114`。独立目录沿用 `experiments/m02s-wifi-only-lab`，只修改此分支，独立包名 `com.vobichngan.bluestar.m02sshotfastlab`、versionCode17/versionName0.1.16、UI标题“蓝星M02S极速高清取图”，旧v0.1.12～v0.1.15代码/Release、正式蓝星向导RC25、Windows OCR、HeyCyan不动。
- **精简后的实现**：`M02sApStarter` 初次只扫描唯一M02S→LE1M GATT→AA14 CCCD订阅→保持BLE待机**不发送0x39/0x30**。收到开源E09 0x45照片状态候选或用户显式按按钮后，才发**一次白名单** `AB 55 00 03 39 30 69`；收到校验正确AA14 `0x25` SSID且AA13写入status0后，优先复用相同SSID+真实私有IPv4网关的已连接Wi-Fi Network，否则**立刻以准确SSID+以前真机已验证的OEM WPA2配置调用一次Android `ConnectivityManager.requestNetwork`**，请求最多25秒，系统关联/授权仍由Android处理。**完全去掉主动 `wifi.startScan()` 和旧12秒扫描/1.8秒等待，不猜测别的热点**。Android Network onAvailable+网关后才访问原厂 `GET /app/getfilelist` HTTP200/result0/info；旧HTTP只读传输、安全文件名、最大32MB、图像签名和Bitmap校验沿用。
- **照片选择/真实性限制**：因为正常拍照发生于AP连接前，所以**没有当前拍照前的HTTP文件基线**。使用已存在 `M02sFreshPhotoPolicy.selectLatestUnverified` 和 `M02sPhotoPolicy.uniqueLatestOrNull`，只选择时间可唯一排序的最新候选，两次只读HTTP列表间约350ms稳定后GET照片，若同一App已下载过同一文件则拒绝重复，若排序不唯一/无合格图片则停止不盲猜。**“最新候选”并不保证就是刚按实体键拍的**，日志明确这样写；未来可增加经过真机证明的拍摄后文件序号/通知匹配，未验证前不伪造正确率。照片只有手机内存预览，不保存、**不上传公网/云AI**，用户“上传照片”在本轮落实为眼镜→手机RAM高清传输；OCR/TTS需后续配置。下载成功解除手机Wi-Fi Network请求，**保留BLE待机**以减少下次启动成本；但手机解除Network不等于眼镜固件退出AP/恢复实体拍照模式，目前仍需真机验证，故没有盲发未实测0x44结束/删除命令。单次事件最多39秒，若网络不通立即停止，不反复自动发送开热点指令、没有每450ms无休止轮询。
- **构建与Release已核实**：GitHub Actions [43606067977](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43606067977) `completed/success`，源码构建HEAD `e6bc8a763d964a1f9bb5d24152810370a819418b`，JDK17/SDK36、JUnit（旧OEM图片选择+新0x45 checksum验证）、独立APK版本/权限、APK v2签名、zipalign、Release创建/回读通过。Tag `bluestar-m02s-shot-fast-wifi-v0116-20261114`，APK `BlueStar_M02S_Shot_Fast_WiFi_0.1.16_Android.apk` 89717字节，SHA256 `693caa4b19fc1cab7a89f9d0b29c36db300098636116f842c98149c6f1c37825`。浏览器直接下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-shot-fast-wifi-v0116-20261114/BlueStar_M02S_Shot_Fast_WiFi_0.1.16_Android.apk 。独立CI文件 `.github/workflows/m02s-shot-fastpath-v0116.yml`。**CI成功不等于M02S已验证自动拍照通知、更不等于已经3秒取到图**。
- **下一步唯一真机操作**：安装v0.1.16→手机蓝牙/WLAN开启，关原厂其他控制APP→点**「准备蓝牙待机」**，直到“BLE已待机”→在**未启动眼镜AP**的正常模式按实体快门一次→观察软件是否收到0x45候选并自动开Wi-Fi；如果没有，**不必重拍**，直接按**「我已经拍照，立即取高清原图」**一次；Android若弹授权则允许→观察AA13回执、AA14 SSID、`Network onAvailable`、私有网关、原厂HTTP200、高清GET分辨率和“触发到解码”计时→贴匿名日志。不要让用户重复已证实的“正常模式可拍照”“原厂高清GET可用”测试。若第1张成功但眼镜未恢复实体拍照状态，再研究经过来源核对的安全退AP机制，不抢先发0x44/0x30/0x00删除指令。
- **治理**：此条仅追加main交接文件开头，**保留v0.1.15及所有旧历史**；继续遵守读main/PROJECT_HANDOFF.md、核实HEAD/Actions/Release、未经授权不改正式蓝星向导/Windows OCR/旧源码与发布版本。

---

# 最新｜M02S v0.1.15 Wi-Fi扫描缺失时对BLE确认SSID精准连接一次（2026-11-14，已发布，待真机）

- **用户指令与真机依据**：v0.1.14 21:55:46 BLE原厂0x39/0x30开启AP AA13写入成功、AA14 0x25返回真实SSID；21:55:47曾检测到本次新的眼镜AP广播，但用户后续截图显示 **“连接失败：12秒内未扫描到本次新的眼镜Wi-Fi热点”**；界面副标题还误显示v0.1.13。用户明确同意立即修复，目标是**保留BLE仅开热点+实体手动快门+Wi-Fi高清原图自动下载**，不改旧已成功的AP/HTTP取图和不通过BLE遥控拍照。
- **修改与安全边界**：沿用原仓库 `vobichngan285-collab/kanzi-assistant` 和v0.1.14成功版分支 `exp/m02s-ble-ap-manual-shutter-autofetch-v0114-20261114` 的代码，创建独立新分支 `exp/m02s-ap-ssid-fallback-v0115-20261114`，发布真实构建HEAD `8916534de2a339cd7a779134cf1416b925b5d85e`。v0.1.15在 `M02sApStarter.java` 中加入 **先检查现有Android Wi-Fi Networks是否已有与AA14 0x25校验SSSSID完全相同的连接并且有真实私有网关，若是则复用，不再重复请求**；如没有继续采用上版成功过的新扫描FRESH_UNIQUE+1.8s稳定路线。若Android扫描12秒仍无稳定可信结果（包括NO_TARGET/CACHE_ONLY/新扫描变旧），仅在**AA13原厂0x39/0x30写入回调status0、AA14 0x25帧长/校验和/SSID UTF-8均合法、未发起Wi-Fi申请、没有同名不同BSSID歧义**时，允许一次 **`WifiNetworkSpecifier`精确SSID+先前已验证原厂WPA2密码**申请；从不连接未知网络或盲目重复发送BLE。不宣称扫描缓存等于AP已上线，**必须Android `onAvailable`/真实私有网关，并进一步原厂 `GET /app/getfilelist` HTTP200/JSON result0/info鉴别**才建立拍前基线并监视实体按键拍照。若失败停并输出不敏感阶段日志。
- **修复原隐藏定时问题**：在AP凭真实网关宣告就绪，或成功复用当前目标连接时，立即取消旧的90秒建连deadline，避免中断后续120秒照片监测。保留v0.1.14已有的**只有用户自己按实体快门**、拍前原有列表基线、每约450ms轮询一次、两次稳定后只读GET高清原图到RAM，最多3张/120秒，终端不记录SSID/密码/MAC/文件名/照片数据，不删除文件、不上传/写相册、不触碰OTA。新增纯Java `M02sApConnectPolicy.java`和4组JUnit，覆盖缓存/限流扫描12秒回退、早于12秒不可回退、已请求不可重复、无AA13写成功回执/无合法SSID禁止回退、同名多BSSID歧义直接阻止。
- **UI/包名**：修复截图副标题错误v0.1.13，MainActivity标题、启动日志/AndroidManifest应用标签统一显示v0.1.15。新独立应用 `com.vobichngan.bluestar.m02sapfallbacklab`、versionCode16/versionName0.1.15，目录仍是 `experiments/m02s-wifi-only-lab` 但仅改v0.1.15隔离分支，所有v0.1.14、v0.1.13、v0.1.12等旧分支/Release和正式蓝星向导RC25、Windows OCR保持不动。参考已核对的开源 MIT `sctg-development/ai-smart-glasses-e09-reverse` 的 `Constants.swift` 和 `WifiMediaManager.swift`、沿用原已真机证明的HTTP实现，MIT出处和许可仍保留 `E09_SOURCE_LICENSE.md`。
- **CI/Release事实**：第一次本轮GitHub CI `43602962364` 虽**编译和JUnit成功**，发布检查因脚本错误仍预期`versionName='0.1.14'`而误失败，没有交付。已将脚本修为实际`versionCode16/versionName0.1.15`，最终独立CI [43603093237](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43603093237) **completed/success**（Android SDK36/JDK17, Gradle JUnit, APK v2签名、zipalign、Manifest权限检查、GitHub Release发布及回读核验）。新Release tag `bluestar-m02s-ap-ssid-fallback-v0115-20261114`；APK `BlueStar_M02S_AP_SSID_Fallback_0.1.15_Android.apk` **93157字节**，SHA256 `417633f8953c00b53c85069d2efdae49dfa9d950e71382baee53631031db2f6d`；浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-ap-ssid-fallback-v0115-20261114/BlueStar_M02S_AP_SSID_Fallback_0.1.15_Android.apk 。
- **下一次仅做这项真机实验**：安装v0.1.15（为独立包）→关HeyCyan→手机蓝牙/Wi-Fi权限授权→点一次 **「连接M02S并开启Wi-Fi、自动等待照片」**→注意是否出现 **“已复用同SSID已有Network”/“检测到目标本次新AP”/“Android扫描12秒…仅请求一次准确SSID”**，观察Android `onAvailable`、网关、原厂HTTP列表与「拍前基线已建立」。**只有基线建立后才按眼镜实体拍照键**，测试在AP连接时是否真的能拍照并产生新原图/自动取图；如果AP拍照互斥，勿谎称纯软件可绕过。3秒目标还未真机达成，未接OCR/TTS。若本版仍失败，优先根据新日志定位Android onUnavailable/SSID/网关，**不要重复以前已证明AA15缩略图或高清GET链路实验**。
- **治理**：先读main交接，核真实分支、HEAD、CI、Release；本次仅在main最前面追加交接历史，不覆盖旧内容；无用户新授权不要修改正式版或自动继续构建。

---

# 最新｜M02S v0.1.14 蓝牙只开AP + 实体快门 + Wi-Fi自动高清取图（2026-11-14，已发布，待真机）

- **用户新反馈与明确修改授权**：用户查看Android WLAN页面后表示 **M02S眼镜没有独立开启Wi-Fi的实体按键**，因此上一个v0.1.13完全零蓝牙的方案无法自行唤醒眼镜AP，用户指示“你重新修改一下”。立即改为**BLE仅一次性开启眼镜AP，之后实体按眼镜快门，Wi-Fi常连接自动发现并下载高清照片**。不发送BLE拍照指令，仍以用户手动拍照为核心。继续使用原仓库 `vobichngan285-collab/kanzi-assistant`；正式蓝星向导RC25、Windows OCR、v0.1.10～v0.1.13旧实验包及Release不改动。
- **已核对旧代码和开源**：先读main交接、核旧v0.1.13分支HEAD `972d04763d5e9ad85646a02a2ccc5c6f1f992bd5`、成功CI `43600617680`、Release `bluestar-m02s-manual-wifi-autofetch-v0113-20261114`；读取此前真机成功的旧实验分支 `exp/m02s-e09-jpeg-tail-fix-v0112-20261114` 中 `MainActivity.java` 的 BLE AA13/AA14、`0x39/0x30`、AA14 `0x25` SSID、Android16精准WifiNetworkSpecifier、ScanResult时间戳/私有网关、原厂HTTP实现，复用已有 `M02sWifiPolicy`、`M02sApScanPolicy`、`M02sApReadinessPolicy`，继续保留已成功AP原图GET。开源 `sctg-development/ai-smart-glasses-e09-reverse` 的 `Constants.swift` 与 `WifiMediaManager.swift` 证实 E09的OPEN_WIFI 0x39/0x30及 `/app/getfilelist` 设计，MIT许可/作者 Ronan Le Meillat / SCTG Development 与文件 `E09_SOURCE_LICENSE.md` 仍保留。没有编造新热点协议/新密码。
- **v0.1.14代码隔离**：新实验分支 `exp/m02s-ble-ap-manual-shutter-autofetch-v0114-20261114`，已发布构建HEAD `9aebc00935b9d5e8155fb36c6f14d5f14dcdef44`。新独立Android app ID `com.vobichngan.bluestar.m02sbleapmanual`、versionCode15、versionName0.1.14；仍用 `experiments/m02s-wifi-only-lab` 源目录但只改v0.1.14独立分支，旧v0.1.13源码绝不覆盖。新增 `M02sApWire.java` **精确且唯一白名单** `AB 55 00 03 39 30 69`，只在用户主动点一次启动后经过唯一M02S BLE扫描/GATT AA14 CCCD订阅，向AA13发一次原厂0x39/0x30开启AP，AA14 `0x25`经帧长/校验/UTF8解析精确SSID。**没有发送BLE `0x22/0x30` / `0x22/0x31` 快门、没有0x44删除命令、没有OTA**。扫描目标SSID对应本次新AP广播，沿用旧版新结果检测与1.8s稳定等待，用OEM已验证的WPA2配置向Android16 `ConnectivityManager.requestNetwork`申请一次Wi-Fi，并通过专属Network的192.168.x.x真实网关和 `GET /app/getfilelist` HTTP200/result0/info进一步验证，避免误连周边热点。
- **手动实体拍照、连续自动取高清图**：AP通过HTTP验证后自动建立 **拍照前旧照片列表基线**，界面提示用户按眼镜实体快门；每约450ms查询 `/app/getfilelist`，要求**新文件名相对基线新增+两次名字/大小/时间稳定**后才GET original image（32MB封顶、安全相对路径、真实Bitmap解码），仅手机RAM显示高清原图、记录单次下载耗时、首次发现文件到解码耗时；120秒内最多3张，前一张完成后再按下一次实体快门，不重复蓝牙开AP。正常取图全过程不依赖遥控快门；网络断开/校验失败则停，不转陌生Wi-Fi。界面精简为连接并开Wi-Fi、停止本次会话、照片预览及匿名日志，照片/密码/SSID/蓝牙地址不写日志、不保存/上传/删除。
- **正确的真机问题边界**：v0.1.10早期在眼镜每次BLE远程拍照后连接AP下载3200×2400原图，单次HTTP GET耗时475～758ms；v0.1.12 BLE直传JPEG 320×180/3061ms也成功。**现在仍不知道眼镜AP传输模式期间实体按键能否拍照、能否让文件列表动态新增**：若Wi-Fi已开启时实体快门没有声音/列表没新文件，则可能固件模式互斥，不能用本次CI成功冒充实验成功；不要重复蓝牙远程快门旧实验。新方案不内置OCR/TTS，待真实取图成功再接；3秒只是目标，没有按实体键时间戳，App日志的“第一次发现文件→解码”不代表整体按键→显示耗时。
- **CI+Release真值**：[GitHub Actions 43601783763](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43601783763) `completed/success`，Android SDK36/JDK17，JUnit覆盖原照片列表/新增稳定判断/网络安全及新增AP指令和0x25校验，独立APK编译、v2签名、zipalign、独立包名/权限、安全过滤、Release资产检查均通过。Tag `bluestar-m02s-ble-ap-manualphoto-v0114-20261114`，APK **BlueStar_M02S_BLE_AP_ManualShutter_WiFi_0.1.14_Android.apk**、90545字节、SHA256 `c10d5b46cc3204def03aac102f7c2a9b6679b04814c9e182266a698cf1218c5f`；可复制浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-ble-ap-manualphoto-v0114-20261114/BlueStar_M02S_BLE_AP_ManualShutter_WiFi_0.1.14_Android.apk 。新CI workflow `.github/workflows/m02s-ble-ap-manual-photo-v0114.yml`，旧CI和Release不动。
- **用户真机操作（仅测试本轮）**：关闭HeyCyan→打开v0.1.14→授予蓝牙和Wi-Fi附近设备/位置权限、开启手机蓝牙和WLAN→点 **「连接M02S并开启Wi-Fi、自动等待照片」** 一次；Android弹眼镜Wi-Fi授权则接受→**等待日志“旧照片基线已建立”**→**按眼镜实体拍照键**一次，看快门声和新文件/原图出现，成功后再按两次→发送匿名诊断。若Wi-Fi已成功但实体键无法拍照，记录此事实即有重大诊断意义，暂不再添加重复遥控指令。
- **交接治理**：以上只在main:PROJECT_HANDOFF.md头部追加进展，保留所有v0.1.13、v0.1.12及历史。后续接手先读main交接，再核真实branch/HEAD/Actions/Release；未经用户后续授权，不动正式版、不自动继续构建。

---

# 最新｜M02S 纯Wi-Fi + 眼镜实体快门自动取高清图 v0.1.13（2026-11-14，已发布；待真机）

- **用户新方向/明确授权**：用户认为AA15缩略图320×180、取图3.061秒仍慢且清晰度不足，要求改为 **眼镜实体按键手动拍照，手机经Wi-Fi自动发现新照片并下载高清原图，软件不涉及任何蓝牙操控**；用户同意立刻制作并要求参考已发现的公开开源 E09 代码。此实验目标优先验证零蓝牙是否能有眼镜AP常连+实体拍照+自动取图；**不改正式蓝星向导RC25、Windows OCR、v0.1.12以及旧Release；不增加OCR/TTS直到图片传输稳定**。
- **开源代码与现有成功代码核对**：直接核对 `sctg-development/ai-smart-glasses-e09-reverse/Sources/e09_reverse/WifiMediaManager.swift`、`Constants.swift`、README（MIT / Ronan Le Meillat / SCTG Development）。开源设备的AP照片服务同样为 `/app/getfilelist` / 本地HTTP图片GET，但开源流程本身通过BLE `OPEN_WIFI 0x39/0x30` 要求眼镜开热点，**没有证据M02S可完全零BLE开启AP**。复用本仓库前版实际M02S成功的 `M02sPhotoTransport`、`M02sPhotoListReader`、`M02sPhotoPolicy`、`M02sFreshPhotoPolicy`、`ProbePolicy`，不重新写已成功的高清原图GET逻辑。原旧版3200×2400原图传输曾实测475～758毫秒，但旧版端到端13秒左右主要消耗在BLE/热点开启/新扫描/Android连接/等待上；不能据此承诺纯Wi-Fi方案一定更快。
- **新独立源码**：分支 `exp/m02s-wifi-only-manual-shutter-v0113-20261114`，真实HEAD/CI构建commit `972d04763d5e9ad85646a02a2ccc5c6f1f992bd5`；独立实验目录 `experiments/m02s-wifi-only-lab`，包名 `com.vobichngan.bluestar.m02swifionlylab`，versionCode14 / versionName0.1.13。**全新 MainActivity 完全不导入android.bluetooth，没有Bluetooth权限、没有蓝牙扫描/连接/命令**。先由用户通过Android系统Wi-Fi设置自行发现、开启并连接眼镜热点，手动填写实际完整SSID；App只遍历当前系统 **已连接** 的Wi-Fi Networks，严格精确SSID匹配、核对该Network的真实192.168.x.x私有网关，用 `Network.openConnection` 验证 `GET /app/getfilelist` HTTP200/JSON `result=0`，**不会猜SSID、密码、眼镜IP或连周边其它设备**。需用户授予Android16的附近Wi-Fi/位置权限及开启系统位置服务。若眼镜无法脱离BLE自行开热点，**本路线在本机型上可能无法开始**，App只报告Wi-Fi不满足，不会偷偷退回BLE。
- **自动取图完整实验路径**：用户点「开始自动取图」→ App读取一次已有照片列表作为*拍前基线*→提示「现在按眼镜实体拍照键」→ 每约450ms通过已核实的专属Network只读查询列表→只对**基线之外的新文件名**，且新文件名+大小+时间**连续两次稳定**才GET高清原图→限制32MB、文件名/URL安全、原图格式签名与扩展名一致、有效Bitmap分辨率≤3200万像素，然后只在内存显示和统计；可连续自动取3张/最多120秒，失败或掉线立即停、不重连其它网络。不保存相册、不上传云端、不删眼镜媒体，不写文件。日志不包含SSID、密码、照片名称或照片字节。
- **延时统计边界**：记录 `HTTP下载耗时`、`GET发起到解码耗时`、`首次发现新文件到解码耗时`；因为本App完全不监听蓝牙/实体按钮，**无法准确知道物理按快门时刻**，不能把“首次文件出现到解码”当作“实体快门到显示全程”，`3秒`只是用户希望的目标，**未获真机证实**。并未实现OCR、TTS。
- **真实构建/Release**：GitHub Actions [43600617680](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43600617680) `completed/success`（JDK17/SDK36、Gradle单元测试和Debug APK、v2签名/zipalign、独立包名版本/权限检查、确认 APK Manifest **无蓝牙、摄像头、媒体、修改Wi-Fi或网络权限**）。**APK已真实发布**：GitHub Release `bluestar-m02s-manual-wifi-autofetch-v0113-20261114`，资产 `BlueStar_M02S_WiFi_Only_AutoPhoto_0.1.13_Android.apk` 57445字节，SHA256 `615844a442765e26972920603418bdec39399ccfc51ec35f0eaa4a0e5bd16c88`，浏览器可复制直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-manual-wifi-autofetch-v0113-20261114/BlueStar_M02S_WiFi_Only_AutoPhoto_0.1.13_Android.apk 。曾有CI run `43600532868`因静态校验把用于裁剪日志的`StringBuilder.delete`误判为相册删除而失败，此为CI检测器误报，已收窄仅验证蓝牙API，最终最新run全成功；**没有媒体删除代码**。
- **唯一待测试点/正确用户操作**：安装新0.1.13→安卓系统Wi-Fi设置看 **不经蓝牙指令能否看见并连接眼镜自己的AP**（可能需要眼镜实体热点开启操作，但不能猜）→回APP输入眼镜SSID→「验证当前眼镜Wi-Fi和原厂照片接口」→「开始自动取图」等到显示拍前列表已建立→眼镜实体键手动拍照，观察有没有快门、列表新文件及首次自动原图下载；接着连续拍另外两张。若热点不存在，直接反馈“无法纯Wi-Fi开启眼镜AP”，**不让用户盲目继续折腾相册代码**；若Wi-Fi模式下眼镜不能手动拍照，说明可能拍摄/传输状态互斥，仍需实证。全部结果尚待真机，不要宣称已成功在3秒内取高清图。
- **交接纪律**：本段只新增main交接文档开头，完整保留既有v0.1.12 E09 AA15 **真机成功320×180/3061ms**及所有旧实验历史。后续先核对main当前交接、真实分支HEAD和Release，未经新指令不修改或发布。

---

# 最新真机｜M02S v0.1.12 E09 蓝牙JPEG直传成功（2026-11-14 21:03:30）

- **重大实测成功，不需要重复做“AA15能否工作”实验**：用户在 vivo Android16/M02S-3584上安装已发布的v0.1.12，21:03:14 BLE GATT 连接成功（424ms），21:03:21~21:03:22 AA14/AA15 CCCD订阅均status=0。**21:03:27** 用户手动点「E09 蓝牙直传照片」，向AA13发送开源 `TAKE_PHOTO 0x22/0x31`，GATT写status0耗时38ms，AA14收到拍照`0x22`和操作状态`0x45`；**21:03:29** AA15收到 `PHOTO_START`，声明8988字节；**21:03:30** 收到`PHOTO_END`，**分片完整、首缺失偏移=-1、分片包21、实际8988字节（声明差值0）、SOI=true、EOI=true、EOI结束位置8987、末尾额外1字节**。Android BitmapFactory **成功解码320×180 JPEG**，候选长度8987字节；日志实际报 **总耗时3061ms（从AA13发送到JPEG内存解码）**。**无需Wi-Fi/原厂AP/相册查询**；不保存相册、不上传云端、不删除眼镜文件。
- **明确结论边界**：此轮证实 **E09开源0x22/0x31→AA15→手机JPEG解码** 在该真实M02S上成功，v0.1.11的「JPEG文件头尾校验失败」可由v0.1.12放宽严格尾部要求解决（本次实测EOI尾后额外1字节，**不是此前另一款E09设备报告的+500字节**）。320×180分辨率已证实，**适合大字OCR的准确率尚未经过实测**；实际OCR和TTS尚未接入；用户的最终「拍照→OCR→朗读 ≤3秒」尚未达成，仅 **蓝牙取图+解码 3.061秒** 已完成。用户之前提议的「眼镜实体按键拍照→Wi-Fi自动下载高清原图」是独立新路线，手动快门与Wi-Fi同时保持连接是否可行尚未验证；E09 RTSP实时取帧也尚未真机验证。
- **下一步建议，未经用户授权不改代码/不构建**：不要再重复已证实的AA15分片协议与JPEG修复。若优先快速读大字，测试320×180画面实际大字OCR并测朗读延迟；若需要菜单/密集小字，优先验证 **眼镜Wi-Fi长连接+实体快门+AP自动发现新照片并下载高清原图** 的状态互斥问题，或验证现有v0.1.12 RTSP`0x67/0x30`。切勿混淆3.061秒JPEG解码与端到端朗读耗时，也不承诺高清Wi-Fi能在3秒内完成。
- **版本/仓库纪律**：这是对 **已经发布的** v0.1.12的真机结果补充到main交接，不修改正式蓝星向导RC25、Windows OCR、实验分支、原厂HeyCyan和所有Release。不擅自再发新版。v0.1.12源码分支`exp/m02s-e09-jpeg-tail-fix-v0112-20261114`；CI [43592932430](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43592932430) completed/success，APK Release `bluestar-m02s-e09-jpeg-fix-v0112-20261114`。此次只补充真实测试证据，保留旧历史。

---

# 最新｜M02S E09 AA15 JPEG尾部修复 v0.1.12（2026-11-14，已发布；待真机验证）

- **用户明确授权**：在 v0.1.11 已经真实触发 E09 蓝牙拍照后，立即制作下一个 **AA15/JPEG接收修复版**。严格保持原仓库 `vobichngan285-collab/kanzi-assistant`；正式蓝星向导 RC25、Windows OCR、原厂HeyCyan、v0.1.11及此前全部实验分支/Release不动，不重复之前已成功的BLE基础、AP连接或原图HTTP实验。
- **真机失败锚点（新修复的直接依据）**：2026-11-14 19:57:27 用户在Android16/M02S-3584上成功发出开源 E09 `0x22/0x31`（AA13写status=0、51ms、AA14收到0x22及0x45）；19:57:29真实AA15 `0x97 PHOTO_START` 宣布 **6684字节**，期间收到AA14 `0x42=00 11`；19:57:30 Java版 `M02sE09PhotoAssembler` 在PHOTO_END校验处返回 **“JPEG文件头尾校验失败”**。v0.1.11严格要求JPEG SOI=FF D8和最后两字节EOI=FF D9，但将两类错误合并成同一消息。开源 `sctg-development/ai-smart-glasses-e09-reverse:BleProbe.swift` 不要求EOI严格在传输末端，该开源项目另一型号样机曾观测 `actual - PHOTO_START` = +500字节，**这不代表已证实本台M02S也固定多500字节**。用户未在v0.1.11实际解码图片，不能宣称已经完成蓝牙图片传输；但眼镜响应开源指令、AA15照片协议确实触发已获真机证实。
- **v0.1.12仅修最小必要代码**：新隔离分支 `exp/m02s-e09-jpeg-tail-fix-v0112-20261114`，源码Release构建提交 `bbc2f6c56ac4c29ecb28edb166fe0c5f8e3d3ffc`（后续文档提交HEAD `b7f42f8ce8887c7417426f1b8a1ad6c8e584cd66`，**只更新README，不影响构建**）。`M02sE09PhotoAssembler` 沿用E09 AA15 `52 58` 帧、`0x97/0x98/0x99` 和4字节大端offset分片，完整性BitSet拒实际缺口和2MB越界；减少初始图片内存分配；`PHOTO_END` 不再误将EOI后附加数据判为损坏，而是遍历接收的连续JPEG数据找到**最后一个实际 EOI**作为裁剪候选，计算真实尾随字节（不假设500）；SOI缺失仍拦截，没EOI但Android解码成功则提示“可能不完整”，不冒充无损原图。App先对JPEG裁剪候选调用真正 `BitmapFactory.decodeByteArray`，失败时可尝试未裁剪的连续AA15数据；只有Bitmap成功才展示内存预览。日志仅保留 **宣布长度、实际接收连续长度、差值、SOI/EOI是否存在与末尾位置、附加字节数、缺口、分片数、耗时、Android解码结果**，不打印图片、文件名、SSID或密钥，不保存相册/上传/删除。
- **回归测试**：更新 `M02sE09PhotoAssemblerTest.java`，8个Junit场景：原始完整JPEG+乱序/重复包、+500尾随数据、其它尾随长度、EOI缺失但候选保留供Android判定、分片缺口、SOI错误、无效长度/越界、AA14及错误帧拒绝。E09 RTSP `0x67/0x30`、不删除完成通知`0x44/0x30/0x01`、原已真机成功的v0.1.10 AP原图GET和蓝牙控制均不改；上游MIT授权原文仍保留于实验目录 `E09_SOURCE_LICENSE.md`。
- **真实CI/发布**：[GitHub Actions 43592932430](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43592932430) `completed/success`，Android SDK36/Gradle JUnit/assembleDebug，独立 APK v2签名、zipalign、应用版本、权限、Release资产核验全通过。独立应用包 `com.vobichngan.bluestar.m02se09jpegfixlab`、versionCode13/name0.1.12。GitHub Release tag `bluestar-m02s-e09-jpeg-fix-v0112-20261114`；安装包 `BlueStar_M02S_E09_JPEG_Fix_0.1.12_Android.apk` **184333字节**，SHA256 `06e691526852c1cbf09fc3cb7a9ce8db14ca49beee877f4d0ed3ddf5c858b846`；**浏览器直链** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-e09-jpeg-fix-v0112-20261114/BlueStar_M02S_E09_JPEG_Fix_0.1.12_Android.apk 。
- **只需一次真机JPEG解码确认**：完全关闭旧实验进程/HeyCyan，启动独立v0.1.12，BLE LE1M连接→开启120秒通知直到AA14和AA15 CCCD status=0，**不点旧⑤“拍照→验证AP→取原图”**，只点⑥「E09 蓝牙直传照片」一次，观察新`E09 AA15 PHOTO_END`数值诊断、`JPEG直传并解码成功`或`BitmapFactory返回null`；如果出现图片，请人工核对分辨率和实际小字是否清楚。若结果仍失败，先根据安全的头尾/连续长度日志具体修接收，不重复拍照通道是否存在的旧实验。**本轮只是CI成功，尚无v0.1.12真机解码成功证据，也尚无RTSP真机验证**。
- **交接纪律**：以上新增到main文档开头，不删除或覆盖前版历史。下一次接手先读取main该文件并核真实Branch/HEAD、Actions、Release；未获用户继续授权不改正式产品/不自动构建。

---

# 最新真机｜M02S E09 BLE AA15 直传首次触发，JPEG校验失败（2026-11-14，19:57）

- **实机原始关键节点**：v0.1.11 Android16/M02S-3584，19:57:17 GATT成功；19:57:19 AA14+AA15 CCCD均status=0；19:57:27 向AA13发送开源 E09 `0x22/0x31`（新路径，而非旧`0x22/0x30`），写入status0、51ms，AA14收到`0x22`、`0x45`；**19:57:29 AA15 `PHOTO_START 0x97`**并宣布长度**6684字节**；AA14 `0x42=00 11`；19:57:30 我们的 `M02sE09PhotoAssembler` 返回 `JPEG文件头尾校验失败`，结束AA15监听。**已经实际证实M02S对E09 0x22/0x31指令作出拍照响应，并发送AA15图片传输协议帧**，不能再说“直传指令未触发/该设备必然不支持”。从指令到校验失败约3秒，尚未完成JPEG解码，不能把3秒等同于完整可用图片交付时间或保证小字OCR。
- **直接对照 GitHub 源码（已回读）**：`exp/m02s-e09-ble-photo-rtsp-v0111-20261114:experiments/m02s-wifi-lab/app/src/main/java/com/vobichngan/bluestar/m02slab/M02sE09PhotoAssembler.java`：正确按AA15偏移写入并设置BitSet，`PHOTO_END 0x99`时在覆盖连续性测试之后，**额外要求** `image[0..1]==FF D8` **且最后两字节** `image[highest-2..highest-1]==FF D9`，否则共用一条`JPEG文件头尾校验失败`；错误处理`fail()`还会将图片缓冲与接收BitSet清空，难以定位错在头、尾、偏移、末尾附加字节或协议数据。用户的19:57:30错误发生于这段`PHOTO_END`校验，不代表GATT写失败、没AA15或已证实媒体损坏。
- **上游 E09 原版**：`sctg-development/ai-smart-glasses-e09-reverse:Sources/e09_reverse/BleProbe.swift`（MIT许可）`parseFileFrame`从`52 58...`取`0x97/0x98/0x99`，用0x98前四个大端偏移重组，`PHOTO_END`后即标记传输完成，**不强制 JPEG EOI 必须落在最后两字节**；`AdditionalValidations.swift` 明确记录实际转输长度与PHOTO_START声明长度在其E09样机上可能存在 **+500字节**差异。**不能据此证明本台M02S也是+500**，其当前实际接收总长度未在日志显示。v0.1.11是我们额外加入严格校验，不是开源原版直接失败。
- **最小后续修复建议（尚未授权/未开发）**：新独立v0.1.12只纠正AA15接收器：PHOTO_END先保留分片和分片总长度/宣布长度差，分别记录SOI/EOI标志、完整连续区间、BitmapFactory解码成功/分辨率（不输出照片字节、敏感数据、不落盘）；对附加尾字节安全尝试解码完整负载，必要时有限检测真实JPEG EOI并按验证结果处理，避免无依据地强制砍500字节；独立JUnit覆盖正常JPEG、EOI后合法尾随字节、缺包/乱序/越界/异常头。不要动旧成功AP原图链路和RTSP实验，也不要让用户重复v0.1.11已有的AA15“能触发”实验。**本次只归档新真机证据，未修改/构建应用。**
- **当前阶段**：原厂旧AP高清原图已真机成功一次，E09 AA15直传已经真实收到数据但未解码。是否低清晰度、是否适合OCR仍未知。RTSP `0x67/0x30` 尚未真机验证。保持主库历史、所有旧实验Release、正式蓝星向导RC25和Windows OCR不变。

---

# 最新｜M02S E09 开源协议 AA15 直传 + RTSP 实验 v0.1.11（2026-11-14，已发布；待真机）

- **用户授权/方向纠正**：用户强烈要求停止在原厂慢速相册导入上反复缝补，直接依据已找到的真正开源项目重新制作独立实验包，优先验证 **蓝牙 AA15 直接收 JPEG** 和 **Wi-Fi RTSP 实时视频**，并关注前版第一次成功后第二次拍照无快门声的状态退出问题。原正式 Blue Star Guide RC25、Windows OCR、官方 HeyCyan、旧 v0.1.0～v0.1.10 的源码与 Release 均未改动。
- **开源来源实际核查**：直接读取 [sctg-development/ai-smart-glasses-e09-reverse](https://github.com/sctg-development/ai-smart-glasses-e09-reverse) 的 `README.md`、`Sources/e09_reverse/BleProbe.swift`、`Constants.swift`、`AdditionalValidations.swift`、`WifiMediaManager.swift`、`LICENSE`。该仓库 **MIT License**，原作者 Ronan Le Meillat / SCTG Development；新实验目录加入 `E09_SOURCE_LICENSE.md` 保存完整许可。E09 代码明示 `TAKE_PHOTO 0x22/0x31`，AA15 `52 58...58 52` 传输帧的 `0x97` PHOTO_START / `0x98` PHOTO_TRANS / `0x99` PHOTO_END、分片前4字节**大端偏移**；`APP_LIVE 0x67/0x30` 返回AA14 `0x25` Wi-Fi SSID后AP RTSP `rtsp://192.168.169.1/h264`；`fileDownloadFinish 0x44` `30 01`不清除媒体，而 `30 00`可触发删除且**必须禁止**。**这些来自另一型号 E09 的源代码，虽然M02S已有AA12/AA13/AA14/AA15服务及AP照片接口一致，仍未证明这些新指令适配 M02S。**
- **v0.1.10最后实测锚点**：M02S-3584/vivo X200 Pro/Android16，19:32:37→19:32:50 完成一次BLE拍照→AP onAvailable/私有网关→AP JSON相册→JPEG 3200×2400原图 **558744字节/475ms** 下载成功；之后19:33:26第二次点击蓝牙拍照 `0x22/0x30` **AA13写入status0但用户确认没有快门声**，截至19:33:31无AA14 `0x22/0x42`；第一次下载后软件仅释放手机AP Network，没有发眼镜侧完成通知，怀疑眼镜仍处于传输状态但非实证。这次不再重测原图GET成功能力。
- **v0.1.11代码隔离**：分支 `exp/m02s-e09-ble-photo-rtsp-v0111-20261114`，HEAD `77d7356f206411f32fb6a01c8a004369b715b83e`，独立包 `com.vobichngan.bluestar.m02se09lab`、versionCode12/name0.1.11。保留旧v0.1.10成功的BLE/AP原图管线作独立对照，不覆盖旧实验包。新白名单仅增三条来自开源 E09 的原帧：`0x22/0x31`、`0x67/0x30`、`0x44/0x30/0x01`；`0x44/0x30/0x00`删除指令明确拒绝。**不自动发送未知/OTA/删除命令，不自动连拍重试。**
- **新增实验1：AA15 BLE JPEG**：按钮「E09 蓝牙直传照片」，在GATT/AA14+AA15 CCCD订阅正常后只发一次 `0x22/0x31`，根据开源 E09 AA15 `0x97/98/99` 使用偏移分片拼图（防乱序/重复，拒缺块/非JPEG），最多2MB/15秒，只在完整 JPEG + Android Bitmap 解码后显示内存预览/尺寸/耗时。开源 E09 样机约12KB/320×180，不保证M02S支持或符合小字OCR；无AA15报文则不能声明高质量照片成功。
- **新增实验2：APP_LIVE RTSP**：按钮「E09 RTSP直播建连」向AA13发一次 `0x67/0x30`（**不是**普通相册Wi-Fi的`0x39/0x30`），复用 `0x25` 精确SSID、Android16独立local-only Wi-Fi Network和真实私有网关验证，然后仅在该 Network 的 socket 上向 `rtsp://<verified gateway>/h264` 发只读 RTSP OPTIONS/DESCRIBE（记录200/SDP含video状态）；按钮「播放RTSP画面」暂时将**此实验App进程**绑定到专属Network，通过Android MediaPlayer/SurfaceView尝试显示连续画面，结束即恢复默认进程网络。RTSP协议响应成功不等于实际视频播放成功；能播放也不代表连续高清OCR已实现。
- **新增实验3：非删除结束**：独立按钮「单独发送不删除结束指令」和「停止直播并安全退出」使用白名单 `0x44/0x30/0x01`（遵守现有5秒蓝牙命令间隔）尝试通知眼镜退出传输状态；**命令在M02S上的恢复效果待真机验证**。禁止发送 `0x44/0x30/0x00`，不提供主动删除任何眼镜媒体功能。旧原图照片全程不保存相册/不上传/不删除。
- **正式CI/发布真值**：[GitHub Actions 43591748480](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43591748480) `completed/success`，Gradle Android SDK36 编译、JUnit（包括AA15乱序/缺块、安全不删除命令）、APK v2签名/zipalign、包名/版本/权限、Release资产均通过。Tag `bluestar-m02s-e09-ble-rtsp-v0111-20261114`；APK `BlueStar_M02S_E09_BLE_RTSP_0.1.11_Android.apk` **181913字节**；SHA256 `ab0a64af29f5953517b06d49849877221a5c89522c4f9452bff25b804f5d30bb`；浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-e09-ble-rtsp-v0111-20261114/BlueStar_M02S_E09_BLE_RTSP_0.1.11_Android.apk 。
- **只需新能力真机实测**：退出HeyCyan→启动本实验→BLE LE1M + AA14/AA15 CCCD全订阅→一次「E09 蓝牙直传照片」，看AA15的START/TRANS/END/JPEG预览；停止后单独点一次「E09 RTSP直播建连」，看AA14 0x25/Network onAvailable/RTSP DESCRIBE，再点「播放RTSP画面」看画面是否真实持续；若发现眼镜不能再次拍，且原传输任务确已结束，可选择手动点击一次**不删除**结束指令并记录实际效果。测试中失败时只需日志，不要重复旧的蓝牙拍照、P2P、相册HTTP实验。**正式低延迟连续 OCR 朗读尚未完成，禁止以CI成功冒充设备兼容。**
- **仓库纪律**：main文档只保留历史并前置当前进展，旧所有实验分支/Release不改。下个版本必须先核实新协议真机响应，不要臆造M02S必然可播RTSP。 

---

# 最新真机｜M02S v0.1.10 首次完整一键成功，第二次拍照回执缺失（2026-11-14）

- **首次完整一键成功（用户提供 19:32 日志并明确表示成功）**：19:32:37 蓝牙AA13原厂拍照 `0x22/0x30` 写入status0、AA14 `0x22`、19:32:39 `0x42=00 0E`；19:32:42 请求AP信息，19:32:43 BLE解析到目标SSID并在本次Wi-Fi扫描中看到新鲜目标AP；保守等待1.8秒，19:32:45用v0.1.6已成功的SSID-only精准WPA2请求一次，19:32:48 Android目标 `Network onAvailable` 且读取真实私有网关；文件列表 `READY`、13图片候选；19:32:50 唯一最新候选连续两次稳定、原图 `HTTP200`、**558744字节/3200×2400/JPEG/475ms**、Bitmap成功解码且仅内存预览。首次本机没有拍前列表基线，仍不能单凭日志证明图片一定是本次新拍，用户已报告这次成功。**可确认 BLE→AP→原图一次性链路真实可用；不再重复研究原图HTTP协议。**
- **第二次接续失败（紧接第一次后约36秒，已拿到部分日志）**：19:32:50第一次成功后，软件的 `releaseOneClickAp()` **只释放手机侧** `ConnectivityManager NetworkCallback`、清Wi-Fi状态、卸载failure listener，**没有向眼镜发送原厂 `SendCommandViaBle.fileDownloadFinish`，也没有证明眼镜已退AP传输状态**；19:33:26再次BLE拍照 `0x22/0x30` 写回status0，但截至19:33:31 **没有AA14 `0x22`、`0x42`照片相关通知**，同时19:33:31 BLE `0x39/0x30` 请求写回0，并有AA14 `0x39`、`0x45`通知；蓝牙链路不等同于整体断开。19:33:30「已有照片任务进行中」只是第二次任务期间额外点击被拦截，非新快门失败的证据。当前用户日志截断于19:33:31，尚未看到第二次的0x25/Android onAvailable/HTTP下载结果，**不能宣称二次AP已连接或二次照片已下载**。最值得验证的假设是眼镜第一次取图结束后仍在AP传输状态，后续快门无反应；尚未实证排除其他原因。两次间隔约36秒，单纯继续加等待时长不应当成为默认方案。
- **直接回读源码证据**：v0.1.10源码 `experiments/m02s-wifi-lab/app/src/main/java/com/vobichngan/bluestar/m02slab/MainActivity.java` 的 `finishOneClickSuccess()` 调 `releaseOneClickAp()`，后者清空/注销手机AP Network请求，但没有官方结束传输通知；`startOneClick()` 只检查手机 `wifiMode == NONE`，无法检查眼镜固件工作状态；`onCommandWriteResult` 对拍照写回status0仍在最短5.5s后运行 `beginOneClickApStage()`，**未把AA14照片通知作为下一阶段成功条件**，因此二次快门状态不明也会请求AP。原厂HeyCyan此前已静态确认，在照片阶段完成后 `Ldp.stopP2pWifiConnect` 可能调用 `SendCommandViaBle.fileDownloadFinish` 并调用 `Lxxh.disconnectAp`；但是否适用此眼镜/具体顺序、是否必要/足以退模式仍需仅做静态核查和有边界真机实验，不允许未经确认发未知命令。
- **建议下一步（尚未授权新代码）**：把“第一次成功→下一次仍能遥控拍照”作为优先里程碑；先追踪原厂相册完成后的BLE退出传输协议及成功条件，再在独立v0.1.11里只做**完成传图后正确退出/恢复拍照模式的单点改动**和日志，失败不要把旧图误认为新图；不重复P2P、BLE基础、原图接口实验。用户最终目标仍是低延迟重复拍照/取图，最好后续能实现BLE+Wi-Fi按设备能力常驻；不得直接把原厂强制断连当成最终设计或声称软硬件不兼容。正式RC25/Windows OCR/旧Release全部不修改。

---

# 最新真机｜M02S v0.1.10 BLE 拍照→AP连接→原图下载成功（2026-11-14，用户实测）

- **用户实测结论**：v0.1.10 19:30 第一次一键流程在严格新扫描阶段 `NO_TARGET`，19:31:09 停止，未进行 AP Network 请求；随后用户同版本进行第二次真机流程，**完整打通一次BLE遥控拍照 → 扫描AP → Android AP onAvailable → 私有网关 → 官方原图列表 → 下载并解码显示 JPEG**。用户明确回复「这次成功了」。此成功并非首次一键拍照后立即成功，也不能当作无失败率/连续常驻连接验收。
- **第二次完整时序（M02S / vivo X200 Pro / Android16）**：19:32:27 GATT LE1M 164ms已连接；19:32:31 AA14订阅status=0；**19:32:37** 发送唯一已验证的蓝牙 `0x22/0x30` 拍照，GATT status0/40ms、收到 AA14 `0x22`；**19:32:39** `0x42=00 0E` 照片计数/序号候选（前次19:30:54为 `00 0D`）；**19:32:42** 发唯一 `0x39/0x30` AP请求；**19:32:43** BLE返回有效 `0x25` SSID，Android本次扫描更新并且取得**请求后新鲜目标热点扫描**；**19:32:45** 达到1.8秒等待，提交一次精确SSID、OEM WPA2的 local-only Android requestNetwork，已注册 Android14+ LocalOnlyConnectionFailureListener；**19:32:48** Android `onAvailable`、从目标 Network 的 LinkProperties 获得**私有网关**；同秒开始拉照片列表，第一次 `READY`、候选 **13 张**，无上次AP文件列表基线；**19:32:50** 唯一最新候选两次列表稳定、原图 `HTTP200`、**558744 bytes**、**JPEG 3200×2400**、**475ms**、匿名摘要 `1a78a75b48f3`，Android Bitmap 解码并在应用内存预览成功；随后仅释放手机端 AP 请求。该轮从19:32:37下发拍照到19:32:50预览完成约13秒，475ms仅是原图GET/传输耗时。
- **成功判断边界**：用户的「成功了」确认本轮流程可用；**首次没有拍前照片列表基线**，现有日志只能证实下载的是官方列表上可唯一选出的最新候选，并不在协议层证明该文件一定由本轮快门生成；若用户目视核对了拍摄场景，才能将因果进一步确认。AA14 `0x42/0x45/0x53`未完全解码，不能用它们伪称保存完成。连续工作、拍照后Wi-Fi始终常驻、第二次无需切换AP以及失败率均待另行验证。
- **此次仅交接更新**：**不修改源码、不构建新版、不重测以前已真机成功的BLE/AP/HTTP基础功能**。v0.1.10分支 `exp/m02s-ap-freshscan-failurecode-v0110-20261114`，Release `bluestar-m02s-ap-freshscan-v0110-20261114`，CI [43590174683](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43590174683)已成功。下一步优先分析第一次 NO_TARGET 与第二次成功的差异，以及用户目标的**低延迟、连续拍照、BLE+AP可能常驻**；不要因单次成功宣称已解决这些长期目标。正式Blue Star Guide RC25、原厂HeyCyan、Windows OCR、v0.1.3～v0.1.10版本一律不动。

---

# 最新｜M02S 拍照后 AP 新扫描与连接失败原因 v0.1.10（2026-11-14，已发布；待真机验证）

- **用户明确授权**：基于 v0.1.9 修改 AP 就绪判断、连接时序，制作独立新版；保持先 BLE 拍照、官方原图 GET、正式蓝星向导 RC25、Windows OCR、原厂 HeyCyan 和所有旧实验的代码/Release 不变；避免重复 v0.1.3 拍照与 v0.1.5/v0.1.6 Wi-Fi+原图已成功的实验。
- **最新用户实测 v0.1.9（不可混淆成 AP 未广播）**：19:20:47 蓝牙遥控拍照 AA13 `0x22/0x30` status0 + 真实快门声、AA14 `0x22`，19:20:49 照片计数候选 `0x42=00 0C`；19:20:52 发原厂 `0x39/0x30`，19:20:53 `0x25` 精确 SSID 解析；19:20:54 Android Wi-Fi 扫描发现**唯一匹配目标SSID**，BSSID-pinned request #1 至19:20:59 `onUnavailable`；随即同秒发 SSID-only request #2，19:21:03 `onUnavailable`。没有建立 AP Network/网关，未发生原图 GET；不能简单归因为热点没广播、密码或家用 Wi-Fi 并发，也不能断言 AP 模式禁拍。旧版扫描记录只限制30秒内，未核对是否在**本轮 AP 请求后**生成，可能是缓存。
- **本轮重要外部系统证据**：Android 官方 `getScanResults()` 可能返回上一轮扫描缓存；`SCAN_RESULTS_AVAILABLE_ACTION/EXTRA_RESULTS_UPDATED` 可表示扫描是否更新，`ScanResult.timestamp` 是开机后微秒计时。Android API34+ `WifiManager.addLocalOnlyConnectionFailureListener` 可接收 local-only `WifiNetworkSpecifier` 失败分类：association/authentication/IP provisioning/not found/no response/user reject/unknown；`ConnectivityManager.onUnavailable()` 本身无法区分原因。这是系统官方接口而非臆造厂商协议。AA14 `0x42`、`0x45`、`0x53` 的业务含义仍未核实，不作为拍照保存/AP就绪的确定信号。
- **新源码**：原仓库 `vobichngan285-collab/kanzi-assistant` 分支 `exp/m02s-ap-freshscan-failurecode-v0110-20261114`，HEAD `4de5eb80dc1264add6572e4188820fbd35d83582`，独立包 `com.vobichngan.bluestar.m02sapreadylab`、versionCode11/name0.1.10。只新增独立纯 Java `M02sApReadinessPolicy`/JUnit测试，改实验 `MainActivity.java`、`build.gradle`、`AndroidManifest.xml`、`README.md`、专用 GitHub workflow。
- **运行逻辑**：用户只需连接 BLE/AA14，不预连AP，单点「拍照→验证AP→取原图」；保持一次 `0x22/0x30` 拍照及>=5s AA13 间隔，只发一次 `0x39/0x30` 读取真实目标SSID。收到 `0x25` 后复用已连接的同SSID且真实网关的 Network（如存在），否则发起一次 Android 前台 Wi-Fi 扫描，比较目标 `ScanResult.timestamp` 与本次扫描请求时刻，**以前的缓存即使只有几秒也拒绝当作新广播证据**；仅接受精确SSID、有效唯一BSSID、过去10秒内且请求后出现的记录；首次新纪录后再等待**1.8秒**并复核目标仍在；仅使用旧 v0.1.6 已成功的 **SSID-only** local-only WPA2 网络请求，**本轮最多一次**，取消了 v0.1.9 失败后同秒 BSSID/SSID 立刻换法重试。12秒内无本次新扫描证据、多个不同BSSID同名目标则停止/说明证据不足，不猜热点、不发伪造命令。连接时注册 Android local-only failure reason listener；onUnavailable 后最多再等待 1 秒接受细分原因，然后停。只有专属 AP Network onAvailable+可信真实网关才继续复用官方 JSON 列表/原图只读 GET/内存预览，首次仍无拍照前基线，必须人工核对是否本次新拍。SSID/BSSID/密钥/照片/路径不进日志；不上传/删除/落盘，结束仅释放手机AP请求，不代表眼镜 AP 固件退出传输状态。
- **真实发布已核验**：GitHub Actions [43590174683](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43590174683) `completed/success`，Gradle `:app:testDebugUnitTest :app:assembleDebug`、Android SDK36、目标 APK package/version/permissions、v2签名、zipalign、ZIP 校验、Release资产检查全部通过。Tag `bluestar-m02s-ap-freshscan-v0110-20261114`；APK `BlueStar_M02S_AP_FreshScan_0.1.10_Android.apk` **160401 bytes**，SHA256 `b24a0f158d16afe7c554ffc09f117bf5691fbf5aa75873a3e13e149443fe3767`；浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-ap-freshscan-v0110-20261114/BlueStar_M02S_AP_FreshScan_0.1.10_Android.apk 。
- **下一步只需一次新版本真机串联**：退出 HeyCyan，在正常拍照模式连接 v0.1.10 BLE LE1M、AA14 订阅 status0，**不预先 AP**，对准新场景点击一次「拍照→验证AP→取原图」，留在 APP 前台观察真实快门声、AP阶段1的新扫描/旧缓存、AP阶段2/3的 Android failure-reason 分类及 onAvailable/网关；如果成功预览，人工确认场景。不让用户再断家用 Wi-Fi、重测基础P2P/蓝牙/原图。**当前 v0.1.10 未经真机验证，成功构建不等于故障已解决；常驻 BLE+Wi-Fi 连续高速取图仍未实现。**
- **仓库纪律**：main 仅前置交接，保留历史；旧实验分支/Release、正式蓝星向导 RC25、Windows OCR、原厂APK均未改动。下次接手先读此文，再核 HEAD/Actions/Release；未经用户进一步授权不改代码或构建。

---

# 最新｜M02S 原厂式 AP 扫描连接 v0.1.9（2026-11-14，已发布；待真机验证）

- **本次授权/边界**：用户批准继续制作独立 v0.1.9，修复 v0.1.8 拍照后 AP 网络连接失败；保留此前真机验证的 BLE 快门、官方 AP 图片列表和原图 GET，不改正式蓝星向导 RC25、Windows OCR、原厂 HeyCyan、旧实验分支与已发布 APK。
- **用户 v0.1.8 最新真机日志（不能混淆两次时间）**：18:51:40 单次 AA13 `0x22/0x30` 成功、用户听到拍照声，AA14 `0x22`、`0x42=00 08`；18:51:46 AP SSID（13 UTF-8字节）返回，Android `requestNetwork`，18:51:52 `onUnavailable`。第二次 **18:55:46** 单次拍照成功，AA14 `0x42=00 09`，18:55:52 再次获取目标 SSID 并提交 Android 网络请求，**18:55:57** 再次 `onUnavailable`，未得到真实AP网关，更未执行原图GET；18:56 手机 WLAN 截图与第二次失败仅间隔几秒，不可错用第一次日志说成过了4分钟。两次照片候选计数递增支持拍照执行，但 `0x42` 未最终解码；**不能**归因于家庭 Wi-Fi、未授权、密码、AP禁拍或照片下载错误，确切失败原因仍未知。用户已明确要求**不重复 v0.1.5/0.1.6 的成功连接/下载实验**。
- **本轮原厂对照依据**：用户上传原厂 APK `70465ff3819258a6218e7871da0a23f8.apk`（`com.starglasses.app v1.0.24-c`），`classes3.dex` 可直接找到 `findTargetScanResult` / `shouldWaitForStableTargetScan` / `connectWithSpecifier` / `notifyIfAlreadyConnectedToTarget` 等连接辅助方法；这是静态方法存在性及已有研究线索，不表示复制了原厂源码全套内部逻辑。原厂后续相册导入有缩略图/原图和主动断开 AP，不适合不加筛选地复制到低延迟常驻目标。
- **v0.1.9 新增范围（只改独立实验）**：按用户要求在 v0.1.8 上建立分支 `exp/m02s-oem-ap-scan-recovery-v019-20261114`，HEAD `01e3b5337f951a1a62331fc4cca88aecf1ae7c0c`，独立包 `com.vobichngan.bluestar.m02soemaplab`、versionCode10/name0.1.9。保留蓝牙一次性拍照 `0x22/0x30`、至少5秒 AA13 间隔和 `0x39/0x30`→`0x25` SSID；与原厂类似先检查精确目标Wi-Fi是否已连接并有私有网关，再做一次 Android 前台扫描、最多10秒观察精确匹配 SSID 的30秒内新鲜热点。唯一BSSID→可锁定BSSID申请 `WifiNetworkSpecifier`；多同名/扫描数据不可用→保留 v0.1.6 已验证 SSID-only 请求。Android `onUnavailable` 后最多观察6秒，**仅在确认精确目标唯一BSSID可见**时，允许一次切换 BSSID-pin ↔ SSID-only 的不同连接策略；绝不重发拍照或厂商Wi-Fi命令、无限重试、猜IP、连接邻近P2P/打印机。只有专属 `Network` `onAvailable` 且获真实私有网关才下载；失败日志匿名记录AP阶段1/2/3、连接请求序号、是否BSSID锁定，不打印SSID/BSSID/口令。首次没有拍前列表基线只显示最新候选供人工核对，不能宣称新拍已证明；图像只存内存。
- **CI/Release 经回查证实**：[Actions 43589236152](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43589236152) `completed/success`，Gradle JUnit + Android SDK36 编译、APK package/version/permissions/v2签名/zipalign/ZIP结构、发布资产确认成功。Tag `bluestar-m02s-oem-ap-recovery-v019-20261114`，APK `BlueStar_M02S_OEM_AP_Recovery_0.1.9_Android.apk` **155321字节**，SHA256 `4c21934114185fcedbb5b7fde856fc27439a6710d8ca27bf6a6489107d5301f9`，浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-oem-ap-recovery-v019-20261114/BlueStar_M02S_OEM_AP_Recovery_0.1.9_Android.apk 。
- **下一步只需一次真机串联**：关闭 HeyCyan，确保眼镜可正常拍照；v0.1.9 连 BLE、订阅 AA14，**不预先连接AP**，点一次「拍照→扫描AP→取原图」，保持 App 前台，听快门、查看AP阶段1/2/3、`onAvailable`/网关，若有预览人工核对新场景。若失败只需脱敏日志；不再建议用户切换家庭Wi-Fi或重测已证实的基础接口。正式常驻BLE+Wi-Fi并存/快速循环尚未实现，不能用本次CI结果冒充真机成功。
- **交接纪律**：main 此项只追加新记录，保留历史；不合并入正式蓝星向导，也不覆盖 v0.1.8 及以前的分支/Release；后续修改需另行授权。

---

# 最新｜M02S 先蓝牙拍照再AP取图 v0.1.8（2026-11-14，已发布，待首次真机验证）

- **最新用户实测结论**：v0.1.3 单独 BLE AA13 拍照确有快门声/AA14 0x22/0x42；v0.1.6 单独 AP 原图下载成功（3200×2400、532ms）；**v0.1.7 用户真机失败：预先连接AP时拍照无声，45秒没有新照片**。AP 抑制拍照仅是假设，不能当作已证实原因。
- **本次用户明确授权**：制作独立 v0.1.8，仅尝试 **先 BLE 遥控拍照 → 至少5秒命令间隔 → 0x39/0x30精确连接 AP → 只读下载**。未改变正式 Blue Star Guide RC25、Windows OCR、原厂 HeyCyan 或旧 v0.1.0～v0.1.7 分支/Release。
- **代码隔离**：新分支 `exp/m02s-ble-first-ap-fetch-v018-20261114`，源码 HEAD `dc55bfac785c9d154a27d330918f3ee73154e1f3`，基于 v0.1.7 的 `e094d2e1c845367d0da77860f115e5077304590d`。只修改 `experiments/m02s-wifi-lab/**` 和其专属 workflow；包名 `com.vobichngan.bluestar.m02sblefirstlab`、versionCode9/name0.1.8，独立共存。
- **行为与证据边界**：第一次启动时不能先通过AP获取拍照前列表基线，因此首次只能从拍照后的AP列表选择**唯一最新原图供人工核对**，不得写成“已验证新照片”；两次连续列表稳定后才下载。一次成功后保留进程内上一次AP列表供第二次比较新文件名，但仍不保证因果。AA13 status0和AA14 0x22/0x42仅为蓝牙层证据，**必须由用户真实快门声/场景核对**。优先输出前8条相关AA14通知，不再被常规前40条日志节流隐藏。原先严格照片路径/大小/格式检查、只用专属 AP Network、32MB上限、不保存/不上传/不删照片全部保留。
- **AP退出风险**：成功/取消时释放的是手机 `WifiNetworkSpecifier` / ConnectivityManager 的目标AP网络请求，**不代表眼镜固件已退出AP传输状态**。需在首次取图后再测下一次是否能拍照；若第二次无声，优先研究原厂 AP 退出/状态恢复逻辑，不重复旧 P2P 实验。不发未知 BLE/固件指令，不自动重复拍照。
- **真实 CI / Release 已核验**：[Actions 43586809286](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43586809286) `completed/success`；Gradle 单元测试+SDK36安装包编译、包名/版本/权限、v2签名、zipalign、ZIP完整性、Release资产均成功。Tag `bluestar-m02s-ble-first-ap-fetch-v018-20261114`；APK `BlueStar_M02S_BLE_First_AP_Fetch_0.1.8_Android.apk`，**145161字节**，SHA256 `b5bc190dfa38f2a5d2150c12cd1090cbf7ef7f6767b6cacdc3a6c6b82b287296`。浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-ble-first-ap-fetch-v018-20261114/BlueStar_M02S_BLE_First_AP_Fetch_0.1.8_Android.apk 。
- **下一步只做一次真机验证**：用户退出HeyCyan，确保眼镜普通拍照状态，启动独立 v0.1.8 → BLE LE1M连接/AA14订阅status0 → **不提前点AP** → 点击`蓝牙拍照 → AP取图`一次并保持实验APP前台 → 观察真实快门声、AA14响应、AP onAvailable/网关、两次稳定列表和是否预览刚拍场景。取图成功后再测第二次拍照是否恢复。首次没有前置列表基线，须人工核对场景。若失败，仅提供脱敏日志，不反复重试。
- **交接纪律**：本次代码构建已经完成并独立发布，真机 v0.1.8 还未测试，不可称硬件成功；main 只追加本段交接，不改正式源码。旧交接全部保留。

---

# 最新｜M02S 一键拍照取图 v0.1.7（2026-11-14，已发布，待首次真机串联测试）

- **首次原图成功的真实用户日志（v0.1.6，已实测，不再重复基础研究）**：vivo X200 Pro / Android16 / M02S-3584，17:55:58 GATT LE1M **85ms**成功、AA14通知订阅status=0，17:56:16发送官方AP命令`0x39/0x30`，17:56:17获取0x25/SSID，17:56:22 Android目标AP连接onAvailable并获私有网关；17:56:59 `GET /app/getfilelist` **AP JSON解析成功，5条目/5个图片/5个可排序时间**；17:58:13 再次解析并唯一选出最新图，**原图下载及Android Bitmap解码成功：HTTP200、481424字节、JPEG、3200×2400、532ms、摘要e884d52cc67e**，用户截图确实在v0.1.6 APP预览区域看到实物照片。由此可**正式确认原图下载和显示已真机成功**，但截至v0.1.6还不是拍照→取图的一键串联。
- **本次用户明确授权制作v0.1.7**：不新建仓库、不重复以前已成功的蓝牙/Wi-Fi/文件接口研究，以v0.1.6成果为基线实现一键拍照→确认产生的是**新照片**→下载内存预览。没有改正式Blue Star Guide RC25、原厂HeyCyan、Windows OCR或既有v0.1.0-v0.1.6分支及发布。
- **独立源码**：仓库`vobichngan285-collab/kanzi-assistant`；新分支`exp/m02s-oneclick-shot-fetch-v017-20261114`，HEAD `e094d2e1c845367d0da77860f115e5077304590d`，基于v0.1.6 `7db185627f2f0e2b9efe4bf152758e65819dd659`。只改`experiments/m02s-wifi-lab`和专用workflow；新增纯Java`M02sFreshPhotoPolicy`与JUnit测试；新独立包`com.vobichngan.bluestar.m02soneclicklab`，versionCode8/name0.1.7，与各版共存。
- **一键流程**：用户先用现有已验证流程连BLE和**AP热点精准连接**（不会自动重连AP），此后点击一次`一键拍照并获取新照片`。程序在发送拍照指令**之前**通过已连接专属`Network`请求官方AP JSON列表建立基线，失败**不发拍照命令**；若AA14监听已过120秒则自动重新开启通知，确保AA14订阅成功；用原先真机验证过的唯一原厂`M02sBleWire.photoThumbnail()`（AA13 `0x22/0x30`）**只发一次**，仍维持5秒最短发送间隔与4秒GATT写回执超时，回执非0/无回执不重试。
- **确认新照片而非旧照片**：收到GATT写入status=0之后才启动短时列表轮询，每次约1.2秒（不再发送任何拍照指令），最多**45秒**；只接受**拍照前基线里没有的新文件名**，图片大小至少32B且不超过32MB。多个新文件仅在创建时间可唯一确定最新时选择；否则继续等或超时，**不能随意拿旧列表里的最新照片**。同一个新文件必须在**连续两次列表响应中名称、字节长度和创建时间均一致**才开始一次原图下载；复用v0.1.6经真机验证的AP JSON解析、安全相对路径、专属WiFi Network + 实际私有网关、HTTP GET不跳转、JPG/PNG/WebP签名/解码/32MB限制、APP进程内存预览。只记录匿名数量/阶段/耗时，不输出SSID、照片名、时间值或照片字节。若同时有其他渠道拍照，单凭新文件名不能百分百证明因果，首次测试须避免同时按眼镜实体拍照键或运行原厂App。
- **状态与安全**：用户可点击`取消本次拍照任务`；蓝牙断开或AP改变立即停止；拍照失败、无新文件、文件大小不稳定、超时、图片GET失败均不回退旧图、不中途无限重试；一键任务运行中禁止同时手动列表/照片下载。原v0.1.6单次列表/手动原图按钮全部保留作对照。照片不落盘/不存相册、不上传云、不删除、不修改固件、无`fileDownloadFinish`、不自动断开AP。首次一键真机串联尚未做，不宣称已经成功。
- **真实CI核验/Release**：GitHub Actions [43583840263](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43583840263) `completed/success`；Gradle `:app:testDebugUnitTest :app:assembleDebug`（新单测覆盖不误取旧图、必须新文件、相同图片必须连续两次稳定、未完成大小、多个新文件歧义、安全选最新），SDK36构建、目标APK包名/version、权限、v2签名、zipalign、ZIP完整性和Release asset核验均通过。Tag `bluestar-m02s-oneclick-capture-v017-20261114`，APK `BlueStar_M02S_OneClick_Capture_0.1.7_Android.apk` **143945 bytes**，SHA256 `c714858a42869e49d25509ff66c8983e5151001c893f667e7bc8bfc3af1a8ffc`，**浏览器直链** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-oneclick-capture-v017-20261114/BlueStar_M02S_OneClick_Capture_0.1.7_Android.apk 。
- **下一步只做单次真机一键串联**：退出HeyCyan→启动v0.1.7→蓝牙扫描M02S-3584、LE1M连通→AA14订阅→AP精准连接直到onAvailable/真实网关成功→将眼镜对准**与之前不同的场景**→点击一次`一键拍照并获取新照片`，保持实验APP前台最多45秒（切换ChatGPT会导致Android定时任务停顿）。等待“拍照前基线获取成功”→“AA13写入status=0”→“新照片已确认”→“一键拍照取图完成”→“原图下载并解码成功”及图片预览。若失败，只提供新增脱敏日志，不要反复快速点击或再次做旧P2P实验。之后才能考虑接入蓝星云AI视觉朗读，仍需用户单独授权。
- **交接纪律**：保留PROJECT_HANDOFF.md全部历史，main只前置本次进度；RELEASE_STATUS.md在main根目录读取返回404，不应捏造；接手必须核对真实HEAD/Actions/Release。此次未修改正式Blue Star Guide、Windows OCR项目或原厂APK。

---

# 最新｜M02S AP 原图单次验证 v0.1.6（2026-11-14，已发布，待真机）

- **用户授权与目的**：基于v0.1.5已有真机成功链路，专门进行“从眼镜获取官方AP JSON照片列表→找到最新一张原始图→仅下载到手机APP内存并校验”的独立实验；不改蓝星向导RC25、不动Windows OCR、不覆盖HeyCyan和既有v0.1.0～v0.1.5实验。**本轮编译测试通过，不等于原图已经在M02S真机下载成功。**
- **前序已经由用户真机证实，无需重复基础研究**：BLE GATT LE1M + AA14通知 + 0x39/0x30→0x25读取眼镜SSID，Android目标AP连接onAvailable并获得私有网关（17:25:23）；只读GET /app/getfilelist 17:26:31 **HTTP200/99ms/635字节**；17:28:10至17:29:57**16次HTTP200**（29～278ms），摘要一致。第15→16次请求间约63秒空档，用户确认因为切换到ChatGPT聊天窗口；前后网络请求成功，但未证明后台定时调度稳定持续120秒。17:26:55大量AA14的0x46尚未确认语义，禁止臆断为照片数据。此前BLE远程快门已经用户真机验证。
- **OEM来源证据**：用户提供的真实 `/mnt/data/70465ff3819258a6218e7871da0a23f8.apk`（com.starglasses.app v1.0.24-c）classes5.dex 中重新检查到了 `app/getfilelist`、`app/getthumbnail?file=`、`PhotoApBean`、`FilesDTO`、`createtimestr` 等字符串；此前对原DEX的调用链追踪已明确AP列表是`GET /app/getfilelist` + JSON `info[] → files[]` 的 `name/size/createtimestr/type` 字段，P2P旧路径才是XML FileListParser；AP原图请求使用基地址+FileItem.name，而缩略图另走`app/getthumbnail?file=`。本版只用AP JSON/原图，不臆造其它私有命令。**真实635字节JSON具体内容尚未获得，因此是否完全吻合字段格式仍待测试。**
- **隔离开发**：新分支 `exp/m02s-photo-direct-download-v016-20261114`，源码 HEAD `7db185627f2f0e2b9efe4bf152758e65819dd659`，基于独立v0.1.5源码 `7914087ddd4cc97f8be01382b419f41d243e944a`；新增`M02sPhotoPolicy`、`M02sPhotoListReader`、`M02sPhotoTransport`和JUnit，修改实验`MainActivity`/README/manifest/build.gradle/独立workflow；正式蓝星向导RC25及既有所有发布不变。新独立包名`com.vobichngan.bluestar.m02sphotolab`、versionCode7、versionName0.1.6。
- **UI/行为**：沿用v0.1.5验证成功的蓝牙LE1M订阅及AP精准连接。新增`只解析照片列表`（仅在Android确认目标AP Network+网关后，GET本地官方AP JSON最大256KB）、`获取最新照片（自动）`（唯一候选或可靠时间能选出唯一最新才下载，不按未知列表顺序猜测）、匿名编号候选下拉框和`下载选中照片（手动）`（解决多个候选无时间或时间并列）、`清除内存预览`。列表日志只显示总条目/图片候选/可排序时间数，不打印JSON、文件名或时间实际值。
- **原图传输安全**：`M02sPhotoTransport`只通过**真实已建立的眼镜专属AP Network**访问`http://<verified private gateway>/<official relative photo name>`，请求只读GET且不跟随redirect、没有系统默认网络回退、没有猜IP/端口；对远程相对图片路径拒绝外部URL、绝对路径、..路径穿越、问号/井号/百分号/反斜杠以及非jpg/jpeg/png/webp；响应限制32MB，检查JPEG/PNG/WebP魔数和扩展名匹配、BitmapFactory尺寸合理（<=8000万像素），降采样仅为UI预览，原图字节保持在APP内存；取消Wi-Fi/清空预览/退出即丢弃。无媒体权限、不保存到相册或磁盘、不访问其它照片、不上传云端、不删除眼镜文件、不发downloadFinish、不主动断开眼镜AP。异常日志只含异常类型，不泄露私有响应或照片名。
- **自动验证与Release已完成**：GitHub Actions [43582530420](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43582530420) `completed/success`；Gradle `:app:testDebugUnitTest :app:assembleDebug`（包含JSON合成fixture解析、图片选择安全、路径限制、大小与去重相关单元测试）和Android SDK36编译通过；v2签名、zipalign、目标独立包名/版本、权限/无照片媒体权限、ZIP结构、GitHub release均已检查。Release tag `bluestar-m02s-photo-verification-v016-20261114`，APK `BlueStar_M02S_Photo_Verification_0.1.6_Android.apk` **127173 bytes**，SHA256 `acc047cbc3932a80c56a3225766f8f85293793a10d26773297768b3098b01310`；浏览器直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-photo-verification-v016-20261114/BlueStar_M02S_Photo_Verification_0.1.6_Android.apk 。
- **下一步只需单次真机验证，别再重测P2P/蓝牙快门**：安装v0.1.6→退出原厂HeyCyan→扫描M02S并连接LE1M→开启AA14监听直到status=0→`AP热点精准连接`直到Android onAvailable/网关到位→首先**点一次“只解析照片列表”**，检查是否出现“AP JSON文件列表解析成功，总条目N、图片候选M”；若失败直接发诊断，不反复折腾。若解析成功，再点一次**“获取最新照片（自动）”**；无法唯一判断最新时，手动选匿名候选并下载一张；成功应有本地图片预览、尺寸/耗时/大小、短摘要，才可断言真机原图已下载。不需要给助手任何照片内容、原始JSON、完整私有文件名或网络密钥。后续是否接入蓝星云端视觉/后台自动拍照下载，必须依据本轮结果另获授权。
- **仓库纪律**：main上的交接文档保留所有旧历史，本次仅前置追加记录；后续接手先核查真实分支、HEAD、Actions和Release，未经用户明确下一步指令不修改代码/启动构建。

---

# 最新｜M02S HeyCyan 照片下载协议静态研究（2026-11-14，仅分析/文档，无新APK）

- **用户最新 v0.1.5 真机结果（从用户本次完整日志核实）**：M02S-3584/vivo X200 Pro/Android16 使用原厂AP请求 `0x39/0x30`，AA14 `0x25` 解析SSID成功；权限 `CHANGE_NETWORK_STATE` 等均已授予，17:25:23 Android `onAvailable` 确认手机已连接目标 AP，读取真实网关成功；17:26:31 请求 `GET /app/getfilelist` **HTTP 200 / 99ms / 635B / SHA256前6字节摘要 `2ff17100c9bb`**。17:28:10～17:29:57 共 **16/16 HTTP 200**，耗时 **29～278ms**、635B及摘要全部一致。17:28:54→17:29:57 有约63秒探测空档，用户明确说明当时切换到了 ChatGPT 聊天窗口，后台调度受限是合理可能原因；随后请求仍成功，但**不能证明空档内Wi-Fi从未中断，也没有完整120秒每3秒稳定执行的证据**。17:26:55大量 `AA14 cmd=0x46` 通知，120秒监听共961条，含义未验证，不得解释成照片数据。用户既有原厂遥控拍照真机成功。**至此BLE控制、AP Wi-Fi连接、HTTP文件列表接口已由真机证实；照片二进制下载尚未验证**。
- **本轮直接重新检查原厂用户提供 APK**：文件 `/mnt/data/70465ff3819258a6218e7871da0a23f8.apk`（`com.starglasses.app` v1.0.24-c），实际解析 APK DEX 方法ID、class_data、指令操作码、Retrofit 方法注解与静态字段值，不依赖猜测或只靠搜索字符串。本轮是**纯协议研究**，未修改/构建正式软件或任何 v0.1.x 实验APK。
- **照片列表的两套不同接口/解析格式，重要纠正**：`classes5.dex Lgyh.getDevicePhotoList_Ap()` 的 Retrofit `@GET("app/getfilelist")` 用于 **AP**；`Lgyh.getDevicePhotoList()` 注解 `@GET("?custom=1&cmd=3015")` 用于旧 **P2P**。`Lt64.getDevicePhotoList` 按 `Lgw6.K` 选择二者。`Lu64$a.onLoadSuccess` 的 **AP分支** 使用 `PhotoApBean` 从 JSON `info[] → files[]` 取得每个文件的 `name`、`size`、`createtimestr`（`FilesDTO` 另有 `type` 字段），整理成 FileListParser.FileItem；**P2P分支** 才调用 `FileListParser.parseXml`，解析 `File/NAME/FPATH/SIZE/TIME/TIMECODE/ATTR` XML。**切勿在AP模式用 XML 解析用户那635B响应**。用户尚未提供635B实际内容，因此没有验证真实图片数量、文件名、返回时间戳内容，切勿编造。
- **真实AP下载地址及下载顺序**：`classes5.dex Lg30` 的静态字段 `h0="http://192.168.169.1/"`，`<clinit>` 把 `j0=h0+"app/getthumbnail?file="`、`k0=h0`；`Ldp.startDownLoadThumbnail()` 在 AP 分支用 `j0 + FileItem.getName()` 构造缩略图下载URL；`Ldp.startDownLoad()` 在 AP 分支用 `k0 + FileItem.getName()` 构造原始文件 URL；两者使用 `Lsk4$a` 构造下载任务后 `enqueue`，失败路径有 `retryDownloadPhotoByP2pDownloader` 调 `Ljub.downloadP2pFile` 的HTTP回退。注意原厂 `getFileNameWithoutPath(name)` 用于本地目标文件名，**不是裁剪远程URL**；不能未经实物响应就认为 name 格式必然是特定文件路径。
- **官方后处理与断连**：`Ldp.onGetDataSuccess` 保存文件列表并进入 `startDownLoadThumbnailPhotoByWifi`；`Ldp.startDownLoadThumbnailPhotoByWifi` 在缩略图阶段完成后会启动 `startDownLoadPhotoByWifi` 原图阶段；后者完成时通过延迟400ms运行任务进入 `stopP2pWifiConnect`。该函数会按情况调用 **`SendCommandViaBle.fileDownloadFinish`**，并对AP路径调用 `Lxxh.disconnectAp`。因此 **首次独立照片下载实验不应照搬官方自动断连/完成通知**，以免破坏当前已成功的保持连接测试；只有下载确实完成后再研究其必要性。
- **下一版建议（仍待用户另行授权，未经允许不写代码/不构建）**：以 v0.1.5 独立分支为基线，在新隔离实验 v0.1.6 中使用真实已验证 AP `Network` 和网关读取 `/app/getfilelist`，**只在内存里JSON解析** `info[].files[]` 的 `name/size/createtimestr/type`，按图像后缀及时间安全筛选最近一张；不输出完整文件名或任何用户数据。用户单击后，构造官网源码所示的 AP 原图URL `base + name`，**只读一次 HTTP GET**，对URL/目标主机/路径做约束、限制最大响应字节、校验JPEG/PNG签名、可选仅预览/摘要，默认不保存到相册、不删除、不上传云端、不发送 `fileDownloadFinish`、不自动断AP。若服务端路径/格式与推断不同，按实测日志修正；需要完成一次真机照片字节验证后才能宣称“下载照片成功”。不要为实现OCR重复构建缩略图/复杂转存链路，优先“原图→内存→后续视觉识别”的简单流程。正式蓝星向导 RC25、Windows OCR、官方 APK、旧实验一律保持不变。
- **仓库现状核对**：`main` 为文档交接分支，独立 v0.1.5 源码 `exp/m02s-ap-permission-fix-v015-20261114` HEAD `7914087ddd4cc97f8be01382b419f41d243e944a`，其 Actions [43580262307](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43580262307) `completed/success`，公开 APK Release `bluestar-m02s-ap-permission-fix-v015-20261114`。本次只写交接说明，旧历史原样保留。

---

# 最新｜M02S Android 16 AP 权限修复 v0.1.5（2026-11-14，已发布；待真机验证）

- **此次用户明确授权**：修复 v0.1.4 AP 热点精准连接中出现的 `SecurityException`，提供可独立安装 APK，不碰正式蓝星向导、官方 HeyCyan、旧实验及 Windows OCR。使用原仓库 `vobichngan285-collab/kanzi-assistant`，**新独立分支** `exp/m02s-ap-permission-fix-v015-20261114`，源码 HEAD `7914087ddd4cc97f8be01382b419f41d243e944a`（基于 v0.1.4 `f65932bea53b36b7bd04960c468cde89c6b5bd10`）。只改实验文件 `experiments/m02s-wifi-lab` + 该实验专用 workflow。
- **原始手机实测**：v0.1.4 / vivo X200 Pro / Android 16：17:17:31 GATT LE 1M 147ms 成功，17:17:34 AA14 CCCD `status=0`，17:17:40 AA13 原厂AP指令 `0x39/0x30` 写入 `status=0`、AA14 收到 `0x39` 和 `0x25`（**SSID UTF-8 13字节，已成功本地解析**），紧接着**“AP 请求异常：SecurityException”**并停止会话，未弹 Android 网络授权、未建立 Wi-Fi。P2P 已单独实测找不到眼镜，不能再次误将附近 HP `DIRECT-92-HP M132f LaserJet` 打印机当眼镜。
- **静态代码查到确切漏项，但根因仍需真机确认**：v0.1.4 `AndroidManifest.xml` 已包含 `ACCESS_NETWORK_STATE`、`CHANGE_WIFI_STATE`、`ACCESS_FINE_LOCATION`、`NEARBY_WIFI_DEVICES` 等，**缺少 `android.permission.CHANGE_NETWORK_STATE`**。调用 `ConnectivityManager.requestNetwork` 时该权限通常必需；本轮只将缺失权限定为首要嫌疑，**不能宣称已完全确定手机安全异常的唯一原因**。
- **v0.1.5 修改**：Manifest 新增标准普通权限 `CHANGE_NETWORK_STATE`。在 `connectTargetAp` 请求前诊断 `CHANGE_NETWORK_STATE`、`ACCESS_NETWORK_STATE`、`NEARBY_WIFI_DEVICES`、`ACCESS_FINE_LOCATION` 的授权状态；缺少关键网络权限立即安全停止。记录三个独立出错阶段（构建 `WifiNetworkSpecifier` / `NetworkRequest` / 提交 `requestNetwork`），捕获 SecurityException / IllegalArgumentException / IllegalStateException 时仅记录类型及阶段，**不记录原始异常消息**，避免 SSID/密码泄露。独立单测覆盖阶段、权限状态及敏感消息不泄露。保留之前成功的 BLE 拍照和AA14/0x25解码、AP/P2P独立目标连接、非眼镜设备拦截、未建立Wi-Fi禁止 HTTP 的逻辑，**没有增添其他未证实的厂商命令**。
- **独立新包与真实CI核验**：包名 `com.vobichngan.bluestar.m02sapfixlab`，versionCode 6 / `0.1.5`，与正式版和旧 v0.1.0~0.1.4 版本共存；CI [43580262307](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43580262307) `completed/success`，Gradle Java JUnit、Android SDK36 构建、APK v2 签名、zipalign、ZIP完整性、实际产物Manifest的 `CHANGE_NETWORK_STATE/ACCESS_NETWORK_STATE/CHANGE_WIFI_STATE` 等权限、包名/版本核验通过。GitHub Release tag `bluestar-m02s-ap-permission-fix-v015-20261114`，APK `BlueStar_M02S_AP_Permission_Fix_0.1.5_Android.apk` **99833 bytes**，SHA256 `5ddc0359a64c2b995507e1dd703d8da3c362931dfb05fee1cb4644dc36d06ad4`；**浏览器直链** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-ap-permission-fix-v015-20261114/BlueStar_M02S_AP_Permission_Fix_0.1.5_Android.apk 。
- **下一步测试仅 AP，不再重复 P2P/拍照**：用户关闭官方 HeyCyan → 安装启动 v0.1.5 → 扫描并连接 M02S LE 1M → 开启蓝牙反馈 120秒直到 AA14 CCCD status=0 → 点击一次 **「AP 热点精准连接」**。检查日志是否显示 `CHANGE_NETWORK_STATE=已授权`，并观察 Android 是否弹出联网许可及 `onAvailable`；若弹系统窗口由用户确认。若仍失败，要求只回传“AP系统权限预检”及“AP系统调用失败：阶段=... 类型=...”，**不得推定已连接 Wi-Fi**。只有 Android 确认目标网络并由该网络路由获得私有设备地址后，才能单次只读文件列表。照片自动传输、Wi-Fi 长连接仍未实现。
- **交接纪律**：必须先看 main 此文及 RELEASE_STATUS 等实际仓库状态；后续修改先取得用户授权。重要进展追加保留旧历史，不覆盖旧版本/源码。该次文档只增新章节，旧内容原样保留。

---

# 最新｜M02S Wi-Fi 目标精确连接 v0.1.4（2026-11-14，已发布，待真机）

- **用户批准执行，本次新增重点**：v0.1.3 已由用户亲测 **AA13 BLE 遥控 M02S-3584 眼镜确实拍照**，GATT LE 1M 连接93ms，写入58ms，回执 `0x22`，计数候选 `0x42` 由4到5；P2P Wi-Fi 信息请求 `AB 55 00 03 39 31 6A` 收到 `0x39` 和约0.45秒后的 `0x25`（13字节负载），但**没有建立 M02S Wi-Fi**。系统先前“发现1台P2P设备”经用户截图确认为 `DIRECT-92-HP M132f LaserJet` **HP打印机**，必须避免误连。
- **依据真正的官方 APK**：本轮再次在本地直接打开用户上传的 `/mnt/data/70465ff3819258a6218e7871da0a23f8.apk`（`com.starglasses.app` v1.0.24-c），用 DEX 静态字节码追踪确认 `classes2.dex: ModBleResponse.onRequestSyncDataResponse` 的 0x25 分支→`WifiCmdBean.recoverByBytes(leHeader.payload)`→整段载荷以 `UTF-8` 解成 `ssid`→`OnWifiDataListener.onWifiData(ssid,常量连接口令)`；`classes5.dex: Ldp.connectWifi` 会根据 `Lgw6.K` 走 `Lxxh.connectApwifi`（`WifiNetworkSpecifier/ConnectivityManager.requestNetwork`）或 `Ldyh.connectToDirectWifi`（`WifiP2pManager.discoverPeers/connect`）；`classes3.dex: Ldyh$e.lambda$onPeersAvailable$0` 确有 `WifiP2pDevice.deviceName.contains(ssid)` 的筛选。 `CustomerCmdBean.project` 包含 `T` 与否影响 AP/P2P 模式，但**此台 M02S 的 project 尚未实机获取，不能直接断言路由**。
- **隔离开发成果**：独立 GitHub 分支 `exp/m02s-wifi-target-connect-v014-20261114`，HEAD `f65932bea53b36b7bd04960c468cde89c6b5bd10`，基于 v0.1.3 已验证的 `17d02883f191a2df080c1e9d209845ed8e91c38b`；仅改 `experiments/m02s-wifi-lab` 实验和独立工作流。新**独立包名** `com.vobichngan.bluestar.m02swifitargetlab`，versionCode5/versionName 0.1.4。**正式蓝星向导 RC25、官方 HeyCyan、Windows OCR、v0.1.0-0.1.3 既有分支和 Release 均未更改。**
- **行为**：保留真机成功的 BLE LE 1M 和 AA14 订阅；用户先手动选择 **“P2P 精准连接”或“AP 热点精准连接”**，程序只发送已知白名单 0x39/0x31 或0x39/0x30，收到 AA14 **0x25 完整帧且校验通过**后才用严格 UTF-8 安全解析 SSID，全部驻留内存，不显示/持久化 SSID 或连接口令。P2P 只匹配 `deviceName.contains(完整可信SSID)` 的唯一设备，SSID 长度8+字符，拒绝打印机、非匹配和多个同名目标；仅在 **`groupFormed` 且手机非组主**并获得经 `ProbePolicy` 限制的私有组主IP后报告 P2P 成功。AP 仅对准确 SSID 使用 `WifiNetworkSpecifier` / 官方连接凭证发起 Android local-only `requestNetwork`，可能需要用户系统授权；仅 `onAvailable` 后报告 AP 网络已建立，网关从该 AP 的 `LinkProperties.getRoutes` 中获取。**不会自动在AP/P2P间轮换、不会重发猜测指令。**
- **安全与日志**：不在日志打印 SSID、口令、来自0x25的原始负载、相邻P2P设备名或完整设备列表；隐私以长度/是否匹配代替。未有目标网络连接确认 + 私有地址前，`/app/getfilelist` **完全禁用**；允许后也只读取文件列表状态、长度、短摘要，不取原图、不上传云端、不修改固件/删除文件。WiFiLock 只能影响手机，不能保证眼镜固件不关Wi-Fi。取消会释放 Android AP 网络请求及实验建立的 P2P 组。
- **CI/Release 真实检查**：GitHub Actions [43578933763](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43578933763) `completed/success`；Java JUnit（SSID UTF-8 与不匹配打印机、敏感数据保护、AA14 0x25 解析）、Android SDK36 Gradle 编译、v2 APK 签名、zipalign、包名/版本、ZIP 检查及 Release 校验全部通过。Tag `bluestar-m02s-target-wifi-v014-20261114`，APK **97149 bytes**，SHA256 `85857b25de65f1ee52cee5d1d573d725efaee383107eff7acaa19ec64411ec3c`；浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-target-wifi-v014-20261114/BlueStar_M02S_Target_WiFi_0.1.4_Android.apk 。
- **下一步用户真机测试，现阶段不可宣称 Wi-Fi 已连接**：在 vivo X200 Pro 关闭官方 HeyCyan → 启动 v0.1.4 → 扫描 M02S 并「连接 LE1M」 →「开启蓝牙监听（120秒）」等 AA14 CCCD status=0 → **先只点「P2P 精准连接」**。系统会接收并脱敏 0x25 SSID、目标匹配、自动尝试精准连接；若没有匹配/失败，可独立取消后重新开启监听试 **「AP 热点精准连接」**并确认 Android 弹窗。只需回传 `0x25` 有效/匹配/网络状态，若 Wi-Fi 连接成功且得出设备私有IP再手动点「只读文件列表」。真机 WiFi/照片传输/持续连接仍待验证。

---

# 最新｜M02S HeyCyan Wi-Fi 接续协议静态核查（2026-11-14，只分析未改代码）

- **用户现场最新结果**：v0.1.3 通过蓝牙对 M02S 发送官方拍照命令后眼镜确实执行一次拍照；发送 `AB 55 00 03 39 31 6A` 后，AA14 先返回 `0x39`，随后约0.45秒出现一条完整校验通过的 `AC 55 ... 25 ...`，**有效载荷13字节**。系统 P2P 搜索到1台设备，用户已截图证实是 `DIRECT-92-HP M132f LaserJet` **惠普打印机，不是 M02S**。目前没有建立 M02S Wi-Fi，不能将发现打印机当成眼镜 Wi-Fi 成功。
- **此次从原始用户上传 HeyCyan APK 直接重新读取 DEX**：`/mnt/data/70465ff3819258a6218e7871da0a23f8.apk`（284MB级安装包，包含 classes.dex～classes7.dex）。针对 `classes2.dex` 的 `ModBleResponse.onRequestSyncDataResponse(Datagram)` 实际检查 **sparse-switch 分发表**：命令 **0x25 跳转 code-unit 760**，实例化 `WifiCmdBean`，以 `leHeader.payload` 调 `WifiCmdBean.recoverByBytes`，取出 `ssid`，然后调用 `OnWifiDataListener.onWifiData(ssid,预设连接口令)`；`WifiCmdBean.recoverByBytes` 本身将**整段载荷** `HexConvert.convertList2Array` 后直接以 **UTF-8** 构造 `String`。这证明 **0x25 是原厂 SSID 数据帧**，而不是未知事件；v0.1.3 为避免泄露 SSID 已对0x25自动脱敏，因此既有日志无法还原真实字符串。注意 0x39 是 Wi-Fi 请求对应响应，**0x25才实际携带 SSID**；当时13个字节并不等于已验证的13个可见字符。
- **后续连接调用链核对**：`classes5.dex` 的 `Lyo.onWifiData(ssid,password)` 只调用 `Ldp.connectWifi(ssid,password)`；`Ldp.connectP2pWifitoReceivePhoto()` 在发 Wi-Fi 请求前根据 `Lgw6.K` 在 `GET_WIFI_AP` 与 `GET_WIFI_P2P` 间分支。收到 SSID 后 `Ldp.connectWifi` 同样用 `Lgw6.K` 选择 `Lxxh.connectApwifi` 或 `Ldyh.connectToDirectWifi`。前者（`classes3.dex`）在 Android Q+ 利用 `WifiNetworkSpecifier/ConnectivityManager.requestNetwork` 和目标 SSID 走**热点连接**；后者主动 `WifiP2pManager.discoverPeers`，取得匹配设备后调用 `WifiP2pManager.connect` 走 **Wi-Fi Direct**。**官方 App 不会仅请求 Wi-Fi 信息后就停止，而是用收到的 SSID 开启明确的连接链路**。此前实验 v0.1.3 没有解析/使用0x25 SSID，所以扫描出惠普打印机不能判定 M02S 的 AP 或 P2P 模式失败。
- **官方如何选择模式**：`ModBleResponse.onRequestSyncDataResponse` 解析 `CustomerCmdBean.project`，检查是否包含 `T`，据此写入 `Lgw6.K`；**包含 T 时 K=true → AP 路径；否则 K=false → P2P 路径**。这是原厂代码选择机制，但用户现有 BLE 诊断尚未采集 M02S 的 `project` 值，**不能擅自确认此台 M02S 必然使用哪一种模式**。
- **断连的重要线索**：`classes5.dex` 中 `Ldp.startDownLoadPhotoByWifi` 完成阶段有 `runOnUiThreadDelayed(...,400ms)` → `Lcp.run` → `Ldp.stopP2pWifiConnect`，按 AP/P2P 模式执行对应断开；这是每次官方导入重新花费连接时间的代码证据，但眼镜固件是否另行断连仍须测试。
- **研究结论与下一步拟议（未授权代码修改/构建）**：不用再重复 BLE 建连或盲扫打印机。建议隔离开发 v0.1.4，以**仅内存**形式安全解析 `0x25` 的真实 UTF-8 SSID（日志只显示长度/是否匹配/隐藏敏感数据）、基于真实眼镜名称尝试 **AP/P2P 两条明确区分的连接路线**，优先按设备 `project` 标识选路；需有用户点击确认，记录 Android 网络回调、设备类型、真正的 Network/IP/网关，**成功后才**允许 HTTP 文件列表测试；禁止凭蓝牙写入成功推断 Wi-Fi 已连接。正式 RC25、官方 APK、旧实验均不得改动。此次已完成静态分析，**没有创建新 APK 或发送额外指令**。

---

# 最新｜M02S 原厂 BLE 拍照及 Wi-Fi 请求指令专项 v0.1.3（2026-11-14，已发布，待真机）

- **真机既有成功结果（用户亲测）**：v0.1.1/v0.1.2 在 vivo X200 Pro/Android16 上使用 `TRANSPORT_LE + PHY_LE_1M` 对 M02S-3584 GATT 连接成功（112ms、143ms），找到服务 `1800/AE00/AA12/180F`，厂商 AA12 下 `AA13` 为有回执写入、`AA14/AA15` 可通知；原厂协议状态 AA14 的 CCCD 订阅 0 状态成功，拍照实体键试验重复收到 `AC 55 ... 22 01`、`45 01`、`42 00 03/04`、`45 00`，验证接收链路。**这是眼镜实体键事件，不等同于已验证远程控制拍照。**
- **本次用户授权**：对之前上传的官方 HeyCyan APK `/mnt/data/70465ff3819258a6218e7871da0a23f8.apk`（`com.starglasses.app` v1.0.24-c）做**真实 DEX 指令流/静态字段值检查**，然后在独立 APK 中仅做可确认的遥控拍照/请求 Wi-Fi 参数的小范围实验，不改正式蓝星向导 RC25、旧 v0.1.0～0.1.2、原厂 APK 或眼镜固件。
- **APK 代码中已经核实，而非猜测**：
  - `classes2.dex: SendCommandViaBle.takePhoto(I)` 将命令 ID **34/0x22** 和传入参数送入 `configSendCommand`；原厂 `DeviceFragment` 将 `TAKE_PHOTO_THUMBNAIL=48/0x30` 传入。对应**待真机验证**的 AA13 写入帧 `AB 55 00 03 22 30 52`。
  - `SendCommandViaBle.getWifiInfo(I)` 命令 ID **57/0x39**；`Command.Param.GET_WIFI_P2P=49/0x31`、`GET_WIFI_AP=48/0x30`，`dp.connectP2pWifitoReceivePhoto()` 会依据设置实际调用两种之一。对应**待真机验证**帧分别是 `AB 55 00 03 39 31 6A`（P2P 信息）、`AB 55 00 03 39 30 69`（AP 信息）。这**不能保证眼镜 Wi-Fi 已启动或连通**。
  - `Datagram.convertLeHeaderToBytes()` 构成 `AB 55` + 长度大端2B（命令+数据+校验） + 命令码 + 参数 + 字节累加模256的校验；`BleCore.setupCharacteristics()` 的官方 `UUID_CMD_WRITE=AA13`、`UUID_CMD_NOTIFY=AA14`、`UUID_PHOTO_NOTIFY=AA15`，与真机发现一致；官方 `WifiCmdBean` 解析 Wi-Fi SSID，存在连接层口令常量（**不要在用户诊断日志中曝光或推断一定适用于 M02S**）。
- **独立实现**：分支 `exp/m02s-verified-ble-commands-20261114`，源码 HEAD `17d02883f191a2df080c1e9d209845ed8e91c38b`（基于 v0.1.2 HEAD `9355aed1ad5b618a65ec05e4c2197150673f3c0f`），只修改 `experiments/m02s-wifi-lab` 源码/单测/说明和独立 GitHub workflow，不改 RC25。全新包名 `com.vobichngan.bluestar.m02scommandlab` version **0.1.3**，与之前版本共存。验证通信端点只使用 AA13；用户手动发送**仅三条静态白名单指令**，强制先 GATT/AA14 CCCD 订阅成功；5 秒最小发送间隔，4 秒写超时不自动重试，GATT 写入回执与真正拍照/Wi-Fi 行为严格分开，接收通知按 AC55 帧解析，Wi-Fi/不明 payload **脱敏**，仅已知拍照状态小包输出参数。保留旧 Wi-Fi 功能作对照但不自动下载照片/上传云端，不发删除、断开、恢复出厂或其他未验证指令。
- **发布已完成**：CI [43576429533](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43576429533) `completed/success`，JUnit、Gradle Android SDK36 编译、签名 v2、zipalign、正确包名/版本、ZIP 检查、GitHub Release 均通过。APK `BlueStar_M02S_Verified_Commands_0.1.3_Android.apk` **80581 bytes**，SHA256 `7c3e16f0ee0b048155c4451df511bb2cde17ce207db2063a6e87a5a3bdd9071a`；[Release](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-m02s-verified-commands-v013-20261114)；[浏览器直链](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-verified-commands-v013-20261114/BlueStar_M02S_Verified_Commands_0.1.3_Android.apk)。
- **下一步用户真机验证**：退出原厂 HeyCyan，打开 v0.1.3 → 扫描选 M02S → 连接 LE1M → 开启 120 秒监听，确认 AA14 CCCD status=0 → **单击一次“遥控拍照：原厂模式”**，观察是否实际生成照片，以及 AA14 回执码。若成功，重开监听，单击**“请求 P2P Wi-Fi 信息”**，查看通知是否包含 0x39 或其他事件，观察系统 P2P 是否发现眼镜；如有必要单独手动试 AP。只需回传相关 GATT/AA14/P2P 日志，不重复以前的低价值扫描/随机 IP 超时实验。**CI 成功不等于真机遥控成功，Wi-Fi 长连接仍未验证。**

---

# 最新｜M02S 蓝牙协议观察 v0.1.2（2026-11-14，已发布，待真机）

- **用户新授权及已确认事实**：用户 vivo X200 Pro / Android 16 的 v0.1.1 实测已经证明 `M02S-3584` 通过 `TRANSPORT_LE + PHY_LE_1M` 成功连接 GATT，`status=0,newState=2`，单次耗时 **112ms**，成功发现服务 `1800`（1特征）、`AE00`（2特征）、`AA12`（3特征）、`180F`（1特征），服务发现 `status=0`。因此**不再测试默认旧连接方式**，下一目标检查厂商特征/只读收取拍照动作通知，仍不碰正式蓝星向导。
- **眼镜原厂 APK 静态交叉核对**：已实际读取会话上传的 `/mnt/data/70465ff3819258a6218e7871da0a23f8.apk` 的 DEX：`classes2.dex` 包含 `0000aa12-...`、`0000aa13-...`、`UUID_CMD_NOTIFY`、`UUID_PHOTO_NOTIFY`，`classes4.dex` 包含 `0000ae00-...`、`0000ae01-...`、`0000ae02-...`，与真机实际服务 UUID 具相关性。**静态字符串不等于已逆向成功，不能把特征功能或 Wi-Fi/快门命令内容当成确证**。本次代码不发送任何猜测指令。
- **独立分支/提交**：仓库 `vobichngan285-collab/kanzi-assistant`，只改 `experiments/m02s-wifi-lab` 实验子项目及其独立 workflow；分支 `exp/m02s-ble-protocol-lab-20261114`，源码 HEAD `9355aed1ad5b618a65ec05e4c2197150673f3c0f`，基于成功 v0.1.1 的 `639cb4db3f15e788baffa284725a79ae1d60e057`；**独立新包名** `com.vobichngan.bluestar.m02sprotolab`，versionCode 3、versionName 0.1.2，与 v0.1.0、v0.1.1 共存。正式安卓 Blue Star Guide RC25、HeyCyan APK、旧实验、Windows OCR 均未修改或覆盖。
- **实现功能**：保留已验证的 GATT LE 1M 连接，连接成功后列表显示 AE00/AA12 各特征的 UUID、读写/通知/指示属性、标准 CCCD 描述符及其他描述符 UUID；只列举元数据不读取厂商特征值。用户手动点「监听拍照通知（60秒）」后，仅对带 NOTIFY/INDICATE 且 CCCD(0x2902) 的厂商特征顺序执行**标准订阅描述符写入**（不是厂商控制命令），5 秒超时保护；回调记录来源 UUID、长度、距监听开始毫秒数和**最多20字节** HEX（前40条），60秒自动停止日志。用户可按眼镜实体快门测试新照片状态是否经蓝牙通知；不主动遥控拍照、不开启眼镜 Wi-Fi、不下载/上传照片、不改固件；HEX 日志可能含敏感字节，分享前应自行检查。
- **GitHub Actions 构建**：[43574820781](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43574820781) `completed/success`，Gradle 单元测试、Android编译、APK v2签名/zipalign、正确包名与版本、ZIP完整性和公开 Release 均通过。**仅证明代码能编译并通过构建检查；没有证明实际设备会产生通知**。
- **Release 及直链**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-m02s-ble-protocol-v012-20261114 ，APK https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-ble-protocol-v012-20261114/BlueStar_M02S_BLE_Protocol_Lab_0.1.2_Android.apk ，文件 **71333 bytes**，SHA256 `8cbee4d8820cd08a2e059261089dc96a099f0475b7bdea747d35fa2258430399`。
- **待用户测试**：退出官方 HeyCyan → 新版扫描 M02S → 选中并连接 LE 1M →「查看特征 UUID/权限」→「监听拍照通知（60秒）」→**等 CCCD 订阅回调 status=0** → 轻按一次眼镜实体拍照键 → 复制最近的特征/订阅/通知日志；无需测试 Wi-Fi 部分。如果无通知，不等于眼镜不支持遥控拍照或 Wi-Fi，只是这个订阅测试没收到事件，应依据 GATT 属性进一步研究官方私有协议。

---

# 最新｜M02S 蓝牙 GATT 连接专项 v0.1.1（2026-11-14，已发布；待真机）

- **用户真机反馈：** M02S-3584（Android 16 / vivo X200 Pro）在独立 v0.1.0 BLE 多次可发现，RSSI 约 -48 至 -50dBm，但主动 `connectGatt` 于 15:56:06 发起，15:57:06 才返回 `newState=0, status=1`；**无任何 GATT UUID**。旧版请求眼镜文件列表均 HTTP 超时，但没建立眼镜 Wi-Fi，不得以此断定固件不可连接。状态1表示失败，具体根因不能仅据该值确定。
- **本次授权只做连接专项实验**：独立分支 `exp/m02s-gatt-connection-lab-20261114`，HEAD `639cb4db3f15e788baffa284725a79ae1d60e057`。在旧实验 `3c44874f4fdfe43ba5f232e0f193bc1eb7cb4b88` 基础上，修改 `experiments/m02s-wifi-lab`；正式蓝星向导 RC25 与官方 HeyCyan APK、Windows OCR 及旧实验 v0.1.0 **均不变**。新实验独立包名 `com.vobichngan.bluestar.m02sgattlab`，version 0.1.1，跟旧实验并存。
- **本版实现：** 只显示扫描到的 M02S/HeyCyan/StarGlasses 设备，显示 `isConnectable`；在 BLE GATT 建连前停止扫描；可分别手动发起显式 `TRANSPORT_LE + PHY_LE_1M` 与旧接口默认传输的连接作对照，两者 `autoConnect=false`；15 秒内无成功回调主动关闭 GATT 并释放，无自动重试，记录 `getType/getBondState`、回调状态/耗时；成功后仅做服务发现、列出 UUID/特征数量。旧 Wi-Fi 只读诊断保留。不向眼镜发送拍照/Wi-Fi 开启等未经证实指令，不访问照片原图或云端。
- **上传的官方 APK 真实静态 DEX 字符串：** `/mnt/data/70465ff3819258a6218e7871da0a23f8.apk` 的 `classes2.dex`、`classes4.dex` 确有 `connectGatt`、`connectGattCompat`、`connectBluetoothGatt` 等名称，但**尚未可靠还原实际调用参数和时序**，不能宣称本版已经移植官方协议。
- **成功构建：** [Actions 43574046702](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43574046702) `completed/success`，Java JUnit、Android SDK36 编译、独立包名/版本、APK v2 签名、zipalign、压缩完整性、GitHub Release 完整通过。**真实 BLE 建连是否成功仍待用户在真机验证。**
- **APK 直接下载：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-gatt-lab-v011-20261114/BlueStar_M02S_GATT_Lab_0.1.1_Android.apk 。大小 **58333 字节**，SHA256 `cf0229a765044a166360c2d6953f610131c63dd7d4d4b9b37437586f45e7b094`。
- **下一步（用户执行）：** 关闭原厂 HeyCyan App → v0.1.1 扫描 M02S → 选中后先“连接 LE 1M（15秒）”；失败再手动“兼容连接（15秒）”各一次；复制出现眼镜、connectable 标志、设备类型/bond、连接回调或超时、GATT 服务发现的日志。暂时不重复 Wi-Fi 文件列表测试。依据新日志再研究 M02S 实际 BLE 连接/传输协议。
  
---

# 最新独立实验｜M02S 眼镜 Wi-Fi 保持连接诊断 v0.1.0（2026-11-14）

- **本次用户授权：** 只制作一个独立实验 APK，研究 HeyCyan/StarGlasses M02S 眼镜的蓝牙扫描、Wi-Fi Direct/AP 连接与持续传输是否可行；**不修改正式蓝星向导、官方 APK 或眼镜固件**。用户实测 HeyCyan 官方 App 点击一次“导入”，不论照片一张两张都需重新建立连接，约7～8秒；同一相册页会自动发现新拍摄文件；手机支持 Wi-Fi 与蜂窝数据同时在线。
- **上传的官方 APK 静态核查：** 在 `/mnt/data/70465ff3819258a6218e7871da0a23f8.apk` 的 DEX 字符串里确实存在 `GET_WIFI_P2P`、`app/getfilelist`、`fileDownloadFinish`、`onTakePhotoState`、`connectP2pWifitoReceivePhoto`、`startDownLoadPhotoByWifi` 等标识。**字符串/方法名不足以证明具体 BLE 指令、固件支持或实际文件服务地址**，所以第一版只做安全、只读连接诊断。
- **隔离代码：** 原仓库 `vobichngan285-collab/kanzi-assistant` 分支 `exp/m02s-wifi-keepalive-lab-20261114`，源码 HEAD `3c44874f4fdfe43ba5f232e0f193bc1eb7cb4b88`，只新增 `experiments/m02s-wifi-lab/` 和 `.github/workflows/m02s-wifi-hold-lab.yml`。正式安卓 Blue Star Guide RC25、旧照片监听实验和 Windows OCR 均未改动。
- **实现范围：** 独立原生 Java Android 应用 `com.vobichngan.bluestar.m02slab`，BLE 扫描和手动只读 GATT 服务发现；Wi-Fi Direct 搜索/手动连接设备；知道 SSID 和密码时可以请求 Android 系统连接 AP；120秒内持有 WiFiLock 并每3秒只读访问允许的本地 `192.168.x.x/app/getfilelist`，记录 P2P/网络事件、HTTP 状态、毫秒数和文件列表摘要变化；复制本地诊断。**不会向眼镜发送未验证的遥控快门/开启 Wi-Fi 指令，不上传照片、不读取原图、不删除文件。** 有些 M02S 固件可能必须由官方 BLE 命令先启用 Wi-Fi，第一版可能无法独立启用连接，务必以真机结果判断。
- **真实 GitHub Actions：** [43567345442](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/43567345442) `completed/success`，Gradle 单测、Android 编译、v2 APK 签名、zipalign、包名/版本/权限、ZIP 结构与上传公开 Release 均通过。此测试**不等于实机成功连接眼镜**。
- **已发布 APK：** [Release bluestar-m02s-wifi-hold-lab-20261114](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-m02s-wifi-hold-lab-20261114)；直链 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-m02s-wifi-hold-lab-20261114/BlueStar_M02S_WifiHold_Lab_0.1.0_Android.apk 。文件 49745 bytes，SHA256 `adf5cca266cfc828328461e100eee54b37e180b622413a54f047188f60ade66c`。
- **下一步由用户真机验证：** vivo X200 Pro 授权蓝牙、附近 Wi-Fi、位置，先扫描 BLE 看是否能发现 M02S，再扫描 P2P。如果 M02S 仅在官方导入期间开启 Wi-Fi，尝试按官方导入时机观察并反馈日志。若已知 AP SSID/密码，也可选择系统 AP 连接。连通后按“保持连接120秒”，观察是否掉线、HTTP 文件列表摘要是否变化；复制诊断。**不要事先声称能让眼镜保持 Wi-Fi。** 根据结果再决定是否从官方 APK 继续提取 BLE 启用 Wi-Fi 的精确协议以及遥控拍照的实现。

---

# 蓝星向导｜当前工作交接（2026-11-09）

## 2026-11-10 20:40 真机验收｜照片完整读取 v0.1.4 成功（冻结实验成果）

- **截图确认：** vivo X200 Pro / Android 16 上，独立 Photo Readiness Fix v0.1.4 对 `IMG_20261110_203945.jpg`（1080×1440、982KB）的检测渠道是 **FileObserver 原生文件事件**（系统已收到真实事件；当时直达捕获数累计2），照片读取渠道是 **SAF 授权目录子文件 URI（无需列举目录）**。成功完成 JPEG 抽样解码、文件大小两轮稳定，以及完整字节可读性的本地“候选”判断。
- **真实里程碑（从监听事件时刻起）：** **首次打开 44ms、首次抽样解码 128ms、完整字节读完 + 稳定候选 299ms、检测轮数2**。此前 v0.1.3“10秒超时等待 JPEG 最末尾 FF D9”的误判在本次 JPG 上已消失；显示“JPEG 内发现 EOI，后面可能附带动态照片数据”，并不单凭此证明是动态照片。0ms 是**事件/扫描开始→TTS.speak 请求**的显示值，**TTS onStart反馈 14ms**仍不是扬声器真实出声时间。
- **结论及限制：** FileObserver 首次事件→直接授权子文件URI→本地可读候选的**单次现场测试**成功，在这张982KB图上只需约299ms。不能据此推定所有照片/高像素照片、蓝星云端AI、AI眼镜传输都一样快；两轮稳定+完整可读也不是生产者绝不再写入的确定保证。实验版并未上传/云端处理任何照片。
- **项目纪律：** 本次只记录**成功的真机结果**到 main/PROJECT_HANDOFF.md，不改实验 v0.1.4 源码和 Release，不改之前用户满意的 v0.1.2 FileObserver 分支，不动正式版 RC25 或云端模型配置。下一阶段候选：独立分支做“照片完成候选→用户授权的云端OCR→首句朗读”的端到端时延实验，需对照片上传、API密钥及成本做独立设置和确认；在完成之前不要宣称眼镜能达到这种速度。

## 2026-11-10 照片读取真机反馈及修复 v0.1.4（独立 APK 已发布；待复测）

- **用户本次真机截图：** vivo X200 Pro，照片 `IMG_20261110_202217.jpg`（4096×3072，1951KB），事件来源确认为 **FileObserver 原生文件事件**（已收到真实文件事件、直达新图累计2）。截图显示“事件/扫描开始→语音请求”**0ms**（设备计时分辨率下的值，不是声波播放延迟）、“TTS onStart”**1199ms**（不能证明扬声器已出声）、“首次打开”**1618ms**、“首次抽样解码”**1688ms**、“完整字节读取+稳定”仍未完成；10秒结束原因“等待 JPEG 结束标志”，**不能推断真实图片读取要10秒**。
- **排查：** 0.1.3 的 `ImageCompletionPolicy.hasValidEndMarker` 只认整份 JPEG 最后两字节为 `FF D9`，并把它作为必过条件。这不适用于可能包含附加运动视频的动态照片（Android Motion Photo 规范允许 JPEG/HEIC/AVIF 图像后附视频，参见 https://developer.android.com/media/platform/motion-photo-format）。此截图**还不能证明该用户的 JPG 一定是动态照片**，但判断过严造成的10秒假超时已成为明确兼容性缺陷。
- **隔离修复：** 从此前成功 v0.1.3 commit `256851a76ad694114d7fadaa209e0d6601aef944` 创建新实验分支 `fix/photo-readiness-fast-uri-and-trailer-20261110`，原 GitHub 仓库未变，**保持 v0.1.2 FileObserver→本地 TTS 快速提醒逻辑不变**，也不改正式蓝星 RC25。读取后台线程先针对**用户 SAF 已授权且精确等于 `primary:DCIM/Camera` 的树**尝试合法直接构造子文件 document Uri（免全文件夹列表查询），仅支持单一安全图片文件名；失败则退回已有 SAF 列表查询。修复 JPEG 结尾逻辑：对 JPEG 已读取的完整字节流检索 `FF D9` EOI（支持跨缓冲块，位于动态照片附加视频**之前**也可），**不强制整个文件的最后两个字节是 FF D9**；PNG 仍检查 IEND。需同时抽样解码成功、两次文件大小/修改时间稳定、全部压缩字节流可读、读后文件大小再次相符，才报告**“本地可读取候选”**；不得将候选当成相机此后不会写入的终局保证。UI 新增“读取渠道”（直接子文件 URI/SAF 列表）和真实末尾标记分析，继续显示各毫秒数。
- **编译验证：** 独立实验 APP v0.1.4（versionCode 5，包名 `com.vobichngan.bluestar.photowatch`），构建固定源码 SHA `74703c147b1df6a16304de73ee3defb25d343797`。GitHub Actions [42816752197](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42816752197) **completed/success**，JUnit（包含JPEG动态照片附加数据与缓冲块分割EOI回归测试）、Android 编译、签名 V2、zipalign、安装包版本/无 INTERNET 权限检查均通过。**未做用户 vivo 真机测试**。
- **发布：** [bluestar-photo-readiness-fix-20261110](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-photo-readiness-fix-20261110)，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-photo-readiness-fix-20261110/BlueStar_PhotoWatch_ReadinessFix_0.1.4_Android.apk，文件 36621 bytes，SHA256 `19e6493cce6b596930991337f6185c9faa8821eb5c510db76d1e175e8116619e`。旧 v0.1.2（用户满意的最速提醒）、0.1.3 及各 Release 均保留。
- **下一步：** 请用户安装 0.1.4，仍选择 `DCIM/Camera`、启动监听、拍一张 JPG；比对自动语音是否仍立即播报；回传“读取渠道”、“首次打开”、“首次抽样解码”、“完整字节读完+稳定候选”、“结束标志分析”和“读取状态”。若正常，才考虑另做完整照片发送到云端 OCR 的端到端实验。调试 App 不上传照片、不请求网络权限。APK测试签名如与旧版冲突，**只卸载照片实验工具，不卸载蓝星正式版**。


## 2026-11-10 新增独立实验｜照片完整读取就绪测速 v0.1.3（已构建发布，待真机）

- **用户授权：** 前一版 `FileObserver` 快速监听已在 vivo X200 Pro 口头反馈“几乎毫秒级”，要求继续独立实验验证**新照片出现后，何时能完整读取并用于后续 OCR**。本次只做第一阶段，不接云端 OCR、不修改原正式安卓 Blue Star Guide RC25、不修改此前成功的 0.1.2 APK/代码分支。
- **代码隔离：** 在原 `vobichngan285-collab/kanzi-assistant` 仓库由 v0.1.2 的固定成功 SHA `288ed01ebb65355ec3e4a309b8e6d9138faa4e5d` 派生新实验分支 `exp/photo-watch-readable-timing-20261110`；构建源码 HEAD `256851a76ad694114d7fadaa209e0d6601aef944`。只修改 `experiments/photo-watch/` 子项目，并新建对应独立 workflow。
- **实现 v0.1.3：** 保留原 `FileObserver`（对 `primary:DCIM/Camera` 优先监听）→立即本地 TTS“发现新照片”链路不阻塞；每张新照片的本地读取检测交给**独立后台线程池（最多2工作线程、最多3张在途，每张最多10秒、图片最多80MB）**。使用用户已授权的 SAF Uri 查询/打开新照片，无新增图片库全局权限，不联网上传、不复制存储原照片、不在日志写入像素或 OCR 文本。每张记录“事件或扫描开始→首次成功打开且非零长度”“→首次成功低内存抽样 BitmapFactory 解码”“→完整压缩字节流可全部读完且大小/修改时间两次稳定、抽样解码成功”三项耗时。**JPG/JPEG 必须有文件末尾 FF D9，PNG 必须有 IEND 尾标**；HEIC/WebP/BMP 无本实验统一尾标校验，所显示“就绪”只是启发式估计。就算有稳定标记也不能绝对保证相机此后不修改文件，未来 OCR 仍需专门完整性/并发保护。另有状态、重试轮数、图像分辨率、来源和字节数供截图反馈。
- **独立版本及验证：** `com.vobichngan.bluestar.photowatch` versionCode4 / versionName `0.1.3`，GitHub Actions [42814264726](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42814264726) **completed/success**：JUnit 单元测试、Android Gradle 编译、APK V2 签名、zipalign、package/version 和不含 INTERNET 权限验证全部通过。该成功表示 APK 构建通过，**尚未经用户 Android 16/vivo 真机验证读取耗时、照片解码、后台运行和性能**。
- **发布及校验：** 公共预发布 [bluestar-photo-readiness-lab-20261110](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-photo-readiness-lab-20261110)，APK `BlueStar_PhotoWatch_ReadinessLab_0.1.3_Android.apk` 大小 **34457 bytes**，SHA256 `c4016d3e6553cdcde4e1af2606e9f0d2048f3c01fefaf5aa1d79659e07033921`；直下地址 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-photo-readiness-lab-20261110/BlueStar_PhotoWatch_ReadinessLab_0.1.3_Android.apk 。旧 v0.1.2/0.1.1/0.1.0 Release 保留。
- **手机测试与下一步：** 用户如覆盖安装报告测试签名不匹配，只能卸载旧**照片监听实验小工具**并重新授权 `DCIM/Camera`，**不可卸载正式蓝星向导**。启动监听后拍一张 JPG，验证“发现新照片”依旧立刻发声，再拍普通/大图观察三项毫秒数字及读取状态（用户不需上传私有照片内容）。若用户实际测试证明很快就绪且不影响音频，后续再做独立云端 OCR 集成方案；没有眼镜，眼镜到手机同步时间仍未知。


## 2026-11-10 现场验收更新｜照片 FileObserver 直达方案用户反馈成功

- **用户实际反馈：** 在 vivo X200 Pro 上安装 v0.1.2 原生文件事件监听实验版后，用户明确反馈「这下非常成功，几乎是毫秒级」。这意味着拍照后「发现新照片」提示在主观体验上比 v0.1.1（先前约2秒）快得多。**这是用户主观听感，未拿到系统诊断的实际事件→TTS 毫秒值，也没有收到“本次发现渠道”的截图，不得声称已测量出精确毫秒级或完全证明 FileObserver 路径为最终触发来源。**
- **现阶段结论：** 独立安卓实验小工具的照片到达与本地提示响应已经达到用户当前满意水平，应**冻结当前有效 v0.1.2 实验 APK/代码**，不再无目标缩短间隔、改动 TTS 或正式蓝星 RC25。实验源码 `exp/photo-watch-fileobserver-direct-20261110`，构建 commit `288ed01ebb65355ec3e4a309b8e6d9138faa4e5d`，公版发布 `bluestar-photo-watch-direct-lab-20261110`。
- **下一阶段技术缺口：** 当前立即触发只是基于文件出现，**并不保证照片 JPG/HEIC 完整可读**。未来接入 OCR 时应在独立实验中测量「文件出现→JPEG/HEIC完整可解码的最早时间」，事件可先立即播短提示，但实际上传必须在成功打开/校验照片后进行；不应只等待固定2秒或对半写入文件上传。AI眼镜官方 App 能否免操作同步、同步时长和息屏行为仍然未验证，未购买眼镜。未经新的用户指令，暂不开发/构建下一步。

## 2026-11-10 最新照片监听实验｜FileObserver 原生文件事件直达 v0.1.2（APK 发布）

- **用户真机新反馈：** v0.1.1 在 vivo X200 Pro 的 `DCIM/Camera` 自动播放「发现新照片」已明显快于 v0.1.0，用户主观约2秒；希望「文件夹只要发生一点变化立刻响应，没有中间商」，继续优化。**这只是用户大致体感，不是严谨测得的总延迟**。
- **独立新分支：** `exp/photo-watch-fileobserver-direct-20261110`，在 v0.1.1 分支之上开展，不改正式安卓 Blue Star Guide RC25、云端 OCR / USB / 语音相关代码。实际构建 commit **`288ed01ebb65355ec3e4a309b8e6d9138faa4e5d`**。
- **改动原理：** 新 `DirectFolderEvents.java` 使用 Android 原生 `FileObserver`（内核 inotify）对**用户 SAF 授权且文档 ID 精确匹配** `primary:DCIM/Camera` 的实际目录，尝试监听 CREATE、MODIFY、MOVED_TO、CLOSE_WRITE。**第一次目录列表建立旧图片基线后**，新 JPG/HEIC/PNG/WebP 图片文件名出现即走 `FileObserver -> PhotoSnapshotTracker 去重 -> TTS.speak`，不等待两次目录扫描、不等待 MediaStore，也不在语音请求之前写文件日志/通知。不读取图片内容，仅给“发现新照片”的本地语音。文件事件与 SAF 迟来的图片元数据统一按文件名去重，避免同一张图重播。保留 `ContentObserver` / MediaStore / **600ms 兜底**，以应对 Android scoped storage 或厂商实现不允许原生事件的情况；`FileObserver.startWatching` 不保证真的收到事件，必须以**实际 FileObserver 事件计数**证明。
- **诊断：** 独立 APK 0.1.2 增加「直接监听状态」（注册但未收到事件/已收到真实事件/不可用）、「直接捕获新照片数」、「本次发现渠道」（FileObserver 直达 或 SAF 回退）、「事件/扫描→TTS 请求耗时」、「TTS 引擎 onStart 反馈耗时」。`onStart` **不代表物理扬声器已经响**；文件首次创建也不代表写入完毕，将来真正将图片传给云端 OCR 必须另做完整性校验。本实验没有 Root、全文件管理权限、互联网权限、图片上传或新权限。
- **构建发布：** 独立 GitHub Actions [42810923510](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42810923510) **completed/success**，Java 单元测试、Android 编译、APK v2 签名、zipalign、包名版本、无 INTERNET 检查成功；原实验与 RC25 不受影响。GitHub Public prerelease [bluestar-photo-watch-direct-lab-20261110](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-photo-watch-direct-lab-20261110)，APK 0.1.2 `BlueStar_PhotoWatch_DirectLab_0.1.2_Android.apk` **25541 bytes**，SHA256 `c6a1cf243dee9849abd9fd76329252317862442520b57e7b70617f10680e6c80`，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-photo-watch-direct-lab-20261110/BlueStar_PhotoWatch_DirectLab_0.1.2_Android.apk；保留 v0.1.0/0.1.1 发行版。
- **待用户真实验收：** 0.1.2 安装后仍选择 `DCIM/Camera`，开启监听等待旧图片基线，手机拍照一次；记录“本次发现渠道”，看“直接捕获新照片数”是否>0，和“事件/扫描→语音请求”“引擎 onStart”。如首次仍靠 SAF，说明此机 Android 16 文件权限/系统行为或相机保存方式没有产生可见原生 FileObserver 通知；不能再盲目缩短间隔，需分析系统相机实际写文件时序。两秒中有多少是相机拍摄处理/保存或 TTS 首声尚无法断言，不承诺低于1秒。
- **安装提醒：** 测试 APK 可能因每轮 GitHub Actions debug signing key 不同而无法覆盖，遇到提示签名不匹配仅卸载之前的**「蓝星照片监听实验」**，重装并重选目录；不得卸载正式蓝星向导。

## 最新反馈及优化｜2026-11-10 照片监听速度专项实验 v0.1.1（已发布，待真机）

- **用户真机反馈：** vivo X200 Pro 已成功通过 SAF 选择 `primary:DCIM/Camera`，实验 v0.1.0 在手机相机拍照后能自动发现新 JPG（截图见成功发现1张）且**自动语音“发现新照片”确实会播报**。用户澄清不是语音失效，而是提示速度偏慢；上一版照片修改时间到检测估计为 **6844ms**，不是实测快门到扬声器延迟，不得据此断定眼镜传输耗时。
- **本次授权与修复：** 只优化独立小工具，不动蓝星 RC25，也不修改语音引擎。新分支 `fix/photo-watch-fast-detection-20261110` 基于原照片监听实验。首次扫描仍建立旧照片基线；后续新图片首次出现且文件长度非零就立即提示一次，**不再为通知目的等待连续两轮文件大小稳定**。保留文件夹 SAF 监听，额外注册系统 MediaStore Images 变更通知，若官方厂商不发事件则 **600ms 轮询兜底**。用标志保留查询时到达的事件以免丢失。保留系统中文 TTS，改 `QUEUE_FLUSH` 避免堆积过往提示。新增界面诊断「本机扫描开始→语音请求毫秒数」「语音引擎接受状态」，保留时间戳到检测的近似估算并解释不是拍照→听声总时长。**新文件刚发现不等于文件写入完整；未来真正云端 OCR 仍须独立检查图片完整性，切勿直接照搬当前快速通知逻辑用于上传。**
- **版本及发布：** 独立 APK 0.1.1，包名仍 `com.vobichngan.bluestar.photowatch`（前一版安卓 debug 签名可能与本次 CI 不同，因此用户如遇覆盖安装签名不匹配，只需卸载**旧照片监听实验小工具**后安装新版，需重新授权目录；**绝不可卸载正式蓝星向导**）。构建+发布 GitHub Actions [run 42809329654](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42809329654) `completed/success`，**构建 Commit `b14baea9770a1ffe5267d632425b2e2cea837f8b`**；Gradle JUnit、APK 签名 v2、zipalign、manifest 包名/版本、无 INTERNET 权限检查全部通过。独立公开 prerelease [bluestar-photo-watch-fast-lab-20261110](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-photo-watch-fast-lab-20261110)，APK `BlueStar_PhotoWatch_FastLab_0.1.1_Android.apk`，22545 bytes，SHA256 `c7353e6a6a729470a79d535f5732f0493fc7f3d2e933ece99b04f4508be92c26`。旧 v0.1.0 release 及旧实验分支保留。
- **待用户测试：** 重选 `DCIM/Camera`，按“开始监听”，相机拍一张，记录是否能更快听到自动提示、新的「本机扫描→语音请求」毫秒值和「照片时间戳→发现」近似值。多扫描可能加重 CPU/电量；如仍慢，需调查相机保存完成、SAF provider 查询、TTS 实际首声，不得承诺1秒内；尚无任何 AI 眼镜传输验证。

## 最新独立实验｜2026-11-10 外部照片自动发现小工具（Android 已构建并发布）

- **用户指令：** 在尚未购买 AI 眼镜之前，先做一个**独立安卓小工具**，系统文件夹选择器绑定照片目录，再用手机拍摄新照片，验证新图片能否被自动发现、播报、计时，并测试切换应用、息屏。**不是蓝星向导 RC26 更新，也不修改 RC25 正式版**。
- **代码隔离：** 原仓库 `kanzi-assistant` 中独立分支 `exp/bluestar-external-photo-watch-20261110`，子项目 `experiments/photo-watch/`，独立包名 `com.vobichngan.bluestar.photowatch`、版本 0.1.0。用户原安卓云端 AI 分支 `feat/bluestar-125-reading-entry-pages-actions-20261108` / RC25 不变。
- **已实现：** Android SAF `ACTION_OPEN_DOCUMENT_TREE` 让用户授权一个文件夹；首次扫描为基线，忽略所有已有照片；`ContentObserver` + 每2秒轮询兜底，图片大小/修改时间连续两轮稳定才当作新图，去重；使用系统本地中文 TTS 播报“发现新照片”，界面显示计数、最近照片文件名、粗略修改时间到检测延迟、最后扫描时刻；提供「打开手机相机」「试听」「停止监听」按钮，屏幕阅读器可访问的标准大字体原生控件。启动30分钟本地前台 `dataSync` 服务、可暂停/终止、短时 `PARTIAL_WAKE_LOCK` 供息屏实验。**没有 CAMERA/INTERNET 权限，不解码、保存或上传任何照片内容，没有 OCR 和云端 API。**
- **CI 验证：** [Android Actions 42806842345](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42806842345) `completed/success`，实际源码 SHA `31b72474a5addabedfdf84e4232b2208505644c7`。Java JUnit 测试、Android SDK36 / Gradle8.11.1 编译、签名 v2 验证、zipalign、APK 包名与版本校验、无 INTERNET 权限校验均通过。第一轮 `42806703601` 因 Android SDK setup 动作请求已下架的 `tools` 包而失败，已修正，并非产品代码故障。
- **公开发行：** [Release bluestar-photo-watch-lab-20261110](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-photo-watch-lab-20261110)，独立直下 APK `BlueStar_PhotoWatch_Lab_0.1.0_Android.apk`，22109 bytes、SHA256 `663e7618f331c370e688493cebc29b4c86c97d5ebf36cb1c137ce88338ac3eac`。可安装到手机并与原版共存；DEBUG 签名仅用于实验。
- **仍需手机验证：** **用户 vivo X200 Pro / Android 16 上的文件夹能否被系统选择并获得持久权限（通常 DCIM/Camera）**，官方相机拍照后是否真的存储在选中目录，后台和息屏通知、中文系统 TTS 是否发声；文件服务商可能不发送实时事件，2秒轮询可兜底；厂商省电策略可能打断前台服务。**仅验证手机新照片到达，不等于 AI 眼镜官方 App 会自动同步照片**。Android11+ 系统禁止选择部分根目录。成功后再评估接到蓝星视觉 AI，切勿提前修改正式版。
- **开发原则：** 后续此小工具只在单独实验分支修改；先等用户真实日志，再做针对性优化。保留 RC25、原 USB、云端 OCR / TTS、RC26 待开发任务不受影响。

> **只按本页恢复当前项目。** 原仓库：[kanzi-assistant](https://github.com/vobichngan285-collab/kanzi-assistant)。本仓库旧离线 OCR／CRAFT 已停止开发；Windows 游戏 OCR 是另一项目，不要混淆或自动恢复旧实验。

## 1. 现在做什么
- **当前活跃项目：** 安卓「蓝星向导 Blue Star Guide」**云端 AI 视觉助手**，使用手机后摄或 USB UVC 摄像头拍照，执行 AI 识图、OCR、追问、中文 TTS 朗读，并有阅读/书页管理功能。
- **最新已发布：RC25 v1.0.24**（2026-11-08），已通过源码检查、Android CI 构建及 Release 校验；**尚未取得完整 vivo X200 Pro／USB 真机交互验收**。RC26 **尚未开发**。
- **正确源码分支：** `feat/bluestar-125-reading-entry-pages-actions-20261108`；本次检查分支 HEAD 为 `fe848acf87cec87d8d7cba0c739b7be40e31072b`（文档提交）；**真正用于 RC25 APK 构建**的源码 SHA 是 `1b29d56ec55e8030dfceb1d34ee7748e79deb7a5`。不要把 `main`（交接文档分支）误当作最新 APK 代码。
- **成功编译：** [Actions 42444753150](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42444753150)；**原包发布：** [Actions 42445082678](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42445082678)。
- **RC25 下载与版本核对：** [Release 页面](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc25-20261108)，[APK 直链](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc25-20261108/BlueStar_Guide_1.0.24_RC25_arm64.apk)；包名 `com.vobich.bluestar.rc25`，SHA256 `d1f863c3a9556d1fc4fc7e3ed6ea7702b1d3badae3be2d5de6a93147b5efdfc9`。

## 2. 已完成的体验与仍需确认的事
- 现有云端视觉任务、手机 Camera2／USB 摄像头、语音追问、语音提示、C 模式云端 OCR **逐行流式朗读**需要保留。C 模式此前有实际用户正面反馈，不必重复旧 A/B/C 实验。
- RC25「阅读」入口仅有**继续阅读／新阅读**；选书后进入书页列表，支持标记、改名、删除；用户进入相机预览后**再次点击画面**才拍照、振动、关闭相机、OCR、朗读并存储文字。正文底部有**下一张／暂停或继续／重新开始**，右上角保留隐藏功能菜单。
- **真机尚待验收：** 阅读按钮、书页管理与持久化、朗读顺序、USB 方向与稳定性，以及手机后摄表现。RC25 独立包与旧版不共享私有书籍和 API Key；不要随意卸载旧包导致数据丢失。CI 成功不代表全部硬件行为已验证。

## 3. 下一步计划（等待用户新指令）
- **RC26 目标：** 设置里允许用户自行添加／切换**豆包、DeepSeek、千问及其他 OpenAI Chat Completions 兼容模型**，填写模型 ID、自己的 API Key，测试连接并保存；同时保留原阿里云百炼及 C 模式。
- 必须检查每个服务是否真的支持**图片、流式返回**；只用 HTTPS，Key 安全存储、不得记录或跨服务商发送，防重定向泄漏；第三方图片上传需明确提示。**不同时改 ASR、TTS 或 USB 摄像头底层。**
- RC26 只是**已确认的需求方向**，没有代码或构建。应等用户明确下令再设计、修改和测试；如用户改了优先级，按新指令执行。

## 4. 放弃项目与工作纪律
- **已放弃：** 早期「看字助手」本地离线 PP-OCR／ChineseOCR-Lite／CRAFT 实验，旧 v55-v57、CRAFT 6×24 等都**不再是当前开发任务**；不要检索、汇报、重复运行这些实验，除非用户点名要求。旧分支尚在 GitHub，不等于仍要维护。
- **唯一当前交接文件：** `main:PROJECT_HANDOFF.md`。完整的旧长文档仅作为可追溯历史保留在 [2026-11-09 清理前 Commit](https://github.com/vobichngan285-collab/kanzi-assistant/blob/766ddb312dd6ad64ad5f66bba093def442293fbc/PROJECT_HANDOFF.md)，新对话**无需读取**。
- **开发规则：** 不新建仓库；只在用户授权后动代码、构建或发行。实施前核对目标分支 HEAD、真实源码与 Actions；修复后审查 diff、自动测试、APK、Release，不能夸称未做的真机验证。
- **交接更新：** 每次获授权的重要进展，在此文件**简短更新最新版本、构建 SHA、Release、已验证事实、未解决问题和下一步**；提交至 `main` 并回读核验。不要再次堆积旧实验流水账；文档-only 提交带 `[skip ci]`。不得在公开仓库写入 Key、私人照片或 OCR 原文。


## 2026-11-26 — OCR 六模型真机实验续接与强制备份规则

用户要求：每次取得实质进展第一时间更新仓库备份文档，追加历史，不依赖聊天记录。后续每次提交后核验并告知提交链接；失败须明确告知。当前继续 v0.2.0 六模型竞技场，使用原价目表 3072×4096、CPU 4线程、全图单次、原始框识别、统一 RepSVTR，每模型两轮。用户将完整诊断保存为 TXT 并与原图提供，待收齐后核对准确性、漏检、排版、耗时、内存和错误归因，只筛选一个最值得优化的方向。原厂输入尺寸不同，单图仅初筛。已收到部分日志：RepViT-DB 总耗时1823/1741ms；YOLO26m-Text 2486/2502ms；尚未完成六模型对照，不提前选定胜者。保持正式APP不变，不自动上传图像；复杂竖排若无足够优化价值，回退v0.1.3稳定横排，复杂排版仅联网且用户授权时云端处理。聊天消失原因未证实。


## 2026-11-26 — 六模型完整日志分析（v0.2.0）

完整TXT共6模型×2轮，3072×4096、CPU4、tiles=false、layout=0；每模型两轮cell记录完全一致。以下为平均总耗时/检测/识别(ms)、框数、suspect、空框：RepViT-DB 1710.5/305.5/1404,51,6,3；YOLO26s 1757.5/215/1541.5,61,16,2；YOLO26l 2396/737/1657.5,62,16,0；YOLO26m 2510/591.5/1916,75,27,5；YOLO26n 3175.5/359/2810,107,38,13；YOLO26x 3549.5/1614.5/1933,70,24,3。n输入1280，其余YOLO640，DB原生；不是同分辨率架构对照。DB识别占82.1%，待核率11.8%，不等于CER；原图真值尚未核验，单图不能证明泛化，PSS受顺序影响。推荐唯一主线：RepViT-DB+几何文本行重建+现有识别器，保留价格列独立；A原框、B排序、C行重建、D自适应扩边、E低置信度复核。理论51→36框，目标1.5秒以内，均未验证。禁止硬编码词语或删除负号。先等待用户授权实施v0.3.0，不动正式APP；不上传私人OCR原文或图片。


## 2026-11-26 — 六模型日志复核，修正优化优先级（以上次统计为基础，以本次结论为准）

重新程序化解析原始TXT全部2598行（末尾换行计数口径不同），6模型×2轮，逐模型raw/cell两轮完全一致；全部852个cell记录（两轮合计）vertical=false，不能据此评价真正竖排能力。此前耗时及suspect/空框统计正确。DB平均1710.5ms，仅比s的1757.5ms少47ms（约2.7%），两轮不能判定显著差异；total不含独立记录的模型加载。按cell中心Y>2900像素统计底部区域框数/非空：n 64/52、m 28/24、x 27/26、l 20/20、s 16/15、DB 8/5。框数非召回率，含重复/误检；DB低suspect率不可视为全图准确率高，需原图真值。n为1280，其他YOLO640，DB原生，分辨率不一致。DB主体大间距横排经几何排序多数可恢复，但cell17异常旋转框det .966 rec .230，另有两处价格符号异常未标suspect；应先排查裁剪/方向，再通用行列重建，再比较全图与四块高清补偿小字检测，最后置信度复核与性能。保留DB为唯一优先优化主线，n仅作小字覆盖参考，不宣称DB普遍最优；真实竖排需独立测试样本。未获实施授权，不改源码/正式APP、不构建、不上传私人OCR原文及图片。


## 2026-11-26 — 稳定版复核及换模决策建议

核对原Release v0.1.0/v0.1.1/v0.1.2/v0.1.3及v0.1.4：稳定横排基线v0.1.3为YOLO26n-Text ONNX(1280)+完整列几何裁剪+RepSVTR，CPU4；v0.1.4发布说明记录v0.1.3同价目表全部已知项目及数字正确，最快1116ms，但竖排刮痧海报失败。v0.2.0 DB+RepSVTR原始框两轮平均1710.5ms，YOLO26n原始框3175.5ms；旧版最快与新版平均、ROI/裁剪策略不一致，不能当作严格检测器A/B，也不能断言DB胜过稳定版。当前建议保留v0.1.3，不换正式部署；如用户希望最后确认，仅在既有Arena同图CPU4全图、相同横排行列策略下对照n与DB，原v0.1.3单独作为基线，另用真实竖排海报验证；比较主区域和全图覆盖、逐字/金额正确性及耗时，未见明确收益即停止本地竖排投入，按此前授权复杂排版仅联网且用户授权时走云端，离线低置信明确提示。未获新实施授权，不改代码、正式APP或Release。依据：https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-openocr-yolo-columns-v013-20261126 及 https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/bluestar-openocr-yolo-mixed-v014-20261126 。


## 2026-11-26 — 原厂配套检测器确认
OpenOCR官方移动端配套为RepViT-DB检测（configs/det/dbnet/repvit_db.yml）+RepSVTR识别（configs/rec/svtrv2/repsvtr_ch.yml），已在六模型Arena中测试。原稳定v0.1.3是跨项目YOLO26n+RepSVTR组合；同源配套不自动代表更准确或更快。仍建议先保留稳定版，若继续验证仅做同后处理A/B。官方来源：https://github.com/Topdu/OpenOCR/releases 。无代码变更。


## 2026-11-26 — 用户确认最终双检测器对照实验
用户同意再做一次实验，不需新APK。使用现有Arena v0.2.0，固定RepSVTR、CPU4、相同原图与每模型2轮；价目表：YOLO26n与RepViT-DB均全图单次+横排行列算法；真实竖排刮痧海报：两模型均全图单次+v0.1.5完整竖列算法，若均严重漏检，再给两者同样开启四块高清对照。每组保存独立诊断TXT，原图供聊天内人工核对，不上传仓库。若原v0.1.3仍安装，可同图CPU4复测作为稳定基线（主区域与全图覆盖、最快与平均、加载单计等口径须注明）。检查项目/金额、漏字、重复、阅读顺序、速度、内存和真实竖排完整性；仅在新方案有可复现实质收益且常规横排不明显退步时才考虑换模，否则保留v0.1.3，停止本地竖排研发，复杂排版联网且用户授权时云端处理。当前未收到新增测试结果，仅备份实验方案，不改代码、不构建、不改正式APP。


## 2026-11-26 — 最终双模型对照：YOLO26n横排模式海报基线
用户回传Arena v0.2.0 3072×4096 CPU4 tiles=false layout=3 YOLO26n两轮；疑似此前竖排海报（原图尚未核验）。raw15 unique14 blocks10 crops11，global308/308ms，rec272/275ms，total580/584ms，均值582ms；PSS698769/649479KiB，6个suspect、3空框，全部vertical=false，两轮raw/cell输出一致。不能把速度当作识别成功，不能视为竖排算法测试。已核对发布提交5ac4b9a853a4e2eee539894ac9f76f53ccc3aaaa的openocr-lab/app/src/main/java/com/vobich/bluestar/openocrlab/MainActivity.kt第29行：0原始框、1 v0.1.4混合排版、2 v0.1.5完整竖列、3横排行列裁剪。下一步同一海报CPU4全图单次layout=2，分别测试DB与n各两轮；若均严重漏检再同等四块高清；价目表仍使用layout=3双模型对照。未选定换模，未修改代码或APP，不备份OCR原文及图片。


## 2026-11-26 — RepViT-DB海报横排对照：发现长竖列原始框
用户回传同尺寸3072×4096、CPU4、tiles=false、layout=3的DB两轮：raw12 unique12 blocks5 crops5，global265/254ms，rec127/129ms，total392/383ms（平均387.5ms），PSS557033/586857KiB，2 suspect/2空框，全部vertical=false，两轮raw/cell一致。与前一轮YOLO26n同配置（582ms，11 crops）比较，DB总耗时低33.4%，但正文未正确识别，速度不能视为有效阅读性能。关键新证据：DB raw5/6/7/9/10为五个近似平行竖长框，长边约995–1153px、短边63–79px、det约0.976–0.987；YOLOn最长原始竖长框约255px。DB layout3后cell1为约1624×1383px大框，rec=0，疑似多列错误聚合；需原图/预览确认覆盖及列内容，不将高det置信度当作准确率。优先立即用DB同图CPU4全图单次layout=2(v0.1.5完整竖列)测试两轮，再用n同配置对照。若DB能保留完整列并识别正文才考虑替换；否则检查现有layout2后处理，按此前停止条件决定。未修改代码、构建或正式APP，不保存私人OCR原文/图片。


## 2026-11-26 — 用户要求暂停实验，保存恢复检查点
当前暂停，待用户明确恢复实验再继续；不主动构建、修改代码或启动测试。已完成六模型价目表原始框对照及海报layout=3的YOLO26n/RepViT-DB双模型对照。海报DB平均387.5ms、YOLO26n 582ms，DB出现五个约995–1153px长竖框，YOLO最长约255px；但layout=3错误合并竖列且正文未成功识别，不能认定换模成功。下次从现有Arena v0.2.0、同一张海报原图、CPU4、全图单次、RepViT-DB、v0.1.5完整竖列（layout=2）、单模型两轮开始；然后YOLO26n同配置对照。如两者均严重漏检，再按此前方案同等开启四块高清。价目表layout=3双模型对照仍待完成；保留v0.1.3稳定横排基线，未决定替换。无需重做已完成实验。不上传私人图片及OCR原文。用户要求每次实质进展立即提交本交接文档并回读核验。


## 2026-11-26 — 海报DB原始框模式补测
用户回传DB layout=0、CPU4、tiles=false、3072×4096，两轮RAW/CELL一致，与此前layout3原始12框一致；12 crops，6 vertical=true，3 suspect，0空框。global260/235ms，rec539/514ms，total800/749ms（平均774.5ms），PSS399206/488288KiB，识别器加载218ms、检测器首次84ms另计。五条长竖列独立保留，但输出不连贯，4/5未标待核，最低字符置信度部分低至0.115–0.178。源码MainActivity.kt第92行确认vertical=true走MixedLayout.verticalRead(bm,q,rec,kind==2)，layout0已有竖排重排但不启用kind2完整列增强；非单纯横向旋转。原图真值、字符分割中间结果未核验，不能断定错误只来自识别权重或分割。下一步仍优先DB同图CPU4全图layout2（v0.1.5完整竖列）两轮，与layout0比较，再YOLO26n同配置；暂不启动四块高清、改代码、构建或换正式模型。用户可自行决定何时继续；本次仅备份数值及诊断结论，不存私人OCR原文/图片。


## 2026-11-26 — 价目表DB原始框重复验证
用户回传Arena v0.2.0 RepViT-DB、3072×4096、CPU4、tiles=false、layout=0，两轮raw/unique/blocks/crops均51，6 suspect、3空框、0 vertical，坐标/文本/置信度两轮一致，且已知乱序及两个疑似多余金额负号等错误复现。global296/239ms，rec1266/1232ms，plan3/2ms，total1565/1473ms（均1519ms），PSS421731/509315KiB，识别器加载213ms、检测器首次57ms单计。相对六模型擂台DB平均1710.5ms耗时减少11.2%，无代码或模型变化，不能归因于优化。无需再重复layout0。未完成的重点实验：价目表DB与YOLO26n同图CPU4全图layout3横排行列裁剪；海报DB与YOLO26n同图CPU4全图layout2完整竖列，优先DB。原图真值仍未核验，不做CER或换模验收；保留v0.1.3稳定基线。未修改源码、APK或正式应用，不上传原图/OCR原文。


## 2026-11-26 — 价目表RepViT-DB横排行列实验完成
Arena v0.2.0、3072×4096、CPU4、tiles=false、layout=3，DB两轮原始51框与layout0一致，24 blocks、35 crops、4 suspect、0空框、0 vertical；两轮raw/cell及输出一致。global276/262ms，plan7/2ms，rec883/853ms，total1166/1117ms（平均1141.5ms），PSS360967/524545KiB；识别器加载207ms、首次检测器30ms单计。相对最新同图layout0平均1519ms、51 crops、1249ms rec，耗时降低24.9%、裁剪减少31.4%、识别耗时降低30.5%；五组大间距短语完整恢复，原两处疑似金额负号消失但另两处金额新增疑似负号（含双负号），未标suspect；充值两框误合并，背景小字仍有错误。不能将空框减少视为准确率改善，不硬编码纠错。DB最快1117ms接近历史v0.1.3最快1116ms，但ROI/后处理口径不同，非严格同条件对照。当前下一步：同价目表CPU4全图layout3切换YOLO26n两轮；随后海报DB及YOLO26n同图CPU4全图layout2各两轮，优先DB。原图真值未核验，尚未达到换模验收；保持原v0.1.3稳定基线，不改正式APP、权重、代码或构建，不上传私人原图及OCR原文。


## 2026-11-26 — 用户授权优化：独立v0.3.0自适应裁剪实验
已基于v0.2.0发布提交5ac4b9a853a4e2eee539894ac9f76f53ccc3aaaa创建隔离分支exp/openocr-adaptive-20261126，提交ee122f9f9956955bce04b410d8e12cf15db44312，回读分支HEAD一致，审阅8文件diff。新增layout4（默认DB/CPU4/全图），旧layout0–3保留；仅保留DB和YOLO26n对照，识别器RepSVTR及字典原样。横排单原框保留原四边形、多独立长文本按几何间距拆分、碎字仍使用横排行列规划；最多6次弱置信/带符号数字原框与扩框对照，保留主读并记录分歧待核，不静默改金额或符号，最低字符置信度<.65/平均<.75也待核。竖长框独立保留，双极性边缘背景对比投影、既有网格切分及保持纵横比例的字形重排；非原生竖排识别器，复杂背景/书法/错切仍可能失败。新包名com.vobich.bluestar.openocrlab.adaptive，拟Release tag bluestar-openocr-adaptive-v030-20261126；CI固定旧APK、资产清单、报告与许可证SHA，原样提取两检测器/识别器/字典并验证，新增4个纯几何/投影/符号策略单测，继续CPU检测推理、安卓单测/构建/签名/无网络权限检查。已推送独立分支，首次按head查询Actions暂无记录；APK/CI/真机尚未验证，不声称已发布或效果改善。正式APP、v0.1.3、v0.2.0 Release及原分支不动。发布通过后同两图CPU4全图layout4双模型各两轮；检查金额分歧、充值分隔、长列阅读、全图覆盖和耗时，若无明确收益保留原稳定方案。禁止上传原图及OCR原文。


v0.3.0 构建已触发：Actions 45788302042（https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45788302042），对应ee122f9f9956955bce04b410d8e12cf15db44312。已核实checkout、Java、Gradle准备成功，当前正在提取并校验原资产；安卓测试、编译、签名和发布尚未完成，未取得可下载安装包，不宣称构建或真机效果成功。


## 2026-11-26 — v0.3.0 自适应裁剪实验发布成功
Actions 45788302042 build/publish全部success，资产复用SHA校验、双检测器CPU推理、Android release单测/编译、APK签名/包名/无网络权限校验及发布均通过。Release tag bluestar-openocr-adaptive-v030-20261126，target ee122f9f9956955bce04b410d8e12cf15db44312，非draft、prerelease，APK BlueStar_OpenOCR_Adaptive_0.3.0.apk 65701662 bytes，SHA256 4f81822c2c79dc76ecb979ea13fd057f0aa218257bcd521df960e3c7a99529a8。下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-adaptive-v030-20261126/BlueStar_OpenOCR_Adaptive_0.3.0.apk 。真机准确率、速度、金额与竖排效果仍未验证。独立安装，不覆盖原版；同价目表/竖排海报分别默认DB、CPU4、全图单次layout4测试两轮，回传日志，必要时同模式YOLO对照。保留原稳定版，不上传原图及OCR原文。


## 2026-11-26 — v0.3.0自适应裁剪价目表首次真机验证
用户回传Adaptive v0.3.0 DB、3072×4096、CPU4、tiles=false、layout4，两轮原始51框与此前一致，24 blocks、36主crops+6 retry_crops、9 suspect、3空框、0 vertical；两轮RAW/CELL/复核结果一致。global332/277ms，plan6/4ms，rec1116/1095ms（含复核），total1454/1376ms（均1415ms），PSS420916/516163KiB；识别器加载277ms、检测器首次129ms单计。相对v0.2.0 DB layout3平均1141.5ms慢24.0%，layout0平均1519ms快6.8%；6次复核均与首读有分歧，实际复核成本未单独计时，不能把全部差异归因于复核。五组大间距短语保持完整，两组充值成功分隔，原layout3新增的两个疑似金额负号消失；原layout0另两处疑似负号重新出现，但两处均标待核，复核数字一致且无符号候选的平均/最低置信度分别0.934738/0.875366及0.987995/0.953751，高于首读0.794723/0.490742及0.902194/0.513037。不能仅凭置信度自动删除金额符号，原图真值仍需核验；小字错误/漏检未解决。当前优先用同一竖排海报DB默认layout4 CPU4全图测试两轮，不立即修改代码；若竖排无实质收益，按原决策保留v0.1.3。未来若继续优化，考虑复核优先级及独立耗时统计、符号冲突保留候选并需像素证据，非数字低风险候选亦需验证，不硬编码。正式APP、旧Release不动，本次只提交诊断汇总，不存私人OCR原文/图片。


## 2026-11-26 — v0.3.0竖排海报真机验证及阶段性结论
用户回传Adaptive v0.3.0 RepViT-DB、3072×4096、CPU4、tiles=false、layout4，两轮原始12框与历史DB海报一致，11 blocks/11 crops、0 retry_crops、6 vertical=true、7 suspect、2空框，两轮坐标及识别结果一致。global308/419ms，plan0/1ms，rec697/610ms，total1005/1030ms（均1017.5ms），PSS393233/543587KiB；识别器加载206ms、检测器首次43ms另计。比v0.2.0 DB layout0均774.5ms慢31.4%；五长列按相同原始框匹配从右到左置信度0.369796→0.408359、0.702432→0.528416、0.562332→0.432933、0.756527→0.382989、0.636237→0.707142，三列下降，五列均值约0.60546→0.49197；置信度非准确率。五长列正文仍不连贯，短竖框为空，全部六竖框已待核，三条底部横排文字仍稳定输出。原始12框变11裁剪且一个大横框，存在进一步的误合并嫌疑，未核验原图及字符中间切分图，不能断言识别权重是唯一原因。结合价目表v0.3.0平均1415ms、仍有两处金额符号分歧，未达到此前换模验收条件。依据此前用户设定的停止条件，建议结束本轮本地复杂竖排研发，保留YOLO26n+RepSVTR+v0.1.3完整列裁剪稳定基线；复杂排版仅联网且用户授权时交云端，离线明确提示不可靠；保留DB及v0.3.0代码和Release供未来复用。尚未完成同算法双模型全套对照及原图真值核验，不宣称DB普遍不如YOLO。当前仅记录阶段性结论，不修改正式APP、不实施回退、不发新APK，后续集成或更改需用户授权。不上传私人OCR原文/图片。


## 2026-11-26 — v0.3.0价目表YOLO26n真机对照完成
用户回传Adaptive v0.3.0 YOLO26n、3072×4096、CPU4、tiles=false、layout4两轮，raw111 unique107 blocks47 crops67 retry_crops6，34 suspect、6空框、0 vertical，原始框、识别及复核结果两轮一致。global427/328ms，plan17/8ms，rec1952/2010ms，total2396/2346ms（均2371ms），PSS513872/691458KiB，识别器加载278ms、检测器首次156ms另计。按此前价目表预期内容十项核心金额均一致且未标待核，五组大间距项目完整，两组充值分隔，仍存在充值附近两个疑似杂框及大量背景小字错误。与同版同图DB layout4（均1415ms、36主裁剪+6复核、9 suspect、3空框、两金额符号冲突待核）比较，DB全图耗时低40.3%；总差956ms中识别阶段875.5ms，占91.6%。YOLO主内容金额表现更好，DB更快，但原厂输入尺寸不同、检测覆盖不同，未核验原图真值，不能据框数或置信度计算准确率/召回率。历史v0.1.3最快1116ms是主区域裁剪策略，不与全图均值直接等同。结合DB竖排v0.3.0未达到可读正文条件，维持此前建议：正式本地OCR保留YOLO26n+RepSVTR及v0.1.3稳定横排基线，复杂排版联网且用户授权时云端处理，离线不可靠应明确提示。v0.3.0独立实验和源码完整保留。当前仅记录新测试；未获进一步授权，不实施正式APP修改、回退或新构建，不存私人OCR原文/图片。


## 2026-11-26 — 用户提供价目表照片，人工真值核验
用户在聊天内提供价目表照片，上传副本1152×1536，与此前3072×4096日志中价目表布局和文字相符（非原始分辨率字节同一性验证）。人工核对主牌十项服务名称/价格、两组充值内容，确认全部价格为正数，服务与价格之间有点状引导线，疑似影响DB裁剪识别符号，具体像素因果仍未证明。v0.3.0同图layout4：YOLO26n十项价格首读10/10与真值一致，DB首读8/10，两项负号确认为错且已待核，两项扩框复核候选均与真值一致（事后人工选择可达10/10，并非程序自动纠正）；两模型十项服务名称及两组充值内容均与真值一致。仅主价目表人工核验，不代表整图或通用准确率，背景小字、杂框、竖排真值尚未核验。维持原v0.1.3稳定横排基线建议，不改代码、正式APP或发布；仅存实验统计和原因假设，不保存照片及原文。


## 2026-11-26 — 用户授权主阅读区优化：v0.4.0独立实验
以v0.1.3发布提交38df92b5bec824a2abf269e2d775e6efcd241455为基线，已核对原TextLocator、Reader、RowPlanner、ColumnPlanner及构建工作流。旧版本已有按连通群分数/中心距离选ROI；新FocusPlanner纯几何去重锚点计分、文字尺度与邻接、多行支持、竞争区域判断，不强制居中；稀疏、疑似竖列、多个重要群或外部重要群回全图，区域外灰框、未读提示和坐标日志保留，可手动全图。仍是启发式选择，可能漏选重要文字；回全图不是解决竖排。原YOLO26n、RepSVTR、字典、行列裁剪及Reader不变，不复读；新模式强化待核阈值（均值<0.75/最低<0.65/空），保留原v0.1.3区域模式作基线及全图模式，同图对照共享一次检测、三模式各两轮，CPU4默认，检测器固定4识别器可2/4/6，耗时不含加载/预览，PSS非峰值。新增5项几何单测，复用v0.1.3 APK固定哈希、逐资产哈希校验及Android单测/签名/无网络权限检查。独立分支exp/openocr-focus-20261126 HEAD c5e20868f61a1bf41a937d99af4df68ee913188e，六文件diff已检查并推送，包名com.vobich.bluestar.openocrlab.focus，拟发布bluestar-openocr-focus-v040-20261126。Actions 45791503828 当前in_progress/未结束。 APK发布及真机性能尚未核验，不声称改善或提供未验证下载。先同价目表三模式对照，再用不同横排照片验证重要文字覆盖及多区域/偏心/稀疏回退，不因速度牺牲读全重要信息。原正式APP、旧Release与复杂竖排停止决策不动；无联网/TTS/Gemma，不上传照片或OCR原文。


## 2026-11-26 — v0.4.0主阅读区实验APK发布
Actions 45791503828 build success，原v0.1.3资产校验、Android单测/编译、APK签名/包名/无网络权限校验及上传均通过；publish步骤与Complete job均success，查询时job总体状态仍in_progress。Release已回读确认非draft、prerelease=true，tag bluestar-openocr-focus-v040-20261126，target c5e20868f61a1bf41a937d99af4df68ee913188e，APK BlueStar_OpenOCR_Focus_0.4.0.apk 43766329 bytes，SHA256 67c370be76145b8a4de192fc6ff53c239dd71122c716559da61e76fdb7116f20。下载：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-focus-v040-20261126/BlueStar_OpenOCR_Focus_0.4.0.apk 。独立安装，旧版本及正式APP未动。下一步同价目表原图CPU4选择同图区域对照（新/旧/全图），三个模式各两轮，核对核心内容、金额、选中/区域外框数、耗时及待核，再用不同横排照片验证区域回退。真机效果仍未验证，不上传原图或OCR原文。


## 2026-11-26 — v0.4.0智能主阅读区价目表真机首测
用户回传Focus v0.4.0 3072×4096、检测/识别CPU4、mode0两轮。共享detect421ms、detector_init199ms、recognizer_load218ms另计；focus_plan2ms，dominant_region，groups6，best199.9536/rival46.4946；ROI(945,1051,1925,2703)，原始111框、选44、区域外67（60.4%）、16行/28裁剪、去重4、行列规划1ms。rec943/800ms，first_row62/28ms，total1367/1224ms（平均1295.5ms），PSS472444/510512KiB，3 suspect、0空框，两轮输出与置信度一致。根据此前照片人工真值，十项金额10/10、项目与金额对应、充值两组、标题及底部署名保留；一个正确的短语带前导全角空格而被待核（rec.8341/min.3367），另两个疑似装饰杂框待核（rec.1886/.3078）。不把待核直接当成错字或删除。该图主要阅读区覆盖成功，区域外仍有实际包装文字，已提示未读并保留全图入口。相对v0.3.0 YOLO全图均2371ms耗时降低45.4%，67主裁剪+6复核变28裁剪无复核；策略不同，不能将收益全部归于区域规划。v0.1.3最快1116ms亦非同次均值。当前日志仅有mode0，缺同图mode1原区域与mode2全图对照；下一步优先取得这两模式各两轮（若已跑完直接复制后半日志），再测两张不同横排图验证稀疏/多区域/偏心条件。不需要立即改代码或新构建，暂不宣布替代稳定版，不上传私人原图或OCR原文。


## 2026-11-26 — v0.4.0价目表全图模式真机对照
用户回传Focus v0.4.0 3072×4096、检测/识别CPU4、mode2两轮。共享detect402ms、detector_init147ms、recognizer_load194ms另计；focus_plan2ms且候选分数与mode0一致，但全图roi=null，选111框、区域外0、47行/66裁剪、去重8、行列规划5ms。rec1665/1634ms（均1649.5ms），first_row34/25ms，total2072/2041ms（均2056.5ms），PSS524454/488372KiB，34 suspect、9空框；两轮输出、坐标与置信度一致。与此前原图人工真值核对，十项金额10/10、项目对应、充值及标题署名均保留。与单独启动的mode0均1295.5ms比较，总耗时减少761ms（37.0%），识别阶段减少778ms，66→28裁剪（减少57.6%），34→3待核（减少91.2%，非准确率提升，主要因少读区域外背景）；全图仍含大量小字错误。主区域与全图的同一核心短语裁剪坐标不同，全图该短语无前导空格且rec.999737/min.999304，主区域rec.834103/min.336701；英文标题最低置信度全图.954977、主区域.702852。可能涉及全局倾角、行列规划及扩框差异，尚未做消融验证，不靠放宽阈值解决。两模式为独立启动，非共享同一次检测。当前仅缺mode1原v0.1.3区域基线两轮；建议取得后再测不同横排场景（偏心、多重要区域或稀疏），不立即改代码/构建/替代正式APP。不存私人OCR原文或照片。


## 2026-11-26 — v0.4.0原区域基线真机测试及三模式结论
用户回传Focus v0.4.0同价目表3072×4096、检测/识别CPU4、mode1两轮；detect339ms共享、detector_init135ms、recognizer_load172ms另计；focus_plan3ms（不计入mode1总耗时），roi(958,1064,1912,2690)，原111框、选44、外67、16行/28裁剪、去重4、plan1ms。rec677/711ms（均694ms），first_row36/24ms，total1017/1051ms（均1034ms），PSS427535/466445KiB，2 suspect、0空框。与mode0的28个裁剪坐标、文字、det/rec/min数值一致，ROI四边仅相差13像素，实际选框相同。mode1按原待核策略不显示两个疑似装饰框；mode0另标一个带前导全角空格的核心短语待核，非识别精度差异。三模式均匹配既有人工核对十项金额和核心项目、充值；mode1/0/2平均总耗时1034/1295.5/2056.5ms，裁剪28/28/66，待核2/3/34（阈值和输出策略不同，不是错误率），空0/0/9。三个模式独立启动、共享各自检测一次，检测339/421/402ms、识别均694/871.5/1649.5ms；运行状态波动，不能把mode0相对mode1的261.5ms差值归因于区域算法本身。该图新区域算法没有新增识别收益，原区域模式实测最快、输出更简洁；新算法多区域/偏心/稀疏回退的潜在价值尚未真机验证。建议保留v0.1.3为稳定基线、v0.4.0作候选，暂不换模型、不修改代码、不构建、不合入正式APP；后续用两张不同横排照片（偏心主体、两个重要阅读区）同图对照，再决定是否采用新区域策略。全图入口及未读提示保留，不自动删除不确定数字，不继续复杂竖排研发。仅备份汇总，不上传OCR原文或私人照片。


## 2026-11-26 — Focus v0.4.0电商截图智能全图回退真机验证
用户回传1260×2800电商页面截图mode0、检测/识别CPU4两轮。detector_init141ms、recognizer_load159ms另计，共享detect335ms、focus_plan2ms，focus_reason=whole_image、groups6、best342.5451/rival13.4504，roi=null、原93框全选、outside0、33行/52裁剪、去重6、plan3ms。rec1639/1858ms（均1748.5ms），first_row59/94ms，total1979/2198ms（均2088.5ms），PSS362925/406161KiB，14 suspect（26.9%）、0空框，两轮输出与置信度一致。相比价目表dominant_region，本样本自动采用全图，初步证明区域回退路径在真机有效，但不能证明全部文字无漏检或优于旧区域算法。截图输出包含疑似商品名及型号字母数字混淆、促销价与原价粘连、相邻模块插入跨行字段、小字及图标误读；部分疑似错字置信度高且未标待核。原图尚未逐字核验，不报准确率。下一步同一截图mode1两轮，比较选框、outside、核心字段与跨行问题；如有需要再测mode2，另测偏心主体/双重要区域。继续保留稳定基线，暂不改代码、不构建、不换模型、不合入正式APP。截图日志含私人收货信息，严禁备份原始OCR文字、身份地址及照片，仅记录脱敏指标和问题类别。


## 2026-11-26 — 电商截图原图人工核验
用户提供截图上传副本691×1536，与此前1260×2800 mode0日志画面内容对应，非原始字节同一性验证。人工核对确认标题首字错误且未待核（rec.9679007/min.82735574）；套餐说明末尾四字漏读；已选规格、两种型号选项、四种容量选项、当前价/划线原价的数字及负向优惠符号保留，但文本输出未区分划线原价和当前价，也未保留选中状态的视觉关系，已选字段被相邻模块插入而断行。品牌标识的字母/数字形态存在歧义，不断言全部型号识别错误。尚未逐字核验全部小字，不报全图准确率。当前保持v0.1.3识别核心，优先取得同图mode1对照以判断区域策略实际收益；阅读顺序和视觉语义为独立问题，未来可评估模块化排序、无障碍节点（有可用节点时）或用户授权的视觉辅助，不进行硬编码商品名、金额猜测，不改变正式APP、不构建新版本。仅保存脱敏问题类别和指标，严禁上传截图、姓名、地址、原始OCR文本。


## 2026-11-26 — 电商截图原区域基线对照及阶段性选型
用户回传Focus v0.4.0同1260×2800电商截图CPU4 mode1第一轮（本次未提供第二轮）。detector_init0/recognizer_load0，为模型已加载；detect305ms、focus_plan2ms（不计入mode1 total）、plan2ms、rec1602ms、first_row50ms、total1909ms、PSS499537KiB。roi=null、93框全选、outside0、33行/52裁剪、去重6、3 suspect、0空框。与mode0两轮的框坐标、原始识别文本及置信度一致，旧模式隐藏3个待核框，其他11个mode0待核在mode1未标；不能将待核数降低当成识别改善。原图已核验的标题错字、说明漏字、跨模块排序、价格语义问题均未解决。mode0第一轮1979ms、第二轮2198ms，mode1仅一轮且热启动，不宣称速度算法优势。结合价目表，新旧区域在两个样本的选框和原始识别结果均相同，尚无证据证明新区域优于旧区域；建议停止在这两张图反复测试，保持v0.1.3识别核心及原区域为稳定基线，v0.4.0独立实验保留，若继续区域验收可用偏心主体及双重要区域样本一次对照；优先考虑未来按模块排序、跨行拼接与重要字段待核，保留全图入口和未读提醒，需用户授权后才修改或集成。未修改代码、未构建、未改正式APP，不上传私人收货信息、截图或原始OCR文本。


## 2026-11-26 — 用户授权眼脑协同 A 独立实验
用户明确忽略下午空壳，重建独立实验，不改正式蓝星/蓝心。基于 Google AI Edge Gallery 1.0.23 commit 9cb31cf47c21b0ed76901ff589a52b509803edfa 原工程，保留模型/推理/会话/采样依赖，历史 Gemma-4-E2B-it GPU，模型文件由原版管理下载/导入，实际revision待核。OCR v0.1.3 commit 38df92b5bec824a2abf269e2d775e6efcd241455 五个核心文件按原blob复用，原APK与三资产SHA校验设入CI；CPU4、原ROI、完整列单轮及待核规则。新增相册OCR按钮/预览/待核/填入原聊天输入框，用户确认后用原发送入口；OCR完成释放会话，加载开销待测。仅首页隐藏演示、136项常用中文资源、禁用Firebase统计及备份，仍有原模型管理网络权限及可能的次级英文入口。包com.vobich.bluestar.eyebrain.gallerya v0.1.0；独立分支 exp/eye-brain-gallery-a-20261126，HEAD 36325b0fff33082c6e59b47c428c9f1e49107f21，回读比对仅10个新增实验/workflow文件，翻译键与主要源码锚点检查通过。Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45841919918 已排队；尚未验证编译、签名、发布或真机速度、GPU/原生库兼容性。拟Release bluestar-eye-brain-gallery-a-v010-20261126。不要声称APK可下载直至回读Release。正式APP不动，不上传私人OCR文字或图片。


## 2026-11-26 — 眼脑协同 A v0.1.0 发布成功
Actions 45841919918 build/publish均completed/success，固定上游源码、OCR资产校验、Gradle release编译、APK签名及包名、原资产复核与发行均通过。Release bluestar-eye-brain-gallery-a-v010-20261126 回读确认非draft、prerelease、target 36325b0fff33082c6e59b47c428c9f1e49107f21。APK BlueStar_EyeBrain_Gallery_A_0.1.0.apk 430910268 bytes，SHA256 99983d59bdcf8ab45e0308beada65ff191cd45554c2fcd37db817773dc6157c7；下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-gallery-a-v010-20261126/BlueStar_EyeBrain_Gallery_A_0.1.0.apk 。真机安装、Gemma原模型加载、OCR到聊天传输及GPU速度尚待验证。优先完成眼脑纯文本接入；用户确认后续再研究包装艺术/潦草字体识别器与倾斜/透视招牌检测，暂不修改。正式APP不动，不上传私人照片或OCR原文。


## 2026-11-26 — 眼脑协同 A v0.1.0 首次真机反馈
用户反馈运行挺稳定，截图显示中文离线AI对话中已产生产品信息分类与总结，支持纯文本眼脑链路初步跑通；尚无同输入原版速度对照、模型revision/后端、完整OCR原文与真值、断网及连续多轮性能日志，不宣布全面验收。截图显示数学标记未渲染，部分疑似OCR杂字进入归纳；需对照原始OCR与图片判断错误来源，不擅自改数字。另用户此前报告倾斜招牌漏读、正面较好，未来应区分检测/裁剪和识别错误；优先完成眼脑协同验证，再研究倾斜招牌检测、包装艺术及手写字体识别。仅备份脱敏结论，不存截图或产品原文，不改代码、模型、正式APP或Release。


## 2026-11-26 — 眼脑协同 A v0.1.0 多轮上下文与飞行模式初测
用户反馈上下文及回忆能力良好；真机截图状态栏显示飞行模式图标，聊天标注模型 on CPU，跨话题追问第一组 OCR 资料中的名称得到具体回答，界面显示该短回答2.5s、前一较长回答27.8s。初步支持同会话跨图片/话题回忆与飞行模式运行；尚未独立核验网络连接状态、第一组原图及原始OCR真值、模型revision、上下文上限、首字及吞吐指标，不认定长期记忆或严格原版性能对照通过。建议保留谷歌原版会话逻辑，下一步验证多图连续运行、上下文长度及新会话隔离；后续再研究OCR准确率。仅记录脱敏结果，不上传截图及原始识别文字，不改代码、正式APP、模型或Release。


## 2026-11-26 — 用户授权眼脑全自动任务实验 v0.2.0
用户要求取消半自动确认/填入/手动发送，增加多任务并重新制作，不重复大量手动测试。独立分支 exp/eye-brain-auto-20261126 从A v0.1.0 36325b0fff33082c6e59b47c428c9f1e49107f21 派生，提交 08977a0f4e57d02d22a3b85901e39992e6b32b2d，回读HEAD及4文件diff确认仅实验适配器/构建脚本/说明/workflow变更。默认快速阅读；另有完整朗读文本、商品信息、招牌路标、菜单价目表、通知步骤、中文翻译、自定义提问。选图后OCR结果与任务指令直接交原版createMessagesToSend/onSendMessage，保留上次OCR原文/待核/区域外/耗时；无可用文字或状态变化不自动发送，不自动执行操作或后台取图，语音仍手动播放。原OCR v0.1.3模型及源码与原谷歌推理/会话保持不变，提示词不能保证无幻觉。新包com.vobich.bluestar.eyebrain.galleryauto v0.2.0，独立安装避免CI debug签名覆盖风险，需重新导入/下载模型。Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45847086626 当前in_progress。编译、签名、发行及真机尚未确认，拟Release bluestar-eye-brain-auto-v020-20261126。正式APP、原A和旧Release不动；不上传私人照片及OCR原文。


## 2026-11-26 — 眼脑协同自动任务 v0.2.0 发布成功
Actions 45847086626 build/publish均completed/success，固定上游及OCR资产检查、Gradle编译、APK签名及包名、资产复核与发行全部通过。Release bluestar-eye-brain-auto-v020-20261126 回读非draft、prerelease、target 08977a0f4e57d02d22a3b85901e39992e6b32b2d，APK BlueStar_EyeBrain_Auto_0.2.0.apk 430926668 bytes，SHA256 1e44374aed498e2daaf7975466832b431fd04f5279f5597d97e782f2496c34e4，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-auto-v020-20261126/BlueStar_EyeBrain_Auto_0.2.0.apk 。独立包需要重新导入模型，自动提交与八任务真机表现待验证。用户另同意复用原六模型擂台 v0.2.0 排查倾斜发光招牌漏检，暂不修改模型或正式APP。无私人截图与OCR原文上传。


## 2026-11-26 — 自动眼脑 v0.2.1 提速实验启动
用户反馈v0.2.0正常运行但感觉慢，授权提速。新独立分支 exp/eye-brain-speed-20261126 从08977a0f4e57d02d22a3b85901e39992e6b32b2d派生，提交d9bba40c645143c722b7cc204949e67d276700dd，回读HEAD及四文件diff已确认。原OCR v0.1.3核心/权重、原Gallery推理/采样/会话、8任务及自动提交保留。适配器改串行worker持有检测与识别会话，默认90秒短时复用、低内存回调及ActivityManager检查、离开组件/关闭常驻/异常释放；可关闭常驻对照，Android回调不保证及时且真机内存待验证。显示OCR冷热/总耗时/模型加载/解码/检测/识别；短提示词，快速阅读30~80字软目标，重要内容不截断；启用原ChatPanel实时指标入口（指标可用时），未修改GPU/CPU默认值、思考、推测解码或模型权重。自动流式TTS本轮未做，保留原语音按钮。新独立包com.vobich.bluestar.eyebrain.galleryspeed v0.2.1，需导入模型，旧版/正式APP不动。Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45851079682 当前in_progress，编译/签名/发行/实际提速均未验证；拟release bluestar-eye-brain-speed-v021-20261126。无私人截图/OCR原文上传。


## 2026-11-26 — v0.2.1发布及v0.2.2轻量实验
用户反馈v0.2.0 GPU已运行，截图上轮4.7s，主要等待在本地OCR弹窗；另首启模型列表联网失败、英文欢迎及通知权限干扰，要求下一版极简。v0.2.1 Actions 45851079682 completed/success，Release回读非draft、target d9bba40c645143c722b7cc204949e67d276700dd，APK BlueStar_EyeBrain_Speed_0.2.1.apk 430943048 bytes sha256:2e68f2ce37b765876391087ebd06ed673cb9a6f6d5d1c6fac060614a83e8ca5d https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-speed-v021-20261126/BlueStar_EyeBrain_Speed_0.2.1.apk，真机提速仍未验证。v0.2.2 独立分支 exp/eye-brain-lite-20261126 从v0.2.1 d9bba40c645143c722b7cc204949e67d276700dd派生，提交0f59048bec760e1fd88a97907ed6d19187d497d0，四文件diff确认。原版本号被用于联网列表URL，改为APK内置固定上游1.0.23目录中的Gemma-4-E2B/E4B原模型条目，仅llm_chat，原权重及revision不动；关闭自动下载，极简首页仅聊天、模型管理、原厂条款查看；取消强制欢迎与首页通知请求、不代用户接受条款、保留模型条款门控；停用FCM自动初始化及调度入口、manifest移除通知和开机权限等，CI核验合并APK权限和本地列表。主动下载及网络权限保留，系统下载状态仍可能出现，不宣称所有深层依赖移除或体积显著减少。保留90秒OCR缓存/分段耗时/8任务/原厂指标及推理，独立包com.vobich.bluestar.eyebrain.gallerylite，需导入模型，正式APP与旧实验不动。Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45852848638 queued，编译签名发布和断网首启/通知真机未验证，拟tag bluestar-eye-brain-lite-v022-20261126。仅脱敏结论，不上传截图及OCR原文。


## 2026-11-26 — v0.2.2发布及v0.2.3精简实验
v0.2.2 Actions 45852848638 completed/success，Release回读非draft，target 0f59048bec760e1fd88a97907ed6d19187d497d0，APK BlueStar_EyeBrain_Lite_0.2.2.apk 430911097 bytes SHA256 27e22ec0984b87a3a6beff3545576e17b1a15123bffbe68d9a756bf0f22490f2，下载 https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-lite-v022-20261126/BlueStar_EyeBrain_Lite_0.2.2.apk 。CI通知/开机权限、内置目录及资产校验成功，真机未验证。用户要求尽量移除无关原厂功能。独立分支 exp/eye-brain-trim-20261126 从0f59048bec760e1fd88a97907ed6d19187d497d0派生，提交fcd28918b5d1ad50001086d8bdf9b126c0369a05，四文件diff回读确认。v0.2.3设仅ARM64、移除日历权限、XR/xr_earcons/tinygarden专用资产、取消七个演示任务的Hilt注册并筛选llm_chat/llm_test（后者保留导入兼容）；取消OCR等待模态框，行内状态、识别中阻止手动发送、自动提交核对会话ID/草稿/状态，异常提示保留。共享DI及依赖暂不物理删除，未开R8，不宣称全部SDK删除；实际减幅待CI，原脑核心、OCR模型、90秒缓存、计时、八任务及语音、模型管理、必要许可保留；未做主动预加载和流式TTS。独立包 com.vobich.bluestar.eyebrain.gallerytrim，需导入模型。Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45854209619 in_progress。拟Release bluestar-eye-brain-trim-v023-20261126，构建及真机未确认。正式APP、旧实验及发布保持不动，无私人图或OCR原文上传。


## 2026-11-27 — OpenOCR 实地朗读 v0.4.2
用户六检测器擂台实测一张夜间倾斜招牌图：热启动 n 534ms/s 604ms/m 879ms/l 1074ms/x 1854ms/RepViT-DB 506ms；s、x、DB检出中文主招牌，DB另覆盖其它小字，但遗漏具体时刻；单图不同输入尺寸，不代表普遍优越。用户要求直接基于v0.4.1原文朗读软件加入DB做实地测试，不接大脑。独立分支 exp/openocr-field-db-20261127 从c550509adb2997c5a009a6c5c4a461df4e7f393c派生，源码提交088988a12ea76343c436b142b7ff203fd6431bc1、构建提交e630d3e0267cd311457ffb3d7071ebdf433c4b82，七文件diff回读验证。DbLocator/MixedLayout/VerticalTracks三文件按竞技场5ac4b9a853a4e2eee539894ac9f76f53ccc3aaaa原blob复用，CI从已发布arena APK提取同份repvit-db.onnx并核验APK与模型hash；原Reader、RepSVTR、字典、YOLO26n权重保留，OpenCV4.12.0；默认DB+全图原框去重与逐框识别，原区域模式保留，每次只运行一个检测器，切换释放，CPU4默认，原始输出/TTS/同图复测/复制诊断保留。DB调用附带原组计算但原框模式不按ROI筛选，计时与擂台并非完全相同。独立包com.vobich.bluestar.openocrlab.fielddb v0.4.2，ARM64，CI核验无INTERNET权限、签名和固定权重；系统TTS是否离线取决于设备。Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45855950792 in_progress，拟tag bluestar-openocr-field-db-v042-20261127，构建发布及真机待验证。正式APP及之前实验不动，无私人照片和OCR原文上传。


## 2026-11-27 — 实地朗读 v0.4.2 和眼脑精简 v0.2.3 发布确认
Actions 45855950792 build/publish success，Release bluestar-openocr-field-db-v042-20261127 非draft，target e630d3e0267cd311457ffb3d7071ebdf433c4b82，BlueStar_OpenOCR_Field_DB_0.4.2.apk 65706982 bytes SHA256 9ded794aa82f543af10499b2ccf42b5eeab9eb1552606f31dfb85dd83f86e517。Actions 45854209619 build/publish success，Release bluestar-eye-brain-trim-v023-20261126 非draft，target fcd28918b5d1ad50001086d8bdf9b126c0369a05，BlueStar_EyeBrain_Trim_0.2.3.apk 211705431 bytes SHA256 2bcae88131d88fc9792766fc9d0995b28664589a8bb653e7d76c7956ded6355d。两包真机待验证。用户眼脑截图冷启动OCR1950ms/加载458ms/大脑CPU回答7.1s，选择GPU确认即退出，重启CPU；用户报告关闭OCR常驻、重启、关闭推测解码的排查方案仍不行（未单独确认每个操作），原因无崩溃日志不能确定，不能归咎OCR缓存；原v0.2.0曾GPU运行。GPU修复未实施，v0.2.3不含专门GPU修复。建议核对具体安装版、模型revision及获取系统崩溃日志。正式APP不动，未上传私人截图或OCR原文。


## 2026-11-27 — GPU兼容性回归实验 v0.2.4
用户授权独立修复包。基于曾真机GPU正常的v0.2.0 08977a0f4e57d02d22a3b85901e39992e6b32b2d创建exp/eye-brain-gpu-20261127，恢复原厂模型目录路径、原始ABI及任务注册、无OCR常驻的基线；保留八任务及自动提交，原模型权重和推理不改。Android versionName保留1.0.23以兼容原厂目录URL，versionCode 6，首页实验标识v0.2.4，独立包com.vobich.bluestar.eyebrain.gallerygpu。源码6d75dd44b8818787521120ab90d91ec340b15b23，构建46fcd2239c95c0d1432c701fc5b60ae8ad20cece，拟tag bluestar-eye-brain-gpu-v024-20261127。此为回归隔离而非已定位根因的修复，暂不保留v0.2.3全部精简及弹窗优化；构建、发布、真机尚未验证。正式APP不动。


## 2026-11-27 — 用户确认 RepViT-DB 专项优化研发方向
用户实地反馈多个店铺招牌：原厂DB对倾斜招牌、街道自然文字更有优势，既有YOLO26n对规整价目表更好；尚无逐字真值统计。历史YOLO经过v0.1.3完整列裁剪/ROI、v0.4.0主阅读区等多轮针对性优化，DB虽有v0.3.0通用行列重建/扩框复核实验，但v0.4.2默认全图原框去重逐框识别，不能将不同工程成熟度当作检测器能力差异。历史同图layout4 DB平均1415ms、YOLO2371ms；十项价格首读DB 8/10、YOLO 10/10，DB两错误负号的扩框候选经人工核对均正确，不代表自动纠正。用户同意优先规划独立OpenOCR v0.4.3 DB专项：P0核对官方预/后处理与Android移植；P1四边形透视矫正及裁剪；P2 DB专属横排行列重建；P3项目金额对应；P4主阅读区适配；P5初始化、导入及识别提速。保留原DB模式和YOLO稳定基线，逐项消融，同图验证自然场景与价目表，不能牺牲街景覆盖或猜测金额；若DB兼顾两类场景再考虑单检测器，暂缓自动双模型切换。此轮仅记录方向，不改源码、不构建OCR新版，不动正式APP，不上传私人照片或原文。
