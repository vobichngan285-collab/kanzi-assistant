# C110 原厂 SJCAM Zone 6.7.3.16 Wi-Fi 连接证据及蓝星对照（2026-11-19）

## 直接分析的原始依据

- 用户此前提供 `bdcc3db17ea0e26aecaf22bc01fe99be.apk`，现场已读取原始 `classes5.dex`（6,360,504 bytes），解析 DEX class/method/code_item 并检查实际方法调用与常量；不是凭网上的通用示例猜测。
- `Lorg/jght/sjcam/zone/view/WifiBottomPopupV2;`：
  - `scanWifi` code_off 0x3382d8；`showInputPassword` 0x338498；`connect` 0x337950，读取实际SSID、密码后调用 `WifiHelper.connectWifi`。
  - `ConnectCameraFragment.parseQrCodeResult` 0x2b0b84 可从扫码结果提取连接字段；`ConnectCameraFragment.connect$lambda$20` 0x2aed40 把所提取的**实际ssid/password**交给 `WifiHelper.connectWifi`。
- `Lorg/jght/sjcam/zone/helper/WifiHelper$1;`，`run` code_off 0x1b1fd4：检测已有请求并取消、调用 `WifiManager.disconnect`，检查 `DevicesToolsKt.isViVoDevice()`（DEX 指令 pos103），vivo/鸿蒙/红米/Oppo/Samsung 走 `WifiHelper.setCurrentConnectWifiNetWork`，而其他品牌部分走 `WifiNetworkSuggestion`。因此不能给vivo套无条件的Suggestion分支。
- `Lorg/jght/sjcam/zone/helper/WifiHelper;`，`setCurrentConnectWifiNetWork` code_off 0x1b3264：
  1. `WifiNetworkSpecifier.Builder`；有合法BSSID时 `setBssid(MacAddress.fromString(bssid))`。
  2. **`setSsid(exactSsid)`**、`setWpa2Passphrase(actualPassword)`，不是固定 `setSsidPattern("C110")`。
  3. `NetworkRequest.Builder.addTransportType(WIFI)`；`addCapability(13 NOT_RESTRICTED)`、`addCapability(14 TRUSTED)`、`removeCapability(12 INTERNET)`。
  4. vivo 进入 `requestNetwork`，在其他品牌/分支有40秒后回调或30/45秒系统请求的差异；本实验暂采用vivo适用的30秒系统请求，不使用无限循环。
- `Lorg/jght/sjcam/zone/helper/WifiHelper$3;`，`onAvailable` code_off 0x1b223c：原厂在网络获知后调用 `ConnectivityManager.bindProcessToNetwork`，然后执行连接结果回调；`onUnavailable` 0x1b2490 注销/处理回调。
- `Lorg/jght/sjcam/zone/ui/fragment/home/ConnectCameraFragment;`，`connectCamera` code_off 0x2aee7c：已连接的Wi-Fi走 `WifiHelper.bindProcessToWifi`，成功后由 `SJConnCameraPresenter.connCamera` 进入相机协议。对标准C110已验证的LY命令链仍为3012身份→3016→3001/par1→2015/par1→LibVLC，属于蓝星已验证基线，不能重写。
- 原厂 `LyCameraWifiNetWorkHelper.isTargetGateway` code_off 0x317b40 使用默认网关 `192.168.1.254` 来跟踪*手动/活动网络*；这与 `WifiNetworkSpecifier` 返回的 local-only 次要网络可能没有默认网关是两个路径，蓝星必须保留对真实Network的直接3012确认。

## 上版蓝星问题和此次最小对照修改

- v0.1.19 仍然 `setSsidPattern(PREFIX="C110")` 并写死密码`12345678`，未基于实际扫描到的SSID/BSSID/用户密码。若相机热点名称并非精确以C110开头，系统弹窗不会找到匹配网络；手机始终显示“正在申请连接”。
- 新实验版采用原厂同类的**先选择实际热点、再用精确ssid/pwd/有效bssid申请**。第一次在蓝星C110页面弹出附近热点列表及密码确认，不要求用户先去手机系统设置；下次使用本包时保存的实际连接参数将用于启动申请。
- Android 16扫描热点需 `ACCESS_FINE_LOCATION` + Wi-Fi扫描权限及系统定位可用；若用户不授予扫描权限，则只在软件内提供手动SSID/密码输入，不盲猜热点。Android系统是否出现连接授权弹窗或是否准许由系统决定，不能绕过。
- 保留此前的 `Network.openConnection` 只读3012作为身份确认，随后C110独立视频 Activity 仍沿原已成功的 `bindProcessToNetwork` + OEM LibVLC 路线使用。**与原厂唯一审慎差异**：蓝星同时运行手机/USB及蜂窝云OCR，因此不在后台MainActivity把全进程永久绑定到无互联网的相机Wi-Fi；让C110视频Activity按原已成功逻辑临时绑定，照片/配置调用显式相机Network，以免破坏其他模式的网络。
- 不修改1001真实高清快门、网络图片下载、9011按键静音、9038指示灯、USB图像、AI任务和用户TTS。新包仅为实验版，原厂现场手机路径尚需用户自己验收。

## 再次验收

手机连家用Wi-Fi，C110打开Wi-Fi；首次进入蓝星“模式→C110”应该出现附近热点列表。选择实际C110热点、确认密码、批准Android系统请求。预览应在收到标准C110的3012成功后直接打开。如果没有扫描列表，诊断需要区分权限/定位、扫描结果为空、精确SSID请求`onUnavailable`、`onAvailable`未得到3012，以及设备只支持单Wi-Fi的系统限制；不再以固定前缀重复申请。
