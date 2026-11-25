package com.phantom.ghost.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class ModelSpec(val id: String, val fileName: String, val url: String, val sha256: String) {
    init { require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "A pinned SHA-256 is required." } }
}

class ModelManager(context: Context, private val client: OkHttpClient = OkHttpClient()) {
    private val modelDir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun localFile(spec: ModelSpec) = File(modelDir, spec.fileName)
    fun isInstalled(spec: ModelSpec): Boolean = localFile(spec).let { it.isFile && sha256(it).equals(spec.sha256, true) }

    suspend fun download(spec: ModelSpec, onProgress: (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            require(spec.url.startsWith("https://")) { "Model downloads must use HTTPS." }
            val destination = localFile(spec)
            if (isInstalled(spec)) return@withContext destination
            val partial = File(modelDir, "${spec.fileName}.part")
            val response = client.newCall(Request.Builder().url(spec.url).build()).execute()
            response.use { res ->
                check(res.isSuccessful) { "Download failed: HTTP ${res.code}" }
                val body = checkNotNull(res.body) { "Empty response body." }
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var count: Int
                        var received = 0L
                        while (input.read(buffer).also { count = it } >= 0) {
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            received += count
                            onProgress(received, total)
                        }
                        output.fd.sync()
                    }
                }
            }
            check(sha256(partial).equals(spec.sha256, true)) {
                partial.delete()
                "SHA-256 mismatch. The downloaded model was rejected."
            }
            check(partial.renameTo(destination)) { "Could not finalize model file." }
            destination
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
