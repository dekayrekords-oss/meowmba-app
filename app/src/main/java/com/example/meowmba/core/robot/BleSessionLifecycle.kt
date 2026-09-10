package com.example.meowmba.core.robot

/** A platform write callback is not proof that a physical motor has stopped. */
enum class BleStopReceipt { NOT_REQUESTED, PENDING, PLATFORM_COMPLETED, UNCONFIRMED }

/**
 * Radio-independent ownership and one-operation-at-a-time policy for a BLE link.
 *
 * Only explicit [beginAttempt] opens admission. Ordinary writes are never queued:
 * a busy link rejects them, rather than replaying stale movement after a stop.
 * Only initialization and explicitly supplied operations queue. This helper
 * cannot establish packet safety; the quarantined Android adapter sends no stops.
 * The Android adapter must hold this monitor across compound guard/send actions.
 */
internal class BleSessionLifecycle<C : Any>(private val closeConnection: (C) -> Unit) {
    enum class Phase { CLOSED, CONNECTING, INITIALIZING, READY, STOPPING, CLOSING }

    data class Operation<C>(val key: String, val start: (C) -> Boolean)
    private data class Pending<C>(val operation: Operation<C>, val stop: Boolean = false)

    var generation: Long = 0
        private set
    var phase: Phase = Phase.CLOSED
        private set
    var connection: C? = null
        private set
    var stopReceipt: BleStopReceipt = BleStopReceipt.NOT_REQUESTED
        private set
    var stopRequestId: Long = 0
        private set
    private val pending = ArrayDeque<Pending<C>>()
    private var inFlight: Pending<C>? = null
    private var stopFailed = false
    private var terminallyClosed = false
    var initializationFailed: Boolean = false
        private set

    @Synchronized fun beginAttempt(): Long? {
        if (terminallyClosed || phase != Phase.CLOSED) return null
        retire()
        generation++
        phase = Phase.CONNECTING
        stopReceipt = BleStopReceipt.NOT_REQUESTED
        initializationFailed = false
        return generation
    }

    @Synchronized fun acceptsAttempt(token: Long): Boolean =
        token == generation && phase != Phase.CLOSED && phase != Phase.CLOSING

    @Synchronized fun accepts(connection: C): Boolean =
        this.connection === connection && acceptsAttempt(generation)

    @Synchronized fun bind(token: Long, connection: C): Boolean {
        if (!acceptsAttempt(token) || phase != Phase.CONNECTING || this.connection != null) {
            if (this.connection !== connection) closeConnection(connection)
            return false
        }
        this.connection = connection
        return true
    }

    @Synchronized fun initialize(connection: C, operations: List<Operation<C>>): Boolean {
        if (!accepts(connection) || phase != Phase.CONNECTING) return false
        phase = Phase.INITIALIZING
        operations.forEach { pending.addLast(Pending(it)) }
        drain()
        return true
    }

    /** Returns platform submission, not completion. A busy operation is dropped. */
    @Synchronized fun submit(operation: Operation<C>): Boolean {
        val current = connection ?: return false
        if (phase != Phase.READY || inFlight != null || pending.isNotEmpty()) return false
        val active = Pending(operation)
        inFlight = active
        val started = runCatching { operation.start(current) }.getOrDefault(false)
        if (!started && inFlight === active) inFlight = null
        return started
    }

    /**
     * Revoke ordinary work immediately, then serialize only stops behind the
     * current platform operation. A true result means admitted, NOT delivered.
     * Repeated stop/close requests do not append writes or extend the deadline.
     */
    @Synchronized fun stop(operations: List<Operation<C>>, closeAfter: Boolean = false): Boolean {
        if (closeAfter) terminallyClosed = true
        if (phase == Phase.CLOSED) {
            if (closeAfter && stopReceipt == BleStopReceipt.NOT_REQUESTED) {
                stopReceipt = BleStopReceipt.UNCONFIRMED
            }
            return false
        }
        if (phase == Phase.CLOSING) return stopReceipt == BleStopReceipt.PENDING
        if (phase == Phase.STOPPING) {
            if (closeAfter) phase = Phase.CLOSING
            return true
        }
        if (connection == null || operations.isEmpty() ||
            (!closeAfter && phase != Phase.READY)
        ) {
            if (closeAfter) {
                stopReceipt = BleStopReceipt.UNCONFIRMED
                retire()
            }
            return false
        }
        phase = if (closeAfter) Phase.CLOSING else Phase.STOPPING
        pending.clear()
        stopFailed = false
        stopReceipt = BleStopReceipt.PENDING
        stopRequestId++
        operations.forEach { pending.addLast(Pending(it, stop = true)) }
        drain()
        return true
    }

    /** Exact connection AND operation matching prevents stale/foreign advances. */
    @Synchronized fun completed(connection: C, key: String, success: Boolean): Boolean {
        if (this.connection !== connection || phase == Phase.CLOSED) return false
        val active = inFlight ?: return false
        if (active.operation.key != key) return false
        inFlight = null
        if (active.stop && !success) stopFailed = true
        if (phase == Phase.INITIALIZING && !success) initializationFailed = true
        drain()
        return true
    }

    /** Missing callbacks leave stop unconfirmed; never resume movement on timeout. */
    @Synchronized fun expireStop(token: Long, requestId: Long): Boolean {
        if (token != generation || requestId != stopRequestId ||
            stopReceipt != BleStopReceipt.PENDING
        ) return false
        stopReceipt = BleStopReceipt.UNCONFIRMED
        retire()
        return true
    }

    @Synchronized fun abort(token: Long): Boolean {
        if (token != generation || phase == Phase.CLOSED) return false
        retire()
        return true
    }

    /** Terminal quarantine: discard even an already-pending stop queue, without starting it. */
    @Synchronized fun revokeWithoutWrites() {
        terminallyClosed = true
        stopReceipt = BleStopReceipt.UNCONFIRMED
        retire()
    }

    @Synchronized fun disconnected(connection: C): Boolean {
        if (this.connection !== connection) return false
        retire()
        return true
    }

    private fun drain() {
        val current = connection ?: return
        while (inFlight == null) {
            val next = pending.removeFirstOrNull()
            if (next == null) {
                when (phase) {
                    Phase.INITIALIZING -> if (initializationFailed) retire() else phase = Phase.READY
                    Phase.STOPPING, Phase.CLOSING -> {
                        stopReceipt = if (stopFailed) BleStopReceipt.UNCONFIRMED
                        else BleStopReceipt.PLATFORM_COMPLETED
                        // A failed safety write must not reopen motion admission.
                        if (phase == Phase.CLOSING || stopFailed) retire()
                        else phase = Phase.READY
                    }
                    else -> Unit
                }
                return
            }
            inFlight = next
            val started = runCatching { next.operation.start(current) }.getOrDefault(false)
            if (started) return
            if (inFlight === next) inFlight = null
            if (next.stop) stopFailed = true
            if (phase == Phase.INITIALIZING) initializationFailed = true
        }
    }

    private fun retire() {
        if (stopReceipt == BleStopReceipt.PENDING) stopReceipt = BleStopReceipt.UNCONFIRMED
        phase = Phase.CLOSED
        pending.clear()
        inFlight = null
        val retired = connection
        connection = null
        if (retired != null) closeConnection(retired)
    }
}
