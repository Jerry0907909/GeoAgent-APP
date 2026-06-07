package com.geoagent.di

import com.geoagent.data.api.DeepSeekChatClient
import com.geoagent.data.api.GeoAgentAuthApi
import com.geoagent.data.api.SiliconFlowEmbeddingClient
import com.geoagent.data.api.TavilySearchClient
import com.geoagent.network.TavilyApi
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

val networkModule = module {
    single {
        val cacheDir = File(androidContext().cacheDir, "http_cache")
        Cache(cacheDir, 10L * 1024 * 1024)
    }

    single {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
    }

    single {
        OkHttpClient.Builder()
            .cache(get<Cache>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    single { GeoAgentAuthApi(get()) }
    single { DeepSeekChatClient(get()) }
    single { TavilySearchClient(get()) }
    single {
        Retrofit.Builder()
            .baseUrl("https://api.tavily.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TavilyApi::class.java)
    }
    single { SiliconFlowEmbeddingClient(get()) }
}
