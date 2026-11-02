# Android experimental layout AI (v0.9.9)

Purpose: improve **reading continuity**, not classify a scene, generate text,
discard difficult OCR boxes, or guess the page the person wants to read.

## Offline model

- Original model: PaddlePaddle/PP-DocLayout-S (Apache-2.0).
- ONNX export: stefanj0/PP-DocLayout-S-ONNX (Apache-2.0).
- Verified SHA-256: `33688dbee1c23e34b81777e97cb428eb40f24b242c02b5f623484959e830aec8`.
- CI downloads the 4,917,852-byte model into the **APK assets**;
  no inference image or OCR text is uploaded to any server.
- ONNX Runtime inputs: `image` float32 [1,3,480,480],
  `scale_factor` float32 [1,2].
- Outputs: [N,6] detections [class, confidence, x1, y1, x2, y2]
  (in original pixel space), followed by the number of valid detections.

The export is community-hosted, not part of the official Android OCR demo.
See the original model card and model export card for information on its
preprocessing, license and model limitations.

## Privacy, performance, and fail-safe

The AI feature is opt-in, disabled by default. It runs locally on a single
still frame only after OCR detects 10+ boxes, only in continuous AUTO mode.
A missing/incompatible model, conversion failure, model runtime error or
insufficient reliable layout regions leads to a geometry-only fallback.
Text produced by the OCR model is never changed or discarded by layout AI.

Inference is measured separately from OCR; do **not** assume the model
makes OCR recognition faster. Compare AI on/off on the same device and scene.

## Decision logic

1. Current PaddleOCR returns text + oriented quadrilateral + confidence.
2. Optional PP-DocLayout-S returns rectangular high-level layout regions.
3. Each valid OCR box may be assigned to a trustworthy model region.
4. A strong difference in region ID blocks accidental horizontal/vertical
   text joins. Geometric restrictions still decide ordinary text connections.
5. With insufficient model coverage, the original geometry-only rules run.
6. Speech chunking reads complete strings; it does not hallucinate missing OCR.

## Validation before recommending as default

- Correctly connects same-line fragments and consecutive paragraph lines.
- Does not splice side notes / footnotes into a main text flow.
- Does not drop or duplicate OCR content, including uncertain short labels.
- No catastrophic regressions on single column, multiple columns,
  vertical text and the user's real skewed quadrilateral diagnostics.
- Benchmarks cold/hot latency and peak memory on target Android devices.
- Test on **real labeled frames**, not only generated geometric cases.

The opt-in experiment is **not evidence** that the new model gives a
quality advantage. Default should only change after controlled comparisons.
