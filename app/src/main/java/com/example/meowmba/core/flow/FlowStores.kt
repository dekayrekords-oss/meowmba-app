package com.example.meowmba.core.flow

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Open intentions. The Room-backed implementation (tables in master plan §5.3,
 * migration 10→11) is added by the integrator, who owns MeowmbaDatabase; this
 * interface keeps the engine independent of storage.
 */
interface IntentionStore {
    val intentions: StateFlow<List<Intention>>
    suspend fun upsert(intention: Intention)
    suspend fun find(id: String): Intention? = intentions.value.firstOrNull { it.id == id }
    suspend fun setStatus(id: String, status: IntentionStatus, snoozedUntil: Long? = null)
    /** Close every open intention a confirmed change satisfied (catalogue #295, #600). */
    suspend fun closeSatisfied(ids: Collection<String>)
    fun open(now: Long): List<Intention> = intentions.value.filter { it.isOpenAt(now) }
}

class InMemoryIntentionStore(initial: List<Intention> = emptyList()) : IntentionStore {
    private val state = MutableStateFlow(initial)
    override val intentions: StateFlow<List<Intention>> = state.asStateFlow()

    override suspend fun upsert(intention: Intention) = state.update { list ->
        list.filterNot { it.id == intention.id } + intention
    }

    override suspend fun setStatus(id: String, status: IntentionStatus, snoozedUntil: Long?) = state.update { list ->
        list.map { if (it.id == id) it.copy(status = status, snoozedUntil = snoozedUntil) else it }
    }

    override suspend fun closeSatisfied(ids: Collection<String>) = state.update { list ->
        list.map { if (it.id in ids && it.status != IntentionStatus.DROPPED) it.copy(status = IntentionStatus.DONE) else it }
    }
}

/**
 * Evidence producers (location, driving, notifications, calendar, speech…) publish here.
 * Non-blocking: bursts beyond the buffer drop the oldest evidence.
 */
class EvidenceBus(capacity: Int = 64) {
    private val flow = MutableSharedFlow<Evidence>(extraBufferCapacity = capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Evidence> = flow.asSharedFlow()
    fun publish(evidence: Evidence) { flow.tryEmit(evidence) }
}

enum class Outcome { SHOWN, ACTED, DISMISSED, SNOOZED, TOO_LATE, TOO_EARLY, REQUESTED_LATER, INTERRUPTED, HELD }

data class OutcomeRecord(
    val intentionId: String,
    val at: Long,
    val outcome: Outcome,
    val channel: Channel? = null,
    val urgency: Urgency? = null,
    val engine: String? = null,
)

/** Feedback for calibration and interrupt learning (master plan §8.4). */
interface OutcomeLog {
    suspend fun record(record: OutcomeRecord)
    suspend fun recent(limit: Int = 200): List<OutcomeRecord>
}

class InMemoryOutcomeLog(private val capacity: Int = 1000) : OutcomeLog {
    private val records = ArrayDeque<OutcomeRecord>()
    override suspend fun record(record: OutcomeRecord) {
        synchronized(records) { records.addLast(record); while (records.size > capacity) records.removeFirst() }
    }
    override suspend fun recent(limit: Int): List<OutcomeRecord> = synchronized(records) { records.toList().takeLast(limit) }
}
