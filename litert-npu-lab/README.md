# 蓝星 NPU 实验室 v0.1.4
独立真机实验，不覆盖正式蓝星或旧GGUF实验室；APK是测试工具，不是已验证提速版。
- LiteRT-LM Android 0.18.1 + 官方MediaTek runtime 2.3.2，固定配对，依据官方API和公开的MT6989 AAR加载报告（非MT6991 MiniCPM验证）。源代码v0.18.1确认vision executor NPU分支请求NPU|CPU，故不能把NPU选项或成功推理当成纯NPU证明。
- 指定同家族社区模型：https://huggingface.co/litert-community/MiniCPM-V-4.7-1B/resolve/main/MiniCPM-V-4.7-1B-VL_int8vis.litertlm ，约1.70GB，SHA256 20c73b5a1e48a315974cff1853535ff5d017100b6420d5ef221b9dd1b955dd7c。与GGUF不同格式，不可复用旧权重，保留旧版。浏览器下载后选择文件导入，可能需额外约1.70GB存储。APP自身无网络权限。
- 先用内置图点CPU6，再点NPU视觉+CPU6；每组3次新conversation、共享组内engine、关闭思考、greedy。也可自动对比。结果文字在屏幕显示；复制日志只含数值/后端请求/错误类型，未含照片或识别文字。空答案标记empty。first_nonblank不是TTS可朗读延迟。无TTS。真实NPU算子覆盖/回退需配合原生日志（ADB logcat按app PID过滤）；不宣称真实NPU已启用。NPU可能失败、native crash或首次JIT慢，重开可查看持久化阶段。无真机测试不能宣称提速。
- APK构建后自动验证版本、签名、arm64 JNI与MediaTek插件/dispatch打包、无INTERNET权限、单元测试，再发布实验Release。JIT路线绕开Host AOT障碍，未改原模型/程序。
