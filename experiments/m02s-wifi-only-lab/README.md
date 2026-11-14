# M02S-3584 Android16｜纯Wi-Fi + 眼镜实体快门 → 自动下载高清原图 v0.1.13

## 目标及边界
用户要求 **不涉及任何蓝牙操控**：由人按眼镜实体快门，App只经已连接的眼镜Wi-Fi，按原厂只读HTTP接口 `GET /app/getfilelist` 发现新照片，再 `GET /<validated-image-path>` 下载3200×2400级别的原图到RAM/手机预览。此次不接OCR/TTS、不发OTA、不删除照片、不上传云端、不保存相册。

**重要物理限制**：从已有实机日志，原来的眼镜 AP 通常是经 BLE `0x39/0x30` 启动，然后 Android 才找到SSID、用原厂密码连接。已查阅的公开开源 `sctg-development/ai-smart-glasses-e09-reverse/Sources/e09_reverse/WifiMediaManager.swift` 同样先经 BLE 打开 AP，并无「M02S 纯Wi-Fi独立开启AP」证明。因此这个实验故意不替用户猜想Wi-Fi开机方式，也不伪装能开眼镜热点。用户需在安卓系统Wi-Fi设置里 **自行发现眼镜热点、知道并输入其真实凭证完成系统连接**，再回实验App。若热点没有独立开启方式、且Android里不可见，则结果为 **无法在零蓝牙前提下开始**，不是App可以通过代码消除的眼镜固件限制。

## 实现（不走Bluetooth）
- UI有「打开系统Wi-Fi设置」「输入眼镜实际SSID」「验证当前眼镜Wi-Fi」「开始自动取图」「停止」「复制匿名诊断」。**只从现有 `ConnectivityManager.getAllNetworks()` 遍历Wi-Fi Network，不扫描周边热点/不猜测SSID或BSSID/不发起Wi-Fi设备连接**；要求 Android 有定位/附近Wi-Fi授权、系统位置服务已开启。精确SSID匹配，并由 Android 已连接网络的 `LinkProperties` 找到 **192.168.x.x私有网关**，用该Network `openConnection` 验证HTTP200、JSON `result=0`及 `info`字段。Android的默认手机流量网络不能代替已连接眼镜Wi-Fi。SSID/文件名、原图内容从不记日志、不写磁盘。
- 启动监视时先读取完整原厂照片列表作为 **拍前基线**，屏幕明确提示“现在按眼镜实体拍照键”。然后按约450ms轮询列表，靠 **新文件名** 区分旧图；新照片需出现在原基线之外，且连续两次照片名+大小+创建时间一致，确保文件写入稳定。若新文件多个且无可靠时间戳，不随便选择，提示不确定。抓取后将成功图片加入已知基线，可连续检测下一张；最多120秒或完成3张，要求仅下载本机新图，不误读旧图。
- 只对从可信原厂文件列表取得的经过安全校验的相对图片路径进行 `GET`；拒绝超32MB、跨地址跳转、非法路径、图片格式与扩展名不符、图片解码异常/过大；完成后仅在App内存显示原始分辨率和耗时。连接丢失立即停止，不自动切换到其他AP/P2P/蜂窝网络，不写文件、不做删除请求。没有蓝牙权限且源码无BluetoothGatt。
- 日志区分 **HTTP下载耗时**、**发起下载请求到JPEG Bitmap解码耗时**、**手机首次发现新文件到解码耗时**。由于没有手动快门事件的BLE/硬件时间戳，**不能直接测量“按实体快门到原图出现”的全流程耗时**。测试的3秒目标需要结合用户自己计时或外部同步观测，不能用第一次看到新文件的时间冒充按下拍照键的时刻。
- 继承同仓库已实测的 `M02sPhotoTransport`（受限只读GET）、`M02sPhotoListReader`（原厂JSON）、`M02sFreshPhotoPolicy`（保守新旧快照与稳定对比）、`M02sPhotoPolicy`（URL/签名/图片安全）、`ProbePolicy`（私有IPv4验证）；从 E09开源 `WifiMediaManager.swift` 的代码/格式说明核对过方案，见 `E09_SOURCE_LICENSE.md`。

## 真机实验顺序（只需要做这个）
1. 关闭HeyCyan及其它会操作眼镜的APP，眼镜正常开机。**不要在实验APP里连接蓝牙**，因为此App没有蓝牙功能。
2. 先在 **Android系统Wi-Fi设置** 查看眼镜热点能否独立出现/开启，并尝试连接（需要真实热点密码；App不会猜测，也不会使用蓝牙拿密码）。如果根本看不到眼镜热点，请直接反馈「无热点」——纯Wi-Fi方案的前提在此机型上未成立。
3. 回实验App，填写系统显示的眼镜 **完整SSID**，点「验证当前眼镜Wi-Fi和原厂照片接口」；必须显示「已验证」才允许继续。
4. 点「开始自动取图」，等日志明确 **“旧照片基线已建立，现在请按眼镜实体拍照键”**。然后按实体拍照键一次，观察是否快门正常、列表出现新文件、手机是否自动下载原图，以及各阶段真实耗时。
5. 继续再按两次实体快门（前一张下载并显示后再拍），总共3张。收集匿名日志。如果眼镜在Wi-Fi连接中不能拍照，请直接记录实际眼镜反应，停止实验；这提示眼镜的相机/传输状态可能互斥。
6. 初版 **不做OCR/朗读**。只要手动拍照+热点常连+高清新照片自动下载能稳定成功，再接入已有的蓝星云端AI朗读。

## 构建发布及版本隔离
- 独立目录 `experiments/m02s-wifi-only-lab`，独立包 `com.vobichngan.bluestar.m02swifionlylab`、versionCode14/versionName0.1.13。旧 E09 v0.1.12、v0.1.10 原厂AP成功路线、正式蓝星向导RC25、Windows OCR和原厂HeyCyan保持不动。
- CI验证Android SDK36/Gradle构建、JUnit、APK签名/zipalign、AndroidManifest**没有任何BLUETOOTH权限**、Wi-Fi/网络权限范围与独立包名。CI成功不等于眼镜可以零BLE开启AP，更不等于3秒真实拍照后自动取图已经成功。
