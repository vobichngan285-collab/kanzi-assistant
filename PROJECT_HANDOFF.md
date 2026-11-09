# 蓝星向导｜当前工作交接（2026-11-09）

> **只按本页恢复当前项目。** 原仓库：[kanzi-assistant](https://github.com/vobichngan285-collab/kanzi-assistant)。本仓库旧离线 OCR／CRAFT 已停止开发；Windows 游戏 OCR 是另一项目，不要混淆或自动恢复旧实验。

## 1. 现在做什么
- **当前活跃项目：** 安卓「蓝星向导 Blue Star Guide」**云端 AI 视觉助手**，使用手机后摄或 USB UVC 摄像头拍照，执行 AI 识图、OCR、追问、中文 TTS 朗读，并有阅读/书页管理功能。
- **最新已发布：RC25 v1.0.24**（2026-11-08），已通过源码检查、Android CI 构建及 Release 校验；**尚未取得完整 vivo X200 Pro／USB 真机交互验收**。RC26 **尚未开发**。
- **正确源码分支：** `feat/bluestar-125-reading-entry-pages-actions-20261108`；本次检查分支 HEAD 为 `fe848acf87cec87d8d7cba0c739b7be40e31072b`（文档提交）；**真正用于 RC25 APK 构建**的源码 SHA 是 `1b29d56ec55e8030dfceb1d34ee7748e79deb7a5`。不要把 `main`（交接文档分支）误当作最新 APK 代码。
- **成功编译：** [Actions 42444753150](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42444753150)；**原包发布：** [Actions 42445082678](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42445082678)。
- **RC25 下载与版本核对：** [Release 页面](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc25-20261108)，[APK 直链](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc25-20261108/BlueStar_Guide_1.0.24_RC25_arm64.apk)；包名 `com.vobich.bluestar.rc25`，SHA256 `d1f863c3a9556d1fc4fc7e3ed6ea7702b1d3badae3be2d5de6a93147b5efdfc9`。

## 2. 已完成的体验与仍需确认的事
- 现有云端视觉任务、手机 Camera2／USB 摄像头、语音追问、语音提示、C 模式云端 OCR **逐行流式朗读**需要保留。C 模式此前有实际用户正面反馈，不必重复旧 A/B/C 实验。
- RC25「阅读」入口仅有**继续阅读／新阅读**；选书后进入书页列表，支持标记、改名、删除；用户进入相机预览后**再次点击画面**才拍照、振动、关闭相机、OCR、朗读并存储文字。正文底部有**下一张／暂停或继续／重新开始**，右上角保留隐藏功能菜单。
- **真机尚待验收：** 阅读按钮、书页管理与持久化、朗读顺序、USB 方向与稳定性，以及手机后摄表现。RC25 独立包与旧版不共享私有书籍和 API Key；不要随意卸载旧包导致数据丢失。CI 成功不代表全部硬件行为已验证。

## 3. 下一步计划（等待用户新指令）
- **RC26 目标：** 设置里允许用户自行添加／切换**豆包、DeepSeek、千问及其他 OpenAI Chat Completions 兼容模型**，填写模型 ID、自己的 API Key，测试连接并保存；同时保留原阿里云百炼及 C 模式。
- 必须检查每个服务是否真的支持**图片、流式返回**；只用 HTTPS，Key 安全存储、不得记录或跨服务商发送，防重定向泄漏；第三方图片上传需明确提示。**不同时改 ASR、TTS 或 USB 摄像头底层。**
- RC26 只是**已确认的需求方向**，没有代码或构建。应等用户明确下令再设计、修改和测试；如用户改了优先级，按新指令执行。

## 4. 放弃项目与工作纪律
- **已放弃：** 早期「看字助手」本地离线 PP-OCR／ChineseOCR-Lite／CRAFT 实验，旧 v55-v57、CRAFT 6×24 等都**不再是当前开发任务**；不要检索、汇报、重复运行这些实验，除非用户点名要求。旧分支尚在 GitHub，不等于仍要维护。
- **唯一当前交接文件：** `main:PROJECT_HANDOFF.md`。完整的旧长文档仅作为可追溯历史保留在 [2026-11-09 清理前 Commit](https://github.com/vobichngan285-collab/kanzi-assistant/blob/766ddb312dd6ad64ad5f66bba093def442293fbc/PROJECT_HANDOFF.md)，新对话**无需读取**。
- **开发规则：** 不新建仓库；只在用户授权后动代码、构建或发行。实施前核对目标分支 HEAD、真实源码与 Actions；修复后审查 diff、自动测试、APK、Release，不能夸称未做的真机验证。
- **交接更新：** 每次获授权的重要进展，在此文件**简短更新最新版本、构建 SHA、Release、已验证事实、未解决问题和下一步**；提交至 `main` 并回读核验。不要再次堆积旧实验流水账；文档-only 提交带 `[skip ci]`。不得在公开仓库写入 Key、私人照片或 OCR 原文。
