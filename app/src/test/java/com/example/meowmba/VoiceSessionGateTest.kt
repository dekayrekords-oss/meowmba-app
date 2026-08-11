package com.example.meowmba

import com.example.meowmba.core.audio.VoiceSessionGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One SpeechRecognizer now serves many sequential sessions, so a callback
 * carries no evidence of which session produced it. The gate decides, from
 * state and timing alone, whether a callback belongs to the live session.
 *
 * The bug this replaces: the listener captured `sessionEpoch` once, at
 * recognizer-build time, while `startListening` incremented it every call. From
 * the second session onward every callback was dropped and voice died silently
 * and permanently.
 */
class VoiceSessionGateTest {

    private val CLIENT = 5             // SpeechRecognizer.ERROR_CLIENT
    private val BUSY = 8               // SpeechRecognizer.ERROR_RECOGNIZER_BUSY
    private val DISCONNECTED = 11      // SpeechRecognizer.ERROR_SERVER_DISCONNECTED

    // ── The regression that broke voice ───────────────────────────────────

    @Test
    fun `the tenth session still accepts its callbacks`() {
        val gate = VoiceSessionGate()
        repeat(10) { i ->
            val t = i * 10_000L
            gate.onStart(t)
            assertTrue("session $i dropped its ready", gate.acceptReady())
            assertTrue("session $i dropped its result", gate.acceptResults())
        }
    }

    @Test
    fun `a fresh gate accepts the very first session`() {
        val gate = VoiceSessionGate()
        gate.onStart(0)
        assertTrue(gate.acceptReady())
        assertTrue(gate.acceptPartial())
        assertTrue(gate.acceptResults())
    }

    // ── The yieldSession contract ─────────────────────────────────────────

    @Test
    fun `a pre-empted session gets no error, however late it arrives`() {
        val gate = VoiceSessionGate()
        gate.onStart(0)
        gate.acceptReady()
        gate.onYield()

        assertFalse("pre-empted engines must not see an error", gate.acceptError(DISCONNECTED, 50))
        assertFalse(gate.acceptError(CLIENT, 5_000))
        assertFalse(gate.acceptError(BUSY, 60_000))
    }

    @Test
    fun `a pre-empted session drops its late results too`() {
        val gate = VoiceSessionGate()
        gate.onStart(0)
        gate.acceptReady()
        gate.onYield()

        assertFalse(gate.acceptResults())
        assertFalse(gate.acceptPartial())
        assertFalse(gate.acceptSpeechEdge())
    }

    // ── Stale callbacks around a restart ──────────────────────────────────

    @Test
    fun `a stale disconnect right after restart is dropped, not surfaced`() {
        val gate = VoiceSessionGate(settleMs = 400)
        gate.onStart(0)
        gate.acceptReady()

        // Restart on the same connection: cancel() makes AiAi emit a late
        // disconnect that belongs to the session we just replaced.
        gate.onStart(1_000)
        assertFalse(gate.acceptError(DISCONNECTED, 1_050))
    }

    @Test
    fun `ERROR_CLIENT is always surfaced, even during the settle window`() {
        // Measured on device: with the recognizer stalled, a lone ERROR_CLIENT
        // was the ONLY callback the service ever emitted. Suppressing it hid
        // the single piece of evidence available.
        val gate = VoiceSessionGate(settleMs = 400)
        gate.onStart(0)
        assertTrue(gate.acceptError(CLIENT, 10))
    }

    @Test
    fun `a stale result arriving before the new session is ready is dropped`() {
        val gate = VoiceSessionGate()
        gate.onStart(0)
        gate.acceptReady()
        gate.onStart(1_000)

        // No onReadyForSpeech yet, so this result can only be the old session's.
        assertFalse(gate.acceptResults())
    }

    @Test
    fun `a genuine busy error during startup is surfaced immediately`() {
        val gate = VoiceSessionGate(settleMs = 400)
        gate.onStart(0)
        // BUSY is never a cancel artifact — the engine must see it and back off.
        assertTrue(gate.acceptError(BUSY, 50))
    }

    @Test
    fun `a stale-prone code after the settle window is a real failure`() {
        val gate = VoiceSessionGate(settleMs = 400)
        gate.onStart(0)
        // Too late to be the cancel artifact; the session genuinely failed to start.
        assertTrue(gate.acceptError(CLIENT, 900))
    }

    // ── The hang this design must not create ──────────────────────────────

    @Test
    fun `a session that never becomes ready eventually reports failure`() {
        val gate = VoiceSessionGate(settleMs = 400, readyTimeoutMs = 5_000)
        gate.onStart(0)
        gate.acceptError(DISCONNECTED, 100)   // dropped as stale

        assertFalse("not yet due", gate.readyTimedOut(4_000))
        assertTrue("must not hang forever", gate.readyTimedOut(5_100))
    }

    @Test
    fun `a session that became ready never times out`() {
        val gate = VoiceSessionGate(readyTimeoutMs = 5_000)
        gate.onStart(0)
        gate.acceptReady()
        assertFalse(gate.readyTimedOut(60_000))
    }

    @Test
    fun `the timeout fires once, not on every check`() {
        val gate = VoiceSessionGate(readyTimeoutMs = 5_000)
        gate.onStart(0)
        assertTrue(gate.readyTimedOut(5_100))
        assertFalse("a second check must not re-fire", gate.readyTimedOut(5_200))
    }

    // ── Terminal states ───────────────────────────────────────────────────

    @Test
    fun `results end the session, so a trailing error is ignored`() {
        val gate = VoiceSessionGate()
        gate.onStart(0)
        gate.acceptReady()
        assertTrue(gate.acceptResults())
        // AiAi delivers a result and THEN a disconnect. The sentence is already
        // complete; surfacing this would truncate the next capture.
        assertFalse(gate.acceptError(DISCONNECTED, 10))
    }

    @Test
    fun `an error ends the session, so a trailing result is ignored`() {
        val gate = VoiceSessionGate()
        gate.onStart(0)
        gate.acceptReady()
        assertTrue(gate.acceptError(BUSY, 10))
        assertFalse(gate.acceptResults())
    }

    @Test
    fun `destroy silences everything`() {
        val gate = VoiceSessionGate()
        gate.onStart(0)
        gate.acceptReady()
        gate.onDestroy()
        assertFalse(gate.acceptResults())
        assertFalse(gate.acceptError(CLIENT, 10))
        assertFalse(gate.acceptReady())
    }

    @Test
    fun `an idle gate accepts nothing`() {
        val gate = VoiceSessionGate()
        assertFalse(gate.acceptReady())
        assertFalse(gate.acceptResults())
        assertFalse(gate.acceptPartial())
        assertFalse(gate.acceptError(CLIENT, 0))
    }

    @Test
    fun `speech edges only count while listening`() {
        val gate = VoiceSessionGate()
        gate.onStart(0)
        assertFalse("not ready yet", gate.acceptSpeechEdge())
        gate.acceptReady()
        assertTrue(gate.acceptSpeechEdge())
    }
}
