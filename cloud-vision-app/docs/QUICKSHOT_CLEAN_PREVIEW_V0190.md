# v1.0.90 快捷拍照恢复与预览清理
用户暂停画质实验，反馈保益双击弹出“请先安装蓝星向导v1.0.59主程序”。从正式v1.0.89 58963e56aaf772385979acea9d4a86949200f6e5派生，保留正式包名 com.vobich.bluestar.c110plusquick159、原HomeActivity和旧桥接器 com.vobich.bluestar.boyuebridge。独立画质实验不合入，不删除旧发行。
旧桥接器明确发送 QUICK_SHOT action 到 HomeActivity，正式Manifest只有 MAIN/LAUNCHER filter。Android 13+目标应用跨包Intent可能要求匹配action/category，不匹配可抛ActivityNotFoundException，符合截图错误分支；是否为用户手机唯一原因尚未确认。新增仅匹配显式QUICK_SHOT的独立filter（无LAUNCHER、无DEFAULT）；原启动器包/组件/action不变，普通打开主页不触发拍照。Home增加resumed守卫，避免未resume时消费一次性请求后Controller拒绝。仍用原快门、云、TTS链，不增加无障碍权限，不重新绑定音量键。
C110预览从正式版保留当前Surface取帧，移除查看上传图、其弹窗及无用途JPEG内存副本；没有A/B实验按钮/NativeFrameDecoder。高清拍照恢复底部唯一按钮，其他显示、Wi-Fi、OEM、手机/USB、云模型/TTS不变。
测试：保留原安装，覆盖安装正式包，保益仍绑定蓝星一键拍照。如果只安装过独立实验APP，新正式版为不同包，可能需重新设置API Key，实验包不删除。先手动打开正式版（不得自动拍照），再双击音量键（应只拍一张C110高清原图并朗读）；分别测试桌面/前台/已在预览页的触发。检查预览无A/B和查看上传图；记录真实失败消息。CI静态与编译检查不等于真机验收。
