package com.phantom.ghost.memory

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

@Entity(tableName = "conversation")
data class ConversationEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val role: String, val content: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "memory_chunk")
data class MemoryChunkEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val vector: ByteArray, val createdAt: Long = System.currentTimeMillis())

@Dao
interface MemoryDao {
    @Insert suspend fun addTurn(turn: ConversationEntity): Long
    @Insert suspend fun addChunk(chunk: MemoryChunkEntity): Long
    @Query("SELECT * FROM memory_chunk ORDER BY createdAt DESC LIMIT :limit") suspend fun recentChunks(limit: Int): List<MemoryChunkEntity>
    @Query("SELECT * FROM conversation ORDER BY createdAt DESC LIMIT :limit") suspend fun recentTurns(limit: Int): List<ConversationEntity>
}

@Database(entities = [ConversationEntity::class, MemoryChunkEntity::class], version = 1, exportSchema = true)
abstract class PhantomMemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    companion object {
        @Volatile private var INSTANCE: PhantomMemoryDatabase? = null
        fun get(context: Context): PhantomMemoryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, PhantomMemoryDatabase::class.java, "phantom_memory.db").build().also { INSTANCE = it }
        }
    }
}

interface LocalEmbedder { suspend fun embed(text: String): FloatArray }

/** Room-backed memory with exact local cosine ranking. Replace the embedder with a bundled ONNX model for semantic quality. */
class LocalMemoryManager(context: Context, private val embedder: LocalEmbedder) {
    private val dao = PhantomMemoryDatabase.get(context).memoryDao()

    suspend fun saveTurn(role: String, text: String) = withContext(Dispatchers.IO) {
        require(text.isNotBlank())
        dao.addTurn(ConversationEntity(role = role, content = text))
        val vector = embedder.embed(text)
        require(vector.isNotEmpty() && vector.all { it.isFinite() })
        dao.addChunk(MemoryChunkEntity(text = text, vector = vector.toBytes()))
    }

    suspend fun searchContext(query: String, limit: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val queryVector = embedder.embed(query)
        dao.recentChunks(500).mapNotNull { chunk ->
            val vector = chunk.vector.toFloats()
            if (vector.size != queryVector.size) null else chunk.text to cosine(queryVector, vector)
        }.sortedByDescending { it.second }.take(limit.coerceIn(1, 10)).map { it.first }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0; var aa = 0.0; var bb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
        return if (aa == 0.0 || bb == 0.0) 0f else (dot / (sqrt(aa) * sqrt(bb))).toFloat()
    }
    private fun FloatArray.toBytes() = java.nio.ByteBuffer.allocate(size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).also { b -> forEach { value -> b.putFloat(value) } }.array()
    private fun ByteArray.toFloats(): FloatArray {
        val b = java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size / 4) { b.float }
    }
}
