package com.geoagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.data.api.dto.SourceDto
import com.geoagent.domain.model.ChatMode
import com.geoagent.domain.model.Message
import com.geoagent.domain.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,
    val currentMode: ChatMode = ChatMode.CHAT,
    val conversationId: Int? = null,
    val webSearchEnabled: Boolean = false
)

class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sseJob: Job? = null

    fun loadConversation(conversationId: Int) {
        if (conversationId <= 0) {
            resetChat()
            return
        }
        sseJob?.cancel()
        _uiState.update {
            ChatUiState(conversationId = conversationId, currentMode = it.currentMode)
        }
        viewModelScope.launch {
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
            ChatUiState(currentMode = it.currentMode, webSearchEnabled = it.webSearchEnabled)
        }
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(webSearchEnabled = enabled) }
    }

    fun sendMessage(text: String) {
        val mode = _uiState.value.currentMode
        val userMsg = Message(role = Message.ROLE_USER, content = text)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isLoading = true,
                error = null,
                statusMessage = if (mode == ChatMode.RAG) "正在检索知识库…" else null
            )
        }

        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            chatRepository.streamChat(
                ChatStreamRequest(
                    message = text,
                    conversation_id = _uiState.value.conversationId,
                    mode = if (mode == ChatMode.RAG) "rag" else "chat",
                    web_search = if (mode == ChatMode.CHAT) _uiState.value.webSearchEnabled else false,
                    return_sources = true
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
                        _uiState.update { it.copy(isLoading = false, statusMessage = null) }
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
        _uiState.update { it.copy(currentMode = mode) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        sseJob?.cancel()
    }
}
