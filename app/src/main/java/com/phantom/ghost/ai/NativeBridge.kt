package com.phantom.ghost.ai

/** JNI connectivity check only; llama.cpp is not integrated in this milestone. */
object NativeBridge {
    private val loaded = runCatching { System.loadLibrary("phantom_native"); true }.getOrDefault(false)
    fun runtimeStatus(): String = if (loaded) nativeRuntimeStatus() else "Native library unavailable on this device/build."
    private external fun nativeRuntimeStatus(): String
}
