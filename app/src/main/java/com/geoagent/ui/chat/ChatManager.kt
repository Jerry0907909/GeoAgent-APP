package com.geoagent.ui.chat

import kotlinx.coroutines.CoroutineScope
import com.geoagent.agent.AgentMeta
import com.geoagent.agent.BuiltinAgents
import com.geoagent.agent.IntentRouter
import com.geoagent.agent.RouteDisposition
import com.geoagent.agent.SessionContextManager
import com.geoagent.agent.UnitConversionAgent
import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatHistoryMessage
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.data.api.dto.SourceDto
import com.geoagent.domain.model.ChatMode
import com.geoagent.domain.model.Message
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val retrievalHint: String? = null,
    val statusMessage: String? = null,
    val currentMode: ChatMode = ChatMode.CHAT,
    val conversationId: Int? = null,
    val webSearchEnabled: Boolean = false,
    val ragTopK: Int = 5,
    val ragMinRelevanceScore: Float = 0.0f,
    val ragSettingsExpanded: Boolean = false,
    val activeAgent: String? = null,
    val availableAgents: List<AgentMeta> = BuiltinAgents.ALL,
    val pendingRouteConfirmation: PendingRouteConfirmation? = null,
    val pendingAgentNavigation: AgentNavigationTarget? = null
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
    private val authRepository: AuthRepository
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

    fun loadConversation(conversationId: Int) {
        if (conversationId <= 0) {
            resetChat()
            return
        }
        sseJob?.cancel()
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

    fun sendMessage(text: String, imageBase64: String? = null) {
        val pending = _uiState.value.pendingRouteConfirmation
        if (pending != null && imageBase64 == null) {
            val handled = tryResolvePendingConfirmation(text, pending)
            if (handled) return
        }

        val route = intentRouter.route(
            input = text,
            sessionContext = sessionContextManager.snapshot(),
            isAuthenticated = true
        )

        if (route.shouldClearContext) {
            sessionContextManager.clear()
            syncActiveAgent()
        }

        val directRoutedAgent = if (route.disposition == RouteDisposition.DIRECT) route.agentName else null

        if (route.disposition == RouteDisposition.DIRECT) {
            when (route.agentName) {
                UnitConversionAgent.META.name -> {
                    sessionContextManager.activate(UnitConversionAgent.META.name, System.currentTimeMillis())
                    syncActiveAgent()
                    dispatchConversionAgent(text)
                    return
                }
                BuiltinAgents.RAG.name -> {
                    sessionContextManager.activate(BuiltinAgents.RAG.name, System.currentTimeMillis())
                    syncActiveAgent()
                    _uiState.update { state ->
                        state.copy(
                            currentMode = ChatMode.RAG,
                            webSearchEnabled = false
                        )
                    }
                }
                BuiltinAgents.SEARCH.name -> {
                    sessionContextManager.activate(BuiltinAgents.SEARCH.name, System.currentTimeMillis())
                    syncActiveAgent()
                    _uiState.update { state ->
                        state.copy(
                            currentMode = ChatMode.CHAT,
                            webSearchEnabled = true
                        )
                    }
                }
                BuiltinAgents.EMAIL.name -> {
                    sessionContextManager.activate(BuiltinAgents.EMAIL.name, System.currentTimeMillis())
                    syncActiveAgent()
                    val userMsg = Message(role = Message.ROLE_USER, content = text, imageBase64 = imageBase64)
                    if (isEmailHistoryIntent(text)) {
                        _uiState.update {
                            it.copy(
                                messages = it.messages + userMsg,
                                pendingRouteConfirmation = null,
                                isLoading = true,
                                statusMessage = "正在查询邮件历史…"
                            )
                        }
                        fetchEmailHistory()
                        return
                    }
                    val draft = extractEmailDraft(text)
                    if (draft == null) {
                        val aiMsg = Message(
                            role = Message.ROLE_ASSISTANT,
                            content = "我已识别为邮件发送需求，但还缺少收件人邮箱。请补充如：给 xxx@qq.com 发邮件，主题是..., 内容是..."
                        )
                        _uiState.update {
                            it.copy(
                                messages = it.messages + userMsg + aiMsg,
                                pendingRouteConfirmation = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                messages = it.messages + userMsg,
                                pendingRouteConfirmation = null,
                                isLoading = true,
                                statusMessage = "正在发送邮件…"
                            )
                        }
                        sendEmailByBackend(draft)
                    }
                    return
                }
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

        if (route.disposition == RouteDisposition.CONFIRM && route.agentName != null) {
            val displayName = _uiState.value.availableAgents.firstOrNull { it.name == route.agentName }?.displayName ?: "对应功能"
            val userMsg = Message(role = Message.ROLE_USER, content = text, imageBase64 = imageBase64)
            val askMsg = Message(
                role = Message.ROLE_ASSISTANT,
                content = "我理解您可能想使用「$displayName」（置信度 ${(route.confidence * 100).toInt()}%）。是否按该功能处理？请回复“是/否”。"
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMsg + askMsg,
                    pendingRouteConfirmation = PendingRouteConfirmation(
                        agentName = route.agentName,
                        originalInput = text,
                        confidence = route.confidence
                    )
                )
            }
            return
        }

        if (directRoutedAgent == null) {
            sessionContextManager.clear()
            syncActiveAgent()
        }
        val mode = _uiState.value.currentMode
        val userMsg = Message(role = Message.ROLE_USER, content = text, imageBase64 = imageBase64)
        val convId = _uiState.value.conversationId ?: (System.currentTimeMillis() % 100000).toInt()
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
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
        sseJob = scope.launch {
            chatRepository.streamChat(
                ChatStreamRequest(
                    message = text,
                    conversation_id = _uiState.value.conversationId,
                    mode = if (mode == ChatMode.RAG) "rag" else "chat",
                    top_k = if (mode == ChatMode.RAG) _uiState.value.ragTopK else null,
                    min_relevance_score = if (mode == ChatMode.RAG) _uiState.value.ragMinRelevanceScore else null,
                    web_search = if (mode == ChatMode.CHAT) _uiState.value.webSearchEnabled else false,
                    return_sources = true,
                    image_base64 = imageBase64,
                    history = _uiState.value.messages
                        .takeLast(20)
                        .filter { it.content.isNotBlank() }
                        .map { ChatHistoryMessage(role = it.role, content = it.content) }
                )
            ).catch { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false, statusMessage = null) }
            }.collect { event ->
                when (event) {
                    is ChatEvent.Info -> {
                        _uiState.update { it.copy(conversationId = event.conversation_id) }
                    }
                    is ChatEvent.Status -> {
                        _uiState.update { it.copy(statusMessage = event.message) }
                    }
                    is ChatEvent.Content -> {
                        _uiState.update { state ->
                            val msgs = state.messages.toMutableList()
                            val last = msgs.lastOrNull()
                            if (last?.role == Message.ROLE_ASSISTANT) {
                                msgs[msgs.lastIndex] = last.copy(content = last.content + event.content)
                            } else {
                                msgs.add(Message(role = Message.ROLE_ASSISTANT, content = event.content))
                            }
                            state.copy(messages = msgs, statusMessage = null)
                        }
                    }
                    is ChatEvent.Sources -> {
                        _uiState.update { state ->
                            attachSourcesToLastAssistant(state, event.sources)
                        }
                    }
                    is ChatEvent.Done -> {
                        _uiState.update { state ->
                            val hint = if (state.currentMode == ChatMode.RAG) {
                                val assistant = state.messages.lastOrNull { it.role == Message.ROLE_ASSISTANT }
                                if (assistant != null && assistant.sources.isEmpty()) {
                                    "未检索到文献来源：请确认已上传文档或联网搜索获取参考资料。"
                                } else null
                            } else null
                            val lastAssistant = state.messages.lastOrNull { it.role == Message.ROLE_ASSISTANT }
                            if (lastAssistant != null && state.conversationId != null) {
                                chatRepository.saveMessage(state.conversationId!!, lastAssistant)
                            }
                            state.copy(isLoading = false, statusMessage = null, retrievalHint = hint)
                        }
                    }
                    is ChatEvent.Error -> {
                        _uiState.update {
                            it.copy(error = event.message, isLoading = false, statusMessage = null)
                        }
                    }
                }
            }
        }
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
                BuiltinAgents.RAG.name -> {
                    sessionContextManager.activate(BuiltinAgents.RAG.name, System.currentTimeMillis())
                    syncActiveAgent()
                    _uiState.update { it.copy(currentMode = ChatMode.RAG, webSearchEnabled = false) }
                    sendMessage(pending.originalInput)
                    return true
                }
                BuiltinAgents.SEARCH.name -> {
                    sessionContextManager.activate(BuiltinAgents.SEARCH.name, System.currentTimeMillis())
                    syncActiveAgent()
                    _uiState.update { it.copy(currentMode = ChatMode.CHAT, webSearchEnabled = true) }
                    sendMessage(pending.originalInput)
                    return true
                }
                BuiltinAgents.EMAIL.name -> {
                    sessionContextManager.activate(BuiltinAgents.EMAIL.name, System.currentTimeMillis())
                    syncActiveAgent()
                    if (isEmailHistoryIntent(pending.originalInput)) {
                        _uiState.update { it.copy(isLoading = true, statusMessage = "正在查询邮件历史…") }
                        fetchEmailHistory()
                        return true
                    }
                    val draft = extractEmailDraft(pending.originalInput)
                    if (draft == null) {
                        val aiMsg = Message(
                            role = Message.ROLE_ASSISTANT,
                            content = "请补充收件人邮箱后我再为你发送邮件。"
                        )
                        _uiState.update { it.copy(messages = it.messages + aiMsg) }
                    } else {
                        _uiState.update { it.copy(isLoading = true, statusMessage = "正在发送邮件…") }
                        sendEmailByBackend(draft)
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
        val idx = msgs.indexOfLast { it.role == Message.ROLE_ASSISTANT }
        if (idx >= 0) {
            msgs[idx] = msgs[idx].copy(sources = sources)
        } else {
            msgs.add(Message(role = Message.ROLE_ASSISTANT, content = "", sources = sources))
        }
        return state.copy(messages = msgs)
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearRetrievalHint() {
        _uiState.update { it.copy(retrievalHint = null) }
    }

    fun consumePendingNavigation() {
        _uiState.update { it.copy(pendingAgentNavigation = null) }
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
    }
}