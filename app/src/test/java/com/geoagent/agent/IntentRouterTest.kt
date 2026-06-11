package com.geoagent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentRouterTest {
    private val router = IntentRouter(BuiltinAgents.ALL)

    @Test
    fun fallback_whenUnitConversionQuery() {
        val result = router.route("3000米等于多少英尺")

        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertNull(result.agentName)
    }

    @Test
    fun fallback_whenMediumConfidenceUnitConversionQuery() {
        val result = router.route("帮我转换一下")

        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertNull(result.agentName)
    }

    @Test
    fun fallback_whenNoMatch() {
        val result = router.route("今天你过得怎么样")

        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertNull(result.agentName)
    }

    @Test
    fun doesNotInheritRemovedUnitConversionContext() {
        val now = 100_000L
        val context = SessionContext(
            activeAgent = "unit_conversion",
            activatedAtMillis = now - 60_000L
        )

        val result = router.route(
            input = "那500米呢",
            sessionContext = context,
            nowMillis = now
        )

        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertNull(result.agentName)
    }

    @Test
    fun unknownRemovedAgentContextDoesNotTriggerExitHandling() {
        val now = 100_000L
        val context = SessionContext(
            activeAgent = "unit_conversion",
            activatedAtMillis = now - 30_000L
        )

        val result = router.route(
            input = "退出",
            sessionContext = context,
            nowMillis = now
        )

        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertNull(result.agentName)
    }

    @Test
    fun fallback_whenDocumentKnowledgeQuery() {
        val result = router.route("根据文献资料回答这个问题")
        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertNull(result.agentName)
    }

    @Test
    fun fallback_whenWebSearchQuery() {
        val result = router.route("帮我联网搜索今天的地质新闻")
        assertEquals(RouteDisposition.FALLBACK, result.disposition)
        assertNull(result.agentName)
    }

    @Test
    fun routeToEmail_whenSendMailQuery() {
        val result = router.route("请给 test@example.com 发邮件，主题：测试，内容：你好")
        assertEquals("v2_email", result.agentName)
        assertEquals(RouteDisposition.DIRECT, result.disposition)
    }

    @Test
    fun routeToEmail_whenMailRequestAlsoMentionsNews() {
        val result = router.route("把这些新闻 发送给1149201272@qq.com")
        assertEquals("v2_email", result.agentName)
        assertEquals(RouteDisposition.DIRECT, result.disposition)
    }

    @Test
    fun routeToEmail_whenHistoryQuery() {
        val result = router.route("查看邮件发送历史")
        assertEquals("v2_email", result.agentName)
        assertEquals(RouteDisposition.DIRECT, result.disposition)
    }
}
