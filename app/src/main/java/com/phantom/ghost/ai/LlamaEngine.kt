package com.phantom.ghost.ai

import com.phantom.ghost.memory.LocalMemoryManager
import com.phantom.ghost.memory.MemoryChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
data class MemoryRecallEvent(val type: String = "MEMORY_RETRIEVED", val facts: List<String>, val scores: List<Float>)
fun interface TokenCounter { suspend fun count(text: String): Int }
fun interface ConversationSummarizer { suspend fun summarize(messages: List<ChatTurn>): String }
interface LlamaNative {
    fun loadModel(path: String): Boolean
    fun generate(prompt: String, config: GenerationConfig): Flow<String>
    fun isModelLoaded(path: String): Boolean
    fun unloadModel()
}

class LlamaEngine(
    private val native: LlamaNative = JniLlamaNative(),
    private val tokenCounter: TokenCounter,
    private val summarizer: ConversationSummarizer,
    private val personaManager: PersonaManager = PersonaManager(),
    private val persistArchived: suspend (List<ChatTurn>) -> Unit = {},
    private val memoryManager: LocalMemoryManager? = null
) {
    private val mutex = Mutex()

    fun updateGhostName(name: String) = personaManager.setGhostName(name)
    fun updateUserAddress(preferredName: String, selfReference: String) =
        personaManager.setUserAddress(preferredName, selfReference)
    private val history = mutableListOf<ChatTurn>()
    private var loaded = false
    private var loadedModelPath: String? = null
    private var userTurnsSinceSummary = 0
    private var contextSummary: String? = null
    private val mutableMemoryEvents = MutableSharedFlow<MemoryRecallEvent>(extraBufferCapacity = 16)
    val memoryEvents = mutableMemoryEvents.asSharedFlow()

    suspend fun load(modelPath: String) = mutex.withLock {
        check(native.loadModel(modelPath)) { "Native Llama runtime unavailable or model failed to load." }
        loadedModelPath = modelPath
        loaded = true
    }

    /** Releases native model/context memory; used by headless jobs before loading ONNX. */
    suspend fun unloadModel() = mutex.withLock {
        if (loaded) native.unloadModel()
        loaded = false
        loadedModelPath = null
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
            val modelPath = checkNotNull(loadedModelPath) { "The loaded model path is unavailable." }
            // A headless WorkManager run may have unloaded the process-wide JNI model while
            // this Activity engine remained alive. Rehydrate it before the next user inference.
            if (!native.isModelLoaded(modelPath)) {
                check(native.loadModel(modelPath)) { "The local Llama model could not be reloaded after background memory work." }
            }

            // Scoped retrieval happens before every inference. A failed recall lookup is non-fatal.
            val memories: List<MemoryChunk> = runCatching {
                memoryManager?.searchContext(userInput.trim(), 3).orEmpty()
            }.getOrDefault(emptyList())
            if (memories.isNotEmpty()) {
                mutableMemoryEvents.emit(
                    MemoryRecallEvent(facts = memories.map { it.content }, scores = memories.map { it.similarity })
                )
            }

            history += ChatTurn("user", userInput.trim())
            runCatching { memoryManager?.recordEligibleTurn("user", userInput.trim()) }
            userTurnsSinceSummary++
            compact(config.contextTokens, force = userTurnsSinceSummary >= 5)

            val prompt = personaManager.buildFullContext(
                userInput = userInput,
                mode = mode,
                contextSummary = contextSummary,
                memories = memories,
                history = history
            )
            val response = StringBuilder()
            native.generate(prompt, config).collect { fragment ->
                response.append(fragment)
                emit(fragment)
            }
            if (response.isNotEmpty()) {
                history += ChatTurn("assistant", response.toString())
                runCatching { memoryManager?.recordEligibleTurn("assistant", response.toString()) }
            }
        } finally {
            mutex.unlock()
        }
    }.flowOn(Dispatchers.IO)

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

    suspend fun recordToolObservations(observations: List<String>) = mutex.withLock {
        observations.take(5).forEach { history += ChatTurn("tool", it.take(500)) }
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
        override fun isModelLoaded(path: String) = NativeBridge.isLlamaModelLoaded(path)
        override fun unloadModel() = NativeBridge.unloadLlamaModel()
    }
}
