# Blue Star Guide 1.0.1 RC2 修复记录与真机验收交接
日期：2026-11-06；源分支：\`fix/bluestar-101-voice-ui-usb-20261106\`；源代码提交：\`c8327dde7a0cf04a7ee9ca3f0386eaebd170fdf3\`。
用户明确恢复开发并要求：**桌面启动名称英文 Blue Star Guide；App 内中文蓝星向导**。复用蓝星地球图标，不要丢。眼镜暂缓。

## 构建与安装
- GitHub Actions 通过：run 42198346895；job 127827148979；链接 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42198346895
- Artifact ID 13492648486；链接 https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/42198346895/artifacts/13492648486
- \`BlueStar_Guide_1.0.1_RC2_arm64.apk\`（debug-signed，ARM64，仅供内部真机验收），8,432,919 字节，SHA256 \`18a03aadd1c5795cf85fdd4186f83a57f828106dc6e391c3c037ccd52ba6b9d8\`。
- GitHub CI 全部成功，单元测试和静态范围检查通过，USB 原生 JNI 编译通过。解包验证 \`SHA256SUMS.txt\` 校验通过、\`res/raw/metal_lid_click.wav\` RIFF/WAVE 完整、\`res/drawable-nodpi-v4/bluestar_icon.webp\`、五个 \`res/mipmap-*-v4/ic_launcher.webp\` 以及 \`res/mipmap-anydpi-v26/ic_launcher.xml\` 真实存在，\`lib/arm64-v8a/libUVCCamera.so\` 真实存在。不能以打包存在声称已经在 vivo 的桌面上显示成功，还需真机确认。
- **签名不兼容特别警告**：对 RC1 APK 与 RC2 APK 的 v2 签名块（0x7109871a）逐个提取 X.509 证书，SHA256 RC1：\`bdd3ebd7bd8886bbb5c6de7e58ad3117df71f39a4e55a98d9213e17d06403b03\`，RC2：\`812566c08b57cd2a3d9e411602a1901d135ad9321bad12f2c1b47ec7d002e496\`，**两者不同且同样的包名 \`com.vobich.bluestar\`**。Android 不支持不同签名的原位更新；安装新 APK 前必须卸载旧 1.0.0 RC1，会清除该应用的本地设置/Key。旧实验版 \`com.vobich.bluestar.cloudprobe\` 不需卸载。后续长期更新必须配固定安全签名，不能反复使用随机 GitHub Runner debug key。
- 旧版0.5.0与新RC2包名不同；RC1与RC2包名相同而签名不同。测试时不要发送真实 API Key 到 GitHub 或聊天。

## 已按用户本轮要求完成的代码
1. 桌面名称改为英文 **Blue Star Guide**；App 里面继续是 **蓝星向导**；消除 Adaptive Icon 原先18dp inset缩小问题，五种 Android density 添 legacy ic_launcher.webp 图标；保持原有蓝色星球+书标志。
2. 状态栏/导航栏安全区域 Insets 处理，避免头部标题与手机时间、电量、通知冲突。
3. 所有设置入口改为全页深蓝大字子菜单，含语音与朗读、语音引擎、任务管理/预设提示词全页编辑、自定义任务图标/文字编辑、摄像头设置、提示与振动、云端AI/API Key 编辑。设置子页不再是灰色小 AlertDialog 三按钮弹窗；语速滑块直列，移除「跟随语速」额外 UI 开关，返回自动保存。
4. 朗读文字任务的 **英文/数字/符号过滤**，以及可选「应用所有任务」；默认全部关闭；**仅在本机 TTS 输出路径过滤，不修改 OCR 原始文本、不破坏用户编辑提示词、不影响图片追问上下文**。单独 \`SpeechFilter.java\` 和 JDK 单测覆盖。
5. 不再依赖 \`SpeechRecognizer.isRecognitionAvailable()\`：单击「AI对话」申请录音权限并启动 AudioRecord，16kHz WAV，检测语音并在持续静音后自动结束，仅在主动单击期间录音，送到阿里百炼 **qwen3-asr-flash**（OpenAI兼容接口），文字转成云端视觉追问后本地TTS读答案；缺 API Key/网络/未开通对应ASR时明确提示。新功能会上传语音且可能计费，确保用户在百炼查看对应配额和用完即停。用户需要在 vivo 上测试麦克风和句尾检测/延迟。
6. 替换旧 \`ToneGenerator.TONE_PROP_BEEP\` 双电子声：内置原创的短促金属盖咔嗒 PCM WAV（\`R.raw.metal_lid_click\`），用 SoundPool 低延迟播放；Android 29+ 选择系统原生 \`VibrationEffect.EFFECT_CLICK\` 点击马达反馈，低版本用短13ms振动。声音/震动独立开关、默认开、可在大字设置页试听。效果像 Zippo 开盖的简短金属点击及苹果键盘Taptic，但具体声音和震动都需用户亲耳/亲手确认，不能保证与原设备完全一致。
7. 复用 AUSBC 3.6.0 / NORMAL 原生 MJPEG 直显，不重写 UVC 中间渲染；USB RawFrame byte[] 在采样时只复制一帧防 native buffer 重用；\`Nv21ImageOps.java\` 在 NV21 上做原生旋转/镜像/裁切/缩小，仅对抓拍照编码一次 JPEG，不再 JPEG→Bitmap→再JPEG；修复 USB 横置90/270度时 preview-local 镜像换算到最终图像的几何关系，保证向云端提交的图和用户画面一致。保持 USB重连原生基线不动，尚需真机多次前后台/插拔验证。
8. 原有五预设+自定义任务九宫格、Android 本地TTS、云端OCR/视觉模型路由保持不变。
9. 删除云端 API Key 缺失时跳灰色小窗流程，直接引导用户进入全页大字编辑器，避免新旧风格冲突。

## 仍待真实 vivo X200 Pro / USB 测试
- 连接USB与手机前后摄，验证画面、旋转记忆、镜像和云端上传结果；双指缩放不得误拍、并保证放大后上传范围正确。
- 桌面英文标题和蓝色星球图标的实际 launcher 显示；应用内部标题避开状态栏。
- 「语音与朗读」所有子页面大字、滑块、过滤、试听；单击AI对话首次麦克风权限、自动检测静音、Qwen ASR是否启用、网络与费用、回答是否真实参照最后一张照片。
- 金属咔嗒音与短振效果是否符合用户审美；若偏电子或不够清脆，应基于真机听感调整，禁止直接声称完美复刻。
- USB 前后台、插拔与预览冻结/黑屏、OCR稳定和耗电发热。需要真实设备测试，不能从CI推断全部通过。

## 历史失败已排除
- 首轮编译缺少 \`CloudKeyStore.load()\` 的受检异常处理；已捕获，不再编译错误。
- 首轮RC2构建脚本残留旧版APK校验文件名；已修正。
- RC2最终 run 42198346895 构建、资源核验、上传全部成功。
