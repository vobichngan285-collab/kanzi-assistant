# v1.0.88 C110真实视频画幅与实际屏幕合成取帧

## v1.0.87真机问题
用户日志连续13次C110预览的`frame.surface_width_px=1260`、`surface_height_px=2590`、JPEG上传也是1260×2590、`crop_left/top=0`。但C110标准版RTSP通常按横向视频播放，v1.0.87仅把SurfaceView显示尺寸当成源像素而没有核实VLC画幅，识别仍只读出“西施”两个字；手机预览使用720×1280反而准确。PixelCopy成功只表示成功复制请求的图像，不表示它包含正确视频内容。

## 修正
- 主相机页`C110CameraActivity`从`MediaPlayer.getCurrentVideoTrack()`读取原视频轨道`width,height`，失败用-1明确未知，并把数值传给`C110FrameReader`的预览诊断。该视频轨道尺寸是**记录信息**，不会不经验证直接拿来当Android Surface/Window采样坐标。
- 预览点击改为`PixelCopy.request(activity.getWindow(),windowRect,bitmap,callback,handler)`，只复制窗口中`cameraRoot`内容区（去除根布局的系统栏padding），保证VLC正在使用的View scaleX/scaleY、竖屏铺满、1–4倍缩放已经在窗口里完成。UI控制层与窗口一起被复制，真实上传图可查看并进一步确认是否遮挡文字。该实现**不保证无控制UI叠加**，但能证明是实际屏幕显示内容，避免使用错误Surface像素坐标造成的异常拉伸或裁剪。源Rect与目标Bitmap宽高严格1:1、不额外缩图，单次JPEG质量100；云端仍接收相同JPEG byte[]，不改变模型/TTS/网络。
- 额外增加可见“查看上传图”按钮（仅C110预览页底部，保留原高清拍照按钮），直接在本机Dialog查看本次运行最近一次真实上云预览JPEG，内存暂存，不写照片到存储、不再次发送、关闭页面回收。方便用户直接核验是否存在UI遮挡、黑边、画幅比例不对或文字模糊。
- 诊断`c110_preview`新增`frame.track_width_px/track_height_px`、`frame.window_left_px/window_top_px`以及`frame.visible_width_px/height_px`，还保留已有`jpeg.width_px/height_px/bytes`、云端时长。轨道元数据未知记-1，不编造视频尺寸。
- 保留C110 RTSP 3012→3016→3001/1→2015/1，LibVLC 3.6.5/player.attachViews，C110原1001高清快门、WiFi自动连接、手机Camera2、USB、语音与模型、不变已验证上传路径。核心变化仅`C110CameraActivity.java`, `C110FrameReader.java`, gradle版本号和新增工作流/说明文档。
- 客观限制：窗口截图包含UI覆盖控件，而不只是视频像素；RTSP视频自身可能压缩画质。真机需检查新按钮显示的实际上传图片，并核对视频轨道宽高与窗口宽高，不应在没验收前声称识别准确率已经修好。

## 安装与验收
构建Release通过后，覆盖安装不卸载；在C110预览点击一次识别，然后点“查看上传图”，确认图片是否真的是屏幕所见、是否包含文字、是否有黑边或按钮遮挡；提供c110_preview诊断。该步骤不是重复让用户比较手机与C110，而是直接观察已发送图片本身。
