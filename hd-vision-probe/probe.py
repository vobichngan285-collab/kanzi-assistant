#!/usr/bin/env python3
"""Host-only validation of fixed-size MiniCPM-V4.7 vision exports.
Do not deploy or publish weights until parity and whole-bundle compatibility pass.
Upstream pure Torch converter is loaded at an immutable commit by CI.
"""
import argparse, gc, hashlib, importlib.util, json, math, os, sys, time
from pathlib import Path

def dump(path, obj):
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False)+"\n")

def load_converter(upstream, size, mode):
    # Set all size-derived constants before class/function definition. For 4x
    # skip the same intermediate merger as HF; keep the final adapter weights.
    path = upstream/"minicpmv47_work/mv47_vision.py"
    code = path.read_text()
    def change(a,b):
        nonlocal code
        if code.count(a)!=1: raise RuntimeError("upstream pattern mismatch: "+a)
        code = code.replace(a,b)
    change("IMG = 448", f"IMG = {size}")
    if mode=="4x":
        change("WGRID = GRID // 2", "WGRID = GRID")
        change("    h = self._vit_merger(h, calib)", "    # 4x HF mode skips intermediate vit_merger")
    spec = importlib.util.spec_from_loader("mv47_vision", loader=None)
    mod = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = mod
    exec(compile(code,str(path),"exec"), mod.__dict__)
    assert mod.GRID==size//14
    assert mod.N_TOK==(size//14)**2//(4 if mode=="4x" else 16)
    return mod

def fixture(out):
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    path=out/"fixture.png"
    # Same synthetic source for all modes; never use private user photos.
    im=Image.new("RGB",(896,896),"#fff7e3")
    draw=ImageDraw.Draw(im)
    fontpath="/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
    if not Path(fontpath).is_file(): raise FileNotFoundError(fontpath)
    title=ImageFont.truetype(fontpath,40)
    font=ImageFont.truetype(fontpath,24)
    draw.rectangle((35,30,861,866),outline="#bf2525",width=5)
    draw.text((62,60),"价目表 PRICE LIST",font=title,fill="#9a1212")
    rows=["中医推拿一小时 90元","半小时 50元","精油开背50分钟 120元","肠胃调理50分钟 120元","肾部保养40分钟 120元","艾灸养生 120元","刺络放血 120元","经络刮痧 60元","滑罐走罐 60元","拔罐热敷 40元","充1000送200 充2000送600","编号 4827  价格 28.50元"]
    for i,row in enumerate(rows): draw.text((72,140+i*55),row,font=font,fill="#2c1914")
    im.save(path)
    (out/"reference_text.txt").write_text("价目表 PRICE LIST\n"+"\n".join(rows)+"\n")
    return path

def sample(out, size):
    import numpy as np
    import torch
    from PIL import Image
    im=Image.open(out/"fixture.png").convert("RGB").resize((size,size),Image.Resampling.BICUBIC)
    return torch.from_numpy((np.asarray(im,dtype=np.float32)/255)[None].copy())

def cos_metrics(a,b):
    import numpy as np
    a=np.asarray(a,dtype=np.float64);b=np.asarray(b,dtype=np.float64)
    if a.shape!=b.shape or not np.isfinite(a).all() or not np.isfinite(b).all():
        raise RuntimeError("invalid shape or nonfinite features")
    aa=a.reshape(-1,a.shape[-1]);bb=b.reshape(aa.shape)
    cos=(aa*bb).sum(-1)/(np.linalg.norm(aa,axis=-1)*np.linalg.norm(bb,axis=-1)+1e-30)
    return {"shape":list(a.shape),"cos_mean":float(cos.mean()),"cos_min":float(cos.min()),"max_abs":float(np.abs(a-b).max())}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("stage",choices=["reference","export"])
    p.add_argument("--src",type=Path,required=True)
    p.add_argument("--upstream",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--size",type=int,choices=[448,896],required=True)
    p.add_argument("--mode",choices=["4x","16x"],required=True)
    a=p.parse_args()
    import torch
    import numpy as np
    torch.set_num_threads(4)
    torch.set_grad_enabled(False)
    a.out.mkdir(parents=True,exist_ok=True)
    stage_path=a.out/(a.stage+".json")
    report={"stage":a.stage,"size":a.size,"mode":a.mode,"torch":torch.__version__,"status":"running"}
    dump(stage_path,report)
    def checkpoint(name,**kw):
        report.update(phase=name,**kw);dump(stage_path,report);print(json.dumps(report),flush=True)
    grid=a.size//14
    tokens=grid**2//(4 if a.mode=="4x" else 16)
    expected_enc=(1,grid**2//(1 if a.mode=="4x" else 4),1152)
    expected_adp=(1,tokens,1024)
    try:
        if a.stage=="reference":
            from transformers import MiniCPMV4_7Config
            from transformers.models.minicpmv4_7.modeling_minicpmv4_7 import MiniCPMV4_7VisionModel, MiniCPMV4_7Merger
            from safetensors import safe_open
            import transformers
            fixture(a.out)
            x=sample(a.out,a.size)
            # Exact upstream patch-strip convention; bypass image processor's
            # normal dynamic tiling so each arm tests ONE fixed square.
            mod=load_converter(a.upstream,a.size,a.mode)
            strip=mod.strip_from_image(x)
            ts=torch.tensor([[grid,grid]],dtype=torch.long)
            checkpoint("load_hf",transformers=transformers.__version__)
            cfg=MiniCPMV4_7Config.from_pretrained(str(a.src))
            cfg.vision_config._attn_implementation="eager"
            # Instantiate only native vision modules on meta; assign original
            # weights directly, without allocating text decoder or duplicate FP32 weights.
            with torch.device("meta"):
                vt=MiniCPMV4_7VisionModel(cfg.vision_config)
                merger=MiniCPMV4_7Merger(cfg)
            with safe_open(str(a.src/"model.safetensors"),framework="pt",device="cpu") as weights:
                for prefix,module in (("model.vision_tower.",vt),("model.merger.",merger)):
                    state={k[len(prefix):]:weights.get_tensor(k).float() for k in weights.keys() if k.startswith(prefix)}
                    module.load_state_dict(state,strict=True,assign=True)
                    del state
            vt.eval();merger.eval()
            gc.collect()
            checkpoint("native_vision")
            start=time.monotonic()
            with torch.inference_mode():
                result=vt(strip,target_sizes=ts,use_vit_merger=a.mode!="4x")
                features=merger(result.last_hidden_state,ts if a.mode=="4x" else ts//2)
            enc=result.last_hidden_state.detach().float().cpu().numpy()
            feat=features[0].detach().float().cpu().numpy()[None]
            assert enc.shape==expected_enc,(enc.shape,expected_enc)
            assert feat.shape==expected_adp,(feat.shape,expected_adp)
            assert np.isfinite(enc).all() and np.isfinite(feat).all()
            np.savez(a.out/"native.npz",enc=enc,feat=feat)
            checkpoint("done",status="pass",vision_seconds=time.monotonic()-start,enc_shape=list(enc.shape),adapter_shape=list(feat.shape),tokens=tokens)
        else:
            import litert_torch
            import importlib.metadata as md
            mod=load_converter(a.upstream,a.size,a.mode)
            # Use fp32 parity first, without size-specific fp16 calibration.
            # This is not a mobile-ready quantization recipe.
            opts=mod.Opts(barrier=False,fp16safe=False)
            x=sample(a.out,a.size)
            ref=np.load(a.out/"native.npz")
            checkpoint("load_reauthored",versions={k:md.version(k) for k in ["litert-torch","litert-converter","ai-edge-litert","jax","jaxlib"]})
            enc,adp,_=mod.build(str(a.src),opts)
            checkpoint("eager")
            start=time.monotonic()
            with torch.no_grad():
                e=enc(x); f=adp(e)
            assert tuple(e.shape)==expected_enc and tuple(f.shape)==expected_adp
            eager={"encoder":cos_metrics(e.numpy(),ref["enc"]),"end_to_end":cos_metrics(f.numpy(),ref["feat"])}
            checkpoint("eager_parity",eager=eager,eager_seconds=time.monotonic()-start,tokens=tokens)
            assert eager["end_to_end"]["cos_mean"]>=0.999 and eager["end_to_end"]["cos_min"]>=0.98,eager
            paths=[a.out/"vision_encoder.tflite",a.out/"vision_adapter.tflite"]
            # Export one module at a time; do not publish weights.
            checkpoint("export_encoder")
            litert_torch.convert(enc,(x,)).export(str(paths[0]))
            del enc;gc.collect()
            checkpoint("export_adapter")
            litert_torch.convert(adp,(e,)).export(str(paths[1]))
            del adp,e,f;gc.collect()
            from ai_edge_litert.interpreter import Interpreter
            def run(path,value,shape):
                it=Interpreter(model_path=str(path),num_threads=4)
                it.allocate_tensors()
                inp=it.get_input_details()[0];out=it.get_output_details()[0]
                assert tuple(inp["shape"])==tuple(value.shape)
                assert tuple(out["shape"])==shape
                it.set_tensor(inp["index"],np.ascontiguousarray(value,dtype=inp["dtype"]))
                start=time.monotonic();it.invoke();seconds=time.monotonic()-start
                return it.get_tensor(out["index"]).copy(),seconds
            checkpoint("tflite_encoder")
            ee,es=run(paths[0],x.numpy(),expected_enc)
            checkpoint("tflite_adapter",encoder_seconds=es)
            ff,fs=run(paths[1],ee,expected_adp)
            metrics={"encoder":cos_metrics(ee,ref["enc"]),"end_to_end":cos_metrics(ff,ref["feat"])}
            checkpoint("tflite_parity",tflite=metrics,adapter_seconds=fs,files=[{"name":p.name,"bytes":p.stat().st_size} for p in paths])
            assert metrics["end_to_end"]["cos_mean"]>=0.999 and metrics["end_to_end"]["cos_min"]>=0.98,metrics
            checkpoint("done",status="pass")
    except BaseException as exc:
        checkpoint(report.get("phase","setup"),status="failed",error=type(exc).__name__,message=str(exc)[:2000])
        raise

if __name__=="__main__":main()
