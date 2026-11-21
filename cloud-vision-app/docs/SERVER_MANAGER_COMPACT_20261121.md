# 蓝星向导 v1.0.73：云服务器管理页面精简（2026-11-21）

## 用户确认
v1.0.72 云服务器管理截图里，百炼 Qwen 3.7/3.8 Flash/Max 因重复以 OCR/视觉分类循环显示，一共七条加 ASR，挤走用户真正添加的智谱服务器；字体已严格缩小 50%，旧 66dp 卡片高且文字靠顶留白过大。用户授权按以下已经讨论的方向直接开发，不再擅改主页或其他布局。

## 本次实现
1. 云服务器管理进入时，最前面是「我的服务器」和 CloudProviderStore.list(app) 的全部真实自定义配置；没有自行创建模型配置，两个名字相同但不同 profile ID 的真实服务器保留。
2. 下面是「＋ 添加新服务器」，仍允许 OCR/视觉/ASR 用原输入界面创建，新增编辑不改变当前实际启用服务。
3. 最下方一个「原有百炼模型（5） · 展开」折叠行，默认折叠；展开后按模型 ID 去重保留 qwen-vl-ocr、qwen3.7-flash、qwen3.8-flash、qwen3.8-max、qwen3-asr-flash 各一次，内置不可删除。可关闭/展开，保留滚动位置。
4. 只精简云服务器管理列表及它的模型操作页，使用 52dp 高紧凑卡片、6dp 间距、文字垂直居中、触摸高度不低于48dp；长按和点击都是同一个管理页面；保留原深蓝色、圆角和箭头图标。**其他设置的 row()、主页五行按钮及底部导航高度、颜色和位置全部不变**。原全软件字体严格50%也不变。
5. 自定义接口只列一遍。ASR协议归 ASR；兼容 Chat 的双用途模型优先使用当前实际选择的 OCR/视觉角色，否则默认 OCR。独立 OCR 提示词始终按 OCR 任务保存，不会因为当前启用的是视觉角色而在管理页消失；Layout Parsing 无虚假的 Chat 思考/提示词设置。
6. 保留加密 API Key、配置名称/URL/model、角色选择、深度思考开关、OCR提示词、连接测试、删除前二次确认，未修改任何核心网络、C110 Wi-Fi/1001/LibVLC、USB Camera2、TTS、SSE 与无障碍输入操作。

## 发布与回归条件
基线为 v1.0.72 Commit d5f4aac056904015350ec77acd09618efcd2258a。分支 exp/server-manager-compact-20261121。版本 versionCode 82，versionName 1.0.73-server-manager-compact。只允许修改 BlueStarSettingsScreen.java、app/build.gradle、本说明、新的纯 Java CloudServerListTest.java 和此版本工作流五个文件。
必须通过差异范围、安全断言、纯 Java Qwen/GLM、任务/OCR行序、C110 Zoom，原 UVC补丁、Gradle ARM64完整编译和 APK 资源/包名/Launcher/SHA 验证后才发布。Android 构建不能代替 vivo X200 Pro 真机与 TalkBack、模型按钮和实际朗读测试。
绝不卸载旧包导致 API Key 丢失；旧版 v1.0.72 Release 保留。
