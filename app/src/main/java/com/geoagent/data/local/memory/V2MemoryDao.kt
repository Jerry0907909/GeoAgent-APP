package com.geoagent.data.local.memory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface V2MemoryDao {
    @Upsert
    suspend fun upsert(entity: V2MemoryEntity)

    @Query("SELECT * FROM v2_memory ORDER BY importance DESC, createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<V2MemoryEntity>>

    @Query("SELECT * FROM v2_memory WHERE content LIKE '%' || :query || '%' ORDER BY importance DESC, createdAtMillis DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<V2MemoryEntity>

    @Query("SELECT * FROM v2_memory ORDER BY importance DESC, createdAtMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<V2MemoryEntity>

    @Query("DELETE FROM v2_memory WHERE createdAtMillis < :oldestAllowedMillis")
    suspend fun deleteOlderThan(oldestAllowedMillis: Long)

    @Query("DELETE FROM v2_memory")
    suspend fun deleteAllMemory()

    @Upsert
    suspend fun upsertTask(entity: V2TaskEntity)

    @Query("SELECT * FROM v2_tasks ORDER BY priority DESC, createdAtMillis DESC LIMIT :limit")
    suspend fun recentTasks(limit: Int): List<V2TaskEntity>

    @Query("SELECT * FROM v2_tasks WHERE status = :status ORDER BY priority DESC, createdAtMillis DESC LIMIT :limit")
    suspend fun tasksByStatus(status: String, limit: Int): List<V2TaskEntity>

    @Query("SELECT * FROM v2_tasks WHERE sourceAgent = :sourceAgent ORDER BY priority DESC, createdAtMillis DESC LIMIT :limit")
    suspend fun tasksByAgent(sourceAgent: String, limit: Int): List<V2TaskEntity>

    @Query("DELETE FROM v2_tasks")
    suspend fun deleteAllTasks()
}
