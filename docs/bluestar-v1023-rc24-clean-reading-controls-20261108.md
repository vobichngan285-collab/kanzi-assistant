# 蓝星向导 RC24 1.0.23 — 阅读界面三按钮横排与右上角菜单

日期：2026-11-08（Asia/Taipei）
仓库：vobichngan285-collab/kanzi-assistant
源码分支：feat/bluestar-124-clean-reading-controls-20261108
从既有 RC23 完成版分支 HEAD `5e1dd89409ce50be3f6f899db259dd7b44da3149` 隔离派生。

## 用户明确确认的界面

用户提供 RC23 真机阅读页面截图，认为「暂停朗读」「重新朗读本页」「下一张」「书架」「章节/页码」「导出文字」「删除这本书」上下堆叠杂乱，先要求设计效果图并审核。确认后明确下达执行：
- 页面**底部一排三按钮**：从左到右**「下一张」「暂停／继续」「重新开始」**。
- 右上角**☰ 三横杠菜单**，默认收起，点开显示「书架」「章节/页码」「导出文字」「删除这本书」。
- 阅读界面深蓝背景、白字正文与原书页状态保留，正文滚动区扩展。
- 一切此前的**阅读逻辑保留**：下一张只开实时预览，用户对准书页再主动点击拍照、成功新帧产生震动，关闭 Camera2/USB 后发送一次已选 OCR 模型任务、C 模式按行朗读、自动保存本地文本，不产生音频文件。

## 改动范围、逻辑/冲突检查

**真正 Android ARM64 APK 编译源 SHA：`50c271c47d9c0c77a0d830efe74c282658658163`。**

与 RC23 基线比对（ahead 3 commits）仅修改三个文件：
1. `ReadingScreen.java`：顶部左返回按钮，标题和右侧 ☰ 锚定 `PopupMenu`；四个旧的次级功能进入菜单，删除需二次确认、章节照旧弹对话框、导出仍使用 Android 原系统文档选择器。仅在书页/识别中/失败页显示菜单，书架入口状态不误弹删除选项。
2. `ReadingScreen.java`：`renderPage` 改为 `LinearLayout.HORIZONTAL` 及三个等权重底部按钮。代码创建顺序及容器添加顺序均为：`nextButton=button("下一张")` → `pauseButton=button(paused?"继续":"暂停")` → `restart=button("重新开始")`。未保存页失败时「下一张」可以重新进入预览；云端尚未完成保存时禁止「下一张」阻断保存；正在按行识别时可暂停/继续但禁止重新朗读不完整半页；已完成页可重新从头朗读本页，暂停状态切换还会更新 TalkBack 描述。
3. `app/build.gradle`：独立安装包 `com.vobich.bluestar.rc24`、versionCode33、versionName1.0.23-rc24。
4. `.github/workflows/cloud-vision-usb-click.yml`：RC24 新静态审查要求三按钮创建/加入顺序严格正确、顶部 PopupMenu 四项存在且正文页无旧底部次级大按钮、危险操作不提前释放，复用全部 RC23 摄像头、OCR、TTS、安全回归、ARM64 构建及 APK 资产核查。

**没有改动** MainActivity、CloudVisionClient、ReadingLibrary、ReadingRules、ReadingNarrationQueue、Camera2/USB UVC、原有任务或语音对话。禁止重复 RC20 A/B/C 和 CRAFT 已完成实验。

## GitHub Actions 事实核验

- [RC24 Android 构建 Run 42437657106](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42437657106)：`completed/success`，head_sha `50c271c47d9c0c77a0d830efe74c282658658163`，job `128599874213`，包括自定义 RC24 三按钮/菜单审查、原 RC23 手机 USB/手动拍照/震动/OCR 规则回归、旧 Java 单元测试、Android arm64 Gradle 编译、APK launcher/音效/原生 UVC/包名版本/sha 校验。原始 Artifact ID `13599977574`，`blue-star-guide-1-0-23-rc24-arm64`。
- 新独立 Release workflow Commit `93169b1dc6818f8420c1796e8311bbae798687e2`， [Release Run 42437906796](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42437906796) `completed/success`。仅验证上述**成功 CI 的精确源码 SHA**并原样下载原 APK，核对 SHA256/ZIP/包名、arm64 UVC、图标、原两个音效后发布；不二次编译、不重签名、不覆盖旧版。
- GitHub public prerelease [blue-star-guide-cloud-rc24-20261108](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc24-20261108) 已核实 `draft=false, prerelease=true`；唯一资产 `BlueStar_Guide_1.0.23_RC24_arm64.apk` `state=uploaded`，**8,617,702 bytes**，sha256 **`f8b82a00b23bcd19b59301ad3d5f72436b5f08d8d8c2aa0d7f585911550a1ccb`**。
- APK 直接下载： https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc24-20261108/BlueStar_Guide_1.0.23_RC24_arm64.apk

## 下一步与事实边界

- 新 RC24 尚未经用户本人于 vivo X200 Pro 实际安装点按或外置 USB 实机操作验收；CI 静态与编译证明**代码及资产可构建**，并不证明真机 UI 对齐、TalkBack、摄像头开关和震动实际效果。
- 用户可安装独立 RC24，重新配置本包的 API Key/相机偏好，验证底部三按钮左/中/右布局，暂停继续、本页重新开始，右上角菜单四项；尤其确认「下一张」**只打开摄像头预览**，要再次手动点屏幕才拍摄并震动，成功后停摄像头朗读。
- RC23 与 RC24 不互相覆盖，书籍存在**各自 Android app 私有目录**，不会自动迁移；旧包卸载前如有重要内容要在旧包菜单中导出 TXT。
