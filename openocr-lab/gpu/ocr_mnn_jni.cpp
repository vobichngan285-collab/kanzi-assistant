#include <jni.h>
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <chrono>
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
using Clock=std::chrono::steady_clock;
static double ms(Clock::time_point t){return std::chrono::duration<double,std::milli>(Clock::now()-t).count();}
struct State{
 MNN::Interpreter* net=nullptr;
 MNN::Session* session=nullptr;
 std::vector<int> shape,output;
 int requested=0;
 std::string info, probe;
 MNN::BackendConfig cfg;
 ~State(){if(net){if(session)net->releaseSession(session);MNN::Interpreter::destroy(net);}}
};
static void check(bool b,const char* s){if(!b)throw std::runtime_error(s);}
// Read-only capability probe through the same process' vendor library namespace.
 // OpenCL C ABI: cl_int=int32, cl_uint=uint32, cl_device_type=cl_ulong.
 // No context/queue/kernel creation; MNN still owns the execution runtime.
static std::string probeOpenCL(){
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
extern "C" JNIEXPORT jlong JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_open(JNIEnv* e,jobject,jstring path,jint backend,jint threads,jstring cachePath){
 try{
  check(backend==0||backend==3||backend==7,"invalid backend");
  const char* p=e->GetStringUTFChars(path,nullptr);check(p!=nullptr,"path");
  std::string file(p);e->ReleaseStringUTFChars(path,p);
  auto s=std::make_unique<State>();s->requested=backend;
  if(backend==3)s->probe=probeOpenCL();
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
  s->cfg.precision=MNN::BackendConfig::Precision_High;sc.backendConfig=&s->cfg;
  if(backend==0)sc.numThread=threads;
  else if(backend==3)sc.mode=MNN_GPU_TUNING_FAST|MNN_GPU_MEMORY_BUFFER;
  else sc.mode=MNN_GPU_TUNING_NONE;
  s->session=s->net->createSession(sc);
  if(!s->session)throw std::runtime_error("GPU_SESSION_CREATE_FAILED "+s->probe);
  requireRequestedGpu(s.get(),"createSession");
  s->info="requested="+std::to_string(backend)+" actual="+std::to_string(primaryBackend(s.get()))+" MNN="+MNN::getVersion()+" device="+ocr_device_get()+" "+s->probe;
  return reinterpret_cast<jlong>(s.release());
 }catch(const std::exception& x){fail(e,x.what());return 0;}
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_run(JNIEnv* e,jobject,jlong handle,jobject input,jlongArray dims){
 try{
  auto s=state(handle);check(e->GetArrayLength(dims)==4,"expected rank4");
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
  auto* out=s->net->getSessionOutput(s->session,nullptr);check(out,"missing output");
  MNN::Tensor result(out,MNN::Tensor::CAFFE);
  check(out->copyToHostTensor(&result),"output copy failed");
  s->output=result.shape();
  check(s->output.size()==3&&s->output[0]==shape[0]&&s->output[2]==2,"bad output rank/batch");
  auto length=result.elementSize();check(length>0&&length<=128*1024,"output too large");
  auto values=e->NewFloatArray(length);check(values,"output JNI allocation failed");
  e->SetFloatArrayRegion(values,0,length,result.host<float>());
  double download=ms(t);
  t=Clock::now();int cacheRc=-1;
  if(resized&&s->requested==3)cacheRc=static_cast<int>(s->net->updateCacheFile(s->session));
  double cacheMs=ms(t);
  int types[16];for(auto& x:types)x=-1;
  bool backendOK=s->net->getSessionInfo(s->session,MNN::Interpreter::BACKENDS,types);
  float memory=-1;s->net->getSessionInfo(s->session,MNN::Interpreter::MEMORY,&memory);
  std::ostringstream log;
  log<<"requested="<<s->requested<<" reported_primary="<<(backendOK?types[0]:-1)<<" selected_device="<<ocr_device_get()
     <<" resized="<<resized<<" resize_ms="<<resize<<" input_copy_ms="<<upload<<" run_submit_ms="<<execute
     <<" output_copy_sync_jni_ms="<<download<<" cache_update_ms="<<cacheMs<<" cache_update_rc="<<cacheRc<<" runtime_reported_memory_mb="<<memory<<" "<<trace;
  s->info=log.str();
  return values;
 }catch(const std::exception& x){if(!e->ExceptionCheck())fail(e,x.what());return nullptr;}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_info(JNIEnv* e,jobject,jlong h){
 try{return e->NewStringUTF(state(h)->info.c_str());}catch(const std::exception& x){fail(e,x.what());return nullptr;}
}
extern "C" JNIEXPORT jintArray JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_outputShape(JNIEnv* e,jobject,jlong h){
 try{auto& v=state(h)->output;auto r=e->NewIntArray(v.size());if(r)e->SetIntArrayRegion(r,0,v.size(),v.data());return r;}catch(const std::exception& x){fail(e,x.what());return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_com_vobich_bluestar_openocrlab_NativeMnn_close(JNIEnv*,jobject,jlong h){delete reinterpret_cast<State*>(h);}
