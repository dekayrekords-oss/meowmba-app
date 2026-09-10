package com.example.meowmba

import android.speech.SpeechRecognizer
import com.example.meowmba.core.audio.SpeechEngine
import com.example.meowmba.core.audio.UtteranceCapture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UtteranceRecoveryTest {
    private class Engine : SpeechEngine {
        override val isListening = MutableStateFlow(false)
        override val partialResults = MutableStateFlow("")
        val results = mutableListOf<(String) -> Unit>()
        val errors = mutableListOf<(Int) -> Unit>()
        var startFailure: Exception? = null
        var submitFailure: Exception? = null
        var stopFailure: Exception? = null
        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            startFailure?.let { throw it }
            results += onResult; errors += onError
        }
        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) = startListening(onResult, onError)
        override fun finishInput(): Boolean { submitFailure?.let { throw it }; return true }
        override fun stopListening() { stopFailure?.let { throw it } }
        override fun destroy() = Unit
    }
    @Test fun permissionRevocationDuringStartupStillEndsTheTurnIfTeardownThrows() = runTest {
        val engine = Engine().apply {
            startFailure = SecurityException("permission revoked")
            stopFailure = IllegalStateException("recorder already gone")
        }
        var error: Int? = null
        val capture = UtteranceCapture(engine, this)
        capture.start(onFinal = { fail("No audio was recorded") }, onError = { error = it })
        assertEquals(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, error)
        assertFalse(capture.isCapturing)
    }
    @Test fun submitFailureReleasesTheTurnInsteadOfLeavingFinalizationActive() = runTest {
        val engine = Engine().apply { submitFailure = IllegalStateException("decoder gone") }
        var error: Int? = null
        val capture = UtteranceCapture(engine, this)
        capture.start(onFinal = { fail("Must not execute a draft") }, onError = { error = it })
        capture.finishNow()
        assertEquals(SpeechRecognizer.ERROR_AUDIO, error)
        assertFalse(capture.isCapturing)
    }
    @Test fun priorSessionCallbacksCannotAddWordsOrStopItsReplacement() = runTest {
        val engine = Engine()
        var time = 1L
        var final: String? = null
        var error: Int? = null
        val capture = UtteranceCapture(engine, this, now = { time })
        capture.start(onFinal = { final = it }, onError = { error = it })
        val oldResult = engine.results.single()
        val oldError = engine.errors.single()
        time = 100; oldResult("please open")
        assertEquals(2, engine.results.size)
        oldResult("an unwanted old command")
        oldError(SpeechRecognizer.ERROR_AUDIO)
        assertNull(error)
        assertNull(final)
        time = 1_000; engine.results.last().invoke("the calendar")
        time = 4_000; engine.errors.last().invoke(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals("please open the calendar", final)
        assertNull(error)
    }
}
