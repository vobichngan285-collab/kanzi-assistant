# v0.1.10 GPU根因诊断（不含v0.1.9 CPU容错）
基于v0.1.8 71fcc53c57cf7063765546804325e1ca409b53b0。保留原模型、原始裁切、CPU、调度及形状。新增OpenCL host transfer返回码记录（并非覆盖所有OpenCL命令）。异常时同一共享内存输入SHA256、原始异常、原会话普通推理重放及逐算子同步回读统计（shape/type/finite/sentinel/min/max）、同精度及FP32/混合精度独立新会话无旧缓存对照。原始结果不被替换；异常依然失败，整批空白仍按原逻辑返回但记录诊断。回调同步可能改变GPU故障表现，不能据此单独断定根因；诊断耗时不能作为性能基准。每个GPU worker初始化后仅自动诊断首次异常，日志有ROOT_DIAG_BEGIN/ROOT_SAME_SESSION/ROOT_OP/ROOT_TRACE_END/ROOT_FRESH/ROOT_DIAG_END。默认智能主阅读区、第7项、CPU6、DB1600、NONE_LOW。仅使用此前连续失败5次的样本，运行一次，导出完整TXT；如GPU服务死亡或超时，保留日志。v0.1.9作为独立容错实验保留；不以CPU容错掩盖诊断。该版本不是正式性能候选。
 
# v0.1.8 GPU精度终局实验
直接从v0.1.6 ec04494ff3d098b282cd65694389938dbdc80687 派生，不继承v0.1.7异步预热。第7项原始宽度协同、CPU6、DB1600、官方原框、NONE高精度为默认。新增两种独立缓存策略：MNN Precision_Normal（FP16存储、FP32计算宏）与 Precision_Low（FP16存储、FP16计算宏），均关闭调优，保留FP32对照；具体算子可能采用其他精度。真机单GPU OpenCL half配置与扩展必须确认，否则拒绝候选。原模型的浮点compact字符编号超过2048不能安全使用半精度，因此候选保留原ArgMax整数中间张量并独立读取int32编号，校验形状、长度和范围，恢复原有[B,T,2]接口；若名字/类型不匹配则明确失败，不静默使用半精度编号。原权重、原CPU、Reader、批次、DB、裁切、几何、缓存生命周期、JNI高精度主路径均保留。额外整数读回有成本，可能抵消收益；低精度也可能引入真实模型精度误差。原始MNN全概率门槛仍失败，不宣称正式替换。PRECISION_COMPARE以同图同设置第7项NONE高精度结果为参考（不是人工真值）。独立APK，不覆盖旧版；先76框/71框各高精度与候选热态对照，保留失败日志，不需重做纯CPU或FAST。只有明显整体性能提升且逐框文字、keep、稳定性与内存无明显退化才考虑采用，否则冻结v0.1.6 NONE。v0.1.7仅历史记录，停止后续开发。

# v0.1.6 GPU冷启动专项实验
从已真机通过17/17、三次低内存GPU死亡自动恢复成功的v0.1.5发布源码0a32d43d69df5d27fc549dfe9b95e606d2fa76e1直接派生，见 BASELINE_V015_20261128.md。v0.1.4/v0.1.5 APK及日志不覆盖。
仅增加GPU调优选项：FAST（原v0.1.5策略，默认）/NONE（候选）。固定MNN3.5.0源码 c35f14f3ab5cb65094863b9a0e888370b027a670 的 include/MNN/MNNForwardType.h 及 source/backend/opencl/core/OpenCLBackend.cpp 已核对：OpenCL支持两种独占调优标志，NONE禁用调优，可能降低运行性能。两种均使用 Precision_High、BUFFER、原始shape/模型/解码与协同调度不变。禁用调优不等于免内核编译、不承诺冷态加速或识别完全一致。FAST缓存名称延续旧版，NONE使用独立文件，切换策略关闭旧会话后创建新会话，不清空任何缓存、不并存两个GPU引擎；本包为独立安装，不能访问v0.1.5私有缓存。原Binder死亡处理、BIND_IMPORTANT、受限首批前恢复政策、GPU真实命令校验均保留。新增请求时缓存字节数/文件名、实际调优策略、切换原因/恢复区分及原批次日志。没有后台预热、隐藏计时、CPU替代或更改固定宽度模式；默认仍第7项6线程DB1600。
测试同一76框公告：先第1项ORT CPU6一次作为文字参考，再第7项FAST第一次（空cache）及复测两次，然后第7项NONE第一次（独立空cache）及复测两次。两组首轮可能受驱动全局缓存/先后顺序、CPU模型热态及动态分配影响，不能称完全控制实验；以实际GPU批次shape及resize为主、完整OCR为主用户指标，后续交替比较热态，必要时用Android强制停止应用后重新开启（保留数据，不清缓存）测有cache的进程冷启动。不要反复清除旧版数据。保留全部成功/失败日志，比较REFERENCE_COMPARE文字/keep/width、实际GPU命令、缓存字节、resize、GPU批次耗时和complete_ms。若NONE冷态改善但热态退步或质量不符，不能直接替换FAST。严格MNN数值门槛仍失败，106框热态实际计算瓶颈另行处理。CI校验原识别与生命周期关键代码，JVM新增调优值/缓存隔离测试；真实GPU效果待验证。
 
# v0.1.5 GPU 生命周期定向修复（实验包）
直接继承冻结 v0.1.4 425ee3986e6d962d166eaf27e63c3b7b3dbfbdf1；完整基线见 BASELINE_V014_20261128.md。真机32次记录30成功/2失败；公告同6线程最佳纯CPU3553ms vs 原宽协同2254ms，文字/keep/width一致；价目表热态均值1262ms，酒店920ms。两次换图首次失败均为旧GPU worker被系统以reason=3 low memory提前终止，v0.1.4只判断对象非空，误复用失效连接。不能据此证明旧原生崩溃根因或内存泄漏。
本版增加 Binder linkToDeath、ServiceConnection失效标记、不可复用的连接代际、识别前isBinderAlive/pingBinder检查和主动重建，保留同一健康会话及磁盘cache；BIND_IMPORTANT提高绑定期间GPU进程重要性，不能保证Android不回收。只有第7项在确认传输连接已失效且首个GPU推理尚未提交时，允许一次明确记录的恢复并重新执行完整REC（两lane已join，丢弃部分结果）；不重试in-flight传输异常、模型/解码异常或超时，不静默回退CPU，不发布不完整原文。新日志GPU_CONNECTION_CHECK / GPU_CONNECTION_READY / GPU_PRE_DISPATCH_RECOVERY / GPU_EXIT_INFO / generation。
相册仅预览缩至长边1200，原图bm及检测/裁切/识别完全不变。第7项、CPU6为默认，旧8项/线程可选项保留。独立包cooperative015 versionCode6，v0.1.4旧安装、APK及日志不覆盖；缓存机制/名称不变但新包无法直接访问旧包私有cache，第一次安装或遇新shape仍可能很慢。此次不声称解决冷缓存调优、原生驱动崩溃或全部内存压力。
CI增加v0.1.4 Reader/CoopCore/检测/几何/历史/原生脚本逐字冻结检查，保留原所有哈希及数值校验；新增受限恢复策略JVM测试。Binder真实生命周期和速度需真机验证。先1项CPU6→7项协同6同图重复；再连续换公告/价目表/酒店/返回公告，含相册停留及前后台；导出累计日志，不手动重试失败。比较逐框文字、实际GPU命令、首次成功率及冷/热延迟；不把首次调优计为热态性能，不把CPU一致性当真值。
 
# v0.1.4 原形状协同与尾批诊断
v0.1.3真机第6项run 2c515ae2-6118-4e73-afd7-6afb4a8a37b9，原图33ee7070...，前72框原宽度GPU成功，东兰小/2/区与CPU一致；最后B6 W421仍断开，未完成OCR，不能把3861.8ms当整页耗时。12批resize 75.084/14.9609/22.5393/20.684/12.0132/13.3157/11.1178/81.3865/9.58646/14.1083/17.0058/66.6172ms；cache_before_bytes=1032832；缓存相关改善但因果未单独验证。第5项用户报告失败，第2项截图仍麻州小区，首段TTS回调3672ms；第3/4项完整记录未收到。保留v0.1.3所有模式。
新增第7项CPU_GPU_EXACT：原始批宽及批大小不变；官方不足一批的最后组预留CPU，第一完整组GPU，其余动态领取，按原序汇总；仅一组走CPU避免虚假协同；不隐藏GPU故障，不静默回退，不降低精度门槛。尾批CPU是明确调度策略而不是GPU崩溃根因修复。新增第8项GPU_TAIL_FIRST：新解释器/会话，同进程可能，保持磁盘缓存；最后一组先以第6项同形状（4补6，原宽421）执行，再按原序完成剩余组，检验特定形状或切换历史影响；不是产品路径。新增worker PID及Android 30+历史进程退出信息（可能尚未生成）；原生JNI、模型、原CPU检测裁切解码、缓存名称/格式不变。独立包cooperative014，首次安装无旧包私有缓存，需区分冷态与重复热态。测试先1→7连续3次（包括失败日志），再8一次；记录完整OCR、REFERENCE_COMPARE及GPU_EXIT_INFO，不将失败计入完整时延。此版未真机验证，仍不用于正式产品。

# 蓝星 OpenOCR CPU＋GPU v0.1.3 精度隔离实验

独立包 cooperative013；v0.1.2 冻结。真机同图76框协同热态2848ms，对照4494ms；GPU8批46框、全部resized=0、尾批4框成功，但text_diff=4、keep_diff=1、最大置信度差0.60333675，照片小区名称错误。不能用于正式产品。
保留前三项，新增第4项 ORT CPU固定宽度（与GPU固定形状完全相同的右侧零填充及尾批重复），第5项GPU原始形状（不改输入），第6项GPU原宽度仅尾批补齐（完整批不改输入）。先同图同设置依次1、4、3、5、6；2为速度复现。每个模式独立完整识别，不静默回退。第5/6项失败前逐框DIAG_CELL和失败批具体信息保存；第3项与第4项同形状MATCHED_FIXED_COMPARE，同时与第1项REFERENCE_COMPARE；所有文字/keep差异逐框记录。第4项对第1项隔离填充影响；第3项对第4项隔离运行时/模型转换影响；第5项与第6项末批对照隔离batch变化，前12批形状相同。原宽度模式使用v0.1.2已实现的OpenCL持久化缓存机制，但首次新包缓存为空，切换形状/失败重建会影响时延；只比较成功整页与明确热态，不能把失败当完整OCR。GPU缓存、JNI、模型、CPU原版裁切和识别路径不变。宽度字段仍记录原批次宽度，真实张量shape另列。严格数值门槛仍失败。未验证真机；此版不是承诺精度修复，而是定位原因。

# 蓝星 OpenOCR 静态照片 CPU＋GPU 协同实验 v0.1.2

独立包 com.vobich.bluestar.openocrlab.cooperative012，源自已真机执行GPU的v0.1.1。仅改变候选GPU输入形状管理和OpenCL缓存；原CPU、DB、裁切、原始模型权重、任务分配、阅读规则不变。每页GPU输入宽度取本页原预处理最大宽度向上对齐64（320–4096），官方模式固定原批量6，智能模式固定1；最后不足6时重复最后一个真实输入占位，只解码真实前缀，不压缩或截断原像素。GPU新增零填充会改变模型上下文和计算量，不保证文字或置信度一致。原宽度字段保留用于CPU对照，GPU实际形状另记 GPU_PAGE_PLAN / gpu_shape。每页仅一次可能的resize，同形状成功会话继续复用；失败仍关闭会话（安全隔离），记录原因。通过MNN setCacheFile/updateCacheFile在私有cacheDir尝试保存OpenCL调优/编译缓存，日志含缓存大小、更新耗时与返回码；存在缓存不等于命中，不预先运行虚假计时，不保证冷启动加速。记录失败批次真实输出行/步/索引/分数；不放宽有效值检查，不静默CPU回退。

验收：先同图CPU基线，再GPU_ONLY与CPU_GPU各重复三次，比较完整点击到原文、每批resize、真实OpenCL命令、尾批输出、文字/keep/置信度、不同照片同形状复用及跨进程缓存。原严格MNN概率和置信度门槛仍未通过；此为实验包，未验证真机提速，不用于正式替换。历史v0.1.1的说明如下。

# 蓝星 OpenOCR 静态照片 CPU＋GPU 协同实验 v0.1.1

独立包 com.vobich.bluestar.openocrlab.cooperative011，从冻结 v0.4.8 (4b52f658a9ec28f564b4ed77f14a97a60b477a63) 派生；保持原CPU对照、原始RepSVTR/DB/字典、原图导入上限1600万像素、DB1600默认、官方原框/智能主阅读区、CPU线程选择、裁切/顺序/置信度与全文TTS规则。主目标是静态照片完整OCR速度，为未来端云只传文字服务。本包无云端/视频功能，不更改正式版、端云实验或冻结版。

## 三种模式
- 原版 ORT CPU：原v0.4.8路径。为公平同图完整OCR比较，每次清除DB概率缓存，模型会话仍复用；日志区分模型加载、热态、GPU是否驻留。
- CPU＋GPU：原官方每6框批次（智能模式每框单独张量）不改输入形状/归一化。GPU领取第0批、CPU第1批（若存在），此后各自完成即取下一批；单独CPU ORT会话和GPU OpenCL会话，CPU/GPU主机调用时间区间可重叠，只有原始批次编号顺序汇总，全部任务完成才输出。单批无双路并行，不假造。
- GPU单独识别：REC任务全走请求OpenCL的候选路径，DET和预处理仍CPU。每次记录实际MNN命令后端、选中设备、CPU回退；没有任何已追踪OpenCL命令则失败，不把CPU冒充GPU。

## 精简输出与失败保护
原始ONNX资产不改；在副本末尾追加ArgMax/ReduceMax/Cast/Concat，只输出每时间步字符索引和最大分数，理论主机输出由6625个FP32值缩为2个；不改变模型权重、CTC去重/空白/平均与最低置信度规则。固定MNN3.5.0 OpenCL高精度、buffer、FAST调优实验。CI先验证原ORT与投影ORT的索引和分数，再报告转换后MNN CPU的索引/分数偏差；历史完整概率严格门槛失败不能被此报告替代。如候选投影门槛不通过，明确标记，只可实验，不接受正式替换。OpenCL曾闪退，本版放入非导出独立进程，用共享内存提交输入，带超时、错误和断开记录；单个候选失败不输出部分结果或静默回退。独立进程仍可能遇到驱动卡死/系统内存压力等，隔离不是绝对保证。GPU reduction若在CPU执行，不能声称避免了内部大张量回读；追踪记录命令类型及后端。GPU异步提交不代表完成，完整host时间包含同步、复制、IPC和解码。

## 测试方式
先同一原图官方原框/DB1600/CPU4，依次测试CPU、协同、GPU单独，再交替复测，区分初次加载/调优与热态；建议每档3次并观察发热。保留同样的模式、原图、框、裁剪工作量，分别测试价目表和密集公告；智能模式另测。CPU与候选同图同几何比较逐框文字、平均及最低置信度、是否通过原阈值、宽度差；内存参考仅当前进程PSS快照及GPU进程PSS，不是峰值或GPU显存。CPU与GPU的真实重叠以host区间记录，不是GPU硬件利用率。未真机测试不宣称提速。

## 累计诊断
每张原文件SHA256标识、每次独立run ID，START/BEGIN/END，配置、原图尺寸、真实框、批次/后端/时序、完整OCR与点击至原文、原文、失败、TTS回调、同图CPU参考比较、热状态均分组保存到应用私有文本；选新图/复测/退出不清历史。复制全部照片实验日志（300KB安全剪贴板上限），超过则用导出完整TXT；不会静默截断或自动删除记录。清空需确认。原文可能含私人信息，请只主动分享愿意提供的日志；不保存原图或URI、不自动上传。对照参考存在内存（最近8种配置），历史全量日志持久保存。

---

## 冻结v0.4.8历史说明

# 蓝星 OpenOCR v0.4.8 分辨率入口修复
v0.4.7 device failure: DbLocator retained old 960/1280/1600 require list, rejecting new settings before inference. Single shared DbResolution list for UI and locator; default1600; all seven settings validated in JVM test. No OCR model/algorithm changes. Higher resolutions still unverified on device.

# 蓝星 OpenOCR v0.4.7 分辨率上限实验
Fork frozen device-verified v0.4.6 47f324470a40be1f19f63df1bc712fe231c37301. Only add detection long-side 1920, 2048, 2560, 3200 to 960/1280/1600; default remains 1600. Both modes, model weights, preprocessing, geometry, recognition, thresholds, batch size, cache, input 16 MP limit, threads, TTS and logging unchanged. Higher resolutions are unverified; test progressively, stop at diminishing useful text gains, unacceptable latency/memory or instability. PSS is a point-in-time reading, not peak memory. Never count extra detection boxes as accuracy. No new model or arena features.

## Frozen v0.4.6 implementation reference
# 蓝星 OpenOCR v0.4.6 官方还原实验
Fork frozen v0.4.5 42f4acf348bd78c2d15d3d39fa333a7c3530057e. No change to previous releases or formal app.
Two manual modes only: official-oriented raw (default) and verified region (legacy v0.4.3 geometry/reader preserved). DB defaults .30/.60/min3/expanded5/unclip1.5/cap1000 fixed; 960 official baseline, 1280,1600 (default) remain. No auto routing, YOLO, custom vertical segmentation, raw dedup or parameter experiments active. No semantic correction.
Official port uses OpenCV INTER_LINEAR detection resize with Python rounding, round Clipper Java polygon offset (pinned 5ef8c0a467023c495e44e582e9cbd8ca7308a590 Boost license), refit and integer coordinate mapping; official sort, original resolution cubic perspective replicate border, rotate >=1.5, dynamic 48-high BGR normalization, official CTC greedy, batch six where ONNX batch dynamic (fixed batch 1 uses single), official recognition drop score .5 for display/TTS, all candidates in copied diagnostics. Verified region mode retains original Bitmap/continuous rectangle/Reader and FocusPlanner/RowPlanner/ColumnPlanner semantics; cache keyed by mode, original bitmap, generation, resolution and session. Existing CPU4/2/6 and offline TTS remain. No YOLO asset in release.
Reference: Topdu/OpenOCR 1ccfc6ee6161f7133b192e16af3a9265273997ba, configs/det/dbnet/repvit_db.yml, opendet/preprocess/db_resize_for_test.py, opendet/postprocess/db_postprocess.py, tools/infer/utility.py, tools/infer_e2e.py, tools/infer_rec.py, configs/rec/svtrv2/repsvtr_ch.yml, openrec/preprocess/resize.py, openrec/postprocess/ctc_postprocess.py. Android port, not claimed byte-identical to official Python (Java Clipper, Android image decode, float precision, runtime); weights and dictionary reused and verified against prior release, not freshly compared to upstream assets. JVM geometry tests; device parity and batch support/accuracy not yet verified. Compare official mode 1600 to v0.4.5 baseline on same images; legacy region regression with menu. Only evaluate actual recognized text, not confidence alone.

## v0.1.1 定向修复（v0.1.0 首次真机 GPU 全部回退 CPU）
恢复旧GPU竞技场已使用的可选厂商库声明 libOpenCL.so；独立GPU进程在创建MNN会话前只读探测 dlopen、OpenCL符号、平台和GPU设备名称/驱动版本；MNN会话创建后以及每次推理之前拒绝非请求主后端，保留原实际执行命令追踪门槛。探测本身不运行推理，设备能枚举不代表模型能在GPU运行。此次不修改模型、裁切、阅读顺序、置信度、任务分配或累计日志机制。新包独立安装，原版日志仍留在旧包。历史MNN概率及精简置信度严格门槛失败仍有效，Rep OpenCL此前闪退尚待实机验证。
