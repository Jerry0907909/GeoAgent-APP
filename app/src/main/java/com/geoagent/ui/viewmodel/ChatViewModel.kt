package com.geoagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoagent.agent.v2.V2Orchestrator
import com.geoagent.agent.v2.V2RuntimeOrchestrator
import com.geoagent.domain.model.ChatMode
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import com.geoagent.ui.chat.ChatManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    chatRepository: ChatRepository,
    authRepository: AuthRepository,
    v2Orchestrator: V2Orchestrator,
    v2RuntimeOrchestrator: V2RuntimeOrchestrator
) : ViewModel() {

    private val manager = ChatManager(
        scope = viewModelScope,
        chatRepository = chatRepository,
        authRepository = authRepository,
        v2Orchestrator = v2Orchestrator,
        v2RuntimeOrchestrator = v2RuntimeOrchestrator
    )

    val uiState = manager.uiState

    fun loadConversation(conversationId: Int) = manager.loadConversation(conversationId)

    fun resetChat() = manager.resetChat()

    fun setWebSearchEnabled(enabled: Boolean) = manager.setWebSearchEnabled(enabled)

    fun setDeepThinkingEnabled(enabled: Boolean) = manager.setDeepThinkingEnabled(enabled)

    fun sendMessage(
        text: String,
        imageBase64: String? = null,
        imageMimeType: String? = null
    ) = manager.sendMessage(text, imageBase64, imageMimeType)

    fun setMode(mode: ChatMode) = manager.setMode(mode)

    fun setRagTopK(topK: Int) = manager.setRagTopK(topK)

    fun setRagMinRelevanceScore(minScore: Float) = manager.setRagMinRelevanceScore(minScore)

    fun setRagSettingsExpanded(expanded: Boolean) = manager.setRagSettingsExpanded(expanded)

    fun refreshConversations() = manager.refreshConversations()

    fun renameConversation(conversationId: Int, title: String) = manager.renameConversation(conversationId, title)

    fun clearError() = manager.clearError()

    fun clearConversationError() = manager.clearConversationError()

    fun clearRetrievalHint() = manager.clearRetrievalHint()

    fun consumePendingNavigation() = manager.consumePendingNavigation()

    fun consumePendingSystemAction() = manager.consumePendingSystemAction()

    override fun onCleared() {
        manager.destroy()
        super.onCleared()
    }
}
