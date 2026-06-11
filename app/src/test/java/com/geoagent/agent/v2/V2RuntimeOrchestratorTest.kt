package com.geoagent.agent.v2

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2RuntimeOrchestratorTest {

    private val tools = V2ToolRegistry.production()
    private val agents = V2AgentRegistry(tools)
    private val gateway = FakeRuntimeGateway()
    private val executors = V2RuntimeAgentExecutorRegistry.production(agents, gateway)
    private val orchestrator = V2RuntimeOrchestrator(
        agentRegistry = agents,
        toolRegistry = tools,
        executorRegistry = executors,
        planner = V2Orchestrator(agents, tools)
    )

    @Test
    fun runtimeRegistryContainsAllVirtualAgents() {
        assertTrue(executors.containsAll(V2AgentId.entries.toSet()))
        assertEquals(V2AgentId.entries.size, executors.all().size)
    }

    @Test
    fun searchAgentUsesTavilyRuntimeGateway() = runBlocking {
        val result = orchestrator.orchestrate("帮我联网搜索最新地质新闻")

        assertEquals(V2AgentId.SEARCH, result.runs.single().agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, result.runs.single().status)
        assertEquals(1, gateway.searchCalls)
        assertEquals(1, gateway.llmCalls)
        assertTrue(result.runs.single().output.contains("LLM-SEARCH"))
        assertTrue(result.runs.single().artifact!!.startsWith("search:"))
        assertEquals("1", result.runs.single().artifact!!.v2ArtifactString("sources"))
    }

    @Test
    fun runtimeExecutesParallelPlanWithMultipleAgents() = runBlocking {
        val result = orchestrator.orchestrate("同时帮我联网搜索最新地质新闻，并且给 test@example.com 发送邮件")

        assertTrue(result.plan.parallelizable)
        assertEquals(listOf(V2AgentId.SEARCH, V2AgentId.EMAIL), result.runs.map { it.agentId })
        assertEquals(1, gateway.searchCalls)
        assertEquals(1, gateway.emailCalls)
        assertEquals(2, gateway.llmCalls)
        assertTrue(result.answer.contains("parallel"))
        assertTrue(result.judgement.passed)
    }

    @Test
    fun emailAgentSendsPreviousAssistantContentWhenUserReferencesAboveContent() = runBlocking {
        gateway.nextEmailDecisionJson = """
            {
              "to": "1149201272@qq.com",
              "subject": "来自 GeoAgent 的邮件",
              "content": "NBA 总决赛战况：雷霆与步行者系列赛仍在进行，核心球员表现突出。",
              "needs_input": false,
              "reason": "用户要求发送上述内容"
            }
        """.trimIndent()

        val result = orchestrator.orchestrate(
            V2RuntimeRequest(
                input = "把上述内容 发送给1149201272@qq.com",
                history = listOf(
                    V2RuntimeHistoryMessage(
                        role = "assistant",
                        content = "NBA 总决赛战况：雷霆与步行者系列赛仍在进行，核心球员表现突出。"
                    )
                )
            )
        )
        val run = result.runs.single()

        assertEquals(V2AgentId.EMAIL, run.agentId)
        assertEquals("NBA 总决赛战况：雷霆与步行者系列赛仍在进行，核心球员表现突出。", gateway.lastEmailRequest?.content)
        assertEquals("1149201272@qq.com", gateway.lastEmailRequest?.to)
        assertTrue(run.output.contains("已经将NBA 总决赛战况"))
        assertTrue(run.output.contains("发送给1149201272@qq.com✅"))
    }

    @Test
    fun emailAgentSendsPreviousTurnWhenAiDecisionWouldAskForInput() = runBlocking {
        gateway.nextEmailDecisionJson = """
            {
              "to": "",
              "subject": "",
              "content": "",
              "needs_input": true,
              "reason": "误判为缺少正文"
            }
        """.trimIndent()

        val result = orchestrator.orchestrate(
            V2RuntimeRequest(
                input = "将当前内容发送给1149201272@qq.com",
                history = listOf(
                    V2RuntimeHistoryMessage(
                        role = "user",
                        content = "今天有哪些热点新闻？"
                    ),
                    V2RuntimeHistoryMessage(
                        role = "assistant",
                        content = "🔥 社会热点与奇闻趣事：四姑娘山出现“鸿运当头”奇观；网传浙江一地要热上50℃已被证实为不实信息。"
                    )
                )
            )
        )
        val run = result.runs.single()

        assertEquals(V2AgentId.EMAIL, run.agentId)
        assertEquals(
            "用户：今天有哪些热点新闻？\n\nGeoAgent：🔥 社会热点与奇闻趣事：四姑娘山出现“鸿运当头”奇观；网传浙江一地要热上50℃已被证实为不实信息。",
            gateway.lastEmailRequest?.content
        )
        assertEquals("1149201272@qq.com", gateway.lastEmailRequest?.to)
        assertFalse(gateway.lastEmailRequest?.content.orEmpty().contains("将当前内容发送给"))
        assertEquals(0, gateway.llmCalls)
    }

    @Test
    fun emailAgentUsesPreviousTurnForRecipientCorrection() = runBlocking {
        gateway.nextEmailDecisionJson = """
            {
              "to": "1149201272@qq.com",
              "subject": "来自 GeoAgent 的邮件",
              "content": "用户：Mercedes-Benz 如何\n\nGeoAgent：哈哈，收到你的“Mercedes-Benz”啦！如果你想了解藏在奔驰背后的石头、矿产或者地球的秘密，随时来找我呀！",
              "needs_input": false,
              "reason": "用户修正收件人并沿用上一轮内容"
            }
        """.trimIndent()

        val result = orchestrator.orchestrate(
            V2RuntimeRequest(
                input = "发错了 是发给1149201272@qq.com",
                history = listOf(
                    V2RuntimeHistoryMessage(
                        role = "user",
                        content = "Mercedes-Benz 如何"
                    ),
                    V2RuntimeHistoryMessage(
                        role = "assistant",
                        content = "哈哈，收到你的“Mercedes-Benz”啦！如果你想了解藏在奔驰背后的石头、矿产或者地球的秘密，随时来找我呀！"
                    )
                )
            )
        )
        val run = result.runs.single()

        assertEquals(V2AgentId.EMAIL, run.agentId)
        assertEquals("1149201272@qq.com", gateway.lastEmailRequest?.to)
        assertTrue(gateway.lastEmailRequest?.content.orEmpty().contains("用户：Mercedes-Benz 如何"))
        assertTrue(gateway.lastEmailRequest?.content.orEmpty().contains("GeoAgent：哈哈，收到你的"))
        assertFalse(gateway.lastEmailRequest?.content.orEmpty().contains("发错了"))
        assertEquals(0, gateway.llmCalls)
    }

    @Test
    fun ragAgentRetrievesChunksAndSynthesizesWithOneLlm() = runBlocking {
        val result = orchestrator.orchestrate("根据知识库文档回答盆地沉积问题")
        val run = result.runs.single()

        assertEquals(V2AgentId.RAG, run.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, run.status)
        assertEquals(1, gateway.ragCalls)
        assertEquals(1, gateway.llmCalls)
        assertTrue(run.artifact!!.startsWith("rag:"))
        assertEquals("1", run.artifact.v2ArtifactString("chunks"))
        assertTrue(run.output.contains("LLM-RAG"))
    }

    @Test
    fun researchAgentCombinesSearchRagAndOneLlm() = runBlocking {
        val result = orchestrator.orchestrate("分析火山监测研究进展")
        val run = result.runs.single()

        assertEquals(V2AgentId.RESEARCH, run.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, run.status)
        assertEquals(1, gateway.searchCalls)
        assertEquals(1, gateway.ragCalls)
        assertEquals(1, gateway.llmCalls)
        assertTrue(run.artifact!!.startsWith("research:"))
        assertEquals("1", run.artifact.v2ArtifactString("webSources"))
        assertEquals("1", run.artifact.v2ArtifactString("chunks"))
    }

    @Test
    fun taskAndScheduleAgentsPersistStructuredTasks() = runBlocking {
        val task = orchestrator.orchestrate("创建一个待办清单：完成地质报告")
            .runs
            .single()
        val schedule = orchestrator.orchestrate("安排明天复习地质构造课程")
            .runs
            .single()

        assertEquals(V2AgentId.TASK, task.agentId)
        assertTrue(task.artifact!!.startsWith("task:"))
        assertEquals(V2AgentId.SCHEDULE, schedule.agentId)
        assertTrue(schedule.artifact!!.startsWith("schedule:"))
        assertEquals(2, gateway.saveTaskCalls)
    }

    @Test
    fun taskAgentListsOpenTasksWithoutCreatingNewTask() = runBlocking {
        val run = orchestrator.orchestrate("查看当前待办任务清单")
            .runs
            .single()

        assertEquals(V2AgentId.TASK, run.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, run.status)
        assertEquals(1, gateway.listTaskCalls)
        assertEquals(0, gateway.saveTaskCalls)
        assertTrue(run.output.contains("完成地质报告"))
        assertTrue(run.artifact!!.startsWith("task_list:"))
        assertEquals("2", run.artifact.v2ArtifactString("count"))
    }

    @Test
    fun researchAndTaskUseCurrentRuntimeExecutors() = runBlocking {
        val research = orchestrator.orchestrate("分析火山监测研究进展")
            .runs
            .single()
        val task = orchestrator.orchestrate("创建一个待办清单：整理 Android 复习计划")
            .runs
            .single()

        assertEquals(V2AgentId.RESEARCH, research.agentId)
        assertTrue(research.artifact!!.startsWith("research:"))
        assertEquals("1", research.artifact.v2ArtifactString("webSources"))
        assertEquals("1", research.artifact.v2ArtifactString("chunks"))

        assertEquals(V2AgentId.TASK, task.agentId)
        assertTrue(task.artifact!!.startsWith("task:"))
    }

    @Test
    fun schedulePreparesCalendarCandidateWhenTimeIsProvided() = runBlocking {
        val fixedClock = FixedRuntimeClock(
            nowMillis = 1_725_257_600_000L,
            zone = TimeZone.getTimeZone("Asia/Shanghai")
        )
        val fixedGateway = FakeRuntimeGateway()
        val fixedExecutors = V2RuntimeAgentExecutorRegistry.production(agents, fixedGateway, fixedClock)
        val fixedOrchestrator = V2RuntimeOrchestrator(
            agentRegistry = agents,
            toolRegistry = tools,
            executorRegistry = fixedExecutors,
            planner = V2Orchestrator(agents, tools)
        )

        val schedule = fixedOrchestrator.orchestrate("安排明天下午3点复习地质构造")
            .runs
            .single()

        assertEquals(V2AgentId.SCHEDULE, schedule.agentId)
        assertEquals("true", schedule.artifact!!.v2ArtifactString("calendar"))
        assertEquals("1725346800000", schedule.artifact.v2ArtifactString("begin"))

        assertEquals(1, fixedGateway.calendarCalls)
    }

    @Test
    fun fileAndPdfAgentsUseDocumentTools() = runBlocking {
        val file = orchestrator.orchestrate("列出 report.txt 文件")
            .runs
            .single()
        val pdf = orchestrator.orchestrate("解析 sample.pdf 并总结")
            .runs
            .single()

        assertEquals(V2AgentId.FILE, file.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, file.status)
        assertTrue(file.artifact!!.startsWith("file:"))
        assertEquals("doc-text", file.artifact.v2ArtifactString("selectedId"))

        assertEquals(V2AgentId.PDF, pdf.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, pdf.status)
        assertEquals(1, gateway.documentTextCalls)
        assertTrue(pdf.artifact!!.startsWith("pdf:"))
        assertEquals("sample.pdf", pdf.artifact.v2ArtifactString("name"))
    }

    @Test
    fun fileAgentDeletesSelectedDocument() = runBlocking {
        val run = orchestrator.orchestrate("删除 report.txt 文件")
            .runs
            .single()

        assertEquals(V2AgentId.FILE, run.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, run.status)
        assertEquals(1, gateway.deleteDocumentCalls)
        assertTrue(run.output.contains("已删除文档：report.txt"))
        assertEquals("delete", run.artifact!!.v2ArtifactString("action"))
        assertEquals("doc-text", run.artifact.v2ArtifactString("selectedId"))
    }

    @Test
    fun fileAgentNeedsTargetBeforeReadOrDelete() = runBlocking {
        val run = orchestrator.orchestrate("删除文件")
            .runs
            .single()

        assertEquals(V2AgentId.FILE, run.agentId)
        assertEquals(V2AgentRunStatus.NEEDS_INPUT, run.status)
        assertEquals(0, gateway.deleteDocumentCalls)
        assertTrue(run.followUps.single().contains("sample.pdf"))
    }

    @Test
    fun scheduleAgentUsesCalendarRuntimeTool() = runBlocking {
        val fixedClock = FixedRuntimeClock(
            nowMillis = 1_725_257_600_000L,
            zone = TimeZone.getTimeZone("Asia/Shanghai")
        )
        val fixedExecutors = V2RuntimeAgentExecutorRegistry.production(agents, gateway, fixedClock)
        val fixedOrchestrator = V2RuntimeOrchestrator(
            agentRegistry = agents,
            toolRegistry = tools,
            executorRegistry = fixedExecutors,
            planner = V2Orchestrator(agents, tools)
        )

        val schedule = fixedOrchestrator.orchestrate("安排明天下午3点项目会议")
            .runs
            .single()

        assertEquals(V2AgentId.SCHEDULE, schedule.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, schedule.status)
        assertEquals(1, gateway.calendarCalls)
        assertTrue(schedule.artifact!!.startsWith("schedule:"))
        assertEquals("1725346800000", schedule.artifact.v2ArtifactString("begin"))
        assertEquals("1725350400000", schedule.artifact.v2ArtifactString("end"))
    }

    @Test
    fun emailAgentSendsThroughRuntimeGateway() = runBlocking {
        val result = orchestrator.orchestrate("给 test@example.com 发送邮件 主题：进度 内容：已经完成V2邮件链路")
        val run = result.runs.single()

        assertEquals(V2AgentId.EMAIL, run.agentId)
        assertEquals(V2AgentRunStatus.COMPLETED, run.status)
        assertEquals(1, gateway.emailCalls)
        assertTrue(run.output.contains("test@example.com"))
        assertTrue(run.artifact!!.startsWith("email:"))
    }

    @Test
    fun emailAgentNeedsRecipientBeforeSending() = runBlocking {
        gateway.nextEmailDecisionJson = """
            {
              "to": "",
              "subject": "进度",
              "content": "缺少收件人",
              "needs_input": true,
              "reason": "缺少有效收件人"
            }
        """.trimIndent()

        val result = orchestrator.orchestrate("发送邮件 主题：进度 内容：缺少收件人")
        val run = result.runs.single()

        assertEquals(V2AgentId.EMAIL, run.agentId)
        assertEquals(V2AgentRunStatus.NEEDS_INPUT, run.status)
        assertEquals(0, gateway.emailCalls)
        assertFalse(result.judgement.passed)
    }

    @Test
    fun ragAgentNeedsInputWhenNoLocalChunks() = runBlocking {
        gateway.returnNoRagChunks = true

        val result = orchestrator.orchestrate("根据知识库文档回答盆地沉积问题")

        assertFalse(result.judgement.passed)
        assertEquals(V2AgentRunStatus.NEEDS_INPUT, result.runs.single().status)
        assertTrue(result.answer.contains("Reasons=agent_needs_input"))
        assertTrue(result.answer.contains("请先在文档页上传 PDF、Word 或文本资料。"))
    }

    @Test
    fun runtimeStreamsAllStagesInOrder() = runBlocking {
        val events = orchestrator.stream("写一段地质报告摘要").toList()

        assertEquals(V2PipelineStage.MASTER, events.first().stage)
        assertEquals(V2PipelineStage.AGGREGATOR, events.last().stage)
        assertEquals(7, events.size)
    }

    @Test
    fun judgeRejectsCompletedRunsMissingRequiredArtifact() = runBlocking {
        val brokenRegistry = V2RuntimeAgentExecutorRegistry.fromExecutors(
            listOf(
                object : V2RuntimeAgentExecutor {
                    override val agentId: V2AgentId = V2AgentId.SCHEDULE

                    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution =
                        V2AgentExecution(
                            status = V2AgentRunStatus.COMPLETED,
                            summary = "Broken schedule run",
                            output = "schedule output",
                            usedTools = task.requiredTools,
                            artifact = null
                        )
                }
            )
        )
        val brokenOrchestrator = V2RuntimeOrchestrator(
            agentRegistry = agents,
            toolRegistry = tools,
            executorRegistry = brokenRegistry,
            planner = V2Orchestrator(agents, tools)
        )

        val result = brokenOrchestrator.orchestrate("安排明天下午3点项目会议")

        assertFalse(result.judgement.passed)
        assertTrue(result.judgement.reasons.contains("missing_required_artifact"))
        assertTrue(result.reflection.notes.any { it.contains("missing required artifacts") })
    }

    @Test
    fun runtimeOrchestrateReportsStageEventsDuringExecution() = runBlocking {
        val events = mutableListOf<V2StageEvent>()

        val result = orchestrator.orchestrate(V2RuntimeRequest("写一段地质报告摘要")) { event ->
            events += event
        }

        assertEquals(result.events, events)
        assertEquals(V2PipelineStage.MASTER, events.first().stage)
        assertEquals(V2PipelineStage.AGGREGATOR, events.last().stage)
    }

    @Test
    fun scheduleAgentStreamsContentChunksDuringRuntimeExecution() = runBlocking {
        val chunks = mutableListOf<String>()

        val result = orchestrator.orchestrate(
            request = V2RuntimeRequest("安排明天复习地质构造"),
            onEvent = {},
            onContent = { agentId, chunk ->
                chunks += "${agentId.wireName}:$chunk"
            }
        )

        assertEquals(V2AgentId.SCHEDULE, result.runs.single().agentId)
        assertEquals(listOf("schedule:LLM-", "schedule:SCHEDULE"), chunks)
        assertEquals("LLM-SCHEDULE", result.runs.single().output)
    }

    @Test
    fun specializedAgentsStreamOneLlmSynthesisChunks() = runBlocking {
        val chunks = mutableListOf<String>()

        val result = orchestrator.orchestrate(
            request = V2RuntimeRequest("根据知识库文档回答盆地沉积问题"),
            onEvent = {},
            onContent = { agentId, chunk ->
                chunks += "${agentId.wireName}:$chunk"
            }
        )

        assertEquals(V2AgentId.RAG, result.runs.single().agentId)
        assertEquals(listOf("rag:LLM-", "rag:RAG"), chunks)
        assertEquals("LLM-RAG", result.runs.single().output)
    }

    @Test
    fun llmRuntimePromptIncludesRecalledRoomMemory() = runBlocking {
        gateway.memoriesToRecall = listOf(
            V2RuntimeMemory(
                content = "用户正在准备盆地沉积报告，需要偏重资料引用。",
                sourceAgent = V2AgentId.RESEARCH,
                kind = "research",
                importance = 0.8f
            )
        )

        orchestrator.orchestrate("分析盆地沉积报告资料")

        assertEquals(1, gateway.recallMemoryCalls)
        assertTrue(gateway.lastUserPrompt.contains("历史记忆"))
        assertTrue(gateway.lastUserPrompt.contains("盆地沉积报告"))
    }

    @Test
    fun searchRuntimeUsesSameConversationHistoryForFollowUpQuestions() = runBlocking {
        orchestrator.orchestrate(
            request = V2RuntimeRequest(
                input = "搜索它最近有哪些热梗",
                history = listOf(
                    V2RuntimeHistoryMessage("user", "这是什么"),
                    V2RuntimeHistoryMessage("assistant", "这是一只北美负鼠。")
                )
            )
        )

        assertTrue(gateway.lastSearchQuestion.contains("北美负鼠"))
        assertTrue(gateway.lastUserPrompt.contains("同一会话前文"))
        assertTrue(gateway.lastUserPrompt.contains("北美负鼠"))
    }

    private class FakeRuntimeGateway : V2RuntimeGateway {
        var searchCalls = 0
        var ragCalls = 0
        var llmCalls = 0
        var rememberCalls = 0
        var saveTaskCalls = 0
        var listTaskCalls = 0
        var documentTextCalls = 0
        var deleteDocumentCalls = 0
        var calendarCalls = 0
        var emailCalls = 0
        var recallMemoryCalls = 0
        var returnNoRagChunks = false
        var lastUserPrompt = ""
        var lastSearchQuestion = ""
        var lastEmailRequest: V2EmailRequest? = null
        var memoriesToRecall: List<V2RuntimeMemory> = emptyList()
        var nextEmailDecisionJson: String? = null

        override suspend fun completeWithOneLlm(
            agentId: V2AgentId,
            systemPrompt: String,
            userPrompt: String
        ): Result<String> {
            llmCalls += 1
            lastUserPrompt = userPrompt
            if (agentId == V2AgentId.EMAIL) {
                return Result.success(
                    nextEmailDecisionJson ?: """
                        {
                          "to": "test@example.com",
                          "subject": "来自 GeoAgent 的邮件",
                          "content": "默认邮件正文",
                          "needs_input": false,
                          "reason": "测试默认邮件决策"
                        }
                    """.trimIndent()
                )
            }
            return Result.success("LLM-${agentId.name}: $userPrompt")
        }

        override suspend fun streamWithOneLlm(
            agentId: V2AgentId,
            systemPrompt: String,
            userPrompt: String,
            onContent: suspend (String) -> Unit
        ): Result<String> {
            llmCalls += 1
            lastUserPrompt = userPrompt
            val first = "LLM-"
            val second = agentId.name
            onContent(first)
            onContent(second)
            return Result.success(first + second)
        }

        override suspend fun search(question: String): Result<V2RuntimeSearchResult> {
            searchCalls += 1
            lastSearchQuestion = question
            return Result.success(
                V2RuntimeSearchResult(
                    enhancedPrompt = "enhanced:$question",
                    sources = listOf(
                        V2RuntimeSearchSource(
                            title = "source",
                            url = "https://example.com",
                            content = "source content"
                        )
                    )
                )
            )
        }

        override suspend fun retrieveRag(
            question: String,
            topK: Int,
            minScore: Float
        ): Result<List<V2RuntimeRagChunk>> {
            ragCalls += 1
            if (returnNoRagChunks) return Result.success(emptyList())
            return Result.success(
                listOf(
                    V2RuntimeRagChunk(
                        documentId = "doc",
                        chunkIndex = 1,
                        text = "local chunk",
                        score = 0.88f
                    )
                )
            )
        }

        override suspend fun listDocuments(): Result<List<V2RuntimeDocumentRecord>> {
            return Result.success(
                listOf(
                    V2RuntimeDocumentRecord(
                        id = "doc-pdf",
                        name = "sample.pdf",
                        type = "pdf",
                        sizeBytes = 1024L,
                        chunkCount = 2
                    ),
                    V2RuntimeDocumentRecord(
                        id = "doc-text",
                        name = "report.txt",
                        type = "txt",
                        sizeBytes = 512L,
                        chunkCount = 1
                    )
                )
            )
        }

        override suspend fun getDocumentText(documentIdOrName: String): Result<String> {
            documentTextCalls += 1
            return Result.success("PDF parsed text for $documentIdOrName")
        }

        override suspend fun deleteDocument(documentIdOrName: String): Result<String> {
            deleteDocumentCalls += 1
            return Result.success(
                when (documentIdOrName) {
                    "doc-text" -> "report.txt"
                    "doc-pdf" -> "sample.pdf"
                    else -> documentIdOrName
                }
            )
        }

        override suspend fun prepareCalendarEvent(
            request: V2CalendarEventRequest
        ): Result<V2RuntimeCalendarArtifact> {
            calendarCalls += 1
            return Result.success(
                V2RuntimeCalendarArtifact(
                    title = request.title,
                    description = request.description,
                    timeZone = request.timeZone,
                    action = "android.intent.action.INSERT",
                    dataUri = "content://com.android.calendar/events"
                )
            )
        }

        override suspend fun sendEmail(request: V2EmailRequest): Result<String> {
            emailCalls += 1
            lastEmailRequest = request
            return Result.success("邮件已发送至 ${request.to}")
        }

        override suspend fun recallMemory(question: String, limit: Int): Result<List<V2RuntimeMemory>> {
            recallMemoryCalls += 1
            return Result.success(memoriesToRecall.take(limit))
        }

        override suspend fun remember(
            content: String,
            sourceAgent: V2AgentId,
            kind: String,
            importance: Float
        ) {
            rememberCalls += 1
        }

        override suspend fun saveTask(
            title: String,
            description: String,
            sourceAgent: V2AgentId,
            priority: Int,
            relatedArtifact: String?
        ): Result<V2RuntimeTaskRecord> {
            saveTaskCalls += 1
            return Result.success(
                V2RuntimeTaskRecord(
                    id = "task-$saveTaskCalls",
                    title = title,
                    sourceAgent = sourceAgent,
                    status = "open",
                    priority = priority
                )
            )
        }

        override suspend fun listTasks(
            status: String?,
            sourceAgent: V2AgentId?,
            limit: Int
        ): Result<List<V2RuntimeTaskRecord>> {
            listTaskCalls += 1
            return Result.success(
                listOf(
                    V2RuntimeTaskRecord(
                        id = "task-open-1",
                        title = "完成地质报告",
                        sourceAgent = V2AgentId.TASK,
                        status = "open",
                        priority = 4
                    ),
                    V2RuntimeTaskRecord(
                        id = "task-open-2",
                        title = "整理会议纪要",
                        sourceAgent = V2AgentId.TASK,
                        status = "open",
                        priority = 5
                    )
                ).take(limit)
            )
        }
    }

    private class FixedRuntimeClock(
        private val nowMillis: Long,
        private val zone: TimeZone
    ) : V2RuntimeClock {
        override fun nowMillis(): Long = nowMillis
        override fun timeZone(): TimeZone = zone
    }
}
