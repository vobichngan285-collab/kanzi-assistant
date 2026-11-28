"""Apply diagnostic-only hooks to pinned MNN sources. Exact replacement or fail."""
from pathlib import Path
root=Path("mnn-src")
p=root/"source/core/Pipeline.cpp"
s=p.read_text()
hook=r'''
#include <map>
#include <sstream>
#include <string>
namespace {
thread_local std::map<std::pair<int,std::string>,long> ocr_counts;
thread_local std::string ocr_device;
thread_local std::string ocr_serialized;
}
extern "C" __attribute__((visibility("default"))) void ocr_trace_reset(){ocr_counts.clear();}
extern "C" __attribute__((visibility("default"))) const char* ocr_trace_get(){
    std::map<int,long> totals;
    std::ostringstream out;
    for(const auto& x:ocr_counts)totals[x.first.first]+=x.second;
    out<<"executed_commands_by_backend=";
    for(const auto& x:totals)out<<x.first<<":"<<x.second<<",";
    out<<" command_types=";
    for(const auto& x:ocr_counts)out<<x.first.first<<":"<<x.first.second<<":"<<x.second<<",";
    ocr_serialized=out.str();return ocr_serialized.c_str();
}
extern "C" __attribute__((visibility("default"))) void ocr_device_reset(){ocr_device.clear();}
extern "C" __attribute__((visibility("default"))) void ocr_device_set(const char* backend,const char* name){
    if(!ocr_device.empty())ocr_device+=";";
    ocr_device+=backend;ocr_device+=":";ocr_device+=name;
}
extern "C" __attribute__((visibility("default"))) const char* ocr_device_get(){return ocr_device.empty()?"unreported":ocr_device.c_str();}
'''
anchor="namespace MNN {"
assert s.count(anchor)==1
s=s.replace(anchor,hook+"\n"+anchor,1)
needle="            auto code = cmd.execution->onExecute(cmd.workInputs, cmd.workOutputs);"
# Exact non-callback loop indentation; other matches may be nested, so anchor newline.
needle="\n"+needle+"\n"
assert s.count(needle)==1
s=s.replace(needle,"\n            ocr_counts[{int(cmd.execution->backend()->type()),cmd.op?EnumNameOpType(cmd.op->type()):\"unknown\"}]++;"+needle,1)
p.write_text(s)
for name,line,backend in [
("source/backend/opencl/core/runtime/OpenCLRuntime.cpp","const std::string deviceName    = mFirstGPUDevicePtr->getInfo<CL_DEVICE_NAME>();","OpenCL"),
("source/backend/vulkan/runtime/VulkanRuntime.cpp","std::string deviceName = dev.proty().deviceName;","Vulkan")]:
    p=root/name;s=p.read_text()
    assert s.count(line)==1,name
    # Declare at file scope before includes; no dependency on MNN internal symbols.
    s='extern "C" void ocr_device_set(const char*,const char*);\n'+s.replace(line,line+'\n    ocr_device_set("'+backend+'",deviceName.c_str());',1)
    p.write_text(s)
print("Pinned MNN instrumentation applied (normal execution commands and selected GPU device only)")

# Root-cause diagnostics: record actual host/device transfer return codes.
# Keep original control flow. Thread-local counters are queried by JNI.
p=root/"source/core/Pipeline.cpp"
s=p.read_text()
anchor="namespace MNN {"
assert s.count(anchor)==1
io=r'''
namespace {
thread_local int ocr_io_reads=0, ocr_io_writes=0, ocr_io_bad=0;
thread_local std::string ocr_io_first,ocr_io_serial;
}
extern "C" __attribute__((visibility("default"))) void ocr_io_reset(){
    ocr_io_reads=ocr_io_writes=ocr_io_bad=0;ocr_io_first.clear();
}
extern "C" __attribute__((visibility("default"))) void ocr_io_note(const char* phase,int rc){
    if(phase[0]=='r')++ocr_io_reads;else ++ocr_io_writes;
    if(rc!=0){++ocr_io_bad;if(ocr_io_first.empty())ocr_io_first=std::string(phase)+":"+std::to_string(rc);}
}
extern "C" __attribute__((visibility("default"))) const char* ocr_io_get(){
    std::ostringstream s;s<<"cl_reads="<<ocr_io_reads<<" cl_writes="<<ocr_io_writes<<" cl_io_errors="<<ocr_io_bad<<" cl_first_error="<<(ocr_io_first.empty()?"none":ocr_io_first);
    ocr_io_serial=s.str();return ocr_io_serial.c_str();
}
'''
s=s.replace(anchor,io+"\n"+anchor,1);p.write_text(s)
p=root/"source/backend/opencl/core/OpenCLBackend.cpp"
s=p.read_text()
s='extern "C" void ocr_io_note(const char*,int);\n'+s
def patch_section(s,start,end,replacements):
    a=s.index(start);b=s.index(end,a+len(start))
    block=s[a:b]
    for old,new,n in replacements:
        assert block.count(old)==n,(start,old,block.count(old))
        block=block.replace(old,new)
    return s[:a]+block+s[b:]
s=patch_section(s,"void OpenCLBackend::copyFromDevice(","void CLRuntime::convertToDevice(",[
("mOpenCLRuntime->commandQueue().enqueueReadBuffer(openCLBuffer(srcTensor), CL_TRUE, 0, needSize, hostPtr);","ocr_io_note(\"read_direct\",mOpenCLRuntime->commandQueue().enqueueReadBuffer(openCLBuffer(srcTensor), CL_TRUE, 0, needSize, hostPtr));",1),
("res = mOpenCLRuntime->commandQueue().enqueueReadBuffer(*mHostBuffer.second, CL_TRUE, 0, needSize, hostPtr);","res = mOpenCLRuntime->commandQueue().enqueueReadBuffer(*mHostBuffer.second, CL_TRUE, 0, needSize, hostPtr); ocr_io_note(\"read_converted\",res);",2)
])
s=patch_section(s,"void OpenCLBackend::copyToDevice(","void OpenCLBackend::copyBetweenDevice(",[
("mOpenCLRuntime->commandQueue().enqueueWriteBuffer(openCLBuffer(dstTensor), CL_TRUE, 0, needSize, tmpPtr);","ocr_io_note(\"write_scalar\",mOpenCLRuntime->commandQueue().enqueueWriteBuffer(openCLBuffer(dstTensor), CL_TRUE, 0, needSize, tmpPtr));",1),
("mOpenCLRuntime->commandQueue().enqueueWriteBuffer(openCLBuffer(dstTensor), CL_TRUE, 0, needSize, hostPtr);","ocr_io_note(\"write_direct\",mOpenCLRuntime->commandQueue().enqueueWriteBuffer(openCLBuffer(dstTensor), CL_TRUE, 0, needSize, hostPtr));",1),
("auto res = mOpenCLRuntime->commandQueue().enqueueWriteBuffer(*mHostBuffer.second, CL_TRUE, 0, needSize, hostPtr);","auto res = mOpenCLRuntime->commandQueue().enqueueWriteBuffer(*mHostBuffer.second, CL_TRUE, 0, needSize, hostPtr); ocr_io_note(\"write_staging\",res);",1)
])
p.write_text(s)
print("OpenCL host-transfer status instrumentation applied")
