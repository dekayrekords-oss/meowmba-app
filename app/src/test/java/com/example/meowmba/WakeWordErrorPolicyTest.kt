package com.example.meowmba

import android.speech.SpeechRecognizer
import com.example.meowmba.core.audio.RecognizerWakeWordEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the wake loop classifies recognizer errors, because getting it wrong is
 * user-visible in both directions:
 *
 *  - too strict, and a quiet room or a mic handoff makes the app announce
 *    "the speech recognizer keeps failing on this device" when it is fine
 *  - too lax, and a genuinely broken recognizer hot-loops forever
 */
class WakeWordErrorPolicyTest {

    @Test
    fun `silence is not a failure`() {
        // The loop's normal heartbeat in a quiet room.
        assertFalse(RecognizerWakeWordEngine.isInfrastructureError(SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(
            RecognizerWakeWordEngine.isInfrastructureError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        )
    }

    @Test
    fun `recognizer busy is contention, not a broken device`() {
        // Fires when the foreground service and the in-app loop overlap during the mic
        // handoff. Counting it produced a false "keeps failing" report.
        assertTrue(
            RecognizerWakeWordEngine.isContention(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        )
    }

    @Test
    fun `contention is narrow`() {
        // Only BUSY. A permissions or client error is a real problem, not contention.
        listOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_NO_MATCH,
        ).forEach { code ->
            assertFalse("code $code should not be contention", RecognizerWakeWordEngine.isContention(code))
        }
    }

    @Test
    fun `real infrastructure failures still escalate`() {
        listOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_SERVER,
        ).forEach { code ->
            assertTrue(
                "code $code should count toward the fatal threshold",
                RecognizerWakeWordEngine.isInfrastructureError(code),
            )
        }
    }
}
