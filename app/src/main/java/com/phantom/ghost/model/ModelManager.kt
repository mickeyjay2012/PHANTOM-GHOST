package com.phantom.ghost.model

import android.content.Context
import com.phantom.ghost.ai.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

enum class ModelStatus { NOT_FOUND, DOWNLOADING, VERIFYING, READY, LOADED, ACTIVE, ERROR }

data class ModelSpec(
    val id: String,
    val fileName: String,
    val url: String,
    val sha256: String,
    val expectedBytes: Long
) {
    init {
        require(url.startsWith("https://"))
        require(sha256.matches(Regex("[a-fA-F0-9]{64}")))
    }
}

/**
 * Downloads a pinned GGUF into app-private external storage, verifies its SHA-256,
 * and only then permits the native engine to load it.
 */
class ModelManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) {
    private val appContext = context.applicationContext
    private val modelDirectory = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "models")
        .apply { mkdirs() }

    private val _modelState = MutableStateFlow(ModelStatus.NOT_FOUND)
    val modelState: StateFlow<ModelStatus> = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val modelFile: File get() = File(modelDirectory, DEFAULT_MODEL.fileName)

    suspend fun checkModelPresence(): ModelStatus = withContext(Dispatchers.IO) {
        _lastError.value = null
        if (!modelFile.isFile) {
            _modelState.value = ModelStatus.NOT_FOUND
        } else {
            _modelState.value = ModelStatus.VERIFYING
            if (sha256(modelFile).equals(DEFAULT_MODEL.sha256, ignoreCase = true)) {
                _modelState.value = ModelStatus.READY
            } else {
                modelFile.delete()
                _modelState.value = ModelStatus.NOT_FOUND
                _lastError.value = "Existing model failed SHA-256 verification and was removed."
            }
        }
        _modelState.value
    }

    suspend fun downloadModel(): File = withContext(Dispatchers.IO) {
        require(modelDirectory.exists() || modelDirectory.mkdirs()) { "Cannot create private model directory." }
        _lastError.value = null
        _downloadProgress.value = 0f
        _modelState.value = ModelStatus.DOWNLOADING

        val partial = File(modelDirectory, DEFAULT_MODEL.fileName + ".part")
        val request = Request.Builder()
            .url(DEFAULT_MODEL.url)
            .header("User-Agent", "PHANTOM-GHOST-Android")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Model download failed: HTTP ${response.code}." }
                val body = checkNotNull(response.body) { "The model server returned an empty response." }
                val total = body.contentLength().takeIf { it > 0 } ?: DEFAULT_MODEL.expectedBytes
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var received = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            received += count
                            _downloadProgress.value = (received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        }
                        output.fd.sync()
                    }
                }
            }

            _modelState.value = ModelStatus.VERIFYING
            check(partial.length() == DEFAULT_MODEL.expectedBytes) {
                "Downloaded file size does not match the pinned model artifact."
            }
            check(sha256(partial).equals(DEFAULT_MODEL.sha256, ignoreCase = true)) {
                "SHA-256 verification failed. The model download was rejected."
            }
            val destination = modelFile
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "Could not move verified model into place." }
            _downloadProgress.value = 1f
            _modelState.value = ModelStatus.READY
            destination
        } catch (error: Throwable) {
            partial.delete()
            _modelState.value = ModelStatus.ERROR
            _lastError.value = error.message ?: error.javaClass.simpleName
            throw error
        }
    }

    suspend fun loadModel(engine: LlamaEngine) {
        check(_modelState.value == ModelStatus.READY || _modelState.value == ModelStatus.LOADED) {
            "Download and verify the model before loading it."
        }
        try {
            engine.load(modelFile.absolutePath)
            _modelState.value = ModelStatus.LOADED
            _lastError.value = null
        } catch (error: Throwable) {
            _modelState.value = ModelStatus.ERROR
            _lastError.value = error.message ?: "Native model loading failed."
            throw error
        }
    }

    suspend fun forceRedownload() = withContext(Dispatchers.IO) {
        modelFile.delete()
        File(modelDirectory, DEFAULT_MODEL.fileName + ".part").delete()
        _downloadProgress.value = 0f
        _modelState.value = ModelStatus.NOT_FOUND
        _lastError.value = null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_NAME = "Llama 3.2 3B Instruct (Q4_K_M)"
        val DEFAULT_MODEL = ModelSpec(
            id = "llama-3.2-3b-instruct-q4_k_m",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/pingappslab/Llama-3.2-3B-Instruct-GGUF/resolve/5ab33fa94d1d04e903623ae72c95d1696f09f9e8/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sha256 = "6c1a2b41161032677be168d354123594c0e6e67d2b9227c84f296ad037c728ff",
            expectedBytes = 2019377696L
        )
    }
}
