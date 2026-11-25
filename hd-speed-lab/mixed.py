#!/usr/bin/env python3
"""Screen real mixed weight quantization on 448/4x; never promote failed candidates."""
import argparse, copy, gc, hashlib, json, re, shutil, statistics, sys, time, tomllib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"hd-vision-probe"))
from probe import cos_metrics
from package_model import sha
p=argparse.ArgumentParser()
p.add_argument("stage",choices=["screen","smoke"])
p.add_argument("--out",type=Path,default=Path("report"))
p.add_argument("--base",type=Path,required=True)
p.add_argument("--model",type=Path)
a=p.parse_args()
o=a.out
BASE_SHA="0d705d8bba51bae8e10c6bfdfc727a1fcc154dd703d9d2f36e9e8bfadc19ee63"
assert sha(a.base)==BASE_SHA
def save(name,d):
    (o/(name+".json")).write_text(json.dumps(d,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(d,ensure_ascii=False),flush=True)

def fixtures():
    from PIL import Image, ImageDraw, ImageFont
    font="/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
    rows=[
      ("simple",["蓝星向导","药品 5931"],80,220,200,"white","black"),
      ("notice",["营业时间 09:30—18:00","周三暂停营业","咨询窗口 3","联系电话 12345678","请携带有效证件","费用 28.50元"],39,110,105,"#e9f4ff","#172a41"),
      ("receipt",["消费清单","商品 数量 金额","牛奶 2 19.80元","面包 1 6.50元","苹果 3 12.00元","优惠 5.00元","实付 33.30元","单号 20261125"],34,80,88,"#fffdf0","#282420")]
    for name,lines,size,y,step,bg,fg in rows:
        im=Image.new("RGB",(896,896),bg); d=ImageDraw.Draw(im); ft=ImageFont.truetype(font,size)
        for i,line in enumerate(lines):d.text((65,y+i*step),line,font=ft,fill=fg)
        im.save(o/(name+".png")); (o/(name+".txt")).write_text("\n".join(lines))
    return [(o/"simple.png",o/"simple.txt"),(o/"fixture.png",o/"reference_text.txt"),(o/"notice.png",o/"notice.txt"),(o/"receipt.png",o/"receipt.txt")]

if a.stage=="screen":
    import numpy as np
    from PIL import Image
    from ai_edge_litert.interpreter import Interpreter
    from ai_edge_quantizer import quantizer, recipe, qtyping
    from ai_edge_quantizer.utils import tfl_flatbuffer_utils as fu
    from litert_lm_builder import litertlm_builder as b
    base_dir=o/"speed_base";base_dir.mkdir(exist_ok=True)
    sections=tomllib.loads(Path(b.unpack(str(a.base),str(base_dir))).read_text())["section"]
    def take(role):
        z=[s for s in sections if s["section_type"]=="TFLiteModel" and s["model_type"].lower().endswith(role)]
        assert len(z)==1,z
        return base_dir/z[0]["data_path"]
    encbase=o/"fp16_baseline.tflite";adp=o/"selected_adapter.tflite"
    shutil.move(take("vision_encoder"),encbase);shutil.move(take("vision_adapter"),adp)
    shutil.rmtree(base_dir)
    src=o/"vision_encoder.tflite"
    # Use the quantizer's own scope function, not guessed exported layer names.
    graph=fu.read_model(src)
    fcs=[];convs=[]
    for sg in graph.subgraphs:
        for op in sg.operators:
            code=graph.operatorCodes[op.opcodeIndex]
            code=max(int(code.builtinCode),int(code.deprecatedBuiltinCode))
            if code not in [qtyping.BuiltinOperator.FULLY_CONNECTED,qtyping.BuiltinOperator.CONV_2D]:continue
            if len(op.inputs)<2 or op.inputs[1]<0:continue
            weight=sg.tensors[op.inputs[1]]
            shape=tuple(int(v) for v in weight.shape)
            scope=fu.get_op_scope(op,sg.tensors)
            item={"scope":scope,"shape":shape}
            if code==qtyping.BuiltinOperator.FULLY_CONNECTED and len(shape)==2 and int(np.prod(shape))>=1024:fcs.append(item)
            elif code==qtyping.BuiltinOperator.CONV_2D:convs.append(item)
    del graph;gc.collect()
    assert len(fcs)>=100 and len(set(x["scope"] for x in fcs))==len(fcs) and all(x["scope"] for x in fcs)
    save("scope_inventory",{"fc":fcs,"conv":convs})
    images=[np.asarray(Image.open(img).convert("RGB").resize((448,448),Image.Resampling.BICUBIC),dtype=np.float32)[None].copy()/255 for img,_ in fixtures()]
    def session(path):
        it=Interpreter(model_path=str(path),num_threads=6)
        it.allocate_tensors();inp=it.get_input_details()[0];out=it.get_output_details()[0]
        def infer(x):
            assert tuple(inp["shape"])==tuple(x.shape)
            it.set_tensor(inp["index"],np.ascontiguousarray(x,dtype=inp["dtype"]))
            t=time.monotonic();it.invoke();s=time.monotonic()-t
            return it.get_tensor(out["index"]).copy(),s
        return infer
    def measure(path):
        e=session(path);d=session(adp)
        e(images[0]) # warm, excluded
        times=[];adapter_times=[];features=[]
        for im in images:
            ee,es=e(im);ff,fs=d(ee)
            assert ee.shape==(1,1024,1152) and ff.shape==(1,256,1024)
            times.append(es);adapter_times.append(fs);features.append(ff)
        del e,d;gc.collect()
        return features,times,adapter_times
    bf,bt,ba=measure(encbase)
    native=np.load(o/"native.npz")
    save("baseline_vision",{"bytes":encbase.stat().st_size,"encoder_seconds":bt,"adapter_seconds":ba,"native_fixture":cos_metrics(bf[1],native["feat"])})
    dr=copy.deepcopy(recipe.dynamic_wi8_afp32()[0])
    half=copy.deepcopy(dr);half["algorithm_key"]="float_casting"
    half["op_config"]["weight_tensor_config"]={"num_bits":16,"symmetric":True,"granularity":"TENSORWISE","dtype":"FLOAT"}
    half["op_config"]["compute_precision"]="FLOAT";half["op_config"]["explicit_dequantize"]=False
    half_fc=copy.deepcopy(half);half_fc["operation"]="FULLY_CONNECTED"
    half_conv=copy.deepcopy(half);half_conv["operation"]="CONV_2D"
    # FCs in actual graph order, not assumed to be equal numbers per block.
    n=len(fcs)
    mlp=[x for x in fcs if 4304 in x["shape"]]
    attn=[x for x in fcs if 4304 not in x["shape"]]
    plans=[
        ("mlp",mlp,False),("attention",attn,False),
        ("middle",fcs[n//6:5*n//6],False),("late_half",fcs[n//2:],False),
        ("early_half",fcs[:n//2],False),("middle_third",fcs[n//3:2*n//3],False),
        ("mlp_weight_only",mlp,True)]
    results=[];accepted=[]
    for name,group,weight_only in plans:
        if not group:continue
        path=o/(name+".tflite")
        try:
            rule=copy.deepcopy(dr);rule["operation"]="FULLY_CONNECTED"
            rule["regex"]="^(?:"+"|".join(re.escape(x["scope"]) for x in group)+")$"
            if weight_only:
                rule["op_config"]["compute_precision"]="FLOAT"
                rule["op_config"]["explicit_dequantize"]=True
            rules=[half_fc,half_conv,rule]
            quantizer.Quantizer(str(src),rules).quantize().export_model(str(path))
            # Verify the output really contains both integer and float16 weights.
            g=fu.read_model(path)
            types={}
            for sg in g.subgraphs:
                for t in sg.tensors:
                    if len(t.shape)>=2 and t.buffer>0 and g.buffers[t.buffer].data is not None and len(g.buffers[t.buffer].data)>0:
                        k=str(int(t.type));types[k]=types.get(k,0)+1
            del g;gc.collect()
            assert types.get(str(int(qtyping.TensorType.INT8)),0)>0 and types.get(str(int(qtyping.TensorType.FLOAT16)),0)>0,types
            ff,tt,aa=measure(path)
            metrics=[cos_metrics(x,y) for x,y in zip(ff,bf)]
            nr=cos_metrics(ff[1],native["feat"])
            ratio=statistics.median(tt)/statistics.median(bt)
            ok=(all(m["cos_mean"]>=.995 and m["cos_min"]>=.95 for m in metrics) and nr["cos_mean"]>=.995 and nr["cos_min"]>=.95 and ratio<=.90 and path.stat().st_size<encbase.stat().st_size)
            rec={"name":name,"precision":"int8/fp16-"+name,"selected_fc":len(group),"all_fc":n,"weight_types":types,"metrics":metrics,"native_fixture":nr,"encoder_seconds":tt,"adapter_seconds":aa,"ratio":ratio,"bytes":path.stat().st_size,"status":"pass" if ok else "failed"}
            results.append(rec)
            if ok:
                accepted.append(rec);accepted.sort(key=lambda x:x["ratio"])
                for x in accepted[3:]:(o/(x["name"]+".tflite")).unlink(missing_ok=True)
                accepted=accepted[:3]
            else:path.unlink(missing_ok=True)
        except Exception as exc:
            results.append({"name":name,"status":"error","error":str(exc)[:1800]})
            path.unlink(missing_ok=True)
        save("mixed_trials",{"baseline":bt,"trials":results,"accepted":accepted})
    assert accepted,"no mixed precision candidate met all quality, size and speed gates"
    (o/"candidate_names.txt").write_text("\n".join(x["name"] for x in accepted)+"\n")
    encbase.unlink();src.unlink();(o/"vision_adapter.tflite").unlink(missing_ok=True)
else:
    import litert_lm
    def norm(t):return "".join(t.split())
    def cer(ref,pred):
        x,y=norm(ref),norm(pred);r=list(range(len(y)+1))
        for i,k in enumerate(x,1):
            nr=[i]
            for j,v in enumerate(y,1):nr.append(min(nr[-1]+1,r[j]+1,r[j-1]+(k!=v)))
            r=nr
        return r[-1]/max(1,len(x))
    fs=fixtures()
    # Both engines are tested sequentially on this runner, same inputs and sampling.
    def run_model(path):
        rec=[]
        with litert_lm.Engine(str(path),backend=litert_lm.Backend.CPU(thread_count=6),vision_backend=litert_lm.Backend.CPU(thread_count=6),max_num_images=1) as e:
            for i,(img,ref) in enumerate([fs[0],*fs,*fs]):
                t=time.monotonic();first=None;answer=""
                with e.create_conversation(sampler_config=litert_lm.SamplerConfig(top_k=1,top_p=1.,temperature=0.,seed=42),thinking_config=litert_lm.ThinkingConfig(enable_thinking=False,thinking_token_budget=0),extra_context={"enable_thinking":False},automatic_tool_calling=False,max_output_tokens=512) as c:
                    for msg in c.send_message_async({"role":"user","content":[{"type":"image","path":str(img.resolve())},{"type":"text","text":"请逐字抄写图片中所有可见文字和数字。不要解释。"}]}):
                        delta="".join(x.get("text","") for x in msg.get("content",[]) if x.get("type")=="text")
                        answer+=delta
                        if first is None and answer.strip():first=time.monotonic()-t
                assert first is not None and answer.strip()
                x={"image":img.name,"warmup":i==0,"text":answer,"cer":cer(ref.read_text(),answer),"first_seconds":first,"total_seconds":time.monotonic()-t}
                rec.append(x);print(json.dumps(x,ensure_ascii=False),flush=True)
        return rec[1:]
    baseline=run_model(a.base);candidate=run_model(a.model)
    base_first=statistics.median(x["first_seconds"] for x in baseline)
    cand_first=statistics.median(x["first_seconds"] for x in candidate)
    base_total=statistics.median(x["total_seconds"] for x in baseline)
    cand_total=statistics.median(x["total_seconds"] for x in candidate)
    quality=all(x["image"]==y["image"] and y["cer"]<=x["cer"]+.01 for x,y in zip(baseline,candidate))
    stable=all(norm(candidate[i]["text"])==norm(candidate[i+4]["text"]) for i in range(4))
    simple=all(x["cer"]==0 for x in candidate if x["image"]=="simple.png")
    ok=quality and stable and simple and cand_first<=base_first*.85 and cand_total<=base_total*.90
    save("smoke",{"baseline":baseline,"candidate":candidate,"median_first":[base_first,cand_first],"median_total":[base_total,cand_total],"quality":quality,"stable":stable,"simple":simple,"status":"pass" if ok else "failed"})
    assert ok,"end-to-end accuracy, repeatability or speed gate failed"
