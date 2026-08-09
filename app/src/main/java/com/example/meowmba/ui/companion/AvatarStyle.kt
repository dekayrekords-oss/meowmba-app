package com.example.meowmba.ui.companion

/**
 * Which face the companion wears. The two styles share every animation, pose
 * and gaze input — only the eye rendering differs — so switching is instant and
 * nothing else in the app has to know which one is active.
 */
enum class AvatarStyle(val key: String, val label: String, val description: String) {
    /** The realistic cat eye: almond aperture, iris fibres, slit pupil. */
    NEO(
        key = "neo",
        label = "Godot",
        description = "Cat Assistant look — floating features, green almond eyes, Godot motion.",
    ),

    /** The original capsule eye the app shipped with. */
    CLASSIC(
        key = "classic",
        label = "Classic",
        description = "The original look: soft glowing capsules with rounded pupils.",
    );

    companion object {
        val DEFAULT = NEO

        /** Unknown or missing keys fall back rather than crashing on stale prefs. */
        fun fromKey(key: String?): AvatarStyle =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
