# M02S 原厂蓝牙指令对照实验 v0.1.3

## 已有真机基础
用户的 vivo X200 Pro / Android 16：
- v0.1.1 显式 TRANSPORT_LE + PHY_LE_1M 连接 M02S-3584 成功，GATT 回调 112ms、143ms，返回 1800/AE00/AA12/180F 四个服务。
- v0.1.2 对 AA14 的 CCCD 订阅成功，按实体拍照键，AA14 重复出现 AC55 / 命令 0x22、0x45、0x42，且 0x42 候选计数从 3 到 4。这些现象**证明眼镜状态通知能够接收**，不能单凭事件推断所有 Wi-Fi 命令已经支持。

## 真实用户上传的原厂 HeyCyan APK 静态核查（v1.0.24-c）
在 `/mnt/data/70465ff3819258a6218e7871da0a23f8.apk` 的 classes2.dex 与 classes5.dex 中实际解码如下：

| 原厂方法/字段 | 经 DEX 字节码追踪确认 |
|---|---|
| `SendCommandViaBle.takePhoto(I)` | 调 `configSendCommand(I, 34)`：命令 ID **0x22** |
| `Command.Param.TAKE_PHOTO_THUMBNAIL` | 十进制 48 = **0x30**，原厂 `DeviceFragment` 使用 |
| `SendCommandViaBle.getWifiInfo(I)` | 调 `configSendCommand(I, 57)`：命令 ID **0x39** |
| `Command.Param.GET_WIFI_P2P` | 十进制 49 = **0x31** |
| `Command.Param.GET_WIFI_AP` | 十进制 48 = **0x30** |
| `dp.connectP2pWifitoReceivePhoto()` | 视配置分别调用 `getWifiInfo(GET_WIFI_AP)`、`getWifiInfo(GET_WIFI_P2P)` |
| `BleCore.setupCharacteristics` | 正式指令 GATT 写入服务 AA12／特征 **AA13**；AA14 指令通知，AA15 文件/照片通知 |
| `Datagram.convertLeHeaderToBytes()` | **AB 55** + 2字节大端长度（命令ID+载荷+1校验） + 命令ID + 载荷 + 命令和载荷字节累加&255 |
| `ModBleResponse` | 解析 Wi-Fi 返回中的 `WifiCmdBean.ssid`，向连接层传 SSID 与一个常量密码；具体能否连接取决于真实设备及手机网络状况 |

因此可独立构造的**仅三种白名单数据帧**：

- 原厂模式遥控拍照（可能实际生成一张新照片）：`AB 55 00 03 22 30 52`
- P2P Wi-Fi 信息请求（**不是**保证已经连接 Wi-Fi）：`AB 55 00 03 39 31 6A`
- AP Wi-Fi 信息请求：`AB 55 00 03 39 30 69`

以上格式已与 APK 字节码结构对照，但**尚未真机验证远程指令响应、远程照片生成或 Wi-Fi 真正建立**。不添加其它未查证的私有指令。

## 安全与操作
- 首次必须点「扫描眼镜 BLE」选 M02S，点 **连接 LE 1M**。
- 点「监听眼镜反馈（120秒）」，必须看到 **AA14 CCCD status=0** 后才可以发送指令。
- 第一次测试建议只点 **“遥控拍照：原厂模式”** 一次，看看眼镜是否拍照以及 AA14 是否返回 `0x22 / 0x42 / 0x45`。
- 成功后重新开始监听，点 **“请求 P2P Wi-Fi 信息”** 一次，观察 AA14 和手机网络变化；必要时再用下面旧模块点“扫描 P2P 设备”。不要直接点击保持连接或猜测 IP。
- 若 P2P 失败，可单独再试一次 “请求 AP Wi-Fi 信息”，但**不能把 GATT 写入 status=0 直接当成 AP/P2P 已建立成功**。
- 发送之间最少间隔 5 秒；最多一个 AA13 写入请求在途，4秒无回调只报“结果未知”并**不自动重发**，避免误拍多张。
- 所有接收通知日志自动隐藏未识别载荷和 Wi-Fi SSID/凭证。只对已知且不含敏感字段的拍照候选小包记录少量原始载荷。
- 只有三种写入指令进入白名单；不发出删除文件、恢复出厂、更新固件、Wi-Fi 断开、文件下载完成等指令。没有读取或上传任何照片。
- 新 Android 包名 `com.vobichngan.bluestar.m02scommandlab`，版本 **0.1.3**，与 v0.1.0、v0.1.1、v0.1.2 和正式版蓝星向导可共存，不修改官方 HeyCyan APK。
- 仍然只在前台运行。用户如需稳定 Wi-Fi，需后续独立研究联网与保活，不能在本阶段直接宣称实现。

## 检验
CI：`:app:testDebugUnitTest :app:assembleDebug`、Android 包名与版本、v2 APK 签名、zipalign、权限和 Release 校验。新增单测验证三组原厂帧字节序列、白名单、真实拍照通知的 AC55 帧解析、Wi-Fi 内容脱敏与错误数据拒绝。
