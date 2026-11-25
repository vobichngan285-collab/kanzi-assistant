# 蓝星 Gemma 560 混合视觉实验 v0.1.4
保持 v0.1.3 已验证模型原字节与模型 SHA，不再导出视觉权重。基于官方 LiteRT-LM v0.18.1 固定提交 bda624851d74be34d4e1f7fac1fc4155939539c7 重编 ARM64 JNI；视觉 encoder/adapter GPU 分支允许 GPU+CPU，保留原 CPU 模式。原版 Kotlin、GPU加速库及其余 AAR 文件保留，JNI 导出、ELF 依赖和 APK 内 JNI SHA 检查通过才发布。不是所有算子实际在 GPU 的保证，也不保证速度提升。
独立包名 .hybrid，不覆盖0.1.3，须重新导入原厂文件并留足约3.1GB模型空间。相同560预算、原图，先CPU/CPU对照，再混合视觉/CPU、混合视觉/GPU各测冷/热；检查识别文字、首字/总耗时、运行错误。native log 标记 BLUESTAR_VISION_HYBRID_014 表示混合配置与执行完成，不是实际GPU占比证明。旧版与正式蓝星保留。
