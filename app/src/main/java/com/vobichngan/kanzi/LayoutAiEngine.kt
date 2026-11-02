package com.vobichngan.kanzi

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.min

/** Optional on-device PP-DocLayout-S detector. No network calls and no text generation. */
class LayoutAiEngine(private val context: Context) {
    data class Result(
        val regions: List<LayoutRegion>,
        val elapsedMs: Long,
        val status: String
    )
    @Volatile private var disabled = false
    private var session: OrtSession? = null
    private val env by lazy { OrtEnvironment.getEnvironment() }

    @Synchronized
    fun analyze(bitmap: Bitmap, enabled: Boolean, ocrBoxes: Int): Result {
        if (!enabled) return Result(emptyList(), 0, "关闭（仅使用几何规则）")
        if (ocrBoxes < 10) return Result(emptyList(), 0, "版面简单，跳过 AI")
        if (disabled) return Result(emptyList(), 0, "AI 不可用，已回退几何规则")
        val started = System.nanoTime()
        return try {
            val current = session ?: run {
                // The build pipeline pins both the source and SHA256 of this
                // 4.9 MB ONNX model. When missing or incompatible, geometry
                // remains usable instead of leaving camera reading broken.
                val bytes = context.assets.open("layout/pp_doclayout_s.onnx").use { it.readBytes() }
                val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
                env.createSession(bytes, options).also { session = it }
            }
            val w = bitmap.width
            val h = bitmap.height
            val size = 480
            val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
            val pixels = IntArray(size * size)
            try { scaled.getPixels(pixels, 0, size, 0, 0, size, size) }
            finally { if (scaled !== bitmap) scaled.recycle() }

            val chw = FloatArray(3 * size * size)
            val plane = size * size
            for (i in pixels.indices) {
                val c = pixels[i]
                val red = (c shr 16 and 255) / 255f
                val green = (c shr 8 and 255) / 255f
                val blue = (c and 255) / 255f
                chw[i] = (red - .485f) / .229f
                chw[plane+i] = (green - .456f) / .224f
                chw[2*plane+i] = (blue - .406f) / .225f
            }

            val input = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw),
                longArrayOf(1,3,size.toLong(),size.toLong()))
            val scale = OnnxTensor.createTensor(env, FloatBuffer.wrap(
                floatArrayOf(size.toFloat()/h, size.toFloat()/w)), longArrayOf(1,2))
            val regions = input.use { imageTensor ->
                scale.use { scaleTensor ->
                    current.run(mapOf("image" to imageTensor, "scale_factor" to scaleTensor)).use { output ->
                        // PicoDet export contract: first [M,6] rows
                        // [class,confidence,left,top,right,bottom];
                        // second output is count. The graph performs NMS.
                        val raw = output[0].value
                        val rawCount = output[1].value
                        val n = when (rawCount) {
                            is IntArray -> rawCount.firstOrNull() ?: 0
                            is LongArray -> (rawCount.firstOrNull() ?: 0L).toInt()
                            is Number -> rawCount.toInt()
                            is Array<*> -> when (val first = rawCount.firstOrNull()) {
                                is IntArray -> first.firstOrNull() ?: 0
                                is LongArray -> (first.firstOrNull() ?: 0L).toInt()
                                is Number -> first.toInt()
                                else -> 0
                            }
                            else -> 0
                        }
                        val rows = when(raw) {
                            is Array<*> -> raw
                            else -> emptyArray<Any>()
                        }
                        val detections = ArrayList<LayoutRegion>()
                        for (i in 0 until min(min(n, rows.size), 80)) {
                            val row = rows[i] as? FloatArray ?: continue
                            if (row.size < 6 || row[1] < .53f) continue
                            val left = row[2].coerceIn(0f,w.toFloat())
                            val top = row[3].coerceIn(0f,h.toFloat())
                            val right = row[4].coerceIn(0f,w.toFloat())
                            val bottom = row[5].coerceIn(0f,h.toFloat())
                            if (right > left && bottom > top)
                                detections.add(LayoutRegion(row[0].toInt(), row[1],
                                    left,top,right,bottom))
                        }
                        detections
                    }
                }
            }
            val ms = (System.nanoTime()-started)/1_000_000
            Result(regions,ms,"模型已执行，发现${regions.size}个可信版面区域")
        } catch (t: Throwable) {
            Log.w("KanziLayoutAI","Optional layout model unavailable; geometry fallback",t)
            disabled=true
            session?.close()
            session=null
            Result(emptyList(),(System.nanoTime()-started)/1_000_000,
                "AI 推理失败（${t.javaClass.simpleName}），已自动回退几何规则")
        }
    }

    @Synchronized fun release() {
        session?.close()
        session=null
    }
}
