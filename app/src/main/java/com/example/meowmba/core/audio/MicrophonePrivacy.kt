package com.example.meowmba.core.audio

import com.example.meowmba.core.security.SecureStorageManager

/** App-wide microphone opt-out; independent of speaker and background-wake preferences. */
object MicrophonePrivacy {
    const val PREF_KEY = "microphone_killed"

    fun isEnabled(storage: SecureStorageManager): Boolean =
        !storage.getBooleanPreference(PREF_KEY, false)
}
