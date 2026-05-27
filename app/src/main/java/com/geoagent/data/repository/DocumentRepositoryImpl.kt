package com.geoagent.data.repository

import android.content.Context
import android.net.Uri
import com.geoagent.data.api.GeoAgentApi
import com.geoagent.data.api.dto.*
import com.geoagent.domain.repository.DocumentRepository
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class DocumentRepositoryImpl(
    private val api: GeoAgentApi
) : DocumentRepository {

    private val gson = Gson()

    private fun httpErrorMessage(e: Exception, fallback: String): String {
        if (e is HttpException) {
            val detail = runCatching {
                val raw = e.response()?.errorBody()?.string().orEmpty()
                if (raw.isBlank()) return@runCatching null
                gson.fromJson(raw, Map::class.java)["detail"]?.toString()
            }.getOrNull()
            if (!detail.isNullOrBlank()) return detail
        }
        return e.message ?: fallback
    }

    override suspend fun getDocuments(collection: String?): Result<List<DocumentDto>> {
        return try {
            val response = api.getDocuments(collection)
            Result.success(response.documents.map { it.toDocumentDto() })
        } catch (e: Exception) {
            Result.failure(Exception(httpErrorMessage(e, "加载文档列表失败"), e))
        }
    }

    override suspend fun uploadFile(
        file: MultipartBody.Part, collection: String?
    ): Result<DocumentUploadResponse> {
        return try {
            val collectionBody = collection?.toRequestBody("text/plain".toMediaType())
            Result.success(api.uploadFile(file, collectionBody))
        } catch (e: Exception) {
            Result.failure(Exception(httpErrorMessage(e, "上传失败"), e))
        }
    }

    override suspend fun uploadFileFromUri(
        context: Context, uri: Uri
    ): Result<DocumentUploadResponse> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("无法读取文件"))
            val bytes = inputStream.use { it.readBytes() }

            var fileName = uri.lastPathSegment ?: "file"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.let { fileName = it }
                    }
                }
            }

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val requestBody = bytes.toRequestBody(mimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("file", fileName, requestBody)

            Result.success(api.uploadFile(part, null))
        } catch (e: Exception) {
            Result.failure(Exception(httpErrorMessage(e, "上传失败"), e))
        }
    }

    override suspend fun deleteDocument(docId: String): Result<Unit> {
        return try {
            val separator = docId.indexOf("::")
            val response = if (separator > 0) {
                val collection = docId.substring(0, separator)
                val sourceName = docId.substring(separator + 2)
                api.deleteDocumentBySource(sourceName, collection)
            } else {
                return Result.failure(Exception("无效的文档标识"))
            }
            if (response.success) Result.success(Unit)
            else Result.failure(Exception(response.message))
        } catch (e: Exception) {
            Result.failure(Exception(httpErrorMessage(e, "删除失败"), e))
        }
    }

    override suspend fun getCollections(): Result<List<CollectionDto>> {
        return try {
            Result.success(api.getCollections().collections)
        } catch (e: Exception) {
            Result.failure(Exception(httpErrorMessage(e, "加载知识库失败"), e))
        }
    }

    override suspend fun getDocumentContent(source: String, collection: String?): Result<String> {
        return try {
            val response = api.getDocumentContent(source, collection)
            Result.success(response.content)
        } catch (e: Exception) {
            Result.failure(Exception(httpErrorMessage(e, "加载文档内容失败"), e))
        }
    }
}
