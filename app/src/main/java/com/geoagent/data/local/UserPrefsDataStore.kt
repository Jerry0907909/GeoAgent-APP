package com.geoagent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** theme: light | dark | system */
class UserPrefsDataStore(private val context: Context) {

    val themeMode: Flow<String> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "system"
    }

    val localAvatarUri: Flow<String?> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_AVATAR_URI]
    }

    val dataImproveEnabled: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_DATA_IMPROVE] ?: false
    }

    val incognitoEnabled: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_INCOGNITO] ?: false
    }

    val memoryEnabled: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_MEMORY] ?: true
    }

    val pushEnabled: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_PUSH] ?: false
    }

    val emailAlertsEnabled: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_EMAIL_ALERTS] ?: false
    }

    val customInstruction: Flow<String> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_INSTRUCTION] ?: ""
    }

    suspend fun setThemeMode(mode: String) {
        context.userPrefsDataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun setDataImproveEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_DATA_IMPROVE] = enabled }
    }

    suspend fun setIncognitoEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_INCOGNITO] = enabled }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_MEMORY] = enabled }
    }

    suspend fun setPushEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_PUSH] = enabled }
    }

    suspend fun setEmailAlertsEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_EMAIL_ALERTS] = enabled }
    }

    suspend fun setCustomInstruction(instruction: String) {
        context.userPrefsDataStore.edit {
            val normalized = instruction.trim()
            if (normalized.isBlank()) it.remove(KEY_CUSTOM_INSTRUCTION)
            else it[KEY_CUSTOM_INSTRUCTION] = normalized
        }
    }

    suspend fun setLocalAvatarUri(uri: String?) {
        context.userPrefsDataStore.edit {
            if (uri.isNullOrBlank()) it.remove(KEY_AVATAR_URI)
            else it[KEY_AVATAR_URI] = uri
        }
    }

    suspend fun clearUserDataPreferences() {
        context.userPrefsDataStore.edit {
            val theme = it[KEY_THEME]
            it.clear()
            if (theme != null) it[KEY_THEME] = theme
        }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_AVATAR_URI = stringPreferencesKey("local_avatar_uri")
        private val KEY_DATA_IMPROVE = booleanPreferencesKey("data_improve_enabled")
        private val KEY_INCOGNITO = booleanPreferencesKey("incognito_enabled")
        private val KEY_MEMORY = booleanPreferencesKey("memory_enabled")
        private val KEY_PUSH = booleanPreferencesKey("push_enabled")
        private val KEY_EMAIL_ALERTS = booleanPreferencesKey("email_alerts_enabled")
        private val KEY_CUSTOM_INSTRUCTION = stringPreferencesKey("custom_instruction")
    }
}
