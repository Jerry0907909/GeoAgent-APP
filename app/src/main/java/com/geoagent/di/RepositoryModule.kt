package com.geoagent.di

import com.geoagent.data.repository.AuthRepositoryImpl
import com.geoagent.data.repository.ChatRepositoryImpl
import com.geoagent.data.repository.DocumentRepositoryImpl
import com.geoagent.data.repository.SearchRepositoryImpl
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import com.geoagent.domain.repository.DocumentRepository
import com.geoagent.domain.repository.SearchRepository
import org.koin.dsl.module

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get()) }
    single<SearchRepository> { SearchRepositoryImpl(get(), get()) }
}