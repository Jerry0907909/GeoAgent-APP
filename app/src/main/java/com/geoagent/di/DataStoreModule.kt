package com.geoagent.di

import com.geoagent.data.local.AccountStore
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.AvatarLocalStore
import com.geoagent.data.local.TokenDataStore
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.data.local.VerificationCodeStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataStoreModule = module {
    single { TokenDataStore(androidContext()) }
    single { UserPrefsDataStore(androidContext()) }
    single { AvatarLocalStore(androidContext()) }
    single { ApiKeyStore(androidContext()) }
    single { VerificationCodeStore(androidContext()) }
    single { AccountStore(androidContext()) }
}