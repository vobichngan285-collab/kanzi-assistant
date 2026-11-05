#!/usr/bin/env python3
"""Research ONLY: same PP-OCRv6 Small REC ONNX model, deterministic crop shapes.
NO app source changes, NO captured image upload, NO accuracy claims from fake pixels.
Cloud timing measures ORT REC inference + tensor batch assembly, NOT end-to-end
Android OCR, original-camera accuracy, heat, battery, TTS or USB stability.
"""
import argparse, hashlib, json, os, platform, random, statistics, time
import numpy as np
import onnxruntime as ort

# Widths extracted locally from user-authorized prior diagnostic metadata
# ("capture: manual-single-frame-only") as dimension values ONLY.
# NO raw frame, photo, recognized text or tensors are uploaded.
WIDTH_6 = [151, 120, 214, 290, 181, 96]
WIDTH_19 = [146, 304, 197, 263, 132, 251, 254, 257, 234, 250,
            235, 250, 243, 202, 221, 261, 51, 208, 219]
# Extra 33-case is synthetic (NOT a captured 33-box page).
WIDTH_33_SYNTH = WIDTH_19 + [156, 164, 187, 211, 234, 265, 287,
                              301, 336, 362, 388, 407, 445, 483]
WORKLOADS = [("usb6_real_shapes", WIDTH_6),
             ("menu19_real_shapes", WIDTH_19),
             ("dense33_synthetic_shapes", WIDTH_33_SYNTH)]
PROFILES = [(2, 1), (2, 2), (3, 2), (4, 2), (4, 4)]


def make_inputs(widths):
    g = np.random.default_rng(20261105)
    # Pixel arrays are synthetic, not camera pictures; same pseudo-pixels each run.
    return [g.uniform(-1.0, 1.0, (3, 48, w)).astype(np.float32)
            for w in widths]


def order_indices(widths, batch_size, bucket):
    first = list(range(min(batch_size, len(widths))))
    if not bucket or batch_size == 1:
        return list(range(len(widths)))
    return first + sorted(range(batch_size, len(widths)), key=lambda i:(widths[i],i))


def do_pass(sess, name, crops, batch, bucket, first_batch_size=None):
    widths = [a.shape[-1] for a in crops]
    order = order_indices(widths, batch, bucket)
    # Hybrid A/B: first N original-order boxes, then larger width-bucketed groups.
    # This is only a benchmark; no Android SDK scheduling is changed.
    first_n = first_batch_size if first_batch_size is not None else batch
    if first_batch_size is None:
        groups=[order[i:i+batch] for i in range(0,len(order),batch)]
    else:
        front=list(range(min(first_n,len(crops))))
        rest=list(range(len(front),len(crops)))
        if bucket: rest.sort(key=lambda i:(widths[i],i))
        groups=([front] if front else []) + [rest[i:i+batch] for i in range(0,len(rest),batch)]
    inf_total = pack_total = 0.0
    padded = 0
    calls = 0
    first_batch_inference_ms = None
    for indices in groups:
        maxw = max(widths[i] for i in indices)
        start = time.perf_counter()
        x = np.zeros((len(indices), 3, 48, maxw), dtype=np.float32)
        for j, idx in enumerate(indices):
            x[j, :, :, :widths[idx]] = crops[idx]
        pack_total += time.perf_counter() - start
        t0 = time.perf_counter()
        _ = sess.run(None, {name:x})
        inference_elapsed = time.perf_counter() - t0
        inf_total += inference_elapsed
        if first_batch_inference_ms is None:
            first_batch_inference_ms = round(inference_elapsed * 1000, 3)
        padded += sum(maxw-widths[i] for i in indices) * 3*48
        calls += 1
    return {"inference_ms":round(inf_total*1000,3),
            "pack_ms":round(pack_total*1000,3),
            "padded_input_floats":padded,
            "ort_calls":calls,
            "first_batch_inference_ms":first_batch_inference_ms}


def run_profile(model, threads, batch, reps):
    opts=ort.SessionOptions()
    opts.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    opts.intra_op_num_threads=threads
    opts.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL
    opts.log_severity_level=3
    sess=ort.InferenceSession(model,sess_options=opts,providers=["CPUExecutionProvider"])
    name=sess.get_inputs()[0].name
    results=[]
    for scenario,widths in WORKLOADS:
        crops=make_inputs(widths)
        variants=[False] if batch==1 else [False,True]
        for bucket in variants:
            # Warm up typical batch+width shapes, ignore first runtime jit effects.
            do_pass(sess,name,crops,batch,bucket)
            vals=[do_pass(sess,name,crops,batch,bucket) for _ in range(reps)]
            v={key:round(statistics.median(row[key] for row in vals),3)
                 for key in ("inference_ms","pack_ms","first_batch_inference_ms")}
            v.update({"scenario":scenario,
                      "threads":threads,"batch":batch,
                      "first_batch_size":batch,
                      "width_bucket":bucket,
                      "n_boxes":len(widths),"ort_calls":vals[0]["ort_calls"],
                      "padded_input_floats":vals[0]["padded_input_floats"],
                      "inference_runs":reps,
                      "inference_ms_min":round(min(x["inference_ms"] for x in vals),3),
                      "inference_ms_max":round(max(x["inference_ms"] for x in vals),3)})
            results.append(v)
            print("REC_CLOUD_RESULT "+json.dumps(v,ensure_ascii=False),flush=True)
    return results


def main():
    p=argparse.ArgumentParser()
    p.add_argument("--model",default="inference.onnx")
    p.add_argument("--output",default="rec-benchmark.json")
    p.add_argument("--reps",type=int,default=3)
    args=p.parse_args()
    sha=hashlib.sha256(open(args.model,"rb").read()).hexdigest()
    expected="5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
    if sha!=expected:raise SystemExit("REFUSE: model hash mismatch: "+sha)
    print("START_REC_BENCH "+json.dumps({
        "model_sha256":sha,"onnxruntime":ort.__version__,
        "host_arch":platform.machine(),"platform":platform.platform(),
        "cpu_count":os.cpu_count(),"note":"Cloud CPU microbenchmark; NEVER infer phone FPS/accuracy/thermal benefit"},ensure_ascii=False),flush=True)
    # Vary execution order reproducibly to reduce drift; report median samples.
    rng=random.Random(20261105)
    profiles=list(PROFILES)
    rng.shuffle(profiles)
    allrows=[]
    for th,b in profiles:
        allrows.extend(run_profile(args.model,th,b,args.reps))
    # Key hybrid hypothesis: keep exact 2-box first-batch latency while using
    # batch4 for the rest, preserving the earliest genuine REC output timing.
    opts=ort.SessionOptions()
    opts.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    opts.intra_op_num_threads=4
    opts.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL
    opts.log_severity_level=3
    sess=ort.InferenceSession(args.model,sess_options=opts,providers=["CPUExecutionProvider"])
    inp=sess.get_inputs()[0].name
    for case,widths in WORKLOADS:
        crops=make_inputs(widths)
        do_pass(sess,inp,crops,4,True,first_batch_size=2)
        vals=[do_pass(sess,inp,crops,4,True,first_batch_size=2) for _ in range(args.reps)]
        record={"scenario":case,"threads":4,"batch":4,
                "first_batch_size":2,"width_bucket":True,
                "profile":"hybrid_first2_then_rest4",
                "n_boxes":len(crops),
                "inference_ms":round(statistics.median(x["inference_ms"] for x in vals),3),
                "first_batch_inference_ms":round(statistics.median(x["first_batch_inference_ms"] for x in vals),3),
                "pack_ms":round(statistics.median(x["pack_ms"] for x in vals),3),
                "ort_calls":vals[0]["ort_calls"],
                "padded_input_floats":vals[0]["padded_input_floats"],
                "inference_runs":args.reps,
                "inference_ms_min":min(x["inference_ms"] for x in vals),
                "inference_ms_max":max(x["inference_ms"] for x in vals)}
        allrows.append(record)
        print("REC_CLOUD_HYBRID "+json.dumps(record,ensure_ascii=False),flush=True)
    meta={"model_sha256":sha,"ort_version":ort.__version__,
          "host_arch":platform.machine(),"cpu_count":os.cpu_count(),
          "repetitions":args.reps,
          "limitations":["No real image content uploaded",
                         "Widths from prior phone diagnostic for 6 and 19 boxes",
                         "Dense 33-case is synthetic",
                         "REC ONNX inference only; excludes DET, cropping, CTC decode and Android thermal scheduling",
                         "First batch inference is not first audible speech: excludes preproc and TTS",
                         "No claim of equal recognized text or improved phone performance"]}
    # Compare per workload against user's selected 4-thread batch2 width-bucket.
    comparisons=[]
    for sc,_ in WORKLOADS:
        ref=next(x for x in allrows if x["scenario"]==sc and
                 x["threads"]==4 and x["batch"]==2 and x["width_bucket"])
        for x in allrows:
            if x["scenario"]==sc:
                comparisons.append({**x, "speedup_vs_user_4t2_bucket":
                    round(ref["inference_ms"]/x["inference_ms"],3) if x["inference_ms"] else None})
    print("REC_CLOUD_COMPARISONS_BEGIN",flush=True)
    for sc,_ in WORKLOADS:
        best=sorted([x for x in comparisons if x["scenario"]==sc],
                    key=lambda x:x["inference_ms"])[:5]
        print("REC_CLOUD_TOP "+sc+" "+json.dumps(best,ensure_ascii=False),flush=True)
    result={"meta":meta,"results":comparisons}
    open(args.output,"w",encoding="utf-8").write(json.dumps(result,ensure_ascii=False,indent=2))
    print("REC_CLOUD_BENCHMARK=PASS; no Android changes; no camera images uploaded",flush=True)

if __name__=="__main__":main()
