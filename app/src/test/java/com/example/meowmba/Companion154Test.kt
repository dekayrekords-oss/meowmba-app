package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.media.SongRecognition
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
class Companion154Test {
    @Test fun everyCharacterHasTwoDistinctPropsAndNeutralRecovery() {
        val scenes = LumenSignatureScenes.scenes
        assertEquals(32, scenes.size)
        assertEquals(32, scenes.map { it.prop }.toSet().size)
        assertEquals(32, scenes.map { it.performance.spec.frames }.toSet().size)
        assertEquals(AvatarAnimal.entries.toSet(), LumenPersonalities.profiles.keys)
        for (animal in AvatarAnimal.entries) {
            val profile = LumenPersonalities.forAnimal(animal)
            assertEquals(2, profile.signatures.size)
            assertEquals(profile.signatures.toSet(), scenes.filter { it.animal == animal }.map { it.performance.id }.toSet())
        }
        for (scene in scenes) {
            assertSame(scene.performance, AvatarPerformanceCatalog.resolve(scene.performance.id))
            val frames = scene.performance.spec.frames
            assertEquals(frames.first().copy(atMs = frames.last().atMs), frames.last())
            assertTrue(frames.zipWithNext().all { (a,b) -> a.atMs < b.atMs })
            assertTrue(scene.performance.spec.durationMs in 4000..7000)
            assertEquals(scene.animal, AvatarAnimal.performanceFor(scene.performance.title))
            for (i in 0..100) {
                val p = i / 100f
                val pose = SignatureTimeline.sample(scene.prop, p)
                assertTrue(pose.point.x in -.4f.. .4f && pose.point.y in -.4f.. .4f)
                val gaze = LumenScenes.gaze(LumenInput(), scene.performance.accent, p, scene.performance.id)
                assertEquals((pose.point.x/.36f).coerceIn(-1f,1f), gaze.sceneGazeX!!, .001f)
            }
        }
    }
    @Test fun idleSelectionUsesTheSavedCharactersSpecialtiesWithoutLosingExistingScenes() {
        val selector = IdlePerformanceSelector(Random(32))
        var time = 0L
        for (animal in listOf(AvatarAnimal.CAT, AvatarAnimal.ROBOT, AvatarAnimal.WIZARD)) {
            val seen = mutableSetOf<String>()
            repeat(24) {
                val entry = selector.pick(time, animal = animal)!!
                LumenSignatureScenes.find(entry.id)?.let { assertEquals(animal, it.animal); seen += entry.id }
                time += entry.spec.durationMs + IdlePerformanceSelector.SCENE_RECOVERY_MS
            }
            assertEquals(LumenPersonalities.forAnimal(animal).signatures.toSet(), seen)
        }
    }
    @Test fun songApiParsesMatchesNoMatchesAndErrorsWithoutInventingTitles() {
        assertEquals("Example by Artist", SongRecognition.parse("""{"status":"success","result":{"title":"Example","artist":"Artist"}}""")!!.label)
        assertNull(SongRecognition.parse("""{"status":"success","result":null}"""))
        assertNull(SongRecognition.parse("""{"status":"success","result":{"title":""}}"""))
        val failure = runCatching { SongRecognition.parse("""{"status":"error","error":{"error_message":"private_token"}}""") }.exceptionOrNull()
        assertNotNull(failure); assertFalse(failure!!.message!!.contains("private_token"))
    }
    @Test fun sampleIsCorrectMonoPcmWavAndCommandsAreWholeUtterances() {
        val data = SongRecognition.wav(byteArrayOf(1,2,3,4))
        assertEquals(48, data.size)
        val header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(40, header.getInt(4)); assertEquals(16_000, header.getInt(24)); assertEquals(4,header.getInt(40))
        assertEquals("RIFF", String(data,0,4)); assertEquals("WAVE",String(data,8,4))
        listOf("what song can you hear?", "identify this song", "what's playing", "listen to this music").forEach { assertTrue(it, SongRecognition.isRequest(it)) }
        assertFalse(SongRecognition.isRequest("write a story about someone asking what's playing"))
    }
    @Test fun chatMicEnablesDisabledMicAndStartsRatherThanImmediatelySubmitting() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val vm = CompanionViewModel(elapsedRealtimeMs = { testScheduler.currentTime }, speechService = stt, captureEngine = stt, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        try {
            vm.setMicEnabled(false)
            val before = stt.startListeningCalls
            vm.onChatMicClick(); runCurrent()
            assertTrue(vm.uiState.value.isMicEnabled)
            assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
            assertEquals(before + 1, stt.startListeningCalls)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun enablingTheChatMicDuringSpeechStopsTheReplyBeforeListening() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val tts = VoicePipelineTest.FakeSpeechSynthesizer()
        val vm = CompanionViewModel(elapsedRealtimeMs = { testScheduler.currentTime },
            speechService = stt, captureEngine = stt, ttsManager = tts)
        try {
            vm.setMicEnabled(false)
            tts.onSpeechStarted?.invoke()
            assertEquals(InteractionState.SPEAKING, vm.uiState.value.interactionState)
            val stops = tts.stopCalls
            vm.onChatMicClick(); runCurrent()
            assertTrue("The old reply must stop before the new capture", tts.stopCalls > stops)
            assertTrue(vm.uiState.value.isMicEnabled)
            assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun callButtonEnablesVoiceAndHangupEndsCall() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val vm = CompanionViewModel(elapsedRealtimeMs = { testScheduler.currentTime }, speechService = stt, captureEngine = stt, ttsManager = VoicePipelineTest.FakeSpeechSynthesizer())
        try {
            vm.setMicEnabled(false); vm.toggleCallMode()
            assertTrue(vm.uiState.value.callMode); assertTrue(vm.uiState.value.isMicEnabled)
            assertTrue(vm.uiState.value.isChatExpanded)
            vm.toggleCallMode(); assertFalse(vm.uiState.value.callMode)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun songSetupReplacesTheChatPanelAndKeepsSensorsOff() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val vm = CompanionViewModel(elapsedRealtimeMs = { testScheduler.currentTime }, speechService = stt, captureEngine = stt)
        try {
            vm.setMicEnabled(false); vm.setCameraEnabled(false); vm.openChat(); vm.requestSongIdentification()
            vm.openSongRecognitionSettings()
            assertTrue(vm.uiState.value.showAvatarScreen)
            assertFalse(vm.uiState.value.isChatExpanded)
            assertFalse(vm.uiState.value.songRecognitionPrompt)
            assertFalse(vm.uiState.value.isMicEnabled)
            assertFalse(vm.uiState.value.isCameraEnabled)
            assertEquals(0, stt.startListeningCalls)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun songPromptDoesNotEnableOrCaptureTheMicAndClosingReleasesIt() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val vm = CompanionViewModel(elapsedRealtimeMs = { testScheduler.currentTime }, speechService = stt, captureEngine = stt)
        try {
            vm.setMicEnabled(false); vm.requestSongIdentification()
            assertTrue(vm.uiState.value.songRecognitionPrompt)
            assertFalse(vm.uiState.value.songRecognitionBusy)
            assertFalse(vm.uiState.value.isMicEnabled)
            vm.identifyNearbySong() // No key; must not silently record or make a cloud request.
            assertFalse(vm.uiState.value.songRecognitionBusy)
            assertEquals(0, stt.startListeningCalls)
            vm.cancelSongIdentification(); assertFalse(vm.uiState.value.songRecognitionPrompt)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
