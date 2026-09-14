package com.example.meowmba

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.runtime.CommandHeuristics
import com.example.meowmba.core.agent.tools.AvatarFormTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class OfficeHelpersTest {
    private val helpers = listOf(AvatarAnimal.CLIPPY, AvatarAnimal.WIZARD)

    @Test fun explicitHelperAliasesUseTheExistingLocalAppearanceTool() = runTest {
        val aliases = mapOf("Clippy" to AvatarAnimal.CLIPPY, "Clippit" to AvatarAnimal.CLIPPY,
            "paperclip" to AvatarAnimal.CLIPPY, "the wizard" to AvatarAnimal.WIZARD,
            "wizard" to AvatarAnimal.WIZARD, "Merlin" to AvatarAnimal.WIZARD)
        for ((alias, expected) in aliases) {
            assertEquals(alias, expected, AvatarAnimal.resolve(alias))
            val phrase = "Change your avatar to $alias"
            val route = AgentRouter.route(phrase)
            assertEquals(phrase, "avatar.form", route?.toolId)
            assertTrue(phrase, CommandHeuristics.mightBeCommand(phrase))
            var applied: AvatarAnimal? = null
            var calls = 0
            val result = AvatarFormTool { calls++; applied = it }.execute(requireNotNull(route).args)
            assertTrue(alias, result is ToolResult.Success)
            assertEquals(expected, applied)
            assertEquals(1, calls)
        }
        for (phrase in listOf("Tell me about Clippy", "don't become Merlin",
            "make a picture of Clippy", "my paperclip fell off the desk")) {
            assertNotEquals(phrase, "avatar.form", AgentRouter.route(phrase)?.toolId)
        }
        assertNull(AvatarAnimal.resolve("paperclip and wizard"))
    }

    @Test fun helperSelectionRestoresThroughSavedKeysWithoutResettingOtherPreferences() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val previousIdle = System.getProperty("meowmba.idleAnimations")
        val models = mutableListOf<CompanionViewModel>()
        Dispatchers.setMain(dispatcher)
        System.setProperty("meowmba.idleAnimations", "false")
        val android = ScopedCompanionAndroidMocks { testScheduler.currentTime }
        try {
            val values = mutableMapOf<String, String>()
            val store = mock(SecureStorageManager::class.java)
            `when`(store.context).thenReturn(mock(Context::class.java))
            `when`(store.getPreference(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            doAnswer { values[it.getArgument<String>(0)] = it.getArgument(1); null }
                .`when`(store).savePreference(anyString(), anyString())
            `when`(store.getBooleanPreference(anyString(), anyBoolean())).thenAnswer {
                values[it.getArgument<String>(0)]?.toBooleanStrictOrNull() ?: it.getArgument<Boolean>(1)
            }
            doAnswer { values[it.getArgument<String>(0)] = it.getArgument<Boolean>(1).toString(); null }
                .`when`(store).saveBooleanPreference(anyString(), anyBoolean())
            doAnswer {
                values["avatar_animal"] = it.getArgument(0)
                values["avatar_style"] = "lumen"
                null
            }.`when`(store).saveAvatarForm(anyString())
            fun model() = CompanionViewModel(secureStorage = store,
                ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher)
                .also { models += it }
            val wallpaper = BackgroundSelection(BackgroundKind.IMAGE, "content://test/office-wallpaper", 43)
            val first = model()
            first.setBackground(wallpaper)
            first.setAvatarScale(2.4f)
            first.setAvatarEyeColor(0xFFAD77EEL)
            first.setAvatarFeatureColor(0xFFFFA6C9L)
            for (helper in helpers) {
                first.transformAvatar(helper)
                verify(store).saveAvatarForm(helper.key)
                assertEquals(helper, AvatarAnimal.fromKey(values["avatar_animal"]))
                val restored = model()
                val state = restored.uiState.value
                assertEquals(AvatarStyle.LUMEN, state.avatarStyle)
                assertEquals(helper, state.avatarAnimal)
                assertEquals(wallpaper, state.background)
                assertEquals(2.4f, state.avatarScale, 0f)
                assertEquals(0xFFAD77EEL, state.avatarEyeColor)
                assertEquals(0xFFFFA6C9L, state.avatarFeatureColor)
                assertNull(state.performanceAnimal)
                restored.viewModelScope.cancel()
            }
        } finally {
            models.forEach { it.viewModelScope.cancel() }
            android.close()
            Dispatchers.resetMain()
            if (previousIdle == null) System.clearProperty("meowmba.idleAnimations")
            else System.setProperty("meowmba.idleAnimations", previousIdle)
        }
    }

    @Test fun helperTraitsMorphContinuouslyAndReturnToPlainEyes() {
        val rig = AvatarFormRig()
        var previous = AvatarFormTraits()
        for (helper in helpers + AvatarAnimal.NONE) {
            val target = AvatarFormProfiles.traits(helper)
            repeat(180) {
                val next = rig.step(target, 1f / 60f)
                for ((a, b) in listOf(previous.clippy to next.clippy, previous.wizard to next.wizard)) {
                    assertTrue(b.isFinite() && b in 0f..1f)
                    assertTrue("Appearance cannot pop in one frame", abs(a - b) < .08f)
                }
                previous = next
            }
            assertEquals(target.clippy, previous.clippy, .0001f)
            assertEquals(target.wizard, previous.wizard, .0001f)
            assertEquals(target.costume, previous.costume, .0001f)
            assertEquals("Paused morphs keep their last pose", previous, rig.step(AvatarFormTraits(), 0f))
        }
    }

    @Test fun helperCostumesKeepTheSameGazeSpeechAndBlinkClock() {
        for (helper in helpers) {
            val plain = LumenRig(814)
            val dressed = LumenRig(814)
            var sawBlink = false
            for (index in 0..480) {
                val input = LumenInput(.6f, -.25f, true, animal = helper, speaking = .7f)
                val reference = plain.step(index / 60f, input.copy(animal = AvatarAnimal.NONE))
                val frame = dressed.step(index / 60f, input)
                assertEquals(reference.gazeX, frame.gazeX, 0f)
                assertEquals(reference.gazeY, frame.gazeY, 0f)
                assertEquals(reference.leftOpen, frame.leftOpen, 0f)
                assertEquals(reference.rightOpen, frame.rightOpen, 0f)
                assertEquals(reference.mouthOpen, frame.mouthOpen, 0f)
                sawBlink = sawBlink || frame.leftOpen < .4f
            }
            assertTrue("The shared blink clock remains alive", sawBlink)
        }
    }

    @Test fun helperWireHatAndBeardTouchesBelongToTheAvatarAtEverySize() {
        for ((width, height) in listOf(360 to 800, 840 to 360)) for (helper in helpers) {
            val frame = LumenFrame(form = AvatarFormProfiles.traits(helper))
            val center = lumenCenter(width.toFloat(), height.toFloat(), frame)
            val unit = lumenUnit(width.toFloat(), height.toFloat(), frame)
            val parts = if (helper == AvatarAnimal.CLIPPY) listOf(Offset(-.05f, .42f))
                else listOf(Offset(0f, -.45f), Offset(0f, .41f))
            for (scale in listOf(.5f, 2f, 3f)) {
                val transform = AvatarHitTransform(center, center, scale)
                parts.forEach { part ->
                    val visible = center + part * unit * scale
                    assertNotEquals("$helper body at $scale cannot activate a background toy", AvatarZone.BACKGROUND,
                        lumenZoneAt(transform.inverse(visible), width, height, frame))
                }
            }
            assertEquals(AvatarZone.BACKGROUND,
                lumenZoneAt(center + Offset(.65f, .65f) * unit, width, height, frame))
        }
    }
}
