# 看字助手｜极简方向输入的真实 PP-OCRv6 Small A/B（2026-11-04）

## 目的
不增加新模型、裁剪补救、文字猜测、TTS改动。对完全相同公开图片，使用固定SHA的 PP-OCRv6 Small DET+REC 对比：
- A：图片正确原向 0°
- B：对同一图片像素向左旋转90°（代码角度270°），不拉伸

在两个分开验证的公开数据组运行实际 DET+REC（不是重排旧 OCR，也不是纯 Java 单元测试）。GT 多边形随图片一起旋转，同一位置同一原文；固定模型SHA、CPU线程数、阈值/预处理和识别配置，使用既有SDK近似 Python/ONNX 推理助手。GT没有参与选择OCR结果。

## 模型固定
- DET SHA256：d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e
- REC SHA256：5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634
- 识别YML SHA256：ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1

## 组1：自然街景12张，72个GT文字区域／271字
来源：Yesianrohn/OCR-Data（RCTW 1400–1405，ReCTS 500–505）。Run [41835799303](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41835799303) 完整SUCCESS，数值Artifact [13325571525](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41835799303/artifacts/13325571525)。
- 原向0°：72个GT中匹配34区域
- 左转90°：72个GT中匹配27区域
- 两者都匹配的同27区域／124字符：0° CER 7.258%；左转90° CER 70.968%。
- 左转相对0°：1个GT区域编辑数改善；29个退步；42个不变。
- 注意：本组大部分不是传统竖排，符合高窄GT代理仅2个，不能把此组当作竖排测试。

## 组2：传统竖排标注页面8张，336个GT文字区域／9316字
来源：AlroWilde/vertical_annotations（公共带标注资料的前8张有效页；含古籍排版，不是8张独立手机实拍）。Run [41836447600](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41836447600) 完整SUCCESS，数值Artifact [13328125175](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41836447600/artifacts/13328125175)。
- A 0°：333/336 GT 区域能与检测框按AABB IoU≥0.4匹配。
- B 左转90°：333/336 GT 区域能匹配。
- 两组都能匹配的相同333个GT区域，9310字，0° CER **15.747%**，左转90° CER **16.584%**。B高出约0.837个百分点（变差）。
- 按336个原文GT区域逐项对比，B的编辑数更少41个，更多84个，持平211个。
- Linux CPU 单页平均DET 250.9ms vs 246.8ms；REC 2092.2ms vs 2627.7ms；不是vivo实机耗时，不能推断功耗。
- 本组全图左转90°并未提高按GT配对的字符正确性。可见的方向处理与汉字本身朝向是不同概念：传统竖排多数字符是直立的，全页左转会把原本直立的汉字转成横躺；SDK本身也会对高竖框做局部方向处理，因此不应默认全页左转。

## 重要限制
1. 这是Linux GitHub Actions环境真实 Small 检测+识别测试，但不是用户vivo X200 Pro上9份私人NV21帧的重新模型推理、不是Android SDK逐字节等价、也不是端侧TTS/热验收。
2. 匹配以AABB IoU≥0.4为几何代理，区域CER只对两组都匹配的**相同GT区域、相同字符分母**计算；未匹配的多余检测框没有纳入整页文字错误率，不能称为整机综合CER。
3. 竖排组来自固定8页，结果可反证“旋转一定提高”的强主张，不能证明所有竖排都不该旋转。
4. 本实验没有针对模糊小字做模型输入过滤，也没有自动筛掉标注外字；因此不证明“忽略小字”提高真实识别率。
5. 私人用户图片、ZIP、NV21、原始OCR汉字全部没有进入本公开仓库与Actions。仅输出公开数据的匿名数值和定位索引。
6. 这次只在隔离分支添加实验程序和报告；**v54程序、模型、TTS、APK全部未动**。

## 研发判断
这次真实A/B不支持“传统竖排一律将完整图片左转90°送进OCR”作为新的默认策略。认可用户的**极简主线**（正确方向、等比例、Small一次OCR、原文保真、稳定朗读），但方向定义必须基于照片实际旋向与字形是否正立，不把“竖排阅读顺序向右到左”混同于“整图像素旋转90°”。

下一步若仅能做一次修改，应在安卓本地同一捕获帧里**只诊断**0°与实际候选270°、固定Small，并用人工GT比较正确输出和成本。不要因单张图而加新补救规则、不要生成未经真实提升证明的v55。

## 复现入口
- 源脚本：scripts/x200_simple_orientation_public_ab.py 和 scripts/x200_simple_vertical_public_ab.py
- 工作流：.github/workflows/x200-simple-orientation-public-ab.yml 和 .github/workflows/x200-simple-vertical-public-ab.yml
- 完成记录：[自然图 Run 41835799303](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41835799303)；[竖排 Run 41836447600](https://github.com/vobichngan285-collab/kanzi-assistant/actions/runs/41836447600)
