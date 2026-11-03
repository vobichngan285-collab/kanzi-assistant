#!/usr/bin/env python3
"""Process-isolated ONNX cold-load, dual residency and CPU timing feasibility.

Run the two configurations in SEPARATE Linux processes:
 small: 1 original detector + 1 Small recognizer
 hybrid: the SAME detector + Small recognizer + Medium recognizer

Loads from model BYTES, matching Java SDK's readBytes() + createSession(byte[])
better than a file-mapped-only Python baseline. Reports memory VmRSS/VmHWM and
cold model load from original SHA-pinned ONNX assets. Does NOT test Android
native ORT, USB camera, actual model swapping, heat, TTS, or latency on phone.
"""
import argparse,csv,gc,hashlib,json,os,statistics,time
from pathlib import Path
import numpy as np
import onnxruntime as ort

SHA={
 "det.onnx":"d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e",
 "small_rec.onnx":"5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634",
 "medium_rec.onnx":"9c09abf0957f7968c7586464b7397b84ad2387a0497a351af40e9acc71b673ba",
}

def memory_kib(field):
    try:
        with open("/proc/self/status") as f:
            for ln in f:
                if ln.startswith(field+":"):
                    return int(ln.split()[1])
    except OSError:pass
    return None

def rss():
    return memory_kib("VmRSS") or 0

def peak():
    return memory_kib("VmHWM") or 0

def percent(values,portion):
    values=sorted(values)
    return round(values[int((len(values)-1)*portion)],3)

def model_load(file,options):
    p=Path(file)
    raw=p.read_bytes()
    assert hashlib.sha256(raw).hexdigest()==SHA[p.name],p.name
    before=rss();start=time.perf_counter()
    session=ort.InferenceSession(raw,sess_options=options,
                                 providers=["CPUExecutionProvider"])
    elapsed=(time.perf_counter()-start)*1000
    assert len(session.get_inputs())==1
    result={
      "name":p.name,"disk_megabytes":round(len(raw)/1e6,2),
      "load_ms":round(elapsed,2),
      "rss_before_kib":before,"rss_after_kib":rss(),
      "peak_so_far_kib":peak(),
    }
    del raw;gc.collect()
    result["rss_after_release_loader_bytes_kib"]=rss()
    print("HYBRID_MODEL_COLD_LOAD",json.dumps(result),flush=True)
    return session,result

def trial(session,shape,loops):
    meta=session.get_inputs()[0]
    rng=np.random.RandomState(3)
    tensor=np.ascontiguousarray(rng.uniform(-.8,.8,shape).astype(np.float32))
    for _ in range(2):session.run(None,{meta.name:tensor})
    ms=[]
    for _ in range(loops):
        t=time.perf_counter()
        session.run(None,{meta.name:tensor})
        ms.append((time.perf_counter()-t)*1000)
    return {"shape":list(shape),"runs":loops,
            "p50_ms":percent(ms,.5),"p95_ms":percent(ms,.95),
            "max_ms":round(max(ms),3)}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--models",required=True)
    parser.add_argument("--scenario",choices=("small","hybrid"),required=True)
    parser.add_argument("--out",required=True)
    args=parser.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    root=Path(args.models)
    before=rss()
    settings=ort.SessionOptions()
    settings.intra_op_num_threads=2
    settings.inter_op_num_threads=1
    settings.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    models={}
    loads={}
    model_order=["det.onnx","small_rec.onnx"]
    if args.scenario=="hybrid":model_order.append("medium_rec.onnx")
    for name in model_order:
        s,report=model_load(root/name,settings)
        models[name]=s;loads[name]=report
    resident=rss()
    peak_after_load=peak()
    print("RESIDENT_MODEL_MEMORY",args.scenario,"start_kib",before,
          "after_all_kib",resident,"peak_after_load_kib",peak_after_load,flush=True)
    results={}
    for name in model_order:
        width=[1,3,640,640] if name=="det.onnx" else [1,3,48,480]
        result=trial(models[name],width,8 if name=="det.onnx" else 15)
        results[name]=result
        print("MODEL_CPU_INFERENCE",args.scenario,name,json.dumps(result),flush=True)
    report={
      "scenario":args.scenario,
      "models":loads,
      "rss_at_start_mb":round(before/1024,2),
      "rss_after_models_loaded_mb":round(resident/1024,2),
      "peak_after_loading_mb":round(peak_after_load/1024,2),
      "rss_after_run_mb":round(rss()/1024,2),
      "peak_after_run_mb":round(peak()/1024,2),
      "load_memory_delta_mb":round((resident-before)/1024,2),
      "inference":results,
      "linux_host_only":True,
      "measures_phone_memory":False,
      "actual_android_sdk_loaded":False,
      "synthetic_inputs_not_photo_recognition":True,
    }
    (out/f'{args.scenario}_memory_latency.json').write_text(
        json.dumps(report,indent=2,ensure_ascii=False))
    print("TWO_RECOGNIZER_LOADING_BENCHMARK=PASS",args.scenario,flush=True)

if __name__=="__main__":
    main()
