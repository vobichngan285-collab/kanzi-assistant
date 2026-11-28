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
