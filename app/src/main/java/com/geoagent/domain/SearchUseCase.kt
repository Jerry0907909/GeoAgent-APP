package com.geoagent.domain

import com.geoagent.data.api.ChatMessage
import com.geoagent.data.api.DeepSeekChatClient
import com.geoagent.model.SearchSource
import com.geoagent.model.TavilySearchResult
import com.geoagent.network.TavilyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchUseCase @Inject constructor(
    private val deepSeekClient: DeepSeekChatClient,
    private val tavilyRepository: TavilyRepository
) {
    fun prepareSearchContext(
        question: String,
        deepSeekApiKey: String,
        tavilyApiKey: String
    ): Flow<SearchUseCaseEvent> = flow {
        emit(SearchUseCaseEvent.Status("智能搜索中…"))
        val decision = judgeSearchRequired(question, deepSeekApiKey).getOrElse { e ->
            emit(SearchUseCaseEvent.Error("联网判断失败：${e.message ?: "未知错误"}"))
            return@flow
        }
        if (!decision) {
            emit(SearchUseCaseEvent.NoSearch)
            return@flow
        }
        emitAll(searchRequiredContext(question, tavilyApiKey))
    }

    fun searchRequiredContext(
        question: String,
        tavilyApiKey: String
    ): Flow<SearchUseCaseEvent> = flow {
        val plan = SearchPromptTools.planSearch(question)
        val queries = plan.queries
        emit(SearchUseCaseEvent.Plan(queries))
        emit(SearchUseCaseEvent.Status("正在联网搜索…"))

        val results = runCatching {
            coroutineScope {
                queries.map { query ->
                    async {
                        tavilyRepository.search(query, tavilyApiKey, maxResults = plan.maxResultsPerQuery).getOrThrow()
                    }
                }.flatMap { it.await() }
                    .let { SearchPromptTools.prepareResults(question, it, plan.maxSources) }
            }
        }.getOrElse { e ->
            emit(SearchUseCaseEvent.Error("联网搜索失败：${e.message ?: "未知错误"}"))
            return@flow
        }

        if (results.isEmpty()) {
            emit(SearchUseCaseEvent.Error("联网搜索未返回可用结果"))
            return@flow
        }

        emit(
            SearchUseCaseEvent.SearchReady(
                SearchContext(
                    enhancedPrompt = buildEnhancedPrompt(question, results),
                    results = results,
                    sources = results.map {
                        SearchSource(
                            title = it.title,
                            url = it.url,
                            content = it.content,
                            publishedDate = it.publishedDate
                        )
                    }
                )
            )
        )
    }

    suspend fun judgeSearchRequired(
        question: String,
        deepSeekApiKey: String
    ): Result<Boolean> = runCatching {
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = SEARCH_DECISION_PROMPT
            ),
            ChatMessage(
                role = "user",
                content = "用户问题：\n$question\n\n只返回 SEARCH_REQUIRED 或 NO_SEARCH。"
            )
        )
        val answer = withTimeout(SEARCH_DECISION_TIMEOUT_MILLIS) {
            deepSeekClient.completeChat(messages, deepSeekApiKey).getOrThrow()
        }
        SearchPromptTools.parseSearchDecision(answer)
            ?: throw IllegalStateException("无法解析联网判断结果：${answer.trim()}")
    }

    fun decomposeQueries(question: String): List<String> =
        SearchPromptTools.decomposeQueries(question)

    fun buildEnhancedPrompt(
        question: String,
        results: List<TavilySearchResult>
    ): String = SearchPromptTools.buildEnhancedPrompt(question, results)

    fun parseSearchDecision(text: String): Boolean? =
        SearchPromptTools.parseSearchDecision(text)

    companion object {
        private const val SEARCH_DECISION_TIMEOUT_MILLIS = 12_000L

        private val SEARCH_DECISION_PROMPT = """
            如果用户的问题涉及：
            最新资讯
            实时新闻
            股票价格
            天气
            互联网内容
            网站信息
            时间敏感问题

            则返回：

            SEARCH_REQUIRED

            否则返回：

            NO_SEARCH
        """.trimIndent()
    }
}

object SearchPromptTools {
    const val DEFAULT_MAX_SOURCES = 6
    const val MAX_ADAPTIVE_SOURCES = 12
    private const val MAX_PROMPT_CONTENT_CHARS = 520
    private const val MAX_RETRY_CONTENT_CHARS = 120
    private const val MAX_FALLBACK_CONTENT_CHARS = 220
    private val navigationPhrases = listOf(
        "无障碍链接",
        "跳转到内容",
        "跳转到导航",
        "跳转到检索",
        "site logo",
        "下一页",
        "上一页",
        "直播 site logo"
    )
    private val excludedGenericChineseNewsHosts = listOf(
        "google.com",
        "news.google.com",
        "www.google.com",
        "www3.nhk.or.jp",
        "voachinese.com",
        "www.voachinese.com"
    )
    private val preferredChineseNewsHosts = listOf(
        "news.cctv.com",
        "www.news.cn",
        "xinhuanet.com",
        "www.xinhuanet.com",
        "people.com.cn",
        "www.people.com.cn",
        "chinanews.com.cn",
        "www.chinanews.com.cn",
        "thepaper.cn",
        "www.thepaper.cn",
        "news.sina.com.cn",
        "news.qq.com",
        "163.com",
        "www.163.com",
        "caixin.com",
        "www.caixin.com",
        "yicai.com",
        "www.yicai.com",
        "jiemian.com",
        "www.jiemian.com",
        "cas.cn",
        "www.cas.cn",
        "igsnrr.cas.cn",
        "www.cgs.gov.cn",
        "cgs.gov.cn",
        "www.cea.gov.cn",
        "cea.gov.cn",
        "mnr.gov.cn",
        "www.mnr.gov.cn",
        "sciencenet.cn",
        "www.sciencenet.cn",
        "eos.org",
        "phys.org",
        "sciencedaily.com",
        "www.sciencedaily.com"
    )

    fun parseSearchDecision(text: String): Boolean? {
        val normalized = text.trim().uppercase()
        return when {
            "SEARCH_REQUIRED" in normalized -> true
            "NO_SEARCH" in normalized -> false
            else -> null
        }
    }

    fun planSearch(question: String): SearchPlan {
        val normalized = question.trim()
        if (normalized.isBlank()) return SearchPlan(emptyList(), maxResultsPerQuery = 3, maxSources = 0)
        if (isGeologyNewsQuestion(normalized)) {
            return SearchPlan(
                queries = listOf(
                    "地质学界 最新 新闻 地球科学 研究进展",
                    "中国地质调查局 地质 科研 最新进展",
                    "中国科学院 地球科学 地质 最新 研究",
                    "地震 火山 矿产 地质灾害 最新 科研 新闻",
                    "geology earth science latest research news"
                ),
                maxResultsPerQuery = 4,
                maxSources = 10
            )
        }
        if (isGeneralChineseNewsQuestion(normalized)) {
            return SearchPlan(
                queries = listOf(
                    "今日中文新闻 最新 国内 国际",
                    "今天中国互联网热点新闻 央视网 新华网",
                    "今日财经 科技 法治 社会 新闻",
                    "今天国际局势 中文 新闻"
                ),
                maxResultsPerQuery = 4,
                maxSources = 12
            )
        }

        val queries = decomposeQueriesInternal(normalized)
        val broadQuery = normalized.length <= 14 || listOf("最近", "最新", "有哪些", "哪些", "汇总", "总结").any { normalized.contains(it) }
        return SearchPlan(
            queries = queries,
            maxResultsPerQuery = if (broadQuery) 5 else 3,
            maxSources = if (broadQuery) 8 else DEFAULT_MAX_SOURCES
        )
    }

    fun decomposeQueries(question: String): List<String> {
        val normalized = question.trim()
        return planSearch(normalized).queries
    }

    private fun decomposeQueriesInternal(normalized: String): List<String> {
        if (normalized.isBlank()) return emptyList()
        val hasCompareIntent = listOf("比较", "对比", "区别", "差异", "vs", "VS").any { normalized.contains(it) }
        if (!hasCompareIntent) return listOf(normalized)

        val subjects = normalized
            .replace("？", "")
            .replace("?", "")
            .split("和", "与", "及", "以及", "vs", "VS")
            .map { part ->
                part.replace("比较", "")
                    .replace("对比", "")
                    .replace("区别", "")
                    .replace("差异", "")
                    .trim()
            }
            .filter { it.length >= 2 }
            .take(2)

        if (subjects.size < 2) return listOf(normalized)

        return subjects.flatMap { subject ->
            listOf("$subject 参数", "$subject 能力")
        }.distinct()
    }

    fun prepareResults(
        question: String,
        results: List<TavilySearchResult>,
        maxSources: Int = planSearch(question).maxSources
    ): List<TavilySearchResult> {
        val limit = maxSources.coerceIn(1, MAX_ADAPTIVE_SOURCES)
        val preferChineseNews = isGeneralChineseNewsQuestion(question) && !isGeologyNewsQuestion(question)
        val cleaned = results
            .mapNotNull { result -> result.cleanedOrNull(preferChineseNews) }
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<TavilySearchResult> { chineseNewsDomainScore(it.url) }
                    .thenByDescending { chineseCharCount(it.title + it.content) }
            )
            .take(limit)

        if (cleaned.isNotEmpty()) return cleaned

        return results
            .mapNotNull { result -> result.cleanedOrNull(requireChineseNews = false) }
            .distinctBy { it.url }
            .take(limit)
    }

    fun buildEnhancedPrompt(
        question: String,
        results: List<TavilySearchResult>
    ): String = buildString {
        appendLine("你正在执行智能搜索的综合生成。")
        appendLine("请只依据下列中文互联网搜索结果，用简体中文整合回答用户问题，并尽量覆盖全部可用来源。")
        appendLine()
        appendLine("用户问题：$question")
        appendLine()
        appendLine("搜索结果：")
        results.forEachIndexed { index, result ->
            appendLine("[${index + 1}] ${result.title}")
            appendLine("摘要：${result.content.compact(MAX_PROMPT_CONTENT_CHARS)}")
            result.publishedDate?.takeIf { it.isNotBlank() }?.let { appendLine("日期：$it") }
            appendLine("链接：${result.url}")
        }
        appendLine()
        appendLine("输出要求：")
        appendLine("1. 先用一句话给出总览，再按要点整合主要事实；如果有 8 个以上来源，至少输出 8 条要点。")
        appendLine("2. 每条要点优先综合多个来源，不要只使用前几条结果；来源之间信息重复时可以合并，但不能忽略不同主题。")
        appendLine("3. 不要逐条粘贴搜索结果，不要输出网页导航、站点菜单、广告词或页面结构文字。")
        appendLine("4. 每条要点用自己的话概括，并用 [1]、[2] 这样的编号标注依据；同一要点可标注多个来源。")
        appendLine("5. 不要在回答末尾输出 Sources、来源列表或裸链接，来源由界面组件展示。")
        appendLine("6. 如果材料不足以确认具体事实，直接说明信息不足，不要编造。")
    }

    fun buildRetryPrompt(
        question: String,
        results: List<TavilySearchResult>
    ): String = buildString {
        appendLine("请基于下列中文搜索线索，用简体中文整合回答。只输出综合后的新闻要点，不复述原文。")
        appendLine()
        appendLine("问题：$question")
        appendLine()
        results.forEachIndexed { index, result ->
            appendLine("[${index + 1}] ${result.title}：${result.content.compact(MAX_RETRY_CONTENT_CHARS)}")
        }
    }

    fun buildSearchPlanThinking(queries: List<String>): String {
        if (queries.isEmpty()) return "正在联网检索相关网页。\n"
        return buildString {
            appendLine("正在联网检索相关网页。")
            queries.take(6).forEachIndexed { index, query ->
                appendLine("${index + 1}. $query")
            }
        }
    }

    fun buildSearchReadyThinking(results: List<TavilySearchResult>): String {
        if (results.isEmpty()) return "未检索到可用网页，准备直接回答。\n"
        return buildString {
            appendLine("已检索到 ${results.size} 个网页，正在整理来源。")
            results.take(MAX_ADAPTIVE_SOURCES).forEachIndexed { index, result ->
                val date = result.publishedDate?.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()
                appendLine("[${index + 1}] ${result.title}（${result.url.toDomain()}$date）")
            }
            appendLine("接下来会根据这些来源综合回答，并在正文中标注来源序号。")
        }
    }

    fun buildFallbackAnswer(
        question: String,
        results: List<TavilySearchResult>
    ): String = buildString {
        appendLine("已根据联网搜索结果整理如下：")
        appendLine()
        appendLine("问题：$question")
        appendLine()
        results.forEachIndexed { index, result ->
            appendLine("${index + 1}. ${result.title}")
            appendLine(result.content.compact(MAX_FALLBACK_CONTENT_CHARS))
            appendLine("来源：${result.url}")
            if (index != results.lastIndex) appendLine()
        }
    }

    private fun String.compact(maxChars: Int): String {
        val compacted = cleanText(this)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
        return if (compacted.length <= maxChars) compacted else compacted.take(maxChars).trimEnd() + "..."
    }

    private fun TavilySearchResult.cleanedOrNull(requireChineseNews: Boolean): TavilySearchResult? {
        val cleanedTitle = cleanText(title).joinToString(" ").trim()
        val cleanedContent = cleanText(content).joinToString(" ").trim()
        if (cleanedTitle.isBlank() || cleanedContent.length < 24 || url.isBlank()) return null

        val lowerUrl = url.lowercase()
        val combined = "$cleanedTitle $cleanedContent"
        val navigationHits = navigationPhrases.count { combined.contains(it, ignoreCase = true) }
        if (navigationHits >= 2) return null
        if (requireChineseNews && excludedGenericChineseNewsHosts.any { lowerUrl.contains(it) }) return null
        if (requireChineseNews && chineseCharCount(combined) < 12) return null

        return copy(title = cleanedTitle, content = cleanedContent)
    }

    private fun cleanText(text: String): List<String> =
        text.trim()
            .replace(Regex("""Image\s+\d+\s*:?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[#*_`>]+"""), "")
            .lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() && navigationPhrases.none { phrase ->
                    line.contains(phrase, ignoreCase = true)
                }
            }
            .toList()

    private fun isGeneralChineseNewsQuestion(text: String): Boolean {
        val hasNewsIntent = listOf("新闻", "资讯", "消息", "热点", "要闻").any { text.contains(it) }
        val hasFreshIntent = listOf("今天", "今日", "最新", "近期", "现在").any { text.contains(it) }
        val asksGeneralNews = Regex("""(有|有哪些|看看|汇总|总结).*(新闻|资讯|消息|热点|要闻)""").containsMatchIn(text)
        return hasNewsIntent && (hasFreshIntent || asksGeneralNews)
    }

    private fun isGeologyNewsQuestion(text: String): Boolean {
        val hasGeology = listOf("地质", "地学", "地球科学", "矿产", "地震", "火山", "地质灾害").any { text.contains(it) } ||
            text.contains("geology", ignoreCase = true) ||
            text.contains("earth science", ignoreCase = true)
        val hasNewsIntent = listOf("新闻", "资讯", "消息", "动态", "进展", "最近", "最新").any { text.contains(it) }
        return hasGeology && hasNewsIntent
    }

    private fun chineseNewsDomainScore(url: String): Int {
        val lowerUrl = url.lowercase()
        return when {
            preferredChineseNewsHosts.any { lowerUrl.contains(it) } -> 2
            excludedGenericChineseNewsHosts.any { lowerUrl.contains(it) } -> 0
            else -> 1
        }
    }

    private fun chineseCharCount(text: String): Int =
        text.count { it in '\u4e00'..'\u9fff' }

    private fun String.toDomain(): String =
        removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
}

data class SearchPlan(
    val queries: List<String>,
    val maxResultsPerQuery: Int,
    val maxSources: Int
)

sealed class SearchUseCaseEvent {
    data class Status(val message: String) : SearchUseCaseEvent()
    data class Plan(val queries: List<String>) : SearchUseCaseEvent()
    data class SearchReady(val context: SearchContext) : SearchUseCaseEvent()
    data object NoSearch : SearchUseCaseEvent()
    data class Error(val message: String) : SearchUseCaseEvent()
}

data class SearchContext(
    val enhancedPrompt: String,
    val results: List<TavilySearchResult>,
    val sources: List<SearchSource>
)
