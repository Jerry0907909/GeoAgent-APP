package com.geoagent.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoagent.data.api.dto.SearchEvent
import com.geoagent.domain.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchPhase { IDLE, PLANNING, SEARCHING, EXTRACTING, ANSWERING, DONE }

data class SearchUiState(
    val query: String = "",
    val currentPhase: SearchPhase = SearchPhase.IDLE,
    val planQueries: List<String> = emptyList(),
    val searchResults: List<String> = emptyList(),
    val extractedContent: String = "",
    val answer: String = "",
    val citations: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        _uiState.update { SearchUiState(query = query, isLoading = true) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchRepository.deepSearch(query).catch { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }.collect { event ->
                when (event) {
                    is SearchEvent.Plan -> _uiState.update { it.copy(planQueries = event.queries, currentPhase = SearchPhase.PLANNING) }
                    is SearchEvent.Search -> _uiState.update { it.copy(searchResults = event.results.map { r -> r.snippet }, currentPhase = SearchPhase.SEARCHING) }
                    is SearchEvent.Extract -> _uiState.update { it.copy(extractedContent = event.content, currentPhase = SearchPhase.EXTRACTING) }
                    is SearchEvent.Answer -> _uiState.update { it.copy(answer = it.answer + event.content, currentPhase = SearchPhase.ANSWERING) }
                    is SearchEvent.Citation -> _uiState.update { it.copy(citations = event.citations.map { c -> c.title }, currentPhase = SearchPhase.DONE) }
                    is SearchEvent.Done -> _uiState.update { it.copy(isLoading = false, currentPhase = SearchPhase.DONE) }
                    is SearchEvent.Error -> _uiState.update { it.copy(error = event.message, isLoading = false) }
                }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun reset() { _uiState.value = SearchUiState() }

    override fun onCleared() { super.onCleared(); searchJob?.cancel() }
}