package com.geoagent.di

import android.content.Context
import com.geoagent.data.api.DeepSeekChatClient
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.search.SearchCacheDatabase
import com.geoagent.data.local.search.TavilySearchCacheDao
import com.geoagent.network.TavilyApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SearchHiltModule {

    @Provides
    @Singleton
    @Named("SearchHttpCache")
    fun provideSearchHttpCache(@ApplicationContext context: Context): Cache =
        Cache(File(context.cacheDir, "search_http_cache"), 10L * 1024L * 1024L)

    @Provides
    @Singleton
    @Named("SearchLogging")
    fun provideSearchLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = HttpLoggingInterceptor.Level.HEADERS
        }

    @Provides
    @Singleton
    @Named("SearchOkHttp")
    fun provideSearchOkHttpClient(
        @Named("SearchHttpCache") cache: Cache,
        @Named("SearchLogging") loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideTavilyApi(
        @Named("SearchOkHttp") client: OkHttpClient
    ): TavilyApi =
        Retrofit.Builder()
            .baseUrl("https://api.tavily.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TavilyApi::class.java)

    @Provides
    @Singleton
    fun provideDeepSeekChatClient(
        @Named("SearchOkHttp") client: OkHttpClient
    ): DeepSeekChatClient = DeepSeekChatClient(client)

    @Provides
    @Singleton
    fun provideSearchCacheDatabase(
        @ApplicationContext context: Context
    ): SearchCacheDatabase = SearchCacheDatabase.create(context)

    @Provides
    fun provideTavilySearchCacheDao(
        database: SearchCacheDatabase
    ): TavilySearchCacheDao = database.tavilySearchCacheDao()

    @Provides
    @Singleton
    fun provideApiKeyStore(
        @ApplicationContext context: Context
    ): ApiKeyStore = ApiKeyStore(context)
}
