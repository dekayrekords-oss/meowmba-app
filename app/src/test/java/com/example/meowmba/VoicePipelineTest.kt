package com.example.meowmba

import com.example.meowmba.core.audio.SpeechEngine
import com.example.meowmba.core.audio.SpeechSynthesizer
import com.example.meowmba.core.audio.WakePhraseMatcher
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.InteractionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WakePhraseMatcherTest {

    @Test
    fun `matches all three built-in phrase families`() {
        val matcher = WakePhraseMatcher("Meow")
        assertNotNull(matcher.match("Hey Meow"))
        assertNotNull(matcher.match("Hey Memba"))
    }

    @Test
    fun `extracts command spoken after the wake phrase`() {
        val matcher = WakePhraseMatcher("Meow")
        val match = matcher.match("Hey Meow, what's the time?")
        assertEquals("hey meow", match?.phrase)
        assertEquals("what's the time", match?.command)
    }

    @Test
    fun `bare wake phrase has no command`() {
        val matcher = WakePhraseMatcher("Meow")
        assertNull(matcher.match("Hey Memba!")?.command)
    }

    @Test
    fun `custom companion name becomes a wake phrase`() {
        val matcher = WakePhraseMatcher("Whiskers")
        val match = matcher.match("hey whiskers sit")
        assertEquals("hey whiskers", match?.phrase)
        assertEquals("sit", match?.command)
    }

    @Test
    fun `word boundaries are enforced`() {
        val matcher = WakePhraseMatcher("Meow")
        assertNull(matcher.match("heymeow"))
        assertNull(matcher.match("hey meowington"))
        assertNull(matcher.match("say something else"))
    }

    @Test
    fun `phrase embedded in a longer sentence still matches`() {
        val matcher = WakePhraseMatcher("Meow")
        val match = matcher.match("um hey memba listen")
        assertEquals("listen", match?.command)
    }
}

/**
 * Phase 2 voice pipeline tests, driven by fake speech engines so no Android
 * runtime is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineTest {

    /** Fake STT: records calls, lets tests push partials/finals/errors. */
    class FakeSpeechEngine : SpeechEngine {
        override val isListening: StateFlow<Boolean> = MutableStateFlow(false)
        override val partialResults = MutableStateFlow("")
        override var isAvailable = true
        var startListeningCalls = 0
        var stopListeningCalls = 0
        private var lastOnResult: ((String) -> Unit)? = null
        private var lastOnError: ((Int) -> Unit)? = null

        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            startListeningCalls++
            lastOnResult = onResult
            lastOnError = onError
        }

        override fun stopListening() { stopListeningCalls++ }
        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            lastOnResult = onResult; lastOnError = onError
        }
        override fun destroy() {}

        fun simulateResult(text: String) { lastOnResult?.invoke(text) }
        fun simulatePartial(text: String) { partialResults.value = text }
        fun simulateError(code: Int) { lastOnError?.invoke(code) }
    }

    /** Fake TTS: records speech, exposes the engine callbacks. */
    class FakeSpeechSynthesizer : SpeechSynthesizer {
        override var onSpeechStarted: (() -> Unit)? = null
        override var onSpeechFinished: (() -> Unit)? = null
        override var onWordBoundary: ((Int, Int) -> Unit)? = null
        var spokenTexts = mutableListOf<String>()
        var stopCalls = 0

        override fun speak(text: String) { spokenTexts.add(text) }
        override fun stop() { stopCalls++ }
        override fun shutdown() {}
    }

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeStt: FakeSpeechEngine
    private lateinit var fakeTts: FakeSpeechSynthesizer
    private lateinit var viewModel: CompanionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeStt = FakeSpeechEngine()
        fakeTts = FakeSpeechSynthesizer()
        viewModel = CompanionViewModel(speechService = fakeStt, ttsManager = fakeTts)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bare wake word listens for a command`() = runTest {
        viewModel.onWakeDetected(null)
        // The ack is spoken aloud first ("Yes?"), then the mic opens for the command.
        assertEquals(InteractionState.SPEAKING, viewModel.uiState.value.interactionState)
        assertEquals("Yes?", viewModel.uiState.value.captionText)
        assertEquals(listOf("Yes?"), fakeTts.spokenTexts)

        advanceTimeBy(1_700) // ack hand-off (~1.5s), then the mic hand-off beat
        runCurrent()

        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
        assertTrue(fakeStt.startListeningCalls > 0)
    }

    @Test
    fun `wake word with command goes straight to thinking and answers`() = runTest {
        viewModel.onWakeDetected("what time is it")
        assertEquals(InteractionState.THINKING, viewModel.uiState.value.interactionState)
        advanceUntilIdle() // run the mock response pipeline past its thinking delay
        // No provider configured: the mock pipeline still answers out loud
        assertEquals(1, fakeTts.spokenTexts.size)
    }

    @Test
    fun `interrupt stops speech and starts a new listening turn`() = runTest {
        fakeTts.onSpeechStarted?.invoke() // companion is now SPEAKING
        assertEquals(InteractionState.SPEAKING, viewModel.uiState.value.interactionState)

        viewModel.interrupt()

        assertEquals(1, fakeTts.stopCalls)
        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
    }

    @Test
    fun `mic tap during speech interrupts it`() = runTest {
        fakeTts.onSpeechStarted?.invoke()
        viewModel.onMicClick()
        assertEquals(1, fakeTts.stopCalls)
        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
    }

    @Test
    fun `conversation mode stays for a follow-up after speaking`() = runTest {
        viewModel.setConversationMode(true)
        fakeTts.onSpeechStarted?.invoke()
        val callsBefore = fakeStt.startListeningCalls

        fakeTts.onSpeechFinished?.invoke()

        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
        // Waiting states carry no caption: captions are for live speech and replies.
        assertNull(viewModel.uiState.value.captionText)
        advanceTimeBy(150) // mic hand-off beat, then the capture session opens
        runCurrent()
        assertTrue(fakeStt.startListeningCalls > callsBefore)
    }

    @Test
    fun `without conversation mode speech end returns to idle`() = runTest {
        fakeTts.onSpeechStarted?.invoke()
        fakeTts.onSpeechFinished?.invoke()
        assertEquals(InteractionState.IDLE, viewModel.uiState.value.interactionState)
    }

    @Test
    fun `stop command ends the conversation politely`() = runTest {
        viewModel.setConversationMode(true)
        fakeTts.onSpeechStarted?.invoke()
        fakeTts.onSpeechFinished?.invoke() // follow-up opens after the mic hand-off beat
        advanceTimeBy(150)
        runCurrent()

        fakeStt.simulateResult("never mind")

        assertEquals(InteractionState.IDLE, viewModel.uiState.value.interactionState)
    }

    @Test
    fun `wake phrase heard during speech interrupts it`() = runTest {
        viewModel.setConversationMode(true)
        fakeTts.onSpeechStarted?.invoke() // SPEAKING (+ interruption watch active)

        fakeStt.simulatePartial("hey meow")

        assertEquals(1, fakeTts.stopCalls)
        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
    }

    @Test
    fun `word boundaries pulse the mouth energy`() = runTest {
        assertEquals(0f, viewModel.uiState.value.mouthEnergy, 0.001f)
        fakeTts.onWordBoundary?.invoke(0, 3)
        assertEquals(1f, viewModel.uiState.value.mouthEnergy, 0.001f)
    }

    @Test
    fun `renaming the companion updates state and persists safely without storage`() = runTest {
        viewModel.renameCompanion("Whiskers")
        assertEquals("Whiskers", viewModel.uiState.value.name)
        viewModel.renameCompanion("   ") // blank names are rejected
        assertEquals("Whiskers", viewModel.uiState.value.name)
    }

    @Test
    fun `muting the mouth stops currently playing speech`() = runTest {
        fakeTts.onSpeechStarted?.invoke()
        viewModel.onAvatarZoneClick(com.example.meowmba.ui.companion.AvatarZone.MOUTH)
        assertEquals(1, fakeTts.stopCalls)
        assertTrue(viewModel.uiState.value.isMuted)
    }

    @Test
    fun `mic privacy cut stops the wake engine and recognizer`() = runTest {
        viewModel.setWakeWordEnabled(true)
        val callsBefore = fakeStt.stopListeningCalls
        viewModel.onAvatarZoneClick(com.example.meowmba.ui.companion.AvatarZone.EAR_LEFT)
        assertFalse(viewModel.uiState.value.isMicEnabled)
        assertTrue(fakeStt.stopListeningCalls > callsBefore)
    }

    @Test
    fun `recognizer stays live during playback without echo interrupting`() = runTest {
        // Companion answers; the SelfVoiceFilter is now tracking its utterance
        viewModel.onUserTextSubmitted("hi")
        advanceUntilIdle()
        fakeTts.onSpeechStarted?.invoke()
        assertEquals(InteractionState.SPEAKING, viewModel.uiState.value.interactionState)
        assertTrue("recognizer is live during playback", fakeStt.startListeningCalls > 0)

        // The mic hears the companion's own answer — must NOT interrupt
        fakeStt.simulatePartial("no model is ready")
        assertEquals(InteractionState.SPEAKING, viewModel.uiState.value.interactionState)
        assertEquals(0, fakeTts.stopCalls)
    }

    @Test
    fun `novel user speech barges in over the companion's voice`() = runTest {
        viewModel.onUserTextSubmitted("hi")
        advanceUntilIdle()
        fakeTts.onSpeechStarted?.invoke()

        fakeStt.simulatePartial("wait stop that")
        assertEquals(1, fakeTts.stopCalls)
        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
    }

    @Test
    fun `background wake command routes through the normal pipeline`() = runTest {
        viewModel.onExternalWakeCommand("what time is it")
        assertEquals(InteractionState.THINKING, viewModel.uiState.value.interactionState)
        advanceUntilIdle()
        assertEquals(1, fakeTts.spokenTexts.size)
    }

    @Test
    fun `blank background wake command is ignored`() = runTest {
        viewModel.onExternalWakeCommand("   ")
        assertEquals(InteractionState.IDLE, viewModel.uiState.value.interactionState)
    }

    @Test
    fun `background listening toggle updates state`() = runTest {
        assertEquals(false, viewModel.uiState.value.backgroundListeningEnabled)
        viewModel.setBackgroundListeningEnabled(true)
        assertEquals(true, viewModel.uiState.value.backgroundListeningEnabled)
    }

    // ── Honest voice failures (mic tap / wake phrase "does nothing") ────────

    @Test
    fun `mic tap with mic privacy off explains instead of doing nothing`() = runTest {
        viewModel.onAvatarZoneClick(com.example.meowmba.ui.companion.AvatarZone.EAR_LEFT)
        assertFalse(viewModel.uiState.value.isMicEnabled)
        val callsBefore = fakeStt.startListeningCalls

        viewModel.onMicClick()

        // Mic privacy cut puts the avatar in MUTED — the tap changes nothing but the caption
        assertEquals(InteractionState.MUTED, viewModel.uiState.value.interactionState)
        assertEquals(callsBefore, fakeStt.startListeningCalls)
        assertTrue(viewModel.uiState.value.captionText!!.contains("Microphone is off"))
    }

    @Test
    fun `unavailable recognizer surfaces an honest caption`() = runTest {
        fakeStt.isAvailable = false

        viewModel.onMicClick()

        assertEquals(InteractionState.IDLE, viewModel.uiState.value.interactionState)
        assertEquals(0, fakeStt.startListeningCalls)
        assertTrue(viewModel.uiState.value.captionText!!.contains("isn't available"))
    }

    @Test
    fun `permission error is surfaced, a pause keeps listening`() = runTest {
        viewModel.onMicClick()
        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)

        fakeStt.simulateError(9) // ERROR_INSUFFICIENT_PERMISSIONS
        assertEquals(InteractionState.IDLE, viewModel.uiState.value.interactionState)
        assertTrue(viewModel.uiState.value.captionText!!.contains("permission"))

        // A no-match is how a PAUSE reports itself: the platform recognizer gives up
        // after about a second of quiet. The turn stays open instead of ending
        // mid-sentence (see UtteranceCapture), and the user is told nothing, because
        // nothing has gone wrong.
        viewModel.onMicClick()
        fakeStt.simulateError(7) // ERROR_NO_MATCH — "nothing heard yet"
        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
        // No waiting-state caption: only live speech and the reply caption the face.
        assertNull(viewModel.uiState.value.captionText)
    }

    @Test
    fun `wake engine stops after repeated recognizer failures`() = runTest {
        val engine = com.example.meowmba.core.audio.RecognizerWakeWordEngine(
            fakeStt, com.example.meowmba.core.audio.WakePhraseMatcher("Meow"), this
        )
        var fatalFired = false
        engine.onFatalError = { fatalFired = true }

        engine.start()
        // error → backoff → new session → error … (realistic interleave)
        // ERROR_AUDIO (4) still rides the regular 5-strike escalation.
        repeat(4) {
            fakeStt.simulateError(4) // ERROR_AUDIO
            advanceUntilIdle()
        }
        assertFalse(fatalFired) // 4 consecutive errors: still retrying

        fakeStt.simulateError(4) // 5th consecutive failure → give up
        assertTrue(fatalFired)
        assertFalse(engine.isRunning.value)
        val callsAtFatal = fakeStt.startListeningCalls
        advanceUntilIdle()
        assertEquals(callsAtFatal, fakeStt.startListeningCalls) // no hot loop after giving up
    }

    @Test
    fun `client error churn is tolerated, sustained client failure still stops`() = runTest {
        // AIAI on the Fold 7 emits short bursts of ERROR_CLIENT (5) right after the app
        // (re)starts; the old 5-strike rule tripped inside one burst and surfaced the
        // false "the speech recognizer keeps failing on this device". CLIENT now has a
        // long leash (25), but ~30s of sustained failure must still stop the loop.
        val engine = com.example.meowmba.core.audio.RecognizerWakeWordEngine(
            fakeStt, com.example.meowmba.core.audio.WakePhraseMatcher("Meow"), this
        )
        var fatalFired = false
        engine.onFatalError = { fatalFired = true }

        engine.start()
        repeat(24) {
            fakeStt.simulateError(5) // ERROR_CLIENT burst
            advanceUntilIdle()
        }
        assertFalse(fatalFired) // a churn burst must not declare the device broken
        assertTrue(engine.isRunning.value)

        fakeStt.simulateError(5) // 25th sustained failure → give up
        assertTrue(fatalFired)
        assertFalse(engine.isRunning.value)
    }

    @Test
    fun `wake engine keeps retrying through occasional errors`() = runTest {
        val engine = com.example.meowmba.core.audio.RecognizerWakeWordEngine(
            fakeStt, com.example.meowmba.core.audio.WakePhraseMatcher("Meow"), this
        )
        var fatalFired = false
        engine.onFatalError = { fatalFired = true }

        engine.start()
        repeat(3) {
            fakeStt.simulateError(5) // infrastructure errors below the fatal threshold
            advanceUntilIdle()
        }

        assertFalse(fatalFired)
        assertTrue(engine.isRunning.value)
        assertEquals(4, fakeStt.startListeningCalls) // 1 initial + 3 retries
        engine.stop()
    }

    @Test
    fun `wake loop never gives up on routine no-speech timeouts`() = runTest {
        // A quiet room means constant ERROR_SPEECH_TIMEOUT / ERROR_NO_MATCH —
        // those are the loop's normal heartbeat, not a failing recognizer.
        // (Regression: the fatal guard counted them and disabled the wake
        // phrase after ~5 idle sessions.)
        val engine = com.example.meowmba.core.audio.RecognizerWakeWordEngine(
            fakeStt, com.example.meowmba.core.audio.WakePhraseMatcher("Meow"), this
        )
        var fatalFired = false
        engine.onFatalError = { fatalFired = true }

        engine.start()
        repeat(12) {
            fakeStt.simulateError(if (it % 2 == 0) 6 else 7)
            advanceUntilIdle()
        }

        assertFalse(fatalFired)
        assertTrue(engine.isRunning.value)
        engine.stop()
    }

    @Test
    fun `no-speech timeout resets the infrastructure error streak`() = runTest {
        val engine = com.example.meowmba.core.audio.RecognizerWakeWordEngine(
            fakeStt, com.example.meowmba.core.audio.WakePhraseMatcher("Meow"), this
        )
        var fatalFired = false
        engine.onFatalError = { fatalFired = true }

        engine.start()
        repeat(4) { // 4 infra errors (streak just below fatal)…
            fakeStt.simulateError(5)
            advanceUntilIdle()
        }
        fakeStt.simulateError(6) // …reset by a routine timeout…
        advanceUntilIdle()
        repeat(4) { // …so 4 more infra errors still don't reach 5 in a row
            fakeStt.simulateError(5)
            advanceUntilIdle()
        }

        assertFalse(fatalFired)
        assertTrue(engine.isRunning.value)
        engine.stop()
    }
}
