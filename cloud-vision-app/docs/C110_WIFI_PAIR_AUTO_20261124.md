# 蓝星向导v1.0.84｜C110首次搜索配对一次，后续自动连接，允许重新选择相机
日期2026-11-24。原仓 `vobichngan285-collab/kanzi-assistant`，稳定基线v1.0.83 SHA `3bb026880e303a1451f2b4906b858117a4035702`，实验分支 `exp/c110-wifi-pair-autoconnect-20261124`。

## 用户授权与边界
用户之前在vivo X200 Pro Android16测试“双WLAN加速同时连接家用+相机WiFi”，原厂SJCAM App报`C110_2e53 相机被其他设备连接`，且会断开。用户明确放弃双WLAN方案，只要求“软件启动后能连接运动相机WiFi；首次从软件中搜索实际SSID、输入密码连接一次，验证后记住；下次开软件+相机能自动连接；另有手动连接和更换相机”。用户随后明确批准开始修改。**不尝试双WiFi上云，不修改移动数据专用云端链路，也不改变已验证1001快门/预览和8MP→1440P。**

## 连接流程
- `HomeActivity`持有**唯一长寿命**`C110WifiBootstrap`网络请求回调；新增首页“运动相机Wi-Fi”入口，已记住显示实际SSID，未配对点击可扫描。新的一次App首页前台`onResume()`调用`startAtLaunch()`，如果已经记住支持的C110热点且自动连接开启，Android系统未连接时发起一次`WifiNetworkSpecifier`精确SSID+保存密码的网络连接请求。避免重复授权提示风暴，不在启动时自动拍照，不自动弹出无预配对的扫描窗口。
- `设置→相机→C110运动相机`内“立即连接”“搜索/更换相机”“下次打开自动连接(开/关)”；设置页、C110实时预览页和首页快捷拍照都通过静态共享入口使用Home持有的一个`NetworkCallback`，设置页权限结果由正确Activity转送Home所有者；Home销毁则释放网络请求；不允许多请求互相撤销。
- 首次手动搜索使用系统`WifiManager`实际SSID、可选当次BSSID；Android需要位置权限和系统定位开启才能读取周围SSID，扫码受系统节流。不按“C110_前缀”猜热点，列表支持选择真实相机，密码第一次用户输入，不再默认猜`12345678`。只在`3012`返回已验证**标准C110**设备身份、且WiFi网络仍有效时，才写入`SharedPreferences c110_selected_wifi_v1`保存SSID/密码/BSSID并默认允许下次自动请求。连接失败或扫到别人的设备不能覆盖此前成功记住的相机。原来在v1.0.83记录的可信旧连接仍可复用。
- 下次重连只用**精确已保存SSID**，不把上一次BSSID绑定到新的会话，避免相机重启/换网卡MAC或更新后旧BSSID锁死。新连接使用当次扫描的实际BSSID；更换相机提供单独显式扫描入口，即使已连接旧相机仍可选择新相机。保存的是经过真实3012确认的WiFi，其他未知设备不冒充C110；保留老固件安全身份核验，未启用没有验证的C110+或PRO原厂命令。
- 不再调用`WifiManager.disconnect()`抢先断开家用WiFi，而由Android系统依`WifiNetworkSpecifier`处理单连接网络选择。**Android16是否每次免确认和是否会切换单WLAN，是操作系统行为，不作伪承诺。** 若系统仍开启双WLAN加速导致相机被占用，用户应关闭手机双WLAN加速再测。WiFi连接成功后仍是相机本地WiFi传照片，`C110WifiCamera.requireCellular()`通过移动数据上传千问。

## 严格改动范围
从v1.0.83严格只改`C110WifiBootstrap.java`、`HomeActivity.java`、`MainActivity.java`、`BlueStarSettingsScreen.java`、`app/build.gradle`，新增`C110WifiPairingPolicy.java`及`C110WifiPairingPolicyTest.java`、本交接文档、专用CI共9文件。OEM`C110WifiCamera`、`C110CameraActivity`、`C110QuickShotController`、`C110FrameReader`、`CloudVisionClient`、`InternalCameraController`、`PhoneStillJpeg`、所有USB与TTS完全不动。首页只多一个连接相机按钮，原有快捷拍照、朗读、预览、云端API和原图/1440P上传不变。

## 验收（真机待做）
vivo X200 Pro先关闭“双WLAN加速”，开启标准C110实际热点`C110_2e53`，进入蓝星向导首页点“运动相机 WiFi”，授权附近WiFi与精确定位扫描、选择正确SSID输入实际密码。核实3012成功后软件提示“已验证并记住”；退出软件与相机关机，再打开相机和软件，核实是否主动发起Android连接请求，可能需系统确认但不再输密码。家WiFi在相机连接时由Android决定切换，后续云端仍需移动数据。断电重启、进入独立C110预览页和首页快捷拍照应保持一条WiFi会话、1001仍只有一次，OCR/TTS及相机原图/1440开关不变。然后重新扫描选择另一相机，错误密码/错误设备不能覆盖旧成功配对。系统权限与Firmware需要真实现场验证；CI通过不等于连接一定无须系统授权。
