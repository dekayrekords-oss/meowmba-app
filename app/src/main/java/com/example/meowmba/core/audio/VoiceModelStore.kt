package com.example.meowmba.core.audio

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Voice models live in [filesDir]/voice_models, not the APK.
 *
 * Debug builds still bundle them as assets; the first launch copies them
 * across so loaders have one filesystem path. Play builds download from
 * the same GitHub URLs as scripts/fetch_kws_assets.sh.
 */
class VoiceModelStore(private val context: Context) {

    val root: File = File(context.filesDir, "voice_models")
    val kwsDir: File = File(root, VoiceModelPack.KWS_DIR)
    val asrDir: File = File(root, VoiceModelPack.ASR_DIR)
    val whisperDir: File = File(root, VoiceModelPack.WHISPER_DIR)

    fun kwsReady(): Boolean = VoiceModelPack.kwsRequired.all { name ->
        File(kwsDir, name).let { it.isFile && it.length() > 0L }
    }

    fun speakerReady(): Boolean =
        File(root, VoiceModelPack.SPEAKER).let { it.isFile && it.length() > 0L }

    fun vadReady(): Boolean =
        File(root, VoiceModelPack.VAD).let { it.isFile && it.length() > 0L }

    fun asrReady(): Boolean = resolvedAsr() != null

    /** Local Whisper (offline batch transcriber). See [VoiceModelPack.WHISPER_DIR]. */
    fun whisperReady(): Boolean = VoiceModelPack.whisperComplete(whisperDir)

    /**
     * Prefer the 2023-06-26 pack only when every file meets the published
     * size floor. A truncated unpack still has length > 0, which used to
     * count as ready and then native-aborted onnxruntime. The 20M pack on
     * disk is the safe fallback.
     */
    fun resolvedAsr(): VoiceModelPack.AsrPack? = when {
        VoiceModelPack.ASR_PRIMARY.isComplete(asrDir) -> VoiceModelPack.ASR_PRIMARY
        VoiceModelPack.ASR_FALLBACK.isComplete(File(root, VoiceModelPack.ASR_FALLBACK.dir)) ->
            VoiceModelPack.ASR_FALLBACK
        else -> null
    }

    fun isReady(): Boolean = kwsReady()

    suspend fun ensure(onProgress: (Float, String) -> Unit = { _, _ -> }): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (isReady()) return@withContext Result.success(Unit)
            runCatching {
                root.mkdirs()
                kwsDir.mkdirs()
                if (copyFromAssetsIfBundled()) {
                    writeKeywords()
                    onProgress(1f, "Voice models ready")
                    return@runCatching
                }
                onProgress(0.02f, "Downloading wake-word model…")
                downloadKws(onProgress)
                writeKeywords()
                onProgress(0.72f, "Downloading voice print…")
                downloadTo(VoiceModelPack.SPEAKER_URL, File(root, VoiceModelPack.SPEAKER))
                onProgress(0.90f, "Downloading VAD…")
                downloadTo(VoiceModelPack.VAD_URL, File(root, VoiceModelPack.VAD))
                if (!kwsReady()) error("Voice models did not unpack correctly")
                copyAsrFromAssets()
                onProgress(1f, "Voice models ready")
            }
        }

    /** Face-mode always-on STT. Separate from KWS so wake still arms if ASR is missing. */
    suspend fun ensureAsr(onProgress: (Float, String) -> Unit = { _, _ -> }): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (asrReady()) return@withContext Result.success(Unit)
            runCatching {
                root.mkdirs()
                asrDir.mkdirs()
                onProgress(0.05f, "Getting always-on speech…")
                ensureAsrFiles(onProgress)
                if (!asrReady()) error("Always-on speech model did not unpack correctly")
                onProgress(1f, "Always-on speech ready")
            }
        }

    /** Install or repair the offline Whisper final-transcript model. */
    suspend fun ensureWhisper(onProgress: (Float, String) -> Unit = { _, _ -> }): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (whisperReady()) return@withContext Result.success(Unit)
            runCatching {
                whisperDir.mkdirs()
                val files = VoiceModelPack.whisperDownloads.entries.toList()
                files.forEachIndexed { index, (name, url) ->
                    val target = File(whisperDir, name)
                    val floor = VoiceModelPack.WHISPER_MIN_BYTES[name] ?: 1L
                    if (target.isFile && target.length() >= floor) return@forEachIndexed
                    target.delete()
                    val start = index.toFloat() / files.size
                    val span = 1f / files.size
                    downloadTo(url, target) { received, total ->
                        val part = total?.takeIf { it > 0 }?.let { received.toFloat() / it } ?: 0f
                        onProgress(
                            (start + span * part).coerceIn(0f, 0.99f),
                            "Downloading Whisper ${index + 1}/${files.size}…",
                        )
                    }
                    check(target.length() >= floor) { "$name download was incomplete" }
                }
                check(whisperReady()) { "Whisper files did not install correctly" }
                onProgress(1f, "Whisper ready")
            }
        }

    private suspend fun ensureAsrFiles(onProgress: (Float, String) -> Unit) {
        if (asrReady()) return
        if (copyAsrFromAssets()) return
        val tar = File(context.cacheDir, "asr.tar.bz2")
        // The 300+MB archive takes minutes to download and the unpack even
        // longer on-device; a service crash mid-flight used to throw away a
        // complete archive and re-download from byte 0 on the next launch.
        // Keep a COMPLETE archive in cache until the files verifiably exist,
        // and stage downloads under .part so a half-fetched tar is never
        // mistaken for a reusable one.
        if (!tar.isFile || tar.length() == 0L) {
            val part = File(context.cacheDir, "asr.tar.bz2.part")
            downloadTo(VoiceModelPack.ASR_TAR_URL, part) { rec, total ->
                val frac = total?.takeIf { it > 0 }?.let { rec.toFloat() / it } ?: 0f
                onProgress(0.10f + 0.75f * frac.coerceIn(0f, 1f), "Downloading always-on speech…")
            }
            check(part.renameTo(tar)) { "could not stage downloaded ASR archive" }
            Log.i(TAG, "saved asr.tar.bz2 (${tar.length()} bytes)")
        } else {
            Log.i(TAG, "reusing cached asr.tar.bz2 (${tar.length()} bytes)")
        }
        onProgress(0.88f, "Unpacking always-on speech…")
        extractAsrTar(tar, asrDir)
        if (VoiceModelPack.ASR_PRIMARY.isComplete(asrDir)) {
            // Reclaim orphans from superseded pack variants (e.g. the fp32
            // encoder this pack also ships) — they only waste 100s of MB,
            // and loading them native-aborts the process.
            val keep = VoiceModelPack.asrRequired.toSet()
            asrDir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
            tar.delete()
        } else {
            Log.w(
                TAG,
                "primary ASR unpack incomplete; keeping archive for retry " +
                    "(encoder=${File(asrDir, VoiceModelPack.ASR_ENCODER).length()} " +
                    "decoder=${File(asrDir, VoiceModelPack.ASR_DECODER).length()})",
            )
        }
    }

    private fun copyAsrFromAssets(): Boolean {
        val ok = VoiceModelPack.asrRequired.all { name ->
            copyAsset("${VoiceModelPack.ASR_DIR}/$name", File(asrDir, name))
        }
        return ok && asrReady()
    }

    private fun writeKeywords() {
        File(kwsDir, VoiceModelPack.KEYWORDS).writeText(VoiceModelPack.keywordsText)
    }

    private fun copyFromAssetsIfBundled(): Boolean {
        val am = context.assets
        val kwsOk = VoiceModelPack.kwsRequired.filter { it != VoiceModelPack.KEYWORDS }.all { name ->
            copyAsset("${VoiceModelPack.KWS_DIR}/$name", File(kwsDir, name))
        }
        if (!kwsOk) return false
        copyAsset(VoiceModelPack.SPEAKER, File(root, VoiceModelPack.SPEAKER))
        copyAsset(VoiceModelPack.VAD, File(root, VoiceModelPack.VAD))
        copyAsrFromAssets()
        return true
    }

    private fun copyAsset(assetPath: String, dest: File): Boolean = try {
        amOpen(assetPath).use { input ->
            dest.parentFile?.mkdirs()
            dest.outputStream().use { input.copyTo(it) }
        }
        dest.isFile && dest.length() > 0L
    } catch (_: Exception) {
        false
    }

    private fun amOpen(path: String) = context.assets.open(path)

    private suspend fun downloadKws(onProgress: (Float, String) -> Unit) {
        val tar = File(context.cacheDir, "kws.tar.bz2")
        downloadTo(VoiceModelPack.KWS_TAR_URL, tar) { rec, total ->
            val frac = total?.takeIf { it > 0 }?.let { rec.toFloat() / it } ?: 0f
            onProgress(0.05f + 0.50f * frac.coerceIn(0f, 1f), "Downloading wake-word model…")
        }
        onProgress(0.58f, "Unpacking wake-word model…")
        extractKwsTar(tar, kwsDir)
        tar.delete()
    }

    companion object {
        private const val TAG = "VoiceModelStore"
        private val client = HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10 * 60_000L
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = 10 * 60_000L
            }
        }

        internal fun extractAsrTar(tarBz: File, destDir: File) {
            destDir.mkdirs()
            val want = VoiceModelPack.asrRequired.toSet()
            FileInputStream(tarBz).use { raw ->
                BZip2CompressorInputStream(raw).use { bz ->
                    TarArchiveInputStream(bz).use { tar ->
                        var entry = tar.nextEntry
                        while (entry != null) {
                            val name = File(entry.name).name
                            if (!entry.isDirectory && name in want) {
                                val dest = File(destDir, name)
                                // Resume support: a prior run may have been
                                // killed mid-unpack (service foreground
                                // deadline, swipe-away). Entries already on
                                // disk with the tar's own size are kept —
                                // bzip2 is far too slow to redo for fun.
                                if (!dest.isFile || dest.length() != entry.size) {
                                    dest.outputStream().use { tar.copyTo(it) }
                                }
                            }
                            entry = tar.nextEntry
                        }
                    }
                }
            }
        }

        internal fun extractKwsTar(tarBz: File, destDir: File) {
            destDir.mkdirs()
            val want = VoiceModelPack.kwsRequired.filter { it != VoiceModelPack.KEYWORDS }.toSet()
            FileInputStream(tarBz).use { raw ->
                BZip2CompressorInputStream(raw).use { bz ->
                    TarArchiveInputStream(bz).use { tar ->
                        var entry = tar.nextEntry
                        while (entry != null) {
                            val name = File(entry.name).name
                            if (!entry.isDirectory && name in want) {
                                File(destDir, name).outputStream().use { tar.copyTo(it) }
                            }
                            entry = tar.nextEntry
                        }
                    }
                }
            }
        }

        private suspend fun downloadTo(
            url: String,
            dest: File,
            onBytes: (Long, Long?) -> Unit = { _, _ -> },
        ) {
            dest.parentFile?.mkdirs()
            val part = File(dest.path + ".part")
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) {
                    error("Download failed ${response.status} for $url")
                }
                val total = response.headers["Content-Length"]?.toLongOrNull()
                val ch = response.bodyAsChannel()
                var rec = 0L
                part.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (!ch.isClosedForRead) {
                        val n = ch.readAvailable(buf, 0, buf.size)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        rec += n
                        onBytes(rec, total)
                    }
                }
            }
            if (!part.renameTo(dest)) {
                dest.delete()
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            Log.i(TAG, "saved ${dest.name} (${dest.length()} bytes)")
        }
    }
}
