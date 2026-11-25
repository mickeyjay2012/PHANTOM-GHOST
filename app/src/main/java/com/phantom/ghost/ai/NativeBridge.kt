package com.phantom.ghost.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Native load failures are reported as unavailable instead of crashing unsupported ABIs. */
object NativeBridge {
    private val loadError: Throwable? = runCatching { System.loadLibrary("phantom_native") }.exceptionOrNull()
    private val loaded get() = loadError == null

    fun runtimeStatus(): String = if (loaded) {
        runCatching { nativeRuntimeStatus() }.getOrElse { "Native JNI entry point unavailable: ${it.javaClass.simpleName}" }
    } else "Native runtime unavailable: ${loadError?.javaClass?.simpleName ?: "load error"}"

    fun loadModel(path: String): Boolean = loaded && runCatching { nativeLoadLlama(path) }.getOrDefault(false)
    fun loadWhisperModel(path: String): Boolean = loaded && runCatching { nativeLoadWhisper(path) }.getOrDefault(false)

    fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, repeatPenalty: Float): Flow<String> = flow {
        check(loaded) { runtimeStatus() }
        val result = runCatching { nativeGenerate(prompt, maxTokens, temperature, topP, repeatPenalty) }
            .getOrElse { throw IllegalStateException("Native generation unavailable: ${it.javaClass.simpleName}", it) }
        emit(result)
    }

    fun transcribe(samples: ShortArray): String {
        check(loaded) { runtimeStatus() }
        return runCatching { nativeTranscribe(samples) }
            .getOrElse { throw IllegalStateException("Native transcription unavailable: ${it.javaClass.simpleName}", it) }
    }

    private external fun nativeRuntimeStatus(): String
    private external fun nativeLoadLlama(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, repeatPenalty: Float): String
    private external fun nativeLoadWhisper(path: String): Boolean
    private external fun nativeTranscribe(samples: ShortArray): String
}
