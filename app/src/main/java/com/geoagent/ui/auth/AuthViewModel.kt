package com.geoagent.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoagent.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _codeSent = MutableStateFlow(false)
    val codeSent: StateFlow<Boolean> = _codeSent.asStateFlow()

    fun login(email: String, password: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.login(email.trim(), password).fold(
                onSuccess = { _state.update { AuthState.Success("登录成功") } },
                onFailure = { e -> _state.update { AuthState.Error(e.message ?: "登录失败") } }
            )
        }
    }

    fun register(username: String, email: String, password: String, code: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.register(username, email, password, code).fold(
                onSuccess = {
                    // Backend register returns user info only; login to obtain tokens (same as Web flow).
                    authRepository.login(email, password).fold(
                        onSuccess = { _state.update { AuthState.Success("注册成功") } },
                        onFailure = { e ->
                            _state.update {
                                AuthState.Error(
                                    "注册成功，但自动登录失败：${e.message ?: "请手动登录"}"
                                )
                            }
                        }
                    )
                },
                onFailure = { e -> _state.update { AuthState.Error(e.message ?: "注册失败") } }
            )
        }
    }

    fun sendVerificationCode(email: String) {
        viewModelScope.launch {
            authRepository.sendVerificationCode(email.trim()).fold(
                onSuccess = { _codeSent.value = true },
                onFailure = { e -> _state.update { AuthState.Error(e.message ?: "发送验证码失败") } }
            )
        }
    }

    fun clearError() {
        _state.value = AuthState.Idle
    }
}