# 蓝星向导 RC23 v1.0.22：读书预览手动拍照、震动确认与 OCR 文本修复

日期：2026-11-08（Asia/Taipei）。仓库：vobichngan285-collab/kanzi-assistant

## 触发问题 / 用户确认

用户实装 RC22 阅读模式，提供真实截图：已保存第2页的文字主体几乎全是形如 244,346,21,59,82、320,377,14,157,83 的五数字逗号分隔行；而「下一张」立即拍照，无法提前调整书页位置。用户明确规定简化最终交互：
- 点击「阅读」直接显示实时预览，可对准书页。
- 点击「下一张」仅切换回实时预览，必须等用户**再次点击画面或拍照键**才取得新照片，绝不能自动拍。
- 用户成功拍照应有实际振动确认；成功取得有效新帧后停用所选 Camera2/USB UVC 摄像头，再执行已选 OCR 模型识别、C 模式朗读、自动将返回文本存入本地书架；不保存录音和照片。此前蓝底白字页面和书架功能保留。

截图五数字行符合图像文字定位常用的 [center_x, center_y, width, height, angle] 形式，但**没有服务端原始载荷，不能完全证明当时产生数字的唯一原因**。阿里云 Qwen-OCR 官方文档确实区分高级定位（坐标）与通用 text_recognition（纯文本）；OpenAI 兼容接口需按文档传任务专用 Prompt：https://help.aliyun.com/zh/model-studio/qwen-vl-ocr

## 修改的实际代码

基线：RC22 独立分支 feat/bluestar-122-reading-library-20261108 HEAD 9491180ee79f8a2faee03a4aec81262c292d6b9c。
新增修复分支：fix/bluestar-123-reading-preview-ocr-text-20261108。
**最终实际 Android ARM64 编译源 Commit：c039af8a88fd692d5eb8d4166174201500b7a0d6**。

仅调整七个相关源码/配置文件；没有创建新仓库，没有更改 RC19/RC20/RC21/RC22 旧 APK、离线 ChineseOCR-Lite/PP-OCR/CRAFT 基准和本地 OCR 模型：

1. ReadingScreen.java：增加透明实时相机预览层和可点的全屏拍照区域（另有底部大号「拍照」键），仅用户主动触发 shutter；返回书页可取消，章节、蓝底白字书架及大号「下一张」按钮保留。「下一张」只调用 enterPreview，不直接请求照片。
2. MainActivity.java：拆开原自动拍摄逻辑为 enterReadingPreview（启动实时相机、显示预览，绝不采集照片）和 takeReadingPhoto（收到用户点击后请求一个**更晚的 Camera2 或 USB NV21 帧**）。手机 onPreviewStarted 不再定时触发 requestFreshFrame；USB callback 只在用户点击之后读新帧。防止 USB onConnect 与 resumeUsb 并发重复 open；检查前后台权限/USB断线、旧帧缓存及请求 token 防串。成功新帧后进行一次 50ms VIBRATE 震动（不依赖拍照振动设置），原金属提示音仍按声音设置，随后停手机 Camera2 或 USB 原生摄像头；只有拍照后才网络 OCR。退出阅读恢复用户之前选择的相机。
3. ReadingRules.java：复用现有 OcrModelCatalog.PROMPT 给其它 OCR 选项；对专用 qwen-vl-ocr 使用官方纯文本任务字串 Please output only the text content from the image without any additional descriptions or formatting.，不要求框坐标；新增保守五数字框行识别、坐标型整页识别拒绝；原文不可胡乱补全。
4. CloudVisionClient.java：仅 reading-page + 专用 OCR 使用官方纯文本 prompt，原常规「朗读文字」任务和三种其它 OCR 模型请求不受影响；坐标型整页响应不得在结尾传给 TTS 或存书。原单图、单次云端请求、RC21 C 逐行流式 TTS 保留。
5. ReadingRulesTest.java：增加用户截图同型五数字、括号型坐标、正常中文正文和数字混合正文、纯文本 Prompt 的 Java 单测；不简单屏蔽所有数字以避免误删书页内容。
6. app/build.gradle：独立包 com.vobich.bluestar.rc23、versionCode=32、versionName=1.0.22-rc23，与旧测试包并存。
7. .github/workflows/cloud-vision-usb-click.yml：RC23 严格断言进入预览绝不触发拍照、唯一拍照入口由用户点击、震动反馈、坐标型输出拦截、单次网络 OCR、Camera2/USB停止调用和原功能不变；原有 unit/native/apk 核验全部保留。

注意：RC23 独立包的书架是其自有的私有存储，不会自动读取 RC22 旧包保存的内容；旧包历史没有被自动删除。重要文字建议从旧包先导出 TXT。

## 严格 CI / 发版验真（实际核实）

- Android CI https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42393584828，最终 completed/success，head SHA **c039af8a88fd692d5eb8d4166174201500b7a0d6**，job 128466161544。日志：RC23_MANUAL_PREVIEW_FRESH_CAPTURE_VIBRATION_TEXT_ONLY_OCR=PASS、RC22_READING_SHELF_OCR_CAMERA_TEXT_STORAGE_STATIC_AUDIT=PASS、DUAL_CAMERA_CLOUD_AI_NO_LOCAL_OCR_AUDIT=PASS、RC22_READING_BOOK_RULES=PASS、RC22_READING_SPEECH_QUEUE=PASS、RC22_READING_FULL_PAGE_LOCAL_BOOKS_TTS_QUEUE_TESTS=PASS；Gradle **BUILD SUCCESSFUL in 2m 32s**。安装包 APK structure/USB arm64 libUVCCamera.so/图标/原两个音效/应用包名 version=32 检查成功。前面的中途运行 42393378397、42393465303 因后续重要修正取消，不能当作有效成功源提交。
- 原始成功 CI Artifact id **13582696487**，name blue-star-guide-1-0-22-rc23-arm64，包含原 APK、SHA256SUMS.txt、badging.txt 和 apk-entries.txt。
- Release workflow 提交 e0ecf1214fa8d032b89fbfee81a02ca32721e5fe，发行 Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42393956642 completed/success，**核对原构建 Run/Commit、原件 SHA256、ZIP、原生 UVC、音效及版本，不重编译也不重签名**。
- Public GitHub prerelease https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc23-20261108，asset BlueStar_Guide_1.0.22_RC23_arm64.apk 已上传 state=uploaded；大小 **8,617,706 bytes**，SHA256 **e63aca13b6b1b622dba2b02f53de75582d6360dbfad26064f1601058520fc6db**。
- 手机浏览器 APK 直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc23-20261108/BlueStar_Guide_1.0.22_RC23_arm64.apk

## 不能靠 CI 代替的手机与 USB 验收

本轮验证了源码静态逻辑、Java 单元及实际 Android 编译/成品校验；**没有真实 vivo X200 Pro 或 USB 外置摄像头的硬件闭环验证**。用户安装后需重点测试：
- 直接点「阅读」是否出现可见实时预览，不自行拍照；用书本对准后点击预览画面，得到震动/音效，随后相机确实停止、蓝底白字识别并朗读；
- 「下一张」是否只唤醒实时预览，等待用户再次点击才拍摄下一页；新页文本非旧页缓存；多次操作能稳定关闭、重启外置 USB 相机；
- 正文是否为真实中文原文，不再出现 5 字段框坐标串。如果 OCR 仍返回坐标，应明确提示不保存；测试含数学数字的普通正文不能被误删；
- 已保存书页可在离线状态下打开、找到页码、使用可用的离线 TTS 朗读，以及 TXT 导出、章节和退出/返回是否正常；
- 整页过长、复杂双栏、小字、模糊书页等仍可能错误/截断；新包真实 TTS 扬声器、震动和 USB 生命周期需由用户验证。根据真实问题再做下一步最小修复，而不重新跑旧 CRAFT 和 RC20 A/B/C 实验。
