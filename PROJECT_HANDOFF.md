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
