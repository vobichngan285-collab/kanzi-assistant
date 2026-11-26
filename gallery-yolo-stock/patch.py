"""Patch only Gallery packaging, explicit album crop path and analytics opt-out."""
from pathlib import Path
import hashlib, json, shutil, zipfile
root=Path("upstream/Android/src")
app=root/"app"
pkg=app/"src/main/java/com/google/ai/edge/gallery"
chat=pkg/"ui/common/chat"
helper=pkg/"ui/llmchat/LlmChatModelHelper.kt"
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
engine_before=sha(helper)
p=app/"build.gradle.kts"
s=p.read_text()
def once(s,old,new):
    assert s.count(old)==1,old
    return s.replace(old,new,1)
s=once(s,'applicationId = "com.google.aiedge.gallery"','applicationId = "com.vobich.bluestar.gallery.yolo"')
s=once(s,'versionName = "1.0.23"','versionName = "1.0.23-bluestar.1"')
s=once(s,'versionCode = 52','versionCode = 1\n    ndk { abiFilters += "arm64-v8a" }')
s=once(s,'implementation(libs.litertlm)','implementation(libs.litertlm)\n  implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")')
s=once(s,'pickFirsts.add("**/liblitertlm_jni.so")','pickFirsts.add("**/liblitertlm_jni.so"); pickFirsts.add("**/libc++_shared.so")')
p.write_text(s)
p=app/"src/main/AndroidManifest.xml";s=p.read_text()
s=once(s,'android:label="Edge Gallery"','android:label="蓝星 Gallery YOLO"')
s=once(s,'android:allowBackup="true"','android:allowBackup="false"')
s=once(s,'android:theme="@style/Theme.Gallery"\n        tools:targetApi="31">','android:theme="@style/Theme.Gallery"\n        tools:targetApi="31">\n        <meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />\n        <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />')
# Preserve Gallery networking/model manager. Disable Firebase analytics and FCM auto initialization.
s=once(s,'<data android:scheme="com.google.ai.edge.gallery" />','<data android:scheme="com.vobich.bluestar.gallery.yolo" />')
p.write_text(s)
p=chat/"MessageInputText.kt";s=p.read_text()
start=s.index("  // Registers a photo picker activity launcher")
end=s.index("  val pickWav =",start)
block=s[start:end]
assert block.count("val pickMedia =")==1 and block.count("handleImagesSelected(")==1
new=block.replace("val pickMedia =","val pickBlueStarMedia =").replace("onImagesSelected = { bitmaps -> updatePickedImages(bitmaps) },","onImagesSelected = { bitmaps -> updatePickedImages(bitmaps) },\n            blueStarCrop = true,")
s=s[:end]+new+s[end:]
start=s.index("                        // Pick an image from album.")
end=s.index("\n                      }\n\n                      // Audio related menu items.",start)
block=s[start:end]
assert block.count("pickMedia.launch(")==1
new=block.replace("Text(stringResource(R.string.pick_from_album))",'Text("YOLO 高清文字裁剪")').replace("pickMedia.launch(","pickBlueStarMedia.launch(")
s=s[:end]+"\n"+new+s[end:]
s=once(s,"  onImagesSelected: (List<Bitmap>) -> Unit,\n) {","  onImagesSelected: (List<Bitmap>) -> Unit,\n  blueStarCrop: Boolean = false,\n) {")
s=once(s,"  for (uri in uris) {\n    val bitmap: Bitmap? =","  for (uri in uris) {\n    if (blueStarCrop) {\n      val cropped = BlueStarTextCrop.crop(context, uri)\n      if (cropped != null) { images.add(cropped); continue }\n    }\n    val bitmap: Bitmap? =")
p.write_text(s)
for name in ("TextLocator.kt","BlueStarTextCrop.kt"):
    shutil.copyfile(Path("gallery-yolo-stock")/name,chat/name)
assets=app/"src/main/assets";assets.mkdir(parents=True,exist_ok=True)
report=json.loads(Path("detector-report.json").read_text())
with zipfile.ZipFile("previous.apk") as z:
    data=z.read("assets/yolo26n-text.onnx")
    assert hashlib.sha256(data).hexdigest()==report["onnx_sha256"]
    (assets/"yolo26n-text.onnx").write_bytes(data)
assert sha(helper)==engine_before
Path("gallery-patch-report.json").write_text(json.dumps({"gallery_commit":"9cb31cf47c21b0ed76901ff589a52b509803edfa","llm_helper_sha256":engine_before,"detector_sha256":report["onnx_sha256"],"network_permission":"original Gallery retained for model management; airplane mode for offline test","changes":["isolated package and label","ARM64","explicit opt-in album crop","analytics and FCM auto-init disabled"]},indent=2))
print("Stock inference helper unchanged:",engine_before)
