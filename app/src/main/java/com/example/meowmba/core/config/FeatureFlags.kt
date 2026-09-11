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

    fun setLumenReflexV2(enabled: Boolean) {
        storage.saveBooleanPreference(KEY_LUMEN_REFLEX_V2, enabled)
    }

    fun setAgenticRuntimeV1(enabled: Boolean) {
        storage.saveBooleanPreference(KEY_AGENTIC_RUNTIME_V1, enabled)
    }

    companion object {
        const val KEY_LUMEN_REFLEX_V2 = "lumen_reflex_v2"
        const val KEY_AGENTIC_RUNTIME_V1 = "agentic_runtime_v1"
        private const val DEFAULT_OFF = false

        fun from(context: Context): FeatureFlags =
            FeatureFlags(SecureStorageManager(context.applicationContext))
    }
}
