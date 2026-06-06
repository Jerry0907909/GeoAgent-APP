package com.geoagent.data.api

import com.geoagent.data.api.dto.LoginRequest
import com.geoagent.data.api.dto.MessageResponse
import com.geoagent.data.api.dto.PasswordChangeRequest
import com.geoagent.data.api.dto.RegisterRequest
import com.geoagent.data.api.dto.SendVerificationCodeRequest
import com.geoagent.data.api.dto.TokenResponse
import com.geoagent.data.api.dto.UserResponse
import com.geoagent.data.api.dto.UserUpdateRequest
import com.geoagent.data.api.dto.VerificationCodeResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeoAgentAuthApi(private val client: OkHttpClient) {

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = "http://10.0.2.2:8000/api/auth"

    suspend fun login(request: LoginRequest): Result<TokenResponse> = post("$baseUrl/login", request, TokenResponse::class.java)

    suspend fun register(request: RegisterRequest): Result<UserResponse> =
        post("$baseUrl/register", request, UserResponse::class.java)

    suspend fun sendVerificationCode(email: String): Result<VerificationCodeResponse> =
        post("$baseUrl/send-verification-code", SendVerificationCodeRequest(email), VerificationCodeResponse::class.java)

    suspend fun getMe(accessToken: String): Result<UserResponse> = get("$baseUrl/me", accessToken, UserResponse::class.java)

    suspend fun updateMe(accessToken: String, request: UserUpdateRequest): Result<UserResponse> =
        put("$baseUrl/me", accessToken, request, UserResponse::class.java)

    suspend fun changePassword(accessToken: String, request: PasswordChangeRequest): Result<MessageResponse> =
        post("$baseUrl/change-password", request, MessageResponse::class.java, accessToken)

    private suspend fun <T> get(url: String, accessToken: String, type: Class<T>): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                execute(request, type)
            }.fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(it) })
        }

    private suspend fun <T> post(
        url: String,
        body: Any,
        type: Class<T>,
        accessToken: String? = null
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
            val builder = Request.Builder().url(url).post(requestBody)
            if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")
            execute(builder.build(), type)
        }.fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(it) })
    }

    private suspend fun <T> put(url: String, accessToken: String, body: Any, type: Class<T>): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .put(requestBody)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                execute(request, type)
            }.fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(it) })
        }

    private fun <T> execute(request: Request, type: Class<T>): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(parseError(body, response.code))
            return gson.fromJson(body, type)
        }
    }

    private fun parseError(body: String, code: Int): String {
        if (body.isBlank()) return "请求失败 ($code)"
        return runCatching {
            val json = JsonParser.parseString(body)
            when {
                json.isJsonObject && json.asJsonObject.has("detail") -> {
                    val detail = json.asJsonObject.get("detail")
                    if (detail.isJsonPrimitive) detail.asString
                    else detail.toString()
                }
                else -> body
            }
        }.getOrDefault(body)
    }
}

class ApiException(message: String) : Exception(message)
