package com.example.meowmba

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.access.MeowAccessibilityService
import com.example.meowmba.core.audio.AsrBackend
import com.example.meowmba.core.audio.VoiceModelStore
import com.example.meowmba.core.audio.WhisperAsrEngine
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Explicitly invoked connected-device tests; no inbox content is inspected. */
class ConnectedRepairSmokeTest {
    @Test fun chatRequestActuallyOpensGmail() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            // Let the real activity finish creating its tool registry.
            Thread.sleep(3000)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[CompanionViewModel::class.java].onUserTextSubmitted("open gmail")
            }
            val deadline = System.currentTimeMillis() + 20000
            // Instrumentation restarts the target process, which disconnects
            // its AccessibilityService. Observe only Android's foreground
            // activity record, not any Gmail screen content.
            fun gmailForeground(): Boolean {
                val fd = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                val lines = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readLines() }
                return lines.any { it.contains("topResumedActivity=") && it.contains("com.google.android.gm/") }
            }
            var opened = gmailForeground()
            while (!opened && System.currentTimeMillis() < deadline) {
                Thread.sleep(250)
                opened = gmailForeground()
            }
            assertTrue("Gmail did not become the foreground activity", opened)
            // Return immediately. Do not call readScreen or inspect mail nodes.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @Test fun whisperDownloadAndSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            Thread.sleep(3000)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[CompanionViewModel::class.java].downloadWhisperModel()
            }
            val store = VoiceModelStore(context)
            val deadline = System.currentTimeMillis() + 300000
            while (System.currentTimeMillis() < deadline && !store.whisperReady()) Thread.sleep(1000)
            assertTrue("Whisper download did not complete", store.whisperReady())
            assertTrue("Streaming ASR pack missing", store.asrReady())
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[CompanionViewModel::class.java]
                vm.setAsrBackend(AsrBackend.WHISPER_LOCAL)
                assertEquals(AsrBackend.WHISPER_LOCAL, vm.uiState.value.asrBackend)
            }
            // Exercise real native initialization/decoding with silence, not user audio.
            val engine = WhisperAsrEngine(context)
            try { assertNotNull(runBlocking { engine.transcribe(ShortArray(16000)) }) }
            finally { engine.release() }
        }
    }
}
