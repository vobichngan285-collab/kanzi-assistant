package com.vobichngan.kanzi

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

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
                    val text = result.results.map { it.text.trim() }
                        .filter { it.isNotEmpty() }.joinToString("\n")
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

    fun release() {
        released = true
        scope.launch {
            try { engine?.release() } catch (_: Throwable) {}
            engine = null
            scope.cancel()
        }
    }
}
