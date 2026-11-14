# M02S v0.1.13｜Vivo 直播AP首次连接采用公开原厂匹配策略（2026-11-15）

**不再盲目试时序：经过实际源代码对比后，只改变一个连接策略。**

## 实测依据

- 用户M02S-3584在v0.1.12-livecheck真机成功查询原厂`0x95/0x00`，获得4字节能力掩码`0x000012AB`，其中bit0=1，**固件报告支持直播**（不是证实RTSP已播放）。
- 此前v0.1.12-export下 `APP_LIVE 0x67/0x30` 收到AA14 `0x67`和`0x25` SSID；Android已看到直播AP新鲜扫描广播，但用`setSsid(ssid)`的`WifiNetworkSpecifier`在Android16上得到关联阶段失败code=1，完全没有进入RTSP OPTIONS/DESCRIBE。
- 公开原厂EyeVue APK反编译 `sctg-development/ai-smart-glasses-e09:reverse/com.eyevue.glassapp/sources/com/eyevue/glassapp/helper/WifiApConnector.java`，具体行340-380的`connectWithSpecifier`与行853-900的`connectApwifi`：对于非Harmony/ColorOS/Xiaomi设备（例如Vivo），初次使用`setSsidPattern(new PatternMatcher(ssid, 0))` **literal pattern** + 扫描得到的目标`BSSID`，指定WPA2。该公开代码不能保证与用户上传的另一原厂APK完全一致；但**我们的旧程序确实仅用了`setSsid(ssid)`、不pin BSSID**。它是可核对的、未经测试的具体实现差异。
- 用户已在此前v0.1.23试过「SSID-only首次失败后有条件 BSSID 重试」，这个版本不是重复该方案：**首次、唯一一次直播AP请求即使用公开OEM的LiteralPattern+BSSID组合**，没有后置重试、没有新增延迟。静态读取与实机是否奏效须分开表述。

## 唯一代码修改

1. 在此前独立v0.1.12-livecheck分支基础上新建独立`exp/m02s-e09-vivo-oem-ap-matcher-20261115`。第⑥区仍按原`0x67/0x30`启动直播；只在`e09LiveRequested && Build.BRAND == "vivo"`时，要求本轮BLE触发后的Android扫描中出现**唯一、新鲜且1.8秒稳定**的真实目标BSSID，并将其传入`WifiNetworkSpecifier.Builder.setBssid(...)`，同时`setSsidPattern(PATTERN_LITERAL)`和原厂WPA2口令。保留一次Android`requestNetwork`，不自动重发BLE、扫描或替换连接。
2. 普通AP相册连接和其他手机品牌继续旧`setSsid()`单次方式，无行为变更。Android已经连上目标AP的原网络复用路径仍然只读验证私有网关。若BSSID不新鲜/不唯一/校验失效，**不连接**而报原因，避免误连旁边热点。连接成功后仍是同一`apNetwork`、路由网关检测、RTSP OPTIONS/DESCRIBE和`MediaPlayer`播放链；不会提前声明RTSP有帧。
3. UI第②普通AP按钮明确标记「非直播」，第⑥ RTSP按钮和0x95只读查询及蓝牙JPEG导出都保留。没有照片删除、眼镜媒体写入、OTA、云上传。
4. 新增纯Java策略类`M02sE09OemMatchPolicy`与JUnit测试，覆盖vivo直播唯一BSSID、普通相册不变、非vivo不变、缓存/歧义/不足1.8s/BSSID不一致均拦截。旧E09 JPEG组包及RTSP探测文件**保持原blob**。

## 一次真机测试

先退出原厂HeyCyan/其他控制APP→安装独立v0.1.13→扫描BLE / LE1M→开启AA14和AA15订阅→**不点第②普通AP**，直接点击第⑥「E09 RTSP直播建连」一次。观察日志是否出现`E09直播原厂式AP：Vivo首次请求采用SSID字面量PatternMatcher + 本轮唯一新鲜BSSID`，其后`onAvailable`还是关联失败`code=1`。如果onAvailable且从网关看到`E09 RTSP探测完成`，再点击「播放RTSP画面」。若再遇关联失败，则明确这个公开原厂匹配差异**并未解决**Android16关联；停止本路线继续试超时或无限重试，下一步先对照Android系统连接与真实硬件AP安全协商。不要连续高频尝试。

本包和旧v0.1.12 JPEG、0.1.12-export、0.1.12-livecheck、正式蓝星向导、Windows OCR、眼镜原厂APP互不覆盖。

---

# M02S E09 实时直播能力只读查询 v0.1.12-livecheck（2026-11-15）

**为什么做这个检查**：用户2026-11-15真机发送 `0x67/0x30` 后收到AA14 `0x67`、`0x25`有效SSID，手机看见眼镜新AP广播，但Android 16 `WifiNetworkSpecifier` 返回本地失败原因=1（**association关联阶段失败**）。没有真实AP Network/私有网关，也没有尝试RTSP播放器/视频解码，**不能把关联失败当成RTSP无画面或不支持的证明**。

**源码依据**：反编译参考原厂EyeVue `SendCommandViaBle.getSupportFunction()` 调用 `configSendCommand(149,0)`，与 MIT 开源 E09 `BleProbe.validateSupportFunction()` 完全对应：AA13 `AB 55 00 03 95 00 95`，AA14 `0x95` 应返回 **4字节大端能力掩码**，`bit0 supportLive`。来源 https://github.com/sctg-development/ai-smart-glasses-e09-reverse 和 https://github.com/sctg-development/ai-smart-glasses-e09/blob/main/REVERSE_ENGINEERING_EYEVUE_BLE_PROTOCOL.md 。与用户设备相同的原厂 APK `com.starglasses.app` 存在 `SupportFunctionBean`、`GET_SUPPORT_FUNCTION`、`StarLiveActivity`、`StarTLiveActivity` 与 RTSP 地址，但**未从设备读到其自身掩码，不可预判支持直播**。用户先前只读项目号为 `TK8`，符合含 T 的 AP 选路规则，但**AP选路不等于直播能力为真**。

**唯一新增功能**：⑥ E09 区新增「检测眼镜直播功能支持位（只读 BLE 0x95）」按钮。仅当BLE GATT AA14通知就绪、当前没有拍照/RTSP/Wi-Fi任务时，用户手动点击发送唯一白名单 `0x95/0x00`，等待最多5秒；不自动重试。严格验AC55帧结构、校验和及**恰好4字节**功能数据，正确按大端解析，仅显示能力掩码/bit0 true或false；未收到、长度不对、校验错一律显示**未知**，不能冒充不支持。退出监听时取消待查。其余已实测成功的BLE 320×180 JPEG及相册导出、原 RTSP APP_LIVE/P2P/AP/MediaPlayer 原代码均保留。这个新按钮**不会连接Wi-Fi、拍照、删眼镜文件、改固件、调用OTA或上传内容**。未授权任何 `0x95` 参数变体。

**真机一次测试**：关闭其它眼镜控制App→安装此独立APK→扫描M02S/连接LE1M→开启蓝牙监听直到AA14订阅 status=0→**保持Wi-Fi会话为空，不要先点AP热点/RTSP直播**→只点「检测眼镜直播功能支持位（只读 BLE 0x95）」一次→回传日志中 `E09直播支持查询：设备能力掩码...bit0 supportLive...` 或「5秒无有效回复」。若bit0=false，不要盲目改扫码/AP超时；若true，说明固件报告能力存在，但仍需独立解决 Android Wi-Fi 关联失败，不能直接保证直播可用。

**版本纪律**：基于已经通过真机JPEG解码的 `v0.1.12-export` 隔离分支，新增包名 `com.vobichngan.bluestar.m02se09livechecklab` 和versionName `0.1.12-livecheck`；不覆盖旧APK、正式蓝星向导RC25、Windows OCR、原厂HeyCyan或眼镜文件。保留 `E09_SOURCE_LICENSE.md` 中MIT许可。

---

# M02S E09 蓝牙JPEG手动导出实验（基于v0.1.12已真机成功版）

- 基线：`exp/m02s-e09-jpeg-tail-fix-v0112-20261114`，用户2026-11-14 21:03实测AA15直传并解码成功320×180 JPEG、3061ms。完整AA15分片、0x22/0x31、RTSP 0x67/0x30、原厂HTTP等全部不改变。
- 唯一新增：在「⑥ E09开源协议独立测试」增加「保存刚收到的蓝牙JPEG到手机相册（原始画质）」按钮。只有用户点击，才将**最新成功Bitmap解码的E09 JPEG原字节**写入 Android MediaStore `Pictures/BlueStarE09`。不重新编码、缩放或锐化，能用手机图库放大查看清晰度。若原BLE传输带JPEG EOI后的固件非图像填充，原v0.1.12解码器可能先移除填充；不会改变真实JPEG画面像素。
- 导出只针对AA15蓝牙图片；原厂AP下载图片不能误保存为E09小图。未成功获取E09解码图时拒绝保存；新的E09拍照前清空旧候选，清除预览和其他取图覆盖时清空导出候选。用户可在手机图库删除自己导出的图片，不触碰眼镜存储。
- Android 10+ scoped MediaStore 自行写入本App新图片，**不需要READ/WRITE媒体权限**；在存储时使用IS_PENDING避免图库见到半张，失败删除本次半成品。只有用户点击才写入手机相册，不上传云端、不删除眼镜照片、不开启OTA。
- 用户真机：安装此独立APK，连接BLE LE1M→打开AA14/AA15通知到订阅status0→点「E09 蓝牙直传照片」→确认软件内显示小图→点「保存刚收到的蓝牙JPEG到手机相册」→手机相册 Pictures/BlueStarE09 放大对比菜单文字，核实真实320×180样张清晰度。RTSP独立入口保留但不代表已验证能播放。
- 新包单独安装，原v0.1.12、官方App、正式蓝星向导RC25、Windows OCR都不覆盖。参考开源作者Ronan Le Meillat/SCTG Development及MIT许可仍保留于 E09_SOURCE_LICENSE.md。

---

# 蓝星 M02S｜E09 AA15 JPEG 尾部修复 v0.1.12（Android 16，独立安装包）

## v0.1.12 本轮修复（基于已经实测成功的AA15链路）

**真实用户日志 v0.1.11，2026-11-14 19:57**：`AA13 0x22/0x31` 写入成功，AA14 `0x22` 回执正常，AA15 `PHOTO_START 0x97` 声明 **6684字节**，19:57:30 Android旧接收器到达 `PHOTO_END` 后返回「JPEG文件头尾校验失败」。既已收到照片通道分片且连续性检查通过，故不再重复验证是否支持开源指令。旧错误将SOI和EOI混为一谈，无法据此断定眼镜JPEG已损坏。MIT 上游 `BleProbe.swift` 不要求尾部严格为 `FF D9`，其E09硬件有声明长度与实际接收相差500字节的观测，但**绝不能假定M02S也固定多500**。

只修复 **AA15 JPEG尾部组装/解码**，其余照片、AP取图、E09 RTSP和安全退出入口保持原逻辑：

- 仍采用开源源代码的AA15绝对偏移、容忍重复/乱序，拒绝实际缺块、非法帧或2MB越界；优化缓冲分配。
- `PHOTO_END` 记录 **宣布长度、收到的连续字节长度、两者差值、SOI是否存在、EOI末端位置、尾随字节长度和分片数**，均为不泄漏内容的纯数值日志。
- 对完整连续、SOI正确的JPEG，**先按最后一个实际EOI精确截取候选**尝试真正Android `BitmapFactory` 解码；必要时对完整原始传输长度尝试解码。没有EOI时，若能解码则明确标记为「可能不完整」，不能冒充无损完整照片。缺包、缺SOI或解码失败则停止，不保存/上传/删除。
- 新增8个JUnit单测，覆盖：原版完整JPEG、尾随500字节、其它尾随长度、缺失EOI但保留解码候选、缺包、缺SOI、越界和无效AA15帧。
- **一次真机测试**：关闭HeyCyan，启动 v0.1.12 → 连接 BLE LE1M → 开启监听直到 **AA14和AA15订阅status=0** → 不触发⑤旧版拍照取图，**只点第⑥「E09 蓝牙直传照片」一次**。观察 `E09 AA15 PHOTO_END` 的纯数值诊断与 `JPEG直传并解码成功` 或 `Android BitmapFactory返回null`，如果出现预览，人工核对分辨率和文字能否辨认。**不再重复扫描P2P/官方AP相册实验。**

## 开源来源与真实适配边界
直接参考并移植 [sctg-development/ai-smart-glasses-e09-reverse](https://github.com/sctg-development/ai-smart-glasses-e09-reverse)（MIT License，原作者 Ronan Le Meillat / SCTG Development，见 `E09_SOURCE_LICENSE.md`），尤其是 `Sources/e09_reverse/BleProbe.swift` 的 AA15 照片分片协议，`Constants.swift` 的指令常量，`AdditionalValidations.swift` 的 `probeStartLivePreview`。Swift/macOS 的 CoreBluetooth、CoreWLAN、ffprobe 不适用于 vivo Android16，因此仅将协议字段与业务逻辑重写成 Android Java/BluetoothGatt/ConnectivityManager/MediaPlayer，不谎称二进制原样运行 Swift 代码。

**已确认相似：** 用户 M02S-3584 真机支持 AA12/AA13/AA14/AA15 特征、官方 `0x22/0x30` 拍照、`0x39/0x30` 获取 AP 信息、`/app/getfilelist` 和原图 GET。**尚未确认：** M02S 是否接受 E09 的 `0x22/0x31` 蓝牙照片直传、`0x67/0x30` RTSP 模式以及 `0x44/0x30/0x01` 的恢复状态效果。即使成功返回 JPEG，E09 样机的蓝牙图可能只有约320×180，不能替代清晰的3200×2400原图；RTSP 的实际分辨率、帧率、稳定性也必须实测。不开启设备 OTA / 删除媒体。

## 本实验包三项独立功能（同一个新包，避免一堆实验版）
1. **E09 蓝牙直传照片**：蓝牙 LE1M、标准 AA14/AA15 CCCD订阅完成后，向 AA13 发一次 `0x22/0x31`，只监听 AA15 `52 58` / `0x97` PHOTO_START / `0x98` PHOTO_TRANS / `0x99` PHOTO_END，按每包4字节大端偏移组装，容忍重复包和乱序，但不能容忍实际缺块，安全上限2MB，等待最长15秒；检查 JPEG SOI、分片连续性和真实 Android Bitmap 解码；EOI 不在传输字节末尾时精确计算附加尾部，按最后一个 EOI 截取候选，不硬编码500字节；没有EOI但可解码时只称可能不完整的预览。照片仅内存预览，不保存、不上传。若无 AA15 报文，只报不兼容/暂不支持，绝不用旧图填充。
2. **E09 RTSP 直播实验**：独立点击发送一次 `APP_LIVE 0x67/0x30`，等待 AA14 `0x25` Wi-Fi SSID，沿用已在 M02S 实测成功的 Android16 Wi-Fi 精准 Network 连接。**不发送原来的 `OPEN_WIFI 0x39/0x30`**；联网成功后在 *专属眼镜 AP Network* 用真实已验证私有网关地址访问 `rtsp://<gateway>/h264`（来源开源项目 `rtsp://192.168.169.1/h264`），尝试 RTSP OPTIONS 和 DESCRIBE，记录 200/SDP video 结果。用户再点 **播放RTSP画面**，使用 Android 系统 MediaPlayer + SurfaceView 真正测试是否能解码持续视频。播放器暂时把**本实验App进程**绑定到眼镜Network，播放停止即解绑，不影响其他App系统默认网络。探测成功≠能播放，播放器启动≠持续得到帧，必须观察显示效果。
3. **安全结束传输/恢复快门试验**：手动发送 MIT 项目来源的不删除媒体指令 `0x44/0x30/0x01`，明确**不提供**删除媒体的 `0x44/0x30/0x00`，也不提供 OTA。实验前有GATT回执、5秒命令间隔保护；程序本身只能确认写入，不擅自断言M02S必然恢复拍照。若自动安全结束因间隔尚短未送出，须用户手动再点“单独发送不删除结束指令”。不要在蓝牙拍照/下载任务尚未结束时发送。

## 真机验证步骤（分别实验，避免混合结果）
- 关闭官方 HeyCyan；保持 v0.1.10 原成功结果作为对照，不需要再测试 P2P 或官方HTTP列表。新 APK 独立包 `com.vobichngan.bluestar.m02se09jpegfixlab` / versionCode13 / v0.1.12，旧版全部保留。
- 连接 M02S BLE LE1M → “开启蓝牙监听” → 等 AA14 订阅完成和 AA15 CCCD status=0。**首先只点一次 E09 蓝牙直传照片**，看是否出现 `AA15 PHOTO_START/TRANS/END` 和真实 JPEG 预览；没有就停止，不反复拍。
- 如果 BLE 直传能返回图、分辨率足够，优先考虑它作为快速OCR方案。否则停止旧任务、确保眼镜可以恢复普通工作状态，再单独点击 **E09 RTSP直播建连**，看 `0x25` / AP onAvailable / RTSP DESCRIBE，确认后点“播放RTSP画面”验证视频帧。
- 为排除快门不能连续工作的缺口，**仅在传输停止后**尝试“单独发送不删除结束指令”；观察是否恢复普通拍照功能，记录AA14状态。仍需确认M02S是否支持该命令，不要当成已验证结果。
- 日志只记录命令号、结果阶段、图像尺寸/字节、耗时和失败类型；不记录 Wi-Fi SSID/BSSID、WPA2密钥、相册文件名、原图数据。若因Android RTSP媒体栈不支持而失败，区别于眼镜无RTSP服务。

## 版本与回滚
此版仅在 `exp/m02s-e09-jpeg-tail-fix-v0112-20261114` 隔离开发，旧 v0.1.11/v0.1.10 项目均保留作为真机验证的成功对照。未触及正式 Blue Star Guide RC25、Windows 游戏OCR、原厂安装包、镜片固件。能构建/发布不等于E09协议与M02S兼容，真机验证后再作决定。
