package com.paddle.ocr.postprocess

import android.graphics.PointF
import com.paddle.ocr.model.OCRBox
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Experimental detector-geometry-only first-pass repair, before Small REC.
 * Never invents text or chooses OCR candidates by language confidence.
 * When ambiguous, return the exact untouched detector boxes.
 */
object CrossRowFirstPass {
    @Volatile var enabled: Boolean = true
    @Volatile var lastDecision: String = "未运行"
        private set
    private data class B(
        val id: Int, val x1: Float, val y1: Float, val x2: Float,
        val y2: Float, val cx: Float, val cy: Float,
        val width: Float, val height: Float
    )
    private data class Cluster(val center: Float, val count: Int)
    private data class Plan(
        val first: B, val second: B,
        val x1: Int, val x2: Int, val centers: List<Cluster>
    )
    private fun cluster(points: List<Float>): List<Cluster> {
        val groups = mutableListOf<MutableList<Float>>()
        for (v in points.sorted()) {
            val prior = groups.lastOrNull()
            if (prior != null && abs(v - prior.last()) <= 14f) prior.add(v)
            else groups.add(mutableListOf(v))
        }
        return groups.map { Cluster(it.average().toFloat(), it.size) }
    }
    private fun shortAnchor(b: B): Boolean =
        b.height < 60f && b.width < 115f

    fun repartition(boxes: List<OCRBox>, frameWidth: Int, frameHeight: Int): List<OCRBox> {
        if (!enabled) {
            lastDecision = "原版模式：保持所有检测框"
            return boxes
        }
        if (frameWidth <= 0 || frameHeight <= 0 || boxes.size < 6 || boxes.size > 140) {
            lastDecision = "未触发：输入文字框数量或尺寸不满足安全条件"
            return boxes
        }
        val data = boxes.mapIndexed { index, box ->
            val q = box.points
            val x1 = q.minOf { it.x }; val x2 = q.maxOf { it.x }
            val y1 = q.minOf { it.y }; val y2 = q.maxOf { it.y }
            val w = hypot(q[1].x - q[0].x, q[1].y - q[0].y)
            val h = hypot(q[3].x - q[0].x, q[3].y - q[0].y)
            B(index, x1, y1, x2, y2, (x1 + x2) / 2f, (y1 + y2) / 2f, w, h)
        }
        val suspicious = data.filter { t ->
            t.height / max(1f, t.width) > 2.4f &&
            t.height > 60f && t.height < 260f && t.width < 70f &&
            run {
                val centers = cluster(data.filter { a ->
                    a.id != t.id && shortAnchor(a) &&
                    a.x1 > t.x2 + 4f && a.x1 < t.x2 + 210f &&
                    a.cy > t.y1 + 4f && a.cy < t.y2 - 4f
                }.map { it.cy })
                centers.size >= 2 && centers.zipWithNext().all { (a,b) ->
                    b.center - a.center >= 20f
                }
            }
        }
        if (suspicious.size < 2) {
            lastDecision = "未触发：不足两个可信跨横行长框"
            return boxes
        }
        val plans = mutableListOf<Plan>()
        for (i in suspicious.indices) {
            for (j in i + 1 until suspicious.size) {
                val a = suspicious[i]; val b = suspicious[j]
                // Adjacent traditional vertical columns are not a horizontal row.
                if (abs(a.cx - b.cx) < max(65f, 1.8f * max(a.width,b.width))) continue
                val commonY = min(a.y2, b.y2) - max(a.y1, b.y1)
                if (commonY < 0.60f * min(a.height, b.height)) continue
                val left = max(0, floor(min(a.x1,b.x1) - 6).toInt())
                val right = min(frameWidth, ceil(max(a.x2,b.x2) + 9).toInt())
                val top = min(a.y1,b.y1)
                val bottom = max(a.y2,b.y2)
                if (right - left > 230 || bottom - top > 270) continue
                val centers = cluster(data.filter { q ->
                    q.id != a.id && q.id != b.id && shortAnchor(q) &&
                    q.cx >= left && q.cx <= right + 10 &&
                    q.cy > top - 8f && q.cy < bottom + 38f
                }.map { it.cy })
                if (centers.size !in 3..8) continue
                if (!centers.zipWithNext().all { (x,y) ->
                    y.center - x.center >= 22f &&
                    y.center - x.center <= 52f
                }) continue
                plans.add(Plan(a,b,left,right,centers))
            }
        }
        if (plans.size != 1) {
            lastDecision = "未触发：候选数量=" + plans.size + "；歧义一律保持原框"
            return boxes
        }
        val plan = plans.single()
        val centers = plan.centers.map { it.center }
        val rows = mutableListOf<OCRBox>()
        for (cy in centers) {
            val y1 = max(0, (cy - 19f).toInt())
            val y2 = min(frameHeight, (cy + 19f).toInt())
            if (y2 - y1 < 28) {
                lastDecision = "未触发：候选裁切高度不足"
                return boxes
            }
            rows.add(OCRBox(listOf(
                PointF(plan.x1.toFloat(),y1.toFloat()),
                PointF(plan.x2.toFloat(),y1.toFloat()),
                PointF(plan.x2.toFloat(),y2.toFloat()),
                PointF(plan.x1.toFloat(),y2.toFloat())
            )))
        }
        val toRemove = data.filter { b ->
            b.id == plan.first.id || b.id == plan.second.id ||
            (shortAnchor(b) && b.cx >= plan.x1 && b.cx <= plan.x2 + 10 &&
              centers.any { abs(b.cy - it) <= 19f })
        }.map { it.id }.toSet()
        if (toRemove.size < 4 || toRemove.size > 24 ||
            toRemove.any { i -> data[i].id != plan.first.id &&
                data[i].id != plan.second.id && !shortAnchor(data[i]) }) {
            lastDecision = "未触发：替换范围不安全"
            return boxes
        }
        lastDecision = "自动分行：原检测框移除" + toRemove.size + "个，" +
            "按相同原图像素生成" + rows.size + "个完整横行ROI，" +
            "X范围=" + plan.x1 + ".." + plan.x2 + "；保持一次Small REC流程"
        return boxes.filterIndexed { i,_ -> i !in toRemove } + rows
    }
}
