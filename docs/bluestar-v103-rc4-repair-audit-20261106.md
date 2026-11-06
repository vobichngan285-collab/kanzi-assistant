# 蓝星向导 1.0.3 RC4：按需拍照、语音追问与运行诊断修复审计（2026-11-06）

## 范围与基线

- 原仓库：`vobichngan285-collab/kanzi-assistant`。不是新的项目。
- 修复基线：`fix/bluestar-102-press-to-talk-target-icon-20261106@7a58fed22cbeadce2eca999ee4fbae3d79c3dde8`。
- 隔离修复分支：`fix/bluestar-103-demand-capture-diagnostics-20261106`。
- 已完成实际源码提交：`a2cc58cd8c80730eb7776ff61b8414cf8abd314e`，原RC3代码和离线OCR/蓝星向导2.0分支均未覆盖。
- 版本：`versionCode 13`、`versionName 1.0.3-rc4`。
- **用户数据保护：** 原 RC3 APK 和早期同包名 RC4 调试包的 v2 签名证书不同，无法同包覆盖升级；因此本轮最终 **debug 专用包名为 `com.vobich.bluestar.rc4`**，可与已装 `com.vobich.bluestar` 并存。启动图标仍为原蓝色地球；新包需单独设置本机 API Key。独立测试包不等于稳定签名的公开发行版。

## 八项修复与代码审查

| 项目 | 本轮代码范围与结果 | 真实设备门槛 |
|---|---|---|
| 1 手机 Camera2 取新帧 | `InternalCameraController` 以 AtomicInteger 门控，不再每 220ms 主动NV21转换/复制；点击后丢弃队列中的一帧，再获取下一帧；3秒无新帧超时且不使用旧图；USB原生直显及复制策略未重写 | vivo 前/后摄冷启动、变焦、跨镜头切换、点击到朗读延迟 |
| 2 拍照声音 | 直接从用户已试听的原始HTML中提取 **C 金属咔叮** MP3（2910B），存 `res/raw/c_metal_clack_ding.mp3`；SHA256 `4a5c7f989e16109d2cfb51b4f7f324cbfcc9be9724ca2ffc038dcd727e6005e6`；删旧 `metal_sharp_click.wav` | 手机试听确认主画面真正触发正确音色 |
| 3 录音、松手发声和ASR | 保留已有按住录音、松开发送、滑动取消、16kHz单声道PCM/WAV、手机内置麦克风优先；新增录音时长/峰值/RMS、ASR响应时间和匿名HTTP错误；有效用户松手录音才进入云端ASR和发送嗖声 | 真机权限、外接摄像头、无声/过短音频、ASR Key与额度 |
| 4 图片追问回答 | 对最后图片及已识别内容追问，增加已返回模型结果但未排队TTS时的完整答复兜底；首句TTS回调关联同一请求token | vivo真实问答、连续追问、旧图片上下文隔离 |
| 5 寻找目标结束 | `TargetVisionClient.Result.confirmed()` 且当前目标一致时清除寻找状态；切换普通任务时也清除；旧token迟到结果不接管新任务 | 真实目标/疑似目标/误报对照与退出流程 |
| 6 云端/TTS链路 | 云端“连接+请求上传近似时间”与完整响应分开记录；TTS onStart / onAudioAvailable与真实扬声器出声明确区分；重放队列仅接受当前请求token，若未成功提交分句则整段兜底 | 同一图片首句/完整耗时、重复/迟到朗读、费用 |
| 7 运行诊断 | 设置页最后一行新增完整大字体「运行诊断」，仅本地 SharedPreferences 保留最近50次操作或7天、每次最多24条结构化数字/状态；可查看/复制/通过SAF导出/确认清空。**不保存照片、文字、音频、API Key、请求体/服务器报文** | 复现失败是否有记录、清空/导出可用、低开销 |
| 8 UI/图标 | 蓝星地球图标保留；多余的常驻底部引导不再显示；状态信息仅短时显示；保留任务和设置大字体页 | Android 16 状态栏/按钮不遮挡、原图标实际显示 |

### 测试、构建及数据保护

- GitHub Actions 工作流：`.github/workflows/cloud-vision-usb-click.yml`，专用 RC4 分支，不接入主 `main` 的正式程序。
- 静态审计：检查相机/麦克风/振动/网络权限、Android 16 USB 补丁、模型包精简、运行诊断限制、按需Camera2、新提示音引用、寻找模式退出、图标资源。
- 纯Java测试：`CloudOptimizationTest` 和 `VoiceFlowTest`，包括 WAV RIFF、按住/松开/取消、寻找指令分类等逻辑。
- APK 二进制门槛：Android ARM64 编译、`libUVCCamera.so`、五种桌面密度 PNG 可解码、选定 C MP3 源 SHA256、单独嗖声 WAV、包名/版本、安全权限、APK和打包ZIP SHA256。
- **与真人实测区分**：不能把CI编译成功称为真实USB连续稳定、真实TTS首字出声、云端ASR/追问成功、拍照图片方向一致或无漏洞。测试所需账号/音视频设备并不在 GitHub runner；必须靠 vivo X200 Pro 回归。
- 隐私：AndroidManifest `allowBackup=false`，`usesCleartextTraffic=false`；API Key 使用 `CloudKeyStore` 的 Android Keystore AES-GCM 加密，诊断仅白名单结构化数值。
- 发布限制：这是可并存的 **调试签名RC4测试版**，非稳定发布签名的正式升级包。独立应用设置不会自动继承旧包。

## 待核实构建与交付（本报告建档时）

- 独立 `com.vobich.bluestar.rc4` 版本的最终 GitHub Actions Run：[`42239675700`](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42239675700)，源码 `a2cc58cd8c80730eb7776ff61b8414cf8abd314e`。
- **当前仍在等待该次 Run 最终 success / artifact / 文件哈希复核；不得将未验证值填写为通过。**
- 前一份同代码功能、未加独立包名的 Run `42237781234` 已通过：`BUILD SUCCESSFUL`、音频二进制测试、五密度图标解码、Java 单元测试。该包因与RC3证书不兼容**不作为最终用户下载候选**。

## 真机回归优先顺序

1. 保留已安装 RC3，另行安装 RC4 独立包，配置单独的百炼 Key；同一时刻只运行其中一个占用USB摄像头的APP。
2. 手机前后/USB分别点击拍照，验证获得新画面且方向/裁切与预览一致；检查C咔叮和振动，朗读原文无重复。
3. 针对同一张菜单/医院标识，按住AI按钮说“这是什么”并松手，检查嗖声、ASR、图片追问、回答与TTS；在设置运行诊断查看数值、HTTP失败状态。
4. 口头“帮我找超市”：未找到继续，确实找到立即结束，切换普通任务不残留；检查5—10分钟USB预览和返回前台后帧率并对比原RC3。
5. 确认运行诊断“复制、导出、清空、有限记录”与个人隐私；如果出现真实设备差异，按证据进一步修，旧 RC3 保持可用。
