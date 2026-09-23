package com.example.meowmba.core.alerts

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads Waze / Google Maps notifications for police and speed-camera reports, and tells
 * Meow Flow who is waiting on you (app + sender only). Off until the user enables
 * notification access in system settings.
 */
class RoadAlertListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString()
        forwardToFlow(sbn, title, posted = true)
        val alert = RoadAlerts.fromNotification(sbn.packageName, title, text) ?: return
        RoadAlertBus.post(alert)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        forwardToFlow(sbn, n.extras.getCharSequence("android.title")?.toString(), posted = false)
    }

    /** Meow Flow gets the app, category and sender only — never the message text. */
    private fun forwardToFlow(sbn: StatusBarNotification, title: String?, posted: Boolean) {
        val n = sbn.notification
        if (sbn.isOngoing || sbn.packageName == packageName) return
        if ((n.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return
        val sender = title?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val hub = com.example.meowmba.core.flow.sources.FlowNotificationHub
        if (posted) {
            @Suppress("DEPRECATION") val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName.substringAfterLast('.'))
            hub.post(com.example.meowmba.core.flow.sources.FlowNotificationHub.Posted(sbn.packageName, label, n.category, sender, sbn.postTime))
        } else {
            hub.post(com.example.meowmba.core.flow.sources.FlowNotificationHub.Removed(sbn.packageName, sender))
        }
    }
}
