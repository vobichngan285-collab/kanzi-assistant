#!/usr/bin/env python3
"""Apply verified, compile-only 12GB-phone hybrid improvements to pinned SDK.

Must run AFTER patch_android_sdk_medium_probe.py on the CI-extracted
upstream PaddleOCR SDK. NEVER touches production app code.

- Small detector ONCE; Small recognition for ALL boxes first.
- Then rank eligible Medium rescue requests by Small confidence, cap 1 or 3.
- A page-epoch freshness callback can SKIP stale Medium retries.
- Medium rec errors/empty text keep original Small text.
- No extra detector, no repeat Small recognition, no APK.

This is compile-only. A Kotlin closure cannot abort a running native ONNX
inference; only rescues NOT YET STARTED can be skipped after a page turn.
"""
from pathlib import Path
import argparse


def once(src, old, new, which):
    n=src.count(old)
    if n!=1:
        raise AssertionError("Expected one occurrence in "+which+": "+str(n)+" => "+old[:120])
    return src.replace(old,new,1)


def main():
    p=argparse.ArgumentParser()
    p.add_argument("sdk_root")
    args=p.parse_args()
    base=Path(args.sdk_root)/"src/main/java/com/paddle/ocr"
    engine=base/"engine/OCREngine.kt"
    text=engine.read_text()
    text=once(text,
        "    private val maxMediumRescuesPerFrame = 3",
        "    private val maxMediumRescuesPerFrame = 3",
        "medium cap constant")  # manifest invariant: DO NOT alter static cap > 3
    text=once(text,
        """    fun run(bitmap: Bitmap, enableMediumRescue: Boolean = false): OCREngineResult {
        val srcMat = BitmapUtils.bitmapToBGRMat(bitmap)
        return runWithOwnedMat(srcMat, enableMediumRescue)
    }""",
        """    fun run(
        bitmap: Bitmap,
        enableMediumRescue: Boolean = false,
        mediumBudget: Int = 3,
        canContinue: () -> Boolean = { true },
    ): OCREngineResult {
        val srcMat = BitmapUtils.bitmapToBGRMat(bitmap)
        return runWithOwnedMat(srcMat, enableMediumRescue, mediumBudget, canContinue)
    }""",
        "engine bitmap opt-in API")
    text=once(text,
        """    private fun runWithOwnedMat(
        srcMat: org.opencv.core.Mat, enableMediumRescue: Boolean
    ): OCREngineResult {
        return try {
            run(srcMat, enableMediumRescue)""",
        """    private fun runWithOwnedMat(
        srcMat: org.opencv.core.Mat, enableMediumRescue: Boolean,
        mediumBudget: Int = 3, canContinue: () -> Boolean = { true },
    ): OCREngineResult {
        return try {
            run(srcMat, enableMediumRescue, mediumBudget, canContinue)""",
        "runWithOwnedMat page epoch check")
    text=once(text,
        """    private fun run(
        srcMat: org.opencv.core.Mat, enableMediumRescue: Boolean
    ): OCREngineResult {""",
        """    private fun run(
        srcMat: org.opencv.core.Mat, enableMediumRescue: Boolean,
        mediumBudget: Int, canContinue: () -> Boolean,
    ): OCREngineResult {""",
        "inner run epoch and budget arguments")
    text=once(text,
        """        var mediumRescueCount = 0
        val allResults = mutableListOf<OCRResult>()""",
        """        val mediumCandidates = mutableListOf<Pair<Int, Float>>()
        val allResults = mutableListOf<OCRResult>()""",
        "defer Medium until after Small pass")
    text=once(text,
        """                        var (text, confidence) = batchResult.texts[j]
                        val optionalChars = optionalMediumCharacters
                        // Explicit opt-in and at most three Medium calls after one detector run.
                        // Require batch=1 to avoid unintentionally redoing other rows.
                        if (enableMediumRescue && optionalChars != null &&
                            batchCrops.size == 1 && confidence < mediumRescueThreshold &&
                            mediumRescueCount < maxMediumRescuesPerFrame) {
                            mediumRescueCount++
                            try {
                                val second = recognitionEngine.recognizeMedium(batchCrops, optionalChars)
                                totalRecPreMs += second.preprocessMs
                                totalRecInfMs += second.inferenceMs
                                totalRecPostMs += second.postprocessMs
                                totalRecMs += second.timeMs
                                if (second.texts.size == 1 && second.texts[0].first.isNotBlank()) {
                                    text = second.texts[0].first
                                    confidence = second.texts[0].second
                                }
                            } catch (error: Throwable) {
                                if (error is OutOfMemoryError) throw error
                                android.util.Log.w("KanziHybridOCR",
                                    "Medium recognizer failed; preserving Small text", error)
                            }
                        }
                        if (confidence >= config.recScoreThresh) {""",
        """                        val (text, confidence) = batchResult.texts[j]
                        if (confidence >= config.recScoreThresh) {""",
        "remove first-three-in-reading-order Medium calls")
    text=once(text,
        """                            allResults.add(
                                OCRResult(
                                    box = sortedBoxes[boxIdx],
                                    text = text,
                                    confidence = confidence,
                                )
                            )
                        }""",
        """                            allResults.add(
                                OCRResult(
                                    box = sortedBoxes[boxIdx],
                                    text = text,
                                    confidence = confidence,
                                )
                            )
                            if (enableMediumRescue && optionalMediumCharacters != null &&
                                batchSize == 1 && confidence < mediumRescueThreshold) {
                                mediumCandidates.add(Pair(allResults.lastIndex, confidence))
                            }
                        }""",
        "track only eligible Small predictions")
    text=once(text,
        """        val totalElapsed = System.currentTimeMillis() - totalStart
        val pipelineOverhead = totalElapsed - detResult.timeMs - totalRecMs""",
        """        // Perform one full Small pass FIRST. Rank all low-confidence
        // boxes, so the limited Medium budget is spent on the most uncertain
        // lines rather than simply the top of the reading order.
        //
        // A new page/readingEpoch invalidates further work before each rescue,
        // without changing an already running native ONNX call.
        val optionalChars = optionalMediumCharacters
        val budget = if (enableMediumRescue && batchSize == 1)
            mediumBudget.coerceIn(0, maxMediumRescuesPerFrame) else 0
        if (optionalChars != null && budget > 0 && canContinue()) {
            val orderedCandidates = mediumCandidates.sortedWith(
                compareBy<Pair<Int, Float>> { it.second }.thenBy { it.first })
                .take(budget)
            for ((resultIndex, _) in orderedCandidates) {
                if (!canContinue()) break
                val oldResult = allResults[resultIndex]
                val rescueCrop = QuadTextCrop.crop(srcMat, oldResult.box)
                try {
                    if (rescueCrop.rows() > 0 && rescueCrop.cols() > 0) {
                        val second = recognitionEngine.recognizeMedium(
                            listOf(rescueCrop), optionalChars)
                        totalRecPreMs += second.preprocessMs
                        totalRecInfMs += second.inferenceMs
                        totalRecPostMs += second.postprocessMs
                        totalRecMs += second.timeMs
                        recInputShapes.add(second.inputShape)
                        perLineRecMs.add(second.timeMs)
                        if (second.texts.size == 1 && second.texts[0].first.isNotBlank()) {
                            val (mediumText, mediumScore) = second.texts[0]
                            allResults[resultIndex] = oldResult.copy(
                                text = mediumText, confidence = mediumScore)
                        }
                    }
                } catch (error: Throwable) {
                    if (error is OutOfMemoryError) throw error
                    android.util.Log.w(
                        "KanziHybridOCR", "Medium rescue failed; keeping Small", error)
                } finally {
                    rescueCrop.release()
                }
            }
        }

        val totalElapsed = System.currentTimeMillis() - totalStart
        val pipelineOverhead = totalElapsed - detResult.timeMs - totalRecMs""",
        "post small-pass ranked bounded rescue")
    engine.write_text(text)
    api=base/"PaddleOCR.kt"
    src=api.read_text()
    src=once(src,
        """    suspend fun recognizeWithMediumRescue(bitmap: Bitmap): OCRRunResult {
        if (bitmap.width == 0 || bitmap.height == 0) throw OCRError.InvalidImage()
        return recognizeResult { engine.run(bitmap, enableMediumRescue = true) }
    }""",
        """    suspend fun recognizeWithMediumRescue(
        bitmap: Bitmap,
        mediumBudget: Int = 3,
        canContinue: () -> Boolean = { true },
    ): OCRRunResult {
        if (bitmap.width == 0 || bitmap.height == 0) throw OCRError.InvalidImage()
        return recognizeResult {
            engine.run(bitmap, enableMediumRescue = true,
                mediumBudget = mediumBudget, canContinue = canContinue)
        }
    }""",
        "explicit rescue with budget and page freshness")
    api.write_text(src)
    source=engine.read_text()
    assert source.count("detectionEngine.detect(srcMat)")==1
    assert source.count("recognitionEngine.recognizeMedium(")==1
    assert "maxMediumRescuesPerFrame = 3" in source
    assert "mediumBudget.coerceIn(0, maxMediumRescuesPerFrame)" in source
    assert "mediumCandidates.sortedWith(" in source
    assert "if (!canContinue()) break" in source
    assert source.index("val orderedCandidates")>source.index("val batchResult = recognitionEngine.recognize(batchCrops)")
    assert "val (text, confidence) = batchResult.texts[j]" in source
    assert "enableMediumRescue: Boolean = false" in source
    assert "val oldResult = allResults[resultIndex]" in source
    assert "oldResult.copy(" in source
    print("PRIORITIZED_BOUNDED_MEDIUM_RESCUE=PASS",flush=True)
    print("READING_EPOCH_ABORT_UNUSED_RESCUES=PASS",flush=True)
    print("SMALL_ONLY_AND_ONE_DETECTOR_UNCHANGED=PASS",flush=True)

if __name__=="__main__":main()
