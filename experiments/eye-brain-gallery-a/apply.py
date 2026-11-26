from pathlib import Path
import re, json, shutil, sys, hashlib
from xml.sax.saxutils import escape
up = Path(sys.argv[1])
here = Path(__file__).resolve().parent
app = up / "Android/src/app"
src = app / "src/main"
pkg = src / "java/com/google/ai/edge/gallery"
def replace(path, before, after):
    s = path.read_text()
    if s.count(before) != 1:
        raise RuntimeError(f"Expected exactly one anchor in {path}: {before[:100]!r}")
    path.write_text(s.replace(before, after, 1))
def block(path, start, end, content):
    s = path.read_text()
    assert s.count(start) == 1
    i = s.index(start); j = s.index(end, i)
    path.write_text(s[:i] + content + s[j:])
gradle = app / "build.gradle.kts"
replace(gradle, 'applicationId = "com.google.aiedge.gallery"', 'applicationId = "com.vobich.bluestar.eyebrain.gallerylite"')
replace(gradle, 'versionName = "1.0.23"', 'versionName = "0.2.2"')
replace(gradle, 'versionCode = 52', 'versionCode = 4')
replace(gradle, 'dependencies {\n', 'dependencies {\n  implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")\n')
# Native C++ runtime conflict must be resolved consistently at packaging; original LiteRT JNI unchanged.
replace(gradle, 'pickFirsts.add("**/liblitertlm_jni.so")', 'pickFirsts.add("**/liblitertlm_jni.so"); pickFirsts.add("**/libc++_shared.so")')
manifest = src / "AndroidManifest.xml"
replace(manifest, 'android:label="Edge Gallery"', 'android:label="眼脑协同轻量版"')
replace(manifest, 'android:allowBackup="true"', 'android:allowBackup="false"')
replace(manifest, '<uses-native-library android:name="libedgetpu_util.so" android:required="false" />', '<meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />\n        <uses-native-library android:name="libedgetpu_util.so" android:required="false" />') if manifest.read_text().count('<uses-native-library android:name="libedgetpu_util.so" android:required="false" />') == 1 else None
# Unique anchor regardless of repeated native library declarations.
if 'firebase_analytics_collection_deactivated' not in manifest.read_text():
    replace(manifest, 'tools:targetApi="31">', 'tools:targetApi="31">\n        <meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />')
replace(manifest, 'android:scheme="com.google.ai.edge.gallery"', 'android:scheme="com.vobich.bluestar.eyebrain.gallerylite"')
replace(manifest, 'android:taskAffinity="com.google.ai.edge.gallery.glasses"', 'android:taskAffinity="com.vobich.bluestar.eyebrain.gallerylite.glasses"')
# Original app-level analytics is disabled; no changes to LLM inference.
replace(pkg/"GalleryApplication.kt", 'firebaseAnalytics?.setAnalyticsCollectionEnabled(dataStoreRepository.readFirebaseAnalytics())', 'firebaseAnalytics?.setAnalyticsCollectionEnabled(false)')
# Use a curated snapshot of the exact upstream 1.0.23 model metadata, without any startup network lookup.
manager = pkg / "ui/modelmanager/ModelManagerViewModel.kt"
block(manager, '        // Load model allowlist json.', '        Log.d(TAG, "Allowlist: $modelAllowlist")', '''        val modelAllowlist = requireNotNull(
          context.assets.open("eye_local_allowlist.json").bufferedReader().use {
            Gson().fromJson(it, ModelAllowlist::class.java)
          }
        ) { "本地模型列表无法读取" }
        val loadedFromTestAllowlist = false

''')
replace(manager, '        startAutoDownloadModels()\n', '        // Lite lab: no automatic model downloads at startup.\n')
replace(manager, '        Log.e(TAG, "Failed to load model allowlist", e)\n', '        Log.e(TAG, "Failed to load model allowlist", e)\n        _uiState.update { it.copy(loadingModelAllowlist = false, loadingModelAllowlistError = "本地模型列表读取失败") }\n')
assets = src / "assets"
assets.mkdir(parents=True, exist_ok=True)
shutil.copyfile(here / "eye_local_allowlist.json", assets / "eye_local_allowlist.json")
# Disable automatic notification opt-in and FCM/boot/scheduled notification entry points.
replace(manifest, '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />', '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" tools:node="remove" />')
for permission in ['android.permission.RECEIVE_BOOT_COMPLETED', 'com.google.android.c2dm.permission.RECEIVE', 'com.google.android.providers.gsf.permission.READ_GSERVICES', 'android.permission.GET_ACCOUNTS']:
    replace(manifest, '<uses-permission android:name="' + permission + '" />', '<uses-permission android:name="' + permission + '" tools:node="remove" />')
m = manifest.read_text()
m, n = re.subn(r'<service\s+android:name="\.GalleryFcmMessagingService".*?</service>', '<service android:name=".GalleryFcmMessagingService" tools:node="remove" />', m, flags=re.S)
assert n == 1
m, n = re.subn(r'<receiver\s+android:name="com.google.ai.edge.gallery.notifications.BootReceiver".*?</receiver>', '<receiver android:name="com.google.ai.edge.gallery.notifications.BootReceiver" tools:node="remove" />', m, flags=re.S)
assert n == 1
m, n = re.subn(r'<receiver\s+android:name="com.google.ai.edge.gallery.notifications.NotificationReceiver"\s+android:exported="false"\s*/>', '<receiver android:name="com.google.ai.edge.gallery.notifications.NotificationReceiver" tools:node="remove" />', m)
assert n == 1
m = m.replace('<meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />', '<meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />\n        <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />\n        <meta-data android:name="firebase_data_collection_default_enabled" android:value="false" />')
assert 'firebase_messaging_auto_init_enabled' in m
manifest.write_text(m)
replace(pkg/"GalleryApplication.kt", '    notificationScheduleManager.initialize()\n', '    // Lite lab: no scheduled notification startup.\n')
# Replace only the home entry UI. Terms remain available on demand; do not persist acceptance on behalf of user.
home = pkg / "ui/home/HomeScreen.kt"
block(home, '  val uiState by modelManagerViewModel.uiState.collectAsState()\n', '@Composable\nprivate fun AppTitle', '''  val uiState by modelManagerViewModel.uiState.collectAsState()
  var showTerms by remember { mutableStateOf(false) }
  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("眼脑协同", style = MaterialTheme.typography.headlineMedium)
    Text("本地识字 · 离线理解")
    if (uiState.loadingModelAllowlist) {
      Text("正在准备本地模型列表…")
    } else if (uiState.loadingModelAllowlistError.isNotEmpty()) {
      Text(uiState.loadingModelAllowlistError)
      TextButton(onClick = { modelManagerViewModel.loadModelAllowlist() }) { Text("重新读取本地列表") }
    } else {
      val chatTask = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)
      if (chatTask != null) {
        TaskCard(task = chatTask, index = 0, animate = false,
          onClick = { navigateToTaskScreen(chatTask) },
          modifier = Modifier.fillMaxWidth(),
          description = "选图自动识别与回答")
      }
      TextButton(onClick = onModelsClicked) { Text("模型管理 / 导入或主动下载") }
    }
    TextButton(onClick = { showTerms = true }) { Text("原厂服务条款与说明") }
  }
  if (showTerms) {
    AppTosDialog(onTosAccepted = { showTerms = false }, viewingMode = true)
  }
}

''')
# Enable original optional UI metrics; no inference engine or sampling changes.
replace(pkg / "ui/common/chat/ChatPanel.kt", "val isLiveMetricsFlagEnabled = false", "val isLiveMetricsFlagEnabled = task.id == com.google.ai.edge.gallery.data.BuiltInTaskId.LLM_CHAT")
# Preserve official navigation, terms, model selection, and original chat send path.
input_file = pkg / "ui/common/chat/MessageInputText.kt"
replace(input_file, '              // First row: text field for input.', '''              if (task.id == com.google.ai.edge.gallery.data.BuiltInTaskId.LLM_CHAT) {
                androidx.compose.runtime.key(selectedModel.name) {
                  EyeOcrInput(
                    enabled = !inProgress && !isResettingSession && !modelInitializing &&
                      !modelPreparing && !isContextLimitReached &&
                      pickedImages.isEmpty() && pickedAudioClips.isEmpty() && pickedTextFiles.isEmpty(),
                    draft = curMessage,
                    onAutoSend = { recognized ->
                      onSendMessage(
                        createMessagesToSend(
                          pickedImages = emptyList(),
                          audioClips = emptyList(),
                          textFiles = emptyList(),
                          text = recognized,
                        )
                      )
                    }
                  )
                }
              }
              // First row: text field for input.''')
# Disable non-OCR attachment UI on the main task only; leave capability flags, model
# initialization, audio dictation and TTS, prompt template and sampling untouched.
module = pkg / "ui/llmchat/LlmChatTaskModule.kt"
replace(module, '      showImagePicker = true,\n      showAudioPicker = true,\n      showTextFilePicker = true,', '      showImagePicker = task.id != BuiltInTaskId.LLM_CHAT,\n      showAudioPicker = task.id != BuiltInTaskId.LLM_CHAT,\n      showTextFilePicker = task.id != BuiltInTaskId.LLM_CHAT,')
strings = src / "res/values/strings.xml"
s = strings.read_text()
translations = json.loads((here / "zh.json").read_text())
missing = []
for name, value in translations.items():
    pattern = r'(<string name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string>)'
    # Keep the default resource so Chinese is also the fallback on unsupported locales.
    s, n = re.subn(pattern, lambda m: m[1] + escape(value) + m[3], s, flags=re.S)
    if n != 1: missing.append(name)
assert not missing, missing
strings.write_text(s)
# Copy exact pinned OCR source files without patching.
ocr = src / "java/com/vobich/bluestar/openocrlab"
ocr.mkdir(parents=True, exist_ok=True)
for file in (here / "ocr").glob("*.kt"):
    shutil.copyfile(file, ocr / file.name)
shutil.copyfile(here / "EyeOcrInput.kt", input_file.parent / "EyeOcrInput.kt")
print("Overlay applied; original model/inference files were not rewritten.")
