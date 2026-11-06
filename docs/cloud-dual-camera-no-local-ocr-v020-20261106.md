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
