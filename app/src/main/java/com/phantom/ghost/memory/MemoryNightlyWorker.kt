package com.phantom.ghost.memory

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phantom.ghost.ai.ConversationSummarizer
import com.phantom.ghost.ai.GenerationConfig
import com.phantom.ghost.ai.LlamaEngine
import com.phantom.ghost.ai.PersonaMode
import com.phantom.ghost.ai.TokenCounter
import com.phantom.ghost.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Headless extraction pipeline. The database is deliberately not in a transaction while
 * either native model is running. Source rows are only cleared after all facts have embeddings.
 */
class MemoryNightlyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val profile = ProfileManager(applicationContext)
        val database = PhantomMemoryDatabase.get(applicationContext)
        val dao = database.memoryDao()
        val profileId = profile.localProfileId
        if (!profile.autoLearnEnabled) {
            dao.clearEligibleTurns(profileId)
            return@withContext Result.success()
        }
        dao.deleteExpiredTurns(profileId, System.currentTimeMillis() - RETENTION_MS)
        val turns = dao.eligibleTurns(profileId, 80)
            .filter { it.createdAt >= System.currentTimeMillis() - DAY_MS }
        if (turns.isEmpty()) return@withContext Result.success()

        val modelManager = ModelManager(applicationContext)
        try {
            if (modelManager.checkModelPresence() != com.phantom.ghost.model.ModelStatus.READY) {
                return@withContext Result.retry()
            }

            // Phase 1: Llama extraction. No embedding session is initialized yet.
            val llama = LlamaEngine(
                tokenCounter = TokenCounter { (it.length / 4).coerceAtLeast(1) },
                summarizer = ConversationSummarizer { archived ->
                    archived.joinToString(" ") { "${it.role}: ${it.content}" }.take(1200)
                }
            )
            llama.load(modelManager.modelFile.absolutePath)
            val transcript = turns.joinToString("\n") { "${it.role}: ${it.content}" }.take(12000)
            val extractionPrompt = """
                Review this opt-in local conversation history from the last 24 hours.
                Extract 3-5 concise, durable Dense Facts useful in future conversations.
                Focus on explicit non-sensitive preferences, recurring non-sensitive entities,
                and strategic goals. Exclude small talk, transient queries, credentials,
                financial account details, health details, identity traits, religion, politics,
                and intimate personal information.
                Return one fact per line, prefixed with "- ". If there are no durable facts,
                return exactly: NONE

                CONVERSATION:
                $transcript
            """.trimIndent()
            val generated = StringBuilder()
            try {
                llama.generateResponse(extractionPrompt, PersonaMode.STANDARD,
                    GenerationConfig(maxTokens = 220, contextTokens = 2048, temperature = 0.1f, topP = 0.8f)
                ).collect { generated.append(it) }
            } finally {
                llama.unloadModel()
            }

            val facts = generated.toString().lineSequence()
                .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                .filter { it.length in 12..500 && !MemoryPrivacyFilter.isSensitive(it) }
                .distinctBy { it.lowercase() }
                .take(5)
                .toList()

            // Phase 2: ONNX embeddings only after llama.cpp has released its native model/context.
            val embeddings = mutableListOf<Pair<String, ByteArray>>()
            if (facts.isNotEmpty()) {
                EmbeddingEngine(applicationContext).use { embedder ->
                    embedder.initialize()
                    for (fact in facts) {
                        val vector = embedder.embed(fact)
                        check(vector.size == EmbeddingEngine.DIMENSIONS && vector.all { it.isFinite() })
                        val magnitude = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
                        check(magnitude.isFinite() && magnitude in 0.999..1.001) {
                            "Embedding output must be L2-normalized before it is stored."
                        }
                        embeddings += fact to vector.toBytes()
                    }
                }
            }

            // If the user disables Auto-Learn while inference is running, do not commit.
            if (!profile.autoLearnEnabled) return@withContext Result.success()

            // Phase 3: one short atomic commit. Failed inference above leaves all source rows eligible.
            database.withTransaction {
                embeddings.forEach { (fact, vector) ->
                    dao.addMemory(
                        MemoryEntity(
                            content = fact,
                            embeddingBlob = vector,
                            localProfileId = profileId,
                            importanceScore = 0.7f
                        )
                    )
                }
                dao.markProcessed(profileId, turns.map { it.id })
            }
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private fun FloatArray.toBytes(): ByteArray =
        ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
            .also { buffer -> forEach(buffer::putFloat) }.array()

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val RETENTION_MS = 7L * DAY_MS
    }
}
