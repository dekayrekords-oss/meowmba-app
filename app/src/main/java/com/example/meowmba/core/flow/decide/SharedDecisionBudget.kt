package com.example.meowmba.core.flow.decide

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by Flow and North Star: adding a feature must not double the phone's inference load. */
class SharedDecisionBudget(private val delegate: DecisionEngine, private val limit: Int = 6,
    private val windowMs: Long = 3 * 60_000L, private val clock: () -> Long = android.os.SystemClock::elapsedRealtime) : DecisionEngine {
    private val lock = Mutex()
    private var windowAt = Long.MIN_VALUE / 2
    private var used = 0
    override val id get() = delegate.id
    override suspend fun available() = delegate.available()
    override suspend fun ask(stateJson: String, questions: List<Question>): List<Answer>? {
        if (questions.isEmpty()) return emptyList()
        val admitted = lock.withLock {
            val now = clock()
            if (now - windowAt >= windowMs || now < windowAt) { used = 0; windowAt = now }
            if (used + questions.size > limit) false else { used += questions.size; true }
        }
        return if (admitted) delegate.ask(stateJson, questions) else null
    }
}
