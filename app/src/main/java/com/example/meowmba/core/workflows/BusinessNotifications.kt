package com.example.meowmba.core.workflows

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.meowmba.MainActivity

object BusinessNotifications {
    const val OPEN_TEAM = "open_business_agent_team"
    fun post(context: Context, run: BusinessRun) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = when (run.status) {
            "APPROVAL" -> "Business agent needs your approval"
            "COMPLETE" -> "Business job finished — review results"
            "BLOCKED" -> "Business job needs attention"
            else -> return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("business_jobs", "Business agent jobs", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).putExtra(OPEN_TEAM, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, run.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "business_jobs")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title)
            .setContentText("Open your agent team to inspect the job and its evidence.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(run.id.hashCode(), notification)
    }
}
