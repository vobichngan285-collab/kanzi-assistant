# 蓝星向导 C110+ v1.0.55 — 主页按钮一按直接拍照、识别朗读；移除错误的拍照实验页面

## 触发原因
用户在v1.0.54真机中明确展示：点击主页第一项“快捷拍照”却进入单独的“C110+快捷拍照”页面，页面仅显示 “取帧已完成，但识别失败：请先在三横杠设置中填写百炼 API Key”及“再拍一张高清原图/返回主页”。**用户要求的是按一下直接拍一张照片并调用现有OCR朗读，不要跳任何拍照/诊断/实验页面**。已核查根因：`HomeActivity.quickPhoto()` 里是 `startActivity(new Intent(this,C110QuickPhotoActivity.class))`，与用户要求直接冲突；MainActivity的未连接C110快捷键也存在同样跳页。

## 本次只修这条主路径
- 以 v1.0.54 `exp/c110plus-home-bluestar-blue-20261120` commit `9a5e243ac1fa3b1dd5bdb52c984bba4021b09401` 为唯一基线；独立 `exp/c110plus-inline-quick-photo-20261120`，独立v1.0.55/versionCode64，包名`com.vobich.bluestar.c110plusinline155`，不覆盖已有包。
- 删除多余 `C110QuickPhotoActivity.java` 及 Manifest 注册，主页 `HomeActivity.quickPhoto()` 仅调用 `C110QuickShotController.trigger()`，按钮和音量键都走同一方法。保持主页深蓝、功能键顺序与底部“主页/预览/设置”不变；只在第一项下面出现小段进度文字，**不会打开全屏状态/诊断页**，不额外启动手机/USB/VLC。
- 新 `C110QuickShotController` 只做与原先单次拍照同样的WiFi连接等待、**恰好一次** `C110WifiCamera.shootAfterLivePreview` 真实1001快门、原SD高清JPEG，调用此前已验证的 `C110FrameReader.recognizeOriginalJpeg`；使用当前主页选择的模型/任务/TTS及已配置的 API Key，语音朗读原文。拍摄/云端正在忙时屏蔽重复触发；断线时等待系统对 C110 WiFi 的连接许可，不自动重复付费请求。
- `MainActivity` 手机/USB预览右上角快拍及其音量快捷键不再触发旧Activity；已有C110网络则走既有高分辨率拍照管线，没有网络则调用同一新Controller等待一次触发。原 C110 专页真实高清快门/竖屏+150ms逻辑保留。
- 因新APP独立包名与旧包沙箱隔离，**API Key 无法跨包自动继承**；需先在本实验版本的主页“设置 → 云端 API Key”粘贴百炼密钥，否则真实相机拍照可以成功但云端无法识别。缺少Key的提示改为“请先在主页的设置中填写百炼 API Key”，不再让用户寻找不存在的三横杠。
- 音量键双击未在 vivo X200 Pro 真机确认功能，保留原有前台按键代码，不在本次将其“宣称已修复”；后续必须通过设备原始按键事件日志验证，绝不编造已成功。机身灯、声音、原厂指令、150ms缓存、OCR模型、USB、阅读和旧实验保留。

## 自动/真机验证
- CI断言：整个主页快捷拍照方法内无`startActivity`、Manifest和源码中**不存在**`C110QuickPhotoActivity`、手机/USB预览未连接时不再启动Activity、单次拍照Controller仅一处真实快门、旧C110 OEM 1001指令及150ms缓存不变、蓝星配色不变、原20M/10M/8M/3M实验单独保留、TTS及本地资源仍能构建。
- 真机：先配置有效Key；停留在深蓝主页，点“快捷拍照”→只有一条小进度提示，WiFi已连接时一次1001+返回真实高清JPEG+云端OCR朗读；截图不会切换页面；没有WiFi时允许系统连接后**同一次点击只拍一张**。测试双击音量键实际是否生效，以真机证据为准。GitHub构建成功≠这些真机动作已经成功。
