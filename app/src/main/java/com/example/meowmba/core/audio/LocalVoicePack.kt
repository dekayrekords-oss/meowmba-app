package com.example.meowmba.core.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class LocalVoice(val id: String, val speaker: Int, val name: String, val description: String, val gender: String)

/** Speaker numbers match the official Kokoro multi-lang v1.0 pack, not v1.1. */
object LocalVoices {
    val all = listOf(
        LocalVoice("af_heart", 3, "Heart", "Warm, conversational · American English", "Female"),
        LocalVoice("af_bella", 2, "Bella", "Clear, expressive · American English", "Female"),
        LocalVoice("am_fenrir", 14, "Fenrir", "Full, steady · American English", "Male"),
        LocalVoice("am_michael", 16, "Michael", "Relaxed, grounded · American English", "Male"),
    )
    fun find(id: String?) = all.firstOrNull { it.id == id } ?: all.first()
    private fun prefs(context: Context) = context.getSharedPreferences("local_neural_voice", Context.MODE_PRIVATE)
    fun selected(context: Context) = find(prefs(context).getString("voice", null))
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun select(context: Context, id: String) {
        require(all.any { it.id == id })
        prefs(context).edit().putString("voice", id).putBoolean("enabled", true).apply()
    }
    fun useSystem(context: Context) { prefs(context).edit().putBoolean("enabled", false).apply() }
}

/** Download-only network access; inference and playback never use a service. */
class LocalVoicePack(private val context: Context) {
    val directory = File(context.filesDir, "voice_models/$NAME")
    fun ready(): Boolean = runCatching { File(directory, ".verified").let { it.isFile && it.readText() == SHA256 } &&
        File(directory, "model.onnx").length() > 300_000_000 &&
        File(directory, "voices.bin").length() > 20_000_000 &&
        File(directory, "tokens.txt").length() > 100 && File(directory, "espeak-ng-data").isDirectory }.getOrDefault(false)

    suspend fun install(progress: (Float, String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        installMutex.withLock {
            try {
                if (ready()) return@withLock Result.success(Unit)
                check(context.filesDir.usableSpace > 1_200_000_000) { "Free at least 1.2 GB to install the local voice pack." }
                val archive = File(context.cacheDir, "$NAME.download")
                val connection = URL(URL_STRING).openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                try {
                    check(connection.responseCode == 200) { "Voice download returned HTTP ${connection.responseCode}" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    connection.inputStream.use { input -> archive.outputStream().use { output ->
                        val bytes = ByteArray(65536)
                        var total = 0L
                        var lastPercent = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(bytes)
                            if (count < 0) break
                            total += count
                            check(total <= ARCHIVE_BYTES) { "Unexpected voice download size" }
                            digest.update(bytes, 0, count)
                            output.write(bytes, 0, count)
                            val fraction = total.toFloat() / ARCHIVE_BYTES * 0.8f
                            val percent = (fraction * 100).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                progress(fraction, "Downloading local voices…")
                            }
                        }
                        check(total == ARCHIVE_BYTES) { "Voice download was incomplete" }
                    } }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == SHA256) { "Voice download failed checksum verification" }
                } finally { connection.disconnect() }
                progress(0.85f, "Unpacking verified local voices…")
                val staging = File(context.cacheDir, "voice-stage-${java.util.UUID.randomUUID()}").apply { mkdirs() }
                try {
                    archive.inputStream().buffered().use { raw -> BZip2CompressorInputStream(raw).use { bz ->
                        TarArchiveInputStream(bz).use { tar ->
                            var total = 0L
                            var count = 0
                            while (true) {
                                coroutineContext.ensureActive()
                                val entry = tar.nextEntry ?: break
                                check(++count <= 10000 && entry.name.startsWith("$NAME/")) { "Unexpected voice archive entry" }
                                val target = safeEntry(staging, entry.name)
                                if (entry.isDirectory) { target.mkdirs(); continue }
                                check(entry.isFile && !entry.isSymbolicLink && !entry.isLink) { "Voice archive links are not allowed" }
                                total += entry.size
                                check(entry.size >= 0 && total <= 700_000_000) { "Voice archive is too large" }
                                target.parentFile!!.mkdirs()
                                target.outputStream().use { tar.copyTo(it) }
                                check(target.length() == entry.size) { "Incomplete archive entry" }
                            }
                        }
                    } }
                    val unpacked = File(staging, NAME)
                    check(File(unpacked, "model.onnx").length() > 300_000_000 && File(unpacked, "voices.bin").length() > 20_000_000) { "Voice pack is incomplete" }
                    File(unpacked, ".verified").writeText(SHA256)
                    directory.parentFile!!.mkdirs()
                    // Only this app-owned, invalid voice pack can be replaced.
                    if (directory.exists()) check(directory.deleteRecursively()) { "Could not replace incomplete voice pack" }
                    check(unpacked.renameTo(directory)) { "Could not install voice pack" }
                    check(ready()) { "Installed voices did not validate" }
                } finally { staging.deleteRecursively(); archive.delete() }
                progress(1f, "Four local voices ready")
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
        }
    }

    companion object {
        const val NAME = "kokoro-multi-lang-v1_0"
        const val URL_STRING = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$NAME.tar.bz2"
        const val SHA256 = "c133d26353d776da730870dac7da07dbfc9a5e3bc80cc5e8e83ab6e823be7046"
        const val ARCHIVE_BYTES = 349418188L
        private val installMutex = Mutex()
        internal fun safeEntry(root: File, name: String): File {
            require(!name.startsWith("/") && name.split('/').none { it == ".." }) { "Unsafe archive path" }
            return File(root, name).canonicalFile.also { require(it.path.startsWith(root.canonicalPath + File.separator)) }
        }
    }
}
