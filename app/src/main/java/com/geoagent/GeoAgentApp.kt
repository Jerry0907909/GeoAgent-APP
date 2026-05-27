package com.geoagent

import android.app.Application
import android.util.Log
import com.geoagent.di.dataStoreModule
import com.geoagent.di.databaseModule
import com.geoagent.di.networkModule
import com.geoagent.di.repositoryModule
import com.geoagent.di.viewModelModule
import com.geoagent.server.GeoAgentServer
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.IOException

class GeoAgentApp : Application() {

    companion object {
        const val TAG = "GeoAgent"
        /** 使用真实 FastAPI 后端时保持 false，避免内嵌 NanoHTTPD 占用端口 */
        private const val USE_EMBEDDED_SERVER = false
    }

    private var server: GeoAgentServer? = null

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GeoAgentApp)
            modules(
                dataStoreModule,
                networkModule,
                databaseModule,
                repositoryModule,
                viewModelModule
            )
        }
        startServer()
    }

    override fun onTerminate() {
        super.onTerminate()
        stopServer()
    }

    private fun startServer() {
        if (!USE_EMBEDDED_SERVER) return
        server = GeoAgentServer(8080)  // binds 127.0.0.1 only
        try {
            server?.start()
            Log.i(TAG, "内嵌服务器已启动 → http://localhost:8080")
        } catch (e: IOException) {
            Log.e(TAG, "服务器启动失败: ${e.message}")
        }
    }

    private fun stopServer() {
        server?.stop()
        Log.i(TAG, "内嵌服务器已停止")
    }
}