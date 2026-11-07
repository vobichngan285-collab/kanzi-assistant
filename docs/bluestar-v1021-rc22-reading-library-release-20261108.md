# 蓝星向导 RC22 v1.0.21：阅读模式与本地电子书书架，严格构建及测试版发布记录

日期：2026-11-08（Asia/Taipei）
原仓库：vobichngan285-collab/kanzi-assistant
独立源码分支：`feat/bluestar-122-reading-library-20261108`
真实 APK 构建 Commit：`525693600ae1a0891c7fbb8c0c2c9d27954d94da`
旧 RC21 原发布保持不变，离线 OCR/CRAFT 研究源不变。

## 用户已明确批准的功能

- 底栏原有 `任务 → AI对话 → 设置` 改为 `任务 → AI对话 → 阅读 → 设置`，不混入可拖动任务宫格。
- 新的「阅读」打开深蓝色 `#091B32` 背景与白色文字的电子书书架，支持新建、查看、打开多本书、页码和明确识别到的章节标题。
- 点击 `拍照朗读`，后续点最明显的 `下一张`；手机 Camera2 / USB UVC 都必须拿到**新鲜帧**，再关闭相机资源、开始云端识别并逐行朗读。没有使用旧照片作为下一张，失败时不写入文字，下一张不重复写入相同最后一页。
- 阅读识别只选用户在「文字识别模型」设置中已选择的 `ocrModel`（默认 `qwen-vl-ocr`），专用任务 ID `reading-page`、engine `OCR`，提示词要求**整张书页清晰可辨的所有文字、自然顺序、原文、分行、不得编造/总结/解释**。不选择普通视觉聊天模型、不改变既有短文字 OCR 提示词。
- 沿用 RC20/RC21 实测认可的 `C` 流式 newline-delimited 完整行送已有 Android TTS，支持暂停/继续/重新朗读。书页输出预算约 3,800 token，超过 20,000 字符拒绝；`finish_reason=length` 拒绝把可能被截断的书页当完整页保存。
- 只保存文字，**不自动保存照片、录音或 TTS 音频**。通过 `AtomicFile` 在 app 私有目录 `filesDir/reading_books` 保存书籍 JSON、页码和章节；断网时可在书架打开已存页，并使用本机可用离线 TTS 发声，不调用云端 OCR/摄像头。经 Android Storage Access Framework `ACTION_CREATE_DOCUMENT` 导出整本书 UTF-8 TXT 至用户自己选择的文件夹。**卸载独立包可能删除应用私有书架，重要书籍请先导出 TXT 备份。**
- 关闭阅读模块恢复先前手机/USB 选中的摄像头；进入和阅读中维持摄像头关闭，拍下一张时单次唤醒、关闭。保留设置、语音 AI 追问、任务宫格、相机画面几何、金属咔叮/嗖声音效和 USB 原生堆栈。

## 源代码核查与修正记录

- 从已完结 RC21 代码 `0e8c896c521a0929344c4f28e826d1615732ce0c` 创建隔离分支，仅新增 `ReadingRules.java`、`ReadingNarrationQueue.java`、`ReadingLibrary.java`、`ReadingScreen.java` 和两类规则/朗读队列测试，修改接线 `MainActivity.java`、云端响应 `CloudVisionClient.java`、App 版本和 GitHub CI/发行流程；没有修改 `TaskCatalog.java` 预设历史、Native USB JNI 或离线 OCR 源码。
- 特别核对手机预览新鲜帧、USB回调原生先开后关防双开、拍完相机停止不只是隐藏预览、旧操作/新操作 `requestVersion` 取消隔离、异步耗时诊断只保存数值、用户隐私及不持久化 JPEG。USB 共享状态用 `volatile` 和原子守卫，减少竞态。
- 原第一次 CI `42390966838`、下一次 `42391050109` 因原静态检查仍要求老式**无条件 USB attach 逻辑**失败；改断言匹配书架模式需要的**受控 reopen**。中间 `42391065663`、`42391307963` 因后续安全修正推送而取消，旧失败、取消不能当成功。最新最终权威 CI 是 `42391373779`。
- 进一步修复云端调用已成功、但本地书籍持久化失败时，不得误计云端调用失败/用量；记录实际成功用量后再尝试本地页保存，失败也明确提示「本页未保存」。
- 本次为源码逐项比对、静态/Java 单元回归、真正 Android 构建与 APK 内容核验；**不可能仅靠 CI 证明手机/USB 真实物理开闭、TTS 实际扬声器已出声或完整 OCR 准确率**，仍需 vivo X200 Pro 真机验收。

## 真正 GitHub Actions 结果（已完成）

- [RC22 Android ARM64 完整构建 Run 42391373779](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42391373779)：`completed/success`；HEAD **`525693600ae1a0891c7fbb8c0c2c9d27954d94da`**，独立包名 `com.vobich.bluestar.rc22`，`versionCode=31`，`versionName=1.0.21-rc22`。
- 详细 CI job `128459543769` 明确输出 `RC22_READING_SHELF_OCR_CAMERA_TEXT_STORAGE_STATIC_AUDIT=PASS`、`DUAL_CAMERA_CLOUD_AI_NO_LOCAL_OCR_AUDIT=PASS`、`RC22_READING_BOOK_RULES=PASS`、`RC22_READING_SPEECH_QUEUE=PASS`、`RC22_READING_FULL_PAGE_LOCAL_BOOKS_TTS_QUEUE_TESTS=PASS`，`BUILD SUCCESSFUL in 2m 33s`；原有镜头方向、TTS、AI、相机控件、USB UVC、4种 OCR 模型、TaskGrid 拖拽、录音和音效原回归通过。
- CI 上传原始 Artifact ID **13581294625**，名 `blue-star-guide-1-0-21-rc22-arm64`，含 `BlueStar_Guide_1.0.21_RC22_arm64.apk`、`SHA256SUMS.txt`、`badging.txt`、`apk-entries.txt`。
- [RC22 官方验真并发布 Run 42391729553](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42391729553)：`completed/success`。发行 workflow 源提交 `0a0bbd6c402df5196f7e138e0093c7d53521453b`，严格先核对准确的成功 CI run 与其源 SHA、原始 APK checksum/ZIP完整性、包名版本、ARM64 `libUVCCamera.so`、图标和两音效；发布**未经重编译或重签名的 CI 原件**；绝不覆盖旧 RC21。
- Public prerelease [blue-star-guide-cloud-rc22-20261108](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc22-20261108) 已核查附件 `state=uploaded`，APK **8,617,702 bytes**，官方 SHA256 **`22e566c575964c011a9d0cfa402a9bbcbc621eda498ec789db65de6632c6e026`**。
- APK **直接下载**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc22-20261108/BlueStar_Guide_1.0.21_RC22_arm64.apk

## 测试版验收流程及下一步

1. vivo X200 Pro 安装独立 RC22，重新设置本包 API Key、OCR 模型/相机偏好（严禁将密钥发送聊天）。确认底栏第四键 `任务/AI对话/阅读/设置`。
2. 「阅读」→「新建一本书」→「拍照朗读」，确认显示蓝底白字，按 C 逐行读，手机摄像头实际停机；再点「下一张」，验证确实唤醒新鲜画面而非旧缓存、读取下一页、停机、自动保存页码。测试 USB 一轮，注意 native 关闭/唤醒。
3. 退出重新进入书架，断网、不开镜头浏览已保存书页、点击本机 TTS 重读。测试章节（只根据显式标题）、删除确认、TXT导出、断网错误提示。
4. 特别检查整页中英文多列排版的顺序、尾行、长输出被截断时拒绝存档以及暂停/继续/连续拍下一张无重复、无卡顿。**真机验收尚未进行，不应当把 CI 通过或原 RC20 C 试听成功等同于 RC22 物理与OCR新模块已实测成功。**
