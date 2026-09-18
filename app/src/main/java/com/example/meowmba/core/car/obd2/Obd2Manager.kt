package com.example.meowmba.core.car.obd2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns ELM327 connect/poll lifecycle and alert evaluation for drive UI + Lumen cues.
 */
class Obd2Manager(
    private val scope: CoroutineScope,
    private val client: Elm327Client,
    private val pollIntervalMs: Long = 1_200L,
) {
    private val _reading = MutableStateFlow(Obd2Reading())
    val reading: StateFlow<Obd2Reading> = _reading.asStateFlow()

    private val _alerts = MutableStateFlow<List<Obd2Alert>>(emptyList())
    val alerts: StateFlow<List<Obd2Alert>> = _alerts.asStateFlow()

    var thresholds: Obd2AlertThresholds = Obd2AlertThresholds()

    private var pollJob: Job? = null
    private var lastAddress: String = ""

    val lastAdapterAddress: String get() = lastAddress

    suspend fun connect(address: String): Result<Unit> {
        lastAddress = address.trim()
        val result = client.connect(lastAddress)
        if (result.isSuccess) {
            _reading.value = Obd2Reading(
                connected = true,
                adapterAddress = lastAddress,
                updatedAtMs = System.currentTimeMillis(),
            )
            startPolling()
        } else {
            _reading.value = Obd2Reading(
                connected = false,
                adapterAddress = lastAddress,
                lastError = result.exceptionOrNull()?.message.orEmpty(),
                updatedAtMs = System.currentTimeMillis(),
            )
            stopPolling()
        }
        return result
    }

    fun disconnect() {
        stopPolling()
        client.disconnect()
        _reading.value = Obd2Reading(
            connected = false,
            adapterAddress = lastAddress,
            updatedAtMs = System.currentTimeMillis(),
        )
        _alerts.value = emptyList()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val snap = client.poll()
                _reading.value = snap.copy(adapterAddress = snap.adapterAddress.ifBlank { lastAddress })
                _alerts.value = Obd2Alerts.evaluate(snap, thresholds)
                delay(pollIntervalMs)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}
