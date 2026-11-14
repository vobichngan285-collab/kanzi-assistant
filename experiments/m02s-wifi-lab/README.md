# 蓝星 M02S 原始照片内存下载验证实验 v0.1.6

## 已经核实的手机真机基础
- vivo X200 Pro / Android 16：M02S-3584 蓝牙 GATT LE1M、AA14 通知、原厂 AP Wi-Fi 信息请求、0x25 SSID 解析、Android AP onAvailable、私有网关均成功。
- GET /app/getfilelist：HTTP 200 / 99ms / 635字节，后续16次连续访问全部HTTP 200（29～278ms）。
- 应用切换到 ChatGPT 后出现约63秒调度空档，恢复访问仍然成功；不能由此推断每3秒后台请求持续运行。
- 原厂 BLE 遥控拍照用户已经亲测。原始照片内容此前没有下载或验证成功。

## 官方 HeyCyan APK 核查
- 原厂 APK com.starglasses.app v1.0.24-c 的 classes5.dex 已实际核对包含 app/getfilelist、app/getthumbnail?file=、PhotoApBean、FilesDTO 与 createtimestr，具体方法链见原仓库 PROJECT_HANDOFF.md。
- AP 模式列表接口 GET /app/getfilelist，JSON 使用 info[] → files[] → name、size、createtimestr、type；旧 P2P 模式才使用 FileListParser.parseXml，不能混淆。
- AP 原图下载使用设备根地址加 FileItem.getName()，缩略图是 /app/getthumbnail?file=...；本实验仅研究单张原图，不要求缩略图。
- 本版不发送 fileDownloadFinish 或其它未经真机验证的蓝牙私有指令，也不主动断开 AP。

## 实验程序
- 在独立版本 v0.1.5 代码基础上开发 v0.1.6；独立包名 com.vobichngan.bluestar.m02sphotolab，正式蓝星向导、官方 APK 和旧实验不更改。
- 保留蓝牙 LE1M → AA14 status=0 → AP热点精准连接 → onAvailable+真实私有网关 的已验证链路。
- 新增“只解析照片列表”：通过已验证的眼镜 AP Network 请求官方JSON，大小上限256KB，不打印照片名、时间或JSON，只显示候选数和能否判断时间。
- 新增“获取最新照片（自动）”：先读取列表，只有唯一候选或能确认唯一最新时间才下载。多个候选且时间缺失或并列时拒绝猜测，用户可从匿名序号下拉框手动选一张点击“下载选中照片（手动）”。
- 原图URL遵循官方 root+name 规则，但限制必须为实际AP Network + 私有网关地址 + 安全相对图片路径（拒绝绝对路径、上级路径、URL/查询/片段等）。不跟随重定向，不切换到其他网络，严格只读HTTP GET。
- JSON限256KB，原图限32MB；校验 JPEG/PNG/WebP 文件头、扩展名与Android BitmapFactory解码尺寸；压缩比例只针对预览缩放，原始图片字节保留在当前APP进程内存。退出、取消网络或点“清除内存预览”即丢弃。
- 不保存相册、不写磁盘、不读取其他照片、不上传云、不删除眼镜文件、不碰固件，不自动发蓝牙完成通知或断开Wi-Fi。
- 新增单测覆盖官方AP结构的合成JSON、路径防穿越、图片头检查、最新图片歧义拒绝、重复记录与超大图片拒绝。

## 单次真机测试说明
1. 退出官方HeyCyan，打开v0.1.6，扫描M02S，连接LE1M，开启AA14监听等待订阅status=0。
2. 点“AP热点精准连接”，等待Android onAvailable和私有网关就绪。
3. 先点一次“只解析照片列表”，若JSON解析失败请直接发诊断日志，不要重复扫描。
4. 如果列表解析成功，点“获取最新照片（自动）”；若显示最新时间无法确定，可手动选择匿名候选序号并只下载一张。
5. 成功会在实验APP中显示照片预览，日志显示原图尺寸、文件大小、耗时和摘要。HTTP200列表本身不代表照片下载成功。
6. 不需要提供原始照片、完整文件名、网络SSID、连接密码或全部JSON；发送自动脱敏的诊断日志即可。

## 当前未解决
原厂 AP JSON 的真实635字节内容仍未知，可能与假设的字段形状不同；实际M02S原图远程路径和图片字节尚未真机验证。后台自动监控、长期AP连通和云端AI朗读未进入本实验。
