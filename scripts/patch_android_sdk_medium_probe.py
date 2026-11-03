#!/usr/bin/env python3
"""PINNED PaddleOCR v3.7.0 Android SDK research patch, COMPILE-ONLY.

No app release code is changed; this patches only the CI runner's downloaded
third_party/PaddleOCR SDK. Hybrid API must be explicitly called by a caller.
The old SDK path and PaddleOCR.recognize() stay unchanged.

One detector/session + two recognizers, NOT two full PaddleOCR engines.
Bounded at most 3 Medium rescue calls per frame at confidence <0.95.
Any nonfatal Medium error falls back to the original Small prediction.
No Android hardware benchmark is claimed by a successful compile.
"""
from pathlib import Path
import argparse
import re


def replace_once(text,old,new,label):
    n=text.count(old)
    if n!=1:raise RuntimeError(f"SDK v3.7.0 drift in {label}: expected 1, found {n}: {old[:110]}")
    return text.replace(old,new,1)


def main():
    p=argparse.ArgumentParser()
    p.add_argument("sdk")
    args=p.parse_args()
    root=Path(args.sdk)/"src/main/java/com/paddle/ocr"
    assert (root/"PaddleOCR.kt").is_file()
    # 1) Android release's BGR patch matters: model YAML says img_mode: BGR.
    recpre=root/"preprocess/RecPreprocessor.kt"
    src=recpre.read_text()
    transforms=[
      ("// Convert BGR to RGB and resize to fixed height while preserving aspect ratio",
       "// Preserve the BGR crop channel order from inference.yml"),
      ("            // Convert BGR to RGB (model expects RGB input)",
       "            // Official inference.yml specifies BGR input."),
      ("            val rgb = Mat()\n",""),
      ("            Imgproc.cvtColor(crop, rgb, Imgproc.COLOR_BGR2RGB)\n",""),
      ("val h = rgb.rows()","val h = crop.rows()"),
      ("val w = rgb.cols()","val w = crop.cols()"),
      ("Imgproc.resize(rgb, dst, Size(","Imgproc.resize(crop, dst, Size("),
      ("            rgb.release()\n",""),
    ]
    for old,new in transforms:
        src=replace_once(src,old,new,"RecPreprocessor")
    assert "Imgproc.COLOR_BGR2RGB" not in src
    recpre.write_text(src)
    print("RELEASE_BGR_SDK_PREPROCESS_PRESERVED=PASS",flush=True)

    # 2) Fix original SDK file loader in identical way as release build.
    ort=root/"engine/ORTSessionManager.kt"
    src=ort.read_text()
    src=replace_once(src,"import java.nio.FloatBuffer","import java.nio.FloatBuffer\nimport java.io.File","ORTSessionManager imports")
    src=replace_once(src,"context.assets.open(assetPath).use { it.readBytes() }",
        """val modelFile = File(assetPath)
            if (modelFile.isFile) modelFile.readBytes()
            else context.assets.open(assetPath).use { it.readBytes() }""",
            "ORTSessionManager model loader")

    src=replace_once(src,
        '    private var recSession: OrtSession? = null',
        '''    private var recSession: OrtSession? = null
    private var optionalMediumSession: OrtSession? = null
    private var optionalMediumInputName: String = "x"''',
        "medium member")

    load_func='''
    /**
     * Explicit opt-in. Shares the original Small detector and ORT environment.
     * Failed Medium load leaves Small session intact.
     */
    fun loadOptionalMediumRecognizer(modelAssetPath: String) {
        check(optionalMediumSession == null) { "Medium recognizer already initialized" }
        val runtime = env ?: throw OCRError.ModelLoadFailed(
            "medium recognition", IllegalStateException("ORT not initialized"))
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(config.numThreads)
        }
        try {
            val modelBytes = readModelAsset(modelAssetPath)
            val candidate = runtime.createSession(modelBytes, opts)
            try {
                val input = candidate.inputNames.iterator().next()
                optionalMediumSession = candidate
                optionalMediumInputName = input
            } catch (t: Throwable) {
                candidate.close()
                throw t
            }
        } catch (t: Throwable) {
            try { optionalMediumSession?.close() } catch (_: Throwable) {}
            optionalMediumSession = null
            throw OCRError.ModelLoadFailed("medium recognition", t)
        } finally {
            opts.close()
        }
    }

    fun runOptionalMediumRecognition(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val session = optionalMediumSession
            ?: throw OCRError.ModelLoadFailed("medium recognition", IllegalStateException("No Medium recognizer"))
        val runtime = env
            ?: throw OCRError.ModelLoadFailed("medium recognition", IllegalStateException("ORT unavailable"))
        return runSession(runtime, session, optionalMediumInputName, input, shape, "medium recognition")
    }
'''
    src=replace_once(src,
        '    fun runDetection(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {',
        load_func+'\n    fun runDetection(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {',
        "optional model loader")
    src=replace_once(src,
        '''    fun release() {
        try {
            detSession?.close()''',
        '''    fun release() {
        try { optionalMediumSession?.close() } finally { optionalMediumSession = null }
        try {
            detSession?.close()''',
        "optional model lifecycle")
    ort.write_text(src)

    cfg=root/"model/ModelConfig.kt"
    src=cfg.read_text()
    src=replace_once(src,"import com.paddle.ocr.util.YamlUtils",
        "import com.paddle.ocr.util.YamlUtils\nimport java.io.File","ModelConfig imports")
    src=replace_once(src,
        'context.assets.open(assetPath).bufferedReader().use { it.readText() }',
        '''val configFile = File(assetPath)
                if (configFile.isFile) configFile.bufferedReader().use { it.readText() }
                else context.assets.open(assetPath).bufferedReader().use { it.readText() }''',
        "ModelConfig private file loader")
    cfg.write_text(src)

    # 3) Optional standalone Medium recognition shares crop & preprocess API.
    recog=root/"engine/RecognitionEngine.kt"
    src=recog.read_text()
    src=replace_once(src,
        '\n    fun recognize(crops: List<Mat>): RecognitionResult {',
        '''
    fun recognizeMedium(crops: List<Mat>, mediumCharacters: List<String>): RecognitionResult {
        val preStart = System.currentTimeMillis()
        val pre = RecPreprocessor.preprocessBatch(crops)
        val preMs = System.currentTimeMillis() - preStart
        val inferStart = System.currentTimeMillis()
        val (output, outputShape) = ortManager.runOptionalMediumRecognition(pre.tensorData, pre.shape)
        val inferMs = System.currentTimeMillis() - inferStart
        val postStart = System.currentTimeMillis()
        val decoded = CTCDecoder.decode(output, outputShape, mediumCharacters)
        val postMs = System.currentTimeMillis() - postStart
        return RecognitionResult(
            texts = decoded, preprocessMs = preMs, inferenceMs = inferMs,
            postprocessMs = postMs, timeMs = preMs + inferMs + postMs,
            inputShape = pre.shape.map { it.toInt() })
    }

    fun recognize(crops: List<Mat>): RecognitionResult {''',
        "optional recognition API")
    recog.write_text(src)

    engine=root/"engine/OCREngine.kt"
    src=engine.read_text()
    src=replace_once(src,
        '''    recConfigAsset: String = "models/rec/inference.yml",
) {''',
        '''    recConfigAsset: String = "models/rec/inference.yml",
    mediumRecModelAsset: String? = null,
    mediumRecConfigAsset: String? = null,
) {''',"engine opt-in ctor args")
    src=replace_once(src,
        '''    private val recognitionEngine: RecognitionEngine
    val coldLoadTimeMs''',
        '''    private val recognitionEngine: RecognitionEngine
    private var optionalMediumCharacters: List<String>? = null
    private val maxMediumRescuesPerFrame = 3
    private val mediumRescueThreshold = 0.95f
    val coldLoadTimeMs''',"engine fields")
    src=replace_once(src,
        '''            val recModelConfig = ModelConfig.parse(context, recConfigAsset)
            recModelConfig''',
        '''            val recModelConfig = ModelConfig.parse(context, recConfigAsset)
            if (mediumRecModelAsset != null || mediumRecConfigAsset != null) {
                require(mediumRecModelAsset != null && mediumRecConfigAsset != null) {
                    "Medium model and dictionary must be provided together"
                }
                val mediumConfig = ModelConfig.parse(context, mediumRecConfigAsset)
                ortManager.loadOptionalMediumRecognizer(mediumRecModelAsset)
                optionalMediumCharacters = mediumConfig.characterList
            }
            recModelConfig''',"engine secondary load")
    src=replace_once(src,
        '''    fun run(bitmap: Bitmap): OCREngineResult {
        val srcMat = BitmapUtils.bitmapToBGRMat(bitmap)
        return runWithOwnedMat(srcMat)
    }''',
        '''    fun run(bitmap: Bitmap, enableMediumRescue: Boolean = false): OCREngineResult {
        val srcMat = BitmapUtils.bitmapToBGRMat(bitmap)
        return runWithOwnedMat(srcMat, enableMediumRescue)
    }''',"bitmap opt-in")
    src=replace_once(src,
        '''        return runWithOwnedMat(srcMat)
    }

    private fun runWithOwnedMat(srcMat: org.opencv.core.Mat): OCREngineResult {
        return try {
            run(srcMat)''',
        '''        return runWithOwnedMat(srcMat, false)
    }

    private fun runWithOwnedMat(
        srcMat: org.opencv.core.Mat, enableMediumRescue: Boolean
    ): OCREngineResult {
        return try {
            run(srcMat, enableMediumRescue)''',"no duplicated detection")
    src=replace_once(src,
        '''    private fun run(srcMat: org.opencv.core.Mat): OCREngineResult {''',
        '''    private fun run(
        srcMat: org.opencv.core.Mat, enableMediumRescue: Boolean
    ): OCREngineResult {''',"run signature")
    src=replace_once(src,
        '''        var totalRecMs = 0L
        val allResults''',
        '''        var totalRecMs = 0L
        var mediumRescueCount = 0
        val allResults''',"per-frame rescue budget")

    src=replace_once(src,
        '''                        val (text, confidence) = batchResult.texts[j]
                        if (confidence >= config.recScoreThresh) {''',
        '''                        var (text, confidence) = batchResult.texts[j]
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
                        if (confidence >= config.recScoreThresh) {''',"conditional low confidence rec")
    engine.write_text(src)

    api=root/"PaddleOCR.kt"
    src=api.read_text()
    src=replace_once(src,
        '''    }

    suspend fun recognize(bitmap: Bitmap): OCRRunResult {''',
        '''        /**
         * Research-only opt-in: one Small detector and two CTC recognizers.
         * Model files are private app files or verified assets. Not used by #315.
         */
        suspend fun createHybrid(
            context: Context,
            config: PaddleOCRConfig,
            engineConfig: EngineConfig,
            detModelAssetPath: String,
            recModelAssetPath: String,
            recConfigAssetPath: String,
            mediumRecModelAssetPath: String,
            mediumRecConfigAssetPath: String,
        ): PaddleOCR = withContext(Dispatchers.IO) {
            val engine = OCREngine(
                context.applicationContext, config, engineConfig,
                detModelAsset = detModelAssetPath,
                recModelAsset = recModelAssetPath,
                recConfigAsset = recConfigAssetPath,
                mediumRecModelAsset = mediumRecModelAssetPath,
                mediumRecConfigAsset = mediumRecConfigAssetPath,
            )
            PaddleOCR(engine)
        }
    }

    suspend fun recognize(bitmap: Bitmap): OCRRunResult {''',"hybrid sdk create api")
    src=replace_once(src,
        '''    suspend fun recognize(imageBytes: ByteArray): OCRRunResult {''',
        '''    /** Called ONLY from explicit developer experiment; no default behavior change. */
    suspend fun recognizeWithMediumRescue(bitmap: Bitmap): OCRRunResult {
        if (bitmap.width == 0 || bitmap.height == 0) throw OCRError.InvalidImage()
        return recognizeResult { engine.run(bitmap, enableMediumRescue = true) }
    }

    suspend fun recognize(imageBytes: ByteArray): OCRRunResult {''',"hybrid explicit recognition")
    api.write_text(src)

    # static assertions refuse silent changes and detect accidental duplicate detector.
    e=engine.read_text()
    assert e.count("detectionEngine.detect(srcMat)") == 1
    assert e.count("recognitionEngine.recognizeMedium(batchCrops, optionalChars)") == 1
    assert "maxMediumRescuesPerFrame = 3" in e
    assert "enableMediumRescue: Boolean = false" in e
    assert "try { optionalMediumSession?.close() }" in ort.read_text()
    print("HYBRID_SDK_OPT_IN_PATCH=PASS",flush=True)
    print("ONE_DETECTOR_TWO_RECOGNIZERS_NO_APK=PASS",flush=True)


if __name__=="__main__":
    main()
