package com.vobichngan.kanzi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
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

fun interface OcrResultCallback { fun onResult(text: String?) }
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
    val recognitionInputShapes: String
)
fun interface OcrDiagnosticCallback { fun onResult(result: OcrDiagnosticResult) }

class PaddleOcrEngine(
    context: Context,
    private val listener: Listener,
    private val modelId: String = "ppocrv6_small"
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
    @Volatile private var engine: PaddleOCR? = null
    @Volatile private var released = false

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
                try {
                    if (enhancedPreprocessing) {
                        val enhanced = enhanceForOcr(bitmap)
                        if (enhanced !== bitmap) { bitmap.recycle(); bitmap = enhanced }
                    }
                    val result = current.recognize(bitmap)
                    val text = arrangeInReadingOrder(result.results)
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
                        recognitionInputShapes = result.recInputShapes.joinToString("；") { it.joinToString("×") }
                    )
                    withContext(Dispatchers.Main) { onResult.onResult(report) }
                } finally { bitmap.recycle() }
            } catch (t: Throwable) {
                Log.e(TAG, "OCR diagnostic failed", t)
                withContext(Dispatchers.Main) { onError.onError(t.message ?: t.javaClass.simpleName) }
            } finally { busy.set(false) }
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
                    recBatchSize = 1
                )
                val engineConfig = EngineConfig(numThreads = 2)
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
                try {
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
                    if (!enhancedPreprocessing && shouldRetryWithEnhancement(result.results)) {
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
                    val text = arrangeInReadingOrder(result.results)
                    val averageConfidence = if (result.results.isEmpty()) 0.0
                        else result.results.map { it.confidence }.average()
                    val message = "frame=${width}x${height}, ocrInputDirection=raw0, " +
                        "bitmap=${bitmap.width}x${bitmap.height}, boxes=${result.results.size}, " +
                        "avgConfidence=${"%.3f".format(java.util.Locale.US, averageConfidence)}, " +
                        "det=${result.detectionTimeMs}ms, rec=${result.recognitionTimeMs}ms, " +
                        "total=${result.totalTimeMs}ms"
                    if (averageConfidence < 0.55 || result.results.isEmpty()) {
                        Log.w(TAG, message)
                    } else {
                        Log.i(TAG, message)
                    }
                    withContext(Dispatchers.Main) { onResult.onResult(text) }
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

    /**
     * Do not silently discard recognized lines on a fixed confidence threshold.
     * The reading-order helper handles slanted quadrilaterals and clear two-column pages.
     */
    private fun arrangeInReadingOrder(results: List<OCRResult>): String {
        val boxes = results.mapNotNull { item ->
            val p = item.box.points
            if (p.size != 4 || item.text.isBlank()) null
            else ReadingOrder.Box(
                item.text, item.confidence,
                floatArrayOf(p[0].x, p[1].x, p[2].x, p[3].x),
                floatArrayOf(p[0].y, p[1].y, p[2].y, p[3].y)
            )
        }
        return ReadingOrder.arrange(boxes)
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
            engine = null
            scope.cancel()
        }
    }
}
