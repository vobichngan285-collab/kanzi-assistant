# M02S v0.1.16 — 实体拍照后极速 Wi-Fi 高清取图（独立实验）

## 这次只解决速度：不再先连Wi-Fi等眼镜拍照
用户在v0.1.15真机看到：每次开热点后Android扫描白等12秒，然后精确连接用2–5秒。已实测原厂高清3200×2400 JPEG GET本身仅475–758ms。眼镜在Wi-Fi模式下实体快门无正常反应；因此v0.1.16改变顺序：**先连接BLE并待机，不开Wi-Fi；眼镜在正常拍照模式下手动实体按键；收到有效候选通知后请求一次原厂Wi-Fi AP，按蓝牙校验SSID立刻请求Android连接；从原厂HTTP目录选择唯一最新候选，两次稳定后仅GET原始高清图片到手机RAM。**

### 具体入口
- **「准备蓝牙待机」**：扫描唯一M02S、GATT LE1M连接、AA14 CCCD通知订阅后停止动作并待机；不发送任何Wi-Fi或拍照指令。BLE待机连接的首次耗时不计入拍照后传输。
- **实体快门**：眼镜正常模式拍照。MIT开源E09的 `ACTION_SYNC/0x45` payload[0] 标记 `isTakePhoto`，本实验只从校验正确的AA14包解析 **0→1** 候选变化，350ms后尝试启动取图。**该上游字段在M02S固件上未经真机证明会对应实体按键；软件日志明确显示“候选”而不伪称确认。**
- **「我已经拍照，立即取高清原图」**：若M02S没有任何可用0x45通知，不要求继续猜协议；在按实体快门之后点本按钮，**立即**发送已真机验证的原厂BLE `0x39/0x30` 开AP命令，完成同一条最短Wi-Fi传输路径。这是验证高速传图的确定性后备入口。
- **Wi-Fi极速连接**：收到AA14严格校验 `0x25` 实际SSID，等待AA13 GATT写入status0，如已连接完全相同SSID+私有IPv4网关则复用；否则 **直接一次 `ConnectivityManager.requestNetwork(WifiNetworkSpecifier exact SSID + OEM WPA2)`**。**没有12秒扫描、没有1.8秒等待、没有多轮补救重试或连接其他网络**。成功必须Android `Network.onAvailable`、真实网关，并经 `GET /app/getfilelist` 的HTTP200/原厂JSON验证；Android是否成功连接、是否弹授权完全由真机决定。
- **取图**：只读 `GET /app/getfilelist`，依照已有 `M02sPhotoPolicy.uniqueLatestOrNull` 判断唯一可排序的最新照片，两次相隔约350ms读取到相同文件/大小/时间并且与本App上次下载的不是同一张，才GET 原始高分辨率图片；解码/长度/文件路径限制沿用已验证代码。**首次启动没有拍照前文件列表：最新只代表“最可能的候选”，无法证明该照片确实来自刚才的实体快门；若相同时间戳、排序不唯一、没有合格图片，停止而不猜。**文件名/照片内容/SSID/蓝牙地址/密码都不写日志。单轮最多39秒超时，禁止自动重发。完成后解除手机本轮Wi-Fi请求，BLE仍保持待机；**这并不证明眼镜固件已退出Wi-Fi导入模式**。
- 不再连接后每450ms无休止查询目录；只在拍照后的传输会话中查询两次稳定列表；不发送BLE `0x22` 拍照/ `0x44` 结束或删除 /OTA命令，不清除眼镜文件，不保存相册，不上传公网或云AI。**本实验“上传照片”指原始JPEG由眼镜传到手机RAM预览，后续云OCR/TTS需要另行明确配置。**

## 最短的真机使用步骤
1. 关HeyCyan/别的眼镜控制APP，打开蓝牙及WLAN，授予权限，打开v0.1.16，点击一次**「准备蓝牙待机」**，观察到 **「BLE已待机」**。
2. 在眼镜正常拍照状态**按实体快门一次**。如果app日志打印了0x45标志并启动取图，等Android系统Wi-Fi授权提示，允许本次精确眼镜网络。**如果没自动反应，直接点击一次「我已经拍照，立即取高清原图」**，无需再拍一张、无需等12秒。
3. 观察“AA13开AP回执/AA14 SSID/直连Wi-Fi请求/Network onAvailable/网关/原厂照片列表/高清JPEG下载”每个阶段耗时和原图尺寸；如果失败，复制匿名日志。不要反复点击取图或触发旧远程拍照。
4. 下载后app只释放手机WifiNetworkSpecifier请求，保留BLE；眼镜恢复拍照模式**仍需真机验证**。如果不能继续实体拍照，要进一步研究原厂**非删除**退出命令，但本版绝不未经验证自动发可能伤及用户照片的0x44指令。
5. 重要：**这不是保证3秒取图**。既然不确定0x45在M02S上有效，自动触发与用户快门时刻目前不可承诺；Android初次Wi-Fi关联也可能耗数秒。实验日志的“取图触发到JPEG解码”不等于用户按快门到成功的全部时延。

## 复用而不是重造
- 本仓库经过用户真实Android16测试的 `M02sApWire`、`M02sApStarter`（唯一BLE AA13/AA14开启AP）、`M02sPhotoTransport`、`M02sPhotoListReader`、`M02sPhotoPolicy`、`M02sFreshPhotoPolicy`、`ProbePolicy` 沿用。
- 开源 MIT [sctg-development/ai-smart-glasses-e09-reverse](https://github.com/sctg-development/ai-smart-glasses-e09-reverse) 的 `Sources/e09_reverse/AdditionalValidations.swift`（decodeActionSyncFlags的isTakePhoto字段）及 `Constants.swift` / `WifiMediaManager.swift`，出处与MIT许可见 `E09_SOURCE_LICENSE.md`。**这里不复制其任何未经真机验证的触发成功结论**。
- 独立分支 `exp/m02s-shot-wifi-fastpath-v0116-20261114`，仅修改该分支的 `experiments/m02s-wifi-only-lab`。独立Android包名 `com.vobichngan.bluestar.m02sshotfastlab`、versionCode17、versionName0.1.16。v0.1.12～v0.1.15和正式蓝星向导RC25、Windows OCR、HeyCyan全部保持不动。
