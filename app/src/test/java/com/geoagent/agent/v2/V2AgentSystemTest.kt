package com.geoagent.agent.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class V2AgentSystemTest {

    private val tools = V2ToolRegistry.production()
    private val agents = V2AgentRegistry(tools)
    private val executors = V2AgentExecutorRegistry.production(agents)
    private val orchestrator = V2Orchestrator(agents, tools, executors)

    @Test
    fun productionRegistryContainsAllRequestedVirtualAgents() {
        val ids = agents.all().map { it.id }.toSet()

        assertEquals(V2AgentId.entries.toSet(), ids)
        assertEquals(8, ids.size)
    }

    @Test
    fun productionRegistryContainsCoreToolBackings() {
        val ids = tools.all().map { it.id }.toSet()

        assertTrue(ids.contains(V2ToolId.ONE_LLM))
        assertTrue(ids.contains(V2ToolId.TAVILY_SEARCH))
        assertTrue(ids.contains(V2ToolId.RAG_RETRIEVAL))
        assertTrue(ids.contains(V2ToolId.ROOM_MEMORY))
        assertTrue(ids.contains(V2ToolId.CALENDAR_CONTRACT))
        assertTrue(ids.contains(V2ToolId.JAVA_MAIL_QQ_SMTP))
        assertTrue(ids.contains(V2ToolId.PDF_PARSER))
        assertTrue(ids.contains(V2ToolId.DOCUMENT_WRITER))
    }

    @Test
    fun everyProductionAgentHasRegisteredTools() {
        agents.all().forEach { agent ->
            assertTrue("${agent.id} has missing tools", tools.containsAll(agent.requiredTools))
            assertTrue("${agent.id} should use the single LLM", agent.requiredTools.contains(V2ToolId.ONE_LLM))
            assertTrue("${agent.id} should support streaming", agent.requiredTools.contains(V2ToolId.FLOW_STREAMING))
        }
    }

    @Test
    fun productionExecutorRegistryContainsAllVirtualAgents() {
        val ids = executors.all().map { it.agentId }.toSet()

        assertEquals(V2AgentId.entries.toSet(), ids)
        assertTrue(executors.containsAll(V2AgentId.entries.toSet()))
    }

    @Test
    fun routerRoutesRepresentativeRequestsToTargetAgents() {
        assertEquals(V2AgentId.SEARCH, agents.route("帮我联网搜索最新地质新闻")?.agent?.id)
        assertEquals(V2AgentId.RAG, agents.route("根据知识库文档回答这个问题")?.agent?.id)
        assertEquals(V2AgentId.EMAIL, agents.route("给 test@example.com 发送邮件")?.agent?.id)
        assertEquals(V2AgentId.EMAIL, agents.route("把这些新闻 发送给1149201272@qq.com")?.agent?.id)
        assertEquals(V2AgentId.SCHEDULE, agents.route("安排明天下午复习地质构造")?.agent?.id)
        assertEquals(V2AgentId.TASK, agents.route("创建一个待办清单：完成地质报告")?.agent?.id)
        assertEquals(V2AgentId.PDF, agents.route("解析 sample.pdf 并总结")?.agent?.id)
        assertEquals(V2AgentId.FILE, agents.route("读取 report.txt 文件")?.agent?.id)
        assertEquals(V2AgentId.RESEARCH, agents.route("分析火山监测研究进展")?.agent?.id)
    }

    @Test
    fun orchestratorEmitsAllPipelineStagesInOrder() {
        val result = orchestrator.orchestrate("同时搜索最新地质新闻，并且给 test@example.com 发送邮件")

        assertEquals(
            listOf(
                V2PipelineStage.MASTER,
                V2PipelineStage.PLANNER,
                V2PipelineStage.ROUTER,
                V2PipelineStage.AGENTS,
                V2PipelineStage.REFLECTION,
                V2PipelineStage.JUDGE,
                V2PipelineStage.AGGREGATOR
            ),
            result.events.map { it.stage }
        )
        assertTrue(result.plan.parallelizable)
        assertTrue(result.plan.tasks.size >= 2)
        assertTrue(result.runs.any { it.agentId == V2AgentId.SEARCH })
        assertTrue(result.runs.any { it.agentId == V2AgentId.EMAIL })
        assertTrue(result.runs.all { it.status == V2AgentRunStatus.COMPLETED })
        assertTrue(result.runs.all { it.output.isNotBlank() })
        assertTrue(result.judgement.passed)
    }

    @Test
    fun publicPlanMatchesOrchestratedPlan() {
        val input = "同时搜索最新地质新闻，并且给 test@example.com 发送邮件"

        val plan = orchestrator.plan(input)
        val result = orchestrator.orchestrate(input)

        assertEquals(result.plan, plan)
        assertTrue(plan.parallelizable)
        assertEquals(listOf(V2AgentId.SEARCH, V2AgentId.EMAIL), plan.tasks.map { it.agentId })
    }

    @Test
    fun orchestratorAgentStageExecutesRegisteredExecutor() {
        val result = orchestrator.orchestrate("安排明天下午复习地质构造")
        val run = result.runs.single()

        assertEquals(V2AgentId.SCHEDULE, run.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, run.status)
        assertTrue(run.toolIds.contains(V2ToolId.DOCUMENT_WRITER))
        assertTrue(run.toolIds.contains(V2ToolId.CALENDAR_CONTRACT))
        assertTrue(run.output.contains("Schedule Agent"))
        assertTrue(result.answer.contains("Schedule Agent prepared execution"))
    }

    @Test
    fun typedExecutorsPrepareSystemArtifacts() {
        val email = orchestrator.orchestrate("给 test@example.com 发送邮件 主题：进度 内容：今天完成V2执行层")
            .runs
            .single()
        val schedule = orchestrator.orchestrate("安排明天下午项目复习")
            .runs
            .single()
        val task = orchestrator.orchestrate("创建一个待办清单：提交报告")
            .runs
            .single()

        assertEquals(V2AgentId.EMAIL, email.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, email.status)
        assertTrue(email.artifact!!.startsWith("email:"))
        assertTrue(email.artifact.contains("test@example.com"))

        assertEquals(V2AgentId.SCHEDULE, schedule.agentId)
        assertTrue(schedule.artifact!!.startsWith("schedule:"))

        assertEquals(V2AgentId.TASK, task.agentId)
        assertTrue(task.artifact!!.startsWith("task:"))
    }

    @Test
    fun orchestratorFallsBackToResearchForAmbiguousRequest() {
        val result = orchestrator.orchestrate("帮我分析一下这个问题")

        assertFalse(result.plan.tasks.isEmpty())
        assertEquals(V2AgentId.RESEARCH, result.plan.tasks.first().agentId)
        assertTrue(result.judgement.passed)
    }

    @Test
    fun orchestratorStreamsPipelineStages() = runBlocking {
        val events = orchestrator.stream("帮我联网搜索最新地质新闻").toList()

        assertEquals(V2PipelineStage.MASTER, events.first().stage)
        assertEquals(V2PipelineStage.AGGREGATOR, events.last().stage)
        assertEquals(7, events.size)
    }
}
