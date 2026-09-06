package com.example.meowmba

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.audio.*
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalVoiceDeviceTest {
    @Test fun localPlaybackAndStop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val error = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val speech = LocalNeuralSpeech(context, { started.countDown() }, { finished.countDown() }, { error.set(it) })
        try {
            speech.enqueue("Hello. These voices run locally on your phone.", true, LocalVoices.find("af_heart"))
            assertTrue("Playback did not start: ${error.get()}", started.await(45, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("Playback did not finish: ${error.get()}", finished.await(30, java.util.concurrent.TimeUnit.SECONDS))
            assertNull(error.get())
            assertFalse(speech.isSpeaking())
        } finally { speech.shutdown() }
        val nextStarted = java.util.concurrent.CountDownLatch(1)
        val cancelSpeech = LocalNeuralSpeech(context, { nextStarted.countDown() }, {}, { error.set(it) })
        try {
            cancelSpeech.enqueue("This longer sentence tests whether stopping speech interrupts playback promptly and keeps the conversation responsive.", true)
            assertTrue(nextStarted.await(45, java.util.concurrent.TimeUnit.SECONDS))
            val before = android.os.SystemClock.elapsedRealtime()
            cancelSpeech.stop()
            assertFalse(cancelSpeech.isSpeaking())
            val latency = android.os.SystemClock.elapsedRealtime() - before
            assertTrue("Stop took $latency ms", latency < 500)
            Thread.sleep(1000)
            assertNull(error.get())
            println("VOICE_PLAYBACK completed=true stop_return_ms=$latency")
        } finally { cancelSpeech.shutdown() }
    }

    @Test fun installAndGenerateFourOfflineVoices() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalVoicePack(context)
        var lastProgress = -1
        store.install { progress, label ->
            val percent = (progress * 100).toInt()
            if (percent / 10 != lastProgress / 10) { println("VOICE_DOWNLOAD $percent $label"); lastProgress = percent }
        }.getOrThrow()
        assertTrue(store.ready())
        val dir = File(context.filesDir, "media/local_voice_previews").apply { mkdirs() }
        val signatures = mutableSetOf<Int>()
        for (voice in LocalVoices.all) {
            val start = android.os.SystemClock.elapsedRealtime()
            var firstAudio = -1L
            val audio = LocalVoiceRuntime.generate(context,
                "Hi, I'm ${voice.name}. I'm here to help. What would you like to do next?", voice) {
                if (firstAudio < 0) firstAudio = android.os.SystemClock.elapsedRealtime() - start
                1
            }
            assertEquals(24000, audio.sampleRate)
            assertTrue(audio.samples.size > 24000)
            assertTrue(audio.samples.any { kotlin.math.abs(it) > 0.01f })
            assertTrue(audio.save(File(dir, "${voice.id}.wav").path))
            signatures.add(audio.samples.contentHashCode())
            println("VOICE_METRIC ${voice.id} first_audio_ms=$firstAudio total_ms=${android.os.SystemClock.elapsedRealtime()-start} duration_ms=${audio.samples.size*1000L/audio.sampleRate}")
        }
        assertEquals(4, signatures.size)
        LocalVoices.select(context, "af_heart")
        assertEquals("af_heart", LocalVoices.selected(context).id)
        assertTrue(LocalVoices.enabled(context))
        // Instrumentation terminates the process immediately; await apply()'s disk write.
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs/local_neural_voice.xml")
        val deadline = System.currentTimeMillis() + 5000
        while ((!prefs.isFile || !prefs.readText().contains("af_heart")) && System.currentTimeMillis() < deadline)
            Thread.sleep(50)
        assertTrue("Voice selection was not saved to disk", prefs.isFile && prefs.readText().contains("af_heart"))
    }

    @Test fun launchRepresentativeInstalledApps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            Thread.sleep(3000)
            for ((name, pkg) in listOf("Chrome" to "com.android.chrome", "Calculator" to "com.sec.android.app.popupcalculator", "My Files" to "com.sec.android.app.myfiles")) {
                scenario.onActivity { ViewModelProvider(it)[CompanionViewModel::class.java].onUserTextSubmitted("open $name") }
                var opened = false
                val deadline = System.currentTimeMillis() + 15000
                while (!opened && System.currentTimeMillis() < deadline) {
                    val fd = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand("dumpsys activity activities")
                    val lines = ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readLines() }
                    opened = lines.any { it.contains("topResumedActivity=") && it.contains("$pkg/") }
                    if (!opened) Thread.sleep(200)
                }
                assertTrue("$name was not opened", opened)
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(500)
            }
        }
    }
}
