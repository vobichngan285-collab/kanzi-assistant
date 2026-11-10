# Blue Star Photo Watch Lab v0.1.2 / 蓝星照片原生事件监听实验工具

**独立 Android APK，独立应用包名 \`com.vobichngan.bluestar.photowatch\`。**
不会修改蓝星向导现有 APK、用户设置、API Key、书籍或相机功能。

## 测试流程

1. 在手机上安装实验 APK，开启应用。
2. 点击「选择照片文件夹」，通过 Android 系统文件选择器授权
   \`DCIM/Camera\`（前提是相机的真实保存路径；某些机型可能不可选）。
3. 点击「开始自动监听」，等待「已忽略 N 张原有照片」。
4. 点击「打开手机相机」，拍摄并保存一张新照片。
5. 返回实验工具，观察「发现新照片」数量、名称、扫描时间与估计延迟；
   若本机有可用的中文 TTS，会播报「发现新照片」。
6. 可切到其他 App、短暂锁屏后重试，测试后台监听。用「停止监听」
   或通知栏「停止」按钮结束。**30分钟后自动停止**。

**目前不包含任何 OCR、云端 API、上传、照片解析或 AI 眼镜协议。**
没有 INTERNET 权限。只读取系统授权文件夹中**直接存在**的图片元数据，
不会递归扫描子目录。

## 设计说明

- 采用 Android SAF \`ACTION_OPEN_DOCUMENT_TREE\` 持久化只读授权；
  通过 \`DocumentsContract\` 读取目录子文件元数据。
- \`ContentObserver\` 有通知时立即触发检测；系统不提供事件时每 2 秒兜底。
- 启动首次扫描仅建立基线；新图只要首次出现且文件元数据报告非零大小，就立即进行一次语音提示，不等待第二轮稳定检查。
  **这只适用于新照片“到达通知”的实验，不代表图片已经写入完整；未来接 OCR 时仍须单独验证图片完整性。**
- 前台通知服务允许用户切应用；为息屏检测短时保留 Partial WakeLock，
  最多 30 分钟。Android 系统与厂商省电策略仍可能中断该实验。
- 延迟是文件修改时间到系统发现时间的**近似值**；另测“开始扫描到 TTS 请求”的时间，但不代表扬声器真正出声，与眼镜拍照至手机
  自动同步的全链路耗时不同；文件供应方也可能不提供修改时间。
- Android 11 起系统文件夹选择器禁止选择部分存储卷根目录与
  Download 根目录。部分设备可能不能选择想要的相册，遇到这个问题
  可以后续考虑 MediaStore 相册监听方案。
- 使用原生 Android Java 实现，无网络 SDK、无后台开机自启动、
  无图片内容采集；仅测试基于元数据的文件到达事件。
- Android 14 FGS \`dataSync\` 声明了对应权限，
  \`POST_NOTIFICATIONS\` 仅用于系统通知。

## 构建

在 Java 17、Android SDK 36、Gradle 8.11.1 的环境下：

    gradle -p experiments/photo-watch :app:testDebugUnitTest :app:assembleDebug

输出 APK：

    experiments/photo-watch/app/build/outputs/apk/debug/app-debug.apk

构建/公开 APK 使用 \`.github/workflows/photo-watch-lab.yml\`；
只在本修复实验分支触发，**绝不触发蓝星向导 RC25 正式 APK 的构建工作流**。

## 0.1.2 原生目录变化直达实验

本版在上次 vivo 约2秒的真机反馈后增加 **Android FileObserver**
（Linux inotify 封装）的原始文件系统事件监听：

- 只有系统文件夹选择器授权的路径精确匹配 `primary:DCIM/Camera` 时
  才尝试直接订阅实际 `DCIM/Camera` 的 CREATE / MODIFY /
  MOVED_TO / CLOSE_WRITE 事件，不尝试遍历任意目录或绕过 SAF。
- 首次 SAF 列表扫描建立既有文件的基线。新的 JPEG/PNG/HEIC/WEBP
  文件名在原生文件系统事件中首次出现时，**立即请求本地 TTS**，
  不等待 MediaStore/SAF 提供新条目，也不等待图片写完。
- 旧图像、同一文件的多次写入、稍后 SAF 出现的新条目都通过文件名
  去重，不重复语音。
- 新版状态会显示「文件系统监听已注册（待验证）」、
  「已收到真实文件系统事件」，以及最近一次成功识别究竟来自
  FileObserver 直达，还是 SAF 目录扫描。
- 记录原生事件/扫描到 `TTS.speak` 请求耗时以及 TTS
  `UtteranceProgressListener.onStart` 引擎反馈耗时。
  注意 **onStart 也不是扬声器真正出声时间**。
- SAF 目录授权不保证原始路径可以被 FileObserver 读取；
  Android 16 的 FUSE/厂商系统可能不产生任何原生文件事件。
  此时仍使用已验证的 SAF+MediaStore+600ms 备用扫描，不允许假称直连成功。
- 通知功能仅发送「发现新照片」，**不意味着此刻可以完整读取/上传照片**。
  将来如果对接蓝星 OCR，必须另外做完整图像验证。
- 没有照片内容读取、上传、Root、管理全部文件权限或额外存储权限。
  原版蓝星向导 RC25 不改。

### 手机判读结果

用手机拍照后，查看最后一次**发现渠道**：

- `文件系统 FileObserver（直达）`：说明最短路径确实在此设备起效；
- `SAF 目录扫描 / 系统通知`：说明原生目录事件没有首先到达，
  当前使用安全回退，勿声称全链路直接访问成功。

这一步仍只验证相机照片到达，不验证将来 AI 眼镜的同步用时。
