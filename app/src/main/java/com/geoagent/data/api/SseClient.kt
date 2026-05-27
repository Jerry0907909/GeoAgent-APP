package com.geoagent.data.api

import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.ChatStreamRequest
import com.geoagent.data.api.dto.parseChatEvent
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

class SseClient(private val client: OkHttpClient, private val baseUrl: String) {

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    fun streamChat(requestBody: ChatStreamRequest, token: String): Flow<ChatEvent> = callbackFlow {
        val jsonBody = gson.toJson(requestBody).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("${baseUrl}chat/stream")
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
                    val detail = resp.body?.string()?.let(::extractErrorDetail)
                    trySend(ChatEvent.Error(detail ?: "HTTP ${resp.code}"))
                    close()
                    return@callbackFlow
                }

                val body = resp.body
                if (body == null) {
                    trySend(ChatEvent.Error("Empty response body"))
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
                                trySend(parseChatEvent(currentData.toString().trim()))
                                currentData = StringBuilder()
                            }
                            else -> { /* ignore event:, id:, retry: lines */ }
                        }
                    }
                }

                if (currentData.isNotEmpty()) {
                    trySend(parseChatEvent(currentData.toString().trim()))
                }
            }
        } catch (e: Exception) {
            trySend(ChatEvent.Error(formatSseException(e)))
        }

        awaitClose { call?.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun extractErrorDetail(raw: String): String? {
        return runCatching {
            val json = com.google.gson.JsonParser.parseString(raw).asJsonObject
            json.get("detail")?.asString
        }.getOrNull()
    }

    private fun formatSseException(e: Exception): String {
        return when (e) {
            is android.os.NetworkOnMainThreadException ->
                "网络请求不能在主线程执行（请更新 APP）"
            is java.net.ConnectException ->
                "无法连接服务器，请确认后端在 :8000 运行"
            is java.net.SocketTimeoutException ->
                "连接超时"
            else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        }
    }
}