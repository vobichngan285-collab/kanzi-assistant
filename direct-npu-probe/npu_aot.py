"""Use vendor AOT compiler only. A successful compile is not device execution."""
import pathlib, json, hashlib, sys
from ai_edge_litert.aot import aot_compile
from ai_edge_litert.aot.vendors.mediatek import target
root=pathlib.Path("unpacked")
report=pathlib.Path("npu-report");report.mkdir(exist_ok=True)
assert (root/"model.toml").is_file(),"unpack missing model.toml"
models=list(root.rglob("*.tflite"))
# Only vision encoder, never accidentally compile a text decoder or adapter.
matches=[p for p in models if "vision_encoder" in p.name.lower()]
print("Sections:",[(p.name,p.stat().st_size) for p in models],flush=True)
assert len(matches)==1, "require one explicitly named vision_encoder TFLite section"
p=matches[0]
with p.open("rb") as f:sha=hashlib.file_digest(f,"sha256").hexdigest()
(report/"input.json").write_text(json.dumps({"section":p.name,"bytes":p.stat().st_size,"sha256":sha,"target":"MT6991","scope":"vision encoder only; not adapter; no device run"},indent=2))
# Official LiteRT AOT example uses target=[Target(SocModel....)].
compiled=aot_compile.aot_compile(str(p),target=[target.Target(target.SocModel.MT6991)],keep_going=False)
summary=str(compiled.compilation_report())
print(summary,flush=True)
(report/"compilation_report.txt").write_text(summary)
compiled.export("compiled-npu",model_name="minicpm_vision")
files=[{"name":str(p.relative_to("compiled-npu")),"size":p.stat().st_size} for p in pathlib.Path("compiled-npu").rglob("*") if p.is_file()]
(report/"outputs.json").write_text(json.dumps(files,indent=2))
print("Compiled outputs (not proof of NPU coverage):",files,flush=True)
assert files
