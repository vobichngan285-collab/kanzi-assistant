package com.vobichngan.kanzi

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.model.OCRRunResult
import com.paddle.ocr.postprocess.QuadTextCrop
import com.paddle.ocr.preprocess.RecPreprocessor
import com.paddle.ocr.util.BitmapUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Explicit, user-initiated, single-frame evidence only.
 * Writes one private ZIP to app files; NEVER uses network or shared storage.
 * The user must separately select a location through ACTION_CREATE_DOCUMENT.
 * No additional DET or REC inference: crops and tensors reuse the returned
 * OCR quadrilaterals and the production SDK's exact cropping/preprocess code.
 */
object SingleFrameEvidence {
    private const val LIMIT = 96
    private const val NAME = "last-ocr-single-frame.zip"

    @JvmStatic fun lastFile(context: Context): File? {
        val f = File(File(context.filesDir, "ocr-single-frame"), NAME)
        return if (f.isFile && f.length() > 0L) f else null
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, writer: () -> Unit) {
        zip.putNextEntry(ZipEntry(name))
        try { writer() } finally { zip.closeEntry() }
    }

    private fun writeBitmap(zip: ZipOutputStream, name: String, bitmap: Bitmap) {
        writeEntry(zip, name) {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) {
                "Bitmap PNG encode failed"
            }
        }
    }

    @JvmStatic fun create(
        context: Context, nv21: ByteArray, width: Int, height: Int,
        rawBitmap: Bitmap, ocrBitmap: Bitmap,
        enhanced: Boolean, modelId: String, result: OCRRunResult,
        profile: OcrPerformanceProfile,
        shadowReport: String,
        inputRotationDegrees: Int,
    ): File {
        check(nv21.size.toLong() == width.toLong() * height.toLong() * 3L / 2L)
        val folder = File(context.filesDir, "ocr-single-frame")
        check(folder.isDirectory || folder.mkdirs())
        val pending = File(folder, "pending.zip")
        val destination = File(folder, NAME)
        try {
            FileOutputStream(pending).use { stream ->
                ZipOutputStream(stream).use { zip ->
                    val info = JSONObject()
                    info.put("schema", 1)
                    info.put("model", modelId)
                    info.put("x200_profile", profile.id)
                    info.put("ocr_cpu_threads", profile.cpuThreads)
                    info.put("ocr_rec_batch", profile.recBatchSize)
                    info.put("shadow_rescue_budget", profile.maxShadowRescuesPerDiagnostic)
                    info.put("shadow_results_affect_live_ocr", false)
                    info.put("shadow_four_way_diagnostic", true)
                    info.put("shadow_report_is_ground_truth", false)
                    info.put("camera_frame_width", width)
                    info.put("camera_frame_height", height)
                    info.put("bitmap_width", ocrBitmap.width)
                    info.put("bitmap_height", ocrBitmap.height)
                    info.put("enhanced_preprocessing", enhanced)
                    info.put("nv21_to_bitmap", "OpenCV COLOR_YUV2RGBA_NV21")
                    info.put("ocr_bitmap_rotation",
                        OcrInputOrientation.normalize(inputRotationDegrees))
                    info.put("ocr_input_orientation_independent_of_preview", true)
                    info.put("source_nv21_unchanged", true)
                    info.put("original_box_count", result.results.size)
                    info.put("exported_box_count", minOf(LIMIT, result.results.size))
                    info.put("box_count_limited", result.results.size > LIMIT)
                    info.put("det_input_shape", JSONArray(result.detInputShape))
                    info.put("rec_input_shapes", JSONArray(result.recInputShapes.map { JSONArray(it) }))
                    info.put("detection_ms", result.detectionTimeMs)
                    info.put("recognition_ms", result.recognitionTimeMs)
                    info.put("capture", "manual-single-frame-only")
                    info.put("upload", "never")
                    info.put("notes", "Raw NV21, source RGB bitmap, actual OCR input, SDK quads, SDK crop and SDK REC NCHW float32.  No human ground truth; never treat as CER.")
                    writeEntry(zip, "frame.nv21") { zip.write(nv21) }
                    writeEntry(zip, "diagnostic/four-way-ab.txt") {
                        zip.write(shadowReport.toByteArray(Charsets.UTF_8))
                    }
                    writeBitmap(zip, "decoded-frame.png", rawBitmap)
                    writeBitmap(zip, "actual-ocr-input.png", ocrBitmap)
                    val bgr = BitmapUtils.bitmapToBGRMat(ocrBitmap)
                    try {
                        val boxes = JSONArray()
                        for ((index, item) in result.results.take(LIMIT).withIndex()) {
                            val itemJson = JSONObject()
                            itemJson.put("index", index)
                            itemJson.put("confidence", item.confidence.toDouble())
                            itemJson.put("ocr_text", item.text)
                            val coords = JSONArray()
                            for (point in item.box.points) {
                                coords.put(point.x.toDouble())
                                coords.put(point.y.toDouble())
                            }
                            itemJson.put("quad_xy", coords)
                            val crop = QuadTextCrop.crop(bgr, item.box)
                            try {
                                itemJson.put("crop_width", crop.cols())
                                itemJson.put("crop_height", crop.rows())
                                if (crop.rows() > 0 && crop.cols() > 0) {
                                    val cropBitmap = BitmapUtils.bgrMatToBitmap(crop)
                                    try {
                                        writeBitmap(zip, "crops/%03d.png".format(index), cropBitmap)
                                    } finally { cropBitmap.recycle() }
                                    val tensor = RecPreprocessor.preprocessBatch(listOf(crop))
                                    val dims = JSONArray()
                                    for (size in tensor.shape) dims.put(size)
                                    itemJson.put("tensor_nchw", dims)
                                    val f32 = ByteBuffer.allocate(tensor.tensorData.size * 4)
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                    for (value in tensor.tensorData) f32.putFloat(value)
                                    writeEntry(zip, "tensors/%03d.float32le".format(index)) {
                                        zip.write(f32.array())
                                    }
                                } else {
                                    itemJson.put("failure", "empty_sdk_crop")
                                }
                            } finally { crop.release() }
                            boxes.put(itemJson)
                        }
                        info.put("boxes", boxes)
                    } finally { bgr.release() }
                    writeEntry(zip, "metadata.json") {
                        zip.write(info.toString(2).toByteArray(Charsets.UTF_8))
                    }
                }
            }
            if (destination.exists()) check(destination.delete())
            check(pending.renameTo(destination)) { "Cannot finalize local evidence archive" }
            return destination
        } finally {
            if (pending.exists()) pending.delete()
        }
    }
}
