"""Apply a narrowly scoped patch to pinned LiteRT-LM source. No model edits."""
from pathlib import Path
import sys, subprocess
root=Path(sys.argv[1])
assert subprocess.check_output(["git","-C",str(root),"rev-parse","HEAD"],text=True).strip()=="bda624851d74be34d4e1f7fac1fc4155939539c7"
p=root/"runtime/executor/vision_litert_compiled_model_executor.cc"
s=p.read_text()
for component in ("Encoder","Adapter"):
    start=s.index("absl::Status VisionLiteRtCompiledModelExecutor::Vision"+component+"::Initialize(")
    gpu=s.index("case Backend::GPU: {",start)
    end=s.index("      break;",gpu)
    old=s[gpu:end]
    assert "GetOptions<::litert::GpuOptions>()" in old
    if component=="Encoder":
        assert old.count("options.SetHardwareAccelerators(litert::HwAccelerators::kGpu);")==1
        old=old.replace("      options.SetHardwareAccelerators(litert::HwAccelerators::kGpu);\n","")
    else:
        assert "SetHardwareAccelerators" not in old
    add="""      LITERT_ASSIGN_OR_RETURN(auto& cpu_options,
                              options.GetOptions<::litert::CpuOptions>());
      ABSL_RETURN_IF_ERROR(SetCpuOptions(vision_executor_settings_, cpu_options));
      ABSL_RETURN_IF_ERROR(SetCpuCacheOptions(
          weight_cache_file, VisionExecutorSettings::k"""+component+"""Name,
          cpu_options));
      options.SetHardwareAccelerators(litert::HwAccelerators::kGpu |
                                      litert::HwAccelerators::kCpu);
      ABSL_LOG(INFO) << "BLUESTAR_VISION_HYBRID_014 """+component+""" requested GPU+CPU";
"""
    s=s[:gpu]+old+add+s[end:]
# Log completion of map-based encoder dispatch; not a claim of GPU partition coverage.
needle="""    LITERT_RETURN_IF_ERROR(vision_encoder_->GetCompiledModel().Run(
        encoder_signature_index, encoder_input_buffers,
        encoder_output_buffers));"""
assert s.count(needle)==1
s=s.replace(needle,needle+'\n    ABSL_LOG(INFO) << "BLUESTAR_VISION_HYBRID_014 encoder run complete signature_index=" << encoder_signature_index;')
p.write_text(s)
print(subprocess.check_output(["git","-C",str(root),"diff","--",str(p.relative_to(root))],text=True))
