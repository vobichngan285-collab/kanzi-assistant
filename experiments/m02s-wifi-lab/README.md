# 蓝星 M02S AP 权限修复实验 v0.1.5（Android 16）

## 来自 vivo X200 Pro 的真实故障
v0.1.4 已经验证：GATT LE 1M 成功、AA14 订阅成功、用户点击“AP 热点精准连接”后向 AA13 发送官方 0x39/0x30，收到 0x39 回执和 0x25 SSID（13 字节、校验通过、UTF-8 已解析）；随后程序在调用 Android 连接网络阶段出现 **`SecurityException`**，日志为：
`17:17:40 实验 Wi-Fi 会话已停止：AP 请求异常：SecurityException`
**系统并未弹 Wi-Fi 许可框，也未成功连接 AP。**

## 修复
- 在 `AndroidManifest.xml` 新增标准普通权限 `android.permission.CHANGE_NETWORK_STATE`；原版本已有 `ACCESS_NETWORK_STATE`、`CHANGE_WIFI_STATE`、`NEARBY_WIFI_DEVICES` 等。此权限用于 Android `ConnectivityManager.requestNetwork()`，是上次异常的**优先排查原因，不把它假定为已经真机修复**。
- 在准备 AP 请求时先读取并记录四项授权状态：`CHANGE_NETWORK_STATE`、`ACCESS_NETWORK_STATE`、`NEARBY_WIFI_DEVICES`、`ACCESS_FINE_LOCATION`。缺少关键网络权限会安全停止，不再盲目发起网络连接。
- 出错时只记录调用阶段：构建 `WifiNetworkSpecifier`、构建 `NetworkRequest`、提交 `ConnectivityManager.requestNetwork`，以及异常类型（`SecurityException` 等）。**不输出 Android 异常原文**，因为某些系统异常可能包含私有 SSID/密码；任何 Wi-Fi SSID、配对口令与 0x25 原始载荷均未输出/持久化。
- 保留 v0.1.4 目标SSID精确匹配、P2P只连接唯一匹配设备（不会连惠普打印机）、原版成功的 BLE 连接/0x25 解码和禁止未确认网络上进行 HTTP 探测的保护逻辑；不新增厂商未知指令。

## 安装与单次测试
- 新包名 `com.vobichngan.bluestar.m02sapfixlab`，版本 `0.1.5`，与 v0.1.4/早期实验、正式 Blue Star Guide RC25 和原厂 HeyCyan **共存，不覆盖**。
- 退出 HeyCyan，打开独立实验APK。先点扫描眼镜→选 M02S-3584→连接 LE 1M→开启 120秒 BLE 监听，等 AA14 CCCD 状态0。
- **只点击一次 AP 热点精准连接**。程序发送官方 0x39/0x30 并监听 AA14 0x25/SSID；看到新日志中的 `AP系统权限预检：CHANGE_NETWORK_STATE=已授权`。随后：
  - 如 Android 提示连接专用 Wi-Fi 网络，请在系统弹窗里确认；
  - 若成功，日志应显示“Android 已连接到指定的眼镜 AP 网络”及“网关IP已获取”。**Wi-Fi 成功之前，切勿尝试文件列表或保活**；
  - 如再失败，复制从“AP系统权限预检”至异常和“会话已停止”的日志即可，不需再次测试 P2P/拍照或反复扫描。
- 对照意义：若 `CHANGE_NETWORK_STATE=已授权`，但在 `提交 requestNetwork` 阶段仍是 `SecurityException`，则应进一步定位 Vivo/Android 16 特定系统限制或调用条件，**不能继续宣称只缺一个权限**。

## 自动验证
GitHub Actions 执行 Java 单元测试、Android Gradle SDK36 编译、APK v2 签名、zipalign、ZIP完整性、应用包名与版本校验，并**额外核查 APK 实际声明 CHANGE_NETWORK_STATE、ACCESS_NETWORK_STATE、CHANGE_WIFI_STATE**。测试覆盖调用阶段诊断、权限状态和异常信息隐私保护。

## 限制
这次只修复 Android 网络权限候选问题，没有证据证实眼镜已经连接热点。照片自动传输、Wi-Fi 保活仍未实现。正式安卓/Windows项目原代码、旧实验分支、官方 APK 均保持不变。
