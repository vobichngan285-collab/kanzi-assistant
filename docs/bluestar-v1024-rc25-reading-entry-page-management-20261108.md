# 蓝星向导 RC25 v1.0.24：极简阅读首页与逐页书架管理

日期：2026-11-08（Asia/Taipei）。仓库：vobichngan285-collab/kanzi-assistant。
分支：`feat/bluestar-125-reading-entry-pages-actions-20261108`（基于 RC24 已验真代码 HEAD `3a9ab992b413331b0ebd027d119748938febf19e`）。

## 用户确认需求与取舍

用户在本轮对话中明确确认采用此前横向双卡片图片方案，并要求打开阅读后尽量干净、**删除顶部「选择阅读方式」等说明性小字**，不允许增加无意义提示。此前逐步明确：
- 阅读入口只显示蓝星向导·阅读标题、左侧退出、**横向并列「继续阅读」「新阅读」**两个大按钮，不自动启动摄像头、不自动新建书籍。
- 「继续阅读」应直接进入最近使用书籍的所有历史**书页列表**（有多本书可切换书架），底部「继续阅读」才重新开启摄像头**实时预览**；必须再次手动点击屏幕才拍照，成功新帧震动、相机关闭、旧 OCR/C 模式按行朗读并自动保存文本。
- 「新阅读」先填写书名并创建新书，再进入同样的手动相机预览流程。
- 绝不调用 AI 判断「第一章、第二章」；仅每次 OCR 成功保存生成一页，按照摄入的顺序编号，名称默认「第1页」「第2页」等。
- 书页**轻点**打开已保存的 OCR 原文，不再调用云端或拍照，页面底部仍保留 RC24 的「下一张｜暂停/继续｜重新开始」。
- 书页**长按**底部弹出固定三项，严格顺序「标记/取消标记」「重命名」「删除」，被标记的页卡为红色，重命名不改变原文，删除仅作用于指定页且需要二次确认，三种修改均保存到本机。
- 自定义豆包/DeepSeek 等第三方 API 网址、模型名、独立密钥属于后续 RC26，**不在 RC25 改动模型请求内核**。

## 实际修改实现和保护

本次变更代码基于 GitHub 原仓库，**真正编译到 APK 的精确源码 HEAD `1b29d56ec55e8030dfceb1d34ee7748e79deb7a5`**。原版本历史与 release 都未覆盖。

- `ReadingScreen.java`：入口 `showEntry()` 只有标题与横向 `continueBook` / `createBook` 两个可点击大卡，无「选择阅读方式」说明、不访问摄像头。 `showBookPages(id)` 以捕获顺序展示本机历史页，红色区分标记，点击 `showSavedPage`，长按进入真正底部 `Dialog` 三行动（仅标记、重命名、删除；点外侧可取消）；删除二次确认。页目录底部「继续阅读」只调用原 `enterPreview`，保持 RC23 真实视频预览+二次手动快门；RC24 书页正文底部三键和右上角隐藏功能菜单保留。旧 `showContents` 入口改为书页列表而不再自动推断章节。
- `ReadingLibrary.java`：JSON 每页增加本地 `label`（页名）和 `marked`（红色标记），读取旧版 JSON 缺字段时安全回退为默认「第N页」/未标记；`renamePage`、`toggleMark`、`deletePage` 用现有 `AtomicFile` 全书原子保存，只更新指定页、不改 OCR 原文。新保存页不调用 `ReadingRules.chapter`，而是 `Page(nextNumber, text, "", ...)`。独立持久化 `nextPageNumber`，即使删除末页，也不回收已用编号、不会错误覆盖其他页。导出 TXT 使用显示名称。**旧版本已有相同完整原文的连续页依然受 RC22 沿用的防重复保存保护；这是当前唯一不按照片数增加页码的保守特殊情况**。
- `MainActivity.java`：去除原点「阅读」后隐式创建「我的第一本书」及自动进入实时摄像头的入口逻辑，改为只打开阅读首页。通过 `lastBookId()`、`selectedBook(id)` 复用旧 `reader_last_book_v1` 偏好，在已有书籍中优先进入上次选择的书。**未更改**原相机控制、USB UVC、蓝星 AI 对话、`CloudVisionClient`、`ReadingNarrationQueue`、OCR 专用 Prompt、拍照震动和音效、云端模型列表或其他任务。
- `app/build.gradle`：独立测试包 `com.vobich.bluestar.rc25`、versionCode `34`、versionName `1.0.24-rc25`。
- `.github/workflows/cloud-vision-usb-click.yml`：新增 RC25 的入口双按钮左右顺序/无小字/不自动开相机/无自动创建书、逐页排序、底部「继续阅读」手动预览、长按底部三行动、标红与持久化、稳定序列编号及无需 AI 章节识别静态审查；保留之前 RC22/RC23/RC24 所有图片/camera/voice/ARM64 项目测试。首次 run `42444665857` 的**旧 RC24 断言仍检查菜单标签「章节／页码」**，属于测试规则未同步，已改为「书页列表」再重跑；首轮失败不能误称通过。

## CI 与公开发布已验真

- 严格 Android Actions [Run **42444753150**](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42444753150) **completed/success**，head_sha `1b29d56ec55e8030dfceb1d34ee7748e79deb7a5`，job **128621225064**。日志真实输出 `RC25_READING_CLEAN_ENTRY_PER_PHOTO_PAGES_BOTTOM_SHEET_PERSISTENCE=PASS`、`RC24_READER_THREE_BOTTOM_CONTROLS_AND_OVERFLOW_MENU=PASS`、`RC23_MANUAL_PREVIEW_FRESH_CAPTURE_VIBRATION_TEXT_ONLY_OCR=PASS`、`RC22_READING_SHELF_OCR_CAMERA_TEXT_STORAGE_STATIC_AUDIT=PASS`、`RC22_READING_BOOK_RULES=PASS`、`RC22_READING_SPEECH_QUEUE=PASS`，Gradle Android `BUILD SUCCESSFUL in 2m 28s`，AAPT 实际包名 `com.vobich.bluestar.rc25`、versionCode34、versionName1.0.24-rc25，原始图标/USB UVC so/声音素材与 APK 整体 SHA256 成功核验。CI artifact **13603216826** `blue-star-guide-1-0-24-rc25-arm64`。
- Release workflow 提交 `9ae94de26b17e83b9c2624ea2e0581471f1a4b2b`，Release [Actions **42445082678**](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42445082678) **completed/success**：对精确成功 CI run + 编译 SHA 锁定；从 Actions artifact 取**原件**，验证 SHA256SUMS、ZIP、原生 USB、音效、包名和版本，无重新编译、无重签名、不覆盖旧版本。
- GitHub 公开预发行 [`blue-star-guide-cloud-rc25-20261108`](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc25-20261108)，资产 `BlueStar_Guide_1.0.24_RC25_arm64.apk`，**8,617,706 bytes**，SHA256 `d1f863c3a9556d1fc4fc7e3ed6ea7702b1d3badae3be2d5de6a93147b5efdfc9`，`state=uploaded`。
- 手机浏览器直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc25-20261108/BlueStar_Guide_1.0.24_RC25_arm64.apk

## 真机注意事项与下一步

- RC25 **已通过静态代码、JVM 单元、Android ARM64 编译、APK 资源与真实 GitHub Release 的校验**，但尚未经用户本人 vivo X200 Pro/USB 硬件操作验收。必须实际确认首页两个按钮横向而且没有小字、入口不自动打开摄像头、最近书页目录与长按弹出三项、标红/改名/单页删除和重开 App 后持久化、下一张先进入实时预览并且手动拍照、震动、OCR 与朗读无新回归。
- 因为测试包独立 `applicationIdSuffix '.rc25'` 且每次调试签名独立，**旧 RC24 及更早测试包的私有书籍数据不会自动迁入 RC25**。重要旧书先在旧 App 导出 TXT 并在卸载前另行备份。当前 RC25 不包含旧 TXT 导入功能，不能声称自动迁移。
- 后续 RC26 才评估多供应商 OpenAI-compatible 文本/图像 API URL、Key、模型标识的自定义配置；先验收 RC25 阅读稳定性，避免混动调试。
