# 蓝星向导 v1.0.87：C110预览点击屏幕所见区域直接PixelCopy，禁止多余缩图/二次裁剪

- 从v1.0.86唯一发布基线 `7f19414fb9c5b290d18428055dd7f767b606d4cf` 派生，用户明确授权仅修改C110预览快速取帧像素链路，严格检查、通过才发布APK；保持C110 Wi-Fi自动连接、原厂高清1001、VLC 3.6.5、手机Camera2/USB、云端模型、TTS和提示音原样。
- 根因检查：旧`C110FrameReader.tap`先按底层Surface尺寸创建整帧Bitmap；竖屏模式面积大于2,073,600时先缩小整帧；随后`analyze`用`C110ZoomFrames.cropPreviewBitmap`做第二次像素裁剪，JPEG质量90，缺少诊断。放大和竖屏画面使用`C110ZoomGeometry.centered/centeredCover`与`C110CameraActivity.applyZoomPreview`中心缩放规则对应。
- 现在把裁剪提前到PixelCopy：读取底层`SurfaceView.getWidth/getHeight`，调用`C110ZoomFrames.visiblePreviewRegion`（与旧几何一致），使用Android API 26+ `PixelCopy.request(SurfaceView,Rect,Bitmap,callback,Handler)`，Bitmap目标分辨率严格等于源Rect宽高，**不先截全部再缩至2MP、不第二次Bitmap裁剪、不插值放大**。2MP先行缩图上限已取消，仅保留最终ROI超过9,000,000像素时的安全拒绝防OOM。对范围、尺寸做边界校验，不拿陈旧画面代替错误结果。所有1～4倍及16:9→竖屏裁剪仍按原C110 ZoomGeometry一致中心范围；没有额外内容发给云端。
- Bitmap仅`JPEG quality=100`压缩一次；**JPEG本质上不是数学无损**，但无需修改已实测工作中的JPEG云端接口：上传还是同一byte[]，没有二次缩小、编码或转换。将来若需逐像素无损需对每个供应商测试PNG MIME和网络体积，本版不更改。
- 对预览点击新建`RuntimeDiagnostics` `c110_preview`（最近50次／7天／最多24匿名数值事件），记录`frame.surface_width_px/surface_height_px`, `frame.crop_left_px/crop_top_px`, `frame.visible_width_px/visible_height_px`, `frame.pixel_copy_status`, `jpeg.width_px/height_px/bytes/encode_ms`, `cloud.upload_ms/network_ms/first_text_ms/first_speakable_ms` 与`capture.total_ms`；云端音效/任务/可中断旧请求逻辑原样。该会话传给既有`recognizeJpeg(...,photoDiag)`，直接记录实际云端请求耗时。
- 几何测试增加1倍/2倍/4倍、竖屏裁剪、偏窄/偏宽画幅、源Rect像素边界以及最终ROI始终不大于源面积、右下边界；确认只在空间上裁剪、无缩图。
- 仅修改`C110FrameReader.java`,`C110ZoomFrames.java`,`C110CameraActivity.java`（后者只变注释），`app/build.gradle`,`tests/C110ZoomGeometryTest.java`和新增本说明/新workflow；**不修改**`C110WifiBootstrap`,`C110WifiCamera`,`C110QuickShotController`,`InternalCameraController`,`PhoneStillJpeg`,`MainActivity`,`CloudVisionClient`,`CaptureDingFeedback`,`C110ZoomGeometry`本身及LibVLC。
- 自动验证：精确7文件diff+预览Rect精准源取样/无2MP比例缩放/仅一次JPEG编码/预览诊断/原云端音效不回退；JUnit式纯Java几何回归、云端供应商/模型/任务/WiFi策略回归、完整ARM64 Gradle+原UVC补丁、APK包名/唯一HomeLauncher/音效/UVC/VLC和SHA256；严格通过才Publish GitHub prerelease。
- 真机仍需在vivo X200 Pro查看C110预览1×/2×/4×及竖屏铺满时的真实屏幕画面和实际提交给云端的区域。由于RTSP视频源实际分辨率、Surface底层缩放/驱动行为和云端服务端处理无法通过CI完全证明，CI通过≠实机相同像素品质或识别率已改善。
