# Blue Star Guide 1.0.8 USB preview producer buffer geometry 修复记录

日期：2026-11-06（Asia/Taipei）。本文件为**待真机测试的实验构建记录**，不是正式发布成功声明。

## 本轮证据与目标

用户实测1.0.7：
- USB 1920×1080 MJPEG，GL提交初段稳定约59.9~60.9次/秒，说明1.0.7的帧率选择修正确实使60FPS进入GL处理；但 **GL提交≠可见屏幕刷新率**。
- 开机数秒到十余秒后，`uvcJava`、`glInput`、`glSwap`同时从正常频率跌至0~3FPS，存在连续3~4秒以上无新帧，且`OCR=false`、EGL错误为0。说明与OCR推理忙没有直接因果证据，不能单纯怪OCR。
- APP画面中央、右侧呈硬边界大面积灰色块。之前版本已出现同类现象，不能说1.0.7新帧率代码才创造了灰块。无需让用户重复提供已上传截图。
- 1.0.7 **实际稳定性失败**；30FPS基线的1.0.6应继续保留。

## 根因候选与精确修复

原库 `ernestp/AndroidUSBCamera 3.6.0`：
- `MultiCameraClient` 创建`RenderManager(ctx, previewWidth, previewHeight, previewCb)`，把真实摄像头输入宽高放在构造器的`surfaceWidth/surfaceHeight`。
- `startRenderScreen(screenWidth,screenHeight,...)`中的`w/h`和`MSG_GL_CHANGED_SIZE`的`mWidth/mHeight`是屏幕上`TextureView`的显示尺寸；实测可为2240×1260，区别于1920×1080原始视频。
- 原库在初次建`SurfaceTexture`时`setDefaultBufferSize(w,h)`，之后尺寸改变又`mCameraSurfaceTexture?.setDefaultBufferSize(mWidth,mHeight)`，错误地拿显示尺寸配置原生视频生产者缓冲；但C++ `UVCPreview::prepare_preview`反而用`ANativeWindow_setBuffersGeometry(...,frameWidth,frameHeight,...)`准备真实摄像头帧。输入缓冲几何不一致，可能导致未写入区域/复制负担/显示异常。当前只能说**高度可疑的根因，真机未最终证实**。
- 本轮仅把上述**两处生产者缓冲默认尺寸**改为`surfaceWidth,surfaceHeight`，保持真正显示的`mCameraRender?.setSize(mWidth,mHeight)`、`mScreenRender?.setSize(mWidth,mHeight)`、`mCaptureRender?.setSize(mWidth,mHeight)`完全不变，并添加`BSG_USB_INPUT_BUFFER_GEOMETRY_108`日志标记。
- 不改图像镜像、FIT、旋转、颜色、USB协商、相机焦点、OCR模型、阅读逻辑或TTS。
- 完全保留1.0.7的最高支持FPS协商`UVC_FPS_NEGOTIATION_107`与GL绘制消息去积压`BSG_GL_DRAW_COALESCE_107`，以及1.0.6的原生OCR回调≤20fps`UVC_NATIVE_OCR_RAW20_GATE_ENABLED`。USB约30FPS时Java RAW约15FPS是采样预期，并非底层源帧率。

## 代码与独立版本

- 分支：`exp/usb-display-stability-108-20261106`；来自`exp/blue-star-usb60-stutter-107-20261105`的已编译实验版。
- 主补丁：`scripts/patch_usb_buffer_geometry_108.py`；用唯一锚点且失败即停方式修改CI中固定AUSBC 3.6.0第三方源码，不能重复应用。
- 回归：`scripts/test_usb_buffer_geometry_108.py`，验证source和screen宽高独立、两处producer buffer调用、GL渲染/完整源帧没有改变，以及旧FPS/GL/RAW20代码存在。
- 包名`com.vobich.kanzi.experiment.dev108`、版本`1.0.8-development`、versionCode108，独立于1.0.6与1.0.7安装。
- GitHub Actions工作流：`.github/workflows/blue-star-usb-display-108.yml`。只上传Actions Artifact，不生成GitHub Release、不覆盖稳定APK。
- 第一次运行42062243521：原生Android构建成功，但严格APK审计把包名误写成dev107，按设计**未上传产物**。纠正严格验收的预期包名后完整重新构建成功。
- **成功运行**：`42063454393`，源码commit `a527c1751846d3490bf8f9dfb407f7906e8dbaaf`；链接 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42063454393 。
- APK审计：实际原生库编译、arm64、无应用INTERNET权限、v2签名、版本/包名、原RAW20+FPS日志标记、Kotlin GL标记及本轮Buffer几何标记均通过；原有OCR模型哈希、语音/布局/图标、手机相机及方向等自动化检查均通过。
- Artifact ID `13432599034`，网页 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42063454393/artifacts/13432599034 （到2026-11-15 UTC约16:24过期）。
- 解压后的APK `BlueStarGuide_Experimental_1.0.8_arm64.apk`大小 **79,382,564 bytes**，SHA256 **`021f687ae9671e646822678433425a603bb9c4361a1e7dba3e4071c4b5e49d93`**。
- **已从GitHub Actions下载实际Artifact ZIP到容器，重新解压APK，独立核对SHA256SUMS和DEX/so字节中的新旧标记，全部PASS**。

## 真机验收标准（未完成）

- vivo X200 Pro + 同一USB镜头，先关OCR，持续2~5分钟观察是否仍有灰色大块、视频冻结、glInput/glSwap突然归零；然后开OCR，再持续5~10分钟观察帧率/发热/文字识别。
- 成功要求**灰色区域消失，持续流畅、不过几秒就停帧，并且60FPS协商与可视画面一致**；不能只拿刚启动的60FPS与通过编译当作成功。
- 仍有冻结则承认1.0.8验收失败，回退1.0.6稳定基线；优先定位UVC底层数据抵达与MJPEG解码线程，尽量避免复杂补救链路，不能未经实测就声称已彻底修复。
- 所有代码/实验仅在独立分支，不将其自动合并入main。需要用户明确同意后才可正式发布。
