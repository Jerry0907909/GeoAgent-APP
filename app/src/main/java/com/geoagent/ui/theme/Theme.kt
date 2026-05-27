package com.geoagent.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = StaticBrandPrimary,
    onPrimary = StaticWhite,
    primaryContainer = StaticBrandSoft,
    surface = StaticSurface,
    background = StaticBackground,
    onSurface = StaticTextPrimary,
    outline = StaticBorderSubtle,
    error = StaticDestructive
)

private val DarkColorScheme = darkColorScheme(
    primary = StaticDarkBrandPrimary,
    onPrimary = StaticDarkOnSurface,
    primaryContainer = StaticDarkBrandSoft,
    surface = StaticDarkSurface,
    background = StaticDarkBackground,
    onSurface = StaticDarkOnSurface,
    outline = StaticDarkOutline,
    error = StaticDestructive
)

@Composable
fun GeoAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalGeoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GeoAgentTypography,
            shapes = GeoAgentShapes,
            content = content
        )
    }
}

/**
 * Animated theme wrapper that crossfades between light and dark themes.
 * Use this at the top level (e.g. in MainActivity) for a smooth transition.
 */
@Composable
fun AnimatedGeoAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = darkTheme,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith
            fadeOut(animationSpec = tween(400))
        },
        label = "theme_transition"
    ) { isDark ->
        GeoAgentTheme(darkTheme = isDark) {
            content()
        }
    }
}
