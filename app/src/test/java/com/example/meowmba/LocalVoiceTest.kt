package com.example.meowmba

import com.example.meowmba.core.audio.*
import com.example.meowmba.core.agent.tools.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalVoiceTest {
    @Test fun `native speech callback exposes the exact JNI method`() {
        val callback = com.k2fsa.sherpa.onnx.NativeTtsCallback { 1 }
        val method = callback.javaClass.getDeclaredMethod("invoke", FloatArray::class.java)
        assertEquals(java.lang.Integer::class.java, method.returnType)
        assertEquals(1, method.invoke(callback, floatArrayOf(0.5f)))
    }
    @Test fun `callback failure cancels before rethrowing outside JNI`() {
        val callback = com.k2fsa.sherpa.onnx.NativeTtsCallback { error("Playback stopped") }
        assertEquals(0, callback(floatArrayOf()))
        assertThrows(IllegalStateException::class.java) { callback.rethrowFailure() }
    }
    @Test fun `four real speaker identities are two female and two male`() {
        assertEquals(4, LocalVoices.all.size)
        assertEquals(2, LocalVoices.all.count { it.gender == "Female" })
        assertEquals(2, LocalVoices.all.count { it.gender == "Male" })
        assertEquals(setOf(2, 3, 14, 16), LocalVoices.all.map { it.speaker }.toSet())
        assertEquals("af_heart", LocalVoices.find("missing").id)
    }
    @Test fun `voice pack cannot extract outside staging`() {
        val root = File("/tmp/voice-unit-test")
        for (bad in listOf("../secret", "/etc/passwd", "kokoro/../../secret")) {
            assertThrows(IllegalArgumentException::class.java) { LocalVoicePack.safeEntry(root, bad) }
        }
        assertEquals(File(root, "kokoro/model.onnx").canonicalFile, LocalVoicePack.safeEntry(root, "kokoro/model.onnx"))
    }
    @Test fun `failed app actions stay failures`() {
        for (message in listOf("I couldn't find Gmail on screen.", "No active window.", "Global action back failed.", "Found field but couldn't type into it."))
            assertTrue(UiAutomationTool.actionResult(message) is ToolResult.Failure)
        assertTrue(UiAutomationTool.actionResult("Tapped Settings.") is ToolResult.Success)
    }
    @Test fun `launcher matches apps outside a curated list`() {
        val apps = listOf(InstalledApps.Entry("Research Notebook", "org.example.research"),
            InstalledApps.Entry("My Files", "com.vendor.files"))
        assertEquals("org.example.research", InstalledApps.match(apps, "Research Notebook")?.packageName)
        assertEquals("com.vendor.files", InstalledApps.match(apps, "the My Files app")?.packageName)
    }
}
