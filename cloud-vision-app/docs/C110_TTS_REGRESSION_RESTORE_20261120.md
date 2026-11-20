# v1.0.61 C110+ 快拍/预览广荣微软语音无声：最小回归修复

## 已查证的版本差异
- 原仓库 `vobichngan285-collab/kanzi-assistant`。基线 v1.0.60 已发布 `exp/c110-settings-direct-controls-20261120` HEAD `aae12305e0d7ae908898c284a8ddfe610d8f005d`。
- 用户实测C110原厂高清1001拍照成功、OCR识别成功（A），但没有语音，且**C110 Wi-Fi连接时进入蓝星统一设置里的「试听当前语音」也无声**，用户指出此前旧版本曾正常出声。对照实际GitHub v1.0.50 (`cdd131908fbed3e22c9ee351a707272c60108dce`)、v1.0.55(`613c051de9810ce0b372a2afd725178f9ccec909`)、v1.0.58(`d0e404fb721ccee2a6c2895e9438dfa4018ae4bd`)、v1.0.60：
  1. v1.0.50的 `MainActivity` 是唯一LAUNCHER，它独占语音引擎；v1.0.50根本没有 `HomeActivity.java`。自增加独立首页及原位 C110 快拍后，`HomeActivity.onCreate` 无条件构造 `C110QuickShotController` 并通过 `C110FrameReader` 立即创建 **第一套TTS**；用户点击「设置」另起 `MainActivity` 后，`initializeSpeech()` 再建立**第二套TTS**。两套并行第三方语音实例是真实的结构性差异，对广荣微软TTS的影响需真机验证，不能仅由源码判为100%根因。
  2. 早期 `C110FrameReader` 明确执行 `tts.setLanguage(Locale.SIMPLIFIED_CHINESE)` 后再设置语速；v1.0.58在没有C110真机声音验收的情况下删除此调用，v1.0.60仍沿用删除后的代码。这是另一处明确的语音初始化回归。
  3. `MainActivity.initializeSpeech()` 从v1.0.50到v1.0.60几乎未改；手机/USB语音仍保留原方案。未发现任何 app `bindProcessToNetwork` 调用，云OCR本来使用独立 `cellular.openConnection`，**不能凭C110 Wi-Fi无声就认定网络路由是根因**。

## 本版最小修复
- 实验分支 `exp/c110-tts-regression-20261120`，独立 v1.0.61/versionCode70，**维持主包完整包名 `com.vobich.bluestar.c110plusquick159` 不变**，使之前真机有效的单独小包 `com.vobich.bluestar.boyuebridge` 不需要更改指向或重新绑定；旧版发布不覆盖。
- `C110FrameReader` 构造函数只初始化成员，**不在打开蓝星主页时提前创建TextToSpeech**。主页实际拍照 `C110QuickShotController.trigger()` 时才根据共享设置 `app_tts_engine_package` 初始化用户选定的引擎。原v1.0.58的引擎变更/语速同步、generation回调隔离、本次OCR文字待引擎就绪后朗读及对旧OCR请求安全清理保留。
- 在C110引擎 `OnInit=SUCCESS` 时**恢复v1.0.50～v1.0.57旧有的 `tts.setLanguage(Locale.SIMPLIFIED_CHINESE)`**，然后设置语速；如果语言不支持记录状态，但不取消本次OCR语音，不切换到未选中的默认引擎。保留用户选中的广荣引擎包。
- `C110QuickShotController.onResume` 不再无缘无故重建TTS；在主页**显式**进入统一设置、手机/USB预览、C110实时预览及阅读页面之前，调用 `beforeOpenAnotherScreen -> C110FrameReader.releaseSpeechForOtherScreen`，释放主页TTS，避免设置的 `MainActivity` / C110预览再次创建并行 TTS。旧引擎 `onInit` 由代际标记忽略。**只停止上一段语音，不修改摄像头Wi-Fi/OCR云请求或原生快门代码**。
- **唯一例外**：用户从快捷拍照立刻切走到另一页，之前仍在播放的声音会被停止。这是为了在新页面调用用户选定语音时不发生两个引擎同时运行；后台弹系统Wi-Fi授权不会自动释放本次TTS。
- 旧按键监听已经停止（保益通过已验收独立小启动器），单个「蓝星向导」主图标保留，C110 9011/9038机身直控、同一云端API Key/模型、手机UVC、原厂1001/150ms VLC和广荣等可选TTS功能不得改动。

## 验收与限制
1. 正常启动新版蓝星，进入设置→语音与朗读→广荣微软→试听：能够出声；再连接C110 Wi-Fi，不打开拍照，重复试听应正常。这两次由 `MainActivity` 独占TTS。
2. 使用主页快捷拍照或保益原来的双击音量上键启动器，C110拍一张高清照片、云OCR确认识别成功后由**同一选定的广荣微软音色**朗读；不多一次1001，不跳诊断页。
3. 返回设置再次试听，既不重启系统语音引擎，也不再有主页第二个TTS占用；手机/USB/C110预览继续正常，云模型共用。
4. 当前CI仅证明代码结构/Android编译/原OEM/资源未退化，**不能在GitHub Actions证明用户手机真实扬声器出声**。仍需vivo真机验收。
