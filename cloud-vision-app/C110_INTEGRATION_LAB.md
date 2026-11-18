# C110 原厂 LibVLC 视频预览对照 v0.1.8（2026-11-18）

## 原厂依据与真实测试
- 用户的v0.1.7两次实际测试都为视频模式0/启流0、轨道1、TCP SETUP200、PLAY404、RTP未开始、视频宽高为0，修改PLAY地址未解决。
- 用户上传的SJCAM Zone 6.7.3.16 APK（只读静态核对）在 LY预览路径采用 3001 模式切换→2015/1启流→getVideoPlayUrl→VlcLyCameraPlayer.playByUrl；播放器通过LibVLC做RTSP协商，而非手写PLAY。原播放器使用网络/直播缓存和硬件解码参数，未在已检查的播放器包装代码中发现强制rtsp-tcp。不能由这些静态证据证明相机实际使用TCP或UDP。

## 本版改动（仅实验分支）
- 完全替换当前预览的Media3播放器为Maven Central开源官方 `org.videolan.android:libvlc-all:3.6.5`，独立安装包`com.vobich.bluestar.c110vlc`。不提交原厂专有APK/SO，不改正式RC25。
- 保留C110真实可用的3001/par1和2015/par1与`rtsp://192.168.1.254:554/xxx.mp4`。LibVLC自行处理RTSP，**不强制TCP**，预览期间不调用手写`C110RtspSessionProbe.probe`也不再重复554/8192只读端口探测。
- Native VLC无法使用Media3 Network SocketFactory，所以只在用户点击C110预览后用`bindProcessToNetwork(cameraNetwork)`临时绑定相机Wi-Fi，停止/失败/进入后台/高清拍照前释放VLC并恢复原进程网络绑定。上云仍指定蜂窝Network，新增 CHANGE_NETWORK_STATE 权限。
- 回调匿名数值：`vlc_wifi_bind`、`vlc_state`、`vlc_vout_count`、`vlc_vout_ms`、`live_width_px/height_px`、`vlc_error/reason/stage`。**Vout/Playing并非已经在屏幕渲染真实首帧**；必须真机目测确认。12秒无Vout会结束本次测试，不自动重试。
- 照片原JPEG、云端OCR/TTS、手机USB功能、相机固件、M02S与旧Release保持不动。

## 真机验收
连C110 Wi-Fi，安装隔离v0.1.8，只点一次顶部C110预览。目视确认是否出现来自C110的真实连续画面，复制最新`c110_live`。无需填百炼Key，不用重试已成功的高清拍照。
