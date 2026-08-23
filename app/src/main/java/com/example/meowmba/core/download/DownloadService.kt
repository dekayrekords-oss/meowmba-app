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

        fun start(
            context: Context,
            url: String,
            fileName: String,
            fileId: String,
            hfToken: String?,
            modelsDir: File
        ) {
            pendingHfToken = hfToken
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_FILE_ID, fileId)
                putExtra(EXTRA_MODELS_DIR, modelsDir.absolutePath)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                val dest = File(modelsDir, fileName)
                val partFile = File(modelsDir, "$fileName.part")
                partFile.delete()

                ModelDownloader.downloadWithKtor(url, partFile, hfToken) { received, total ->
                    val fraction = if (total != null && total > 0) {
                        (received.toFloat() / total).coerceIn(0f, 1f)
                    } else 0f
                    updateNotification("Downloading $fileName", fraction)
                }

                if (dest.exists()) dest.delete()
                check(partFile.renameTo(dest)) { "Could not finalize download" }

                withContext(Dispatchers.Main) {
                    val notification = buildNotification("$fileName downloaded", 1f)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
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
        job?.cancel()
        scope.cancel()
        super.onDestroy()
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
