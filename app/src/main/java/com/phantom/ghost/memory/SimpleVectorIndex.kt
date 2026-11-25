package com.phantom.ghost.memory

import kotlin.math.sqrt

/** Small top-K cosine index over embeddings stored as SQLite BLOBs by Room. */
class SimpleVectorIndex {
    data class Entry(val id: Long, val text: String, val vector: FloatArray)
    data class Match(val entry: Entry, val score: Float)

    fun search(query: FloatArray, entries: List<Entry>, limit: Int = 3): List<Match> {
        require(query.isNotEmpty() && query.all { it.isFinite() })
        return entries.asSequence()
            .filter { it.vector.size == query.size && it.vector.all { value -> value.isFinite() } }
            .map { Match(it, cosine(query, it.vector)) }
            .sortedByDescending { it.score }
            .take(limit.coerceIn(1, 10))
            .toList()
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0; var aa = 0.0; var bb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
        return if (aa == 0.0 || bb == 0.0) 0f else (dot / (sqrt(aa) * sqrt(bb))).toFloat()
    }
}
