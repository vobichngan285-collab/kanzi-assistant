#!/usr/bin/env python3
"""v56 isolated build patch: width-bucket PP-OCRv6 Small REC batches only.

This patches the exact pinned PaddleOCR v3.7.0 SDK checkout in CI. It NEVER
changes the text detector, CTC lexicon, crop transforms, or official REC model.

The source's original detector reading-order boxes retain their indices;
recognition is computed in groups of similar cropped widths to reduce
zero-padding, and then restored to exactly the original reading order.
Batch=1 still processes strictly in reading order as the rollback baseline.
"""
from pathlib import Path

path=Path("third_party/PaddleOCR/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr/engine/OCREngine.kt")
src=path.read_text(encoding="utf-8")

def change(old,new,what):
    global src
    count=src.count(old)
    if count!=1:
        raise SystemExit(f"Refuse {what}: expected one SDK anchor, found {count}")
    src=src.replace(old,new,1)

change(
    """        val allResults = mutableListOf<OCRResult>()
        val recInputShapes = mutableListOf<List<Int>>()
        val perLineRecMs = mutableListOf<Long>()
        val batchSize = config.recBatchSize.coerceAtLeast(1)

        var i = 0
        while (i < sortedBoxes.size) {""",
    """        // The original BoxSorter order remains authoritative for reading.
        // For small REC batches only, pair geometrically similar crop widths.
        // Short price labels must not be padded to entire textbook-line width.
        val resultsInReadingOrder = arrayOfNulls<OCRResult>(sortedBoxes.size)
        val recInputShapes = mutableListOf<List<Int>>()
        val perLineRecMs = mutableListOf<Long>()
        val batchSize = config.recBatchSize.coerceAtLeast(1)
        val recOrder = if (batchSize == 1) sortedBoxes.indices.toList()
            else sortedBoxes.indices.sortedWith(
                compareBy<Int> { approximateRecAspect(sortedBoxes[it]) }.thenBy { it }
            )

        var i = 0
        while (i < recOrder.size) {""",
    "recognition batch array and width order"
)
change(
    """            while (next < sortedBoxes.size && batchCrops.size < batchSize) {
                val crop = QuadTextCrop.crop(srcMat, sortedBoxes[next])
                if (crop.rows() > 0 && crop.cols() > 0) {
                    batchCrops.add(crop)
                    batchBoxIndices.add(next)
                } else {
                    crop.release()
                }
                next++
            }""",
    """            while (next < recOrder.size && batchCrops.size < batchSize) {
                val originalIdx = recOrder[next]
                val crop = QuadTextCrop.crop(srcMat, sortedBoxes[originalIdx])
                if (crop.rows() > 0 && crop.cols() > 0) {
                    batchCrops.add(crop)
                    batchBoxIndices.add(originalIdx)
                } else {
                    crop.release()
                }
                next++
            }""",
    "batch indices mapping"
)
change(
    """                            allResults.add(
                                OCRResult(
                                    box = sortedBoxes[boxIdx],
                                    text = text,
                                    confidence = confidence,
                                )
                            )""",
    """                            resultsInReadingOrder[boxIdx] = OCRResult(
                                box = sortedBoxes[boxIdx],
                                text = text,
                                confidence = confidence,
                            )""",
    "restore exactly-original position of recognized rows"
)
change(
    """        val totalElapsed = System.currentTimeMillis() - totalStart
        val pipelineOverhead""",
    """        // Do not change the reading order delivered to the existing OCR
        // flow, overlay, TTS, diagnostics or the user.
        val allResults = resultsInReadingOrder.filterNotNull()
        val totalElapsed = System.currentTimeMillis() - totalStart
        val pipelineOverhead""",
    "read-order reconstruction"
)
change(
    """    fun release() {
        ortManager.release()
    }""",
    """    // Same QuadTextCrop minAreaRect orientation rule (>=1.5 => rotate).
    // Only a cheap box-side-length estimate; actual input is still the same
    // official cropped pixels. No text-based or generative grouping.
    private fun approximateRecAspect(box: com.paddle.ocr.model.OCRBox): Double {
        val p = box.points
        fun span(a: Int,b: Int): Double {
            val dx = (p[a].x - p[b].x).toDouble()
            val dy = (p[a].y - p[b].y).toDouble()
            return kotlin.math.hypot(dx,dy)
        }
        val w = kotlin.math.max(span(0,1),span(2,3))
        val h = kotlin.math.max(span(0,3),span(1,2))
        if (w < 1.0 || h < 1.0) return 1.0
        return if (h/w >= 1.5) h/w else w/h
    }

    fun release() {
        ortManager.release()
    }""",
    "aspect estimator"
)
assert "val allResults = resultsInReadingOrder.filterNotNull()" in src
assert "compareBy<Int> { approximateRecAspect(sortedBoxes[it]) }" in src
assert src.count("resultsInReadingOrder[boxIdx] = OCRResult")==1
path.write_text(src,encoding="utf-8")
print("V56_REC_WIDTH_BATCH_PATCH=PASS; original OCR boxes/crop/CTC unchanged; order restored")
