package com.example.meowmba

import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.avatarSceneVisible
import com.example.meowmba.ui.companion.faceModeActive
import com.example.meowmba.ui.companion.idlePerformanceAllowed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainAvatarVisibilityTest {
    @Test fun compactBrainKeepsTheAvatarVisibleWithoutBecomingBareFaceMode() {
        val state = CompanionState(showNeuralInfo = true)
        assertTrue(avatarSceneVisible(state))
        assertFalse(faceModeActive(state))
        assertTrue("Visible neural clips can run", idlePerformanceAllowed(state, 0f))
        assertFalse("Heat and lifecycle gate still wins", idlePerformanceAllowed(state, 0f, stageAllowed = false))
        assertFalse("Speaking must still block ambient acting", idlePerformanceAllowed(state, .8f))
    }

    @Test fun compactChatAndBrainCanShareTheScene() {
        val state = CompanionState(showNeuralInfo = true, isChatExpanded = true)
        assertTrue(avatarSceneVisible(state))
        assertFalse(faceModeActive(state))
    }

    @Test fun opaquePanesStillHideTheSceneEvenWhenBrainIsMarkedOpen() {
        val brain = CompanionState(showNeuralInfo = true)
        val covered = listOf(
            "settings" to brain.copy(showSettings = true),
            "tile menu" to brain.copy(showTiles = true),
            "avatar setup" to brain.copy(showAvatarScreen = true),
            "background picker" to brain.copy(showBackgroundScreen = true),
            "model manager" to brain.copy(showModelManager = true),
            "privacy" to brain.copy(showPrivacyDashboard = true),
            "memory" to brain.copy(showMemoryScreen = true),
            "studio" to brain.copy(showStudioScreen = true),
            "app drawer" to brain.copy(showAppDrawer = true),
            "robot" to brain.copy(showRobotScreen = true),
        )
        for ((name, state) in covered) assertFalse(name, avatarSceneVisible(state))
    }

    @Test fun dismissingBrainRestoresTheOrdinaryAvatarState() {
        val state = CompanionState(showNeuralInfo = true).copy(showNeuralInfo = false)
        assertTrue(avatarSceneVisible(state))
        assertTrue(faceModeActive(state))
    }
}
