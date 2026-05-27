package com.geoagent.ui.theme

import androidx.compose.runtime.compositionLocalOf

enum class AppThemeMode(val label: String, val apiValue: String?) {
    LIGHT("浅色", "light"),
    DARK("深色", "dark"),
    SYSTEM("跟随系统", null)
}

val LocalAppThemeMode = compositionLocalOf { AppThemeMode.SYSTEM }
