// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.engine

import android.graphics.Bitmap
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.postprocess.DBPostProcessor
import com.paddle.ocr.preprocess.DetPreprocessResult
import com.paddle.ocr.preprocess.DetPreprocessor
import org.opencv.core.Mat

class DetectionEngine(
    private val ortManager: ORTSessionManager,
    private val config: PaddleOCRConfig,
) {
    data class DetectionResult(
        val boxes: List<OCRBox>,
        val preprocessMs: Long,
        val inferenceMs: Long,
        val postprocessMs: Long,
        val timeMs: Long,
        val inputShape: List<Int>,
    )

    fun detect(bitmap: Bitmap): DetectionResult {
        return detect {
            DetPreprocessor.preprocess(
                bitmap,
                config.detLimitSideLen,
                config.detLimitType,
                config.detMaxSideLimit,
                config.detImgMode,
            )
        }
    }

    fun detect(src: Mat): DetectionResult {
        // The lab selects 4000 for native-resolution tiled detection, not a huge single tensor.
        if (config.detMaxSideLimit >= 4000) return detectTiled(src)
        return detectRegion(src, config.detLimitSideLen, config.detMaxSideLimit)
    }

    private fun detectRegion(src: Mat, cap: Int, maxCap: Int): DetectionResult = detect {
        DetPreprocessor.preprocess(src, cap, config.detLimitType, maxCap, config.detImgMode)
    }

    private fun tileStarts(n: Int): List<Int> {
        val edge = 2048
        if (n <= edge) return listOf(0)
        val intervals = kotlin.math.ceil((n - edge) / 1664.0).toInt()
        return (0..intervals).map { i -> (i.toLong() * (n - edge) / intervals).toInt() }
    }

    private fun detectTiled(src: Mat): DetectionResult {
        val xs = tileStarts(src.cols())
        val ys = tileStarts(src.rows())
        val boxes = mutableListOf<OCRBox>()
        var pre = 0L; var inf = 0L; var post = 0L; var total = 0L
        var shape = emptyList<Int>()
        for ((yi, y) in ys.withIndex()) for ((xi, x) in xs.withIndex()) {
            val w = minOf(2048, src.cols() - x)
            val h = minOf(2048, src.rows() - y)
            val tile = src.submat(y, y + h, x, x + w)
            val result = try { detectRegion(tile, 2048, 2048) } finally { tile.release() }
            pre += result.preprocessMs; inf += result.inferenceMs; post += result.postprocessMs; total += result.timeMs
            if (shape.isEmpty() || result.inputShape[2].toLong()*result.inputShape[3] > shape[2].toLong()*shape[3]) shape=result.inputShape
            val left = if (xi == 0) 0.0 else (xs[xi-1] + 2048 + x)/2.0
            val right = if (xi == xs.lastIndex) src.cols().toDouble() else (x+w+xs[xi+1])/2.0
            val top = if (yi == 0) 0.0 else (ys[yi-1] + 2048 + y)/2.0
            val bottom = if (yi == ys.lastIndex) src.rows().toDouble() else (y+h+ys[yi+1])/2.0
            for (box in result.boxes) {
                val points=box.points.map { android.graphics.PointF(it.x+x,it.y+y) }
                val cx=points.map{it.x}.average();val cy=points.map{it.y}.average()
                if (cx >= left && cx < right && cy >= top && cy < bottom) boxes.add(OCRBox(points))
            }
            android.util.Log.i("PaddleTile", "tile x=$x y=$y w=$w h=$h input=${result.inputShape} boxes=${result.boxes.size}")
        }
        return DetectionResult(boxes,pre,inf,post,total,shape)
    }

    private fun detect(preprocessor: () -> DetPreprocessResult): DetectionResult {
        val preStart = System.currentTimeMillis()
        val preResult = preprocessor()
        val preprocessMs = System.currentTimeMillis() - preStart

        val infStart = System.currentTimeMillis()
        val (outputData, outputShape) = ortManager.runDetection(preResult.tensorData, preResult.shape)
        val inferenceMs = System.currentTimeMillis() - infStart

        val postStart = System.currentTimeMillis()
        val boxes = DBPostProcessor.process(
            pred = outputData,
            predShape = outputShape,
            thresh = config.detThresh,
            boxThresh = config.detBoxThresh,
            unclipRatio = config.detUnclipRatio,
            maxCandidates = config.detMaxCandidates,
            useDilation = config.detUseDilation,
            scoreMode = config.detScoreMode,
            boxType = config.detBoxType,
            originalH = preResult.originalH,
            originalW = preResult.originalW,
        )
        val postprocessMs = System.currentTimeMillis() - postStart

        return DetectionResult(
            boxes = boxes,
            preprocessMs = preprocessMs,
            inferenceMs = inferenceMs,
            postprocessMs = postprocessMs,
            timeMs = preprocessMs + inferenceMs + postprocessMs,
            inputShape = listOf(1, 3, preResult.shape[2].toInt(), preResult.shape[3].toInt()),
        )
    }
}
