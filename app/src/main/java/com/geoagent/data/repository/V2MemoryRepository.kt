package com.geoagent.data.repository

import com.geoagent.agent.v2.V2AgentId
import com.geoagent.data.local.memory.V2MemoryDao
import com.geoagent.data.local.memory.V2MemoryEntity
import com.geoagent.data.local.memory.V2TaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class V2MemoryRepository(
    private val dao: V2MemoryDao
) {
    fun observeRecent(limit: Int = 20): Flow<List<V2MemoryEntity>> = dao.observeRecent(limit)

    suspend fun remember(
        content: String,
        sourceAgent: V2AgentId,
        kind: String = "conversation",
        importance: Float = 0.5f
    ) {
        if (content.isBlank()) return
        dao.upsert(
            V2MemoryEntity(
                id = UUID.randomUUID().toString(),
                kind = kind,
                content = content.trim(),
                sourceAgent = sourceAgent.wireName,
                createdAtMillis = System.currentTimeMillis(),
                importance = importance.coerceIn(0f, 1f)
            )
        )
    }

    suspend fun search(query: String, limit: Int = 10): List<V2MemoryEntity> {
        if (query.isBlank()) return emptyList()

        val safeLimit = limit.coerceIn(1, 50)
        val direct = dao.search(query.trim(), safeLimit)
        if (direct.size >= safeLimit) return direct

        val directIds = direct.map { it.id }.toSet()
        val tokens = memorySearchTokens(query)
        val ranked = dao.recent(80)
            .asSequence()
            .filterNot { it.id in directIds }
            .map { entity -> entity to entity.memoryRelevanceScore(tokens) }
            .filter { it.second > 0f }
            .sortedWith(
                compareByDescending<Pair<V2MemoryEntity, Float>> { it.second }
                    .thenByDescending { it.first.importance }
                    .thenByDescending { it.first.createdAtMillis }
            )
            .map { it.first }
            .take(safeLimit - direct.size)
            .toList()
        return direct + ranked
    }

    suspend fun recentMemories(limit: Int = 20): List<V2MemoryEntity> =
        dao.recent(limit.coerceIn(1, 100))

    suspend fun pruneBefore(oldestAllowedMillis: Long) {
        dao.deleteOlderThan(oldestAllowedMillis)
    }

    suspend fun clearAll() {
        dao.deleteAllMemory()
        dao.deleteAllTasks()
    }

    suspend fun saveTask(
        title: String,
        description: String,
        sourceAgent: V2AgentId,
        status: String = "open",
        priority: Int = 3,
        dueAtMillis: Long? = null,
        relatedArtifact: String? = null
    ): V2TaskEntity {
        val now = System.currentTimeMillis()
        val task = V2TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { sourceAgent.wireName },
            description = description.trim(),
            sourceAgent = sourceAgent.wireName,
            status = status,
            priority = priority.coerceIn(1, 5),
            dueAtMillis = dueAtMillis,
            relatedArtifact = relatedArtifact,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        dao.upsertTask(task)
        return task
    }

    suspend fun recentTasks(limit: Int = 20): List<V2TaskEntity> =
        dao.recentTasks(limit.coerceIn(1, 100))

    suspend fun openTasks(limit: Int = 20): List<V2TaskEntity> =
        dao.tasksByStatus("open", limit.coerceIn(1, 100))

    suspend fun tasksByAgent(agentId: V2AgentId, limit: Int = 20): List<V2TaskEntity> =
        dao.tasksByAgent(agentId.wireName, limit.coerceIn(1, 100))

    private fun V2MemoryEntity.memoryRelevanceScore(tokens: Set<String>): Float {
        if (tokens.isEmpty()) return 0f
        val normalizedContent = content.lowercase()
        val hits = tokens.count { normalizedContent.contains(it) }
        return hits.toFloat() / tokens.size
    }

    private fun memorySearchTokens(query: String): Set<String> {
        val normalized = query.lowercase()
        val latinTokens = Regex("""[\p{L}\p{N}_-]{2,}""")
            .findAll(normalized)
            .map { it.value }
        val cjkText = Regex("""[\p{IsHan}]+""")
            .findAll(normalized)
            .joinToString("") { it.value }
        val cjkTokens = cjkText
            .windowed(size = 2, step = 1, partialWindows = false)
            .asSequence()
        return (latinTokens + cjkTokens)
            .filter { it.length >= 2 }
            .take(40)
            .toSet()
    }
}
