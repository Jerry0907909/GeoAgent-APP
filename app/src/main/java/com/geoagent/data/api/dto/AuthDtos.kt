package com.geoagent.data.api.dto

import com.google.gson.annotations.SerializedName

// Auth DTOs
data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Int
)

/** FastAPI: POST /auth/refresh body */
data class RefreshTokenRequest(
    val refresh_token: String
)

data class LoginRequest(
    // Backend expects field name "username" (can be username or email)
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val verification_code: String
)

data class RegisterResponse(
    val id: Int,
    val username: String,
    val email: String,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val last_login: String? = null
)

data class SendCodeRequest(val email: String)

/** Matches FastAPI: {"message": "...", "email": "..."} */
data class SendCodeResponse(
    val message: String,
    val email: String? = null
)

/** FastAPI expects `old_password`, not `current_password`. */
data class ChangePasswordRequest(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

data class UpdateProfileRequest(
    val full_name: String?,
    val avatar_url: String?
)