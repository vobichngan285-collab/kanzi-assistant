# Blue Star Photo Watch Lab v0.1.0 / 蓝星照片监听实验工具

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
- 启动首次扫描仅建立基线；新图必须在两次扫描间大小、修改时间保持稳定；
  只播报一次。对于官方 App 同步过程中先建立文件再追加内容的情况，
  这个门槛可避免读取半写入图，但仍需特定设备实测。
- 前台通知服务允许用户切应用；为息屏检测短时保留 Partial WakeLock，
  最多 30 分钟。Android 系统与厂商省电策略仍可能中断该实验。
- 延迟是文件修改时间到系统发现时间的**近似值**，与眼镜拍照至手机
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
只在本实验分支触发，**绝不触发蓝星向导 RC25 正式 APK 的构建工作流**。
