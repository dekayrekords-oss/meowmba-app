package com.example.meowmba.core.update

import org.json.JSONObject

/**
 * Canonical Preview version feed served from apk-serve as `preview-version.json`.
 *
 * Written by the Preview ship step whenever an APK is published.
 */
data class PreviewVersionFeed(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
    val size: Long,
    val notes: String? = null,
    val publishedAt: String? = null,
    /** Optional alt (Claude-key) APK filename when published alongside prog. */
    val apkAlt: String? = null,
    val sha256Alt: String? = null,
    val sizeAlt: Long? = null,
) {
    companion object {
        fun parse(json: String): PreviewVersionFeed {
            val root = JSONObject(json)
            val versionCode = root.getInt("versionCode")
            require(versionCode > 0) { "versionCode must be positive" }
            val apk = root.getString("apk").trim()
            require(apk.isNotEmpty()) { "apk filename required" }
            return PreviewVersionFeed(
                versionCode = versionCode,
                versionName = root.optString("versionName", "").ifBlank { "0.0.0" },
                apk = apk,
                sha256 = root.optString("sha256", ""),
                size = root.optLong("size", 0L),
                notes = root.optionalString("notes"),
                publishedAt = root.optionalString("publishedAt"),
                apkAlt = root.optionalString("apkAlt"),
                sha256Alt = root.optionalString("sha256Alt"),
                sizeAlt = if (root.has("sizeAlt") && !root.isNull("sizeAlt")) root.getLong("sizeAlt") else null,
            )
        }

        private fun JSONObject.optionalString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key, "").trim().takeIf { it.isNotEmpty() }
        }
    }
}

enum class PreviewSignerKind {
    /** prog debug keystore (b7850d5f…). */
    PROG,
    /** Claude / Fold matching keystore (75b19d5a…). */
    CLAUDE,
    UNKNOWN,
}

sealed class PreviewUpdateResult {
    data class UpToDate(val installedCode: Int, val feed: PreviewVersionFeed?) : PreviewUpdateResult()
    data class Available(
        val installedCode: Int,
        val feed: PreviewVersionFeed,
        val preferredPage: String,
        val altPage: String?,
        val signer: PreviewSignerKind,
    ) : PreviewUpdateResult()
    data class Error(val message: String) : PreviewUpdateResult()
}
