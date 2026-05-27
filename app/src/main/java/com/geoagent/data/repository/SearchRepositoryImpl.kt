package com.geoagent.data.repository

import com.geoagent.data.api.SearchSseClient
import com.geoagent.data.api.dto.SearchEvent
import com.geoagent.data.local.TokenDataStore
import com.geoagent.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class SearchRepositoryImpl(
    private val searchSseClient: SearchSseClient,
    private val tokenDataStore: TokenDataStore
) : SearchRepository {

    override fun deepSearch(query: String): Flow<SearchEvent> = flow {
        val token = tokenDataStore.accessToken.first()
        if (token.isNullOrBlank()) {
            emit(SearchEvent.Error("未登录，请重新登录"))
            return@flow
        }
        emitAll(searchSseClient.streamSearch(query, token))
    }
}