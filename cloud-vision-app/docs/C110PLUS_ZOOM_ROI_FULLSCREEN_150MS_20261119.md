# C110+ 新版本：画面缩放随OCR、横屏全屏、双入口150ms缓存 v1.0.48

## 用户授权的三项关键更新
2026-11-19用户明确许可：**“结合我们刚才说的几条更新原则做新版本，尤其是画面缩放，还有个全屏，还有一个就是150毫秒。这三个东西你一起做下一个版本，现在开始”。**

原有C110+运动相机已实机成功Wi-Fi连接、1001真实快门、SD原始JPEG下载和LibVLC RTSP视频，且用户反馈双入口150ms的流畅度/主观清晰度优于300ms。此次只能在此基线上增量修改，绝不让新控件重写VLC/OEM/WiFi核心。照片分辨率8M/10M/20M仍由**原厂SJCAM Zone**修改；不要在蓝星向导里增加1002相机尺寸指令，更不能启用未实机验证的C110+原图删除。继续保留老综合实验页用于数值对照，不影响正式RC25或任何历史APK。

## 仓库和隔离
原仓：`vobichngan285-collab/kanzi-assistant`。从实验完整包`exp/c110plus-photo-ocr-upload-ab-20261119`（基线HEAD `0f48823624f64f488852a25106c54a2918b04576`）新建独立`exp/c110plus-zoom-roi-fullscreen-150ms-20261119`。独立debug包ID`com.vobich.bluestar.c110pluszoom150`，版本`1.0.48-c110plus-zoom-fullscreen-150ms`。无新仓库、不覆盖旧APK。

## 缩放逻辑（不要混淆视觉缩放与真正OCR画面）
- C110相机专页（用户从“模式→C110运动相机”进入）：预览下方固定`− SeekBar +`，**1.0～4.0倍**，0.1档，按钮约0.25倍微调；默认1倍；`zoomValue`显示真实倍率。调用`VLCVideoLayout.setScaleX/setScaleY`只影响预览**显示**；并在每次用户按实时画面或高清拍照时**冻结本次倍率**。缩放在相机端不执行光学变焦，也不修改原厂照片尺寸/RTSP编码。
- **实时预览点屏幕**：`C110FrameReader.tap(video,zoom)`保持现有`PixelCopy.request(SurfaceView,...)`，拿到未缩放的真正RTSP表面帧；**云端上传前**从中心裁取宽和高各为`原图/zoom`的ROI JPEG，避免把视觉放大结果和未经裁剪的整图混淆。只请求一次PixelCopy/一次云端OCR，不碰相机1001。
- **按“高清拍照”**：保持原一张1001快门、相机SD新原图完整下载、原流恢复及独立蜂窝云请求；在`C110FrameReader.recognizeOriginalJpeg(photo.jpeg,...,zoomAtShutter)`里，倍率1时原byte[]**原封不动**上传，倍率>1时用`BitmapRegionDecoder.decodeRegion`**只解码同一中心对应ROI**并生成单张JPEG上传；仅裁剪这份发云端的临时内存图片，**SD原图保留完整不变**。如果无法安全裁剪则明确失败，不偷偷上传全幅画面伪装已经放大。仅允许原生照片近16:9画幅；设备实际宽高/画幅不匹配需真实核实映射。
- **主页C110内联预览路径**：原来普通双指`zoom`影响USB/手机预览，但对C110被忽略。现在新增`c110PreviewZoom`独立状态（默认1×），C110预览中双指手势控制1～4×，只变换其自身VLC view；`captureC110LiveFrame`对PixelCopy真实帧中心ROI裁切、`captureC110OneShot`对SD JPEG按快门时的倍率中心ROI裁切。切回手机/USB不污染其原有缩放设置；手机/USB现有图像管线不变。
- **坐标假设与限制**：两条拍摄路径为16:9中心视角，同一`C110ZoomGeometry.centered(width,height,zoom)`按归一化中心计算；不是凭空增加真实成像清晰度。相机不同RTSP和照片可能有微小数字防抖/画幅差，需**真机**用可辨认居中目标验证取景对应。JPEG区域编码存在一次本机质量92的编码成本；倍率1则没有额外编码。视频PixelCopy原链路本来就JPEG90，仍一次编码。

## 全屏设计
- C110相机预览滑块右侧提供`全屏`按钮：单击进入**横屏沉浸式全屏**，隐藏安卓状态栏/导航栏及正常顶栏/任务/底部栏/日志，但保留缩放条、`退出`以及单独的`高清拍照`快捷按钮；点击画面仍是实时取帧OCR，按高清按钮还是原生快门。
- `C110CameraActivity` Manifest 标记`configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize"`，在`onConfigurationChanged`里只刷新视图比例和沉浸式标志，**禁止重启C110热点请求、3012/3016/3001/2015、VLC实例或拍照**。退出全屏恢复竖屏普通控制和系统栏。对方向锁定/Android16全屏限制，若系统拒绝旋转，则系统栏全屏可独立生效，仍能退出。
- 全屏预览下方缩放条与普通模式保持相同倍率状态，来回全屏不改变OCR裁剪目标。

## 必须保持的真机基线
- C110+不是标准C110；此前C110_PRO相关删除白名单仍未适配，综合实验基线已隐藏4003删图入口，本版继续不显示、不允许自动删。
- 原 OEM网络和LibVLC播放器保持单一实例：
  - **专页**`C110CameraActivity` VLC options `--network-caching=150`、`--live-caching=150`、Media options `:network-caching=150`、`:live-caching=150`，其他file/sout cache300以及300ms单次delayedPlay不修改。
  - **主页内联预览**`C110LivePreview`完全同样四个150ms开关、300ms原延迟、无新增额外play、无多余Surface重建门控。
- 1001真实快门的单次操作不自动重复，不重命名照片、不修改SD原图，保留9011静音/9038指示灯、已验证WiFiNetworkSpecifier、云端经蜂窝网络、TTS等。
- 视觉OCR输入以实际JPEG为准，不上传视频界面截图代替高分辨率照片；只有用户选中的局部ROI被上传（1×除外）；无需额外AI评分/自动模型切换或输入图像分辨率配额管理。

## CI与真机验收
纯Java`tests/C110ZoomGeometryTest.java` 验证20M、10M、8M和RTSP的1～4×中心ROI以及非法倍率；既有C110PhotoPolicy/C110DeletePolicy、CER回归继续运行。静态检查两入口150ms flags、双路径原1001只有一次、Zoom>1先裁再上传、1×原JPEG passthrough、相机页横屏配置不重新启动VLC，真实Gradle arm64 debug构建、LibVLC/USB本地so与原声音资源仍在，独立Release不得覆盖旧Tag。
CI成功只是代码和编译通过；**用户实机必须验证**1×/2×/4×放大后预览画面与云OCR实际看到的文字区域一致，跨20M/8M照片画幅、横屏切换不黑屏不掉帧不触发额外拍照，150ms长时间稳定不退化。尤其检查150ms是**缓存**而不是分辨率参数，不宣称参数本身提高光学画质。
