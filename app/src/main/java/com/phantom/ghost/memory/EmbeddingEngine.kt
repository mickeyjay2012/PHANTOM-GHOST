package com.phantom.ghost.memory

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.math.sqrt
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * all-MiniLM-L6-v2 sentence embedding. Uses BERT uncased WordPiece, attention-mask
 * mean pooling and L2 normalization. Model files are downloaded on demand.
 */
class EmbeddingEngine(context: Context) : LocalEmbedder, AutoCloseable {
    private val app = context.applicationContext
    private val directory = File(app.filesDir, "embedding").apply { mkdirs() }
    private val modelFile = File(directory, "model.onnx")
    private val vocabFile = File(directory, "vocab.txt")
    private val client = OkHttpClient()
    private var session: OrtSession? = null
    private var tokenizer: WordPieceTokenizer? = null
    private val env = OrtEnvironment.getEnvironment()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (session != null) return@withContext
        ensureFile(modelFile, MODEL_URL, MODEL_SHA256)
        ensureFile(vocabFile, VOCAB_URL, VOCAB_SHA256)
        tokenizer = WordPieceTokenizer(vocabFile)
        session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    }

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        require(text.isNotBlank())
        val activeSession = checkNotNull(session) { "EmbeddingEngine is not initialized." }
        val ids = checkNotNull(tokenizer).encode(text, MAX_TOKENS)
        val mask = LongArray(MAX_TOKENS) { if (it < ids.size) 1L else 0L }
        val padded = LongArray(MAX_TOKENS) { if (it < ids.size) ids[it].toLong() else 0L }
        val shape = longArrayOf(1, MAX_TOKENS.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(padded), shape).use { inputIds ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape).use { attention ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(MAX_TOKENS)), shape).use { tokenTypes ->
                    val inputs = mapOf("input_ids" to inputIds, "attention_mask" to attention, "token_type_ids" to tokenTypes)
                    activeSession.run(inputs).use { result ->
                        val output = result[0].value as Array<Array<FloatArray>>
                        normalize(meanPool(output[0], mask))
                    }
                }
            }
        }
    }

    /** Attention-mask-aware mean pooling over [sequence, hidden] token states. */
    fun meanPool(hiddenStates: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        require(hiddenStates.isNotEmpty() && hiddenStates.size == attentionMask.size)
        val dimensions = hiddenStates.first().size
        require(dimensions == DIMENSIONS && hiddenStates.all { it.size == dimensions })
        val pooled = FloatArray(dimensions)
        var count = 0
        hiddenStates.indices.forEach { token ->
            if (attentionMask[token] != 0L) {
                for (d in 0 until dimensions) pooled[d] += hiddenStates[token][d]
                count++
            }
        }
        require(count > 0) { "Cannot pool an all-padding sequence." }
        for (d in pooled.indices) pooled[d] /= count.toFloat()
        return pooled
    }

    fun normalize(vector: FloatArray): FloatArray {
        require(vector.size == DIMENSIONS && vector.all { it.isFinite() })
        val magnitude = sqrt(vector.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        require(magnitude > 1e-12f) { "Embedding vector has zero magnitude." }
        return FloatArray(vector.size) { vector[it] / magnitude }
    }

    private suspend fun ensureFile(file: File, url: String, expectedSha: String) = withContext(Dispatchers.IO) {
        if (file.isFile && sha256(file) == expectedSha) return@withContext
        file.delete()
        val partial = File(file.parentFile, file.name + ".part")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Embedding asset download failed: HTTP ${response.code}" }
            val body = checkNotNull(response.body)
            body.byteStream().use { input ->
                FileOutputStream(partial).use { output -> input.copyTo(output); output.fd.sync() }
            }
        }
        check(sha256(partial) == expectedSha) { "Embedding asset SHA-256 verification failed." }
        check(partial.renameTo(file)) { "Could not install verified embedding asset." }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    override fun close() {
        session?.close()
        session = null
        tokenizer = null
    }

    companion object {
        const val DIMENSIONS = 384
        private const val MAX_TOKENS = 128
        private const val REVISION = "03582985522b9cfbe6e2a0fcfa2f1b90f2c7fcfd"
        private const val BASE = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/$REVISION"
        private const val MODEL_URL = "$BASE/onnx/model.onnx"
        private const val VOCAB_URL = "$BASE/vocab.txt"
        private const val MODEL_SHA256 = "c01c3fb6f89996e15c1cb92ea3fa9cc3c76e9f385ed0b88ca71aebfad2be1547"
        private const val VOCAB_SHA256 = "07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3"
    }
}

/** BERT uncased greedy WordPiece tokenizer for the matching MiniLM vocabulary. */
internal class WordPieceTokenizer(file: File) {
    private val vocabulary = file.readLines().mapIndexed { index, token -> token to index }.toMap()
    private val cls = vocabulary["[CLS]"] ?: error("Missing [CLS] token")
    private val sep = vocabulary["[SEP]"] ?: error("Missing [SEP] token")
    private val unk = vocabulary["[UNK]"] ?: error("Missing [UNK] token")

    fun encode(text: String, maxLength: Int): IntArray {
        val result = mutableListOf(cls)
        val basicTokens = Regex("[a-z0-9]+|[^\\w\\s]", RegexOption.IGNORE_CASE)
            .findAll(text.lowercase()).map { it.value }
        for (token in basicTokens) {
            val pieces = wordPiece(token)
            if (result.size + pieces.size >= maxLength) break
            result.addAll(pieces)
        }
        result.add(sep)
        return result.toIntArray()
    }

    private fun wordPiece(token: String): List<Int> {
        if (token.length > 100) return listOf(unk)
        val pieces = mutableListOf<Int>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var found: Int? = null
            while (start < end) {
                val part = token.substring(start, end)
                val candidate = if (start == 0) part else "##$part"
                found = vocabulary[candidate]
                if (found != null) break
                end--
            }
            if (found == null) return listOf(unk)
            pieces.add(found)
            start = end
        }
        return pieces
    }
}
