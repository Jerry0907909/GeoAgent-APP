package com.geoagent.ui.documents

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoagent.data.api.dto.DocumentDto
import com.geoagent.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentUiState(
    val documents: List<DocumentDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val uploadSuccess: String = "",
    val documentContent: String? = null,
    val contentTitle: String? = null,
    val isContentLoading: Boolean = false
)

class DocumentViewModel(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    fun loadDocuments(collection: String? = null) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            documentRepository.getDocuments(collection).fold(
                onSuccess = { docs -> _uiState.update { it.copy(documents = docs, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }

    fun uploadFile(context: Context, uri: Uri) {
        _uiState.update { it.copy(isLoading = true, uploadSuccess = "") }
        viewModelScope.launch {
            documentRepository.uploadFileFromUri(context, uri).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, uploadSuccess = "上传成功") }
                    loadDocuments()
                },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            documentRepository.deleteDocument(docId).fold(
                onSuccess = { loadDocuments() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
    }

    fun loadDocumentContent(source: String, collection: String) {
        _uiState.update {
            it.copy(isContentLoading = true, contentTitle = source, documentContent = null, error = null)
        }
        viewModelScope.launch {
            documentRepository.getDocumentContent(source, collection).fold(
                onSuccess = { content ->
                    _uiState.update {
                        it.copy(isContentLoading = false, documentContent = content)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isContentLoading = false, error = e.message)
                    }
                }
            )
        }
    }

    fun clearDocumentContent() {
        _uiState.update {
            it.copy(documentContent = null, contentTitle = null, isContentLoading = false)
        }
    }
}