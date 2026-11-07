# 蓝星向导 v1.0.15 RC16 — Android USB 自动启动后没有切换外置摄像头

用户 2026-11-07 16:49 匿名日志：16:22 电话后置 `phone_back` 会话内 `usb.intent_attach=1` 连续3次，但没有随后 `usb.selected_source=0`；APP 被 USB 插入正常唤起却继续显示手机镜头。16:49 的 `usb_switch` 会话显示 `usb.opened=1`、NV21 4.4–4.9Hz、TextureView 新缓冲299–303/10，即稳定约**29.9–30.3新缓冲/秒**，近10秒均值29.9、最低29.9，USB首帧88ms。用户还缺失新独立包 API Key: `cloud.key=0 [missing]`，**与 USB切换失败无因果**。

## 源码定位
- RC15 在 `MainActivity.onNewIntent` 遇到系统 `USB_DEVICE_ATTACHED` 仅记录 `usb.intent_attach`，并没有触发摄像头切换。原 `onUsbDeviceConnectedForApp` 需同时 `usbForeground=true`，但 Android 16 的系统 USB chooser 可在 APP `onResume` 之前转发权限已获准的 UVC 回调，导致初始事件被忽略。
- RC15 `onResume` 在手机源时仅调用 `startPhoneCamera`，没有重新检查已在 `UsbManager.getDeviceList()` 的 UVC 设备，造成“已启动APP但没切USB”。日志没有 `device_attached/device_connected` 的情况也会发生：USB TextureView 原本不可见时，上游 listener 注册并不总在系统 USB intent 之前完成。
- 注意：`USB_DEVICE_ATTACHED` 与 UI 切换是两个独立流程，Android 系统是否弹出默认设备授权和直播预览打开仍需 vivo 真机检验；不将系统 intent 当作已连接原生 UVC 摄像头的充分证据。

## 最小范围修正
- 原仓库原 RC15 真实文档+发布分支 `fix/bluestar-114-usb-auto-switch-launch-fps-20261107` HEAD `ee19a5c587a0d1fc439d51b6a1c1cb91dd22e961` 派生 `fix/bluestar-115-cold-usb-auto-select-20261107`。包号 `com.vobich.bluestar.rc16`，versionCode25，versionName `1.0.15-rc16`，独立安装。
- `MainActivity.onNewIntent` 收到系统 USB attach 时真正排队一次带180ms去抖的 `reconcileAttachedUsb`；`onResume` 在手机预览且枚举到真实 USB Video 设备时也做一次；从原 pinned AUSBC `onUsbDeviceAttachedForApp` 时再轻量排队，解决事件发生顺序差异。
- reconcile 再次从 `UsbManager.getDeviceList` 枚举**当前真实插入**的 USB Video class/UVC 设备（优先此前选中的摄像头和 EBA4:6579），检查自动切换已开启、APP前台、当前手机源、没有手动覆盖；满足才切换，保持原 AUSBC onConnect/requestPermission/TextureView listener，不注册第二USB接收器、不盲目二次open、不拍照、不发云端。
- 手动选择手机后记录当时所插 USB 设备的 `deviceId`；重复 USB Intent 或返回前台只要它仍物理存在就不抢回；只有该物理设备真正移除或重新接入、deviceId变化才撤销旧覆盖，兼容隐藏 TextureView 不收到 detach 回调的场景。
- **不修改 RC13 32方向/JPEG同向、RC14 UVC画质、RC15 10秒FPS计算和基本拍摄/模型/音效/任务**，30 FPS 不擅自强制提高至60。之前 `cloud.key missing` 是新沙盒未配置密钥；要在新 APK 重新填写。
- CI新增静态实际路径审计和 `UsbAutoSwitchPolicyTest` 冷启动/Intent/前后台/手动覆盖/拔插物理ID场景，保留全部此前回归、Gradle arm64构建和校验。
- 此文写于CI前，**不可把源码改动或 Android 编译当作 vivo 真机验收**。用户可先在 RC15 手动进入设置选 USB 摄像头恢复使用；以RC16实测系统 USB 自动启动并自动切镜头验收。
