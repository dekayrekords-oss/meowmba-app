package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LumenNaturalMotionTest {
    @Test fun largeAcquisitionIsFasterThanTheOldRestingSpring() {
        val gaze = LumenGazeMotion(); val resting = LumenSpring()
        var point = LumenAttentionPoint()
        repeat(12) {
            point = gaze.step(.9f, -.4f, 1f / 120, true)
            resting.step(.9f, 1f / 120, 19f)
        }
        assertTrue("large movement acquires promptly", point.x > resting.value + .15f)
        assertTrue(point.x < .9f && point.y > -.4f)
    }

    @Test fun heldSamplesSettleWithoutPredictionOrOscillation() {
        for (hz in listOf(30, 60, 120)) {
            val gaze = LumenGazeMotion(); var last = 0f
            repeat(hz * 3) {
                val point = gaze.step(.85f, -.3f, 1f / hz, true)
                assertTrue("no overshoot at $hz Hz", point.x in last..0.85001f)
                last = point.x
            }
            assertEquals(.85f, last, .0001f)
        }
    }

    @Test fun rapidReversalsHaveBoundedVelocityAndNeverBlankTheEyes() {
        val rig = LumenRig(); rig.step(0f, LumenInput())
        var previous = LumenFrame()
        val control = LumenRig(); control.step(0f, LumenInput())
        repeat(300) { i ->
            val target = if (i % 36 < 18) 1f else -1f
            val f = rig.step((i + 1f) / 120, LumenInput(target, -target, true))
            assertTrue(f.gazeX in -1f..1f && f.gazeY in -1f..1f)
            assertTrue(abs(f.gazeX - previous.gazeX) <= LumenGazeMotion.MAX_VELOCITY / 120 + .0001f)
            assertTrue(abs(f.gazeY - previous.gazeY) <= LumenGazeMotion.MAX_VELOCITY / 120 + .0001f)
            val resting = control.step((i + 1f) / 120, LumenInput(0f, 0f, true))
            assertEquals(resting.leftOpen, f.leftOpen, .0001f)
            assertEquals(resting.rightOpen, f.rightOpen, .0001f) // A gaze reversal does not deform the lids.
            assertEquals(1f, f.presence, 0f)
            previous = f
        }
    }

    @Test fun smallBoxNoiseDoesNotBecomeFastSaccades() {
        val gaze = LumenGazeMotion()
        repeat(300) { i ->
            val jitter = if (i % 2 == 0) .015f else -.015f
            val point = gaze.step(jitter, -jitter, 1f / 60, true)
            assertTrue(abs(point.x) < .005f && abs(point.y) < .005f)
        }
    }

    @Test fun idleScoreIsDeterministicVariedAndMostlyHeld() {
        val first = LumenIdleAttention(71); val second = LumenIdleAttention(71)
        var previous = LumenAttentionPoint(); var held = 0; var changes = 0
        var left = false; var right = false; var up = false; var down = false; var centered = false
        repeat(60 * 90) {
            val point = first.step(1f / 60, true)
            assertEquals(point, second.step(1f / 60, true))
            if (point == previous) held++ else changes++
            left = left || point.x < -.3f; right = right || point.x > .3f
            up = up || point.y < -.3f; down = down || point.y > .2f
            centered = centered || point == LumenAttentionPoint()
            previous = point
        }
        assertTrue(left && right && up && down && centered)
        assertTrue("holds, not per-frame random noise", held > 5300 && changes in 15..75)
    }

    @Test fun cameraKillIgnoresOldSamplesAndReturnsSmoothlyBeforeIdleWanders() {
        val rig = LumenRig(); val input = LumenInput(.9f, -.4f, true)
        rig.step(0f, input)
        var before = LumenFrame()
        repeat(120) { before = rig.step((it + 1f) / 120, input) }
        val first = rig.step(1f + 1f / 120, input.copy(cameraEnabled = false))
        assertTrue(first.gazeX < before.gazeX && first.gazeX > .87f)
        assertTrue(abs(first.driftX - before.driftX) < .015f)
        var last = first
        repeat(120) { last = rig.step(1f + (it + 2f) / 120, input.copy(cameraEnabled = false)) }
        assertEquals(0f, last.gazeX, .001f)
        val ignored = LumenRig(); val absent = LumenRig()
        repeat(1800) { i ->
            assertEquals(ignored.step(i / 60f, input.copy(cameraEnabled = false)), absent.step(i / 60f, LumenInput()))
        }
    }

    @Test fun wholePairFollowsAfterTheEyesAndRestartsWithoutSnapping() {
        val rig = LumenRig(); val target = LumenInput(.8f, -.3f, true)
        rig.step(0f, target)
        var f = LumenFrame()
        repeat(12) { f = rig.step((it + 1f) / 120, target) }
        assertTrue(f.gazeX / .8f > f.driftX / (.8f * .78f))
        repeat(240) { f = rig.step(.1f + (it + 1f) / 120, target) }
        val parked = lumenCenter(1200f, 500f, f)
        val oldRoot = lumenCenter(1200f, 500f, f.copy(driftX = 0f, driftY = 0f))
        assertTrue("wide stages should visibly translate the pair", parked.x - oldRoot.x > 35f)
        val reacquired = rig.step(2.1f + 1f / 120, target.copy(gazeX = -.8f))
        assertTrue(abs(reacquired.gazeX - f.gazeX) < .07f)
        assertTrue(abs(reacquired.driftX - f.driftX) < .014f)
    }

    @Test fun scenesAndSpeechDoNotStartAutonomousGlances() {
        for (input in listOf(LumenInput(performing = true),
            LumenInput(interaction = InteractionState.LISTENING),
            LumenInput(interaction = InteractionState.SPEAKING))) {
            val rig = LumenRig()
            repeat(1800) { i ->
                val f = rig.step(i / 60f, input)
                assertEquals(0f, f.gazeX, 0f); assertEquals(0f, f.gazeY, 0f)
                // Conversational acknowledgements may shift posture slightly,
                // but must not wander into the idle exploration score.
                assertTrue(abs(f.driftX) < .04f && abs(f.driftY) < .15f)
            }
        }
    }

    @Test fun fullSceneFocusIsIndependentOfCameraAndKeepsTheOldRoot() {
        val a = LumenRig(); val b = LumenRig()
        val scene = LumenInput(performing = true, sceneGazeX = -.6f, sceneGazeY = .3f, sceneGazeWeight = 1f)
        repeat(300) { i ->
            val first = a.step(i / 60f, scene)
            val second = b.step(i / 60f, scene.copy(cameraEnabled = true, gazeX = 1f, gazeY = -1f))
            assertEquals(first, second)
            assertEquals(0f, first.driftX, 0f); assertEquals(0f, first.driftY, 0f)
        }
    }
}
