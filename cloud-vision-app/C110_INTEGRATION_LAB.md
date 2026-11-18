# C110 Surface Ready Gating 实验 v0.1.11（2026-11-19）

## 唯一真机新证据
v0.1.10 两轮 00:24:18和00:24:55：3001/par1与2015/par1均0；绑定C110 Wi-Fi=1；VLC phase1..5；`vlc_native_state1/2=0`、`vlc_is_playing1/2=0`；完整第一轮`vlc_event_count=1`、`vlc_last_event=256`（MediaChanged）、`vlc_route_alive=1`、`vlc_error=2302`（自设无Vout超时）。未观察到Opening(258)、Playing(260)，不能推断相机已经拒绝RTSP PLAY。

## 源码/推断分界
- VideoLAN LibVLC Java MediaPlayer.play()会在AWindow.areSurfacesWaiting()时只记录播放请求，暂不调用nativePlay；可在onSurfacesCreated后继续播放。此为LibVLC源码机制；是否在v0.1.10真机上发生仍属于待验证假设。
- `VLCVideoLayout`通过VideoHelper动态inflate SurfaceView（不是VLCVideoLayout本体）。上一版未记录Surface生命周期，可能误把初始化成功当作Surface已就绪。
- 本轮没有再次猜测C110固件动态getVideoPlayUrl；当前设备的实际CameraVersion仍未知，之前已知候选RTSP只确认DESCRIBE200，无法据此证明可PLAY。

## v0.1.11隔离实验版改动
- 按用户已授权的方案，保持当前官方开源LibVLC3.6.5及VLCVideoLayout、已实测的相机3001/par1和2015/par1，唯一改变播放触发：找到VideoHelper已经inflated的video SurfaceView，添加SurfaceHolder.Callback记录创建、正尺寸变更、销毁。只有目标View处于attached、Surface有效并有正宽高，且原厂300ms延迟已到，才**单次**调用`current.play()`；Surface重复回调不能自动重放。关闭/拍照/Activity暂停时先注销callback与delayed Runnable、关闭LibVLC、恢复原Wi-Fi进程绑定。
- 新匿名数值：`vlc_surface_event` 1=created、3=destroyed、4=已有效，`vlc_surface_width/height`是第一组有效Surface大小，`vlc_play_gate`=0等待/1已发起唯一play；异常/看门狗还有`vlc_surface_ready`、`vlc_play_issued`，配合`vlc_native_state1/2`和`vlc_event_count/last_event`判读。此尺寸是**显示Surface尺寸，不是C110编码视频分辨率**。为了24条每会话上限不再重复记录vlc_phase1..5，仅保留`vlc_last_phase`。
- 不改URL、端口、播放缓存、云端API Key、已成功的JPEG→蜂窝OCR、TTS、正式RC25/USB/M02S，绝不上传原厂APK/专有so或原始视频流。独立包`com.vobich.bluestar.c110surface`，不覆盖任何旧包。

## 唯一验收
安装v0.1.11、连C110 Wi-Fi，只按一次预览。复制最新`c110_live`完整日志并告知是否有**真实来自C110的连续画面**。重点区分：
- Surface无创建或正尺寸：检查布局/Surface生命周期；
- Surface有效但未发送play：检查状态门控逻辑；
- Surface有效、play=1、仍只有MediaChanged：检查LibVLC内部播放器启动与相机真实固件播放地址；
- 出现Opening或Vout：进入视频流解析/首帧实测，而不是重复视频启动测试。
