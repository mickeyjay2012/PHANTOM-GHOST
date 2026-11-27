package com.phantom.ghost.memory

import kotlin.math.sqrt

/** Small top-K cosine index over normalized embeddings stored as SQLite BLOBs. */
class SimpleVectorIndex {
    data class Entry(val id: Long, val text: String, val vector: FloatArray)
    data class Match(val entry: Entry, val score: Float)

    fun search(query: FloatArray, entries: List<Entry>, limit: Int = 3): List<Match> {
        require(query.size == EmbeddingEngine.DIMENSIONS && query.all { it.isFinite() })
        require(norm(query) > 1e-12) { "Query embedding cannot be zero." }
        return entries.asSequence()
            .filter { it.vector.size == query.size && it.vector.all { value -> value.isFinite() } && norm(it.vector) > 1e-12 }
            .map { Match(it, cosine(query, it.vector)) }
            .sortedByDescending { it.score }
            .take(limit.coerceIn(1, 10))
            .toList()
    }

    private fun norm(a: FloatArray): Double = a.sumOf { it.toDouble() * it.toDouble() }
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        for (i in a.indices) dot += a[i] * b[i]
        val denominator = sqrt(norm(a) * norm(b))
        return if (denominator <= 1e-12) 0f else (dot / denominator).toFloat()
    }
}
