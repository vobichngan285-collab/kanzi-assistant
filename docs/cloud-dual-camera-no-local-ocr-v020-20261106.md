# 蓝星向导云端实验 v0.2 双摄像头显示修复（2026-11-06）

## 用户纠正与实施范围
用户指出v0.1.0丢失原有手机相机、改变布局，要求恢复以前的双摄像头显示，仅移除本地OCR。已从2.0分支 `release/blue-star-guide-2.0-20261106` 核查并移植手机Camera2控制器 `InternalCameraController.java`、NV21旋转助手 `Nv21Rotation.java` 和 `PhonePreviewGeometry.java`。所有修改仍仅在 `exp/cloud-vision-usb-click-v1-20261106` 分支的 `cloud-vision-app/` 独立工程。

## 本轮具体改动
- 保持2.0黑色全屏预览、底部三列深色悬浮按钮和暗色设置对话框的框架。左按钮由“模式”改为“任务”，中按钮由“开始识别”改为“点击拍照”，右按钮仍为“设置”。
- 设置→摄像头完整保留2.0的三个选项（USB外接、手机后置、手机前置），保存 `phone_camera_source` / `phone_camera_front` 选择；不再使用v0.1.0单USB滚动页面。
- USB预览保持AUSBC 3.6.0 Native NORMAL / TextureView，1920x1080请求和MJPEG，preview FIT；新版本约5Hz NV21帧回调仅用于**手动**取图，不运行识别模型，不改变UVC视频预览帧率。
- 手机前后摄像头使用2.0原有Camera2控制器、原有SurfaceTexture挂载顺序（`super.onCreate`之后才附加手机TextureView）、传感器旋转及全帧FIT矩阵；不对整个画面进行中心裁剪。
- USB和手机用同一个最新帧的单次点击抓取和JPEG内存压缩路径，保留1500ms防重复点击和1400ms新鲜帧门槛；任务预设和本地自定义功能继续存在。
- 无本地OCR依赖/模型、无`INTERNET`权限、无AI Key；模拟TTS明确说尚未接云端，**不会朗读真实照片文字**。API账号在准备好安全代理后再开通。
- 保留旧2.0、1.0.9、1.0.10分支和历史APK；不将本测试版作为稳定正式版。

## 验收
本文件随源码提交。GitHub Actions需要确认编译、包名、源代码双摄像头链路和无OCR模型。必须在vivo X200 Pro依次测试USB/手机前后摄像头实际画面、拍照是否为当前帧、切换回USB及返回前台、TTS；CI成功不能替代真机通过。


## 2026-11-06 构建与产物复核结果
- 双摄像头源码提交 SHA：`a3d4ad37fcf8d27ad4ec6debeaa007e1d5e28653`。
- GitHub Actions `42158082173` **SUCCESS**：静态双摄像头 / 原版全屏布局 / 无OCR依赖审核、固定AUSBC原生补丁、arm64构建、APK检查均通过。https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42158082173
- Artifact `13475213529`：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42158082173/artifacts/13475213529 ；过期时间 `2026-11-13T03:44:30Z`。
- 独立下载实际 ZIP、提取实际 APK 后复核 `SHA256SUMS.txt`：`BlueStar_CloudVision_DualCamera_Probe_v0.2.0_arm64.apk` 8,348,015 bytes，SHA256 `896a535f450fa0ed12fa81e1b40b6114cdef18c276118cca91e850c665830814`，完全一致。
- ZIP 内APK仅带原生UVC USB/JPEG动态库；完整打包条目检查未出现 `inference.onnx`、Paddle、PP-OCR、sherpa等模型或库。
- **尚未通过** vivo X200 Pro USB/手机前后相机真机画面、切换后回USB、熄屏工作、云端识别测试；本APK只有模拟语音提示，不能当作完成云端OCR。


## 2026-11-06 用户第一次真机反馈：手机后置摄像头（追加）
- 用户发送v0.2.0实际界面截图；可见手机后置摄像头预览（蓝色商品包装盒、遥控器和桌面），黑色全屏预览与底部原三按钮布局仍正常显示。
- 点击取图Toast显示“已取得手机后置摄像头最新画面，1080×1920，JPEG 73KB，任务：…”；**手机后摄预览及单次取图压缩初测通过（来自用户截图证据）**。
- 截图不能单独证明模拟TTS已实际发声，也不能证明手机前置、USB摄像头或三路切换成功；这些保持待测。
- 下一项：用户切换到USB外接摄像头，检查实时画面、点击取图Toast和切回手机；其次测试前置镜头。
- 照片中盒子小字较远，实际云端OCR/VL连接后需要评估1280长边压缩与识字率，必要时任务场景提高分辨率。
