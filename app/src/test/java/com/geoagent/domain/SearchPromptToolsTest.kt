package com.geoagent.domain

import com.geoagent.model.TavilySearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPromptToolsTest {

    @Test
    fun parseSearchDecisionRecognizesRequired() {
        assertEquals(true, SearchPromptTools.parseSearchDecision("SEARCH_REQUIRED"))
    }

    @Test
    fun parseSearchDecisionRecognizesNoSearch() {
        assertEquals(false, SearchPromptTools.parseSearchDecision("NO_SEARCH"))
    }

    @Test
    fun decomposeQueriesSplitsCompareIntent() {
        val queries = SearchPromptTools.decomposeQueries("比较 GPT-5.5 和 DeepSeek-R2")

        assertEquals(
            listOf(
                "GPT-5.5 参数",
                "GPT-5.5 能力",
                "DeepSeek-R2 参数",
                "DeepSeek-R2 能力"
            ),
            queries
        )
    }

    @Test
    fun decomposeQueriesExpandsGeneralChineseNewsIntent() {
        val queries = SearchPromptTools.decomposeQueries("今天有哪些新闻")

        assertTrue(queries.size >= 3)
        assertTrue(queries.any { it.contains("国内") && it.contains("国际") })
        assertTrue(queries.any { it.contains("科技") })
    }

    @Test
    fun decomposeQueriesExpandsGeologyNewsIntent() {
        val queries = SearchPromptTools.decomposeQueries("地质学界最近有哪些新闻")

        assertTrue(queries.size >= 4)
        assertTrue(queries.any { it.contains("地质学") || it.contains("地球科学") })
        assertTrue(queries.any { it.contains("地震") || it.contains("火山") || it.contains("矿产") })
        assertTrue(queries.any { it.contains("geology", ignoreCase = true) })
    }

    @Test
    fun prepareResultsFiltersNavigationNoiseForChineseNews() {
        val results = SearchPromptTools.prepareResults(
            question = "今天有哪些新闻",
            results = listOf(
                TavilySearchResult(
                    title = "美国相关新闻- 美国之音中文网",
                    url = "https://www.voachinese.com/a/example",
                    content = "无障碍链接 跳转到内容 跳转到导航 site logo 下一页 美国 中国时间"
                ),
                TavilySearchResult(
                    title = "国内联播快讯",
                    url = "https://news.cctv.com/2026/06/07/example.shtml",
                    content = "央视网消息：今天国内多地发布最新政策，科技、民生、交通等领域均有新进展。"
                )
            )
        )

        assertEquals(1, results.size)
        assertEquals("国内联播快讯", results.first().title)
        assertTrue(results.first().content.contains("央视网消息"))
    }

    @Test
    fun prepareResultsKeepsAdaptiveSourceCount() {
        val input = (1..9).map { index ->
            TavilySearchResult(
                title = "地质新闻 $index",
                url = "https://news.cctv.com/2026/06/07/geology-$index.shtml",
                content = "地质学界发布第${index}项最新研究进展，涉及地球科学、矿产资源、地震活动和环境变化。"
            )
        }

        val results = SearchPromptTools.prepareResults("地质学界最近有哪些新闻", input)

        assertEquals(9, results.size)
    }

    @Test
    fun prepareResultsKeepsInternationalGeologySources() {
        val results = SearchPromptTools.prepareResults(
            question = "地质学界最近有哪些新闻",
            results = listOf(
                TavilySearchResult(
                    title = "Earth science researchers report new seismic findings",
                    url = "https://eos.org/research-spotlights/seismic-findings",
                    content = "Researchers reported new geology and earth science findings about seismic activity and fault systems."
                ),
                TavilySearchResult(
                    title = "中国科学院地质研究新进展",
                    url = "https://www.cas.cn/cm/202606/t20260607_123456.shtml",
                    content = "中国科学院相关团队发布地球科学和地质研究新进展，涉及构造活动与资源环境。"
                )
            )
        )

        assertEquals(2, results.size)
        assertTrue(results.any { it.url.contains("eos.org") })
        assertTrue(results.any { it.url.contains("cas.cn") })
    }

    @Test
    fun buildEnhancedPromptIncludesSourcesAndQuestion() {
        val prompt = SearchPromptTools.buildEnhancedPrompt(
            question = "今天北京天气如何？",
            results = listOf(
                TavilySearchResult(
                    title = "天气预报",
                    url = "https://example.com/weather",
                    content = "北京今日多云。"
                )
            )
        )

        assertTrue(prompt.contains("[1] 天气预报"))
        assertTrue(prompt.contains("摘要：北京今日多云。"))
        assertTrue(prompt.contains("链接：https://example.com/weather"))
        assertTrue(prompt.contains("今天北京天气如何？"))
        assertTrue(prompt.contains("只依据下列中文互联网搜索结果"))
        assertTrue(prompt.contains("不要逐条粘贴搜索结果"))
    }

    @Test
    fun buildEnhancedPromptCompactsLongSearchContent() {
        val longContent = "新闻内容 ".repeat(200)

        val prompt = SearchPromptTools.buildEnhancedPrompt(
            question = "今天有哪些新闻？",
            results = listOf(
                TavilySearchResult(
                    title = "新闻摘要",
                    url = "https://example.com/news",
                    content = longContent
                )
            )
        )

        assertTrue(prompt.length < longContent.length)
        assertTrue(prompt.contains("..."))
        assertTrue(prompt.contains("https://example.com/news"))
    }

    @Test
    fun buildFallbackAnswerIncludesSearchResultsAndSources() {
        val answer = SearchPromptTools.buildFallbackAnswer(
            question = "今天有哪些新闻？",
            results = listOf(
                TavilySearchResult(
                    title = "新闻摘要",
                    url = "https://example.com/news",
                    content = "第一条新闻摘要。"
                )
            )
        )

        assertTrue(answer.contains("已根据联网搜索结果整理如下"))
        assertTrue(answer.contains("今天有哪些新闻？"))
        assertTrue(answer.contains("新闻摘要"))
        assertTrue(answer.contains("来源：https://example.com/news"))
    }

    @Test
    fun buildRetryPromptIsShortAndAsksForSynthesis() {
        val prompt = SearchPromptTools.buildRetryPrompt(
            question = "今天有哪些新闻？",
            results = listOf(
                TavilySearchResult(
                    title = "新闻摘要",
                    url = "https://example.com/news",
                    content = "新闻内容 ".repeat(200)
                )
            )
        )

        assertTrue(prompt.contains("只输出综合后的新闻要点"))
        assertTrue(prompt.length < 500)
    }
}
