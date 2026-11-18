# 蓝星C110 真实预览实验 v0.1.3：先切录像模式，再启动RTSP，检查SDP（2026-11-18）

## 上一轮真机最新事实
用户 v0.1.2 22:15:28与22:15:35两轮独立实验一致：
- `2015/par=1 Status=-13`，确定控制指令执行失败，不是旧日志将其它负数压成-1。
- Wi-Fi TCP554 可连；`OPTIONS=200`、`DESCRIBE=200`，意味着候选 `rtsp://192.168.1.254:554/xxx.mp4` 地址能响应RTSP基础协商；**不能推断已成功SETUP/PLAY或能解码出帧**。
- 8192 TCP拒绝连接（-2），当前固件此端口不可用；不再盲改URL。
- Media3 `2000`（未分类I/O）、先前细分类5（RTSP类异常）且视频宽高0；具体可能是直播状态未初始化、SDP不合规、RTSP SETUP/PLAY或编码不支持。
- 同时 22:15:40 的C110高清拍照仍成功：取完整 JPEG 5888×3312、1,379,123B、902ms，云端网络耗时2279ms；正式可用的JPEG→OCR/TTS主链保持不变。

## v0.1.3 严格单变量改动
- 按原厂Zone DEX已有的 `LYCamera.changeCameraModel→startLiveVideoStream→VlcLyCameraPlayer` 顺序，用户手动点「C110预览」后，先仅一次 `3001&par=1` 切录像模式，再仅一次 `2015&par=1` 启动视频；若切录像模式明确失败则不盲发2015并展示已知结果。该操作会改变C110当前模式，需要退出预览后下次高清拍照重新进入照片模式。每次用户点击才发送，不自动重试。
- 新匿名诊断 `live_mode_cmd`（3001结果）、`live_mode_ms`、`live_cmd`（2015结果）；保留真实负错误码和之前的554/8192端口测试。
- 在旧RTSP DESCRIBE响应200后，从**同一个已确认URL**再做一次大小≤24KiB的有界只读DESCRIBE，**只在本机解析**媒体类型/参数标记，日志仅记录 `rtsp_sdp_flags` 组合值，不保存 SDP/连接令牌/视频流。比特：1=有v0;2=有m=video;4=H264;8=H265;16=JPEG;32=有sprop-parameter-sets;64=control;128=fmtp;256=rtpmap;512=application/sdp。该字段用于判断Media3所需H264 SPS/PPS是否缺失；缺失不代表一定没直播，也可能用其它方式传输。
- 播放报错增加 `live_rtsp_stage`，1=ParserException、2=RTSP 461传输错误、3=RTSP其它、4=一般IO、5=IllegalArgument、9=其它。日志不保存异常消息/原始SDP。
- **不增加猜测的视频URL、不刷机、不删除照片、不自动持续采图；保持已经验证成功的完整JPEG、蜂窝百炼OCR、语音朗读、手机/USB摄像头和RC25源码不变**。本次仍是实验包，不能宣称已在真实C110显示视频。

## 真机下一步
连接相机Wi-Fi，打开独立实验包，点一次「C110预览」，只发当次 `c110_live` 日志。优先看 `live_mode_cmd/live_cmd`、`rtsp_sdp_flags`、`live_rtsp_stage`、`live_first_ms`。若两条状态均0却Media3继续解析失败，则结合SDP flags考虑使用原厂的VLC式解码内核；若3001成功让2015从-13变0则证明必须有模式前置。由于新包名，配置百炼Key不是预览验证前提。
