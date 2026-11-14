# M02S 蓝牙连接专项实验 v0.1.1

根据用户 vivo X200 Pro / Android 16 的现场日志，v0.1.0 能稳定发现 `M02S-3584`，但 `connectGatt(this,false,...)` 约 60 秒后收到 `status=1,newState=0`。这个状态只能说明连接失败，**不能据此直接判断认证错误、设备限制或固件故障**。

## 本版改进
- BLE 扫描只保留 M02S/HeyCyan/StarGlasses 候选，记录 `isConnectable`。
- 先停止 BLE 扫描，再选 **TRANSPORT_LE + PHY_LE_1M** 进行独立只读连接，超时 **15秒**。
- 若失败，用户可以**手动**选择 Android 原始兼容连接接口对照，两种均 `autoConnect=false`，不自动多次重试。
- 记录设备 `getType()/getBondState()`、状态回调码/耗时、GATT 服务 UUID，支持取消和释放资源。
- 所有控制均限于系统 BLE 扫描/连接与服务发现；**不写 GATT 特征、不发送眼镜拍照或 Wi-Fi 唤醒命令**。
- 原 Wi-Fi Direct/AP、120秒 HTTP 文件列表只读测试保留，但目前没有眼镜网络连接时，不应依据超时判断眼镜故障。
- 使用**独立新包名** `com.vobichngan.bluestar.m02sgattlab`，与旧独立实验版、正式蓝星向导、HeyCyan 原厂 App 共存。

## 测试步骤
1. 确保眼镜开机，退出 HeyCyan App，开启手机蓝牙和位置。
2. 扫描眼镜 BLE，选 M02S-3584。
3. 先点“连接 LE 1M（15秒）”，观察反馈；失败再点“兼容连接（15秒）”对照一次。
4. 复制诊断日志，重点提供可连接标志、设备类型、配对状态、GATT 回调及耗时、发现的服务列表。
5. 不要重复无目的点击 Wi-Fi 连接保持按钮；只有确认眼镜网络可用后才能做后续 Wi-Fi 优化。

静态检查眼镜官方 APK `70465ff3819258a6218e7871da0a23f8.apk` 的 DEX 中存在 `connectGatt`、`connectGattCompat`、`connectBluetoothGatt`、`onConnectionStateChange` 等名称，但未验证这些方法在 M02S 上使用的具体传参/鉴权。新版只做安全的系统接口对照实验，不声称已经复制官方连接协议。
