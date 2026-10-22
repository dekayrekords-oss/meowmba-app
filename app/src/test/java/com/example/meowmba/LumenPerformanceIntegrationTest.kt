package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.tools.AvatarAnimateTool
import com.example.meowmba.core.agent.tools.DescribeAnimationTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LumenPerformanceIntegrationTest {
    @Test fun everyCatalogTitleRoutesAndActuallyPlaysOnTheFace() = runTest {
        var played: CustomAnimationSpec? = null
        val tool = AvatarAnimateTool(null) { played = it }
        AvatarPerformanceCatalog.entries.forEach { performance ->
            val intent = AgentRouter.route("play the ${performance.title} animation")!!
            assertEquals("avatar.animate", intent.toolId)
            assertTrue(tool.execute(intent.args) is ToolResult.Success)
            assertEquals(performance.spec, played)
        }
    }

    @Test fun varietyRequestsDoNotRepeatTheLastThreePerformances() = runTest {
        val played = mutableListOf<String>()
        val tool = AvatarAnimateTool(null) { played += it.name }
        repeat(20) {
            val intent = AgentRouter.route(if (it == 0) "do a trick" else "show me another trick")!!
            assertTrue(tool.execute(intent.args) is ToolResult.Success)
        }
        played.windowed(4).forEach { assertEquals(4, it.distinct().size) }
    }

    @Test fun descriptionsResolveOnlyWholeAliasesBeforeLegacyComposition() = runTest {
        var played: CustomAnimationSpec? = null
        val tool = DescribeAnimationTool { played = it }
        assertTrue(tool.execute(mapOf("description" to "HEART_FLUTTER")) is ToolResult.Success)
        assertEquals("Heart Flutter", played?.name)
        assertNull(AvatarPerformanceCatalog.resolve("my cat says hello to snow"))
        for (phrase in listOf("just chilling with the cats", "do not do a trick", "it is snowing outside", "I feel sad today")) {
            assertNotEquals(phrase, "avatar.animate", AgentRouter.route(phrase)?.toolId)
        }
    }

    @Test fun selectionAndSearchExposeBothNewAndLegacyPerformances() {
        val names = AvatarPerformanceCatalog.names() + "paw lick"
        assertEquals(87, avatarAnimationChoices(names, "New", "").size)
        assertEquals(48, avatarAnimationChoices(names, "Scenes", "").size)
        assertEquals(listOf("Butterfly Chase"), avatarAnimationChoices(names, "New", "butterfly"))
        assertEquals(listOf("paw lick"), avatarAnimationChoices(names, "More", ""))
        assertEquals(listOf("Playful Wink"), avatarAnimationChoices(names, "More", "wink"))
        assertTrue(avatarAnimationChoices(names, "Weather", "").all { AvatarPerformanceCatalog.resolve(it)?.category == "Weather" })
        assertTrue(avatarAnimationChoices(names, "New", "no matching animation").isEmpty())
        assertEquals(AvatarStyle.LUMEN_CAT, AvatarStyle.fromKey("lumen_cat"))
        assertEquals(AvatarStyle.LUMEN, AvatarStyle.DEFAULT)
    }

    @Test fun envelopeWinkAndHideAreBoundedAndLeaveNoTemporaryChannels() {
        val input = LumenInput(openness = 0.5f, animal = AvatarAnimal.CAT)
        val wink = AvatarPerformanceCatalog.resolve("playful_wink")!!
        val hide = AvatarPerformanceCatalog.resolve("little_hide")!!
        assertEquals(1f, lumenPerformanceInput(input, wink, 0.4f).winkLeft, 0f)
        assertEquals(1f, lumenPerformanceInput(input, wink, 0.4f).openness, 0f)
        assertTrue(lumenPerformanceInput(input, hide, 0.4f).presence < 0.04f)
        for (t in listOf(-1f, 0f, 1f, 1.5f, Float.NaN)) {
            assertEquals(0f, lumenPerformanceEnvelope(t), 0f)
            assertEquals(0f, lumenPerformanceInput(input, wink, t).winkLeft, 0f)
            assertEquals(1f, lumenPerformanceInput(input, hide, t).presence, 0f)
        }
        assertEquals(AvatarAnimal.CAT, lumenPerformanceInput(input, wink, 0.4f).animal)
    }

    @Test fun customSpecsWithCatalogNamesDoNotReceiveHiddenPropChannels() {
        for (name in listOf("wink", "hide", "Playful Wink", "Heart Flutter")) {
            val custom = CustomAnimationSpec(name, listOf(AnimFrame(0, eyeOpen = 1f), AnimFrame(1200, eyeOpen = 1f)))
            assertNull(AvatarPerformanceCatalog.fromSpec(custom))
        }
        AvatarPerformanceCatalog.entries.forEach { assertEquals(it, AvatarPerformanceCatalog.fromSpec(it.spec)) }
    }

    @Test fun savedAliasesStillReplayTheUsersOwnAnimation() = runTest {
        val directory = kotlin.io.path.createTempDirectory("lumen-saved-alias-").toFile()
        val store = CustomAnimationStore(directory)
        val custom = CustomAnimationSpec("hide", listOf(AnimFrame(0, eyeOpen = 1f), AnimFrame(1800, headTiltDeg = 4f)))
        store.save(custom)
        var played: CustomAnimationSpec? = null
        val tool = AvatarAnimateTool(store) { played = it }
        assertTrue(tool.execute(mapOf("name" to "hide")) is ToolResult.Success)
        assertEquals(custom, played)
        assertNull(AvatarPerformanceCatalog.fromSpec(played))
    }

    @Test fun oneEyeWinksWhileTheOtherKeepsItsDepthAndBothRecover() {
        val rig = LumenRig()
        val wink = AvatarPerformanceCatalog.resolve("Playful Wink")!!
        val input = LumenInput(0f, 0f, true, animal = AvatarAnimal.CAT, performing = true)
        rig.step(0f, input)
        var frame = LumenFrame()
        repeat(60) { frame = rig.step((it + 1f) / 60f, lumenPerformanceInput(input, wink, 0.4f)) }
        assertTrue(frame.leftOpen < 0.01f)
        assertTrue(frame.rightOpen > 0.99f)
        assertEquals(0.5f, frame.pupil, 0.001f)
        repeat(60) { frame = rig.step(1f + (it + 1f) / 60f, input.copy(performing = false)) }
        val rest = LumenActingPoses.forEmotion(CompanionEmotion.IDLE)
        assertEquals(rest.openLeft, frame.leftOpen, .005f)
        assertEquals(rest.openRight, frame.rightOpen, .005f)
    }

    private suspend fun TestScope.withVm(block: suspend TestScope.(CompanionViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val old = System.getProperty("meowmba.idleAnimations")
        System.setProperty("meowmba.idleAnimations", "false")
        val vm = CompanionViewModel(ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        try { block(vm) } finally {
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            Dispatchers.resetMain()
            if (old == null) System.clearProperty("meowmba.idleAnimations") else System.setProperty("meowmba.idleAnimations", old)
        }
    }

    @Test fun performanceReturnsToUserColorsFormAndThemeWithoutChangingPermissions() = runTest { withVm { vm ->
        vm.setAvatarStyle(AvatarStyle.LUMEN_CAT)
        vm.setAvatarEyeColor(0xFFA371F7L); vm.setAvatarGlowColor(0xFF39D0D8L); vm.setAvatarFeatureColor(0xFFFFA6C9L)
        vm.setAppearanceMode(AppearanceMode.LIGHT)
        vm.toggleAvatarScreen()
        vm.playAnimationNamed("Heart Flutter")
        assertEquals("Heart Flutter", vm.uiState.value.activeAnimation?.name)
        assertFalse(vm.uiState.value.showAvatarScreen)
        assertFalse(vm.uiState.value.showBrowser)
        advanceTimeBy(4500); runCurrent()
        val state = vm.uiState.value
        assertNull(state.activeAnimation); assertNull(state.performanceAnimal)
        assertEquals(AvatarStyle.LUMEN_CAT, state.avatarStyle)
        assertEquals(0xFFA371F7L, state.avatarEyeColor); assertEquals(0xFF39D0D8L, state.avatarGlowColor)
        assertEquals(0xFFFFA6C9L, state.avatarFeatureColor); assertEquals(AppearanceMode.LIGHT, state.appearanceMode)
        assertFalse(state.isCameraEnabled)
    } }

    @Test fun ambientPerformanceYieldsToUserAndCannotCloseTheirPanel() = runTest { withVm { vm ->
        vm.setAvatarStyle(AvatarStyle.LUMEN_CAT)
        vm.playAnimationNamed("Listening Lean", userRequested = false)
        assertTrue(vm.uiState.value.ambientPerformance)
        vm.toggleAvatarScreen(); runCurrent()
        assertTrue(vm.uiState.value.showAvatarScreen)
        assertNull(vm.uiState.value.activeAnimation)
        vm.playAnimationNamed("Thoughtful Pause", userRequested = false)
        assertNull(vm.uiState.value.activeAnimation)
        vm.toggleAvatarScreen()
        vm.playAnimationNamed("Listening Lean", userRequested = false)
        vm.onMicClick(); runCurrent()
        assertNull(vm.uiState.value.activeAnimation)
        assertFalse(vm.uiState.value.ambientPerformance)
    } }

    @Test fun replacingAndStoppingAClipCannotLeaveCostumesOrEraseNextClipEarly() = runTest { withVm { vm ->
        vm.playAnimationNamed("dog")
        assertEquals(AvatarAnimal.DOG, vm.uiState.value.performanceAnimal)
        advanceTimeBy(1000)
        vm.playAnimationNamed("Sloth Stretch")
        assertNull(vm.uiState.value.performanceAnimal)
        advanceTimeBy(4500); runCurrent()
        assertEquals("Sloth Stretch", vm.uiState.value.activeAnimation?.name)
        vm.stopAvatarPerformance()
        assertNull(vm.uiState.value.activeAnimation)
        assertNull(vm.uiState.value.activeComposed)
    } }

    @Test fun queuedAmbientYieldCannotCancelANewerExplicitPerformance() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CompanionViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        try {
            runCurrent()
            vm.playAnimationNamed("Listening Lean", userRequested = false)
            runCurrent()
            vm.toggleAvatarScreen()
            vm.playAnimationNamed("Heart Flutter", userRequested = true)
            runCurrent()
            assertEquals("Heart Flutter", vm.uiState.value.activeAnimation?.name)
            assertFalse(vm.uiState.value.ambientPerformance)
            assertFalse(vm.uiState.value.showAvatarScreen)
        } finally {
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }
}
