# v1.0.62：设置→预览不再跳回主页；C110运动相机设置极简化

## 用户明确验收的基线
- **v1.0.61 广荣微软语音已由用户在 vivo 真机确认恢复正常**，不再修改 C110FrameReader / QuickShot TTS / MainActivity语音逻辑，不再反复试网络。
- 用户反馈：从「设置」底栏点「预览」反而跳到首页；「相机 → C110+运动相机」含不需要的「照片分辨率/OCR准确率实验」、大量啰嗦说明，还有旧C110实时预览中的实验/删图安全提示子菜单。希望干净的设置界面。
- v1.0.61分支 `exp/c110-tts-regression-20261120` HEAD `e8c5430a5846c58958eec4ef0ae567e8219b2d51`，本版在唯一原仓创建隔离分支 `exp/preview-navigation-clean-camera-20261120`。

## 查到的具体导航bug
- `HomeActivity` 打开「设置」时启动 `MainActivity`，携带 `bluestar_launch_settings=true`。`MainActivity.settingsOnly` 导致预览画面隐藏，并在 `onResume` 只展示设置。
- 设置页底栏「预览」调用 `MainActivity.openPreviewFromUnifiedSettings()`，旧代码又 `startActivity(new Intent(this,MainActivity.class))` 同类启动。**Manifest的MainActivity设为 `singleTop`**，因此当前设置Activity位于栈顶时，Android可能通过 `onNewIntent` 复用它；旧代码随后 `finish()` 关闭当前设置Activity，用户眼前恢复的是其下方 `HomeActivity`。这一定位是确定的代码逻辑冲突。
- 修复是**复用当前已有 MainActivity**：点击底部「预览」将 `settingsOnly=false`、删除 Intent 中旧的设置标记、恢复黑色相机预览背景、显示之前创建的 `previewContainer`、关闭设置叠层，然后直接调用 `resumeForegroundPreview()`。复用 `onResume` 原样的 USB/手机 Camera2 权限、设备监测/打开流程；**不启动第二个MainActivity、不再finish当前Activity**，并且进入预览后点「设置」仍能原位打开统一设置。普通从主页进预览和返回后台后再进入，都调用同一预览恢复函数；不换 UVC/Camera2/LibVLC/OCR/朗读组件。
- **保持安全**：现有 UVC 自动连接/USB权限、手机摄像头、相机Wi-Fi、普通预览的点击抓帧等函数未改；`MainActivity` 原 `singleTop` manifest 不改以防 USB设备打开流程回归。

## 精简设置/移除实验
- `BlueStarSettingsScreen`「相机 → C110+运动相机」只保留：连接 Wi-Fi、机身提示音关/开、指示灯关/开/15/30/60秒延迟/自定义秒数。只显示一条简短**真实连接或命令状态**，删除实验分组、照片分辨率/OCR准确率测试入口、所有C110重复说明。API Key/视觉模型/文字OCR仍在顶层「云端AI」，不另设。
- C110 实时预览顶部「相机」和「☰」按钮不再弹出「C110相机/OCR实验→照片实验/删图安全提示/机身设置」的冗余二级选择菜单，而是直接打开已有 `C110DeviceControls.showSettings()`，保留实际9011/9038 控制（指令、身份校验均未改）。移除 `C110DeviceControls` 菜单最后一个「说明」空操作项。
- `MainActivity` 移除旧实验导航方法 `openC110PhotoLabFromUnifiedSettings()`；Manifest取消 `C110ResolutionLabActivity` 注册，不可由APP跳转访问。以前的实验源码仍保留Git历史和原分支，不删仓库、不改真实OEM相机实现、不添加新预览。
- 删除统一设置中其余不必要的**固定教学长段落**（USB画质、云模型、OCR方式和模型、运行诊断等页面）；保留可操作选项、真实状态、错误反馈、消耗Token的确认弹窗、统计数值和必要的错误提示。
- 保留 **v1.0.61已恢复正常的广荣 TTS**，保留保益悦听 `com.vobich.bluestar.boyuebridge` 的独立小启动器和唯一正常蓝星主图标，主APP包名始终 `com.vobich.bluestar.c110plusquick159`，本版versionCode71/versionName `1.0.62-preview-clean-camera`。C110真实1001单张高清JPEG/云OCR、150ms VLC/竖屏ROI及9011/9038依旧未改。

## 实机检查
1. 正常打开「蓝星向导」→「设置」→**底栏「预览」**应就地进入手机/USB相机画面，不会回到主页；再按「设置」也能往返，底栏「主页」才回主页。
2. 设置→相机→C110+仅显示实际机身选项与一条状态，无照片实验/删图警告、没有长段说明。
3. 从主页选C110实时预览，顶部「相机」直接进入声音/灯光选择，不显示中间的实验菜单；仍能看到150ms的视频。
4. 保益悦听双击音量上调用原独立小启动器，C110单次1001→OCR→**正常广荣微软语音朗读**；设置→语音试听也仍正常。
5. Android构建与静态验证成功**不等于真机相机渲染或保益/语音均验收**；保留旧版APK可回退。
