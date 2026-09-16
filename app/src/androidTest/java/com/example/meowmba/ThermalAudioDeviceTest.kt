package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.audio.SherpaAsrEngine
import com.example.meowmba.core.audio.WhisperAsrEngine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ThermalAudioDeviceTest {
    @Test fun rapidCaptureRestartsDoNotLetOldSessionStopNewSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = SherpaAsrEngine(context, scope)
        val errors = CopyOnWriteArrayList<Int>()
        try {
            assertTrue("Installed streaming model required", engine.isAvailable)
            engine.startListening({}, errors::add)
            delay(2500)
            repeat(8) {
                engine.stopListening()
                engine.startListening({}, errors::add)
                delay(70)
            }
            delay(1200)
            assertTrue("Previous cleanup stopped the current session", engine.isListening.value)
            assertTrue("Capture errors: $errors", errors.isEmpty())
            // Must return null immediately, not wait forever behind capture.
            withTimeout(1000) { assertNull(engine.transcribePcm(ShortArray(1600))) }
            engine.stopAndAwait(2500)
            assertFalse(engine.isListening.value)
        } finally {
            engine.destroy()
            scope.cancel()
        }
    }

    @Test fun installedWhisperStillDecodesLocally() = runBlocking {
        val engine = WhisperAsrEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            assertTrue("Installed Whisper model required", engine.isAvailable())
            assertEquals("", engine.transcribe(ShortArray(16000)))
            // Nonzero synthetic signal exercises native decode, without
            // recording the user's room or claiming this is a spoken test.
            val signal = ShortArray(16000) { (500 * kotlin.math.sin(it * 2.0 * Math.PI * 220 / 16000)).toInt().toShort() }
            assertNotNull(withTimeout(30000) { engine.transcribe(signal) })
        } finally { engine.release() }
    }
}
