package com.geoagent.agent.v2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import java.util.UUID

enum class V2PipelineStage {
    MASTER,
    PLANNER,
    ROUTER,
    AGENTS,
    REFLECTION,
    JUDGE,
    AGGREGATOR
}

enum class V2AgentId(val wireName: String) {
    SEARCH("search"),
    RAG("rag"),
    RESEARCH("research"),
    SCHEDULE("schedule"),
    TASK("task"),
    EMAIL("email"),
    PDF("pdf"),
    FILE("file")
}

enum class V2ToolId {
    ONE_LLM,
    TAVILY_SEARCH,
    RAG_RETRIEVAL,
    ROOM_MEMORY,
    RETROFIT_NETWORK,
    FLOW_STREAMING,
    TASK_MANAGER,
    CALENDAR_CONTRACT,
    JAVA_MAIL_QQ_SMTP,
    PDF_PARSER,
    FILE_ACCESS,
    DOCUMENT_WRITER
}

enum class V2ToolBacking {
    LOCAL_ANDROID,
    LOCAL_DATABASE,
    EXTERNAL_API,
    LLM_VIRTUAL,
    ANDROID_SYSTEM
}

data class V2ToolMeta(
    val id: V2ToolId,
    val displayName: String,
    val backing: V2ToolBacking,
    val description: String
)

data class V2AgentMeta(
    val id: V2AgentId,
    val displayName: String,
    val description: String,
    val keywords: Set<String>,
    val regexPatterns: List<Regex> = emptyList(),
    val requiredTools: Set<V2ToolId>,
    val priority: Int,
    val supportsParallel: Boolean = true
)

data class V2RouteCandidate(
    val agent: V2AgentMeta,
    val confidence: Float,
    val reason: String
)

data class V2PlanTask(
    val id: String,
    val input: String,
    val agentId: V2AgentId,
    val requiredTools: Set<V2ToolId>,
    val canRunInParallel: Boolean,
    val reason: String,
    val dependsOn: List<String> = emptyList()
)

data class V2Plan(
    val originalInput: String,
    val tasks: List<V2PlanTask>,
    val parallelizable: Boolean
)

enum class V2AgentRunStatus {
    COMPLETED,
    NEEDS_INPUT,
    BLOCKED
}

data class V2AgentExecution(
    val status: V2AgentRunStatus,
    val summary: String,
    val output: String,
    val usedTools: Set<V2ToolId>,
    val artifact: String? = null,
    val followUps: List<String> = emptyList()
)

data class V2AgentRun(
    val taskId: String,
    val agentId: V2AgentId,
    val status: V2AgentRunStatus,
    val toolIds: Set<V2ToolId>,
    val summary: String,
    val output: String,
    val artifact: String? = null,
    val followUps: List<String> = emptyList()
)

data class V2CalendarEventRequest(
    val title: String,
    val description: String,
    val beginTimeMillis: Long?,
    val endTimeMillis: Long?,
    val timeZone: String
)

data class V2ReminderRequest(
    val title: String,
    val content: String,
    val delayMillis: Long?
)

data class V2EmailRequest(
    val to: String,
    val subject: String,
    val content: String
)

data class V2ExecutionContext(
    val originalInput: String,
    val previousRuns: List<V2AgentRun>,
    val imageBase64: String? = null,
    val history: List<V2RuntimeHistoryMessage> = emptyList(),
    val onContent: suspend (V2AgentId, String) -> Unit = { _, _ -> }
)

interface V2AgentExecutor {
    val agentId: V2AgentId

    fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution
}

data class V2Reflection(
    val notes: List<String>,
    val missingTools: Map<V2AgentId, Set<V2ToolId>>
)

data class V2Judgement(
    val passed: Boolean,
    val score: Float,
    val reasons: List<String>
)

data class V2StageEvent(
    val stage: V2PipelineStage,
    val message: String
)

data class V2OrchestrationResult(
    val traceId: String,
    val input: String,
    val plan: V2Plan,
    val runs: List<V2AgentRun>,
    val reflection: V2Reflection,
    val judgement: V2Judgement,
    val answer: String,
    val events: List<V2StageEvent>
)

class V2ToolRegistry private constructor(
    private val tools: Map<V2ToolId, V2ToolMeta>
) {
    fun all(): List<V2ToolMeta> = tools.values.sortedBy { it.id.name }

    fun get(id: V2ToolId): V2ToolMeta? = tools[id]

    fun containsAll(ids: Set<V2ToolId>): Boolean = ids.all { tools.containsKey(it) }

    companion object {
        fun production(): V2ToolRegistry = V2ToolRegistry(
            listOf(
                V2ToolMeta(V2ToolId.ONE_LLM, "One LLM", V2ToolBacking.LLM_VIRTUAL, "Single model used by all virtual agents."),
                V2ToolMeta(V2ToolId.TAVILY_SEARCH, "Tavily Search", V2ToolBacking.EXTERNAL_API, "Web search through Tavily."),
                V2ToolMeta(V2ToolId.RAG_RETRIEVAL, "RAG Retrieval", V2ToolBacking.LOCAL_DATABASE, "Local document chunk and embedding retrieval."),
                V2ToolMeta(V2ToolId.ROOM_MEMORY, "Room Memory", V2ToolBacking.LOCAL_DATABASE, "Persistent memory and search cache through Room."),
                V2ToolMeta(V2ToolId.RETROFIT_NETWORK, "Retrofit", V2ToolBacking.EXTERNAL_API, "Network API access."),
                V2ToolMeta(V2ToolId.FLOW_STREAMING, "Flow Streaming", V2ToolBacking.LOCAL_ANDROID, "Stage and answer streaming through Kotlin Flow."),
                V2ToolMeta(V2ToolId.TASK_MANAGER, "Task Manager", V2ToolBacking.LOCAL_DATABASE, "Task capture, decomposition, and follow-up persistence."),
                V2ToolMeta(V2ToolId.CALENDAR_CONTRACT, "CalendarContract", V2ToolBacking.ANDROID_SYSTEM, "Android calendar read/write integration."),
                V2ToolMeta(V2ToolId.JAVA_MAIL_QQ_SMTP, "JavaMail QQ SMTP", V2ToolBacking.EXTERNAL_API, "QQ SMTP email delivery configured from .env."),
                V2ToolMeta(V2ToolId.PDF_PARSER, "PDF Parser", V2ToolBacking.LOCAL_ANDROID, "PDF text extraction and rendering."),
                V2ToolMeta(V2ToolId.FILE_ACCESS, "File Access", V2ToolBacking.LOCAL_ANDROID, "Android file picker and local document access."),
                V2ToolMeta(V2ToolId.DOCUMENT_WRITER, "Document Writer", V2ToolBacking.LLM_VIRTUAL, "Structured drafting, rewriting, and resume support.")
            ).associateBy { it.id }
        )
    }
}

class V2AgentRegistry(
    private val toolRegistry: V2ToolRegistry,
    agents: List<V2AgentMeta> = productionAgents()
) {
    private val agentsById: Map<V2AgentId, V2AgentMeta> = agents.associateBy { it.id }

    fun all(): List<V2AgentMeta> = agentsById.values.sortedBy { it.priority }

    fun get(id: V2AgentId): V2AgentMeta? = agentsById[id]

    fun route(input: String): V2RouteCandidate? {
        val normalized = normalize(input)
        val candidates = all().map { agent -> score(agent, input, normalized) }
            .filter { it.confidence > 0.35f }
            .sortedWith(compareByDescending<V2RouteCandidate> { it.confidence }.thenBy { it.agent.priority })

        return candidates.firstOrNull()
    }

    fun missingTools(agent: V2AgentMeta): Set<V2ToolId> =
        agent.requiredTools.filterNot { toolRegistry.get(it) != null }.toSet()

    private fun score(agent: V2AgentMeta, rawInput: String, normalizedInput: String): V2RouteCandidate {
        val keywordHits = agent.keywords.count { normalizedInput.contains(normalize(it)) }
        val regexHits = agent.regexPatterns.count { it.containsMatchIn(rawInput) }
        val explicitEmailSend = agent.id == V2AgentId.EMAIL &&
            Regex("""(?:发给|发送给|发送至|发至|给)\s*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+""", RegexOption.IGNORE_CASE)
                .containsMatchIn(rawInput)
        val base = when {
            explicitEmailSend -> 0.95f
            regexHits > 0 -> 0.68f + regexHits * 0.12f
            keywordHits > 0 -> 0.48f + keywordHits * 0.1f
            else -> 0f
        }
        val priorityBoost = ((100 - agent.priority.coerceIn(0, 100)) / 1000f).coerceAtLeast(0f)
        val confidence = (base + priorityBoost).coerceAtMost(0.98f)
        val reason = when {
            explicitEmailSend -> "email_send"
            regexHits > 0 -> "regex:$regexHits"
            keywordHits > 0 -> "keyword:$keywordHits"
            else -> "none"
        }
        return V2RouteCandidate(agent, confidence, reason)
    }

    companion object {
        private val commonTools = setOf(V2ToolId.ONE_LLM, V2ToolId.ROOM_MEMORY, V2ToolId.FLOW_STREAMING)

        fun productionAgents(): List<V2AgentMeta> = listOf(
            agent(V2AgentId.SEARCH, "Search Agent", "Tavily-backed web search and synthesis", 10, setOf("搜索", "联网", "tavily", "最新", "新闻"), setOf(V2ToolId.TAVILY_SEARCH, V2ToolId.RETROFIT_NETWORK), Regex("""(联网|搜索|查一下|最新|新闻)""")),
            agent(V2AgentId.RAG, "RAG Agent", "Local knowledge-base retrieval answering", 11, setOf("知识库", "文档", "文献", "资料", "rag"), setOf(V2ToolId.RAG_RETRIEVAL, V2ToolId.PDF_PARSER), Regex("""(知识库|文档|文献|资料).*(检索|回答|查询)?""")),
            agent(V2AgentId.RESEARCH, "Research Agent", "Research planning, evidence gathering, and source synthesis", 12, setOf("研究", "调研", "综述", "论文研究", "资料整理", "分析"), setOf(V2ToolId.TAVILY_SEARCH, V2ToolId.RAG_RETRIEVAL, V2ToolId.DOCUMENT_WRITER), Regex("""(研究|调研|综述|资料整理|分析)""")),
            agent(V2AgentId.SCHEDULE, "Schedule Agent", "Schedule planning and time-block arrangement", 31, setOf("安排", "排期", "时间表", "计划表", "schedule"), setOf(V2ToolId.CALENDAR_CONTRACT, V2ToolId.DOCUMENT_WRITER), Regex("""(安排|排期|时间表|schedule)""", RegexOption.IGNORE_CASE)),
            agent(V2AgentId.TASK, "Task Agent", "Task capture, tracking, and decomposition", 32, setOf("任务", "待办", "todo", "事项", "清单"), setOf(V2ToolId.TASK_MANAGER, V2ToolId.ROOM_MEMORY), Regex("""(任务|待办|todo|清单)""", RegexOption.IGNORE_CASE)),
            agent(V2AgentId.EMAIL, "Email Agent", "QQ SMTP email sending and mail drafting", 8, setOf("邮件", "邮箱", "email", "smtp", "发信", "发送给", "发给"), setOf(V2ToolId.JAVA_MAIL_QQ_SMTP, V2ToolId.DOCUMENT_WRITER), Regex("""(发|发送).*(邮件|email|邮箱)|(?:发给|发送给|发送至|发至|给)\s*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+""", RegexOption.IGNORE_CASE)),
            agent(V2AgentId.PDF, "PDF Agent", "PDF parsing, summarization, and extraction", 19, setOf("pdf", "PDF", "提取pdf", "解析pdf"), setOf(V2ToolId.PDF_PARSER, V2ToolId.FILE_ACCESS), Regex("""pdf|解析.*文件|提取.*文件""", RegexOption.IGNORE_CASE)),
            agent(V2AgentId.FILE, "File Agent", "File intake, parsing, and local document operations", 42, setOf("文件", "上传", "打开文件", "保存", "导入"), setOf(V2ToolId.FILE_ACCESS), Regex("""(文件|上传|导入|保存)"""), Regex("""(读取|查看|打开|删除|移除).*\.[a-zA-Z0-9]{2,5}""", RegexOption.IGNORE_CASE))
        )

        private fun agent(
            id: V2AgentId,
            displayName: String,
            description: String,
            priority: Int,
            keywords: Set<String>,
            tools: Set<V2ToolId>,
            vararg patterns: Regex
        ): V2AgentMeta = V2AgentMeta(
            id = id,
            displayName = displayName,
            description = description,
            keywords = keywords + id.wireName,
            regexPatterns = patterns.toList(),
            requiredTools = commonTools + tools,
            priority = priority
        )
    }
}

class V2AgentExecutorRegistry private constructor(
    private val executors: Map<V2AgentId, V2AgentExecutor>
) {
    fun all(): List<V2AgentExecutor> = executors.values.sortedBy { it.agentId.ordinal }

    fun get(id: V2AgentId): V2AgentExecutor? = executors[id]

    fun containsAll(ids: Set<V2AgentId>): Boolean = ids.all { executors.containsKey(it) }

    companion object {
        fun production(
            agentRegistry: V2AgentRegistry = V2AgentRegistry(V2ToolRegistry.production())
        ): V2AgentExecutorRegistry {
            val executors = agentRegistry.all()
                .map { meta ->
                    when (meta.id) {
                        V2AgentId.EMAIL -> V2EmailAgentExecutor(meta)
                        else -> V2LlmMediatedAgentExecutor(meta)
                    }
                }
                .associateBy { it.agentId }
            return V2AgentExecutorRegistry(executors)
        }
    }
}

private class V2LlmMediatedAgentExecutor(
    private val meta: V2AgentMeta
) : V2AgentExecutor {
    override val agentId: V2AgentId = meta.id

    override fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        if (task.input.isBlank()) {
            return V2AgentExecution(
                status = V2AgentRunStatus.NEEDS_INPUT,
                summary = "${meta.displayName} needs a non-empty user request.",
                output = "用户输入为空，无法执行 ${meta.displayName}。",
                usedTools = emptySet(),
                followUps = listOf("请补充需要处理的问题或目标。")
            )
        }

        val action = actionFor(meta.id)
        val previous = if (context.previousRuns.isEmpty()) {
            "无前置 Agent 结果。"
        } else {
            context.previousRuns.joinToString(separator = "\n") { "- ${it.agentId.wireName}: ${it.summary}" }
        }
        val output = """
            Agent: ${meta.displayName}
            Task: ${task.input}
            Action: $action
            Tools: ${task.requiredTools.joinToString { it.name }}
            Previous: $previous
        """.trimIndent()

        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "${meta.displayName} prepared execution for '${task.input.take(40)}'.",
            output = output,
            usedTools = task.requiredTools,
            artifact = v2Artifact(
                meta.id.wireName,
                mapOf(
                    "taskId" to task.id,
                    "agent" to meta.id.wireName,
                    "summary" to "${meta.displayName} prepared execution."
                )
            ),
            followUps = followUpsFor(meta.id)
        )
    }

    private fun actionFor(agentId: V2AgentId): String = when (agentId) {
        V2AgentId.SEARCH -> "使用 Tavily 检索实时网页来源，再交给单一 LLM 综合。"
        V2AgentId.RAG -> "检索本地知识库 chunk 与 PDF 文本，基于来源约束回答。"
        V2AgentId.RESEARCH -> "拆解研究问题，结合联网搜索、RAG 与写作工具形成证据链。"
        V2AgentId.SCHEDULE -> "生成时间块安排，并在需要时写入日历。"
        V2AgentId.TASK -> "拆解为可追踪任务，并写入任务/记忆系统。"
        V2AgentId.EMAIL -> "生成邮件草稿，并通过 QQ SMTP 邮件工具发送或记录。"
        V2AgentId.PDF -> "解析 PDF 文本，必要时渲染页面并走 OCR。"
        V2AgentId.FILE -> "处理本地文件访问、导入、保存和文档操作。"
    }

    private fun followUpsFor(agentId: V2AgentId): List<String> = when (agentId) {
        V2AgentId.EMAIL -> listOf("确认收件人、主题和正文后再发送。")
        V2AgentId.SCHEDULE -> listOf("确认具体日期、时间和时区。")
        V2AgentId.FILE,
        V2AgentId.PDF -> listOf("确认已选择或上传目标文件。")
        else -> emptyList()
    }
}

private class V2EmailAgentExecutor(
    private val meta: V2AgentMeta
) : V2AgentExecutor {
    override val agentId: V2AgentId = meta.id

    override fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        val to = Regex("""([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""")
            .find(task.input)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (to.isNullOrBlank()) {
            return V2AgentExecution(
                status = V2AgentRunStatus.NEEDS_INPUT,
                summary = "Email Agent needs a recipient address.",
                output = "缺少收件人邮箱，无法构造 JavaMail QQ SMTP 发送请求。",
                usedTools = task.requiredTools,
                followUps = listOf("请提供收件人邮箱、主题和正文。")
            )
        }
        val request = V2EmailRequest(
            to = to,
            subject = Regex("""(?:主题[:：]?\s*)([^。；;\n]+)""")
                .find(task.input)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "来自 GeoAgent 的邮件",
            content = Regex("""(?:内容|正文)\s*(?:[:：]|为|是)\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
                .find(task.input)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: task.input
        )
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "Email Agent prepared JavaMail SMTP request to ${request.to}.",
            output = "JavaMail QQ SMTP request prepared for ${request.to}.",
            usedTools = task.requiredTools,
            artifact = request.toArtifact(),
            followUps = listOf("发送前请确认邮件内容。")
        )
    }
}

private fun extractTitle(input: String, fallback: String): String {
    val cleaned = input
        .replace(Regex("""(提醒我|提醒|加入日历|添加到日历|日程|日历|安排)"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '，', ',', '。', '.')
    return cleaned.take(30).ifBlank { fallback }
}

private fun V2EmailRequest.toArtifact(): String =
    v2Artifact(
        "email",
        mapOf(
            "to" to to,
            "subject" to subject,
            "content" to content
        )
    )

class V2Orchestrator(
    private val agentRegistry: V2AgentRegistry = V2AgentRegistry(V2ToolRegistry.production()),
    private val toolRegistry: V2ToolRegistry = V2ToolRegistry.production(),
    private val executorRegistry: V2AgentExecutorRegistry = V2AgentExecutorRegistry.production(agentRegistry)
) {
    fun stream(input: String): Flow<V2StageEvent> = flow {
        orchestrate(input).events.forEach { emit(it) }
    }

    fun plan(input: String): V2Plan = buildPlan(input)

    fun orchestrate(input: String): V2OrchestrationResult {
        val events = mutableListOf<V2StageEvent>()
        events += V2StageEvent(V2PipelineStage.MASTER, "Accepted user request for V2 orchestration.")

        val plan = buildPlan(input)
        events += V2StageEvent(V2PipelineStage.PLANNER, "Created ${plan.tasks.size} task(s); parallel=${plan.parallelizable}.")
        events += V2StageEvent(V2PipelineStage.ROUTER, plan.tasks.joinToString { "${it.input} -> ${it.agentId.wireName}" })

        val runs = execute(plan)
        events += V2StageEvent(
            V2PipelineStage.AGENTS,
            "Executed ${runs.size} virtual agent task(s); parallel-ready=${plan.parallelizable}."
        )

        val reflection = reflect(runs)
        events += V2StageEvent(V2PipelineStage.REFLECTION, reflection.notes.joinToString())

        val judgement = judge(input, plan, runs, reflection)
        events += V2StageEvent(V2PipelineStage.JUDGE, "passed=${judgement.passed}, score=${judgement.score}")

        val answer = aggregate(plan, runs, reflection, judgement)
        events += V2StageEvent(V2PipelineStage.AGGREGATOR, "Aggregated V2 plan response.")

        return V2OrchestrationResult(
            traceId = UUID.randomUUID().toString(),
            input = input,
            plan = plan,
            runs = runs,
            reflection = reflection,
            judgement = judgement,
            answer = answer,
            events = events
        )
    }

    private fun buildPlan(input: String): V2Plan {
        val segments = splitIntoSegments(input)
        val parallelHint = containsParallelHint(input)
        val tasks = segments.mapIndexedNotNull { index, segment ->
            val candidate = agentRegistry.route(segment) ?: return@mapIndexedNotNull null
            val taskId = "task-${index + 1}"
            V2PlanTask(
                id = taskId,
                input = segment,
                agentId = candidate.agent.id,
                requiredTools = candidate.agent.requiredTools,
                canRunInParallel = parallelHint && candidate.agent.supportsParallel,
                reason = candidate.reason,
                dependsOn = if (parallelHint || index == 0) emptyList() else listOf("task-$index")
            )
        }
        return V2Plan(
            originalInput = input,
            tasks = tasks,
            parallelizable = tasks.size > 1 && tasks.all { it.canRunInParallel }
        )
    }

    private fun splitIntoSegments(input: String): List<String> {
        val normalized = input.trim()
        if (normalized.isBlank()) return listOf("")
        return normalized
            .split(Regex("""(?:并且|同时|然后|；|;|，并|, and )""", RegexOption.IGNORE_CASE))
            .map { it.trim(' ', '，', ',', '。', '.') }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(normalized) }
    }

    private fun containsParallelHint(input: String): Boolean {
        val normalized = input.lowercase(Locale.getDefault())
        return normalized.contains("同时") || normalized.contains("并行") || normalized.contains("parallel")
    }

    private fun execute(plan: V2Plan): List<V2AgentRun> {
        val runs = mutableListOf<V2AgentRun>()
        for (task in plan.tasks) {
            val executor = executorRegistry.get(task.agentId)
            val run = if (executor == null) {
                V2AgentRun(
                    taskId = task.id,
                    agentId = task.agentId,
                    status = V2AgentRunStatus.BLOCKED,
                    toolIds = emptySet(),
                    summary = "No executor registered for ${task.agentId.wireName}.",
                    output = "Agent executor missing: ${task.agentId.wireName}",
                    followUps = listOf("注册 ${task.agentId.wireName} executor 后重试。")
                )
            } else {
                val result = executor.execute(
                    task,
                    V2ExecutionContext(
                        originalInput = plan.originalInput,
                        previousRuns = runs.toList()
                    )
                )
                V2AgentRun(
                    taskId = task.id,
                    agentId = task.agentId,
                    status = result.status,
                    toolIds = result.usedTools,
                    summary = result.summary,
                    output = result.output,
                    artifact = result.artifact,
                    followUps = result.followUps
                )
            }
            runs += run
        }
        return runs
    }

    private fun reflect(runs: List<V2AgentRun>): V2Reflection {
        val missing = runs.associate { run ->
            val absent = run.toolIds.filterNot { toolRegistry.get(it) != null }.toSet()
            run.agentId to absent
        }.filterValues { it.isNotEmpty() }
        val blockedRuns = runs.filter { it.status == V2AgentRunStatus.BLOCKED }
        val needsInputRuns = runs.filter { it.status == V2AgentRunStatus.NEEDS_INPUT }

        val notes = buildList {
            add("One LLM controls all virtual agents.")
            add("Agent registry, executor registry, and tool registry were checked before aggregation.")
            if (missing.isEmpty()) add("No missing registered tools.") else add("Missing tools detected: ${missing.keys.joinToString()}.")
            if (blockedRuns.isNotEmpty()) add("Blocked runs: ${blockedRuns.joinToString { it.agentId.wireName }}.")
            if (needsInputRuns.isNotEmpty()) add("Runs need more input: ${needsInputRuns.joinToString { it.agentId.wireName }}.")
        }
        return V2Reflection(notes, missing)
    }

    private fun judge(
        input: String,
        plan: V2Plan,
        runs: List<V2AgentRun>,
        reflection: V2Reflection
    ): V2Judgement {
        val reasons = mutableListOf<String>()
        if (input.isBlank()) reasons += "input_blank"
        if (plan.tasks.isEmpty()) reasons += "plan_empty"
        if (runs.any { it.status == V2AgentRunStatus.BLOCKED }) reasons += "agent_blocked"
        if (runs.any { it.status == V2AgentRunStatus.NEEDS_INPUT }) reasons += "agent_needs_input"
        if (reflection.missingTools.isNotEmpty()) reasons += "missing_tools"
        val passed = reasons.isEmpty()
        val score = when {
            passed -> 0.92f
            reasons.size == 1 -> 0.64f
            else -> 0.32f
        }
        return V2Judgement(passed, score, if (reasons.isEmpty()) listOf("ready") else reasons)
    }

    private fun aggregate(
        plan: V2Plan,
        runs: List<V2AgentRun>,
        reflection: V2Reflection,
        judgement: V2Judgement
    ): String {
        val agents = runs.joinToString { it.agentId.wireName }
        val mode = if (plan.parallelizable) "parallel" else "sequential"
        val summaries = runs.joinToString(" ") { it.summary }
        return "V2 orchestration ready: $mode tasks via [$agents]. Judge=${judgement.passed}. $summaries ${reflection.notes.joinToString(" ")}"
    }
}

private fun normalize(text: String): String {
    if (text.isBlank()) return ""
    return text
        .lowercase(Locale.getDefault())
        .replace(Regex("[`~!@#$%^&*()\\-_=+\\[\\]{}\\\\|;:'\",.<>/?，。！？、；：（）【】《》]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
