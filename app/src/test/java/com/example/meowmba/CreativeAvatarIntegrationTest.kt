package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreativeAvatarIntegrationTest {
    @Test fun animalCommandsReachSavedFormsAndSupportReset() {
        for ((phrase, key) in mapOf("become an Australian shepherd" to "australian_shepherd",
            "be an Aussie" to "australian_shepherd", "become an Australian shepherd dog" to "australian_shepherd",
            "be an owl" to "owl", "turn into a duck" to "duck")) {
            val intent = AgentRouter.route(phrase)
            assertEquals(phrase, "avatar.form", intent?.toolId)
            assertEquals(key, AvatarAnimal.resolve(intent!!.args.getValue("animal"))?.key)
            assertTrue(phrase, CommandHeuristics.mightBeCommand(phrase))
        }
        for (phrase in listOf("stop being an owl", "stop being a duck", "stop being an Australian shepherd")) {
            assertEquals(phrase, "none", AgentRouter.route(phrase)?.args?.get("animal"))
        }
    }

    @Test fun storyAndHatRequestsUseAuthoredScenesWithoutCapturingFileRequests() {
        for ((phrase, id) in mapOf("chase a mouse" to "mouse_chase", "could you read a book?" to "book_reading",
            "wear a hat" to "cowboy_hat", "put on a cowboy hat" to "cowboy_hat",
            "wear a wizard hat" to "wizard_hat", "wear a beret" to "painter_beret",
            "follow a mouse" to "mouse_chase", "open a book" to "book_reading",
            "turn a page" to "book_reading", "stargaze in a wizard hat" to "wizard_hat",
            "pose like a painter" to "painter_beret")) {
            val intent = AgentRouter.route(phrase)
            assertEquals(phrase, "avatar.animate", intent?.toolId)
            assertEquals(phrase, id, AvatarPerformanceCatalog.resolve(intent!!.args.getValue("name"))?.id)
            assertTrue(phrase, CommandHeuristics.mightBeCommand(phrase))
            assertFalse(phrase, CompanionViewModel.looksLikeDirectQuestion(phrase))
        }
        for (phrase in listOf("read my book.pdf", "read a book and email me a summary", "don't chase a mouse",
            "make a video of you reading a book", "I saw an owl wearing a hat", "wear a wizard hat and open Gmail")) {
            assertNull(phrase, AvatarSceneRequest.parse(phrase))
        }
    }

    @Test fun mousePursuitMovesTheWholeAvatarAndReleasesItsTravelTarget() {
        val scene = AvatarPerformanceCatalog.resolve("mouse_chase")!!
        val rig = LumenRig()
        val positions = (0..360).map { i ->
            val input = lumenPerformanceInput(LumenInput(performing = true,
                animal = AvatarAnimal.fromKey("australian_shepherd")), scene, i / 360f)
            rig.step(i / 60f, input).driftX
        }
        assertTrue("The character must pursue, not only move its pupils", positions.max() - positions.min() > .10f)
        assertEquals(0f, lumenPerformanceInput(LumenInput(performing = true), scene, 1f).sceneGazeWeight, 0f)
        val book = AvatarPerformanceCatalog.resolve("book_reading")!!
        assertEquals(0f, lumenPerformanceInput(LumenInput(performing = true), book, .5f).sceneTravelWeight, 0f)
    }

    @Test fun storiesKeepTheSelectedFormAndReleasePropsOnCompletionOrInterruption() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CompanionViewModel(ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        try {
            for (key in listOf("australian_shepherd", "owl", "duck")) {
                val form = AvatarAnimal.fromKey(key)
                vm.transformAvatar(form)
                for (id in listOf("mouse_chase", "book_reading", "cowboy_hat", "wizard_hat", "painter_beret")) {
                    val scene = AvatarPerformanceCatalog.resolve(id)!!
                    vm.playAnimationNamed(scene.title)
                    assertEquals(scene.title, vm.uiState.value.activeAnimation?.name)
                    assertEquals(form, vm.uiState.value.avatarAnimal)
                    assertNull(vm.uiState.value.performanceAnimal)
                    advanceTimeBy(scene.spec.durationMs.toLong() + 301); runCurrent()
                    assertNull(vm.uiState.value.activeAnimation)
                    assertEquals(form, vm.uiState.value.avatarAnimal)
                    vm.playAnimationNamed(scene.title)
                    vm.transformAvatar(form)
                    assertNull(vm.uiState.value.activeAnimation)
                }
            }
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
