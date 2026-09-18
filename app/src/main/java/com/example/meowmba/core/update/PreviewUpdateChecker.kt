package com.example.meowmba.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.meowmba.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Fetches apk-serve `preview-version.json` and compares to [BuildConfig.VERSION_CODE].
 *
 * Tries the configured NetBird base first; optionally falls back to LAN.
 * Prefers get-preview-alt when the installed APK is Claude-signed.
 */
class PreviewUpdateChecker(
    private val context: Context,
    private val store: PreviewUpdateStore = PreviewUpdateStore(context),
) {
    fun check(force: Boolean = false, nowMs: Long = System.currentTimeMillis()): PreviewUpdateResult {
        if (!force &&
            store.lastCheckEpochMs > 0L &&
            nowMs - store.lastCheckEpochMs < PreviewUpdateStore.MIN_AUTO_CHECK_INTERVAL_MS
        ) {
            return PreviewUpdateResult.UpToDate(BuildConfig.VERSION_CODE, null)
        }
        val bases = linkedSetOf(store.baseUrl)
        if (store.useLanFallback) bases += store.lanFallbackUrl
        var lastError: String? = null
        for (base in bases) {
            when (val fetched = fetchFeed(store.feedUrl(base))) {
                is FetchOk -> {
                    store.lastCheckEpochMs = nowMs
                    return compare(fetched.feed, installedSigner())
                }
                is FetchErr -> lastError = fetched.message
            }
        }
        return PreviewUpdateResult.Error(lastError ?: "Could not reach preview version feed")
    }

    fun compare(feed: PreviewVersionFeed, signer: PreviewSignerKind = installedSigner()): PreviewUpdateResult {
        val installed = BuildConfig.VERSION_CODE
        if (feed.versionCode <= installed) {
            return PreviewUpdateResult.UpToDate(installed, feed)
        }
        val preferAlt = signer == PreviewSignerKind.CLAUDE
        val preferred = if (preferAlt) "get-preview-alt.html" else "get-preview.html"
        val alt = if (preferAlt) "get-preview.html" else "get-preview-alt.html"
        return PreviewUpdateResult.Available(
            installedCode = installed,
            feed = feed,
            preferredPage = preferred,
            altPage = alt,
            signer = signer,
        )
    }

    fun installedSigner(): PreviewSignerKind {
        val prefix = signingCertSha256Prefix(context) ?: return PreviewSignerKind.UNKNOWN
        return when {
            prefix.startsWith(PROG_CERT_PREFIX, ignoreCase = true) -> PreviewSignerKind.PROG
            prefix.startsWith(CLAUDE_CERT_PREFIX, ignoreCase = true) -> PreviewSignerKind.CLAUDE
            else -> PreviewSignerKind.UNKNOWN
        }
    }

    private sealed class FetchOutcome
    private data class FetchOk(val feed: PreviewVersionFeed) : FetchOutcome()
    private data class FetchErr(val message: String) : FetchOutcome()

    private fun fetchFeed(url: String): FetchOutcome {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                return FetchErr("HTTP $code from version feed")
            }
            FetchOk(PreviewVersionFeed.parse(text))
        } catch (e: Exception) {
            FetchErr(e.message ?: e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        /** First 8 hex chars of SHA-256 of the prog debug signing cert. */
        const val PROG_CERT_PREFIX = "b7850d5f"
        /** First 8 hex chars of SHA-256 of the Claude / Fold debug signing cert. */
        const val CLAUDE_CERT_PREFIX = "75b19d5a"

        fun signingCertSha256Prefix(context: Context): String? {
            return try {
                val pm = context.packageManager
                val pkg = context.packageName
                val bytes: ByteArray? = if (Build.VERSION.SDK_INT >= 28) {
                    val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                    val si = info.signingInfo ?: return null
                    val signers = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                    signers?.firstOrNull()?.toByteArray()
                } else {
                    @Suppress("DEPRECATION")
                    val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                    @Suppress("DEPRECATION")
                    info.signatures?.firstOrNull()?.toByteArray()
                }
                if (bytes == null) return null
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                digest.joinToString("") { b -> "%02x".format(b) }.take(8)
            } catch (_: Exception) {
                null
            }
        }
    }
}
