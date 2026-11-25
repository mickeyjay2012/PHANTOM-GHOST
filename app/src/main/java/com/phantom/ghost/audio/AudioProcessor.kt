package com.phantom.ghost.audio

import kotlin.math.sqrt

/** Thread-safe bounded mono PCM16 ring buffer. AudioRecord should feed 16 kHz samples. */
class AudioRingBuffer(capacitySamples: Int = 16_000 * 30) {
    private val data = ShortArray(capacitySamples)
    private var write = 0
    private var size = 0

    @Synchronized fun append(samples: ShortArray, count: Int = samples.size) {
        require(count in 0..samples.size)
        for (i in 0 until count) {
            data[write] = samples[i]
            write = (write + 1) % data.size
            if (size < data.size) size++
        }
    }

    @Synchronized fun snapshot(): ShortArray {
        val result = ShortArray(size)
        val start = (write - size + data.size) % data.size
        for (i in 0 until size) result[i] = data[(start + i) % data.size]
        return result
    }

    @Synchronized fun clear() { write = 0; size = 0 }
}

class EnergyVad(private val threshold: Double = 0.012, private val silenceFrames: Int = 18) {
    private var quiet = 0
    fun hasSpeech(samples: ShortArray): Boolean {
        if (samples.isEmpty()) return false
        val rms = sqrt(samples.sumOf { (it.toDouble() / 32768.0) * (it.toDouble() / 32768.0) } / samples.size)
        if (rms < threshold) quiet++ else quiet = 0
        return quiet < silenceFrames
    }
    fun reset() { quiet = 0 }
}
