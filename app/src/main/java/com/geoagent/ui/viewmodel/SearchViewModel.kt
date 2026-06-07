package com.geoagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoagent.BuildConfig
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.domain.SearchContext
import com.geoagent.domain.SearchUseCase
import com.geoagent.domain.SearchUseCaseEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase,
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun prepareSearch(question: String) {
        viewModelScope.launch {
            val deepSeekKey = apiKeyStore.deepseekKey.first()?.takeIf { it.isNotBlank() }
                ?: BuildConfig.DEEPSEEK_API_KEY.takeIf { it.isNotBlank() }
            val tavilyKey = apiKeyStore.tavilyKey.first()?.takeIf { it.isNotBlank() }
                ?: BuildConfig.TAVILY_API_KEY.takeIf { it.isNotBlank() }
            if (deepSeekKey.isNullOrBlank() || tavilyKey.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, error = "请先设置 DeepSeek 和 Tavily API Key") }
                return@launch
            }

            _uiState.update { SearchUiState(isLoading = true) }
            searchUseCase.prepareSearchContext(question, deepSeekKey, tavilyKey).collect { event ->
                when (event) {
                    is SearchUseCaseEvent.Status -> _uiState.update { it.copy(status = event.message) }
                    is SearchUseCaseEvent.Plan -> _uiState.update { it.copy(queries = event.queries) }
                    is SearchUseCaseEvent.SearchReady -> _uiState.update {
                        it.copy(isLoading = false, status = null, context = event.context)
                    }
                    SearchUseCaseEvent.NoSearch -> _uiState.update {
                        it.copy(isLoading = false, status = null, searchRequired = false)
                    }
                    is SearchUseCaseEvent.Error -> _uiState.update {
                        it.copy(isLoading = false, status = null, error = event.message)
                    }
                }
            }
        }
    }
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val status: String? = null,
    val searchRequired: Boolean = true,
    val queries: List<String> = emptyList(),
    val context: SearchContext? = null,
    val error: String? = null
)
