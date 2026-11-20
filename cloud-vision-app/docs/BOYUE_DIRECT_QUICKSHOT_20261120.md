# 蓝星向导 + 保益悦听：音量上键双击打开后，自动单次 C110+ 高清拍照识别 v1.0.59

## 已通过用户 vivo Android16 真机验证的基线
- 保益悦听开启读屏时，蓝星 `C110VolumeShortcut.dispatchKeyEvent` 收不到音量上下硬件事件（按下/抬起均0）；关闭保益悦听后，蓝星原逻辑能收到上下键与两次成功触发、最近间隔82ms。这证实**不要再改蓝星双击识别速度、更不能新建竞争性的无障碍按键过滤服务**。
- 用户已在保益悦听 → 自定义快捷键功能 → 双击音量上键 → 打开应用 → 选择「蓝星向导 快捷拍照实验」，真机证实**双击音量上键能够自动打开蓝星主页**；目前缺少“打开后执行单次拍照”的差异化启动入口。
- 真实目标：保益持续读屏/捕获按键，蓝星只负责C110单次原厂1001 JPEG→共享云OCR/TTS广荣微软语音，**普通启动仍然只进主页**、不新增拍照诊断页面，也不抢实体音量键。

## 为什么同时准备两个入口（不是两套拍照逻辑）
Android `MAIN/LAUNCHER` 可以暴露多个图标，但保益的「应用选择」是按每个**包**列还是按每个**启动 Activity** 列，尚无用户实测。不能假定它会显示同一包的第二个图标。为让本次实验实际可测，提供：
1. **蓝星向导 主APP v1.0.59**（保留所有 C110/云OCR/广荣TTS/统一设置与现有蓝色界面），仍有默认「蓝星向导」普通启动；额外有同包 **「蓝星一键拍照」** 专用Launcher图标 `QuickShotEntryActivity`。若保益应用选择器列出这个单独入口，则只需主APP一个安装包。
2. **极小的独立「蓝星一键拍照」触发器APP** `boyuebridge`，仅一个Activity（无相机、照片、OCR、语音、权限、密钥），给只按包名列出的保益「打开应用」选择器用。它被保益唤醒到前台后明确启动上述主 APP 的 `HomeActivity`，发送仅这次的 `com.vobich.bluestar.cloudprobe.action.QUICK_SHOT`；随后立即退出，不给用户增加永久新界面。若已有同包专用入口可选，不安装这第二个小APP即可。
**核心仅一套 C110QuickShotController**，不会开发新的相机实现，也不跨APP分享API Key；用户只在蓝星主APP设置API Key和TTS，触发器没有设置。
 
## 修改范围/协议
- 基线原仓 `vobichngan285-collab/kanzi-assistant` 已通过CI发布的 `exp/c110plus-tts-engine-sync-20261120` HEAD `d0e404fb721ccee2a6c2895e9438dfa4018ae4bd`，新分支 `exp/boyue-shortcut-photo-entry-20261120`，主APP独立版本 `1.0.59-boyue-direct-shot` / versionCode68 / appId `com.vobich.bluestar.c110plusquick159`；触发器独立包 `com.vobich.bluestar.boyuebridge`，小版本1.0.59。都不覆盖此前任何应用。
- `HomeActivity` 仅在收到**显式指定的** `ACTION_QUICK_SHOT` Intent 时，等待自己真实 `onResume` 后按一次现成 `quickPhoto()`，内部走**现成** `C110QuickShotController.trigger()`；立刻消费 Intent 防止权限/WiFi选择器返回、其他 onResume 或配置改变导致重复拍照。普通 `ACTION_MAIN/LAUNCHER` 无动作，用户手动点主页「快捷拍照」仍按一次只拍一次。`onNewIntent` 可处理已运行时的专用快捷启动。
- `QuickShotEntryActivity` 是额外的同APK启动图标，**无拍照页面**，只在前台发送一次ACTION至Home后结束；主manifest保持两个标准LAUNCHER入口。
- `boyuebridge.LaunchActivity` 作为单独安装包仅有LAUNCHER，真正焦点到前台才显式启动主APP，随后自动结束；仅显示转瞬间的「正在执行蓝星一键拍照」，不使用辅助服务或后台启动，也不需要API Key；无法找到主APP时只提示要先安装主APP。
- 未修改 `C110WifiCamera` OEM命令、`C110FrameReader` TTS引擎（含广荣grtts）、云OCR模型、视频LibVLC 3.6.5缓存150ms/竖屏ROI、设备灯光/静音、手机Camera2/USB预览及设置页；也不改变旧音量键诊断算法。
- 系统WiFi授予权限可能弹出，按先前逻辑完成连接等待后执行**同一回**用户触发，不自动重试。
 
## 实机安装与测试顺序
1. 安装主包 `BlueStar_Guide_Boyue_Direct_QuickShot_1.0.59.apk`，设置本实验包的百炼 API Key，语音选广荣微软并试听；打开普通「蓝星向导」，验证**绝不会自动拍照**，手动点击第一行仍一张+C110+OCR/TTS。
2. 保益悦听维持开启，打开快捷键「双击音量上 → 打开应用」的选择器；**如果出现单独「蓝星一键拍照」入口**，直接绑定并测试。若只出现「蓝星向导」且选中后只打开主页，则**再安装**小包 `BlueStar_Boyue_QuickShot_Trigger_1.0.59.apk`，在保益「打开应用」列表里改选这个单独的「蓝星一键拍照」APP，测试双击。注意不要误选普通「蓝星向导」。
3. 正常期望：双击音量上 → 保益正常读屏不退出 → 自动进入蓝星主主页但**立即执行一回 C110+ 1001 高清原图拍照、云OCR和自选TTS朗读**；主页只显示一行进度，没有C110诊断页或拍照实验页；再次打开普通蓝星图标不触发快门。
4. 验证重复：双击只执行一次快门；从WiFi许可页回来不重复；相机WiFi未连接时正常请求一次接入（不能当作拍照成功）。
5. 当前CI可静态核验Launcher/Manifest/Intent唯一消费/原有C110和TTS逻辑、两个APK都能编译且身份/签名/完整性正确；**不能凭CI宣称保益列表肯定出现第二图标或Android16真的能从触发器自动拍照**。两种入口都待用户真机验证。
