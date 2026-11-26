package com.phantom.ghost.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** Single-session JNI bridge; llama_context is stateful and generation is serialized. */
object NativeBridge {
    private val loadError: Throwable? = runCatching { System.loadLibrary("phantom_native") }.exceptionOrNull()
    private val loaded get() = loadError == null
    private val activeSink = AtomicReference<SendChannel<String>?>(null)

    fun runtimeStatus(): String = if (loaded) {
        runCatching { nativeRuntimeStatus() }
            .getOrElse { "Native JNI entry point unavailable: ${it.javaClass.simpleName}" }
    } else "Native runtime unavailable: ${loadError?.javaClass?.simpleName ?: "load error"}"

    fun loadModel(path: String): Boolean = loaded && runCatching { nativeLoadLlama(path) }.getOrDefault(false)
    fun loadWhisperModel(path: String): Boolean = loaded && runCatching { nativeLoadWhisper(path) }.getOrDefault(false)

    fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, repeatPenalty: Float): Flow<String> =
        callbackFlow {
            check(loaded) { runtimeStatus() }
            check(activeSink.compareAndSet(null, channel)) { "A native generation is already active." }
            val worker = launch(Dispatchers.IO) {
                try {
                    val completed = nativeGenerate(prompt, maxTokens, temperature, topP, repeatPenalty)
                    if (completed) close()
                    else close(IllegalStateException("Native generation was cancelled or failed."))
                } catch (error: Throwable) {
                    close(error)
                } finally {
                    activeSink.compareAndSet(channel, null)
                }
            }
            awaitClose {
                nativeCancelGeneration()
                worker.cancel()
                activeSink.compareAndSet(channel, null)
            }
        }.buffer(Channel.UNLIMITED)

    /** JNI calls this once for each generated text fragment. */
    @Suppress("unused")
    fun onTokenGenerated(token: String) {
        activeSink.get()?.trySend(token)
    }

    /** Accepts mono signed PCM16 samples at 16 kHz. */
    fun transcribe(samples: ShortArray): String {
        check(loaded) { runtimeStatus() }
        require(samples.isNotEmpty()) { "No PCM samples supplied to Whisper." }
        return nativeTranscribe(samples)
    }

    private external fun nativeRuntimeStatus(): String
    private external fun nativeLoadLlama(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, repeatPenalty: Float): Boolean
    private external fun nativeCancelGeneration()
    private external fun nativeLoadWhisper(path: String): Boolean
    private external fun nativeTranscribe(samples: ShortArray): String
}
