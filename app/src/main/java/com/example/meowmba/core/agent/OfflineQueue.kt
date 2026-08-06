package com.example.meowmba.core.agent

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Queues actions that require the remote server when the bridge is offline.
 * Flushes automatically when connectivity is restored.
 */
data class QueuedAction(
    val id: Long = System.currentTimeMillis(),
    val toolId: String,
    val args: Map<String, String>,
    val description: String,
    val queuedAt: Long = System.currentTimeMillis(),
)

class OfflineQueue {
    companion object {
        private const val TAG = "OfflineQueue"
        private const val MAX_QUEUE_SIZE = 50
    }

    private val queue = CopyOnWriteArrayList<QueuedAction>()
    private val mutex = Mutex()

    val size: Int get() = queue.size
    val isEmpty: Boolean get() = queue.isEmpty()
    val pending: List<QueuedAction> get() = queue.toList()

    fun enqueue(action: QueuedAction) {
        if (queue.size >= MAX_QUEUE_SIZE) {
            queue.removeAt(0) // drop oldest
        }
        queue.add(action)
        Log.d(TAG, "Queued: [${action.toolId}] ${action.description} (${queue.size} pending)")
    }

    /**
     * Flush all queued actions. Returns them and clears the queue.
     * Caller is responsible for executing them via the bridge.
     */
    suspend fun flush(): List<QueuedAction> = mutex.withLock {
        val items = queue.toList()
        queue.clear()
        Log.d(TAG, "Flushed ${items.size} queued actions")
        items
    }

    fun clear() {
        queue.clear()
    }

    /** Summary for the user. */
    fun summary(): String {
        if (queue.isEmpty()) return "No pending actions."
        val grouped = queue.groupBy { it.toolId }
        return "Pending (${queue.size}): " + grouped.entries.joinToString(", ") {
            "${it.value.size}x ${it.key}"
        }
    }
}
