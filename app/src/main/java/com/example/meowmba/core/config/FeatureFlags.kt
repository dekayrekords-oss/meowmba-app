package com.example.meowmba.core.config

import android.content.Context
import com.example.meowmba.core.security.SecureStorageManager

/**
 * Process-local feature flags for Lumen reflex v2 and agentic runtime v1.
 * Defaults OFF. Values persist via [SecureStorageManager] boolean prefs.
 *
 * Keys match the product flag names: `lumen_reflex_v2`, `agentic_runtime_v1`.
 */
class FeatureFlags(private val storage: SecureStorageManager) {

    val lumenReflexV2: Boolean
        get() = storage.getBooleanPreference(KEY_LUMEN_REFLEX_V2, DEFAULT_OFF)

    val agenticRuntimeV1: Boolean
        get() = storage.getBooleanPreference(KEY_AGENTIC_RUNTIME_V1, DEFAULT_OFF)

    /** Scales sensory drive push into ReflexState when reflex v2 is on. Default 1.0. */
    val lumenReflexGain: Float
        get() = storage.getPreference(KEY_LUMEN_REFLEX_GAIN)?.toFloatOrNull()
            ?.coerceIn(0.25f, 1.5f) ?: 1f

    fun setLumenReflexV2(enabled: Boolean) {
        storage.saveBooleanPreference(KEY_LUMEN_REFLEX_V2, enabled)
    }

    fun setAgenticRuntimeV1(enabled: Boolean) {
        storage.saveBooleanPreference(KEY_AGENTIC_RUNTIME_V1, enabled)
    }

    fun setLumenReflexGain(gain: Float) {
        storage.savePreference(KEY_LUMEN_REFLEX_GAIN, gain.coerceIn(0.25f, 1.5f).toString())
    }

    /**
     * User Avatar ACTIVE FPS target when thermally cool (15 / 30 / 45 / 60).
     * Default 30 — safer mid after ~60fps overheating. Wired into AvatarFrameCadence.
     */
    val avatarFpsTarget: Int
        get() = storage.getPreference(KEY_AVATAR_FPS_TARGET)?.toIntOrNull()
            ?.coerceIn(15, 60) ?: 30

    fun setAvatarFpsTarget(fps: Int) {
        val snapped = when {
            fps <= 22 -> 15
            fps <= 37 -> 30
            fps <= 52 -> 45
            else -> 60
        }
        storage.savePreference(KEY_AVATAR_FPS_TARGET, snapped.toString())
    }

    companion object {
        const val KEY_LUMEN_REFLEX_V2 = "lumen_reflex_v2"
        const val KEY_AGENTIC_RUNTIME_V1 = "agentic_runtime_v1"
        const val KEY_LUMEN_REFLEX_GAIN = "lumen_reflex_gain"
        const val KEY_AVATAR_FPS_TARGET = "avatar_fps_target"
        private const val DEFAULT_OFF = false

        fun from(context: Context): FeatureFlags =
            FeatureFlags(SecureStorageManager(context.applicationContext))
    }
}
