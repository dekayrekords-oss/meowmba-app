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

    private val sharedPreferences = com.example.meowmba.core.backup.BackupPreferences.protect(EncryptedSharedPreferences.create(
        context,
        "meowmba_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    ))

    fun saveApiKey(provider: String, key: String) {
        sharedPreferences.edit().putString("api_key_$provider", key).apply()
    }

    fun getApiKey(provider: String): String? {
        return sharedPreferences.getString("api_key_$provider", null)
    }

    /** Connection endpoint and secret must change atomically, including after a crash. */
    fun saveVerifiedConnection(provider: String, key: String, endpoint: String) {
        check(sharedPreferences.edit().putString("api_key_$provider", key)
            .putString("endpoint_$provider", endpoint).commit()) { "Could not save the private connection." }
        check(getApiKey(provider) == key && getEndpoint(provider) == endpoint) { "Connection save could not be verified." }
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

    fun hasBooleanPreference(key: String): Boolean =
        sharedPreferences.contains("pref_bool_$key")

    fun getBooleanPreference(key: String, default: Boolean): Boolean {
        return sharedPreferences.getBoolean("pref_bool_$key", default)
    }

    /** Decrypted logical values; only the password-encrypted archive may leave private storage. */
    internal fun portableValues(): Map<String, Any?> = sharedPreferences.all.filterKeys(::isPortableKey)

    /** Missing values only, re-encrypted with this installation's Android Keystore key. */
    @Suppress("UNCHECKED_CAST")
    internal fun importPortableValues(values: Map<String, Any?>): Int = com.example.meowmba.core.backup.BackupPreferences.locked {
        require(values.keys.all(::isPortableKey))
        require(values.values.all { it is String || it is Boolean || it is Int || it is Long || it is Float || (it is Set<*> && it.all { v -> v is String }) })
        val editor = sharedPreferences.edit()
        var count = 0
        values.forEach { (key, value) ->
            if (!sharedPreferences.contains(key)) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value as Set<String>)
                }
                count++
            }
        }
        check(editor.commit()) { "Could not restore private settings" }
        count
    }

    companion object {
        internal fun isPortableKey(key: String): Boolean = key.length in 1..1024 && '\u0000' !in key &&
            listOf("api_key_", "endpoint_", "model_", "pref_").any(key::startsWith)
    }
}
