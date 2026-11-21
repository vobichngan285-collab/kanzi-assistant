# 蓝星向导 v1.0.72｜字体减半 / 模型管理 / 自定义思考与OCR提示词

## 已获用户明确授权（2026-11-21）
1. 全蓝星向导所有页面的应用内字体**严格按原 effective sp 大小乘以 0.5**，不移走任何按钮、不重做主页、不替换图标、颜色、触摸面积或功能顺序。截图确认的主页仍是快捷拍照／C110+实时预览／朗读任务／阅读模式／设置，底部仍是主页／预览／按住AI。
2. 删除无意义说明文字，编辑服务器的配置名称、API URL、模型 ID、API Key 使用**框内提示**；聚焦时提示即时消失，失焦且为空时恢复；API Key 不回显、不放到日志。
3. 语音与提示中只添加 **云服务器管理** 一行：集中列出全部已保存自定义服务和系统百炼模型；点击或长按某模型打开相同操作菜单，支持自定义配置编辑、明确确认后删除，亦能新建。不能对系统内置模型删除。模型**选择页仅做选择**，不再夹杂编辑、删除和创建。
4. 每个模型（按内置 model ID 或自定义 profile ID+model ID）分别保存深度思考开关（默认关闭）和 OCR 提示词（默认为空，即按原任务提示词）。切换模型恢复配置。新提示词仅覆盖标准“朗读文字”的OCR任务，绝不改阅读模式、视觉分析、AI追问、ASR或其他用户自定义任务。不支持的协议/模型不出现思考开关。
5. 保留原有 C110 固件 3012/3016/3001/2015、1001 JPEG、9011/9038、LibVLC 3.6.5、USB/UVC/Camera2、GR TTS/本地TTS、咔叮音效和首句逐行 SSE、单次拍照忙碌锁及API Key加密不变。

## 具体实现
- 基线 **已发布 v1.0.71 HEAD b5996933d69da71ec198358e0283be49e93c39c7**；独立实验分支 `exp/ui-font50-model-manager-20261121`；versionCode 81 / versionName `1.0.72-font50-model-manager`。debug applicationId 与唯一 Launcher 均不变。
- 新 `BlueStarFontScale.wrap(Context)` 仅通过四个 Activity 的 `attachBaseContext` 返回 **拷贝 Configuration** (`fontScale = 原配置.fontScale*0.5f`)。使原有 `sp` 和按钮默认字体保持严格减半，`dp` 位置、宽度、高度和可触控区域不改；绝不全局改用户 Android 字体设置。
- `BlueStarSettingsScreen` 新增 `server-manage`、`server-actions`、`model-prompt` 三个独立界面，保留现有其余排版；模型选择 `provider-role` 删除每行下的编辑项和添加/测试混合按钮。模型管理列表的点击和长按显示**同一菜单**，避免把长按作为唯一入口。新建/编辑配置**不自动切换**生效模型。
- 原 CloudProviderStore `cloud_custom_profiles_v1` / `cloud_custom_selected_v1_*` JSON 与 Keystore Key 原样保留；另在本地 SharedPreferences `cloud_model_options_v1` 存模型开关与 OCR 用户文本。删除 profile 时只清除此 profile 的模型参数和命名密钥。内置同型号视觉/OCR共享开关，OCR专用提示词只在 OCR 时使用。
- `CloudThinkingPolicy` 用官方 HTTPS host+Chat接口路径+精确模型 ID 的**保守白名单**，只向智谱官方 `glm-4.6v` / `glm-4.6v-flashx` / `glm-4.6v-flash` 写入 `thinking.type = enabled|disabled`；只向官方百炼 `qwen3.7-flash` / `qwen3.8-flash` / `qwen3.8-max` 写入 `enable_thinking = true|false`；不在未知厂商注入无法验证的参数。Model 的 thinking 默认 false；Layout Parsing glm-ocr 不接收 Chat thinking 或文本 prompt，UI不提供无效开关/伪配置。
- SSE 仍只从 `delta.content` 拼接/朗读，`reasoning_content` 不给 TTS。模型专属 prompt 优先于标准 OCR 任务默认 prompt，但用户自定义的非 OCR 任务、书页 OCR 与 text-only 回调不受影响。
- 模型连接测试总是发送准确被点击模型的微量字母A验证请求，不会在管理界面改变生效模型。API Key只保存在现有 Keystore，编辑框留空保留旧 Key。
- 关键风险：极端字号减半后可读性会下降，这是用户明确指定的**严格50%**；有问题可直接使用保留的 v1.0.71 Release。Android系统状态栏不属于App，故不受影响。

## 测试与发布条件
- 编译前先审阅上述源文件变化、Java大括号、四入口覆盖、主界面未改布局、支持白名单、不向Layout/ASR加入思考/无效提示词。
- CI 编译：Android Java17 + Gradle 8.11.1，固定上游 AndroidUSBCamera v3.6.0、原已验证UVC补丁；纯Java `CloudProviderPolicyTest`、新 `CloudThinkingPolicyTest`、OCR分行/任务/Zoom测试；arm64 :app:assembleDebug 及 APK ZIP/aapt/包名/LibVLC/UVC/音效/唯一Launcher；通过后才上传APK并发布 **v1.0.72** 单独 Release。
- 真机待确认：五入口主页一字不移动，仅文字减半；整个应用页面、弹窗、键盘输入文本按相同比例；云服务器管理增加/修改/删除/长按；千问与智谱开启关闭思考且切换后记忆；每型号不同 OCR 提示词真正在请求中生效；Layout Parsing无虚假选项；手机/USB/C110录像高清拍照、无障碍/TTS和原有API Key不退化。
- CI成功只证明编译+静态测试，**不等于已经真机验收**。
