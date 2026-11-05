# 蓝星向导 USB 1080p 60FPS/短时卡顿修复实验 1.0.7（2026-11-05）

## 状态 / 证据

- 用户授权“先修复这个摄像头卡顿和60帧不到的情况”，并要求**发布前严格验收；未经明确确认不得正式发布或覆盖稳定版本**。
- 本次仅实验分支 `exp/blue-star-usb60-stutter-107-20261105`，以1.0.6已验证基线为起点；独立appId `com.vobich.kanzi.experiment.dev107`，versionCode=107，versionName=1.0.7-development。
- GitHub Actions构建 **SUCCESS**：run 42055034691，源码SHA `d94a74218ef54c1d0e717ec738090dac7dbbf1ea`，链接： https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42055034691
- **仅GitHub Actions artifact，无Release发布**。Artifact ID `13429561511`（10天保存期至2026-11-15）：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42055034691/artifacts/13429561511
- 包内APK：`BlueStarGuide_Experimental_1.0.7_arm64.apk`，**79,382,564 bytes**，**SHA256 `26d85b6874019f330b75f3668b2320c64589ec2b20b30a6ec91c7819f489e543`**。
- CI验证：Gradle/NDK compile SUCCESS；签名v2有效、无INTERNET权限、arm64、实际APK含v6 Small REC/DET及ChineseOCR-Lite DET（校验固定SHA）、无旧Tiny/v5/Sherpa包；native `libUVCCamera.so`含`UVC_NATIVE_OCR_RAW20_GATE_ENABLED`及`UVC_FPS_NEGOTIATION_107`，DEX含`BSG_GL_DRAW_COALESCE_107`。已下载GitHub Actions artifact到本地并重新解包、独立SHA核对和二进制标记核对，全部PASS。
- **尚无真机用户实测**。没有宣称实际USB采集、GL提交或可见屏幕达60FPS；没有宣称卡顿完全消失。

## 解决的两个明确底层缺陷

**1. FPS协商遗漏。** 上游 `libuvc/src/main/jni/UVCCamera/UVCPreview.cpp` 的 `setPreviewSize()` 仅在width/height/mode变化时改变请求并调用`uvc_get_stream_ctrl_format_size_fps`。上游`UVCCamera.open()`可能先以默认1..31FPS设置流，而后向其请求1..61FPS但**宽高格式相同**，导致不重新协商。现新增minFps/maxFps/bandwidth的变更检测。只在配置变化时重新协商；不强制摄像头输出不支持的模式。

**2. 枚举顺序决定帧率。** 上游 `libuvc/src/main/jni/libuvc/src/stream.c` 遇到符合1..61FPS的第一条帧间隔立即返回。若UVC描述符列出1080p/30后列出1080p/60，则可能只选中30。现对**同一分辨率/格式的有效候选帧间隔**选数值最小（FPS最大）者，保留不支持60时合法回退到30/其他支持值。小数帧率如59.94可按实际间隔处理。保留1920x1080 MJPEG优先，以及YUYV上游回退。原生日志 `UVC_FPS_NEGOTIATION_107` 输出宽高、格式、协商出的dwFrameInterval和计算FPS；该日志需用logcat查看，不能用GL提交率代替底层实际选定帧率。

## 降低GL队列积压风险（缓解偶发卡顿）

- 上游 `RenderManager.kt` 为每个`SurfaceTexture.onFrameAvailable`消息都向渲染线程排一个`MSG_GL_DRAW`，当解码/CPU/OCR突发负载使GL处理变慢时可积压旧绘制任务。
- 新增`AtomicBoolean mGlDrawQueued/mGlDrawDirty`，保证**最多一个等待中的绘制任务**；渲染中出现新帧则dirty标志置位，当前绘制完成后再安排处理最新画面。通过`try/finally`确保异常时恢复调度。渲染线程重启/关闭时清零标志。
- 保留`SurfaceTexture.updateTexImage`、原`mScreenRender.swapBuffers`及既有`DiagnosticRenderHeartbeat.noteSwapAttempt`，不捏造可见刷新，不增加OCR处理负担。此机制减少积压风险，**无法凭CI证明它必然消除USB原生解码/调度/发热造成的短时卡顿**。
- 保留1.0.6原生每50毫秒最多1次OCR NV21回调（真正源30FPS时Java RAW约15FPS，预览独立允许60）。不改OCR模型、检测阈值、框、阅读顺序、TTS、变焦曝光、焦点策略、USB预览几何和显示颜色设置。
- **右侧灰色区域本轮不声称修复**：缺乏显示栈/具体像素分界的独立取证，不盲改TextureView/FIT/颜色效果。

## 回归/质量门槛

- `scripts/patch_usb_fps_and_gl_107.py` 针对固定AUSBC3.6.0使用唯一精确锚点、否则拒绝补丁，确保先运行1.0.6原生RAW20及诊断插桩，再运行FPS/GL补丁。
- `scripts/test_usb_fps_and_gl_107.py` 检查fps重协商字段、最高支持帧间隔选择、原RAW20保持、CameraRequest仍1080p MJPEG、GL Draw有界且保留交换/测量，数值验证按序[30,60]→60、[59.94,30]→59.94、只有30→30、空列表→无；GL慢于相机时队列最多1项的状态机模拟。
- 原有扫描、长文、手机摄像头预览、OCR方向/模型/语音、USB诊断、自动对焦策略回归运行通过。
- CI首次因旧版号guard拒绝；同步1.0.7断言后继续。又因render插桩增加`noteSwapAttempt`破坏精确锚点拒绝；改为**保留已有心跳**后通过。失败时无APK被误发。
- 最终CI run 42055034691全部完成成功；验证成功**不等于真机性能验收成功**。

## 真机测试建议 / 验收待办

1. vivo X200 Pro上保留1.0.6和1.0.7并排安装，用同一UVC摄像头和同一USB连接，**逐个运行**，同分辨率1920×1080、固定光线和画面。
2. 先OCR停止，只看USB日志里`glInput`、`glSwap`和真实可见画面流畅度；若glInput约60、GL约60，说明帧进入并被提交，但仍不证明屏幕已显示60不同画面；如glInput≈60、GL≈30，说明显示渲染有压力；如glInput≈30且原生日志协商FPS≈60，检查上游原生解码或USB数据流；若协商值为30，取原生支持模式或USB带宽/端口限制继续判断。1.0.7的`appRaw/uvcJava`约15~20属于主动OCR回调限流，不能当真实USB源帧率。
3. 再启用实时OCR，运行5~10分钟，记录GL/RAW的下跌持续多久、是否自动恢复、画面是否冻结、热/背压；与1.0.6对比。优先新旧同一场景A/B。
4. 对右边灰边只观察是否仍存在，不宣称改动此问题；用户已反复提供原有截图，不应重复向用户索要已有证据。
5. 若测试证据充分且用户明确授权，再决定是否正式发布/合并。稳定main只追加PROJECT_HANDOFF记录，不覆盖实验历史。
