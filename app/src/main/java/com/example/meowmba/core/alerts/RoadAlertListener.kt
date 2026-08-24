package com.example.meowmba.core.alerts

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads Waze / Google Maps notifications for police and speed-camera reports.
 * Off until the user enables notification access in system settings.
 */
class RoadAlertListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString()
        val alert = RoadAlerts.fromNotification(sbn.packageName, title, text) ?: return
        RoadAlertBus.post(alert)
    }
}
