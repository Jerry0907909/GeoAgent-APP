package com.geoagent.domain.model

import com.geoagent.data.api.dto.SourceDto

data class User(
    val id: Int,
    val username: String,
    val email: String,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val is_active: Boolean = true,
    val is_superuser: Boolean = false
)

data class Message(
    val role: String,
    val content: String,
    val sources: List<SourceDto> = emptyList(),
    val imageBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val agentName: String? = null,
    val agentResultJson: String? = null,
    val thinkingContent: String = "",
    val thinkingStartedAt: Long? = null,
    val thinkingFinishedAt: Long? = null
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

enum class ChatMode { CHAT, RAG }

data class Conversation(
    val id: Int,
    val title: String?,
    val lastMessage: String,
    val updatedAt: String
)

data class UserPreferences(
    val language: String = "zh-CN",
    val theme: String = "light",
    val default_model: String? = null,
    val max_context_messages: Int? = 10,
    val enable_memory: Boolean? = true
)
