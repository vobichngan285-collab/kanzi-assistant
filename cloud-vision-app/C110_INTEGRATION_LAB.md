# C110 v0.1.10：原厂播放启动顺序 / 原生状态专项 (2026-11-19)

## v0.1.9 真机证据
2026-11-19 00:07:15：`live_mode_cmd=0`、`live_cmd=0`、`vlc_wifi_bind=1`、`vlc_phase=1..5`、`vlc_last_phase=5`、`vlc_event_count=1`、`vlc_last_event=256`（原生MediaChanged）、`vlc_route_alive=1`、`vlc_error=2302`（App自己的20秒无Vout超时）。**没有发现Opening(258)/Playing(260)原生事件**，不能从2302判断RTSP错误、视频服务不支持或解码失败。

## 本次直接对照用户提供的原厂 SJCAM Zone 6.7.3.16 `classes5.dex`
- 实际反汇编 `LYCamera.getVideoPlayUrl()`：根据 `SCameraInfo.getCameraVersion()` 的多组固件条件选择返回`rtsp://192.168.1.254:554/xxx.mp4`、`http://192.168.1.254:8192`或空值。此前只观察到相机对已知RTSP源的OPTIONS/DESCRIBE=200，不知道实际C110固件版本，因此**不能把另一个候选地址臆造为真实流地址**，也不能认为对某个服务返回200证明能够播放。
- 原厂 `VlcLyCameraPlayer.playByUrl()` 方法体确证使用 `Handler.postDelayed(...,300)`，然后执行 `VlcLyCameraPlayer$1.run()` 中的 MediaPlayer.attachViews/setEventListener/Media设置/播放。v0.1.9立即play而不是匹配原厂的300ms延迟。
- 原厂Media作为播放器对象字段持有，直到换流/释放。v0.1.9调用setMedia后立刻`Media.release()`（Java层正常引用计数可能允许，但非原厂生命周期）。

## v0.1.10仅对隔离实验包做两项原厂时序适配 + 状态诊断
- 在旧顺序已创建LibVLC和VLCVideoLayout、已完成Media设置后，`Handler.postDelayed(delayedPlay,300L)` 调用`current.play()`；停止/暂停/拍照/错误时主动removeCallbacks，防止关闭后迟到播放。
- 将Media对象保留到`stop()`，播放器stop/release以后才release Media，避免过早释放；仍仅一次用户触发的连接，不盲目重试。
- 新增仅匿名数值：`vlc_native_state1`/`vlc_is_playing1` 是play后1.2秒原生MediaPlayer状态、`vlc_native_state2`/`vlc_is_playing2` 是20秒本地watchdog前的状态。原`vlc_phase`/event_count/last_event/route_alive和error保持。没有记录网络封包、原始VLC日志、SDP、视频、任何密钥。
- `getPlayerState()` 是内部Media状态枚举编号，不等同于Event编号；`isPlaying=0`只代表采样瞬间非播放，需结合事件与状态判读。
- 不改原厂候选URL、模式指令3001/2015、不强制TCP、不触碰高清JPEG/蜂窝OCR/TTS/USB/M02S及正式RC25。新独立包名`com.vobich.bluestar.c110oemstart`，避免覆盖任何旧测试版。

## 真机验收
连接C110 Wi-Fi、安装v0.1.10、点一次C110预览，目视判断是否真实连续相机画面；复制最新完整`c110_live`。重点`vlc_phase`、`vlc_event_count`、`vlc_last_event`、`vlc_native_state1/2`、`vlc_is_playing1/2`、`vlc_vout_count`、`vlc_error`。不必填API Key或再测试拍照。
