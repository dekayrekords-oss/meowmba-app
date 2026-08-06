package com.example.meowmba.core.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Intercepts all device notifications and stores them in [NotificationStore].
 * Filters out low-value noise (system, media controls, ongoing).
 */
class MeowNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "MeowNotifListener"

        /** Packages to always ignore. */
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers.downloads",
            "com.example.meowmba", // our own notifications
        )

        /** Categories to ignore. */
        private val IGNORED_CATEGORIES = setOf(
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_PROGRESS,
        )

        @Volatile
        var isConnected: Boolean = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.i(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (shouldIgnore(sbn)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        // Skip empty notifications
        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val ai = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            sbn.packageName.substringAfterLast('.')
        }

        val captured = CapturedNotification(
            id = sbn.id,
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = bigText ?: text,
            timestamp = sbn.postTime,
            isOngoing = sbn.isOngoing,
        )

        NotificationStore.add(captured)
        Log.d(TAG, "Captured: [$appName] $title")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationStore.remove(sbn.key)
    }

    private fun shouldIgnore(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in IGNORED_PACKAGES) return true
        if (sbn.isOngoing) return true
        val category = sbn.notification?.category
        if (category in IGNORED_CATEGORIES) return true
        // Ignore silent/low-priority
        val importance = sbn.notification?.priority ?: 0
        if (importance < Notification.PRIORITY_DEFAULT) return true
        return false
    }
}
