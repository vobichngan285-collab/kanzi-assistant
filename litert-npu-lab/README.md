# 蓝星 CPU / GPU 实验室 v0.1.7
- 根据v0.1.6真机结果暂移除GPU全面及NPU测试入口，保持LiteRT-LM 0.18.1、MediaTek runtime 2.3.2、相同模型SHA256与448内置简单图、CPU6、相同提示词、关闭思考、greedy，不改正式蓝星。GPU视觉为请求，不能证明硬件覆盖。
- 单模式4轮结束后保留同一独立推理进程与Engine；再次点同模式并选择新图，复用模型，记录resident_reused和load_ms。切换模式或停止/释放、退出Activity时终止隔离进程，避免双引擎占内存。系统仍可能回收进程，不承诺后台永久常驻。
- 自动对照按CPU/GPU/GPU/CPU（ABBA）4组各4轮，每组新进程，分开初始化与组内预热，两个模式各8轮，其中各2轮首次和6轮后续；避免同时载入两个模型。对照结束释放模型。记录batch/block、首字、总耗时、session、CPU累计时间、PSS、系统热状态、可用电池温度（0.1摄氏度，非芯片温度，-1不可用）、CER与输出数。不能根据CPU累计时间推断功耗或NPU。内置简单/密集合成图或真实相册原图，可选参考文字；无参考则CER=NA。日志复制最多600行，屏幕仅最近24行。保持10秒心跳及分阶段超时、手动停止和确认进程退出后才开始下组。
- 旧实验APK是CI临时签名，采用独立包com.vobich.bluestar.npulab.resident，保留旧版不覆盖。重新导入之前下载的1.698GB原始.litertlm文件，不重新下载；如原始文件丢失则可能需重新下载，旧版私有目录不能直接读。模型链接 https://huggingface.co/litert-community/MiniCPM-V-4.7-1B/resolve/main/MiniCPM-V-4.7-1B-VL_int8vis.litertlm
- 推荐先选内置密集图或真实密集图，点自动对照；再单独选CPU或GPU，完成后选新图再次点相同模式核实常驻。无TTS，无网络权限，数值日志不包含识别文字或图片。实际加速与常驻行为待真机验证。
