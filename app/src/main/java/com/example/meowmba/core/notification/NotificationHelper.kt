package com.example.meowmba.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.meowmba.MainActivity
import com.example.meowmba.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "meowmba_companion"
        const val CHANNEL_NAME = "Companion"
        const val NOTIFICATION_ID_THOUGHT = 1
        private const val TAG = "NotificationHelper"
    }

    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        runCatching {
            val nm = notificationManager ?: return@runCatching
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications from your Meowmba companion"
            }
            nm.createNotificationChannel(channel)
        }.onFailure { Log.w(TAG, "createNotificationChannel failed", it) }
    }

    fun postThought(text: String) {
        runCatching {
            val nm = notificationManager ?: return@runCatching
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Meowmba")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            nm.notify(NOTIFICATION_ID_THOUGHT, notification)
        }.onFailure { Log.w(TAG, "postThought failed", it) }
    }
}
