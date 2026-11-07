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


## 完整 CI / Release 审计结果（2026-11-07 追加）

- **真实 Android 构建成功**：GitHub Actions run **42336813720**：`completed/success`，编译源 commit `c9fec2edfc881098fe896d2cf3c669cf5cbafbfd`；静态隐私/新逻辑检查、JVM 模型迁移/任务排序回归、UVC Android 16 补丁、原始五密度图标、Java 17/Gradle 8.11.1 arm64 APK 构建和 `aapt` 包名/音效检查全部成功。
- 原始 APK Artifact **13559851670**，工件名 `blue-star-guide-1-0-11-rc12-arm64`。
- **原 CI APK 直接发布，无重新编译/签名**：GitHub workflow run **42337102034**：`completed/success`。脚本核验 run+源码 SHA、`sha256sum -c SHA256SUMS.txt`、APK ZIP CRC、`lib/arm64-v8a/libUVCCamera.so`、金属咔叮 MP3、语音嗖声 WAV、`aapt` 包名/版本后，仅上传成功 CI 原件。
- Release tag `blue-star-guide-cloud-rc12-20261107`，2026-11-07 发布，`draft=false`、`prerelease=true`、Asset `state=uploaded`。
- Asset `BlueStar_Guide_1.0.11_RC12_arm64.apk`，大小 **8,567,980 字节**，GitHub 服务器 SHA256 **`a602d31a540fb00b6ddd2512ed614aef065dacb32fb5321ab20fc1acb1bcddb3`**。发布校验 job 日志确认 `SHA256SUMS.txt: OK` 对应原 APK。
- **手机浏览器直接下载 APK**：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc12-20261107/BlueStar_Guide_1.0.11_RC12_arm64.apk
- **保留限制**：这是 GitHub 编译与制品核查，不等于 vivo X200 Pro 已亲测操作或高精度延迟。RC12 独立包需要重新配置北京百炼 Key 和自定义任务。
