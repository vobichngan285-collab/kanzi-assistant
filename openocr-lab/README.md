# C110 原生1440P手动截帧实验 v0.1.2

从已验证原始1440P采集及短文字OCR的 v0.1.0 (264e47874131743df914ce2272eda0dd01518fda) 派生。不继承v0.1.1失败门控。独立包 com.vobich.bluestar.uvcliveocr012，不覆盖旧版。

默认启动相机仅预览与缓存，不自动OCR。16:9 SurfaceView 原生显示，可以关闭；Activity暂停时隐藏，服务继续运行。手动按钮复制最近一帧完整2560×1440 YUYV（7372800 bytes），拒绝超过500ms的陈旧缓存；没有屏幕截图、JPEG重编码或应用层缩图。再次点击取消旧推理、清除旧队列，识别当前快照并从首批文字开始朗读。原始Bitmap为3686400px；冻结DB内部长边1600，REC从原始图裁切。保持CPU4、两种阅读模式和模型资产不变。手动默认关闭自动识别，互不干扰。

自动识别为独立对照：不等稳定、不做亮度门控、单worker识别完成即取最新帧，完整原文仅按去空白后精确相等去重；有差异则立即抢读。不是最终实时抢占设计：自动画面改变不取消正在运行的OCR，小字变化需下一次识别确认，OCR错字抖动仍可能重复播报，持续运行会增加功耗。手动点击/关闭自动/停止可取消旧推理。用户此次优先要求建立手动原始帧基准，先不继续叠加场景门控。

日志 PREVIEW_ATTACHED/DETACHED、FIRST_FRAME、MANUAL_CAPTURE (真实帧序号、尺寸、字节、帧年龄及点击至复制完成)、OCR_INPUT (实际Bitmap尺寸)、FIRST_TEXT、TTS_FIRST (点击至回调非扬声器出声)、OCR_DONE、CANCEL_DONE、HEARTBEAT。帧率及预览、锁屏稳定性待真机，缓存每帧复制约221MB/s理论数据量并非实测功耗。先停止其他USB实验再启动相机，手动静止招牌/价目表，核对预览文字与识别结果，然后切换画面重复测试。自动实验另测，不把结果混为手动基准。

---

## 原冻结OCR说明

# 蓝星 OpenOCR v0.4.8 分辨率入口修复
v0.4.7 device failure: DbLocator retained old 960/1280/1600 require list, rejecting new settings before inference. Single shared DbResolution list for UI and locator; default1600; all seven settings validated in JVM test. No OCR model/algorithm changes. Higher resolutions still unverified on device.

# 蓝星 OpenOCR v0.4.7 分辨率上限实验
Fork frozen device-verified v0.4.6 47f324470a40be1f19f63df1bc712fe231c37301. Only add detection long-side 1920, 2048, 2560, 3200 to 960/1280/1600; default remains 1600. Both modes, model weights, preprocessing, geometry, recognition, thresholds, batch size, cache, input 16 MP limit, threads, TTS and logging unchanged. Higher resolutions are unverified; test progressively, stop at diminishing useful text gains, unacceptable latency/memory or instability. PSS is a point-in-time reading, not peak memory. Never count extra detection boxes as accuracy. No new model or arena features.

## Frozen v0.4.6 implementation reference
# 蓝星 OpenOCR v0.4.6 官方还原实验
Fork frozen v0.4.5 42f4acf348bd78c2d15d3d39fa333a7c3530057e. No change to previous releases or formal app.
Two manual modes only: official-oriented raw (default) and verified region (legacy v0.4.3 geometry/reader preserved). DB defaults .30/.60/min3/expanded5/unclip1.5/cap1000 fixed; 960 official baseline, 1280,1600 (default) remain. No auto routing, YOLO, custom vertical segmentation, raw dedup or parameter experiments active. No semantic correction.
Official port uses OpenCV INTER_LINEAR detection resize with Python rounding, round Clipper Java polygon offset (pinned 5ef8c0a467023c495e44e582e9cbd8ca7308a590 Boost license), refit and integer coordinate mapping; official sort, original resolution cubic perspective replicate border, rotate >=1.5, dynamic 48-high BGR normalization, official CTC greedy, batch six where ONNX batch dynamic (fixed batch 1 uses single), official recognition drop score .5 for display/TTS, all candidates in copied diagnostics. Verified region mode retains original Bitmap/continuous rectangle/Reader and FocusPlanner/RowPlanner/ColumnPlanner semantics; cache keyed by mode, original bitmap, generation, resolution and session. Existing CPU4/2/6 and offline TTS remain. No YOLO asset in release.
Reference: Topdu/OpenOCR 1ccfc6ee6161f7133b192e16af3a9265273997ba, configs/det/dbnet/repvit_db.yml, opendet/preprocess/db_resize_for_test.py, opendet/postprocess/db_postprocess.py, tools/infer/utility.py, tools/infer_e2e.py, tools/infer_rec.py, configs/rec/svtrv2/repsvtr_ch.yml, openrec/preprocess/resize.py, openrec/postprocess/ctc_postprocess.py. Android port, not claimed byte-identical to official Python (Java Clipper, Android image decode, float precision, runtime); weights and dictionary reused and verified against prior release, not freshly compared to upstream assets. JVM geometry tests; device parity and batch support/accuracy not yet verified. Compare official mode 1600 to v0.4.5 baseline on same images; legacy region regression with menu. Only evaluate actual recognized text, not confidence alone.
