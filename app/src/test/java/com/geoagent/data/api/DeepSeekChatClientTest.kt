package com.geoagent.data.api

import com.geoagent.data.api.dto.ChatEvent
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekChatClientTest {

    @Test
    fun streamChatClosesAfterDoneEvent() = runBlocking {
        val client = DeepSeekChatClient(fakeClient("data: [DONE]\n"))

        val events = mutableListOf<ChatEvent>()
        withTimeout(1_000L) {
            client.streamChat(listOf(ChatMessage("user", "hello")), "test-key").collect {
                events.add(it)
            }
        }

        assertTrue(events.any { it is ChatEvent.Done })
    }

    @Test
    fun streamChatIgnoresThinkingEventsWhenThinkingModeDisabled() = runBlocking {
        val client = DeepSeekChatClient(fakeClient(reasoningChunkBody()))

        val events = mutableListOf<ChatEvent>()
        withTimeout(1_000L) {
            client.streamChat(
                listOf(ChatMessage("user", "hello")),
                "test-key",
                enableThinking = false
            ).collect {
                events.add(it)
            }
        }

        assertFalse(events.any { it is ChatEvent.Thinking })
        assertTrue(events.any { it is ChatEvent.Content })
    }

    @Test
    fun streamChatEmitsThinkingEventsWhenThinkingModeEnabled() = runBlocking {
        val client = DeepSeekChatClient(fakeClient(reasoningChunkBody()))

        val events = mutableListOf<ChatEvent>()
        withTimeout(1_000L) {
            client.streamChat(
                listOf(ChatMessage("user", "hello")),
                "test-key",
                enableThinking = true
            ).collect {
                events.add(it)
            }
        }

        assertTrue(events.any { it is ChatEvent.Thinking })
        assertTrue(events.any { it is ChatEvent.Content })
    }

    @Test
    fun streamChatSendsThinkingDisabledExplicitly() = runBlocking {
        var requestBody = ""
        val client = DeepSeekChatClient(
            fakeClient("data: [DONE]\n", onRequestBody = { requestBody = it })
        )

        withTimeout(1_000L) {
            client.streamChat(
                listOf(ChatMessage("user", "hello")),
                "test-key",
                enableThinking = false
            ).collect { }
        }

        val json = JsonParser.parseString(requestBody).asJsonObject
        assertTrue(json.has("enable_thinking"))
        assertFalse(json.get("enable_thinking").asBoolean)
    }

    @Test
    fun streamChatSendsThinkingEnabledExplicitly() = runBlocking {
        var requestBody = ""
        val client = DeepSeekChatClient(
            fakeClient("data: [DONE]\n", onRequestBody = { requestBody = it })
        )

        withTimeout(1_000L) {
            client.streamChat(
                listOf(ChatMessage("user", "hello")),
                "test-key",
                enableThinking = true
            ).collect { }
        }

        val json = JsonParser.parseString(requestBody).asJsonObject
        assertTrue(json.has("enable_thinking"))
        assertTrue(json.get("enable_thinking").asBoolean)
        assertEquals(128, json.get("thinking_budget").asInt)
    }

    @Test
    fun streamChatUsesConfiguredOpenAiCompatibleBaseUrl() = runBlocking {
        var requestUrl = ""
        val client = DeepSeekChatClient(
            client = fakeClient(
                body = "data: [DONE]\n",
                onRequest = { requestUrl = it.url.toString() }
            ),
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "Qwen3.7-Plus",
            apiKey = "test-key"
        )

        withTimeout(1_000L) {
            client.streamChat(listOf(ChatMessage("user", "hello"))).collect { }
        }

        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", requestUrl)
    }

    @Test
    fun streamChatSendsImageAsOpenAiCompatibleVisionContent() = runBlocking {
        var requestBody = ""
        val client = DeepSeekChatClient(
            fakeClient("data: [DONE]\n", onRequestBody = { requestBody = it })
        )

        withTimeout(1_000L) {
            client.streamChat(
                listOf(ChatMessage.userWithImage("这是什么", "abc123")),
                "test-key"
            ).collect { }
        }

        val json = JsonParser.parseString(requestBody).asJsonObject
        val content = json.getAsJsonArray("messages")[0].asJsonObject.getAsJsonArray("content")
        assertEquals("text", content[0].asJsonObject.get("type").asString)
        assertEquals("这是什么", content[0].asJsonObject.get("text").asString)
        assertEquals("image_url", content[1].asJsonObject.get("type").asString)
        assertEquals(
            "data:image/jpeg;base64,abc123",
            content[1].asJsonObject.getAsJsonObject("image_url").get("url").asString
        )
    }

    private fun reasoningChunkBody(): String {
        return """
            data: {"choices":[{"delta":{"reasoning_content":"thinking","content":"answer"}}]}
            data: [DONE]
        """.trimIndent()
    }

    private fun fakeClient(
        body: String,
        onRequestBody: (String) -> Unit = {},
        onRequest: (okhttp3.Request) -> Unit = {}
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                onRequest(chain.request())
                val requestBody = chain.request().body
                if (requestBody != null) {
                    val buffer = okio.Buffer()
                    requestBody.writeTo(buffer)
                    onRequestBody(buffer.readUtf8())
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }
            .build()
    }
}
