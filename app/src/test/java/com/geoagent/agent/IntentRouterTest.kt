package com.geoagent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {
    private val router = IntentRouter(BuiltinAgents.ALL)

    @Test
    fun directRoute_whenHighConfidenceNaturalLanguage() {
        val result = router.route("3000米等于多少英尺")

        assertEquals(RouteDisposition.DIRECT, result.disposition)
        assertEquals(UnitConversionAgent.META.name, result.agentName)
        assertTrue(result.confidence >= 0.8f)
    }

    @Test
    fun confirmRoute_whenMediumConfidenceNaturalLanguage() {
        val result = router.route("帮我转换一下")

        assertEquals(RouteDisposition.CONFIRM, result.disposition)
        assertEquals(UnitConversionAgent.META.name, result.agentName)
        assertTrue(result.confidence in 0.6f..0.8f)
    }

    @Test
    fun fallback_whenNoMatch() {
        val result = router.route("今天你过得怎么样")

        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertNull(result.agentName)
    }

    @Test
    fun inheritContext_whenWithinTtl() {
        val now = 100_000L
        val context = SessionContext(
            activeAgent = UnitConversionAgent.META.name,
            activatedAtMillis = now - 60_000L
        )

        val result = router.route(
            input = "那500米呢",
            sessionContext = context,
            nowMillis = now
        )

        assertEquals(RouteDisposition.DIRECT, result.disposition)
        assertEquals(UnitConversionAgent.META.name, result.agentName)
        assertTrue(result.reason.startsWith("context_inherit"))
    }

    @Test
    fun clearContext_whenExitKeywordReceived() {
        val now = 100_000L
        val context = SessionContext(
            activeAgent = UnitConversionAgent.META.name,
            activatedAtMillis = now - 30_000L
        )

        val result = router.route(
            input = "退出",
            sessionContext = context,
            nowMillis = now
        )

        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertTrue(result.shouldClearContext)
    }

    @Test
    fun routeToRag_whenDocumentKnowledgeQuery() {
        val result = router.route("根据文献资料回答这个问题")
        assertEquals(BuiltinAgents.RAG.name, result.agentName)
        assertEquals(RouteDisposition.DIRECT, result.disposition)
    }

    @Test
    fun routeToSearch_whenWebSearchQuery() {
        val result = router.route("帮我联网搜索今天的地质新闻")
        assertEquals(BuiltinAgents.SEARCH.name, result.agentName)
        assertEquals(RouteDisposition.DIRECT, result.disposition)
    }

    @Test
    fun routeToEmail_whenSendMailQuery() {
        val result = router.route("请给 test@example.com 发邮件，主题：测试，内容：你好")
        assertEquals(BuiltinAgents.EMAIL.name, result.agentName)
        assertEquals(RouteDisposition.DIRECT, result.disposition)
    }

    @Test
    fun routeToEmail_whenHistoryQuery() {
        val result = router.route("查看邮件发送历史")
        assertEquals(BuiltinAgents.EMAIL.name, result.agentName)
        assertTrue(result.disposition == RouteDisposition.DIRECT || result.disposition == RouteDisposition.CONFIRM)
    }
}
