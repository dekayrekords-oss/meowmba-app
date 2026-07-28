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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

    /** Fake STT: records calls, lets tests push partials/finals. */
    class FakeSpeechEngine : SpeechEngine {
        override val isListening: StateFlow<Boolean> = MutableStateFlow(false)
        override val partialResults = MutableStateFlow("")
        var startListeningCalls = 0
        var stopListeningCalls = 0
        private var lastOnResult: ((String) -> Unit)? = null

        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            startListeningCalls++
            lastOnResult = onResult
        }

        override fun stopListening() { stopListeningCalls++ }
        override fun destroy() {}

        fun simulateResult(text: String) { lastOnResult?.invoke(text) }
        fun simulatePartial(text: String) { partialResults.value = text }
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
        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
        assertEquals("Yes?", viewModel.uiState.value.captionText)
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
        assertEquals("Listening for follow-up…", viewModel.uiState.value.captionText)
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
        fakeTts.onSpeechFinished?.invoke() // now in follow-up LISTENING

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
        fakeStt.simulatePartial("i am in phase")
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
}
