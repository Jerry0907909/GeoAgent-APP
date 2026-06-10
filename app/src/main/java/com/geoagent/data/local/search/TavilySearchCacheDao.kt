package com.geoagent.data.local.search

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TavilySearchCacheDao {
    @Query("SELECT * FROM tavily_search_cache WHERE queryHash = :queryHash LIMIT 1")
    suspend fun find(queryHash: String): TavilySearchCacheEntity?

    @Upsert
    suspend fun upsert(entity: TavilySearchCacheEntity)

    @Query("DELETE FROM tavily_search_cache WHERE createdAtMillis < :oldestAllowedMillis")
    suspend fun deleteExpired(oldestAllowedMillis: Long)
}
