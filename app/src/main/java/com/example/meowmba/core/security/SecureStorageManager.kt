package com.example.meowmba.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages secure storage of API keys and configuration using EncryptedSharedPreferences.
 */
class SecureStorageManager(val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "meowmba_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(provider: String, key: String) {
        sharedPreferences.edit().putString("api_key_$provider", key).apply()
    }

    fun getApiKey(provider: String): String? {
        return sharedPreferences.getString("api_key_$provider", null)
    }

    fun saveEndpoint(provider: String, url: String) {
        sharedPreferences.edit().putString("endpoint_$provider", url).apply()
    }

    fun getEndpoint(provider: String): String? {
        return sharedPreferences.getString("endpoint_$provider", null)
    }

    fun saveModelName(provider: String, model: String) {
        sharedPreferences.edit().putString("model_$provider", model).apply()
    }

    fun getModelName(provider: String): String? {
        return sharedPreferences.getString("model_$provider", null)
    }

    /** Generic encrypted preference for app settings (voice mode, companion name). */
    fun savePreference(key: String, value: String) {
        sharedPreferences.edit().putString("pref_$key", value).apply()
    }

    fun getPreference(key: String): String? {
        return sharedPreferences.getString("pref_$key", null)
    }

    fun saveBooleanPreference(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean("pref_bool_$key", value).apply()
    }

    fun getBooleanPreference(key: String, default: Boolean): Boolean {
        return sharedPreferences.getBoolean("pref_bool_$key", default)
    }
}
