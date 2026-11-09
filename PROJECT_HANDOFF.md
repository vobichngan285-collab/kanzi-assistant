# 蓝星向导｜当前项目交接（精简版）

**更新日期：2026-11-09（Asia/Taipei）**  
**原仓库：** https://github.com/vobichngan285-collab/kanzi-assistant  
**唯一交接入口：** https://github.com/vobichngan285-collab/kanzi-assistant/blob/main/PROJECT_HANDOFF.md  
**当前项目：** Android「蓝星向导 / Blue Star Guide」云端视觉助手。原「看字助手」纯离线 OCR / CRAFT 研究已由用户明确决定停止开发；不要主动恢复其旧实验。  
**请注意：`main` 负责项目入口及交接文档，不是最新已发布 App 的源代码分支。** 任何开发前均须重新核查 GitHub 实际 HEAD、Actions 和 Release。

## 1. 当前有效版本：蓝星向导 RC25（v1.0.24）

- **最后已记录并核实的发布版：** RC25 / `1.0.24-rc25`，2026-11-08 GitHub 公开 prerelease。RC26 尚未开始编码或构建。
- **有效源代码分支：** `feat/bluestar-125-reading-entry-pages-actions-20261108`。
- **实际参与 RC25 构建的源代码 Commit：** `1b29d56ec55e8030dfceb1d34ee7748e79deb7a5`。本次整理时该分支 HEAD 为 `fe848acf87cec87d8d7cba0c739b7be40e31072b`（后续文档提交），不能混淆分支 HEAD 与 APK 构建 SHA。
- **GitHub Actions 编译：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42444753150 （success；head_sha 与上述构建 SHA 一致）。
- **原 APK 发行 Actions：** https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42445082678 （success；发布原已校验 APK，没有重新编译或重签名）。
- **Release：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc25-20261108
- **APK：** https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc25-20261108/BlueStar_Guide_1.0.24_RC25_arm64.apk
- **包名 / 大小 / SHA256：** `com.vobich.bluestar.rc25` / 8,617,706 bytes / `d1f863c3a9556d1fc4fc7e3ed6ea7702b1d3badae3be2d5de6a93147b5efdfc9`。
- **完整 RC25 代码与验收报告：** https://github.com/vobichngan285-collab/kanzi-assistant/blob/feat/bluestar-125-reading-entry-pages-actions-20261108/docs/bluestar-v1024-rc25-reading-entry-page-management-20261108.md

### RC25 已实现的主要功能

1. 深蓝色阅读入口仅保留「继续阅读」「新阅读」两张横向大卡。点击阅读入口不自动打开相机或创建新书。
2. 「继续阅读」显示最近书籍的书页列表；轻点页可离线查看/重读原 OCR 文字；长按页从底部选择红色标记/取消、重命名、二次确认删除；页码按成功保存次数单调递增。
3. 书页列表底部「继续阅读」或「新阅读」进入手机/USB 摄像头实时预览；用户再次点画面才手动拍照、振动、关闭相机并发送单次云端 OCR，采用此前已认可的 C 模式逐行 TTS，保存文字原文。不保留照片与音频。
4. 已进入正文的阅读页保留「下一张｜暂停/继续｜重新开始」三个底部按钮和右上角四项功能菜单。
5. 保留云端视觉任务、手机 Camera2、USB UVC、原有音效、原始 OCR 文本/语音链路，不能因后续改动破坏这些功能。

**证据边界：** RC25 的源码静态审查、自动测试、ARM64 编译、APK 资产校验与 Release 均成功；交接时仍无用户对 RC25 的 vivo X200 Pro + USB 外置摄像头完整真人交互验收，不得声称真机已验证。RC25 是独立包，不会自动迁移 RC24 的私有书籍、Key；卸载旧包前应自行导出重要 TXT。

### 之前已验证、值得继续保留的结论

- **云端 OCR 流式「C 模式」：** RC20 A/B/C 实验中，720P 一次有效记录 A 模式 TTS onStart=5210ms、C=1650ms，C 约提前 3560ms；用户追加多轮体验后反馈 C 更快、体验满意。RC21 已将 C 作为后续版本默认朗读路径。TTS 回调不代表扬声器首声，不能编造每轮 P50/P95。
- **USB 注意事项：** 旧版 USB 摄像头有持续前台卡顿、后台恢复卡顿、方向和上传照片不一致等历史问题，部分版本做过修复和日志采集；不得将历史 CI 通过误当作所有 USB 设备的硬件稳定性保证。后续修改需覆盖手机 Camera2 和外接 UVC 两种路径。
- **云端模型与密钥：** 已有阿里云百炼兼容接口、多档视觉/OCR 模型、按需任务、追问、朗读队列；后续修改必须保持原功能可回退，不能上传密钥、OCR 私密原文或照片到公开日志。

## 2. 下一阶段：RC26 自定义云端模型（待用户另行授权开发）

**目前仅需求方案，不是已实现功能。本次清理授权不等于授权开发 RC26。**

- 在「设置 → 云端 AI → 自定义 AI 模型」允许保存和切换多组配置：豆包、DeepSeek、千问及「其他」。
- 预设由正式文档核准的 HTTPS 地址；填写真实模型 ID、个人 API Key；支持「测试连接并保存」、删除配置、回退到现有百炼。
- 第一阶段仅支持兼容 OpenAI Chat Completions 的文本及视觉请求；分别校验 `image_url` 图片能力、streaming SSE、返回格式，不能认为文本模型一定支持看图。
- API Key 使用安全存储、配置与目标服务商严格绑定；只允许 HTTPS，防错误重定向、凭证串用，禁止 Key 进入日志；第三方视觉端点须明确提示照片外传。连接成功不等于图片及流式能力全部测试成功。
- 不在 RC26 混入实时语音对话、ASR 或云端 TTS 协议改造；保留 RC25 完整阅读功能、C 模式、USB/Camera2、已存在模型与音效。
- 开发前重新核对提供商最新官方端点/模型 ID；在原仓库独立开发，保持 RC25 原 APK、用户数据与签名体系不被覆盖。

## 3. 已停止开发的旧项目：离线 OCR / CRAFT（最终结果摘要）

**用户于 2026-11-09 明确放弃此线；以下仅用于避免重复旧研究。不要再以它为后续默认开发任务。**

- 最终方向曾是 ChineseOCR-Lite DET + PP-OCRv6 Small REC；v55 用户在 vivo X200 Pro 反馈相较之前整行框和朗读体验改善；v56 加入新字抢读和 REC 批2/宽度分组，构建成功但没有真实 v56 性能验收。v56 源码 Commit `578455359ce80d3cbdba653c4d3464cdec838ae8`，Actions https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41953023321 ，Release https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/x200-cnlite-immediate-recfast-20261105-v56 。v57 有分支 Commit `897a76595195ffa102502e3edd07d7250eb1d6d2`，本轮未确认其正式构建或发布，不能作为最后已验证 APK。
- CRAFT 768 INT8 已完成 vivo 真机 6 轮 × 24 次测试；纯神经网络推理 P50 约 544–665ms，不含完整 REC/TTS。**不重复测试。**
- 固定 36 张 RCTW 图、219 个 GT 文字行，Small 全图匹配 147 行，全图 CRAFT 115 行，Small+局部 CRAFT 149 行；局部仅净增加 2 行，却使 Linux CPU 全流程均值 357.65→1312.31ms（增加约 954.66ms/图）。因此不集成 CRAFT 默认补漏。历史完整报告：https://github.com/vobichngan285-collab/kanzi-assistant/blob/test/craft768-int8-roi-dense-text-v2-20261103/docs/craft768-int8-conditional-local-roi-v1-v2-results-20261103.md
- ChineseOCR-Lite 的 GPL-2.0 及模型权重分发/商业许可资格历史上未全部核清；这些研究包不等于正式商用许可已验收。
- 不再保留旧离线项目的逐版本开发计划、详细失败补丁记录或待做清单于本主交接文件。原历史仍可通过旧 Git Commit 恢复，不重新创建旧项目。

## 4. 开发及交接约束

1. **只使用本原仓库。** 每次先检查真实 HEAD、源码分支、Action 日志与 Release 资产；`main` 是统一文档入口，不把它误认作最新 APK 代码。
2. **用户明确指令后才修改或构建。** 不自动开启 Agent、工作流、重复实验、长轮询、无权限操作；旧离线 OCR 已停止开发。
3. 新任务只修改必要模块；先检查现有代码和已完成实验，避免覆盖 RC25 的源码和 APK。严格区分构建通过与真实 vivo/USB 测试通过。
4. 每次获授权的重要开发/实验完成后更新本文件，记录日期、项目版本、分支、**实际构建 Commit**、Actions/Release、确证结果、真实局限和下一步；通过 GitHub 提交并回读核验。优先更新「当前版本」与简短变更摘要，不再无上限粘贴几万字旧日志；Git 历史承担长期存档。
5. 文档-only Commit 使用 `[skip ci]`，避免徒增构建。不得将密钥、私人照片、OCR 文字原文、音频或敏感诊断内容公开上传。

## 5. 历史恢复锚点与本次整理

- **精简前的完整版交接（不可变 Commit）：** https://github.com/vobichngan285-collab/kanzi-assistant/blob/766ddb312dd6ad64ad5f66bba093def442293fbc/PROJECT_HANDOFF.md （原 1541 行、约 26.6 万字符）。当前 `main` 为便于继续工作而精简；**不改写 Git 历史**。
- **2026-11-09 用户授权：** 删除已放弃离线 OCR/CRAFT 的过时实验叙述，保留最后确认结果；整理 README 和旧分支。当前交接精简及 README 属文档整理，不改程序，不运行构建。**旧分支的实际删除需另行核实远端是否完成，不因写入此文档就宣称分支已删除。**
- 此文档随后续开发持续维护，最新状态以实时 GitHub 检查为准。