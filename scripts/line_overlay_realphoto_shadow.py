#!/usr/bin/env python3
"""Read-only real-photo shadow replay of PP-OCRv6 Small DBNet -> LineOverlayPlanner.

This is a NEW row-overlay evaluation, NOT a rerun of CRAFT A/B or the 148
synthetic Java assertions. Fixed public RCTW rows 1400..1411, reused from the
prior study (NOT a new blind set). No tuning or GT-driven group generation.
Download images/models only on ephemeral CI; artifact contains anonymous box
geometry, metadata and scores, NOT images, model weights, OCR text or an APK.
The Python OpenCV DBNet approximation is NOT the Android SDK implementation.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import sys

import cv2
import numpy as np
import onnxruntime as ort
import yaml

# Imported read-only from a separately checked-out, pinned existing experiment.
import ocr_box_value_deep_dive as sdk
import craft_small_detector_ab as craft

EXPECTED_SHA = {
    "det.onnx": sdk.DET_SHA,
    "small_rec.onnx": sdk.REC_SHA,
    "small_rec.yml": sdk.REC_CONFIG_SHA,
}
PREDECLARED_SLICES = {"RCTW": [(1400, 12)]}
IOU_LIMIT = .40


def to_list(quad):
    return [round(float(v), 3) for v in np.asarray(quad).reshape(8)]


def bbox(q):
    p = np.asarray(q, dtype=np.float32).reshape(4, 2)
    return [float(p[:, 0].min()), float(p[:, 1].min()),
            float(p[:, 0].max()), float(p[:, 1].max())]


def iou(a, b):
    return sdk.box_iou(bbox(a), bbox(b))


def matched_count(pred, gt):
    edges = sorted(((iou(a, b), ai, bi)
                    for ai, a in enumerate(pred)
                    for bi, b in enumerate(gt)
                    if iou(a, b) >= IOU_LIMIT), reverse=True)
    used_a, used_b = set(), set()
    for _, ai, bi in edges:
        if ai not in used_a and bi not in used_b:
            used_a.add(ai)
            used_b.add(bi)
    return len(used_a)


def gt_assignment(raw, gt):
    """Geometry-only diagnostic. Ambiguous/unmatched boxes get no GT index."""
    result = {}
    for i, source in enumerate(raw):
        if not source["text_nonempty"]:
            continue
        a = bbox(source["quad"])
        area = max(1., (a[2] - a[0]) * (a[3] - a[1]))
        cx, cy = (a[0] + a[2]) / 2, (a[1] + a[3]) / 2
        options = []
        for j, q in enumerate(gt):
            b = bbox(q)
            ox = max(0., min(a[2], b[2]) - max(a[0], b[0]))
            oy = max(0., min(a[3], b[3]) - max(a[1], b[1]))
            fraction = ox * oy / area
            inside = b[0] <= cx <= b[2] and b[1] <= cy <= b[3]
            if fraction >= .20 or inside:
                options.append((fraction, j))
        if options:
            options.sort(reverse=True)
            if len(options) == 1 or options[0][0] - options[1][0] > .15:
                result[i] = options[0][1]
    return result


def run_java(java_cp, photo_id, raw):
    data = ["PHOTO\t" + photo_id]
    for i, item in enumerate(raw):
        q = item["quad"]
        data.append("\t".join(["BOX", str(i),
                "1" if item["text_nonempty"] else "0"] + [str(n) for n in q]))
    data.append("END")
    proc = subprocess.run(
        ["java", "-cp", str(java_cp), "LineOverlayRealPhotoReplay"],
        input="\n".join(data) + "\n", encoding="utf-8",
        capture_output=True, check=True, timeout=40)
    rows = []
    done = False
    for s in proc.stdout.splitlines():
        p = s.split("\t")
        if p[0] == "ROW":
            assert len(p) == 6 and p[1] == photo_id, s
            rows.append(dict(merged=p[2] == "1", drawable=p[3] == "1",
                             source_ids=[int(v) for v in p[4].split(",")],
                             quad=[float(v) for v in p[5].split(",")]))
        elif p[0] == "DONE":
            assert len(p) == 5 and p[1] == photo_id
            assert int(p[2]) == len(raw) and int(p[3]) == len(rows)
            done = True
        else:
            raise RuntimeError("Unexpected Java output: " + s)
    assert done and sum(len(x["source_ids"]) for x in rows) == len(raw)
    assert sorted(i for x in rows for i in x["source_ids"]) == list(range(len(raw)))
    return rows


def corner_violations(rows, raw):
    """Count original points outside the proposed merged quad by over 2 pixels."""
    bad = 0
    for row in rows:
        if not row["merged"]:
            continue
        contour = np.asarray(row["quad"], dtype=np.float32).reshape(4, 1, 2)
        for i in row["source_ids"]:
            for xy in np.asarray(raw[i]["quad"]).reshape(4, 2):
                if cv2.pointPolygonTest(contour, tuple(map(float, xy)), True) < -2.:
                    bad += 1
    return bad


def evaluate(photo_id, raw, gt, rows):
    assigned = gt_assignment(raw, gt)
    sources_by_gt = {}
    for source, target in assigned.items():
        sources_by_gt.setdefault(target, set()).add(source)
    fragmented = [ids for ids in sources_by_gt.values() if len(ids) >= 2]
    united = sum(1 for ids in fragmented if any(
        ids.issubset(set(r["source_ids"])) for r in rows))
    merged = [r for r in rows if r["merged"]]
    crossed = sum(1 for r in merged if len(set(
        assigned[x] for x in r["source_ids"] if x in assigned)) >= 2)
    return dict(
        photo=photo_id,
        gt_annotations=len(gt),
        raw_boxes=len(raw),
        raw_nonempty_boxes=sum(x["text_nonempty"] for x in raw),
        candidate_rows=len(rows),
        merged_rows=len(merged),
        raw_iou040_matches=matched_count(
            [x["quad"] for x in raw if x["text_nonempty"]], gt),
        candidate_iou040_matches=matched_count(
            [r["quad"] for r in rows if r["drawable"] and any(
                raw[x]["text_nonempty"] for x in r["source_ids"])], gt),
        gt_regions_with_multiple_assigned_source_boxes=len(fragmented),
        gt_regions_fully_united=united,
        merged_rows_assigned_multiple_gt=crossed,
        merged_source_corner_violations=corner_violations(rows, raw),
        unassigned_nonempty_source_boxes=sum(
            x["text_nonempty"] and i not in assigned
            for i, x in enumerate(raw)))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--models", required=True)
    p.add_argument("--java-cp", required=True)
    p.add_argument("--out", required=True)
    args = p.parse_args()
    dest = Path(args.out)
    dest.mkdir(parents=True, exist_ok=True)
    m = Path(args.models)
    for filename, required in EXPECTED_SHA.items():
        actual = hashlib.sha256((m / filename).read_bytes()).hexdigest()
        if actual != required:
            raise RuntimeError("Pinned Small model mismatch: " + filename)
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 2
    opts.inter_op_num_threads = 1
    det = ort.InferenceSession(str(m / "det.onnx"),
        sess_options=opts, providers=["CPUExecutionProvider"])
    rec = ort.InferenceSession(str(m / "small_rec.onnx"),
        sess_options=opts, providers=["CPUExecutionProvider"])
    chars = list(yaml.safe_load((m / "small_rec.yml").read_text())
                 ["PostProcess"]["character_dict"])
    if chars[-1] != " ":
        chars.append(" ")
    sdk.SLICES = PREDECLARED_SLICES
    sdk.THRESHOLDS = [0.45]
    sdk.DET_BITMAP_THRESH = .20
    sdk.DET_UNCLIP = 1.40
    photos = sdk.collect_images()
    if len(photos) < 8:
        raise RuntimeError("Not enough predeclared public photographs available")
    evidence, metrics = [], []
    for split, idx, rgb, labels, polys in photos:
        gt = craft.gt_rows(labels, polys)
        if not gt:
            continue
        photo_id = f"{split}-{idx}"
        detected, _ = craft.small_detect(det, rgb)
        predictions = craft.rec_predictions(rec, chars, rgb, detected)
        raw = [dict(quad=to_list(obj["quad"]),
                    text_nonempty=bool(predictions.get(i, "").strip()))
               for i, obj in enumerate(detected)]
        gt_quads = []
        for text, poly in zip(labels, polys):
            if not isinstance(text, str) or "###" in text:
                continue
            try:
                q = np.asarray(poly, dtype=np.float32).reshape(-1, 2)
                if q.shape != (4, 2) or not np.isfinite(q).all():
                    continue
                xy = bbox(q)
                if xy[2] - xy[0] < 6 or xy[3] - xy[1] < 6:
                    continue
                if sdk.normalize(text):
                    gt_quads.append(to_list(q))
            except (TypeError, ValueError):
                continue
        rows = run_java(args.java_cp, photo_id, raw)
        metrics.append(evaluate(photo_id, raw, gt_quads, rows))
        evidence.append(dict(photo=photo_id, image_width=int(rgb.shape[1]),
            image_height=int(rgb.shape[0]), raw_boxes=raw,
            gt_quads=gt_quads, planner_rows=rows))
        print("LINE_OVERLAY_REALPHOTO_SCENE", json.dumps(metrics[-1],
            ensure_ascii=False, sort_keys=True), flush=True)
    if len(metrics) < 8:
        raise RuntimeError("Too few annotated scene results; no substitute set")
    summary = {"status":"completed", "source":"Yesianrohn/OCR-Data RCTW 1400..1411",
        "not_new_blind":True, "python_opencv_sdk_approximation":True,
        "no_android_camera_or_tts":True, "photos":len(metrics)}
    for k in metrics[0]:
        if k != "photo":
            summary[k] = sum(v[k] for v in metrics)
    (dest / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (dest / "box_replay_no_ocr_text.json").write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    with (dest / "per_photo.csv").open("w", newline="", encoding="utf-8-sig") as f:
        wr = csv.DictWriter(f, fieldnames=list(metrics[0]))
        wr.writeheader()
        wr.writerows(metrics)
    print("LINE_OVERLAY_REALPHOTO_SHADOW_COMPLETE",
          json.dumps(summary, ensure_ascii=False, sort_keys=True), flush=True)

if __name__ == "__main__":
    main()
