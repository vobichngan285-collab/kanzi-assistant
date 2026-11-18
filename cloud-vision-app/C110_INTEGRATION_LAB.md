# C110 RTSP PLAY 请求地址专项 v0.1.7（2026-11-18）

## 最新真机结论
- v0.1.6 在 23:09:54 和 23:10:19 连续两次：`live_mode_cmd=0`、`live_cmd=0`、`tcp554=1`、`OPTIONS=200`、`DESCRIBE=200`、`rtsp_sdp_flags=999`、`rtsp_track=1`、`rtsp_tcp_setup=200`，但`rtsp_tcp_play=404`、`rtsp_tcp_rtp=-9`，视频尺寸=0、没有真实首帧。`404`不证明相机无视频，`-9`为未开始检测。
- 源码核查：SETUP 使用实际视频轨道URL，但 PLAY 和 TEARDOWN 无条件发送固定presentation URL `rtsp://192.168.1.254:554/xxx.mp4`。若 SDP 未提供聚合控制，可能因请求目标不同触发404。此为待验证的具体假设，并不能证明其为唯一根因。

## 仅改单一PLAY目标选择，保持原Session
- 新`videoTargets`同时解析第一条视频段媒体级`a=control`与会话级`a=control`。有会话级控制字段时以它指定的聚合目标PLAY（`*=原已知演示URL`）；未声明聚合控制时仅对已成功SETUP的视频轨道URI PLAY。同一SETUP响应`Session`沿用，不重建链路、不盲试第二个PLAY地址；TEARDOWN使用选择的PLAY URI。
- 严格限制目标为`192.168.1.254:554`，拒绝跨域、账号、query、fragment及路径穿越，日志不存真实SDP、轨道地址、图片、视频或密钥。
- 新匿名字段`rtsp_play_target`：1=SDP会话级/聚合控制地址；2=无聚合时单视频轨道地址；-9=尚未取得有效目标。原有`rtsp_track/setup/play/rtp`保留，仍一次人工点击一次实验。
- 增加原纯Java回归测试：无会话控制时PLAY必须等于SETUP轨道；通配聚合、明确聚合、会话/媒体 URI 不同、仅会话控制和非法聚合URI必须有稳定结果。
- 不改正式RC25、M02S、原照片模式3001/par0+1001、JPEG回传、蜂窝云端OCR/TTS、Media3、Wi-Fi分流或原厂固件。

## 验收
安装新的独立包`com.vobich.bluestar.c110play`，手机连C110 Wi-Fi，点击一次C110预览，复制最新`c110_live`；观察`rtsp_play_target`和`rtsp_tcp_play`、`rtsp_tcp_rtp`。RTSP SETUP=200和PLAY地址修复不等于已出现实时视频，只有真实首帧能证明预览成功。
