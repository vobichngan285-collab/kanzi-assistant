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
    "食品生产日期质量安全检验结果符合国家标准要求",
    "生产日期保质期净含量产品名称生产许可证编号执行标准",
]


def cer(expected, actual):
    prev = list(range(len(actual) + 1))
    for i, ch in enumerate(expected, 1):
        curr = [i]
        for j, other in enumerate(actual, 1):
            curr.append(min(curr[-1] + 1, prev[j] + 1, prev[j - 1] + (ch != other)))
        prev = curr
    return prev[-1] / max(1, len(expected))



def sdk_dict_like_android(yaml_text):
    """Mirror the actual PaddleOCR v3.7.0 Android ModelConfig YAML list parser."""
    lines = yaml_text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    ind = lambda text: len(text) - len(text.lstrip(" "))
    post_line = next(i for i, line in enumerate(lines) if line.strip() == "PostProcess:")
    post_indent = ind(lines[post_line])
    char_line = None
    for i in range(post_line + 1, len(lines)):
        value = lines[i].strip()
        if not value or value.startswith("#"):
            continue
        if ind(lines[i]) <= post_indent:
            break
        if value == "character_dict:":
            char_line = i
            break
    if char_line is None:
        raise ValueError("Java parser would not find character_dict")
    chars = []
    char_indent = ind(lines[char_line])
    for line in lines[char_line + 1:]:
        indent = ind(line)
        value = line[indent:]
        if not value.strip() or value.strip().startswith("#"):
            continue
        if not value.startswith("-"):
            if indent <= char_indent:
                break
            continue
        raw = value[1:].lstrip(" ")
        if len(raw) >= 2 and raw[0] == raw[-1] == "'":
            raw = raw[1:-1].replace("''", "'")
        elif len(raw) >= 2 and raw[0] == raw[-1] == '"':
            raw = raw[1:-1].replace('\\\"', '"').replace('\\\\', '\\').replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t')
        chars.append(raw)
    return chars

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
        yaml_text = (model / "rec/inference.yml").read_text(encoding="utf-8")
        config = yaml.safe_load(yaml_text)
        chars = list(config["PostProcess"]["character_dict"])
        parsed_by_android = sdk_dict_like_android(yaml_text)
        if parsed_by_android != chars:
            first = next((i for i, (x, y) in enumerate(zip(parsed_by_android, chars)) if x != y), -1)
            raise RuntimeError(f"{label}: ANDROID DICTIONARY PARSER DIFFERS from official YAML at "
                               f"index={first}, Java={parsed_by_android[first:first+4]!r} "
                               f"YAML={chars[first:first+4]!r}, lengths={len(parsed_by_android)} / {len(chars)}")
        print(f"ANDROID_DICTIONARY_EQUALS_YAML={label} count={len(chars)}", flush=True)
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
            if mode == "BGR":
                original = make_crop(LINES[0], font, (0, 0, 0))
                for orientation, rotated in (
                        ("upside_down", np.ascontiguousarray(original[::-1, ::-1])),
                        ("mirror_horizontal", np.ascontiguousarray(original[:, ::-1]))):
                    inp = prepare(rotated, mode)
                    output = session.run(None, {input_name: inp})[0]
                    actual, confidence = decode(output, chars)
                    print(f"ORIENTATION_PROBE model={label} orientation={orientation} "
                          f"expected={LINES[0]!r} actual={actual!r} "
                          f"confidence={confidence:.3f} CER={cer(LINES[0], actual):.3f}", flush=True)
    print("SYNTHETIC_RECOGNITION_SMOKE_TEST=PASS (results above require interpretation)", flush=True)


if __name__ == "__main__":
    main()
