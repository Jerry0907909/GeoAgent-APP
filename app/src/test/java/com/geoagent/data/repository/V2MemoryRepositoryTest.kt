package com.geoagent.data.repository

import com.geoagent.agent.v2.V2AgentId
import com.geoagent.data.local.memory.V2MemoryDao
import com.geoagent.data.local.memory.V2MemoryEntity
import com.geoagent.data.local.memory.V2TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2MemoryRepositoryTest {

    @Test
    fun searchFallsBackToRecentMemoryTokenRanking() = runBlocking {
        val dao = FakeV2MemoryDao(
            memories = listOf(
                memory("old", "用户正在准备盆地沉积报告，需要偏重资料引用。", importance = 0.8f),
                memory("other", "旅行攻略要包含酒店和机票信息。", importance = 0.9f)
            )
        )
        val repository = V2MemoryRepository(dao)

        val result = repository.search("写一段盆地沉积相关摘要", limit = 2)

        assertEquals("old", result.first().id)
        assertEquals(1, result.size)
        assertTrue(dao.directSearchQueries.contains("写一段盆地沉积相关摘要"))
        assertEquals(1, dao.recentCalls)
    }

    private fun memory(id: String, content: String, importance: Float): V2MemoryEntity =
        V2MemoryEntity(
            id = id,
            kind = "test",
            content = content,
            sourceAgent = V2AgentId.RESEARCH.wireName,
            createdAtMillis = id.hashCode().toLong(),
            importance = importance
        )

    private class FakeV2MemoryDao(
        private val memories: List<V2MemoryEntity>
    ) : V2MemoryDao {
        val directSearchQueries = mutableListOf<String>()
        var recentCalls = 0

        override suspend fun upsert(entity: V2MemoryEntity) = Unit

        override fun observeRecent(limit: Int): Flow<List<V2MemoryEntity>> =
            flowOf(memories.take(limit))

        override suspend fun search(query: String, limit: Int): List<V2MemoryEntity> {
            directSearchQueries += query
            return memories
                .filter { it.content.contains(query) }
                .take(limit)
        }

        override suspend fun recent(limit: Int): List<V2MemoryEntity> {
            recentCalls += 1
            return memories.take(limit)
        }

        override suspend fun deleteOlderThan(oldestAllowedMillis: Long) = Unit

        override suspend fun deleteAllMemory() = Unit

        override suspend fun upsertTask(entity: V2TaskEntity) = Unit

        override suspend fun recentTasks(limit: Int): List<V2TaskEntity> = emptyList()

        override suspend fun tasksByStatus(status: String, limit: Int): List<V2TaskEntity> = emptyList()

        override suspend fun tasksByAgent(sourceAgent: String, limit: Int): List<V2TaskEntity> = emptyList()

        override suspend fun deleteAllTasks() = Unit
    }
}
