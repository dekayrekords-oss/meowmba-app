package com.example.meowmba.core.update

import android.content.Context

/**
 * Non-secret Preview update prefs: apk-serve base URL, last check time, last
 * notified versionCode (dedupe), and optional LAN fallback base.
 */
class PreviewUpdateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE, DEFAULT_BASE_URL)?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_BASE_URL
        set(value) {
            val cleaned = value.trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }
            prefs.edit().putString(KEY_BASE, cleaned).apply()
        }

    var lanFallbackUrl: String
        get() = prefs.getString(KEY_LAN, DEFAULT_LAN_URL)?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_LAN_URL
        set(value) {
            val cleaned = value.trim().trimEnd('/').ifBlank { DEFAULT_LAN_URL }
            prefs.edit().putString(KEY_LAN, cleaned).apply()
        }

    var useLanFallback: Boolean
        get() = prefs.getBoolean(KEY_USE_LAN, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_LAN, value).apply()

    var lastCheckEpochMs: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    var lastNotifiedVersionCode: Int
        get() = prefs.getInt(KEY_NOTIFIED_VC, 0)
        set(value) = prefs.edit().putInt(KEY_NOTIFIED_VC, value).apply()

    fun feedUrl(base: String = baseUrl): String = "${base.trimEnd('/')}/$FEED_PATH"

    fun getPreviewUrl(base: String = baseUrl, alt: Boolean): String =
        "${base.trimEnd('/')}/" + if (alt) "get-preview-alt.html" else "get-preview.html"

    fun apkUrl(base: String = baseUrl, filename: String): String =
        "${base.trimEnd('/')}/${filename.trimStart('/')}"

    companion object {
        const val PREFS = "preview_update"
        const val FEED_PATH = "preview-version.json"
        const val DEFAULT_BASE_URL = "http://100.77.97.236:8765"
        const val DEFAULT_LAN_URL = "http://192.168.1.69:8765"
        private const val KEY_BASE = "base_url"
        private const val KEY_LAN = "lan_url"
        private const val KEY_USE_LAN = "use_lan_fallback"
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val KEY_NOTIFIED_VC = "last_notified_vc"

        /** Minimum gap between automatic (resume / WorkManager) checks. */
        const val MIN_AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
