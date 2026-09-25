package com.example.meowmba.core.alerts

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads Waze / Google Maps notifications for police and speed-camera reports, and tells
 * Meow Flow who is waiting, with bounded text for local North Star links. Off until the user enables
 * notification access in system settings.
 */
class RoadAlertListener : NotificationListenerService() {

    override fun onListenerConnected() {
        // Re-seed active notifications after listener/process restart so Flow can rebuild
        // message intentions and their exact conversation targets.
        val active = runCatching { getActiveNotifications()?.toList().orEmpty() }.getOrElse { return }
        com.example.meowmba.core.flow.sources.FlowNotificationHub.reconcileActive(active.map { it.key }.toSet())
        active.forEach { sbn ->
            val n = sbn.notification
            forwardToFlow(sbn, n.extras.getCharSequence("android.title")?.toString(), true,
                n.extras.getCharSequence("android.text")?.toString() ?: n.extras.getCharSequence("android.bigText")?.toString(), seed = true)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString()
        forwardToFlow(sbn, title, posted = true, text = text)
        val alert = RoadAlerts.fromNotification(sbn.packageName, title, text) ?: return
        RoadAlertBus.post(alert)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        forwardToFlow(sbn, n.extras.getCharSequence("android.title")?.toString(), posted = false)
    }

    private var labels: Set<String> = emptySet()
    private var labelsAt = 0L

    /** Lower-cased names of launchable apps, refreshed at most every 6 hours. */
    private fun appLabels(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - labelsAt > 6 * 3_600_000L) {
            labels = runCatching {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                packageManager.queryIntentActivities(intent, 0).map { it.loadLabel(packageManager).toString().lowercase(java.util.Locale.ROOT) }.toSet()
            }.getOrDefault(labels)
            labelsAt = now
        }
        return labels
    }

    /**
     * Notification text remains local: delivery parsing and bounded North Star evidence.
     * PendingIntent is retained separately from the durable Flow item and never serialized.
     */
    private fun forwardToFlow(sbn: StatusBarNotification, title: String?, posted: Boolean, text: String? = null, seed: Boolean = false) {
        val n = sbn.notification
        if (sbn.isOngoing || sbn.packageName == packageName) return
        if ((n.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return
        val sender = title?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val hub = com.example.meowmba.core.flow.sources.FlowNotificationHub
        if (posted) {
            @Suppress("DEPRECATION") val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName.substringAfterLast('.'))
            // Companies and bots (an "Instagram" email, "no-reply") are not people waiting on you.
            val category = n.category?.takeIf {
                it !in hub.PEOPLE_CATEGORIES || com.example.meowmba.core.flow.sources.AppIntentions.looksLikePerson(sender, appLabels())
            }
            val delivery = if (n.category in DELIVERY_CATEGORIES || sbn.packageName in DELIVERY_APPS)
                com.example.meowmba.core.flow.sources.DeliveryNotices.parse(title, text) else null
            hub.post(com.example.meowmba.core.flow.sources.FlowNotificationHub.Posted(
                sbn.packageName, label, category, sender, sbn.key, sbn.postTime, n.contentIntent,
                text?.take(500).orEmpty(), seed, delivery))
        } else {
            hub.post(com.example.meowmba.core.flow.sources.FlowNotificationHub.Removed(sbn.packageName, sender, sbn.key, sbn.postTime))
        }
    }

    private companion object {
        /** Messages and email: where carriers and shops text you. */
        val DELIVERY_CATEGORIES = setOf(android.app.Notification.CATEGORY_MESSAGE, android.app.Notification.CATEGORY_EMAIL)
        /** Text/email apps (some omit the category) and carrier/shop apps, never general promos. */
        val DELIVERY_APPS = setOf(
            "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms",
            "com.google.android.gm", "com.samsung.android.email.provider", "com.microsoft.office.outlook",
            "com.amazon.mShop.android.shopping", "com.ups.mobile.android", "com.fedex.ida.android",
            "gov.usps.mobile", "com.dhl.exp.dhlmobile",
        )
    }
}
