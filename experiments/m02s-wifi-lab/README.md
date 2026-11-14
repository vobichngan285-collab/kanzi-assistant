# M02S v0.1.9｜原厂启发式 AP 扫描/连接实验（独立 APK）

## 真机已验收的基线
- v0.1.3 BLE AA13 0x22/0x30 确认真机拍照成功。
- v0.1.5/0.1.6 精准 AP 连接 onAvailable、真实网关、HTTP200 列表与 3200×2400 原图下载成功，532ms。
- v0.1.7 先连接 AP 后快门无声；v0.1.8 先 BLE 拍照确有快门，AA14 0x22/0x42 有回应（相邻两次候选计数 00 08→00 09），但两次都在 0x25 后 5～6 秒 Android onUnavailable，没进入原图 GET。尚不能证明原因是手机家用 Wi-Fi 或眼镜 AP 模式本身。

## 本次只改 AP 建连逻辑，保留拍照和原图下载
依据用户提供的真实 HeyCyan APK（com.starglasses.app v1.0.24-c）和此前静态研究：原厂 Lxxh 具有 findTargetScanResult / shouldWaitForStableTargetScan / connectWithSpecifier / notifyIfAlreadyConnectedToTarget 等步骤，而 v0.1.8 是收 0x25 即请求网络。v0.1.9 **仅仿照其状态检查方式**，不宣称字节级照搬原厂方法。

1. BLE 连接 + AA14 订阅 → 点击「拍照→扫描AP→取原图」 → 单次官方拍照 0x22/0x30，等待 5 秒指令间隔 → 仅发送一次已验证 0x39/0x30，拿到 0x25 提供的精准 SSID。
2. 只尝试复用 Android **已连接且 SSID 与 BLE 完全一致、具有可信私有网关**的 Network，不绑定普通家庭 Wi-Fi。
3. 否则只在前台请求一次系统 Wi-Fi 扫描，最多 10 秒读近期扫描缓存；只认完整相同目标 SSID，过滤旧于30秒的缓存。唯一目标 BSSID 时精确锁定该 BSSID；多个同名目标时不用猜 BSSID，使用 v0.1.6 原有 SSID-only 方法；扫描数据无结果或被系统限流时，也保留已成功过的 SSID-only 连接回退，不会拒绝正确目标。
4. 仅以 Android onAvailable + 专属 Network + 路由获取到实际私有网关为成功，不接受虚假的「发送命令成功即 Wi-Fi 连通」。AP 连接确实失败时记录第几次请求、是否锁定 BSSID。只在再次扫描确认**同一精确 SSID 的唯一 BSSID 在近期确实存在**时，可以尝试 **至多一次**不同的连接锁定策略（BSSID ↔ SSID-only）。不重复 BLE 拍照、AP指令、不连接相邻 P2P 或未知热点；手机系统如再次弹授权由用户决定。
5. AP 连通后继续复用 v0.1.6 原厂 JSON 文件列表/原图只读 GET/内存 Bitmap 预览，不改上传/相册/删除功能。首次拍照没有拍前列表基线，必须显示「未验证最新候选，需人工确认」，不能冒充已经证明拍到了新图。
6. 成功/取消后只释放本应用建立的专属 Android AP 请求，不宣称眼镜已退出 AP 传输状态。实验目的是把失败明确划分为：目标热点扫描不可见 / Android请求不可用 / IP或HTTP失败 / 场景不一致。

## 真机测试（一次就够，不重复旧实验）
- 退出原厂 HeyCyan，让眼镜处于普通拍照状态，启动独立 v0.1.9。
- BLE LE1M 连接，开启 AA14 通知到 status=0；**不要提前连接 AP**。
- 对准一处新场景，只点一次「拍照→扫描AP→取原图」，观察真实快门声和自动连接授权。尽量留在实验 App 前台直至自动完成/失败。
- 回传脱敏诊断中新增的「AP阶段1/2/3」「onAvailable/网关」状态；若预览显示，人工确认是否新场景即可，不必发送图片本身。
- 如果热点扫描确实不存在，不要反复尝试和换家用 Wi-Fi；我们下一步将围绕眼镜 AP 广播和原厂模式转换分析。此版仅是独立诊断包，非最终常驻连接架构。

## 安全/版本隔离
独立包 com.vobichngan.bluestar.m02soemaplab；versionCode10/name0.1.9。照片只在内存；仅白名单 BLE 命令，不触碰眼镜固件、相册文件、云端、正式蓝星向导 RC25、Windows OCR，也不覆盖 v0.1.3～v0.1.8 旧安装包和 GitHub Release。APK 编译成功不等于真机验证成功。
