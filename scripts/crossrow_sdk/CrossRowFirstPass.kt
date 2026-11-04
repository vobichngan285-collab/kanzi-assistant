package com.paddle.ocr.postprocess

import android.graphics.PointF
import com.paddle.ocr.model.OCRBox
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Simple horizontal text-line merge BEFORE the first recognizer call.
 *
 * Steps: sort into rows by y + comparable text height; within each row merge
 * horizontally continuous boxes; REMOVE their old boxes; crop/recognize each
 * new row ONCE in the existing OCREngine pipeline.
 *
 * The original detector and Small recognizer are unchanged. No text guesses,
 * extra DET/REC, confidence reranking, second-pass fallback or TTS changes.
 * Single nonmatching boxes (including vertical and tiny labels) stay untouched.
 */
object CrossRowFirstPass {
    @Volatile var enabled: Boolean = true
    @Volatile var lastDecision: String = "未运行"
        private set

    private data class R(
        val id: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val cy: Float get() = (top + bottom) * 0.5f
    }

    private class Row(val first: R) {
        val parts = mutableListOf(first)
        var centerY: Float = first.cy
        var height: Float = first.height

        fun accepts(r: R): Boolean {
            val ratio = max(r.height, height) / max(1f, min(r.height, height))
            if (ratio > 1.8f) return false
            // A normal printed line has centers near one y, even if split
            // into words. Distinct rows separated by a line gap stay apart.
            return abs(r.cy - centerY) <= 0.43f * min(r.height, height)
        }

        fun add(r: R) {
            parts.add(r)
            centerY = parts.map { it.cy }.average().toFloat()
            height = parts.map { it.height }.average().toFloat()
        }
    }

    fun repartition(boxes: List<OCRBox>, frameWidth: Int, frameHeight: Int): List<OCRBox> {
        if (!enabled) {
            lastDecision = "关闭：保留原始文字框 " + boxes.size + " 个"
            return boxes
        }
        if (boxes.size < 2 || frameWidth < 30 || frameHeight < 30) {
            lastDecision = "未合并：框数不足"
            return boxes
        }

        val rects = boxes.mapIndexed { i, box ->
            val x1 = box.points.minOf { it.x }
            val x2 = box.points.maxOf { it.x }
            val y1 = box.points.minOf { it.y }
            val y2 = box.points.maxOf { it.y }
            R(i, x1, y1, x2, y2)
        }
        // Only horizontal-looking, readable-size regions participate.
        // Other boxes remain in the OCR result; they are NOT silently deleted.
        val candidates = rects.filter { r ->
            r.height >= 7f &&
            r.height <= min(frameWidth, frameHeight) * 0.18f &&
            r.width >= r.height * 0.80f
        }.sortedWith(compareBy<R> { it.cy }.thenBy { it.left })

        val rows = mutableListOf<Row>()
        for (rect in candidates) {
            var best: Row? = null
            var distance = Float.MAX_VALUE
            for (row in rows) {
                if (!row.accepts(rect)) continue
                val d = abs(rect.cy - row.centerY)
                if (d < distance) {
                    distance = d
                    best = row
                }
            }
            if (best == null) rows.add(Row(rect)) else best.add(rect)
        }

        val removed = mutableSetOf<Int>()
        val merged = mutableListOf<OCRBox>()
        var mergedGroups = 0
        for (row in rows) {
            val byX = row.parts.sortedWith(compareBy<R> { it.left }.thenBy { it.right })
            var group = mutableListOf<R>()

            fun flush() {
                if (group.size >= 2) {
                    // Replace every participating old box. This is NOT an
                    // additional drawing-only overlay: downstream Small REC
                    // consumes this new crop in place of the original crops.
                    val x1 = group.minOf { it.left }.coerceIn(0f, frameWidth.toFloat()-1)
                    val y1 = group.minOf { it.top }.coerceIn(0f, frameHeight.toFloat()-1)
                    val x2 = group.maxOf { it.right }.coerceIn(x1+1f, frameWidth.toFloat())
                    val y2 = group.maxOf { it.bottom }.coerceIn(y1+1f, frameHeight.toFloat())
                    val pad = max(1f, row.height * 0.08f)
                    val lx = max(0f, x1-pad)
                    val ty = max(0f, y1-pad)
                    val rx = min(frameWidth.toFloat(), x2+pad)
                    val by = min(frameHeight.toFloat(), y2+pad)
                    merged.add(OCRBox(listOf(
                        PointF(lx,ty), PointF(rx,ty),
                        PointF(rx,by), PointF(lx,by)
                    )))
                    removed.addAll(group.map { it.id })
                    mergedGroups++
                }
                group = mutableListOf()
            }

            for (rect in byX) {
                if (group.isEmpty()) {
                    group.add(rect)
                    continue
                }
                val prior = group.last()
                val gap = rect.left - prior.right
                // One conservative relative-gap rule, scaled by nearby text height.
                // The earlier 1.65x exploratory gap incorrectly merged
                // independent price/menu columns; this 0.65x gate preserves
                // all 24 originally complete phrases in the 37-target photo check.
                val maxGap = 0.65f * min(prior.height, rect.height)
                if (gap <= maxGap) group.add(rect)
                else {
                    flush()
                    group.add(rect)
                }
            }
            flush()
        }
        lastDecision = if (merged.isEmpty()) {
            "无可合并同行：保持原版 " + boxes.size + " 框"
        } else {
            "实际合并 " + mergedGroups + " 行；删旧框 " + removed.size +
                " 个、生成整行框 " + merged.size + " 个；" +
                "原 " + boxes.size + " 框 → 新 " + (boxes.size-removed.size+merged.size) +
                " 框；合并后从原图重裁并首次Small识别"
        }
        return boxes.filterIndexed { i, _ -> i !in removed } + merged
    }
}
