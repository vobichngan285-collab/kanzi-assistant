#!/usr/bin/env python3
"""12 representative public-photo 1080p buffers through CRAFT768 + ONNX CPU.

Cloud Linux host-only forward/postprocess sustained profile; not Android device
testing, no UVC camera, no PP-OCR REC, no TTS, no APK. Preserves source photo
aspect using white letterbox (a 1080p-like pixel buffer, not device capture).
"""
import argparse, gc, hashlib, json, os, statistics, time
from pathlib import Path
import cv2
import numpy as np
import onnxruntime as ort
import psutil
import craft_small_detector_ab as craft
import ocr_box_value_deep_dive as sdk

CRAFT_SHA="28c05f8b5e97f3c5765d00bca556ec2397c86c954d4ac5ef73aa10043db16388"

def percentile(xs,p):
    vals=sorted(xs)
    return round(vals[int(round((len(vals)-1)*p))],2)

def scale1080p(src):
    h,w=src.shape[:2]
    ratio=min(1920/w,1080/h)
    nw=max(1,int(round(w*ratio)));nh=max(1,int(round(h*ratio)))
    content=cv2.resize(src,(nw,nh),interpolation=cv2.INTER_AREA if ratio<1 else cv2.INTER_CUBIC)
    result=np.full((1080,1920,3),255,dtype=np.uint8)
    left=(1920-nw)//2;top=(1080-nh)//2
    result[top:top+nh,left:left+nw]=content
    return result

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--model",required=True)
    parser.add_argument("--out",required=True)
    parser.add_argument("--frames",type=int,default=24)
    args=parser.parse_args()
    assert 12<=args.frames<=120
    outfile=Path(args.out);outfile.mkdir(exist_ok=True,parents=True)
    craft.CRAFT_LONG_SIDE=768
    path=Path(args.model)
    sha=hashlib.sha256(path.read_bytes()).hexdigest()
    assert sha==CRAFT_SHA,sha
    proc=psutil.Process()
    rss=lambda:round(proc.memory_info().rss/(1024*1024),2)
    before=rss()
    options=ort.SessionOptions()
    options.intra_op_num_threads=2
    options.inter_op_num_threads=1
    options.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    t=time.perf_counter()
    session=ort.InferenceSession(str(path),sess_options=options,providers=["CPUExecutionProvider"])
    load_ms=round((time.perf_counter()-t)*1000,2)
    afterload=rss()
    sdk.SLICES={s:[(0,12),(500,12)] for s in ("ReCTS","RCTW","ART")}
    photos=sdk.collect_images()
    assert len(photos)>=70,len(photos)
    chosen=photos[::6]
    assert len(chosen)==12
    images=[scale1080p(p[2]) for p in chosen]
    # Keep 4 buffers in memory, roughly a small live capture queue.
    fake_nv21_buffers=[bytearray(1920*1080*3//2) for _ in range(4)]
    for buf in fake_nv21_buffers:
        for k in range(0,len(buf),4096):
            buf[k]=(k//4096)%251
    tname=session.get_inputs()[0].name
    samples=[]
    for k in range(args.frames+2):
        rgb=images[k%12]
        st=time.perf_counter()
        data,scale=craft.craft_preprocess(rgb)
        pre_ms=(time.perf_counter()-st)*1000
        st=time.perf_counter()
        raw=session.run(None,{tname:data})[0]
        infer_ms=(time.perf_counter()-st)*1000
        st=time.perf_counter()
        boxes,_=craft.craft_regions(raw[0],rgb.shape[:2],scale)
        post_ms=(time.perf_counter()-st)*1000
        if k<2:continue
        row=dict(frame=k-2,pre_ms=round(pre_ms,2),infer_ms=round(infer_ms,2),
                 post_ms=round(post_ms,2),total_ms=round(pre_ms+infer_ms+post_ms,2),
                 recognized_boxes=len(boxes),input_shape=list(data.shape),
                 rss_mib=rss())
        samples.append(row)
        if (k-2)%8==0:
            print("CRAFT768_HOST_1080P_PROGRESS",k-2,"/",args.frames,
              "inference_ms",row["infer_ms"],"RSS_MiB",row["rss_mib"],flush=True)
    middle=rss()
    del images,photos,chosen,session,data,raw,boxes,fake_nv21_buffers
    gc.collect()
    after=rss()
    summary=dict(
      craft_sha256=CRAFT_SHA,model_bytes=path.stat().st_size,
      frames=len(samples),real_public_photo_content=True,
      image_size="1920x1080 aspect-preserved letterbox",
      onnx_cpu_threads=2,load_ms=load_ms,
      process_rss_before_session_mib=before,
      rss_after_load_mib=afterload,rss_after_frames_mib=middle,
      peak_observed_rss_mib=max(x["rss_mib"] for x in samples),
      rss_after_session_release_mib=after,
      rss_growth_during_frames_mib=round(samples[-1]["rss_mib"]-samples[0]["rss_mib"],2),
      total_detection_p50_ms=percentile([x["total_ms"] for x in samples],.5),
      total_detection_p95_ms=percentile([x["total_ms"] for x in samples],.95),
      pure_model_infer_p50_ms=percentile([x["infer_ms"] for x in samples],.5),
      pure_model_infer_p95_ms=percentile([x["infer_ms"] for x in samples],.95),
      extra_recognition_latency_included=False,
      android_device_run=False,actual_UVC_camera_run=False,
      tts_first_word_latency_measured=False,
      actual_android_PSS_measured=False,
      cloud_CPU_host_only=True)
    (outfile/"summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2))
    (outfile/"frames.json").write_text(json.dumps(samples,ensure_ascii=False,indent=2))
    print("CRAFT768_HOST_PROFILE",json.dumps(summary,ensure_ascii=False),flush=True)
    print("CRAFT768_HOST_CPU_PROFILE_COMPLETE",len(samples),flush=True)

if __name__=="__main__":main()
