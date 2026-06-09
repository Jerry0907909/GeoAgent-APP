package com.geoagent.agent.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2RagFallbackTest {

    @Test
    fun keywordFallbackRanksMatchingChunksAndKeepsScores() {
        val ranked = rankV2RagChunksByKeywords(
            question = "盆地沉积 地震解释",
            chunks = listOf(
                chunk("doc-a", 0, "火山监测和遥感解译。"),
                chunk("doc-b", 0, "盆地沉积体系包含三角洲沉积和湖相沉积。"),
                chunk("doc-c", 0, "地震解释可以辅助识别盆地沉积边界。")
            ),
            topK = 2
        )

        assertEquals(listOf("doc-c", "doc-b"), ranked.map { it.documentId })
        assertTrue(ranked[0].score > ranked[1].score)
        assertTrue(ranked.all { it.score > 0f })
    }

    @Test
    fun keywordFallbackHonorsTopKAndDropsNonMatches() {
        val ranked = rankV2RagChunksByKeywords(
            question = "构造应力",
            chunks = listOf(
                chunk("doc-a", 0, "构造应力控制断裂活动。"),
                chunk("doc-b", 0, "构造应力与盆地演化有关。"),
                chunk("doc-c", 0, "天气预报和城市交通。")
            ),
            topK = 1
        )

        assertEquals(1, ranked.size)
        assertEquals("doc-a", ranked.single().documentId)
    }

    private fun chunk(documentId: String, index: Int, text: String): V2RuntimeRagChunk =
        V2RuntimeRagChunk(
            documentId = documentId,
            chunkIndex = index,
            text = text,
            score = 0f
        )
}
