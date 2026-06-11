package com.geoagent.di

import android.content.Context
import com.geoagent.data.api.DeepSeekChatClient
import com.geoagent.data.api.GeoAgentAuthApi
import com.geoagent.data.api.SiliconFlowEmbeddingClient
import com.geoagent.data.local.AccountStore
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.AvatarLocalStore
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.local.GeoAgentDatabase
import com.geoagent.data.local.TokenDataStore
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.data.local.VerificationCodeStore
import com.geoagent.data.repository.AuthRepositoryImpl
import com.geoagent.data.repository.ChatRepositoryImpl
import com.geoagent.data.repository.DocumentRepositoryImpl
import com.geoagent.domain.SearchUseCase
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import com.geoagent.domain.repository.DocumentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppHiltModule {

    @Provides
    @Singleton
    fun provideTokenDataStore(
        @ApplicationContext context: Context
    ): TokenDataStore = TokenDataStore(context)

    @Provides
    @Singleton
    fun provideVerificationCodeStore(
        @ApplicationContext context: Context
    ): VerificationCodeStore = VerificationCodeStore(context)

    @Provides
    @Singleton
    fun provideUserPrefsDataStore(
        @ApplicationContext context: Context
    ): UserPrefsDataStore = UserPrefsDataStore(context)

    @Provides
    @Singleton
    fun provideAccountStore(
        @ApplicationContext context: Context
    ): AccountStore = AccountStore(context)

    @Provides
    @Singleton
    fun provideAvatarLocalStore(
        @ApplicationContext context: Context
    ): AvatarLocalStore = AvatarLocalStore(context)

    @Provides
    @Singleton
    fun provideGeoAgentDatabase(
        @ApplicationContext context: Context
    ): GeoAgentDatabase = GeoAgentDatabase(context)

    @Provides
    @Singleton
    fun provideDocumentStore(
        @ApplicationContext context: Context
    ): DocumentStore = DocumentStore(context)

    @Provides
    @Singleton
    fun provideSiliconFlowEmbeddingClient(
        @Named("SearchOkHttp") client: OkHttpClient
    ): SiliconFlowEmbeddingClient = SiliconFlowEmbeddingClient(client)

    @Provides
    @Singleton
    fun provideGeoAgentAuthApi(
        @Named("SearchOkHttp") client: OkHttpClient
    ): GeoAgentAuthApi = GeoAgentAuthApi(client)

    @Provides
    @Singleton
    fun provideAuthRepository(
        accountStore: AccountStore,
        tokenDataStore: TokenDataStore,
        verificationCodeStore: VerificationCodeStore,
        apiKeyStore: ApiKeyStore
    ): AuthRepository = AuthRepositoryImpl(accountStore, tokenDataStore, verificationCodeStore, apiKeyStore)

    @Provides
    @Singleton
    fun provideChatRepository(
        deepSeekClient: DeepSeekChatClient,
        searchUseCase: SearchUseCase,
        apiKeyStore: ApiKeyStore,
        documentStore: DocumentStore,
        embeddingClient: SiliconFlowEmbeddingClient,
        database: GeoAgentDatabase,
        userPrefsDataStore: UserPrefsDataStore
    ): ChatRepository = ChatRepositoryImpl(
        deepSeekClient,
        searchUseCase,
        apiKeyStore,
        documentStore,
        embeddingClient,
        database,
        userPrefsDataStore
    )

    @Provides
    @Singleton
    fun provideDocumentRepository(
        documentStore: DocumentStore,
        embeddingClient: SiliconFlowEmbeddingClient,
        apiKeyStore: ApiKeyStore
    ): DocumentRepository = DocumentRepositoryImpl(documentStore, embeddingClient, apiKeyStore)
}
