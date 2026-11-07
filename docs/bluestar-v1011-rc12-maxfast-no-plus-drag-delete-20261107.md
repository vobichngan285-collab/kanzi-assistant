# 蓝星向导 1.0.11 RC12 — 用户批准变更与测试事项

2026-11-07，在原仓库分支 `fix/bluestar-111-maxfast-remove-plus-drag-delete-20261107`，从 RC11 源码 commit `e7042f97dbdde677024f97e7fa55fc5094ff8973` 继续。
本次明确授权：Max 快速模式、移除 Qwen3-VL-Plus、宫格长按拖动删除。

## 源码实现
- 模型菜单改为 qwen3.7-flash、qwen3.8-flash 和 qwen3.8-max 三选一。已存储的 qwen3-vl-plus 在读取旧设置时通过 `VisionModelCatalog.normalize` 自动迁移至 qwen3.8-flash；历史用量仍可查看。
- Max 正常视觉任务强制 `enable_thinking=false`，取消 `thinking_budget=512` 和 2048 Token 特殊输出额度。沿用 Flash 类似任务输出上限及逐段 TTS，不新增重试或额外 API 消耗。专用 OCR 与 ASR 不变。
- 长按任务卡 480ms 进入原生 Android 拖动时出现红色删除区。拖入并松手仅弹确认，取消不更改；自定义任务确认后删除，预设任务仅隐藏且在设置 > 任务管理恢复。保存顺序继续使用稳定任务 ID，禁止最后一个任务被隐藏；删除当前任务重置至可用选择。
- 独立 Android 测试包 id `com.vobich.bluestar.rc12`、版本 `1.0.11-rc12`、versionCode 21。RC11 安装保持独立。
- 原 USB/Camera2、拍照采帧、JPEG、ASR、TTS、声音与设置保留。

## 待验证
- GitHub Actions Android CI + JVM 回归 + 静态隐私/资源检查，必须以实际结果为准。
- APK 独立包名、图标、原生 USB 库与拍照/录音音效实际装包。
- vivo X200 Pro 真机同图比较 Max 首字/首句时间和准确性；旧 Plus 选择迁移；任务拖动排序、跨页、删自定义、取消、隐藏/恢复系统任务、删除当前任务、最后任务保护。

**注意：本文在 CI 运行前创建，不能据此声称测试通过或 APK 已发布。**
