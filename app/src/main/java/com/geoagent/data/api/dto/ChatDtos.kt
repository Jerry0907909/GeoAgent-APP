package com.geoagent.data.api.dto

import com.google.gson.Gson
import com.google.gson.JsonParser

data class ChatStreamRequest(
    val message: String,
    val conversation_id: Int? = null,
    val mode: String = "chat",
    val top_k: Int? = 5,
    val min_relevance_score: Float? = 0.0f,
    val web_search: Boolean? = false,
    val return_sources: Boolean? = true,
    val image_base64: String? = null,
    val history: List<ChatHistoryMessage> = emptyList()
)

data class ChatHistoryMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val answer: String,
    val sources: List<SourceDto>? = null,
    val conversation_id: Int
)

sealed class ChatEvent {
    data class Info(val conversation_id: Int?) : ChatEvent()
    data class Status(val message: String) : ChatEvent()
    data class Content(val content: String) : ChatEvent()
    data class Sources(val sources: List<SourceDto>) : ChatEvent()
    data class Done(val message: String? = null) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}

data class SourceDto(
    val content: String,
    val source: String,
    val url: String? = null,
    val type: String = "document",
    val relevance_score: Float? = null,
    val images: List<SourceImageDto>? = null
)

data class SourceImageDto(
    val base64: String,
    val page: Int,
    val width: Int,
    val height: Int
)

fun parseChatEvent(data: String): ChatEvent {
    return try {
        val json = JsonParser.parseString(data).asJsonObject
        val type = json.get("type")?.asString ?: return ChatEvent.Error("Unknown event type")
        val gson = Gson()
        when (type) {
            "info" -> {
                val id = json.get("conversation_id")
                    ?.takeIf { !it.isJsonNull }
                    ?.asInt
                ChatEvent.Info(id)
            }
            "status" -> ChatEvent.Status(json.get("message")?.asString ?: "")
            "content" -> ChatEvent.Content(json.get("content")?.asString ?: "")
            "sources" -> {
                val sources = gson.fromJson(json.get("sources"), Array<SourceDto>::class.java).toList()
                ChatEvent.Sources(sources)
            }
            "done" -> ChatEvent.Done(json.get("message")?.takeIf { !it.isJsonNull }?.asString)
            "error" -> ChatEvent.Error(json.get("message")?.asString ?: "Unknown error")
            else -> ChatEvent.Error("Unknown event type: $type")
        }
    } catch (e: Exception) {
        ChatEvent.Error("Failed to parse chat event: ${e.message}")
    }
}
