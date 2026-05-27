package com.geoagent.data.repository

import com.geoagent.data.api.GeoAgentApi
import com.geoagent.data.api.UserResponse
import com.geoagent.data.api.dto.*
import com.geoagent.data.local.TokenDataStore
import com.geoagent.domain.repository.AuthRepository
import com.google.gson.Gson
import retrofit2.HttpException

class AuthRepositoryImpl(
    private val api: GeoAgentApi,
    private val tokenDataStore: TokenDataStore
) : AuthRepository {

    private val gson = Gson()

    private data class ErrorBody(
        val detail: Any? = null
    )

    private fun throwableMessage(e: Exception, fallback: String): String {
        if (e is HttpException) {
            val detail = runCatching {
                val raw = e.response()?.errorBody()?.string().orEmpty()
                if (raw.isBlank()) return@runCatching null
                val body = gson.fromJson(raw, ErrorBody::class.java)
                when (val d = body.detail) {
                    is String -> d
                    else -> null
                }
            }.getOrNull()
            if (!detail.isNullOrBlank()) return detail
        }
        return e.message ?: fallback
    }

    override suspend fun login(email: String, password: String): Result<TokenResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            tokenDataStore.saveTokens(response.access_token, response.refresh_token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(Exception(throwableMessage(e, "登录失败"), e))
        }
    }

    override suspend fun register(
        username: String, email: String, password: String, code: String
    ): Result<RegisterResponse> {
        return try {
            val response = api.register(RegisterRequest(username, email, password, code))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(Exception(throwableMessage(e, "注册失败"), e))
        }
    }

    override suspend fun sendVerificationCode(email: String): Result<SendCodeResponse> {
        return try {
            Result.success(api.sendVerificationCode(SendCodeRequest(email)))
        } catch (e: Exception) {
            Result.failure(Exception(throwableMessage(e, "发送验证码失败"), e))
        }
    }

    override suspend fun refreshToken(): Result<TokenResponse> {
        return try {
            val refresh = com.geoagent.data.local.TokenDataStore::class
            // Handled by TokenAuthenticator
            Result.failure(Exception("Not implemented directly"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMe(): Result<UserResponse> {
        return try {
            val response = api.getMe()
            if (response.isSuccessful) {
                Result.success(response.body() ?: UserResponse())
            } else {
                Result.failure(Exception("Failed to get user: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMe(fullName: String?, avatarUrl: String?): Result<UserResponse> {
        return try {
            val response = api.updateMe(UpdateProfileRequest(fullName, avatarUrl))
            if (response.isSuccessful) {
                Result.success(response.body() ?: UserResponse())
            } else {
                Result.failure(Exception("Failed to update profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun changePassword(current: String, new: String, confirm: String): Result<Unit> {
        return try {
            val response = api.changePassword(
                ChangePasswordRequest(
                    oldPassword = current,
                    newPassword = new,
                    confirmPassword = confirm
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val detail = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                Result.failure(
                    Exception(detail ?: "修改密码失败: ${response.code()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(throwableMessage(e, "修改密码失败"), e))
        }
    }

    override suspend fun getPreferences(): Result<PreferencesResponse> {
        return try {
            val response = api.getPreferences()
            if (response.isSuccessful) {
                Result.success(response.body() ?: PreferencesResponse())
            } else {
                Result.failure(Exception("获取偏好设置失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(throwableMessage(e, "获取偏好设置失败"), e))
        }
    }

    override suspend fun updatePreferences(theme: String?, language: String?): Result<PreferencesResponse> {
        return try {
            val response = api.updatePreferences(PreferencesUpdateRequest(language = language, theme = theme))
            if (response.isSuccessful) {
                Result.success(response.body() ?: PreferencesResponse())
            } else {
                Result.failure(Exception("更新偏好设置失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(throwableMessage(e, "更新偏好设置失败"), e))
        }
    }

    override suspend fun logout() {
        tokenDataStore.clearTokens()
    }
}