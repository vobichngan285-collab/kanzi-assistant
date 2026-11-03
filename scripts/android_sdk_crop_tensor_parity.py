#!/usr/bin/env python3
"""PaddleOCR v3.7.0 REAL SDK crop/tensor host parity check; NO OCR inference.
Prepare 2 fixed PUBLIC scene photos + GT polygons, run compiled upstream
QuadTextCrop & build-patched RecPreprocessor, compare raw BGR crop/tensor
against previously used Python OpenCV reference. NO real Android Bitmap/NV21.
"""
import argparse
import hashlib
import io
import json
import math
import shutil
import time
from pathlib import Path
from urllib.parse import urlparse
import numpy as np
import cv2
from PIL import Image
import requests
import ocr_box_value_deep_dive as old

DATA_URL = "https://datasets-server.huggingface.co/rows"
FIXED_PHOTOS = [("RCTW", 1406), ("RCTW", 1409)]
SDK_DIR = "deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr"

def retry_get(url, *, params=None):
    for attempt in range(4):
        try:
            response = requests.get(url, params=params, timeout=80)
            response.raise_for_status()
            return response
        except requests.RequestException:
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)

def fixed_scene(split, index):
    result = retry_get(DATA_URL, params={"dataset":"Yesianrohn/OCR-Data",
        "config":"default","split":split,"offset":index,"length":1}).json()
    rows = result["rows"]
    assert len(rows) == 1 and rows[0]["row_idx"] == index, "Unexpected photo identity"
    data = rows[0]["row"]
    url = (data.get("image") or {}).get("src", "")
    host = urlparse(url)
    assert host.scheme == "https" and (host.hostname or "").endswith(".huggingface.co")
    raw = retry_get(url).content
    assert len(raw) < 18000000, "Unexpected public photo size"
    rgb = np.asarray(Image.open(io.BytesIO(raw)).convert("RGB"))
    return rgb, data["texts"], data["polygons"], hashlib.sha256(raw).hexdigest()

def prepare_fixtures(root):
    root.mkdir(parents=True, exist_ok=True)
    inp = root/"input"; ref = root/"expected"
    inp.mkdir(exist_ok=True); ref.mkdir(exist_ok=True)
    manifest = []; lines = []
    for split, index in FIXED_PHOTOS:
        rgb, texts, polys, digest = fixed_scene(split,index)
        h,w=rgb.shape[:2]
        bgr=np.ascontiguousarray(cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR))
        image_name=f"{split}_{index}.bgr"
        bgr.tofile(inp/image_name)
        tested=0
        for j,(label,coords) in enumerate(zip(texts,polys)):
            if not isinstance(label,str) or "###" in label or not old.normalize(label):
                continue
            try:
                q=np.asarray(coords,np.float32).reshape(-1,2)
            except (ValueError,TypeError):
                continue
            if q.shape != (4,2) or not np.isfinite(q).all():
                continue
            x0,y0=q.min(axis=0);x1,y1=q.max(axis=0)
            if x1-x0 < 6 or y1-y0 < 6:continue
            pycrop=old.crop_box(rgb,{"quad":q})
            if pycrop is None:continue
            pybgr=np.ascontiguousarray(cv2.cvtColor(pycrop,cv2.COLOR_RGB2BGR))
            ch,cw=pybgr.shape[:2]
            new_w=max(1,min(old.MAX_RECOGNITION_WIDTH,math.ceil(48*cw/max(1,ch))))
            norm=cv2.resize(pybgr,(new_w,48),interpolation=cv2.INTER_LINEAR)
            tensor=np.ascontiguousarray(norm.transpose(2,0,1)[None].astype(np.float32)/127.5-1.)
            case=f"{split}_{index}_gt{j:02d}"
            tensor.tofile(ref/f"{case}.f32")
            pybgr.tofile(ref/f"{case}.crop.bgr")
            points=[float(v) for v in q.reshape(8)]
            lines.append("\t".join([case,image_name,str(h),str(w)]+[str(v) for v in points]))
            manifest.append({"case":case,"photo":f"{split}-{index}","gt_index":j,
                "source_photo_sha256":digest,"image_dimensions":[w,h],
                "python_crop_dimensions":[cw,ch],
                "python_tensor_shape":list(tensor.shape)})
            tested+=1
            if tested >= 6:break
        assert tested >= 4, f"Not enough eligible GT quads: {split}-{index}"
    (root/"fixtures.tsv").write_text("\n".join(lines)+"\n",encoding="utf-8")
    (root/"manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    print("SDK_CROP_TENSOR_PUBLIC_FIXTURES_READY",len(lines),"two fixed photos",flush=True)

def make_project(upstream, project, adapter):
    src=upstream/SDK_DIR
    assert (src/"preprocess/RecPreprocessor.kt").is_file()
    dst=project/"src/main/kotlin"
    dst.mkdir(parents=True,exist_ok=True)
    for rel in ("postprocess/QuadTextCrop.kt","postprocess/QuadGeometry.kt",
                "model/OCRBox.kt","preprocess/RecPreprocessor.kt"):
        shutil.copy2(src/rel,dst/Path(rel).name)
    p=dst/"RecPreprocessor.kt"
    code=p.read_text(encoding="utf-8")
    # These replacements MUST stay exactly aligned with the production
    # .github/workflows/android.yml patch. Do not change color policy here.
    changes=[
        ("// Convert BGR to RGB and resize to fixed height while preserving aspect ratio",
         "// Preserve BGR input and resize to fixed height as required by inference.yml"),
        ("            // Convert BGR to RGB (model expects RGB input)",
         "            // Model inputs are BGR crops, not RGB."),
        ("            val rgb = Mat()\n", ""),
        ("            Imgproc.cvtColor(crop, rgb, Imgproc.COLOR_BGR2RGB)\n", ""),
        ("val h = rgb.rows()", "val h = crop.rows()"),
        ("val w = rgb.cols()", "val w = crop.cols()"),
        ("Imgproc.resize(rgb, dst, Size(", "Imgproc.resize(crop, dst, Size("),
        ("            rgb.release()\n", ""),
    ]
    for before, after in changes:
        if code.count(before)!=1:
            raise ValueError("Paddle SDK source changed: cannot apply exact production patch")
        code=code.replace(before,after,1)
    assert "Imgproc.COLOR_BGR2RGB" not in code
    p.write_text(code,encoding="utf-8")
    shutil.copy2(adapter,dst/"AndroidSdkCropTensorProbe.kt")
    (dst/"PointF.kt").write_text(
        "package android.graphics\ndata class PointF(val x: Float, val y: Float)\n",
        encoding="utf-8")
    (project/"settings.gradle.kts").write_text(
        'rootProject.name = "kanzi-sdk-crop-parity"\n',encoding="utf-8")
    (project/"build.gradle.kts").write_text(
        'plugins { kotlin("jvm") version "1.9.25"; application }\n'
        'repositories { mavenCentral() }\n'
        'dependencies { implementation("org.openpnp:opencv:4.9.0-0") }\n'
        'application { mainClass.set("AndroidSdkCropTensorProbeKt") }\n'
        'kotlin { jvmToolchain(17) }\n',
        encoding="utf-8")
    print("SDK_CROP_TENSOR_REAL_UPSTREAM_SOURCES_PREPARED",flush=True)

def verify(root, actual):
    manifest=json.loads((root/"manifest.json").read_text(encoding="utf-8"))
    index={}
    for line in (actual/"sdk_outputs.tsv").read_text().splitlines():
        case,croph,cropw,shape=line.split("\t")
        index[case]={"crop_h":int(croph),"crop_w":int(cropw),
                     "shape":[int(x) for x in shape.split(",")]}
    assert set(index)=={x["case"] for x in manifest}
    records=[]
    for m in manifest:
        case=m["case"];sdk=index[case]
        exp_sh=m["python_tensor_shape"]
        same_shape=sdk["shape"]==exp_sh
        exp_crop=np.fromfile(root/"expected"/f"{case}.crop.bgr",dtype=np.uint8)
        sdk_crop=np.fromfile(actual/f"{case}.crop.bgr",dtype=np.uint8)
        same_crop_dims=[sdk["crop_w"],sdk["crop_h"]]==m["python_crop_dimensions"]
        crop_mae=None;crop_max=None;tensor_max=None;tensor_mae=None
        if same_crop_dims and exp_crop.size==sdk_crop.size:
            diff=np.abs(exp_crop.astype(np.int16)-sdk_crop.astype(np.int16))
            crop_max=int(diff.max());crop_mae=float(diff.mean())
        if same_shape:
            expected=np.fromfile(root/"expected"/f"{case}.f32",dtype="<f4")
            actual_arr=np.fromfile(actual/f"{case}.f32",dtype="<f4")
            assert expected.size==actual_arr.size==int(np.prod(exp_sh))
            d=np.abs(expected.astype(np.float64)-actual_arr.astype(np.float64))
            tensor_max=float(d.max());tensor_mae=float(d.mean())
        records.append({"case":case,"tensor_shape_equal":same_shape,
                        "crop_dimensions_equal":same_crop_dims,
                        "crop_max_pixel_diff":crop_max,
                        "crop_mean_pixel_diff":crop_mae,
                        "max_tensor_abs_diff":tensor_max,
                        "mean_tensor_abs_diff":tensor_mae})
    summary={
        "status":"completed", "real_upstream_sdk_executed_in_linux_jvm":True,
        "android_device_nv21_bitmap_not_executed":True,
        "no_ocr_model_or_detector_inference":True,
        "no_user_photos":True,
        "photos":len(FIXED_PHOTOS),"cases":len(records),
        "shape_equal_count":sum(r["tensor_shape_equal"] for r in records),
        "crop_dims_equal_count":sum(r["crop_dimensions_equal"] for r in records),
        "tensor_max_abs_diff":max((r["max_tensor_abs_diff"] for r in records if r["max_tensor_abs_diff"] is not None),default=None),
        "crop_max_pixel_diff":max((r["crop_max_pixel_diff"] for r in records if r["crop_max_pixel_diff"] is not None),default=None),
        "numeric_tolerance_pass":all(r["tensor_shape_equal"] and
            r["crop_dimensions_equal"] and r["max_tensor_abs_diff"]<=0.02 and
            r["crop_max_pixel_diff"]<=3 for r in records)
    }
    # Store metrics only; raw public images, crops and tensors remain ephemeral.
    (root/"parity_summary.json").write_text(
        json.dumps({"summary":summary,"per_case":records},indent=2),encoding="utf-8")
    print("ACTUAL_SDK_VS_PYTHON_PARITY_COMPLETE",json.dumps(summary),flush=True)
    assert len(records)>=8
    return 0

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--mode",choices=["prepare","make-project","verify"],required=True)
    parser.add_argument("--root",required=True)
    parser.add_argument("--upstream")
    parser.add_argument("--project")
    parser.add_argument("--adapter")
    parser.add_argument("--actual")
    args=parser.parse_args()
    root=Path(args.root)
    if args.mode=="prepare":prepare_fixtures(root)
    elif args.mode=="make-project":
        assert args.upstream and args.project and args.adapter
        make_project(Path(args.upstream),Path(args.project),Path(args.adapter))
    else:
        assert args.actual
        verify(root,Path(args.actual))

if __name__=="__main__":main()
