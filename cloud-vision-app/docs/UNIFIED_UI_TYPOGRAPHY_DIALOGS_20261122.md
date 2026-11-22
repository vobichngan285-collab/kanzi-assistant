# 蓝星向导 v1.0.75｜全软件统一字体与弹窗风格（2026-11-22）

## 用户要求与视觉基准
用户基于真机截图反复指出：v1.0.74 的系统原生与 AndroidX 弹窗分别为白色、灰色，不同屏幕字体经原始字号×0.5后过细、小且显得奇怪，行高、间距/弹窗高度与文字不协调。用户先确认深蓝色弹窗视觉样图，再要求文字整体再加大10%，最后明确批准“就这样改”，**所有弹窗、主页、所有可见文字都必须统一**，不能只改云端模型的一处。新基准是截图确认的 **约为原始字号0.825（0.75×1.10）**，自然 Android sans-serif 字形、清晰白色文本、略深的深蓝底+淡蓝边框、列表行52dp、居中对齐。
上一版用户严格要求50%，此次明确批准替换为更易读的新效果；不得未经授权改主页五行功能入口、底部3个导航、排版、C110/USB相机调用、网络/密钥、TTS/SSE/AI寻找逻辑。

## 真实全局覆盖（不是单个页面）
1. `BlueStarFontScale.wrap(Context)` 在**四个**唯一 Activity: `HomeActivity`、`MainActivity`、`TaskDesktopActivity`、`C110CameraActivity` 已有 `attachBaseContext` 中统一复制 Android `Configuration.fontScale` 并乘以 **0.825f**。保留用户设备系统字体缩放倍数；不修改 Android系统设置或屏幕系统状态栏/系统权限窗口；只改变 app 内 sp 字号，不改变 dp 位置、点击范围、颜色和控件几何。
2. `styles.xml` 的 `AppTheme` 把 `fontFamily` 切为系统 `sans-serif`，保持原屏幕的色块绘制代码；所有内部 AndroidX `AlertDialog` 使用同一 `BlueStarAppCompatDialogTheme`，所有系统原生 `android.app.AlertDialog` 和 app 内 `Dialog` 使用深蓝色 `BlueStarPlatformDialogTheme`；后台遮罩、文字颜色、蓝色强调色、列表行高一致。
3. C110原 Activity 主题是 `Theme.Material.Light.NoActionBar`，为了避免干扰已验证的 VLC 视频布局，改用 **同一主题为parent** 的 `BlueStarC110Theme`，仅设置字体 family 和Dialog主题；不改原控制和 RTSP。它发出的系统原生 `AlertDialog` 和 WiFi列表因此也使用新暗蓝主题。
4. 新共享背景 `drawable/bluestar_dialog_background.xml` 为暗蓝色圆角22dp+淡蓝描边，用于原生与AndroidX弹窗，适配小列表及超长WiFi滚动列表。
5. 新 `BlueStarDialogChoices.java` 有两种同逻辑 builder：`AppCompatBuilder` 和 `PlatformBuilder`。所有应用源码中原先的 `new AlertDialog.Builder` 全部替换为对应 builder（7个文件、29个构造处；包括云模型协议、云服务器操作、API Key复用、C110热点和灯光、任务/阅读/AI对话/所有本机确认窗口）。`setItems()` 会得到统一的暗蓝圆角52dp条目、可读系统字体、合适图标和右向箭头；`setSingleChoiceItems()` 同样有条目外观、原先的选择与回调行为；未重排条目、未删除取消/确认和任何业务回调。无障碍以整行真实标签描述，保持大于48dp的命中目标。
6. 原 ReadingScreen 页面底部自定义 sheet 继续沿用其自己的暗蓝界面；普通 Android Dialog 会继承相同主题。Android 系统级权限/网络同意弹窗由系统掌控，不应假冒能修改。

## 完整回归
- 用户指定**全软件覆盖，不只是几个窗口**；完整Java diff检查必须证明：主页五行与底栏原逻辑与布局字节一致；七个承载弹窗的文件里只替换构造器类型，其他功能流程完全未变；C110、USB、OCR/TTS、AI聊天、KeyStore、模型参数等源码完全不变。
- Android构建需要同时运行现有纯Java测试：VoiceAction Shared、ProviderKeyReusePolicy、CloudProviderPolicy、CloudThinkingPolicy、CloudServerList、TaskOrder、FollowupPrompt、C110PhotoPolicy/C110ZoomGeometry、OcrLineChunks/OcrSpeedExperiment。
- GitHub ARM64 完整 Gradle build 检验样式和背景资源可正确链接到 AAPT/APK，唯一Launcher/包名/版本/LibVLC3.6.5/UVC/两套音效完整，严格文件清单差异检查通过才发布测试包。
- **用户真机尚需验收**：实际弹窗是否与样图一致，是否所有应用内窗口的选项正常点击、C110 WiFi/模式/灯光、编辑云端接口、选择OCR Chat协议、模型提示词及删除确认/单选选中样式，原主页/预览/任务/阅读 UI 操作、Android16无障碍读屏和真实C110/TTS/GPU预览无退化。
- 新版仍为原仓库分支 `exp/unified-ui-typography-dialogs-20261122`，基线 v1.0.74 HEAD `5627db373d0a41dd7e1bc9578631f5f759874011`；版本 `versionCode 84` / `versionName 1.0.75-unified-ui`。不覆写旧Release，debug签名兼容问题仍须注意，切勿贸然卸载导致Keystore密钥丢失。
