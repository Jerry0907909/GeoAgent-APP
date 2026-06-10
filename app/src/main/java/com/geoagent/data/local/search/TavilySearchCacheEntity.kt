package com.geoagent.data.local.search

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tavily_search_cache")
data class TavilySearchCacheEntity(
    @PrimaryKey val queryHash: String,
    val query: String,
    val responseJson: String,
    val createdAtMillis: Long
)
