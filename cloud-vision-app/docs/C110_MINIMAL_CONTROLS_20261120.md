# 蓝星向导 v1.0.65 — C110 取景页极简视觉修正

## 用户确认的唯一目标
用户在 19:47 C110 真机截图上要求「上面去掉」；随后用红线标注变焦控件背后的蓝色长方形底板，要求去除背景色，但保留变焦滑条的绿色圆点、刻度线和「1.0×」，以及下方「高清拍照」按钮。最终黑色取景背景、没有顶部工具栏、变焦细线直接浮于取景画面、只有蓝色拍照按钮的示意图已得到用户明确「对，开始」授权。

## 本次技术修改
仅修改 `C110CameraActivity` **界面层**：
- 保留原有 titleRow/重连按钮/全屏状态控件对象初始化（避免代码调用空引用），但设置 `titleRow.setVisibility(View.GONE)`，并在 `setFullscreen` 中继续隐藏上方整排。系统状态栏和系统返回手势不改；机身控制/设置可通过主页统一设置进入。顶部不再出现「C110+ 预览」或重连/全屏/齿轮按钮。
- `footer.setBackgroundColor(Color.TRANSPARENT)`：移除大块底部蓝色底板，只保留中间原「◎ 高清拍照」按钮及其可点击尺寸、1001原厂拍照逻辑。
- `zoomControls.setBackgroundColor(Color.TRANSPARENT)`：移除变焦滑动条后方的蓝色矩形底板，保留 Android SeekBar 的原轨道/滑块、倍率文字和现有 1–4× 变焦及云端 ROI 裁剪逻辑。
- 拍照取图/OEM/网络/RTSP/LibVLC/识别/TTS 无行为更改。状态提示仍限时显示必要连接/错误文字。**非 C110 的手机/USB 普通预览本次不改**。

## 版本、保护与验收
在原仓 `vobichngan285-collab/kanzi-assistant`，从 v1.0.64 已发布 HEAD `5dbe3a1c07406eae46f4f7bb375dc28bd1c9460f` 建立隔离分支 `exp/c110-minimal-controls-20261120`。主APK `versionCode=74`，versionName `1.0.65-c110-minimal-controls`；debug 包名 `com.vobich.bluestar.c110plusquick159` 不变，不重建或重装保益独立小启动器。

构建检查要求：仅UI源、版本、工作流和说明文档有差异；静态核对顶栏不显示、两层底板透明、轨道/倍率/拍照仍存在；Java任务排序/云端追问/C110 zoom和照片策略测试；pinned UVC补丁、真实arm64 APK编译、ZIP/libvlc/音效/唯一launcher和版本检查。检查全部通过才建独立的v1.0.65 prerelease。**CI不等于真机拍摄/音效/预览验收**，用户需在vivo X200 Pro上确认C110依旧能实时取景、拖动倍率、原厂1001快拍、云端ROI和语音，安卓返回手势正常。

