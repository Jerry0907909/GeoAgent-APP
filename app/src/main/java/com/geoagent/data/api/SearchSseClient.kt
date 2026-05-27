package com.geoagent.data.api

import com.geoagent.data.api.dto.SearchEvent
import com.geoagent.data.api.dto.parseSearchEvent
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SearchSseClient(private val client: OkHttpClient, private val baseUrl: String) {

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    fun streamSearch(query: String, token: String): Flow<SearchEvent> = callbackFlow {
        val requestBody = mapOf("query" to query)
        val jsonBody = gson.toJson(requestBody).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("${baseUrl}search/deep")
            .post(jsonBody)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val sseClient = client.newBuilder()
            .cache(null)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        var call: Call? = null
        try {
            call = sseClient.newCall(request)
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    trySend(SearchEvent.Error("HTTP ${resp.code}"))
                    close()
                    return@callbackFlow
                }

                val body = resp.body
                if (body == null) {
                    trySend(SearchEvent.Error("Empty response body"))
                    close()
                    return@callbackFlow
                }

                var currentData = StringBuilder()
                body.byteStream().bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        when {
                            line.startsWith("data: ") -> {
                                currentData.append(line.removePrefix("data: "))
                            }
                            line.startsWith("data:") -> {
                                currentData.append(line.removePrefix("data:"))
                            }
                            line.isEmpty() && currentData.isNotEmpty() -> {
                                trySend(parseSearchEvent(currentData.toString().trim()))
                                currentData = StringBuilder()
                            }
                            else -> { /* ignore */ }
                        }
                    }
                }

                if (currentData.isNotEmpty()) {
                    trySend(parseSearchEvent(currentData.toString().trim()))
                }
            }
        } catch (e: Exception) {
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            trySend(SearchEvent.Error(msg))
        }

        awaitClose { call?.cancel() }
    }.flowOn(Dispatchers.IO)
}