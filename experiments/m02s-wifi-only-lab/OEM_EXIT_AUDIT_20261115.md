# M02S 原厂文件导入结束与第二轮状态审计（2026-11-15）

## 范围与证据等级

- 直接读取用户私有原厂安装包 `70465ff3819258a6218e7871da0a23f8.apk` 的原始DEX，文件 SHA256 `92f103b9d473d4cdf7424407ddfc851cac1700d15a06e55dfa0009ccf962ad57`，包含7个DEX。**没有上传原厂APK到公共仓库**。
- 下述原厂细节是直接从APK字节码还原的具体方法调用/字段赋值（不同于仅凭方法名推测）。当缺少运行时证据时明确标注“未知”。
- 对照蓝星 `exp/m02s-oem-wifi-restore-v0126-20261115` 的 `MainActivity.java`、`M02sApStarter.java`、`M02sPhotoListReader.java`。不修改原厂App、眼镜照片或固件。

## 一、原厂怎样得到本轮文件总数（确证）

1. `classes5.dex: Ldp;->connectP2pWifitoReceivePhoto()`：新建传输会话时重置 `m,l,o,p` 计数与缓存，并通过 `getWifiInfo(GET_WIFI_AP)` 启动AP。
2. AP联通后调用 `Lu64;->loadData()` → `Lt64;->getDevicePhotoList()` → `Lgyh;->getDevicePhotoList_Ap()`。
3. `Lu64$a;->onLoadSuccess()`：解析 `PhotoApBean.info[]`，对其中每组 `files[]` 逐个创建 `FileListParser.FileItem`，加入用于本轮下载的列表；可见路径中**没有只保留JPEG/PNG的图片筛选谓词**。音频与视频可能包含在列表中，至少不能把该列表无条件当作“JPEG图片数”。
4. `Ldp;->onGetDataSuccess(List)`：将列表保存到 `k`，初始化 `m,n`，并用 `List.size()` 赋给 `l`（原厂本轮待传输总条目数）。这不是 Android 相册历史所有文件总数，也不是蓝星过滤后的图片数量，虽然来自镜端同类HTTP列表。
5. `Ldp;->startDownLoadThumbnailPhotoByWifi()`：先处理每个条目的缩略图（对音频另有跳过/占位逻辑），完成后调用 `startDownLoadPhotoByWifi()`。
6. `Ldp$e;->taskEnd(COMPLETED)`：先处理原图临时文件迁移；成功后递增 `m`（进度索引）和 `o`（真正完成原图数），再处理下一项。若本地迁移失败，不会走这条成功递增路径。
7. `Ldp;->startDownLoadPhotoByWifi()`：当进度达到 `l` 且状态允许时报告完成，延迟约 **400ms** 调用 `Lcp;->run()` → `Ldp;->stopP2pWifiConnect()`。

## 二、原厂如何结束会话（确证）

`classes5.dex: Ldp;->stopP2pWifiConnect()` 比较 `o` 与 `l`：

| 本轮完成数/总数 | 发送的 BLE 参数 | 证据 |
|---|---|---|
| `o=0` 或 `l=0` | `WIFI_FILE_DOWNLOAD_FINISH` + `WIFI_FILE_DOWNLOAD_NOT_CLEAR`（44/30/01） | 原厂0x44分支 |
| `o=l>0` | `WIFI_FILE_DOWNLOAD_FINISH` + `WIFI_FILE_DOWNLOAD_CLEAR_THUMBNAIL`（44/30/00） | 原厂0x44分支 |
| `0<o<l` | `WIFI_FILE_DOWNLOAD_FINISH_NUN` + `o`（44/31/N） | 原厂0x44分支 |

随后 AP 分支调用 `Lxxh;->disconnectAp()`，其定义位于 `classes3.dex`，包括旧会话ID失效、Handler回调清理、注销ConnectivityManager NetworkCallback、解绑进程网络、清理SSID/连接相关状态等。原厂这段代码**没有等待0x45 isImport=0或显式确认眼镜已恢复拍照状态**。发送 `0x44` 的GATT排队结果不等于固件完全完成退出。

## 三、为什么不能照搬 `44/31/01`

- 蓝星 v0.1.26 的 `M02sPhotoListReader.Result.entriesObserved` 是解析到的镜端全部条目数；`eligibleImages` 只保存识别为安全图片扩展名的候选，两者不一定相等。
- 原厂 `l=List.size()` 是其解析器生成的原始 `FileItem` 列表长度；蓝星没有复现缩略图遍历及原图逐条顺序，仅下载最新的一张，而且**不知道该张在原厂待处理列表中的位置**。
- 即使例如镜端共5张图片，本轮蓝星只读1张，`44/31/01` 传给固件的参数也可能按原厂下载顺序解释，而非蓝星选取的最新文件。**不知道固件是否据此清理某些文件**，不能保证安全。
- `CLEAR_THUMBNAIL` 是原厂参数名称，不等于已经证明镜端高清原图绝不会受影响。不能发送 `44/30/00` 做试错。
- v0.1.20已经测试过 `44/30/01` 并且第二轮仍可能失败；不重复这一路线。

**结论：截至本审计，没有从原厂APK单独证明能够“只退出 AP、完全不清理眼镜任何文件”的可靠 0x44 参数组合。**

## 四、蓝星 v0.1.26 的结束状态误判（确证）

成功下载3200×2400 JPEG后，`MainActivity.finishTransfer(true)` 默认调用 `M02sApStarter.releasePhotoNetwork()`，只注销本手机 `NetworkCallback`、清空本地SSID、网络句柄、AP请求标志，保留BLE GATT监听。

- 此时“手机 Wi-Fi 请求已释放”**不等于**眼镜已收到 `0x44`。
- `0x45 isImport=1` 是用户日志曾观测到的值；没有后续 `isImport=0` 既不证明镜端一直为1，也不证明已退出。
- 即便镜端后来被动发送 `isImport=0`，也只是报告导入标记不活跃；它**不能代替**下一轮真的 `onAvailable` + HTTP200 + 不同JPEG成功下载。
- v0.1.26 在用户日志中第一轮HTTP200+最新3200×2400 JPEG已经成功；这早在此前版本出现过，因此**第一轮成功不是本轮项目验收**。

## 五、本次实现：只读生命周期模型（独立实验分支）

分支 `exp/m02s-oem-exit-audit-v0127-20261115` 基于 v0.1.26；不替换任何既有安装包。

- 新增 `M02sSessionEvidence.java`：独立记录原始文件条目数与过滤后图片数、手机释放Wi-Fi的成功轮次、被动 `isImport` 状态、释放后0标志的观察、释放后实体快门活动。它不导出任何BLE包，也不把单项状态当作设备恢复证明。只有第二张**不同原图**经现有逻辑成功HTTP200/JPEG下载并释放网络后，才记为已观察到第二轮成功。
- 在 `M02sApStarter` 向上报告0x45被动 `isImport`，对已释放手机连接后的0标志保留单独观测结果；不存在任何自动AP重连或0x44跟随触发。
- 在 `MainActivity` 中输出状态：“手机网络已释放，但镜端退出未确认”；记录原始条目数和过滤后可读图片数，避免把2个概念混为一谈。
- **从此分支实际可触达的BLE协议代码中移除所有 `0x44` 发送与授权实现**，原 `0x44` 实验仅完整保留在旧 v0.1.25/v0.1.26 分支；不会自动更改任何用户安装。只保留验证过的 `0x39/0x30` AP开启与用户手动只读 `0x64` 查询。
- 保留 v0.1.26 的一次性 vivo Wi-Fi Pattern/新鲜BSSID 配对、实体拍照与手动导入、原厂只读GET、同名防重复、JPEG解析到RAM、安全取消逻辑。
- 单元测试覆盖原厂计数分支（纯模拟、不发送BLE）、释放Wi-Fi不等于设备退出、先前的0标志不得被当作释放后的新证据、第二轮必须有不同JPEG完成等；并检查所有0x44候选帧不能通过本审计协议白名单。

## 六、仍缺少什么，如何定义最终验收

**当前没有完成 M02S 连续第二轮修复。** 原厂 APK 只能证明应用端“该发什么、怎么清理”，不能证明具体M02S固件收到每条帧后清理了什么。要声称根因确证，还需要同一固件原厂App导入后蓝牙GATT回执/固件状态（或供应商官方协议说明），和蓝星对应行为的可靠对照。

在拿到这类证据前，不增加拍照时Wi-Fi常连、定时盲重试，也不催用户重复已失败的真机实验。不把本分支 CI 构建通过当作第二轮已修复。

