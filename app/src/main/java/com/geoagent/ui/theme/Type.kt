package com.geoagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Typography colors use [Color.Unspecified] so [MaterialTheme.colorScheme] applies in light/dark. */
val GeoAgentTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        color = Color.Unspecified
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        color = Color.Unspecified
    ),
    labelMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        color = Color.Unspecified
    )
)
