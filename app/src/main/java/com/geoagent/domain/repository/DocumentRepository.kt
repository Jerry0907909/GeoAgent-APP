package com.geoagent.domain.repository

import android.content.Context
import android.net.Uri
import com.geoagent.data.api.dto.DocumentDto
import com.geoagent.data.api.dto.DocumentUploadResponse
import com.geoagent.data.api.dto.CollectionDto
import okhttp3.MultipartBody

interface DocumentRepository {
    suspend fun getDocuments(collection: String? = null): Result<List<DocumentDto>>
    suspend fun uploadFile(file: MultipartBody.Part, collection: String?): Result<DocumentUploadResponse>
    suspend fun uploadFileFromUri(context: Context, uri: Uri): Result<DocumentUploadResponse>
    suspend fun deleteDocument(docId: String): Result<Unit>
    suspend fun getCollections(): Result<List<CollectionDto>>
    suspend fun getDocumentContent(source: String, collection: String?): Result<String>
}