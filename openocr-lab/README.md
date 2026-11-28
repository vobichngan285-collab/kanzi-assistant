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
