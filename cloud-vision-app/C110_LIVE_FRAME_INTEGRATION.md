# C110 已成功原厂视频 → 蓝星向导云端OCR/TTS接入专项 (v0.1.12)

## 目标、真实证据与保留
- 已被用户现场截图验证的成功视频基线：独立C110 mini v0.1.0，原仓库分支 exp/c110-oem-minimal-20261119，commit 6bd85802a31d0535e4209f8e012ba531297032e9。C110水印、键盘、鼠标和桌面均出现在实际画面。不要再使用此前v0.1.11阻止play的自定义Surface门控。
- 仅修改新实验分支 exp/c110-live-frame-cloud-20261119，独立包 com.vobich.bluestar.c110liveframe；正式RC25、既有JPEG高清拍照→蜂窝云端识别/TTS、USB/手机/阅读功能保持不变。未合并到main或覆盖旧Release。

## 与首个成功版一致的视频路径
- 原SJCAM Zone v6.7.3.16 DEX与成功mini链：3012设备只读信息→3016当前模式只读查询→3001/par1录像模式→2015/par1启流→原厂LY mode1候选RTSP rtsp://192.168.1.254:554/xxx.mp4。
- 从Activity创建时让VLCVideoLayout保持Attached和VISIBLE，启动时置顶；用官方VideoLAN开源LibVLC3.6.5和MediaPlayer.attachViews，让VideoHelper独立管理Surface，300ms后单次play。无自定义Surface阻断、无手写RTSP SETUP/PLAY、无强制TCP/多轮端口重试。
- Native VLC进程临时绑定C110 Wi-Fi，停止、进入后台、高清拍照前恢复之前进程绑定。云端客户端继续经显式蜂窝Network.openConnection连接。C110_PRO其他固件分支明确排除，不推断或硬套特殊URL。只记录数字类别，不保存相机响应、序列号或原始RTSP。

## 新增视频点击取帧→识别→朗读
- 用户打开C110预览并点击视频画面；或者此前任务已有的单次拍照入口正在预览C110时，走captureC110LiveFrame。**不会触发C110实体快门、不会下载照片、不会切换相机模式**。顶栏C110拍照按钮仍为原始高清JPEG路径。
- API26+使用PixelCopy.request从正在显示的VLCVideoLayout内部SurfaceView抓一帧Bitmap，只有Vout已出现、源Surface有效、返回SUCCESS才继续，失败绝不复用上次截图。
- 内存JPEG品质90编码后释放Bitmap，复用已经存在的Frame.remoteJpeg→beginCapturedFrame→CloudVisionClient.recognize/TargetVisionClient.inspect→本地TTS。过滤、任务选择、模型、翻页及语音既有逻辑不变；Frame.c110LiveFrame区分视频截图与C110高清原图。
- 每次OCR明确要求蜂窝数据Network和API Key；不假定相机Wi-Fi有公网。c110PreviewSeq + requestVersion隔离迟到取帧/识别。日志仅包含PixelCopy状态、视频宽高、JPEG大小、云耗时等数值。

## 一轮实机验收
1. 手机连接C110 Wi-Fi→启动新隔离APK→按C110预览，确认真实连续画面。
2. 保持手机移动数据正常、配置百炼Key，点击视频区域一次，确认PixelCopy取一帧→云端识别→TTS。失败优先看最新c110_frame运行诊断的pixelcopy数值。
3. C110高清拍照按钮依旧独立，仅必要时做一次回归，不要求重复之前已经验证的整套拍照链。
4. 仅已验证mini预览，**新集成版取帧、云端OCR与朗读在真机通过前一律不声称成功**。不包含连续自动OCR或录像。
