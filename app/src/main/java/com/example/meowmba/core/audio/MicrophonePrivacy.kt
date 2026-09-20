package com.example.meowmba.core.audio

import com.example.meowmba.core.security.SecureStorageManager

/** App-wide microphone opt-out; independent of speaker and background-wake preferences. */
object MicrophonePrivacy {
    const val PREF_KEY = "microphone_killed"

    /** Fresh setup starts muted; upgrades and saved choices keep their existing behavior. */
    fun initializeForFirstRun(storage: SecureStorageManager) {
        if (!storage.getBooleanPreference("onboarding_done", false) &&
            !storage.hasBooleanPreference(PREF_KEY)) {
            storage.saveBooleanPreference(PREF_KEY, true)
        }
    }

    fun isEnabled(storage: SecureStorageManager): Boolean =
        !storage.getBooleanPreference(PREF_KEY, false)
}
