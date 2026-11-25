package com.phantom.ghost.ai

data class ChatMessage(val role: String, val content: String)
interface AssistantEngine { fun reply(prompt: String): String }

/** UI-only placeholder used until an actual on-device model runtime is integrated. */
class DemoAssistantEngine : AssistantEngine {
    override fun reply(prompt: String): String =
        "The interface received your message. The on-device language model is not connected yet. Your prompt was: \"${prompt.take(180)}\""
}
