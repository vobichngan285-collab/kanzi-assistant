# 蓝星 GPU / NPU 实验室 v0.1.6
- 使用已验证的v0.1.4 LiteRT-LM 0.18.1、MediaTek runtime 2.3.2、同一1.698GB模型（SHA不变），同一448内置测试图和提示词，保持CPU6基准、关闭思考、每组3轮。新增GPU视觉+CPU6、GPU全面、NPU视觉+CPU6。GPU/NPU请求不等于真实硬件执行或提速；仍需真机结果、原生日志。CPU累计时间与PSS仅辅助，不证明全部算子GPU/NPU。CPU与GPU模式会使用各自缓存。
- v0.1.4使用CI临时debug签名，无法保证覆盖安装，故独立包com.vobich.bluestar.npulab.speed，不卸载旧版。选择之前已下载的原始模型重新导入即可，无需再次下载。约1.70GB内部副本；如原文件已删除，不能保证从旧版私有目录直接访问。下载链接（仅原文件丢失时）：https://huggingface.co/litert-community/MiniCPM-V-4.7-1B/resolve/main/MiniCPM-V-4.7-1B-VL_int8vis.litertlm
- UI与每组推理分离到独立:inference进程；阶段connect/load/conversation/send/first_text/close均记录，10秒心跳，加载/创建会话120s、等待首字180s、首字后120s、关闭30s上限。超时或手动停止终止隔离进程，确认binder死亡后才进入下组；不能确认退出则停止批次。不同组重新加载，同组共享模型。数值日志不包含图片和OCR文字。无TTS，不修改正式蓝星。不宣称解决NPU卡顿根因。
- 推荐先选内置图→自动对比CPU/GPU；随后单独NPU，或选全部对比。复制数值日志。NPU可能需要JIT，超时只能说明本轮在限定时间内未完成。首次session、加载与首字分别记录，native TTFT不是完整视觉延迟。
- CI单测、编译、签名、包版本、ARM64运行库、独立进程声明及无INTERNET权限验证后发布实验APK。真机功能及GPU加速尚未验证。
- 代码复核修正跨模式切换时看门狗可能停止的问题；不建议使用中间构建v0.1.5。
