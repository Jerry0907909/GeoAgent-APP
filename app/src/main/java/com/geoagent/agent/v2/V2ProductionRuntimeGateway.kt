package com.geoagent.agent.v2

import android.content.Context
import com.geoagent.BuildConfig
import com.geoagent.data.api.ChatMessage
import com.geoagent.data.api.DeepSeekChatClient
import com.geoagent.data.api.SiliconFlowEmbeddingClient
import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.repository.V2MemoryRepository
import com.geoagent.domain.SearchContext
import com.geoagent.domain.SearchUseCase
import com.geoagent.domain.SearchUseCaseEvent
import com.geoagent.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

class V2ProductionRuntimeGateway(
    private val context: Context,
    private val deepSeekClient: DeepSeekChatClient,
    private val searchUseCase: SearchUseCase,
    private val apiKeyStore: ApiKeyStore,
    private val documentStore: DocumentStore,
    private val embeddingClient: SiliconFlowEmbeddingClient,
    private val memoryRepository: V2MemoryRepository,
    private val authRepository: AuthRepository
) : V2RuntimeGateway {

    override suspend fun completeWithOneLlm(
        agentId: V2AgentId,
        systemPrompt: String,
        userPrompt: String
    ): Result<String> {
        val apiKey = deepseekApiKey()
            ?: return Result.failure(IllegalStateException("请先设置 DeepSeek API Key"))
        return deepSeekClient.completeChat(
            messages = listOf(
                ChatMessage("system", "$systemPrompt\n\n当前虚拟 Agent：${agentId.wireName}。"),
                ChatMessage("user", userPrompt)
            ),
            apiKey = apiKey
        )
    }

    override suspend fun completeWithOneLlmVision(
        agentId: V2AgentId,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String,
        imageMimeType: String
    ): Result<String> {
        val apiKey = deepseekApiKey()
            ?: return Result.failure(IllegalStateException("请先设置 DeepSeek API Key"))
        return deepSeekClient.completeChat(
            messages = listOf(
                ChatMessage("system", "$systemPrompt\n\n当前虚拟 Agent：${agentId.wireName}。"),
                ChatMessage.userWithImage(userPrompt, imageBase64, imageMimeType)
            ),
            apiKey = apiKey
        )
    }

    override suspend fun streamWithOneLlm(
        agentId: V2AgentId,
        systemPrompt: String,
        userPrompt: String,
        onContent: suspend (String) -> Unit
    ): Result<String> {
        val apiKey = deepseekApiKey()
            ?: return Result.failure(IllegalStateException("请先设置 DeepSeek API Key"))
        val content = StringBuilder()
        var error: String? = null
        deepSeekClient.streamChat(
            messages = listOf(
                ChatMessage("system", "$systemPrompt\n\n当前虚拟 Agent：${agentId.wireName}。"),
                ChatMessage("user", userPrompt)
            ),
            apiKey = apiKey
        ).collect { event ->
            when (event) {
                is ChatEvent.Content -> {
                    content.append(event.content)
                    onContent(event.content)
                }
                is ChatEvent.Error -> error = event.message
                is ChatEvent.Thinking -> Unit
                is ChatEvent.Done,
                is ChatEvent.Info,
                is ChatEvent.Sources,
                is ChatEvent.Status -> Unit
            }
        }
        error?.let { return Result.failure(IllegalStateException(it)) }
        return Result.success(content.toString())
    }

    override suspend fun streamWithOneLlmVision(
        agentId: V2AgentId,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String,
        imageMimeType: String,
        onContent: suspend (String) -> Unit
    ): Result<String> {
        val apiKey = deepseekApiKey()
            ?: return Result.failure(IllegalStateException("请先设置 DeepSeek API Key"))
        val content = StringBuilder()
        var error: String? = null
        deepSeekClient.streamChat(
            messages = listOf(
                ChatMessage("system", "$systemPrompt\n\n当前虚拟 Agent：${agentId.wireName}。"),
                ChatMessage.userWithImage(userPrompt, imageBase64, imageMimeType)
            ),
            apiKey = apiKey
        ).collect { event ->
            when (event) {
                is ChatEvent.Content -> {
                    content.append(event.content)
                    onContent(event.content)
                }
                is ChatEvent.Error -> error = event.message
                is ChatEvent.Thinking -> Unit
                is ChatEvent.Done,
                is ChatEvent.Info,
                is ChatEvent.Sources,
                is ChatEvent.Status -> Unit
            }
        }
        error?.let { return Result.failure(IllegalStateException(it)) }
        return Result.success(content.toString())
    }

    override suspend fun search(question: String): Result<V2RuntimeSearchResult> {
        val tavilyKey = tavilyApiKey()
            ?: return Result.failure(IllegalStateException("请先设置 Tavily API Key"))
        var context: SearchContext? = null
        var error: String? = null
        searchUseCase.searchRequiredContext(question, tavilyKey).collect { event ->
            when (event) {
                is SearchUseCaseEvent.SearchReady -> context = event.context
                is SearchUseCaseEvent.Error -> error = event.message
                is SearchUseCaseEvent.Plan,
                is SearchUseCaseEvent.Status,
                SearchUseCaseEvent.NoSearch -> Unit
            }
        }
        error?.let { return Result.failure(IllegalStateException(it)) }
        val ready = context ?: return Result.failure(IllegalStateException("联网搜索未返回可用上下文"))
        return Result.success(
            V2RuntimeSearchResult(
                enhancedPrompt = ready.enhancedPrompt,
                sources = ready.results.map {
                    V2RuntimeSearchSource(
                        title = it.title,
                        url = it.url,
                        content = it.content
                    )
                }
            )
        )
    }

    override suspend fun retrieveRag(
        question: String,
        topK: Int,
        minScore: Float
    ): Result<List<V2RuntimeRagChunk>> = runCatching {
        val chunkTexts = mutableMapOf<String, Pair<String, com.geoagent.data.local.DocumentChunk>>()
        documentStore.getAllChunks().forEach { (docId, chunk) ->
            chunkTexts["${docId}_${chunk.index}"] = docId to chunk
        }
        if (chunkTexts.isEmpty()) return@runCatching emptyList()
        val keywordChunks = chunkTexts.values.map { (docId, chunk) ->
            V2RuntimeRagChunk(
                documentId = docId,
                chunkIndex = chunk.index,
                text = chunk.text,
                score = 0f
            )
        }

        val apiKey = siliconFlowApiKey()
        if (apiKey == null) {
            return@runCatching rankV2RagChunksByKeywords(
                question = question,
                chunks = keywordChunks,
                topK = topK
            )
        }

        val queryEmbedding = embeddingClient.embedSingle(question, apiKey).getOrElse {
            return@runCatching rankV2RagChunksByKeywords(
                question = question,
                chunks = keywordChunks,
                topK = topK
            )
        }
        val embeddings = documentStore.getAllEmbeddings()
        if (embeddings.isEmpty()) {
            return@runCatching rankV2RagChunksByKeywords(
                question = question,
                chunks = keywordChunks,
                topK = topK
            )
        }

        embeddings
            .map { (chunkId, _, vector) -> chunkId to cosineSimilarity(queryEmbedding, vector) }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .take(topK.coerceIn(1, 20))
            .mapNotNull { (chunkId, score) ->
                val (docId, chunk) = chunkTexts[chunkId] ?: return@mapNotNull null
                V2RuntimeRagChunk(
                    documentId = docId,
                    chunkIndex = chunk.index,
                    text = chunk.text,
                    score = score
                )
            }
    }

    override suspend fun listDocuments(): Result<List<V2RuntimeDocumentRecord>> = runCatching {
        documentStore.getDocumentsSnapshot().map {
            V2RuntimeDocumentRecord(
                id = it.id,
                name = it.name,
                type = it.fileType,
                sizeBytes = it.sizeBytes,
                chunkCount = it.chunkCount
            )
        }
    }

    override suspend fun getDocumentText(documentIdOrName: String): Result<String> = runCatching {
        val documents = documentStore.getDocumentsSnapshot()
        val document = documents.firstOrNull {
            it.id == documentIdOrName || it.name.equals(documentIdOrName, ignoreCase = true)
        } ?: throw IllegalArgumentException("未找到文档：$documentIdOrName")
        val chunks = documentStore.getChunks(document.id)
        if (chunks.isEmpty()) throw IllegalStateException("文档没有可用文本片段：${document.name}")
        chunks.joinToString("\n\n") { it.text }
    }

    override suspend fun deleteDocument(documentIdOrName: String): Result<String> = runCatching {
        val documents = documentStore.getDocumentsSnapshot()
        val document = documents.firstOrNull {
            it.id == documentIdOrName || it.name.equals(documentIdOrName, ignoreCase = true)
        } ?: throw IllegalArgumentException("未找到文档：$documentIdOrName")
        documentStore.deleteDocument(document.id)
        document.name
    }

    override suspend fun prepareCalendarEvent(
        request: V2CalendarEventRequest
    ): Result<V2RuntimeCalendarArtifact> = runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.Events.TITLE, request.title)
            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, request.description)
            .putExtra(android.provider.CalendarContract.Events.EVENT_TIMEZONE, request.timeZone)
            .apply {
                request.beginTimeMillis?.let { putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                request.endTimeMillis?.let { putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, it) }
            }
        V2RuntimeCalendarArtifact(
            title = request.title,
            description = request.description,
            timeZone = request.timeZone,
            action = intent.action.orEmpty(),
            dataUri = intent.dataString.orEmpty()
        )
    }

    override suspend fun sendEmail(request: V2EmailRequest): Result<String> {
        return authRepository.sendEmail(request.to, request.subject, request.content)
            .map { response -> response.message.ifBlank { "邮件已发送至 ${request.to}" } }
    }

    override suspend fun recallMemory(question: String, limit: Int): Result<List<V2RuntimeMemory>> = runCatching {
        memoryRepository.search(question, limit.coerceIn(1, 20)).map { entity ->
            V2RuntimeMemory(
                content = entity.content,
                sourceAgent = V2AgentId.entries.firstOrNull { it.wireName == entity.sourceAgent },
                kind = entity.kind,
                importance = entity.importance
            )
        }
    }

    override suspend fun remember(
        content: String,
        sourceAgent: V2AgentId,
        kind: String,
        importance: Float
    ) {
        memoryRepository.remember(content, sourceAgent, kind, importance)
    }

    override suspend fun saveTask(
        title: String,
        description: String,
        sourceAgent: V2AgentId,
        priority: Int,
        relatedArtifact: String?
    ): Result<V2RuntimeTaskRecord> = runCatching {
        val task = memoryRepository.saveTask(
            title = title,
            description = description,
            sourceAgent = sourceAgent,
            priority = priority,
            relatedArtifact = relatedArtifact
        )
        V2RuntimeTaskRecord(
            id = task.id,
            title = task.title,
            sourceAgent = sourceAgent,
            status = task.status,
            priority = task.priority
        )
    }

    override suspend fun listTasks(
        status: String?,
        sourceAgent: V2AgentId?,
        limit: Int
    ): Result<List<V2RuntimeTaskRecord>> = runCatching {
        val tasks = when {
            sourceAgent != null -> memoryRepository.tasksByAgent(sourceAgent, limit)
            status == "open" -> memoryRepository.openTasks(limit)
            else -> memoryRepository.recentTasks(limit)
        }
        tasks.map { task ->
            V2RuntimeTaskRecord(
                id = task.id,
                title = task.title,
                sourceAgent = V2AgentId.entries.firstOrNull { it.wireName == task.sourceAgent } ?: V2AgentId.TASK,
                status = task.status,
                priority = task.priority
            )
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
        val denominator = sqrt(normA * normB)
        return if (denominator > 0f) dot / denominator else 0f
    }
}
