package com.phantom.ghost.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** JNI surface. Native methods return explicit unavailable errors until pinned upstream sources are built. */
object NativeBridge {
    private val loaded = runCatching { System.loadLibrary("phantom_native"); true }.getOrDefault(false)
    fun runtimeStatus(): String = if (loaded) nativeRuntimeStatus() else "Native library unavailable."
    fun loadModel(path: String): Boolean = loaded && nativeLoadLlama(path)
    fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, repeatPenalty: Float): Flow<String> = flow {
        check(loaded) { runtimeStatus() }
        emit(nativeGenerate(prompt, maxTokens, temperature, topP, repeatPenalty))
    }
    fun loadWhisperModel(path: String): Boolean = loaded && nativeLoadWhisper(path)
    fun transcribe(samples: ShortArray): String {
        check(loaded) { runtimeStatus() }
        return nativeTranscribe(samples)
    }
    private external fun nativeRuntimeStatus(): String
    private external fun nativeLoadLlama(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, repeatPenalty: Float): String
    private external fun nativeLoadWhisper(path: String): Boolean
    private external fun nativeTranscribe(samples: ShortArray): String
}
