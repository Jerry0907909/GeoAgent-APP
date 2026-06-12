package com.geoagent.agent.v2

import com.geoagent.domain.ConversationContextBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import java.util.Calendar
import java.util.TimeZone

data class V2RuntimeSearchSource(
    val title: String,
    val url: String,
    val content: String
)

data class V2RuntimeSearchResult(
    val enhancedPrompt: String,
    val sources: List<V2RuntimeSearchSource>
)

data class V2RuntimeRagChunk(
    val documentId: String,
    val chunkIndex: Int,
    val text: String,
    val score: Float
)

data class V2RuntimeTaskRecord(
    val id: String,
    val title: String,
    val sourceAgent: V2AgentId,
    val status: String,
    val priority: Int
)

data class V2RuntimeMemory(
    val content: String,
    val sourceAgent: V2AgentId?,
    val kind: String,
    val importance: Float
)

data class V2RuntimeDocumentRecord(
    val id: String,
    val name: String,
    val type: String,
    val sizeBytes: Long,
    val chunkCount: Int
)

data class V2RuntimeCalendarArtifact(
    val title: String,
    val description: String,
    val timeZone: String,
    val action: String,
    val dataUri: String
)

data class V2RuntimeRequest(
    val input: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null,
    val history: List<V2RuntimeHistoryMessage> = emptyList()
)

data class V2RuntimeHistoryMessage(
    val role: String,
    val content: String
)

interface V2RuntimeGateway {
    suspend fun completeWithOneLlm(agentId: V2AgentId, systemPrompt: String, userPrompt: String): Result<String>
    suspend fun completeWithOneLlmVision(
        agentId: V2AgentId,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String,
        imageMimeType: String = "image/jpeg"
    ): Result<String> = completeWithOneLlm(agentId, systemPrompt, userPrompt)

    suspend fun streamWithOneLlm(
        agentId: V2AgentId,
        systemPrompt: String,
        userPrompt: String,
        onContent: suspend (String) -> Unit
    ): Result<String> {
        return completeWithOneLlm(agentId, systemPrompt, userPrompt).onSuccess { onContent(it) }
    }
    suspend fun streamWithOneLlmVision(
        agentId: V2AgentId,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String,
        imageMimeType: String = "image/jpeg",
        onContent: suspend (String) -> Unit
    ): Result<String> {
        return completeWithOneLlmVision(
            agentId,
            systemPrompt,
            userPrompt,
            imageBase64,
            imageMimeType
        ).onSuccess { onContent(it) }
    }
    suspend fun search(question: String): Result<V2RuntimeSearchResult>
    suspend fun retrieveRag(question: String, topK: Int = 5, minScore: Float = 0.25f): Result<List<V2RuntimeRagChunk>>
    suspend fun listDocuments(): Result<List<V2RuntimeDocumentRecord>>
    suspend fun getDocumentText(documentIdOrName: String): Result<String>
    suspend fun deleteDocument(documentIdOrName: String): Result<String>
    suspend fun prepareCalendarEvent(request: V2CalendarEventRequest): Result<V2RuntimeCalendarArtifact>
    suspend fun sendEmail(request: V2EmailRequest): Result<String>
    suspend fun recallMemory(question: String, limit: Int = 5): Result<List<V2RuntimeMemory>> = Result.success(emptyList())
    suspend fun remember(content: String, sourceAgent: V2AgentId, kind: String = "runtime", importance: Float = 0.5f)
    suspend fun saveTask(
        title: String,
        description: String,
        sourceAgent: V2AgentId,
        priority: Int = 3,
        relatedArtifact: String? = null
    ): Result<V2RuntimeTaskRecord>
    suspend fun listTasks(
        status: String? = null,
        sourceAgent: V2AgentId? = null,
        limit: Int = 20
    ): Result<List<V2RuntimeTaskRecord>>
}

interface V2RuntimeAgentExecutor {
    val agentId: V2AgentId

    suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution
}

class V2RuntimeAgentExecutorRegistry private constructor(
    private val executors: Map<V2AgentId, V2RuntimeAgentExecutor>
) {
    fun all(): List<V2RuntimeAgentExecutor> = executors.values.sortedBy { it.agentId.ordinal }

    fun get(id: V2AgentId): V2RuntimeAgentExecutor? = executors[id]

    fun containsAll(ids: Set<V2AgentId>): Boolean = ids.all { executors.containsKey(it) }

    companion object {
        fun fromExecutors(executors: List<V2RuntimeAgentExecutor>): V2RuntimeAgentExecutorRegistry =
            V2RuntimeAgentExecutorRegistry(executors.associateBy { it.agentId })

        fun production(
            agentRegistry: V2AgentRegistry,
            gateway: V2RuntimeGateway,
            clock: V2RuntimeClock = V2SystemClock
        ): V2RuntimeAgentExecutorRegistry {
            val executors = agentRegistry.all()
                .map { meta ->
                    when (meta.id) {
                        V2AgentId.SEARCH -> V2RuntimeSearchExecutor(meta, gateway)
                        V2AgentId.RAG -> V2RuntimeRagExecutor(meta, gateway)
                        V2AgentId.RESEARCH -> V2RuntimeResearchExecutor(meta, gateway)
                        V2AgentId.TASK -> V2RuntimeTaskExecutor(meta, gateway)
                        V2AgentId.SCHEDULE -> V2RuntimeScheduleExecutor(meta, gateway, clock)
                        V2AgentId.FILE -> V2RuntimeFileExecutor(meta, gateway)
                        V2AgentId.PDF -> V2RuntimePdfExecutor(meta, gateway)
                        V2AgentId.EMAIL -> V2RuntimeEmailExecutor(meta, gateway)
                    }
                }
                .associateBy { it.agentId }
            return V2RuntimeAgentExecutorRegistry(executors)
        }
    }
}

interface V2RuntimeClock {
    fun nowMillis(): Long
    fun timeZone(): TimeZone
}

object V2SystemClock : V2RuntimeClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun timeZone(): TimeZone = TimeZone.getDefault()
}

class V2RuntimeOrchestrator(
    private val agentRegistry: V2AgentRegistry,
    private val toolRegistry: V2ToolRegistry,
    private val executorRegistry: V2RuntimeAgentExecutorRegistry,
    private val planner: V2Orchestrator = V2Orchestrator(agentRegistry, toolRegistry)
) {
    fun stream(input: String): Flow<V2StageEvent> = stream(V2RuntimeRequest(input))

    fun stream(request: V2RuntimeRequest): Flow<V2StageEvent> = flow {
        orchestrate(request) { emit(it) }
    }

    suspend fun orchestrate(input: String): V2OrchestrationResult =
        orchestrate(V2RuntimeRequest(input), onEvent = {})

    suspend fun orchestrate(request: V2RuntimeRequest): V2OrchestrationResult =
        orchestrate(request, onEvent = {})

    suspend fun orchestrate(
        request: V2RuntimeRequest,
        onEvent: suspend (V2StageEvent) -> Unit
    ): V2OrchestrationResult = orchestrate(request, onEvent = onEvent, onContent = { _, _ -> })

    suspend fun orchestrate(
        request: V2RuntimeRequest,
        onEvent: suspend (V2StageEvent) -> Unit,
        onContent: suspend (V2AgentId, String) -> Unit
    ): V2OrchestrationResult {
        val input = request.input
        val events = mutableListOf<V2StageEvent>()
        suspend fun stage(stage: V2PipelineStage, message: String) {
            val event = V2StageEvent(stage, message)
            events += event
            onEvent(event)
        }

        stage(V2PipelineStage.MASTER, "Accepted user request for V2 runtime orchestration.")

        val plan = planner.orchestrate(input).plan
        stage(V2PipelineStage.PLANNER, "Created ${plan.tasks.size} task(s); parallel=${plan.parallelizable}.")
        stage(V2PipelineStage.ROUTER, plan.tasks.joinToString { "${it.input} -> ${it.agentId.wireName}" })

        val runs = execute(plan, request, onContent)
        stage(V2PipelineStage.AGENTS, "Runtime executed ${runs.size} virtual agent task(s).")

        val reflection = reflect(runs)
        stage(V2PipelineStage.REFLECTION, reflection.notes.joinToString())

        val judgement = judge(input, plan, runs, reflection)
        stage(V2PipelineStage.JUDGE, "passed=${judgement.passed}, score=${judgement.score}")

        val answer = aggregate(plan, runs, reflection, judgement)
        stage(V2PipelineStage.AGGREGATOR, "Aggregated V2 runtime response.")

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

    private suspend fun execute(
        plan: V2Plan,
        request: V2RuntimeRequest,
        onContent: suspend (V2AgentId, String) -> Unit
    ): List<V2AgentRun> {
        return if (plan.parallelizable) {
            coroutineScope {
                plan.tasks.map { task ->
                    async { executeOne(task, emptyList(), request, onContent) }
                }.awaitAll()
            }
        } else {
            val runs = mutableListOf<V2AgentRun>()
            for (task in plan.tasks) {
                runs += executeOne(task, runs.toList(), request, onContent)
            }
            runs
        }
    }

    private suspend fun executeOne(
        task: V2PlanTask,
        previousRuns: List<V2AgentRun>,
        request: V2RuntimeRequest,
        onContent: suspend (V2AgentId, String) -> Unit
    ): V2AgentRun {
        val executor = executorRegistry.get(task.agentId)
            ?: return V2AgentRun(
                taskId = task.id,
                agentId = task.agentId,
                status = V2AgentRunStatus.BLOCKED,
                toolIds = emptySet(),
                summary = "No runtime executor registered for ${task.agentId.wireName}.",
                output = "Runtime executor missing: ${task.agentId.wireName}",
                followUps = listOf("注册 ${task.agentId.wireName} runtime executor 后重试。")
            )

        val result = executor.execute(
            task,
            V2ExecutionContext(
                originalInput = request.input,
                contextualInput = ConversationContextBuilder.buildV2ContextQuestion(
                    question = task.input,
                    history = request.history
                ),
                previousRuns = previousRuns,
                imageBase64 = request.imageBase64,
                imageMimeType = request.imageMimeType,
                history = request.history,
                onContent = onContent
            )
        )
        return V2AgentRun(
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

    private fun reflect(runs: List<V2AgentRun>): V2Reflection {
        val missing = runs.associate { run ->
            val absent = run.toolIds.filterNot { toolRegistry.get(it) != null }.toSet()
            run.agentId to absent
        }.filterValues { it.isNotEmpty() }
        val emptyOutputs = runs.filter { it.status == V2AgentRunStatus.COMPLETED && it.output.isBlank() }
        val missingArtifacts = runs.filter { it.status == V2AgentRunStatus.COMPLETED && requiresArtifact(it.agentId) && it.artifact.isNullOrBlank() }
        val weakEvidenceRuns = runs.filter { run ->
            run.status == V2AgentRunStatus.COMPLETED &&
                run.agentId in setOf(V2AgentId.SEARCH, V2AgentId.RAG, V2AgentId.RESEARCH) &&
                run.artifact.isNullOrBlank()
        }
        val notes = buildList {
            add("One LLM controls all virtual agents at runtime.")
            add("Runtime executor registry and tool registry were checked before aggregation.")
            if (missing.isEmpty()) add("No missing registered tools.") else add("Missing tools detected: ${missing.keys.joinToString()}.")
            runs.filter { it.status != V2AgentRunStatus.COMPLETED }
                .takeIf { it.isNotEmpty() }
                ?.let { add("Non-completed runs: ${it.joinToString { run -> run.agentId.wireName }}.") }
            if (emptyOutputs.isNotEmpty()) add("Completed runs with empty output: ${emptyOutputs.joinToString { it.agentId.wireName }}.")
            if (missingArtifacts.isNotEmpty()) add("Completed runs missing required artifacts: ${missingArtifacts.joinToString { it.agentId.wireName }}.")
            if (weakEvidenceRuns.isNotEmpty()) add("Evidence-oriented runs missing evidence artifacts: ${weakEvidenceRuns.joinToString { it.agentId.wireName }}.")
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
        if (runs.any { it.status == V2AgentRunStatus.COMPLETED && it.output.isBlank() }) reasons += "empty_agent_output"
        if (runs.any { it.status == V2AgentRunStatus.COMPLETED && requiresArtifact(it.agentId) && it.artifact.isNullOrBlank() }) {
            reasons += "missing_required_artifact"
        }
        val passed = reasons.isEmpty()
        val score = when {
            passed -> 0.94f
            reasons.size == 1 -> 0.66f
            else -> 0.34f
        }
        return V2Judgement(passed, score, if (reasons.isEmpty()) listOf("ready") else reasons)
    }

    private fun aggregate(
        plan: V2Plan,
        runs: List<V2AgentRun>,
        reflection: V2Reflection,
        judgement: V2Judgement
    ): String {
        val mode = if (plan.parallelizable) "parallel" else "sequential"
        val outputs = runs.joinToString("\n\n") { "${it.agentId.wireName}: ${it.output}" }
        val followUps = runs.flatMap { run -> run.followUps.map { "${run.agentId.wireName}: $it" } }
        return buildString {
            append("V2 runtime completed $mode execution. Judge=${judgement.passed}, score=${judgement.score}.")
            if (!judgement.passed) {
                append(" Reasons=${judgement.reasons.joinToString(",")}.")
            }
            append("\n\n")
            append(outputs)
            if (followUps.isNotEmpty()) {
                append("\n\nFollow-ups:\n")
                append(followUps.joinToString("\n") { "- $it" })
            }
            append("\n\n")
            append(reflection.notes.joinToString(" "))
        }
    }

    private fun requiresArtifact(agentId: V2AgentId): Boolean = true
}

private suspend fun V2RuntimeGateway.streamWithMemory(
    agentId: V2AgentId,
    question: String,
    systemPrompt: String,
    userPrompt: String,
    imageBase64: String? = null,
    imageMimeType: String? = null,
    onContent: suspend (String) -> Unit
): Result<String> {
    val memoryContext = recallMemory(question, limit = 4)
        .getOrDefault(emptyList())
        .filter { it.content.isNotBlank() }
        .joinToString("\n") { memory ->
            val source = memory.sourceAgent?.wireName ?: "unknown"
            "- [${memory.kind}/$source/${"%.2f".format(memory.importance)}] ${memory.content.take(600)}"
        }
    val prompt = if (memoryContext.isBlank()) {
        userPrompt
    } else {
        "$userPrompt\n\n历史记忆：\n$memoryContext"
    }
    val image = imageBase64?.takeIf { it.isNotBlank() }
    return if (image != null) {
        streamWithOneLlmVision(
            agentId = agentId,
            systemPrompt = systemPrompt,
            userPrompt = prompt,
            imageBase64 = image,
            imageMimeType = imageMimeType ?: "image/jpeg",
            onContent = onContent
        )
    } else {
        streamWithOneLlm(
            agentId = agentId,
            systemPrompt = systemPrompt,
            userPrompt = prompt,
            onContent = onContent
        )
    }
}

private class V2RuntimeSearchExecutor(
    private val meta: V2AgentMeta,
    private val gateway: V2RuntimeGateway
) : V2RuntimeAgentExecutor {
    override val agentId: V2AgentId = meta.id

    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        val search = gateway.search(context.contextualInput).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "Search Agent failed to retrieve Tavily context.",
                output = e.message ?: "Tavily search failed",
                usedTools = task.requiredTools
            )
        }
        val evidence = search.sources.take(8).joinToString("\n\n") { source ->
            "Title: ${source.title}\nURL: ${source.url}\nContent: ${source.content.take(900)}"
        }
        val answer = gateway.streamWithMemory(
            agentId = agentId,
            question = context.contextualInput,
            systemPrompt = "你是 GeoScientist Search Agent。基于 Tavily 搜索证据回答，必须保留关键来源链接。",
            userPrompt = "用户问题：${context.contextualInput}\n\n检索增强提示：${search.enhancedPrompt}\n\nTavily 证据：\n$evidence",
            imageBase64 = context.imageBase64,
            imageMimeType = context.imageMimeType,
            onContent = { chunk -> context.onContent(agentId, chunk) }
        ).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "Search Agent retrieved Tavily context but One LLM synthesis failed.",
                output = e.message ?: "One LLM synthesis failed",
                usedTools = task.requiredTools
            )
        }
        val sourceLines = search.sources.joinToString("\n") { "- ${it.title}: ${it.url}" }
        val output = buildString {
            append(answer)
            if (sourceLines.isNotBlank()) append("\n\nSources:\n$sourceLines")
        }
        gateway.remember(output, agentId, kind = "search", importance = 0.7f)
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "Search Agent synthesized ${search.sources.size} Tavily source(s) with One LLM.",
            output = output,
            usedTools = task.requiredTools,
            artifact = v2Artifact(
                "search",
                mapOf(
                    "sources" to search.sources.size,
                    "urls" to search.sources.take(8).map { it.url },
                    "titles" to search.sources.take(8).map { it.title }
                )
            )
        )
    }
}

private class V2RuntimeRagExecutor(
    private val meta: V2AgentMeta,
    private val gateway: V2RuntimeGateway
) : V2RuntimeAgentExecutor {
    override val agentId: V2AgentId = meta.id

    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        val chunks = gateway.retrieveRag(context.contextualInput).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "RAG Agent failed to retrieve local context.",
                output = e.message ?: "RAG retrieval failed",
                usedTools = task.requiredTools
            )
        }
        if (chunks.isEmpty()) {
            return V2AgentExecution(
                status = V2AgentRunStatus.NEEDS_INPUT,
                summary = "RAG Agent found no local document chunks.",
                output = "本地知识库没有可用文档片段，请先上传文档。",
                usedTools = task.requiredTools,
                followUps = listOf("请先在文档页上传 PDF、Word 或文本资料。")
            )
        }
        val contextText = chunks.joinToString("\n\n") { chunk ->
            "[${chunk.documentId}#${chunk.chunkIndex}, score=${"%.2f".format(chunk.score)}]\n${chunk.text}"
        }
        val answer = gateway.streamWithMemory(
            agentId = agentId,
            question = context.contextualInput,
            systemPrompt = "你是 GeoScientist RAG Agent，只能基于给定文档片段回答，并在结尾列出来源。",
            userPrompt = "问题：${context.contextualInput}\n\n文档片段：\n$contextText",
            imageBase64 = context.imageBase64,
            imageMimeType = context.imageMimeType,
            onContent = { chunk -> context.onContent(agentId, chunk) }
        ).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "RAG Agent retrieved chunks but One LLM synthesis failed.",
                output = e.message ?: "One LLM synthesis failed",
                usedTools = task.requiredTools
            )
        }
        gateway.remember(answer, agentId, kind = "rag", importance = 0.65f)
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "RAG Agent synthesized answer from ${chunks.size} chunk(s).",
            output = answer,
            usedTools = task.requiredTools,
            artifact = v2Artifact(
                "rag",
                mapOf(
                    "chunks" to chunks.size,
                    "documents" to chunks.map { it.documentId }.distinct(),
                    "minScore" to (chunks.minOfOrNull { it.score } ?: 0f)
                )
            )
        )
    }
}

private class V2RuntimeResearchExecutor(
    private val meta: V2AgentMeta,
    private val gateway: V2RuntimeGateway
) : V2RuntimeAgentExecutor {
    override val agentId: V2AgentId = meta.id

    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        val search = gateway.search(context.contextualInput).getOrNull()
        val chunks = gateway.retrieveRag(context.contextualInput, topK = 4).getOrDefault(emptyList())
        val evidence = buildString {
            search?.sources?.take(5)?.forEachIndexed { index, source ->
                append("Web ${index + 1}: ${source.title}\n${source.content.take(500)}\n\n")
            }
            chunks.forEachIndexed { index, chunk ->
                append("Doc ${index + 1}: ${chunk.documentId}#${chunk.chunkIndex}\n${chunk.text.take(700)}\n\n")
            }
        }
        val answer = gateway.streamWithMemory(
            agentId = agentId,
            question = context.contextualInput,
            systemPrompt = "你是 GeoScientist Research Agent，负责把联网资料和本地文档整理为研究分析。",
            userPrompt = "研究问题：${context.contextualInput}\n\n证据：\n${evidence.ifBlank { "暂无外部证据，请基于问题给出研究拆解框架。" }}",
            imageBase64 = context.imageBase64,
            imageMimeType = context.imageMimeType,
            onContent = { chunk -> context.onContent(agentId, chunk) }
        ).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "Research Agent could not synthesize with One LLM.",
                output = e.message ?: "One LLM synthesis failed",
                usedTools = task.requiredTools
            )
        }
        gateway.remember(answer, agentId, kind = "research", importance = 0.75f)
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "Research Agent synthesized web and local evidence.",
            output = answer,
            usedTools = task.requiredTools,
            artifact = v2Artifact(
                "research",
                mapOf(
                    "webSources" to (search?.sources?.size ?: 0),
                    "chunks" to chunks.size,
                    "sourceUrls" to (search?.sources?.take(5)?.map { it.url } ?: emptyList<String>()),
                    "documents" to chunks.map { it.documentId }.distinct()
                )
            )
        )
    }
}

private class V2RuntimeTaskExecutor(
    private val meta: V2AgentMeta,
    private val gateway: V2RuntimeGateway
) : V2RuntimeAgentExecutor {
    override val agentId: V2AgentId = meta.id

    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        if (isTaskListIntent(task.input)) {
            val tasks = gateway.listTasks(status = "open", limit = 20).getOrElse { e ->
                return V2AgentExecution(
                    status = V2AgentRunStatus.BLOCKED,
                    summary = "Task Agent could not list saved tasks.",
                    output = e.message ?: "Task listing failed",
                    usedTools = task.requiredTools
                )
            }
            if (tasks.isEmpty()) {
                return V2AgentExecution(
                    status = V2AgentRunStatus.COMPLETED,
                    summary = "Task Agent found no open tasks.",
                    output = "当前没有未完成任务。",
                    usedTools = task.requiredTools,
                    artifact = v2Artifact(
                        "task_list",
                        mapOf(
                            "count" to 0,
                            "status" to "open"
                        )
                    )
                )
            }
            val output = tasks.joinToString("\n") {
                "- [${it.status}] ${it.title} (${it.sourceAgent.wireName}, priority=${it.priority}, id=${it.id})"
            }
            return V2AgentExecution(
                status = V2AgentRunStatus.COMPLETED,
                summary = "Task Agent listed ${tasks.size} open task(s).",
                output = output,
                usedTools = task.requiredTools,
                artifact = v2Artifact(
                    "task_list",
                    mapOf(
                        "count" to tasks.size,
                        "status" to "open",
                        "ids" to tasks.map { it.id }
                    )
                )
            )
        }
        val plan = gateway.streamWithMemory(
            agentId = agentId,
            question = context.contextualInput,
            systemPrompt = "你是 GeoScientist Task Agent。把用户请求拆解为可执行待办，输出简短标题和步骤。",
            userPrompt = context.contextualInput,
            imageBase64 = context.imageBase64,
            imageMimeType = context.imageMimeType,
            onContent = { chunk -> context.onContent(agentId, chunk) }
        ).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "Task Agent could not call One LLM.",
                output = e.message ?: "One LLM call failed",
                usedTools = task.requiredTools
            )
        }
        val record = gateway.saveTask(
            title = extractRuntimeTitle(task.input, "GeoScientist 任务"),
            description = plan,
            sourceAgent = agentId,
            priority = 4,
            relatedArtifact = "task:${task.id}"
        ).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "Task Agent could not persist task.",
                output = e.message ?: "Task persistence failed",
                usedTools = task.requiredTools
            )
        }
        gateway.remember(plan, agentId, kind = "task", importance = 0.7f)
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "Task Agent saved structured task ${record.id}.",
            output = plan,
            usedTools = task.requiredTools,
            artifact = "task:id=${record.id};title=${record.title}"
        )
    }
}

private class V2RuntimeScheduleExecutor(
    private val meta: V2AgentMeta,
    private val gateway: V2RuntimeGateway,
    private val clock: V2RuntimeClock
) : V2RuntimeAgentExecutor {
    override val agentId: V2AgentId = meta.id

    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        val timeWindow = parseV2TimeWindow(task.input, clock)
        val schedule = gateway.streamWithMemory(
            agentId = agentId,
            question = context.contextualInput,
            systemPrompt = "你是 GeoScientist Schedule Agent。把用户目标安排成时间块计划，并标出可以写入日历的具体事项、开始时间、持续时间和优先级。",
            userPrompt = context.contextualInput,
            imageBase64 = context.imageBase64,
            imageMimeType = context.imageMimeType,
            onContent = { chunk -> context.onContent(agentId, chunk) }
        ).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "Schedule Agent could not call One LLM.",
                output = e.message ?: "One LLM call failed",
                usedTools = task.requiredTools
            )
        }
        val record = gateway.saveTask(
            title = extractRuntimeTitle(task.input, "GeoScientist 日程安排"),
            description = schedule,
            sourceAgent = agentId,
            priority = 4,
            relatedArtifact = "schedule:${task.id}"
        ).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "Schedule Agent could not persist schedule task.",
                output = e.message ?: "Schedule persistence failed",
                usedTools = task.requiredTools
            )
        }
        val calendarArtifact = if (timeWindow != null) {
            gateway.prepareCalendarEvent(
                V2CalendarEventRequest(
                    title = extractRuntimeTitle(task.input, "GeoScientist 日程安排"),
                    description = schedule,
                    beginTimeMillis = timeWindow.beginMillis,
                    endTimeMillis = timeWindow.endMillis,
                    timeZone = clock.timeZone().id
                )
            ).getOrNull()
        } else {
            null
        }
        gateway.remember(schedule, agentId, kind = "schedule", importance = 0.7f)
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "Schedule Agent saved structured schedule ${record.id}.",
            output = schedule,
            usedTools = task.requiredTools,
            artifact = v2Artifact(
                "schedule",
                mapOf(
                    "taskId" to record.id,
                    "calendar" to (calendarArtifact != null),
                    "begin" to timeWindow?.beginMillis,
                    "end" to timeWindow?.endMillis,
                    "timezone" to (calendarArtifact?.timeZone ?: clock.timeZone().id)
                )
            ),
            followUps = if (calendarArtifact == null) listOf("补充具体日期和时间后可写入系统日历。") else emptyList()
        )
    }
}

private class V2RuntimeFileExecutor(
    private val meta: V2AgentMeta,
    private val gateway: V2RuntimeGateway
) : V2RuntimeAgentExecutor {
    override val agentId: V2AgentId = meta.id

    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        val docs = gateway.listDocuments().getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "File Agent could not list local documents.",
                output = e.message ?: "Document listing failed",
                usedTools = task.requiredTools
            )
        }
        if (docs.isEmpty()) {
            return V2AgentExecution(
                status = V2AgentRunStatus.NEEDS_INPUT,
                summary = "File Agent found no local documents.",
                output = "当前没有本地文档，请先上传文件。",
                usedTools = task.requiredTools,
                followUps = listOf("请在文档页上传 PDF、Word、Markdown 或文本文件。")
            )
        }
        val selected = selectDocumentForInput(docs, context.contextualInput)
        val action = fileActionFor(task.input)
        if (action != V2FileAction.LIST && selected == null) {
            return V2AgentExecution(
                status = V2AgentRunStatus.NEEDS_INPUT,
                summary = "File Agent needs a target document.",
                output = "请指定要${if (action == V2FileAction.DELETE) "删除" else "读取"}的文件名或文档 ID。",
                usedTools = task.requiredTools,
                followUps = listOf("当前文档：${docs.joinToString { it.name }}")
            )
        }
        if (action == V2FileAction.DELETE && selected != null) {
            val deletedName = gateway.deleteDocument(selected.id).getOrElse { e ->
                return V2AgentExecution(
                    status = V2AgentRunStatus.BLOCKED,
                    summary = "File Agent could not delete ${selected.name}.",
                    output = e.message ?: "Document delete failed",
                    usedTools = task.requiredTools
                )
            }
            gateway.remember("Deleted document: $deletedName", agentId, kind = "file", importance = 0.45f)
            return V2AgentExecution(
                status = V2AgentRunStatus.COMPLETED,
                summary = "File Agent deleted $deletedName.",
                output = "已删除文档：$deletedName",
                usedTools = task.requiredTools,
                artifact = v2Artifact(
                    "file",
                    mapOf(
                        "action" to "delete",
                        "selectedId" to selected.id,
                        "selectedName" to selected.name
                    )
                )
            )
        }
        if (action == V2FileAction.READ && selected != null) {
            val text = gateway.getDocumentText(selected.id).getOrElse { e ->
                return V2AgentExecution(
                    status = V2AgentRunStatus.BLOCKED,
                    summary = "File Agent could not read ${selected.name}.",
                    output = e.message ?: "Document read failed",
                    usedTools = task.requiredTools
                )
            }
            return V2AgentExecution(
                status = V2AgentRunStatus.COMPLETED,
                summary = "File Agent read ${selected.name}.",
                output = "文档：${selected.name}\n\n${text.take(4000)}",
                usedTools = task.requiredTools,
                artifact = v2Artifact(
                    "file",
                    mapOf(
                        "action" to "read",
                        "selectedId" to selected.id,
                        "selectedName" to selected.name,
                        "chars" to text.length
                    )
                )
            )
        }
        val output = docs.joinToString("\n") {
            "- ${it.name} (${it.type}, ${it.chunkCount} chunks, ${it.sizeBytes} bytes)"
        }
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = if (selected == null) {
                "File Agent listed ${docs.size} local document(s)."
            } else {
                "File Agent selected ${selected.name} from ${docs.size} local document(s)."
            },
            output = if (selected == null) {
                output
            } else {
                "已匹配文档：${selected.name}\n类型：${selected.type}\n分块：${selected.chunkCount}\n大小：${selected.sizeBytes} bytes\n\n全部文档：\n$output"
            },
            usedTools = task.requiredTools,
            artifact = v2Artifact(
                "file",
                mapOf(
                    "documents" to docs.size,
                    "action" to "list",
                    "selectedId" to selected?.id,
                    "selectedName" to selected?.name,
                    "selectedType" to selected?.type
                )
            )
        )
    }
}

private class V2RuntimePdfExecutor(
    private val meta: V2AgentMeta,
    private val gateway: V2RuntimeGateway
) : V2RuntimeAgentExecutor {
    override val agentId: V2AgentId = meta.id

    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        val docs = gateway.listDocuments().getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "PDF Agent could not inspect local documents.",
                output = e.message ?: "Document listing failed",
                usedTools = task.requiredTools
            )
        }
        val pdfDocs = docs.filter { it.type.equals("pdf", ignoreCase = true) }
        val pdf = selectDocumentForInput(pdfDocs, context.contextualInput) ?: pdfDocs.firstOrNull()
            ?: return V2AgentExecution(
                status = V2AgentRunStatus.NEEDS_INPUT,
                summary = "PDF Agent found no parsed PDF document.",
                output = "当前知识库没有已解析 PDF，请先上传 PDF。",
                usedTools = task.requiredTools,
                followUps = listOf("请上传需要解析或总结的 PDF。")
            )
        val text = gateway.getDocumentText(pdf.id).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "PDF Agent could not read parsed PDF text.",
                output = e.message ?: "PDF content loading failed",
                usedTools = task.requiredTools
            )
        }
        val answer = gateway.streamWithMemory(
            agentId = agentId,
            question = context.contextualInput,
            systemPrompt = "你是 GeoScientist PDF Agent。基于已解析 PDF 文本提取重点、结构和可引用结论。",
            userPrompt = "用户请求：${context.contextualInput}\n\nPDF：${pdf.name}\n\n文本：\n${text.take(5000)}",
            imageBase64 = context.imageBase64,
            imageMimeType = context.imageMimeType,
            onContent = { chunk -> context.onContent(agentId, chunk) }
        ).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "PDF Agent could not synthesize with One LLM.",
                output = e.message ?: "One LLM synthesis failed",
                usedTools = task.requiredTools
            )
        }
        gateway.remember(answer, agentId, kind = "pdf", importance = 0.65f)
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "PDF Agent analyzed ${pdf.name}.",
            output = answer,
            usedTools = task.requiredTools,
            artifact = v2Artifact(
                "pdf",
                mapOf(
                    "document" to pdf.id,
                    "name" to pdf.name,
                    "chars" to text.length
                )
            )
        )
    }
}

private class V2RuntimeEmailExecutor(
    private val meta: V2AgentMeta,
    private val gateway: V2RuntimeGateway
) : V2RuntimeAgentExecutor {
    override val agentId: V2AgentId = meta.id

    override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
        val request = parseEmailRequestFallback(task.input, context.history)
            ?: buildEmailRequestWithAi(task.input, context.history, gateway)
            ?: return V2AgentExecution(
                status = V2AgentRunStatus.NEEDS_INPUT,
                summary = "Email Agent needs a recipient address.",
                output = "请补充收件人邮箱、主题和正文。",
                usedTools = task.requiredTools,
                followUps = listOf("示例：给 test@example.com 发送邮件，主题：进度，内容：今天已完成。")
            )
        val result = gateway.sendEmail(request).getOrElse { e ->
            return V2AgentExecution(
                status = V2AgentRunStatus.BLOCKED,
                summary = "Email Agent could not send email through JavaMail SMTP.",
                output = e.message ?: "邮件发送失败",
                usedTools = task.requiredTools
            )
        }
        gateway.remember("${request.to}\n${request.subject}\n${request.content}", agentId, kind = "email", importance = 0.6f)
        val contentTitle = summarizeEmailContent(request.content)
        return V2AgentExecution(
            status = V2AgentRunStatus.COMPLETED,
            summary = "Email Agent sent message to ${request.to}.",
            output = "已经将${contentTitle}等内容发送给${request.to}✅",
            usedTools = task.requiredTools,
            artifact = v2Artifact(
                "email",
                mapOf(
                    "to" to request.to,
                    "subject" to request.subject,
                    "delivery" to result
                )
            )
        )
    }
}

private val RE_TITLE_NOISE = Regex("""(请|帮我|给我|安排|生成|创建|制定|任务|待办|会议|旅行|旅游|行程)""")
private val RE_WHITESPACE = Regex("""\s+""")

private fun extractRuntimeTitle(input: String, fallback: String): String {
    val cleaned = input
        .replace(RE_TITLE_NOISE, " ")
        .replace(RE_WHITESPACE, " ")
        .trim(' ', '，', ',', '。', '.', '：', ':')
    return cleaned.take(32).ifBlank { fallback }
}

private fun selectDocumentForInput(
    documents: List<V2RuntimeDocumentRecord>,
    input: String
): V2RuntimeDocumentRecord? {
    val normalizedInput = input.lowercase()
    return documents.firstOrNull {
        normalizedInput.contains(it.id.lowercase()) || normalizedInput.contains(it.name.lowercase())
    } ?: documents.firstOrNull {
        val baseName = it.name.substringBeforeLast(".").lowercase()
        baseName.length >= 2 && normalizedInput.contains(baseName)
    }
}

private enum class V2FileAction {
    LIST,
    READ,
    DELETE
}

private fun fileActionFor(input: String): V2FileAction {
    val normalized = input.lowercase()
    return when {
        Regex("""(删除|移除|删掉|delete|remove)""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> V2FileAction.DELETE
        Regex("""(读取|查看|打开|显示|内容|read|show|open)""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> V2FileAction.READ
        else -> V2FileAction.LIST
    }
}

private fun isTaskListIntent(input: String): Boolean {
    val normalized = input.lowercase()
    val hasTaskWord = Regex("""(任务|待办|todo|清单|事项)""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
    val hasListWord = Regex("""(查看|列出|显示|有哪些|当前|list|show)""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
    val hasCreateWord = Regex("""(创建|新增|添加|保存|安排|制定|生成)""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
    return hasTaskWord && hasListWord && !hasCreateWord
}

data class V2TimeWindow(
    val beginMillis: Long,
    val endMillis: Long
)

fun parseV2TimeWindow(
    input: String,
    clock: V2RuntimeClock = V2SystemClock,
    defaultDurationMillis: Long = 60L * 60L * 1000L
): V2TimeWindow? {
    val normalized = input.trim()
    if (normalized.isBlank()) return null
    val delay = parseRelativeDelayMillis(normalized)
    if (delay != null) {
        val begin = clock.nowMillis() + delay
        return V2TimeWindow(begin, begin + defaultDurationMillis)
    }

    val calendar = Calendar.getInstance(clock.timeZone()).apply {
        timeInMillis = clock.nowMillis()
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    var hasDateHint = applyRelativeDate(normalized, calendar)
    val weekdayApplied = applyWeekday(normalized, calendar)
    hasDateHint = hasDateHint || weekdayApplied
    val time = parseClockTime(normalized)
    val hour = time?.first ?: defaultHourFor(normalized)
    val minute = time?.second ?: 0
    val hasTimeHint = time != null || hasTimePeriodHint(normalized)

    if (!hasDateHint && !hasTimeHint) return null
    calendar.set(Calendar.HOUR_OF_DAY, hour)
    calendar.set(Calendar.MINUTE, minute)
    if (!hasDateHint && calendar.timeInMillis <= clock.nowMillis()) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    val begin = calendar.timeInMillis
    return V2TimeWindow(begin, begin + defaultDurationMillis)
}

fun rankV2RagChunksByKeywords(
    question: String,
    chunks: List<V2RuntimeRagChunk>,
    topK: Int
): List<V2RuntimeRagChunk> {
    if (question.isBlank() || chunks.isEmpty()) return emptyList()
    val tokens = v2KeywordTokens(question)
    if (tokens.isEmpty()) return chunks.take(topK.coerceIn(1, 20)).map { it.copy(score = 0.1f) }
    return chunks
        .mapNotNull { chunk ->
            val lowerText = chunk.text.lowercase()
            val score = tokens.sumOf { token ->
                val lowerToken = token.lowercase()
                val containsScore = if (lowerText.contains(lowerToken)) 1.0 else 0.0
                containsScore + lowerText.countV2Substring(lowerToken) * 0.25
            }.toFloat()
            if (score > 0f) chunk.copy(score = score) else null
        }
        .sortedWith(compareByDescending<V2RuntimeRagChunk> { it.score }.thenBy { it.documentId }.thenBy { it.chunkIndex })
        .take(topK.coerceIn(1, 20))
}

private fun parseRelativeDelayMillis(input: String): Long? {
    Regex("""(\d+)\s*(分钟|分|minute|minutes)\s*(后|以后)?""", RegexOption.IGNORE_CASE)
        .find(input)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?.let { return it * 60_000L }
    Regex("""(\d+)\s*(小时|hour|hours)\s*(后|以后)?""", RegexOption.IGNORE_CASE)
        .find(input)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?.let { return it * 3_600_000L }
    Regex("""(\d+)\s*(天|day|days)\s*(后|以后)?""", RegexOption.IGNORE_CASE)
        .find(input)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?.let { return it * 24L * 3_600_000L }
    return null
}

private fun v2KeywordTokens(input: String): List<String> =
    input
        .replace(Regex("[，。！？、；：\"'（）【】《》\\s]+"), " ")
        .split(" ")
        .flatMap { part ->
            val trimmed = part.trim()
            when {
                trimmed.length >= 2 && trimmed.any { it.isLetterOrDigit() } -> listOf(trimmed)
                trimmed.length >= 4 -> trimmed.windowed(2)
                trimmed.length >= 2 -> listOf(trimmed)
                else -> emptyList()
            }
        }
        .distinct()

private fun String.countV2Substring(sub: String): Int {
    if (sub.isBlank()) return 0
    var count = 0
    var idx = 0
    while (idx <= length - sub.length) {
        idx = indexOf(sub, idx)
        if (idx < 0) break
        count++
        idx += sub.length
    }
    return count
}

private fun applyRelativeDate(input: String, calendar: Calendar): Boolean {
    return when {
        input.contains("大后天") -> {
            calendar.add(Calendar.DAY_OF_YEAR, 3)
            true
        }
        input.contains("后天") -> {
            calendar.add(Calendar.DAY_OF_YEAR, 2)
            true
        }
        input.contains("明天") || input.contains("明早") || input.contains("明晚") -> {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            true
        }
        input.contains("今天") || input.contains("今晚") -> true
        else -> false
    }
}

private fun applyWeekday(input: String, calendar: Calendar): Boolean {
    val match = Regex("""(?:下?周|星期|礼拜|周)([一二三四五六日天])""").find(input) ?: return false
    val target = when (match.groupValues[1]) {
        "一" -> Calendar.MONDAY
        "二" -> Calendar.TUESDAY
        "三" -> Calendar.WEDNESDAY
        "四" -> Calendar.THURSDAY
        "五" -> Calendar.FRIDAY
        "六" -> Calendar.SATURDAY
        else -> Calendar.SUNDAY
    }
    var days = (target - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
    if (days == 0 || match.value.startsWith("下周")) days += 7
    calendar.add(Calendar.DAY_OF_YEAR, days)
    return true
}

private fun parseClockTime(input: String): Pair<Int, Int>? {
    Regex("""(\d{1,2})[:：](\d{1,2})""")
        .find(input)
        ?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            return normalizeHour(input, hour) to minute.coerceIn(0, 59)
        }
    Regex("""(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分?)?""")
        .find(input)
        ?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            return normalizeHour(input, hour) to minute.coerceIn(0, 59)
        }
    return null
}

private fun normalizeHour(input: String, hour: Int): Int {
    val safeHour = hour.coerceIn(0, 23)
    val isAfternoon = input.contains("下午") || input.contains("晚上") || input.contains("今晚") || input.contains("明晚")
    return if (isAfternoon && safeHour in 1..11) safeHour + 12 else safeHour
}

private fun defaultHourFor(input: String): Int = when {
    input.contains("凌晨") -> 1
    input.contains("早") || input.contains("上午") -> 9
    input.contains("中午") -> 12
    input.contains("下午") -> 14
    input.contains("晚") -> 20
    else -> 9
}

private fun hasTimePeriodHint(input: String): Boolean =
    listOf("凌晨", "早", "上午", "中午", "下午", "晚上", "今晚", "明晚").any { input.contains(it) }

private suspend fun buildEmailRequestWithAi(
    input: String,
    history: List<V2RuntimeHistoryMessage>,
    gateway: V2RuntimeGateway
): V2EmailRequest? {
    val prompt = buildEmailDecisionPrompt(input, history)
    val json = gateway.completeWithOneLlm(
        agentId = V2AgentId.EMAIL,
        systemPrompt = EMAIL_DECISION_SYSTEM_PROMPT,
        userPrompt = prompt
    ).getOrNull() ?: return null
    return parseAiEmailDecision(json)
}

private fun buildEmailDecisionPrompt(
    input: String,
    history: List<V2RuntimeHistoryMessage>
): String = buildString {
    appendLine("当前用户指令：")
    appendLine(input)
    appendLine()
    appendLine("最近对话历史，按时间顺序排列：")
    if (history.isEmpty()) {
        appendLine("(无)")
    } else {
        history.takeLast(8).forEachIndexed { index, message ->
            val role = when (message.role) {
                "user" -> "用户"
                "assistant" -> "GeoScientist"
                else -> message.role
            }
            appendLine("${index + 1}. $role：${message.content}")
        }
    }
    appendLine()
    appendLine("请判断要发送的邮件收件人、主题和正文。")
}

private fun parseAiEmailDecision(raw: String): V2EmailRequest? {
    val jsonText = extractJsonObject(raw) ?: return null
    return runCatching {
        val obj = JsonParser.parseString(jsonText).asJsonObject
        val needsInput = obj.get("needs_input")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        if (needsInput) return null
        val to = obj.get("to")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
        val subject = obj.get("subject")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
        val content = obj.get("content")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
        if (!to.isValidEmailAddress() || content.isBlank()) return null
        V2EmailRequest(
            to = to,
            subject = subject.ifBlank { "来自 GeoScientist 的邮件" },
            content = content
        )
    }.getOrNull()
}

private fun extractJsonObject(raw: String): String? =
    Regex("""(?s)```(?:json)?\s*(\{.*?})\s*```""")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: Regex("""(?s)\{.*}""").find(raw)?.value?.trim()

private fun parseEmailRequestFallback(
    input: String,
    history: List<V2RuntimeHistoryMessage> = emptyList()
): V2EmailRequest? {
    val to = Regex("""([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""")
        .find(input)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: return null
    val subject = Regex("""(?:主题[:：]?\s*)([^。；;\n]+)""")
        .find(input)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "来自 GeoScientist 的邮件"
    val explicitContent = Regex("""(?:内容|正文)\s*(?:[:：]|为|是)\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
        .find(input)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val referencedContent = if (input.referencesPreviousAssistantContent() || input.isEmailRecipientCorrection()) {
        history.previousConversationTurnContent()
    } else {
        null
    }
    val content = explicitContent ?: referencedContent ?: return null
    return V2EmailRequest(to, subject, content)
}

private fun String.isEmailRecipientCorrection(): Boolean {
    val normalized = trim()
    return listOf("发错", "发错了", "不是发给", "改发", "改成发给", "应该发给", "是发给").any {
        normalized.contains(it)
    }
}

private fun String.isValidEmailAddress(): Boolean =
    matches(Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"""))

private val EMAIL_DECISION_SYSTEM_PROMPT = """
    你是 GeoScientist 的邮件发送决策器。你的任务不是写闲聊回复，而是根据当前用户指令和最近对话历史，决定即将通过 SMTP 发送的邮件结构。

    只输出 JSON，不要输出 Markdown 或解释。JSON 字段固定为：
    {
      "to": "收件人邮箱，缺失则为空字符串",
      "subject": "邮件主题，缺失则给出简短中文主题",
      "content": "邮件正文",
      "needs_input": false,
      "reason": "一句极短原因"
    }

    规则：
    1. 邮箱地址必须来自当前用户指令或历史里明确出现的邮箱，不要编造。
    2. 如果用户说“上述内容、当前内容、这段对话、对话记录、聊天记录、这些内容”等，content 应包含最近一轮相关用户问题和 GeoScientist 回答，格式为“用户：...\n\nGeoScientist：...”。
    3. 如果用户是在纠错，例如“发错了，是发给 xxx@qq.com”，通常表示沿用上一封/上一轮要发送的正文，只修正收件人。
    4. 如果用户显式提供“内容/正文”，优先使用显式正文。
    5. 如果缺少可发送正文或缺少有效收件人，needs_input 为 true，to/subject/content 能填多少填多少。
    6. 不要把“发送给某邮箱”“发错了”等操作指令本身当作正文。
""".trimIndent()

private fun String.referencesPreviousAssistantContent(): Boolean {
    val normalized = trim()
    return listOf(
        "上述内容",
        "上面内容",
        "以上内容",
        "上述",
        "上面",
        "以上",
        "这些内容",
        "这些新闻",
        "这段内容",
        "这段对话",
        "当前内容",
        "当前这段",
        "当前对话",
        "对话记录",
        "聊天记录",
        "刚才的内容"
    )
        .any { normalized.contains(it) }
}

private fun List<V2RuntimeHistoryMessage>.previousConversationTurnContent(): String? {
    val assistantIndex = indexOfLast { it.role == "assistant" && it.content.isNotBlank() }
    if (assistantIndex < 0) return null
    val assistant = this[assistantIndex].content.trim()
    val user = subList(0, assistantIndex)
        .asReversed()
        .firstOrNull { it.role == "user" && it.content.isNotBlank() }
        ?.content
        ?.trim()
    return if (user.isNullOrBlank()) {
        assistant
    } else {
        "用户：$user\n\nGeoScientist：$assistant"
    }
}

private fun summarizeEmailContent(content: String): String {
    val firstMeaningfulLine = content
        .lineSequence()
        .map { it.trim().trimStart('#', '-', '*', '•', ' ', '\t') }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val compact = firstMeaningfulLine
        .replace(Regex("""\[[0-9]+]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '。', '，', ',', '.', ':', '：')
    return compact.take(24).ifBlank { "上述" }
}
