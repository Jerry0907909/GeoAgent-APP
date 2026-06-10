package com.geoagent.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class VerificationCodeStore(private val context: Context) {

    companion object {
        private val CODE_KEY = stringPreferencesKey("verification_code")
        private val EMAIL_KEY = stringPreferencesKey("verification_email")
        private val EXPIRES_KEY = longPreferencesKey("verification_expires")
        private const val TTL_MS = 5 * 60_000L
    }

    suspend fun saveCode(email: String, code: String) {
        context.dataStore.edit { prefs ->
            prefs[EMAIL_KEY] = email.trim().lowercase()
            prefs[CODE_KEY] = code
            prefs[EXPIRES_KEY] = System.currentTimeMillis() + TTL_MS
        }
    }

    suspend fun verify(email: String, code: String): Boolean {
        val normalizedEmail = email.trim().lowercase()
        val normalizedCode = code.trim()
        val stored = context.dataStore.data.map { prefs ->
            Triple(
                prefs[EMAIL_KEY],
                prefs[CODE_KEY],
                prefs[EXPIRES_KEY] ?: 0L
            )
        }.first()

        val (storedEmail, storedCode, expiresAt) = stored
        if (storedEmail != normalizedEmail || storedCode != normalizedCode) return false
        if (System.currentTimeMillis() > expiresAt) return false
        return true
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(CODE_KEY)
            prefs.remove(EMAIL_KEY)
            prefs.remove(EXPIRES_KEY)
        }
    }
}
