package com.example.meowmba.core.config

import android.content.Context
import com.example.meowmba.core.security.SecureStorageManager

/**
 * Process-local feature flags for Lumen reflex v2 and agentic runtime v1.
 * Defaults OFF. Values persist via [SecureStorageManager] boolean prefs.
 *
 * Keys match the product flag names: `lumen_reflex_v2`, `agentic_runtime_v1`.
 * Computer Off/Idle/On lives on [ComputerPowerStore] (not a feature flag) so
 * Off can skip Computer background ticks without touching these keys.
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
     * User Avatar ACTIVE FPS target when thermally cool (15–60 inclusive, 1 FPS steps).
     * Default 30 — safer mid after ~60fps overheating. Wired into AvatarFrameCadence.
     */
    val avatarFpsTarget: Int
        get() = storage.getPreference(KEY_AVATAR_FPS_TARGET)?.toIntOrNull()
            ?.coerceIn(15, 60) ?: 30

    fun setAvatarFpsTarget(fps: Int) {
        storage.savePreference(KEY_AVATAR_FPS_TARGET, fps.coerceIn(15, 60).toString())
    }

    companion object {
        const val KEY_LUMEN_REFLEX_V2 = "lumen_reflex_v2"
        const val KEY_AGENTIC_RUNTIME_V1 = "agentic_runtime_v1"
        const val KEY_LUMEN_REFLEX_GAIN = "lumen_reflex_gain"
        const val KEY_AVATAR_FPS_TARGET = "avatar_fps_target"
        private const val DEFAULT_OFF = false

        @Volatile private var cached: Pair<Context, FeatureFlags>? = null

        /** Reuse the application-owned encrypted store across recompositions.
         * Recreating MasterKey/EncryptedSharedPreferences on every UI read caused
         * needless keystore work. Values themselves remain live preference reads. */
        fun from(context: Context): FeatureFlags {
            val app = context.applicationContext ?: context
            cached?.let { if (it.first === app) return it.second }
            return synchronized(this) {
                cached?.takeIf { it.first === app }?.second
                    ?: FeatureFlags(SecureStorageManager.create(app)).also { cached = app to it }
            }
        }
    }
}
