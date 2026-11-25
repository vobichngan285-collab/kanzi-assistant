import pathlib,sys,json,re,urllib.request,hashlib,shutil,os
root=pathlib.Path(sys.argv[1]); lab=pathlib.Path(__file__).parent
app=root/"MiniCPM-V-demo-Android/app"; main=app/"src/main"
java=main/"java/com/example/minicpm_v_demo"
def change(path,old,new):
    s=path.read_text()
    assert s.count(old)==1, (path,old,s.count(old))
    path.write_text(s.replace(old,new))
# GitHub-hosted US runners use canonical Maven repositories; avoid Aliyun 502s
# for artifacts outside the Google repository.
settings=app.parent/"settings.gradle.kts"
settings_original=settings.read_text()
assert settings_original.count("maven.aliyun.com")==5
settings.write_text('''pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MiniCPM-V-demo"
include(":app")
''')
gradle=app/"build.gradle.kts"
package=os.environ.get("LAB_PACKAGE","com.vobich.bluestar.offlinelab")
assert package in ("com.vobich.bluestar.offlinelab","com.vobich.bluestar.offlinelab.speed")
change(gradle,'applicationId = "com.tianchi.minicpmv"','applicationId = "'+package+'"')
change(gradle,'versionCode = 17','versionCode = 2')
change(gradle,'versionName = "2.5"','versionName = "0.1.1"')
change(gradle,'implementation(libs.androidx.core.ktx)','implementation(libs.androidx.core.ktx)\n    implementation("androidx.documentfile:documentfile:1.0.1")')
manifest=main/"AndroidManifest.xml"
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
<uses-permission android:name="android.permission.INTERNET"/>
<application android:allowBackup="false" android:usesCleartextTraffic="false" android:label="蓝星离线AI实验室 速度实验" android:icon="@mipmap/ic_launcher" android:theme="@style/Theme.MiniCPMVdemo">
<activity android:name=".LabActivity" android:exported="true" android:screenOrientation="portrait" android:launchMode="singleTask" android:configChanges="orientation|screenSize|keyboardHidden">
<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
</activity>
<provider android:name="androidx.core.content.FileProvider" android:authorities="com.vobich.bluestar.offlinelab.labfiles" android:exported="false" android:grantUriPermissions="true"><meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/lab_paths"/></provider>
</application></manifest>'''.replace("com.vobich.bluestar.offlinelab.labfiles",package+".labfiles"))
(main/"res/xml").mkdir(parents=True,exist_ok=True)
(main/"res/xml/lab_paths.xml").write_text('<paths xmlns:android="http://schemas.android.com/apk/res/android"><cache-path name="lab_photos" path="lab_photos/"/></paths>')
kt=java/"LlamaEngine.kt"
change(kt,'    fun sendUserPrompt(','''    @Volatile var labPromptMs: Long = 0
        private set
    @Volatile var labGeneratedTokens: Int = 0
        private set
    suspend fun labRelease() = withContext(llamaDispatcher) {
        if (_state.value !is LlamaState.Initialized) {
            unload()
            _readyForSystemPrompt = false
            _mmprojLoaded = false
            _cancelGeneration = false
            _state.value = LlamaState.Initialized
        }
    }
    fun sendUserPrompt(''')
change(kt,'            processUserPrompt(message, predictLength).let { result ->','''            labGeneratedTokens = 0
            val labStart = android.os.SystemClock.elapsedRealtime()
            processUserPrompt(message, predictLength).let { result ->
                labPromptMs = android.os.SystemClock.elapsedRealtime() - labStart''')
change(kt,'                    return@flow','                    throw RuntimeException("Prompt/vision prefill failed: " + result)')
change(kt,'                generateNextToken()?.let { utf8token ->','                generateNextToken()?.let { utf8token ->\n                    labGeneratedTokens++')
cpp=main/"cpp/llama_jni.cpp"
change(cpp,'LlamaEngine_fullReset(JNIEnv *, jobject)', 'LlamaEngine_fullReset(JNIEnv *env, jobject)')
change(cpp,'LOGe("%s: Failed to reinitialize context!", __func__);\n        return;', 'LOGe("%s: Failed to reinitialize context!", __func__);\n        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Native context reset failed");\n        return;')
change(cpp,'    llama_batch_free(g_batch);\n    llama_free(g_context);','    llama_batch_free(g_batch);\n    g_batch = {};\n    llama_free(g_context);')
change(cpp,'LOGe("%s: llama_decode() failed for generated token", __func__);\n        return nullptr;', 'LOGe("%s: llama_decode() failed for generated token", __func__);\n        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Native decode failed");\n        return nullptr;')
# Silence native bridge log calls, which can contain prompts or generated text.
s=cpp.read_text()
# LOG macros are supplied by included header. Redefine after include block.
i=s.index('constexpr int')
s=s[:i]+'''#undef LOGi
#undef LOGd
#undef LOGv
#undef LOGw
#define LOGi(...) ((void)0)
#define LOGd(...) ((void)0)
#define LOGv(...) ((void)0)
#define LOGw(...) ((void)0)
'''+s[i:]
cpp.write_text(s)
# Model catalog fixed to an exact revision and cryptographic content checks.
hf="https://huggingface.co"; repo="tc-mb/MiniCPM-V-Apps-gguf"
def get(url):
    with urllib.request.urlopen(url,timeout=60) as f:return f.read()
rev="2a3a3ddfbfc3d8e98c1070680b2fa98df88c3452"
meta=json.loads(get(f"{hf}/api/models/{repo}/revision/{rev}"))
assert meta["sha"]==rev
models=[]
for ident,title,prefix,mainfile in [
 ("minicpm-v-4_7-1b","MiniCPM-V 4.7 1B","MiniCPM-V-4.7-1B","MiniCPM-V-4_7-1B-Q4_K_M.gguf"),
 ("minicpm-v-4_7-35b","MiniCPM-V 4.7 35B","MiniCPM-V-4.7-35B-A3B","MiniCPM-V-4_7-35B-A3B-mix_q3gud_im-les.gguf")]:
    tree=json.loads(get(f"{hf}/api/models/{repo}/tree/{rev}/{prefix}?recursive=true&expand=false"))
    entries={x["path"].removeprefix(prefix+"/"):x for x in tree if x["type"]=="file"}
    names=[mainfile,"mmproj-model-f16.gguf"]
    if ident.endswith("35b"):
        raw=get(f"{hf}/{repo}/resolve/{rev}/{prefix}/experts/manifest.json")
        sizes={}
        for e in json.loads(raw)["entries"]:
            n=e["repacked_file"];assert re.fullmatch(r"layer_[0-9]{3}\.bin",n)
            z=int(e["expert_stride"])*int(e["shape"][2])
            assert z>0;sizes[n]=max(z,sizes.get(n,0))
        assert len(sizes)==40
        names+=["experts/manifest.json"]+["experts/"+n for n in sorted(sizes)]
    files=[]
    for name in names:
        item=entries[name];size=item["size"];assert size>0
        if name=="experts/manifest.json":
            assert len(raw)==size;sha=hashlib.sha256(raw).hexdigest()
        else:
            sha=item["lfs"]["oid"];assert item["lfs"]["size"]==size
            if name.startswith("experts/"):assert size==sizes[name.split("/")[1]]
        assert re.fullmatch("[a-f0-9]{64}",sha)
        files.append(dict(name=name,size=size,sha256=sha,url=f"{hf}/{repo}/resolve/{rev}/{prefix}/{name}"))
    models.append(dict(id=ident,title=title,files=files))
assets=main/"assets";assets.mkdir(exist_ok=True)
(assets/"lab_models.json").write_text(json.dumps(dict(revision=rev,models=models),ensure_ascii=False))
shutil.copy(root/"LICENSE",assets/"UPSTREAM_LICENSE.txt")
shutil.copy(root/"llama.cpp-omni/LICENSE",assets/"ENGINE_LICENSE.txt")
for n in ("LabActivity.kt","LabMetrics.kt"):shutil.copy(lab/n,java/n)
test=app/"src/test/java/com/example/minicpm_v_demo";test.mkdir(parents=True,exist_ok=True)
shutil.copy(lab/"LabMetricsTest.kt",test/"LabMetricsTest.kt")
print("Pinned weights",rev,[(m["id"],len(m["files"]),sum(f["size"] for f in m["files"])) for m in models])
