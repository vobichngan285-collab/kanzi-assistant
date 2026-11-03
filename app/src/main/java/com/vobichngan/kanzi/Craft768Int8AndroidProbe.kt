package com.vobichngan.kanzi

import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * SOURCE-COMPILE ONLY: standalone candidate for loading CRAFT 768 S8S8 QDQ on Android.
 *
 * Not used by MainActivity, PaddleOcrEngine, capture code, or voice; no model
 * is bundled and no application APK is built by the research workflow.
 * The experimental model must exist in private app storage and have exactly
 * the SHA of the 9-DEV-photo-calibrated quantization run. This is not a proof
 * of Android load or on-device latency without an actual instrumented device.
 */
internal class Craft768Int8AndroidProbe(private val context: Context) : Closeable {
    internal data class LoadMetrics(
        val checksumVerificationMs: Long,
        val onnxSessionLoadMs: Long,
        val modelBytes: Long,
        val pssAfterLoadKb: Long,
        val nativeHeapAfterLoadKb: Long
    )

    internal data class InferenceMetrics(
        val inputWidth: Int,
        val inputHeight: Int,
        val outputShape: List<Long>,
        val preprocessingAndInferenceMs: Long,
        val inferenceOnlyMs: Long,
        val pssAfterInferenceKb: Long,
        val nativeHeapAfterInferenceKb: Long,
        val sampleOutput: Float
    )

    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String? = null

    @Synchronized
    fun load(privateModel: File): LoadMetrics {
        val privateRoot = context.filesDir.canonicalFile.toPath()
        val candidate = privateModel.canonicalFile.toPath()
        require(candidate.startsWith(privateRoot) && candidate != privateRoot) {
            "CRAFT model must be in this app's private files directory"
        }
        val file = candidate.toFile()
        require(file.isFile && file.length() == EXPECTED_BYTES) {
            "CRAFT INT8 test model must be the exact validated 20.95MB model"
        }
        val checksumStart = System.nanoTime()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { source ->
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val size = source.read(chunk)
                if (size < 0) break
                if (size > 0) digest.update(chunk, 0, size)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(sha == EXPECTED_SHA256) {
            "CRAFT INT8 model SHA-256 differs from tested QDQ weight"
        }
        val checksumMs = (System.nanoTime() - checksumStart) / 1_000_000L

        close()
        val started = System.nanoTime()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
        }
        try {
            val created = env.createSession(file.absolutePath, options)
            try {
                val name = created.inputNames.singleOrNull()
                    ?: error("Expected exactly one CRAFT image input")
                val meta = created.inputInfo[name]?.info as? TensorInfo
                    ?: error("Expected CRAFT tensor input")
                val dims = meta.shape
                require(dims.size == 4 && dims[0] == 1L && dims[1] == 3L) {
                    "Unexpected CRAFT input dims: ${dims.contentToString()}"
                }
                session = created
                inputName = name
            } catch (failure: Throwable) {
                created.close()
                throw failure
            }
        } finally {
            options.close()
        }
        return LoadMetrics(
            checksumVerificationMs = checksumMs,
            onnxSessionLoadMs = (System.nanoTime() - started) / 1_000_000L,
            modelBytes = file.length(),
            pssAfterLoadKb = Debug.getPss(),
            nativeHeapAfterLoadKb = Debug.getNativeHeapAllocatedSize() / 1024L
        )
    }

    /** Runs only the raw [1,H,W,2] CRAFT network; NOT text box reconstruction or Small REC. */
    @Synchronized
    fun forward(bitmap: Bitmap): InferenceMetrics {
        val loaded = session ?: error("Model not loaded; call load() first")
        val name = inputName ?: error("CRAFT image input missing")
        require(bitmap.width > 0 && bitmap.height > 0)

        val start = System.nanoTime()
        val ratio = min(1.0, INPUT_LONG_SIDE.toDouble() / max(bitmap.width, bitmap.height))
        val w = min(INPUT_LONG_SIDE, max(32, ceil(bitmap.width * ratio / 32.0).toInt() * 32))
        val h = min(INPUT_LONG_SIDE, max(32, ceil(bitmap.height * ratio / 32.0).toInt() * 32))
        val resized = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        try {
            resized.getPixels(pixels, 0, w, 0, 0, w, h)
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
        val plane = w * h
        val values = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            values[i] = ((pixel shr 16 and 255) / 255f - .485f) / .229f
            values[plane + i] = ((pixel shr 8 and 255) / 255f - .456f) / .224f
            values[2 * plane + i] = ((pixel and 255) / 255f - .406f) / .225f
        }
        val tensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(values), longArrayOf(1L, 3L, h.toLong(), w.toLong())
        )
        var outputShape = emptyList<Long>()
        var sample = Float.NaN
        var forwardMs = 0L
        tensor.use { onnxInput ->
            val inferenceStart = System.nanoTime()
            loaded.run(mapOf(name to onnxInput)).use { result ->
                val out = result[0] as? OnnxTensor
                    ?: error("Expected CRAFT float tensor output")
                val shape = (out.info as? TensorInfo)?.shape
                    ?: error("Expected CRAFT output shape")
                outputShape = shape.toList()
                require(shape.size == 4 && shape[0] == 1L && shape[3] == 2L) {
                    "Unexpected CRAFT output dims: ${shape.contentToString()}"
                }
                sample = out.floatBuffer.get(0)
                require(sample.isFinite()) { "CRAFT INT8 produced nonfinite output" }
                forwardMs = (System.nanoTime() - inferenceStart) / 1_000_000L
            }
        }
        return InferenceMetrics(
            inputWidth = w,
            inputHeight = h,
            outputShape = outputShape,
            preprocessingAndInferenceMs = (System.nanoTime() - start) / 1_000_000L,
            inferenceOnlyMs = forwardMs,
            pssAfterInferenceKb = Debug.getPss(),
            nativeHeapAfterInferenceKb = Debug.getNativeHeapAllocatedSize() / 1024L,
            sampleOutput = sample
        )
    }

    @Synchronized
    override fun close() {
        session?.close()
        session = null
        inputName = null
    }

    companion object {
        const val INPUT_LONG_SIDE = 768
        const val EXPECTED_BYTES = 20_946_608L
        const val EXPECTED_SHA256 = "9ab885d2f6669b77f70e9c8f68acfbe408c0b051501fd02d94bcaa0c7779ab15"
    }
}
