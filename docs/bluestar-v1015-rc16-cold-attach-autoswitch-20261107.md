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


## 用户追加需求：简单直接的画面和 USB 61 FPS 优先（源代码修复，待 CI/真机）

- 本轮用户在 RC16 发布前又明确要求“把30改成61起改”“我们的原则就是简单粗暴，越简单越好；看看还有哪些画面复杂，都改成最直接”。对全套图像路径只读核对：USB 已为 `CameraRequest.RenderMode.NORMAL` + MJPEG + `AspectRatioTextureView` 原生 Texture 直显；手机 Camera2 也是 TextureView 直显，**无多余GL/AI视频渲染链可以删**；RC13 JPEG 方向变换是保持 OCR 准确性的必要处理，不能删除。所谓“简单”是减少重复 copy/变换，不能删除本来有用的功能或无故改功能已稳定的模块。
- 确认 pinned AUSBC 3.6.0 `CameraUVC.kt` `setPreviewSize(width,height,MIN_FS=1,MAX_FPS=61,MJPEG)` 只是允许1..61的范围，并不要求最高帧率；真机 RC15 数据 TextureView 29.9~30.3更新/秒。现在**优先在同一1920x1080 MJPEG格式请求 60..61 FPS**，若 UVC 接口不支持此档，原格式安全回退到原1..61（通常可保持30），若 MJPEG 也不行才维持已有的 YUYV 兼容回退。**不能声称设备将确定输出61实际独立画面/秒**。
- 发现底层 `UVCPreview.cpp::setPreviewSize` 原来的变更检查仅比较宽、高、预览格式，不比较 FPS；先请求60..61失败后再请求1..61，如果尺寸格式相同会被忽略并导致后续打开失败。针对这个确凿 bug 同步修改仅在 `min_fps/max_fps` 不同的情况下重新查询 UVC 协商，不动原生 render/capture/close 路径。
- 原 pinned `CameraUVC.kt.frameCallBack` 每次 JNI ByteBuffer→`ByteArray(capacity())` 全新分配且只入队，不再修改；`MainActivity.updateFrame()` 仍为 USB 每帧 `Arrays.copyOf(bytes,bytes.length)` 重复制约3.11MB。RC16 直接保留每次新分配的原 ByteArray（电话 Camera2 也有自有 byte[]），移除此冗余拷贝。USB原始NV21回调仍原生限约5Hz以降低CPU；不是预览 60 FPS 限制。
- 增加 CI 校验原生高帧率优先/相同尺寸重协商/兼容回退/没有冗余全帧复制，保留 RC13 方向32组测试、冷启动 USB 自动切换、10秒 FPS诊断、USB亮度等画质硬件控制、音效、云端 AI、ASR/TTS 和图标检查。真实 USB 摄像头最终是否支持60/61fps需要验收新包 10秒 FPS 当前/均值/最低、开关往返与OCR；**不要在30FPS模式继续盲目叠加复杂补帧**。


## 最终 CI / APK 审计：RC16 USB自动切换修复 + 61 FPS 优先与直接画面（2026-11-07）

- **最终实际 Android 编译源码 SHA**：`e06d39b40f6f90d30752a9d86900ec6fb24c1e03`，原仓库RC16分支 `fix/bluestar-115-cold-usb-auto-select-20261107`；构建 GitHub Actions **[42358079498](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42358079498)** = `completed/success`，原始 workflow artifact **13567843898** `blue-star-guide-1-0-15-rc16-arm64`。
- **经真实 CI 日志逐项核查**：安全审计 `DUAL_CAMERA_CLOUD_AI_NO_LOCAL_OCR_AUDIT=PASS`、原 RC13 32组方向与 JPEG 数据逐字节测试 `RC13_USB_PREVIEW_JPEG_32_COMBINATIONS_AND_90_CORRECTION=PASS 32`、RC14 USB健康/画质参数测试 `PASS`、RC15 自动插拔手动覆盖策略及10秒滑动FPS回归 `PASS`、CloudOptimization 和 TaskOrder 回归 `PASS`、pinned UVC native补丁 `BSG_USB_AUTOSWITCH_115=PASS`、`BSG_UVC_FPS_61_FIRST=PASS`、`BSG_UVC_FPS_61_REPROBE=PASS`，完整 Gradle+JNI ARM64 **`BUILD SUCCESSFUL in 1m 48s`**，图标与音效/包信息/原生 arm64 libUVCCamera 等验证通过。
- **完整问题与修正历史**：最初 RC16 自动切换修复 commit `a755e33196c05119a39c880b59d4ea5cdd12dc74` 已曾通过 CI **42357565129**，但随后用户提出“30改成61”及直接画面优化，因此没有发布旧产物。首次增加高FPS补丁的 Actions **42358044054** 因原生UVCPreview.cpp 代码是tab缩进而补丁误用四个空格、被 fail-closed 源码锚点检查拦截，未进入安卓编译；修正精确tab锚点后用最新 SHA **e06d39b...** 全量构建成功，以最终 **42358079498** 为准。构建中原生FPS目标为优先 **60..61**，并不是对外虚报“61fps”，失败则原 MJPEG **1..61**回退及旧 YUYV兼容回退。
- **发布工作流** **[42358329216](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42358329216)** = `completed/success`，先核验原CI `conclusion=success`、精确 `head_sha`、下载原 artifact，校验 `sha256sum -c SHA256SUMS.txt`、APK ZIP CRC、原 arm64 JNI .so、图标、金属咔叮和语音嗖声音效、manifest `com.vobich.bluestar.rc16` / versionCode25 / versionName `1.0.15-rc16`、未夹带本地OCR模型。**完全不重新编译、不重签、不替换原CI APK**。
- **真实 Release** tag `blue-star-guide-cloud-rc16-20261107`，`draft=false`、`prerelease=true`、asset `state=uploaded`；APK `BlueStar_Guide_1.0.15_RC16_arm64.apk` **8,601,318 bytes**，SHA256 **`47c8441266c0ddee7322432e7b07b575ab85acc54e525832cdb68a3fece0d77a`**。
- **手机浏览器直接下载**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc16-20261107/BlueStar_Guide_1.0.15_RC16_arm64.apk
- **必须真机验证**：用户 vivo X200 Pro + USB 相机插入系统唤起APP是否自动从手机后置切外置、拔出能否回手机，手动选手机是否不被意外抢占；USB正常运行10秒后读取 `TextureView` 更新均值/最低/当前是否从30向60提高；若仍30可能摄像头的1920×1080 UVC模式只有30或系统实际协商失败后落到回退，不能仅凭Android静态编译结果说已60/61，**不使用插帧、软件帧重复或多一层预览渲染伪造61 FPS**。旧 RC13 旋转与实际发送AI JPEG须对照一次，确认已修复的文字识别方向未退化；作为独立安装包需重填API Key/任务等。
