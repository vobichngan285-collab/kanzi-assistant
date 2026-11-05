# 蓝星向导 1.0.5 vs 1.0.6：原生USB OCR取帧限流A/B（2026-11-05）

## 实验目标
**只减少UVC→NV21→Java/OCR的无效整帧转换和复制，USB摄像头OpenGL实时预览仍使用最高61fps协商设置。**

对照：蓝星向导实验版1.0.5（旧UVC NV21逐帧回调）。
实验：蓝星向导实验版1.0.6（原生capture callback在YUV→NV21格式转换前，使用MONOTONIC时间闸门，间隔小于50,000,000纳秒即跳过，正确回收原生帧）。USB/MJPEG解码、native RGBX预览、OpenGL输入/交换与OCR神经网络、TTS、阅读顺序完全保留。不会使用第三方APK的专有代码。

### 真实代码路径
- `CameraUVC.kt`的`setFrameCallback(frameCallBack, PIXEL_FORMAT_YUV420SP)`配置原生NV21回调。该回调每次在Java侧调用`ByteArray(capacity())`复制整帧并插入最新帧队列。
- `UVCPreview.cpp`的MJPEG preview线程先执行`uvc_mjpeg2yuyv`、`draw_preview_one(..., uvc_any2rgbx,...)`和`addCaptureFrame(frame)`，捕获线程稍后调用`do_capture_callback`进行NV21转换。
- 实验限流只在`do_capture_callback`，且只在有Java回调和转换函数时启用。跳过分支明确`recycle_frame(frame)`后`return`。不会把跳过的帧交给Java或改变屏幕画面。系统时钟读取失败时回退到原始全帧逻辑。
- 原有1.0.3 USB分级心跳仍在；1.0.6的`uvcJava/appRaw`约15-20fps、`glInput/glSwap`约30-60fps是合理预期，非流中断的直接证据。
- native补丁本身位于`scripts/patch_uvc_native_raw20_106.py`，锁定`ernestp/AndroidUSBCamera@3.6.0`源码。静态/模拟校验`scripts/test_uvc_native_raw20_106.py`。CI额外断言最终APK中`lib/arm64-v8a/libUVCCamera.so`存在`UVC_NATIVE_OCR_RAW20_GATE_ENABLED`标记，避免源码打了补丁但打包的.so没变。

### 成果状态
- Actions run `42028438516` 最终`success`，重要步骤通过：原生NDK编译、20fps模拟/静态源码校验、APK内原生bin标记、签名、ABI arm64、应用包名/版本、无INTERNET权限、离线模型哈希、旧OCR/TTS/手机预览回归检查。
- 经CI编译的实验版源码SHA `3a8a718fc91af9c7feaf8a84f8f9f7c2492d8ff3`。
- 独立实验APK：`com.vobich.kanzi.experiment.dev106`，v1.0.6，Release URL `https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-experimental-dev1.0.6-20261105`。
- APK链接 `https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-experimental-dev1.0.6-20261105/BlueStarGuide_Experimental_1.0.6_arm64.apk`。
- GitHub已上传APK文件大小79,382,528字节、SHA256 `3b769bd8ee11f8d887056eb26b745c3ff7827e6263c23d14d72c67f205181d92`。
- CI通过只证明源码/打包/模型一致，**不能断言真机真实60fps、温度改善、垃圾回收频率改善、识别率上升或者后台卡顿已解决**。

## vivo X200 Pro真机测试流程
1. 安装1.0.6独立包，保留1.0.5用于回退。两款不要同时占用同一USB设备；每次退出上一个，再打开下一个。
2. 在同一光线和距离下，先**关闭OCR**，各自预览约3分钟。记录1.0.5和1.0.6显示的原始NV21 FPS、GL提交FPS（若出现卡顿留意画面是否实际更新）。注意1.0.6的OCR原始NV21计数预计明显低于GL提交，这是实验设定。
3. 再**启动实时识别**，选清楚静止书页/商品包装/店招各一组；记录是否稳定朗读准确、能否正常识别新一页及错误/漏行。
4. 在正常手持轻微晃动/翻页工况重复测试；验证20fps采样是否造成遗漏瞬间出现的短字。若文字严重漏读、OCR等待时间变长，返回旧1.0.5。
5. 观察约5~10分钟、手机发热、界面流畅度；点击USB运行状态条复制诊断记录（包含四级计数和`USB_CTRL_CAPS`，不含照片或识别文本）提供对照。

## 预期与停止条件
1920×1080 NV21每帧3,110,400字节。理论60fps产生约186.6 MB/s的新Java数组，限制20fps后最多约62.2 MB/s，按入库数量计算减少最多约三分之二，不能将其等同于实测总内存/CPU/电量节省。MJPEG→YUYV和native RGBX预览仍按采集帧处理。对于短时移动的文字目标，限制输入帧率可能降低抓取到清晰帧的概率，须A/B与实际读字成功率一起判断。出现明显识别降级、因前后台切换出现新的异常或其他崩溃则停止试用，旧版独立保留。

**边界：** 此轮没有引入GC智能buffer池、系统级后台Service、GL任务合并或新颜色/曝光策略；避免多变量混杂。后续若A/B证明有效，再讨论第二阶段buffer pool/硬件解码/显示层消息合并独立实验。
