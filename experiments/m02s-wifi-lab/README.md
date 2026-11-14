# 蓝星 M02S Wi-Fi 精准连接实验 v0.1.4

## 已验证的真机结果
- vivo X200 Pro / Android16：M02S-3584 蓝牙 GATT 显式 LE 1M，93ms 连接成功；AA13 发送原厂 `AB 55 00 03 22 30 52` 后眼镜确实执行了拍照。
- 用户发送 `AB 55 00 03 39 31 6A`，收到了 `AC 55 ... 39 ...`，再约0.45秒经 AA14 收到**0x25 / 13字节载荷**（日志当时已脱敏）。
- 用户截图中 Android P2P 发现的 `DIRECT-92-HP M132f LaserJet` **是惠普打印机，不是眼镜**。因此“发现1个P2P设备”不是 M02S 已连接的证据。

## 官方 HeyCyan APK 源依据
针对用户上传的 `70465ff3819258a6218e7871da0a23f8.apk` 重新静态核查：
- `ModBleResponse.onRequestSyncDataResponse` 在 **0x25** 分支，使用 `WifiCmdBean.recoverByBytes(leHeader.payload)`，把整段 payload 按 **UTF-8** 解成 `ssid` 并调用 `OnWifiDataListener.onWifiData(ssid,...)`。
- `Ldp.connectP2pWifitoReceivePhoto` 根据 `CustomerCmdBean.project` 决定 AP/P2P，随后 `Ldp.connectWifi(ssid,password)` 调对应联网器。官方判断 `project` 包含 `T` 时偏向 AP，否则 P2P，**此用户眼镜的 project 尚未实测**。
- 原厂 `Ldyh$e.lambda$onPeersAvailable$0` 实际通过 `WifiP2pDevice.deviceName.contains(ssid)` 筛选设备后再 `WifiP2pManager.connect`。本实验以此为匹配依据，同时要求完整SSID长度至少8字符、最多32字节，并拒绝多个同名候选，避免误连普通打印机。
- 对 AP 路线，原厂代码中走 `WifiNetworkSpecifier/ConnectivityManager.requestNetwork`；本实验使用精确 SSID 和官方 APK 内置连接凭证（仅内存，不打印日志）。系统可能弹连接授权或拒绝无互联网热点。
- 三条上一版已核实的蓝牙白名单指令和 AA13/AA14 特征不变；本轮**没有添加猜测的厂商命令**。

## 新版使用方式
1. 退出原厂 HeyCyan，启动独立 **v0.1.4**，扫描选 M02S，点击「连接 LE 1M」。
2. 点击「开启蓝牙监听（120秒）」，等待 **AA14 CCCD 订阅 status=0**。
3. 由于设备 `project` 未知，**先测试「P2P 精准连接」一次**（之前使用过0x39/0x31得到0x25）；程序自动发送 `0x39/0x31`，等待0x25，安全解码目标 SSID，在不透露 SSID 的前提下扫描P2P；**只向名称包含完整目标SSID的唯一设备发起系统连接请求**。发现打印机但不匹配会被忽略。
4. 如果经60～70秒确认 P2P 无目标设备，点「取消 Wi-Fi 连接」，先重新开启监听（如果120秒已到），再单独点「AP 热点精准连接」。自动发送 `0x39/0x30`；收到0x25后通过 Android 发起对应 SSID 的网络请求；如弹出系统授权请确认。
5. 只有在手机收到了**目标 P2P groupFormed（手机非组主）+ 地址**或 **目标 AP onAvailable + 本地网关IP**，才允许按「只读文件列表」或「保持连接观察120秒」，从真实路由推导目标本地IP，而不盲目测试历史固定IP。
6. 复制诊断日志发回，只提供：0x25是否有效、目标匹配数量/连接组状态、AP/P2P系统回调、网络可用与 HTTP 响应；日志始终不输出 SSID、密码、照片内容。
7. 连通后 App **不会主动断开**，除非用户点取消/退出、系统或眼镜主动断开。WiFiLock 不能保证眼镜固件不关 Wi-Fi，故持续连接能力仍待实测。

## 保护范围
- 从0x25拿到的真实SSIDs仅驻留内存，长度/状态记录而不记录名字；**没有持久化、云上传、照片读取**。
- 未确认目标网络/IP时禁止 HTTP；只读 `/app/getfilelist`，不会自动遍历图像、下载照片、删除文件或写固件。
- AP/P2P 互不自动兜底切换，无未授权重试；不连接不匹配/多个同名的设备。
- 全新独立包名 `com.vobichngan.bluestar.m02swifitargetlab`，不覆盖 v0.1.0~0.1.3、正式 Blue Star Guide RC25、原厂 HeyCyan 或 Windows OCR。
- Android CI 单测、SDK36构建、APK v2签名、包名/版本和发行产物检查≠真机Wi-Fi已经成功。

## 风险和下一步
本版首次进行真实目标 Wi-Fi 联网测试。若 M02S 的P2P广播的 deviceName 与其通过0x25返回的SSID并非 `contains` 关系，将正确拒绝误连接；此时应进一步抓原厂设备广播和连接回调再完善识别，不应宽泛匹配。
