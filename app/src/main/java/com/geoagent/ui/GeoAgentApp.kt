package com.geoagent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.navigation.GeoNavHost
import com.geoagent.ui.theme.AnimatedGeoAgentTheme
import org.koin.androidx.compose.get

@Composable
fun GeoAgentApp() {
    val userPrefs: UserPrefsDataStore = get()
    val themeModeStr by userPrefs.themeMode.collectAsState(initial = "system")

    val darkTheme = when (themeModeStr.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    AnimatedGeoAgentTheme(darkTheme = darkTheme) {
        GeoNavHost()
    }
}
