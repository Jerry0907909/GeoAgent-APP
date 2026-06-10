package com.geoagent.ui.theme

import androidx.appcompat.app.AppCompatDelegate

enum class AppThemeMode { LIGHT, DARK, SYSTEM }

object AppThemeHelper {
    fun apply(mode: AppThemeMode) {
        val nightMode = when (mode) {
            AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun fromStored(value: String): AppThemeMode = when (value.lowercase()) {
        "light" -> AppThemeMode.LIGHT
        "dark" -> AppThemeMode.DARK
        else -> AppThemeMode.SYSTEM
    }
}
