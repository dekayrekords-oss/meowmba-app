package com.example.meowmba

import android.speech.SpeechRecognizer
import com.example.meowmba.core.audio.RecognizerWakeWordEngine
import com.example.meowmba.core.audio.WakePhraseMatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wake detection must survive a session that produces no final transcript.
 *
 * The recognizer returns ERROR_NO_MATCH for a whole session fairly often — observed
 * on-device after 7 seconds of detected speech. Matching only the final result meant
 * every wake phrase in such a session was thrown away, which is why the wake word
 * "doesn't always hear you".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WakeWordPartialDetectionTest {

    private fun engine(scope: TestScope, fake: VoicePipelineTest.FakeSpeechEngine) =
        RecognizerWakeWordEngine(fake, WakePhraseMatcher("Meow"), scope)

    @Test
    fun `wakes from a partial transcript`() = runTest(StandardTestDispatcher()) {
        val fake = VoicePipelineTest.FakeSpeechEngine()
        var woken: String? = null
        var wakeCount = 0
        val e = engine(this, fake).apply {
            onWake = { cmd -> wakeCount++; woken = cmd }
            start()
        }
        advanceUntilIdle()

        fake.simulatePartial("hey meow what time is it")
        advanceUntilIdle()

        assertEquals(1, wakeCount)
        assertEquals("what time is it", woken)
        e.stop()
    }

    @Test
    fun `wakes from a partial even when the session then reports NO_MATCH`() =
        runTest(StandardTestDispatcher()) {
            val fake = VoicePipelineTest.FakeSpeechEngine()
            var wakeCount = 0
            val e = engine(this, fake).apply {
                onWake = { wakeCount++ }
                start()
            }
            advanceUntilIdle()

            fake.simulatePartial("hey meow")
            advanceUntilIdle()
            // The real-world case: no final transcript is ever delivered.
            fake.simulateError(SpeechRecognizer.ERROR_NO_MATCH)
            advanceUntilIdle()

            assertEquals("wake must survive a NO_MATCH session", 1, wakeCount)
            e.stop()
        }

    @Test
    fun `growing partials wake only once`() = runTest(StandardTestDispatcher()) {
        val fake = VoicePipelineTest.FakeSpeechEngine()
        var wakeCount = 0
        val e = engine(this, fake).apply {
            onWake = { wakeCount++ }
            start()
        }
        advanceUntilIdle()

        // Partials arrive cumulatively; each update still contains the phrase.
        fake.simulatePartial("hey meow")
        fake.simulatePartial("hey meow what")
        fake.simulatePartial("hey meow what time")
        advanceUntilIdle()

        assertEquals(1, wakeCount)
        e.stop()
    }

    @Test
    fun `final result does not wake twice after a partial already did`() =
        runTest(StandardTestDispatcher()) {
            val fake = VoicePipelineTest.FakeSpeechEngine()
            var wakeCount = 0
            val e = engine(this, fake).apply {
                onWake = { wakeCount++ }
                start()
            }
            advanceUntilIdle()

            fake.simulatePartial("hey meow open the door")
            advanceUntilIdle()
            fake.simulateResult("hey meow open the door")
            advanceUntilIdle()

            assertEquals(1, wakeCount)
            e.stop()
        }

    @Test
    fun `unrelated partials never wake`() = runTest(StandardTestDispatcher()) {
        val fake = VoicePipelineTest.FakeSpeechEngine()
        var woken: String? = null
        var wakeCount = 0
        val e = engine(this, fake).apply {
            onWake = { cmd -> wakeCount++; woken = cmd }
            start()
        }
        advanceUntilIdle()

        fake.simulatePartial("the weather looks fine today")
        fake.simulatePartial("i was talking to someone else")
        advanceUntilIdle()

        assertEquals(0, wakeCount)
        assertNull(woken)
        e.stop()
    }

    @Test
    fun `stopping cancels partial watching`() = runTest(StandardTestDispatcher()) {
        val fake = VoicePipelineTest.FakeSpeechEngine()
        var wakeCount = 0
        val e = engine(this, fake).apply {
            onWake = { wakeCount++ }
            start()
        }
        advanceUntilIdle()
        e.stop()
        advanceUntilIdle()

        fake.simulatePartial("hey meow")
        advanceUntilIdle()

        assertEquals("a stopped engine must not wake", 0, wakeCount)
    }
}
