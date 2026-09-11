package com.example.meowmba.core.config

import android.content.Context
import com.example.meowmba.core.security.SecureStorageManager

/**
 * Power for the local **Computer** Linux guest (Termux/PRoot if available, else sandbox sh).
 *
 * - [On]: start/keep the guest session. Terminal Connect works.
 * - [Idle]: unused for [ComputerPowerStore.idleMinutes] — PTY disconnected, guest
 *   processes stopped, disk/state kept. Cheap. UI shows Idle.
 * - [Off]: user toggle — kill session, no Computer background ticks, no Termux/PRoot
 *   held by this app. Saves CPU. Disk/state remain so Turn On reconnects (no reinstall).
 *
 * Guest root (PRoot) ≠ phone root. The guest can SSH out over the network.
 * See docs/LOCAL_LINUX.md.
 */
enum class ComputerPowerState {
    Off,
    Idle,
    On,
}

/**
 * Persists Computer power + idle timeout. Dedicated store (not a feature flag)
 * so Off can skip all Computer ticks without going through [FeatureFlags].
 */
class ComputerPowerStore(private val storage: SecureStorageManager) {

    val state: ComputerPowerState
        get() = storage.getPreference(KEY_STATE)
            ?.let { runCatching { ComputerPowerState.valueOf(it) }.getOrNull() }
            ?: DEFAULT_STATE

    fun setState(next: ComputerPowerState) {
        storage.savePreference(KEY_STATE, next.name)
    }

    /** Idle timeout in minutes while On (1–30). Default 5. */
    val idleMinutes: Int
        get() = storage.getPreference(KEY_IDLE_MINUTES)?.toIntOrNull()?.coerceIn(MIN_IDLE, MAX_IDLE)
            ?: DEFAULT_IDLE_MINUTES

    fun setIdleMinutes(minutes: Int) {
        storage.savePreference(KEY_IDLE_MINUTES, minutes.coerceIn(MIN_IDLE, MAX_IDLE).toString())
    }

    companion object {
        const val KEY_STATE = "computer_power_state"
        const val KEY_IDLE_MINUTES = "computer_idle_minutes"
        const val MIN_IDLE = 1
        const val MAX_IDLE = 30
        const val DEFAULT_IDLE_MINUTES = 5
        val DEFAULT_STATE = ComputerPowerState.Idle

        const val COPY =
            "Computer is a Linux guest (root inside only). It can SSH out. Off = no CPU. Idle = asleep."

        fun from(context: Context): ComputerPowerStore =
            ComputerPowerStore(SecureStorageManager(context.applicationContext))
    }
}
