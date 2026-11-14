# M02S-3584｜手按眼镜快门 + BLE仅开启AP + Wi-Fi自动取高清原图 v0.1.14

## 本轮为什么修改
用户已在Android系统WLAN页面验证：眼镜**没有独立开启Wi-Fi的实体按键**，现场没有可确认眼镜热点，故 v0.1.13「完全零蓝牙」无法自行建立眼镜AP。新方案最小修正：**仅用一次官方已验证BLE `0x39/0x30` 打开眼镜AP**（含AA14 `0x25` 返回SSID）；**绝不向眼镜发送蓝牙拍照命令**，快门必须由用户亲自按。随后手机专属Wi-Fi Network连续监听照片列表，检测新照片并自动下载高清原图。

## 直接复用成功的代码/开源依据
- 同仓库 v0.1.10 的真实Android16成功路线：BLE LE1M → AA14 CCCD通知 → AA13向眼镜发 **唯一一次 `AB 55 00 03 39 30 69`** → 校验AA14 0x25 的SSID → 重新扫描到目标 AP 新广播 → 等1.8秒 → Android `WifiNetworkSpecifier` 精确SSID+已验证的OEM WPA2密钥（仅内存）→ 实际 `Network.onAvailable`/私有网关 → 原厂 `GET /app/getfilelist` 和只读原图GET。此前实测 **3200×2400** 图片、HTTP下载单次475～758ms。**本次唯一改变是移除所有BLE快门控制**，不再先遥控拍照再连接AP。
- 开源 `sctg-development/ai-smart-glasses-e09-reverse`（MIT / Ronan Le Meillat / SCTG Development，详见 `E09_SOURCE_LICENSE.md`）核对 `Constants.swift` 的 `OPEN_WIFI 0x39/0x30`、AA14 `0x25`、`WifiMediaManager.swift` 的照片列表及 GET。旧版 v0.1.12 的 E09 `0x22/0x31` AA15 直传已真机成功320×180/3061ms，但这条小图路线不用于此次高清实验。
- 旧版 v0.1.13 已验证可编译的 `M02sPhotoTransport`、`M02sPhotoListReader`、`M02sFreshPhotoPolicy`、`M02sPhotoPolicy`、`ProbePolicy` 和自带的JUnit全部保留。复用旧版 `M02sWifiPolicy`、`M02sApReadinessPolicy`、`M02sApScanPolicy` 及其JUnit；新写的 `M02sApWire` 只允许 `0x39/0x30`，禁止 BLE `0x22` 遥控拍照、0x44清除媒体、OTA/P2P探测。
- **性能实际界限**：蓝牙+Wi-Fi在开始监测前一次性连接，连接耗时**不计算**在之后的手动拍照取图中。连接后列表每约450ms更新，要求新文件相对于拍照前基线**连续两次稳定**（避免半成品照片），然后直接只读GET，内存解码。能否保持AP并实体拍照**仍待真机验证**；若眼镜固件把拍照与Wi-Fi传输互斥，App无法绕过。照片下载和识别总共3秒只是目标。

## v0.1.14 实验操作
1. 关闭HeyCyan和其它眼镜控制软件；确认手机蓝牙/WLAN开启，应用获得附近设备/蓝牙/位置许可。无需在系统Wi-Fi设置里猜SSID或密码。
2. 打开 **独立v0.1.14安装包**，点**「连接M02S并开启Wi-Fi、自动等待照片」**一次。程序只扫描名称前缀M02S的唯一眼镜，订阅AA14，发送一次 `0x39/0x30`；如果发现多台/无AA14/无0x25/没有本次新Wi-Fi广播/系统连接失败，会明确提示并停止，不向邻居Wi-Fi发请求。
3. Android16若弹出连接Wi-Fi系统提示，允许本次眼镜热点连接。等应用显示**「旧照片基线已建立」以及「现在请按眼镜的实体拍照键」**，此时才按眼镜实体快门一次。手机**不会自动触发快门**。
4. 如果快门正常：观察日志是否出现“检测到旧基线以外新照片→连续两次大小稳定→高清原图下载成功”，看图像尺寸及 **HTTP下载**、**首次新文件发现到解码**耗时。完成一次后再按第二次、第三次实体快门，最多3张/120秒。
5. 如果Wi-Fi连接后实体快门**无拍照音/不能生成文件**，立刻反馈；这是此次最重要的固件互斥性验证，**不要盲目反复拍或用蓝牙遥控拍照替代**。如果眼镜AP连接/HTTP服务不可靠，复制匿名日志定位具体阶段。
6. 停止按钮可停止观察并释放手机Wi-Fi Network/GATT；固件是否关闭眼镜AP由眼镜决定。**不发可能删除媒体的0x44/0x30/0x00**，也不发未知退出指令，不保存照片，不上传云端，不更改眼镜文件。

## 版本隔离和真实结论
- 在原仓库 `vobichngan285-collab/kanzi-assistant` 的独立分支 `exp/m02s-ble-ap-manual-shutter-autofetch-v0114-20261114` 中继续沿用 v0.1.13 目录 `experiments/m02s-wifi-only-lab`（旧 v0.1.13 分支和 Release 不修改）。
- 独立应用 ID `com.vobichngan.bluestar.m02sbleapmanual`，versionCode15/name0.1.14，与以前各实验及正式 Blue Star Guide RC25 都能共存，Windows OCR、原厂HeyCyan均不动。
- GitHub Actions构建只证明APK编译、单测、签名、权限符合要求，不代表实体快门与AP并行可用，也不代表最终3秒朗读达成。**不加入OCR/TTS，直到连续3张高清原图实测成功**。
