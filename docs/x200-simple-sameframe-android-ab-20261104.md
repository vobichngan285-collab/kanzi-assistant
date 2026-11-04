# 看字助手 X200｜同一原始帧0°与左转90°真实Small OCR独立手机实验

- 完成日：2026-11-04
- 目的：基于用户提出的「极简流程」，不增加模型和补救逻辑，在**同一份手机/USB原始NV21帧**上分别真实执行 PP-OCRv6 Small DET+REC 0°和向左旋转90°；核对错误字来源，不凭模型置信度选择方向。
- 来源基线：v54 quiet speech \`6f32b2bd9476ea9206b52c113fb1d95ab21bfb50\`；**没有覆盖**v54分支或其APK。
- 实验独立分支：\`test/x200-simple-same-frame-phone-ab-20261104\`；最终构建commit：\`4fa45fecdeb04023c19ae83fe699c136ee8802a6\`。
- Android debug包名：\`com.vobich.kanzi.x200simpleorientationab\`；versionCode **1**、versionName **0.1.0-same-frame-orientation-ab**，与v54共存。
- 完整Build： [GitHub Actions 41839423818](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41839423818) **success**，包含真正Java/Kotlin Android Gradle assembleDebug、模型验证、签名v2、ARM64库、没有\`android.permission.INTERNET\`和独立包名审计。
- Release： [下载同帧极简实验独立APK](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-simple-ocr-angle-ab-20261104-experiment/Kanzi_X200_SameFrameOCRAB_experiment.apk)，上传资产390432655 bytes，SHA256 \`0b5e412f963f4e66e9ee8d0310e5e1793f611cceca57c59b40161161e65a14e2\`。
- 入口：打开「看字助手 · 同帧极简OCR实验」→「设置」→「OCR诊断」→ 点击「实验：同一画面原向0°与左转90°真实OCR对照」。
- 实际行为：一个手动操作抓一份NV21数据，OpenCV解码为Bitmap一次；对原始0°执行一次\`current.recognize\`；用矩阵270°生成逆时针90°的新Bitmap，再执行一次**同一个**\`current.recognize\`。实验必须选择PP-OCRv6 Small，不允许Tiny/v5混用。
- 每组记录真实SDK框计数、平均模型置信度、DET/REC模型耗时、完整一次OCR墙钟及**原始SDK文字内容**；另记录诊断前后系统热状态/内存状态。原始手机/USB预览方向不参与这次实验。
- 两次均只在用户手动按键后运行；不做整图自动定向、二次ROI补救、局部裁剪实验、AI版面或自动排序，也**不会把任意结果注入实时OCR/TTS**。原v54实时朗读/USB预览/缓存、默认Small/其他模型设置均不修改。实验不会上传摄像头画面到GitHub。
- 严格局限：**尚无用户X200 Pro本机执行结果、真实字符CER或温控验收。** CPU和模型编译测试不等于端侧识别率提升。这一版只给真正的0°和左90°文本证据；没有自动的人工真值，也没有用置信度替代正确率。两次全图OCR必然耗费更多诊断时间，所以只允许手动运行。
- 补充局限：当前极简按钮没有自动导出本次帧的原始图片ZIP。需要人工直接看同一块稳定目标文字，逐字对照A、B报告；若需严格计算同帧CER，应在下一次受控评测里把图像原文或同帧原始数据与结果一同留存。请勿把与不同时间抓取的单帧ZIP强行配对成「同帧」。
- 后续建议：收集两种关键真实目标（横排价格表与传统竖排牌），人工抄录主体原文，分别比较0°/左90°的实际字符编辑数。除非同一设备同一帧的真值显示更高准确率，否则不引入新的修复或发布正式v55。
