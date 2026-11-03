package com.vobichngan.kanzi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OcrReadResult(
    val text: String,
    val boxes: List<FloatArray>,
    val frameWidth: Int,
    val frameHeight: Int,
    val boxCount: Int,
    val averageConfidence: Double,
    val totalTimeMs: Long
)
fun interface OcrResultCallback { fun onResult(result: OcrReadResult?) }
fun interface OcrErrorCallback { fun onError(message: String) }
data class OcrDiagnosticResult(
    val text: String,
    val rawDetails: String,
    val modelId: String,
    val boxCount: Int,
    val averageConfidence: Double,
    val detectionTimeMs: Long,
    val recognitionTimeMs: Long,
    val totalTimeMs: Long,
    val detectorInputShape: String,
    val recognitionInputShapes: String,
    val focusDescription: String,
    val fullPageText: String,
    val layoutStatus: String,
    val layoutRegions: Int,
    val layoutElapsedMs: Long,
    val layoutReuseStats: String,
    val candidateText: String,
    val candidateDetails: String,
    val realtimeText: String,
    val x200ProfileReport: String
)
fun interface OcrDiagnosticCallback { fun onResult(result: OcrDiagnosticResult) }

class PaddleOcrEngine(
    context: Context,
    private val listener: Listener,
    private val modelId: String = "ppocrv6_small",
    private val performanceProfile: OcrPerformanceProfile = OcrPerformanceProfile.BASELINE
) {
    interface Listener {
        fun onReady()
        fun onError(message: String)
    }

    private data class PositionedText(
        val item: OCRResult,
        val text: String,
        val left: Float,
        val top: Float,
        val bottom: Float,
        val centerY: Float,
        val height: Float
    )

    private data class TextLine(
        val items: MutableList<PositionedText>,
        var top: Float,
        var bottom: Float,
        var centerY: Float
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = AtomicBoolean(false)
    private val evidenceRequested = AtomicBoolean(false)
    @Volatile private var evidenceStatus = "尚未采集单帧图像证据"

    fun requestEvidenceForNextDiagnostic() {
        SingleFrameEvidence.lastFile(appContext)?.delete()
        evidenceStatus = "正在等待手动采集"
        evidenceRequested.set(true)
    }

    fun lastEvidenceStatus(): String = evidenceStatus
    fun latestEvidenceZip(): File? = SingleFrameEvidence.lastFile(appContext)
    @Volatile private var engine: PaddleOCR? = null
    @Volatile private var released = false
    @Volatile private var fastScanMode = false
    // Optional 90/270 degree correction in the live text scanner.
    // Manual correction performs just ONE normal OCR inference.
    @Volatile private var liveTextRotationDegrees = 0
    @Volatile private var readingFocusMode = ReadingFocus.Mode.AUTO
    private val focusTracker = ReadingFocus.Tracker()
    private val layoutAi = LayoutAiEngine(appContext)
    private val layoutReuse = LayoutAiReuseCoordinator(layoutAi)
    @Volatile private var aiLayoutEnabled = false
    fun setAiLayoutEnabled(enabled: Boolean) {
        if (aiLayoutEnabled != enabled) layoutReuse.reset()
        aiLayoutEnabled = enabled
    }
    fun setFastScanMode(enabled: Boolean) { fastScanMode = enabled }
    fun setLiveTextRotationDegrees(degrees: Int) {
        liveTextRotationDegrees = if (degrees == 90 || degrees == 270) degrees else 0
    }
    fun setReadingFocusMode(mode: String) {
        readingFocusMode = try { ReadingFocus.Mode.valueOf(mode) }
            catch (_: IllegalArgumentException) { ReadingFocus.Mode.AUTO }
        focusTracker.reset()
        layoutReuse.reset()
    }

    fun isReady(): Boolean = engine != null && !released

    fun diagnoseNv21(
        data: ByteArray, width: Int, height: Int,
        enhancedPreprocessing: Boolean, legacyNv21Jpeg: Boolean,
        onResult: OcrDiagnosticCallback, onError: OcrErrorCallback
    ) {
        val current = engine ?: run { onError.onError("OCR 引擎尚未初始化"); return }
        if (released || !busy.compareAndSet(false, true)) {
            onError.onError("OCR 引擎正忙，请稍后重试")
            return
        }
        scope.launch {
            try {
                require(width > 0 && height > 0 && (width and 1) == 0 && (height and 1) == 0) {
                    "摄像头画面尺寸无效：${width}×${height}"
                }
                require(width.toLong() * height.toLong() * 3L / 2L == data.size.toLong()) {
                    "摄像头 NV21 帧长度不正确"
                }
                var bitmap = nv21ToBitmap(data, width, height, legacyNv21Jpeg)
                val decodedBitmap = bitmap
                try {
                    if (enhancedPreprocessing) {
                        val enhanced = enhanceForOcr(bitmap)
                        if (enhanced !== bitmap) { bitmap = enhanced }
                    }
                    val performanceBefore = OcrHardwareProbe.capture(appContext)
                    val startedNanos = System.nanoTime()
                    val result = current.recognize(bitmap)
                    val originalOcrWallMs = (System.nanoTime() - startedNanos) / 1_000_000L
                    // Second real Small REC only inside MANUAL diagnostics.
                    // Candidate output NEVER replaces live recognition/TTS.
                    val shadow = inspectVerticalRescues(current, bitmap, result.results,
                        OcrHardwareProbe.safeShadowBudget(performanceProfile, performanceBefore))
                    val performanceAfter = OcrHardwareProbe.capture(appContext)
                    val profileDiagnostic = "X200实验档：${performanceProfile.label}" +
                        "；CPU线程=${performanceProfile.cpuThreads}，REC批=${performanceProfile.recBatchSize}" +
                        "；本轮普通OCR墙钟=${originalOcrWallMs}ms" +
                        "\n推理前：${performanceBefore.describe()}" +
                        "\n推理后：${performanceAfter.describe()}" +
                        "\n本帧影子二次识别：$shadow" +
                        "\n说明：这是用户手动诊断，候选绝不会自动写入正式朗读。"
                    if (evidenceRequested.getAndSet(false)) {
                        evidenceStatus = try {
                            val saved = SingleFrameEvidence.create(appContext, data, width, height,
                                decodedBitmap, bitmap, enhancedPreprocessing, modelId, result,
                                performanceProfile)
                            "已在手机本地采集：${saved.name}，点击「导出诊断 ZIP」选择保存位置"
                        } catch (e: Exception) {
                            Log.e(TAG, "One-shot evidence export failed", e)
                            "单帧证据保存失败：${e.message ?: e.javaClass.simpleName}"
                        }
                    }
                    // A diagnostic must measure fresh model inference, not a cached result.
                    layoutReuse.reset()
                    val layout = layoutAi.analyze(bitmap, aiLayoutEnabled &&
                        readingFocusMode == ReadingFocus.Mode.AUTO, result.results.size)
                    val focus = arrangeInReadingOrder(result.results, bitmap.width,
                        bitmap.height, layout.regions)
                    // Candidate ordering is diagnostic only. Never change live TTS here.
                    val candidate = inspectCandidateOrder(result.results, layout.regions)
                    val text = focus.text
                    val confidence = if (result.results.isEmpty()) 0.0
                        else result.results.map { it.confidence.toDouble() }.average()
                    val rawDetails = result.results.mapIndexed { index, item ->
                        val points = item.box.points.joinToString(";") { point ->
                            "(${"%.1f".format(java.util.Locale.US, point.x)},${"%.1f".format(java.util.Locale.US, point.y)})"
                        }
                        "${index + 1}. conf=${"%.4f".format(java.util.Locale.US, item.confidence)} box=$points raw=${item.text}"
                    }.joinToString("\n").ifEmpty { "（SDK 未返回任何识别框）" }
                    val report = OcrDiagnosticResult(
                        text = text,
                        rawDetails = rawDetails,
                        modelId = modelId,
                        boxCount = result.results.size,
                        averageConfidence = confidence,
                        detectionTimeMs = result.detectionTimeMs.toLong(),
                        recognitionTimeMs = result.recognitionTimeMs.toLong(),
                        totalTimeMs = result.totalTimeMs.toLong(),
                        detectorInputShape = result.detInputShape.joinToString("×"),
                        recognitionInputShapes = result.recInputShapes.joinToString("；") { it.joinToString("×") },
                        focusDescription = focus.description + inspectVerifiedOrder(result.results, layout.regions),
                        fullPageText = ReadingOrder.arrange(toReadingBoxes(result.results)),
                        layoutStatus = layout.status,
                        layoutRegions = layout.regions.size,
                        layoutElapsedMs = layout.elapsedMs,
                        layoutReuseStats = layoutReuse.statistics(),
                        candidateText = candidate.first,
                        candidateDetails = candidate.second,
                        // Exactly the geometry-only order used by the new
                        // real-time scanner; no additional ONNX inference.
                        realtimeText = RealtimeTextOrientation.arrange(
                            toReadingBoxes(result.results), bitmap.width, bitmap.height),
                        x200ProfileReport = profileDiagnostic
                    )
                    withContext(Dispatchers.Main) { onResult.onResult(report) }
                } finally {
                    if (bitmap !== decodedBitmap) bitmap.recycle()
                    decodedBitmap.recycle()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "OCR diagnostic failed", t)
                withContext(Dispatchers.Main) { onError.onError(t.message ?: t.javaClass.simpleName) }
            } finally { busy.set(false) }
        }
    }

    /**
     * Explicit diagnostic-only same-frame REC rescue. The first recognized text
     * is NEVER altered. Candidate is produced by the same pinned offline model,
     * through the isolated SDK Y-sorted crop API (no repeat DBNet pass).
     */
    private suspend fun inspectVerticalRescues(
        current: PaddleOCR, bitmap: Bitmap, originals: List<OCRResult>, budget: Int
    ): String {
        if (budget <= 0) return "本档不额外识别，或设备热/内存状态要求回退"
        val selected = mutableListOf<Pair<Int, Pair<OCRResult, VerticalCropAxisGuard.Inspection>>>()
        for ((index, item) in originals.withIndex()) {
            if (selected.size >= budget) break
            val pts = item.box.points
            if (pts.size != 4) continue
            val source = org.opencv.core.MatOfPoint2f()
            try {
                source.fromList(pts.map {
                    org.opencv.core.Point(it.x.toDouble(), it.y.toDouble())
                })
                val minRect = Imgproc.minAreaRect(source)
                val rect = Array(4) { org.opencv.core.Point() }
                minRect.points(rect)
                val q = FloatArray(8)
                for (i in rect.indices) {
                    q[2 * i] = rect[i].x.toFloat()
                    q[2 * i + 1] = rect[i].y.toFloat()
                }
                val evidence = VerticalCropAxisGuard.inspect(q)
                if (VerticalRecRescuePolicy.shouldRetry(evidence, item.text,
                        selected.size, budget))
                    selected.add(Pair(index, Pair(item, evidence)))
            } catch (t: RuntimeException) {
                Log.w(TAG, "Skip invalid shadow geometry", t)
            } finally {
                source.release()
            }
        }
        if (selected.isEmpty()) return "0个满足严格条件的竖排风险框（无额外推理）"
        val stamp = System.nanoTime()
        return try {
            val candidates = current.recognizeVerticalCandidates(bitmap,
                selected.map { it.second.first.box })
            val elapsedMs = (System.nanoTime() - stamp) / 1_000_000L
            val lines = selected.mapIndexed { i, pair ->
                val candidate = candidates.getOrNull(i)
                val old = pair.second.first
                val decision = VerticalRecRescuePolicy.decide(pair.second.second,
                    old.text, candidate?.first, candidate?.second ?: 0f, i, budget)
                val newText = candidate?.first ?: ""
                "框#${pair.first + 1} 原「${old.text}」→候选「$newText」" +
                    "，候选conf=${candidate?.second ?: 0f}" +
                    "，单框耗时=${candidate?.third ?: -1}ms" +
                    "，保守门控=${decision.reason}（仅提示）"
            }
            "${selected.size}次真实额外Small REC，合计${elapsedMs}ms\n" + lines.joinToString("\n")
        } catch (t: Throwable) {
            Log.e(TAG, "Shadow vertical REC failed; baseline OCR kept", t)
            "附加REC出错（${t.javaClass.simpleName}），原OCR完整保留"
        }
    }

    fun initialize() {
        scope.launch {
            try {
                check(OpenCVUtils.init(appContext)) { "OpenCV 初始化失败" }
                // Keep detector input bounded for mobile devices. The previous SDK defaults
                // (64/min, 4000 max) left a 1080p frame almost untouched, creating a very
                // large detector tensor and unnecessary CPU/memory pressure.
                // Keep detector preprocessing/post-processing paired with the selected model.
                // PP-OCRv6 ONNX inference.yml: thresh=0.20, box_thresh=0.45, unclip=1.40.
                // PP-OCRv5 Mobile ONNX inference.yml: resize_long=960, 0.30/0.60/1.50.
                val isV5 = modelId == "ppocrv5_mobile"
                val ocrConfig = PaddleOCRConfig(
                    detLimitSideLen = if (isV5) 960 else 1600,
                    detLimitType = "max",
                    detMaxSideLimit = if (isV5) 960 else 1600,
                    detThresh = if (isV5) 0.30f else 0.20f,
                    detBoxThresh = if (isV5) 0.60f else 0.45f,
                    detUnclipRatio = if (isV5) 1.50f else 1.40f,
                    // Do not discard recognition rows before diagnostics can inspect them.
                    recScoreThresh = 0.0f,
                    recBatchSize = performanceProfile.recBatchSize
                )
                val engineConfig = EngineConfig(numThreads = performanceProfile.cpuThreads)
                val modelPrefix = when (modelId) {
                    "ppocrv6_tiny" -> "models/ppocrv6_tiny"
                    "ppocrv5_mobile" -> "models/ppocrv5_mobile"
                    else -> "models"
                }
                val created = PaddleOCR.create(
                    context = appContext,
                    config = ocrConfig,
                    engineConfig = engineConfig,
                    detModelAssetPath = "$modelPrefix/det/inference.onnx",
                    recModelAssetPath = "$modelPrefix/rec/inference.onnx",
                    recConfigAssetPath = "$modelPrefix/rec/inference.yml"
                )
                if (released) {
                    created.release()
                    return@launch
                }
                engine = created
                withContext(Dispatchers.Main) { listener.onReady() }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { listener.onError(t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    fun recognizeNv21(
        data: ByteArray,
        width: Int,
        height: Int,
        enhancedPreprocessing: Boolean,
        legacyNv21Jpeg: Boolean,
        onResult: OcrResultCallback,
        onError: OcrErrorCallback
    ) {
        val current = engine ?: run {
            onResult.onResult(null)
            return
        }
        if (released || !busy.compareAndSet(false, true)) {
            onResult.onResult(null)
            return
        }
        scope.launch {
            try {
                require(width > 0 && height > 0 && (width and 1) == 0 && (height and 1) == 0) {
                    "无效的摄像头画面尺寸：${width}×${height}"
                }
                val expectedBytes = width.toLong() * height.toLong() * 3L / 2L
                require(expectedBytes == data.size.toLong()) {
                    "NV21 数据长度不匹配：收到 ${data.size} 字节，预期 ${expectedBytes} 字节"
                }
                var bitmap = nv21ToBitmap(data, width, height, legacyNv21Jpeg)
                val liveAngle = if (fastScanMode) liveTextRotationDegrees else 0
                try {
                    if (liveAngle != 0) {
                        val transform = Matrix()
                        transform.postRotate(liveAngle.toFloat())
                        val rotated = Bitmap.createBitmap(bitmap, 0, 0,
                            bitmap.width, bitmap.height, transform, true)
                        if (rotated !== bitmap) {
                            bitmap.recycle()
                            bitmap = rotated
                        }
                    }
                    if (enhancedPreprocessing) {
                        val enhanced = enhanceForOcr(bitmap)
                        if (enhanced !== bitmap) {
                            bitmap.recycle()
                            bitmap = enhanced
                        }
                    }
                    var result = current.recognize(bitmap)
                    // Retry only low-confidence frames. This avoids paying for enhancement
                    // on every frame while giving difficult text a second preprocessing pass.
                    if (!fastScanMode && !enhancedPreprocessing && shouldRetryWithEnhancement(result.results)) {
                        val retryBitmap = enhanceForOcr(bitmap)
                        if (retryBitmap !== bitmap) {
                            try {
                                val retryResult = current.recognize(retryBitmap)
                                if (recognitionQuality(retryResult.results) > recognitionQuality(result.results)) {
                                    result = retryResult
                                }
                            } finally {
                                retryBitmap.recycle()
                            }
                        }
                    }
                    // Fast real-time recognition must not wait for a
                    // separate document-layout model. It uses just OCR's
                    // existing quadrilaterals and top-first coherent flows.
                    // Keep the old BOOK/diagnostic flow available separately.
                    val realtime = fastScanMode
                    val layout = layoutReuse.analyze(data, width, height, bitmap,
                        !realtime && aiLayoutEnabled &&
                            readingFocusMode == ReadingFocus.Mode.AUTO,
                        result.results.size)
                    val text: String
                    val flowDescription: String
                    if (realtime) {
                        text = RealtimeTextOrientation.arrange(
                            toReadingBoxes(result.results), bitmap.width, bitmap.height)
                        flowDescription = "实时文字：按文字方向、从上至下连续朗读（输入旋转${liveAngle}°）"
                    } else {
                        val focus = arrangeInReadingOrder(result.results, bitmap.width,
                            bitmap.height, layout.regions)
                        text = focus.text
                        flowDescription = focus.description
                    }
                    val verification = if (!realtime && aiLayoutEnabled &&
                        readingFocusMode == ReadingFocus.Mode.AUTO)
                        inspectVerifiedOrder(result.results, layout.regions) else ""
                    Log.i(TAG, "Reading focus: $flowDescription; layout=${layout.status}; " +
                        "layoutMs=${layout.elapsedMs}; ordering=$verification")
                    val averageConfidence = if (result.results.isEmpty()) 0.0
                        else result.results.map { it.confidence }.average()
                    val message = "frame=${width}x${height}, ocrInputRotation=${liveAngle}, " +
                        "bitmap=${bitmap.width}x${bitmap.height}, boxes=${result.results.size}, " +
                        "avgConfidence=${"%.3f".format(java.util.Locale.US, averageConfidence)}, " +
                        "det=${result.detectionTimeMs}ms, rec=${result.recognitionTimeMs}ms, " +
                        "total=${result.totalTimeMs}ms"
                    if (averageConfidence < 0.55 || result.results.isEmpty()) {
                        Log.w(TAG, message)
                    } else {
                        Log.i(TAG, message)
                    }
                    val boxed = result.results.mapNotNull { item ->
                        if (item.box.points.size != 4) null
                        else {
                            val p = item.box.points
                            val coordinates = FloatArray(8)
                            for (i in 0..3) {
                                val x = p[i].x
                                val y = p[i].y
                                // OCR polygons are on a rotated bitmap.
                                // Map them back to native camera coordinates.
                                coordinates[2 * i] = when (liveAngle) {
                                    90 -> y
                                    270 -> width - y
                                    else -> x
                                }
                                coordinates[2 * i + 1] = when (liveAngle) {
                                    90 -> height - x
                                    270 -> x
                                    else -> y
                                }
                            }
                            coordinates
                        }
                    }
                    val response = OcrReadResult(
                        text = text,
                        boxes = boxed,
                        frameWidth = width,
                        frameHeight = height,
                        boxCount = result.results.size,
                        averageConfidence = averageConfidence,
                        totalTimeMs = result.totalTimeMs.toLong()
                    )
                    withContext(Dispatchers.Main) { onResult.onResult(response) }
                } finally {
                    bitmap.recycle()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "OCR frame processing failed", t)
                withContext(Dispatchers.Main) { onError.onError(t.message ?: t.javaClass.simpleName) }
            } finally {
                busy.set(false)
            }
        }
    }


    private fun shouldRetryWithEnhancement(results: List<OCRResult>): Boolean {
        if (results.isEmpty()) return false
        val average = results.map { it.confidence }.average()
        val lowConfidenceCount = results.count { it.confidence < 0.55f }
        return average < 0.72 || lowConfidenceCount.toFloat() / results.size >= 0.40f
    }

    private fun recognitionQuality(results: List<OCRResult>): Float {
        if (results.isEmpty()) return -1f
        val average = results.map { it.confidence }.average().toFloat()
        val characters = results.sumOf { it.text.length }.coerceAtMost(200)
        return average + characters / 2000f
    }

    /** Optional grayscale denoise + local contrast + mild sharpening for difficult scenes. */
    private fun enhanceForOcr(source: Bitmap): Bitmap {
        val rgba = Mat()
        val gray = Mat()
        val denoised = Mat()
        val contrasted = Mat()
        val blurred = Mat()
        val sharpened = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.bilateralFilter(gray, denoised, 5, 35.0, 35.0)
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(denoised, contrasted)
            Imgproc.GaussianBlur(contrasted, blurred, Size(0.0, 0.0), 1.0)
            Core.addWeighted(contrasted, 1.30, blurred, -0.30, 0.0, sharpened)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(sharpened, output)
            return output
        } catch (_: Throwable) {
            // Keep the original frame if an enhancement operation is unsupported on a device.
            return source
        } finally {
            rgba.release()
            gray.release()
            denoised.release()
            contrasted.release()
            blurred.release()
            sharpened.release()
        }
    }

    /** OCR sees all pixels; only the final spoken text is focused. */
    private fun toReadingBoxes(results: List<OCRResult>): List<ReadingOrder.Box> =
        results.mapNotNull { item ->
            val p = item.box.points
            if (p.size != 4 || item.text.isBlank()) null
            else ReadingOrder.Box(
                item.text, item.confidence,
                floatArrayOf(p[0].x, p[1].x, p[2].x, p[3].x),
                floatArrayOf(p[0].y, p[1].y, p[2].y, p[3].y)
            )
        }

    private fun arrangeInReadingOrder(
        results: List<OCRResult>, width: Int, height: Int,
        layoutRegions: List<LayoutRegion> = emptyList()
    ): ReadingFocus.Selection {
        return focusTracker.choose(toReadingBoxes(results), width, height,
            readingFocusMode, layoutRegions)
    }

    /**
     * Produce a human-auditable candidate. All characters come from OCR and
     * each entry retains the original OCR index even if invalid boxes are skipped.
     */
    private fun inspectCandidateOrder(
        results: List<OCRResult>, regions: List<LayoutRegion>
    ): Pair<String, String> {
        return try {
            // Do not certify completeness after silently dropping a nonempty
            // OCR result whose quadrilateral is absent or has invalid numbers.
            val unpositioned = results.count { item ->
                item.text.isNotBlank() && (item.box.points.size != 4 ||
                    item.box.points.any { !it.x.isFinite() || !it.y.isFinite() })
            }
            if (unpositioned > 0) {
                return Pair("", "候选排序不可用：有${unpositioned}个非空OCR文字框坐标异常，不能保证全部原文保留")
            }
            val valid = results.mapIndexedNotNull { originalIndex, item ->
                val p = item.box.points
                if (p.size != 4 || item.text.isBlank()) null
                else Pair(originalIndex, ReadingOrder.Box(
                    item.text, item.confidence,
                    floatArrayOf(p[0].x, p[1].x, p[2].x, p[3].x),
                    floatArrayOf(p[0].y, p[1].y, p[2].y, p[3].y)
                ))
            }
            val raw = valid.map { (originalIndex, box) ->
                StrictOcrOrderGuard.Item(originalIndex, box.text)
            }
            val plan = LayoutReadingPlanner.plan(raw, valid.map { it.second }, regions)
            val lines = plan.placements.joinToString("\n") { placement ->
                val region = if (placement.regionId < 0) "几何回退"
                    else "区域#${placement.regionId + 1}"
                "${placement.readingIndex + 1}. 原始OCR框#${placement.ocrId + 1}" +
                    " [${region}] " + results[placement.ocrId].text
            }
            val state = if (plan.verified.accepted)
                "OCR框编号完整性校验通过（不代表候选阅读顺序正确）" else
                "OCR框编号完整性校验未通过，已退回OCR输入顺序"
            val strategy = if (plan.geometryGroupOrder)
                "几何分组排序（包含标题/边缘辅助分组）共${plan.geometryGroups}组"
                else "单区域/逐行几何排序（尚未可靠识别左右页）"
            val membership =
                "AI区域归属${plan.assignedBoxes}/${valid.size}个框，" +
                "实际使用${plan.distinctAssignedRegions}个独立AI区域。" +
                if (plan.distinctAssignedRegions < 2)
                    "仅一大区不能证明识别出双页。" else ""
            Pair(plan.verified.exactText(), state + "\n" +
                membership + "\n" + strategy + "\n" + lines)
        } catch (error: RuntimeException) {
            Pair("", "候选排序异常（${error.javaClass.simpleName}），保留原有阅读顺序")
        }
    }

    /**
     * Shadow-mode inspection. The planner may only return IDs of existing OCR
     * boxes. Do not change live speech before the actual camera samples pass.
     */
    private fun inspectVerifiedOrder(
        results: List<OCRResult>, regions: List<LayoutRegion>
    ): String {
        return try {
            val invalidCoordinates = results.count { item ->
                item.text.isNotBlank() && (item.box.points.size != 4 ||
                    item.box.points.any { !it.x.isFinite() || !it.y.isFinite() })
            }
            if (invalidCoordinates > 0) return "｜候选编号不完整：有${invalidCoordinates}个原始OCR框不可定位，未通过完整性审查"
            val boxes = toReadingBoxes(results)
            val ids = boxes.mapIndexed { i, b -> StrictOcrOrderGuard.Item(i, b.text) }
            val plan = LayoutReadingPlanner.plan(ids, boxes, regions)
            val strategy = if (plan.geometryGroupOrder)
                "几何${plan.geometryGroups}组" else "几何单区"
            val regionState = if (plan.distinctAssignedRegions < 2)
                "AI未成功分出双页" else "AI分出${plan.distinctAssignedRegions}区"
            "｜OCR框编号=${if (plan.verified.accepted) "完整" else "回退"}，" +
                "归属=${plan.assignedBoxes}/${boxes.size}，${regionState}，" +
                "${strategy}，候选顺序仅诊断"
        } catch (error: RuntimeException) {
            "｜阅读编号校验异常：${error.javaClass.simpleName}，维持原排序"
        }
    }

    /** Decode the camera's unrotated NV21 pixels; TextureView handles display transforms. */
    private fun nv21ToBitmap(data: ByteArray, width: Int, height: Int, legacyNv21Jpeg: Boolean): Bitmap {
        if (legacyNv21Jpeg) {
            val output = ByteArrayOutputStream()
            check(YuvImage(data, ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), 82, output)) {
                "0.7.0 兼容图像转换失败"
            }
            val bytes = output.toByteArray()
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("0.7.0 兼容图像解码失败")
        }
        val yuv = Mat(height + height / 2, width, CvType.CV_8UC1)
        val rgba = Mat()
        try {
            yuv.put(0, 0, data)
            Imgproc.cvtColor(yuv, rgba, Imgproc.COLOR_YUV2RGBA_NV21)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(rgba, it)
            }
        } finally {
            yuv.release()
            rgba.release()
        }
    }

    companion object {
        private const val TAG = "KanziOCR"
    }

    fun release() {
        released = true
        scope.launch {
            try { engine?.release() } catch (_: Throwable) {}
            layoutReuse.reset()
            try { layoutAi.release() } catch (_: Throwable) {}
            engine = null
            scope.cancel()
        }
    }
}
