package com.geoagent.network

import com.geoagent.data.local.search.TavilySearchCacheDao
import com.geoagent.data.local.search.TavilySearchCacheEntity
import com.geoagent.model.TavilyRequest
import com.geoagent.model.TavilySearchResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TavilyRepository @Inject constructor(
    private val api: TavilyApi,
    private val cacheDao: TavilySearchCacheDao
) {
    private val gson = Gson()
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun search(
        query: String,
        apiKey: String,
        maxResults: Int = 5
    ): Result<List<TavilySearchResult>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return Result.success(emptyList())
        val now = System.currentTimeMillis()
        val queryHash = normalizedQuery.cacheHash()

        getMemory(queryHash, now)?.let { return Result.success(it) }
        getRoom(queryHash, now)?.let { cached ->
            memoryCache[queryHash] = CacheEntry(cached, now)
            return Result.success(cached)
        }

        return runCatching {
            val results = searchNetworkWithRetry(normalizedQuery, apiKey, maxResults)
            save(queryHash, normalizedQuery, results, now)
            results
        }
    }

    private fun getMemory(queryHash: String, now: Long): List<TavilySearchResult>? {
        val entry = memoryCache[queryHash] ?: return null
        return if (now - entry.createdAtMillis <= CACHE_TTL_MILLIS) {
            entry.results
        } else {
            memoryCache.remove(queryHash)
            null
        }
    }

    private suspend fun getRoom(queryHash: String, now: Long): List<TavilySearchResult>? {
        val entity = cacheDao.find(queryHash) ?: return null
        if (now - entity.createdAtMillis > CACHE_TTL_MILLIS) return null
        val type = object : TypeToken<List<TavilySearchResult>>() {}.type
        return gson.fromJson<List<TavilySearchResult>>(entity.responseJson, type)
    }

    private suspend fun save(
        queryHash: String,
        query: String,
        results: List<TavilySearchResult>,
        now: Long
    ) {
        memoryCache[queryHash] = CacheEntry(results, now)
        cacheDao.upsert(
            TavilySearchCacheEntity(
                queryHash = queryHash,
                query = query,
                responseJson = gson.toJson(results),
                createdAtMillis = now
            )
        )
        cacheDao.deleteExpired(now - CACHE_TTL_MILLIS)
    }

    private suspend fun searchNetworkWithRetry(
        query: String,
        apiKey: String,
        maxResults: Int
    ): List<TavilySearchResult> {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val response = withTimeout(TAVILY_REQUEST_TIMEOUT_MILLIS) {
                    api.search(
                        authorization = "Bearer $apiKey",
                        request = TavilyRequest(query = query, maxResults = maxResults)
                    )
                }
                if (!response.isSuccessful) {
                    throw IllegalStateException("Tavily HTTP ${response.code()}")
                }
                val body = response.body()
                    ?: throw IllegalStateException("Tavily response is empty")
                return body.results.mapNotNull { item ->
                    val title = item.title.orEmpty().trim()
                    val url = item.url.orEmpty().trim()
                    val content = (item.content ?: item.rawContent).orEmpty().trim()
                    if (title.isBlank() || url.isBlank() || content.isBlank()) null
                    else TavilySearchResult(title = title, url = url, content = content)
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1) delay(500L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Tavily search failed")
    }

    private fun String.cacheHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(lowercase().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class CacheEntry(
        val results: List<TavilySearchResult>,
        val createdAtMillis: Long
    )

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val TAVILY_REQUEST_TIMEOUT_MILLIS = 15_000L
        private const val CACHE_TTL_MILLIS = 30L * 60L * 1000L
    }
}
