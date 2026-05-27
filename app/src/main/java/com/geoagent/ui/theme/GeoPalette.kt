package com.geoagent.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class GeoPalette(
    val brandPrimary: Color,
    val brandSoft: Color,
    val brandBorder: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textTertiary: Color,
    val surface: Color,
    val background: Color,
    val cardSurface: Color,
    val inputSurface: Color,
    val segmentTrack: Color,
    val dividerLine: Color,
    val borderSubtle: Color,
    val userBubbleBg: Color,
    val userBubbleBorder: Color,
    val onPrimary: Color,
)

val LocalGeoPalette = staticCompositionLocalOf { LightPalette }

internal val LightPalette = GeoPalette(
    brandPrimary = Color(0xFF2563EB),
    brandSoft = Color(0xFFEFF6FF),
    brandBorder = Color(0xFFBFDBFE),
    textPrimary = Color(0xFF111827),
    textMuted = Color(0xFF6B7280),
    textTertiary = Color(0xFF9CA3AF),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF3F4F6),
    cardSurface = Color(0xFFFFFFFF),
    inputSurface = Color(0xFFFFFFFF),
    segmentTrack = Color(0xFFE5E7EB),
    dividerLine = Color(0xFFE5E7EB),
    borderSubtle = Color(0x1A111827),
    userBubbleBg = Color(0xFFF4F7FF),
    userBubbleBorder = Color(0xFFDBE5FF),
    onPrimary = Color(0xFFFFFFFF),
)

internal val DarkPalette = GeoPalette(
    brandPrimary = Color(0xFF5A7FFF),
    brandSoft = Color(0xFF1E293B),
    brandBorder = Color(0xFF334155),
    textPrimary = Color(0xFFF3F4F6),
    textMuted = Color(0xFF9CA3AF),
    textTertiary = Color(0xFF6B7280),
    surface = Color(0xFF1A1A1A),
    background = Color(0xFF0F0F0F),
    cardSurface = Color(0xFF1F1F1F),
    inputSurface = Color(0xFF262626),
    segmentTrack = Color(0xFF374151),
    dividerLine = Color(0xFF2A2A2A),
    borderSubtle = Color(0x33FFFFFF),
    userBubbleBg = Color(0xFF1E3A5F),
    userBubbleBorder = Color(0xFF334155),
    onPrimary = Color(0xFFFFFFFF),
)

private val palette: GeoPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalGeoPalette.current

val BrandPrimary: Color @Composable @ReadOnlyComposable get() = palette.brandPrimary
val BrandSoft: Color @Composable @ReadOnlyComposable get() = palette.brandSoft
val BrandBorder: Color @Composable @ReadOnlyComposable get() = palette.brandBorder
val TextPrimary: Color @Composable @ReadOnlyComposable get() = palette.textPrimary
val TextMuted: Color @Composable @ReadOnlyComposable get() = palette.textMuted
val TextTertiary: Color @Composable @ReadOnlyComposable get() = palette.textTertiary
val Surface: Color @Composable @ReadOnlyComposable get() = palette.surface
val Background: Color @Composable @ReadOnlyComposable get() = palette.background
val CardSurface: Color @Composable @ReadOnlyComposable get() = palette.cardSurface
val InputSurface: Color @Composable @ReadOnlyComposable get() = palette.inputSurface
val SegmentTrack: Color @Composable @ReadOnlyComposable get() = palette.segmentTrack
val DividerLine: Color @Composable @ReadOnlyComposable get() = palette.dividerLine
val BorderSubtle: Color @Composable @ReadOnlyComposable get() = palette.borderSubtle
val UserBubbleBg: Color @Composable @ReadOnlyComposable get() = palette.userBubbleBg
val UserBubbleBorder: Color @Composable @ReadOnlyComposable get() = palette.userBubbleBorder
val White: Color @Composable @ReadOnlyComposable get() = palette.onPrimary

// Static semantic colors (same in light/dark)
val Destructive = Color(0xFFDC2626)
val Success = Color(0xFF16A34A)
val NeutralStrong = Color(0xFF374151)
val ComposerShadow = Color(0x0A111827)
