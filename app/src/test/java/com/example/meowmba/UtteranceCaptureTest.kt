package com.example.meowmba

import android.speech.SpeechRecognizer
import com.example.meowmba.core.audio.SpeechEngine
import com.example.meowmba.core.audio.UtteranceCapture
import com.example.meowmba.core.audio.VoiceEndpointing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The complaint this exists for: "if I pause for a second the voice stops being picked up
 * and I can't finish my sentence." The platform ends a session after about a second of
 * quiet, so the fix has to treat that ending as a pause and keep going.
 *
 * Time is injected rather than slept through — these assert the endpointing rule, not how
 * fast a machine runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UtteranceCaptureTest {

    private class FakeEngine : SpeechEngine {
        override val isListening: StateFlow<Boolean> = MutableStateFlow(false)
        override val partialResults = MutableStateFlow("")
        var sessions = 0
        var stops = 0
        private var onResult: ((String) -> Unit)? = null
        private var onError: ((Int) -> Unit)? = null

        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            sessions++
            this.onResult = onResult
            this.onError = onError
        }

        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            this.onResult = onResult; this.onError = onError
        }

        override fun stopListening() { stops++ }
        override fun destroy() {}

        fun result(text: String) = onResult?.invoke(text)
        fun error(code: Int) = onError?.invoke(code)
        fun partial(text: String) { partialResults.value = text }
    }

    /** A clock the test moves by hand. */
    private class Clock(var t: Long = 0L) {
        operator fun invoke(): Long = t
    }

    private fun capture(
        engine: FakeEngine,
        scope: TestScope,
        clock: Clock,
    ) = UtteranceCapture(
        engine = engine,
        scope = scope,
        silenceToFinishMs = 2_500L,
        maxUtteranceMs = 45_000L,
        initialSilenceMs = 8_000L,
        now = { clock() },
    )

    @Test
    fun `a pause mid-sentence does not end the turn`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var final: String? = null
        capture(engine, this, clock).start(onFinal = { final = it }, onError = {})

        // First half, then the recognizer decides you are done after its ~1s of quiet.
        clock.t = 1_000; engine.result("sell my")
        assertNull("must not deliver half a sentence", final)
        assertEquals("should have opened a new session", 2, engine.sessions)

        // Second half arrives in the new session.
        clock.t = 2_000; engine.result("fender amp")
        assertNull(final)

        // Now a real silence: the recognizer reports nothing heard, well past the gap.
        clock.t = 5_000; engine.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals("sell my fender amp", final)
    }

    @Test
    fun `a long enough silence after speech ends the turn`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var final: String? = null
        capture(engine, this, clock).start(onFinal = { final = it }, onError = {})

        clock.t = 1_000; engine.result("what do I have")
        // The next session closes only after the full pause has elapsed.
        clock.t = 4_000; engine.result("")
        assertEquals("what do I have", final)
    }

    @Test
    fun `an idle session result is preserved while continuation is captured`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var final: String? = null
        val capture = UtteranceCapture(
            engine = engine,
            scope = this,
            initialText = "send a message",
            silenceToFinishMs = 2_500L,
            endpointDelayFor = VoiceEndpointing::silenceAfter,
            now = { clock() },
        )
        capture.start(onFinal = { final = it }, onError = {})

        assertEquals("the continuation mic opens immediately", 1, engine.sessions)
        clock.t = 700; engine.result("to Wyatt")
        assertNull(final)
        clock.t = 2_000; engine.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals("send a message to Wyatt", final)
    }

    @Test
    fun `semantic endpointing is fast for questions and patient for unfinished speech`() {
        assertEquals(VoiceEndpointing.COMPLETE_TURN_MS, VoiceEndpointing.silenceAfter("what time is it"))
        assertEquals(VoiceEndpointing.INCOMPLETE_TURN_MS, VoiceEndpointing.silenceAfter("send a message to"))
        assertEquals(VoiceEndpointing.SHORT_TURN_MS, VoiceEndpointing.silenceAfter("play music"))
    }

    @Test
    fun `partials keep the turn alive`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var final: String? = null
        capture(engine, this, clock).start(onFinal = { final = it }, onError = {})

        clock.t = 1_000; engine.result("add a")
        // Still talking — a partial three seconds later resets the pause timer, so the
        // turn must not be cut even though more than the gap has passed since the result.
        clock.t = 4_000; engine.partial("space heater")
        testScheduler.runCurrent()
        clock.t = 5_000; engine.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertNull("partial was recent, keep listening", final)

        clock.t = 9_000; engine.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals("add a space heater", final)
    }

    @Test
    fun `a hang after only partials still delivers what was said`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var final: String? = null
        var error: Int? = null
        capture(engine, this, clock).start(
            onFinal = { final = it },
            onError = { error = it },
        )

        clock.t = 1_000
        engine.partial("what's the weather")
        testScheduler.runCurrent()
        assertNull("must wait out the pause", final)

        // Recognizer never calls back. After a real silence the watchdog
        // has to deliver the partial — this is the hang the user reported.
        clock.t = 3_600
        testScheduler.advanceTimeBy(2_800)
        assertEquals("what's the weather", final)
        assertNull(error)
    }

    @Test
    fun `watchdog timeout with no speech still errors`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var final: String? = null
        var error: Int? = null
        capture(engine, this, clock).start(
            onFinal = { final = it },
            onError = { error = it },
        )

        clock.t = 8_200
        testScheduler.advanceTimeBy(8_400)
        assertNull(final)
        assertEquals(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, error)
    }

    @Test
    fun `saying nothing at all times out instead of looping`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var error: Int? = null
        capture(engine, this, clock).start(onFinal = {}, onError = { error = it })

        clock.t = 1_000; engine.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertNull("an early no-match is just a slow start", error)

        // Passed through as the recognizer reported it: "nothing heard" is the honest
        // reason, and the caller treats no-match and timeout the same way.
        clock.t = 9_000; engine.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(SpeechRecognizer.ERROR_NO_MATCH, error)
    }

    /** A broken recognizer must still look broken. */
    @Test
    fun `real errors pass straight through`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var error: Int? = null
        capture(engine, this, clock).start(onFinal = {}, onError = { error = it })

        engine.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertEquals(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, error)
        assertEquals("must not retry a permission failure", 1, engine.sessions)
    }

    @Test
    fun `cancel stops the mic and delivers nothing`() = runTest {
        val engine = FakeEngine()
        val clock = Clock()
        var final: String? = null
        val c = capture(engine, this, clock)
        c.start(onFinal = { final = it }, onError = {})
        clock.t = 500; engine.result("half a thought")
        c.cancel()
        clock.t = 4_000; engine.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertNull(final)
        assertEquals(1, engine.stops)
    }
}
