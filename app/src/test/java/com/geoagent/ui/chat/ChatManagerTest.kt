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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
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

    private class FakeChatRepository(
        private val events: Flow<ChatEvent>
    ) : ChatRepository {
        val savedMessages = mutableListOf<Message>()

        override suspend fun chat(request: ChatStreamRequest): Result<ChatResponse> =
            Result.success(ChatResponse(answer = "", conversation_id = request.conversation_id ?: 1))

        override fun streamChat(request: ChatStreamRequest): Flow<ChatEvent> = events

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
        override suspend fun isLoggedIn(): Boolean = true
        override suspend fun sendEmail(toAddr: String, subject: String, content: String): Result<EmailSendResponse> = Result.success(EmailSendResponse())
        override suspend fun getEmailHistory(limit: Int): Result<EmailHistoryResponse> = Result.success(EmailHistoryResponse())
        override suspend fun logout() = Unit
    }
}
