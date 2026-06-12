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
    val content: String,
    val imageBase64: String? = null,
    val imageMimeType: String = "image/jpeg"
) {
    fun toOpenAiMessage(): Map<String, Any> {
        val image = imageBase64?.takeIf { it.isNotBlank() }
            ?: return mapOf("role" to role, "content" to content)
        val mimeType = imageMimeType.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        return mapOf(
            "role" to role,
            "content" to listOf(
                mapOf("type" to "text", "text" to content),
                mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf("url" to "data:$mimeType;base64,$image")
                )
            )
        )
    }

    companion object {
        fun userWithImage(
            content: String,
            imageBase64: String,
            imageMimeType: String = "image/jpeg"
        ): ChatMessage = ChatMessage("user", content, imageBase64, imageMimeType)
    }
}

class DeepSeekChatClient(
    private val client: OkHttpClient,
    baseUrl: String = com.geoagent.BuildConfig.LLM_BASE_URL,
    private val defaultModel: String = com.geoagent.BuildConfig.LLM_MODEL,
    private val apiKey: String = com.geoagent.BuildConfig.LLM_API_KEY,
    private val defaultMaxTokens: Int = com.geoagent.BuildConfig.LLM_MAX_TOKENS
) {

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val chatCompletionsUrl = baseUrl
        .trimEnd('/')
        .let { "$it/chat/completions" }

    suspend fun completeChat(
        messages: List<ChatMessage>,
        apiKey: String = this.apiKey,
        model: String = defaultModel
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = gson.toJson(
                mapOf(
                    "model" to model,
                    "messages" to messages.map { it.toOpenAiMessage() },
                    "stream" to false,
                    "max_tokens" to defaultMaxTokens
                )
            ).toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(chatCompletionsUrl)
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
        apiKey: String = this.apiKey,
        model: String = defaultModel,
        enableThinking: Boolean = false
    ): Flow<ChatEvent> = flow {
        val bodyMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages.map { it.toOpenAiMessage() },
            "stream" to true,
            "max_tokens" to defaultMaxTokens,
            "enable_thinking" to enableThinking
        )

        val requestBody = gson.toJson(bodyMap).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(chatCompletionsUrl)
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
                        val (reasoning, content) = parseOpenAiChunk(data)
                        if (enableThinking && reasoning != null) {
                            emit(ChatEvent.Thinking(reasoning))
                        }
                        if (content != null) {
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

    private fun parseOpenAiChunk(data: String): Pair<String?, String?> {
        return try {
            val json = JsonParser.parseString(data).asJsonObject
            val choices = json.getAsJsonArray("choices") ?: return Pair(null, null)
            if (choices.size() == 0) return Pair(null, null)
            val delta = choices[0].asJsonObject.getAsJsonObject("delta") ?: return Pair(null, null)
            val reasoning = delta.get("reasoning_content")?.takeIf { !it.isJsonNull }?.asString
            val content = delta.get("content")?.takeIf { !it.isJsonNull }?.asString
            Pair(reasoning, content)
        } catch (_: Exception) {
            Pair(null, null)
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
