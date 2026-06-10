package com.geoagent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tokens")

class TokenDataStore(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN_KEY] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN_KEY] }

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = access
            prefs[REFRESH_TOKEN_KEY] = refresh
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { it.clear() }
    }
}