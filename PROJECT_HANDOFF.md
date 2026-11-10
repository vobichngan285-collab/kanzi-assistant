# 蓝星向导｜当前工作交接（2026-11-09）

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
