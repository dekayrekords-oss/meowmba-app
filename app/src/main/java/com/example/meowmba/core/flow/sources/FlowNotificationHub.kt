package com.example.meowmba.core.flow.sources

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridge from the single NotificationListenerService (RoadAlertListener) to Flow.
 * Carries only package, app label, category and the sender/title the user already sees —
 * never the message text (master plan §7.3). Works only after the user grants
 * notification access; Flow shows a "connect" item until then.
 */
object FlowNotificationHub {
    sealed interface Event { val packageName: String; val sender: String }
    data class Posted(override val packageName: String, val appLabel: String, val category: String?,
                      override val sender: String, val at: Long,
                      /** A package update recognised on the phone from the text; the text itself is not carried. */
                      val delivery: DeliveryNotices.Delivery? = null) : Event
    data class Removed(override val packageName: String, override val sender: String) : Event

    private val flow = MutableSharedFlow<Event>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Event> = flow.asSharedFlow()

    /** Categories that mean "a person is waiting on you". */
    val PEOPLE_CATEGORIES = setOf("msg", "email", "call", "missed_call", "social")

    fun post(e: Event) { flow.tryEmit(e) }
}
