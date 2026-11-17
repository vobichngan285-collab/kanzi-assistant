## 0. 用户确认的最终预览目标（2026-11-17）

- **真正目标**：蓝星向导自己作为 Wi-Fi 摄像头客户端，直接与 C110 建立相机网络连接，读取相机实际发出的 RTSP/HTTP 预览编码流，在蓝星内部解码/取帧，再云端 AI 识别与 TTS。**不启动原厂 App、不使用屏幕共享、不对原厂播放器截图、不把豆包屏幕共享当核心方案**。
- 必须区别**实时视频码流**与**静态高清照片**：直接取码流只去掉屏幕采集、二次编码/缩放等额外损耗，不能自动提高相机已经编码的 720p 流的原始分辨率。用户约720p是主观观察，实机从解码帧测宽高；录像2K或静态16MP不等于Wi-Fi实时流2K。是否存在高分辨率次/主码流、独立照片预览路径、可切码率/尺寸参数，须由实际型号固件/协议证明，不盲发猜测命令。
- **主/备双通道**：主通道 Wi-Fi 实时视频→本地无损取已解码帧→AI OCR→朗读；备用高清快门→等待实际新JPEG写完→同一Wi-Fi会话HTTP下载完整JPEG→云端OCR→朗读。**Wi-Fi 连续保持**是核心验收项。
- 手机相机 Wi-Fi 不提供公网时，需专门验证 Android16 相机网络绑定与蜂窝上云分流，避免 App 全局绑定 Wi-Fi 后阻断云端。
- 真机流程第一关确认**蓝星独立打开/稳定维持视频流并测真实帧分辨率**，而不是再次证明“原厂 App 预览+豆包共享”可行；此用户此前已验证，无须重做旧实验。

# C110 标准版：SJCAM Zone/X 原厂 APK 协议深化与 2026-11-18 实机交接

> 2026-11-17 只读研究，未连接实体 C110、未修改任何 App 代码、未构建、未操作相机、未上传原厂 APK。保存新证据，明天先验设备/固件再决定是否适用 LY 指令。原仓库及 M02S 记录保持不变。

## 1. 机型来源与照片分辨率边界

- **标准 C110**：SJCAM 官方下载页将多数机型归于 **SJCAM Zone**，升级版 C110 Plus 主要使用 **SJCAM X**；故以 Zone 6.7.3.16 的 C110 分支优先、X 1.1.15 对照。不能将 X 的 `565-C110` 适配器路线直接当作当前硬件事实。
- 官方 C110 用户手册照片模式包括 **16M (4608×3456)**（还有 20M 等选项），但“720p”仅被列为**录像规格**之一，**不能证明实际 Wi-Fi 预览流就是 720p**；也不能仅由输出像素断言传感器原生解析力或云端最终使用像素。
- 用户此前**真实验证**：无 microSD 卡，原厂 App 可实时预览；豆包屏幕共享可识别招牌。这是预览事实，尚未验证蓝星独立取流。
- 参考：https://www.sjcam.com/download/ ；https://static.sjcam.com/www/uploads/2024/12/C110-user-manual.pdf

## 2. 原始 APK / DEX 复核信息

- Zone APK：`org.jght.sjcam.zone` 6.7.3.16，SHA256 `a07236df9b97b5a202179ab2bd98cead497bdcae5a6aa32e55113d49b37e1274`。
- X APK：`org.jght.sjcam.x` 1.1.15，SHA256 `a8ebceff0faa8d5b96b4e5f34bf981c932d39bf5070038555f9169edc3cc9b3b`。
- 本轮重新从 Zone `classes5.dex` 读取方法体 `invoke-*`、`const-string`、常量与反向引用，而非仅转述旧报告；不是完整的 Java/Kotlin 还原。X 上一轮方法索引作交叉验证。
- Zone `ConnectCameraFragment.onConnCameraSuccess$lambda$17` 明确含 `C110`、`C110_PRO`、`v1.0.4`、`v1.1.2` 固件条件分流到不同控制页。实机具体分支必须由真实回包确定。

## 3. Zone 预览控制链（LY 分支候选，非全型号通用）

```text
LYCamera.changeCameraModel()
  -> Command(cmd=3001, par=<mode>) -> HTTP 请求
  -> LYCamera$12.onResponse
  -> LYCamera.startLiveVideoStream()
  -> Command(cmd=2015, par="1") -> OkHttp.enqueue
  -> ISCamera.getVideoPlayUrl() （依据设备型号/固件）
  -> NewCameraControlActivity.playVideo() 或 LyCameraControlActivity.playVideo()
  -> VlcLyCameraPlayer.playByUrl(...)
```

- Zone `LYCamera$15.onResponse` 与 `LYCamera$16.onResponse` 也引用 `startLiveVideoStream()`，所以 `2015/1` 不是唯一或必定最初的网络包；上面只是已找到的**某条直接调用链**。
- `LYCamera.<init>` / `handlerConnection` / `getVideoPlayUrl` / `LYCamera$13.onResponse` 中同时有 **`rtsp://192.168.1.254:554/xxx.mp4`**、**`http://192.168.1.254:8192`**。应根据机型固件确定当前播放 URL；8192 返回内容格式未确认，不能武断叫 MJPEG/高清原图。
- `NewCameraControlActivity.onPause()` -> `stopVideo()` -> `VlcLyCameraPlayer.stop()`，`onResume()` -> `refreshVideo()`，`onDestroy()` -> `VlcLyCameraPlayer.release()` + `SCamera.reSet()`。**原厂后台暂停的是播放器**；不能据此断言设备 Wi-Fi 已被断开，也不能让蓝星照抄 UI 页面生命周期。
- `LYCamera$18.onResponse` 含「拍照=====刷新VIdeo」和「拍照=====不刷新VIdeo」分支字面量。快门后视频流是否需要重开依固件/状态判断，应实测中断、恢复时间。
- `SCamera.getCamera` 在 Zone 有 LY、AB、LY_M20、AllWinner 等不同通信后端；**不允许在尚未确认后端前乱发 LY 命令**。

## 4. 高清拍照 / 媒体目录 / 下载

```text
NewCameraControlActivity.takeClick()
  -> SocketHelper.isConnect() / 条件性 startSocket()
  -> SCameraInfo.getSdCardInfo().getStatus()
  -> ISCamera.take(...)
  [LY] LYCamera.take() -> cmd=1001 (照片), cmd=2001 (录像)
  -> 等待拍照完成及新文件真正写入
CameraMediaLibFragment.initData()
  -> ISCamera.getCameraMediaFile()
  -> [LY] LYCameraFileList.getFileList()
  -> LYCameraFileList$1.run() -> 读取 DCIM/PHOTO 等媒体目录
  -> LYCameraFileList.getHtmlBody() -> URLConnection.openConnection()
  -> LYCameraFileList.getFileListNew() -> 解析 HTML a[href] 得到原 JPEG 路径
  -> SDownloadTask.run() -> HttpURLConnection + 输入流保存文件
  -> SDownloadManager.onFinishDownload()
```

- LY 分支代码含 `http://192.168.1.254/DCIM/`、`PHOTO`、`MOVIE`、`EVENT` 等目录；仅为候选，真实网关/路径和固件可能不同。
- `LYCamera.changeCameraModel()` / `inMediaLib()` 通过 **3001** 操作媒体模式，`outMediaLib()` 会恢复其他模式。但**拍照模式能否直接 GET 新 JPEG 而免切回放模式**仍未知。
- `SDownloadTask.run` 没有发现下载结束必调用 `WifiManager.disconnect()`。单个 `HttpURLConnection.disconnect()` 只关闭文件 HTTP 连接；与 Wi-Fi/AP 断开不同。Zone 另有 `ConnectCameraFragment.onDestroy()` -> `WifiHelper.forceDisconnect()` -> Android `WifiManager.disconnect()`，退出连接界面可能主动断网，不得误称“下载完成自动断网”。
- 最小成功标准是**三次不同新照片**，文件标识改变、JPEG 字节完整、实际图片宽高可解码，保持同一 Wi-Fi 会话中拍照、下载、回拍照模式。

## 5. 2026-11-18 唯一测试顺序（读完按顺序走，勿重复旧实验）

1. **A 身份**：匹配实际原厂 App，读相机型号、firmware、SSID/BSSID、网关、协议后端、SD 卡状态。C110 vs C110+、Zone 旧/新控制页和 LY/AllWinner 是首要分流，未确认则**不要发 2015、1001、3001**。
2. **B 实际视频流**：先复现原厂无卡预览，再退出原厂，用独立只读工具绑定相机 Android `Network`；从真实响应确认 RTSP/HTTP URL，需要时在确定是 LY 且有明确测试授权后启动 `2015/1`。记录**真正解码的宽/高、编码格式、首帧时间、连续稳定度**，不要拿手机截屏像素冒充流分辨率。
3. **C 网络路由**：相机 Wi-Fi 通常无互联网；相机访问使用绑定的 Wi-Fi `Network`/Socket，云端 OCR 通过有互联网的网络（通常蜂窝）提交。不要长期将云端 HTTP 绑定到无互联网的相机 Wi-Fi；Android 16 实测双网络分流是否稳定。
4. **D 高清原图**：装测试 TF 卡、原厂拍新 JPEG，确认新增文件、读新路径、完整 HTTP 下载、校验尺寸/字节。无卡预览成功 **不等于**无卡可以输出/下载高清照片。
5. **E 免切 A/B/C**：拍照模式下只读取图 vs 原厂正确进入回放模式取图；失败先排除错误路径/未写盘/网络路由/错误设备后端。
6. **F 三轮持续性**：同一 Wi-Fi 会话重复三轮拍照→新图→下载→恢复拍照，记录每步耗时、预览是否需要刷新，并区分 HTTP 关闭、播放器暂停、系统网络丢失、相机 AP 停止、界面退出造成的 forceDisconnect。
7. **G OCR/TTS**：若 B 先成功即可用预览帧接 OCR；D/F 成功才开启高清模式。高清 JPEG 在上传云端前尽量保留原始像素或裁切细字区域；核实云端接口是否自动缩图，避免“16MP 文件”实际只按小图推理。

## 6. 对比 M02S 与边界

M02S/TK8 的已验证事实是 `isImport=1`、AP 在线拍照命令（包括 0x22/0x31）成功写入但多轮 JPEG 列表不新增；此结论**不能外推为 C110 同样禁拍**。C110 的原厂软件链具备快门/目录/下载/预览/模式切换；**还没有 C110 本轮真机通过数据**。不得宣称“始终不断 Wi-Fi”“必然 720p”“16MP 必达实际图像细节”“无需 SD 卡可得原图”“所有版本通用 2015”。

本轮只追加此技术交接和 `main/PROJECT_HANDOFF.md` 摘要；不修改 App、固件、CI、Release，不另建仓库。


## 补充：C110 前面状态指示灯能否关闭（2026-11-17）

- **官方证据**：SJCAM C110 系列说明书的前部为「Indicator Light」而非补光灯：蓝色待机亮，拍照或录像时按状态闪烁；绿色 Wi-Fi 开启闪烁、连接后常亮；红色为充电和无卡等指示。官方说明书仅说明这些灯的状态和 App 系统设置入口，**未列出可关闭状态灯的选项**。来源： https://static.sjcam.com/www/uploads/2026/02/C110-series-user-manual20260207.pdf （产品简介/指示灯说明）及 https://static.sjcam.com/www/uploads/2024/12/C110-user-manual.pdf （Indicator Light Description、App Settings）。
- **APK 实际检索（只读）**：在用户上传的 SJCAM Zone 6.7.3.16 的 classes4.dex 找到 `LED_ON_OFF`、`LED_ON_OFF_RESP`、`led_on_off` 等字符串；方法体证据为 `Lcom/softwinner/un/tool/util/UNIOCtrlDefs$AW_cdr_net_config;->toString` 输出 `led_on_off=` 字段，**归属于 AllWinner 后端**，不代表 C110 固件实现/允许此命令。SJCAM X 1.1.15 的 classes5.dex `SigMaStarCmd.<clinit>` 可见 `Stealth` 文本，也在**其他后端**，不能拿来直接控制 C110。当前没有标准 C110 的 LY 分支关闭指示灯可验证命令。
- **技术边界**：蓝星向导替代原厂 App 独立获取 Wi-Fi 视频流不等于状态灯自动关闭。关闭 Wi-Fi 也会熄灭其连接指示，但会破坏用户最核心的长连取图需求，不能作为解决方案。
- **次日动作**：先确认实际 C110 机型/固件/协议分支，再查看匹配原厂 App 的设备设置里是否有 `LED`/`指示灯`/`工作灯` 开关；若不存在，不盲发 AllWinner、SigmaStar 的写命令、不刷固件、不拆机。可另问厂家是否有官方可支持的状态灯开关，但不能承诺软件可控。
