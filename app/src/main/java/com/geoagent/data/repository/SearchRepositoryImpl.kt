package com.geoagent.data.repository

import com.geoagent.BuildConfig
import com.geoagent.data.api.TavilySearchClient
import com.geoagent.data.api.dto.SearchEvent
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class SearchRepositoryImpl(
    private val tavilyClient: TavilySearchClient,
    private val apiKeyStore: ApiKeyStore
) : SearchRepository {

    override fun deepSearch(query: String): Flow<SearchEvent> = flow {
        val apiKey = apiKeyStore.tavilyKey.first()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.TAVILY_API_KEY.takeIf { it.isNotBlank() }
        if (apiKey.isNullOrBlank()) {
            emit(SearchEvent.Error("请先设置 Tavily API Key"))
            return@flow
        }
        emitAll(tavilyClient.search(query, apiKey))
    }
}
