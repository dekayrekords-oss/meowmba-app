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
    fun `mic-unavailable errors are contention, not a broken device`() {
        // Both were observed on a HEALTHY device: BUSY during the service/in-app mic
        // handoff, and INSUFFICIENT_PERMISSIONS because appops grants mic in foreground
        // mode only. Escalating either produced a false "keeps failing on this device".
        listOf(
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        ).forEach { code ->
            assertTrue("code $code should be contention", RecognizerWakeWordEngine.isContention(code))
        }
    }

    @Test
    fun `server disconnected from a properly-bound recognizer is contention, not a broken device`() {
        // Observed on-device (Z Fold 4), bound explicitly to AIAI: short bursts of
        // SERVER_DISCONNECTED right after the loop restarts, fast enough to trip the
        // fatal threshold before a no-match session could reset the count — so
        // re-enabling Wake Word in Settings re-tripped the same breaker instantly.
        // That is AIAI's connection settling, not a device that cannot recognize speech.
        assertTrue(
            RecognizerWakeWordEngine.isContention(SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
        )
    }

    @Test
    fun `contention does not swallow every error`() {
        // CLIENT stays escalating on purpose: SpeechService reports it when the
        // recognizer could not be created at all, so retrying it forever would hide a
        // genuinely unusable device.
        listOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_NO_MATCH,
        ).forEach { code ->
            assertFalse("code $code should not be contention", RecognizerWakeWordEngine.isContention(code))
        }
    }

    @Test
    fun `real infrastructure failures still escalate`() {
        // Something durable is wrong with audio or the service itself.
        listOf(
            SpeechRecognizer.ERROR_CLIENT,
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
