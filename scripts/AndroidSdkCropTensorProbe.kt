// A host-only adapter for the unchanged upstream PaddleOCR v3.7.0
// QuadTextCrop and Android-build-patched RecPreprocessor.
// No inference, Android UI, camera device, app code or APK.
import android.graphics.PointF
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.postprocess.QuadTextCrop
import com.paddle.ocr.preprocess.RecPreprocessor
import nu.pattern.OpenCV
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

fun main(args: Array<String>) {
    require(args.size == 3) { "fixtures.tsv inputDirectory outputDirectory required" }
    OpenCV.loadLocally()
    val rows = File(args[0]).readLines().filter { it.isNotBlank() }
    val input = File(args[1])
    val out = File(args[2])
    require(out.isDirectory || out.mkdirs())
    require(rows.size in 4..24) { "Expected a bounded set of public GT quads" }
    val audit = StringBuilder()
    for (row in rows) {
        val fields = row.split('\t')
        require(fields.size == 12) { "Expected case,file,height,width,8 corners" }
        val caseId = fields[0]
        require(caseId.matches(Regex("[a-zA-Z0-9_-]+"))) { "Unsafe case ID" }
        val height = fields[2].toInt()
        val width = fields[3].toInt()
        val raw = File(input, fields[1]).readBytes()
        require(raw.size == height * width * 3) { "BGR source size mismatch" }
        val mat = Mat(height, width, CvType.CV_8UC3)
        try {
            mat.put(0, 0, raw)
            val points = (0..3).map { j ->
                PointF(fields[4 + 2*j].toFloat(), fields[5 + 2*j].toFloat())
            }
            val crop = QuadTextCrop.crop(mat, OCRBox(points))
            try {
                require(!crop.empty())
                val tensor = RecPreprocessor.preprocessBatch(listOf(crop))
                require(tensor.shape.size == 4 && tensor.shape[0] == 1L &&
                        tensor.shape[1] == 3L && tensor.shape[2] == 48L)
                val bytes = ByteBuffer.allocate(tensor.tensorData.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                for (v in tensor.tensorData) bytes.putFloat(v)
                Files.write(File(out, "$caseId.f32").toPath(), bytes.array())
                val cropBytes = ByteArray(crop.total().toInt() * 3)
                crop.get(0, 0, cropBytes)
                Files.write(File(out, "$caseId.crop.bgr").toPath(), cropBytes)
                audit.append(caseId).append('\t')
                    .append(crop.rows()).append('\t').append(crop.cols()).append('\t')
                    .append(tensor.shape.joinToString(",")).append('\n')
                println("ACTUAL_SDK_CROP_TENSOR " + caseId + " " + tensor.shape.joinToString("x"))
            } finally {
                crop.release()
            }
        } finally {
            mat.release()
        }
    }
    File(out, "sdk_outputs.tsv").writeText(audit.toString())
    println("ACTUAL_SDK_CROP_TENSOR_COMPLETE cases=" + rows.size)
}
