package com.geoagent.di

import com.geoagent.data.local.AvatarLocalStore
import com.geoagent.data.local.TokenDataStore
import com.geoagent.data.local.UserPrefsDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataStoreModule = module {
    single { TokenDataStore(androidContext()) }
    single { UserPrefsDataStore(androidContext()) }
    single { AvatarLocalStore(androidContext()) }
}