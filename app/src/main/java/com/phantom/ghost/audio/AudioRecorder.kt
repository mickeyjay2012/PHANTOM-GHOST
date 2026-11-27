package com.phantom.ghost.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Mono PCM16, 16 kHz capture; 30-second bounded ring buffer. */
class AudioRecorder(
    private val ring: AudioRingBuffer = AudioRingBuffer(16_000 * 30)
) {
    private var audio: AudioRecord? = null
    private var job: Job? = null
    private val stopping = AtomicBoolean(false)

    fun start(scope: CoroutineScope, onFrame: suspend (ShortArray) -> Unit) {
        check(job?.isActive != true) { "Microphone is already active." }
        val minSize = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minSize > 0) { "16 kHz microphone capture is unsupported." }
        val input = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minSize * 2, 32_000)
        )
        check(input.state == AudioRecord.STATE_INITIALIZED) {
            input.release()
            "Microphone initialization failed."
        }
        ring.clear()
        stopping.set(false)
        audio = input
        input.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val frame = ShortArray(320)
            try {
                while (isActive && !stopping.get()) {
                    val count = input.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                    if (count < 0) {
                        if (stopping.get() || !isActive) break
                        error("AudioRecord read failed: $count")
                    }
                    if (count > 0) {
                        val samples = frame.copyOf(count)
                        ring.append(samples)
                        onFrame(samples)
                    }
                }
            } finally {
                runCatching { if (input.recordingState == AudioRecord.RECORDSTATE_RECORDING) input.stop() }
                input.release()
                if (audio === input) audio = null
            }
        }
    }

    suspend fun stopAndSnapshot(): ShortArray {
        stopping.set(true)
        runCatching { audio?.stop() }
        job?.cancelAndJoin()
        job = null
        audio = null
        return ring.snapshot()
    }

    fun cancel() {
        stopping.set(true)
        runCatching { audio?.stop() }
        job?.cancel()
        audio = null
        ring.clear()
    }
}
