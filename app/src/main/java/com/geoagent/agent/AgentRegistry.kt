package com.geoagent.agent

import java.util.Locale

data class AgentMeta(
    val name: String,
    val displayName: String,
    val description: String,
    val keywords: Set<String>,
    val regexPatterns: List<Regex> = emptyList(),
    val semanticHints: Set<String> = emptySet(),
    val priority: Int = 100,
    val requiresAuth: Boolean = true,
    val ttlMinutes: Int = 5,
    val contextExitKeywords: Set<String> = DEFAULT_CONTEXT_EXIT_KEYWORDS
)

data class SessionContext(
    val activeAgent: String? = null,
    val activatedAtMillis: Long? = null
) {
    fun isActive(nowMillis: Long, ttlMinutes: Int): Boolean {
        if (activeAgent == null || activatedAtMillis == null) return false
        return nowMillis - activatedAtMillis <= ttlMinutes * 60_000L
    }
}

class SessionContextManager(initial: SessionContext = SessionContext()) {
    private var context: SessionContext = initial

    fun snapshot(): SessionContext = context

    fun activate(agentName: String, nowMillis: Long) {
        context = SessionContext(activeAgent = agentName, activatedAtMillis = nowMillis)
    }

    fun clear() {
        context = SessionContext()
    }
}

enum class RouteDisposition {
    DIRECT,
    CONFIRM,
    FALLBACK
}

data class RouteResult(
    val agentName: String?,
    val confidence: Float,
    val disposition: RouteDisposition,
    val reason: String,
    val extractedParams: Map<String, String> = emptyMap(),
    val shouldClearContext: Boolean = false
)

private data class AgentScore(
    val meta: AgentMeta,
    val confidence: Float,
    val reason: String
)

class IntentRouter(
    private val agents: List<AgentMeta>,
    private val highConfidenceThreshold: Float = 0.8f,
    private val mediumConfidenceThreshold: Float = 0.6f
) {
    fun route(
        input: String,
        sessionContext: SessionContext = SessionContext(),
        nowMillis: Long = System.currentTimeMillis(),
        isAuthenticated: Boolean = true
    ): RouteResult {
        val normalized = normalize(input)
        if (normalized.isBlank()) {
            return fallback("empty_input")
        }

        val contextAgent = sessionContext.activeAgent?.let { active ->
            agents.firstOrNull { it.name == active }
        }

        if (contextAgent != null && containsContextExit(normalized, contextAgent)) {
            return fallback(reason = "context_exit", shouldClearContext = true)
        }

        if (contextAgent != null && sessionContext.isActive(nowMillis, contextAgent.ttlMinutes)) {
            val inheritedScore = scoreAgent(contextAgent, input, normalized)
            if (inheritedScore.confidence >= 0.55f) {
                val confidence = (inheritedScore.confidence + 0.12f).coerceAtMost(0.92f)
                return decide(
                    agentName = contextAgent.name,
                    confidence = confidence,
                    reason = "context_inherit:${inheritedScore.reason}"
                )
            }
        }

        val candidates = agents
            .asSequence()
            .filter { isAuthenticated || !it.requiresAuth }
            .map { scoreAgent(it, input, normalized) }
            .filter { it.confidence > 0f }
            .sortedByDescending { it.confidence }
            .toList()

        val best = candidates.firstOrNull() ?: return fallback("no_match")
        val second = candidates.getOrNull(1)
        val adjusted = if (second != null && best.confidence - second.confidence < 0.08f) {
            (best.confidence - 0.08f).coerceAtLeast(0f)
        } else {
            best.confidence
        }

        return decide(
            agentName = best.meta.name,
            confidence = adjusted,
            reason = best.reason
        )
    }

    private fun decide(agentName: String, confidence: Float, reason: String): RouteResult {
        return when {
            confidence >= highConfidenceThreshold -> RouteResult(
                agentName = agentName,
                confidence = confidence,
                disposition = RouteDisposition.DIRECT,
                reason = reason
            )

            confidence >= mediumConfidenceThreshold -> RouteResult(
                agentName = agentName,
                confidence = confidence,
                disposition = RouteDisposition.CONFIRM,
                reason = reason
            )

            else -> fallback(reason = "below_threshold:$reason")
        }
    }

    private fun fallback(reason: String, shouldClearContext: Boolean = false): RouteResult {
        return RouteResult(
            agentName = null,
            confidence = 0f,
            disposition = RouteDisposition.FALLBACK,
            reason = reason,
            shouldClearContext = shouldClearContext
        )
    }

    private fun containsContextExit(normalized: String, meta: AgentMeta): Boolean {
        return meta.contextExitKeywords.any { exit -> normalized.contains(normalize(exit)) }
    }

    private fun scoreAgent(meta: AgentMeta, rawInput: String, normalizedInput: String): AgentScore {
        val keywordHits = meta.keywords.count { keyword ->
            normalizedInput.contains(normalize(keyword))
        }
        val keywordScore = if (keywordHits == 0) 0f else (0.42f + keywordHits * 0.14f).coerceAtMost(0.9f)

        val regexHits = meta.regexPatterns.count { pattern ->
            pattern.containsMatchIn(rawInput)
        }
        val regexScore = if (regexHits == 0) 0f else (0.56f + regexHits * 0.16f).coerceAtMost(0.95f)

        val semanticHits = meta.semanticHints.count { hint ->
            normalizedInput.contains(normalize(hint))
        }
        val semanticScore = if (semanticHits == 0) 0f else (0.5f + semanticHits * 0.1f).coerceAtMost(0.84f)

        val base = maxOf(keywordScore, regexScore, semanticScore)
        if (base <= 0f) {
            return AgentScore(meta, 0f, "none")
        }

        val priorityBoost = ((120 - meta.priority.coerceIn(0, 120)) / 2000f).coerceAtLeast(0f)
        val confidence = (base + priorityBoost).coerceAtMost(0.98f)
        val reason = when {
            regexScore >= keywordScore && regexScore >= semanticScore -> "regex_hit:$regexHits"
            keywordScore >= semanticScore -> "keyword_hit:$keywordHits"
            else -> "semantic_hit:$semanticHits"
        }
        return AgentScore(meta, confidence, reason)
    }

    private fun normalize(text: String): String {
        if (text.isBlank()) return ""
        return text
            .lowercase(Locale.getDefault())
            .replace(Regex("[`~!@#$%^&*()\\-_=+\\[\\]{}\\\\|;:'\",.<>/?，。！？、；：（）【】《》]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

val DEFAULT_CONTEXT_EXIT_KEYWORDS = setOf(
    "退出",
    "exit",
    "结束",
    "不用了",
    "停止",
    "stop",
    "cancel"
)

object BuiltinAgents {
    val CHAT = AgentMeta(
        name = "chat",
        displayName = "通用对话",
        description = "默认通用问答",
        keywords = setOf("聊聊", "解释", "介绍", "问答"),
        semanticHints = setOf("chat", "talk"),
        priority = 120,
        requiresAuth = false,
        ttlMinutes = 1
    )

    val RAG = AgentMeta(
        name = "rag",
        displayName = "知识库检索",
        description = "基于文档知识库进行检索问答",
        keywords = setOf("文献", "文档", "资料", "知识库", "检索", "根据文档", "资料中", "rag"),
        regexPatterns = listOf(
            Regex("""(根据|结合).*(文档|文献|资料|知识库)""", RegexOption.IGNORE_CASE),
            Regex("""(文献|文档|资料).*(提到|说到|写到|指出)""", RegexOption.IGNORE_CASE),
            Regex("""(检索|查询).*(知识库|文献|文档)""", RegexOption.IGNORE_CASE)
        ),
        semanticHints = setOf("知识库", "文献", "检索"),
        priority = 20,
        requiresAuth = true,
        ttlMinutes = 5
    )

    val SEARCH = AgentMeta(
        name = "search",
        displayName = "联网搜索",
        description = "进行联网深度搜索并综合回答",
        keywords = setOf("联网", "搜索", "查一下", "查找", "网上", "最新", "新闻", "tavily"),
        regexPatterns = listOf(
            Regex("""(帮我)?(联网|上网|网上).*(搜索|查|找)""", RegexOption.IGNORE_CASE),
            Regex("""(最新|今天|近期).*(消息|进展|新闻|动态)""", RegexOption.IGNORE_CASE),
            Regex("""(搜索|查找|查询).*(一下|一下子|看看)?""", RegexOption.IGNORE_CASE)
        ),
        semanticHints = setOf("联网搜索", "deep search", "search"),
        priority = 30,
        requiresAuth = true,
        ttlMinutes = 3
    )

    val DOCUMENT = AgentMeta(
        name = "document",
        displayName = "文档管理",
        description = "上传、查看、删除文档",
        keywords = setOf("上传", "删除文档", "文档列表", "我的文档", "文件管理", "知识库文件"),
        regexPatterns = listOf(
            Regex("""(上传|添加).*(文档|文件|资料)""", RegexOption.IGNORE_CASE),
            Regex("""(删除|移除).*(文档|文件|资料)""", RegexOption.IGNORE_CASE),
            Regex("""(查看|打开).*(文档|文件)""", RegexOption.IGNORE_CASE)
        ),
        semanticHints = setOf("document", "upload file"),
        priority = 40,
        requiresAuth = true,
        ttlMinutes = 3
    )

    val SETTINGS = AgentMeta(
        name = "settings",
        displayName = "设置助手",
        description = "主题、密码、账号偏好设置",
        keywords = setOf("设置", "主题", "密码", "偏好", "账号", "个人资料", "深色模式"),
        regexPatterns = listOf(
            Regex("""(打开|进入|去).*(设置|偏好|主题|账号)""", RegexOption.IGNORE_CASE),
            Regex("""(修改|更改).*(密码|主题|头像|昵称|资料)""", RegexOption.IGNORE_CASE)
        ),
        semanticHints = setOf("settings", "preference"),
        priority = 50,
        requiresAuth = true,
        ttlMinutes = 3
    )

    val EMAIL = AgentMeta(
        name = "email",
        displayName = "邮件助手",
        description = "发送邮件、查询发送历史（后端直发）",
        keywords = setOf("发邮件", "发送邮件", "邮箱", "email", "mail", "邮件通知", "给我发一封", "邮件历史", "发送历史", "邮件记录"),
        regexPatterns = listOf(
            Regex("""(发|发送).*(邮件|邮箱|email|mail)""", RegexOption.IGNORE_CASE),
            Regex("""(邮件|邮箱).*(历史|记录)""", RegexOption.IGNORE_CASE),
            Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""", RegexOption.IGNORE_CASE)
        ),
        semanticHints = setOf("邮件", "发信", "send email", "history"),
        priority = 25,
        requiresAuth = false,
        ttlMinutes = 3
    )

    val ALL: List<AgentMeta> = listOf(
        UnitConversionAgent.META,
        EMAIL,
        RAG,
        SEARCH,
        DOCUMENT,
        SETTINGS
    )
}