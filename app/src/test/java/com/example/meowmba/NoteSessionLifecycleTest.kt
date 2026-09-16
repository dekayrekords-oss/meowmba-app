package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.vault.VaultRepository
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteSessionLifecycleTest {
    private suspend fun TestScope.withNotes(
        block: suspend TestScope.(CompanionViewModel, VaultRepository) -> Unit,
    ) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val dir = kotlin.io.path.createTempDirectory("note-session").toFile()
        val vault = VaultRepository(dir)
        val engine = VoicePipelineTest.FakeSpeechEngine().apply { deliversFinalResults = true }
        val vm = CompanionViewModel(noteVault = vault, captureEngine = engine,
            ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher)
        try { block(vm, vault) } finally {
            vm.viewModelScope.cancel(); dir.deleteRecursively(); Dispatchers.resetMain()
        }
    }
    @Test fun stoppingSavesAnOfflineDigestAndRestoresOriginalSpeechSetting() = runTest { withNotes { vm, vault ->
        vm.onUserTextSubmitted("start taking notes on planning")
        runCurrent()
        assertTrue(vm.uiState.value.isNoting)
        assertTrue(vm.uiState.value.isMuted)
        vm.onUserTextSubmitted("We agreed to finish the draft tomorrow")
        vm.stopNoting(); runCurrent()
        assertFalse(vm.uiState.value.isNoting)
        assertFalse(vm.uiState.value.isMuted)
        val notes = vault.get("Notes/planning")!!
        assertTrue(notes.body.contains("We agreed to finish the draft tomorrow"))
        assertFalse(notes.body.contains(".trim()"))
        val summary = vault.list().single { it.title.startsWith("Summaries/") }
        assertTrue(summary.body.contains("Decisions mentioned"))
        assertEquals(summary.body.trimEnd(), vm.uiState.value.liveAssistantReply?.trimEnd())
    } }
    @Test fun notesRemainDataAfterUnmutingAndNeverBecomeProjectCommands() = runTest { withNotes { vm, vault ->
        vm.onUserTextSubmitted("start taking notes on ideas"); runCurrent()
        vm.setMuted(false)
        vm.onUserTextSubmitted("start a project called secret ideas"); runCurrent()
        assertTrue(vm.uiState.value.isNoting)
        assertNull(vm.uiState.value.activeProjectId)
        assertTrue(vm.uiState.value.activeTasks.isEmpty())
        assertTrue(vault.get("Notes/ideas")!!.body.contains("start a project called secret ideas"))
        vm.stopNoting(); runCurrent()
    } }
    @Test fun backgroundingStopsRecordingAndCreatesSummaryWithoutAutoResume() = runTest { withNotes { vm, vault ->
        vm.onUserTextSubmitted("start taking notes on today"); runCurrent()
        vm.onUserTextSubmitted("Please follow up with the team"); runCurrent()
        vm.releaseWakeMicForBackground(); runCurrent()
        assertFalse(vm.uiState.value.isNoting)
        assertTrue(vault.list().any { it.title.startsWith("Summaries/") })
        vm.reclaimWakeMicForForeground(); runCurrent()
        assertFalse(vm.uiState.value.isNoting)
    } }
    @Test fun typedStartRequestsPermissionAndOnlyStartsAfterGrant() = runTest { withNotes { vm, _ ->
        vm.onMicrophonePermissionResult(false)
        vm.onUserTextSubmitted("start taking notes on meeting"); runCurrent()
        assertTrue(vm.uiState.value.microphonePermissionRequested)
        assertFalse(vm.uiState.value.isNoting)
        vm.onMicrophonePermissionResult(true); runCurrent()
        assertTrue(vm.uiState.value.isNoting)
        vm.stopNoting(); runCurrent()
    } }
    @Test fun previousMuteAndMicrophonePrivacyArePreserved() = runTest { withNotes { vm, _ ->
        vm.setMuted(true)
        vm.onUserTextSubmitted("start notes for Monday"); runCurrent()
        assertTrue(vm.uiState.value.isNoting)
        vm.setMicEnabled(false); runCurrent()
        assertFalse(vm.uiState.value.isNoting)
        assertTrue(vm.uiState.value.isMuted)
        assertFalse(vm.uiState.value.isMicEnabled)
        vm.onUserTextSubmitted("start notes for Tuesday"); runCurrent()
        assertFalse(vm.uiState.value.isNoting)
        assertTrue(vm.uiState.value.captionText.orEmpty().contains("Microphone is off"))
    } }
}
