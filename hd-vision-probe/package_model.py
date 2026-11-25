#!/usr/bin/env python3
"""Quantize and repackage the validated vision graphs; keep original text path byte-identical."""
import argparse, gc, hashlib, json, sys, time, tomllib
from pathlib import Path
from google.protobuf import text_format

def sha(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda:f.read(8*1024*1024),b""):h.update(b)
    return h.hexdigest()

def main():
    p=argparse.ArgumentParser()
    p.add_argument("stage",choices=["quant","pack","smoke"])
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--upstream",type=Path,required=True)
    p.add_argument("--size",type=int,required=True)
    p.add_argument("--mode",required=True)
    p.add_argument("--base",type=Path)
    p.add_argument("--model",type=Path,required=True)
    a=p.parse_args()
    o=a.out
    def report(name, data):
        (o/(name+".json")).write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n")
        print(json.dumps(data,ensure_ascii=False),flush=True)
    if a.stage=="quant":
        import numpy as np
        from PIL import Image
        from ai_edge_litert.interpreter import Interpreter
        sys.path.insert(0,str(a.upstream/"minicpmv47_work"))
        import vision_export as v
        from probe import cos_metrics
        ref=np.load(o/"native.npz")
        x=(np.asarray(Image.open(o/"fixture.png").convert("RGB").resize((a.size,a.size),Image.Resampling.BICUBIC),dtype=np.float32)/255)[None]
        for name,fn,suffix in [("vision_encoder",v.quant_int8,"int8"),("vision_adapter",v.quant_fp16,"fp16")]:
            src=o/(name+".tflite");dst=o/(name+"_"+suffix+".tflite")
            print("quantizing",name,flush=True)
            fn(str(src),str(dst));assert 0<dst.stat().st_size<src.stat().st_size
            gc.collect()
        def run(name,value,shape):
            it=Interpreter(model_path=str(o/name),num_threads=4)
            it.allocate_tensors();inp=it.get_input_details()[0];out=it.get_output_details()[0]
            assert tuple(inp["shape"])==tuple(value.shape) and tuple(out["shape"])==tuple(shape)
            it.set_tensor(inp["index"],np.ascontiguousarray(value,dtype=inp["dtype"]))
            t=time.monotonic();it.invoke()
            return it.get_tensor(out["index"]).copy(),time.monotonic()-t
        e,es=run("vision_encoder_int8.tflite",x,ref["enc"].shape)
        f,fs=run("vision_adapter_fp16.tflite",e,ref["feat"].shape)
        q=cos_metrics(f,ref["feat"])
        report("quant",{"encoder":cos_metrics(e,ref["enc"]),"end_to_end":q,"encoder_seconds":es,"adapter_seconds":fs,"status":"pass" if q["cos_mean"]>=.98 and q["cos_min"]>=.90 else "failed"})
        assert q["cos_mean"]>=.98 and q["cos_min"]>=.90,q
    elif a.stage=="pack":
        from litert_lm_builder import litertlm_builder as b
        from litert_lm_builder.runtime.proto import llm_metadata_pb2
        assert sha(a.base)=="20c73b5a1e48a315974cff1853535ff5d017100b6420d5ef221b9dd1b955dd7c"
        unpack=o/"base_sections";unpack.mkdir(exist_ok=True)
        toml=Path(b.unpack(str(a.base),str(unpack)))
        cfg=tomllib.loads(toml.read_text())
        sections=cfg["section"]
        def one(pred):
            x=[s for s in sections if pred(s)];assert len(x)==1,x;return x[0]
        md=one(lambda s:s["section_type"]=="LlmMetadata")
        enc=one(lambda s:s["section_type"]=="TFLiteModel" and s["model_type"].lower().endswith("vision_encoder"))
        adp=one(lambda s:s["section_type"]=="TFLiteModel" and s["model_type"].lower().endswith("vision_adapter"))
        assert any(s["section_type"]=="ExecutorMetadata" for s in sections)
        assert not any(s["section_type"]=="TFLiteWeights" for s in sections),"external weights require separate validation"
        mp=unpack/md["data_path"];msg=llm_metadata_pb2.LlmMetadata()
        text_format.Parse(mp.read_text(),msg)
        assert msg.llm_model_type.WhichOneof("model_type")=="fast_vlm"
        assert msg.llm_model_type.fast_vlm.image_tensor_width==448
        msg.llm_model_type.fast_vlm.image_tensor_width=a.size
        msg.llm_model_type.fast_vlm.image_tensor_height=a.size
        # Legacy published model may predate mandatory capabilities; preserve any existing values.
        for field in ["supports_thinking","supports_function_calling"]:
            if not msg.HasField(field):setattr(msg,field,False)
        mp.write_text(text_format.MessageToString(msg))
        import shutil
        keep=[s for s in sections if s not in [md,enc,adp]]
        old={s["data_path"]:sha(unpack/s["data_path"]) for s in keep}
        shutil.copyfile(o/"vision_encoder_int8.tflite",unpack/enc["data_path"])
        shutil.copyfile(o/"vision_adapter_fp16.tflite",unpack/adp["data_path"])
        a.model.parent.mkdir(parents=True,exist_ok=True)
        b.pack(str(toml),str(a.model),weights_in_model_graph=True)
        assert a.model.is_file() and 0<a.model.stat().st_size<2_000_000_000
        # Unpack again, independently compare all unchanged payloads by section identity.
        check=o/"check_sections";check.mkdir(exist_ok=True)
        newcfg=tomllib.loads(Path(b.unpack(str(a.model),str(check))).read_text())
        assert len(newcfg["section"])==len(sections)
        for before,after in zip(sections,newcfg["section"]):
            assert before["section_type"]==after["section_type"]
            assert before.get("model_type")==after.get("model_type")
            if before["data_path"] in old:
                assert sha(check/after["data_path"])==old[before["data_path"]],before
        report("bundle",{"size":a.size,"mode":a.mode,"tokens":256,"bytes":a.model.stat().st_size,"sha256":sha(a.model),"unchanged_sections":old,"status":"pass"})
        shutil.rmtree(check);shutil.rmtree(unpack)
    else:
        import litert_lm
        from PIL import Image,ImageDraw,ImageFont
        simple=o/"simple.png"
        im=Image.new("RGB",(896,896),"white")
        d=ImageDraw.Draw(im);ft=ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",80)
        d.text((100,220),"蓝星向导",font=ft,fill="black");d.text((100,420),"药品 5931",font=ft,fill="black");im.save(simple)
        def norm(t):return "".join(t.split())
        def cer(ref,pred):
            a,b=norm(ref),norm(pred);r=list(range(len(b)+1))
            for i,x in enumerate(a,1):
                nr=[i]
                for j,y in enumerate(b,1):nr.append(min(nr[-1]+1,r[j]+1,r[j-1]+(x!=y)))
                r=nr
            return r[-1]/max(1,len(a))
        def run_model(path):
            records=[]
            with litert_lm.Engine(str(path),backend=litert_lm.Backend.CPU(thread_count=6),vision_backend=litert_lm.Backend.CPU(thread_count=6),max_num_images=1) as engine:
                for img,ref in [(simple,"蓝星向导\n药品 5931"),(o/"fixture.png",(o/"reference_text.txt").read_text()),(simple,"蓝星向导\n药品 5931")]:
                    t=time.monotonic()
                    with engine.create_conversation(sampler_config=litert_lm.SamplerConfig(top_k=1,top_p=1.,temperature=0.,seed=42),thinking_config=litert_lm.ThinkingConfig(enable_thinking=False,thinking_token_budget=0),extra_context={"enable_thinking":False},automatic_tool_calling=False,max_output_tokens=512) as c:
                        ans=c.send_message({"role":"user","content":[{"type":"image","path":str(img.resolve())},{"type":"text","text":"请逐字抄写图片中所有可见文字和数字。不要解释。"}]})
                    txt="".join(x.get("text","") for x in ans.get("content",[]) if x.get("type")=="text")
                    records.append({"image":img.name,"text":txt,"cer":cer(ref,txt),"seconds":time.monotonic()-t})
                    print(json.dumps(records[-1],ensure_ascii=False),flush=True)
            return records
        baseline=run_model(a.base);new=run_model(a.model)
        # Require working OCR, same-image stability and no regression on dense synthetic test.
        ok=all("5931" in r["text"] and r["cer"]<=.25 for r in (new[0],new[2])) and norm(new[0]["text"])==norm(new[2]["text"]) and new[1]["cer"]<=baseline[1]["cer"]+.02
        report("smoke",{"baseline":baseline,"candidate":new,"status":"pass" if ok else "failed"})
        assert ok,"candidate failed OCR smoke gate"

if __name__=="__main__":main()
