package com.phantom.ghost.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class GenerationConfig(
    val maxTokens: Int = 160,
    val contextTokens: Int = 2048,
    val temperature: Float = 0.65f,
    val topP: Float = 0.85f,
    val repeatPenalty: Float = 1.12f
) {
    init {
        require(maxTokens in 1..512)
        require(contextTokens in 512..2048)
        require(temperature in 0f..2f && topP in 0.01f..1f && repeatPenalty in 0.8f..2f)
    }
}

data class ChatTurn(val role: String, val content: String)
interface TokenCounter { suspend fun count(text: String): Int }
interface ConversationSummarizer { suspend fun summarize(messages: List<ChatTurn>): String }
interface LlamaNative {
    fun loadModel(path: String): Boolean
    fun generate(prompt: String, config: GenerationConfig): Flow<String>
}

class LlamaEngine(
    private val native: LlamaNative = JniLlamaNative(),
    private val tokenCounter: TokenCounter,
    private val summarizer: ConversationSummarizer,
    private val personaManager: PersonaManager = PersonaManager(),
    private val persistArchived: suspend (List<ChatTurn>) -> Unit = {}
) {
    private val mutex = Mutex()
    private val history = mutableListOf<ChatTurn>()
    private var loaded = false
    private var userTurnsSinceSummary = 0
    private var contextSummary: String? = null

    suspend fun load(modelPath: String) = mutex.withLock {
        check(native.loadModel(modelPath)) { "Native Llama runtime unavailable or model failed to load." }
        loaded = true
    }

    fun generateResponse(
        userInput: String,
        mode: PersonaMode,
        config: GenerationConfig = GenerationConfig()
    ): Flow<String> = flow {
        require(userInput.isNotBlank())
        mutex.lock()
        try {
            check(loaded) { "No verified local model is loaded. Install and verify a model first." }
            history += ChatTurn("user", userInput.trim())
            userTurnsSinceSummary++
            compact(config.contextTokens, force = userTurnsSinceSummary >= 5)

            val prompt = buildString {
                append("System: ").append(personaManager.getActiveSystemPrompt(mode)).append('\n')
                if (!contextSummary.isNullOrBlank()) {
                    append("Context Summary: ").append(contextSummary).append('\n')
                }
                history.forEach { append(it.role).append(": ").append(it.content).append('\n') }
                append("assistant:")
            }
            val response = StringBuilder()
            native.generate(prompt, config).collect { fragment ->
                response.append(fragment)
                emit(fragment)
            }
            if (response.isNotEmpty()) history += ChatTurn("assistant", response.toString())
        } finally {
            mutex.unlock()
        }
    }.flowOn(Dispatchers.IO)

    /** Backward-compatible Standard-mode entry point. */
    fun stream(userText: String, config: GenerationConfig = GenerationConfig()): Flow<String> =
        generateResponse(userText, PersonaMode.STANDARD, config)

    private suspend fun compact(limit: Int, force: Boolean) {
        val serialized = history.joinToString("\n") { "${it.role}: ${it.content}" }
        if (!force && tokenCounter.count(serialized) <= limit - 256) return
        if (history.size < 2) return
        val count = (history.size / 2).coerceAtLeast(1)
        val archived = history.take(count)
        persistArchived(archived)
        contextSummary = summarizer.summarize(archived)
        history.subList(0, count).clear()
        userTurnsSinceSummary = 0
    }

    suspend fun reset() = mutex.withLock {
        history.clear()
        contextSummary = null
        userTurnsSinceSummary = 0
    }

    private class JniLlamaNative : LlamaNative {
        override fun loadModel(path: String) = NativeBridge.loadModel(path)
        override fun generate(prompt: String, config: GenerationConfig) =
            NativeBridge.generate(prompt, config.maxTokens, config.temperature, config.topP, config.repeatPenalty)
    }
}
