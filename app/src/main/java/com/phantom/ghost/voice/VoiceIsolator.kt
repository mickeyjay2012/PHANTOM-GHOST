package com.phantom.ghost.voice

/**
 * Compares an embedding produced by a separately supplied on-device speaker model.
 * This class does not record audio, create a voiceprint, or persist biometric data.
 * A voice match is a convenience signal, never proof of identity or account authentication.
 */
class VoiceIsolator(
    private val threshold: Float = 0.85f,
    private val expectedDimensions: Int = 256
) {
    init {
        require(threshold in 0f..1f)
        require(expectedDimensions > 0)
    }

    enum class Decision { MATCH, REJECT, INVALID_EMBEDDING }

    fun compare(masterEmbedding: FloatArray, frameEmbedding: FloatArray): Decision {
        if (masterEmbedding.size != expectedDimensions || frameEmbedding.size != expectedDimensions) {
            return Decision.INVALID_EMBEDDING
        }
        val similarity = cosineSimilarity(masterEmbedding, frameEmbedding) ?: return Decision.INVALID_EMBEDDING
        return if (similarity >= threshold) Decision.MATCH else Decision.REJECT
    }

    /** Returns a score in [-1, 1], or null for zero/non-finite vectors. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float? {
        if (a.size != b.size || a.isEmpty()) return null
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            if (!x.isFinite() || !y.isFinite()) return null
            dot += x * y
            normA += x * x
            normB += y * y
        }
        if (normA <= 0.0 || normB <= 0.0) return null
        return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).toFloat()
    }
}
