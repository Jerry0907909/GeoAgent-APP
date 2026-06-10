package com.geoagent.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ApiKeyStore(private val context: Context) {

    companion object {
        private val DEEPSEEK_KEY = stringPreferencesKey("deepseek_api_key")
        private val TAVILY_KEY = stringPreferencesKey("tavily_api_key")
        private val SILICONFLOW_KEY = stringPreferencesKey("siliconflow_api_key")
        private val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        private val CURRENT_USER_EMAIL = stringPreferencesKey("current_user_email")
    }

    val deepseekKey: Flow<String?> = context.dataStore.data.map { it[DEEPSEEK_KEY] }
    val tavilyKey: Flow<String?> = context.dataStore.data.map { it[TAVILY_KEY] }
    val siliconFlowKey: Flow<String?> = context.dataStore.data.map { it[SILICONFLOW_KEY] }
    val displayName: Flow<String?> = context.dataStore.data.map { it[USER_DISPLAY_NAME] }
    val currentUserEmail: Flow<String?> = context.dataStore.data.map { it[CURRENT_USER_EMAIL] }

    suspend fun saveDeepseekKey(key: String) {
        context.dataStore.edit { it[DEEPSEEK_KEY] = key.trim() }
    }

    suspend fun saveTavilyKey(key: String) {
        context.dataStore.edit { it[TAVILY_KEY] = key.trim() }
    }

    suspend fun saveSiliconFlowKey(key: String) {
        context.dataStore.edit { it[SILICONFLOW_KEY] = key.trim() }
    }

    suspend fun saveDisplayName(name: String) {
        context.dataStore.edit { it[USER_DISPLAY_NAME] = name.trim() }
    }

    suspend fun saveCurrentUser(email: String) {
        context.dataStore.edit { it[CURRENT_USER_EMAIL] = email.trim() }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(CURRENT_USER_EMAIL)
            it.remove(USER_DISPLAY_NAME)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
