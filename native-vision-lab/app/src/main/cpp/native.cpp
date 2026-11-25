#include <jni.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include "chat.h"
#include "common.h"
static llama_model *model=nullptr;
static llama_context *ctx=nullptr;
static mtmd_context *vision=nullptr;
static common_chat_templates_ptr tmpls;
static std::string active_model,active_vision;
static int active_threads=0;
static std::atomic<bool> cancelled{false};
static bool backend_started=false;
using Clock=std::chrono::steady_clock;
static long long ms(Clock::time_point t){return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now()-t).count();}
static void quiet(ggml_log_level,const char*,void*){}
static bool abort_fn(void*){return cancelled.load();}
static void release(){
    tmpls.reset();
    if(vision) mtmd_free(vision);
    if(ctx) llama_free(ctx);
    if(model) llama_model_free(model);
    vision=nullptr; ctx=nullptr; model=nullptr; active_threads=0;
    active_model.clear();active_vision.clear();
}
static std::string str(JNIEnv* e,jstring j){
    if(!j)throw std::runtime_error("null argument");
    const char* p=e->GetStringUTFChars(j,nullptr);
    if(!p)throw std::runtime_error("string allocation");
    std::string s(p);e->ReleaseStringUTFChars(j,p);return s;
}
static void check(bool ok,const char *reason){if(!ok)throw std::runtime_error(reason);}
static void phase(JNIEnv* e,jobject o,jmethodID m,const char* s){
    auto j=e->NewStringUTF(s);e->CallVoidMethod(o,m,j);e->DeleteLocalRef(j);
}
static bool complete_utf8(const std::string &s){
    size_t i=0;
    while(i<s.size()){
        unsigned char c=s[i];size_t n=c<0x80?1:(c&0xe0)==0xc0?2:(c&0xf0)==0xe0?3:(c&0xf8)==0xf0?4:1;
        if(i+n>s.size())return false;
        i+=n;
    }return true;
}
static void emit(JNIEnv* e,jobject o,jmethodID m,const std::string &s){
    auto b=e->NewByteArray((jsize)s.size());
    if(!b)throw std::runtime_error("token allocation");
    e->SetByteArrayRegion(b,0,(jsize)s.size(),reinterpret_cast<const jbyte*>(s.data()));
    e->CallVoidMethod(o,m,b);e->DeleteLocalRef(b);
}
extern "C" JNIEXPORT void JNICALL Java_com_vobich_bluestar_nativelab_MainActivity_nativeCancel(JNIEnv*,jobject){cancelled.store(true);}
extern "C" JNIEXPORT void JNICALL Java_com_vobich_bluestar_nativelab_MainActivity_nativeResetCancel(JNIEnv*,jobject){cancelled.store(false);}
extern "C" JNIEXPORT void JNICALL Java_com_vobich_bluestar_nativelab_MainActivity_nativeRelease(JNIEnv*,jobject){release();}
extern "C" JNIEXPORT jstring JNICALL Java_com_vobich_bluestar_nativelab_MainActivity_nativeRun(
 JNIEnv* e,jobject o,jstring jm,jstring jv,jstring ji,jint threads){
    auto begin=Clock::now();long long load=0,reset=0,decode=0,split=0,enc=0,prefill=0,first=-1,gen=0;
    size_t image_chunks=0,image_tokens=0,text_tokens=0;int tokens=0;
    std::string result;
    llama_batch batch{};llama_sampler* sampler=nullptr;
    try{
        auto clazz=e->GetObjectClass(o);
        auto onphase=e->GetMethodID(clazz,"onPhase","(Ljava/lang/String;)V");
        auto onpiece=e->GetMethodID(clazz,"onPiece","([B)V");
        e->DeleteLocalRef(clazz);
        check(onphase&&onpiece,"callback methods missing");
        auto m=str(e,jm),v=str(e,jv),image=str(e,ji);
        check(threads==4||threads==6,"bad threads");
        check(!cancelled,"cancelled");
        if(!backend_started){llama_log_set(quiet,nullptr);mtmd_helper_log_set(quiet,nullptr);llama_backend_init();backend_started=true;}
        if(!model||m!=active_model||v!=active_vision||threads!=active_threads){
            phase(e,o,onphase,"load");auto t=Clock::now();release();
            auto mp=llama_model_default_params();mp.n_gpu_layers=0;mp.use_mmap=true;
            model=llama_model_load_from_file(m.c_str(),mp);check(model,"language model load");
            auto cp=llama_context_default_params();
            cp.n_ctx=8192;cp.n_batch=512;cp.n_ubatch=512;cp.n_threads=threads;cp.n_threads_batch=threads;
            cp.flash_attn_type=LLAMA_FLASH_ATTN_TYPE_AUTO;cp.abort_callback=abort_fn;cp.abort_callback_data=nullptr;
            ctx=llama_init_from_model(model,cp);check(ctx,"language context load");
            auto vp=mtmd_context_params_default();vp.use_gpu=false;vp.n_threads=threads;
            vp.image_max_slice_nums=9;vp.flash_attn_type=LLAMA_FLASH_ATTN_TYPE_AUTO;vp.warmup=false;
            vision=mtmd_init_from_file(v.c_str(),model,vp);check(vision&&mtmd_support_vision(vision),"vision model load");
            tmpls=common_chat_templates_init(model,"");check((bool)tmpls,"model template");
            active_model=m;active_vision=v;active_threads=threads;load=ms(t);
        }
        check(!cancelled,"cancelled");
        auto t=Clock::now();llama_memory_clear(llama_get_memory(ctx),true);reset=ms(t);
        phase(e,o,onphase,"decode");t=Clock::now();
        std::unique_ptr<mtmd_bitmap,decltype(&mtmd_bitmap_free)> bmp(mtmd_helper_bitmap_init_from_file(vision,image.c_str()),mtmd_bitmap_free);
        check((bool)bmp,"image decode: JPEG/PNG only");decode=ms(t);
        phase(e,o,onphase,"split");t=Clock::now();
        common_chat_msg msg;msg.role="user";msg.content=std::string(mtmd_default_marker())+"\n请逐字抄写图片中所有可见文字和数字。按阅读顺序输出，不解释、不补充、不猜测。";
        common_chat_templates_inputs in;in.messages.push_back(msg);in.use_jinja=true;in.enable_thinking=false;in.add_generation_prompt=true;
        auto formatted=common_chat_templates_apply(tmpls.get(),in);
        check(formatted.prompt.find(mtmd_default_marker())!=std::string::npos,"missing image marker");
        mtmd_input_text input{};input.text=formatted.prompt.c_str();input.add_special=true;input.parse_special=true;
        std::unique_ptr<mtmd_input_chunks,decltype(&mtmd_input_chunks_free)> chunks(mtmd_input_chunks_init(),mtmd_input_chunks_free);
        const mtmd_bitmap* bm[]={bmp.get()};
        check(mtmd_tokenize(vision,chunks.get(),&input,bm,1)==0,"image tokenization");
        bmp.reset();split=ms(t);
        auto count=mtmd_input_chunks_size(chunks.get());check(count>0,"empty chunks");
        for(size_t i=0;i<count;i++){
            auto c=mtmd_input_chunks_get(chunks.get(),i);
            auto type=mtmd_input_chunk_get_type(c);
            if(type==MTMD_INPUT_CHUNK_TYPE_IMAGE){image_chunks++;image_tokens+=mtmd_input_chunk_get_n_tokens(c);}
            else if(type==MTMD_INPUT_CHUNK_TYPE_TEXT)text_tokens+=mtmd_input_chunk_get_n_tokens(c);
            else throw std::runtime_error("unsupported chunk");
        }
        check(image_chunks>0&&image_tokens+text_tokens+512<8192,"image context exceeds limit");
        llama_pos pos=0;
        for(size_t i=0;i<count;i++){
            check(!cancelled,"cancelled");
            auto c=mtmd_input_chunks_get(chunks.get(),i);
            if(mtmd_input_chunk_get_type(c)==MTMD_INPUT_CHUNK_TYPE_IMAGE){
                phase(e,o,onphase,"vision");t=Clock::now();
                check(mtmd_encode_chunk(vision,c)==0,"vision encode");enc+=ms(t);
                check(!cancelled,"cancelled");
                phase(e,o,onphase,"prefill");t=Clock::now();llama_pos next=pos;
                check(mtmd_helper_decode_image_chunk(vision,ctx,c,mtmd_get_output_embd(vision),pos,0,512,&next)==0,"image prefill");
                llama_synchronize(ctx);pos=next;prefill+=ms(t);
            }else{
                phase(e,o,onphase,"prefill");t=Clock::now();llama_pos next=pos;
                check(mtmd_helper_eval_chunk_single(vision,ctx,c,pos,0,512,i+1==count,&next)==0,"text prefill");
                llama_synchronize(ctx);pos=next;prefill+=ms(t);
            }
        }
        chunks.reset();
        phase(e,o,onphase,"generate");t=Clock::now();
        auto vocab=llama_model_get_vocab(model);sampler=llama_sampler_init_greedy();check(sampler,"sampler");
        batch=llama_batch_init(1,0,1);
        std::string pending;
        for(int i=0;i<512&&!cancelled;i++){
            auto tok=llama_sampler_sample(sampler,ctx,-1);
            if(llama_vocab_is_eog(vocab,tok))break;
            auto piece=common_token_to_piece(ctx,tok);
            tokens++;pending+=piece;
            if(complete_utf8(pending)&&!pending.empty()){
                if(first<0&&pending.find_first_not_of(" \t\r\n")!=std::string::npos)first=ms(begin)-load;
                emit(e,o,onpiece,pending);pending.clear();
            }
            if(e->ExceptionCheck())throw std::runtime_error("callback error");
            common_batch_clear(batch);common_batch_add(batch,tok,pos++,{0},true);
            check(llama_decode(ctx,batch)==0,"decode token");
        }
        gen=ms(t);
        check(!cancelled,"cancelled");
        check(first>=0,"empty result");
        result="ok";
    }catch(const std::exception &ex){
        result=cancelled?"cancelled":"error";
        // Do not leak model paths, image bytes, prompts or generated text in diagnostics.
        if(!e->ExceptionCheck()){
            auto c=e->GetObjectClass(o);auto p=e->GetMethodID(c,"onPhase","(Ljava/lang/String;)V");e->DeleteLocalRef(c);
            if(p)phase(e,o,p,ex.what());
        }
        release();
    }catch(...){result="error";release();}
    if(batch.token)llama_batch_free(batch);
    if(sampler)llama_sampler_free(sampler);
    if(e->ExceptionCheck())return nullptr;
    auto report="status="+result+" threads="+std::to_string(threads)+" load_ms="+std::to_string(load)+" reset_ms="+std::to_string(reset)+" decode_ms="+std::to_string(decode)+" split_ms="+std::to_string(split)+" vision_ms="+std::to_string(enc)+" prefill_ms="+std::to_string(prefill)+" image_chunks="+std::to_string(image_chunks)+" image_tokens="+std::to_string(image_tokens)+" text_tokens="+std::to_string(text_tokens)+" first_ms="+std::to_string(first)+" gen_ms="+std::to_string(gen)+" tokens="+std::to_string(tokens)+" total_ms="+std::to_string(ms(begin)-load);
    return e->NewStringUTF(report.c_str());
}
