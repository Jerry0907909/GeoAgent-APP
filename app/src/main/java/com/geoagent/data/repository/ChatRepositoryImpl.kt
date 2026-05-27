package com.geoagent.data.repository

import com.geoagent.data.api.GeoAgentApi
import com.geoagent.data.api.SseClient
import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatResponse
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.data.local.TokenDataStore
import com.geoagent.domain.model.Conversation
import com.geoagent.domain.model.Message
import com.geoagent.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class ChatRepositoryImpl(
    private val sseClient: SseClient,
    private val api: GeoAgentApi,
    private val tokenDataStore: TokenDataStore
) : ChatRepository {

    override suspend fun chat(request: ChatStreamRequest): Result<ChatResponse> {
        return try {
            Result.success(api.chat(request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun streamChat(request: ChatStreamRequest): Flow<ChatEvent> = flow {
        val token = tokenDataStore.accessToken.first()
        if (token.isNullOrBlank()) {
            emit(ChatEvent.Error("未登录，请重新登录"))
            return@flow
        }
        emitAll(sseClient.streamChat(request, token))
    }

    override suspend fun followUp(question: String, answer: String): Result<List<String>> {
        return try {
            val request = com.geoagent.data.api.dto.FollowUpRequest(question, answer)
            Result.success(api.followUp(request).questions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listConversations(limit: Int): Result<List<Conversation>> {
        return try {
            val items = api.listConversations(limit = limit).conversations.orEmpty()
                .mapNotNull { dto ->
                    val id = dto.id ?: return@mapNotNull null
                    Conversation(
                        id = id,
                        title = dto.title,
                        lastMessage = dto.summary?.takeIf { it.isNotBlank() } ?: "暂无消息",
                        updatedAt = dto.updated_at ?: ""
                    )
                }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConversationMessages(conversationId: Int): Result<List<Message>> {
        return try {
            val messages = api.getConversationMessages(conversationId).messages.orEmpty()
                .mapNotNull { dto ->
                    val role = dto.role?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val content = dto.content.orEmpty()
                    Message(role = role, content = content)
                }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}