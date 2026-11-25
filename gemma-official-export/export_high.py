from pathlib import Path
import json
import os
from huggingface_hub import snapshot_download
from litert_torch.generative.export_hf.export import export

source=snapshot_download("google/gemma-4-E2B-it-qat-mobile-transformers",revision="dd693ff40353f057ca5f07e945ad867f4afbf2ec",local_dir="checkpoint")
# Preserve the official QAT checkpoint; do not apply a second text quantization recipe.
config=json.loads(Path(source,"config.json").read_text())
print("source",source,"quantization",config.get("quantization_config"),flush=True)
assert config.get("quantization_config"),"Expected official QAT checkpoint"
assert config["quantization_config"].get("quant_method")=="gemma"
kwargs=dict(model=source,output_dir="converted",task="image_text_to_text",target="cpu_gpu",
            bundle_litert_lm=True,externalize_embedder=True,single_token_embedder=True,
            export_vision_encoder=True,export_audio_encoder=False,cache_length=4096,
            prefill_lengths=[128,512,1024],quantization_recipe=None,
            vision_encoder_quantization_recipe="dynamic_wi8_afp32",
            experimental_lightweight_conversion=True,
            litert_lm_llm_metadata_override="none",
            jinja_chat_template_override="litert-community/gemma-4-E2B-it-litert-lm",
            gemma4_vision_token_budgets="280,560,1120")
print("export plan",kwargs,flush=True)
export(**kwargs)
files=list(Path("converted").rglob("*.litertlm"))
assert len(files)==1,files
Path("model_path.txt").write_text(str(files[0]))
