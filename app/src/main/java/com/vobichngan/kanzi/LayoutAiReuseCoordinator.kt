package com.vobichngan.kanzi

import android.graphics.Bitmap
import android.os.SystemClock

/**
 * Optional, offline PP-DocLayout-S invocation with conservative frame-identity reuse.
 * Does not change OCR text or read order. Diagnostics deliberately bypass this cache
 * so their inference timing remains meaningful.
 */
internal class LayoutAiReuseCoordinator(private val engine: LayoutAiEngine) {
    // The camera's OCR gate already throttles recognition to occasional frames.
    // Reuse across stable OCR frames for up to ten seconds; never across changed scenes.
    private val scheduler = LayoutFrameScheduler(500L, 10_000L, .03f)
    private var previous: LayoutSceneFingerprint? = null
    private var cached: List<LayoutRegion> = emptyList()

    @Synchronized
    fun reset() {
        previous = null
        cached = emptyList()
        scheduler.invalidate()
    }

    @Synchronized
    fun analyze(
        nv21: ByteArray,
        width: Int,
        height: Int,
        bitmap: Bitmap,
        enabled: Boolean,
        ocrBoxes: Int
    ): LayoutAiEngine.Result {
        if (!enabled) {
            reset()
            return LayoutAiEngine.Result(emptyList(), 0L, "AI 关闭，仅使用几何阅读顺序")
        }
        if (ocrBoxes < 10) {
            reset()
            return LayoutAiEngine.Result(emptyList(), 0L, "文字框不足 10，跳过版面 AI")
        }
        val fingerprint = try {
            LayoutSceneFingerprint.fromNv21(nv21, width, height)
        } catch (_: IllegalArgumentException) {
            reset()
            return LayoutAiEngine.Result(emptyList(), 0L, "画面校验失败，回退几何排序")
        }
        val sameFrame = previous?.let { fingerprint.isStationaryComparedWith(it) } == true
        val choice = scheduler.decide(SystemClock.elapsedRealtime(),
            if (sameFrame) 0f else Float.NaN, !sameFrame)
        return when (choice) {
            LayoutFrameScheduler.Action.REUSE -> {
                // Fail closed if the application cache and scheduler ever disagree.
                if (cached.isEmpty() || !sameFrame) {
                    reset()
                    LayoutAiEngine.Result(emptyList(), 0L, "缓存不同步，回退几何排序")
                } else {
                    LayoutAiEngine.Result(cached, 0L, "稳定画面，复用上次版面（无需推理）")
                }
            }
            LayoutFrameScheduler.Action.GEOMETRY_FALLBACK ->
                LayoutAiEngine.Result(emptyList(), 0L,
                    "画面变化或分析冷却中，暂用几何阅读顺序")
            LayoutFrameScheduler.Action.ANALYZE -> {
                try {
                    val result = engine.analyze(bitmap, true, ocrBoxes)
                    val usable = result.regions.isNotEmpty()
                    scheduler.completed(SystemClock.elapsedRealtime(), usable)
                    if (usable) {
                        previous = fingerprint
                        cached = result.regions.toList()
                    } else {
                        previous = null
                        cached = emptyList()
                    }
                    result
                } catch (t: Throwable) {
                    scheduler.completed(SystemClock.elapsedRealtime(), false)
                    previous = null
                    cached = emptyList()
                    LayoutAiEngine.Result(emptyList(), 0L,
                        "版面 AI 调用异常（${t.javaClass.simpleName}），回退几何排序")
                }
            }
        }
    }
}
