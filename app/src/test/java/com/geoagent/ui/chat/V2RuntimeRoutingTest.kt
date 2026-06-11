package com.geoagent.ui.chat

import com.geoagent.agent.v2.V2AgentId
import com.geoagent.agent.RouteDisposition
import com.geoagent.agent.RouteResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2RuntimeRoutingTest {

    @Test
    fun nonEmailHistoryTextStillRoutesThroughV2Runtime() {
        assertTrue(V2AgentId.RESEARCH.shouldAnswerWithV2Runtime("分析地球历史资料"))
        assertTrue(V2AgentId.RAG.shouldAnswerWithV2Runtime("根据知识库回答沉积历史问题"))
        assertTrue(V2AgentId.SEARCH.shouldAnswerWithV2Runtime("搜索地质历史最新资料"))
    }

    @Test
    fun emailHistoryTextKeepsLegacyHistoryLookup() {
        assertFalse(V2AgentId.EMAIL.shouldAnswerWithV2Runtime("查看已发送邮件记录"))
        assertFalse(V2AgentId.EMAIL.shouldAnswerWithV2Runtime("email history"))
    }

    @Test
    fun normalEmailSendRoutesThroughV2Runtime() {
        assertTrue(V2AgentId.EMAIL.shouldAnswerWithV2Runtime("给 test@example.com 发送邮件 主题：进度 内容：完成"))
    }

    @Test
    fun removedUnitConversionRouteNoLongerBypassesV2() {
        val route = RouteResult(
            agentName = "unit_conversion",
            confidence = 0.9f,
            disposition = RouteDisposition.DIRECT,
            reason = "unit"
        )

        assertFalse(route.shouldUseLocalDirectAgentBeforeV2())
    }

    @Test
    fun nonConversionDirectRouteDoesNotBypassV2Runtime() {
        val route = RouteResult(
            agentName = "SEARCH",
            confidence = 0.9f,
            disposition = RouteDisposition.DIRECT,
            reason = "search"
        )

        assertFalse(route.shouldUseLocalDirectAgentBeforeV2())
    }
}
