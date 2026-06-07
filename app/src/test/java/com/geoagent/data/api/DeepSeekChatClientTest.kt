package com.geoagent.data.api

import com.geoagent.data.api.dto.ChatEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    private fun fakeClient(body: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
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
