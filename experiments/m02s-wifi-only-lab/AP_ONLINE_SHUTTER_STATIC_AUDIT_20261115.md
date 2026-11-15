# M02S 原厂APK专项静态核查：Wi-Fi/AP在线时能否拍摄原生高清照片（2026-11-15）

## 目标与范围
- 用户真正要求：**眼镜AP与手机Wi-Fi常连状态下，按实体快门拍摄新的3200×2400高清JPEG并立即通过已有HTTP服务送到手机**，不走卡顿的RTSP视频流，不每次重新开AP/关联，不接受长于5秒的朗读时延作为目标。
- 本次按用户授权进行**静态调查**，**没有修改M02S蓝星任何版本源码、没有新增真机指令、没有刷固件、没有生成APK**。
- 唯一原始依据：用户私有原厂安装包 `com.starglasses.app` `70465ff3819258a6218e7871da0a23f8.apk`，284656626 bytes，SHA256 `92f103b9d473d4cdf7424407ddfc851cac1700d15a06e55dfa0009ccf962ad57`；全部7个DEX已直接解析。**原厂APK未上传公共GitHub**。
- 已检查以前v0.1.7、v0.1.10~v0.1.12、v0.1.24~v0.1.30的 `main/PROJECT_HANDOFF.md`，不重复已有实验。外部HeyCyan资料可能是其它型号/固件，不作为此M02S固件事实。

## 一、原厂App本身明确拦截导入时拍照（直接DEX证据）
1. `classes5.dex Lcom/starglasses/app/ui/activity/main/DeviceFragment$c;->onClick(Landroid/view/View;)V`（拍照按钮）：先检查 `BleCore.isConnected()`；读取`Lgw6.N`、`Lgw6.M`（录音/录像相关）与 `Lgw6.O`；`O`为真时显示`string_isp_importing`并返回，不调用拍照。
2. `classes2.dex Lcom/starglasses/app/ble/manager/ModBleResponse;->onRequestSyncDataResponse(...)` 的字节码位置约704～707：直接读取 `SyncValueBean.isImport:Z` 并写入全局 `Lgw6.O:Z`。所以原厂UI确实以**镜端上报的isImport**作为禁止拍照的条件，而不只是手机Wi-Fi是否绑定。
3. `classes5.dex Lcom/starglasses/app/ui/activity/album/AlbumFragment$k;->lambda$onIspWorkState$0(I)V`根据 `Command$Param.ISP_IMPORTING` 和 `ISP_TAKE_PHOTO` 显示不同ISP状态文字。原厂APK常量直接解析：`ISP_TAKE_PHOTO=0`、`ISP_TAKE_VIDEO=1`、`ISP_TAKE_AUDIO=2`、`ISP_IMPORTING=3`。这证明原厂**明确区分这些工作状态**，但单凭枚举不足以证明固件在硬件上绝对不能并行。

## 二、原厂全部可见拍照入口与指令（直接DEX证据）
- `classes2.dex SendCommandViaBle.takePhoto(I)` 使用同一 `configSendCommand(II)`，BLE commandId **0x22**。原厂静态常量 `TAKE_PHOTO_THUMBNAIL=0x30`、`TAKE_PHOTO_HIGH_QUALITY=0x31`。
- 普通拍照按钮 `classes5.dex DeviceFragment$c.onClick` 经验证调用 `takePhoto(TAKE_PHOTO_THUMBNAIL)`，即`0x22/0x30`，会执行上述`isImport`UI门禁。
- AI拍照/语音拍照 `classes3.dex StarAiService$b.onDevicePhotoRecognition`与`StarAiService$c.onPhotoIdentify`调用 `takePhoto(TAKE_PHOTO_HIGH_QUALITY)`，即`0x22/0x31`；另有`StarAiService$c.onCommand`下发`0x22/0x30`。这只是**已有0x22命令的不同参数**，未找到它能够在AP导入模式持续拍摄的特殊证明。
- 之前真机`v0.1.10`连AP后的重复`0x22/0x30`写GATT status0却**无快门声/无新的0x22、0x42拍照通知**；`v0.1.12`在独立BLE实验模式发送`0x22/0x31`后取得AA15 320×180 JPEG，耗时约3061ms；**这两个结果都不是`0x22/0x31 + 眼镜AP已联通 + isImport=1`成功的证明**。绝不能把320×180 BLE小图冒充3200×2400原生JPEG。
- `classes2.dex SendCommandViaBle.setCameraMode(I)` 独立 commandId **0x50**；APK静态常量`CAPTURE_MODE_STANDARD=0x30`、`CAPTURE_MODE_CLASSIC=0x31`。逐个扫描7个DEX可见方法调用，没有找到这个方法的实际调用者，更**没有找到“保持Wi-Fi AP并把ISP_IMPORTING改为拍照”**的已实现路径，不能把拍摄风格参数误说成解除固件互斥的指令。
- `classes2.dex SupportFunctionBean`包含`supportLive`、`supportPhotoWatermark`等能力位；未发现明确描述“supportPhotoWhileWifiTransfer”的能力位。

## 三、原厂导入与退出路径（直接DEX及已有真机证据）
- `classes5.dex Ldp.connectP2pWifitoReceivePhoto()`触发 `getWifiInfo(GET_WIFI_AP=0x30)` → BLE 0x39/0x30，镜端返回0x25 SSID，Android另行连接Wi-Fi，HTTP`GET /app/getfilelist`与原始JPEG。
- `classes5.dex Ldp.stopP2pWifiConnect()`：实际已下载全部→`0x44/0x30/0x00`；0张→`0x44/0x30/0x01`；部分→`0x44/0x31/N`；随后原厂`disconnectAp`。v0.1.28~v0.1.30实机已证实完成导入后`isImport`转0且允许再次实体快门；这也支持工作状态存在切换。
- 整包`*.bin`只有`DebugProbesKt.bin`(1728 bytes，非镜端固件)，没有可见`firmware/`、`*.fw`、`*.img`、`*.elf`或OTA刷写镜像；**不能仅从手机APK安全修改镜端摄像头ISP固件**。APK并非镜端源码。

## 四、结论／不应夸大
**明确证实：手机原厂UI主动禁止isImport=1时拍照；原厂APP仅暴露0x22两类拍照参数，未发现已实现的“AP在线持续拍高清照片”命令或直通HTTP快门接口。** 

**尚未确定：真实M02S固件是否存在另一个未公开命令可让ISP_IMPORTING状态下拍照，同时维持AP连接；单纯删掉App拍照禁用判断不够，因为此前用户已发现实体物理快门同样无法拍照。** 不能据此断言“只需一行代码解决”，也不能保证3～5秒。

## 五、唯一有增量价值的后续真机实验提案（目前未执行，需独立授权）
与已失败的AP在线`0x22/0x30`不同，**唯一尚未以相同组合验证的已有原厂拍照参数是`0x22/0x31`**：
1. 只在单独隔离实验APP，用户主动触发，设备GATT、AA14/AA15已订阅，眼镜AP真实`onAvailable`+HTTP200列表基线、`isImport=1`均已确认时，**仅发送一次原厂明确存在的`0x22/0x31`**。不发不明模式字节，不循环重试、不使用直播、不开OTA。
2. 被动比对是否有拍照音、`0x22`/0x45/0x42通知、AA15 JPEG、AP是否中断、/app/getfilelist是否出现新的图片；若仅AA15 320×180小图而AP列表不增加，**仍未实现高清Wi-Fi在线拍照**。
3. 成功判据是：**AP没有断开、在`isImport=1`状态下新产生3200×2400原图、原HTTP直接GET成功**。任一不满足即不继续试同类普通拍照命令；按原厂ZERO退出镜端导入状态，保留旧v0.1.29稳定基线。
4. 若单次明确命令在AP在线状态不产生新高清原图，下一步需找**针对M02S确切主板/固件版本的厂家接口或固件源码/可验证恢复包**。不要凭其它HeyCyan型号的`QCOperatorDeviceModePhoto`示例就推断M02S能在传输模式强行拍照；也不在没有镜端恢复手段时刷固件。

## 非同机型公开参考（不代替实机证据）
- HeyCyan官方说明：Wi-Fi文件传输期间**录像**暂禁，没有把`照片拍摄`写成对所有型号均禁止：https://heycyan.net/zh/docs/user-manual/basic-settings
- 另一系列HeyCyan SDK描述Photo/AIPhoto/Transfer不同模式，不对应本APK的具体`0x22/0x39`协议：https://github.com/ebowwa/HeyCyanSmartGlassesSDK/blob/main/WIFI_TRANSFER_ARCHITECTURE.md
