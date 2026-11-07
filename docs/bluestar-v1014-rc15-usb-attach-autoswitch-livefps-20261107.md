# 蓝星向导 v1.0.14 RC15 — USB 自动插拔切换、系统接入启动和真实预览更新诊断

2026-11-07，用户授权“可以开始”，基于原仓库已发布 RC14 分支 HEAD `29cad6f32c7ffe45eacfd752fa3c8d0fc3b99e53` 开始下一轮，不创建新仓库、不触碰离线 OCR。
本版独立应用包 `com.vobich.bluestar.rc15` / `versionCode 24` / `1.0.14-rc15`，不覆盖 RC14。

## 三项自动行为

1. USB 摄像头**拔出**，自动切换至手机后置 Camera2；如果当前是用户手动选择的手机摄像头，则不重复切换。非选中 UVC 的拔出不应干扰当前画面。
2. USB 视频设备插入并经已有 AUSBC 权限连接后，当前 APP 前台且已开启自动切换时，自动切换到已连接 USB。用户手动选手机镜头后暂不抢回 USB，等待真正拔出再插入；对非 UVC 设备不切换，也不因插拔额外拍照、上传 AI、朗读或扣费。
3. 用户可在摄像头设置选择「插入 USB 时自动启动」。Android 系统 USB host `USB_DEVICE_ATTACHED` 意图（视频接口 class 14 + 已知 EBA4:6579）通过动态启用的 **disabled-by-default activity-alias** 交给系统设备选择/默认应用提示，绝不后台偷偷启动。不保证锁屏/所有 vivo 权限配置都能自动前台弹出；需用户实际系统授权。首次安装需至少运行应用一次，以启用偏好的系统别名。

## 防干扰和监测

- 只在已存在的 pinned AUSBC `CameraActivity.kt` attach / detach / permission-success 回调加轻量 hook；新增 `shouldAutoOpenUsbCameraForApp` 防止用户在手机模式时幕后 UVC 仍被打开；不注册第二 USB 广播、不改 Native UVC FPS、不改 RC13 的方向和 JPEG 变换。
- 用户主动从 USB 切手机时关闭旧 UVC，停止 USB 监测；切 USB 时开启监测并允许安全的正常打开；首次 attach 的真正 open 由 AUSBC 原调用执行，禁止在同一次连接上双重 open。
- 新增纯 Java `UsbAutoSwitchPolicy` 和测试，涵盖插/拔、非视频、后台、重复事件、手动覆盖、只允许 USB 状态开启原生流；`UsbRollingFps` 和测试覆盖 10 秒数据窗口、0FPS 停顿、首个采样0基线不计、切源后旧统计清空。
- 修复 RC14 `switchCameraSource(false)` 不启动 USB FPS 采样、旧 USB sample 在手机模式错误显示。显示最近一次 2秒采样的 TextureView 新缓冲更新次数/秒、最近10秒均值和最低值、USB 首 NV21 帧等待，明确不是 LCD 真正上屏 FPS 或必然不同的视频帧数。截图中出现 `usb_resume` 0/starting 尚不能证明稳定FPS为零。
- 实测前后台复现仍需收集持续低FPS、raw/view是否一起掉以及 UVC open/close 时间，**不以每次回前台强制自动重连代替故障定位**。

## 验收边界

- 静态范围/隐私审计、native pinned 源码唯一锚点注入、JVM 四种方向32组合、自动策略、滚动FPS、任务和朗读回归、Android arm64 Gradle、APK包名/启动入口/音效/图标检查，全部必须实际通过 CI。
- 真机上测试：在手机模式插 UVC 自动切 USB；拔掉恢复手机后置；用户手动手机不被重连抢回；在设置关闭某项开关后不发生相应行为；Android 16/vivo USB 默认应用选择/权限弹窗和应用前台启动；USB平稳期 vs 低FPS时复制诊断，检查10秒均值和最低更新次数、首帧耗时；USB已修好的90°/左右校正/朗读不回退。
- 本审计文档在 CI 前写入，**不可提前声称构建/安装/真机通过**。
