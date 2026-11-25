package com.phantom.ghost.memory

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Offloads one oldest half below 20% free heap, and rearms only above 40%. */
class MemoryGuard(
    private val runtime: Runtime = Runtime.getRuntime(),
    private val offloadAt: Double = 0.20,
    private val rearmAt: Double = 0.40
) {
    private val mutex = Mutex()
    private var armed = true

    init { require(offloadAt in 0.05..0.5 && rearmAt > offloadAt && rearmAt < 0.9) }

    suspend fun checkAndOffload(
        context: MutableList<ChatMemoryItem>,
        persist: suspend (List<ChatMemoryItem>) -> Unit
    ): Int {
        mutex.lock()
        try {
            val ratio = freeHeapFraction()
            if (!armed) {
                if (ratio >= rearmAt) armed = true
                return 0
            }
            if (ratio >= offloadAt || context.size < 2) return 0
            val count = (context.size / 2).coerceAtLeast(1)
            val oldest = context.take(count)
            persist(oldest) // Commit to SQLite before removing from RAM.
            repeat(count) { context.removeAt(0) }
            armed = false
            return count
        } finally {
            mutex.unlock()
        }
    }

    fun freeHeapFraction(): Double {
        val max = runtime.maxMemory().coerceAtLeast(1L)
        val used = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        return ((max - used).coerceAtLeast(0L).toDouble() / max).coerceIn(0.0, 1.0)
    }
}

data class ChatMemoryItem(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())
