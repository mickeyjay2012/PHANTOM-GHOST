package com.phantom.ghost.memory

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Entity(tableName = "conversation", indices = [Index(value = ["userId", "createdAt"])])
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_chunk", indices = [Index(value = ["userId", "createdAt"])])
data class MemoryChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val text: String,
    val vector: ByteArray,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Insert suspend fun addTurn(turn: ConversationEntity): Long
    @Insert suspend fun addChunk(chunk: MemoryChunkEntity): Long
    @Query("SELECT * FROM memory_chunk WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentChunks(userId: String, limit: Int): List<MemoryChunkEntity>
    @Query("SELECT * FROM conversation WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentTurns(userId: String, limit: Int): List<ConversationEntity>
    @Query("DELETE FROM memory_chunk WHERE userId = :userId AND id NOT IN (SELECT id FROM memory_chunk WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1000)")
    suspend fun pruneChunks(userId: String)
}

@Database(entities = [ConversationEntity::class, MemoryChunkEntity::class], version = 2, exportSchema = true)
abstract class PhantomMemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    companion object {
        @Volatile private var INSTANCE: PhantomMemoryDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversation ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memory_chunk ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_userId_createdAt ON conversation(userId, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_chunk_userId_createdAt ON memory_chunk(userId, createdAt)")
            }
        }
        fun get(context: Context): PhantomMemoryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, PhantomMemoryDatabase::class.java, "phantom_memory.db")
                .addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}

interface LocalEmbedder { suspend fun embed(text: String): FloatArray }

class LocalMemoryManager(
    context: Context,
    private val embedder: LocalEmbedder,
    private val currentUserId: () -> String
) {
    private val dao = PhantomMemoryDatabase.get(context).memoryDao()
    private val index = SimpleVectorIndex()

    suspend fun saveTurn(role: String, text: String) = withContext(Dispatchers.IO) {
        require(text.isNotBlank())
        val userId = currentUserId().also { require(it.isNotBlank()) { "An authenticated user is required." } }
        dao.addTurn(ConversationEntity(userId = userId, role = role, content = text))
        val vector = embedder.embed(text)
        require(vector.isNotEmpty() && vector.all { it.isFinite() })
        dao.addChunk(MemoryChunkEntity(userId = userId, text = text, vector = vector.toBytes()))
        dao.pruneChunks(userId)
    }

    suspend fun searchContext(query: String, limit: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val userId = currentUserId().also { require(it.isNotBlank()) { "An authenticated user is required." } }
        val queryVector = embedder.embed(query)
        val entries = dao.recentChunks(userId, 1000).map { chunk ->
            SimpleVectorIndex.Entry(chunk.id, chunk.text, chunk.vector.toFloats())
        }
        index.search(queryVector, entries, limit).map { it.entry.text }
    }

    private fun FloatArray.toBytes() = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).also { buffer ->
        forEach(buffer::putFloat)
    }.array()

    private fun ByteArray.toFloats(): FloatArray {
        require(size % 4 == 0) { "Invalid embedding blob." }
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size / 4) { buffer.float }
    }
}
