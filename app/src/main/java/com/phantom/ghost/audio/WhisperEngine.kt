package com.phantom.ghost.audio

/** Whisper expects mono 16 kHz PCM. Capture permission and AudioRecord lifecycle belong to the UI/service layer. */
class WhisperEngine(private val native: WhisperNative = JniWhisperNative()) {
    fun load(modelPath: String): Boolean = native.loadModel(modelPath)
    fun transcribe(buffer: AudioRingBuffer): String {
        val samples = buffer.snapshot()
        require(samples.isNotEmpty()) { "No audio captured." }
        return native.transcribe(samples)
    }
}

interface WhisperNative {
    fun loadModel(path: String): Boolean
    fun transcribe(samples: ShortArray): String
}
private class JniWhisperNative : WhisperNative {
    override fun loadModel(path: String) = com.phantom.ghost.ai.NativeBridge.loadWhisperModel(path)
    override fun transcribe(samples: ShortArray) = com.phantom.ghost.ai.NativeBridge.transcribe(samples)
}
