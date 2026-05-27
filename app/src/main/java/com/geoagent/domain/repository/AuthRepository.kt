package com.geoagent.domain.repository

import com.geoagent.data.api.dto.*
import com.geoagent.data.api.UserResponse

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<TokenResponse>
    suspend fun register(username: String, email: String, password: String, code: String): Result<RegisterResponse>
    suspend fun sendVerificationCode(email: String): Result<SendCodeResponse>
    suspend fun refreshToken(): Result<TokenResponse>
    suspend fun getMe(): Result<UserResponse>
    suspend fun updateMe(fullName: String?, avatarUrl: String?): Result<UserResponse>
    suspend fun changePassword(current: String, new: String, confirm: String): Result<Unit>
    suspend fun getPreferences(): Result<PreferencesResponse>
    suspend fun updatePreferences(theme: String?, language: String? = null): Result<PreferencesResponse>
    suspend fun logout()
}