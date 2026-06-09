package com.geoagent.di

import android.content.Context
import com.geoagent.agent.v2.V2AgentExecutorRegistry
import com.geoagent.agent.v2.V2AgentRegistry
import com.geoagent.agent.v2.V2Orchestrator
import com.geoagent.agent.v2.V2ProductionRuntimeGateway
import com.geoagent.agent.v2.V2RuntimeAgentExecutorRegistry
import com.geoagent.agent.v2.V2RuntimeGateway
import com.geoagent.agent.v2.V2RuntimeOrchestrator
import com.geoagent.agent.v2.V2ToolRegistry
import com.geoagent.data.api.DeepSeekChatClient
import com.geoagent.data.api.SiliconFlowEmbeddingClient
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.local.memory.V2MemoryDao
import com.geoagent.data.local.memory.V2MemoryDatabase
import com.geoagent.data.repository.V2MemoryRepository
import com.geoagent.domain.SearchUseCase
import com.geoagent.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object V2AgentHiltModule {

    @Provides
    @Singleton
    fun provideV2ToolRegistry(): V2ToolRegistry = V2ToolRegistry.production()

    @Provides
    @Singleton
    fun provideV2AgentRegistry(
        toolRegistry: V2ToolRegistry
    ): V2AgentRegistry = V2AgentRegistry(toolRegistry)

    @Provides
    @Singleton
    fun provideV2AgentExecutorRegistry(
        agentRegistry: V2AgentRegistry
    ): V2AgentExecutorRegistry = V2AgentExecutorRegistry.production(agentRegistry)

    @Provides
    @Singleton
    fun provideV2Orchestrator(
        agentRegistry: V2AgentRegistry,
        toolRegistry: V2ToolRegistry,
        executorRegistry: V2AgentExecutorRegistry
    ): V2Orchestrator = V2Orchestrator(agentRegistry, toolRegistry, executorRegistry)

    @Provides
    @Singleton
    fun provideV2MemoryDatabase(
        @ApplicationContext context: Context
    ): V2MemoryDatabase = V2MemoryDatabase.create(context)

    @Provides
    fun provideV2MemoryDao(
        database: V2MemoryDatabase
    ): V2MemoryDao = database.memoryDao()

    @Provides
    @Singleton
    fun provideV2MemoryRepository(
        dao: V2MemoryDao
    ): V2MemoryRepository = V2MemoryRepository(dao)

    @Provides
    @Singleton
    fun provideV2RuntimeGateway(
        @ApplicationContext context: Context,
        deepSeekChatClient: DeepSeekChatClient,
        searchUseCase: SearchUseCase,
        apiKeyStore: ApiKeyStore,
        documentStore: DocumentStore,
        embeddingClient: SiliconFlowEmbeddingClient,
        memoryRepository: V2MemoryRepository,
        authRepository: AuthRepository
    ): V2RuntimeGateway = V2ProductionRuntimeGateway(
        context,
        deepSeekChatClient,
        searchUseCase,
        apiKeyStore,
        documentStore,
        embeddingClient,
        memoryRepository,
        authRepository
    )

    @Provides
    @Singleton
    fun provideV2RuntimeAgentExecutorRegistry(
        agentRegistry: V2AgentRegistry,
        gateway: V2RuntimeGateway
    ): V2RuntimeAgentExecutorRegistry = V2RuntimeAgentExecutorRegistry.production(agentRegistry, gateway)

    @Provides
    @Singleton
    fun provideV2RuntimeOrchestrator(
        agentRegistry: V2AgentRegistry,
        toolRegistry: V2ToolRegistry,
        executorRegistry: V2RuntimeAgentExecutorRegistry,
        planner: V2Orchestrator
    ): V2RuntimeOrchestrator = V2RuntimeOrchestrator(agentRegistry, toolRegistry, executorRegistry, planner)
}
