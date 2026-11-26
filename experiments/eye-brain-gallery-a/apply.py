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
replace(gradle, 'applicationId = "com.google.aiedge.gallery"', 'applicationId = "com.vobich.bluestar.eyebrain.galleryspeed"')
replace(gradle, 'versionName = "1.0.23"', 'versionName = "0.2.1"')
replace(gradle, 'versionCode = 52', 'versionCode = 3')
replace(gradle, 'dependencies {\n', 'dependencies {\n  implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")\n')
# Native C++ runtime conflict must be resolved consistently at packaging; original LiteRT JNI unchanged.
replace(gradle, 'pickFirsts.add("**/liblitertlm_jni.so")', 'pickFirsts.add("**/liblitertlm_jni.so"); pickFirsts.add("**/libc++_shared.so")')
manifest = src / "AndroidManifest.xml"
replace(manifest, 'android:label="Edge Gallery"', 'android:label="眼脑协同实验室"')
replace(manifest, 'android:allowBackup="true"', 'android:allowBackup="false"')
replace(manifest, '<uses-native-library android:name="libedgetpu_util.so" android:required="false" />', '<meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />\n        <uses-native-library android:name="libedgetpu_util.so" android:required="false" />') if manifest.read_text().count('<uses-native-library android:name="libedgetpu_util.so" android:required="false" />') == 1 else None
# Unique anchor regardless of repeated native library declarations.
if 'firebase_analytics_collection_deactivated' not in manifest.read_text():
    replace(manifest, 'tools:targetApi="31">', 'tools:targetApi="31">\n        <meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />')
replace(manifest, 'android:scheme="com.google.ai.edge.gallery"', 'android:scheme="com.vobich.bluestar.eyebrain.galleryspeed"')
replace(manifest, 'android:taskAffinity="com.google.ai.edge.gallery.glasses"', 'android:taskAffinity="com.vobich.bluestar.eyebrain.galleryspeed.glasses"')
# Original app-level analytics is disabled; no changes to LLM inference.
replace(pkg/"GalleryApplication.kt", 'firebaseAnalytics?.setAnalyticsCollectionEnabled(dataStoreRepository.readFirebaseAnalytics())', 'firebaseAnalytics?.setAnalyticsCollectionEnabled(false)')
home = pkg / "ui/home/HomeScreen.kt"
block(home, '                var selectedCategoryIndex by remember { mutableIntStateOf(0) }', '                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 10.dp))', '''                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text("眼脑协同实验室", style = MaterialTheme.typography.headlineMedium)
                  Text("OCR v0.1.3 · 自动提速实验版")
                  val chatTask = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)
                  if (chatTask != null) {
                    TaskCard(task = chatTask, index = 0, animate = false,
                      onClick = { navigateToTaskScreen(chatTask) },
                      modifier = Modifier.fillMaxWidth(),
                      description = "选图后自动识别与回答，支持多种任务")
                  }
                  TextButton(onClick = onModelsClicked) { Text("模型管理 / 下载与导入") }
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
