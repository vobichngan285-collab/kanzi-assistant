# 蓝星向导：USB点击取帧 / 云端视觉任务独立实验日志

日期：2026-11-06（Asia/Taipei）

## 独立性与边界
- 实验分支：exp/cloud-vision-usb-click-v1-20261106；基于 main a5b8851af36e6d81b082499c7f594b7db5d84283。
- 完全独立的 Gradle 工程：cloud-vision-app/；独立应用ID com.vobich.bluestar.cloudprobe；版本 0.1.0-probe。
- 不覆盖 1.0.9/1.0.10/2.0 分支、APK、离线发布版。
- 新工程不含本地 OCR、检测器、识别模型和相关推理依赖。系统 TTS 在手机本地工作。
- 由于尚无阿里云账号/安全代理，当前 APK **没有 INTERNET 权限、没有 API 密钥，也不会上传图片**。它只能朗读明确标注的模拟消息，不能识别图像文字；不得误称云端AI已打通。

## 第一期功能实施
- 复用上游 AUSBC 3.6.0，USB MJPEG 原生 NORMAL/TextureView 直显，原始 NV21 限制回调约 5Hz，不限制相机实际直显帧率。
- 点击预览或点击按钮，检查 NV21 帧是否在 1400ms 内取得，才将该帧引用交给单工作线程；按一次压成不大于 1280 像素长边的 JPEG，只在内存里操作，不保存原始照片。
- 1.5 秒防误触节流；后台/切换任务取消旧工作和旧语音；旧结果的 generation ID 不得覆盖新状态。
- 两条固定引擎路线：原样朗读文字→云端 OCR；菜单/商场/医院/场景/自定义任务→云端 VL。当前任务本地记忆；自定义任务支持新建/编辑/删除，保存在 Android 私有 SharedPreferences。
- 可手动启用亮屏前台音量键测试；默认不劫持音量键。应用 onPause 关闭 USB，因此 **真实锁屏继续采集和锁屏音量键** 没有实现，须单独验证，不许以黑色页面模拟通过。
- USB 原始回调 FPS 和最新帧年龄诊断；静态源码审查和 Android Actions 构建结果另记录。

## 等待后续验收
1. GitHub Actions 编译、APK权限/依赖/包名/签名审核，产物SHA256。
2. vivo X200 Pro + USB 相机前台实际画面、10分钟取图、任务记忆、TTS、音量键测试。
3. 真正锁屏的前台服务/摄像头独立采集方案和允许的实体键触发方式。
4. 后端安全代理与阿里云百炼账户/预算配置、实际OCR与VL调用、照片上传告知和用户授权、费用记录。

该文档是实施记录，不表示已通过实机或云端计费验收。

## 2026-11-06 CI、源码审计与真实产物校验（追加）
- 初版提交 `73dc69e67d3e54cadea981c3c00cb8e2dce581b4`；独立工程、任务UI、原始USB回调、720P内存JPEG、模拟TTS和安全边界均在代码中。
- 第一次 CI `42153408725`：`android-actions/setup-android@v3` 使用已失效的 `sdkmanager tools` 步骤失败，尚未开始编译；改回仓库以往成功的runner SDK。
- 第二次 CI `42153507729`：已完成AUSBC原生库构建，但Java编译发现 `USBMonitor.UsbControlBlock` 对应 `:libuvc` 缺少直接依赖。新增 `implementation project(':libuvc')`，提交 `9061fc7bab308c6344b041bcfdf0909fa1413e13`。
- 第三次 CI `42153808969`：**SUCCESS**，包含静态无本地OCR/无INTERNET权限审核、Android USB授权 `PendingIntent` 补丁验证、原生约5Hz回调限制与关闭屏障、Android ARM64编译、APK产物库检查。
- GitHub Actions 地址：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42153808969
- Artifact ID `13473743021`：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42153808969/artifacts/13473743021 ；artifact ZIP 共 `6,332,679` 字节，2026-11-13 UTC到期。
- ZIP 中 APK `BlueStar_CloudVision_USBClick_Probe_v0.1.0_arm64.apk`：`8,324,507` 字节。下载后独立解压复算 SHA256 = `e3ab8c3c7ac04e7a83d9229cfb5a18fcc6d0e87c0055a5f3528d2c3621c74199`，与 artifact 内 `SHA256SUMS.txt` 完全相符。APK仅有 `arm64-v8a` 下的原生USB库，枚举ZIP未发现OCR/ONNX/Paddle模型文件。
- 编译成功≠真正USB连接或USB锁屏拍照成功。vivo X200 Pro尚未安装实测；**锁屏/息屏持续采集不支持**（本版Activity.onPause关闭相机）；本版只回放明确提示的模拟语句，无AI模型请求、无实际文字识别。下次开发继续单帧真机验证，之后接安全代理和真实阿里云百炼OCR/VL。
