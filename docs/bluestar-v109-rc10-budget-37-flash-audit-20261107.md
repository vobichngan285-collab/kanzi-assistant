# Blue Star Guide 1.0.9 RC10：新增低成本 Qwen3.7-Flash（保留 Plus）

**项目与基线**：继续使用原仓库 `vobichngan285-collab/kanzi-assistant`。读取 `main:PROJECT_HANDOFF.md`、RC9 真 HEAD `36ef85b3a868a7d1d0e18e6881eae0acb926d1c4`、Release 和 Actions 后建立独立分支 `fix/bluestar-109-37flash-keep-plus-20261107`，不覆盖 RC9 / RC8 / 离线 OCR / USB 源码。

**用户明确授权**：仅增加 `qwen3.7-flash` 省钱视觉选项。已验证的 `qwen3-vl-plus` 本版本绝不能删除；用户希望等真机确认 3.7 后**下个版本再考虑**移除 Plus。原 `qwen3.8-flash` 继续默认，`qwen3.8-max` 继续手动应急。先前记录的口头取消寻找失效、任务六宫格拖动排序，仍只登记而不修改，以缩小本版本测试范围。

## 官方依据

阿里云百炼官方 `https://help.aliyun.com/zh/model-studio/qwen3-7-flash` / `https://docs.bailian.console.aliyun.com/en/model-studio/qwen3-7-flash` 在 2026-11-07 核查显示：`qwen3.7-flash` 是北京地域可用的原生多模态视觉语言模型，输入支持 Image/Text/Video，输出 Text；北京地域 <=32k 输入 token 阶梯标准单价输入 ¥0.20/百万 Token、输出 ¥0.80/百万 Token。模型和计费可由用户最终地域/合同/促销/阶梯影响，**价格验证不是使用用户 API Key 发起连接测试**。官方视觉/视觉推理文档确认 Qwen3.7 Flash 可通过 `enable_thinking=false` 关闭思考，适合短图轻量交互，非流式寻找目标的 JSON 约束下同样关闭思考。

## 实际修改范围（RC9 其他功能不动）

- `VisionModelCatalog.java`：新增 `FLASH_37="qwen3.7-flash"`；按 **超省 3.7 → 日常 3.8 → 稳定备用 Plus → 应急 Max** 列出四种视觉模型。**默认仍然是 3.8**，已有四种有效手动选择原样保存，不允许无声 fallback；旧撤下的 `qwen3-vl-flash` 和 `qwen3.5-flash` 迁移到 3.8。新增 3.7 算作 Flash 模式，跟 3.8 一样不开思考，避免额外输出 Token。
- `LargeSettingsScreen.java` 仍用原来 `VisionModelCatalog.IDS/LABELS` 动态绘制四项，不增加描述行、不改变不透明深蓝布局。仅原 `MainActivity` 遗留文字选择对话说明从“三选一”改为“四选一”，现有连接测试仍手动点击后才发送 64×64 小测试 JPEG 一次，不自动重试、不会静默换成 Plus/Max。
- `TargetVisionClient.java` 原先就根据合法视觉模型清单选择处理，复用 `isBudgetFlash` 自动为新 3.7 关闭思考；`CloudVisionClient.java` 正常照片理解入口也同理。**固定 OCR `qwen-vl-ocr`、语音 ASR `qwen3-asr-flash` 和纯文字追问 `qwen3.8-flash` 都不改动**，保留原来已修复的文字不重传图片机制。
- `ModelUsageLedger.java`：加 3.7 独立记录行，完全保留 3.8、Plus、Max、固定 OCR 和 ASR，不估算缺失 Token。因 RC10 用独立包名安装，与 RC9 的本地统计数据隔离。
- `VisionModelCatalogTest.java`：新增四模型数量与顺序、重复/非法 ID、3.7 模式、Plus/Max 保留、旧配置迁移、默认 3.8 不悄然切换、普通模型不触发 Max 特权的纯 Java 测试。
- `.github/workflows/cloud-vision-usb-click.yml`：为 RC10 分支添加真实 Android CI，静态验证全四模型、廉价 Flash 无思考、纯文字追问仍固定 3.8、Plus 仍在、USB/音效/深蓝设置及包名，并执行原先 Java 套件、UVC native、APK badging 与输出制品。
- 版本：`1.0.9-rc10`，versionCode 19，包名 `com.vobich.bluestar.rc10`，独立安装、不覆盖 RC9。安装 RC10 需在新包名下重新配置北京地域百炼普通 Key。并存方便真机 A/B，对照之后再讨论将来去掉 Plus。

## 真机仍需做的验收

用户提供百炼北京地域 API Key，手动选择 `qwen3.7-flash`，点击“测试当前模型”：只有 HTTP 请求得到有效文字返回后才可确认该 Key 下可用。用相同的菜单、商品招牌、医院科室、超市寻找场景对比 3.7 与 3.8 的正确率、首次朗读时间和统计中的实际 input/output Token。单纯连接成功不等于场景识别准确或真实成本一定更低。上一轮「取消寻找」和「长按拖动排序」未做修改，仍属下一轮工作。
