package com.geoagent.di

import com.geoagent.data.local.GeoAgentDatabase
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.local.search.SearchCacheDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single { GeoAgentDatabase(androidContext()) }
    single { DocumentStore(androidContext()) }
    single { SearchCacheDatabase.create(androidContext()) }
    single { get<SearchCacheDatabase>().tavilySearchCacheDao() }
}
