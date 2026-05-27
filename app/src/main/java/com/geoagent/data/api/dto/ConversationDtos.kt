package com.geoagent.data.api.dto

data class ConversationListResponse(
    val total: Int = 0,
    val conversations: List<ConversationDto>? = null
)

data class ConversationDto(
    val id: Int? = null,
    val title: String? = null,
    val summary: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val message_count: Int = 0
)

data class MessageListResponse(
    val conversation_id: Int = 0,
    val messages: List<MessageDto>? = null,
    val total: Int = 0
)

data class MessageDto(
    val id: Int? = null,
    val conversation_id: Int? = null,
    val role: String? = null,
    val content: String? = null,
    val created_at: String? = null
)
