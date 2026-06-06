package com.geoagent

import android.app.Application
import com.geoagent.di.dataStoreModule
import com.geoagent.di.databaseModule
import com.geoagent.di.networkModule
import com.geoagent.di.repositoryModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class GeoAgentApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GeoAgentApp)
            modules(
                dataStoreModule,
                networkModule,
                databaseModule,
                repositoryModule
            )
        }
    }
}