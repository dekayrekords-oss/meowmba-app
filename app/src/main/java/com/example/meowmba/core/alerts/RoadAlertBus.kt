package com.example.meowmba.core.alerts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Latest road alert for the face. The GPS feed and the notification listener both post here. */
object RoadAlertBus {
    private val _latest = MutableStateFlow<RoadAlert?>(null)
    val latest: StateFlow<RoadAlert?> = _latest.asStateFlow()

    fun post(alert: RoadAlert) {
        _latest.value = alert
    }

    fun clear() {
        _latest.value = null
    }
}
