package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class IdlePerformancePolicyTest {
    @Test fun quietMicOffAndOfflineCanPerformButTtsAndUserTurnsAlwaysWin() {
        for (state in InteractionState.entries) {
            val quiet = state in listOf(InteractionState.IDLE, InteractionState.MUTED, InteractionState.OFFLINE)
            val snapshot = CompanionState(interactionState = state, isMicEnabled = false)
            assertEquals("eligibility for $state", quiet, idlePerformanceAllowed(snapshot, .001f))
            for (energy in listOf(.03f, .6f, Float.NaN, Float.POSITIVE_INFINITY)) {
                assertFalse("speaking or invalid input for $state", idlePerformanceAllowed(snapshot, energy))
            }
        }
    }

    @Test fun coveringTheStageDrivingTasksAndPausingPreventNewAndContinuingAmbientClips() {
        val performance = AvatarPerformanceCatalog.resolve("Butterfly Chase")!!.spec
        for (active in listOf(false, true)) {
            val resting = CompanionState(activeAnimation = if (active) performance else null,
                ambientPerformance = active)
            assertTrue(idlePerformanceAllowed(resting, 0f))
            assertFalse(idlePerformanceAllowed(resting, 0f, stageAllowed = false))
            for (blocked in listOf(resting.copy(showSettings = true), resting.copy(isChatExpanded = true),
                resting.copy(showConnectorsScreen = true), resting.copy(showBrowser = true),
                resting.copy(showProjectsScreen = true), resting.copy(driving = true),
                resting.copy(activeTask = "Working"))) {
                assertFalse(idlePerformanceAllowed(blocked, 0f))
            }
        }
    }

    @Test fun firstIdlePerformanceIsARealSceneAndEverySceneGetsATurnWithoutRepeats() {
        for (seed in listOf(1, 43, 812)) {
            val selector = IdlePerformanceSelector(Random(seed))
            var now = 0L
            val played = mutableListOf<AvatarPerformance>()
            val expected = AvatarPerformanceCatalog.entries.filter { it.category == "Scenes" }.map { it.id }.toSet()
            repeat(expected.size * 2) {
                val next = selector.pick(now)!!
                assertEquals("Scenes", next.category)
                played += next
                now += next.spec.durationMs + IdlePerformanceSelector.SCENE_RECOVERY_MS
            }
            assertEquals(expected, played.take(expected.size).map { it.id }.toSet())
            assertEquals(expected, played.drop(expected.size).map { it.id }.toSet())
            played.zipWithNext().forEach { (a, b) -> assertNotEquals(a.id, b.id) }
            assertTrue(played.any { it.id == "butterfly_chase" })
        }
    }

    @Test fun sceneFamiliesAlternateWheneverAnotherFamilyRemainsInTheBag() {
        val scenes = AvatarPerformanceCatalog.entries.filter { it.category == "Scenes" }
        for (seed in 0..20) {
            val selector = IdlePerformanceSelector(Random(seed))
            val remaining = scenes.toMutableList()
            var lastFamily: String? = null
            var now = 0L
            repeat(scenes.size * 2) {
                if (remaining.isEmpty()) remaining.addAll(scenes)
                val next = selector.pick(now)!!
                if (remaining.any { it.family != lastFamily }) assertNotEquals(lastFamily, next.family)
                remaining.removeAll { it.id == next.id }
                lastFamily = next.family
                now += next.spec.durationMs + IdlePerformanceSelector.SCENE_RECOVERY_MS
            }
        }
    }

    @Test fun sceneRecoveryHasQuietClipsAndDoesNotAllowRapidRepeatCalls() {
        val selector = IdlePerformanceSelector(Random(73))
        val first = selector.pick(1_000L)!!
        assertEquals("Scenes", first.category)
        assertNull(selector.pick(1_000L))
        assertNull(selector.pick(9_999L))
        val quiet = selector.pick(10_000L)!!
        assertTrue(quiet.ambient)
        assertNull(selector.pick(9_000L))
        val secondQuiet = selector.pick(19_000L)!!
        assertTrue(secondQuiet.ambient)
        assertNotEquals(quiet.id, secondQuiet.id)
        val secondScene = selector.pick(1_000L + first.spec.durationMs + IdlePerformanceSelector.SCENE_RECOVERY_MS)!!
        assertEquals("Scenes", secondScene.category)
        assertNotEquals(first.id, secondScene.id)
    }

    @Test fun disablingScenesOrReducingMotionKeepsVariedQuietPerformances() {
        for (reduced in listOf(false, true)) {
            val selector = IdlePerformanceSelector(Random(123))
            val played = (0L until 30L).map { i ->
                selector.pick(i * 12_000L, scenesEnabled = reduced, reducedMotion = reduced)!!
            }
            assertTrue(played.all { it.ambient && it.category != "Scenes" })
            played.windowed(4).forEach { assertEquals(4, it.map { p -> p.id }.distinct().size) }
            assertEquals("Scenes", selector.pick(400_000L, scenesEnabled = true, reducedMotion = false)?.category)
        }
    }

    @Test fun differentSessionSeedsVaryTheFirstSceneOrder() {
        val firstScenes = (0..12).map { IdlePerformanceSelector(Random(it)).pick(0L)!!.id }.toSet()
        assertTrue(firstScenes.size > 3)
    }
}
