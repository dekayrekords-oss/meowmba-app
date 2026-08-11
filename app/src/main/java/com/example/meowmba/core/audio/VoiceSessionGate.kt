package com.example.meowmba.core.audio

/**
 * Decides which recognizer callbacks belong to the live session.
 *
 * ## Why this exists
 *
 * The engine keeps ONE [android.speech.SpeechRecognizer] alive across many
 * sequential sessions, because creating a fresh one per session is what fills
 * AiAi's small per-app capacity until it refuses everyone. The cost of that
 * choice is that callbacks lose their identity: Android delivers them to
 * whatever listener is currently attached, with nothing saying which session
 * produced them. Re-attaching a listener per session does not help — a late
 * callback from the old session is still delivered to the new listener.
 *
 * The previous attempt captured a `sessionEpoch` in the listener and compared
 * it on every callback. Because the listener is attached once (inside
 * `buildRecognizer`) while the epoch incremented on every `startListening`, the
 * captured value went stale immediately: from the second session onward every
 * callback failed the guard and voice died silently and permanently, with a
 * bound recognizer and no session ever reporting ready.
 *
 * So identity is not recoverable, and this gate decides from **state and
 * timing** instead — the only evidence that actually exists.
 *
 * ## The rules
 *
 * - A session is live only between [onStart] and its terminal callback.
 * - A pre-empted session ([onYield]) goes silent immediately: its late error is
 *   not a failure, it lost a race, and surfacing it would drive that engine's
 *   retry loop straight back into a fight with the engine that took the slot.
 * - Restarting on the same connection makes the vendor emit a late error for
 *   the session just replaced. Inside [settleMs] of a start, and only for codes
 *   a cancel actually produces, that error is treated as the artifact it is.
 * - Dropping startup errors risks waiting forever for a ready that never comes,
 *   so [readyTimedOut] gives the caller a bounded way to notice and retry.
 *
 * Pure and clock-injected: every rule above is a unit test, no device needed.
 */
class VoiceSessionGate(
    /** How long after a start a cancel artifact may still arrive. */
    private val settleMs: Long = 400L,
    /** How long to wait for onReadyForSpeech before calling the start failed. */
    private val readyTimeoutMs: Long = 5_000L,
) {

    private enum class State { IDLE, STARTING, LISTENING }

    private var state: State = State.IDLE
    private var startedAt: Long = 0L
    private var timeoutReported = false

    /**
     * Error codes a `cancel()` can produce on its own, copied from
     * android.speech.SpeechRecognizer so this class stays free of Android
     * types and runs in a plain JVM test.
     *
     * ERROR_RECOGNIZER_BUSY (8) is deliberately absent: it is never a cancel
     * artifact, it means the service genuinely refused, and the engine above
     * needs to see it to back off.
     */
    private val cancelArtifacts = setOf(
        5,  // ERROR_CLIENT
        11, // ERROR_SERVER_DISCONNECTED
    )

    /** A new session is being started on the recognizer. */
    fun onStart(nowMs: Long) {
        state = State.STARTING
        startedAt = nowMs
        timeoutReported = false
    }

    /** This engine lost the slot. It did not fail, and it must stay quiet. */
    fun onYield() {
        state = State.IDLE
    }

    fun onDestroy() {
        state = State.IDLE
    }

    /** onReadyForSpeech: the session is genuinely live from here. */
    fun acceptReady(): Boolean = when (state) {
        State.STARTING -> { state = State.LISTENING; true }
        State.LISTENING -> true   // a duplicate ready is harmless
        State.IDLE -> false
    }

    /** onBeginningOfSpeech / onEndOfSpeech. */
    fun acceptSpeechEdge(): Boolean = state == State.LISTENING

    fun acceptPartial(): Boolean = state == State.LISTENING

    /** onResults — terminal. A result before ready belongs to the old session. */
    fun acceptResults(): Boolean =
        if (state == State.LISTENING) { state = State.IDLE; true } else false

    /** onError — terminal when accepted. */
    fun acceptError(code: Int, nowMs: Long): Boolean = when (state) {
        // Pre-empted or already finished: silence is the contract.
        State.IDLE -> false

        State.LISTENING -> { state = State.IDLE; true }

        // The ambiguous window. A cancel-prone code this soon after a restart is
        // the artifact of the session we just replaced, not a new failure.
        State.STARTING ->
            if (code in cancelArtifacts && nowMs - startedAt <= settleMs) {
                false
            } else {
                state = State.IDLE
                true
            }
    }

    /**
     * True once, when a start has waited too long for onReadyForSpeech.
     *
     * Without this, dropping a stale startup error would leave the engine
     * waiting on a session that will never report ready. Fires a single time so
     * a polling caller raises one failure rather than a storm.
     */
    fun readyTimedOut(nowMs: Long): Boolean {
        if (state != State.STARTING || timeoutReported) return false
        if (nowMs - startedAt < readyTimeoutMs) return false
        timeoutReported = true
        state = State.IDLE
        return true
    }

    /** For logging and tests. */
    val isListening: Boolean get() = state == State.LISTENING
}
