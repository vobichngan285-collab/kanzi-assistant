#pragma once
#include "ggml.h"
#include "ggml-cpu.h"
#include "ggml-backend.h"
#include "ggml-alloc.h"
#include <sched.h>
#include <sys/auxv.h>
#include <time.h>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cmath>
#include <memory>
#include <vector>
#include <stdexcept>
#include <chrono>
#include <asm/hwcap.h>
extern "C" uint64_t bs_count_f16_generic(void);
extern "C" uint64_t bs_count_f16_kai(void);
namespace bsdiag {
inline long long cpu_ms(){timespec t{};if(clock_gettime(CLOCK_PROCESS_CPUTIME_ID,&t))return -1;return t.tv_sec*1000LL+t.tv_nsec/1000000;}
inline std::string mask_text(const cpu_set_t &s){std::string a;for(int i=0;i<CPU_SETSIZE;i++)if(CPU_ISSET(i,&s)){if(!a.empty())a+=",";a+=std::to_string(i);}return a.empty()?"none":a;}
inline long metric(int cpu,const char* key){std::ifstream f("/sys/devices/system/cpu/cpu"+std::to_string(cpu)+"/"+key);long v=0;return f>>v?v:0;}
struct Affinity {
    cpu_set_t old{}, applied{};
    bool saved=false,changed=false;std::string result="default";
    explicit Affinity(bool top){
        CPU_ZERO(&old);CPU_ZERO(&applied);
        saved=sched_getaffinity(0,sizeof(old),&old)==0;
        if(!saved){result="read_failed";return;}
        applied=old;
        if(!top)return;
        std::vector<std::pair<long,int>> rank;
        for(int i=0;i<CPU_SETSIZE;i++)if(CPU_ISSET(i,&old))rank.emplace_back(metric(i,"cpu_capacity"),i);
        if(rank.empty()){result="empty";return;}
        if(std::any_of(rank.begin(),rank.end(),[](auto p){return p.first<=0;}))
            for(auto &p:rank)p.first=metric(p.second,"cpufreq/cpuinfo_max_freq");
        if(std::any_of(rank.begin(),rank.end(),[](auto p){return p.first<=0;})||rank.size()<4){result="top4_unavailable";return;}
        std::sort(rank.rbegin(),rank.rend());
        cpu_set_t target;CPU_ZERO(&target);for(int i=0;i<4;i++)CPU_SET(rank[i].second,&target);
        changed=sched_setaffinity(0,sizeof(target),&target)==0;
        result=changed?"top4_request_ok":"top4_request_failed";
        if(sched_getaffinity(0,sizeof(applied),&applied)!=0)CPU_ZERO(&applied);
    }
    ~Affinity(){if(changed&&saved)sched_setaffinity(0,sizeof(old),&old);}
    std::string info()const{return " affinity="+result+" caller_allowed="+mask_text(applied);}
};
inline std::string features(){
    auto a=getauxval(AT_HWCAP),b=getauxval(AT_HWCAP2);
    std::ostringstream o;o<<" hwcap="<<a<<" hwcap2="<<b<<" ggml_neon="<<ggml_cpu_has_neon()<<" ggml_fp16va="<<ggml_cpu_has_fp16_va()<<" ggml_dotprod="<<ggml_cpu_has_dotprod()<<" ggml_i8mm="<<ggml_cpu_has_matmul_int8()<<" ggml_sve="<<ggml_cpu_has_sve()<<" ggml_sme="<<ggml_cpu_has_sme()<<" ggml_llamafile="<<ggml_cpu_has_llamafile();
#ifdef HWCAP_ASIMDHP
    o<<" hw_asimdhp="<<bool(a&HWCAP_ASIMDHP);
#endif
#ifdef HWCAP2_BF16
    o<<" hw_bf16="<<bool(b&HWCAP2_BF16);
#endif
    return o.str();
}
inline std::string bench(int threads,bool top){
    Affinity affinity(top);
    constexpr int k=1152,m=1152,n=256;
    std::unique_ptr<ggml_context,decltype(&ggml_free)> c(ggml_init({ggml_tensor_overhead()*8+ggml_graph_overhead()+4096,nullptr,true}),ggml_free);
    if(!c)throw std::runtime_error("bench context");
    auto a=ggml_new_tensor_2d(c.get(),GGML_TYPE_F16,k,m);
    auto b=ggml_new_tensor_2d(c.get(),GGML_TYPE_F32,k,n);
    auto out=ggml_mul_mat(c.get(),a,b);
    auto graph=ggml_new_graph(c.get());ggml_build_forward_expand(graph,out);
    std::unique_ptr<ggml_backend,decltype(&ggml_backend_free)> be(ggml_backend_cpu_init(),ggml_backend_free);
    if(!be)throw std::runtime_error("bench backend");
    ggml_backend_cpu_set_n_threads(be.get(),threads);
    std::unique_ptr<ggml_backend_buffer,decltype(&ggml_backend_buffer_free)> buf(ggml_backend_alloc_ctx_tensors(c.get(),be.get()),ggml_backend_buffer_free);
    if(!buf)throw std::runtime_error("bench buffer");
    std::vector<ggml_fp16_t> av(k*m,ggml_fp32_to_fp16(1.0f/64));
    std::vector<float> bv(k*n,1.0f/16),ov(m*n);
    ggml_backend_tensor_set(a,av.data(),0,av.size()*sizeof(av[0]));ggml_backend_tensor_set(b,bv.data(),0,bv.size()*sizeof(bv[0]));
    std::ostringstream o;o<<"bench_f16_f32 threads="<<threads<<affinity.info()<<" dims=1152x1152x256";
    for(int i=0;i<3;i++){
        auto g=bs_count_f16_generic(),kai=bs_count_f16_kai();auto cpu=cpu_ms();auto t=std::chrono::steady_clock::now();
        if(ggml_backend_graph_compute(be.get(),graph)!=GGML_STATUS_SUCCESS)throw std::runtime_error("bench compute");
        auto elapsed=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-t).count();auto used=cpu_ms()-cpu;
        ggml_backend_tensor_get(out,ov.data(),0,ov.size()*sizeof(float));
        float err=0;for(float x:ov){if(!std::isfinite(x))throw std::runtime_error("bench nonfinite");err=std::max(err,std::abs(x-1.125f));}
        if(err>0.001f)throw std::runtime_error("bench result");
        o<<" run"<<i<<"_ms="<<elapsed<<" cpu_ms="<<used<<" generic_f16="<<bs_count_f16_generic()-g<<" kai_f16="<<bs_count_f16_kai()-kai<<" max_error="<<err;
    }
    return o.str();
}
}
