package com.geoagent.data.api.dto

/** UI / domain model */
data class DocumentDto(
    val id: String,
    val name: String,
    val source: String,
    val type: String,
    val size: Long,
    val created_at: String,
    val collection: String,
    val chunks: Int = 0
)

data class DocumentChunkDto(
    val index: Int,
    val text: String,
    val charOffset: Int
)

data class DocumentUploadResponse(
    val success: Boolean = true,
    val message: String = "",
    val document_id: String? = null,
    val num_chunks: Int? = null
)

data class DocumentUploadProgress(
    val percent: Int,
    val stage: String,
    val detail: String = ""
)

data class DocumentImageDto(
    val id: String,
    val document_id: String,
    val index: Int,
    val path: String,
    val mime_type: String
)

data class CollectionDto(
    val name: String,
    val document_count: Int = 0,
    val count: Int? = null,
    val created_at: String? = null
)
