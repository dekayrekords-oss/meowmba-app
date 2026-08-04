package com.example.meowmba.core.agent

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads on-device model bundles into the app's PRIVATE storage
 * (`filesDir/models`) — the only location the app can both write to and load
 * from. The historical default `/data/local/tmp/llm/` is adb-only, which is
 * why "no model is installed" could never fix itself.
 *
 * Downloads stream to a `.part` file and atomically rename on success, so a
 * failed attempt never leaves a corrupt "installed" model. The byte engine
 * is injectable for tests.
 */
class ModelDownloader(
    private val modelsDir: File,
    private val hfTokenProvider: () -> String? = { null },
    private val engine: suspend (url: String, dest: File, token: String?, onProgress: (Long, Long?) -> Unit) -> Unit =
        Companion::downloadWithKtor,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {
    data class DownloadProgress(
        val received: Long = 0,
        val total: Long? = null,
        val done: Boolean = false,
        val error: String? = null
    ) {
        val fraction: Float?
            get() = total?.takeIf { it > 0 }?.let { (received.toFloat() / it).coerceIn(0f, 1f) }
    }

    init {
        modelsDir.mkdirs()
    }

    /** The installed file for a catalog option, or null when absent/empty. */
    fun installedFile(option: LocalModelOption): File? =
        File(modelsDir, option.fileName).takeIf { it.isFile && it.length() > 0 }

    /**
     * The installed file for an image-model catalog option, or null when
     * absent/empty. Overload (not a new type) because installed-check and
     * download both key purely on `fileName` — see [ImageModelOption]'s doc
     * for why the catalog entry itself is a separate type.
     */
    fun installedFile(option: ImageModelOption): File? =
        File(modelsDir, option.fileName).takeIf { it.isFile && it.length() > 0 }

    /** Any installed catalog model (used at startup when no path is configured). */
    fun firstInstalledOption(): LocalModelOption? =
        options.firstOrNull { installedFile(it) != null }

    /**
     * Every model bundle actually present in the models dir — catalog or
     * custom — so the UI can show what is really on disk (never guessed).
     */
    fun listInstalled(): List<File> =
        modelsDir.listFiles { f ->
            f.isFile && f.length() > 0 &&
                (f.name.endsWith(".task") || f.name.endsWith(".litertlm"))
        }?.sortedBy { it.name } ?: emptyList()

    private val options get() = ModelCatalog.options

    fun destinationFor(fileName: String): File = File(modelsDir, fileName)

    fun delete(option: LocalModelOption): Boolean =
        File(modelsDir, option.fileName).delete()

    /** Delete an installed image-model catalog entry. Mirrors [delete] for [LocalModelOption]. */
    fun delete(option: ImageModelOption): Boolean =
        File(modelsDir, option.fileName).delete()

    /**
     * Download [url] to [fileName] in the models directory.
     * Throws on HTTP/network errors (caller maps to user-facing state).
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (DownloadProgress) -> Unit
    ): File = withContext(dispatcher) {
        val finalFile = destinationFor(fileName)
        val partFile = File(modelsDir, "$fileName.part")
        if (partFile.exists()) partFile.delete()

        val token = hfTokenProvider()?.takeIf { it.isNotBlank() }
        try {
            engine(url, partFile, token) { received, total ->
                onProgress(DownloadProgress(received, total))
            }
            check(partFile.isFile && partFile.length() > 0) { "Download produced an empty file" }
            if (finalFile.exists()) finalFile.delete()
            check(partFile.renameTo(finalFile)) { "Could not finalize download" }
            onProgress(DownloadProgress(finalFile.length(), finalFile.length(), done = true))
            finalFile
        } catch (e: Exception) {
            partFile.delete()
            throw e
        }
    }

    companion object {
        private val client = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            // Model files are large: no overall request timeout, just
            // a stall guard on the socket.
            requestTimeoutMillis = Long.MAX_VALUE
            socketTimeoutMillis = 120_000
        }
        }

        /** Stream an HTTP(S) URL to [dest] with progress callbacks. Follows HF redirects. */
        suspend fun downloadWithKtor(
            url: String,
            dest: File,
            token: String?,
            onProgress: (Long, Long?) -> Unit
        ) {
            client.prepareGet(url) {
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val hint = if (response.status.value == 401 || response.status.value == 403) {
                        " (license-gated: add your Hugging Face token in Settings)"
                    } else ""
                    throw IllegalStateException("HTTP ${response.status.value}$hint")
                }
                val total = response.contentLength()
                val channel: ByteReadChannel = response.bodyAsChannel()
                dest.outputStream().buffered().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            received += read
                            onProgress(received, total)
                        }
                    }
                }
            }
        }
    }
}
