package com.example.meowmba.core.flow.laya

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches Laya's six files from the Hub into [LayaFiles.dir]: on an unmetered network only,
 * resumable (HTTP Range into `.part`), each file checked against its published SHA-256
 * before it is moved into place. Started only by the user (Flow › Adjust › Thinking).
 */
class LayaDownloader(private val context: Context, private val files: LayaFiles, private val scope: CoroutineScope) {

    sealed interface Status {
        data object Missing : Status
        data object NeedsWifi : Status
        data class Downloading(val bytes: Long, val total: Long) : Status
        data object Verifying : Status
        data object Ready : Status
        data class Failed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(if (files.present()) Status.Ready else Status.Missing)
    val status: StateFlow<Status> = _status
    private var job: Job? = null

    fun refresh() { if (job?.isActive != true) _status.value = if (files.present()) Status.Ready else Status.Missing }

    fun start() {
        if (job?.isActive == true || files.present()) { refresh(); return }
        if (!unmetered()) { _status.value = Status.NeedsWifi; return }
        job = scope.launch(Dispatchers.IO) {
            runCatching {
                files.dir.mkdirs()
                val total = LayaFiles.TOTAL_BYTES
                var doneBefore = 0L
                for (e in LayaFiles.MANIFEST) {
                    val dest = File(files.dir, e.name)
                    if (dest.isFile && dest.length() == e.size) { doneBefore += e.size; continue }
                    val part = File(files.dir, e.name + ".part")
                    fetch(LayaFiles.SOURCE + e.name, part, e.size) { got -> _status.value = Status.Downloading(doneBefore + got, total) }
                    ensureActive()
                    _status.value = Status.Verifying
                    check(sha256(part) == e.sha256) { part.delete(); "${e.name} did not match its published checksum" }
                    check(part.renameTo(dest)) { "could not move ${e.name} into place" }
                    doneBefore += e.size
                }
                _status.value = Status.Ready
            }.onFailure { _status.value = Status.Failed(it.message ?: "download failed") }
        }
    }

    fun cancel() { job?.cancel(); refresh() }

    private suspend fun fetch(url: String, part: File, size: Long, progress: (Long) -> Unit) {
        var have = if (part.isFile) part.length().coerceAtMost(size) else 0L
        if (have == size) return
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) have = 0L // server ignored Range: start over
            else check(code == HttpURLConnection.HTTP_PARTIAL) { "server answered $code" }
            RandomAccessFile(part, "rw").use { out ->
                out.setLength(have); out.seek(have)
                conn.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    var lastReport = 0L
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (!unmetered()) error("Wi-Fi was lost; the download will resume from here")
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); have += n
                        if (have - lastReport > 1 shl 20) { progress(have); lastReport = have }
                    }
                }
            }
            progress(have)
            check(have == size) { "incomplete download (${have / 1_000_000} of ${size / 1_000_000} MB)" }
        } finally { conn.disconnect() }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input -> val buf = ByteArray(1 shl 16); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
