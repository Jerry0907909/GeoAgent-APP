package com.geoagent.data.api

import com.geoagent.data.api.dto.CitationItem
import com.geoagent.data.api.dto.SearchEvent
import com.geoagent.data.api.dto.SearchResultItem
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

class TavilySearchClient(private val client: OkHttpClient) {

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = "https://api.tavily.com/search"

    fun search(query: String, apiKey: String): Flow<SearchEvent> = callbackFlow {
        trySend(SearchEvent.Plan(listOf(query)))
        trySend(SearchEvent.Status("正在联网搜索…"))

        val requestBody = gson.toJson(
            mapOf(
                "api_key" to apiKey,
                "query" to query,
                "search_depth" to "advanced",
                "include_answer" to true,
                "include_raw_content" to false,
                "max_results" to 5
            )
        ).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        val httpClient = client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        var call: okhttp3.Call? = null
        try {
            call = httpClient.newCall(request)
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    trySend(SearchEvent.Error("搜索请求失败: HTTP ${resp.code}"))
                    close()
                    return@callbackFlow
                }

                val body = resp.body?.string().orEmpty()
                val json = JsonParser.parseString(body).asJsonObject

                val results = json.getAsJsonArray("results")?.mapNotNull { elem ->
                    val obj = elem.asJsonObject
                    SearchResultItem(
                        title = obj.get("title")?.asString ?: "",
                        url = obj.get("url")?.asString ?: "",
                        snippet = obj.get("content")?.asString ?: ""
                    )
                } ?: emptyList()

                trySend(SearchEvent.Search(results))

                val answer = json.get("answer")?.asString
                if (!answer.isNullOrBlank()) {
                    trySend(SearchEvent.Extract(answer))
                    trySend(SearchEvent.Answer(answer))
                } else if (results.isNotEmpty()) {
                    val synthesized = buildString {
                        appendLine("搜索到 ${results.size} 条相关结果：")
                        results.forEachIndexed { i, r ->
                            appendLine()
                            appendLine("**${i + 1}. ${r.title}**")
                            appendLine(r.snippet.take(300))
                        }
                    }
                    trySend(SearchEvent.Answer(synthesized))
                } else {
                    trySend(SearchEvent.Answer("未找到与「$query」相关的搜索结果。"))
                }

                val citations = results.filter { it.url.isNotBlank() }.map {
                    CitationItem(title = it.title, url = it.url)
                }
                if (citations.isNotEmpty()) {
                    trySend(SearchEvent.Citation(citations))
                }

                trySend(SearchEvent.Done())
            }
        } catch (e: java.net.ConnectException) {
            trySend(SearchEvent.Error("无法连接搜索服务，请检查网络"))
        } catch (e: java.net.SocketTimeoutException) {
            trySend(SearchEvent.Error("搜索请求超时"))
        } catch (e: Exception) {
            trySend(SearchEvent.Error(e.message ?: "搜索出错"))
        }

        awaitClose { call?.cancel() }
    }.flowOn(Dispatchers.IO)
}
