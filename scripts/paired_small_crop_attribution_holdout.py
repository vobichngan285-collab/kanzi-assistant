#!/usr/bin/env python3
"""Matched-only paired Small REC crop test. Uses a PREVIOUS detector's CSV.
Exactly the same 122 HOLDOUT GT regions are scored with:
(A) unchanged historical automatic DET crop REC edits (no new DET inference)
(B) same Small REC on annotated polygon crop (new inference, same source image).
Never treat this as an Android SDK parity test or a whole-page OCR CER.
"""
import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
import yaml

import ocr_box_value_deep_dive as sdk
import ocr_final_error_attribution as prior

REC_SHA = "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
YML_SHA = "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
EXPECTED = {"photo_count":36, "gt_count":244, "paired":122,
            "chars":690, "automatic_edits":111, "automatic_exact":89}

def check_hash(path, expected):
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise RuntimeError("Pinned historical Small REC model/config differs: " + path.name)

def gt_with_polygons(texts, polygons):
    # Replicate the prior gt_items filter exactly; compare its output below.
    items = []
    for label, poly in zip(texts, polygons):
        if not isinstance(label, str) or "###" in label:
            continue
        try:
            xy = np.asarray(poly, np.float32).reshape(-1, 2)
        except (TypeError, ValueError):
            continue
        if len(xy) < 4 or not np.isfinite(xy).all():
            continue
        bb = sdk.bbox_from_quad(xy)
        if bb[2]-bb[0] < 6 or bb[3]-bb[1] < 6:
            continue
        normal = sdk.normalize(label)
        if not normal:
            continue
        items.append((label, normal, xy))
    return items

def summarize(rows):
    chars = sum(r["chars"] for r in rows)
    ae = sum(r["automatic_edits"] for r in rows)
    ge = sum(r["gt_crop_edits"] for r in rows)
    categories = Counter()
    for r in rows:
        a, g = r["automatic_edits"], r["gt_crop_edits"]
        categories[("both_exact" if a == 0 and g == 0 else
                    "auto_wrong_gt_right" if a > 0 and g == 0 else
                    "auto_right_gt_wrong" if a == 0 and g > 0 else
                    "both_wrong")] += 1
        categories[("gt_crop_better" if g < a else
                    "auto_crop_better" if g > a else "same_edit_distance")] += 1
    return {
        "matched_gt_regions":len(rows), "normalized_chars":chars,
        "old_auto_crop_edits":ae,"new_gt_crop_edits":ge,
        "old_auto_crop_CER":round(ae/chars,6) if chars else None,
        "new_gt_crop_CER":round(ge/chars,6) if chars else None,
        "paired_CER_difference_pp":round(100*(ae-ge)/chars,4) if chars else None,
        "old_auto_exact_rows":sum(r["automatic_edits"] == 0 for r in rows),
        "new_gt_exact_rows":sum(r["gt_crop_edits"] == 0 for r in rows),
        "oracle_failed_crops":sum(r["crop_failed"] for r in rows),
        "categories":dict(sorted(categories.items()))
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--old-csv", required=True)
    parser.add_argument("--rec", required=True)
    parser.add_argument("--yml", required=True)
    parser.add_argument("--out", required=True)
    a = parser.parse_args()
    dest = Path(a.out)
    dest.mkdir(parents=True, exist_ok=True)
    model, yml = Path(a.rec), Path(a.yml)
    check_hash(model, REC_SHA); check_hash(yml, YML_SHA)
    with Path(a.old_csv).open(encoding="utf-8-sig", newline="") as f:
        original = list(csv.DictReader(f))
    holdout = [r for r in original if r["cohort"] == "HOLDOUT"]
    matched = [r for r in holdout if r["matched_iou_040"] == "1"]
    assert len(holdout) == EXPECTED["gt_count"], len(holdout)
    assert len(matched) == EXPECTED["paired"], len(matched)
    assert sum(int(r["gt_chars"]) for r in matched) == EXPECTED["chars"]
    assert sum(int(r["rec_edits"]) for r in matched) == EXPECTED["automatic_edits"]
    assert sum(int(r["rec_edits"]) == 0 for r in matched) == EXPECTED["automatic_exact"]
    expected_keys = {(r["split"],int(r["image"]),int(r["gt_id"])) for r in matched}
    assert len(expected_keys) == len(matched), "duplicate old GT keys"
    lookup = {(r["split"],int(r["image"]),int(r["gt_id"])):r for r in matched}
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 2
    opts.inter_op_num_threads = 1
    sess = ort.InferenceSession(str(model), sess_options=opts, providers=["CPUExecutionProvider"])
    cfg = yaml.safe_load(yml.read_text(encoding="utf-8"))
    chars = list(cfg["PostProcess"]["character_dict"])
    if chars[-1] != " ": chars.append(" ")
    sdk.SLICES = {split:[(500,12)] for split in ("ART","RCTW","ReCTS")}
    photos = sdk.collect_images()
    assert len(photos) == EXPECTED["photo_count"], ("incomplete fixed source cohort",len(photos))
    observed_photos = {(sp,idx) for sp,idx,*_ in photos}
    assert observed_photos == {(sp,i) for sp in ("ART","RCTW","ReCTS") for i in range(500,512)}
    metrics = []
    seen = set()
    seen_gt = 0
    for sp,idx,rgb,labels,polys in photos:
        fresh = gt_with_polygons(labels,polys)
        prior_gt = prior.gt_items(labels,polys)
        assert len(fresh) == len(prior_gt)
        for j, ((label,normal,xy), other) in enumerate(zip(fresh,prior_gt)):
            assert label == other["truth"] and normal == other["normal"]
            assert np.allclose(np.asarray(sdk.bbox_from_quad(xy)),other["box"])
            seen_gt += 1
            key = (sp,idx,j)
            if key not in lookup: continue
            historical = lookup[key]
            assert normal == sdk.normalize(historical["gt_text"])
            assert len(normal) == int(historical["gt_chars"])
            assert int(historical["rec_edits"]) >= 0
            historical_box = np.array([float(v) for v in historical["gt_box"].split(",")])
            assert np.allclose(historical_box, sdk.bbox_from_quad(xy),atol=0.015)
            crop = sdk.crop_box(rgb,{"quad":xy})
            failed = crop is None
            if failed:
                edits = len(normal)
                inference_ms = None
            else:
                # Preserve EXACT same BGR path used in previous oracle and
                # historical auto-crop recognition. We do not change sdk.rec.
                bgr = cv2.cvtColor(crop,cv2.COLOR_RGB2BGR)
                prediction, confidence, inference_ms = sdk.rec(
                    sess,sess.get_inputs()[0].name,chars,bgr)
                edits = sdk.distance(normal,sdk.normalize(prediction))
            metrics.append({
                "split":sp,"image":idx,"gt_id":j,"chars":len(normal),
                "automatic_edits":int(historical["rec_edits"]),
                "gt_crop_edits":int(edits),
                "crop_failed":int(failed),
                "gt_crop_rec_ms":round(inference_ms,2) if inference_ms is not None else "",
                "gt_crop_size":"" if failed else f"{crop.shape[1]}x{crop.shape[0]}",
                "previous_max_iou":float(historical["max_iou"])
            })
            seen.add(key)
        print("PAIRED_CROP_SOURCE",sp,idx,"matched",sum(x["split"]==sp and x["image"]==idx for x in metrics),flush=True)
    assert seen_gt == EXPECTED["gt_count"], seen_gt
    assert seen == expected_keys, ("missing prior GT regions",sorted(expected_keys-seen))
    assert len(metrics) == EXPECTED["paired"]
    totals = summarize(metrics)
    assert totals["old_auto_crop_edits"] == 111
    summary = {
        "result":"completed", "comparison":"same 122 matched GT regions only",
        "old_run":41566050026,"old_artifact":13199760778,
        "old_run_input_not_rerun":True,
        "source_fixed_36_holdout_photos":len(photos),
        "gt_total_in_fixed_holdout":seen_gt,
        "rec_weights_sha256":REC_SHA,
        "no_new_det_or_craft_inference":True,
        "no_android_sdk_camera_or_tts":True,
        "do_not_generalize_to_unmatched_gt":True,
        "overall":totals,
        "per_dataset":{sp:summarize([r for r in metrics if r["split"]==sp])
                       for sp in ("ART","RCTW","ReCTS")}
    }
    with (dest/"paired_matched_only.csv").open("w",newline="",encoding="utf-8-sig") as f:
        wr=csv.DictWriter(f,fieldnames=list(metrics[0]))
        wr.writeheader();wr.writerows(metrics)
    (dest/"summary.json").write_text(json.dumps(summary,indent=2,ensure_ascii=False),encoding="utf-8")
    print("PAIRED_SMALL_CROP_ATTRIBUTION_COMPLETE",json.dumps(summary,ensure_ascii=False),flush=True)

if __name__=="__main__": main()
