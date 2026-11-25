# 蓝星原厂权重实验 v0.2.0
独立应用 com.vobich.bluestar.nativelab；没有使用或修改原厂 Android App 源码，也没有修改正式蓝星向导。只链接原厂配套 llama.cpp-omni 引擎固定提交 0c0b1109f2d338acf65f28a7b0a1505da3945286，使用 openbmb 官方根目录 Q4_K_M（关闭思考）及 FP16 16x/4x projector，导入 SHA256 校验。不再重转换、重训练或修改视觉结构。模型在外部浏览器下载，实验包无 INTERNET 权限。原图只做字节复制和尺寸探测，推理引擎一次解码，不转 PNG，不缩图、不自动裁切。保留原生 M-RoPE/动态切片，slice_cap=9；实际图块数独立记录。默认 CPU6，CPU4 对照；ARMv8.6 dotprod/i8mm、KleidiAI、OpenMP、flash attention auto。针对 MT6991 ARM64，不承诺兼容其它芯片。GPU/NPU 不启用：之前同图 GPU 更慢，实际 NPU 未证实。模型常驻同权重同线程，独立清空上下文，切换权重/线程才重载。所有图片块各自记录视觉编码与嵌入预填充累计耗时。first_ms/total_ms不含冷模型load；file_read_ms单独记录。TTS使用系统离线中文音色，queued并非实际出声。用户照片不上传，数值日志不保存识别文本。首次需导入语言文件和16x视觉文件，4x可选。

EXIF方向在引擎解码后按原像素纠正，不重编码；所有活动共享串行推理执行器，避免销毁/重开同时使用全局native状态。发布前新增实际官方权重SHA及同固定引擎Host两档简单数字图烟测，Host通过不代表安卓运行或密集文字达标。
