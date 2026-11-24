# v1.0.85 快速拍照双入口：基于已真机验证成功的 v1.0.84

授权：用户明确选方案A，全部12项一次完成；在用户确认的最新图稿基础上，页面顶部显示当前任务+“选择任务”；中间两枚大按钮手机高清拍照/C110高清拍照；底部只显示状态。移除冗余像素描述和点击后返回的教学说明。点击选择任务必须原样打开现有4列 TaskDesktopActivity（每页12格、翻页/长按编辑/拖动保持），点任务自动finish返回上一个QuickPhotoActivity，不返回首页。只有按手机拍照才启动Camera2，结束后马上stop，继续停留快速拍照。C110沿用原厂1001真实单张快门，C110FrameReader的原图/1440P偏好、TTS、千问OCR、摄像头操控和自动WiFi会话保留。

WiFi：删除首页"运动相机 Wi-Fi"多余选项和独立首页网络状态，**仍在HomeActivity onCreate构建C110WifiBootstrap并在onResume调用startAtLaunch()**，绝对不能破坏用户已实测有效的自动连接。设置→相机中删除单独"搜索/更换运动相机"行，只留下"连接相机Wi-Fi"；点击唯一入口提供"连接已记住的相机"/"重新搜索相机Wi-Fi"选项，首次没有记录直接搜索；自动连接开关保留。

相机：手机后台采用已存在的 InternalCameraController.requestStillPhoto()真实Camera2 JPEG，直接复用既有 PhoneStillJpeg.prepare旋转/镜像及 PhoneStillUploadPolicy档位（默认1440P），不改变真实约8MP拍照/1400P上传。为简洁双按钮页新增独立QuickPhotoActivity，手机后台SurfaceTexture仅在用户点按钮后启动，真实JPEG回调后立即停相机；手机与C110两条拍照相机互不自动启动。云端复用已有 C110FrameReader 统一当前任务、OCR/视觉模型、过滤、朗读引擎；手机可通过有互联网的WiFi或蜂窝网（明确排除C110本地热点）上云，C110仍须通过蜂窝网络。下一张拍照可取消上一次云端OCR结果，但不取消尚在获取JPEG的物理快门。

只基于v1.0.84发布SHA abcc1e13b0a11236d0fe3c9338e55b645ca7854e新实验分支 exp/quick-photo-two-sources-ui-20261124；原有C110WifiBootstrap/原厂1001/LibVLC/USB、云端模型配置、TTS、相机原图/1440P偏好、TaskCatalog数据不改。versionCode94 versionName 1.0.85-quick-photo-two-sources。

需CI验证：Android arm64完整编译、单入口、唯一debug包、Resource+LibVLC+UVC+声效检查、回归单元测试和GitHub Compare白名单。真机必须覆盖安装保持已保存WiFi和云端密钥，验证自动连接仍成功、页面打开无摄像头活动、左按钮真8MP原始Camera2/JPEG上传1440P且正确朗读，右按钮C110原图/1440P及1001朗读，4列任务桌面返回正确，点击新一张替代旧云端，设置唯一WiFi入口可重搜；CI不能代替真机验收。
