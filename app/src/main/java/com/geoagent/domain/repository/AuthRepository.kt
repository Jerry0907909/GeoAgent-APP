package com.geoagent.domain.repository

import com.geoagent.data.api.dto.EmailHistoryResponse
import com.geoagent.data.api.dto.EmailSendResponse
import com.geoagent.data.api.dto.UserResponse

interface AuthRepository {
    suspend fun loginWithPassword(username: String, password: String): Result<Unit>
    suspend fun loginWithEmailCode(email: String, code: String): Result<Unit>
    suspend fun register(username: String, email: String, password: String, code: String): Result<Unit>
    suspend fun sendVerificationCode(email: String): Result<Unit>
    suspend fun getMe(): Result<UserResponse>
    suspend fun updateMe(fullName: String?, avatarUrl: String? = null): Result<UserResponse>
    suspend fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String): Result<Unit>
    suspend fun resetPassword(email: String, code: String, newPassword: String, confirmPassword: String): Result<Unit>
    suspend fun isLoggedIn(): Boolean
    suspend fun sendEmail(toAddr: String, subject: String, content: String): Result<EmailSendResponse>
    suspend fun getEmailHistory(limit: Int = 20): Result<EmailHistoryResponse>
    suspend fun logout()
}
