package com.geoagent.data.api.dto

import com.google.gson.Gson
import com.google.gson.JsonParser

// Search DTOs

data class DeepSearchRequest(
    val query: String
)

sealed class SearchEvent {
    data class Plan(val queries: List<String>) : SearchEvent()
    data class Search(val results: List<SearchResultItem>) : SearchEvent()
    data class Extract(val content: String) : SearchEvent()
    data class Answer(val content: String) : SearchEvent()
    data class Citation(val citations: List<CitationItem>) : SearchEvent()
    data class Done(val message: String? = null) : SearchEvent()
    data class Error(val message: String) : SearchEvent()
}

data class SearchResultItem(
    val title: String = "",
    val url: String = "",
    val snippet: String = ""
)

data class CitationItem(
    val title: String = "",
    val url: String = ""
)

fun parseSearchEvent(data: String): SearchEvent {
    return try {
        val json = JsonParser.parseString(data).asJsonObject
        val type = json.get("type")?.asString ?: return SearchEvent.Error("Unknown event type")
        val gson = Gson()
        when (type) {
            "plan" -> {
                val queries = gson.fromJson(json.get("queries"), Array<String>::class.java).toList()
                SearchEvent.Plan(queries)
            }
            "search" -> {
                val results = gson.fromJson(json.get("results"), Array<SearchResultItem>::class.java).toList()
                SearchEvent.Search(results)
            }
            "extract" -> SearchEvent.Extract(json.get("content")?.asString ?: "")
            "answer" -> SearchEvent.Answer(json.get("content")?.asString ?: "")
            "citation" -> {
                val citations = gson.fromJson(json.get("citations"), Array<CitationItem>::class.java).toList()
                SearchEvent.Citation(citations)
            }
            "done" -> {
                val message = json.get("message")?.asString
                SearchEvent.Done(message)
            }
            "error" -> SearchEvent.Error(json.get("message")?.asString ?: "Unknown error")
            else -> SearchEvent.Error("Unknown event type: $type")
        }
    } catch (e: Exception) {
        SearchEvent.Error("Failed to parse search event: ${e.message}")
    }
}

// Preferences DTOs
data class PreferencesResponse(
    val language: String? = "zh-CN",
    val theme: String? = "light",
    val default_model: String? = null,
    val max_context_messages: Int? = 10,
    val enable_memory: Boolean? = true
)

data class PreferencesUpdateRequest(
    val language: String? = null,
    val theme: String? = null,
    val enable_memory: Boolean? = null
)