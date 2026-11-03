#!/usr/bin/env python3
"""Fail-closed patch of pinned upstream PaddleOCR v3.7.0 for a SHADOW ONLY
second recognition of skewed vertical source polygons. No private images.
Original SDK run(), source REC tensors, live speech and detector are unchanged.
Run AFTER checkout of exact v3.7.0 SDK, BEFORE Gradle compilation.
"""
from pathlib import Path
root=Path("third_party/PaddleOCR/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr")

def edit(path,old,new):
    p=root/path
    s=p.read_text(encoding="utf-8")
    if s.count(old)!=1:
        raise SystemExit(f"SDK patch unsafe: {path}, expected 1 anchor {old!r}, found {s.count(old)}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

edit("postprocess/QuadTextCrop.kt",
    "    fun crop(src: Mat, box: OCRBox): Mat {",
    """    // The original production crop remains bit-for-bit unchanged.
    fun crop(src: Mat, box: OCRBox): Mat = cropInternal(src, box, false)

    // Only called from the user-triggered single-frame A/B diagnostic.
    fun cropWithVerticalAxisRepair(src: Mat, box: OCRBox): Mat =
        cropInternal(src, box, true)

    private fun cropInternal(src: Mat, box: OCRBox, repair: Boolean): Mat {""")
edit("postprocess/QuadTextCrop.kt",
     "        val ordered = QuadGeometry.orderMinAreaRectPoints(boxPoints)",
     """        val ordered = if (!repair) {
            QuadGeometry.orderMinAreaRectPoints(boxPoints)
        } else {
            // For extremely long slanted vertical lines, sorting the four
            // vertices by X groups both bottom corners as the "left edge".
            // Group by Y into top/bottom pairs then sort X within each pair.
            // Caller must separately prove the axis-risk predicate.
            val byY = boxPoints.sortedWith(compareBy<Point> { it.y }.thenBy { it.x })
            val top = byY.take(2).sortedBy { it.x }
            val bottom = byY.drop(2).sortedBy { it.x }
            listOf(top[0], top[1], bottom[1], bottom[0])
        }""")
edit("engine/OCREngine.kt",
     "import com.paddle.ocr.model.OCRResult",
     "import com.paddle.ocr.model.OCRResult\nimport com.paddle.ocr.model.OCRBox")
edit("engine/OCREngine.kt",
     "    fun release() {",
     """    /**
     * Experimental shadow-only re-recognition using ORIGINAL bitmap pixels.
     * No second DET pass, no semantic processing, at most four crop attempts.
     * The normal OCRRunResult remains immutable to callers.
     */
    fun recognizeVerticalCandidates(
        bitmap: Bitmap, boxes: List<OCRBox>
    ): List<Triple<String, Float, Long>> {
        require(boxes.size <= 4) { "Excess shadow OCR crop budget" }
        if (boxes.isEmpty()) return emptyList()
        val srcMat = BitmapUtils.bitmapToBGRMat(bitmap)
        try {
            return boxes.map { box ->
                val start = System.nanoTime()
                val crop = QuadTextCrop.cropWithVerticalAxisRepair(srcMat, box)
                try {
                    if (crop.empty()) Triple("", 0f, (System.nanoTime()-start)/1000000L)
                    else {
                        val candidate = recognitionEngine.recognize(listOf(crop))
                            .texts.firstOrNull() ?: Pair("", 0f)
                        Triple(candidate.first, candidate.second,
                            (System.nanoTime()-start)/1000000L)
                    }
                } finally {
                    crop.release()
                }
            }
        } finally {
            srcMat.release()
        }
    }

    fun release() {""")
edit("PaddleOCR.kt",
    "import com.paddle.ocr.model.OCRRunResult",
    "import com.paddle.ocr.model.OCRRunResult\nimport com.paddle.ocr.model.OCRBox")
edit("PaddleOCR.kt",
     "    private suspend fun recognizeResult(runEngine: () -> OCREngineResult): OCRRunResult {",
     """    /**
     * A/B experiment only. Returns independent candidate text/confidence/elapsed
     * from same-frame pixels, NEVER overwrites baseline OCR or live speech.
     */
    suspend fun recognizeVerticalCandidates(
        bitmap: Bitmap, boxes: List<OCRBox>
    ): List<Triple<String, Float, Long>> = withContext(Dispatchers.IO) {
        engine.recognizeVerticalCandidates(bitmap, boxes)
    }

    private suspend fun recognizeResult(runEngine: () -> OCREngineResult): OCRRunResult {""")
# This SECOND shadow path is deliberately independent of the true-vertical
# axis repair above. Crop only horizontal strips proposed from detector geometry
# and run the same Small recognizer; never run a second DBNet pass.
edit("engine/OCREngine.kt",
     "    fun release() {",
     """    fun recognizeHorizontalCandidates(
        bitmap: Bitmap, xyxy: List<FloatArray>
    ): List<Triple<String, Float, Long>> {
        require(xyxy.size <= 4) { "Excess horizontal shadow REC budget" }
        if (xyxy.isEmpty()) return emptyList()
        val srcMat = BitmapUtils.bitmapToBGRMat(bitmap)
        try {
            return xyxy.map { box ->
                val start = System.nanoTime()
                require(box.size == 4 && box.all { it.isFinite() }) {
                    "Invalid horizontal crop"
                }
                val left = kotlin.math.floor(box[0].toDouble()).toInt().coerceIn(0, srcMat.cols())
                val top = kotlin.math.floor(box[1].toDouble()).toInt().coerceIn(0, srcMat.rows())
                val right = kotlin.math.ceil(box[2].toDouble()).toInt().coerceIn(0, srcMat.cols())
                val bottom = kotlin.math.ceil(box[3].toDouble()).toInt().coerceIn(0, srcMat.rows())
                if (right - left < 4 || bottom - top < 4) {
                    Triple("", 0f, (System.nanoTime()-start)/1000000L)
                } else {
                    val crop = srcMat.submat(top, bottom, left, right)
                    try {
                        // Keep horizontal strips horizontal. No forced text rotation.
                        val candidate = recognitionEngine.recognize(listOf(crop))
                            .texts.firstOrNull() ?: Pair("", 0f)
                        Triple(candidate.first, candidate.second,
                            (System.nanoTime()-start)/1000000L)
                    } finally {
                        crop.release()
                    }
                }
            }
        } finally {
            srcMat.release()
        }
    }

    fun release() {""")
edit("PaddleOCR.kt",
     "    private suspend fun recognizeResult(runEngine: () -> OCREngineResult): OCRRunResult {",
     """    suspend fun recognizeHorizontalCandidates(
        bitmap: Bitmap, xyxy: List<FloatArray>
    ): List<Triple<String, Float, Long>> = withContext(Dispatchers.IO) {
        engine.recognizeHorizontalCandidates(bitmap, xyxy)
    }

    private suspend fun recognizeResult(runEngine: () -> OCREngineResult): OCRRunResult {""")
print("COMBINED_HORIZONTAL_VERTICAL_SHADOW_SDK_PATCH_PASS: source DET/REC preserved")

