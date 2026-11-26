package com.phantom.ghost.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class WhisperModelSpec(val id: String, val fileName: String, val url: String, val sha256: String)

/** Downloads and verifies the preferred English model, then falls back to multilingual base. */
class WhisperModelManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val directory = File(context.applicationContext.filesDir, "models/whisper").apply { mkdirs() }
    @Volatile private var active: WhisperModelSpec? = null
    val activeModelVersion: String get() = active?.id ?: "Not loaded"

    suspend fun ensureModelLoaded(engine: WhisperEngine): String = withContext(Dispatchers.IO) {
        active?.let { return@withContext it.id }
        var failure: Throwable? = null
        for (spec in CANDIDATES) {
            val target = File(directory, spec.fileName)
            try {
                if (!target.isFile || !digest(target).equals(spec.sha256, true)) {
                    target.delete()
                    download(spec, target)
                }
                check(engine.load(target.absolutePath)) { "whisper.cpp failed to load ${spec.id}." }
                active = spec
                return@withContext spec.id
            } catch (error: Throwable) {
                failure = error
                target.delete()
                File(directory, spec.fileName + ".part").delete()
            }
        }
        throw IllegalStateException("Preferred and fallback Whisper models are unavailable.", failure)
    }

    private fun download(spec: WhisperModelSpec, target: File) {
        val part = File(directory, spec.fileName + ".part")
        val request = Request.Builder().url(spec.url).header("User-Agent", "PHANTOM-GHOST-Android").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} while downloading ${spec.id}." }
            val body = checkNotNull(response.body) { "Empty response for ${spec.id}." }
            body.byteStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n > 0) output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            }
        }
        check(part.length() > 50L * 1024 * 1024) { "Whisper model download is incomplete." }
        check(digest(part).equals(spec.sha256, true)) { "SHA-256 verification failed for ${spec.id}." }
        check(part.renameTo(target)) { "Unable to install ${spec.id}." }
    }

    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val REV = "0b364b566045a405be7225ee1e415a073e04da77"
        val CANDIDATES = listOf(
            WhisperModelSpec(
                "ggml-base.en · English", "ggml-base.en.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/$REV/ggml-base.en.bin",
                "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002"
            ),
            WhisperModelSpec(
                "ggml-base · multilingual fallback", "ggml-base.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/$REV/ggml-base.bin",
                "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"
            )
        )
    }
}
