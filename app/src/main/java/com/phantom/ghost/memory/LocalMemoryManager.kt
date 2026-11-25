package com.phantom.ghost.memory

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    @Query("DELETE FROM memory_chunk WHERE id NOT IN (SELECT id FROM memory_chunk ORDER BY createdAt DESC LIMIT 1000)") suspend fun pruneChunks()
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

class LocalMemoryManager(context: Context, private val embedder: LocalEmbedder) {
    private val dao = PhantomMemoryDatabase.get(context).memoryDao()
    private val index = SimpleVectorIndex()

    suspend fun saveTurn(role: String, text: String) = withContext(Dispatchers.IO) {
        require(text.isNotBlank())
        dao.addTurn(ConversationEntity(role = role, content = text))
        val vector = embedder.embed(text)
        require(vector.isNotEmpty() && vector.all { it.isFinite() })
        dao.addChunk(MemoryChunkEntity(text = text, vector = vector.toBytes()))
        dao.pruneChunks()
    }

    suspend fun searchContext(query: String, limit: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val queryVector = embedder.embed(query)
        val entries = dao.recentChunks(1000).map { MemoryChunkEntity ->
            SimpleVectorIndex.Entry(MemoryChunkEntity.id, MemoryChunkEntity.text, MemoryChunkEntity.vector.toFloats())
        }
        index.search(queryVector, entries, limit).map { it.entry.text }
    }

    private fun FloatArray.toBytes() = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).also { buffer ->
        forEach { value -> buffer.putFloat(value) }
    }.array()

    private fun ByteArray.toFloats(): FloatArray {
        require(size % 4 == 0) { "Invalid embedding blob." }
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size / 4) { buffer.float }
    }
}
