# v1.0.86：快速拍照成功叮声、C110实时PixelCopy叮声和双相机实际尺寸/耗时诊断

基线：已发布v1.0.85 SHA `d27b336e4ec1d37270fa18d48a9412790a5fbd95`。授权来源：用户真机验证手机高清按钮可先拍照后得到文字识别；但新快速拍照无金属叮声，C110独立实时预览点击屏幕快速取帧无金属叮声；导出的运行诊断只有USB后台pause，不能核对真实JPEG原图和实际云端上传尺寸；底部用户要直接看实际拍摄分辨率、上传JPEG分辨率、拍照耗时与云端识别耗时，不要原本“已识别：朗读文字，取帧到识别…”冗余说明。明确授权“修复后逐项检查，成功再发布”。

具体改动：
- `QuickPhotoActivity`复用已有真实`CaptureDingFeedback`（同音频资源`c_metal_clack_ding.mp3`、同`shutter_feedback_sound`设置），手机`InternalCameraController.requestStillPhoto()`回调**真实JPEG成功**后一次`photoSucceeded()`，失败、入页面、云端返回均不播放。C110通过不变的`C110QuickShotController`保留原1001取图成功后已有叮声。
- `C110CameraActivity`让`C110FrameReader`只在用户点击视频且`PixelCopy.SUCCESS`、真正获取新画面后通知`captureDing.photoSucceeded()`一次；失败、旧请求、重复快速点击均不响；原C110实体1001成功叮声仍原样保留。不修改`CaptureDingFeedback`音效本身，也不改9011机身静音。
- `QuickPhotoActivity`底部状态保留单块，只显示拍摄来源/真实原图尺寸/实际上传JPEG尺寸/物理取图时长/云端网络识别时长；未测到明确“待测”，不再把任务名称、泛化OCR状态与下载耗时混写。真实`raw_width_px/raw_height_px`来自Camera2`StillListener.onJpeg`或C110原厂`Photo.width/height`；手机云端尺寸为`PhoneStillJpeg.prepare`最终byte[]的宽高，C110云端尺寸来自`C110ZoomFrames.limitForCloud`。手机拍照时长为点击至真实JPEG回调的单调时钟间隔，C110为`Photo.totalMs`；“识别耗时”明确为`CloudVisionClient.Result.networkMs`（含网络上传与服务端返回，不代表TTS已外放），不能用原来模糊“取帧到识别”替代。
- 只给`C110FrameReader`增加可选拍照数据监听器；快门观察通过`C110QuickShotController`转接，仅`QuickPhotoActivity`订阅，因此其余读取页面不出现无关UI。手机拍照创建`RuntimeDiagnostics.begin("phone_quick")`，写入原图高宽/拍照耗时；`recognizePreparedPhoneJpeg`新增传入同一诊断会话、写入最终上云JPEG宽高/字节数/云端上传和网络时间；C110原有`c110_photo`诊断追加`photo_total_ms`（只有新快速拍照入口传入），原上传JPEG/网络记录不变。日志仍匿名、最近50次/7天/每次至多24数字事件，无照片文字音频密钥。
- 页面保留v1.0.85的双按钮、当前任务和4列桌面；手机真实近8MP物理JPEG和已有1440P/原图/1080P上传设置完全不动；C110原厂1001、3012、实时LibVLC、移动流量云端、wifi自动连接、各项任务/语音设置/USB代码不动。新source切换不能取消尚在获取C110实体JPEG的物理快门；仅可以取消旧云端识别。
- 失败时仍展示必要错误文字，成功时不再打印冗余任务描述。所有显示数值必须来自真实JPEG和真实请求回调，不能用默认设置伪装。

验证要求：白名单严格对比v1.0.85源，版本号95，JDK17+ARM64 Gradle全项目成功，原有纯Java regression/供应商/USB补丁/模型/真机约定、APK唯一包名、唯一Home启动入口、正确LibVLC和金属叮资源、SHA256；CI不能验证设备扬声器是否真实出声。真机覆盖安装检查（1）手机JPEG回调叮声；（2）C110视频成功PixelCopy叮声；（3）C110实体高清1001原有叮声不回退；（4）手机真实原图尺寸、经过设置转换后的上传尺寸、拍照耗时和云端网络耗时均实测出现于底栏及诊断；（5）C110原图/1440P差异；（6）OCR/TTS与相机wifi自动连接正常。音效前提：原设置中的`shutter_feedback_sound`打开、手机系统音量可播放，SoundPool启动回调不等于耳朵确实听到。
