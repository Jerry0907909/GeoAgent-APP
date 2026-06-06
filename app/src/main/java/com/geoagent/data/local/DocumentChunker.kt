package com.geoagent.data.local

data class DocumentChunk(
    val index: Int,
    val text: String,
    val charOffset: Int
)

object DocumentChunker {

    private const val CHUNK_SIZE = 500
    private const val OVERLAP = 100

    fun chunk(text: String): List<DocumentChunk> {
        if (text.length <= CHUNK_SIZE) {
            return listOf(DocumentChunk(index = 0, text = text.trim(), charOffset = 0))
        }

        val chunks = mutableListOf<DocumentChunk>()
        var offset = 0
        var index = 0

        while (offset < text.length) {
            val end = (offset + CHUNK_SIZE).coerceAtMost(text.length)
            var chunkText = text.substring(offset, end)

            if (end < text.length) {
                val lastPeriod = chunkText.lastIndexOf("。")
                val lastNewline = chunkText.lastIndexOf("\n")
                val breakPoint = maxOf(lastPeriod, lastNewline)
                if (breakPoint > CHUNK_SIZE / 2) {
                    chunkText = chunkText.substring(0, breakPoint + 1)
                }
            }

            chunks.add(DocumentChunk(index = index, text = chunkText.trim(), charOffset = offset))
            offset += chunkText.length - OVERLAP
            index++
        }

        return chunks
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
