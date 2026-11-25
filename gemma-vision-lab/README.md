# 蓝星 Gemma 1120 高预算实验 v0.1.1
独立安装包 com.vobich.bluestar.gemmalab.highbudget，与原厂280实验 v0.1.0 共存，不覆盖其模型/APP。模型为第三方 Penra E2B，非谷歌原厂部署包，量化/词表可能不同，不可把任何准确率差异完全归因于视觉预算。
固定下载 https://huggingface.co/PenraApp/penra-models/resolve/e7947bb3f9a2960713df011ebca32aedc0f271bb/penra-e2b-1120.litertlm ，1708468392 bytes，SHA256 4f52793970e74cae45b971e447cd5834dac6b9a70b687a5fe0f968c6e1c26b9e。发布前官方 LiteRT-LM Python ModelInfo 校验1120签名与元数据，APP再次校验文件SHA/实际视觉签名，不虚构560或280档；仅显示实际支持档位，默认1120。推理框架保持 Android LiteRT-LM 0.18.1、GPU/GPU、TopK1、TopP1、temperature0、context4000，关闭思考/MTP，保留原图/1024/512对照、模型常驻、追问、流式离线TTS及日志。真机GPU执行、首字和识别准确率待测。若仅有1120签名，仅测试1120。原厂高预算导出是独立路线。

首次真实校验发现原文件虽具1120视觉签名，metadata上限仍280。新CI先检查encoder+adapter实际计算图签名，确认metadata字段2520且pooling3，严格仅改该protobuf字段为10080（等长），验证其他protobuf字段完全一致、LiteRT-LM读取1120且新SHA；把由CI得到的精确offset/前后字节/新SHA打包进APK。APP导入原始第三方文件时先验证原SHA，再只在自己的临时副本上修正并验证完整新SHA，原下载文件、原厂280模型及其APP不改。若任一检查失败不发布APK。真机运行仍待验证。
