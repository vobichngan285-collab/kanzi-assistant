# v1.0.78：最新拍照优先＋旧云端HTTP真正取消（仅两项）

日期：2026-11-23。基线：已真机验证v1.0.77（`4118b708e17e4fc436f1c03f4b5c515aa792008f`）。
分支：`exp/latest-photo-cancel-cloud-20261123`。

## 明确授权的第一轮改动

**① 云端识别期间能拍下一张，最新照片取代旧任务。** 手机前后置（快速／1200万高清）、USB取帧、主界面C110高清、独立C110预览及主页C110快捷拍照：旧请求失效、旧TTS停止、旧结果禁止回写；约350ms防双击，取代此前1500ms误触间隔。仍禁止两个机身1001真快门同时执行；手机JPEG尚未返回前不重叠Camera2静态拍照，避免旧JPEG误配新请求。旧云端已经开始处理可能仍计费，客户端仅能尝试断开。

**② 拍照/JPEG不再被旧云端网络读取占住。** 原手机/USB/C110本地编码串行工作器`encoder`仍在，正常拍照JPEG准备结束即调度专属最多2个云端工作线程；每次请求对应独立`CloudVisionClient.Cancellation`。新拍照时请求版本递增，旧TTS/回调失效，调用`disconnect()`主动中止对应HTTP连接并取消旧任务Future。相机处理不必等待旧HTTP超时。C110FrameReader也把预览/照片预处理`io`与有界云端线程`photoCloudIo`分开，其`cancelRecognition()`只停止识别、不关闭视频预览；真实C110机身快门继续单次串行。

## 防回归与约束

- 硬件侧**不改**手机Camera2本身、1200万像素拍照档位和JPEG编码规则；C110原厂指令3012/3016/3001/2015/1001及Wi-Fi网关、USB UVC和VLC播放器完全不变。
- 云端各模型的请求body、可选thinking/stream、服务商路由、OCR提示词和TTS音色不变。只有显式用于拍照的请求获得取消令牌：CloudVisionClient Chat/SSE、LayoutParsingClient 和 TargetVisionClient。其他语音问答、书架阅读等用旧API，避免越界。
- 旧结果过滤靠`requestVersion`/`C110FrameReader.request`，`RecentPhotoSession.analyzed`旧照片不覆盖新照片；客户端取消不保证云端不收费。
- C110机身快门、真实JPEG下载仍串行；只允许已取得JPEG且等待云端的阶段开始新照片。不能让取消信号中断正在执行1001的机身操作。
- 后台/前台和C110预览页面继续保持原有相机生命周期；不用自动重试原厂快门；保留USB近期画面限制、原云端超时与诊断隐私原则。
- `ModelUsageLedger`沿用服务商报告，可能会有旧任务部分计费或未知Token，不伪造统计。

## 验收计划（CI与用户真机分开）

1. 静态Diff只允许本轮相关代码、Gradle版本、文档与专用Actions；原Camera2分辨率和ImageReader、PhoneStillJpeg、FrameCompressor、HomeActivity、C110WifiCamera/Bootstrap、USB库、TTS引擎实现不变。
2. Java历史回归（Qwen/模型/语音/任务/C110）、GitHub Actions Android arm64 APK编译、原UVC补丁、包名/唯一Launcher/LibVLC/UVC动态库/音效/SHA256/Release必须全成功。
3. vivo X200 Pro：手机高清连拍——首张尚未返回时启动第二张；旧文字不朗读，第二张独立获取1200万像素，第一张网络响应不阻塞新任务；手机快速模式和USB各测试两次，旧结果不得覆盖；前后摄切换与TTS正常。C110+：先用独立相机页面及主页快捷拍照分别测真实1001后云端未返回时按第二张，确保机身串行、只读最新照片；网络断开和401时提示，不自动重拍/改密钥。
4. 真机未验收前不能声称连拍速度或旧请求已完全停止云端计费；debug签名冲突时不要卸载旧包导致Android Keystore密钥丢失。

