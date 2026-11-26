package com.phantom.ghost.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** JNI adapter that forwards each decoded token to a Flow collector. */
object NativeBridge {
    private val loadError: Throwable? = runCatching { System.loadLibrary("phantom_native") }.exceptionOrNull()
    private val loaded get() = loadError == null
    @Volatile private var activeSink: SendChannel<String>? = null

    fun runtimeStatus(): String = if (loaded) {
        runCatching { nativeRuntimeStatus() }.getOrElse { "Native JNI entry point unavailable: ${it.javaClass.simpleName}" }
    } else "Native runtime unavailable: ${loadError?.javaClass?.simpleName ?: "load error"}"

    fun loadModel(path: String): Boolean = loaded && runCatching { nativeLoadLlama(path) }.getOrDefault(false)
    fun loadWhisperModel(path: String): Boolean = loaded && runCatching { nativeLoadWhisper(path) }.getOrDefault(false)

    fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, repeatPenalty: Float): Flow<String> = callbackFlow {
        check(loaded) { runtimeStatus() }
        check(activeSink == null) { "A native generation is already active." }
        activeSink = channel
        val worker = launch(Dispatchers.IO) {
            try {
                if (nativeGenerate(prompt, maxTokens, temperature, topP, repeatPenalty)) close()
                else close(IllegalStateException("Native generation was cancelled or failed."))
            } catch (error: Throwable) {
                close(error)
            }
        }
        awaitClose {
            nativeCancelGeneration()
            worker.cancel()
            if (activeSink === channel) activeSink = null
        }
    }

    @Suppress("unused")
    fun onTokenGenerated(token: String) { activeSink?.trySend(token) }

    fun transcribe(samples: ShortArray): String {
        check(loaded) { runtimeStatus() }
        return nativeTranscribe(samples)
    }

    private external fun nativeRuntimeStatus(): String
    private external fun nativeLoadLlama(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, repeatPenalty: Float): Boolean
    private external fun nativeCancelGeneration()
    private external fun nativeLoadWhisper(path: String): Boolean
    private external fun nativeTranscribe(samples: ShortArray): String
}
