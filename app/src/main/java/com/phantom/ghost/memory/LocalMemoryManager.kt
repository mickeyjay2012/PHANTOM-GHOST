package com.phantom.ghost.memory

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

@Entity(
    tableName = "conversation",
    indices = [Index(value = ["userId", "createdAt"]), Index(value = ["local_profile_id", "status", "createdAt"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = "local",
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "local_profile_id", defaultValue = "''") val localProfileId: String,
    @ColumnInfo(defaultValue = "'PROCESSED'") val status: String = STATUS_ELIGIBLE,
    @ColumnInfo(name = "auto_learn", defaultValue = "0") val autoLearn: Boolean = true,
    @ColumnInfo(name = "processed_at") val processedAt: Long? = null
) {
    companion object {
        const val STATUS_ELIGIBLE = "ELIGIBLE"
        const val STATUS_PROCESSED = "PROCESSED"
    }
}

@Entity(
    tableName = "memory_chunk",
    indices = [
        Index(value = ["userId", "createdAt"]),
        Index(value = ["local_profile_id", "createdAt"]),
        Index(value = ["local_profile_id", "retrieval_count"])
    ]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val userId: String = "local",
    @ColumnInfo(name = "text") val content: String,
    @ColumnInfo(name = "vector") val embeddingBlob: ByteArray,
    @ColumnInfo(name = "createdAt") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "local_profile_id", defaultValue = "''") val localProfileId: String,
    @ColumnInfo(name = "importance_score", defaultValue = "0.5") val importanceScore: Float = 0.5f,
    @ColumnInfo(name = "retrieval_count", defaultValue = "0") val retrievalCount: Int = 0,
    @ColumnInfo(name = "last_similarity", defaultValue = "0") val lastSimilarity: Float = 0f
)

data class MemoryChunk(
    val id: Long,
    val content: String,
    val retrievalCount: Int,
    val similarity: Float,
    val importanceScore: Float
)

@Dao
interface MemoryDao {
    @Insert
    suspend fun addTurn(turn: ConversationEntity): Long

    @Insert
    suspend fun addMemory(memory: MemoryEntity): Long

    @Query("""
        SELECT * FROM conversation
        WHERE local_profile_id = :profileId
          AND status = 'ELIGIBLE' AND auto_learn = 1
        ORDER BY createdAt ASC LIMIT :limit
    """)
    suspend fun eligibleTurns(profileId: String, limit: Int = 80): List<ConversationEntity>

    @Query("""
        UPDATE conversation SET status = 'PROCESSED', processed_at = :processedAt, content = ''
        WHERE local_profile_id = :profileId AND id IN (:ids) AND status = 'ELIGIBLE'
    """)
    suspend fun markProcessed(profileId: String, ids: List<Long>, processedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT * FROM memory_chunk
        WHERE local_profile_id = :profileId
        ORDER BY retrieval_count DESC, importance_score DESC, createdAt DESC
        LIMIT 10
    """)
    suspend fun topMemories(profileId: String): List<MemoryEntity>

    @Query("SELECT * FROM memory_chunk WHERE local_profile_id = :profileId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun candidateMemories(profileId: String, limit: Int = 1000): List<MemoryEntity>

    @Query("""
        UPDATE memory_chunk SET retrieval_count = retrieval_count + 1, last_similarity = :similarity
        WHERE local_profile_id = :profileId AND id = :memoryId
    """)
    suspend fun recordRetrieval(profileId: String, memoryId: Long, similarity: Float)

    @Query("DELETE FROM memory_chunk WHERE local_profile_id = :profileId AND id = :memoryId")
    suspend fun deleteMemory(profileId: String, memoryId: Long)

    @Query("DELETE FROM memory_chunk WHERE local_profile_id = :profileId")
    suspend fun clearVault(profileId: String)

    @Query("SELECT COUNT(*) FROM memory_chunk WHERE local_profile_id = :profileId")
    suspend fun memoryCount(profileId: String): Int

    @Query("DELETE FROM conversation WHERE local_profile_id = :profileId AND createdAt < :cutoff")
    suspend fun deleteExpiredTurns(profileId: String, cutoff: Long)

    @Query("DELETE FROM conversation WHERE local_profile_id = :profileId AND status = 'ELIGIBLE'")
    suspend fun clearEligibleTurns(profileId: String)
}

@Database(entities = [ConversationEntity::class, MemoryEntity::class], version = 3, exportSchema = true)
abstract class PhantomMemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var INSTANCE: PhantomMemoryDatabase? = null

        // v1 -> v2 was the already-shipped userId migration. Keep it for installs upgrading
        // directly from v1; profile-scoped columns are introduced in v3.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversation ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memory_chunk ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_userId_createdAt ON conversation(userId, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_chunk_userId_createdAt ON memory_chunk(userId, createdAt)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversation ADD COLUMN local_profile_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversation ADD COLUMN status TEXT NOT NULL DEFAULT 'PROCESSED'")
                db.execSQL("ALTER TABLE conversation ADD COLUMN auto_learn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversation ADD COLUMN processed_at INTEGER")
                db.execSQL("ALTER TABLE memory_chunk ADD COLUMN local_profile_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memory_chunk ADD COLUMN importance_score REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE memory_chunk ADD COLUMN retrieval_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memory_chunk ADD COLUMN last_similarity REAL NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_local_profile_id_status_createdAt ON conversation(local_profile_id, status, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_chunk_local_profile_id_createdAt ON memory_chunk(local_profile_id, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_chunk_local_profile_id_retrieval_count ON memory_chunk(local_profile_id, retrieval_count)")
            }
        }

        fun get(context: Context): PhantomMemoryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, PhantomMemoryDatabase::class.java, "phantom_memory.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
        }
    }
}

interface LocalEmbedder { suspend fun embed(text: String): FloatArray }

class LocalMemoryManager(
    context: Context,
    private val embedder: LocalEmbedder,
    private val profileManager: ProfileManager
) {
    private val database = PhantomMemoryDatabase.get(context)
    private val dao = database.memoryDao()
    private val embeddingMutex = Mutex()
    val profileId: String get() = profileManager.localProfileId

    suspend fun recordEligibleTurn(role: String, text: String) = withContext(Dispatchers.IO) {
        if (!profileManager.autoLearnEnabled || text.isBlank() || MemoryPrivacyFilter.isSensitive(text)) return@withContext
        dao.addTurn(
            ConversationEntity(
                role = role,
                content = text.take(6000),
                localProfileId = profileId,
                autoLearn = true
            )
        )
    }

    suspend fun pin(text: String): Long = withContext(Dispatchers.IO) {
        require(text.isNotBlank())
        require(!MemoryPrivacyFilter.isSensitive(text)) { "This message appears to contain sensitive information and was not pinned." }
        val vector = embedText(text)
        require(vector.size == EmbeddingEngine.DIMENSIONS && vector.all { it.isFinite() })
        val magnitude = sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
        require(magnitude in 0.999..1.001) { "Embedding must be L2-normalized." }
        dao.addMemory(
            MemoryEntity(
                content = text.trim().take(2000),
                embeddingBlob = vector.toBytes(),
                localProfileId = profileId,
                importanceScore = 1.0f
            )
        )
    }

    suspend fun searchContext(query: String, limit: Int = 3): List<MemoryChunk> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val queryVector = embedText(query)
        val queryMagnitude = sqrt(queryVector.sumOf { it.toDouble() * it.toDouble() })
        require(queryVector.size == EmbeddingEngine.DIMENSIONS && queryMagnitude in 0.999..1.001)
        val candidates = dao.candidateMemories(profileId)
        val ranked = candidates.mapNotNull { memory ->
            val vector = memory.embeddingBlob.toFloats()
            if (vector.size != EmbeddingEngine.DIMENSIONS) return@mapNotNull null
            val similarity = vector.indices.sumOf { vector[it].toDouble() * queryVector[it].toDouble() }.toFloat()
            if (!similarity.isFinite()) null else memory to similarity.coerceIn(-1f, 1f)
        }.filter { it.second > 0.20f }.sortedByDescending { it.second }.take(limit.coerceIn(1, 10))

        ranked.forEach { (memory, score) -> dao.recordRetrieval(profileId, memory.id, score) }
        ranked.map { (memory, score) ->
            MemoryChunk(memory.id, memory.content, memory.retrievalCount + 1, score, memory.importanceScore)
        }
    }

    suspend fun vaultTopTen(): List<MemoryChunk> = withContext(Dispatchers.IO) {
        dao.topMemories(profileId).map {
            MemoryChunk(it.id, it.content, it.retrievalCount, it.lastSimilarity, it.importanceScore)
        }
    }

    suspend fun delete(memoryId: Long) = withContext(Dispatchers.IO) {
        dao.deleteMemory(profileId, memoryId)
    }

    suspend fun clearVault() = withContext(Dispatchers.IO) {
        dao.clearVault(profileId)
    }

    suspend fun clearPendingTurns() = withContext(Dispatchers.IO) {
        dao.clearEligibleTurns(profileId)
    }

    private suspend fun embedText(text: String): FloatArray = embeddingMutex.withLock {
        if (embedder is EmbeddingEngine) embedder.initialize()
        try {
            embedder.embed(text)
        } finally {
            // Do not keep an ONNX session resident while Llama is generating or a Worker
            // is loading its native model. The pinned assets remain cached on disk.
            if (embedder is EmbeddingEngine) embedder.close()
        }
    }

    private fun FloatArray.toBytes() = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        .also { buffer -> forEach(buffer::putFloat) }.array()

    private fun ByteArray.toFloats(): FloatArray {
        require(size % 4 == 0) { "Invalid embedding blob." }
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size / 4) { buffer.float }
    }
}

/** Conservative local filter: do not turn obvious secrets or sensitive personal disclosures into memory. */
internal object MemoryPrivacyFilter {
    // Conservative deny-list for automatic capture and manual pinning. The extractor prompt
    // also excludes these categories; this local check is a second, deterministic boundary.
    private val patterns = listOf(
        Regex("(?i)\\b(password|passcode|api[_ -]?key|secret|access token|private key|recovery phrase)\\b"),
        Regex("(?i)\\b(health|medical|diagnos(ed|is)|medication|prescription|therapy|symptom|hospital|patient)\\b"),
        Regex("(?i)\\b(religion|religious|church|mosque|temple|christian|muslim|hindu|jewish|atheist)\\b"),
        Regex("(?i)\\b(politic(s|al)|political party|vote for|election preference)\\b"),
        Regex("(?i)\\b(sexual orientation|sex life|intimate relationship|gay|lesbian|bisexual|transgender)\\b"),
        Regex("(?i)\\b(criminal record|criminal history|arrested|convicted|court case)\\b"),
        Regex("(?i)\\b(bank account|account number|card number|passport|national id|home address|phone number|email address)\\b")
    )
    fun isSensitive(text: String): Boolean = patterns.any { it.containsMatchIn(text) }
}
