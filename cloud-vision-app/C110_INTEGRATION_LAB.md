# C110 原厂 VLCVideoLayout / VLC 诊断 v0.1.9（2026-11-18）

## 真机结果
v0.1.8 23:49:12与23:49:25：3001/par1=0、2015/par1=0、vlc_wifi_bind=1，之后只有vlc_error=2302（本应用自己设置的12秒无Vout定时器）、reason=2/stage=5。没有vlc_state/Vout/视频尺寸。**没有观察到原生错误事件不等于相机或VLC确认无问题**，不能从2302推断RTSP 404。

## 直接从用户原厂 SJCAM Zone 6.7.3.16 APK classes5.dex 新复核
- `VlcLyCameraPlayer$1.run`先创建LibVLC/MediaPlayer，再`MediaPlayer.attachViews(VLCVideoLayout,...)`、设置EventListener、创建Media、添加编码/缓存选项、setMedia、play。v0.1.8 用原生Vout人工attach SurfaceView；与原厂不同。
- 原版构造函数 `cache=300`，Media选项有`:sout-mux-caching=300`、`:live-caching=300`、`:file-caching=300`、`:network-caching=300`、`:avcodec-skiploopfilter=4`、`:avcodec-skip-frame=0`、`:avcodec-skip-idct=0`、`:no-stats`、`:codec=mediacodec,iomx,all`，禁音。
- `LYCamera.getVideoPlayUrl()`按相机固件版本字段判断并根据路由返回RTSP/HTTP/mVideoUrl，并非全机型统一RTSP。未获得当前C110实机固件版本，不凭猜测改掉已经返回过DESCRIBE200的已知RTSP URL，也不重复无效8192端口探测。

## 本版仅隔离实验包（RC25/照片JPEG/云端OCR/M02S不动）
- 使用开源官方 LibVLC 3.6.5，`VLCVideoLayout`+ `MediaPlayer.attachViews(view,null,false,false)`，原厂四缓存300与上述解码参数，不强制TCP。
- 继续仅用户点击C110预览后启动3001/2015和绑定C110 Wi-Fi，停止/错误/拍照/Activity暂停时恢复先前网络，按既有路径走蜂窝云端。
- 匿名数字进程诊断：`vlc_phase`=1绑定成功、2 LibVLC构造、3视图挂接、4媒体设置、5调用play；错误/超时时记录`vlc_last_phase`、`vlc_event_count`、`vlc_last_event`(VLC原生枚举值，0代表没有事件)以及`vlc_route_alive`。重复buffer事件不反复刷日志。仅记录状态数值，绝不保存原厂原生日志、网址/视频流、SDP、密钥。
- 超时从12秒改为一次20秒，code2302仍明确表示**应用自身无Vout超时**，不是原厂错误；native Error是2301。Vout不是屏幕真实首帧，需要用户目视。
- 版本`1.0.34-c110-libvlcdiag-lab`；新独立安装包`com.vobich.bluestar.c110vlcdiag`。

## 验收
手机连接C110 Wi-Fi，安装v0.1.9，点击一次C110预览，只观察真实画面及最新c110_live日志。特别关注`vlc_phase`、`vlc_last_phase`、`vlc_event_count`、`vlc_last_event`、`vlc_route_alive`、`vlc_vout_count`和`vlc_error`。不用配置Key或重试拍照；若没有Vout，不能宣称取流成功。
