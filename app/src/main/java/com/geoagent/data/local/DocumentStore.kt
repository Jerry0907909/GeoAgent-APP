package com.geoagent.data.local

import android.content.ContentValues
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Locale

data class LocalDocument(
    val id: String,
    val name: String,
    val fileType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val chunkCount: Int = 0
)

data class LocalDocumentImage(
    val id: String,
    val documentId: String,
    val index: Int,
    val path: String,
    val mimeType: String
)

class DocumentStore(context: Context) {

    private val appContext = context.applicationContext
    private val db = GeoAgentDatabase(context).writableDatabase
    private val gson = Gson()

    // ── Documents ──

    fun getDocumentsSnapshot(): List<LocalDocument> {
        val cursor = db.rawQuery("SELECT * FROM documents ORDER BY created_at DESC", null)
        val docs = mutableListOf<LocalDocument>()
        cursor.use {
            while (it.moveToNext()) {
                docs.add(LocalDocument(
                    id = it.getString(0),
                    name = it.getString(1),
                    fileType = it.getString(2),
                    sizeBytes = it.getLong(3),
                    createdAt = it.getLong(4),
                    chunkCount = it.getInt(5)
                ))
            }
        }
        return docs
    }

    fun addDocument(
        doc: LocalDocument,
        chunks: List<DocumentChunk>,
        embeddings: List<FloatArray> = emptyList(),
        images: List<ParsedDocumentImage> = emptyList()
    ) {
        db.beginTransaction()
        try {
            db.insert("documents", null, ContentValues().apply {
                put("id", doc.id)
                put("name", doc.name)
                put("file_type", doc.fileType)
                put("size_bytes", doc.sizeBytes)
                put("created_at", doc.createdAt)
                put("chunk_count", chunks.size)
            })
            for (chunk in chunks) {
                val chunkId = "${doc.id}_${chunk.index}"
                db.insert("chunks", null, ContentValues().apply {
                    put("id", chunkId)
                    put("document_id", doc.id)
                    put("chunk_index", chunk.index)
                    put("text", chunk.text)
                    put("char_offset", chunk.charOffset)
                })
            }
            val safeCount = embeddings.size.coerceAtMost(chunks.size)
            for (i in 0 until safeCount) {
                val chunkId = "${doc.id}_${chunks[i].index}"
                db.insert("embeddings", null, ContentValues().apply {
                    put("chunk_id", chunkId)
                    put("document_id", doc.id)
                    put("vector_json", gson.toJson(embeddings[i].toList()))
                })
            }
            images.forEachIndexed { index, image ->
                val stored = persistImage(doc.id, index, image)
                db.insert("document_images", null, ContentValues().apply {
                    put("id", stored.id)
                    put("document_id", stored.documentId)
                    put("image_index", stored.index)
                    put("path", stored.path)
                    put("mime_type", stored.mimeType)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteDocument(docId: String) {
        db.beginTransaction()
        try {
            val images = getImages(docId)
            db.delete("embeddings", "document_id = ?", arrayOf(docId))
            db.delete("chunks", "document_id = ?", arrayOf(docId))
            db.delete("document_images", "document_id = ?", arrayOf(docId))
            db.delete("documents", "id = ?", arrayOf(docId))
            images.forEach { File(it.path).delete() }
            File(imagesDir(docId).absolutePath).deleteRecursively()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun renameDocument(docId: String, name: String): Boolean {
        val rows = db.update("documents", ContentValues().apply {
            put("name", name)
        }, "id = ?", arrayOf(docId))
        return rows > 0
    }

    fun deleteAllDocuments() {
        db.beginTransaction()
        try {
            val docs = getDocumentsSnapshot()
            db.delete("embeddings", null, null)
            db.delete("chunks", null, null)
            db.delete("document_images", null, null)
            db.delete("documents", null, null)
            docs.forEach { File(imagesDir(it.id).absolutePath).deleteRecursively() }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── Chunks ──

    fun getChunks(docId: String): List<DocumentChunk> {
        val cursor = db.rawQuery("SELECT * FROM chunks WHERE document_id = ? ORDER BY chunk_index", arrayOf(docId))
        val chunks = mutableListOf<DocumentChunk>()
        cursor.use {
            while (it.moveToNext()) {
                chunks.add(DocumentChunk(
                    index = it.getInt(2),
                    text = it.getString(3),
                    charOffset = it.getInt(4)
                ))
            }
        }
        return chunks
    }

    fun getAllChunks(): List<Pair<String, DocumentChunk>> {
        val cursor = db.rawQuery("SELECT document_id, chunk_index, text, char_offset FROM chunks ORDER BY document_id, chunk_index", null)
        val chunks = mutableListOf<Pair<String, DocumentChunk>>()
        cursor.use {
            while (it.moveToNext()) {
                chunks.add(it.getString(0) to DocumentChunk(
                    index = it.getInt(1),
                    text = it.getString(2),
                    charOffset = it.getInt(3)
                ))
            }
        }
        return chunks
    }

    // ── Embeddings ──

    fun getEmbeddings(docId: String): List<Pair<String, FloatArray>> {
        val cursor = db.rawQuery("SELECT chunk_id, vector_json FROM embeddings WHERE document_id = ?", arrayOf(docId))
        val result = mutableListOf<Pair<String, FloatArray>>()
        cursor.use {
            while (it.moveToNext()) {
                val vec = gson.fromJson<List<Float>>(it.getString(1), object : TypeToken<List<Float>>() {}.type)
                result.add(it.getString(0) to vec.map { f -> f }.toFloatArray())
            }
        }
        return result
    }

    fun getAllEmbeddings(): List<Triple<String, String, FloatArray>> {
        val cursor = db.rawQuery("SELECT chunk_id, document_id, vector_json FROM embeddings", null)
        val result = mutableListOf<Triple<String, String, FloatArray>>()
        cursor.use {
            while (it.moveToNext()) {
                val vec = gson.fromJson<List<Float>>(it.getString(2), object : TypeToken<List<Float>>() {}.type)
                result.add(Triple(it.getString(0), it.getString(1), vec.map { f -> f }.toFloatArray()))
            }
        }
        return result
    }

    fun getImages(docId: String): List<LocalDocumentImage> {
        val cursor = db.rawQuery(
            "SELECT id, document_id, image_index, path, mime_type FROM document_images WHERE document_id = ? ORDER BY image_index",
            arrayOf(docId)
        )
        val images = mutableListOf<LocalDocumentImage>()
        cursor.use {
            while (it.moveToNext()) {
                images.add(LocalDocumentImage(
                    id = it.getString(0),
                    documentId = it.getString(1),
                    index = it.getInt(2),
                    path = it.getString(3),
                    mimeType = it.getString(4)
                ))
            }
        }
        return images
    }

    private fun persistImage(docId: String, index: Int, image: ParsedDocumentImage): LocalDocumentImage {
        val dir = imagesDir(docId).apply { mkdirs() }
        val id = "${docId}_img_$index"
        val target = File(dir, "$id.${image.mimeType.fileExtension()}")
        target.outputStream().use { it.write(image.bytes) }
        return LocalDocumentImage(
            id = id,
            documentId = docId,
            index = index,
            path = target.absolutePath,
            mimeType = image.mimeType
        )
    }

    private fun imagesDir(docId: String): File = File(appContext.filesDir, "document_images/$docId")

    private fun String.fileExtension(): String {
        return when (lowercase(Locale.ROOT)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/tiff" -> "tiff"
            else -> "bin"
        }
    }
}
