# 蓝星向导 v1.0.66：普通预览极简底栏 + C110 拍照成功“叮”音

## 用户真机定位与批准
用户在已安装 v1.0.65 普通手机/USB 预览截图指出旧的顶部：LOGO/蓝星向导、☰、快拍、🎙、变焦标签，以及画面左上“任务：...”标签覆盖取景；底栏右侧仍为“设置”，与已调整的主页不一致。用户明确批准其亲自确认的图片：**取景画面无顶栏和任务标签；底栏三个入口「主页」「预览」「按住AI」**；预览中不再有设置入口，统一设置仍在 Home 竖排。另要求 C110 运动相机拍照完成后恢复本机金属“叮”反馈。

## 具体改动
- `MainActivity.getRootView()` 原顶部控件仍初始化以确保老代码引用/辅助可用，但整个 `title.setVisibility(View.GONE)`；`currentTaskChip.setVisibility(View.GONE)`。不改变 Camera2、AUSBC UVC、画面 tap OCR、双指变焦、C110 模式切换、设置弹层、权限和接口。云结果/录音状态保留 **4.5秒限时提示**，不会成为常驻遮挡。
- 底栏保留原位置与三列和已有主页/预览按钮，原右侧 `cameraEntryButton` 从「⚙ 设置」改「🎙 按住AI」，复用原先**顶部话筒的同一个** `handleVoiceButtonTouch`、`VoicePressState`、`VoiceRecorder`、`CloudAsrClient` 与 `routeSpokenRequest`，不新建第二套云接口、录音/隐私状态。切换手机/USB的无障碍提示从隐藏的settingsButton移到活跃 rootFrame 发送。
- 新的 `CaptureDingFeedback` 预加载**现有** `R.raw.c_metal_clack_ding` 和 **同一个用户开关** `kanzi_settings/shutter_feedback_sound`，同MainActivity的 SoundPool 音效设置；成功收到 C110 真实 1001 JPEG 后只在主线程响一次，本机扬声器提示 **独立于 C110 机身 9011 静音**。样本尚未完成载入时短暂保留一次pending，成功即播；退出时释放资源。
- 在 `C110CameraActivity.finishPhotoFlow` 有真实JPEG时触发，不成功不触发；在 `C110QuickShotController.takeOnce` 的成功JPEG回调处触发，失败/超时/重复等待不触发；包括用户在主页点击“快捷拍照”、独立 C110 取景页面底部点“高清拍照”两条路径。
- `MainActivity.captureC110OneShot` 原先云通畅时会在 `beginCapturedFrame` 内响一次 `signalCapture`，**保持不变**，只在真实C110 JPEG已成功但云密钥/蜂窝不可用时补一次 `signalCapture`；避免在线路径重复响。

## 严格范围与验收
原仓 `vobichngan285-collab/kanzi-assistant`，以已发布v1.0.65 commit `c30c033b9f7ec97a351f479a499c6d4ba4c06d31` 为基线，从 `exp/phone-preview-clean-c110-ding-20261120` 独立分支制作；版本 versionCode75、`1.0.66-clean-preview-c110-ding`、安装包名 `com.vobich.bluestar.c110plusquick159` 不变；不覆盖任何旧Release或保益独立小APK。代码只改 MainActivity、C110CameraActivity、C110QuickShotController、新CaptureDingFeedback和版本号，另新增文档/CI；不动相机底层、C110WifiCamera/C110FrameReader/OEM 1001/3012/3016/3001/2015、150ms LibVLC、云端API/TTS以及任务列表。

CI会逐项检查：布局三按钮/语音触控/顶栏及任务标签隐藏；三个 C110 实际拍照成功入口的本机音效且没有失败音；音效文件、声音开关、释放资源和没有TTS/OEM修改；原任务、语音追问、变焦策略单测、UVC补丁、arm64 Gradle build、APK ZIP/唯一 Launcher/native .so/音效、发布SHA。**真机未验收不能宣称已经听到**，需 vivo X200 Pro 实际操作两种C110快拍与手机/USB预览AI录音。C110 机身提示音无论关闭与否都应按手机声音开关发本机叮音。
