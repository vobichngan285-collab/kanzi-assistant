"""v0.1.3: preserve CPU baseline and register Vulkan only for requested vision.
The previous preparation is the fixed v0.1.1 base plus measured GPU additions.
No prior release files or weight catalog are changed.
"""
import os, pathlib, subprocess, sys
root=pathlib.Path(sys.argv[1]).resolve()
repo=pathlib.Path(__file__).resolve().parent.parent
subprocess.run([sys.executable,str(repo/"gpu-ai-lab/prepare.py"),str(root)],check=True)
app=root/"MiniCPM-V-demo-Android/app"
main=app/"src/main"
java=main/"java/com/example/minicpm_v_demo"
engine=root/"llama.cpp-omni"
def change(path,old,new):
    s=path.read_text()
    assert s.count(old)==1,(str(path),old,s.count(old))
    path.write_text(s.replace(old,new))
def replace_all(path,old,new):
    s=path.read_text()
    assert old in s,(str(path),old)
    path.write_text(s.replace(old,new))

# Match the installed certificate when possible; otherwise preserve both older APKs.
package=os.environ["LAB_PACKAGE"]
assert package in ("com.vobich.bluestar.offlinelab.gpu","com.vobich.bluestar.offlinelab.gpu.recovery")
gradle=app/"build.gradle.kts"
if package.endswith(".recovery"):
    change(gradle,'applicationId = "com.vobich.bluestar.offlinelab.gpu"','applicationId = "'+package+'"')
    change(main/"AndroidManifest.xml","com.vobich.bluestar.offlinelab.gpu.labfiles",package+".labfiles")
change(gradle,'versionCode = 1','versionCode = 2')
change(gradle,'versionName = "0.1.2"','versionName = "0.1.3"')
change(main/"AndroidManifest.xml","蓝星离线AI实验室 GPU实验","蓝星离线AI实验室 GPU修复")

# Both the built-in registry and generic backend scan must skip Vulkan at startup.
# Vulkan is a linked shared library, not a dynamically unloaded driver. Remove
# only its registry entry after all model/context/projector objects are freed.
reg=engine/"ggml/src/ggml-backend-reg.cpp"
header=engine/"ggml/include/ggml-backend.h"
change(reg,'''    // Add runtime disable check
    if (getenv("GGML_DISABLE_VULKAN") == nullptr) {
        register_backend(ggml_backend_vk_reg());
    } else {
        GGML_LOG_DEBUG("Vulkan backend disabled by GGML_DISABLE_VULKAN environment variable\n");
    }'''.replace("\n\");","\u005cn\");"),'''    // Lab: no Vulkan device probing/registration during CPU startup.
    // Only ggml_lab_enable_vulkan() may register the static Vulkan backend.''')
change(reg,'    ggml_backend_load_best("vulkan", silent, dir_path);','    // Lab: skip dynamic Vulkan discovery; enable explicitly for vision only.')
change(header,'    GGML_API void ggml_backend_register(ggml_backend_reg_t reg);','''    GGML_API void ggml_backend_register(ggml_backend_reg_t reg);
    GGML_API int ggml_lab_enable_vulkan(void);
    GGML_API void ggml_lab_disable_vulkan(void);''')
change(reg,'''void ggml_backend_register(ggml_backend_reg_t reg) {
    get_reg().register_backend(reg);
}''','''void ggml_backend_register(ggml_backend_reg_t reg) {
    get_reg().register_backend(reg);
}

static ggml_backend_reg_t lab_vk_reg = nullptr;
void ggml_lab_disable_vulkan(void) {
    if (lab_vk_reg) {
        // Linked backend has no dl_handle; do not dlclose the Vulkan library.
        get_reg().unload_backend(lab_vk_reg, true);
        lab_vk_reg = nullptr;
    }
}
int ggml_lab_enable_vulkan(void) {
#ifdef GGML_USE_VULKAN
    try {
        if (!lab_vk_reg) {
            lab_vk_reg = ggml_backend_vk_reg();
            if (!lab_vk_reg) return 1;
            get_reg().register_backend(lab_vk_reg);
        }
        if (!ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_GPU) &&
            !ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_IGPU)) {
            ggml_lab_disable_vulkan();
            return 1;
        }
        return 0;
    } catch (...) {
        ggml_lab_disable_vulkan();
        return 2;
    }
#else
    return 3;
#endif
}''')
cpp=main/"cpp/llama_jni.cpp"
# CPU language-model load occurs before GPU registration, using v0.1.1 defaults.
change(cpp,'''    model_params.n_gpu_layers = 0;
    static ggml_backend_dev_t cpu_only_devices[] = {nullptr};
    model_params.devices = cpu_only_devices;
    model_params.split_mode = LLAMA_SPLIT_MODE_NONE;''','''    model_params.n_gpu_layers = 0;
    // Keep original v0.1.1 model-device selection, with no Vulkan registered.
    if (ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_GPU) ||
        ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_IGPU)) {
        return 3; // A stale GPU registry would contaminate the CPU language model.
    }''')
change(cpp,'''    ctx_params.offload_kqv = false;
    ctx_params.op_offload = false;''','''    if (g_lab_gpu) {
        // Only the GPU-vision run requires explicit LLM offload restrictions.
        ctx_params.offload_kqv = false;
        ctx_params.op_offload = false;
    }''')
change(cpp,'''    mtmd_context_params mparams = mtmd_context_params_default();
    mparams.use_gpu   = g_lab_gpu;''','''    if (g_lab_gpu) {
        const int vk = ggml_lab_enable_vulkan();
        if (vk != 0) {
            env->ReleaseStringUTFChars(jmmproj_path, mmproj_path);
            return 10 + vk; // 11: no GPU, 12: registration exception, 13: not built.
        }
    }
    mtmd_context_params mparams = mtmd_context_params_default();
    mparams.use_gpu   = g_lab_gpu;''')
change(cpp,'''    g_ctx_vision = mtmd_init_from_file(mmproj_path, g_model, mparams);
    env->ReleaseStringUTFChars(jmmproj_path, mmproj_path);''','''    try {
        g_ctx_vision = mtmd_init_from_file(mmproj_path, g_model, mparams);
    } catch (...) {
        env->ReleaseStringUTFChars(jmmproj_path, mmproj_path);
        return 14; // vision initialization exception
    }
    env->ReleaseStringUTFChars(jmmproj_path, mmproj_path);''')
# In unload, all vision / LLM objects are released BEFORE registry removal.
s=cpp.read_text()
start=s.index("Java_com_example_minicpm_1v_1demo_LlamaEngine_unload(")
end=s.index("Java_com_example_minicpm_1v_1demo_LlamaEngine_shutdown(",start)
part=s[start:end]
assert part.count("    g_model = nullptr;\n}")==1
part=part.replace("    g_model = nullptr;\n}","    g_model = nullptr;\n    ggml_lab_disable_vulkan();\n    g_lab_gpu = false;\n}")
cpp.write_text(s[:start]+part+s[end:])

# Exact stage and numeric native return codes; do not export exception messages
# containing local paths, prompts or recognized text.
kt=java/"LlamaEngine.kt"
change(kt,'    private external fun labConfigureNative(gpu:Boolean, threads:Int)','''    @Volatile var labStage = "idle"
        private set
    @Volatile var labCode = -1
        private set
    fun labMark(stage:String) {
        labStage=stage
        labCode=-1
        context.getSharedPreferences("lab_stage",Context.MODE_PRIVATE).edit()
            .putString("stage",stage).putInt("code",-1).commit()
    }
    private fun labReturned(code:Int) {
        labCode=code
        context.getSharedPreferences("lab_stage",Context.MODE_PRIVATE).edit()
            .putInt("code",code).commit()
    }
    private external fun labConfigureNative(gpu:Boolean, threads:Int)''')
change(kt,'        labConfigureNative(gpu,threads)','        labMark("configure")\n        labConfigureNative(gpu,threads)')
change(kt,'                load(pathToModel).let {\n                    if (it != 0)','                labMark("llm_load")\n                load(pathToModel).let {\n                    labReturned(it)\n                    if (it != 0)')
change(kt,'                    loadMmproj(pathToMmproj, sliceCap).let {','                    labMark("vision_load")\n                    loadMmproj(pathToMmproj, sliceCap).let {\n                        labReturned(it)')
change(kt,'                            Log.w(TAG, "Failed to load mmproj (code: $it), continuing without vision support")','                            throw RuntimeException("Failed to load mmproj (code: $it)")')
change(kt,'                prepare().let {\n                    if (it != 0)','                labMark("context_prepare")\n                prepare().let {\n                    labReturned(it)\n                    if (it != 0)')
change(kt,'                _state.value = LlamaState.ModelReady\n            } catch (e: Exception) {\n                Log.e(TAG, (e.message ?: "Error loading model")','                labMark("model_ready")\n                _state.value = LlamaState.ModelReady\n            } catch (e: Exception) {\n                Log.e(TAG, (e.message ?: "Error loading model")')
activity=java/"LabActivity.kt"
replace_all(activity,"v0.1.2","v0.1.3")
change(activity,'                engine.labConfigure(cfg.gpu,cfg.threads)\n                val loadStart=now()\n                try{','                val loadStart=now()\n                try{\n                    engine.labConfigure(cfg.gpu,cfg.threads)')
change(activity,'                    val actual=engine.labBackendInfo()','                    engine.labMark("backend_verify")\n                    val actual=engine.labBackendInfo()')
change(activity,'                    for((run,trial) in trials.withIndex()){','                    engine.labMark("inference")\n                    for((run,trial) in trials.withIndex()){')
change(activity,'phase=failed error="+e.javaClass.simpleName','phase=failed stage="+engine.labStage+" native_code="+engine.labCode+" error="+e.javaClass.simpleName')
change(activity,'        getSharedPreferences("lab_log",MODE_PRIVATE).edit().putString("rows",rows.joinToString("\\n")).apply()','        getSharedPreferences("lab_log",MODE_PRIVATE).edit().putString("rows",rows.joinToString("\\n")).commit()')
change(activity,'        log.text=rows.joinToString("\\n")\n    }\n    override fun onSaveInstanceState','''        log.text=rows.joinToString("\\n")
        val lastStage=getSharedPreferences("lab_stage",MODE_PRIVATE)
        val stage=lastStage.getString("stage","idle")
        if(stage !in listOf("idle","complete")) append("previous_native_stage="+stage+" native_code="+lastStage.getInt("code",-1))
    }
    override fun onSaveInstanceState''')
# Keep last native stage on crash/failure; mark complete only after successful batch.
change(activity,'                }catch(e:CancellationException){throw e}','                    engine.labMark("complete")\n                }catch(e:CancellationException){throw e}')
# Stage writes happen before each native loading phase, not inside token timing.
assert "model_params.devices = cpu_only_devices" not in cpp.read_text()
assert 'ggml_backend_load_best("vulkan", silent, dir_path);' not in reg.read_text()
assert "ggml_lab_disable_vulkan();" in cpp.read_text()
print("v0.1.3 isolation and stage diagnostics applied")
