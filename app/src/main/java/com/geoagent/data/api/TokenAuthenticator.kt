package com.geoagent.data.api

import com.geoagent.data.api.dto.RefreshTokenRequest
import com.geoagent.data.local.TokenDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TokenAuthenticator(
    private val tokenDataStore: TokenDataStore,
    private val baseUrl: String
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Retry-With-Refresh") != null) {
            return null
        }

        val refreshToken = runBlocking { tokenDataStore.refreshToken.first() } ?: return null

        return runBlocking {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val api = retrofit.create(GeoAgentApi::class.java)
                val newTokens = api.refreshToken(RefreshTokenRequest(refreshToken))
                tokenDataStore.saveTokens(newTokens.access_token, newTokens.refresh_token)

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.access_token}")
                    .header("X-Retry-With-Refresh", "true")
                    .build()
            } catch (e: Exception) {
                tokenDataStore.clearTokens()
                null
            }
        }
    }
}