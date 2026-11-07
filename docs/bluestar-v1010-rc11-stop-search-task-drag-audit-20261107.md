# 蓝星向导 Blue Star Guide RC11（1.0.10）——取消寻找与任务长按拖动

日期：2026-11-07。继续使用用户既有 `vobichngan285-collab/kanzi-assistant` 仓库。

## 已授权范围

用户反馈：RC10/RC9 用语音下达“找门”后，左上角显示“寻找：门”；说“取消找门/取消掉掌门”等自然话语后，AI 可能只口头答应，但程序并未清除寻找状态，直至真正找到门才自动结束。用户此前也提出任务界面六个图标可像手机桌面一样**长按拖动调整顺序，重新打开仍记得顺序**；RC10 未实际实现，用户指出后正式授权把以上两项尚未完成工作在本次一起修复。

**明确不包含**：此前只是讨论、尚待用户真机 A/B 后再决定的删除 `qwen3-vl-plus`；未获测试结论，本版继续完整保留 RC10 四个视觉模型，默认 `qwen3.8-flash` 不变；云端百炼 OCR/ASR/文字追问、模型用量、原生 UVC/Camera2、短音效、深蓝设置样式不动。

## 项目基线与隔离

- 真正读取 `main:PROJECT_HANDOFF.md`、RC10 原分支 `fix/bluestar-109-37flash-keep-plus-20261107` 的当前 HEAD `7c6ded78fda596bb02abeca1288e589f2d2b7fcf` 和 GitHub Release / Actions。
- 建立隔离分支 `fix/bluestar-110-stop-finding-drag-grid-20261107`。未建立新仓库、未覆盖旧 RC10/RC9。
- 新测试版 `versionCode=20`、`versionName=1.0.10-rc11`、Android debug 包名 `com.vobich.bluestar.rc11`；旧 RC10 可并存，但新应用沙盒的百炼 API Key、已保存自定义任务需单独设置。属于测试阶段安全隔离，不宣称无损原地升级。

## A. 语音停止寻找真的退出

- `VoiceAction.classify(utterance,activeTarget)` 新增本地上下文路由，支持“取消找门”“取消掉找门”“停止找门”“结束找门任务”“把找门任务取消”“不要再找门了”“取消当前寻找任务”“取消这个任务”等明确命令。对于用户反映的 ASR 同音误识别，当活跃寻找目标**恰好是“门”**且存在明确取消动词时，也保守接受“掌门”，防止随意误停其他任务。
- 先处理“改找/换成找”，如“不找超市了，改找药店”应切换而不是取消；“不要取消寻找/别停止寻找”不能错误结束；“停止寻找是什么意思”不是控制命令；“找门了吗”是问句，不是重新开始寻找。
- `MainActivity.routeSpokenRequest` 给路由器传入真实本机 `findingTarget`，匹配 STOP 后直接执行原有 `setFindingTarget("")`，其 SharedPreferences 持久状态和左上角角标马上同步变更，随后本地 TTS 播报确认，**不经云端文本分析伪装完成、更不发图片**。原有正确退出与找到目标自动退出逻辑完全保留。

## B. 任务卡片真实长按移动

- `TaskDesktopOverlay` 保持原 3×3、默认五个任务 +「＋自定义任务」的大图标界面。新实现是**真正的 Android 原生 `View.startDragAndDrop` 和 `DragEvent.ACTION_DROP`**，不是只更改文案。短按仍选择原任务；长按约480毫秒触发拖放和系统触觉反馈；在另一格上松手移动排序。被选中的金色框随着任务本身移动，颜色也与任务 ID 固定绑定而非按位置变色。原左右滑动翻页继续保留。
- 有自定义任务超过一页时，可将图标拖向底部「‹ / ›」页码箭头转到相邻页面，再拖到目标格松手；直接在箭头释放会放到目标页首/尾可用位置。不可用箭头不应造成意外乱序。
- `TaskCatalog.displayOrder/saveDisplayOrder` 把排序后的稳定任务 ID（包括单独 `__create_task__` 创建入口）保存在现有 `cloud_vision_tasks` 的 `task_grid_order_v1`。每次打开任务页先用纯 Java `TaskOrder.reconcile` 清理失效、重复任务 ID，再追加新创建的任务；删除自定义任务不留鬼影。每次成功移动都调用 `TaskCatalog.saveDisplayOrder`，不修改名称、提示词、当前选择、引擎或任务业务定义。
- 修正旧手势实现只消费按下/抬起但从不识别长按的原因，用同一套触摸逻辑区分点击/滑动/长按：轻扫翻页、长按使用影子拖放、释放后不能额外触发点击或开启自定义任务。卡片无文本说明冗余，已设置 TalkBack 无障碍简短说明。

## 测试与验收边界

- `VoiceFlowTest` 覆盖取消说法、换目标优先、否定取消、防止误启动寻找、门/掌门 ASR 误识别，以及以前语音找超市、看场景、读文字和 WAV/按住发送测试。
- `TaskOrderTest` 覆盖首页六卡重排、创建入口置前、移动到任意格、拖动跨页的纯序列动作、新增自定义与删除遗留 ID 的稳定排序、重复 ID 清理、输入越界拒绝和原列表不变。
- CI 脚本静态核查原生 `startDragAndDrop`、`ACTION_DROP`、`onPageArrowDrag`、SharedPreferences ID 保存以及 STOP → `setFindingTarget("")` 真正执行。还跑原 OCR 结果门控、四视觉模型及纯文字追问检查，以及原 UVC/Camera2/原声效/图标完整性；GitHub Android CI 不能代替用户手机触摸与口播实测。
- **需要用户 vivo X200 Pro 验收**：①先说“帮我找门”，左上角标出寻找；再说“取消掉找门”，应立即恢复“任务：朗读文字”或当前任务而不是等待拍到门，且不增加额外图片调用；②在任务页按住“店铺招牌”约0.5秒并拖到左上，放手后留在第一格；关任务页重开、重启 APP 后顺序仍在；③短点任务仍正常选择、向左滑动只翻页；④若已增加自定义任务到超过九格，试底部箭头跨页拖动；⑤四模型选项、USB/手机画面、OCR/TTS 正常。

## GitHub 实际编译、校验及发布（已完成）

- 初版 Actions run **42331962973** 的语音回归测试发现“找门了吗”被重新当作寻找目标，此例错误在 `VoiceAction` 的旧宽泛寻找规则；已增加目标状态问句识别并重跑。随后的多次 run 由于改动后自动取消，被明确区分为 cancelled，**没有把其当成功**。
- **最终真正构建 SHA**：`cfe016f87b9477f438216c83ac2ab0f9eb9d3b48`；GitHub Actions **[42332177046](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42332177046)** `completed/success`。原静态安全审计、更新后的 `VoiceFlowTest`、`TaskOrderTest`、`CloudOptimizationTest`、`FollowupPromptTest`、`VisionModelCatalogTest` 全通过；Android Gradle 8.11.1 / ARM64 固定原生 UVC 编译、原有提示音/桌面图标及 APK 包名核对均通过。
- **真实 ZIP Artifact** id **13557264149**、名称 `blue-star-guide-1-0-10-rc11-arm64`；实际下载到容器 `/mnt/data/BlueStar_Guide_1.0.10_RC11_artifact.zip`。通过 Python zipfile **逐项校验 ZIP 完整性、归档内 `SHA256SUMS.txt`、APK ZIP CRC、`lib/arm64-v8a/libUVCCamera.so`、`res/raw/c_metal_clack_ding.mp3`、`res/raw/voice_send_whoosh.wav`**。原 APK `BlueStar_Guide_1.0.10_RC11_arm64.apk` **8,567,980 bytes**，SHA256 **`3801e557b140201f9ed7608777a3c7347e438be4b09a51b0773ce0e42f89a137`**。APK 原 `badging.txt` 首行 `package: name='com.vobich.bluestar.rc11' versionCode='20' versionName='1.0.10-rc11'`。
- **发布成功**：GitHub Workflow **[42332556442](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42332556442)** `completed/success`。独立发布流程锁定成功 CI run、源 SHA、文件大小、SHA256、原 USB 库和两个声效，**直接上传 CI 原 APK 不重新编译/签名**。GitHub Release tag `blue-star-guide-cloud-rc11-20261107` API 实际回报 `draft=false`、`prerelease=true`，资产 `state=uploaded`、大小 **8,567,980 bytes**、服务端 digest 与本地计算完全一致。
- **公网直链（非 ZIP，可复制手机浏览器）**：
  https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/blue-star-guide-cloud-rc11-20261107/BlueStar_Guide_1.0.10_RC11_arm64.apk

**实测边界**：JVM 纯逻辑、Android 编译及 ZIP 校验均已通过，仍不能宣称已在 vivo X200 Pro 真机完成长按/拖动、跨页 drop、麦克风 ASR 文本、系统 TTS 播放及 USB/Camera2 运行验证；需要用户安装操作反馈。与 RC10 分离意味着新包的本地 Keystore API Key 与自定义任务不自动继承，切勿误称可以无缝升级。
