package com.geoagent.network

import com.geoagent.model.TavilyRequest
import com.geoagent.model.TavilyResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TavilyApi {
    @POST("search")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: TavilyRequest
    ): Response<TavilyResponse>
}
