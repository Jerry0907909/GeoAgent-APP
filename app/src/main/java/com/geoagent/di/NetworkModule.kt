package com.geoagent.di

import com.geoagent.data.api.AuthInterceptor
import com.geoagent.data.api.GeoAgentApi
import com.geoagent.data.api.SearchSseClient
import com.geoagent.data.api.SseClient
import com.geoagent.data.api.TokenAuthenticator
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** 内嵌 NanoHTTPD 服务器地址 */
const val BASE_URL = "http://10.0.2.2:8000/api/" // 真实后端
//const val BASE_URL = "http://localhost:8080/api/" APP端模拟后端

val networkModule = module {
    single {
        val cacheDir = File(androidContext().cacheDir, "http_cache")
        Cache(cacheDir, 10L * 1024 * 1024) // 10MB
    }

    single {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS // headers only, not BODY (avoids leaking tokens)
        }
    }

    single { TokenAuthenticator(get(), BASE_URL) }

    single {
        OkHttpClient.Builder()
            .cache(get<Cache>())
            .addInterceptor(AuthInterceptor(get()))
            .authenticator(get<TokenAuthenticator>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single { get<Retrofit>().create(GeoAgentApi::class.java) }

    single { SseClient(get(), BASE_URL) }
    single { SearchSseClient(get(), BASE_URL) }
}