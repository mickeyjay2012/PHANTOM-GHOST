package com.phantom.ghost.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.math.roundToInt

data class PiperVoiceCandidate(
    val id: String,
    val modelFile: String,
    val configFile: String,
    val modelBytes: Long,
    val modelMd5: String,
    val configBytes: Long,
    val configMd5: String
)

data class ResolvedPiperVoice(
    val candidate: PiperVoiceCandidate,
    val model: File,
    val config: File
)

data class PiperVoiceProvenance(
    val modelName: String,
    val revision: String,
    val datasetStatus: String,
    val artifactStatus: String
)

object PiperVoiceCatalog {
    const val REVISION = "bae641dcb5a608cff81ccaf7ff018baaca1a33e1"
    private const val BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/$REVISION/en/en_US/ljspeech"
    val candidates = listOf(
        PiperVoiceCandidate(
            "en_US-ljspeech-medium", "en_US-ljspeech-medium.onnx", "en_US-ljspeech-medium.onnx.json",
            63_531_379L, "109d552e9dd78d92d1169a7edd6de38d",
            4_972L, "0668112b8b3ac5bb4b12c2b1a366776a"
        ),
        PiperVoiceCandidate(
            "en_US-ljspeech-high", "en_US-ljspeech-high.onnx", "en_US-ljspeech-high.onnx.json",
            114_199_011L, "dad093b5d2cff6a5fda99883ceda09d1",
            4_970L, "de98fc398ddead60fb82d93bfafb3ad1"
        )
    )

    fun url(candidate: PiperVoiceCandidate, fileName: String): String {
        val quality = if (candidate.id.endsWith("medium")) "medium" else "high"
        return "$BASE/$quality/$fileName"
    }

    fun provenance(candidate: PiperVoiceCandidate) = PiperVoiceProvenance(
        modelName = candidate.id,
        revision = REVISION,
        datasetStatus = "Public-domain source recordings (as stated in upstream model card)",
        artifactStatus = "Pinned Hugging Face ONNX artifact; checksum verified against upstream voice catalog"
    )
}

class PiperModelResolver(private val modelDirectory: File) {
    fun resolve(candidates: List<PiperVoiceCandidate> = PiperVoiceCatalog.candidates): ResolvedPiperVoice {
        candidates.forEach { candidate ->
            val model = File(modelDirectory, candidate.modelFile)
            val config = File(modelDirectory, candidate.configFile)
            if (model.isFile && model.length() == candidate.modelBytes &&
                config.isFile && config.length() == candidate.configBytes
            ) return ResolvedPiperVoice(candidate, model, config)
        }
        throw IllegalStateException("No verified LJSpeech voice is installed.")
    }
}

/** Downloads the pinned medium voice first, then the high-quality fallback. */
class PiperVoiceModelManager(private val modelDirectory: File) {
    suspend fun ensureVoice(): ResolvedPiperVoice = withContext(Dispatchers.IO) {
        check(modelDirectory.exists() || modelDirectory.mkdirs()) { "Cannot create voice model directory." }
        var lastError: Throwable? = null
        for (candidate in PiperVoiceCatalog.candidates) {
            try {
                ensureFile(candidate, candidate.modelFile, candidate.modelBytes, candidate.modelMd5)
                ensureFile(candidate, candidate.configFile, candidate.configBytes, candidate.configMd5)
                return@withContext PiperModelResolver(modelDirectory).resolve(listOf(candidate))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error
                File(modelDirectory, candidate.modelFile + ".part").delete()
                File(modelDirectory, candidate.configFile + ".part").delete()
            }
        }
        throw IllegalStateException("Unable to install either pinned LJSpeech voice.", lastError)
    }

    private fun ensureFile(candidate: PiperVoiceCandidate, name: String, expectedBytes: Long, expectedMd5: String) {
        val target = File(modelDirectory, name)
        if (target.isFile && target.length() == expectedBytes &&
            md5(target).equals(expectedMd5, ignoreCase = true)
        ) return
        target.delete()
        val partial = File(modelDirectory, "$name.part")
        val connection = URL(PiperVoiceCatalog.url(candidate, name)).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 90_000
        connection.setRequestProperty("User-Agent", "PHANTOM-GHOST-Android")
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Voice download failed: HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
            }
            check(partial.length() == expectedBytes) { "Voice file size mismatch for $name." }
            check(md5(partial).equals(expectedMd5, ignoreCase = true)) { "Voice checksum mismatch for $name." }
            check(partial.renameTo(target)) { "Could not install verified voice file $name." }
        } finally {
            connection.disconnect()
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class PiperModelConfig(
    val sampleRateHz: Int,
    val noiseScale: Float,
    val lengthScale: Float,
    val noiseW: Float,
    val phonemeIdMap: Map<String, LongArray>
) {
    companion object {
        fun read(file: File): PiperModelConfig {
            val root = JSONObject(file.readText())
            require(root.optString("phoneme_type") == "espeak") {
                "Only eSpeak-phoneme Piper voices are supported by this phonemizer."
            }
            val audio = root.getJSONObject("audio")
            val inference = root.optJSONObject("inference") ?: JSONObject()
            val phonemeConfig = root.getJSONObject("phoneme_id_map")
            val ids = buildMap {
                val keys = phonemeConfig.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val values = phonemeConfig.getJSONArray(key)
                    put(key, LongArray(values.length()) { index -> values.getLong(index) })
                }
            }
            return PiperModelConfig(
                sampleRateHz = audio.getInt("sample_rate"),
                noiseScale = inference.optDouble("noise_scale", 0.667).toFloat(),
                lengthScale = inference.optDouble("length_scale", 1.0).toFloat(),
                noiseW = inference.optDouble("noise_w", 0.8).toFloat(),
                phonemeIdMap = ids
            )
        }
    }
}

fun interface PiperPhonemizer {
    fun toPhonemeIds(
        text: String,
        phonemeIdMap: Map<String, LongArray>,
        onLowConfidence: (String) -> Unit
    ): LongArray
}

class SpeechSegmenter(
    private val minimumPhraseChars: Int = 48,
    private val maximumChunkChars: Int = 220
) {
    private val pending = StringBuilder()

    fun accept(token: String): List<String> {
        if (token.isEmpty()) return emptyList()
        pending.append(token)
        val output = mutableListOf<String>()
        while (true) {
            val text = pending.toString()
            val sentenceEnd = text.indexOfFirst { it == '.' || it == '!' || it == '?' || it == '\n' }
            val phraseEnd = if (text.length >= minimumPhraseChars) {
                text.indexOfFirst { it == ',' || it == ';' || it == ':' }
            } else -1
            val cut = when {
                sentenceEnd >= 0 -> sentenceEnd + 1
                phraseEnd >= minimumPhraseChars - 1 -> phraseEnd + 1
                text.length >= maximumChunkChars -> {
                    val boundary = text.lastIndexOf(' ', maximumChunkChars)
                    if (boundary >= minimumPhraseChars) boundary else maximumChunkChars
                }
                else -> -1
            }
            if (cut < 0) break
            val chunk = pending.substring(0, cut).trim()
            if (chunk.isNotEmpty()) output += chunk
            pending.delete(0, cut)
            while (pending.isNotEmpty() && pending[0].isWhitespace()) pending.deleteCharAt(0)
        }
        return output
    }

    fun flush(): String? = pending.toString().trim().takeIf { it.isNotEmpty() }
        .also { pending.clear() }
}

/** Keeps one warm ONNX session per voice so sentence boundaries do not reload the model. */
class PiperOnnxBackend(
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment(),
    private val phonemizer: PiperPhonemizer
) : AutoCloseable {
    private val sessions = mutableMapOf<String, OrtSession>()

    private fun sessionFor(model: File): OrtSession = synchronized(sessions) {
        sessions[model.absolutePath] ?: run {
            val options = OrtSession.SessionOptions()
            try {
                environment.createSession(model.absolutePath, options).also {
                    sessions[model.absolutePath] = it
                }
            } finally {
                options.close()
            }
        }
    }

    suspend fun synthesize(
        voice: ResolvedPiperVoice,
        text: String,
        onLowConfidence: (String) -> Unit
    ): Pair<Int, FloatArray> = withContext(Dispatchers.Default) {
        val config = PiperModelConfig.read(voice.config)
        val phonemeIds = phonemizer.toPhonemeIds(text, config.phonemeIdMap, onLowConfidence)
        require(phonemeIds.isNotEmpty()) { "Phonemizer returned no phoneme IDs." }
        val activeSession = sessionFor(voice.model)
        val inputNames = activeSession.inputNames
        require(inputNames.containsAll(listOf("input", "input_lengths", "scales"))) {
            "Unsupported Piper ONNX input signature: $inputNames"
        }
        val tensors = mutableMapOf<String, OnnxTensor>()
        try {
            tensors["input"] = OnnxTensor.createTensor(
                environment, LongBuffer.wrap(phonemeIds),
                longArrayOf(1L, phonemeIds.size.toLong())
            )
            tensors["input_lengths"] = OnnxTensor.createTensor(
                environment, LongBuffer.wrap(longArrayOf(phonemeIds.size.toLong())), longArrayOf(1L)
            )
            tensors["scales"] = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(floatArrayOf(config.noiseScale, config.lengthScale, config.noiseW)),
                longArrayOf(3L)
            )
            if (inputNames.contains("sid")) {
                tensors["sid"] = OnnxTensor.createTensor(
                    environment, LongBuffer.wrap(longArrayOf(0L)), longArrayOf(1L)
                )
            }
            activeSession.run(tensors).use { result ->
                val audio = result.get(0).value as? Array<*> ?: error("Unexpected Piper ONNX output type.")
                val firstBatch = audio.firstOrNull() as? Array<*> ?: error("Unexpected Piper ONNX audio batch.")
                val samples = firstBatch.firstOrNull() as? FloatArray ?: error("Unexpected Piper ONNX waveform tensor.")
                config.sampleRateHz to samples
            }
        } finally {
            tensors.values.forEach { runCatching { it.close() } }
        }
    }

    override fun close() {
        synchronized(sessions) {
            sessions.values.forEach { runCatching { it.close() } }
            sessions.clear()
        }
    }
}

/** Buffers Llama fragments, synthesizing each semantic chunk while generation continues. */
class PiperEngine(
    private val backend: PiperOnnxBackend,
    private val player: StreamingAudioTrack = StreamingAudioTrack()
) : AutoCloseable {
    suspend fun speakResponse(
        tokens: Flow<String>,
        modelDirectory: File,
        onLowConfidence: (String) -> Unit = {},
        onVoiceLoaded: (PiperVoiceProvenance) -> Unit = {},
        segmenter: SpeechSegmenter = SpeechSegmenter()
    ) = coroutineScope {
        val voice = PiperVoiceModelManager(modelDirectory).ensureVoice()
        onVoiceLoaded(PiperVoiceCatalog.provenance(voice.candidate))
        val queue = Channel<String>(capacity = 4)
        val producer = launch {
            try {
                tokens.collect { token -> segmenter.accept(token).forEach { queue.send(it) } }
                segmenter.flush()?.let { queue.send(it) }
            } finally {
                queue.close()
            }
        }
        try {
            for (phrase in queue) {
                currentCoroutineContext().ensureActive()
                val (sampleRate, pcm) = backend.synthesize(voice, phrase, onLowConfidence)
                player.play(sampleRate, pcm)
            }
            producer.join()
        } catch (cancelled: CancellationException) {
            producer.cancel()
            throw cancelled
        } finally {
            player.stop()
        }
    }

    fun stop() = player.stop()

    override fun close() {
        stop()
        backend.close()
    }
}

class StreamingAudioTrack {
    @Volatile private var activeTrack: AudioTrack? = null

    suspend fun play(sampleRateHz: Int, samples: FloatArray) = withContext(Dispatchers.IO) {
        require(sampleRateHz in 8_000..48_000) { "Unsupported Piper sample rate: $sampleRateHz" }
        if (samples.isEmpty()) return@withContext
        val minimum = AudioTrack.getMinBufferSize(
            sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(minimum > 0) { "AudioTrack rejected the Piper sample rate." }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minimum, sampleRateHz))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        activeTrack = track
        try {
            track.play()
            val blockSize = 4_096
            var offset = 0
            while (offset < samples.size) {
                currentCoroutineContext().ensureActive()
                val count = minOf(blockSize, samples.size - offset)
                val pcm = ShortArray(count) { index ->
                    (samples[offset + index].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
                }
                val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                check(written > 0) { "AudioTrack write failed or stalled: $written" }
                offset += written
            }
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            track.release()
            if (activeTrack === track) activeTrack = null
        }
    }

    fun stop() {
        activeTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
        }
    }
}
