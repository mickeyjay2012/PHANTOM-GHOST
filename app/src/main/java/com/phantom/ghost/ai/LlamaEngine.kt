package com.phantom.ghost.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class GenerationConfig(
    val maxTokens: Int = 512,
    val contextTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f
) {
    init {
        require(maxTokens in 1..4096)
        require(contextTokens in 512..32768)
        require(temperature in 0f..2f && topP in 0.01f..1f && repeatPenalty in 0.8f..2f)
    }
}

data class ChatTurn(val role: String, val content: String)

interface TokenCounter { suspend fun count(text: String): Int }
interface ConversationSummarizer { suspend fun summarize(messages: List<ChatTurn>): String }

/** Streaming facade. Native generation is deliberately gated until the llama.cpp submodule is built. */
class LlamaEngine(
    private val native: LlamaNative = JniLlamaNative(),
    private val tokenCounter: TokenCounter,
    private val summarizer: ConversationSummarizer
) {
    private val mutex = Mutex()
    private val history = mutableListOf<ChatTurn>()
    private var loaded = false

    suspend fun load(modelPath: String) = mutex.withLock {
        check(native.loadModel(modelPath)) { "Native Llama runtime is not available or model could not be loaded." }
        loaded = true
    }

    fun stream(userText: String, config: GenerationConfig = GenerationConfig()): Flow<String> = flow {
        mutex.withLock {
            check(loaded) { "Load a verified GGUF model before generating." }
            history += ChatTurn("user", userText)
            compact(config.contextTokens)
            val prompt = history.joinToString("\n") { "${it.role}: ${it.content}" } + "\nassistant:"
            native.generate(prompt, config).collect { piece ->
                emit(piece)
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun compact(limit: Int) {
        while (tokenCounter.count(history.joinToString("\n") { "${it.role}: ${it.content}" }) > limit - 512) {
            if (history.size <= 2) break
            val old = history.take((history.size / 2).coerceAtLeast(1))
            val summary = summarizer.summarize(old)
            history.subList(0, old.size).clear()
            history.add(0, ChatTurn("system", "Earlier conversation summary: $summary"))
        }
    }

    suspend fun reset() = mutex.withLock { history.clear() }
}

interface LlamaNative {
    fun loadModel(path: String): Boolean
    fun generate(prompt: String, config: GenerationConfig): Flow<String>
}

private class JniLlamaNative : LlamaNative {
    override fun loadModel(path: String) = NativeBridge.loadModel(path)
    override fun generate(prompt: String, config: GenerationConfig): Flow<String> =
        NativeBridge.generate(prompt, config.maxTokens, config.temperature, config.topP, config.repeatPenalty)
}
