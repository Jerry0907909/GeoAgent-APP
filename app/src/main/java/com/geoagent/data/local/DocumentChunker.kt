package com.geoagent.data.local

data class DocumentChunk(
    val index: Int,
    val text: String,
    val charOffset: Int
)

object DocumentChunker {

    private const val CHUNK_SIZE = 500
    private const val OVERLAP = 100
    private val imageOcrMarker = Regex("""【\s*(?:文档图片|图片/扫描页)\s*OCR\s*】""")

    fun chunk(text: String): List<DocumentChunk> {
        val indexableText = removeImageOcrSections(text)
        if (indexableText.length <= CHUNK_SIZE) {
            return listOf(DocumentChunk(index = 0, text = indexableText.trim(), charOffset = 0))
        }

        val chunks = mutableListOf<DocumentChunk>()
        var offset = 0
        var index = 0

        while (offset < indexableText.length) {
            val end = (offset + CHUNK_SIZE).coerceAtMost(indexableText.length)
            var chunkText = indexableText.substring(offset, end)

            if (end < indexableText.length) {
                val lastPeriod = chunkText.lastIndexOf("。")
                val lastNewline = chunkText.lastIndexOf("\n")
                val breakPoint = maxOf(lastPeriod, lastNewline)
                if (breakPoint > CHUNK_SIZE / 2) {
                    chunkText = chunkText.substring(0, breakPoint + 1)
                }
            }

            chunks.add(DocumentChunk(index = index, text = chunkText.trim(), charOffset = offset))
            if (end == indexableText.length) break
            offset += chunkText.length - OVERLAP
            index++
        }

        return chunks
    }

    fun removeImageOcrSections(text: String): String {
        val markerStart = imageOcrMarker.find(text)?.range?.first ?: return text.trim()
        return text.substring(0, markerStart).trim()
    }

    fun withoutImageOcrChunks(chunks: List<DocumentChunk>): List<DocumentChunk> {
        val cleaned = mutableListOf<DocumentChunk>()
        for (chunk in chunks.sortedBy { it.index }) {
            val cleanText = removeImageOcrSections(chunk.text)
            if (cleanText.isNotBlank()) {
                cleaned.add(chunk.copy(text = cleanText))
            }
            if (cleanText != chunk.text.trim()) {
                break
            }
        }
        return cleaned
    }

    fun indexableStoredChunks(chunks: List<DocumentChunk>): List<DocumentChunk> {
        val cleaned = mutableListOf<DocumentChunk>()
        for (chunk in chunks.sortedBy { it.index }) {
            if (imageOcrMarker.containsMatchIn(chunk.text)) break
            cleaned.add(chunk)
        }
        return cleaned
    }

    fun searchChunks(query: String, chunks: List<DocumentChunk>, topK: Int = 5): List<DocumentChunk> {
        if (query.isBlank() || chunks.isEmpty()) return emptyList()

        val keywords = query
            .replace(Regex("[，。！？、；：\"'（）【】《》\\s]+"), " ")
            .trim()
            .split(" ")
            .filter { it.length >= 2 }

        if (keywords.isEmpty()) return chunks.take(topK)

        return chunks.map { chunk ->
            val score = keywords.sumOf { kw ->
                chunk.text.contains(kw, ignoreCase = true).let { if (it) 1.0 else 0.0 }
            } + keywords.sumOf { kw ->
                chunk.text.lowercase().countSubstring(kw.lowercase()) * 0.3
            }
            chunk to score
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    private fun String.countSubstring(sub: String): Int {
        var count = 0
        var idx = 0
        while (idx <= length - sub.length) {
            idx = indexOf(sub, idx)
            if (idx < 0) break
            count++
            idx += sub.length
        }
        return count
    }
}
