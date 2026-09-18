package com.example.meowmba.core.car

/**
 * Best-effort vehicle identity / projection awareness for Android.
 *
 * Honest limits: third-party Android apps cannot own an Apple CarPlay session
 * (radio / phone / audio / templates) the way a native CarPlay entitlement app
 * can. This model surfaces what Meow *can* observe: Bluetooth hands-free unit
 * name/address, Android Auto / projection hints via system features, and
 * optional user-labeled vehicle name.
 */
data class VehicleIdentity(
    val displayName: String = "",
    val bluetoothAddress: String = "",
    val bluetoothConnected: Boolean = false,
    val androidAutoLikely: Boolean = false,
    val carPlayRemoteControlAvailable: Boolean = false,
    val notes: String = "",
) {
    val known: Boolean
        get() = displayName.isNotBlank() || bluetoothAddress.isNotBlank() || bluetoothConnected
}

object VehicleAwareness {
    const val CARPLAY_LIMIT_NOTE =
        "Apple CarPlay session control (radio/phone/audio templates) requires an " +
            "Apple CarPlay entitlement / companion iOS path. On Android, Meow uses " +
            "MediaSession, AudioManager, Bluetooth phone intents, and Android Auto " +
            "projection when the OEM exposes it — not full CarPlay ownership."

    fun fromBluetooth(
        deviceName: String?,
        address: String?,
        connected: Boolean,
        hasAndroidAutoFeature: Boolean = false,
    ): VehicleIdentity = VehicleIdentity(
        displayName = deviceName?.trim().orEmpty(),
        bluetoothAddress = address?.trim().orEmpty(),
        bluetoothConnected = connected,
        androidAutoLikely = hasAndroidAutoFeature && connected,
        carPlayRemoteControlAvailable = false,
        notes = CARPLAY_LIMIT_NOTE,
    )

    fun summary(id: VehicleIdentity): String = buildString {
        when {
            id.displayName.isNotBlank() -> append(id.displayName)
            id.bluetoothAddress.isNotBlank() -> append("BT ${id.bluetoothAddress}")
            else -> append("Unknown vehicle")
        }
        if (id.bluetoothConnected) append(" · connected")
        if (id.androidAutoLikely) append(" · Android Auto possible")
        append(" · CarPlay remote: unavailable on Android third-party apps")
    }
}
