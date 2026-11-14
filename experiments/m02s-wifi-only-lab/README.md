# M02S v0.1.20 — 明确结束传输模式的“不删除”对照实验（默认关闭）

## 目标：解决前两张成功、后续 Android Wi-Fi 无法重新连接
v0.1.18 用户已真机成功取过3200×2400原图，取图触发→解码6188ms；但v0.1.18有两张成功后第三次Android连接失败现象，v0.1.19在本次连续两轮都收到眼镜真实SSID（开启AP→0x25分别511ms和505ms）却收到`onUnavailable`（6761ms和4182ms），均无onAvailable/原厂HTTP。**蓝牙回SSID并不等于眼镜AP真实广播或Android一定能关联，onUnavailable也不证明被系统限流。** 既有代码只是`unregisterNetworkCallback`释放**手机**网络，会继续保留BLE待机；**没有证据证明眼镜固件同步退出了传输模式**，可能影响下次AP行为。

## 已验证的开源来源（不要把不同厂商BLE协议混用）
- MIT公开 [sctg-development/ai-smart-glasses-e09-reverse](https://github.com/sctg-development/ai-smart-glasses-e09-reverse) `Sources/e09_reverse/Constants.swift`和`WifiMediaManager.swift`明确指出原厂反编译`iec.java:stopP2pWifiConnect()`有 `CMD_FILE_DOWNLOAD_FINISH = 0x44`、`PARAM_WIFI_FILE_DOWNLOAD_FINISH = 0x30`、`PARAM_WIFI_FILE_DOWNLOAD_NOT_CLEAR = 0x01`。参数`0x00`会清除/删除眼镜照片；**绝对不能发0x44/0x30/0x00**。E09公开代码显示`[0x44,0x30,0x01]`为不删除结束通知，发送到AA13的完整帧`AB 55 00 04 44 30 01 75`，校验值`0x75=0x44+0x30+0x01`。
- 这个`0x44 0x30 0x01`在**本用户M02S尚未实机验证**，也不保证GATT ACK后眼镜真的关闭AP。甚至“不删除”的含义也来源于E09而不是这副M02S，因此默认关闭，让用户明确选择才使用，防止误操作。
- 开源 [FerSaiyan/Alternative-HeyCyan-App-and-SDK](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK) `android/AGENTS.md`和`android/CyanBridge`中有`WifiP2pManager`、P2P WPS连接、BLE`glassesControl([0x02,0x01,0x04])`启动传输和`[0x02,0x01,0x09]`退出的成熟实现。但这不是本用户已验证`AA12/AA13/AA14`+原厂`0x39/0x30`方案的同一BLE帧，不能盲发这些命令。
- 开源 [pepebot-space/nisaetus](https://github.com/pepebot-space/nisaetus) `ARCHITECTURE.md`针对M02S/A02S的另一套`02 01 04` P2P控制。**没有证据证明用户当前固件支持以这组原始指令替换成功的AP路径**；本版不修改已成功Wi-Fi的连接类型、端口、密码或下载协议。

## 本版功能：唯一一个协议实验，前后两次对照
1. 完全保留v0.1.18已成功的逻辑：BLE预连接→严格等AA14`0x45 0→1→0`照片活动结束→250ms后唯一一次`0x39/0x30`开AP→验证`AA14 0x25`准确SSID→立即Android`WifiNetworkSpecifier`请求→`onAvailable`与真实私有网关→原厂`GET /app/getfilelist` HTTP200/result0/info→两次350ms稳定候选→JPEG原图只读GET到RAM。保留v0.1.19的onUnavailable旧回调归属防护、分段毫秒计时，没有12秒扫描、没有2.2秒强制AP兜底、没有无限轮询。
2. 新增`M02sApWire.nonClearFinishRequest()`，只返回严格字节帧`AB 55 00 04 44 30 01 75`。新增独立白名单`authorizedNonClearFinish`，保留原`authorized`只能发既有`39 30`开AP；**不添加任何可输入任意0x44参数/删除功能**。JUnit明确验证删除变体`44 30 00 74`、部分下载`44 31`和其它变化全部拒绝。
3. 新增**默认未勾选**的“启用实验：每次高清取图成功后发送一次不删除结束帧”。勾选后，且**只有真实JPEG经所有检查下载并解码成功**，在释放手机Network请求之前经BLE AA13提交**唯一一次不删除结束帧**，等待GATT成功回执或最多3秒，再释放手机Wi-Fi请求、回到BLE待机。无GATT回执不重发，不宣称固件已退出。勾选状态不存盘、不跨进程保留。
4. 新增“手动尝试退出眼镜传输模式（需先勾选；不删除）”。如果旧固件AP状态仍残留，用户可主动重连BLE待机（不打开新AP）、勾选实验并点一次按钮；只发送同一个精确不删除帧、等待一次回执、释放手机网请求。**不保证一次能真正使固件恢复拍照**。
5. 不勾选时行为和v0.1.19完全相同；只释放手机网络请求，**根本不会发0x44**。如果BLE关闭、正在拍照/取图、正在写其它命令，则拒绝发送退出帧。自动退出只在本张JPEG已经成功下载/解码时才尝试，失败不自动发送实验指令。
6. 本版不发送`0x22`拍照、`0x44 0x30 0x00`删除/清除、0x31未知计数、OTA，也不写相册、不上传云端、不保存文件内容在诊断，不修改正式蓝星向导。

## 设备真机的最短对照
1. 安装v0.1.20独立包，**保持实验开关关闭**，蓝牙待机后实体拍照一次，观察是否仍然不能连接Wi-Fi，复制日志。**失败就不要暴力重复开AP**。
2. 若只看到SSID但Android连不上，想验证“遗留传输状态”假设：重新点准备蓝牙待机（不能有活跃取图），**手动勾选实验**，点**“手动尝试退出眼镜传输模式（不删除）”**一次；留意AA13 GATT写入回执，但不能直接当作眼镜已退出。再按眼镜实体快门，测试能否成功建立Android Wi-Fi/GET原图。
3. 若第一次成功，并想比较连续两张，在开启实验状态下拍一张，等图片显示及“结束帧GATT写入”后再按实体快门一次。重点比较旧模式 vs 开启退出实验后的`onAvailable/onUnavailable`、单次连接耗时；**切勿在手机未验证原图成功时反复发送清理命令**。
4. 如果有任何照片数量异常、不能拍照或状态不稳定，立即关实验并回退v0.1.18/0.1.19成功基线，不要靠盲发更多命令补救。

## 代码、隔离与边界
本版从`exp/m02s-network-callback-guard-v0119-20261114`成功发布HEAD`d6befdb9343d4e55e8f34d85904b1769c94f8805` 创建`exp/m02s-optin-noclear-exit-v0120-20261114`。独立`com.vobichngan.bluestar.m02snoclearexitlab`、versionCode21/versionName0.1.20，仅实验目录及独立CI修改。原v0.1.12～0.1.19实验分支/Release、正式蓝星向导RC25、Windows OCR、HeyCyan原厂及开源项目都不修改。是否真正加速或稳定下次Wi-Fi仍需M02S真机实测，不能从CI推断。开源E09 MIT归属见`E09_SOURCE_LICENSE.md`。
