package com.vobichngan.kanzi

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import java.io.File

/**
 * Native speech inference is NEVER called on the UI/camera thread.
 * The official sherpa-onnx AAR supplies the JNI engine; the user downloads
 * model/lexicon files separately into app-private storage.
 */
class OfflineVoiceSynthesizer(private val store: DownloadableVoiceStore) {
    class Audio(val samples: FloatArray, val sampleRate: Int)

    private var loadedPack = ""
    private var engine: OfflineTts? = null

    @Synchronized
    fun synthesize(voice: DownloadableVoiceStore.Voice, text: String, speed: Float): Audio {
        require(!voice.isSystem)
        require(store.isInstalled(voice)) { "所选语音包尚未安装" }
        if (engine == null || loadedPack != voice.pack) {
            engine?.release()
            engine = null
            loadedPack = ""
            engine = OfflineTts(config = buildConfig(voice))
            loadedPack = voice.pack
        }
        val id = voice.speakerId.coerceIn(0, (engine!!.numSpeakers() - 1).coerceAtLeast(0))
        val cfg = GenerationConfig(sid = id, speed = speed.coerceIn(0.5f, 2.0f))
        // Bound the input to a TTS-sized chunk. OCR segmentation supplies
        // smaller segments; this is an additional defense against huge arrays.
        require(text.length <= 240) { "语音文本过长" }
        val generated = engine!!.generateWithConfig(text, cfg)
        require(generated.samples.isNotEmpty() && generated.sampleRate >= 8000) {
            "语音模型没有产生有效音频"
        }
        // Some corrupt/incompatible packages still return a PCM array of
        // pure zeros or NaN. Reject that before claiming a voice works.
        var loudness = 0f
        for (i in generated.samples.indices step 8) {
            val sample = generated.samples[i]
            require(sample.isFinite()) { "语音模型生成了无效音频数据" }
            loudness = maxOf(loudness, kotlin.math.abs(sample))
        }
        require(loudness >= 0.002f) {
            "语音模型仅生成静音，未通过发声检查"
        }
        return Audio(generated.samples, generated.sampleRate)
    }

    private fun buildConfig(voice: DownloadableVoiceStore.Voice): OfflineTtsConfig {
        val dir = store.modelDir(voice)
        fun file(name: String) = File(dir, name).absolutePath
        val model = OfflineTtsModelConfig(numThreads = 2, debug = false, provider = "cpu")
        var ruleFsts = ""
        when (voice.pack) {
            "kokoro" -> {
                model.kokoro = OfflineTtsKokoroModelConfig(
                    model = file("model.onnx"),
                    voices = file("voices.bin"),
                    tokens = file("tokens.txt"),
                    // Official Kokoro package includes these pronunciation
                    // resources; older sherpa-native builds still consume
                    // dataDir/dictDir even if newer Kotlin docs deprecate them.
                    dataDir = file("espeak-ng-data"),
                    dictDir = file("dict"),
                    lexicon = file("lexicon-us-en.txt") + "," + file("lexicon-zh.txt")
                )
            }
            "piper" -> {
                model.vits = OfflineTtsVitsModelConfig(
                    model = file("zh_CN-chaowen-medium.onnx"),
                    lexicon = file("lexicon.txt"),
                    tokens = file("tokens.txt")
                )
                ruleFsts = listOf("phone.fst", "date.fst", "number.fst")
                    .map { file(it) }.filter { File(it).isFile }.joinToString(",")
            }
            "melo" -> {
                model.vits = OfflineTtsVitsModelConfig(
                    model = file("model.onnx"),
                    lexicon = file("lexicon.txt"),
                    dictDir = file("dict"),
                    tokens = file("tokens.txt")
                )
                ruleFsts = listOf("date.fst", "number.fst")
                    .map { file(it) }.filter { File(it).isFile }.joinToString(",")
            }
            else -> throw IllegalArgumentException("未知的离线语音库")
        }
        return OfflineTtsConfig(model = model, ruleFsts = ruleFsts, maxNumSentences = 1)
    }

    @Synchronized
    fun release() {
        engine?.release()
        engine = null
        loadedPack = ""
    }
}
