package com.geoagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoagent.domain.model.Conversation
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import com.geoagent.data.api.UserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val conversations: List<Conversation> = emptyList(),
    val user: UserResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val userResult = authRepository.getMe()
                val convResult = chatRepository.listConversations()
                _state.update { state ->
                    state.copy(
                        user = userResult.getOrNull(),
                        conversations = convResult.getOrElse { emptyList() },
                        isLoading = false,
                        error = convResult.exceptionOrNull()?.message
                            ?: userResult.exceptionOrNull()?.message
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
