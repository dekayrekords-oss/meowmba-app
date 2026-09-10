package com.example.meowmba.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.meowmba.MainActivity
import com.example.meowmba.R
import com.example.meowmba.core.agent.ModelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadService : Service() {

    companion object {
        data class Status(val fileName: String = "", val active: Boolean = false, val received: Long = 0, val total: Long? = null, val error: String? = null)
        private val mutableStatus = kotlinx.coroutines.flow.MutableStateFlow(Status())
        val status: kotlinx.coroutines.flow.StateFlow<Status> = mutableStatus
        const val CHANNEL_ID = "meowmba_download"
        const val NOTIFICATION_ID = 100
        const val ACTION_DOWNLOAD = "com.example.meowmba.DOWNLOAD_MODEL"
        const val EXTRA_URL = "url"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_MODELS_DIR = "models_dir"

        /** Token stays in-process — never in the Intent extras dumpsys can read. */
        @Volatile
        var pendingHfToken: String? = null

        @Synchronized fun start(
            context: Context,
            url: String,
            fileName: String,
            fileId: String,
            hfToken: String?,
            modelsDir: File
        ) {
            if (mutableStatus.value.active) {
                check(mutableStatus.value.fileName == fileName) { "Another model is downloading. Let it finish, then retry." }
                return
            }
            pendingHfToken = hfToken
            // Publish synchronously; two taps before onStartCommand must not
            // queue duplicate downloads or overwrite another request's token.
            mutableStatus.value = Status(fileName, active = true)
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_FILE_ID, fileId)
                putExtra(EXTRA_MODELS_DIR, modelsDir.absolutePath)
            }
            try { ContextCompat.startForegroundService(context, intent) }
            catch (e: Exception) {
                pendingHfToken = null
                mutableStatus.value = Status(fileName, error = e.message ?: "Download could not start")
                throw e
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // One writer per service: a second request must not race or cancel a
        // model that is already downloading into the same .part file.
        if (job?.isActive == true) return START_NOT_STICKY
        if (intent?.action != ACTION_DOWNLOAD) {
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent.getStringExtra(EXTRA_URL) ?: run { stopSelf(); return START_NOT_STICKY }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: run { stopSelf(); return START_NOT_STICKY }
        val fileId = intent.getStringExtra(EXTRA_FILE_ID) ?: run { stopSelf(); return START_NOT_STICKY }
        val hfToken = pendingHfToken.also { pendingHfToken = null }
        val modelsDir = intent.getStringExtra(EXTRA_MODELS_DIR)?.let { File(it) }
            ?: run { stopSelf(); return START_NOT_STICKY }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Starting download…", 0f),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        job = scope.launch {
            try {
                modelsDir.mkdirs()
                mutableStatus.value = Status(fileName, active = true)
                val dest = File(modelsDir, fileName)
                val partFile = File(modelsDir, "$fileName.part")
                partFile.delete()

                var lastProgress = 0L
                ModelDownloader.downloadWithKtor(url, partFile, hfToken) { received, total ->
                    val fraction = if (total != null && total > 0) {
                        (received.toFloat() / total).coerceIn(0f, 1f)
                    } else 0f
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastProgress >= 500 || received == total) {
                        lastProgress = now
                        mutableStatus.value = Status(fileName, true, received, total)
                        updateNotification("Downloading $fileName", fraction)
                    }
                }

                check(partFile.length() > 0) { "Download produced an empty file; please retry" }
                if (dest.exists()) dest.delete()
                check(partFile.renameTo(dest)) { "Could not finalize download" }
                com.example.meowmba.core.security.SecureStorageManager(this@DownloadService)
                    .savePreference("model_downloaded_at:$fileName", System.currentTimeMillis().toString())
                mutableStatus.value = Status(fileName, received = dest.length(), total = dest.length())

                withContext(Dispatchers.Main) {
                    val notification = buildNotification("$fileName downloaded", 1f)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                mutableStatus.value = Status(fileName, error = e.message ?: "Download interrupted; retry when online")
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    val notification = buildNotification("Download failed: ${e.message?.take(60) ?: "error"}", -1f)
                        .let { NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("Download failed")
                            .setContentText(e.message?.take(120) ?: "Error")
                            .setAutoCancel(true)
                            .setContentIntent(openAppIntent())
                            .build()
                        }
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification)
                }
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // A cleanup failure must not kill the process.
        //
        // Cancelling a Ktor request while its response body is still open makes
        // the engine close the underlying stream from a coroutine completion
        // handler (io.ktor.client.engine.UtilsKt.attachToUserJob). The Ktor
        // Android engine is HttpURLConnection-backed, so that close lands in the
        // platform's com.android.okhttp, which on some Samsung builds throws
        // IllegalStateException("Unbalanced enter/exit") from an R8-outlined
        // synthetic (com.android.okhttp.Cache$2).
        //
        // kotlinx.coroutines wraps anything a completion handler throws in
        // CompletionHandlerException and rethrows it on the thread that called
        // cancel(). This runs on main, so an uncaught handler throwable escapes
        // to the Looper and kills the app — observed ~3 min apart on an
        // AOT-compiled build (pids 525 and 2089, 2026-09-10).
        //
        // Only that one platform failure is swallowed; anything else still
        // propagates, so this cannot mask a real bug in our own code. The root
        // cause is in platform okhttp, not here; the real fix is moving the
        // model download off the HttpURLConnection-backed Ktor engine.
        try {
            job?.cancel()
            scope.cancel()
        } catch (t: Throwable) {
            if (isPlatformOkHttpCleanupFailure(t)) {
                android.util.Log.w(
                    "DownloadService",
                    "Swallowed platform okhttp cleanup failure while cancelling a download",
                    t,
                )
            } else {
                throw t
            }
        }
        super.onDestroy()
    }

    /**
     * True when [t], or anything it wraps, is the platform okhttp
     * `Unbalanced enter/exit` failure. Matched on the message rather than the
     * wrapping type because kotlinx.coroutines' `CompletionHandlerException` is
     * an internal API we must not reference.
     */
    private fun isPlatformOkHttpCleanupFailure(t: Throwable): Boolean {
        var cursor: Throwable? = t
        var hops = 0
        while (cursor != null && hops++ < 16) {
            if (cursor is IllegalStateException && cursor.message == "Unbalanced enter/exit") return true
            val next = cursor.cause
            cursor = if (next === cursor) null else next
        }
        return false
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String, progress: Float): Notification {
        val max = if (progress > 0) 100 else 0
        val cur = if (progress > 0) (progress * 100).toInt() else 0
        val indeterminate = progress <= 0f

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Model download")
            .setContentText(text)
            .setOngoing(progress < 1f)
            .setProgress(max, cur, indeterminate)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun updateNotification(text: String, progress: Float) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text, progress))
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
