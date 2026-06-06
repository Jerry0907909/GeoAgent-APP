package com.geoagent.domain.repository

import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatResponse
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.domain.model.Conversation
import com.geoagent.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun chat(request: ChatStreamRequest): Result<ChatResponse>
    fun streamChat(request: ChatStreamRequest): Flow<ChatEvent>
    suspend fun followUp(question: String, answer: String): Result<List<String>>
    suspend fun listConversations(limit: Int = 50): Result<List<Conversation>>
    suspend fun getConversationMessages(conversationId: Int): Result<List<Message>>
    fun saveMessage(conversationId: Int, message: Message)
}