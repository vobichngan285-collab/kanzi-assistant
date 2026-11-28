#!/usr/bin/env python3
"""Build-time adapter for isolated UVC live OCR; frozen model sources retained."""
from pathlib import Path
import subprocess
root=Path("openocr-lab")
uvc=root/"third_party/AndroidUSBCamera/libuvc"
src=root/"app/src/main/java/com/vobich/bluestar/openocrlab"
def patch(p,a,b,count=1):
    s=p.read_text()
    assert s.count(a)==count,(str(p),a,s.count(a))
    p.write_text(s.replace(a,b))
def write(p,s):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(s)
subprocess.run(["python3","scripts/patch_usbmonitor.py",str(uvc/"src/main/java/com/jiangdg/usb/USBMonitor.java")],check=True)
patch(uvc/"build.gradle","id 'com.android.library'","id 'com.android.library' version '8.13.2'")
patch(uvc/"build.gradle","abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'","abiFilters 'arm64-v8a'")
patch(uvc/"src/main/java/com/jiangdg/uvc/IFrameCallback.java",
      "public void onFrame(ByteBuffer frame);",
      "public void onFrame(ByteBuffer frame);\n\tdefault void onFrame(ByteBuffer frame, int width, int height) { onFrame(frame); }")
p=uvc/"src/main/jni/UVCCamera/UVCPreview.cpp"
patch(p,'"(Ljava/nio/ByteBuffer;)V"','"(Ljava/nio/ByteBuffer;II)V"')
patch(p,"env->CallVoidMethod(mFrameCallbackObj, iframecallback_fields.onFrame, buf);",
      "env->CallVoidMethod(mFrameCallbackObj, iframecallback_fields.onFrame, buf, (jint)callback_frame->width, (jint)callback_frame->height);")
patch(p,"if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode)) {",
      "if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode) || (requestMinFps != min_fps) || (requestMaxFps != max_fps)) {")
# Upstream drawing already checks mPreviewWindow == NULL and bypasses RGBX/rendering.
# Permit only the existing callback path to start without a display.
patch(p,"if (LIKELY(mPreviewWindow)) {\n\t\t\t\tresult = pthread_create(&preview_thread, NULL, preview_thread_func, (void *)this);",
      "if (mPreviewWindow || mFrameCallbackObj) {\n\t\t\t\tresult = pthread_create(&preview_thread, NULL, preview_thread_func, (void *)this);")
with (root/"settings.gradle.kts").open("a") as f:
    f.write('\ninclude(":libuvc")\nproject(":libuvc").projectDir=file("third_party/AndroidUSBCamera/libuvc")\n')
write(root/"build.gradle",'''ext {
    versionCompiler = 36
    minSdkVersion = 28
    javaSourceCompatibility = JavaVersion.VERSION_1_8
    javaTargetCompatibility = JavaVersion.VERSION_1_8
    androidXVersion = '1.7.1'
    ndkVersion = '28.1.13356709'
    versionNameString = '3.6.0'
}
''')
p=root/"app/build.gradle.kts"
patch(p,'applicationId = "com.vobich.bluestar.openocrlab.fielddb"','applicationId = "com.vobich.bluestar.uvcliveocr"')
patch(p,'versionCode = 17','versionCode = 1')
patch(p,'versionName = "0.4.8"','versionName = "0.1.0"')
patch(p,'dependencies {\n','dependencies {\n implementation(project(":libuvc"))\n implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.20"))\n')
write(root/"app/src/main/AndroidManifest.xml",'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-feature android:name="android.hardware.usb.host" android:required="true"/>
<uses-feature android:name="android.hardware.camera" android:required="false"/>
<queries><intent><action android:name="android.intent.action.TTS_SERVICE"/></intent></queries>
<application android:label="蓝星 C110 实时离线OCR" android:allowBackup="false" android:theme="@android:style/Theme.Material.Light.NoActionBar">
<activity android:name=".LiveActivity" android:exported="true" android:configChanges="orientation|screenSize|keyboardHidden"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
<service android:name=".LiveService" android:exported="false" android:foregroundServiceType="connectedDevice"/>
</application></manifest>
''')
# Only optional run options are added to the frozen OCR engine in the generated checkout.
# No threshold, tensor shape, weights, geometry, or confidence rules changed.
p=src/"DbLocator.kt"
patch(p,"official:Boolean=true):List<TextLocator.Box>","official:Boolean=true,runOptions:OrtSession.RunOptions?=null):List<TextLocator.Box>")
patch(p,"session.run(mapOf(session.inputNames.single() to input)).use{res->",
      "(if(runOptions==null)session.run(mapOf(session.inputNames.single() to input)) else session.run(mapOf(session.inputNames.single() to input),runOptions)).use{res->")
p=src/"Reader.kt"
patch(p,"forceHorizontal:Boolean=false):Answer","forceHorizontal:Boolean=false,runOptions:OrtSession.RunOptions?=null):Answer")
patch(p,"boxes:List<TextLocator.Box>):List<Answer>","boxes:List<TextLocator.Box>,runOptions:OrtSession.RunOptions?=null):List<Answer>")
patch(p,"session.run(mapOf(session.inputNames.single() to input))",
      "(if(runOptions==null)session.run(mapOf(session.inputNames.single() to input)) else session.run(mapOf(session.inputNames.single() to input),runOptions))",2)
print("Generated headless UVC + cancellable frozen OCR adapter")
