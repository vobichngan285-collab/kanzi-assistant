package com.vobichngan.kanzi

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the official PP-OCRv6 Small ONNX pair into app-private storage.
 * The two ONNX files are pinned to the verified Hugging Face revisions and SHA-256 values.
 */
class OcrModelDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "ocr_models/ppocrv6_small")

    interface Listener {
        fun onProgress(percent: Int)
        fun onComplete()
        fun onError(message: String)
    }

    fun downloadSmallModel(listener: Listener): Job {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                modelDir.mkdirs()
                downloadVerified(
                    DET_URL, File(modelDir, "det.onnx"),
                    "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e", 0, listener
                )
                downloadVerified(
                    REC_URL, File(modelDir, "rec.onnx"),
                    "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634", 1, listener
                )
                downloadConfig(File(modelDir, "rec.yml"), listener)
                File(modelDir, "complete.flag").writeText("PP-OCRv6-small\n$DET_REV\n$REC_REV")
                withContext(Dispatchers.Main) { listener.onProgress(100); listener.onComplete() }
            } catch (t: Throwable) {
                File(modelDir, "complete.flag").delete()
                withContext(Dispatchers.Main) {
                    listener.onError(t.message ?: "模型下载失败")
                }
            }
        }
    }

    private suspend fun downloadVerified(
        url: String, destination: File, expectedSha256: String, index: Int, listener: Listener
    ) {
        if (destination.isFile && sha256(destination).equals(expectedSha256, ignoreCase = true)) {
            withContext(Dispatchers.Main) { listener.onProgress((index + 1) * 33) }
            return
        }
        downloadToPart(url, destination, index, listener, expectedSha256)
    }

    private suspend fun downloadConfig(destination: File, listener: Listener) {
        if (destination.isFile && validConfig(destination)) {
            withContext(Dispatchers.Main) { listener.onProgress(99) }
            return
        }
        val part = File(destination.parentFile, destination.name + ".part")
        part.delete()
        val connection = (URL(CONFIG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KanziAssistant/0.7.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("下载识别字典失败：HTTP ${connection.responseCode}")
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        val length = connection.contentLengthLong
                        val local = if (length > 0) (total * 100 / length).toInt().coerceIn(0, 99) else 50
                        withContext(Dispatchers.Main) { listener.onProgress(66 + local / 3) }
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!validConfig(part)) {
            part.delete()
            error("识别字典文件校验失败")
        }
        if (destination.exists()) destination.delete()
        if (!part.renameTo(destination)) {
            part.delete()
            error("无法保存识别字典")
        }
    }

    private suspend fun downloadToPart(
        url: String, destination: File, index: Int, listener: Listener, expectedSha256: String
    ) {
        val part = File(destination.parentFile, destination.name + ".part")
        part.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KanziAssistant/0.7.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("下载 OCR 模型失败：HTTP ${connection.responseCode}")
            val length = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        val local = if (length > 0) (total * 100 / length).toInt().coerceIn(0, 99) else 50
                        withContext(Dispatchers.Main) {
                            listener.onProgress(index * 33 + local / 3)
                        }
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!sha256(part).equals(expectedSha256, ignoreCase = true)) {
            part.delete()
            error("PP-OCRv6 Small 模型 SHA-256 校验失败，已丢弃下载文件")
        }
        if (destination.exists()) destination.delete()
        if (!part.renameTo(destination)) {
            part.delete()
            error("无法保存 OCR 模型文件")
        }
    }

    private fun validConfig(file: File): Boolean {
        if (!file.isFile || file.length() < 1024 || file.length() > 20L * 1024 * 1024) return false
        val content = file.readText()
        return content.contains("PostProcess:") && content.contains("character_dict:")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DET_REV = "5994b4f"
        private const val REC_REV = "3d2d345"
        private const val DET_URL = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/5994b4f/inference.onnx"
        private const val REC_URL = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/3d2d345/inference.onnx"
        private const val CONFIG_URL = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.yml"

        @JvmStatic fun isInstalled(context: Context): Boolean {
            val dir = File(context.applicationContext.filesDir, "ocr_models/ppocrv6_small")
            return File(dir, "complete.flag").isFile &&
                File(dir, "det.onnx").isFile && File(dir, "rec.onnx").isFile &&
                File(dir, "rec.yml").isFile
        }

        @JvmStatic fun modelDirectory(context: Context): File =
            File(context.applicationContext.filesDir, "ocr_models/ppocrv6_small")

        @JvmStatic fun deleteSmallModel(context: Context) {
            modelDirectory(context).deleteRecursively()
        }
    }
}
