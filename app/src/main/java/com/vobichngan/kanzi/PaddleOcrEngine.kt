package com.vobichngan.kanzi

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun interface OcrResultCallback { fun onResult(text: String?) }
fun interface OcrErrorCallback { fun onError(message: String) }

class PaddleOcrEngine(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onReady()
        fun onError(message: String)
    }

    private data class PositionedText(
        val item: OCRResult,
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

    fun initialize() {
        scope.launch {
            try {
                check(OpenCVUtils.init(appContext)) { "OpenCV 初始化失败" }
                val created = PaddleOCR.create(
                    context = appContext,
                    config = PaddleOCRConfig(recBatchSize = 1),
                    engineConfig = EngineConfig(numThreads = 2),
                    detModelAssetPath = "models/det/inference.onnx",
                    recModelAssetPath = "models/rec/inference.onnx",
                    recConfigAssetPath = "models/rec/inference.yml"
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
                val output = ByteArrayOutputStream()
                YuvImage(data, ImageFormat.NV21, width, height, null)
                    .compressToJpeg(Rect(0, 0, width, height), 82, output)
                val bytes = output.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("摄像头画面解码失败")
                try {
                    val result = current.recognize(bitmap)
                    val text = arrangeInReadingOrder(result.results)
                    withContext(Dispatchers.Main) { onResult.onResult(text) }
                } finally {
                    bitmap.recycle()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.onError(t.message ?: t.javaClass.simpleName) }
            } finally {
                busy.set(false)
            }
        }
    }

    // Rebuild lines from text-box coordinates; do not rely only on result-list order.
    private fun arrangeInReadingOrder(results: List<OCRResult>): String {
        if (results.isEmpty()) return ""

        val positioned = results.mapNotNull { item ->
            val text = item.text.trim()
            if (text.isEmpty() || item.box.points.size != 4) return@mapNotNull null
            val xs = item.box.points.map { it.x }
            val ys = item.box.points.map { it.y }
            val left = xs.minOrNull() ?: return@mapNotNull null
            val top = ys.minOrNull() ?: return@mapNotNull null
            val bottom = ys.maxOrNull() ?: return@mapNotNull null
            PositionedText(item, left, top, bottom, (top + bottom) / 2f, max(1f, bottom - top))
        }.sortedWith(compareBy<PositionedText> { it.centerY }.thenBy { it.left })

        val rows = mutableListOf<TextLine>()
        for (entry in positioned) {
            var bestRow: TextLine? = null
            var bestDistance = Float.MAX_VALUE
            for (row in rows) {
                val overlap = max(0f, min(row.bottom, entry.bottom) - max(row.top, entry.top))
                val smallerHeight = max(1f, min(row.bottom - row.top, entry.height))
                val overlapRatio = overlap / smallerHeight
                val distance = abs(row.centerY - entry.centerY)
                val sameRow = overlapRatio >= 0.30f ||
                    distance <= max(7f, smallerHeight * 0.48f)
                if (sameRow && distance < bestDistance) {
                    bestRow = row
                    bestDistance = distance
                }
            }

            if (bestRow == null) {
                rows.add(TextLine(mutableListOf(entry), entry.top, entry.bottom, entry.centerY))
            } else {
                bestRow.items.add(entry)
                bestRow.top = min(bestRow.top, entry.top)
                bestRow.bottom = max(bestRow.bottom, entry.bottom)
                bestRow.centerY = (bestRow.top + bestRow.bottom) / 2f
            }
        }

        return rows.sortedBy { it.centerY }.joinToString("\n") { row ->
            val fragments = row.items.sortedBy { it.left }.map { it.item.text.trim() }
            fragments.fold("") { combined, fragment ->
                if (combined.isEmpty()) fragment
                else {
                    val previous = combined.last()
                    val next = fragment.firstOrNull() ?: ' '
                    val needsSpace = previous.isAsciiWordChar() && next.isAsciiWordChar()
                    combined + (if (needsSpace) " " else "") + fragment
                }
            }
        }.trim()
    }

    private fun Char.isAsciiWordChar(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    fun release() {
        released = true
        scope.launch {
            try { engine?.release() } catch (_: Throwable) {}
            engine = null
            scope.cancel()
        }
    }
}
