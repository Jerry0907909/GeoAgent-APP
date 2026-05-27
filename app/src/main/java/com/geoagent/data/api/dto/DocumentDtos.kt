package com.geoagent.data.api.dto

// Document DTOs — aligned with FastAPI /documents/list response

/** Raw item from GET /documents/list */
data class BackendDocumentItem(
    val source: String = "",
    val collection: String = "",
    val author: String? = null,
    val date: String? = null,
    val type: String? = null,
    val file_type: String? = null,
    val chunks: Int = 0
)

data class DocumentListResponse(
    val total: Int = 0,
    val documents: List<BackendDocumentItem> = emptyList()
)

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

fun BackendDocumentItem.toDocumentDto(): DocumentDto {
    val displayName = source.substringAfterLast('/').ifEmpty { source }
    return DocumentDto(
        id = "$collection::$source",
        name = displayName,
        source = source,
        type = file_type ?: type ?: "document",
        size = 0L,
        created_at = date?.takeIf { it.isNotBlank() } ?: "-",
        collection = collection,
        chunks = chunks
    )
}

data class DocumentUploadResponse(
    val success: Boolean = true,
    val message: String = "",
    val document_id: String? = null,
    val num_chunks: Int? = null
)

data class DeleteResponse(
    val success: Boolean,
    val message: String
)

data class CollectionDto(
    val name: String,
    val document_count: Int = 0,
    val count: Int? = null,
    val created_at: String? = null
)

data class CollectionListResponse(
    val collections: List<CollectionDto> = emptyList()
)

data class DocumentContentResponse(
    val source: String,
    val content: String,
    val chunk_count: Int = 0
)
