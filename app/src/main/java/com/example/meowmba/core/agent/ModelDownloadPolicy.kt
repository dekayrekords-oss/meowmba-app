package com.example.meowmba.core.agent

import com.example.meowmba.core.security.CleartextPolicy
import java.net.URI

/** Credentials belong to the Hub origin, never a custom URL or signed file CDN. */
object ModelDownloadPolicy {
    fun tokenForUrl(url: String, token: String?): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || !uri.host.equals("huggingface.co", true) ||
            uri.port !in setOf(-1, 443) || uri.rawUserInfo != null) return null
        return token?.trim()?.takeIf { value -> value.isNotBlank() && value.length <= 512 &&
            value.none { it.isWhitespace() || it.code < 32 } }
    }

    fun validateUrl(url: String): String {
        val value = url.trim()
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri != null && uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null) {
            "Use an HTTP or HTTPS model address without embedded credentials."
        }
        require(uri.port == -1 || uri.port in 1..65535) { "The model address has an invalid port." }
        require(CleartextPolicy.isAllowed(value)) { "HTTP model downloads are allowed only on your local network. Use HTTPS for public hosts." }
        return value
    }

    /** Relative and signed CDN redirects work, while each hop is revalidated. */
    fun redirectTarget(currentUrl: String, location: String): String {
        require(location.length <= 16_384 && location.isNotBlank()) { "Invalid model download redirect." }
        val next = runCatching { URI(currentUrl).resolve(location).toString() }.getOrNull()
            ?: throw IllegalArgumentException("Invalid model download redirect.")
        validateUrl(next)
        require(!(URI(currentUrl).scheme == "https" && URI(next).scheme == "http")) {
            "The model server redirected HTTPS to an insecure HTTP address."
        }
        return next
    }

    fun validFileName(name: String): Boolean = name.length in 1..255 && name != "." && name != ".." &&
        name.matches(Regex("[a-zA-Z0-9._-]+"))
}
