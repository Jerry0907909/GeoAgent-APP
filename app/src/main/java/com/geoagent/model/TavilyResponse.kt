package com.geoagent.model

import com.google.gson.annotations.SerializedName

data class TavilyResponse(
    val answer: String? = null,
    val results: List<TavilyResultDto> = emptyList()
)

data class TavilyResultDto(
    val title: String? = null,
    val url: String? = null,
    val content: String? = null,
    @SerializedName("raw_content")
    val rawContent: String? = null
)

data class TavilySearchResult(
    val title: String,
    val url: String,
    val content: String
)

data class SearchSource(
    val title: String,
    val url: String
)
