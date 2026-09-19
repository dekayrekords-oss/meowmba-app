package com.example.meowmba.ui.companion

/** Fast controls drawing/review complexity, not an unrealistically short free-provider queue. */
internal object InventTimeBudget {
    fun prop(remote: Boolean, level: InventReasonLevel): Long = if (remote) when (level) {
        InventReasonLevel.FAST -> 180_000L
        InventReasonLevel.BALANCED -> 210_000L
        InventReasonLevel.THOROUGH -> 240_000L
    } else when (level) {
        InventReasonLevel.FAST -> 60_000L
        InventReasonLevel.BALANCED -> 75_000L
        InventReasonLevel.THOROUGH -> 100_000L
    }
}
