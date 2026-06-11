package com.geoagent.ui.chat

import com.geoagent.agent.v2.V2AgentExecution
import com.geoagent.agent.v2.V2AgentId
import com.geoagent.agent.v2.V2AgentRunStatus
import com.geoagent.agent.v2.V2AgentRegistry
import com.geoagent.agent.v2.V2ExecutionContext
import com.geoagent.agent.v2.V2Orchestrator
import com.geoagent.agent.v2.V2PlanTask
import com.geoagent.agent.v2.V2RuntimeAgentExecutor
import com.geoagent.agent.v2.V2RuntimeAgentExecutorRegistry
import com.geoagent.agent.v2.V2RuntimeHistoryMessage
import com.geoagent.agent.v2.V2RuntimeOrchestrator
import com.geoagent.agent.v2.V2ToolRegistry
import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatResponse
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.data.api.dto.EmailHistoryResponse
import com.geoagent.data.api.dto.EmailSendResponse
import com.geoagent.data.api.dto.SourceDto
import com.geoagent.data.api.dto.UserResponse
import com.geoagent.domain.model.Conversation
import com.geoagent.domain.model.Message
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatManagerTest {

    @Test
    fun webSearchSourcesAreAttachedOnlyAfterAnswerDone() = runBlocking {
        val sourcesEmitted = CompletableDeferred<Unit>()
        val finishStream = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(
            events = flow {
                emit(ChatEvent.Sources(listOf(SourceDto(content = "来源内容", source = "来源一", url = "https://example.com/a"))))
                sourcesEmitted.complete(Unit)
                finishStream.await()
                emit(ChatEvent.Content("综合回答 [1]"))
                emit(ChatEvent.Done())
            }
        )
        val manager = ChatManager(
            scope = this,
            chatRepository = repository,
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emptyRuntimeOrchestrator()
        )

        manager.setWebSearchEnabled(true)
        manager.sendMessage("最近有什么新闻")
        sourcesEmitted.await()

        val beforeDone = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertTrue(beforeDone.sources.isEmpty())

        finishStream.complete(Unit)
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }

        val assistant = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("综合回答 [1]", assistant.content)
        assertEquals(1, assistant.sources.size)
        assertFalse(manager.uiState.value.isLoading)
    }

    @Test
    fun ragKnowledgeBaseSourcesKeepDocumentIdentityAfterDone() = runBlocking {
        val sourcesEmitted = CompletableDeferred<Unit>()
        val finishStream = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(
            events = flow {
                emit(
                    ChatEvent.Sources(
                        listOf(
                            SourceDto(
                                content = "文档片段",
                                source = "report.docx",
                                type = "knowledge_base",
                                document_id = "doc-1",
                                document_name = "report.docx"
                            )
                        )
                    )
                )
                sourcesEmitted.complete(Unit)
                finishStream.await()
                emit(ChatEvent.Content("知识库回答 [1]"))
                emit(ChatEvent.Done())
            }
        )
        val manager = ChatManager(
            scope = this,
            chatRepository = repository,
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emptyRuntimeOrchestrator()
        )

        manager.setMode(com.geoagent.domain.model.ChatMode.RAG)
        manager.sendMessage("校区里有哪些学生社团")
        withTimeout(1_000L) {
            sourcesEmitted.await()
        }

        val beforeDone = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("doc-1", beforeDone.sources.single().document_id)
        assertEquals("knowledge_base", beforeDone.sources.single().type)

        finishStream.complete(Unit)
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }

        val assistant = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("rag", repository.streamRequests.single().mode)
        assertEquals("知识库回答 [1]", assistant.content)
        assertEquals("knowledge_base", assistant.sources.single().type)
        assertEquals("doc-1", assistant.sources.single().document_id)
        assertEquals("report.docx", assistant.sources.single().source)
    }

    @Test
    fun ragKnowledgeBaseSourcesAttachBeforeDoneForSourcePill() = runBlocking {
        val sourcesEmitted = CompletableDeferred<Unit>()
        val finishStream = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(
            events = flow {
                emit(ChatEvent.Content("知识库回答 [1]"))
                emit(
                    ChatEvent.Sources(
                        listOf(
                            SourceDto(
                                content = "文档片段",
                                source = "report.docx",
                                type = "knowledge_base",
                                document_id = "doc-1",
                                document_name = "report.docx"
                            )
                        )
                    )
                )
                sourcesEmitted.complete(Unit)
                finishStream.await()
                emit(ChatEvent.Done())
            }
        )
        val manager = ChatManager(
            scope = this,
            chatRepository = repository,
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emptyRuntimeOrchestrator()
        )

        manager.setMode(com.geoagent.domain.model.ChatMode.RAG)
        manager.sendMessage("根据知识库回答")
        withTimeout(1_000L) {
            sourcesEmitted.await()
        }

        val assistantBeforeDone = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("doc-1", assistantBeforeDone.sources.single().document_id)
        assertEquals("knowledge_base", assistantBeforeDone.sources.single().type)

        finishStream.complete(Unit)
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }

        val assistantAfterDone = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals(1, assistantAfterDone.sources.size)
        assertEquals("doc-1", assistantAfterDone.sources.single().document_id)
    }

    @Test
    fun explicitEmailSendUsesEmailLoadingEvenWhenWebSearchEnabled() = runBlocking {
        val manager = ChatManager(
            scope = this,
            chatRepository = FakeChatRepository(events = flow { }),
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emailRuntimeOrchestrator()
        )

        manager.setWebSearchEnabled(true)
        manager.sendMessage("把这些新闻 发送给1149201272@qq.com")

        assertEquals("邮件发送中...", manager.uiState.value.statusMessage)
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }

        val assistant = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("email", assistant.agentName)
        assertTrue(assistant.content.contains("已经将NBA 总决赛战况等内容发送给1149201272@qq.com✅"))
    }

    @Test
    fun emailRuntimeReceivesPreviousAssistantContentForAboveContentRequest() = runBlocking {
        val executor = FakeEmailRuntimeExecutor()
        val manager = ChatManager(
            scope = this,
            chatRepository = FakeChatRepository(
                events = flow {
                    emit(ChatEvent.Content("NBA 总决赛战况：雷霆与步行者正在争夺冠军。"))
                    emit(ChatEvent.Done())
                }
            ),
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emailRuntimeOrchestrator(executor)
        )

        manager.setWebSearchEnabled(true)
        manager.sendMessage("NBA 总决赛战况")
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }
        manager.sendMessage("把上述内容 发送给1149201272@qq.com")
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }

        assertTrue(executor.lastHistory.any { it.role == "assistant" && it.content.contains("NBA 总决赛战况") })
        val assistant = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertTrue(assistant.content.contains("已经将NBA 总决赛战况等内容发送给1149201272@qq.com✅"))
    }

    @Test
    fun currentUserMessageIsNotDuplicatedIntoStreamHistory() = runBlocking {
        val repository = FakeChatRepository(
            events = flow {
                emit(ChatEvent.Content("你好！很高兴为你服务。"))
                emit(ChatEvent.Done())
            }
        )
        val manager = ChatManager(
            scope = this,
            chatRepository = repository,
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emptyRuntimeOrchestrator()
        )

        manager.sendMessage("你好")
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }

        val request = repository.streamRequests.single()
        assertFalse(request.history.any { it.role == Message.ROLE_USER && it.content == "你好" })
    }

    @Test
    fun webSearchRequestKeepsPreviousQaInSameConversationHistory() = runBlocking {
        val repository = FakeChatRepository(
            events = flow {
                emit(ChatEvent.Content("回答"))
                emit(ChatEvent.Done())
            }
        )
        val manager = ChatManager(
            scope = this,
            chatRepository = repository,
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emptyRuntimeOrchestrator()
        )

        manager.sendMessage("这是什么")
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }

        manager.setWebSearchEnabled(true)
        manager.sendMessage("最近它有哪些热梗")
        coroutineContext[Job]?.children?.toList().orEmpty().forEach { it.join() }

        val request = repository.streamRequests.last()
        assertTrue(request.web_search == true)
        assertTrue(request.history.any { it.role == Message.ROLE_USER && it.content == "这是什么" })
        assertTrue(request.history.any { it.role == Message.ROLE_ASSISTANT && it.content == "回答" })
        assertFalse(request.history.any { it.role == Message.ROLE_USER && it.content == "最近它有哪些热梗" })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun burstyContentAndThinkingChunksAreBufferedUntilNextRenderFrame() = runTest {
        val chunksEmitted = CompletableDeferred<Unit>()
        val finishStream = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(
            events = flow {
                emit(ChatEvent.Thinking("我"))
                emit(ChatEvent.Thinking("正在"))
                emit(ChatEvent.Content("你"))
                emit(ChatEvent.Content("好"))
                chunksEmitted.complete(Unit)
                finishStream.await()
                emit(ChatEvent.Done())
            }
        )
        val manager = ChatManager(
            scope = this,
            chatRepository = repository,
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emptyRuntimeOrchestrator()
        )

        manager.setDeepThinkingEnabled(true)
        manager.sendMessage("你好")
        runCurrent()
        chunksEmitted.await()

        val beforeFrame = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("", beforeFrame.content)
        assertEquals("", beforeFrame.thinkingContent)

        advanceTimeBy(40L)
        runCurrent()

        val afterFrame = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("", afterFrame.content)
        assertEquals("我正在", afterFrame.thinkingContent)

        advanceTimeBy(40L)
        runCurrent()

        val afterReveal = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("你好", afterReveal.content)
        assertEquals("我正在", afterReveal.thinkingContent)
        assertTrue(afterReveal.thinkingFinishedAt != null)

        finishStream.complete(Unit)
        runCurrent()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun thinkingTailRendersBeforeAnswerContentStarts() = runTest {
        val contentEmitted = CompletableDeferred<Unit>()
        val finishStream = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(
            events = flow {
                emit(ChatEvent.Thinking("正在分析问题"))
                emit(ChatEvent.Thinking("，准备给出结论"))
                emit(ChatEvent.Content("回答"))
                contentEmitted.complete(Unit)
                finishStream.await()
                emit(ChatEvent.Done())
            }
        )
        val manager = ChatManager(
            scope = this,
            chatRepository = repository,
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emptyRuntimeOrchestrator()
        )

        manager.setDeepThinkingEnabled(true)
        manager.sendMessage("解释一下")
        runCurrent()
        contentEmitted.await()
        advanceTimeBy(40L)
        runCurrent()

        val beforeAnswer = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("", beforeAnswer.content)
        assertEquals("正在分析问题，准备给出结论", beforeAnswer.thinkingContent)
        assertTrue(beforeAnswer.thinkingFinishedAt == null)

        advanceTimeBy(40L)
        runCurrent()

        val afterAnswer = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("回答", afterAnswer.content)
        assertEquals("正在分析问题，准备给出结论", afterAnswer.thinkingContent)
        assertTrue(afterAnswer.thinkingFinishedAt != null)
        assertTrue(manager.uiState.value.isLoading)

        finishStream.complete(Unit)
        runCurrent()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun burstyV2AgentChunksAreBufferedUntilNextRenderFrame() = runTest {
        val executor = FakeStreamingSearchRuntimeExecutor()
        val manager = ChatManager(
            scope = this,
            chatRepository = FakeChatRepository(events = flow { }),
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = searchRuntimeOrchestrator(executor),
            streamDispatcher = StandardTestDispatcher(testScheduler)
        )

        manager.sendMessage("搜索最新地质新闻")
        runCurrent()
        executor.chunksEmitted.await()

        assertFalse(
            manager.uiState.value.messages.any {
                it.role == Message.ROLE_ASSISTANT && it.content == "智能搜索"
            }
        )

        advanceTimeBy(40L)
        runCurrent()

        val streamed = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("search", streamed.agentName)
        assertEquals("智能搜索", streamed.content)

        executor.finish.complete(Unit)
        runCurrent()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun webSearchStatusAppearsAsCompactThinkingProgress() = runTest {
        val statusEmitted = CompletableDeferred<Unit>()
        val finishStream = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(
            events = flow {
                emit(ChatEvent.Status("正在检索网络来源…"))
                statusEmitted.complete(Unit)
                finishStream.await()
                emit(ChatEvent.Content("搜索完成"))
                emit(ChatEvent.Done())
            }
        )
        val manager = ChatManager(
            scope = this,
            chatRepository = repository,
            authRepository = FakeAuthRepository(),
            v2Orchestrator = V2Orchestrator(),
            v2RuntimeOrchestrator = emptyRuntimeOrchestrator()
        )

        manager.setWebSearchEnabled(true)
        manager.setDeepThinkingEnabled(true)
        manager.sendMessage("最近有哪些新闻")
        runCurrent()
        statusEmitted.await()

        val beforeFrame = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertEquals("", beforeFrame.thinkingContent)

        advanceTimeBy(40L)
        runCurrent()

        val afterFrame = manager.uiState.value.messages.last { it.role == Message.ROLE_ASSISTANT }
        assertTrue(afterFrame.thinkingContent.contains("正在联网检索相关信息"))
        assertFalse(afterFrame.thinkingContent.contains("1."))
        assertFalse(afterFrame.thinkingContent.contains("[1]"))

        finishStream.complete(Unit)
        runCurrent()
    }

    private fun emptyRuntimeOrchestrator(): V2RuntimeOrchestrator {
        val tools = V2ToolRegistry.production()
        val agents = V2AgentRegistry(tools)
        return V2RuntimeOrchestrator(
            agentRegistry = agents,
            toolRegistry = tools,
            executorRegistry = V2RuntimeAgentExecutorRegistry.fromExecutors(emptyList()),
            planner = V2Orchestrator(agents, tools)
        )
    }

    private fun emailRuntimeOrchestrator(
        executor: FakeEmailRuntimeExecutor = FakeEmailRuntimeExecutor()
    ): V2RuntimeOrchestrator {
        val tools = V2ToolRegistry.production()
        val agents = V2AgentRegistry(tools)
        return V2RuntimeOrchestrator(
            agentRegistry = agents,
            toolRegistry = tools,
            executorRegistry = V2RuntimeAgentExecutorRegistry.fromExecutors(listOf(executor)),
            planner = V2Orchestrator(agents, tools)
        )
    }

    private fun searchRuntimeOrchestrator(
        executor: FakeStreamingSearchRuntimeExecutor
    ): V2RuntimeOrchestrator {
        val tools = V2ToolRegistry.production()
        val agents = V2AgentRegistry(tools)
        return V2RuntimeOrchestrator(
            agentRegistry = agents,
            toolRegistry = tools,
            executorRegistry = V2RuntimeAgentExecutorRegistry.fromExecutors(listOf(executor)),
            planner = V2Orchestrator(agents, tools)
        )
    }

    private class FakeEmailRuntimeExecutor : V2RuntimeAgentExecutor {
        override val agentId: V2AgentId = V2AgentId.EMAIL
        var lastHistory: List<V2RuntimeHistoryMessage> = emptyList()

        override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
            lastHistory = context.history
            return V2AgentExecution(
                status = V2AgentRunStatus.COMPLETED,
                summary = "Email sent",
                output = "已经将NBA 总决赛战况等内容发送给1149201272@qq.com✅",
                usedTools = task.requiredTools,
                artifact = "V2_ARTIFACT|type=email|to=1149201272@qq.com"
            )
        }
    }

    private class FakeStreamingSearchRuntimeExecutor : V2RuntimeAgentExecutor {
        override val agentId: V2AgentId = V2AgentId.SEARCH
        val chunksEmitted = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()

        override suspend fun execute(task: V2PlanTask, context: V2ExecutionContext): V2AgentExecution {
            context.onContent(agentId, "智能")
            context.onContent(agentId, "搜索")
            chunksEmitted.complete(Unit)
            finish.await()
            return V2AgentExecution(
                status = V2AgentRunStatus.COMPLETED,
                summary = "Search done",
                output = "智能搜索完成",
                usedTools = task.requiredTools
            )
        }
    }

    private class FakeChatRepository(
        private val events: Flow<ChatEvent>
    ) : ChatRepository {
        val savedMessages = mutableListOf<Message>()
        val streamRequests = mutableListOf<ChatStreamRequest>()

        override suspend fun chat(request: ChatStreamRequest): Result<ChatResponse> =
            Result.success(ChatResponse(answer = "", conversation_id = request.conversation_id ?: 1))

        override fun streamChat(request: ChatStreamRequest): Flow<ChatEvent> {
            streamRequests += request
            return events
        }

        override suspend fun followUp(question: String, answer: String): Result<List<String>> =
            Result.success(emptyList())

        override suspend fun listConversations(limit: Int): Result<List<Conversation>> =
            Result.success(emptyList())

        override suspend fun getConversationMessages(conversationId: Int): Result<List<Message>> =
            Result.success(emptyList())

        override fun saveMessage(conversationId: Int, message: Message) {
            savedMessages += message
        }

        override fun updateConversationTitle(conversationId: Int, title: String) = Unit

        override suspend fun clearAllConversations(): Result<Unit> = Result.success(Unit)
    }

    private class FakeAuthRepository : AuthRepository {
        override suspend fun loginWithPassword(username: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun loginWithEmailCode(email: String, code: String): Result<Unit> = Result.success(Unit)
        override suspend fun register(username: String, email: String, password: String, code: String): Result<Unit> = Result.success(Unit)
        override suspend fun sendVerificationCode(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun getMe(): Result<UserResponse> = Result.success(UserResponse())
        override suspend fun updateMe(fullName: String?, avatarUrl: String?): Result<UserResponse> = Result.success(UserResponse())
        override suspend fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String): Result<Unit> = Result.success(Unit)
        override suspend fun resetPassword(email: String, code: String, newPassword: String, confirmPassword: String): Result<Unit> = Result.success(Unit)
        override suspend fun isLoggedIn(): Boolean = true
        override suspend fun sendEmail(toAddr: String, subject: String, content: String): Result<EmailSendResponse> = Result.success(EmailSendResponse())
        override suspend fun getEmailHistory(limit: Int): Result<EmailHistoryResponse> = Result.success(EmailHistoryResponse())
        override suspend fun logout() = Unit
    }
}
