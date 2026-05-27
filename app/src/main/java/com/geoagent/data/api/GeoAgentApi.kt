package com.geoagent.data.api

import com.geoagent.data.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface GeoAgentApi {

    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("auth/send-verification-code")
    suspend fun sendVerificationCode(@Body request: SendCodeRequest): SendCodeResponse

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): TokenResponse

    @GET("auth/me")
    suspend fun getMe(): Response<UserResponse>

    @PUT("auth/me")
    suspend fun updateMe(@Body request: UpdateProfileRequest): Response<UserResponse>

    @POST("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<SendCodeResponse>

    @GET("auth/preferences")
    suspend fun getPreferences(): Response<PreferencesResponse>

    @PUT("auth/preferences")
    suspend fun updatePreferences(@Body request: PreferencesUpdateRequest): Response<PreferencesResponse>

    // Chat
    @POST("chat")
    suspend fun chat(@Body request: ChatStreamRequest): ChatResponse

    @POST("chat/follow-up")
    suspend fun followUp(@Body request: FollowUpRequest): FollowUpResponse

    @GET("chat/conversations")
    suspend fun listConversations(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): ConversationListResponse

    @GET("chat/conversations/{conversationId}/messages")
    suspend fun getConversationMessages(
        @Path("conversationId") conversationId: Int
    ): MessageListResponse

    // Documents
    @GET("documents/list")
    suspend fun getDocuments(@Query("collection") collection: String? = null): DocumentListResponse

    @Multipart
    @POST("documents/upload-file")
    suspend fun uploadFile(
        @Part file: okhttp3.MultipartBody.Part,
        @Part("collection") collection: okhttp3.RequestBody?
    ): DocumentUploadResponse

    @DELETE("documents/by-source/{source_name}")
    suspend fun deleteDocumentBySource(
        @Path(value = "source_name", encoded = true) sourceName: String,
        @Query("collection") collection: String? = null
    ): DeleteResponse

    @GET("documents/collections")
    suspend fun getCollections(): CollectionListResponse

    @GET("documents/content/{source_name}")
    suspend fun getDocumentContent(
        @Path(value = "source_name", encoded = true) sourceName: String,
        @Query("collection") collection: String? = null
    ): DocumentContentResponse
}

data class UserResponse(
    val id: Int = 0,
    val username: String = "",
    val email: String = "",
    val full_name: String? = null,
    val avatar_url: String? = null,
    val is_active: Boolean = true,
    val is_superuser: Boolean = false
)