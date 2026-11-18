# C110 预览真实协议诊断 v0.1.2（待真机）

用户实测 v0.1.1：C110拍照/云端识别成功（22:05:10，JPEG 5888×3312、1,256,313字节、拍照取图1,029ms）；预览失败（22:05:00），旧日志 `live_cmd=-1`、`live_rtsp_error=2000`、`live_width=0/height=0`、`live_http8192=-1`。

## 直接定位的错误
1. 旧 `RuntimeDiagnostics.event()` 将负数统一裁剪为 -1，无法区别设备真实 `Status=-256`、`-13` 及其他值。本版仅修复匿名整数状态裁剪范围到 -4096；无原始网络报文记录。
2. `2000` 是 Media3 `ERROR_CODE_IO_UNSPECIFIED`，不是“URL不支持”的确定证据；旧播放器只保存errorCode，没有根因类别。增加 `live_rtsp_cause` 1=SocketTimeout、2=ConnectException、3=NoRoute、4=SecurityException、5=RTSP专有异常、6=UnknownHost、7=一般IO、9=其他。
3. 旧 `live_http8192=-1` 可由超时、拒绝连接、非HTTP裸流等多种原因触发，旧方法吞异常导致无法区别。新版在相机Wi-Fi Network上读取：`tcp554` / `tcp8192`：1=TCP可连接、-1=超时、-2=拒绝、-3=无路由、-4=权限、-5=其他；`rtsp_options` / `rtsp_describe`：实际RTSP返回数字状态（如200、404）、-1/-2/-3/-4/-5=连接失败、-6=非RTSP格式、-9=对应端口不可连接；`http8192`：200等HTTP状态或同上分类。**只进行一次只读探测，不抓视频原始报文，不猜新URL，不自动重拍。**
4. 本版仍只尝试此前SJCAM Zone DEX出现的 `rtsp://192.168.1.254:554/xxx.mp4` 和2015/par=1，不保证实际C110固件使用它们。Media3失败则恢复原手机相机画面，不再显示黑屏。

旧 v0.1.0 成功的 C110 高分辨率 JPEG→蜂窝百炼识别/TTS 完全保留；实验包为独立 `com.vobich.bluestar.c110probe`，不会覆盖 RC25/集成0.1.0/预览0.1.1；由于CI Debug签名可能变化，避免对同包覆盖更新，API Key须重新配置。

**真机下一步**：手机连接 C110 Wi-Fi，打开诊断版，只点一次「C110预览」，把整个 `c110_live` 段发回。判读 `live_cmd`、`tcp554/rtsp_options/rtsp_describe`、`tcp8192/http8192`、`live_rtsp_error/live_rtsp_cause`。若554 TCP不通或RTSP DESCRIBE非200，应转查原厂对应固件真实传输端点，不盲发其他改状态命令；若DESCRIBE200但Media3报错，继续查SDP/编码/RTP传输兼容性。正式版与固件不变。
