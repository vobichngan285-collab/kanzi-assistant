#include <jni.h>
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <chrono>
#include <cmath>
#include <algorithm>
#include <limits>
#include <cstring>
#include <cstdint>
#include <dlfcn.h>
#include <memory>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>
extern "C" void ocr_trace_reset();
extern "C" const char* ocr_trace_get();
extern "C" void ocr_device_reset();
extern "C" const char* ocr_device_get();
extern "C" void ocr_io_reset();
extern "C" const char* ocr_io_get();
using Clock=std::chrono::steady_clock;
static double ms(Clock::time_point t){return std::chrono::duration<double,std::milli>(Clock::now()-t).count();}
struct State{
 MNN::Interpreter* net=nullptr;
 MNN::Session* session=nullptr;
 std::vector<int> shape,output;
 int requested=0;
 int tuning=0;
 bool fp16=false;
 std::string indexName;
 std::string info, probe;
 MNN::BackendConfig cfg;
 ~State(){if(net){if(session)net->releaseSession(session);MNN::Interpreter::destroy(net);}}
};
static void check(bool b,const char* s){if(!b)throw std::runtime_error(s);}
// Read-only capability probe through the same process' vendor library namespace.
 // OpenCL C ABI: cl_int=int32, cl_uint=uint32, cl_device_type=cl_ulong.
 // No context/queue/kernel creation; MNN still owns the execution runtime.
static std::string probeOpenCL(bool* fp16){
 std::ostringstream log;
 dlerror();
 void* library=dlopen("libOpenCL.so",RTLD_NOW|RTLD_LOCAL);
 if(!library){const char* error=dlerror();log<<"CL_PROBE dlopen=0 error="<<(error?error:"unknown");return log.str();}
 struct Close{void* h;~Close(){dlclose(h);}} close{library};
 using Platforms=int32_t(*)(uint32_t,void**,uint32_t*);
 using Devices=int32_t(*)(void*,uint64_t,uint32_t,void**,uint32_t*);
 using DeviceInfo=int32_t(*)(void*,uint32_t,size_t,void*,size_t*);
 auto platforms=reinterpret_cast<Platforms>(dlsym(library,"clGetPlatformIDs"));
 auto devices=reinterpret_cast<Devices>(dlsym(library,"clGetDeviceIDs"));
 auto info=reinterpret_cast<DeviceInfo>(dlsym(library,"clGetDeviceInfo"));
 log<<"CL_PROBE dlopen=1 symbols="<<(platforms&&devices&&info?1:0);
 if(!platforms||!devices||!info)return log.str();
 uint32_t n=0;int32_t rc=platforms(0,nullptr,&n);
 log<<" platform_rc="<<rc<<" platforms="<<n;
 if(rc!=0||n==0||n>64)return log.str();
 std::vector<void*> ps(n);
 rc=platforms(n,ps.data(),nullptr);
 log<<" platform_list_rc="<<rc;
 if(rc!=0)return log.str();
 for(uint32_t p=0;p<n;p++){
  uint32_t count=0;rc=devices(ps[p],uint64_t(4),0,nullptr,&count); // CL_DEVICE_TYPE_GPU
  log<<" platform["<<p<<"]_gpu_rc="<<rc<<" gpu_count="<<count;
  if(rc!=0||count==0||count>64)continue;
  std::vector<void*> ds(count);
  rc=devices(ps[p],uint64_t(4),count,ds.data(),nullptr);
  log<<" device_list_rc="<<rc;
  if(rc!=0)continue;
  for(uint32_t d=0;d<count;d++){
   uint64_t halfConfig=0;size_t extSize=0;
   auto halfRc=info(ds[d],0x1033,sizeof(halfConfig),&halfConfig,nullptr);
   auto extRc=info(ds[d],0x1030,0,nullptr,&extSize);
   bool halfExtension=false;
   if(extRc==0&&extSize>0&&extSize<65536){std::vector<char> ext(extSize,0);if(info(ds[d],0x1030,extSize,ext.data(),nullptr)==0)halfExtension=(std::string(" ")+ext.data()+" ").find(" cl_khr_fp16 ")!=std::string::npos;}
   bool supports=halfRc==0&&halfConfig!=0&&halfExtension;
   if(p==0&&d==0&&count==1&&n==1)*fp16=supports;
   log<<" half_fp_config="<<halfConfig<<" half_extension="<<halfExtension<<" fp16_capability="<<supports;
   for(auto param:{uint32_t(0x102B),uint32_t(0x102D),uint32_t(0x102F)}){ // NAME, DRIVER_VERSION, VERSION
    char value[512]={};size_t size=0;
    int32_t result=info(ds[d],param,sizeof(value)-1,value,&size);
    value[sizeof(value)-1]='\0';
    log<<" d"<<d<<"_"<<param<<"_rc="<<result;
    if(result==0)log<<" value=["<<value<<"]";
   }
  }
 }
 return log.str();
}
static int primaryBackend(State* s){
 int types[16];for(auto& x:types)x=-1;
 return s->net->getSessionInfo(s->session,MNN::Interpreter::BACKENDS,types)?types[0]:-1;
}
static void requireRequestedGpu(State* s,const char* phase){
 if(s->requested!=3&&s->requested!=7)return;
 int primary=primaryBackend(s);
 if(primary!=s->requested){
  throw std::runtime_error(std::string("GPU_BACKEND_REJECT phase=")+phase+" requested="+std::to_string(s->requested)+" actual="+std::to_string(primary)+" selected_device="+ocr_device_get()+" "+s->probe+" no_inference=1");
 }
}
static void fail(JNIEnv* e,const char* s){e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),s);}
static State* state(jlong h){auto s=reinterpret_cast<State*>(h);check(s!=nullptr,"null native handle");return s;}
extern "C" JNIEXPORT jlong JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_open(JNIEnv* e,jobject,jstring path,jint backend,jint threads,jstring cachePath,jint tuning){
 try{
  check(backend==0||backend==3||backend==7,"invalid backend");
  check(tuning>=0&&tuning<=3,"invalid OpenCL policy");
  check(tuning<2||backend==3,"FP16 experiment requires OpenCL");
  const char* p=e->GetStringUTFChars(path,nullptr);check(p!=nullptr,"path");
  std::string file(p);e->ReleaseStringUTFChars(path,p);
  auto s=std::make_unique<State>();s->requested=backend;s->tuning=tuning;
  if(backend==3)s->probe=probeOpenCL(&s->fp16);
  if(tuning>=2&&!s->fp16)throw std::runtime_error("FP16 capability not confirmed "+s->probe);
  ocr_device_reset();
  s->net=MNN::Interpreter::createFromFile(file.c_str());check(s->net,"createFromFile failed");
  if(backend==3){
   const char* c=e->GetStringUTFChars(cachePath,nullptr);check(c!=nullptr,"cache path");
   std::string cache(c);e->ReleaseStringUTFChars(cachePath,c);
   s->net->setCacheFile(cache.c_str());
  }
  s->net->setSessionMode(MNN::Interpreter::Session_Resize_Defer);
  s->net->setSessionMode(MNN::Interpreter::Session_Backend_Fix);
  MNN::ScheduleConfig sc;sc.type=static_cast<MNNForwardType>(backend);sc.backupType=MNN_FORWARD_CPU;
  s->cfg.precision=tuning==2?MNN::BackendConfig::Precision_Normal:tuning==3?MNN::BackendConfig::Precision_Low:MNN::BackendConfig::Precision_High;sc.backendConfig=&s->cfg;
  if(tuning>=2)sc.saveTensors={"compact","compact_idx64","compact_idx64/not_keepdim"};
  if(backend==0)sc.numThread=threads;
  else if(backend==3)sc.mode=(tuning==0?MNN_GPU_TUNING_FAST:MNN_GPU_TUNING_NONE)|MNN_GPU_MEMORY_BUFFER;
  else sc.mode=MNN_GPU_TUNING_NONE;
  s->session=s->net->createSession(sc);
  if(!s->session)throw std::runtime_error("GPU_SESSION_CREATE_FAILED "+s->probe);
  requireRequestedGpu(s.get(),"createSession");
  if(tuning>=2){
   const auto& outputs=s->net->getSessionOutputAll(s->session);
   for(const char* name:{"compact_idx64","compact_idx64/not_keepdim"})if(outputs.count(name)){s->indexName=name;break;}
   check(!s->indexName.empty()&&outputs.count("compact"),"integer compact index output unavailable; FP16 blocked");
  }
  s->info="requested="+std::to_string(backend)+" actual="+std::to_string(primaryBackend(s.get()))+" gpu_tuning="+(tuning==0?"FAST":"NONE")+" precision_policy="+(tuning==2?"NORMAL_FP16_STORAGE":tuning==3?"LOW_FP16_COMPUTE":"HIGH_FP32")+" index_tensor="+s->indexName+" MNN="+MNN::getVersion()+" device="+ocr_device_get()+" "+s->probe;
  return reinterpret_cast<jlong>(s.release());
 }catch(const std::exception& x){fail(e,x.what());return 0;}
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_run(JNIEnv* e,jobject,jlong handle,jobject input,jlongArray dims){
 try{
  auto s=state(handle);ocr_io_reset();check(e->GetArrayLength(dims)==4,"expected rank4");
  jlong raw[4];e->GetLongArrayRegion(dims,0,4,raw);
  std::vector<int> shape;size_t count=1;
  for(auto x:raw){check(x>0&&x<=4096,"invalid input dimension");shape.push_back((int)x);count*=x;}
  check(shape[1]==3&&count<=32*1024*1024,"invalid tensor size");
  auto* data=static_cast<float*>(e->GetDirectBufferAddress(input));
  check(data&&e->GetDirectBufferCapacity(input)>=(jlong)count,"invalid direct float buffer");
  auto* in=s->net->getSessionInput(s->session,nullptr);check(in,"missing input");
  auto t=Clock::now();bool resized=shape!=s->shape;
  if(resized){
   s->net->resizeTensor(in,shape);s->net->resizeSession(s->session);
   int status=-1;check(s->net->getSessionInfo(s->session,MNN::Interpreter::RESIZE_STATUS,&status)&&status==0,"resize failed");
   s->shape=shape;in=s->net->getSessionInput(s->session,nullptr);
  }
  double resize=ms(t);t=Clock::now();
  requireRequestedGpu(s,"before_run");
  check(in&&in->shape()==shape,"input shape mismatch");
  MNN::Tensor host(in,MNN::Tensor::CAFFE);
  check(host.host<float>()&&host.elementSize()==count,"input host shape");
  std::memcpy(host.host<float>(),data,count*sizeof(float));
  check(in->copyFromHostTensor(&host),"input copy failed");
  double upload=ms(t);t=Clock::now();
  ocr_trace_reset();
  auto code=s->net->runSession(s->session);
  double execute=ms(t);check(code==MNN::NO_ERROR,"runSession failed");
  std::string trace=ocr_trace_get();
  t=Clock::now();
  auto* out=s->net->getSessionOutput(s->session,s->tuning>=2?"compact":nullptr);check(out,"missing output");
  MNN::Tensor result(out,MNN::Tensor::CAFFE);
  check(out->copyToHostTensor(&result),"output copy failed");
  s->output=result.shape();
  check(s->output.size()==3&&s->output[0]==shape[0]&&s->output[2]==2,"bad output rank/batch");
  auto length=result.elementSize();check(length>0&&length<=128*1024,"output too large");
  auto values=e->NewFloatArray(length);check(values,"output JNI allocation failed");
  if(s->tuning>=2){
   auto* idx=s->net->getSessionOutput(s->session,s->indexName.c_str());
   check(idx&&idx->getType().code==halide_type_int&&idx->getType().bits==32,"integer compact index must be int32");
   auto idShape=idx->shape();
   check((idShape.size()==2||(idShape.size()==3&&idShape[2]==1))&&idShape[0]==s->output[0]&&idShape[1]==s->output[1],"integer compact index shape mismatch");
   MNN::Tensor ids(idx,MNN::Tensor::CAFFE);
   check(idx->copyToHostTensor(&ids)&&ids.elementSize()*2==length&&ids.host<int32_t>(),"integer compact index copy failed");
   std::vector<float> exact(result.host<float>(),result.host<float>()+length);
   for(int i=0;i<ids.elementSize();i++){int32_t id=ids.host<int32_t>()[i];if(id<0||id>=6625){std::ostringstream err;err<<"invalid int32 character index step="<<i<<" actual="<<id<<" compact_float_index="<<exact[2*i]<<" compact_score="<<exact[2*i+1];throw std::runtime_error(err.str());}exact[2*i]=static_cast<float>(id);}
   e->SetFloatArrayRegion(values,0,length,exact.data());
  }else e->SetFloatArrayRegion(values,0,length,result.host<float>());
  double download=ms(t);
  t=Clock::now();int cacheRc=-1;
  if(resized&&s->requested==3)cacheRc=static_cast<int>(s->net->updateCacheFile(s->session));
  double cacheMs=ms(t);
  int types[16];for(auto& x:types)x=-1;
  bool backendOK=s->net->getSessionInfo(s->session,MNN::Interpreter::BACKENDS,types);
  float memory=-1;s->net->getSessionInfo(s->session,MNN::Interpreter::MEMORY,&memory);
  std::ostringstream log;
  log<<"requested="<<s->requested<<" reported_primary="<<(backendOK?types[0]:-1)<<" selected_device="<<ocr_device_get()
     <<" gpu_tuning="<<(s->tuning==0?"FAST":"NONE")<<" precision_policy="<<(s->tuning==2?"NORMAL_FP16_STORAGE":s->tuning==3?"LOW_FP16_COMPUTE":"HIGH_FP32")<<" integer_index_readback="<<(s->tuning>=2?1:0)<<" resized="<<resized<<" resize_ms="<<resize<<" input_copy_ms="<<upload<<" run_submit_ms="<<execute
     <<" output_copy_sync_jni_ms="<<download<<" cache_update_ms="<<cacheMs<<" cache_update_rc="<<cacheRc<<" runtime_reported_memory_mb="<<memory<<" "<<trace<<" "<<ocr_io_get();
  s->info=log.str();
  return values;
 }catch(const std::exception& x){if(!e->ExceptionCheck()){std::string why=std::string(x.what())+" "+ocr_io_get();fail(e,why.c_str());}return nullptr;}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_info(JNIEnv* e,jobject,jlong h){
 try{return e->NewStringUTF(state(h)->info.c_str());}catch(const std::exception& x){fail(e,x.what());return nullptr;}
}
extern "C" JNIEXPORT jintArray JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_outputShape(JNIEnv* e,jobject,jlong h){
 try{auto& v=state(h)->output;auto r=e->NewIntArray(v.size());if(r)e->SetIntArrayRegion(r,0,v.size(),v.data());return r;}catch(const std::exception& x){fail(e,x.what());return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_close(JNIEnv*,jobject,jlong h){delete reinterpret_cast<State*>(h);}

 // Synchronous diagnostic replay, deliberately not used for normal performance.
 // Host-copy sentinel distinguishes unwritten buffers from observed NaN/Inf.
 static std::string tensorStats(MNN::Tensor* t,bool& bad){
  std::ostringstream s;
  if(!t){s<<"null_tensor";return s.str();}
  auto sh=t->shape();s<<"shape=[";for(int d:sh)s<<d<<",";s<<"] type="<<int(t->getType().code)<<":"<<int(t->getType().bits);
  if(t->usize()==0||t->usize()>32u*1024u*1024u){s<<" skip_size="<<t->usize();return s.str();}
  auto type=t->getType();
  if(type.bits!=32||(type.code!=halide_type_float&&type.code!=halide_type_int)){s<<" skip_dtype=1";return s.str();}
  std::unique_ptr<MNN::Tensor> host;
  const MNN::Tensor* v=t;
  if(!t->buffer().host){
   host.reset(new MNN::Tensor(t,MNN::Tensor::CAFFE));
   if(!host->buffer().host){s<<" host_alloc_failed";bad=true;return s.str();}
   std::memset(host->buffer().host,0xA5,host->size());
   ocr_io_reset();
   bool ok=t->copyToHostTensor(host.get());
   std::string io=ocr_io_get();s<<" copied="<<ok<<" "<<io;
   if(io.find("cl_io_errors=0 ")==std::string::npos)bad=true;
   if(!ok){bad=true;return s.str();}
   v=host.get();
  }else{s<<" host_resident=1";}
  int n=v->elementSize(),sentinel=0,nonfinite=0,zero=0;
  double mn=std::numeric_limits<double>::infinity(),mx=-mn;
  for(int i=0;i<n;i++){
   uint32_t bits;std::memcpy(&bits,v->buffer().host+4*i,4);
   if(bits==0xA5A5A5A5u){sentinel++;continue;}
   double x=type.code==halide_type_float?double(v->host<float>()[i]):double(v->host<int32_t>()[i]);
   if(!std::isfinite(x)){nonfinite++;continue;}
   mn=std::min(mn,x);mx=std::max(mx,x);if(x==0)zero++;
  }
  if(nonfinite||sentinel)bad=true;
  s<<" n="<<n<<" nonfinite="<<nonfinite<<" sentinel="<<sentinel<<" zeros="<<zero<<" min="<<mn<<" max="<<mx;
  return s.str();
 }
 extern "C" JNIEXPORT jstring JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_inspectNative(JNIEnv* e,jobject,jlong h,jobject input,jlongArray dims){
  try{
   auto s=state(h);check(e->GetArrayLength(dims)==4,"inspect rank");
   jlong raw[4];e->GetLongArrayRegion(dims,0,4,raw);
   std::vector<int> shape;size_t count=1;
   for(auto x:raw){check(x>0&&x<=4096,"inspect shape");shape.push_back((int)x);count*=x;}
   check(shape==s->shape,"inspect requires same resized shape");
   auto* data=static_cast<float*>(e->GetDirectBufferAddress(input));check(data&&e->GetDirectBufferCapacity(input)>=(jlong)count,"inspect input buffer");
   auto* in=s->net->getSessionInput(s->session,nullptr);check(in,"inspect missing input");
   MNN::Tensor host(in,MNN::Tensor::CAFFE);check(host.host<float>()&&host.elementSize()==count,"inspect input host shape");
   std::memcpy(host.host<float>(),data,count*sizeof(float));ocr_io_reset();check(in->copyFromHostTensor(&host),"inspect input copy failed");
   std::ostringstream log;log<<"ROOT_TRACE_BEGIN precision="<<s->tuning<<" same_session=1 callback_sync=1 "<<ocr_io_get()<<"\n";
   int op=0,firstBad=-1;
   auto before=[](const std::vector<MNN::Tensor*>&,const MNN::OperatorInfo*){return true;};
   auto after=[&](const std::vector<MNN::Tensor*>& tensors,const MNN::OperatorInfo* info){
    for(size_t k=0;k<tensors.size();k++){
     bool bad=false;auto stats=tensorStats(tensors[k],bad);
     if(bad&&firstBad<0)firstBad=op;
     log<<"ROOT_OP seq="<<op<<" name="<<(info?info->name():"unknown")<<" type="<<(info?info->type():"unknown")<<" output="<<k<<" "<<stats<<"\n";
    }
    op++;return true;
   };
   auto rc=s->net->runSessionWithCallBackInfo(s->session,before,after,true);
   bool finalBad=false;auto* out=s->net->getSessionOutput(s->session,s->tuning>=2?"compact":nullptr);
   log<<"ROOT_TRACE_END rc="<<int(rc)<<" callbacks="<<op<<" first_bad_seq="<<firstBad<<" compact "<<tensorStats(out,finalBad);
   if(s->tuning>=2){bool bad=false;log<<" integer_index "<<tensorStats(s->net->getSessionOutput(s->session,s->indexName.c_str()),bad);}
   return e->NewStringUTF(log.str().c_str());
  }catch(const std::exception& x){return e->NewStringUTF((std::string("ROOT_TRACE_ERROR ")+x.what()).c_str());}
 }
