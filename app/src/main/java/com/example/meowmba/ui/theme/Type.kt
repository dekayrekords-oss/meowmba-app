package com.example.meowmba.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Shared native workbench hierarchy. System sans respects Android font fallback and scaling. */
private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val Typography = Typography(
    displayLarge = type(48, 56, FontWeight.SemiBold, -.5f),
    displayMedium = type(40, 48, FontWeight.SemiBold, -.4f),
    displaySmall = type(36, 44, FontWeight.SemiBold, -.3f),
    headlineLarge = type(32, 40, FontWeight.SemiBold, -.3f),
    headlineMedium = type(28, 36, FontWeight.SemiBold, -.2f),
    headlineSmall = type(24, 32, FontWeight.SemiBold, -.1f),
    titleLarge = type(22, 28, FontWeight.SemiBold),
    titleMedium = type(16, 24, FontWeight.SemiBold),
    titleSmall = type(14, 20, FontWeight.SemiBold),
    bodyLarge = type(16, 24),
    bodyMedium = type(14, 22),
    bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.Medium, .1f),
    labelMedium = type(12, 18, FontWeight.Medium, .1f),
    labelSmall = type(11, 16, FontWeight.Medium, .1f),
)
