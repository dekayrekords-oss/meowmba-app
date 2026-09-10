package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarMorphTest {
    @Test fun resetPhrasesRestoreEyesThroughEveryRoutingGate() {
        for (text in listOf("go back to normal", "stop being an animal", "Meow, go back to normal",
            "could you please stop being a cat?", "return to your original animation",
            "please go back to normal", "stop acting like a dog", "eyes only")) {
            val intent = AgentRouter.route(text)
            assertEquals(text, "avatar.form", intent?.toolId)
            assertEquals(text, AvatarAnimal.NONE.key, intent?.args?.get("animal"))
            assertTrue(text, CommandHeuristics.mightBeCommand(text))
            assertFalse(text, CompanionViewModel.looksLikeDirectQuestion(text))
        }
        for (text in listOf("don't go back to normal", "stop being an animal and open Facebook",
            "when will things go back to normal", "stop being rude", "go back to normal volume")) {
            assertNotEquals(text, "avatar.form", AgentRouter.route(text)?.toolId)
        }
    }

    @Test fun explicitPoliteAndAddressedFormsPassEveryRoutingGate() {
        for (text in listOf("become a squirrel", "Meow can you become a raccoon", "please be a dog",
            "I want you to be a cat", "could you turn yourself into a racoon?", "set your form to eyes only")) {
            assertEquals(text, "avatar.form", AgentRouter.route(text)?.toolId)
            assertTrue(text, CommandHeuristics.mightBeCommand(text))
            assertFalse(text, CompanionViewModel.looksLikeDirectQuestion(text))
        }
        assertTrue(AgentRegistry.byId(AgentRegistry.MAIN)!!.allowedTools.contains("avatar.form"))
    }

    @Test fun rolesCasualMentionsAndMediaAreNotHijacked() {
        for (text in listOf("be a ceo", "be a lawyer", "become a doctor", "look like a professional",
            "be happy", "my cat is sleeping", "just chillin with the cats", "don't become a dog",
            "create an image of a raccoon", "make a video of you as a squirrel")) {
            assertNotEquals(text, "avatar.form", AgentRouter.route(text)?.toolId)
        }
        assertEquals("dragon", AgentRouter.route("be a dragon")?.args?.get("animal"))
        assertEquals("pangolin", AgentRouter.route("change your avatar to a pangolin")?.args?.get("animal"))
        assertEquals("avatar.describe_animation", AgentRouter.route("act like a dog")?.toolId)
        for (animal in listOf("squirrel", "raccoon")) {
            val intent = AgentRouter.route("act like a $animal")!!
            assertEquals("avatar.describe_animation", intent.toolId)
            val clip = com.example.meowmba.core.agent.tools.AnimationDescriber.describe(intent.args.getValue("description"))!!
            assertEquals(AvatarAnimal.resolve(animal), AvatarAnimal.performanceFor(clip.name))
        }
    }

    @Test fun allFormsShareTheExactSameGazeSpeechAndBlinkClock() {
        val reference = LumenRig(812); val morph = LumenRig(812)
        for (i in 0..720) {
            val t = i / 60f
            val input = LumenInput(0.55f, -0.23f, true, speaking = 0.62f,
                animal = AvatarAnimal.entries[(i / 90) % AvatarAnimal.entries.size])
            val a = reference.step(t, input.copy(animal = AvatarAnimal.NONE))
            val b = morph.step(t, input)
            assertEquals(a.gazeX, b.gazeX, 0f); assertEquals(a.gazeY, b.gazeY, 0f)
            assertEquals(a.leftOpen, b.leftOpen, 0f); assertEquals(a.rightOpen, b.rightOpen, 0f)
            assertEquals(a.pupil, b.pupil, 0f); assertEquals(a.mouthOpen, b.mouthOpen, 0f)
        }
    }

    @Test fun rapidMidMorphRetargetsAreContinuousAndSettleIntoOneForm() {
        val rig = LumenRig(); var previous = LumenFrame(); var f = previous
        for (i in 0..360) {
            val animal = if (i < 150) AvatarAnimal.entries[(i / 7) % 5] else AvatarAnimal.RACCOON
            f = rig.step(i / 60f, LumenInput(animal = animal))
            assertTrue(abs(f.form.roundEars - previous.form.roundEars) < 0.08f)
            assertTrue(abs(f.form.eyeMask - previous.form.eyeMask) < 0.08f)
            assertTrue(f.form.costume in 0f..1f)
            previous = f
        }
        assertTrue(f.form.eyeMask > 0.999f && f.form.ringTail > 0.999f)
        assertTrue(f.cat < 0.001f && f.dog < 0.001f && f.form.bushyTail < 0.001f)
    }

    @Test fun formAndTailFreezeWithTheExistingActiveClock() {
        val rig = LumenRig()
        val input = LumenInput(animal = AvatarAnimal.SQUIRREL)
        rig.step(0f, input); val before = rig.step(0.5f, input)
        repeat(50) { assertEquals(before, rig.step(0.5f, input.copy(animal = AvatarAnimal.DOG))) }
    }

    @Test fun legacyCatSelectionChangesFormAndSurvivesRecreationWithoutResettingColors() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.context).thenReturn(mock(android.content.Context::class.java))
        val vm = CompanionViewModel(secureStorage = storage, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        try {
            vm.setAvatarStyle(AvatarStyle.LUMEN_CAT)
            vm.setAvatarEyeColor(0xFFAD77EEL); vm.setAvatarFeatureColor(0xFFFFA6C9L)
            vm.setAppearanceMode(AppearanceMode.LIGHT)
            vm.playAnimationNamed("Peekaboo")
            vm.transformAvatar(AvatarAnimal.RACCOON)
            val s = vm.uiState.value
            assertEquals(AvatarStyle.LUMEN, s.avatarStyle); assertEquals(AvatarAnimal.RACCOON, s.avatarAnimal)
            assertNull(s.activeAnimation); assertNull(s.activeComposed); assertNull(s.performanceAnimal)
            assertFalse(s.showBrowser); assertEquals(0xFFAD77EEL, s.avatarEyeColor)
            assertEquals(0xFFFFA6C9L, s.avatarFeatureColor); assertEquals(AppearanceMode.LIGHT, s.appearanceMode)
            verify(storage).saveAvatarForm("raccoon")
            `when`(storage.getPreference("avatar_style")).thenReturn("lumen")
            `when`(storage.getPreference("avatar_animal")).thenReturn("raccoon")
            val restored = CompanionViewModel(secureStorage = storage, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
            try { assertEquals(AvatarAnimal.RACCOON, restored.uiState.value.avatarAnimal)
                assertEquals(AvatarStyle.LUMEN, restored.uiState.value.avatarStyle)
            } finally { restored.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
            vm.playAnimationNamed("dog")
            assertEquals(AvatarAnimal.DOG, vm.uiState.value.performanceAnimal)
            advanceTimeBy(5000); runCurrent()
            assertNull(vm.uiState.value.performanceAnimal)
            assertEquals(AvatarAnimal.RACCOON, vm.uiState.value.avatarAnimal)
            vm.playAnimationNamed("dog")
            vm.transformAvatar(AvatarAnimal.NONE)
            advanceTimeBy(5000); runCurrent()
            assertEquals(AvatarAnimal.NONE, vm.uiState.value.avatarAnimal)
            assertEquals(AvatarStyle.LUMEN, vm.uiState.value.avatarStyle)
            assertNull(vm.uiState.value.performanceAnimal)
            verify(storage).saveAvatarForm("none")
        } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
    }
}
