#!/usr/bin/env python3
"""Export official FAST-T MSRA-TD500-512 weights to raw-logit ONNX on CPU.

Official repo is only cloned in the temporary CI checkout. We reconstruct the
same named inference modules (without the legacy CUDA postprocessor/MMCV) using
the official, unmodified NAS layer classes, then verify torch/ONNX outputs.
No user or Android code changed, no APK generated.
"""
import argparse, copy, hashlib, importlib.util, json, sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
import torch.nn as nn
import torch.nn.functional as F

class TextBackbone(nn.Module):
    def __init__(self,nas,c):
        super().__init__()
        mk=lambda x:nas.set_layer_from_config(copy.deepcopy(x))
        self.first_conv=mk(c["first_conv"])
        for key in ("stage1","stage2","stage3","stage4"):
            setattr(self,key,nn.ModuleList([mk(z) for z in c[key]]))
    def forward(self,x):
        x=self.first_conv(x);outs=[]
        for key in ("stage1","stage2","stage3","stage4"):
            for layer in getattr(self,key):x=layer(x)
            outs.append(x)
        return outs

class TextNeck(nn.Module):
    def __init__(self,nas,c):
        super().__init__()
        for key in ("reduce_layer1","reduce_layer2","reduce_layer3","reduce_layer4"):
            setattr(self,key,nas.set_layer_from_config(copy.deepcopy(c[key])))
    def forward(self,outs):
        a,b,c,d=outs
        a=self.reduce_layer1(a)
        b=F.interpolate(self.reduce_layer2(b),size=a.shape[-2:],mode="bilinear")
        c=F.interpolate(self.reduce_layer3(c),size=a.shape[-2:],mode="bilinear")
        d=F.interpolate(self.reduce_layer4(d),size=a.shape[-2:],mode="bilinear")
        return torch.cat((a,b,c,d),dim=1)

class TextHead(nn.Module):
    def __init__(self,nas,c):
        super().__init__()
        self.conv=nas.set_layer_from_config(copy.deepcopy(c["conv"]))
        if c.get("blocks"):self.blocks=nn.ModuleList([nas.set_layer_from_config(copy.deepcopy(z)) for z in c["blocks"]])
        else:self.blocks=None
        self.final=nas.set_layer_from_config(copy.deepcopy(c["final"]))
    def forward(self,x):
        x=self.conv(x)
        if self.blocks is not None:
            for layer in self.blocks:x=layer(x)
        return self.final(x)

class RawFAST(nn.Module):
    def __init__(self,nas,c):
        super().__init__()
        self.backbone=TextBackbone(nas,c)
        self.neck=TextNeck(nas,c["neck"])
        self.det_head=TextHead(nas,c["head"])
    def forward(self,imgs):
        return self.det_head(self.neck(self.backbone(imgs)))

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--fast-repo",required=True)
    p.add_argument("--checkpoint",required=True)
    p.add_argument("--out",required=True)
    a=p.parse_args()
    torch.set_num_threads(2)
    root=Path(a.fast_repo)
    out=Path(a.out);out.parent.mkdir(exist_ok=True,parents=True)
    ck=Path(a.checkpoint)
    digest=hashlib.sha256(ck.read_bytes()).hexdigest()
    print("OFFICIAL_FAST_T_MSRA_CHECKPOINT_SHA256",digest,"bytes",ck.stat().st_size,flush=True)
    spec=importlib.util.spec_from_file_location("fast_nas_utils",root/"models/utils/nas_utils.py")
    nas=importlib.util.module_from_spec(spec);sys.modules[spec.name]=nas;spec.loader.exec_module(nas)
    config=json.loads((root/"config/fast/nas-configs/fast_tiny.config").read_text())
    model=RawFAST(nas,config)
    checkpoint=torch.load(ck,map_location="cpu",weights_only=True)
    print("FAST_CHECKPOINT_KEYS",list(checkpoint.keys())[:16],flush=True)
    weights=checkpoint.get("ema",checkpoint.get("state_dict",checkpoint))
    weights={k.removeprefix("module."):v for k,v in weights.items()}
    load=model.load_state_dict(weights,strict=False)
    print("FAST_LOAD_MISSING",list(load.missing_keys)[:25],"COUNT",len(load.missing_keys),flush=True)
    print("FAST_LOAD_UNEXPECTED",list(load.unexpected_keys)[:25],"COUNT",len(load.unexpected_keys),flush=True)
    if load.missing_keys or load.unexpected_keys:raise RuntimeError("Checkpoint architecture mismatch: refuse export")
    model.eval()
    with torch.no_grad():
        for module in model.modules():
            if hasattr(module,"switch_to_deploy"):
                module.switch_to_deploy()
    model.eval()
    x=torch.randn((1,3,512,512))
    with torch.no_grad():torch_out=model(x).detach().numpy()
    print("FAST_TORCH_OUTPUT",torch_out.shape,float(torch_out.min()),float(torch_out.max()),flush=True)
    if tuple(torch_out.shape)!=(1,5,128,128):raise RuntimeError("Unexpected raw FAST output shape")
    torch.onnx.export(model,x,str(out),export_params=True,
          opset_version=17,do_constant_folding=True,
          input_names=["image"],output_names=["fast_logits"],
          dynamic_axes={"image":{2:"height",3:"width"},
                        "fast_logits":{2:"out_height",3:"out_width"}},
          dynamo=False)
    so=ort.SessionOptions();so.intra_op_num_threads=2
    session=ort.InferenceSession(str(out),so,providers=["CPUExecutionProvider"])
    actual=session.run(None,{"image":x.numpy()})[0]
    error=float(np.max(np.abs(torch_out-actual)))
    print("FAST_T_ONNX_PARITY",json.dumps({"shape":list(actual.shape),
           "max_abs_error":error,"onnx_bytes":out.stat().st_size,
           "onnx_sha256":hashlib.sha256(out.read_bytes()).hexdigest()}),flush=True)
    if error>0.015:raise RuntimeError("ONNX model differs too much from official torch")
    # Confirm dynamic aspect-ratio support for production-like photos.
    t=np.zeros((1,3,512,768),dtype=np.float32)
    result=session.run(None,{"image":t})[0]
    if result.shape!=(1,5,128,192):raise RuntimeError(f"Non-square ONNX inference failed {result.shape}")
    print("FAST_T_MSRA_ONNX_CPU_EXPORT_PASS",flush=True)

if __name__=="__main__":main()
