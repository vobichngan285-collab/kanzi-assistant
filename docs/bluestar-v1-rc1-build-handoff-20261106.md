# 蓝星向导 1.0.0 RC1 — 正式版功能集成 / 编译交付记录

- 日期：2026-11-06（Asia/Taipei）
- 构建源码分支：`exp/cloud-vision-usb-click-v1-20261106`
- 源码提交：`c69ff9c8a2092643e2754eefd6c675a30c3d6a1f`
- GitHub Actions：**SUCCESS**，run `42190246492`，job `127800187819`，所有 12 个主步骤（静态审计、单测、原生 USB 补丁、Gradle arm64、APK 校验、上传）全部成功
- 产物 artifact：`13489167506`，名称 `blue-star-1-0-0-rc1-arm64`
- GitHub 构建和下载：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42190246492/artifacts/13489167506
- 文件名：`BlueStar_1.0.0_RC1_arm64.apk`；调试签名 APK，仅供真实手机验收，不是商用正式签名发行版
- APK 的 SHA256：`ca079d9887a4e3b8384f33a7927202e0e6d7c16a749e50f311ef8d7f8c0eb09a`
- 本次执行程序直接解包检验：`sha256sum -c SHA256SUMS.txt` PASS；APK 内含 `res/drawable-nodpi-v4/bluestar_icon.webp` 和 `lib/arm64-v8a/libUVCCamera.so`；大小 8,380,827 bytes。
- Android：`applicationId 'com.vobich.bluestar'`，versionCode 10，versionName `1.0.0-rc1`，arm64-v8a，targetSdk 35，minSdk 26。包名与 v0.5.0 的 `com.vobich.bluestar.cloudprobe` 不同，可以并排安装，但用户需要重新在新应用中保存自己的百炼 API Key。

## 本次已实装的功能（静态 / 编译验证，不等于真机通过）
1. 蓝星地球图标 + 统一应用名称“蓝星向导”、深蓝摄像头UI、底部增大的任务 / AI对话 / 设置三个按钮，点击相机画面直接抓拍并识别；不再显示阻碍追问的识别结果模态弹窗，结果可在设置中查看全文。
2. 左下任务键才显示无外框、轻遮罩、三列桌面式任务浮层（最多 3×3，分页；选择立即关闭）。五项系统任务：朗读文字、餐厅菜单、医院科室、店铺招牌、商品信息；加自定义任务入口；删除实验旧逛商场 / 场景识别。
3. 设置中五个预设任务的提示词能独立修改、保存、恢复默认，影响真实云端请求；自定义任务可增删改、改图标，默认提示词按用户批准规格固定。
4. 独立保存手机前置、后置及按 USB VID/PID 区分的摄像头旋转0/90/180/270、镜像、翻转、缩放；已知 EBA4:6579 用户 USB 型号默认90°（如有旧设置会迁移）。
5. 双指缩放（1–3x）：Camera2 的 repeating request hardware crop 同步 Preview 和 ImageReader；USB 原生预览 TextureView 缩放，提交云端前基于同样的方向/镜像/缩放裁切单张 JPEG；多指手势禁止误拍；手机变焦请求节流。
6. 单击AI对话：先有照片识别结果才允许追问；申请 RECORD_AUDIO 并使用手机 Android SpeechRecognizer，识别结束自动提交，云端实际携带上一张JPEG与识别内容、多轮对话最近三轮文本，流式回复与Android本地TTS。若无ASR可用，提供文字输入回退。用户不需长按，在线配音未集成。
7. 手机本地TTS引擎可选、跟随引擎语速或独立0.5–3.0倍、试听；双提示音/短振动可各自设置。云端OCR（qwen-vl-ocr）和视觉AI（qwen3-vl-plus）保留 v0.5.0 较快首句机制、JPEG质量自动选择等核心；实验版Flash仍为用户手动切换。
8. 密钥使用原有本机 Android Keystore 加密；不会在仓库保存用户密钥。在线AI可能消耗Token，必须依用户的“用完即停”设置控制；公开服务必须后端代理、用户鉴权、费用限制。

## 验收与风险
- **已验证**：GitHub静态安全和源码范围审计；Java单元测试（SpeechChunker、ImageQualityPolicy、NV21方向旋转回环）；pinned AUSBC 3.6.0原生补丁编译；Gradle arm64 APK成功；APK资源、签名构建、SHA256归档校验。
- **还未真机验证**：vivo X200 Pro 的正式UI尺寸和按钮可点性、摄像头后/前/USB运行稳定性和90°方向自动恢复、USB图像水平/垂直镜像与发送给AI的方向一致、手机硬件变焦和USB数字变焦截图区域一致、连续拍照防重复、TTS速度与ASR权限/麦克风自动终止、云端图像追问连续会话、硬件兼容性。发现错误应按用户反馈修复，不得称“已经正式上线”。
- 一次本轮编译中出现CI脚本仍检测v0.5旧产物名（虽Gradle成功但归档失败），之后已修复；一次Java字段前向引用导致编译失败，已移到方法中修复，再构建全部通过。多轮中间产生历史CI失败不代表本版失败。
- **未做**：阿里/火山在线TTS、眼镜接入、熄屏USB/音量键触发、服务器密钥代理、Google Play/安卓正式签名。

## 用户安装建议
1. 下载安装 `BlueStar_1.0.0_RC1_arm64.apk` 并安装在 vivo X200 Pro 上；不需卸载 v0.5.0 实验包。
2. 开启摄像头和麦克风权限（麦克风仅AI对话时启用）；设置里重新输入用户自己的百炼 API Key，切勿发送给ChatGPT。
3. 先用手机后摄点击画面做一次 OCR；再点“任务”测试预设/编辑，点中间 AI对话提问，最后接USB检查90°恢复与双指缩放。
4. 若遇到异常，请保留具体操作步骤、当前手机或USB来源、是否旋转/缩放、截图但不要包含API Key。先排查，不直接重写已稳定的相机底层。
