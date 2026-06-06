package com.geoagent.data.api

import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.SourceDto
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String
)

class DeepSeekChatClient(private val client: OkHttpClient) {

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = "https://api.deepseek.com/v1/chat/completions"

    fun streamChat(
        messages: List<ChatMessage>,
        apiKey: String,
        model: String = "deepseek-chat"
    ): Flow<ChatEvent> = callbackFlow {
        val requestBody = gson.toJson(
            mapOf(
                "model" to model,
                "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
                "stream" to true
            )
        ).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .build()

        val sseClient = client.newBuilder()
            .cache(null)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        var call: okhttp3.Call? = null
        try {
            trySend(ChatEvent.Status("正在生成回复…"))

            call = sseClient.newCall(request)
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val detail = extractDeepSeekError(body)
                    trySend(ChatEvent.Error(detail ?: "DeepSeek HTTP ${resp.code}"))
                    close()
                    return@callbackFlow
                }

                val body = resp.body ?: run {
                    trySend(ChatEvent.Error("Empty response"))
                    close()
                    return@callbackFlow
                }

                val contentBuilder = StringBuilder()

                body.byteStream().bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") {
                                val fullContent = contentBuilder.toString()
                                val sources = if (contentBuilder.contains("【来源】")) {
                                    extractInlineSources(fullContent)
                                } else emptyList()
                                trySend(ChatEvent.Sources(sources))
                                trySend(ChatEvent.Done())
                                return@forEachLine
                            }
                            parseOpenAiChunk(data)?.let { content ->
                                contentBuilder.append(content)
                                trySend(ChatEvent.Content(content))
                            }
                        }
                    }
                }

                if (contentBuilder.isNotEmpty()) {
                    trySend(ChatEvent.Done())
                }
            }
        } catch (e: java.net.ConnectException) {
            trySend(ChatEvent.Error("无法连接 DeepSeek API，请检查网络"))
        } catch (e: java.net.SocketTimeoutException) {
            trySend(ChatEvent.Error("DeepSeek API 响应超时"))
        } catch (e: Exception) {
            trySend(ChatEvent.Error(e.message ?: "未知错误"))
        }

        awaitClose { call?.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun parseOpenAiChunk(data: String): String? {
        return try {
            val json = JsonParser.parseString(data).asJsonObject
            val choices = json.getAsJsonArray("choices") ?: return null
            if (choices.size() == 0) return null
            val delta = choices[0].asJsonObject.getAsJsonObject("delta") ?: return null
            val content = delta.get("content")
            if (content == null || content.isJsonNull) return null
            content.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDeepSeekError(body: String): String? {
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            val error = json.getAsJsonObject("error")
            error?.get("message")?.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun extractInlineSources(content: String): List<SourceDto> {
        val sources = mutableListOf<SourceDto>()
        val sourceBlock = Regex("【来源】([\\s\\S]*?)(?:$|【)").find(content)
        if (sourceBlock != null) {
            val lines = sourceBlock.groupValues[1].split("\n").filter { it.isNotBlank() }
            for (line in lines) {
                val cleaned = line.removePrefix("- ").removePrefix("• ").trim()
                sources.add(SourceDto(content = cleaned, source = cleaned.take(50)))
            }
        }
        return sources
    }
}
