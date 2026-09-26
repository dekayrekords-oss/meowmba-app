package com.example.meowmba.ui.companion

/**
 * Owner request 2026-09-25: "when driving the avatar should automatically appear."
 * While a drive is detected, Flow and the Desk step aside for the full avatar (drive reactions,
 * drive card). When the drive ends the previous home comes back. The switch is never saved as
 * the user's choice, and a theme the user picks during the drive wins.
 */
internal object DriveAvatarPolicy {
    /** Returns the theme to show and the theme to return to after the drive (null = nothing pending). */
    fun onDriving(driving: Boolean, current: AppTheme, returnTo: AppTheme?): Pair<AppTheme, AppTheme?> = when {
        driving && returnTo == null && (current == AppTheme.FLOW || current == AppTheme.DESK) -> AppTheme.COMPANION to current
        !driving && returnTo != null -> (if (current == AppTheme.COMPANION) returnTo else current) to null
        else -> current to returnTo
    }
}
