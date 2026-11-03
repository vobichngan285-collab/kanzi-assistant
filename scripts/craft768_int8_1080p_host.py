#!/usr/bin/env python3
"""Same-run 24-frame CRAFT768 FP32-vs-INT8 sustained 1080p host CPU study.

Uses original publicly available scene photos resized with letterboxing into
1920x1080 RGB. No Android device/USB/UVC/TTS, no Small REC in timing.
Not intended to infer real-device PSS, thermal performance or user experience.
"""
import argparse,collections,gc,json,time
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort,psutil
import ocr_box_value_deep_dive as sdk
import craft_small_detector_ab as craft
from profile_craft768_1080p_host import scale1080p,percentile,CRAFT_SHA

def new_session(path):
    option=ort.SessionOptions()
    option.intra_op_num_threads=2
    option.inter_op_num_threads=1
    option.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    start=time.perf_counter()
    s=ort.InferenceSession(str(path),sess_options=option,providers=["CPUExecutionProvider"])
    return s,round((time.perf_counter()-start)*1000,2)

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--fp32",required=True)
    p.add_argument("--int8",required=True)
    p.add_argument("--out",required=True)
    p.add_argument("--frames",type=int,default=24)
    a=p.parse_args();assert 12<=a.frames<=72
    out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    craft.CRAFT_LONG_SIDE=768
    proc=psutil.Process();rss=lambda:round(proc.memory_info().rss/2**20,2)
    start_rss=rss()
    fp32,fp32_load=new_session(a.fp32)
    int8,int8_load=new_session(a.int8)
    sessions={"FP32":fp32,"INT8":int8}
    postload_rss=rss()
    sdk.SLICES={name:[(0,12),(500,12)] for name in ("ReCTS","RCTW","ART")}
    public_photos=sdk.collect_images()
    assert len(public_photos)==72,len(public_photos)
    chosen=public_photos[::6]
    assert len(chosen)==12
    imgs=[scale1080p(row[2]) for row in chosen]
    bufferpool=[bytearray(1920*1080*3//2) for _ in range(4)]
    for b in bufferpool:
        for k in range(0,len(b),4096):b[k]=(k//4096)%251
    input_names={name:s.get_inputs()[0].name for name,s in sessions.items()}
    measurements=[]
    for warm in range(2):
        for mode in ("FP32","INT8"):
            img=imgs[warm]
            x,scale=craft.craft_preprocess(img)
            raw=sessions[mode].run(None,{input_names[mode]:x})[0]
            craft.craft_regions(raw[0],img.shape[:2],scale)
    for f in range(a.frames):
        img=imgs[f%12]
        # Reverse order on alternating frames to reduce systematic order bias.
        order=("FP32","INT8") if f%2 else ("INT8","FP32")
        for mode in order:
            t0=time.perf_counter()
            image_input,resize_scale=craft.craft_preprocess(img)
            t1=time.perf_counter()
            result=sessions[mode].run(None,{input_names[mode]:image_input})[0]
            t2=time.perf_counter()
            boxes,_=craft.craft_regions(result[0],img.shape[:2],resize_scale)
            t3=time.perf_counter()
            measurements.append(dict(frame=f,mode=mode,
              preprocess_ms=round((t1-t0)*1000,2),
              model_infer_ms=round((t2-t1)*1000,2),
              postprocess_ms=round((t3-t2)*1000,2),
              detector_ms=round((t3-t0)*1000,2),
              detected_boxes=len(boxes),
              input_shape=list(image_input.shape),process_rss_mib=rss()))
        if f%8==0:
            print("CRAFT768_INT8_1080P_PROGRESS",f,"/",a.frames,
                  [(r["mode"],r["detector_ms"]) for r in measurements[-2:]],flush=True)
    report=[]
    for mode in ("FP32","INT8"):
        sub=[x for x in measurements if x["mode"]==mode]
        rep=dict(model=mode,frames=len(sub),resolution="1920x1080 public photos letterboxed",
           craft_max_input_side=768,
           quantized=mode=="INT8",
           model_bytes=Path(a.fp32 if mode=="FP32" else a.int8).stat().st_size,
           model_load_ms=fp32_load if mode=="FP32" else int8_load,
           warmup_frames=2,
           detector_p50_ms=percentile([x["detector_ms"] for x in sub],.50),
           detector_p95_ms=percentile([x["detector_ms"] for x in sub],.95),
           model_infer_p50_ms=percentile([x["model_infer_ms"] for x in sub],.50),
           model_infer_p95_ms=percentile([x["model_infer_ms"] for x in sub],.95),
           first_frame_rss_mib=sub[0]["process_rss_mib"],
           last_frame_rss_mib=sub[-1]["process_rss_mib"],
           cpu_execution_provider=True,
           uses_android_arm_cpu=False,
           uses_actual_UVC_camera=False,
           includes_small_recognition=False,
           measures_tts_first_word=False)
        report.append(rep)
        print("CRAFT768_INT8_1080P_RESULT",json.dumps(rep),flush=True)
    combined=dict(process_initial_rss_mib=start_rss,
                  both_sessions_loaded_rss_mib=postload_rss,
                  process_last_rss_mib=rss(),
                  combined_sessions_and_all_72_photo_buffers=True,
                  note="Combined RSS includes two sessions, full public dataset and simulated 1080p buffers; not Android PSS.")
    (out/"summary.json").write_text(json.dumps(report,indent=2,ensure_ascii=False))
    (out/"combined_memory.json").write_text(json.dumps(combined,indent=2,ensure_ascii=False))
    (out/"per_frame.json").write_text(json.dumps(measurements,indent=2,ensure_ascii=False))
    print("CRAFT768_INT8_1080P_MEMORY",json.dumps(combined),flush=True)
    print("CRAFT768_INT8_1080P_COMPLETE",a.frames,flush=True)

if __name__=="__main__":main()
