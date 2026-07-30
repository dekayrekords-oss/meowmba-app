package com.example.meowmba.core.audio

import java.util.concurrent.atomic.AtomicReference

/**
 * Single-owner guard for the wake-word microphone.
 *
 * There are two wake loops in this app — the in-app one in CompanionViewModel while a
 * screen is visible, and [WakeWordService] while it is not — and the handoff between
 * them is supposed to be exclusive. It was not: with the activity top-resumed and the
 * service also alive, both drove recognition sessions and the platform answered with a
 * steady stream of ERROR_RECOGNIZER_BUSY (measured: 30 in 45 seconds), which is why the
 * wake phrase kept being missed.
 *
 * Whoever holds the mic wins; the other side simply does not start. Both are in the
 * same process, so a process-wide reference is the right scope — and if a stale process
 * ever survives, the guard cannot see it, which is exactly why [WakeWordService] no
 * longer returns START_STICKY.
 */
object MicOwner {

    private val owner = AtomicReference<String?>(null)

    /** Who currently owns the wake mic, or null. */
    val current: String? get() = owner.get()

    /** True if [who] took ownership (or already had it). */
    fun acquire(who: String): Boolean {
        if (owner.compareAndSet(null, who)) return true
        // Re-entrant: startWakeStandby() runs on every return to idle and must not lock
        // itself out.
        return owner.get() == who
    }

    /** Releases only if [who] is the current owner, so a late release cannot steal. */
    fun release(who: String) {
        owner.compareAndSet(who, null)
    }

    /** Test hook. */
    fun resetForTest() = owner.set(null)

    const val IN_APP = "in-app"
    const val SERVICE = "foreground-service"
}
