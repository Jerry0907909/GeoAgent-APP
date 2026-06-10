package com.geoagent.domain.repository

import com.geoagent.data.api.dto.SearchEvent
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    fun deepSearch(query: String): Flow<SearchEvent>
}