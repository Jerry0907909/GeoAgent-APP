package com.geoagent.domain

import com.geoagent.data.api.dto.ChatHistoryMessage
import com.geoagent.agent.v2.V2RuntimeHistoryMessage

object ConversationContextBuilder {
    fun buildChatContextQuestion(
        question: String,
        history: List<ChatHistoryMessage>,
        maxTurns: Int = 8
    ): String = buildContextQuestion(
        question = question,
        history = history.map { it.role to it.content },
        maxTurns = maxTurns
    )

    fun buildV2ContextQuestion(
        question: String,
        history: List<V2RuntimeHistoryMessage>,
        maxTurns: Int = 8
    ): String = buildContextQuestion(
        question = question,
        history = history.map { it.role to it.content },
        maxTurns = maxTurns
    )

    private fun buildContextQuestion(
        question: String,
        history: List<Pair<String, String>>,
        maxTurns: Int
    ): String {
        val context = history
            .filter { (_, content) -> content.isNotBlank() }
            .takeLast(maxTurns.coerceAtLeast(1))
            .joinToString("\n") { (role, content) ->
                val label = when (role) {
                    "user" -> "用户"
                    "assistant" -> "助手"
                    else -> role
                }
                "$label：${content.compactForContext()}"
            }
        if (context.isBlank()) return question
        return buildString {
            appendLine("同一会话前文：")
            appendLine(context)
            appendLine()
            appendLine("当前问题：")
            append(question)
        }
    }

    private fun String.compactForContext(limit: Int = 480): String {
        val cleaned = replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length <= limit) cleaned else cleaned.take(limit) + "..."
    }
}
