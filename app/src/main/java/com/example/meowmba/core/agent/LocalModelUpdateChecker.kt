package com.example.meowmba.core.agent

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Explicit, read-only integrity check against the runtime-compatible catalog.
 * Never guesses that equal file sizes mean identical weights. Imported models
 * without an authoritative catalog source remain unverified, not "up to date".
 */
object LocalModelUpdateChecker {
    data class Status(val message: String, val replacement: LocalModelOption? = null)

    suspend fun check(file: File, token: String?): Status = withContext(Dispatchers.IO) {
        if (file.name == "needle2.cact") return@withContext Status(
            "Needle 3 replaces this tool router. Your old file will be kept.",
            ModelCatalog.options.firstOrNull { it.id == "needle-3" })
        val option = ModelCatalog.optionForFile(file.name)
            ?: return@withContext Status("Custom model: no verified update source. Choose a newer compatible model manually.")
        val uri = URI(option.downloadUrl)
        require(uri.scheme == "https" && uri.host == "huggingface.co")
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            token?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            val code = connection.responseCode
            if (code !in 200..399) return@withContext Status("Could not check this model (HTTP $code). Existing file unchanged.")
            val digest = (connection.getHeaderField("X-Linked-Etag") ?: connection.getHeaderField("ETag"))
                ?.trim('"')?.lowercase()
            if (digest == null || !digest.matches(Regex("[0-9a-f]{64}")))
                return@withContext Status("The source did not provide a weight checksum; update status is unknown.")
            val local = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    local.update(buffer, 0, count)
                }
            }
            val actual = local.digest().joinToString("") { "%02x".format(it) }
            if (actual == digest) Status("Verified: matches the current compatible catalog weights.")
            else Status("Different or incomplete weights. Download a verified-source replacement and keep the old file.",
                option.copy(id = option.id + "-" + digest.take(12),
                    fileName = option.fileName.substringBeforeLast('.') + "--" + digest.take(12) + "." + option.fileName.substringAfterLast('.')))
        } finally { connection.disconnect() }
    }
}
