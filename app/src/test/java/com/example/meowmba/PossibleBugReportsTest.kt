package com.example.meowmba

import com.example.meowmba.core.diagnostics.PossibleBugReports
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import androidx.lifecycle.viewModelScope
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class PossibleBugReportsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun actualUserFailureReportsAreUnverifiedAndHypotheticalsAreNotLogged() {
        for (text in listOf("the traffic updates gives a 404", "it got stuck in a loop making projects",
            "the mic locks me out", "it isn't working", "the app keeps crashing", "there is no response",
            "the app does not work", "it is broken", "the yellow mic button stays on sometimes",
            "it does not hear me", "sometimes I do not see the response in the text chat"))
            assertNotNull(text, PossibleBugReports.extract(text))
        for (text in listOf("everything is working", "create a project", "what if the app crashes?",
            "write a test where the mic is stuck", "say it isn't working", "translate the app is not working"))
            assertNull(text, PossibleBugReports.extract(text))
    }

    @Test fun duplicateReportsSurviveRestartWithContextWithoutCreatingTasks() {
        val root = temp.newFolder()
        val first = PossibleBugReports(root).record("The microphone is stuck", "version=1; source=voice", 100)!!
        val second = PossibleBugReports(root).record("The microphone is stuck", "version=2; source=text", 200)!!
        val saved = PossibleBugReports(root).list().single()
        assertEquals(first.id, second.id); assertEquals(2, saved.occurrences)
        assertEquals(100L, saved.firstSeen); assertEquals(200L, saved.lastSeen)
        assertEquals("possible_bug", saved.status)
        assertEquals("version=2; source=text", saved.context)
        assertEquals(listOf("diagnostics"), root.listFiles()!!.map { it.name })
    }

    @Test fun reportsAreBoundedAndCommonCredentialsAreRedacted() {
        val root = temp.newFolder(); val reports = PossibleBugReports(root)
        val report = reports.record("not working api_key=abcdef password=secret sk-abcdefghijklmnop 1234567890123456", "text")!!
        assertFalse(report.report.contains("secret")); assertFalse(report.report.contains("abcdef"))
        assertFalse(report.report.contains("1234567890123456"))
        repeat(110) { reports.record("screen $it is stuck", "text", it.toLong()) }
        assertEquals(100, reports.list().size)
        assertFalse(File(root, "diagnostics").listFiles()!!.any { it.name.endsWith("pending") })
    }

    @Test fun corruptReportHistoryIsNotSilentlyReplaced() {
        val root = temp.newFolder()
        val file = File(root, "diagnostics/possible-bugs.json").apply { parentFile!!.mkdirs(); writeText("damaged") }
        assertThrows(Exception::class.java) { PossibleBugReports(root).record("mic is stuck", "voice") }
        assertEquals("damaged", file.readText())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun typedFailureWithMicrophoneOffReachesPersistentReportsAndAudit() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val root = temp.newFolder()
        val vm = CompanionViewModel(elapsedRealtimeMs = { System.nanoTime() / 1_000_000L }, workspaceRoot = root, ioDispatcher = dispatcher,
            ttsManager = VoicePipelineTest.FakeSpeechSynthesizer())
        try {
            vm.setMicEnabled(false)
            vm.onUserTextSubmitted("The yellow mic button stays on sometimes")
            runCurrent()
            val saved = PossibleBugReports(root).list().single()
            assertTrue(saved.context.contains("source=text"))
            assertTrue(vm.uiState.value.auditEntries.any { it.action == "possible_bug" })
            assertNull(vm.uiState.value.activeProjectId)
            assertFalse(vm.uiState.value.isMicEnabled)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
