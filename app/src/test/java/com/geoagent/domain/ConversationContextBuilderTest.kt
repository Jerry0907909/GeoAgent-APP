package com.geoagent.domain

import com.geoagent.data.api.dto.ChatHistoryMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextBuilderTest {

    @Test
    fun chatContextQuestionCarriesPreviousQaForPronounFollowUp() {
        val question = ConversationContextBuilder.buildChatContextQuestion(
            question = "最近它有哪些热梗",
            history = listOf(
                ChatHistoryMessage("user", "这是什么"),
                ChatHistoryMessage("assistant", "这是一只北美负鼠。")
            )
        )

        assertTrue(question.contains("同一会话前文"))
        assertTrue(question.contains("北美负鼠"))
        assertTrue(question.contains("最近它有哪些热梗"))
    }

    @Test
    fun chatContextQuestionReturnsCurrentQuestionWhenHistoryIsEmpty() {
        val question = ConversationContextBuilder.buildChatContextQuestion(
            question = "最近它有哪些热梗",
            history = emptyList()
        )

        assertTrue(question == "最近它有哪些热梗")
        assertFalse(question.contains("同一会话前文"))
    }
}
