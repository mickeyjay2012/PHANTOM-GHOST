package com.phantom.ghost.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

data class PiperVoice(val id: String, val modelFile: String, val configFile: String)
object VoiceCatalog {
    val voices = listOf(
        PiperVoice("en_US-lessac-medium", "en_US-lessac-medium.onnx", "en_US-lessac-medium.onnx.json"),
        PiperVoice("en_GB-alan-medium", "en_GB-alan-medium.onnx", "en_GB-alan-medium.onnx.json"),
        PiperVoice("en_US-amy-medium", "en_US-amy-medium.onnx", "en_US-amy-medium.onnx.json")
    )
}

/** Adapter contract for Piper's ONNX runtime. It reports unavailable until the native Piper/ONNX build is supplied. */
class PiperEngine(private val native: PiperNative = UnavailablePiperNative()) {
    fun synthesize(text: String, voice: PiperVoice): ShortArray {
        require(text.isNotBlank() && text.length <= 4000)
        return native.synthesize(text, voice)
    }
}
interface PiperNative { fun synthesize(text: String, voice: PiperVoice): ShortArray }
private class UnavailablePiperNative : PiperNative {
    override fun synthesize(text: String, voice: PiperVoice): ShortArray =
        error("Piper runtime is not built yet. Add the Piper and ONNX Runtime Android dependencies first.")
}

class TtsPlayer {
    private var track: AudioTrack? = null
    fun play(pcm16: ShortArray, sampleRate: Int = 22050) {
        stop()
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val player = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, pcm16.size * 2))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        track = player
        player.play()
        player.write(pcm16, 0, pcm16.size)
        player.stop()
        player.release()
        track = null
    }
    fun stop() { track?.let { runCatching { it.pause(); it.flush(); it.release() } }; track = null }
}
