package com.geoagent.data.api

import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.SourceDto
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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

    suspend fun completeChat(
        messages: List<ChatMessage>,
        apiKey: String,
        model: String = "deepseek-chat"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = gson.toJson(
                mapOf(
                    "model" to model,
                    "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
                    "stream" to false
                )
            ).toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            val response = client.newBuilder()
                .cache(null)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()

            response.use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException(extractDeepSeekError(body) ?: "DeepSeek HTTP ${resp.code}")
                }
                parseCompletionContent(body) ?: throw IllegalStateException("DeepSeek response missing content")
            }
        }
    }

    fun streamChat(
        messages: List<ChatMessage>,
        apiKey: String,
        model: String = "deepseek-chat"
    ): Flow<ChatEvent> = flow {
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

        try {
            emit(ChatEvent.Status("正在生成回复…"))

            val call = sseClient.newCall(request)
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val detail = extractDeepSeekError(body)
                    emit(ChatEvent.Error(detail ?: "DeepSeek HTTP ${resp.code}"))
                    return@flow
                }

                val body = resp.body ?: run {
                    emit(ChatEvent.Error("Empty response"))
                    return@flow
                }

                val contentBuilder = StringBuilder()

                body.byteStream().bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data: ")) continue
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            val fullContent = contentBuilder.toString()
                            val sources = if (contentBuilder.contains("【来源】")) {
                                extractInlineSources(fullContent)
                            } else emptyList()
                            emit(ChatEvent.Sources(sources))
                            emit(ChatEvent.Done())
                            return@flow
                        }
                        parseOpenAiChunk(data)?.let { content ->
                            contentBuilder.append(content)
                            emit(ChatEvent.Content(content))
                        }
                    }
                }

                if (contentBuilder.isNotEmpty()) {
                    emit(ChatEvent.Done())
                }
            }
        } catch (e: java.net.ConnectException) {
            emit(ChatEvent.Error("无法连接 DeepSeek API，请检查网络"))
        } catch (e: java.net.SocketTimeoutException) {
            emit(ChatEvent.Error("DeepSeek API 响应超时"))
        } catch (e: Exception) {
            emit(ChatEvent.Error(e.message ?: "未知错误"))
        }
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

    private fun parseCompletionContent(body: String): String? {
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            val choices = json.getAsJsonArray("choices") ?: return null
            if (choices.size() == 0) return null
            val message = choices[0].asJsonObject.getAsJsonObject("message") ?: return null
            val content = message.get("content")
            if (content == null || content.isJsonNull) null else content.asString
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
