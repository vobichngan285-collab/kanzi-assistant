package com.vobichngan.kanzi

import android.graphics.Bitmap
import android.os.Debug
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Isolated CRAFT768 Android ONNX Runtime compile/load experiment ONLY.
 *
 * This class is not instantiated by MainActivity/PaddleOcrEngine, so it cannot
 * change current OCR or speech output. No model weight is packaged in the app.
 * An actual Android device must explicitly call load() with a verified model
 * stored on the device, then run() with camera bitmaps. CI compiles but cannot
 * claim device inference or first-word speech latency.
 *
 * Runs only the raw model forward pass; text box postprocessing and Small REC
 * remain in the separate paired Python real-photo experiments.
 */
internal class Craft768AndroidProbe : Closeable {
    internal data class LoadReport(
        val loadMs: Long,
        val modelBytes: Long,
        val pssKb: Long,
        val nativeHeapKb: Long
    )

    internal data class ForwardReport(
        val originalWidth: Int,
        val originalHeight: Int,
        val inputWidth: Int,
        val inputHeight: Int,
        val outputShape: List<Long>,
        val forwardMs: Long,
        val totalPrepareAndForwardMs: Long,
        val pssKb: Long,
        val nativeHeapKb: Long,
        val firstOutputValue: Float
    )

    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String? = null
    private val limitSide: Int = 768

    /** A model MUST be obtained via external, SHA256-checked app-private file. */
    @Synchronized fun load(modelFile: File): LoadReport {
        require(modelFile.isFile && modelFile.length() > 70_000_000L) {
            "CRAFT ONNX file missing, truncated or not available from app-private storage"
        }
        close()
        val started = System.nanoTime()
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        try {
            val newSession = environment.createSession(modelFile.absolutePath, opts)
            val name = newSession.inputNames.singleOrNull()
                ?: error("CRAFT should expose exactly one image input")
            session = newSession
            inputName = name
        } finally {
            opts.close()
        }
        return LoadReport(
            loadMs = (System.nanoTime() - started) / 1_000_000L,
            modelBytes = modelFile.length(),
            pssKb = Debug.getPss(),
            nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024L
        )
    }

    @Synchronized fun run(image: Bitmap): ForwardReport {
        val loaded = session ?: error("Call load() before evaluating the CRAFT model")
        val input = inputName ?: error("CRAFT input name missing")
        require(image.width > 0 && image.height > 0)

        val started = System.nanoTime()
        // Exactly the same 768 max long-side, rounded-up multiple-of-32
        // policy as scripts/craft_small_detector_ab.py (no upscaling).
        val ratio = min(1.0, limitSide.toDouble() / max(image.width, image.height))
        val inputWidth = min(limitSide, max(32, ceil(image.width * ratio / 32.0).toInt() * 32))
        val inputHeight = min(limitSide, max(32, ceil(image.height * ratio / 32.0).toInt() * 32))
        val scaled = Bitmap.createScaledBitmap(image, inputWidth, inputHeight, true)
        val pixels = IntArray(inputWidth * inputHeight)
        try {
            scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        } finally {
            if (scaled !== image) scaled.recycle()
        }
        val plane = inputWidth * inputHeight
        val tensorData = FloatArray(3 * plane)
        for (k in pixels.indices) {
            val rgb = pixels[k]
            tensorData[k] = ((rgb shr 16 and 0xff) / 255.0f - .485f) / .229f
            tensorData[plane + k] = ((rgb shr 8 and 0xff) / 255.0f - .456f) / .224f
            tensorData[2 * plane + k] = ((rgb and 0xff) / 255.0f - .406f) / .225f
        }
        val tensor = OnnxTensor.createTensor(
            environment, FloatBuffer.wrap(tensorData),
            longArrayOf(1L, 3L, inputHeight.toLong(), inputWidth.toLong())
        )
        var outputShape: List<Long> = emptyList()
        var sample = Float.NaN
        val forwardStarted = System.nanoTime()
        var forwardElapsed = 0L
        tensor.use { inputTensor ->
            loaded.run(mapOf(input to inputTensor)).use { result ->
                val outTensor = result[0] as? OnnxTensor
                    ?: error("Expected CRAFT float ONNX tensor as first output")
                val info = outTensor.info as? TensorInfo
                    ?: error("Expected float tensor metadata")
                outputShape = info.shape.toList()
                require(outputShape.size == 4 && outputShape[0] == 1L &&
                    outputShape[3] == 2L) {
                    "CRAFT raw heatmap must be [1,H,W,2], got $outputShape"
                }
                sample = outTensor.floatBuffer.get(0)
                forwardElapsed = (System.nanoTime() - forwardStarted) / 1_000_000L
            }
        }
        return ForwardReport(
            originalWidth = image.width, originalHeight = image.height,
            inputWidth = inputWidth, inputHeight = inputHeight,
            outputShape = outputShape,
            forwardMs = forwardElapsed,
            totalPrepareAndForwardMs = (System.nanoTime() - started) / 1_000_000L,
            pssKb = Debug.getPss(),
            nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024L,
            firstOutputValue = sample
        )
    }

    @Synchronized override fun close() {
        session?.close()
        session = null
        inputName = null
    }
}
