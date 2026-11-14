# M02S v0.1.19 — 连续取图的 Android Wi-Fi 会话回调修复（隔离实验）

## 真实故障、确认的源码漏洞与未知原因
- 用户2026-11-14曾在v0.1.18成功完成 **从实体拍照到3200×2400原图解码** 的一次完整路径，拍照结束触发到解码6188ms、其中开AP+Wi-Fi就绪4648ms。截图随后显示本App已成功下载2张3200×2400原图，但再拍后发生「取图失败：Android未能连接眼镜AP，拒绝反复申请或猜测热点。BLE已断开」。**截图没有那次失败的完整时间日志**，不能据此声称Android限制了连接次数、眼镜损坏、热点失效，或固件必定没退出传输模式。
- 严格核查v0.1.18现有源码后确认一个具体漏洞：`ConnectivityManager.NetworkCallback.onLost` 检查 `networkCallback == this` 归属，但 `onUnavailable` 缺少同一检查，直接 `fail(token,...)` 会`stop()`蓝牙及网络。因`releasePhotoNetwork()`在每张下载后撤销NetworkRequest、**BLE generation保持不变**，旧请求延迟派发的`onUnavailable`可能误终止新一轮，即使新请求正常工作。**这是潜在的竞态漏洞，不等于已经证明截图中的实际故障必定由此导致。**
- 另外，每张照片完成后依用户明确要求立刻解除手机Wi-Fi的 `NetworkRequest`、保留BLE待机以免眼镜在AP模式下实体快门失效。下一次拍照需重新由已验证蓝牙 `0x39/0x30`打开AP，经`AA14 0x25`准确SSID申请系统Wi-Fi。这个模式是保留正常实体拍照所必需的折中，**本版不为减少申请次数而强行持续占用AP，不擅自改变拍照/传图模式切换逻辑**。

## v0.1.19 本轮最小修复
1. `onUnavailable()`也必须同时核对当前 `generation token` 和同一具体 `NetworkCallback` 对象；旧回调被忽略，不能影响下一张照片。这个判断与`onAvailable`、`onLost`、`onLinkPropertiesChanged`等已有防护对齐。
2. `releasePhotoNetwork()`与`stop()`中**先置空当前回调归属，再调用系统`unregisterNetworkCallback`**；已注销请求不再允许旧事件影响在途请求。
3. 对本次BLE发AP→收到准确SSID、Android `requestNetwork`→`onAvailable` / `onUnavailable`分别记录毫秒耗时；不打印SSID/密码/MAC。正常失败状态文字改为「本次Android精准Wi-Fi请求未成功（onUnavailable，耗时xxms），未重复申请」，不再把用户误导成Android对反复连接实行限制。Android官方`onUnavailable`表示`requestNetwork`未被满足或达到超时，**不能仅凭该事件认定具体根因**。
4. 如本次 `onUnavailable`后系统其实已连接同一已由BLE校验过的SSID且有真实私有IPv4网关，允许**仅检查一次当前已存在Network** 并复用（不发送第二次系统请求），仍须由原厂HTTP200/JSON result0和info字段验真；如果不存在就停止、等待用户下一次明确操作。**绝不反复Wi-Fi申请、暴力重试、猜测热点或扩大目标网络。**

## 明确保留的已成功基线
- 严格等待`AA14 0x45`拍照状态`1→0`后250ms发送仅一次白名单 `AB 55 00 03 39 30 69`，没结束事件不会倒计时自动发。人工确认已拍好可手动触发。
- 已有AA13写回执、AA14 `0x25`校验及5秒SSID超时、无需12秒扫描的精确`WifiNetworkSpecifier`，请求本身25秒上限原样保留以便观察是否真实超时。绝不通过BLE遥控拍照 `0x22`，不发可能删除文件的`0x44`或OTA。
- Wi-Fi专属`Network` + 私有网关 + 原厂只读`GET /app/getfilelist`严格校验；两次稳定最新候选相差350ms、原始JPEG只读GET、32MB限制与路径/格式/尺寸检查，照片仅RAM显示，不上传云端、不保存相册、不删除眼镜照片，不接OCR/TTS。
- 首轮未建立拍前照片基线，唯一时间排序的最新候选尚不能严格证明是本次实体快门拍摄；无新增风险。
- 本轮除增强连接诊断及竞态保护外，**不更改 Wi-Fi 建连方式、不更改已测成功的拍照状态处理或JPEG取图，不以猜测条件延长/缩短延时**。

## 真机验证
1. 安装独立v0.1.19，点`准备蓝牙待机`，按实体快门，一次次观察。Android 16授权窗口出现时按系统引导操作。
2. 复制一轮完整日志：`0x45 1→0`→AA13开AP写回执→AA14 `0x25` SSID及「开AP指令至SSID耗时」→「精确SSID请求→onAvailable耗时」→网关→GET、3200×2400解码；如果`onUnavailable`，新日志会给出本次精确SSID申请耗时，可区分接近25s的请求超时或短时间即不满足。
3. 可连续拍两张验证旧回调不会影响新一轮，但若发生失败**不要循环重复按钮试运气**；反馈一次日志。失败时上张高清内存预览保留，并不意味着本张也已下载。

隔离分支 `exp/m02s-network-callback-guard-v0119-20261114`从v0.1.18成功发布HEAD `de927a30174d542484f73e1d06e2ffc268bc32a7`新建；独立包名`com.vobichngan.bluestar.m02scallbackguardlab`，versionCode20/versionName0.1.19。正式蓝星向导、Windows OCR、HeyCyan与v0.1.12-v0.1.18旧实验/发布均不修改。公开MIT E09协议来源保留在`E09_SOURCE_LICENSE.md`。
