package com.geoagent.data.repository

import android.content.ContentValues
import com.geoagent.BuildConfig
import com.geoagent.data.api.ChatMessage
import com.geoagent.data.api.DeepSeekChatClient
import com.geoagent.data.api.SiliconFlowEmbeddingClient
import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatResponse
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.data.api.dto.SourceDto
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.DocumentChunker
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.local.GeoAgentDatabase
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.domain.ConversationContextBuilder
import com.geoagent.domain.SearchContext
import com.geoagent.domain.SearchPromptTools
import com.geoagent.domain.SearchUseCase
import com.geoagent.domain.SearchUseCaseEvent
import com.geoagent.domain.model.Conversation
import com.geoagent.domain.model.Message
import com.geoagent.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.sqrt

class ChatRepositoryImpl(
    private val deepSeekClient: DeepSeekChatClient,
    private val searchUseCase: SearchUseCase,
    private val apiKeyStore: ApiKeyStore,
    private val documentStore: DocumentStore,
    private val embeddingClient: SiliconFlowEmbeddingClient,
    private val db: GeoAgentDatabase,
    private val userPrefsDataStore: UserPrefsDataStore
) : ChatRepository {

    override suspend fun chat(request: ChatStreamRequest): Result<ChatResponse> {
        return try {
            val apiKey = deepseekApiKey()
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
        val apiKey = deepseekApiKey()
        if (apiKey.isNullOrBlank()) {
            emit(ChatEvent.Error("请先设置 DeepSeek API Key"))
            return@flow
        }
        val contextQuestion = ConversationContextBuilder.buildChatContextQuestion(
            question = request.message,
            history = request.history
        )
        val ragChunks = if (request.mode == "rag") searchByEmbedding(contextQuestion) else emptyList()

        val searchContext = if (request.web_search == true) {
            emit(ChatEvent.Status("智能搜索中…"))
            val tavilyKey = tavilyApiKey()
            if (tavilyKey.isNullOrBlank()) {
                emit(ChatEvent.Error("请先设置 Tavily API Key"))
                return@flow
            }
            var context: SearchContext? = null
            var error: String? = null
            searchUseCase.searchRequiredContext(contextQuestion, tavilyKey).collect { event ->
                when (event) {
                    is SearchUseCaseEvent.Status -> emit(ChatEvent.Status("正在检索网络来源…"))
                    is SearchUseCaseEvent.Plan -> {
                        emit(ChatEvent.Status("正在检索网络来源…"))
                        if (request.enable_thinking) {
                            emit(ChatEvent.Thinking(SearchPromptTools.buildSearchPlanThinking(event.queries)))
                        }
                    }
                    is SearchUseCaseEvent.SearchReady -> {
                        context = event.context
                        if (request.enable_thinking) {
                            emit(ChatEvent.Thinking(SearchPromptTools.buildSearchReadyThinking(event.context.results)))
                        }
                    }
                    is SearchUseCaseEvent.Error -> error = event.message
                    SearchUseCaseEvent.NoSearch -> Unit
                }
            }
            if (error != null) {
                emit(ChatEvent.Error(error!!))
                return@flow
            }
            context
        } else null

        val searchResults = searchContext?.toSourceDtos().orEmpty()
        val ragResults = ragChunks.toSourceDtos()
        val chatRequest = if (searchContext != null) {
            request.copy(message = searchContext.enhancedPrompt)
        } else request
        val messages = buildMessages(chatRequest, ragChunks)
        var emittedContent = false
        var retrySearchSynthesis = false
        deepSeekClient.streamChat(messages, apiKey, enableThinking = request.enable_thinking).collect { event ->
            when (event) {
                is ChatEvent.Thinking -> emit(event)
                is ChatEvent.Content -> {
                    emittedContent = true
                    emit(event)
                }
                is ChatEvent.Status -> emit(event)
                is ChatEvent.Done -> {
                    when {
                        ragResults.isNotEmpty() -> emit(ChatEvent.Sources(ragResults))
                        searchResults.isNotEmpty() -> emit(ChatEvent.Sources(searchResults))
                    }
                    emit(event)
                }
                is ChatEvent.Sources -> {
                    if (searchResults.isEmpty() && ragResults.isEmpty()) emit(event)
                }
                is ChatEvent.Error -> {
                    if (searchContext != null && !emittedContent) {
                        retrySearchSynthesis = true
                    } else {
                        emit(event)
                    }
                }
                else -> emit(event)
            }
        }

        if (retrySearchSynthesis) {
            emit(ChatEvent.Status("正在综合生成…"))
            var retryEmittedContent = false
            val retryMessages = buildMessages(
                request.copy(
                    message = SearchPromptTools.buildRetryPrompt(contextQuestion, searchContext!!.results),
                    web_search = true
                ),
                emptyList()
            )
            deepSeekClient.streamChat(retryMessages, apiKey).collect { event ->
                when (event) {
                    is ChatEvent.Content -> {
                        retryEmittedContent = true
                        emit(event)
                    }
                    is ChatEvent.Status -> emit(ChatEvent.Status("正在综合生成…"))
                    is ChatEvent.Done -> {
                        if (searchResults.isNotEmpty()) emit(ChatEvent.Sources(searchResults))
                        emit(event)
                    }
                    is ChatEvent.Sources -> {
                        if (searchResults.isEmpty()) emit(event)
                    }
                    is ChatEvent.Error -> {
                        if (!retryEmittedContent) {
                            emit(ChatEvent.Content(SearchPromptTools.buildFallbackAnswer(contextQuestion, searchContext.results)))
                            if (searchResults.isNotEmpty()) emit(ChatEvent.Sources(searchResults))
                            emit(ChatEvent.Done())
                        } else {
                            emit(event)
                        }
                    }
                    else -> emit(event)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun followUp(question: String, answer: String): Result<List<String>> {
        return try {
            val apiKey = deepseekApiKey() ?: return Result.failure(Exception("请先设置 API Key"))
            val followUpMessages = listOf(
                ChatMessage("system", "你是 GeoScientist 智能助手。生成3个简短的相关追问。只返回JSON数组格式。"),
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

    private suspend fun deepseekApiKey(): String? =
        apiKeyStore.deepseekKey.first()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.LLM_API_KEY.takeIf { it.isNotBlank() }

    private suspend fun tavilyApiKey(): String? =
        apiKeyStore.tavilyKey.first()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.TAVILY_API_KEY.takeIf { it.isNotBlank() }

    private suspend fun siliconFlowApiKey(): String? =
        apiKeyStore.siliconFlowKey.first()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SILICONFLOW_API_KEY.takeIf { it.isNotBlank() }

    private fun SearchContext.toSourceDtos(): List<SourceDto> =
        results.map {
            SourceDto(
                content = it.content.take(SOURCE_CONTENT_LIMIT),
                source = it.title,
                url = it.url,
                type = "web",
                published_date = it.publishedDate
            )
        }

    private companion object {
        private const val SOURCE_CONTENT_LIMIT = 700
    }

    override suspend fun listConversations(limit: Int): Result<List<Conversation>> {
        return try {
            val db = this.db.readableDatabase
            val cursor = db.rawQuery(
                "SELECT m1.conversation_id, MAX(m1.timestamp), " +
                "(SELECT content FROM messages m2 WHERE m2.conversation_id = m1.conversation_id ORDER BY timestamp DESC LIMIT 1), " +
                "c.title " +
                "FROM messages m1 " +
                "LEFT JOIN conversations c ON c.id = m1.conversation_id " +
                "GROUP BY m1.conversation_id " +
                "ORDER BY MAX(m1.timestamp) DESC LIMIT ?",
                arrayOf(limit.toString())
            )
            val conversations = mutableListOf<Conversation>()
            cursor.use {
                while (it.moveToNext()) {
                    conversations.add(Conversation(
                        id = it.getInt(0),
                        title = it.getString(3),
                        lastMessage = it.getString(2)?.take(100) ?: "",
                        updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it.getLong(1)))
                    ))
                }
            }
            Result.success(conversations)
        } catch (e: Exception) {
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    override fun saveMessage(conversationId: Int, message: Message) {
        try {
            val writable = db.writableDatabase
            val now = System.currentTimeMillis()
            writable.insert("messages", null, ContentValues().apply {
                put("conversation_id", conversationId)
                put("role", message.role)
                put("content", message.content)
                put("timestamp", now)
            })
            ensureConversation(writable, conversationId, now)
            maybeAutoTitleConversation(writable, conversationId)
        } catch (_: Exception) {}
    }

    override fun updateConversationTitle(conversationId: Int, title: String) {
        val normalized = normalizeTitle(title)
        if (normalized.isBlank()) return
        try {
            val writable = db.writableDatabase
            ensureConversation(writable, conversationId, System.currentTimeMillis())
            writable.update(
                "conversations",
                ContentValues().apply {
                    put("title", normalized)
                    put("title_edited", 1)
                    put("updated_at", System.currentTimeMillis())
                },
                "id = ?",
                arrayOf(conversationId.toString())
            )
        } catch (_: Exception) {}
    }

    override suspend fun clearAllConversations(): Result<Unit> {
        return runCatching {
            val writable = db.writableDatabase
            writable.beginTransaction()
            try {
                writable.delete("messages", null, null)
                writable.delete("conversations", null, null)
                writable.setTransactionSuccessful()
            } finally {
                writable.endTransaction()
            }
        }
    }

    private fun ensureConversation(db: android.database.sqlite.SQLiteDatabase, conversationId: Int, now: Long) {
        db.insertWithOnConflict(
            "conversations",
            null,
            ContentValues().apply {
                put("id", conversationId)
                putNull("title")
                put("title_edited", 0)
                put("updated_at", now)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        )
        db.update(
            "conversations",
            ContentValues().apply { put("updated_at", now) },
            "id = ?",
            arrayOf(conversationId.toString())
        )
    }

    private fun maybeAutoTitleConversation(db: android.database.sqlite.SQLiteDatabase, conversationId: Int) {
        val edited = db.rawQuery(
            "SELECT title_edited FROM conversations WHERE id = ?",
            arrayOf(conversationId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) == 1 else false
        }
        if (edited) return

        val messages = mutableListOf<Message>()
        db.rawQuery(
            "SELECT role, content FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC LIMIT 6",
            arrayOf(conversationId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(Message(role = cursor.getString(0), content = cursor.getString(1)))
            }
        }
        val title = buildConversationTitle(messages)
        if (title.isBlank()) return
        db.update(
            "conversations",
            ContentValues().apply {
                put("title", title)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ? AND title_edited = 0",
            arrayOf(conversationId.toString())
        )
    }

    private fun buildConversationTitle(messages: List<Message>): String {
        val user = messages.firstOrNull { it.role == Message.ROLE_USER }?.content.orEmpty()
        val assistant = messages.firstOrNull { it.role == Message.ROLE_ASSISTANT }?.content.orEmpty()
        val base = when {
            user.contains("新闻") -> "今日新闻速览"
            user.contains("文献") || user.contains("资料") || user.contains("文档") -> "文档资料问答"
            user.contains("搜索") || user.contains("联网") -> "联网搜索问答"
            user.contains("图片") || assistant.contains("图片") -> "图片内容分析"
            else -> user
        }
        return normalizeTitle(base.ifBlank { assistant })
    }

    private fun normalizeTitle(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[`*_#>\[\]（）()【】"'“”‘’]"""), "")
            .replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('，', '。', '？', '?', '！', '!', '：', ':', '、')
        if (cleaned.isBlank()) return ""
        return cleaned.take(18)
    }

    private suspend fun buildMessages(
        request: ChatStreamRequest,
        ragChunks: List<RagSourceChunk> = emptyList()
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        val systemPrompt = buildString {
            if (request.web_search == true) {
                append("你是 GeoScientist。你已接入联网搜索。禁止在思考中逐条分析每个搜索结果——思考仅用于快速判断来源可信度和规划回答结构，控制在 3 句话以内。直接输出最终回答，用自然段落概括而非逐条罗列。")
            } else {
                append("你是 GeoScientist，一个通用智能助手。回答应准确、有条理。")
            }
            append("如果启用思考模式，思考必须极度简练——仅确认问题意图、判断所需信息、规划回答方向。控制在 3 句话以内，像速记笔记而非完整段落。禁止逐点列举、展开分析、重复用户问题。请始终使用简体中文。")
            if (request.history.isNotEmpty()) {
                append(" 结合对话历史理解代词和省略表达。")
            }
            val customInstruction = userPrefsDataStore.customInstruction.first().trim()
            if (customInstruction.isNotBlank()) {
                append("\n\n用户自定义指令：")
                append(customInstruction)
            }
            if (ragChunks.isNotEmpty()) {
                append("\n\n以下是从用户上传文献中检索到的相关内容，请基于此回答。")
                append("正文引用只使用 [1]、[2] 这样的编号，不要输出【来源】、来源列表或裸链接；来源由界面组件展示。\n")
                ragChunks.forEachIndexed { i, chunk ->
                    append("\n--- 文献块 ${i + 1}｜${chunk.documentName}#${chunk.chunkIndex} ---\n${chunk.text}\n")
                }
            }
        }
        messages.add(ChatMessage("system", systemPrompt))

        for (h in request.history) {
            if (h.role == Message.ROLE_USER || h.role == Message.ROLE_ASSISTANT) {
                messages.add(ChatMessage(h.role, h.content))
            }
        }

        if (!request.image_base64.isNullOrBlank()) {
            messages.add(
                ChatMessage.userWithImage(
                    request.message,
                    request.image_base64,
                    request.image_mime_type ?: "image/jpeg"
                )
            )
        } else {
            messages.add(ChatMessage("user", request.message))
        }

        return messages
    }

    private suspend fun searchByEmbedding(query: String): List<RagSourceChunk> {
        return try {
            val apiKey = siliconFlowApiKey() ?: return emptyList()
            val queryEmbedding = embeddingClient.embedSingle(query, apiKey).getOrNull() ?: return emptyList()

            val allEmbeddings = documentStore.getAllEmbeddings()
            if (allEmbeddings.isEmpty()) return emptyList()

            val documentsById = documentStore.getDocumentsSnapshot().associateBy { it.id }
            val chunkTexts = mutableMapOf<String, RagSourceChunk>()
            val indexableChunks = documentStore.getAllChunks()
                .groupBy({ it.first }, { it.second })
                .flatMap { (docId, chunks) ->
                    DocumentChunker.indexableStoredChunks(chunks).map { docId to it }
                }
            for ((docId, chunk) in indexableChunks) {
                val document = documentsById[docId]
                chunkTexts["${docId}_${chunk.index}"] = RagSourceChunk(
                    documentId = docId,
                    documentName = document?.name ?: docId,
                    chunkIndex = chunk.index,
                    text = chunk.text,
                    score = 0f
                )
            }

            val scored = allEmbeddings
                .filter { (chunkId, _, _) -> chunkTexts.containsKey(chunkId) }
                .map { (chunkId, _, vec) ->
                    val similarity = cosineSimilarity(queryEmbedding, vec)
                    chunkId to similarity
                }
                .filter { it.second > 0.3f }
                .sortedByDescending { it.second }
                .take(5)

            scored.mapNotNull { (chunkId, score) -> chunkTexts[chunkId]?.copy(score = score) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun List<RagSourceChunk>.toSourceDtos(): List<SourceDto> {
        return distinctBy { it.documentId }
            .map { firstChunk ->
                val documentChunks = filter { it.documentId == firstChunk.documentId }
                SourceDto(
                    content = documentChunks.joinToString("\n\n") { it.text }.take(SOURCE_CONTENT_LIMIT),
                    source = firstChunk.documentName,
                    url = null,
                    type = "knowledge_base",
                    relevance_score = documentChunks.maxOfOrNull { it.score },
                    document_id = firstChunk.documentId,
                    document_name = firstChunk.documentName
                )
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

    private data class RagSourceChunk(
        val documentId: String,
        val documentName: String,
        val chunkIndex: Int,
        val text: String,
        val score: Float
    )
}
