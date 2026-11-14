# M02S AP 就绪与真实失败原因实验 v0.1.10（独立 APK）

## 经真机证实的现状（不能重复已完成实验）
- v0.1.3 BLE 官方 AA13 `0x22/0x30` 真机遥控拍照成功。
- v0.1.5/v0.1.6 Android 成功连接眼镜 AP、获取真实网关，官方 `GET /app/getfilelist` 和原图下载成功：JPEG 3200×2400、481424 字节、532ms。
- v0.1.7 先连 AP 时拍照未成功；v0.1.8 先拍照成功（真实快门声）后 Android AP 两次失败；v0.1.9 在新拍照后收到 `0x42=00 0C`，**扫描到唯一相同 SSID 的热点**，但首次 BSSID 锁定请求 19:20:59 `onUnavailable`，随后立即第二次 SSID-only 请求 19:21:03 `onUnavailable`。没有进入 HTTP 原图下载。
- v0.1.9 原先将 **30 秒内缓存**视为可连接热点；`ScanResult.timestamp` 不能证明照片完成后热点已可认证。连续两次失败仍不能直接断言是 AP 启动时序、密码、系统拒绝或者同 Wi-Fi 并发问题。

## 本轮严格限定的修订
参考 Android 官方 Wi-Fi 扫描文档：`getScanResults` 可能返回前次扫描的缓存；`SCAN_RESULTS_AVAILABLE_ACTION/EXTRA_RESULTS_UPDATED` 表示最近扫描是否完成和结果是否更新。通过 `ScanResult.timestamp`（开机后微秒）和本次主动扫描发起时刻（`SystemClock.elapsedRealtime`）比较：

1. 保留 v0.1.8/v0.1.9 的 **一次 BLE 真机拍照 → 至少5秒 AA13 间隔 → 单次原厂AP信息请求**。AA14 `0x42`、`0x45`、`0x53` 的业务含义没有充分验证，不能直接标注为拍照保存完毕或 AP 就绪。
2. 从 BLE `0x25` 取得精确目标 SSID 后，检查是否有 **同SSID且真实私有网关**的已有 Wi-Fi Network（原逻辑保留）。否则调用一次 `startScan`，监听 Android 扫描完成广播，读取 `ScanResult.timestamp`：仅信任发出本次扫描请求 **之后**产生、10 秒以内的同SSID、有效 BSSID；即使旧记录只几秒也算旧缓存。
3. 新鲜且唯一目标扫描记录出现后保守等待 **1.8 秒**，同一目标仍可见时只使用 **v0.1.6 已成功的 SSID-only WPA2 NetworkSpecifier** 发起一次网络请求。多个同名目标热点拒绝猜测，12 秒内无新扫描记录则不申请连接，日志明确显示是扫描/系统限流而不能推定眼镜没广播。**扫描发现热点也不等于能认证连接**。
4. Android 14+（本机 Android 16）用 `WifiManager.addLocalOnlyConnectionFailureListener` 获取系统报告的失败分类（认证、关联、IP分配、无响应、未找到、用户拒绝、未知）；在 `onUnavailable` 后留 1 秒接收可能迟到的原因回调再结束。**不自动二次重连**，不反复发送 BLE 快门/厂商 Wi-Fi 命令，也不猜 AP 密钥或新私有接口。
5. AP 一旦有真实专属 `Network` + 网关，继续复用 v0.1.6 官方 AP JSON 列表、只读 GET 原图、32MB 限制、图片格式校验、原图内存预览，不涉及相册、上传、文件删除或固件。第一次拍照前没有基线，只能显示唯一最新候选供人工核对，不能冒充已证实本次新照片。
6. 记录的只有扫描更新/缓存数量、连接阶段、Android 提供的失败状态分类和时间。不记录 BLE 返回的目标 SSID、BSSID、密码、真实路径或图片内容。

## 只需一次新版本真机实验
- 退出 HeyCyan，眼镜处于普通拍照状态；开启本版 APP，连接 BLE LE1M，AA14 通知订阅成功。**不要提前连接 AP**。
- 眼镜对准独特新场景，点击一次 **「拍照→验证AP→取原图」**，在实验 APP 前台完成观察。如果系统弹出授权，用户自主同意/拒绝，保持手机原有家庭 Wi-Fi 设置不变。
- 记录真实快门声；查看 **AP阶段1** 是否出现“本次请求后新扫描”，及 **AP阶段2/阶段3** 的系统连接结果，尤其 `Android报告本地设备网络失败原因`。如果预览出现，用户只需核对是不是刚拍的画面，不要求发送照片。
- 若只见旧缓存/无新扫描，不代表眼镜一定不广播；若出现明确认证/用户拒绝/未找到故障，再依据系统分类继续修复，不要重复旧版本成功的 Wi-Fi 或原图实验。

## 版本与边界
独立分支 `exp/m02s-ap-freshscan-failurecode-v0110-20261114`、独立包 `com.vobichngan.bluestar.m02sapreadylab`，versionCode 11/name 0.1.10。保留 v0.1.3～v0.1.9 所有旧分支/Release，正式蓝星向导 RC25、Windows OCR、HeyCyan 不修改。CI APK 编译测试完成不等于手机实测成功，也不表示最终常驻 Wi-Fi 目标已实现。
