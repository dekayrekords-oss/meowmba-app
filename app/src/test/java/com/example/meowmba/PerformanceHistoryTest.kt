package com.example.meowmba

import com.example.meowmba.ui.companion.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PerformanceHistoryTest {
    private val epoch = 1_800_000_000_000L
    private val scenes get() = AvatarPerformanceCatalog.entries.filter { it.category == "Scenes" }
    private val quiet get() = AvatarPerformanceCatalog.entries.filter { it.ambient }

    @Test fun roundTripContainsOnlyBoundedCatalogHistoryAndUsesCurrentFamilies() {
        val recent = (0 until PerformanceHistory.MAX_RECENT + 5).map { i ->
            val p = quiet[i % quiet.size]
            PerformanceHistoryEntry(p.id, "untrusted saved family", epoch - 100L + i)
        }
        val history = PerformanceHistory(epoch, recent,
            PerformanceHistoryEntry(scenes.first().id, "old family", epoch - 200L),
            listOf(scenes.first().id, "removed_scene") + scenes.drop(1).flatMap { listOf(it.id, it.id) })
        val json = history.toJson()
        val restored = PerformanceHistory.fromJson(json, epoch + 1L)
        assertEquals(PerformanceHistory.MAX_RECENT, restored.recent.size)
        assertEquals(scenes.drop(1).map { it.id }, restored.remainingSceneIds)
        assertEquals(scenes.first().family, restored.lastScene?.family)
        restored.recent.forEach { entry ->
            assertEquals(AvatarPerformanceCatalog.entries.first { it.id == entry.id }.family, entry.family)
        }
        assertFalse(json.contains("untrusted saved family"))
        assertTrue(json.length < PerformanceHistory.MAX_JSON_CHARS)
        assertEquals(PerformanceHistory.VERSION, Json.parseToJsonElement(json).jsonObject["version"]?.jsonPrimitive?.int)
    }

    @Test fun corruptFutureExpiredAndOversizedPreferencesResetWithoutThrowing() {
        val valid = PerformanceHistory(epoch, listOf(PerformanceHistoryEntry(quiet.first().id, quiet.first().family, epoch)))
        for (raw in listOf(null, "", "not json", "[]", "null", "{\"version\":99,\"savedAtMs\":$epoch}",
            "{\"version\":\"1\",\"savedAtMs\":$epoch}", "{\"version\":1,\"savedAtMs\":[]}",
            " ".repeat(PerformanceHistory.MAX_JSON_CHARS + 1))) {
            assertEquals("raw=${raw?.take(80)}", PerformanceHistory(), PerformanceHistory.fromJson(raw, epoch))
        }
        assertEquals(PerformanceHistory(), PerformanceHistory.fromJson(valid.toJson(), epoch - 1L))
        assertEquals(PerformanceHistory(), PerformanceHistory.fromJson(valid.toJson(), epoch + PerformanceHistory.MAX_AGE_MS + 1L))
        assertEquals(PerformanceHistory(), PerformanceHistory.fromJson(valid.toJson(), -1L))
    }

    @Test fun malformedEntriesAndUnknownIdsAreDroppedWithoutLosingValidNeighbors() {
        val p = quiet.first()
        val s = scenes.first()
        val raw = """{"version":1,"savedAtMs":$epoch,"extra":"ignored",
            "recent":[null,7,[],{"id":"removed","playedAtMs":$epoch},
            {"id":"${p.id}","family":{},"playedAtMs":$epoch},
            {"id":"${p.id}","playedAtMs":${epoch + 1}},
            {"id":"${p.id}","playedAtMs":"$epoch"}],
            "lastScene":{"id":"${p.id}","playedAtMs":$epoch},
            "remainingSceneIds":[{},false,"removed","${s.id}","${s.id}","${p.id}"]}"""
        val restored = PerformanceHistory.fromJson(raw, epoch)
        assertEquals(listOf(PerformanceHistoryEntry(p.id, p.family, epoch)), restored.recent)
        assertNull(restored.lastScene)
        assertEquals(listOf(s.id), restored.remainingSceneIds)
    }

    @Test fun quietRepertoireDoesNotRepeatAcrossFrequentRestartsEvenWithIdenticalSeeds() {
        var saved = PerformanceHistory()
        var wallNow = epoch
        val played = mutableListOf<AvatarPerformance>()
        repeat(quiet.size * 4) {
            val selector = IdlePerformanceSelector(Random(12), saved, { saved = it }, { wallNow })
            played += selector.pick(0L, scenesEnabled = false)!!
            saved = PerformanceHistory.fromJson(saved.toJson(), wallNow)
            wallNow += 12_000L
        }
        played.windowed(minOf(4, quiet.size)).forEach { window ->
            assertEquals(window.size, window.map { it.id }.distinct().size)
        }
        assertTrue(played.all { it.ambient })
    }

    @Test fun sceneBagAndFamilyFairnessSurviveARestartAfterEverySelection() {
        var saved = PerformanceHistory()
        var wallNow = epoch
        val played = mutableListOf<AvatarPerformance>()
        var remaining = scenes.toMutableList()
        var previousFamily: String? = null
        repeat(scenes.size * 2) {
            if (remaining.isEmpty()) remaining = scenes.toMutableList()
            val selector = IdlePerformanceSelector(Random(3), saved, { saved = it }, { wallNow })
            val next = selector.pick(0L)!!
            assertEquals("Scenes", next.category)
            if (remaining.any { it.family != previousFamily }) assertNotEquals(previousFamily, next.family)
            remaining.removeAll { it.id == next.id }
            previousFamily = next.family
            played += next
            saved = PerformanceHistory.fromJson(saved.toJson(), wallNow)
            wallNow += next.spec.durationMs + IdlePerformanceSelector.SCENE_RECOVERY_MS
        }
        assertEquals(scenes.map { it.id }.toSet(), played.take(scenes.size).map { it.id }.toSet())
        assertEquals(scenes.map { it.id }.toSet(), played.drop(scenes.size).map { it.id }.toSet())
        played.zipWithNext().forEach { (a, b) -> assertNotEquals(a.id, b.id) }
    }

    @Test fun shortRestartPreservesRecoveryButQuietAndSceneSettingsStillWork() {
        var saved = PerformanceHistory()
        var wallNow = epoch
        val selector = IdlePerformanceSelector(Random(19), onHistoryChanged = { saved = it }, wallClockMs = { wallNow })
        val first = selector.pick(0L)!!
        wallNow += 1_000L
        val restarted = IdlePerformanceSelector(Random(19), saved, { saved = it }, { wallNow })
        assertNull(restarted.pick(0L))
        assertNull(restarted.pick(7_999L))
        wallNow += 8_000L
        assertTrue(restarted.pick(8_000L)!!.ambient)
        wallNow += 12_000L
        assertTrue(restarted.pick(20_000L, reducedMotion = true)!!.ambient)
        wallNow += 80_000L
        assertTrue(restarted.pick(100_000L, scenesEnabled = false)!!.ambient)
        wallNow += 12_000L
        val nextScene = restarted.pick(112_000L)!!
        assertEquals("Scenes", nextScene.category)
        assertNotEquals(first.id, nextScene.id)
    }

    @Test fun callbacksOnlyRunForSelectedClipsAndWallClockChangesCannotBreakSessionCooldowns() {
        var calls = 0
        var wallNow = epoch
        val selector = IdlePerformanceSelector(Random(23), onHistoryChanged = { calls++ }, wallClockMs = { wallNow })
        val first = selector.pick(1_000L)!!
        assertEquals(1, calls)
        wallNow += PerformanceHistory.MAX_AGE_MS * 2
        assertNull(selector.pick(1_000L))
        assertNull(selector.pick(9_999L))
        assertEquals(1, calls)
        wallNow = epoch - 10_000L
        assertTrue(selector.pick(10_000L)!!.ambient)
        assertNull(selector.pick(9_000L))
        val nextScene = selector.pick(1_000L + first.spec.durationMs + IdlePerformanceSelector.SCENE_RECOVERY_MS)!!
        assertEquals("Scenes", nextScene.category)
        assertNotEquals(first.id, nextScene.id)
        assertEquals(3, calls)
    }

    @Test fun storageFailureDoesNotStopSelectionAndUnknownHistoryDoesNotSuppressScenes() {
        val selector = IdlePerformanceSelector(Random(44),
            PerformanceHistory(epoch, listOf(PerformanceHistoryEntry("removed", "removed", epoch)),
                PerformanceHistoryEntry("removed", "removed", epoch), listOf("removed")),
            onHistoryChanged = { error("preference storage unavailable") }, wallClockMs = { epoch })
        assertEquals("Scenes", selector.pick(0L)?.category)
    }

    @Test fun conservativeSocialGateBlocksNewAndContinuingIdleWithoutChangingMicPrivacy() {
        for (interaction in listOf(InteractionState.IDLE, InteractionState.MUTED, InteractionState.OFFLINE)) {
            for (active in listOf(false, true)) {
                val state = CompanionState(interactionState = interaction, isMicEnabled = false,
                    activeAnimation = if (active) scenes.first().spec else null, ambientPerformance = active)
                assertTrue(idlePerformanceAllowed(state, 0f, socialContextAllowed = true))
                assertFalse(idlePerformanceAllowed(state, 0f, socialContextAllowed = false))
                assertFalse(state.isMicEnabled)
            }
        }
    }

    @Test fun quietPlayfulnessKeepsVariedAmbientActingAndLivelyStillHasBoundedRecovery() {
        val quietSelector = IdlePerformanceSelector(Random(92), wallClockMs = { epoch })
        val quietPicks = (0L until 20L).map { i -> quietSelector.pick(i * 12_000L, playfulness = Playfulness.QUIET)!! }
        assertTrue(quietPicks.all { it.ambient && it.category != "Scenes" })
        quietPicks.windowed(minOf(4, quiet.size)).forEach { assertEquals(it.size, it.map { p -> p.id }.distinct().size) }

        val lively = IdlePerformanceSelector(Random(92), wallClockMs = { epoch })
        val first = lively.pick(0L, playfulness = Playfulness.LIVELY)!!
        assertEquals("Scenes", first.category)
        assertNull(lively.pick(8_999L, playfulness = Playfulness.LIVELY))
        assertTrue(lively.pick(9_000L, playfulness = Playfulness.LIVELY)!!.ambient)
        val next = lively.pick(first.spec.durationMs + IdlePerformanceSelector.LIVELY_SCENE_RECOVERY_MS,
            playfulness = Playfulness.LIVELY)!!
        assertEquals("Scenes", next.category)
        assertNotEquals(first.id, next.id)
    }

    @Test fun changingPlayfulnessCannotShortenTheExistingSceneRecovery() {
        val toLively = IdlePerformanceSelector(Random(62), wallClockMs = { epoch })
        val balancedFirst = toLively.pick(0L)!!
        val early = balancedFirst.spec.durationMs + IdlePerformanceSelector.LIVELY_SCENE_RECOVERY_MS
        assertTrue(toLively.pick(early, playfulness = Playfulness.LIVELY)!!.ambient)
        assertEquals("Scenes", toLively.pick(balancedFirst.spec.durationMs + IdlePerformanceSelector.SCENE_RECOVERY_MS,
            playfulness = Playfulness.LIVELY)?.category)

        val toBalanced = IdlePerformanceSelector(Random(63), wallClockMs = { epoch })
        val livelyFirst = toBalanced.pick(0L, playfulness = Playfulness.LIVELY)!!
        val laterEarly = livelyFirst.spec.durationMs + IdlePerformanceSelector.LIVELY_SCENE_RECOVERY_MS
        assertTrue(toBalanced.pick(laterEarly, playfulness = Playfulness.BALANCED)!!.ambient)
        // Switching back to LIVELY does not undo the calmer setting's recovery.
        assertEquals("Scenes", toBalanced.pick(livelyFirst.spec.durationMs + IdlePerformanceSelector.SCENE_RECOVERY_MS,
            playfulness = Playfulness.LIVELY)?.category)
    }
}
