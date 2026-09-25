package com.example.meowmba.core.flow.sources

import android.app.PendingIntent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Bounded, on-phone notification evidence and original Android conversation launch targets. */
object FlowNotificationHub {
    sealed interface Event { val packageName: String; val sender: String; val notificationKey: String }
    data class Posted(override val packageName: String, val appLabel: String, val category: String?,
        override val sender: String, override val notificationKey: String, val at: Long,
        val contentIntent: PendingIntent? = null, val text: String = "", val seed: Boolean = false,
        val delivery: DeliveryNotices.Delivery? = null) : Event
    data class Removed(override val packageName: String, override val sender: String,
        override val notificationKey: String, val at: Long = Long.MAX_VALUE) : Event

    private val flow = MutableSharedFlow<Event>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Event> = flow.asSharedFlow()
    private val active = LinkedHashMap<String, Posted>()
    val PEOPLE_CATEGORIES = setOf("msg", "email", "call", "missed_call", "social")

    @Synchronized fun post(event: Event) {
        val e = if (event is Posted) event.copy(text = event.text.take(500)) else event
        when (e) {
            is Posted -> {
                if ((active[e.notificationKey]?.at ?: Long.MIN_VALUE) > e.at) return
                active[e.notificationKey] = e
                while (active.size > 256) {
                    val oldest = active.values.minByOrNull { it.at } ?: break
                    active.remove(oldest.notificationKey)
                }
            }
            is Removed -> {
                if ((active[e.notificationKey]?.at ?: Long.MIN_VALUE) > e.at) return
                active.remove(e.notificationKey)
            }
        }
        flow.tryEmit(e)
    }
    @Synchronized fun activePosted(): List<Posted> = active.values.toList()
    @Synchronized fun target(key: String): PendingIntent? = active[key]?.contentIntent
    @Synchronized fun reconcileActive(keys: Set<String>) {
        active.values.filter { it.notificationKey !in keys }.toList().forEach {
            post(Removed(it.packageName, it.sender, it.notificationKey))
        }
    }
}
