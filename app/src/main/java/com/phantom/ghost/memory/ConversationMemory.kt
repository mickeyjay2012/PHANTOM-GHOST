package com.phantom.ghost.memory

import com.phantom.ghost.ai.ChatMessage

/** Bounded in-process buffer. This first milestone does not persist messages to disk. */
class ConversationMemory(private val maxMessages: Int = 40) {
    private val entries = ArrayDeque<ChatMessage>()
    @Synchronized fun add(message: ChatMessage) {
        entries.addLast(message)
        while (entries.size > maxMessages) entries.removeFirst()
    }
    @Synchronized fun snapshot(): List<ChatMessage> = entries.toList()
    @Synchronized fun clear() = entries.clear()
}
