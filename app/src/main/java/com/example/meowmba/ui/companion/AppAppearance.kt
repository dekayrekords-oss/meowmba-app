package com.example.meowmba.ui.companion

/** App layout and brightness are independent; changing one never resets the other. */
enum class AppTheme(val key: String, val label: String) {
    COMPANION("companion", "Companion"),
    PROFESSIONAL("professional", "Professional"),
    FLOW("flow", "Meow Flow");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: COMPANION
    }
}

enum class AppearanceMode(val key: String, val label: String) {
    SYSTEM("system", "System"), LIGHT("light", "Light"), DARK("dark", "Dark");

    fun isDark(systemDark: Boolean) = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DARK
    }
}
