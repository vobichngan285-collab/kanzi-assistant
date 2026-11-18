# 蓝星C110 RTSP播放会话专项 v0.1.5（2026-11-18）

## 本轮现场证据
用户 v0.1.4 两次测试一致：
- 进入录像模式 `3001/par=1` **Status=0**，启动预览 `2015/par=1` **Status=0**；
- 相机 `tcp554=1`、`OPTIONS=200`、`DESCRIBE=200`，候选URL `rtsp://192.168.1.254:554/xxx.mp4` 服务真实可达。
- SDP flags `999`，解码后包含 v=0、m=video、H264、sprop-parameter-sets(SPS/PPS)、a=control、fmtp、rtpmap、application/sdp；不包含H265/MJPEG。**因此缺少H264 SPS/PPS的猜测不再是优先方向**。
- 现用 Media3 `setForceUseRtpTcp(true)`，仍在首帧前报 `2000`、`Rtsp`类异常、stage=3，宽高为0；可能是 RTSP SETUP/PLAY/传输不兼容而非视频源不存在。原厂采用 VLC。
- 22:31:23静态高清拍照仍成功，1,127,176B、5888x3312、取图1244ms，云端响应正常。

## 新实验：用已证实的唯一 RTSP URL 验证完整 TCP 视频会话
仅用户点击「C110预览」，走原已成功模式切换 + 启流。Media3出错后**先关闭播放器**，在已经绑定C110 Wi-Fi的原 Network 上通过独立一次性 TCP Socket 执行：
1. **DESCRIBE** 提取真实第一条 video track a=control （不记录SDP或地址），只允许解析到192.168.1.254:554，拒绝其它域名/路径穿越。
2. **SETUP**：一次 `Transport: RTP/AVP/TCP;unicast;interleaved=0-1`，记录 `rtsp_tcp_setup` 的RTSP状态（200成功、461不接受TCP、其他原码；-1超时、-5其它）。
3. 若SETUP=200，从本次Session标识**PLAY** 一次，记录 `rtsp_tcp_play`，再限时查看是否收到`$`开头的RTP interleaved头：`rtsp_tcp_rtp=1`(可见)、-1超时、0非RTP或-7关闭。
4. **TEARDOWN**关闭当前一次性 RTSP Session，不修改SD卡、固件、不录制/存储视频、不上传原始帧。
5. 记录 `rtsp_track`（1=合法视频轨道、-2未找到、-6 DESCRIBE失败等）、`rtsp_tcp_setup`、`rtsp_tcp_play`、`rtsp_tcp_rtp`。最多约数秒；未出画面不会自动再次触发拍照。
**仍保留原高清JPEG取图→蜂窝云端OCR/TTS以及手机USB摄像头、正式RC25功能**。此为隔离诊断APK，而不是预览已修复的承诺。

## 判读
- TCP SETUP=461/其它拒绝：原厂VLC可能需UDP RTP；下一阶段选用支持更宽传输兼容性的播放内核并解决Wi-Fi/蜂窝分流。
- SETUP和PLAY均200，且首个RTP=1：摄像头真实TCP视频输出正常，Media3不兼容可能性大，可切VLC或适配RTSP播放器。
- SETUP=200但PLAY错误：再对照原厂RTSP会话、Session/Range/header。
- 没有track：改为比对SDP轨道URI解析，不盲换主URL。

**测试方法**：连接C110 Wi-Fi，点击顶部一次C110预览，失败后复制最新 `c110_live` 日志；不用填API Key或重新测试已成功拍照。

