#!/usr/bin/env python3
"""Sustained co-resident three-model CPU stress, not Android phone measurements.

All three official SHA256-pinned ONNX models stay resident. Run a repeatable
synthetic whole-frame shape and fixed numbers of Small rec lines and optional
Medium retries. Allocate and touch 1080p-like NV21/RGB buffers, but NO actual
USB stream, camera driver, text detection postprocess, speech, Android runtime,
thermal signal or true frame latency. Strictly report inference-only workload.

This is useful for discovering serious ORT memory growth/perf instability
before any APK/device test. Not sufficient to claim a mobile performance pass.
"""
import argparse,collections,gc,hashlib,json,math,os,time
from pathlib import Path
import numpy as np
import onnxruntime as ort

SHA={
"det.onnx":"d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e",
"small_rec.onnx":"5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634",
"medium_rec.onnx":"9c09abf0957f7968c7586464b7397b84ad2387a0497a351af40e9acc71b673ba"}

def status(field):
    try:
        for line in Path("/proc/self/status").read_text().splitlines():
            if line.startswith(field+":"):
                return int(line.split()[1])/1024
    except OSError:pass
    return None

def percent(xs,q):
    s=sorted(xs)
    return round(s[int(round((len(s)-1)*q))],3)

def session(path,options):
    path=Path(path)
    raw=path.read_bytes()
    assert hashlib.sha256(raw).hexdigest()==SHA[path.name]
    s=ort.InferenceSession(raw,sess_options=options,
                            providers=["CPUExecutionProvider"])
    del raw
    return s,s.get_inputs()[0].name

def run_trial(s,inp,t):
    start=time.perf_counter()
    s.run(None,{inp:t})
    return (time.perf_counter()-start)*1000

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--models",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--frames",type=int,default=32)
    p.add_argument("--lines",type=int,default=10)
    args=p.parse_args()
    assert 15<=args.frames<=200 and 1<=args.lines<=30
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    opt=ort.SessionOptions()
    opt.intra_op_num_threads=2
    opt.inter_op_num_threads=1
    opt.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    models={}
    for name in SHA:
        s,input_name=session(Path(args.models)/name,opt)
        models[name]=(s,input_name)
        print("STRESS_LOAD",name,round(status("VmRSS") or 0,2),flush=True)

    # Approximate simultaneous UVC frame buffers only; no USB or TTS.
    # Touch each buffer to commit its memory and include it in RSS/HWM.
    preview_bytes=1920*1080*3//2
    all_buffers=[bytearray(preview_bytes) for i in range(4)]
    all_buffers += [bytearray(1920*1080*3) for i in range(2)]
    for buffer in all_buffers:
        for i in range(0,len(buffer),4096):
            buffer[i]=(i//4096)%251
    initial_rss=status("VmRSS")
    print("STRESS_BUFFERS",
          "synthetic_1080p_preview_and_color_buffers",len(all_buffers),
          "MB",round(sum(map(len,all_buffers))/1048576,2),
          "rss",initial_rss,flush=True)

    r=np.random.RandomState(19)
    xdet=np.ascontiguousarray(r.uniform(-1,1,(1,3,736,1280)).astype(np.float32))
    xrec=np.ascontiguousarray(r.uniform(-1,1,(1,3,48,480)).astype(np.float32))
    for name in models:
        s,i=models[name]
        s.run(None,{i:xdet if name=="det.onnx" else xrec})
    output=[]
    for scenario,medium_cap in [("small_only",0),("quick_scan_cap_1",1),("long_read_cap_3",3)]:
        latencies=[];samples=[];before=status("VmRSS")
        # Keep camera-like buffers alive, rotate references to avoid free/realloc.
        for frame in range(args.frames):
            started=time.perf_counter()
            det_s,det_name=models["det.onnx"]
            det_ms=run_trial(det_s,det_name,xdet)
            small_s,small_name=models["small_rec.onnx"]
            small_ms=sum(run_trial(small_s,small_name,xrec) for _ in range(args.lines))
            med_ms=0.
            if medium_cap:
                medium_s,medium_name=models["medium_rec.onnx"]
                med_ms=sum(run_trial(medium_s,medium_name,xrec) for _ in range(medium_cap))
            elapsed=(time.perf_counter()-started)*1000
            latencies.append(elapsed)
            samples.append({"frame":frame,"cpu_ms":round(elapsed,2),
                            "det_infer_ms":round(det_ms,2),
                            "small_rec_ms":round(small_ms,2),
                            "medium_rec_ms":round(med_ms,2),
                            "rss_mib":round(status("VmRSS") or 0,2),
                            "hwm_mib":round(status("VmHWM") or 0,2)})
            if frame%12==0:
                print("SUSTAINED_PROGRESS",scenario,frame,
                      round(elapsed,1),"RSS",samples[-1]["rss_mib"],flush=True)
            # Simulate mutation of a preview buffer, NOT actual USB capture.
            all_buffers[frame%len(all_buffers)][0]=(frame%250)
        s={"scenario":scenario,"frames":len(latencies),"rec_lines_per_photo":args.lines,
           "medium_retries_per_photo":medium_cap,
           "inference_only_p50_ms":percent(latencies,.5),
           "inference_only_p95_ms":percent(latencies,.95),
           "inference_only_p99_ms":percent(latencies,.99),
           "inference_first_8_p50_ms":percent(latencies[:8],.5),
           "inference_last_8_p50_ms":percent(latencies[-8:],.5),
           "RSS_before_mib":round(before or 0,2),
           "RSS_after_mib":round(status("VmRSS") or 0,2),
           "peak_RSS_mib":round(status("VmHWM") or 0,2),
           "RAM_12GB_nominal_percent":round((status("VmRSS") or 0)/12288*100,2),
           "host_linux_only":True,"camera_preview_buffers_simulated":True,
           "input_real_photo":False,"real_UVC_timing":False,
           "Android_latency_or_temperature_measured":False}
        output.append(s)
        print("SUSTAINED_MODEL_PROFILE",json.dumps(s,ensure_ascii=False),flush=True)
        (out/(scenario+"_per_frame.json")).write_text(json.dumps(samples,indent=2))
    (out/"summary.json").write_text(json.dumps(output,indent=2,ensure_ascii=False))
    if max(x["RSS_after_mib"]-x["RSS_before_mib"] for x in output)>150:
        print("WARN_HOST_RSS_GREW_OVER_150_MB; check native arena reuse",flush=True)
    print("TWELVE_GB_HYBRID_SUSTAINED_HOST_PASS",args.frames,flush=True)

if __name__=="__main__":main()
