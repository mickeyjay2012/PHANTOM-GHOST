package com.phantom.ghost.audio

import kotlin.math.sqrt

enum class VadEvent { NONE, SPEECH_STARTED, SPEECH_ENDED, NO_SPEECH_TIMEOUT }

/** RMS energy VAD. 20 ms frames; 1,000 ms trailing silence; 5 s initial timeout. */
class VoiceActivityDetector(
    private val threshold: Double = 0.012,
    private val trailingSilenceMs: Long = 1_000,
    private val initialTimeoutMs: Long = 5_000
) {
    private var elapsed = 0L
    private var quiet = 0L
    var isSpeaking = false
        private set

    fun accept(frame: ShortArray): VadEvent {
        if (frame.isEmpty()) return VadEvent.NONE
        elapsed += 20
        val meanSquare = frame.sumOf {
            val value = it.toDouble() / 32768.0
            value * value
        } / frame.size
        if (sqrt(meanSquare) >= threshold) {
            quiet = 0
            if (!isSpeaking) {
                isSpeaking = true
                return VadEvent.SPEECH_STARTED
            }
        } else if (isSpeaking) {
            quiet += 20
            if (quiet >= trailingSilenceMs) {
                isSpeaking = false
                return VadEvent.SPEECH_ENDED
            }
        } else if (elapsed >= initialTimeoutMs) {
            return VadEvent.NO_SPEECH_TIMEOUT
        }
        return VadEvent.NONE
    }

    fun reset() {
        elapsed = 0
        quiet = 0
        isSpeaking = false
    }
}
