#!/usr/bin/env python3
"""Pinned SDK v3.7.0: optional first-batch genuine REC text for live TTS.

Safe test-only source patch. First batch uses the first real BoxSorter
reading-order frames; later width buckets stay as before. No new model,
hallucination, fake text, heuristics or reading-order rearrangement.
The existing complete OCR result continues to be returned unchanged.
"""
from pathlib import Path

root=Path("third_party/PaddleOCR/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr")
p=root/"engine/OCREngine.kt"
s=p.read_text(encoding="utf-8")
def sub(a,b,tag):
    global s
    count=s.count(a)
    if count!=1:
        raise SystemExit(f"Streaming SDK OCREngine anchor mismatch: {tag} found {count}")
    s=s.replace(a,b,1)
sub(
    "fun run(bitmap: Bitmap): OCREngineResult {",
    "fun run(bitmap: Bitmap, onFirstBatch: ((List<OCRResult>) -> Unit)? = null): OCREngineResult {",
    "bitmap signature")
sub(
    "        return runWithOwnedMat(srcMat)\n    }\n\n    fun run(imageBytes:",
    "        return runWithOwnedMat(srcMat, onFirstBatch)\n    }\n\n    fun run(imageBytes:",
    "bitmap adapter")
sub(
    "private fun runWithOwnedMat(srcMat: org.opencv.core.Mat): OCREngineResult {",
    "private fun runWithOwnedMat(srcMat: org.opencv.core.Mat, onFirstBatch: ((List<OCRResult>) -> Unit)? = null): OCREngineResult {",
    "mat signature")
sub(
    "            run(srcMat)\n        } finally {",
    "            run(srcMat, onFirstBatch)\n        } finally {",
    "mat adapter")
sub(
    "private fun run(srcMat: org.opencv.core.Mat): OCREngineResult {",
    "private fun run(srcMat: org.opencv.core.Mat, onFirstBatch: ((List<OCRResult>) -> Unit)?): OCREngineResult {",
    "run signature")
sub(
    """        val recOrder = if (batchSize == 1) sortedBoxes.indices.toList()
            else sortedBoxes.indices.sortedWith(
                compareBy<Int> { approximateRecAspect(sortedBoxes[it]) }.thenBy { it }
            )""",
    """        // First batch is the actual reading-order start, not the smallest
        // crop from another part of a two-page image. The rest retains the
        // previous geometrical width batching and exact index restoration.
        val front = sortedBoxes.indices.take(batchSize)
        val recOrder = if (batchSize == 1) sortedBoxes.indices.toList()
            else front + sortedBoxes.indices.drop(batchSize).sortedWith(
                compareBy<Int> { approximateRecAspect(sortedBoxes[it]) }.thenBy { it }
            )""",
    "first-batch order")
sub(
    """                    for (j in batchResult.texts.indices) {
                        val boxIdx = batchBoxIndices[j]
                        val (text, confidence) = batchResult.texts[j]
                        if (confidence >= config.recScoreThresh) {
                            resultsInReadingOrder[boxIdx] = OCRResult(
                                box = sortedBoxes[boxIdx],
                                text = text,
                                confidence = confidence,
                            )
                        }
                    }""",
    """                    for (j in batchResult.texts.indices) {
                        val boxIdx = batchBoxIndices[j]
                        val (text, confidence) = batchResult.texts[j]
                        if (confidence >= config.recScoreThresh) {
                            resultsInReadingOrder[boxIdx] = OCRResult(
                                box = sortedBoxes[boxIdx],
                                text = text,
                                confidence = confidence,
                            )
                        }
                    }
                    // Emit ONLY original model-recognized first-line text,
                    // without touching existing final results or OCR routing.
                    if (i == 0 && onFirstBatch != null) {
                        val ready = batchBoxIndices.mapNotNull { resultsInReadingOrder[it] }
                            .filter { it.text.isNotBlank() && it.confidence >= 0.60f }
                        if (ready.isNotEmpty()) {
                            try { onFirstBatch(ready) } catch (e: Throwable) {
                                android.util.Log.w("BlueStarOCR","first-batch listener failed",e)
                            }
                        }
                    }""",
    "first batch emitted once")
p.write_text(s,encoding="utf-8")

p=root/"PaddleOCR.kt";s=p.read_text(encoding="utf-8")
a="import com.paddle.ocr.model.OCRRunResult"
assert s.count(a)==1
s=s.replace(a,a+"\nimport com.paddle.ocr.model.OCRResult",1)
a="suspend fun recognize(bitmap: Bitmap): OCRRunResult {"
assert s.count(a)==1
s=s.replace(a,"suspend fun recognize(bitmap: Bitmap, onFirstBatch: ((List<OCRResult>) -> Unit)? = null): OCRRunResult {",1)
a="return recognizeResult { engine.run(bitmap) }"
assert s.count(a)==1
s=s.replace(a,"return recognizeResult { engine.run(bitmap, onFirstBatch) }",1)
p.write_text(s,encoding="utf-8")
assert "onFirstBatch(ready)" in (root/"engine/OCREngine.kt").read_text()
assert "front + sortedBoxes.indices.drop(batchSize)" in (root/"engine/OCREngine.kt").read_text()
print("BLUE_STAR_FIRST_BATCH_STREAMING_SDK_PATCH=PASS")
