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
