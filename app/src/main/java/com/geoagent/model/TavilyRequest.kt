package com.geoagent.model

import com.google.gson.annotations.SerializedName

data class TavilyRequest(
    val query: String,
    @SerializedName("search_depth")
    val searchDepth: String = "advanced",
    @SerializedName("include_answer")
    val includeAnswer: Boolean = true,
    @SerializedName("include_raw_content")
    val includeRawContent: Boolean = false,
    @SerializedName("max_results")
    val maxResults: Int = 5
)
