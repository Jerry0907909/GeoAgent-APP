package com.geoagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoagent.data.api.UserResponse
import com.geoagent.data.api.dto.PreferencesResponse
import com.geoagent.data.local.AvatarLocalStore
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val user: UserResponse? = null,
    val preferences: PreferencesResponse? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val localAvatarUri: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val logoutSuccess: Boolean = false,
    val switchAccountRequested: Boolean = false,
    val passwordChangeSuccess: Boolean = false,
    val passwordError: String? = null
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val userPrefsDataStore: UserPrefsDataStore,
    private val avatarLocalStore: AvatarLocalStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val storedTheme = userPrefsDataStore.themeMode.first()
            _state.update { it.copy(themeMode = parseThemeMode(storedTheme)) }
        }
        viewModelScope.launch {
            userPrefsDataStore.localAvatarUri.collect { uri ->
                _state.update { s -> s.copy(localAvatarUri = uri) }
            }
        }
    }

    fun loadUser() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.getMe().fold(
                onSuccess = { user ->
                    authRepository.getPreferences().fold(
                        onSuccess = { prefs ->
                            val localTheme = parseThemeMode(userPrefsDataStore.themeMode.first())
                            _state.update {
                                it.copy(
                                    user = user,
                                    preferences = prefs,
                                    themeMode = localTheme,
                                    isLoading = false
                                )
                            }
                        },
                        onFailure = {
                            _state.update { it.copy(user = user, isLoading = false) }
                        }
                    )
                },
                onFailure = { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
            )
        }
    }

    fun updateDisplayName(fullName: String) {
        viewModelScope.launch {
            authRepository.updateMe(fullName.takeIf { it.isNotBlank() }, _state.value.user?.avatar_url)
                .fold(
                    onSuccess = { user ->
                        _state.update { it.copy(user = user, message = "昵称已更新") }
                    },
                    onFailure = { e -> _state.update { it.copy(error = e.message) } }
                )
        }
    }

    fun setLocalAvatarFromPicker(pickerUri: String) {
        viewModelScope.launch {
            val savedPath = avatarLocalStore.persistFromPickerUri(pickerUri)
            if (savedPath == null) {
                _state.update { it.copy(error = "无法读取所选图片，请重试") }
                return@launch
            }
            userPrefsDataStore.setLocalAvatarUri(savedPath)
            _state.update { it.copy(localAvatarUri = savedPath, message = "头像已更新") }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            userPrefsDataStore.setThemeMode(mode.name.lowercase())
            val apiTheme = mode.apiValue
            if (apiTheme != null) {
                authRepository.updatePreferences(theme = apiTheme).onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
            }
            _state.update { it.copy(themeMode = mode, message = "外观已更新") }
        }
    }

    fun changePassword(current: String, new: String, confirm: String) {
        viewModelScope.launch {
            _state.update { it.copy(passwordError = null, passwordChangeSuccess = false) }
            authRepository.changePassword(current, new, confirm).fold(
                onSuccess = {
                    _state.update {
                        it.copy(passwordChangeSuccess = true, passwordError = null, message = "密码修改成功")
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(passwordError = e.message ?: "修改密码失败") }
                }
            )
        }
    }

    fun clearPasswordChangeSuccess() {
        _state.update { it.copy(passwordChangeSuccess = false) }
    }

    fun clearPasswordError() {
        _state.update { it.copy(passwordError = null) }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.logout()
                _state.update { it.copy(logoutSuccess = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun requestSwitchAccount() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { it.copy(switchAccountRequested = true) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun parseThemeMode(raw: String): AppThemeMode = when (raw.lowercase()) {
        "light" -> AppThemeMode.LIGHT
        "dark" -> AppThemeMode.DARK
        else -> AppThemeMode.SYSTEM
    }

    private fun apiThemeToMode(theme: String): AppThemeMode = when (theme.lowercase()) {
        "light" -> AppThemeMode.LIGHT
        "dark" -> AppThemeMode.DARK
        else -> AppThemeMode.SYSTEM
    }
}
