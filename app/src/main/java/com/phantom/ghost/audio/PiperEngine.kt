package com.phantom.ghost.audio

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
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Locale
import kotlin.math.roundToInt

data class PiperVoiceCandidate(
    val id: String,
    val modelFile: String,
    val configFile: String
)

data class ResolvedPiperVoice(
    val candidate: PiperVoiceCandidate,
    val model: File,
    val config: File
)

object PiperVoiceCatalog {
    // The medium model is preferred; low is a compatible fallback voice family.
    val lessac = listOf(
        PiperVoiceCandidate("en_US-lessac-medium", "en_US-lessac-medium.onnx", "en_US-lessac-medium.onnx.json"),
        PiperVoiceCandidate("en_US-lessac-low", "en_US-lessac-low.onnx", "en_US-lessac-low.onnx.json")
    )
}

class PiperModelResolver(private val modelDirectory: File) {
    fun resolve(candidates: List<PiperVoiceCandidate> = PiperVoiceCatalog.lessac): ResolvedPiperVoice {
        candidates.forEach { candidate ->
            val model = File(modelDirectory, candidate.modelFile)
            val config = File(modelDirectory, candidate.configFile)
            if (model.isFile && model.length() > 0L && config.isFile && config.length() > 0L) {
                return ResolvedPiperVoice(candidate, model, config)
            }
        }
        throw IllegalStateException(
            "No supported Lessac voice is installed. Expected medium first, then low."
        )
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

/**
 * Piper models consume phoneme IDs, not raw text. Supply a phonemizer implementation
 * (for example, an independently licensed espeak-ng bridge) that maps text through
 * the selected voice's phoneme_id_map. This interface deliberately avoids linking
 * PHANTOM GHOST against the GPL piper-kotlin runtime.
 */
fun interface PiperPhonemizer {
    fun toPhonemeIds(text: String, phonemeIdMap: Map<String, LongArray>): LongArray
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

class PiperOnnxBackend(
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment(),
    private val phonemizer: PiperPhonemizer
) {
    suspend fun synthesize(voice: ResolvedPiperVoice, text: String): Pair<Int, FloatArray> =
        withContext(Dispatchers.Default) {
            val config = PiperModelConfig.read(voice.config)
            val phonemeIds = phonemizer.toPhonemeIds(text, config.phonemeIdMap)
            require(phonemeIds.isNotEmpty()) { "Phonemizer returned no phoneme IDs." }

            val options = OrtSession.SessionOptions()
            val session = environment.createSession(voice.model.absolutePath, options)
            options.close()
            session.use { activeSession ->
                val inputNames = activeSession.inputNames
                require(inputNames.containsAll(listOf("input", "input_lengths", "scales"))) {
                    "Unsupported Piper ONNX input signature: $inputNames"
                }
                val tensors = mutableMapOf<String, OnnxTensor>()
                try {
                    tensors["input"] = OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(phonemeIds),
                        longArrayOf(1L, phonemeIds.size.toLong())
                    )
                    tensors["input_lengths"] = OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(longArrayOf(phonemeIds.size.toLong())),
                        longArrayOf(1L)
                    )
                    tensors["scales"] = OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.wrap(floatArrayOf(config.noiseScale, config.lengthScale, config.noiseW)),
                        longArrayOf(3L)
                    )
                    if (inputNames.contains("sid")) {
                        tensors["sid"] = OnnxTensor.createTensor(
                            environment,
                            LongBuffer.wrap(longArrayOf(0L)),
                            longArrayOf(1L)
                        )
                    }
                    activeSession.run(tensors).use { result ->
                        val audio = result.get(0).value as? Array<*>
                            ?: error("Unexpected Piper ONNX output type.")
                        val firstBatch = audio.firstOrNull() as? Array<*>
                            ?: error("Unexpected Piper ONNX audio batch.")
                        val samples = firstBatch.firstOrNull() as? FloatArray
                            ?: error("Unexpected Piper ONNX waveform tensor.")
                        config.sampleRateHz to samples
                    }
                } finally {
                    tensors.values.forEach { runCatching { it.close() } }
                }
            }
        }
}

/** Token-stream TTS: synthesize complete semantic phrases while Llama continues generating. */
class PiperEngine(
    private val backend: PiperOnnxBackend,
    private val player: StreamingAudioTrack = StreamingAudioTrack()
) {
    suspend fun speakResponse(
        tokens: Flow<String>,
        modelDirectory: File,
        segmenter: SpeechSegmenter = SpeechSegmenter()
    ) = coroutineScope {
        val voice = PiperModelResolver(modelDirectory).resolve()
        val queue = Channel<String>(capacity = 4)
        val producer = launch {
            try {
                tokens.collect { token ->
                    segmenter.accept(token).forEach { queue.send(it) }
                }
                segmenter.flush()?.let { queue.send(it) }
            } finally {
                queue.close()
            }
        }
        try {
            for (phrase in queue) {
                currentCoroutineContext().ensureActive()
                val (sampleRate, pcm) = backend.synthesize(voice, phrase)
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
}

/** Writes synthesized float PCM to one streaming AudioTrack in bounded blocks. */
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
                check(written >= 0) { "AudioTrack write failed: $written" }
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
