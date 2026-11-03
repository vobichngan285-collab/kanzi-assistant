#!/usr/bin/env python3
"""Research-only static INT8 QDQ quantization of unchanged CRAFT MLT25K ONNX.

Use nine predeclared DEV real-scene photo inputs only for calibration; never
calibrate from HOLDOUT or use GT labels. The same CRAFT 768 preprocessing is
shared by FP32, INT8 calibration, and subsequent OCR comparison.

Target: ONNX Runtime ARM-friendly S8S8 QDQ CNN; per-tensor Conv weights and
calibrated activations. This is not a guarantee of Android execution/speed.
"""
import argparse, collections, hashlib, json, time
from pathlib import Path
import numpy as np
import onnx
import onnxruntime as ort
from onnxruntime.quantization import (
    CalibrationDataReader, CalibrationMethod, QuantFormat, QuantType, quantize_static
)
import craft_small_detector_ab as craft
import ocr_box_value_deep_dive as sdk

SOURCE_SHA="28c05f8b5e97f3c5765d00bca556ec2397c86c954d4ac5ef73aa10043db16388"
MODEL_SIZE_MIN=10_000_000
CALIBRATION_PER_DATASET=3
EXPECTED_CALIBRATION=9
INPUT_SIDE=768

class FixedDevReader(CalibrationDataReader):
    def __init__(self, inputs, input_name):
        self.inputs=inputs
        self.name=input_name
        self.index=0
    def get_next(self):
        if self.index>=len(self.inputs):return None
        image=self.inputs[self.index]
        self.index+=1
        return {self.name:image}
    def rewind(self):
        self.index=0

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def choose_calibration(photos):
    """Three annotated DEV *images*, not GT boxes, from each of ReCTS/RCTW/ART."""
    chosen=[]
    per=collections.Counter()
    for split,image_index,rgb,_,_ in photos:
        if image_index>=100:continue
        if per[split]>=CALIBRATION_PER_DATASET:continue
        chosen.append((split,image_index,rgb))
        per[split]+=1
    if len(chosen)!=EXPECTED_CALIBRATION or len(per)!=3:
        raise RuntimeError(f"Unexpected calibration cohort: {per}")
    return chosen

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--source",required=True)
    p.add_argument("--output",required=True)
    p.add_argument("--report",required=True)
    a=p.parse_args()
    original=Path(a.source)
    quant=Path(a.output)
    report=Path(a.report)
    quant.parent.mkdir(parents=True,exist_ok=True)
    report.parent.mkdir(parents=True,exist_ok=True)
    src_sha=sha(original)
    assert src_sha==SOURCE_SHA,src_sha
    assert original.stat().st_size>70_000_000
    craft.CRAFT_LONG_SIDE=INPUT_SIDE
    sdk.SLICES={x:[(0,12),(500,12)] for x in ("ReCTS","RCTW","ART")}
    photos=sdk.collect_images()
    assert len(photos)==72,len(photos)
    chosen=choose_calibration(photos)
    inputs=[]
    for split,idx,rgb in chosen:
        x,_=craft.craft_preprocess(rgb)
        inputs.append(x)
        print("CRAFT_INT8_DEV_CALIBRATION",split,idx,list(x.shape),flush=True)
    del photos
    input_name="input"
    before=ort.InferenceSession(str(original),providers=["CPUExecutionProvider"])
    names=[x.name for x in before.get_inputs()]
    if len(names)!=1:raise RuntimeError(f"Expected CRAFT image input only, got {names}")
    input_name=names[0]
    del before
    reader=FixedDevReader(inputs,input_name)
    print("CRAFT_INT8_CALIBRATION_SETUP",len(inputs),"target_CPU_ARM_S8S8_QDQ",
          "Conv_MatMul_Gemm","per_channel_false",flush=True)
    started=time.perf_counter()
    quantize_static(
        model_input=str(original),
        model_output=str(quant),
        calibration_data_reader=reader,
        calibrate_method=CalibrationMethod.MinMax,
        quant_format=QuantFormat.QDQ,
        op_types_to_quantize=["Conv","MatMul","Gemm"],
        activation_type=QuantType.QInt8,
        weight_type=QuantType.QInt8,
        per_channel=False,
        reduce_range=False,
        extra_options={
            "ActivationSymmetric":True,
            "WeightSymmetric":True
        }
    )
    elapsed=(time.perf_counter()-started)*1000
    if not quant.is_file() or quant.stat().st_size<MODEL_SIZE_MIN:
        raise RuntimeError("Quantized model missing or unexpectedly small")
    model=onnx.load(str(quant),load_external_data=True)
    onnx.checker.check_model(model)
    opcounts=collections.Counter(n.op_type for n in model.graph.node)
    dq=int(opcounts.get("DequantizeLinear",0))
    q=int(opcounts.get("QuantizeLinear",0))
    if dq<10 or q<10:raise RuntimeError(f"Quantization missed CNN operations: {opcounts}")
    sess=craft.model(quant,None)
    orig_sess=craft.model(original,SOURCE_SHA)
    check_samples=[inputs[0],inputs[-1]]
    deviations=[]
    for x in check_samples:
        a0=orig_sess.run(None,{input_name:x})[0]
        a1=sess.run(None,{input_name:x})[0]
        if a0.shape!=a1.shape:raise RuntimeError(f"Quantized output shape changed: {a0.shape} -> {a1.shape}")
        if not np.isfinite(a1).all():raise RuntimeError("Quantized graph produced NaNs/Infs")
        mae=float(np.mean(np.abs(a0-a1)))
        maxae=float(np.max(np.abs(a0-a1)))
        deviations.append({"shape":list(a0.shape),"mae":round(mae,6),
                           "max_abs_error":round(maxae,6)})
    info=dict(source_sha256=SOURCE_SHA,quant_sha256=sha(quant),
              source_bytes=original.stat().st_size,quantized_bytes=quant.stat().st_size,
              compression_ratio=round(original.stat().st_size/quant.stat().st_size,3),
              calibration_image_count=len(inputs),
              calibration_images=[dict(dataset=x[0],image_index=x[1]) for x in chosen],
              configuration="static QDQ symmetric S8S8 per_tensor Conv,MatMul,Gemm",
              calibrated_with_holdout=False,quantization_ms=round(elapsed,2),
              q_nodes=q,dq_nodes=dq,onnx_op_counts=dict(opcounts),
              fp32_int8_raw_output_diff=deviations,
              android_runtime_verified=False,model_reproduced_on_android=False)
    report.write_text(json.dumps(info,ensure_ascii=False,indent=2))
    print("CRAFT_INT8_QUANTIZATION_RESULT",json.dumps(
        {k:v for k,v in info.items() if k!="onnx_op_counts"},ensure_ascii=False),flush=True)
    print("CRAFT_INT8_QDQ_QUANTIZATION_PASS",flush=True)

if __name__=="__main__":main()
