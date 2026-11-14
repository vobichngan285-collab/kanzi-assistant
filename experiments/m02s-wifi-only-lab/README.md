# M02S v0.1.21 — 被动导入状态 + 设备只读项目号 + Wi-Fi状态诊断

## 这版只做诊断，不再猜测或增加清理流程
真实v0.1.18/v0.1.20曾成功通过眼镜实体快门0x45`0→1→0`→250ms→已验证BLE`0x39/0x30`→AA14`0x25`返回SSID→Android精确SSID WLAN→OEM HTTP200文件列表→3200×2400原始JPEG下载到手机内存，最好一次耗时4584ms。此后经常第二张Android`onUnavailable`（v0.1.20第二次耗时3005ms）。该次官方系统Wi-Fi设置手动连接DH-Twl-725B38也提示拒绝请求；蓝牙返回SSID与系统缓存中看到该热点仍不能单独证明它当时可正常关联。v0.1.20的`0x44/0x30/0x01`实验只证明GATT写入成功，**第二张依然失败**；不应继续盲改Android超时或重复发0x44。

## 针对同协议开源源码的确切依据
- `sctg-development/ai-smart-glasses-e09-reverse/Sources/e09_reverse/AdditionalValidations.swift` 的`decodeActionSyncFlags`：收到`ACTION_SYNC/0x45`时payload前9字节为状态位，**可选第10个字节`isImport`**。不存在第10字节时应报告“未提供”，**绝不当作退出导入模式**。
- `FerSaiyan/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/devices/eyevue/EyevueProtocol.kt`：同样的`AA12/AA13/AA14`蓝牙服务，`GET_CUSTOMER(0x64)`为只读项目查询，回复0x64首4字节项目号、后4字节客户号；本实验**只提取长度≤4且ASCII安全的项目号，完全不写客户号**。
- `EyevueMediaSync.kt` 的`EyevueMediaProfile.fromProject`提供参考识别：包含`SK`通常选择AP 192.168.1.254；包含`T`通常选择AP 192.168.169.1；其它分支默认P2P，但**这不是当前M02S设备支持Wi-Fi Direct的证据**。应用只显示提示，**绝不因项目码自动切模式或发送未知P2P指令**。
- `EyevueWifiTransport.kt`确认AP也使用Android`WifiNetworkSpecifier`，不能仅凭其间歇性失败就断言API选错。非本机固件的CyanBridge P2P控制指令绝不套用。

## v0.1.21实际功能
1. **只监听0x45**：现有快门开始/结束检测不变。每帧严格核验AC55前缀、长度、命令与校验和，再解析可选payload[9]。首次收到有值的0或1及后续变化才记录`isImport`，如果第10字节缺失则单次提示“未提供”；无数据时未知，不会伪称“导入已退出”。本机是否发10字节需真机检测。
2. **设备项目只读查询（手动）**：BLE待机后可点“只读查询设备项目号”，发唯一精确白名单`AB 55 00 02 64 64`，不打开Wi-Fi、不拍照、不中断原厂图片文件、不清除。仅在没拍照、没下载、没有其它GATT项目查询时发，最多等待3秒，不自动重发。回复只显示项目4字节，不打印客户信息；M02S未验证是否回应这个命令，不返回就记录未知。
3. **Wi-Fi只读诊断快照**：在第一次/第二次开热点前、收到BLE准确SSID、开始系统Wi-Fi申请、onAvailable/onUnavailable、网关通过、释放手机Network前/后分别记录**当前Android Wi-Fi Network数量及已有扫描缓存中目标SSID的最小年龄**。不记录SSID/MAC/IP/密码/BSSID；不启动扫描；扫描缓存可能是旧结果，**不是证明眼镜AP真实在线/可关联**。配合已存在开AP→SSID、WLAN请求→onAvailable/onUnavailable的毫秒计时及本次第N次取图编号，能对比第一次和第二次状态变化。
4. **从成功v0.1.19分支派生以完全避免新清理命令**：没有v0.1.20的可选0x44实验。唯一能发送的BLE写入命令是已有实验成功的`0x39/0x30`开AP和**用户主动点击的只读`0x64`**；从代码里禁止`0x44`所有参数（含NOT_CLEAR/CLEAR）、`0x22`遥控拍照、OTA、P2P切换等。
5. **保留所有已经成功的机制**：BLE唯一目标扫描、AA14订阅、严格等待0x45`1→0`后250ms才开AP、5秒SSID回执超时、精确SSID WifiNetworkSpecifier只申请一次、私有网关+OEM HTTP200/JSON校验、约350ms间隔的两个稳定目录快照、最新唯一候选的安全GET、高清JPEG原图到RAM。不重复Wi-Fi，不扫描12秒，不随意改密码/IP，不写相册、不上传云端、不删除眼镜文件、不接OCR/TTS。照片最新候选不能保证严格由当次快门生成。

## 最短真机验证
1. 打开独立v0.1.21，点击准备蓝牙待机；**可选**点击一次“只读查询设备项目号”，看有没有0x64校验通过的回应。如果没有，这并不妨碍拍照。
2. 眼镜实体快门拍一张，等下载成功，**再**按快门拍第二张（不要重复操作/不手动重试）。复制**完整匿名日志**，重点是第一次/第二次`isImport`变化、开AP→SSID和Android请求→成功或失败，以及“Wi-Fi只读快照”中被动缓存年龄。如果截图顶部出现VPN或手机仍连接家用路由器，日志中的网络数量只作辅助。
3. 若第二次失败，停止尝试即可。**isImport缺失、扫描缓存有SSID、onUnavailable都不能单独证明眼镜AP开/关状态**；此实验只聚焦判别，不宣称修复。

## 版本与仓库治理
从原仓`vobichngan285-collab/kanzi-assistant`已发布v0.1.19 HEAD`d6befdb9343d4e55e8f34d85904b1769c94f8805`分出`exp/m02s-passive-state-diagnostics-v0121-20261115`。独立APK包名`com.vobichngan.bluestar.m02sstatereadonlylab`、versionCode22/versionName0.1.21。所有v0.1.12～0.1.20独立实验与正式蓝星向导、Windows OCR、HeyCyan都不改；从不修改用户眼镜固件。开源MIT归属保留在`E09_SOURCE_LICENSE.md`。
