package com.geoagent.data.repository

import android.content.Context
import android.net.Uri
import com.geoagent.BuildConfig
import com.geoagent.data.api.SiliconFlowEmbeddingClient
import com.geoagent.data.api.dto.*
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.DocumentChunker
import com.geoagent.data.local.DocumentParser
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.local.LocalDocument
import com.geoagent.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import java.util.UUID

class DocumentRepositoryImpl(
    private val documentStore: DocumentStore,
    private val embeddingClient: SiliconFlowEmbeddingClient,
    private val apiKeyStore: ApiKeyStore
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
        return withContext(Dispatchers.IO) {
            uploadFileInternal(context, uri) {}
        }
    }

    override fun uploadFileFromUriWithProgress(
        context: Context,
        uri: Uri
    ): Flow<DocumentUploadProgress> = flow {
        val result = uploadFileInternal(context, uri) { progress -> emit(progress) }
        val response = result.getOrElse { throw it }
        emit(DocumentUploadProgress(100, "上传完成", response.message))
    }.flowOn(Dispatchers.IO)

    private suspend fun uploadFileInternal(
        context: Context,
        uri: Uri,
        onProgress: suspend (DocumentUploadProgress) -> Unit
    ): Result<DocumentUploadResponse> {
        return try {
            onProgress(DocumentUploadProgress(5, "读取文件", "正在读取文件信息"))
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

            onProgress(DocumentUploadProgress(25, "解析文档", "正在提取文本和图片"))
            DocumentParser.init(context)
            val parsed = DocumentParser.parse(context, uri, fileName).getOrElse { e ->
                return Result.failure(Exception(e.message ?: "文档解析失败"))
            }
            val indexText = DocumentChunker.removeImageOcrSections(parsed.text)
                .ifBlank { "该文档包含 ${parsed.images.size} 张图片，未识别到可索引文本。" }

            onProgress(DocumentUploadProgress(45, "切分内容", "正在构建知识片段"))
            val docId = UUID.randomUUID().toString().take(8)
            val chunks = DocumentChunker.chunk(indexText).filter { it.text.isNotBlank() }
            if (chunks.isEmpty()) {
                return Result.failure(Exception("未能切分出有效文档内容"))
            }
            val doc = LocalDocument(
                id = docId,
                name = fileName,
                fileType = fileName.substringAfterLast('.', "unknown"),
                sizeBytes = fileSize,
                chunkCount = chunks.size
            )

            onProgress(DocumentUploadProgress(65, "生成向量", "${chunks.size} 个片段正在向量化"))
            val chunkTexts = chunks.map { it.text }
            val apiKey = siliconFlowApiKey()
                ?: return Result.failure(Exception("请先设置 SiliconFlow API Key 后再上传知识库文档"))
            val embeddings = embeddingClient.embed(chunkTexts, apiKey).getOrElse { e ->
                return Result.failure(Exception(e.message ?: "Embedding 向量生成失败"))
            }
            if (embeddings.size < chunks.size) {
                return Result.failure(Exception("Embedding 向量数量不足，请重试上传"))
            }

            onProgress(DocumentUploadProgress(88, "保存知识库", "保存文本、向量和 ${parsed.images.size} 张图片"))
            documentStore.addDocument(doc, chunks, embeddings, parsed.images)
            Result.success(DocumentUploadResponse(
                success = true,
                document_id = docId,
                message = "解析成功：$fileName，${chunks.size}块，${embeddings.size}向量，${parsed.images.size}张图片",
                num_chunks = chunks.size
            ))
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

    override suspend fun renameDocument(docId: String, name: String): Result<Unit> {
        return try {
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                return Result.failure(Exception("文件名不能为空"))
            }
            if (!documentStore.renameDocument(docId, trimmedName)) {
                return Result.failure(Exception("未找到要重命名的文档"))
            }
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
            val chunks = DocumentChunker.withoutImageOcrChunks(documentStore.getChunks(resolveDocumentId(source)))
            if (chunks.isEmpty()) {
                return Result.failure(Exception("未找到文档内容"))
            }
            Result.success(chunks.joinToString("\n\n") { it.text })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDocumentChunks(source: String, collection: String?): Result<List<DocumentChunkDto>> {
        return try {
            val chunks = DocumentChunker.withoutImageOcrChunks(documentStore.getChunks(resolveDocumentId(source)))
            if (chunks.isEmpty()) {
                return Result.failure(Exception("未找到文档内容"))
            }
            Result.success(chunks.map {
                DocumentChunkDto(
                    index = it.index,
                    text = it.text,
                    charOffset = it.charOffset
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDocumentImages(source: String): Result<List<DocumentImageDto>> {
        return try {
            val images = documentStore.getImages(resolveDocumentId(source))
            Result.success(images.map {
                DocumentImageDto(
                    id = it.id,
                    document_id = it.documentId,
                    index = it.index,
                    path = it.path,
                    mime_type = it.mimeType
                )
            })
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

    private suspend fun siliconFlowApiKey(): String? =
        apiKeyStore.siliconFlowKey.first()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SILICONFLOW_API_KEY.takeIf { it.isNotBlank() }

    private fun resolveDocumentId(source: String): String {
        return documentStore.getDocumentsSnapshot().firstOrNull {
            it.id == source || it.name.equals(source, ignoreCase = true)
        }?.id ?: source
    }
}
