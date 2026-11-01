#!/usr/bin/env python3
"""Real Chinese OCR recognition smoke test using the APK's exact ONNX/YAML assets.

Unlike the metadata-only verifier, this runs real rendered Chinese glyphs
through the actual recognizers and CTC decode; it reports CER for BOTH channel
orders to distinguish preprocessing from CTC/dictionary failures.
This is synthetic recognition only; camera cropping needs device evidence.
"""
from pathlib import Path
import argparse
import math
import subprocess

import cv2
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageFont
import yaml

MODELS = [
    ("v6-small", "models"),
    ("v6-tiny", "models/ppocrv6_tiny"),
    ("v5-mobile", "models/ppocrv5_mobile"),
]
LINES = [
    "中国文字识别测试",
    "生产日期商品名称",
    "看字助手北京上海",
]


def cer(expected, actual):
    prev = list(range(len(actual) + 1))
    for i, ch in enumerate(expected, 1):
        curr = [i]
        for j, other in enumerate(actual, 1):
            curr.append(min(curr[-1] + 1, prev[j] + 1, prev[j - 1] + (ch != other)))
        prev = curr
    return prev[-1] / max(1, len(expected))


def choose_font():
    preferred = ["/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                 "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"]
    for path in preferred:
        if Path(path).exists():
            return path
    found = subprocess.check_output(["fc-match", "-f", "%{file}", ":lang=zh"], text=True).strip()
    if not found or not Path(found).exists():
        raise RuntimeError("No CJK font available; install fonts-noto-cjk")
    return found


def make_crop(text, font, color):
    font_obj = ImageFont.truetype(font, 54)
    temp = Image.new("RGB", (2000, 90), "white")
    draw = ImageDraw.Draw(temp)
    bounds = draw.textbbox((0, 0), text, font=font_obj)
    width = int(bounds[2] - bounds[0] + 36)
    img = Image.new("RGB", (width, 80), "white")
    draw = ImageDraw.Draw(img)
    draw.text((18 - bounds[0], 5 - bounds[1]), text, fill=color, font=font_obj)
    return np.asarray(img)


def prepare(rgb_image, channel_order):
    src = cv2.cvtColor(rgb_image, cv2.COLOR_RGB2BGR) if channel_order == "BGR" else rgb_image
    h, w = src.shape[:2]
    width = min(3200, math.ceil(48 * w / h))
    resized = cv2.resize(src, (width, 48), interpolation=cv2.INTER_LINEAR)
    return np.ascontiguousarray((resized.transpose(2, 0, 1)[None].astype(np.float32) / 127.5) - 1.0)


def decode(logits, chars):
    if logits.ndim != 3:
        raise RuntimeError(f"Expected NTC recognition output, got {logits.shape}")
    indices = np.argmax(logits[0], axis=1)
    scores = np.max(logits[0], axis=1)
    result = []
    confidences = []
    prev = -1
    for i, index in enumerate(indices):
        index = int(index)
        if index != 0 and index != prev and 1 <= index <= len(chars):
            result.append(chars[index - 1])
            confidences.append(float(scores[i]))
        prev = index
    return "".join(result), (sum(confidences) / len(confidences) if confidences else 0.0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("asset_root")
    args = parser.parse_args()
    root = Path(args.asset_root)
    font = choose_font()
    print(f"SYNTHETIC_TEST_FONT={font}", flush=True)
    print("Note: model loading and synthetic recognition are independently checked; real USB camera image quality is NOT measured.")
    samples = [(text, "black", (0, 0, 0)) for text in LINES]
    samples.append((LINES[0], "blue", (25, 55, 220)))
    for label, subdir in MODELS:
        model = root / subdir
        config = yaml.safe_load((model / "rec/inference.yml").read_text(encoding="utf-8"))
        chars = list(config["PostProcess"]["character_dict"])
        if chars[-1] != " ":
            chars.append(" ")
        session = ort.InferenceSession(str(model / "rec/inference.onnx"), providers=["CPUExecutionProvider"])
        input_name = session.get_inputs()[0].name
        print(f"RECOGNIZER={label} num_classes={len(chars)+1}", flush=True)
        for mode in ("BGR", "RGB"):
            total = 0.0
            for expected, color_name, color in samples:
                inp = prepare(make_crop(expected, font, color), mode)
                out = session.run(None, {input_name: inp})[0]
                if out.shape[-1] != len(chars) + 1:
                    raise RuntimeError(f"{label} {mode}: class count {out.shape[-1]} does not match dictionary {len(chars)+1}")
                actual, confidence = decode(out, chars)
                error = cer(expected, actual)
                total += error
                print(f"CASE model={label} mode={mode} color={color_name} width={inp.shape[-1]} expected={expected!r} "
                      f"actual={actual!r} confidence={confidence:.3f} CER={error:.3f}", flush=True)
            print(f"SUMMARY model={label} mode={mode} mean_CER={total/len(samples):.3f}", flush=True)
    print("SYNTHETIC_RECOGNITION_SMOKE_TEST=PASS (results above require interpretation)", flush=True)


if __name__ == "__main__":
    main()
