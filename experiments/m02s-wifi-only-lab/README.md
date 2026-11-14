# M02S-3584｜v0.1.15 实体手动快门 + Wi-Fi 精准连接回退修复

## 修复依据（真实Android16失败）
- 用户 v0.1.14 21:55:46 BLE `0x39/0x30` 开AP已成功写入；AA14 `0x25`返回校验通过的真实SSID；21:55:47 曾检测到本次新AP广播。
- 后续实机屏幕提示 **“连接失败：12秒内未扫描到本次新的眼镜Wi-Fi热点；不连接旧缓存或邻居设备”**。当眼镜BLE提供真实SSID后，Android扫描可能限流、结果回收或广播暂时消失。扫描缺席**不能推断该眼镜不支持或不存在AP**；也不能直接承诺AP在线。
- 截图界面顶部错误残留 `v0.1.13`，虽然运行的是v0.1.14。此版修正UI与APK Manifest版本标签，所有显示统一为v0.1.15。

## 最小变更——在旧成功流程上加安全回退
1. **只启动一次原厂已验证BLE开AP**：扫描唯一 `M02S`，GATT LE1M、AA14 CCCD，AA13写精确白名单 `AB 55 00 03 39 30 69` (`0x39/0x30`)，等待GATT status0和AA14 checksum正确的 `0x25` 真实SSID。无 `0x22` 远程拍照、无BLE直传、无删除/OTA/P2P写指令。
2. **优先复用已经连接的精确目标**：遍历Android已连Wi-Fi Network，若WifiInfo SSID与BLE返回的完全相同、并有真实私有IPv4网关，复用此Network，**不申请新连接**。但仍必须让 `MainActivity` 读取 `GET /app/getfilelist` 验证原厂JSON `result=0` / `info`。不因SSID相同就宣称眼镜已就绪。
3. **新扫描成功**：依旧遵循旧版真实已测的 `FRESH_UNIQUE` / 1.8秒新扫描稳定规则，再执行一次精确SSID/WPA2 Android `requestNetwork`；不能连接其它热点，不凭扫描结果宣称Wi-Fi已连通。
4. **扫描12秒无可靠本次新结果**：如果**AA13开AP写入回调status0、AA14 `0x25` SSID字节/校验合法、没有已发出Network请求、未出现多个同SSID不同BSSID歧义**，则**只对BLE确认的准确SSID**用先前v0.1.6/v0.1.10成功的 `WifiNetworkSpecifier`+OEM WPA2密钥申请一次Android目标网络。Android是否提示授权、`onAvailable`、真实网关和原厂HTTP列表是否存在，才是最终判断；失败明确停止，不再次写BLE、不反复Wi-Fi重试、不猜邻居SSID。
5. **建连成功后取消旧90秒连接超时**，让拍照前基线+最多120秒/3张连续监听不被建连超时误停。手动按实体快门，HTTP每约450ms找相对旧基线的新文件并稳定两次再GET原始照片。保留旧的文件名/URL/尺寸/32MB安全限制，只在内存显示，不上传、不存相册、不删眼镜照片。

## 实机验证指引
1. 不需要进入Android WLAN手动猜SSID。关闭HeyCyan，手机蓝牙/WLAN开，授予所需蓝牙/附近Wi-Fi/位置权限，打开 **独立v0.1.15**。
2. 点击 **「连接M02S并开启Wi-Fi、自动等待照片」** 一次，不再重复点；Android系统如果弹出眼镜Wi-Fi授权，请选择允许。
3. 日志应出现 `收到AA14 0x25准确SSID`，然后根据实际情况显示“复用已有目标Network”“已看到新AP”“扫描12秒回退，仅请求一次准确SSID”其中之一；接着**必须**有`Network onAvailable`或复用真实Network、原厂HTTP200照片列表通过、旧照片基线建立。
4. **只有显示“旧照片基线已建立”后**才按眼镜实体快门一次，观察有没有真实快门动作，以及是否出现新文件/下载高清图；第一张确认后再按第二和第三次。
5. 若走到 `onAvailable` 但原厂HTTP失败，请复制安全日志以定位网关/原厂接口；若AP已连接但眼镜实体按键拍照没反应，记录**固件可能拍照/传输互斥**，不要重试旧蓝牙拍照链路。
6. **测试边界**：本轮仅保证代码能编译并在Android中尝试一次安全回退，不保证眼镜AP真的允许手动快门，亦不保证“按键到高清原图≤3秒”。用户的全流程OCR/TTS尚未添加；单次Wi-Fi GET过去真机实测约475～758ms是已有事实，端到端速度仍待测。

## 开源与版本隔离
- 复用本仓库旧实验已验证的 `M02sPhotoTransport`、`M02sPhotoListReader`、`M02sFreshPhotoPolicy`，WPA2与BLE AA14/AA13控制，以及 `M02sApScanPolicy` / `M02sApReadinessPolicy`。参考 MIT `sctg-development/ai-smart-glasses-e09-reverse` 源码 `Constants.swift`、`WifiMediaManager.swift`，许可证和署名保存在 `E09_SOURCE_LICENSE.md`。
- 新隔离分支 `exp/m02s-ap-ssid-fallback-v0115-20261114`，实验目录 `experiments/m02s-wifi-only-lab`。独立Android App ID `com.vobichngan.bluestar.m02sapfallbacklab`，versionCode16/versionName0.1.15，与旧v0.1.14、v0.1.13、v0.1.12及正式蓝星向导RC25共存，Windows OCR/原厂App均不动。
- 新增 `M02sApConnectPolicy` + JUnit回归测试覆盖无扫描/只有旧缓存可安全回退、12秒前不回退、没BLE写确认/无效SSID/已请求过网络/同名多热点不回退等。CI成功≠真机连接成功或手动快门验证成功。
