# C110 实时离线 OCR v0.1.1 — 运动阶段调度实验

独立包 com.vobich.bluestar.uvcliveocr011；冻结 v0.1.0 不覆盖。保持已实测原生 2560×1440 MJPEG 约30fps、无显示窗口、DB1600/RepSVTR/CPU4、官方原框与智能阅读区、增量TTS、前台服务与锁屏设计、无网络权限。仅改变画面门控及调度。

参考历史 99363da41f3a0ca51d7f09fc686533d1f55fa911 的 FrameChangeGate、RealtimeSceneTracker、LayoutSceneFingerprint、RealtimeSpeechGuard 及 docs/x200-cnlite-v56-immediate-recfast.md。旧版真实价目表30行/30框 DET234ms REC902ms 为旧模型/场景数据，不能与本版直接比；旧 v55 记载正常划框和朗读，旧 v56 文档明确曝光/运动和同文字重复播报风险。未直接复用旧模型、固定重检间隔或等待整轮OCR后切换。

本版64×36亮度签名每格5点，扣除全局亮度偏移，比较上次接受的锚点与相邻帧，兼顾全局和局部差异。确认与锚点变化时仅一次取消旧任务和TTS、清除旧pending；运动期间不启动OCR、不复制全图、不积压；连续两次相邻帧低变化后，立即取当下原始帧识别。30fps下静止证据约67ms，实际可能更长；不是固定延迟或稳定保证。手动重识别绕过门控。局部文字可能漏检、缓慢移动或曝光非均匀变化仍可能触发错误，需实测；不加入文字相似度抑制，以免误删不同价格或共享标题的新文字，后续需验证同页重复。旧任务真正停止时间独立记录 CANCEL_DONE cancel_to_release_ms；MOTION_BEGIN、NEW、HEARTBEAT 汇总运动、替换和取消。不得把 TTS_START 回调当实际声音。

测试：静止短招牌，轻微晃动/曝光，同页重复，连续移动，移动后停在新文字，局部小字变化，锁屏；记录实际听感、首次文字/回调时间、完成数和准确率。静止/运动/曝光/局部变化/手动触发的合成单元测试不等同于真机验证。无需屏幕预览或实体快门。停止旧版后再运行本版，避免争用USB。

---

## 原冻结OCR说明（历史参考）

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
