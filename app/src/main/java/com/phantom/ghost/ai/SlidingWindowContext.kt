package com.phantom.ghost.ai

/** Persistence adapter; implementations should partition records by authenticated user ID. */
interface ConversationArchive {
    suspend fun append(userId: String, messages: List<ChatTurn>)
}

fun interface ContextTokenCounter {
    suspend fun count(text: String): Int
}

/**
 * Keeps the newest turns within a token budget and archives older turns.
 * This class does not itself provide encryption or database isolation.
 */
class SlidingWindowContext(
    private val archive: ConversationArchive,
    private val counter: ContextTokenCounter,
    private val maxTokens: Int = 2048,
    private val reserveTokens: Int = 256
) {
    init {
        require(maxTokens in 512..2048)
        require(reserveTokens in 32 until maxTokens)
    }

    suspend fun fit(userId: String, input: List<ChatTurn>): List<ChatTurn> {
        require(userId.isNotBlank()) { "A user partition is required." }
        val kept = input.toMutableList()
        while (kept.size > 1 && counter.count(serialize(kept)) > maxTokens - reserveTokens) {
            val removeCount = (kept.size / 3).coerceAtLeast(1)
            val archived = kept.take(removeCount)
            archive.append(userId, archived)
            repeat(removeCount) { kept.removeAt(0) }
        }
        return kept
    }

    private fun serialize(messages: List<ChatTurn>) =
        messages.joinToString("\n") { "${it.role}: ${it.content}" }
}
