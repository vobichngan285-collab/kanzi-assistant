# 蓝星 M02S｜E09 开源协议直传实验 v0.1.11（Android 16，独立安装包）

## 开源来源与真实适配边界
直接参考并移植 [sctg-development/ai-smart-glasses-e09-reverse](https://github.com/sctg-development/ai-smart-glasses-e09-reverse)（MIT License，原作者 Ronan Le Meillat / SCTG Development，见 `E09_SOURCE_LICENSE.md`），尤其是 `Sources/e09_reverse/BleProbe.swift` 的 AA15 照片分片协议，`Constants.swift` 的指令常量，`AdditionalValidations.swift` 的 `probeStartLivePreview`。Swift/macOS 的 CoreBluetooth、CoreWLAN、ffprobe 不适用于 vivo Android16，因此仅将协议字段与业务逻辑重写成 Android Java/BluetoothGatt/ConnectivityManager/MediaPlayer，不谎称二进制原样运行 Swift 代码。

**已确认相似：** 用户 M02S-3584 真机支持 AA12/AA13/AA14/AA15 特征、官方 `0x22/0x30` 拍照、`0x39/0x30` 获取 AP 信息、`/app/getfilelist` 和原图 GET。**尚未确认：** M02S 是否接受 E09 的 `0x22/0x31` 蓝牙照片直传、`0x67/0x30` RTSP 模式以及 `0x44/0x30/0x01` 的恢复状态效果。即使成功返回 JPEG，E09 样机的蓝牙图可能只有约320×180，不能替代清晰的3200×2400原图；RTSP 的实际分辨率、帧率、稳定性也必须实测。不开启设备 OTA / 删除媒体。

## 本实验包三项独立功能（同一个新包，避免一堆实验版）
1. **E09 蓝牙直传照片**：蓝牙 LE1M、标准 AA14/AA15 CCCD订阅完成后，向 AA13 发一次 `0x22/0x31`，只监听 AA15 `52 58` / `0x97` PHOTO_START / `0x98` PHOTO_TRANS / `0x99` PHOTO_END，按每包4字节大端偏移组装，容忍重复包和乱序，但不能容忍实际缺块，安全上限2MB，等待最长15秒；确认 JPEG SOI/EOI 并成功 Android Bitmap 解码后才宣称接到照片。照片仅内存预览，不保存、不上传。若无 AA15 报文，只报不兼容/暂不支持，绝不用旧图填充。
2. **E09 RTSP 直播实验**：独立点击发送一次 `APP_LIVE 0x67/0x30`，等待 AA14 `0x25` Wi-Fi SSID，沿用已在 M02S 实测成功的 Android16 Wi-Fi 精准 Network 连接。**不发送原来的 `OPEN_WIFI 0x39/0x30`**；联网成功后在 *专属眼镜 AP Network* 用真实已验证私有网关地址访问 `rtsp://<gateway>/h264`（来源开源项目 `rtsp://192.168.169.1/h264`），尝试 RTSP OPTIONS 和 DESCRIBE，记录 200/SDP video 结果。用户再点 **播放RTSP画面**，使用 Android 系统 MediaPlayer + SurfaceView 真正测试是否能解码持续视频。播放器暂时把**本实验App进程**绑定到眼镜Network，播放停止即解绑，不影响其他App系统默认网络。探测成功≠能播放，播放器启动≠持续得到帧，必须观察显示效果。
3. **安全结束传输/恢复快门试验**：手动发送 MIT 项目来源的不删除媒体指令 `0x44/0x30/0x01`，明确**不提供**删除媒体的 `0x44/0x30/0x00`，也不提供 OTA。实验前有GATT回执、5秒命令间隔保护；程序本身只能确认写入，不擅自断言M02S必然恢复拍照。若自动安全结束因间隔尚短未送出，须用户手动再点“单独发送不删除结束指令”。不要在蓝牙拍照/下载任务尚未结束时发送。

## 真机验证步骤（分别实验，避免混合结果）
- 关闭官方 HeyCyan；保持 v0.1.10 原成功结果作为对照，不需要再测试 P2P 或官方HTTP列表。新 APK 独立包 `com.vobichngan.bluestar.m02se09lab` / versionCode12 / v0.1.11，旧版全部保留。
- 连接 M02S BLE LE1M → “开启蓝牙监听” → 等 AA14 订阅完成和 AA15 CCCD status=0。**首先只点一次 E09 蓝牙直传照片**，看是否出现 `AA15 PHOTO_START/TRANS/END` 和真实 JPEG 预览；没有就停止，不反复拍。
- 如果 BLE 直传能返回图、分辨率足够，优先考虑它作为快速OCR方案。否则停止旧任务、确保眼镜可以恢复普通工作状态，再单独点击 **E09 RTSP直播建连**，看 `0x25` / AP onAvailable / RTSP DESCRIBE，确认后点“播放RTSP画面”验证视频帧。
- 为排除快门不能连续工作的缺口，**仅在传输停止后**尝试“单独发送不删除结束指令”；观察是否恢复普通拍照功能，记录AA14状态。仍需确认M02S是否支持该命令，不要当成已验证结果。
- 日志只记录命令号、结果阶段、图像尺寸/字节、耗时和失败类型；不记录 Wi-Fi SSID/BSSID、WPA2密钥、相册文件名、原图数据。若因Android RTSP媒体栈不支持而失败，区别于眼镜无RTSP服务。

## 版本与回滚
此版仅在 `exp/m02s-e09-ble-photo-rtsp-v0111-20261114` 隔离开发，旧 v0.1.10 项目保留作为真机验证的成功对照。未触及正式 Blue Star Guide RC25、Windows 游戏OCR、原厂安装包、镜片固件。能构建/发布不等于E09协议与M02S兼容，真机验证后再作决定。
