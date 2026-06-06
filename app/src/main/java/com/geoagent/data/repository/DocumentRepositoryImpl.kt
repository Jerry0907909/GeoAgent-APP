package com.geoagent.data.repository

import android.content.Context
import android.net.Uri
import com.geoagent.data.api.SiliconFlowEmbeddingClient
import com.geoagent.data.api.dto.*
import com.geoagent.data.local.DocumentChunker
import com.geoagent.data.local.DocumentParser
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.local.LocalDocument
import com.geoagent.domain.repository.DocumentRepository
import okhttp3.MultipartBody
import java.util.UUID

class DocumentRepositoryImpl(
    private val documentStore: DocumentStore,
    private val embeddingClient: SiliconFlowEmbeddingClient
) : DocumentRepository {

    override suspend fun getDocuments(collection: String?): Result<List<DocumentDto>> {
        return try {
            val docs = documentStore.getDocumentsSnapshot()
            Result.success(docs.map { it.toDto() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(
        file: MultipartBody.Part, collection: String?
    ): Result<DocumentUploadResponse> {
        return Result.failure(Exception("请使用文件选择器上传"))
    }

    override suspend fun uploadFileFromUri(
        context: Context, uri: Uri
    ): Result<DocumentUploadResponse> {
        return try {
            var fileName = uri.lastPathSegment ?: "document"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { fileName = it }
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }

            DocumentParser.init(context)
            val result = DocumentParser.parse(context, uri, fileName)
            result.fold(
                onSuccess = { text ->
                    val docId = UUID.randomUUID().toString().take(8)
                    val chunks = DocumentChunker.chunk(text)
                    val doc = LocalDocument(
                        id = docId,
                        name = fileName,
                        fileType = fileName.substringAfterLast('.', "unknown"),
                        sizeBytes = fileSize,
                        chunkCount = chunks.size
                    )

                    // Generate embeddings for all chunks
                    val chunkTexts = chunks.map { it.text }
                    val embeddings = if (chunkTexts.isNotEmpty()) {
                        embeddingClient.embed(chunkTexts).getOrDefault(emptyList())
                    } else emptyList()

                    documentStore.addDocument(doc, chunks, embeddings)
                    Result.success(DocumentUploadResponse(
                        success = true,
                        document_id = docId,
                        message = "解析成功：$fileName，${chunks.size}块，${embeddings.size}向量",
                        num_chunks = chunks.size
                    ))
                },
                onFailure = { e ->
                    Result.failure(Exception(e.message ?: "文档解析失败"))
                }
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "上传失败"))
        }
    }

    override suspend fun deleteDocument(docId: String): Result<Unit> {
        return try {
            documentStore.deleteDocument(docId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCollections(): Result<List<CollectionDto>> {
        return Result.success(listOf(CollectionDto(name = "local", document_count = 0, created_at = "")))
    }

    override suspend fun getDocumentContent(source: String, collection: String?): Result<String> {
        return try {
            val chunks = documentStore.getChunks(source)
            Result.success(chunks.joinToString("\n\n") { it.text })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun LocalDocument.toDto() = DocumentDto(
        id = id,
        name = name,
        source = name,
        type = fileType,
        size = sizeBytes,
        created_at = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(createdAt)),
        collection = "local"
    )
}


