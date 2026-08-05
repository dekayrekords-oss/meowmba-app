package com.example.meowmba

import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.IdleAnimationDirector
import com.example.meowmba.ui.companion.faceModeActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Face-mode detection (keep-screen-on gate) and the idle-animation cadence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdleBehaviorTest {

    @Test
    fun `bare face is face mode`() {
        assertTrue(faceModeActive(CompanionState()))
    }

    @Test
    fun `any overlay screen leaves face mode`() {
        assertFalse(faceModeActive(CompanionState(showSettings = true)))
        assertFalse(faceModeActive(CompanionState(showModelManager = true)))
        assertFalse(faceModeActive(CompanionState(isChatExpanded = true)))
        assertFalse(faceModeActive(CompanionState(showTiles = true)))
        assertFalse(faceModeActive(CompanionState(showPrivacyDashboard = true)))
        assertFalse(faceModeActive(CompanionState(showMemoryScreen = true)))
        assertFalse(faceModeActive(CompanionState(showAgentsScreen = true)))
        assertFalse(faceModeActive(CompanionState(showBackgroundScreen = true)))
        assertFalse(faceModeActive(CompanionState(showWeatherScreen = true)))
        assertFalse(faceModeActive(CompanionState(showNavScreen = true)))
        assertFalse(faceModeActive(CompanionState(showVaultScreen = true)))
    }

    @Test
    fun `an active clip is still face mode`() {
        // Clips play ON the face, so the display should stay awake for them.
        val spec = com.example.meowmba.ui.companion.CustomAnimations.BUILT_INS.values.first()
        assertTrue(faceModeActive(CompanionState(activeAnimation = spec)))
    }

    @Test
    fun `idle cadence stays in the 45 to 120 second window`() {
        val director = IdleAnimationDirector(
            scope = TestScope(),
            random = Random(42),
            canPlay = { true },
            pickSpec = { null },
            play = {},
        )
        repeat(1000) {
            val wait = director.nextWaitMs()
            assertTrue("wait $wait below floor", wait >= 45_000L)
            assertTrue("wait $wait above ceiling", wait < 120_000L)
        }
    }

    @Test
    fun `director plays only when canPlay allows`() = runTest {
        var played = 0
        var allow = false
        val spec = com.example.meowmba.ui.companion.CustomAnimations.BUILT_INS.values.first()
        // Own dispatcher + TestScope bound to it: the director's loop is
        // fully under this test's virtual clock and can never escape onto
        // the shared Main dispatcher.
        val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testScheduler + dispatcher)
        val director = IdleAnimationDirector(
            scope = testScope,
            random = Random(1),
            canPlay = { allow },
            pickSpec = { spec },
            play = { played++ },
        )
        director.start()
        // Skip several cadence windows while blocked, then allow one.
        testScheduler.advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(0, played)
        allow = true
        testScheduler.advanceTimeBy(2 * 60_000L)
        runCurrent()
        assertTrue("expected at least one idle animation", played >= 1)
        director.stop()
        testScope.coroutineContext.cancelChildren()
    }
}
