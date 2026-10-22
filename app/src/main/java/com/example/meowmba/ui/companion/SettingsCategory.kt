package com.example.meowmba.ui.companion

import androidx.compose.ui.graphics.vector.ImageVector

/** Separate from the large screen so Kotlin lint can resolve constructor properties. */
internal data class SettingsCategory(val title: String, val summary: String, val icon: ImageVector)
