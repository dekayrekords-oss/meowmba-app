package com.example.meowmba.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.example.meowmba.MainActivity
import com.example.meowmba.R

/**
 * Posts a single update notification per [versionCode] (deduped via [PreviewUpdateStore]).
 */
object PreviewUpdateNotifier {
    const val CHANNEL_ID = "meowmba_preview_update"
    const val CHANNEL_NAME = "Preview updates"
    const val NOTIFICATION_ID = 1310
    const val EXTRA_OPEN_UPDATE = "com.example.meowmba.OPEN_PREVIEW_UPDATE"
    const val EXTRA_OPEN_SETTINGS_PAGE = "com.example.meowmba.OPEN_SETTINGS_PAGE"

    fun ensureChannel(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@runCatching
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "When a newer Meowmba Preview build is on apk-serve"
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * @return true if a notification was posted (false if deduped or skipped).
     */
    fun notifyIfNew(
        context: Context,
        available: PreviewUpdateResult.Available,
        store: PreviewUpdateStore = PreviewUpdateStore(context),
        openInBrowser: Boolean = false,
    ): Boolean {
        val vc = available.feed.versionCode
        if (vc <= store.lastNotifiedVersionCode) return false
        ensureChannel(context)
        return runCatching {
            val app = context.applicationContext
            val base = store.baseUrl
            val pageUrl = store.getPreviewUrl(base, alt = available.preferredPage.contains("alt"))
            val contentIntent = if (openInBrowser) {
                PendingIntent.getActivity(
                    app,
                    vc,
                    Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getActivity(
                    app,
                    vc,
                    Intent(app, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_OPEN_UPDATE, true)
                        putExtra(EXTRA_OPEN_SETTINGS_PAGE, "Help & feedback")
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val notes = available.feed.notes?.take(120)
            val text = buildString {
                append("Preview ${available.feed.versionName} (vc$vc) is ready.")
                if (!notes.isNullOrBlank()) append(' ').append(notes)
            }
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Preview vc$vc available")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@runCatching false
            nm.notify(NOTIFICATION_ID, notification)
            store.lastNotifiedVersionCode = vc
            true
        }.getOrDefault(false)
    }
}
