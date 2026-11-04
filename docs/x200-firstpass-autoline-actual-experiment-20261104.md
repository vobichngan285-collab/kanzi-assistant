# 看字助手 X200｜首遍自动分行裁切独立实验版（2026-11-04）

## 实验原则
同一PP-OCRv6 Small；优先修复检测框把多条横排误合并成细长竖框。仅在原始检测之后、一次性Small REC之前按严格几何门槛重建行裁切。不添加VLM、模型猜字、默认二次全图OCR或语义补字。

## 已完成的两项证据
1. **手机原有9份私人样本复核**：188个真实SDK框。第7份价目表出现2个狭长跨行框，原图经几何分行得到4条整行ROI，从原有47个框替换13个错误/碎片框后为38个。此前已实用相同Small REC重识别4行主体共16字，全部正确，原框简单顺序拼接的该16字编辑距离为7。其余8份保存样本不触发；用户私人文件和原始汉字不上传GitHub。此为已知正例而非独立正样本。
2. **全新公开防误触发组**：36张其他自然场景照片和8张其他传统竖排公开页面，实际重新执行Small DET共548个框，零次触发。成功CI：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41855459247 。Python模拟SDK框，不等于Android逐字节复现；没有新正例触发，不证明这44张识别率提高。
3. **历史简单扩框反例**：公开36张/122配对区域，直接放大框5%令CER由16.09%变为16.67%，故本版没有采用简单扩框。

## 实际Android实现
- 实验源码：scripts/crossrow_sdk/CrossRowFirstPass.kt，打包时脚本 scripts/patch_x200_firstpass_rows.py 在官方SDK OCREngine.detect()后、BoxSorter与RecognitionEngine之前替换符合强条件的ROI。
- 保守条件：至少2个相距65px以上的窄高短框，周围有3–8个对齐横行的短框；出现候选歧义、边界超限或覆盖风险，一律保留原始检测框。
- 原默认是启用实验开关，可在设置→OCR诊断中点击“自动分行实验：开启（点击切回原版）”关闭，关闭后完全走原始Small框。诊断报告包含“首遍自动分行状态”，可核查是否实际触发。
- 正常帧的Small DET仍只跑一次，识别器只对最终ROI执行一次，没有新增语义或自动全页二次推理。改变ROI数量会影响识别耗时，因此不保证零额外CPU负担。
- 新APK独立包名 com.vobich.kanzi.x200autolineab，versionCode=1，versionName=0.1.0-experimental-firstpass-auto-row；保持旧v54独立存在。

## 构建与下载
- GitHub Actions完整成功：https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41855699826 。Android编译，APK V2签名，ARM64与官方固定Small模型SHA，无INTERNET权限审计通过。
- APK直链：https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/x200-firstpass-auto-row-20261104-experimental/Kanzi_X200_AutoRow_FirstPass_AB_experimental.apk
- 文件大小390449039字节；SHA256 c4c692f0a5397391f257dc50fc75e7e7cc76dccaebf5655cb34d999ff9a51ffb 。
- 只有正式APK编译/发布成功，并未在vivo X200 Pro实际安装验收，不可称手机识别率已经提高。v54代码/安装包不变，不是v55正式版。

## 用户真机验证
先在新实验APP拍同一块价目表，进入设置→OCR诊断运行，检查首遍自动分行状态与实际文字；在设置页关闭自动分行并保持照片/距离基本不变再诊断，逐字记录错、漏、多字。再拍真正竖排和普通大字页面，确认它们不被误切，观察语音顺序、速度及温度。不同时间的两帧对比不能当作严格像素同帧实验。实验门槛未通过时继续保留v54。