package com.example.meowmba.core.flow

import com.example.meowmba.core.flow.decide.Assessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Builds the current [Situation] from the context sources (location, calendar, driving…). */
fun interface SituationProvider {
    suspend fun current(now: Long): Situation
}

/** Persists the interruption ledger across process restarts. */
interface LedgerStore {
    fun load(): InterruptLedger
    fun save(ledger: InterruptLedger)
}

class InMemoryLedgerStore(private var ledger: InterruptLedger = InterruptLedger()) : LedgerStore {
    override fun load() = ledger
    override fun save(ledger: InterruptLedger) { this.ledger = ledger }
}

/**
 * Orchestrates Meow Flow (master plan §1): evidence → debounce → situation →
 * assessor chain → [FlowPolicy] → [snapshot].
 *
 * Heat rules: nothing runs unless evidence, an intention change, a user action or a
 * settings change arrives. Settings changes re-apply the policy to the last
 * assessments without calling a model. One recompute at a time.
 */
class FlowController(
    private val scope: CoroutineScope,
    private val store: IntentionStore,
    private val bus: EvidenceBus,
    private val situations: SituationProvider,
    private val assessor: Assessor,
    private val settings: StateFlow<FlowSettings>,
    private val ledgerStore: LedgerStore = InMemoryLedgerStore(),
    private val outcomes: OutcomeLog = InMemoryOutcomeLog(),
    private val policy: FlowPolicy = FlowPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    private val state = MutableStateFlow(FlowSnapshot.EMPTY)
    val snapshot: StateFlow<FlowSnapshot> = state.asStateFlow()

    private val mutex = Mutex()
    private var jobs: List<Job> = emptyList()
    private var pending: Job? = null
    private var lastSituation: Situation? = null
    private var lastAssessments: List<Assessment> = emptyList()
    private var lastInterruptId: String? = null
    /** Interruptions already answered/passed, with the time they stay suppressed until. */
    private val suppressedUntil = HashMap<String, Long>()
    private var lastHeld: Set<String> = emptySet()

    /** Id of the assessor that produced the current snapshot ("laya-q8-phone", "rules", …). */
    var engineUsed: String? = null
        private set

    val running: Boolean get() = jobs.any { it.isActive }

    fun start() {
        if (running) return
        jobs = listOf(
            scope.launch { bus.events.collect { schedule() } },
            scope.launch { store.intentions.drop(1).collect { schedule() } },
            scope.launch { settings.drop(1).collect { reapplyPolicy() } },
        )
        schedule(immediate = true)
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        pending?.cancel()
    }

    /** Coalesce bursts of evidence into one decision. */
    fun schedule(immediate: Boolean = false) {
        pending?.cancel()
        pending = scope.launch {
            if (!immediate) delay(debounceMs)
            recompute()
        }
    }

    /** Full pass: new situation and fresh judgments. */
    suspend fun recompute() = mutex.withLock {
        val now = clock()
        val situation = situations.current(now)
        val open = store.open(now)
        val assessments = assessor.assess(situation, open) ?: emptyList()
        engineUsed = (assessor as? com.example.meowmba.core.flow.decide.AssessorChain)?.lastUsed ?: assessor.id
        lastSituation = situation
        lastAssessments = assessments
        publish(situation, open)
    }

    /** Settings changed: same judgments, new policy result. No model call. */
    suspend fun reapplyPolicy() = mutex.withLock {
        val situation = lastSituation ?: return@withLock
        publish(situation.copy(now = clock()), store.open(clock()))
    }

    private suspend fun publish(situation: Situation, open: List<Intention>) {
        val now = situation.now
        suppressedUntil.entries.removeAll { it.value <= now }
        val result = policy.decide(
            situation, open, lastAssessments, settings.value, ledgerStore.load(),
            activeInterrupt = lastInterruptId, suppressed = suppressedUntil.keys.toSet(),
        )
        ledgerStore.save(result.ledger)
        val snap = result.snapshot
        val interrupt = snap.interrupt
        if (interrupt != null && interrupt.item.intentionId != lastInterruptId) {
            outcomes.record(OutcomeRecord(interrupt.item.intentionId, snap.at, Outcome.INTERRUPTED, interrupt.channel, interrupt.item.urgency, engineUsed))
        }
        lastInterruptId = interrupt?.item?.intentionId
        val heldNow = snap.more.filter { it.heldReason != null }
        heldNow.filter { it.intentionId !in lastHeld }.forEach {
            outcomes.record(OutcomeRecord(it.intentionId, snap.at, Outcome.HELD, null, it.urgency, engineUsed))
        }
        lastHeld = heldNow.map { it.intentionId }.toSet()
        state.value = snap
    }

    /** User pressed the item's primary action. The intention is done unless the action only opens something. */
    suspend fun onPrimary(intentionId: String, completes: Boolean = true) {
        outcomes.record(OutcomeRecord(intentionId, clock(), Outcome.ACTED, engine = engineUsed))
        if (completes) store.setStatus(intentionId, IntentionStatus.DONE)
        dismissInterruptIf(intentionId)
    }

    /** "Later" / "Not today": snooze without losing the intention. */
    suspend fun onLater(intentionId: String, forMs: Long = SNOOZE_MS) {
        outcomes.record(OutcomeRecord(intentionId, clock(), Outcome.SNOOZED, engine = engineUsed))
        store.setStatus(intentionId, IntentionStatus.SNOOZED, clock() + forMs)
        dismissInterruptIf(intentionId)
    }

    /** The chance passed without an answer (e.g. the store is behind the user). */
    suspend fun onChancePassed(intentionId: String) {
        outcomes.record(OutcomeRecord(intentionId, clock(), Outcome.TOO_LATE, engine = engineUsed))
        dismissInterruptIf(intentionId)
    }

    private fun dismissInterruptIf(intentionId: String) {
        suppressedUntil[intentionId] = clock() + SUPPRESS_MS
        if (lastInterruptId == intentionId) lastInterruptId = null
        val snap = state.value
        if (snap.interrupt?.item?.intentionId == intentionId) state.value = snap.copy(interrupt = null)
    }

    companion object {
        const val DEBOUNCE_MS = 2_000L
        const val SNOOZE_MS = 60 * 60 * 1000L
        /** After an interruption is answered or passes, don't interrupt for it again for this long. */
        const val SUPPRESS_MS = 6 * 60 * 60 * 1000L
    }
}
