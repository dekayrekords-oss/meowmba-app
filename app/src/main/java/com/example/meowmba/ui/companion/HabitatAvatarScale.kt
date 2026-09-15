package com.example.meowmba.ui.companion

/** Applied at the habitat root, never written back into the user's size preference. */
internal const val HABITAT_AVATAR_MAGNIFICATION = 1.30f

internal fun habitatFaceScale(userScale: Float): Float =
    .40f * boundedAvatarScale(userScale) * HABITAT_AVATAR_MAGNIFICATION

internal fun habitatFlyScale(userScale: Float): Float =
    1.22f * boundedAvatarScale(userScale) * HABITAT_AVATAR_MAGNIFICATION

private fun boundedAvatarScale(value: Float) = value.takeIf { it.isFinite() }?.coerceIn(.5f, 3f) ?: 2f
