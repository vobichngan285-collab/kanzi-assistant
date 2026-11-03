package com.vobichngan.kanzi

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig

/**
 * COMPILE-ONLY feasibility probe; deliberately not used by MainActivity.
 *
 * No installable APK, startup initialization, settings toggle or camera changes.
 * Future runtime evaluation must be explicitly approved and device-measured.
 */
internal object HybridMediumCompileProbe {
    suspend fun load(
        context: Context,
        smallDetOnnxPrivatePath: String,
        smallRecOnnxPrivatePath: String,
        smallRecYamlPrivatePath: String,
        mediumRecOnnxPrivatePath: String,
        mediumRecYamlPrivatePath: String
    ): PaddleOCR = PaddleOCR.createHybrid(
        context = context.applicationContext,
        config = PaddleOCRConfig(
            detLimitSideLen = 1600,
            detLimitType = "max",
            detMaxSideLimit = 1600,
            detThresh = 0.20f,
            detBoxThresh = 0.45f,
            detUnclipRatio = 1.40f,
            recScoreThresh = 0.0f,
            recBatchSize = 1
        ),
        engineConfig = EngineConfig(numThreads = 2),
        detModelAssetPath = smallDetOnnxPrivatePath,
        recModelAssetPath = smallRecOnnxPrivatePath,
        recConfigAssetPath = smallRecYamlPrivatePath,
        mediumRecModelAssetPath = mediumRecOnnxPrivatePath,
        mediumRecConfigAssetPath = mediumRecYamlPrivatePath
    )

    /**
     * Research-only call: one retry in fast scan and three in long reading.
     * If the reading epoch has changed, no further Medium retries are started.
     * A native inference already in progress cannot be interrupted by this callback.
     */
    suspend fun tryOneFrame(
        engine: PaddleOCR,
        bitmap: Bitmap,
        fastScan: Boolean,
        readingEpoch: java.util.concurrent.atomic.AtomicInteger,
        capturedEpoch: Int
    ) = engine.recognizeWithMediumRescue(
        bitmap = bitmap,
        mediumBudget = if (fastScan) 1 else 3,
        canContinue = { readingEpoch.get() == capturedEpoch }
    )
}
