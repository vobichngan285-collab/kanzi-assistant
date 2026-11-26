# 蓝心／蓝星向导 — 当前研发交接（2026-11-27）
新对话先读本页。只恢复以下两条活跃研发线，不自动恢复旧实验。正式APP保持不动。历史流水账已从本页移除，原记录可由 Git 历史追溯（清理前提交 e29ee62f96a5e7fed8f43c2c4558a1ae4b09dc7e），无需日常读取。

## 1. 当前任务 A：眼脑协同 GPU v0.2.4 — 等待用户真机测试
- 分支 exp/eye-brain-gpu-20261127；构建提交 46fcd2239c95c0d1432c701fc5b60ae8ad20cece；[CI](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45858313534) build/publish success。
- [APK 直接下载](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-eye-brain-gpu-v024-20261127/BlueStar_EyeBrain_GPU_0.2.4.apk)；独立包 com.vobich.bluestar.eyebrain.gallerygpu，versionName 1.0.23（兼容原厂目录），首页标识 v0.2.4。
- 以曾真机GPU正常的 v0.2.0（08977a0f4e57d02d22a3b85901e39992e6b32b2d）为回归基线，恢复原厂目录、ABI、任务注册、无OCR常驻；保留八任务和自动提交。Google AI Edge Gallery 1.0.23 原工程、Gemma原权重与推理不改。v0.2.3 GPU选择后退出，根因尚未定位；v0.2.4 是隔离回归，不保证修复，也未保留全部精简优化。
- 下一步：用户尚未测试。核对GPU初始化、实际后端、首字/总耗时、稳定性及崩溃日志；同模型同输入对照CPU/GPU。先取得真机结果再修改。

## 2. 当前任务 B：OpenOCR v0.4.3 原厂 RepViT-DB 画框工具专项
- 尚未开发或构建 v0.4.3。当前 v0.4.2 分支 exp/openocr-field-db-20261127，构建 e630d3e0267cd311457ffb3d7071ebdf433c4b82，[CI](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/45855950792) 成功，[APK](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-field-db-v042-20261127/BlueStar_OpenOCR_Field_DB_0.4.2.apk)。默认DB全图原框去重逐框识别，保留YOLO切换、原区域模式、原Reader、RepSVTR、字典、CPU4、原文TTS和诊断；每次只运行一个检测器。不接大脑。
- 用户实地反馈：DB在倾斜店铺招牌、街道自然文字更有优势；YOLO26n在规整价目表、小字可能更准确。**默认YOLO已经多轮专项优化（完整列裁剪、ROI、主阅读区等），原厂DB几乎没有同等专项优化，仅有通用重建/扩框实验；不能将当前结果直接归因于模型优劣。**
- 夜间倾斜招牌单图热启动 n534/s604/m879/l1074/x1854/DB506ms；s、x、DB检出中文主招牌，DB另覆盖小字但漏具体时刻。历史同图layout4 DB1415ms、YOLO2371ms；十项价格首读8/10与10/10，DB两处错误负号的扩框候选经人工核对正确，不代表自动纠正。输入尺寸不同、样本有限，不宣称普遍优越。
- P0 核对官方预/后处理与Android移植；P1 四边形透视矫正及裁剪；P2 DB专属横排行列重建及小字适配；P3 项目金额对应；P4 主阅读区适配；P5 初始化、导入及识别提速。
- 保留原DB和YOLO稳定基线，逐项消融，同图同识别器，记录输入尺寸、漏检、误检、裁剪/识别错误及耗时。不得牺牲街景覆盖、猜测金额或删除负号。暂缓双检测器自动切换，待DB兼顾两类场景再决定。等待用户授权开始源码修改。

## 3. 必须保护的有效基线（非活跃实验）
- OpenOCR v0.4.1 原文TTS：用户明确授权冻结，真机反馈快速（约零点几秒至1秒为主观体感）。分支 exp/openocr-system-tts-20261126，提交 c550509adb2997c5a009a6c5c4a461df4e7f393c，[APK](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-openocr-focus-tts-v041-20261126/BlueStar_OpenOCR_Focus_TTS_0.4.1.apk)。原YOLO26n、RepSVTR、CPU4、四区域模式，原文直接系统TTS，无AI修饰。不得覆盖或删除冻结版本及tag。
- OCR v0.1.3 核心固定提交 38df92b5bec824a2abf269e2d775e6efcd241455；眼脑实验按原blob复用。六模型竞技场 exp/openocr-arena-20261126 固定参考 5ac4b9a853a4e2eee539894ac9f76f53ccc3aaaa，供DB原资产与对照复用。
- 正式蓝星向导 v1.0.98：分支 exp/c110-event-listener-v0198-20261125，提交 a1850a37e965e1b9115ca26dd277755749811c03，[APK](https://github.com/vobichngan285-collab/kanzi-assistant/releases/download/bluestar-c110-event-listener-20261125-v0198/BlueStar_Guide_Event_Listener_1.0.98.apk)，CI成功，完整真机验收未确认。C110被动拍照通知、移除自动删图；正式功能保持，不主动恢复硬件研发。早期RC25及其他硬件历史不再列为当前任务。

## 4. 研发纪律
- 每次实质进展立即更新本页，提交main、回读验证、告知提交链接；失败明确报告。保持当前状态简洁，已结束流水账从当前页移除，仍被依赖的成果只保留必要引用。
- 不新建仓库；未经授权不改源码、构建或发行。独立实验分支、独立包及新Release；不动正式APP、不覆盖冻结基线。核对源码、CI、签名、发布及真实设备结果，CI成功不等于真机成功。
- 眼睛OCR、大脑Gemma、嘴巴TTS分开；保留原文快速朗读通道。禁止上传私人照片、原始OCR文本、地址、Key等。
- 旧CRAFT/ChineseOCR-Lite及其他过时实验不主动恢复。历史分支及其他文档尚未物理删除：当前接口不支持删除分支；本次只精简唯一交接入口，不宣称全仓清理完成。
