"""Build independent GPU lab on verified v0.1.1 sources. Fail on patch drift."""
import os, pathlib, subprocess, sys
root=pathlib.Path(sys.argv[1]).resolve()
repo=pathlib.Path(__file__).resolve().parent.parent
env=dict(os.environ,LAB_PACKAGE="com.vobich.bluestar.offlinelab.speed")
subprocess.run([sys.executable,str(repo/"offline-ai-lab/prepare.py"),str(root)],env=env,check=True)
app=root/"MiniCPM-V-demo-Android/app"
main=app/"src/main"
java=main/"java/com/example/minicpm_v_demo"
engine=root/"llama.cpp-omni"
def change(path,old,new):
    s=path.read_text()
    assert s.count(old)==1,(str(path),old,s.count(old))
    path.write_text(s.replace(old,new))
gradle=app/"build.gradle.kts"
change(gradle,'applicationId = "com.vobich.bluestar.offlinelab.speed"','applicationId = "com.vobich.bluestar.offlinelab.gpu"')
change(gradle,'versionCode = 2','versionCode = 1')
change(gradle,'versionName = "0.1.1"','versionName = "0.1.2"')
change(gradle,'minSdk = 24\n        targetSdk','minSdk = 28\n        targetSdk')
change(gradle,'                arguments += "-DGGML_NATIVE=OFF"','''                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_VULKAN=ON"
                arguments += "-DGGML_BACKEND_DL=OFF"
                arguments += "-DCMAKE_CXX_FLAGS=" + System.getenv("LAB_VK_CXX_FLAGS")
                arguments += "-DVulkan_INCLUDE_DIR=" + System.getenv("LAB_VK_INCLUDE")
                arguments += "-DVulkan_LIBRARY=" + System.getenv("LAB_VK_LIB")
                arguments += "-DVulkan_GLSLC_EXECUTABLE=" + System.getenv("LAB_GLSLC")
                arguments += "-DSPIRV-Headers_DIR=" + System.getenv("LAB_SPIRV_DIR")''')
manifest=main/"AndroidManifest.xml"
change(manifest,"com.vobich.bluestar.offlinelab.speed.labfiles","com.vobich.bluestar.offlinelab.gpu.labfiles")
change(manifest,"蓝星离线AI实验室 速度实验","蓝星离线AI实验室 GPU实验")
# Expose the actual selected primary visual backend and projector parameters.
cliph=engine/"tools/mtmd/clip.h"
clipcpp=engine/"tools/mtmd/clip.cpp"
mtmdh=engine/"tools/mtmd/mtmd.h"
mtmdcpp=engine/"tools/mtmd/mtmd.cpp"
change(cliph,'void clip_free(struct clip_ctx * ctx);','''void clip_free(struct clip_ctx * ctx);
const char * clip_lab_backend_name(const struct clip_ctx * ctx);
int clip_lab_backend_gpu(const struct clip_ctx * ctx);
int clip_lab_merge(const struct clip_ctx * ctx);''')
change(clipcpp,'void clip_set_image_max_slice_nums(struct clip_ctx * ctx, int n) {','''const char * clip_lab_backend_name(const struct clip_ctx * ctx) {
    return ctx && ctx->backend ? ggml_backend_name(ctx->backend) : "none";
}
int clip_lab_backend_gpu(const struct clip_ctx * ctx) {
    if (!ctx || !ctx->backend) return 0;
    auto dev = ggml_backend_get_device(ctx->backend);
    if (!dev) return 0;
    auto type = ggml_backend_dev_type(dev);
    return type == GGML_BACKEND_DEVICE_TYPE_GPU || type == GGML_BACKEND_DEVICE_TYPE_IGPU;
}
int clip_lab_merge(const struct clip_ctx * ctx) {
    return ctx ? ctx->model.hparams.n_merge : 0;
}
void clip_set_image_max_slice_nums(struct clip_ctx * ctx, int n) {''')
change(mtmdh,'MTMD_API bool mtmd_support_vision(const mtmd_context * ctx);','''MTMD_API bool mtmd_support_vision(const mtmd_context * ctx);
MTMD_API const char * mtmd_lab_backend_name(const mtmd_context * ctx);
MTMD_API int mtmd_lab_backend_gpu(const mtmd_context * ctx);
MTMD_API int mtmd_lab_merge(const mtmd_context * ctx);
MTMD_API int mtmd_lab_image_size(const mtmd_context * ctx);''')
change(mtmdcpp,'bool mtmd_support_vision(const mtmd_context * ctx) {','''const char * mtmd_lab_backend_name(const mtmd_context * ctx) {
    return ctx && ctx->ctx_v ? clip_lab_backend_name(ctx->ctx_v) : "none";
}
int mtmd_lab_backend_gpu(const mtmd_context * ctx) {
    return ctx && ctx->ctx_v ? clip_lab_backend_gpu(ctx->ctx_v) : 0;
}
int mtmd_lab_merge(const mtmd_context * ctx) {
    return ctx && ctx->ctx_v ? clip_lab_merge(ctx->ctx_v) : 0;
}
int mtmd_lab_image_size(const mtmd_context * ctx) {
    return ctx && ctx->ctx_v ? clip_get_image_size(ctx->ctx_v) : 0;
}
bool mtmd_support_vision(const mtmd_context * ctx) {''')
cpp=main/"cpp/llama_jni.cpp"
change(cpp,'#include <cstdlib>','#include <cstdlib>\n#include <chrono>\n#include <cctype>\n#include <algorithm>')
change(cpp,'constexpr int   N_THREADS               = 4;','''static int N_THREADS = 4;
static bool g_lab_gpu = false;
struct lab_times {
    int64_t tokenize_us=0, vision_us=0, image_decode_us=0, text_decode_us=0;
    int64_t image_chunks=0, image_tokens=0, text_tokens=0;
};
static lab_times g_lab_times;
using lab_clock = std::chrono::steady_clock;
static int64_t lab_us(lab_clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::microseconds>(lab_clock::now()-start).count();
}
extern "C" JNIEXPORT void JNICALL
Java_com_example_minicpm_1v_1demo_LlamaEngine_labConfigureNative(JNIEnv *env, jobject, jboolean gpu, jint threads) {
    if (threads != 4 && threads != 6 && threads != 8) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid thread count");
        return;
    }
    N_THREADS = threads;
    g_lab_gpu = gpu == JNI_TRUE;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_minicpm_1v_1demo_LlamaEngine_labTimingNative(JNIEnv *env, jobject) {
    const auto &t = g_lab_times;
    std::ostringstream s;
    s << "tokenize_us=" << t.tokenize_us << " vision_encode_us=" << t.vision_us
      << " image_decode_us=" << t.image_decode_us << " text_decode_us=" << t.text_decode_us
      << " image_chunks=" << t.image_chunks << " visual_tokens=" << t.image_tokens
      << " prompt_text_tokens=" << t.text_tokens;
    return env->NewStringUTF(s.str().c_str());
}''')
change(cpp,'static mtmd_context                     * g_ctx_vision;','''static mtmd_context                     * g_ctx_vision;
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_minicpm_1v_1demo_LlamaEngine_labBackendNative(JNIEnv *env, jobject) {
    std::string name = mtmd_lab_backend_name(g_ctx_vision);
    for (char &c : name) if (!std::isalnum(static_cast<unsigned char>(c)) && c!='-' && c!='_' && c!='.') c='_';
    std::ostringstream s;
    s << "backend=" << name << " backend_gpu=" << mtmd_lab_backend_gpu(g_ctx_vision)
      << " merge=" << mtmd_lab_merge(g_ctx_vision) << " internal_image_size=" << mtmd_lab_image_size(g_ctx_vision);
    return env->NewStringUTF(s.str().c_str());
}''')
change(cpp,'mparams.use_gpu   = false;','mparams.use_gpu   = g_lab_gpu;')
# Keep language model on CPU, including host ops and KV; GPU only accelerates visual encoder.
change(cpp,'    model_params.n_gpu_layers = 0;','''    model_params.n_gpu_layers = 0;
    static ggml_backend_dev_t cpu_only_devices[] = {nullptr};
    model_params.devices = cpu_only_devices;''')
change(cpp,'    ctx_params.n_threads_batch = N_THREADS;','''    ctx_params.n_threads_batch = N_THREADS;
    ctx_params.offload_kqv = false;
    ctx_params.op_offload = false;''')
# Greedy sampling for comparable output. Same setting in CPU/GPU modes; not identical to v0.1.1.
change(cpp,'constexpr float DEFAULT_SAMPLER_TEMP    = 0.7f;','constexpr float DEFAULT_SAMPLER_TEMP    = 0.0f;')
change(cpp,'''        jint n_predict
) {
    reset_short_term_states();''','''        jint n_predict
) {
    g_lab_times = {};
    reset_short_term_states();''')
change(cpp,'''        int32_t res = mtmd_tokenize(g_ctx_vision, chunks, &text,
                                   g_pending_bitmap ? pending : nullptr,
                                   g_pending_bitmap ? 1 : 0);''','''        auto lab_start = lab_clock::now();
        int32_t res = mtmd_tokenize(g_ctx_vision, chunks, &text,
                                   g_pending_bitmap ? pending : nullptr,
                                   g_pending_bitmap ? 1 : 0);
        g_lab_times.tokenize_us = lab_us(lab_start);''')
change(cpp,'''        llama_pos new_n_past;
        if (mtmd_helper_eval_chunks(g_ctx_vision, g_context, chunks,
                                    current_position, 0, BATCH_SIZE,
                                    true, &new_n_past)) {
            LOGe("%s: mtmd_helper_eval_chunks failed!", __func__);
            mtmd_input_chunks_free(chunks);
            return 2;
        }''','''        // Same sequential chunk eval as mtmd_helper_eval_chunks; split image encoding
        // from LLM image embedding decode. Text path uses the original single-chunk helper.
        llama_pos new_n_past = current_position;
        const size_t count = mtmd_input_chunks_size(chunks);
        for (size_t i = 0; i < count; ++i) {
            const auto *chunk = mtmd_input_chunks_get(chunks, i);
            const auto type = mtmd_input_chunk_get_type(chunk);
            const auto n_tokens = mtmd_input_chunk_get_n_tokens(chunk);
            int32_t rc = 0;
            if (type == MTMD_INPUT_CHUNK_TYPE_IMAGE) {
                ++g_lab_times.image_chunks;
                g_lab_times.image_tokens += n_tokens;
                auto t = lab_clock::now();
                rc = mtmd_encode_chunk(g_ctx_vision, chunk);
                g_lab_times.vision_us += lab_us(t);
                if (rc == 0) {
                    t = lab_clock::now();
                    rc = mtmd_helper_decode_image_chunk(g_ctx_vision, g_context, chunk,
                            mtmd_get_output_embd(g_ctx_vision), new_n_past, 0, BATCH_SIZE, &new_n_past);
                    g_lab_times.image_decode_us += lab_us(t);
                }
            } else if (type == MTMD_INPUT_CHUNK_TYPE_TEXT) {
                g_lab_times.text_tokens += n_tokens;
                auto t = lab_clock::now();
                rc = mtmd_helper_eval_chunk_single(g_ctx_vision, g_context, chunk, new_n_past,
                        0, BATCH_SIZE, i == count - 1, &new_n_past);
                g_lab_times.text_decode_us += lab_us(t);
            } else {
                rc = -1; // lab accepts still images only
            }
            if (rc != 0) {
                mtmd_input_chunks_free(chunks);
                return 2;
            }
        }''')
# Fix native unload idempotence on failed model loads. No change to normal unload.
s=cpp.read_text()
start=s.index("Java_com_example_minicpm_1v_1demo_LlamaEngine_unload(")
end=s.index("Java_com_example_minicpm_1v_1demo_LlamaEngine_shutdown(",start)
part=s[start:end]
for old,new in [
    ("common_sampler_free(g_sampler);","if (g_sampler) common_sampler_free(g_sampler);"),
    ("llama_batch_free(g_batch);","if (g_batch.token || g_batch.embd) llama_batch_free(g_batch);"),
    ("llama_free(g_context);","if (g_context) llama_free(g_context);"),
    ("llama_model_free(g_model);","if (g_model) llama_model_free(g_model);")]:
    assert part.count(old)==1
    part=part.replace(old,new)
s=s[:start]+part+s[end:]
cpp.write_text(s)
kt=java/"LlamaEngine.kt"
change(kt,'    fun sendUserPrompt(','''    private external fun labConfigureNative(gpu:Boolean, threads:Int)
    private external fun labTimingNative():String
    private external fun labBackendNative():String
    suspend fun labConfigure(gpu:Boolean,threads:Int)=withContext(llamaDispatcher){
        check(_state.value is LlamaState.Initialized)
        labConfigureNative(gpu,threads)
    }
    suspend fun labBackendInfo()=withContext(llamaDispatcher){labBackendNative()}
    suspend fun labTimings()=withContext(llamaDispatcher){labTimingNative()}
    fun sendUserPrompt(''')
activity=java/"LabActivity.kt"
change(activity,'    private val sliceValues=listOf(1,2,4,6,9)','''    private val sliceValues=listOf(1)
    private data class Backend(val gpu:Boolean,val threads:Int){
        val title get()=if(gpu)"GPU视觉 + CPU4线程" else "CPU"+threads+"线程"
    }
    private val backends=listOf(Backend(false,4),Backend(false,6),Backend(false,8),Backend(true,4))
    private lateinit var backendPicker:Spinner''')
change(activity,'models=readManifest()','models=readManifest().filter{it.id=="minicpm-v-4_7-1b"}')
change(activity,'label("v0.1.1 · 图像速度专项 · CPU · 不上传照片")','label("v0.1.2 · CPU/GPU视觉对比 · 单切片 · 仅1B · 不上传照片")')
old_start=activity.read_text().index('        label("视觉切片上限")')
s=activity.read_text()
old_end=s.index('        stop=button(',old_start)
s=s[:old_start]+'''        label("固定单切片；语言模型始终CPU；贪心解码")
        slices=spinner(sliceValues.map{it.toString()})
        backendPicker=spinner(backends.map{it.title})
        button("测试所选后端（1冷 + 2暖）"){
            val config=Trial(edgeValues[resolution.selectedItemPosition],1)
            startTest(listOf(selected()),List(3){config},"manual_backend",listOf(backends[backendPicker.selectedItemPosition]))
        }
        button("自动对比 CPU4 / GPU视觉（同图，分别1冷2暖）"){
            val config=Trial(edgeValues[resolution.selectedItemPosition],1)
            startTest(listOf(selected()),List(3){config},"cpu_gpu",listOf(backends[0],backends[3]))
        }
        button("自动对比 CPU 4 / 6 / 8线程"){
            val config=Trial(edgeValues[resolution.selectedItemPosition],1)
            startTest(listOf(selected()),List(3){config},"cpu_threads",backends.take(3))
        }
'''+s[old_end:]
activity.write_text(s)
change(activity,'private fun startTest(chosen:List<Model>,trials:List<Trial>,suite:String){','private fun startTest(chosen:List<Model>,trials:List<Trial>,suite:String,configs:List<Backend>){')
# Verify weights once per batch; each backend loads and releases its own model/visual encoder.
change(activity,'''                val loadStart=now()
                try{
                    status.text="加载 "+m.title''','''                for(cfg in configs){
                checkStop()
                val request="requested="+(if(cfg.gpu)"gpu" else "cpu")+" threads="+cfg.threads
                append("v=0.1.2 batch="+batch+" suite="+suite+" "+request+" phase=begin")
                engine.labConfigure(cfg.gpu,cfg.threads)
                val loadStart=now()
                try{
                    status.text="加载 "+m.title+" "+cfg.title''')
change(activity,'''                    check(engine.isVisionSupported){"视觉投影器加载失败，禁止文本降级"}
                    for''','''                    check(engine.isVisionSupported){"视觉投影器加载失败，禁止文本降级"}
                    val actual=engine.labBackendInfo()
                    append("v=0.1.2 batch="+batch+" "+request+" "+actual+" phase=backend_loaded load_ms="+loadMs)
                    check(actual.contains(if(cfg.gpu)"backend_gpu=1" else "backend_gpu=0")){"请求的视觉后端未实际启用，不计入对比"}
                    for''')
change(activity,'status.text=m.title+" 第"+(run+1)','status.text=cfg.title+" 第"+(run+1)')
change(activity,'val header="\\n"+m.title+" 第"+(run+1)','val header="\\n"+cfg.title+" 第"+(run+1)')
change(activity,'''                            append(listOf("v=0.1.1","batch="+batch,''','''                            val native=engine.labTimings()
                            append(listOf("v=0.1.2",request,actual,native,"sampling=greedy","batch="+batch,''')
change(activity,'''                }finally{withContext(NonCancellable){engine.labRelease()}}
            }''','''                }catch(e:CancellationException){throw e}
                catch(e:Exception){
                    append("v=0.1.2 batch="+batch+" "+request+" phase=failed error="+e.javaClass.simpleName)
                    throw e
                }finally{withContext(NonCancellable){engine.labRelease()}}
                }
            }''')
# UI feedback: failures must not be mistaken for successful GPU measurements.
print("GPU lab patch applied; same GGUF catalog; single slice; greedy generation")
