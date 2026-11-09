# 蓝星向导（Blue Star Guide）｜Android 云端视觉助手

> **活跃项目：云端视觉助手。** 原「看字助手」离线 OCR、CRAFT、ChineseOCR-Lite 实验路线已于 2026-11-09 停止开发。请勿将其作为当前默认工作计划。

## 从这里开始

- **当前交接与最新状态：** [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md)
- **当前已发布版本：** [RC25 / v1.0.24](https://github.com/vobichngan285-collab/kanzi-assistant/releases/tag/blue-star-guide-cloud-rc25-20261108)
- **RC25 实际构建源码：** [1b29d56](https://github.com/vobichngan285-collab/kanzi-assistant/commit/1b29d56ec55e8030dfceb1d34ee7748e79deb7a5)
- **RC25 源码所在分支：** [feat/bluestar-125-reading-entry-pages-actions-20261108](https://github.com/vobichngan285-collab/kanzi-assistant/tree/feat/bluestar-125-reading-entry-pages-actions-20261108)
- **下一版本 RC26：** 自定义云端模型服务商及 API Key 配置，目前仅记录需求、尚未开发。

## 已有能力

手机 Camera2 与 USB UVC 摄像头、云端 AI 识图/OCR、流式按行 TTS 朗读、视觉任务与追问、阅读入口、手动拍照、书页列表及本地 OCR 文字保存等。

**重要：** 本仓库默认分支 `main` 主要作为交接文档入口，不代表最新 APK 的构建源码。每次工作必须先核对源代码分支、Commit、GitHub Actions 与 Release。构建通过也不等于 vivo 或 USB 真机验收通过。

## 旧实验归档指针

离线 CRAFT/DBNet/Small/CN-Lite 已停止开发。只在 [当前交接文件](PROJECT_HANDOFF.md#3-已停止开发的旧项目离线-ocr--craft最终结果摘要) 保留最终结论；清理前的 1541 行全文仍可通过[历史提交](https://github.com/vobichngan285-collab/kanzi-assistant/blob/766ddb312dd6ad64ad5f66bba093def442293fbc/PROJECT_HANDOFF.md) 恢复。历史保留不代表项目仍活跃。

本仓库中的云端蓝星向导和另行研究的 **Windows 游戏 OCR 文字助手** 属不同项目，不得混合构建或误删。