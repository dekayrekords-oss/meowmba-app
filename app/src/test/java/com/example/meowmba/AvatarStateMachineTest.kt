package com.example.meowmba

import com.example.meowmba.ui.companion.AvatarZone
import com.example.meowmba.ui.companion.CatAnimationRegistry
import com.example.meowmba.ui.companion.CatAvatarStateMachine
import com.example.meowmba.ui.companion.CompanionEmotion
import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.InteractionState
import com.example.meowmba.ui.companion.MouthMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the avatar animation state machine: the mapping from
 * [CompanionState] (interaction state x emotion x privacy flags) to the
 * declarative [com.example.meowmba.ui.companion.AvatarPose] the renderer draws.
 */
class AvatarStateMachineTest {

    private fun poseOf(state: CompanionState) = CatAvatarStateMachine.resolvePose(state)

    @Test
    fun `idle pose is neutral`() {
        val pose = poseOf(CompanionState())
        assertEquals(1.0f, pose.eyeOpen, 0.001f)
        assertEquals(MouthMode.IDLE, pose.mouth)
        assertFalse(pose.happyEyes)
        assertFalse(pose.micOffMark)
        assertFalse(pose.cameraOffMark)
        assertFalse(pose.glowPulse)
    }

    @Test
    fun `listening perks ears, widens eyes, dilates pupils, pulses halo`() {
        val pose = poseOf(CompanionState(interactionState = InteractionState.LISTENING))
        assertTrue("eyes widen", pose.eyeOpen > 1.0f)
        assertTrue("ears perk", pose.earPerk > 1.0f)
        assertTrue("pupils dilate", pose.pupilSize >= 0.5f)
        assertTrue(pose.glowPulse)
    }

    @Test
    fun `thinking glances upward and narrows eyes`() {
        val pose = poseOf(CompanionState(interactionState = InteractionState.THINKING))
        assertTrue("gaze biased upward", pose.pupilGazeUp > 0f)
        assertTrue("eyes narrow", pose.eyeOpen < 1.0f)
    }

    @Test
    fun `speaking animates mouth and flutters whiskers`() {
        val pose = poseOf(CompanionState(interactionState = InteractionState.SPEAKING))
        assertEquals(MouthMode.SPEAKING, pose.mouth)
        assertTrue(pose.whiskerFlutter)
    }

    @Test
    fun `muting speech shows muted mouth`() {
        val pose = poseOf(CompanionState(isMuted = true))
        assertEquals(MouthMode.MUTED, pose.mouth)
    }

    @Test
    fun `microphone off marks ears and dims the face`() {
        val pose = poseOf(CompanionState(isMicEnabled = false))
        assertTrue(pose.micOffMark)
        assertTrue(pose.dimmed)
    }

    @Test
    fun `camera off marks pupils and nearly closes the eyes`() {
        val pose = poseOf(CompanionState(isCameraEnabled = false))
        assertTrue(pose.cameraOffMark)
        assertTrue("eyes nearly closed", pose.eyeOpen <= 0.3f)
        assertFalse("no happy arcs while camera is off", pose.happyEyes)
    }

    @Test
    fun `happy emotion shows happy arcs and raised mouth`() {
        val pose = poseOf(CompanionState(emotion = CompanionEmotion.HAPPY))
        assertTrue(pose.happyEyes)
        assertEquals(MouthMode.HAPPY, pose.mouth)
    }

    @Test
    fun `curious emotion tilts the head`() {
        val pose = poseOf(CompanionState(emotion = CompanionEmotion.CURIOUS))
        assertTrue(pose.headTiltDeg > 0f)
    }

    @Test
    fun `sleepy emotion droops ears and closes eyes`() {
        val pose = poseOf(CompanionState(emotion = CompanionEmotion.SLEEPY))
        assertTrue(pose.eyeOpen <= 0.2f)
        assertTrue(pose.earDroopDeg > 0f)
    }

    @Test
    fun `error state turns the halo red`() {
        val pose = poseOf(CompanionState(errorMessage = "boom"))
        assertEquals(0xFFF85149, pose.glowColorHex)
    }

    @Test
    fun `registry serves known clips and falls back safely`() {
        val blink = CatAnimationRegistry.spec("idle.blink")
        assertTrue(blink.minDelayMs > 0)
        val unknown = CatAnimationRegistry.spec("does.not.exist")
        assertEquals(1000, unknown.durationMs)
        assertTrue(CatAnimationRegistry.registeredNames().contains("speech.talk_cycle"))
    }
}

/**
 * Unit tests for the avatar touch-zone reducer: ears -> mic privacy,
 * eyes -> camera privacy, mouth -> mute, forehead -> settings,
 * background -> tiles. Plus the swipe entry points and privacy dashboard.
 */
class CompanionZoneTest {

    @Test
    fun `ear tap toggles microphone privacy`() {
        val vm = CompanionViewModel()
        assertTrue(vm.uiState.value.isMicEnabled)
        vm.onAvatarZoneClick(AvatarZone.EAR_LEFT)
        assertFalse(vm.uiState.value.isMicEnabled)
        assertEquals(InteractionState.MUTED, vm.uiState.value.interactionState)
        vm.onAvatarZoneClick(AvatarZone.EAR_RIGHT)
        assertTrue(vm.uiState.value.isMicEnabled)
    }

    @Test
    fun `eye tap toggles camera privacy`() {
        val vm = CompanionViewModel()
        assertFalse(vm.uiState.value.isCameraEnabled)
        vm.onAvatarZoneClick(AvatarZone.EYE_LEFT)
        assertTrue(vm.uiState.value.isCameraEnabled)
        vm.onAvatarZoneClick(AvatarZone.EYE_RIGHT)
        assertFalse(vm.uiState.value.isCameraEnabled)
    }

    @Test
    fun `mouth tap toggles speech mute`() {
        val vm = CompanionViewModel()
        assertFalse(vm.uiState.value.isMuted)
        vm.onAvatarZoneClick(AvatarZone.MOUTH)
        assertTrue(vm.uiState.value.isMuted)
    }

    @Test
    fun `forehead toggles settings and background toggles tiles`() {
        val vm = CompanionViewModel()
        vm.onAvatarZoneClick(AvatarZone.FOREHEAD)
        assertTrue(vm.uiState.value.showSettings)
        vm.onAvatarZoneClick(AvatarZone.BACKGROUND)
        assertTrue(vm.uiState.value.showTiles)
    }

    @Test
    fun `privacy dashboard switches drive the same state as touch zones`() {
        val vm = CompanionViewModel()
        vm.setMicEnabled(false)
        assertFalse(vm.uiState.value.isMicEnabled)
        vm.setCameraEnabled(false)
        assertFalse(vm.uiState.value.isCameraEnabled)
        vm.setMuted(true)
        assertTrue(vm.uiState.value.isMuted)
        vm.togglePrivacyDashboard()
        assertTrue(vm.uiState.value.showPrivacyDashboard)
    }

    @Test
    fun `openChat and openTiles only open, never close`() {
        val vm = CompanionViewModel()
        vm.openChat()
        assertTrue(vm.uiState.value.isChatExpanded)
        vm.openChat()
        assertTrue("second openChat must not close the chat", vm.uiState.value.isChatExpanded)

        vm.openTiles()
        assertTrue(vm.uiState.value.showTiles)
        vm.openTiles()
        assertTrue("second openTiles must not close the tiles", vm.uiState.value.showTiles)
    }

    @Test
    fun `blank chat input is ignored`() {
        val vm = CompanionViewModel()
        vm.onUserTextSubmitted("   ")
        assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
    }
}
