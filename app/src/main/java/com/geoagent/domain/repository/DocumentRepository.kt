package com.geoagent.domain.repository

import android.content.Context
import android.net.Uri
import com.geoagent.data.api.dto.DocumentChunkDto
import com.geoagent.data.api.dto.DocumentDto
import com.geoagent.data.api.dto.DocumentImageDto
import com.geoagent.data.api.dto.DocumentUploadProgress
import com.geoagent.data.api.dto.DocumentUploadResponse
import com.geoagent.data.api.dto.CollectionDto
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody

interface DocumentRepository {
    suspend fun getDocuments(collection: String? = null): Result<List<DocumentDto>>
    suspend fun uploadFile(file: MultipartBody.Part, collection: String?): Result<DocumentUploadResponse>
    suspend fun uploadFileFromUri(context: Context, uri: Uri): Result<DocumentUploadResponse>
    fun uploadFileFromUriWithProgress(context: Context, uri: Uri): Flow<DocumentUploadProgress>
    suspend fun deleteDocument(docId: String): Result<Unit>
    suspend fun renameDocument(docId: String, name: String): Result<Unit>
    suspend fun getCollections(): Result<List<CollectionDto>>
    suspend fun getDocumentContent(source: String, collection: String?): Result<String>
    suspend fun getDocumentChunks(source: String, collection: String?): Result<List<DocumentChunkDto>>
    suspend fun getDocumentImages(source: String): Result<List<DocumentImageDto>>
}
