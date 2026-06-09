package com.geoagent.data.api.dto

data class UserResponse(
    val id: Int = 0,
    val username: String = "",
    val email: String = "",
    val full_name: String? = null,
    val avatar_url: String? = null,
    val is_active: Boolean = true,
    val is_superuser: Boolean = false
)

data class TokenResponse(
    val access_token: String = "",
    val refresh_token: String = "",
    val token_type: String = "bearer",
    val expires_in: Int = 1800
)

data class LoginRequest(
    val username: String,
    val password: String? = null,
    val verification_code: String? = null
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val verification_code: String,
    val full_name: String? = null
)

data class SendVerificationCodeRequest(val email: String)

data class VerificationCodeResponse(
    val message: String = "",
    val email: String = ""
)

data class UserUpdateRequest(
    val full_name: String? = null,
    val avatar_url: String? = null
)

data class PasswordChangeRequest(
    val old_password: String,
    val new_password: String,
    val confirm_password: String
)

data class MessageResponse(val message: String = "")

data class EmailSendRequest(
    val to_addr: String,
    val subject: String,
    val content: String
)

data class EmailSendResponse(
    val success: Boolean = true,
    val message: String = ""
)

data class EmailHistoryItem(
    val to_addr: String = "",
    val subject: String = "",
    val content: String = "",
    val sent_at: Long = 0L
)

data class EmailHistoryResponse(
    val total: Int = 0,
    val items: List<EmailHistoryItem> = emptyList()
)

data class UserSettingsRequest(
    val theme: String? = null,
    val custom_instruction: String? = null,
    val data_improve_enabled: Boolean? = null,
    val incognito_enabled: Boolean? = null,
    val memory_enabled: Boolean? = null,
    val enable_memory: Boolean? = null,
    val push_enabled: Boolean? = null,
    val email_alerts_enabled: Boolean? = null
)

data class UserDataExportResponse(
    val content: String = "",
    val filename: String = "geoagent-export.json"
)
