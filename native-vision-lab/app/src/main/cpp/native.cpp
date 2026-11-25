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
#include "resize_area.h"
#include "perf_diag.h"
static llama_model *model=nullptr;
static llama_context *ctx=nullptr;
static mtmd_context *vision=nullptr;
static common_chat_templates_ptr tmpls;
static std::string active_model,active_vision;
static int active_threads=0,active_profile=-1;
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
    vision=nullptr; ctx=nullptr; model=nullptr; active_threads=0;active_profile=-1;
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
extern "C" JNIEXPORT jstring JNICALL Java_com_vobich_bluestar_nativelab_MainActivity_nativeBench(JNIEnv* e,jobject){
    try{
        release();
        if(!backend_started){llama_log_set(quiet,nullptr);mtmd_helper_log_set(quiet,nullptr);llama_backend_init();backend_started=true;}
        auto s=bsdiag::features();
        for(auto p:{0,1,2})s+="\n"+bsdiag::bench(p==1?6:4,p==2);
        return e->NewStringUTF(s.c_str());
    }catch(const std::exception &ex){return e->NewStringUTF(ex.what());}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_vobich_bluestar_nativelab_MainActivity_nativeRun(
 JNIEnv* e,jobject o,jstring jm,jstring jv,jstring ji,jint threads,jint orientation,jint pixel_cap,jint profile){
    bsdiag::Affinity affinity(profile==2);auto cpu_begin=bsdiag::cpu_ms();
    std::string chunk_stats;auto begin=Clock::now();long long load=0,reset=0,decode=0,resize=0,split=0,enc=0,prefill=0,first=-1,gen=0;
    size_t image_chunks=0,image_tokens=0,text_tokens=0,encoded_chunks=0; uint32_t input_w=0,input_h=0,work_w=0,work_h=0;int tokens=0;
    std::string result;
    llama_batch batch{};llama_sampler* sampler=nullptr;
    try{
        auto clazz=e->GetObjectClass(o);
        auto onphase=e->GetMethodID(clazz,"onPhase","(Ljava/lang/String;)V");
        auto onpiece=e->GetMethodID(clazz,"onPiece","([B)V");
        e->DeleteLocalRef(clazz);
        check(onphase&&onpiece,"callback methods missing");
        auto m=str(e,jm),v=str(e,jv),image=str(e,ji);
        check(threads==4||threads==6,"bad threads");check(profile>=0&&profile<=3,"bad profile");
        check(pixel_cap==0||pixel_cap==3145728||pixel_cap==2239488||pixel_cap==1769472||pixel_cap==786432||pixel_cap==442368,"bad pixel cap");
        check(!cancelled,"cancelled");
        if(!backend_started){llama_log_set(quiet,nullptr);mtmd_helper_log_set(quiet,nullptr);llama_backend_init();backend_started=true;}
        if(!model||m!=active_model||v!=active_vision||threads!=active_threads||profile!=active_profile){
            phase(e,o,onphase,"load");auto t=Clock::now();release();
            auto mp=llama_model_default_params();mp.n_gpu_layers=0;mp.use_mmap=true;
            model=llama_model_load_from_file(m.c_str(),mp);check(model,"language model load");
            auto cp=llama_context_default_params();
            cp.n_ctx=8192;cp.n_batch=profile==3?2048:512;cp.n_ubatch=cp.n_batch;cp.n_threads=threads;cp.n_threads_batch=threads;
            cp.flash_attn_type=LLAMA_FLASH_ATTN_TYPE_AUTO;cp.abort_callback=abort_fn;cp.abort_callback_data=nullptr;
            ctx=llama_init_from_model(model,cp);check(ctx,"language context load");
            auto vp=mtmd_context_params_default();vp.use_gpu=false;vp.n_threads=threads;
            vp.image_max_slice_nums=9;vp.flash_attn_type=LLAMA_FLASH_ATTN_TYPE_AUTO;vp.warmup=profile==1||profile==3;
            vision=mtmd_init_from_file(v.c_str(),model,vp);check(vision&&mtmd_support_vision(vision),"vision model load");
            tmpls=common_chat_templates_init(model,"");check((bool)tmpls,"model template");
            active_model=m;active_vision=v;active_threads=threads;active_profile=profile;load=ms(t);
        }
        check(!cancelled,"cancelled");
        auto t=Clock::now();llama_memory_clear(llama_get_memory(ctx),true);reset=ms(t);
        phase(e,o,onphase,"decode");t=Clock::now();
        std::unique_ptr<mtmd_bitmap,decltype(&mtmd_bitmap_free)> bmp(mtmd_helper_bitmap_init_from_file(vision,image.c_str()),mtmd_bitmap_free);
        check((bool)bmp,"image decode: JPEG/PNG only");
        // Preserve pixels, apply EXIF orientation without lossy re-encoding.
        if(orientation>=2&&orientation<=8){
            auto w=mtmd_bitmap_get_nx(bmp.get()),h=mtmd_bitmap_get_ny(bmp.get());
            auto data=mtmd_bitmap_get_data(bmp.get());
            check(w>0&&h>0&&mtmd_bitmap_get_n_bytes(bmp.get())==size_t(w)*h*3,"RGB geometry");
            auto dw=orientation>=5?h:w,dh=orientation>=5?w:h;
            std::vector<unsigned char> rgb(size_t(dw)*dh*3);
            for(uint32_t y=0;y<h;y++)for(uint32_t x=0;x<w;x++){
                uint32_t a=x,b=y;
                switch(orientation){
                    case 2:a=w-1-x;break;
                    case 3:a=w-1-x;b=h-1-y;break;
                    case 4:b=h-1-y;break;
                    case 5:a=y;b=x;break;
                    case 6:a=h-1-y;b=x;break;
                    case 7:a=h-1-y;b=w-1-x;break;
                    case 8:a=y;b=w-1-x;break;
                }
                auto src=(size_t(y)*w+x)*3,dst=(size_t(b)*dw+a)*3;
                for(int c=0;c<3;c++)rgb[dst+c]=data[src+c];
            }
            bmp.reset(mtmd_bitmap_init(dw,dh,rgb.data()));check((bool)bmp,"orientation");
        }
        decode=ms(t);
        check(!cancelled,"cancelled");
        input_w=mtmd_bitmap_get_nx(bmp.get()); input_h=mtmd_bitmap_get_ny(bmp.get());
        check(input_w>0&&input_h>0&&uint64_t(input_w)*input_h<=24000000&&mtmd_bitmap_get_n_bytes(bmp.get())==size_t(input_w)*input_h*3,"RGB geometry");
        auto dims=bluestar::fit(input_w,input_h,pixel_cap); work_w=dims.first;work_h=dims.second;
        if(work_w!=input_w||work_h!=input_h){
            phase(e,o,onphase,"resize");t=Clock::now();
            auto rgb=bluestar::resize_area(mtmd_bitmap_get_data(bmp.get()),input_w,input_h,work_w,work_h);
            bmp.reset(mtmd_bitmap_init(work_w,work_h,rgb.data()));check((bool)bmp,"resize bitmap");
            resize=ms(t);
        }
        check(!cancelled,"cancelled");
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
        const int eval_batch=profile==3?2048:512;
        llama_pos pos=0;
        for(size_t i=0;i<count;i++){
            check(!cancelled,"cancelled");
            auto c=mtmd_input_chunks_get(chunks.get(),i);
            if(mtmd_input_chunk_get_type(c)==MTMD_INPUT_CHUNK_TYPE_IMAGE){
                auto progress=std::string("vision ")+std::to_string(encoded_chunks+1)+"/"+std::to_string(image_chunks)+" tokens="+std::to_string(mtmd_input_chunk_get_n_tokens(c))+" input="+std::to_string(work_w)+"x"+std::to_string(work_h);
                phase(e,o,onphase,progress.c_str());t=Clock::now();
                auto before_generic=bs_count_f16_generic(),before_kai=bs_count_f16_kai();auto before_cpu=bsdiag::cpu_ms();
                check(mtmd_encode_chunk(vision,c)==0,"vision encode");auto dt=ms(t);enc+=dt;encoded_chunks++;
                auto detail=std::string(" block=")+std::to_string(encoded_chunks)+":"+std::to_string(dt)+"ms:"+std::to_string(bsdiag::cpu_ms()-before_cpu)+"cpu_ms:"+std::to_string(bs_count_f16_generic()-before_generic)+"generic_f16:"+std::to_string(bs_count_f16_kai()-before_kai)+"kai_f16";
                chunk_stats+=detail;
                phase(e,o,onphase,("vision_done"+detail).c_str());
                check(!cancelled,"cancelled");
                auto progress2=std::string("prefill image ")+std::to_string(encoded_chunks)+"/"+std::to_string(image_chunks);
                phase(e,o,onphase,progress2.c_str());t=Clock::now();llama_pos next=pos;
                check(mtmd_helper_decode_image_chunk(vision,ctx,c,mtmd_get_output_embd(vision),pos,0,eval_batch,&next)==0,"image prefill");
                llama_synchronize(ctx);pos=next;prefill+=ms(t);
            }else{
                phase(e,o,onphase,"prefill");t=Clock::now();llama_pos next=pos;
                check(mtmd_helper_eval_chunk_single(vision,ctx,c,pos,0,eval_batch,i+1==count,&next)==0,"text prefill");
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
    auto report="status="+result+" threads="+std::to_string(threads)+" load_ms="+std::to_string(load)+" reset_ms="+std::to_string(reset)+" decode_ms="+std::to_string(decode)+" resize_ms="+std::to_string(resize)+" pixel_cap="+std::to_string(pixel_cap)+" input="+std::to_string(input_w)+"x"+std::to_string(input_h)+" work="+std::to_string(work_w)+"x"+std::to_string(work_h)+" encoded_chunks="+std::to_string(encoded_chunks)+" split_ms="+std::to_string(split)+" vision_ms="+std::to_string(enc)+" prefill_ms="+std::to_string(prefill)+" image_chunks="+std::to_string(image_chunks)+" image_tokens="+std::to_string(image_tokens)+" text_tokens="+std::to_string(text_tokens)+" first_ms="+std::to_string(first)+" gen_ms="+std::to_string(gen)+" tokens="+std::to_string(tokens)+" total_ms="+std::to_string(ms(begin)-load);
    report+=" profile="+std::to_string(profile)+" process_cpu_ms="+std::to_string(bsdiag::cpu_ms()-cpu_begin)+affinity.info()+bsdiag::features()+chunk_stats;
    return e->NewStringUTF(report.c_str());
}
