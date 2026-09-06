package com.example.meowmba.ui.companion

/**
 * Which face the companion wears. The cat styles share every animation, pose
 * and gaze input — only the eye rendering differs — so switching is instant and
 * nothing else in the app has to know which one is active. The Jarvis Orb
 * keeps the same pose inputs but renders the Meow OS particle sphere instead.
 */
enum class AvatarStyle(val key: String, val label: String, val description: String) {
    MODEL_3D("model_3d", "3D Model", "Your imported Blender / GLB avatar, with supported live expressions and animations."),
    PEARL(
        key = "pearl",
        label = "Pearl",
        description = "Sculpted floating features, expressive eyes and a soft glow. Inspired by the concept.",
    ),
    SIGNAL(
        key = "signal",
        label = "Jarvis Orb",
        description = "The Meow OS avatar: every dot a model parameter, thinking ejects them.",
    ),

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
