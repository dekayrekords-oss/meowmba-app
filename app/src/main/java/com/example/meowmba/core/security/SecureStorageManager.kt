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

    /** Connection wizards call this off the main thread. Do not report a
     * verified connection as saved until encrypted preferences reach disk. */
    fun saveVerifiedApiKey(provider: String, key: String) {
        check(sharedPreferences.edit().putString("api_key_$provider", key).commit()) { "Could not save the key securely; please retry" }
        check(getApiKey(provider) == key) { "The saved key could not be verified" }
    }

    fun saveVerifiedEndpoint(provider: String, endpoint: String) {
        check(sharedPreferences.edit().putString("endpoint_$provider", endpoint).commit()) { "Could not save the model selection; please retry" }
        check(getEndpoint(provider) == endpoint) { "The saved selection could not be verified" }
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

    /** Style and anatomy are one reversible preference, including legacy Cat migration. */
    fun saveAvatarForm(animalKey: String) {
        sharedPreferences.edit().putString("pref_avatar_animal", animalKey)
            .remove("pref_generated_avatar_id")
            .putString("pref_avatar_style", "lumen").apply()
    }

    /** The validated file is written first; one preference edit then selects it. */
    fun saveGeneratedAvatarForm(id: String) {
        require(id.matches(Regex("[a-f0-9]{64}"))) { "Invalid saved avatar ID" }
        sharedPreferences.edit().putString("pref_generated_avatar_id", id)
            .putString("pref_avatar_style", "lumen").apply()
    }

    /** One disk transaction for onboarding, so a saved key/default/readiness
     * cannot be lost halfway through a process restart. Call off the UI thread. */
    fun saveSetupSelection(preferences: Map<String, String>, provider: String? = null,
        apiKey: String? = null, model: String? = null, endpoint: String? = null,
        models: Map<String, String> = emptyMap()) {
        val editor = sharedPreferences.edit()
        preferences.forEach { (name, value) -> editor.putString("pref_$name", value) }
        if (provider != null && apiKey != null) editor.putString("api_key_$provider", apiKey)
        if (provider != null && model != null) editor.putString("model_$provider", model)
        if (provider != null && endpoint != null) editor.putString("endpoint_$provider", endpoint)
        models.forEach { (name, value) -> editor.putString("model_$name", value) }
        check(editor.commit()) { "Could not save setup securely. Please retry." }
    }

    fun saveBooleanPreference(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean("pref_bool_$key", value).apply()
    }

    fun getBooleanPreference(key: String, default: Boolean): Boolean {
        return sharedPreferences.getBoolean("pref_bool_$key", default)
    }
}
