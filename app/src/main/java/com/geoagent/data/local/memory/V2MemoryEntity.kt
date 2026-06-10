package com.geoagent.data.local.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "v2_memory")
data class V2MemoryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val content: String,
    val sourceAgent: String,
    val createdAtMillis: Long,
    val importance: Float
)

@Entity(tableName = "v2_tasks")
data class V2TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sourceAgent: String,
    val status: String,
    val priority: Int,
    val dueAtMillis: Long?,
    val relatedArtifact: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
