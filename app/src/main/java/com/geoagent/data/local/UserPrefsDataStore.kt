package com.geoagent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    suspend fun setThemeMode(mode: String) {
        context.userPrefsDataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun setLocalAvatarUri(uri: String?) {
        context.userPrefsDataStore.edit {
            if (uri.isNullOrBlank()) it.remove(KEY_AVATAR_URI)
            else it[KEY_AVATAR_URI] = uri
        }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_AVATAR_URI = stringPreferencesKey("local_avatar_uri")
    }
}
