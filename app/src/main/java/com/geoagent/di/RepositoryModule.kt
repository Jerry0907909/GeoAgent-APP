package com.geoagent.di

import com.geoagent.data.repository.AuthRepositoryImpl
import com.geoagent.data.repository.ChatRepositoryImpl
import com.geoagent.data.repository.DocumentRepositoryImpl
import com.geoagent.data.repository.SearchRepositoryImpl
import com.geoagent.domain.SearchUseCase
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import com.geoagent.domain.repository.DocumentRepository
import com.geoagent.domain.repository.SearchRepository
import com.geoagent.network.TavilyRepository
import org.koin.dsl.module

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }
    single { TavilyRepository(get(), get()) }
    single { SearchUseCase(get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get(), get()) }
    single<SearchRepository> { SearchRepositoryImpl(get(), get()) }
}
