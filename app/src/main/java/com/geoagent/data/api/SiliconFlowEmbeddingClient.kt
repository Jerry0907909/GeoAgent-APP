package com.geoagent.data.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SiliconFlowEmbeddingClient(private val client: OkHttpClient) {

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = "https://api.siliconflow.cn/v1/embeddings"
    private val apiKey = "sk-aprgegankxvkcydcyukrgqaipbspefwcjmkvgegjuesftbsv"
    private val model = "BAAI/bge-m3"

    data class EmbeddingResponse(
        val data: List<EmbeddingItem> = emptyList()
    )

    data class EmbeddingItem(
        val embedding: List<Float> = emptyList(),
        val index: Int = 0
    )

    suspend fun embed(texts: List<String>): Result<List<FloatArray>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(mapOf(
                    "model" to model,
                    "input" to texts,
                    "encoding_format" to "float"
                )).toRequestBody(jsonMediaType)

                val httpClient = client.newBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(baseUrl)
                    .post(requestBody)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("Embedding API HTTP ${resp.code}"))
                    }
                    val body = resp.body?.string().orEmpty()
                    val json = JsonParser.parseString(body).asJsonObject
                    val data = json.getAsJsonArray("data")
                    val embeddings = data.map { item ->
                        item.asJsonObject.getAsJsonArray("embedding").map { it.asFloat }.toFloatArray()
                    }
                    Result.success(embeddings)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun embedSingle(text: String): Result<FloatArray> {
        return embed(listOf(text)).map { it.first() }
    }
}
