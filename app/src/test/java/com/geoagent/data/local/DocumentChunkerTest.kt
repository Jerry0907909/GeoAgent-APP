package com.geoagent.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentChunkerTest {

    @Test(timeout = 1_000L)
    fun chunkFinishesWhenTailIsNoLongerThanOverlap() {
        val text = "a".repeat(550)

        val chunks = DocumentChunker.chunk(text)

        assertTrue(chunks.isNotEmpty())
        assertEquals(0, chunks.first().charOffset)
        assertTrue(chunks.last().charOffset < text.length)
    }

    @Test
    fun chunkDoesNotIncludeDocxImageOcrSection() {
        val text = """
            正文第一段。
            正文第二段。

            【文档图片 OCR】
            Chengdu University Teg
            cumt.edu.cn
        """.trimIndent()

        val chunks = DocumentChunker.chunk(text)

        assertEquals(1, chunks.size)
        assertEquals("正文第一段。\n正文第二段。", chunks.first().text)
    }

    @Test
    fun existingChunksHideImageOcrMarkerAndFollowingChunks() {
        val chunks = listOf(
            DocumentChunk(0, "正文第一段。", 0),
            DocumentChunk(1, "正文第二段。\n\n【文档图片 OCR】\nChengdu University Teg", 20),
            DocumentChunk(2, "cumt.edu.cn\n机构设置", 120)
        )

        val cleaned = DocumentChunker.withoutImageOcrChunks(chunks)

        assertEquals(2, cleaned.size)
        assertEquals("正文第一段。", cleaned[0].text)
        assertEquals("正文第二段。", cleaned[1].text)
    }

    @Test
    fun storedSearchIndexExcludesDirtyMarkerChunk() {
        val chunks = listOf(
            DocumentChunk(0, "正文第一段。", 0),
            DocumentChunk(1, "正文第二段。\n\n【文档图片 OCR】\nChengdu University Teg", 20),
            DocumentChunk(2, "cumt.edu.cn\n机构设置", 120)
        )

        val searchable = DocumentChunker.indexableStoredChunks(chunks)

        assertEquals(1, searchable.size)
        assertEquals("正文第一段。", searchable[0].text)
    }
}
