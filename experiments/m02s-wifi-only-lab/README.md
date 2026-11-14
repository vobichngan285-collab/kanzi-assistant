# M02S v0.1.23：手动导入 + 新鲜热点确认 + 最多一次有条件恢复

在已发布 v0.1.22 基础上建立独立分支和包名，不覆盖正式蓝星向导、Windows OCR、v0.1.22 或原厂 App。操作仍是实体快门拍照 -> AA14 0x45 拍照结束候选 -> 用户点击“导入高清原图”，没有自动 250ms 开 AP。

**本轮唯一变化：Android Wi-Fi 连接策略。** 精确 0x39/0x30 BLE 开 AP 和 0x25 BLE 返回 SSID 沿用已在 M02S 真机成功的路径。先复用系统中已有精确 SSID+私有网关的网络（仍需 OEM HTTP 200 验真）；否则进行一次 Android startScan，并以本轮 BLE 开 AP 后的新鲜 ScanResult 时间戳、精确 SSID、唯一 BSSID 与 1.8 秒观察门槛核查热点，然后首次仍用旧版验证的 SSID+WPA2 WifiNetworkSpecifier 连接。Android 第一次 onUnavailable 后，只有再次新扫描观察到失败后出现的唯一目标广播，且改为精确 BSSID pinning 这一实质不同策略时，才允许唯一一次恢复请求；最大总计两次 Android 网络请求，每次扫描上限 7 秒，绝不多次发送 BLE AP 指令。

Android 16 系统可能限制主动扫描，startScan 接受不代表扫到新的 AP，旧缓存/没有结果也不代表眼镜热点关闭。扫描未能证实就停止，不凭猜测连接，不宣称已复刻原厂内部 Wi-Fi 状态机。公开 Eyevue 安卓参考代码仅证明 0x39/0x30 和 Android WifiNetworkSpecifier 路线，不证明原厂必有这个扫描/重试策略。

保留只读 0x64 项目查询、0x45 isImport 被动记录、私有网关确认、OEM GET /app/getfilelist 的 HTTP200 JSON、连续稳定文件列表、安全 JPEG 原图 GET 到手机 RAM、已有源文件去重/尺寸校验/匿名耗时。没有任何 0x44 清理/删除、0x22 遥控拍照、P2P、OTA、云上传、手机相册写入、OCR/TTS；不记录 SSID、BSSID、Wi-Fi 密码、照片名/图像或 BLE 地址。

**真机验收：** 每次先实体拍照，再手动导入，观察新鲜广播、Android requestNetwork 第一/第二次结果、网关、HTTP200、原 JPEG。和原 v0.1.22 对照连续最多五轮；一旦失败先复制匿名日志，不连续猛试。本版独立 applicationId com.vobichngan.bluestar.m02sfreshaplab，v0.1.23 versionCode24。

---

## v0.1.22 历史说明

# M02S v0.1.22 — 原厂式「实体拍照 → 用户手动导入」对照实验

## 为什么改
用户在原厂眼镜App中重复验证：实体快门拍照后原厂相册出现文件，手动点「导入」才开始连接眼镜，反复多张稳定成功；我们v0.1.18～v0.1.21把0x45拍照活动1→0与250ms后开AP绑在一起，第一张常成功，第二张常在Android精准SSID请求`onUnavailable`，甚至系统WLAN手动加入同一眼镜热点也可能失败。v0.1.20选用E09`44/30/01`实验并未稳定解决第二张；v0.1.21读取到M02S项目码`TK8`（CyanBridge T-series AP参考），被动`isImport`确实有0→1→0变化；且有一次第二张成功，也有再次失败。**还不能断言250ms自动开启是根因，必须单独隔离导入触发时机。**

## v0.1.22 流程
1. 点击 **准备蓝牙待机**。订阅AA14；不启动WLAN、不向眼镜拍照。若已经BLE待机，再点该按钮**不会强制重连**，只提示已待机。
2. **用实体快门拍照**。成功识别AA14`0x45`中的`isTakePhoto 0→1→0`，软件只标记「待导入候选」，**不**发送`0x39/0x30`，**不**自动请求Wi-Fi，**无250ms的自动延迟/触发任务**。这只是拍照结束候选，**不能在还没连眼镜HTTP之前声称已拿到真实文件列表，也不能保证JPEG已写盘**。
3. 如果眼镜确实已拍照、但缺失结束通知，用户可亲自点击 **已拍照但没收到结束通知？手动标记待导入**。这个按钮**只修改手机内存UI状态，不发送任何BLE命令**；必须由用户确认真正按快门并听到拍照反馈，避免重复读旧图。
4. 在待导入状态下，用户**单独点击** **导入高清原图（手动连接眼镜）**。这是本App**唯一**能调用`M02sApStarter.requestPhotoTransfer()`的入口；手动按钮仅调用一次准确白名单`0x39/0x30`启动T系列AP，接收AA14`0x25`准确SSID，Android只请求一次精确`WifiNetworkSpecifier`，验证真实Wi-Fi Network和私有网关，读`/app/getfilelist`合法HTTP200 JSON并复核最新文件稳定，再对最新安全候选只读GET原始JPEG到RAM显示。
5. 成功/失败均按原有流程结束本轮手机网络请求、保持BLE待机。**不会自动重试、不会自行重连或主动重新启动热点**；下一张重新按实体快门，再人工点击一次导入。连接失败时不要盲目重复点，停止并复制完整日志供比较。

保留v0.1.21已有的0x45`isImport`可选第十字段、用户点击才发的只读0x64项目查询、Android现有Wi-Fi Network数量、已有扫描缓存年龄和分阶段耗时。新增`拍照结束候选→用户点击导入`等待时间（若只有手动确认则记未知），以及连续第N轮手动导入编号。导入点击→解码耗时与以前拍照结束→解码耗时口径不同，不能直接比较认为更快。

**安全白名单保持不变**：仅允许已真机成功的`AB 55 00 03 39 30 69` 开AP，以及由用户主动发起的只读项目查询`AB 55 00 02 64 64`；没有0x44任何结束/清除版本、0x22遥控拍照、未知P2P、OTA、眼镜文件删除、云端上传、系统相册写入或OCR/TTS。现有照片只存手机RAM；匿名日志不记录SSID、密码、MAC、图片文件名或客户标识。

## 开源及原厂证据边界
- 原厂Eyevue公开逆向审计 `FerSaiyan/Alternative-HeyCyan-App-and-SDK/android/DATA_SYNC_COMMAND_AUDIT_20260909.md`指出照片导入使用`connectP2pWifitoReceivePhoto()`，T系列AP调用`getWifiInfo(GET_WIFI_AP)`，最终帧`39/30`与我们一致。**手动点击导入**是用户实测原厂UI操作；本实验只是相同交互顺序的对照，不声称复制了原厂完整Wi-Fi协商、全部结束状态和文件列表显示实现。
- E09 MIT `sctg-development/ai-smart-glasses-e09-reverse/Sources/e09_reverse/AdditionalValidations.swift`定义`0x45`的可选`isImport`状态。0不等于WLAN已经完整复位，1不等于WLAN肯定可以关联，扫描缓存不等于当前广播。

## 真机最短检查
启动独立APK→准备蓝牙待机→眼镜实体拍照→看「待导入」状态（此时**不得**自动出现AA13开AP或者Wi-Fi申请）→手动点导入→等待成功/失败→下一张重复，力争**连续5张各自经用户点导入并成功取原始JPEG**。记录每次Android`onAvailable`还是`onUnavailable`、`isImport`变化和整个匿名日志。第一次失败即停止，以免重复造成其它未知状态。**稳定5次只证明这次流程相对稳定，不能替代后续系统/固件兼容验证**。

## 分支与版本
原仓库`vobichngan285-collab/kanzi-assistant`，仅在已发布v0.1.21 HEAD`b32b4982581cf1190b8e9e7a741475a594f3d928`基础上建立隔离分支`exp/m02s-manual-import-compare-v0122-20261115`。独立包名`com.vobichngan.bluestar.m02smanualimportlab`，版本v0.1.22（versionCode23），绝不覆盖之前实验APK及正式蓝星向导。
