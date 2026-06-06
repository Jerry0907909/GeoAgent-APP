package com.geoagent.data.repository

import android.content.ContentValues
import com.geoagent.data.api.ChatMessage
import com.geoagent.data.api.DeepSeekChatClient
import com.geoagent.data.api.SiliconFlowEmbeddingClient
import com.geoagent.data.api.TavilySearchClient
import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatResponse
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.data.api.dto.SearchEvent
import com.geoagent.data.api.dto.SourceDto
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.DocumentChunker
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.local.GeoAgentDatabase
import com.geoagent.domain.model.Conversation
import com.geoagent.domain.model.Message
import com.geoagent.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.math.sqrt

class ChatRepositoryImpl(
    private val deepSeekClient: DeepSeekChatClient,
    private val tavilyClient: TavilySearchClient,
    private val apiKeyStore: ApiKeyStore,
    private val documentStore: DocumentStore,
    private val embeddingClient: SiliconFlowEmbeddingClient,
    private val db: GeoAgentDatabase
) : ChatRepository {

    override suspend fun chat(request: ChatStreamRequest): Result<ChatResponse> {
        return try {
            val apiKey = apiKeyStore.deepseekKey.first()
            if (apiKey.isNullOrBlank()) return Result.failure(Exception("请先设置 DeepSeek API Key"))
            val messages = buildMessages(request)
            var answer = ""
            deepSeekClient.streamChat(messages, apiKey).collect { event ->
                if (event is ChatEvent.Content) answer += event.content
                else if (event is ChatEvent.Error) throw Exception(event.message)
            }
            Result.success(ChatResponse(answer = answer, conversation_id = request.conversation_id ?: 1))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun streamChat(request: ChatStreamRequest): Flow<ChatEvent> = flow {
        val apiKey = apiKeyStore.deepseekKey.first()
        if (apiKey.isNullOrBlank()) {
            emit(ChatEvent.Error("请先设置 DeepSeek API Key"))
            return@flow
        }

        val searchResults = if (request.web_search == true) {
            val tavilyKey = apiKeyStore.tavilyKey.first()
            if (!tavilyKey.isNullOrBlank()) {
                val results = mutableListOf<SourceDto>()
                tavilyClient.search(request.message, tavilyKey).collect { event ->
                    when (event) {
                        is SearchEvent.Status -> emit(ChatEvent.Status(event.message))
                        is SearchEvent.Search -> results.addAll(event.results.map {
                            SourceDto(content = it.snippet, source = it.title, url = it.url, type = "web")
                        })
                        is SearchEvent.Error -> emit(ChatEvent.Status("搜索未启用: ${event.message}"))
                        else -> {}
                    }
                }
                results
            } else {
                emit(ChatEvent.Status("未设置 Tavily API Key，跳过联网搜索"))
                emptyList()
            }
        } else emptyList()

        val messages = buildMessages(request, searchResults)
        deepSeekClient.streamChat(messages, apiKey).collect { event ->
            when (event) {
                is ChatEvent.Content -> emit(event)
                is ChatEvent.Status -> emit(event)
                is ChatEvent.Done -> {
                    if (searchResults.isNotEmpty()) emit(ChatEvent.Sources(searchResults))
                    emit(event)
                }
                is ChatEvent.Sources -> emit(event)
                is ChatEvent.Error -> emit(event)
                else -> emit(event)
            }
        }
    }

    override suspend fun followUp(question: String, answer: String): Result<List<String>> {
        return try {
            val apiKey = apiKeyStore.deepseekKey.first() ?: return Result.failure(Exception("请先设置 API Key"))
            val followUpMessages = listOf(
                ChatMessage("system", "你是一个地质学助手。生成3个简短的相关追问。只返回JSON数组格式。"),
                ChatMessage("user", "问题: $question\n回答: $answer\n\n请生成3个追问，JSON数组格式：[\"追问1\", \"追问2\", \"追问3\"]")
            )
            var jsonStr = ""
            deepSeekClient.streamChat(followUpMessages, apiKey).collect { event ->
                when (event) {
                    is ChatEvent.Content -> jsonStr += event.content
                    is ChatEvent.Error -> throw Exception(event.message)
                    else -> {}
                }
            }
            val questions = try {
                val cleaned = jsonStr.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                com.google.gson.Gson().fromJson(cleaned, Array<String>::class.java).toList()
            } catch (_: Exception) {
                listOf("能否详细说明？", "还有其他相关研究吗？", "这个结论的依据是什么？")
            }
            Result.success(questions)
        } catch (e: Exception) {
            Result.success(listOf("能否详细说明？", "还有其他相关研究吗？", "这个结论的依据是什么？"))
        }
    }

    override suspend fun listConversations(limit: Int): Result<List<Conversation>> {
        return try {
            val db = this.db.readableDatabase
            val cursor = db.rawQuery(
                "SELECT conversation_id, MAX(timestamp), " +
                "(SELECT content FROM messages m2 WHERE m2.conversation_id = m1.conversation_id ORDER BY timestamp DESC LIMIT 1) " +
                "FROM messages m1 GROUP BY conversation_id ORDER BY MAX(timestamp) DESC LIMIT ?",
                arrayOf(limit.toString())
            )
            val conversations = mutableListOf<Conversation>()
            cursor.use {
                while (it.moveToNext()) {
                    conversations.add(Conversation(
                        id = it.getInt(0),
                        title = null,
                        lastMessage = it.getString(2)?.take(100) ?: "",
                        updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it.getLong(1)))
                    ))
                }
            }
            Result.success(conversations)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getConversationMessages(conversationId: Int): Result<List<Message>> {
        return try {
            val db = this.db.readableDatabase
            val cursor = db.rawQuery(
                "SELECT role, content FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC",
                arrayOf(conversationId.toString())
            )
            val messages = mutableListOf<Message>()
            cursor.use {
                while (it.moveToNext()) {
                    messages.add(Message(role = it.getString(0), content = it.getString(1)))
                }
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override fun saveMessage(conversationId: Int, message: Message) {
        try {
            db.writableDatabase.insert("messages", null, ContentValues().apply {
                put("conversation_id", conversationId)
                put("role", message.role)
                put("content", message.content)
                put("timestamp", System.currentTimeMillis())
            })
        } catch (_: Exception) {}
    }

    private suspend fun buildMessages(
        request: ChatStreamRequest,
        searchResults: List<SourceDto> = emptyList()
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        val ragChunks = if (request.mode == "rag") {
            searchByEmbedding(request.message)
        } else emptyList()

        val systemPrompt = buildString {
            append("你是一个专业的地质学文献智能助手，擅长回答地质学相关问题。")
            if (ragChunks.isNotEmpty()) {
                append("\n\n以下是从用户上传文献中检索到的相关内容，请基于此回答，末尾标注【来源】：\n")
                ragChunks.forEachIndexed { i, chunk ->
                    append("\n--- 文献块 ${i + 1} ---\n$chunk\n")
                }
            }
            if (searchResults.isNotEmpty()) {
                append("\n\n以下是从网络搜索到的相关资料：\n")
                searchResults.forEachIndexed { i, src ->
                    append("${i + 1}. [${src.source}] ${src.content}\n")
                }
            }
        }
        messages.add(ChatMessage("system", systemPrompt))

        for (h in request.history) {
            if (h.role == Message.ROLE_USER || h.role == Message.ROLE_ASSISTANT) {
                messages.add(ChatMessage(h.role, h.content))
            }
        }

        if (request.image_base64 != null) {
            messages.add(ChatMessage("user", buildString {
                append(request.message)
                append("\n[图片已作为上下文提供]")
            }))
        } else {
            messages.add(ChatMessage("user", request.message))
        }

        return messages
    }

    private suspend fun searchByEmbedding(query: String): List<String> {
        return try {
            // Generate query embedding
            val queryEmbedding = embeddingClient.embedSingle(query).getOrNull() ?: return emptyList()

            // Compare with all stored embeddings using cosine similarity
            val allEmbeddings = documentStore.getAllEmbeddings()
            if (allEmbeddings.isEmpty()) return emptyList()

            val chunkTexts = mutableMapOf<String, String>()
            val chunks = documentStore.getAllChunks()
            for ((docId, chunk) in chunks) {
                chunkTexts["${docId}_${chunk.index}"] = chunk.text
            }

            val scored = allEmbeddings.map { (chunkId, _, vec) ->
                val similarity = cosineSimilarity(queryEmbedding, vec)
                chunkId to similarity
            }
                .filter { it.second > 0.3f }
                .sortedByDescending { it.second }
                .take(5)

            scored.mapNotNull { (chunkId, _) -> chunkTexts[chunkId] }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA * normB)
        return if (denom > 0f) dot / denom else 0f
    }
}
