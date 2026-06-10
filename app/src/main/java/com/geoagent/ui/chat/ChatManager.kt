package com.geoagent.ui.chat

import kotlinx.coroutines.CoroutineScope
import com.geoagent.agent.AgentMeta
import com.geoagent.agent.BuiltinAgents
import com.geoagent.agent.IntentRouter
import com.geoagent.agent.RouteDisposition
import com.geoagent.agent.RouteResult
import com.geoagent.agent.SessionContextManager
import com.geoagent.agent.UnitConversionAgent
import com.geoagent.agent.v2.V2OrchestrationResult
import com.geoagent.agent.v2.V2Orchestrator
import com.geoagent.agent.v2.V2PipelineStage
import com.geoagent.agent.v2.V2RuntimeHistoryMessage
import com.geoagent.agent.v2.V2RuntimeOrchestrator
import com.geoagent.agent.v2.V2RuntimeRequest
import com.geoagent.agent.v2.V2AgentId
import com.geoagent.agent.v2.V2AgentRunStatus
import com.geoagent.agent.v2.V2StageEvent
import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatHistoryMessage
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.data.api.dto.SourceDto
import com.geoagent.domain.model.ChatMode
import com.geoagent.domain.model.Conversation
import com.geoagent.domain.model.Message
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val retrievalHint: String? = null,
    val statusMessage: String? = null,
    val currentMode: ChatMode = ChatMode.CHAT,
    val conversationId: Int? = null,
    val conversations: List<Conversation> = emptyList(),
    val conversationError: String? = null,
    val webSearchEnabled: Boolean = false,
    val deepThinkingEnabled: Boolean = false,
    val ragTopK: Int = 5,
    val ragMinRelevanceScore: Float = 0.0f,
    val ragSettingsExpanded: Boolean = false,
    val activeAgent: String? = null,
    val availableAgents: List<AgentMeta> = BuiltinAgents.ALL,
    val pendingRouteConfirmation: PendingRouteConfirmation? = null,
    val pendingAgentNavigation: AgentNavigationTarget? = null,
    val pendingSystemAction: V2SystemAction? = null,
    val v2Trace: V2OrchestrationResult? = null
)

data class PendingRouteConfirmation(
    val agentName: String,
    val originalInput: String,
    val confidence: Float
)

enum class AgentNavigationTarget {
    DOCUMENTS,
    SETTINGS
}

data class EmailDraft(
    val to: String,
    val subject: String,
    val body: String
)

class ChatManager(
    private val scope: CoroutineScope,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val v2Orchestrator: V2Orchestrator,
    private val v2RuntimeOrchestrator: V2RuntimeOrchestrator,
    private val streamDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private val conversionAgent = UnitConversionAgent()

    private val intentRouter = IntentRouter(
        BuiltinAgents.ALL
    )
    private val sessionContextManager = SessionContextManager()

    private var sseJob: Job? = null
    private var streamRenderJob: Job? = null
    private var activeAssistantTimestamp: Long? = null
    private var shouldHoldFirstAnswerFrame = false
    private val pendingStreamContent = StringBuilder()
    private val pendingThinkingContent = StringBuilder()
    private val pendingV2ContentByAgent = linkedMapOf<String, StringBuilder>()

    fun loadConversation(conversationId: Int) {
        if (conversationId <= 0) {
            resetChat()
            return
        }
        sseJob?.cancel()
        activeAssistantTimestamp = null
        stopStreamRenderer()
        _uiState.update {
            ChatUiState(
                conversationId = conversationId,
                currentMode = it.currentMode,
                webSearchEnabled = it.webSearchEnabled,
                ragTopK = it.ragTopK,
                ragMinRelevanceScore = it.ragMinRelevanceScore,
                ragSettingsExpanded = it.ragSettingsExpanded
            )
        }
        scope.launch {
            runCatching {
                chatRepository.getConversationMessages(conversationId).fold(
                    onSuccess = { messages ->
                        _uiState.update { it.copy(messages = messages, conversationId = conversationId) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(error = e.message ?: "加载对话失败") }
                    }
                )
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "加载对话失败") }
            }
        }
    }

    fun resetChat() {
        sseJob?.cancel()
        activeAssistantTimestamp = null
        stopStreamRenderer()
        _uiState.update {
            ChatUiState(
                currentMode = it.currentMode,
                webSearchEnabled = it.webSearchEnabled,
                ragTopK = it.ragTopK,
                ragMinRelevanceScore = it.ragMinRelevanceScore,
                ragSettingsExpanded = it.ragSettingsExpanded
            )
        }
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(webSearchEnabled = enabled) }
    }

    fun setDeepThinkingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(deepThinkingEnabled = enabled) }
    }

    fun sendMessage(text: String, imageBase64: String? = null) {
        val pending = _uiState.value.pendingRouteConfirmation
        if (pending != null && imageBase64 == null) {
            val handled = tryResolvePendingConfirmation(text, pending)
            if (handled) return
        }

        val useExplicitWebSearch = _uiState.value.currentMode == ChatMode.CHAT && _uiState.value.webSearchEnabled
        val route = intentRouter.route(
            input = text,
            sessionContext = sessionContextManager.snapshot(),
            isAuthenticated = true
        )
        val explicitEmailRoute = route.agentName == "v2_email" && route.disposition == RouteDisposition.DIRECT

        if (route.shouldClearContext) {
            sessionContextManager.clear()
            syncActiveAgent()
        }

        val directRoutedAgent = if (
            route.disposition == RouteDisposition.DIRECT &&
            !(useExplicitWebSearch && route.agentName?.startsWith("v2_") == true)
        ) route.agentName else null

        if (route.shouldUseLocalDirectAgentBeforeV2()) {
            sessionContextManager.activate(UnitConversionAgent.META.name, System.currentTimeMillis())
            syncActiveAgent()
            dispatchConversionAgent(text)
            return
        }

        val v2Plan = v2Orchestrator.plan(text)
        if ((!useExplicitWebSearch || explicitEmailRoute) && v2Plan.tasks.any { it.agentId.shouldAnswerWithV2Runtime(text) }) {
            dispatchV2RuntimeMessage(text, imageBase64)
            return
        }

        if (directRoutedAgent != null) {
            when (directRoutedAgent) {
                BuiltinAgents.DOCUMENT.name -> {
                    sessionContextManager.activate(BuiltinAgents.DOCUMENT.name, System.currentTimeMillis())
                    syncActiveAgent()
                    val userMsg = Message(role = Message.ROLE_USER, content = text, imageBase64 = imageBase64)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + userMsg,
                            pendingRouteConfirmation = null,
                            isLoading = true,
                            statusMessage = "正在打开文档管理…"
                        )
                    }
                    scope.launch {
                        delay(120)
                        val aiMsg = Message(
                            role = Message.ROLE_ASSISTANT,
                            content = "已识别为文档管理需求。请点击左上角菜单进入「文档」页面进行上传、删除或查看。"
                        )
                        _uiState.update {
                            it.copy(
                                messages = it.messages + aiMsg,
                                pendingAgentNavigation = AgentNavigationTarget.DOCUMENTS,
                                isLoading = false,
                                statusMessage = null
                            )
                        }
                    }
                    return
                }
                BuiltinAgents.SETTINGS.name -> {
                    sessionContextManager.activate(BuiltinAgents.SETTINGS.name, System.currentTimeMillis())
                    syncActiveAgent()
                    val userMsg = Message(role = Message.ROLE_USER, content = text, imageBase64 = imageBase64)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + userMsg,
                            pendingRouteConfirmation = null,
                            isLoading = true,
                            statusMessage = "正在打开设置…"
                        )
                    }
                    scope.launch {
                        delay(120)
                        val aiMsg = Message(
                            role = Message.ROLE_ASSISTANT,
                            content = "已识别为设置需求。请点击左上角菜单进入「设置」页面进行主题、账号与偏好调整。"
                        )
                        _uiState.update {
                            it.copy(
                                messages = it.messages + aiMsg,
                                pendingAgentNavigation = AgentNavigationTarget.SETTINGS,
                                isLoading = false,
                                statusMessage = null
                            )
                        }
                    }
                    return
                }
            }
        }

        if (directRoutedAgent == null) {
            sessionContextManager.clear()
            syncActiveAgent()
        }
        val mode = _uiState.value.currentMode
        val thinkingEnabled = _uiState.value.deepThinkingEnabled
        val requestHistory = _uiState.value.messages
            .takeLast(20)
            .filter { it.content.isNotBlank() }
            .map { ChatHistoryMessage(role = it.role, content = it.content) }
        val userMsg = Message(role = Message.ROLE_USER, content = text, imageBase64 = imageBase64)
        val assistantMsg = Message(
            role = Message.ROLE_ASSISTANT,
            content = "",
            thinkingStartedAt = if (thinkingEnabled) System.currentTimeMillis() else null
        )
        activeAssistantTimestamp = assistantMsg.timestamp
        val convId = _uiState.value.conversationId ?: (System.currentTimeMillis() % 100000).toInt()
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + assistantMsg,
                isLoading = true,
                error = null,
                retrievalHint = null,
                pendingRouteConfirmation = null,
                statusMessage = if (mode == ChatMode.RAG) "正在检索知识库…" else null,
                conversationId = convId
            )
        }
        chatRepository.saveMessage(convId, userMsg)

        sseJob?.cancel()
        stopStreamRenderer()
        sseJob = scope.launch {
            startStreamRenderer()
            var pendingSources = emptyList<SourceDto>()
            chatRepository.streamChat(
                ChatStreamRequest(
                    message = text,
                    conversation_id = _uiState.value.conversationId,
                    mode = if (mode == ChatMode.RAG) "rag" else "chat",
                    top_k = if (mode == ChatMode.RAG) _uiState.value.ragTopK else null,
                    min_relevance_score = if (mode == ChatMode.RAG) _uiState.value.ragMinRelevanceScore else null,
                    web_search = if (mode == ChatMode.CHAT) _uiState.value.webSearchEnabled else false,
                    enable_thinking = thinkingEnabled,
                    return_sources = true,
                    image_base64 = imageBase64,
                    history = requestHistory
                )
            )
                .buffer(STREAM_EVENT_BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.SUSPEND)
                .flowOn(streamDispatcher)
                .catch { e ->
                flushPendingStreamContent(forceContent = true)
                stopStreamRenderer()
                activeAssistantTimestamp = null
                _uiState.update { state ->
                    finishThinking(state).copy(error = e.message, isLoading = false, statusMessage = null)
                }
            }.collect { event ->
                when (event) {
                    is ChatEvent.Info -> {
                        _uiState.update { it.copy(conversationId = event.conversation_id) }
                    }
                    is ChatEvent.Status -> {
                        _uiState.update { it.copy(statusMessage = event.message) }
                        if (thinkingEnabled && useExplicitWebSearch) {
                            appendSearchProgressThinking(event.message)
                        }
                    }
                    is ChatEvent.Thinking -> {
                        synchronized(pendingThinkingContent) {
                            pendingThinkingContent.append(event.content)
                        }
                    }
                    is ChatEvent.Content -> {
                        synchronized(pendingStreamContent) {
                            if (pendingStreamContent.isEmpty()) {
                                shouldHoldFirstAnswerFrame = true
                            }
                            pendingStreamContent.append(event.content)
                        }
                    }
                    is ChatEvent.Sources -> {
                        flushPendingStreamContent(forceContent = true)
                        pendingSources = mergeSources(pendingSources, event.sources)
                    }
                    is ChatEvent.Done -> {
                        flushPendingStreamContent(forceContent = true)
                        stopStreamRenderer()
                        activeAssistantTimestamp = null
                        _uiState.update { state ->
                            val withSources = attachSourcesToLastAssistant(state, pendingSources)
                            val hint = if (withSources.currentMode == ChatMode.RAG) {
                                val assistant = withSources.messages.lastOrNull { it.role == Message.ROLE_ASSISTANT }
                                if (assistant != null && assistant.sources.isEmpty()) {
                                    "未检索到文献来源：请确认已上传文档或联网搜索获取参考资料。"
                                } else null
                            } else null
                            val lastAssistant = withSources.messages.lastOrNull { it.role == Message.ROLE_ASSISTANT }
                            if (lastAssistant != null && withSources.conversationId != null) {
                                chatRepository.saveMessage(withSources.conversationId, lastAssistant)
                            }
                            finishThinking(withSources).copy(isLoading = false, statusMessage = null, retrievalHint = hint)
                        }
                    }
                    is ChatEvent.Error -> {
                        flushPendingStreamContent(forceContent = true)
                        stopStreamRenderer()
                        activeAssistantTimestamp = null
                        _uiState.update { state ->
                            finishThinking(state).copy(error = event.message, isLoading = false, statusMessage = null)
                        }
                    }
                }
            }
        }
    }

    private fun dispatchV2RuntimeMessage(text: String, imageBase64: String?) {
        val userMsg = Message(role = Message.ROLE_USER, content = text, imageBase64 = imageBase64)
        val convId = _uiState.value.conversationId ?: (System.currentTimeMillis() % 100000).toInt()
        val agentId = v2Orchestrator.plan(text).tasks.firstOrNull()?.agentId
        val history = _uiState.value.messages
            .takeLast(12)
            .filter { it.content.isNotBlank() }
            .map { V2RuntimeHistoryMessage(role = it.role, content = it.content) }
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isLoading = true,
                error = null,
                statusMessage = loadingTextForV2Agent(agentId),
                pendingRouteConfirmation = null,
                conversationId = convId,
                v2Trace = null
            )
        }
        chatRepository.saveMessage(convId, userMsg)

        scope.launch {
            startStreamRenderer()
            val stageEvents = mutableListOf<V2StageEvent>()
            var streamedAssistant = false
            runCatching {
                withContext(streamDispatcher) {
                    v2RuntimeOrchestrator.orchestrate(
                        request = V2RuntimeRequest(text, imageBase64, history),
                        onEvent = { event ->
                            stageEvents += event
                            _uiState.update { state ->
                                state.copy(
                                    statusMessage = event.toLoadingText(agentId)
                                )
                            }
                        },
                        onContent = { agentId, chunk ->
                            if (chunk.isNotBlank()) {
                                streamedAssistant = true
                                synchronized(pendingV2ContentByAgent) {
                                    pendingV2ContentByAgent
                                        .getOrPut(agentId.wireName) { StringBuilder() }
                                        .append(chunk)
                                }
                            }
                        }
                    )
                }
            }
                .onSuccess { trace ->
                    flushPendingStreamContent(forceContent = true)
                    stopStreamRenderer()
                    val assistant = trace.toAssistantMessage()
                    _uiState.update { state ->
                        val messages = if (streamedAssistant) {
                            state.messages.toMutableList().apply {
                                val userIdx = indexOfLast { it.role == Message.ROLE_USER && it.content == userMsg.content }
                                if (userIdx >= 0 && userIdx < lastIndex) {
                                    subList(userIdx + 1, size).clear()
                                }
                                add(assistant)
                            }
                        } else {
                            state.messages + assistant
                        }
                        state.copy(
                            messages = messages,
                            isLoading = false,
                            statusMessage = null,
                            v2Trace = trace,
                            pendingSystemAction = trace.toV2SystemAction() ?: state.pendingSystemAction
                        )
                    }
                    chatRepository.saveMessage(convId, assistant)
                }
                .onFailure { e ->
                    flushPendingStreamContent(forceContent = true)
                    stopStreamRenderer()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = stageEvents.lastOrNull()?.toLoadingText(agentId),
                            error = e.message ?: "V2 Agent 执行失败"
                        )
                    }
                }
        }
    }

    private fun startStreamRenderer() {
        streamRenderJob?.cancel()
        streamRenderJob = scope.launch {
            while (isActive) {
                delay(STREAM_FRAME_DELAY_MILLIS)
                flushPendingStreamContent()
            }
        }
    }

    private fun stopStreamRenderer() {
        streamRenderJob?.cancel()
        streamRenderJob = null
        synchronized(pendingStreamContent) {
            pendingStreamContent.clear()
        }
        shouldHoldFirstAnswerFrame = false
        synchronized(pendingThinkingContent) {
            pendingThinkingContent.clear()
        }
        synchronized(pendingV2ContentByAgent) {
            pendingV2ContentByAgent.clear()
        }
    }

    private fun flushPendingStreamContent(forceContent: Boolean = false) {
        val now = System.currentTimeMillis()
        val thinkingChunk = synchronized(pendingThinkingContent) {
            if (pendingThinkingContent.isEmpty()) "" else pendingThinkingContent.toString().also {
                pendingThinkingContent.clear()
            }
        }
        val contentChunk = synchronized(pendingStreamContent) {
            if (pendingStreamContent.isEmpty()) {
                ""
            } else if (forceContent || shouldRevealPendingAnswerContent()) {
                pendingStreamContent.toString().also {
                    pendingStreamContent.clear()
                    shouldHoldFirstAnswerFrame = false
                }
            } else {
                ""
            }
        }
        val v2Chunks = synchronized(pendingV2ContentByAgent) {
            if (pendingV2ContentByAgent.isEmpty()) emptyList()
            else pendingV2ContentByAgent.map { (agentName, builder) ->
                agentName to builder.toString()
            }.also {
                pendingV2ContentByAgent.clear()
            }
        }
        if (contentChunk.isEmpty() && thinkingChunk.isEmpty() && v2Chunks.isEmpty()) return
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            if (contentChunk.isNotEmpty() || thinkingChunk.isNotEmpty()) {
                val idx = activeAssistantIndex(msgs)
                if (idx >= 0) {
                    val current = msgs[idx]
                    msgs[idx] = current.copy(
                        content = current.content + contentChunk,
                        thinkingContent = current.thinkingContent + thinkingChunk,
                        thinkingStartedAt = if (thinkingChunk.isNotBlank()) current.thinkingStartedAt ?: now else current.thinkingStartedAt,
                        thinkingFinishedAt = when {
                            current.thinkingFinishedAt != null -> current.thinkingFinishedAt
                            contentChunk.isNotBlank() && (current.thinkingStartedAt != null || thinkingChunk.isNotBlank()) -> now
                            thinkingChunk.isNotBlank() -> null
                            else -> current.thinkingFinishedAt
                        }
                    )
                } else {
                    val assistant = Message(
                        role = Message.ROLE_ASSISTANT,
                        content = contentChunk,
                        thinkingContent = thinkingChunk,
                        thinkingStartedAt = if (thinkingChunk.isNotBlank()) now else null,
                        thinkingFinishedAt = if (contentChunk.isNotBlank() && thinkingChunk.isNotBlank()) now else null
                    )
                    activeAssistantTimestamp = assistant.timestamp
                    msgs.add(assistant)
                }
            }
            for ((agentName, chunk) in v2Chunks) {
                if (chunk.isEmpty()) continue
                val idx = msgs.indexOfLast { it.role == Message.ROLE_ASSISTANT && it.agentName == agentName }
                if (idx >= 0) {
                    val current = msgs[idx]
                    msgs[idx] = current.copy(content = current.content + chunk)
                } else {
                    msgs.add(
                        Message(
                            role = Message.ROLE_ASSISTANT,
                            content = chunk,
                            agentName = agentName
                        )
                    )
                }
            }
            state.copy(messages = msgs, statusMessage = null)
        }
    }

    private fun shouldRevealPendingAnswerContent(): Boolean {
        val activeThinking = _uiState.value.messages.lastOrNull { it.role == Message.ROLE_ASSISTANT }
            ?.let { it.thinkingStartedAt != null && it.thinkingFinishedAt == null } == true
        if (!activeThinking || !shouldHoldFirstAnswerFrame) return true
        shouldHoldFirstAnswerFrame = false
        return false
    }

    private fun appendSearchProgressThinking(status: String) {
        val line = when {
            status.contains("检索") || status.contains("搜索") -> "正在联网检索相关信息，并筛选可信来源。"
            status.contains("综合") || status.contains("生成") -> "正在把检索线索压缩为可回答的要点。"
            else -> return
        }
        synchronized(pendingThinkingContent) {
            val existing = pendingThinkingContent.toString()
            if (!existing.contains(line)) {
                if (existing.isNotEmpty() && !existing.endsWith("\n")) pendingThinkingContent.append('\n')
                pendingThinkingContent.appendLine(line)
            }
        }
    }

    private fun finishThinking(state: ChatUiState): ChatUiState {
        val msgs = state.messages.toMutableList()
        val idx = msgs.indexOfLast {
            it.role == Message.ROLE_ASSISTANT &&
                it.thinkingStartedAt != null &&
                it.thinkingFinishedAt == null
        }
        if (idx < 0) return state
        msgs[idx] = msgs[idx].copy(thinkingFinishedAt = System.currentTimeMillis())
        return state.copy(messages = msgs)
    }

    private fun dispatchConversionAgent(text: String) {
        val userMsg = Message(role = Message.ROLE_USER, content = text)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isLoading = true,
                statusMessage = "正在换算单位…"
            )
        }
        scope.launch {
            delay(120)
            dispatchConversionAgentInternal(text)
        }
    }

    private fun dispatchConversionAgentInternal(text: String) {
        val result = conversionAgent.parse(text)

        if (result != null) {
            val converted = conversionAgent.convert(result)
            val resultJson = gson.toJson(converted)
            val aiMsg = Message(
                role = Message.ROLE_ASSISTANT,
                content = converted.output,
                agentName = UnitConversionAgent.META.name,
                agentResultJson = resultJson
            )
            _uiState.update { it.copy(messages = it.messages + aiMsg, pendingRouteConfirmation = null) }
        } else {
            val errMsg = Message(
                role = Message.ROLE_ASSISTANT,
                content = "未能识别换算请求。试试：\n`3000米等于多少英尺`\n`100摄氏度转华氏度`\n`10MPa换算psi`"
            )
            _uiState.update { it.copy(messages = it.messages + errMsg, pendingRouteConfirmation = null) }
        }
        _uiState.update { it.copy(isLoading = false, statusMessage = null) }
    }

    private fun tryResolvePendingConfirmation(text: String, pending: PendingRouteConfirmation): Boolean {
        val normalized = text.trim().lowercase()
        val positive = setOf("是", "好", "好的", "确认", "yes", "y", "ok")
        val negative = setOf("否", "不", "不用", "不是", "no", "n", "cancel")

        if (positive.any { normalized == it || normalized.contains(it) }) {
            val userMsg = Message(role = Message.ROLE_USER, content = text)
            _uiState.update { it.copy(messages = it.messages + userMsg, pendingRouteConfirmation = null) }
            when (pending.agentName) {
                UnitConversionAgent.META.name -> {
                    sessionContextManager.activate(UnitConversionAgent.META.name, System.currentTimeMillis())
                    syncActiveAgent()
                    _uiState.update { it.copy(isLoading = true, statusMessage = "正在换算单位…") }
                    scope.launch {
                        delay(120)
                        dispatchConversionAgentInternal(pending.originalInput)
                    }
                    return true
                }
                BuiltinAgents.DOCUMENT.name -> {
                    _uiState.update { it.copy(isLoading = true, statusMessage = "正在打开文档管理…") }
                    scope.launch {
                        delay(120)
                        val aiMsg = Message(role = Message.ROLE_ASSISTANT, content = "请点击左上角菜单进入「文档」页面。")
                        _uiState.update {
                            it.copy(
                                messages = it.messages + aiMsg,
                                pendingAgentNavigation = AgentNavigationTarget.DOCUMENTS,
                                isLoading = false,
                                statusMessage = null
                            )
                        }
                    }
                    return true
                }
                BuiltinAgents.SETTINGS.name -> {
                    _uiState.update { it.copy(isLoading = true, statusMessage = "正在打开设置…") }
                    scope.launch {
                        delay(120)
                        val aiMsg = Message(role = Message.ROLE_ASSISTANT, content = "请点击左上角菜单进入「设置」页面。")
                        _uiState.update {
                            it.copy(
                                messages = it.messages + aiMsg,
                                pendingAgentNavigation = AgentNavigationTarget.SETTINGS,
                                isLoading = false,
                                statusMessage = null
                            )
                        }
                    }
                    return true
                }
            }
        }

        if (negative.any { normalized == it || normalized.contains(it) }) {
            val userMsg = Message(role = Message.ROLE_USER, content = text)
            val displayName = _uiState.value.availableAgents
                .firstOrNull { it.name == pending.agentName }
                ?.displayName ?: "该功能"
            val aiMsg = Message(role = Message.ROLE_ASSISTANT, content = "已取消「$displayName」路由，继续普通对话。")
            _uiState.update {
                it.copy(
                    messages = it.messages + userMsg + aiMsg,
                    pendingRouteConfirmation = null
                )
            }
            sessionContextManager.clear()
            syncActiveAgent()
            return true
        }

        return false
    }

    private fun syncActiveAgent() {
        _uiState.update { it.copy(activeAgent = sessionContextManager.snapshot().activeAgent) }
    }

    private fun attachSourcesToLastAssistant(
        state: ChatUiState,
        sources: List<SourceDto>
    ): ChatUiState {
        if (sources.isEmpty()) return state
        val msgs = state.messages.toMutableList()
        val idx = activeAssistantIndex(msgs)
        if (idx >= 0) {
            val current = msgs[idx]
            msgs[idx] = current.copy(sources = mergeSources(current.sources, sources))
        } else {
            val assistant = Message(role = Message.ROLE_ASSISTANT, content = "", sources = sources)
            activeAssistantTimestamp = assistant.timestamp
            msgs.add(assistant)
        }
        return state.copy(messages = msgs)
    }

    private fun mergeSources(existing: List<SourceDto>, incoming: List<SourceDto>): List<SourceDto> {
        if (existing.isEmpty()) return incoming
        if (incoming.isEmpty()) return existing
        return (existing + incoming).distinctBy {
            it.url?.trim()?.lowercase()?.ifBlank { null } ?: it.source.trim().lowercase()
        }
    }

    private fun activeAssistantIndex(messages: List<Message>): Int {
        val activeTimestamp = activeAssistantTimestamp
        if (activeTimestamp != null) {
            val idx = messages.indexOfLast {
                it.role == Message.ROLE_ASSISTANT && it.timestamp == activeTimestamp
            }
            if (idx >= 0) return idx
        }
        return messages.indexOfLast { it.role == Message.ROLE_ASSISTANT }
    }

    fun setMode(mode: ChatMode) {
        _uiState.update {
            it.copy(
                currentMode = mode,
                ragSettingsExpanded = if (mode == ChatMode.CHAT) false else it.ragSettingsExpanded
            )
        }
    }

    fun setRagTopK(topK: Int) {
        _uiState.update { it.copy(ragTopK = topK.coerceIn(1, 10)) }
    }

    fun setRagMinRelevanceScore(minScore: Float) {
        _uiState.update { it.copy(ragMinRelevanceScore = minScore.coerceIn(0f, 1f)) }
    }

    fun setRagSettingsExpanded(expanded: Boolean) {
        _uiState.update { it.copy(ragSettingsExpanded = expanded) }
    }

    fun refreshConversations() {
        scope.launch {
            chatRepository.listConversations().fold(
                onSuccess = { conversations ->
                    _uiState.update { it.copy(conversations = conversations, conversationError = null) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(conversationError = e.message ?: "加载失败") }
                }
            )
        }
    }

    fun renameConversation(conversationId: Int, title: String) {
        chatRepository.updateConversationTitle(conversationId, title)
        refreshConversations()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearConversationError() {
        _uiState.update { it.copy(conversationError = null) }
    }

    fun clearRetrievalHint() {
        _uiState.update { it.copy(retrievalHint = null) }
    }

    fun consumePendingNavigation() {
        _uiState.update { it.copy(pendingAgentNavigation = null) }
    }

    fun consumePendingSystemAction() {
        _uiState.update { it.copy(pendingSystemAction = null) }
    }

    private fun extractEmailDraft(input: String): EmailDraft? {
        val to = Regex("""([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?: return null

        val subject = Regex("""(?:主题[:：]?\s*)([^。；;\n]+)""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "来自 Geo-Agent 的邮件"

        val body = Regex("""(?:内容|正文)[:：]?\s*(.+)$""", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "您好，这是一封由 Geo-Agent 辅助生成的邮件。"

        return EmailDraft(
            to = to,
            subject = subject,
            body = body
        )
    }

    private fun sendEmailByBackend(draft: EmailDraft) {
        scope.launch {
            authRepository.sendEmail(
                toAddr = draft.to,
                subject = draft.subject,
                content = draft.body
            ).fold(
                onSuccess = { resp ->
                    val sentTo = draft.to
                    _uiState.update {
                        it.copy(
                            messages = it.messages + Message(role = Message.ROLE_ASSISTANT, content = "邮件已发送至$sentTo✅"),
                            isLoading = false,
                            statusMessage = null
                        )
                    }
                },
                onFailure = { e ->
                    val err = e.message.orEmpty()
                    val timeoutLike = err.contains("timeout", ignoreCase = true) ||
                        err.contains("timed out", ignoreCase = true)
                    if (timeoutLike) {
                        authRepository.getEmailHistory(5).fold(
                            onSuccess = { history ->
                                val matched = history.items.any { item ->
                                    item.to_addr.equals(draft.to, ignoreCase = true) &&
                                        item.subject == draft.subject
                                }
                                if (matched) {
                                    _uiState.update {
                                        it.copy(
                                            messages = it.messages + Message(role = Message.ROLE_ASSISTANT, content = "邮件已发送至${draft.to}✅"),
                                            isLoading = false,
                                            statusMessage = null
                                        )
                                    }
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            messages = it.messages + Message(role = Message.ROLE_ASSISTANT, content = "发送结果暂时未知，请稍后查看邮件历史。"),
                                            isLoading = false,
                                            statusMessage = null
                                        )
                                    }
                                }
                            },
                            onFailure = {
                                _uiState.update {
                                    it.copy(
                                        messages = it.messages + Message(role = Message.ROLE_ASSISTANT, content = "发送结果暂时未知，请稍后查看邮件历史。"),
                                        isLoading = false,
                                        statusMessage = null
                                    )
                                }
                            }
                        )
                    } else {
                        _uiState.update {
                            it.copy(
                                messages = it.messages + Message(role = Message.ROLE_ASSISTANT, content = "邮件发送失败：${e.message ?: "未知错误"}"),
                                isLoading = false,
                                statusMessage = null
                            )
                        }
                    }
                }
            )
        }
    }

    private fun isEmailHistoryIntent(input: String): Boolean {
        val s = input.trim().lowercase()
        return listOf("历史", "记录", "已发送", "发送过", "发过", "history").any { s.contains(it) }
    }

    private fun fetchEmailHistory(limit: Int = 10) {
        scope.launch {
            authRepository.getEmailHistory(limit).fold(
                onSuccess = { resp ->
                    if (resp.items.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                messages = it.messages + Message(role = Message.ROLE_ASSISTANT, content = "暂无邮件发送记录。"),
                                isLoading = false,
                                statusMessage = null
                            )
                        }
                        return@fold
                    }
                    val lines = resp.items.mapIndexed { idx, item ->
                        val subject = item.subject.ifBlank { "(无主题)" }
                        val preview = item.content.replace("\n", " ").take(60).let { if (item.content.length > 60) "$it…" else it }
                        "${idx + 1}. 收件人：${item.to_addr}｜主题：$subject｜内容：$preview"
                    }
                    _uiState.update {
                        it.copy(
                            messages = it.messages + Message(
                                role = Message.ROLE_ASSISTANT,
                                content = "最近 ${resp.items.size} 条邮件发送记录：\n" + lines.joinToString("\n")
                            ),
                            isLoading = false,
                            statusMessage = null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + Message(role = Message.ROLE_ASSISTANT, content = "查询邮件历史失败：${e.message ?: "未知错误"}"),
                            isLoading = false,
                            statusMessage = null
                        )
                    }
                }
            )
        }
    }

    fun destroy() {
        sseJob?.cancel()
        stopStreamRenderer()
    }

    private companion object {
        private const val STREAM_FRAME_DELAY_MILLIS = 40L
        private const val STREAM_EVENT_BUFFER_CAPACITY = 64
    }
}

internal fun RouteResult.shouldUseLocalDirectAgentBeforeV2(): Boolean =
    disposition == RouteDisposition.DIRECT && agentName == UnitConversionAgent.META.name

internal fun V2AgentId.shouldAnswerWithV2Runtime(input: String): Boolean = when (this) {
    V2AgentId.SEARCH,
    V2AgentId.RAG,
    V2AgentId.RESEARCH,
    V2AgentId.SCHEDULE,
    V2AgentId.TASK,
    V2AgentId.PDF,
    V2AgentId.FILE -> true
    V2AgentId.EMAIL -> !input.trim().lowercase().let { text ->
        listOf("历史", "记录", "已发送", "发送过", "发过", "history").any { text.contains(it) }
    }
}

private fun V2StageEvent.toLoadingText(agentId: V2AgentId?): String = when (agentId) {
    V2AgentId.EMAIL -> "邮件发送中..."
    V2AgentId.SEARCH -> "智能搜索中..."
    V2AgentId.RAG -> "知识库检索中..."
    else -> when (stage) {
        V2PipelineStage.MASTER,
        V2PipelineStage.PLANNER,
        V2PipelineStage.ROUTER -> "正在分析请求..."
        V2PipelineStage.AGENTS -> "正在执行任务..."
        V2PipelineStage.REFLECTION,
        V2PipelineStage.JUDGE,
        V2PipelineStage.AGGREGATOR -> "正在整理结果..."
    }
}

private fun loadingTextForV2Agent(agentId: V2AgentId?): String = when (agentId) {
    V2AgentId.EMAIL -> "邮件发送中..."
    V2AgentId.SEARCH -> "智能搜索中..."
    V2AgentId.RAG -> "知识库检索中..."
    else -> "正在处理..."
}

private fun V2OrchestrationResult.toAssistantMessage(): Message {
    val content = runs.joinToString("\n\n") { run ->
        val prefix = when (run.status) {
            V2AgentRunStatus.COMPLETED -> ""
            V2AgentRunStatus.NEEDS_INPUT -> "需要补充信息："
            V2AgentRunStatus.BLOCKED -> "执行失败："
        }
        buildString {
            append(prefix)
            append(run.output.ifBlank { run.summary })
            if (run.followUps.isNotEmpty()) {
                append("\n\n")
                append(run.followUps.joinToString("\n") { "- $it" })
            }
        }
    }.ifBlank { answer }

    return Message(
        role = Message.ROLE_ASSISTANT,
        content = content,
        agentName = runs.firstOrNull()?.agentId?.wireName,
        agentResultJson = Gson().toJson(this)
    )
}
