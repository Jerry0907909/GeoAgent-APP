package com.geoagent.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.geoagent.BuildConfig
import com.geoagent.data.api.GeoAgentAuthApi
import com.geoagent.data.api.dto.UserSettingsRequest
import com.geoagent.data.local.DocumentStore
import com.geoagent.data.local.TokenDataStore
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.domain.repository.ChatRepository
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPrefsDataStore: UserPrefsDataStore,
    private val tokenDataStore: TokenDataStore,
    private val authApi: GeoAgentAuthApi,
    private val chatRepository: ChatRepository,
    private val documentStore: DocumentStore,
    private val memoryRepository: V2MemoryRepository
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class ExportResult(
        val file: File,
        val uri: Uri,
        val source: String
    )

    data class UsageStats(
        val conversationCount: Int,
        val documentCount: Int,
        val memoryCount: Int,
        val taskCount: Int
    )

    suspend fun syncSettings(request: UserSettingsRequest): Result<Unit> {
        val token = tokenDataStore.accessToken.first().orEmpty()
        if (token.isBlank() || token.startsWith("local-")) return Result.success(Unit)
        return authApi.updateSettings(token, request).map { Unit }
    }

    suspend fun exportUserData(): Result<ExportResult> = runCatching {
        val token = tokenDataStore.accessToken.first().orEmpty()
        val remoteExport = if (token.isNotBlank() && !token.startsWith("local-")) {
            authApi.exportUserData(token).getOrNull()
        } else {
            null
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val filename = remoteExport?.filename?.takeIf { it.isNotBlank() }
            ?: "geoagent-export-$timestamp.json"
        val content = remoteExport?.content?.takeIf { it.isNotBlank() }
            ?: buildLocalExportJson()
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, filename.sanitizeFileName())
        exportFile.writeText(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            exportFile
        )
        ExportResult(exportFile, uri, if (remoteExport != null) "后端" else "本地")
    }

    suspend fun clearAllUserData(): Result<Unit> = runCatching {
        val token = tokenDataStore.accessToken.first().orEmpty()
        if (token.isNotBlank() && !token.startsWith("local-")) {
            authApi.deleteAllUserData(token)
        }
        chatRepository.clearAllConversations().getOrThrow()
        documentStore.deleteAllDocuments()
        memoryRepository.clearAll()
        userPrefsDataStore.clearUserDataPreferences()
    }

    suspend fun usageStats(): UsageStats {
        val conversations = chatRepository.listConversations(limit = 500).getOrDefault(emptyList())
        val documents = documentStore.getDocumentsSnapshot()
        val memories = memoryRepository.recentMemories(100)
        val tasks = memoryRepository.recentTasks(100)
        return UsageStats(
            conversationCount = conversations.size,
            documentCount = documents.size,
            memoryCount = memories.size,
            taskCount = tasks.size
        )
    }

    private suspend fun buildLocalExportJson(): String {
        val conversations = chatRepository.listConversations(limit = 500).getOrDefault(emptyList())
        val documents = documentStore.getDocumentsSnapshot()
        val tasks = memoryRepository.recentTasks(100)
        val payload = mapOf(
            "exported_at" to System.currentTimeMillis(),
            "settings" to mapOf(
                "data_improve_enabled" to userPrefsDataStore.dataImproveEnabled.first(),
                "incognito_enabled" to userPrefsDataStore.incognitoEnabled.first(),
                "memory_enabled" to userPrefsDataStore.memoryEnabled.first(),
                "push_enabled" to userPrefsDataStore.pushEnabled.first(),
                "email_alerts_enabled" to userPrefsDataStore.emailAlertsEnabled.first(),
                "custom_instruction" to userPrefsDataStore.customInstruction.first()
            ),
            "conversations" to conversations,
            "documents" to documents,
            "tasks" to tasks
        )
        return gson.toJson(payload)
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "geoagent-export.json" }
}
