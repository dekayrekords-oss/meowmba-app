package com.example.meowmba.core.notification

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory ring buffer of recent device notifications.
 * Thread-safe, accessed from the listener service and the VM.
 */
data class CapturedNotification(
    val id: Int,
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val isOngoing: Boolean = false,
    var read: Boolean = false,
)

object NotificationStore {
    private const val MAX_SIZE = 50
    private val buffer = CopyOnWriteArrayList<CapturedNotification>()
    private var lastClearedAt: Long = 0L

    fun add(notification: CapturedNotification) {
        // Deduplicate by key
        buffer.removeAll { it.key == notification.key }
        buffer.add(notification)
        // Trim oldest
        while (buffer.size > MAX_SIZE) buffer.removeAt(0)
    }

    fun remove(key: String) {
        buffer.removeAll { it.key == key }
    }

    fun getAll(): List<CapturedNotification> = buffer.toList()

    fun getUnread(): List<CapturedNotification> = buffer.filter { !it.read && it.timestamp > lastClearedAt }

    fun getSince(timestamp: Long): List<CapturedNotification> = buffer.filter { it.timestamp > timestamp }

    fun getByApp(packageName: String): List<CapturedNotification> = buffer.filter { it.packageName == packageName }

    fun markAllRead() {
        buffer.forEach { it.read = true }
    }

    fun clear() {
        lastClearedAt = System.currentTimeMillis()
        buffer.clear()
    }

    /** Grouped summary: app name -> count of unread. */
    fun unreadSummary(): Map<String, Int> {
        return getUnread().groupBy { it.appName }.mapValues { it.value.size }
    }

    val unreadCount: Int get() = getUnread().size
}
