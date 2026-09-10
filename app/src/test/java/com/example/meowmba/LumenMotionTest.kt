package com.example.meowmba

import com.example.meowmba.ui.companion.*
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.tools.AnimationDescriber
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LumenMotionTest {
    @Test fun restingStepIsMonotonicAndDoesNotOvershoot() {
        val s = LumenSpring()
        var previous = 0f
        repeat(120) {
            val next = s.step(1f, 1f / 120f, 19f)
            assertTrue(next >= previous && next <= 1.00001f)
            previous = next
        }
        assertEquals(1f, previous, 0.001f)
    }

    @Test fun gazeLeadsHeadAndSettlesWithoutIdleNoiseWhileTracking() {
        val rig = LumenRig()
        val target = LumenInput(0.8f, -0.2f, true)
        rig.step(0f, target)
        var early = LumenFrame()
        repeat(12) { early = rig.step((it + 1) / 120f, target) }
        assertTrue(early.gazeX / 0.8f > early.headX / (0.8f * 0.42f))
        var last = early
        repeat(2400) { last = rig.step(0.1f + (it + 1) / 120f, target) }
        assertEquals(0.8f, last.gazeX, 0.0001f)
        assertEquals(-0.2f, last.gazeY, 0.0001f)
    }

    @Test fun frozenActiveClockPreservesEveryMotionChannel() {
        val rig = LumenRig()
        rig.step(0f, LumenInput())
        val before = rig.step(0.016f, LumenInput())
        repeat(100) {
            assertEquals(before, rig.step(0.016f, LumenInput(1f, 1f, true, animal = AvatarAnimal.DOG)))
        }
    }

    @Test fun refreshRatesConvergeToSamePose() {
        fun sample(hz: Int): LumenFrame {
            val rig = LumenRig()
            val input = LumenInput(0.7f, -0.4f, true, animal = AvatarAnimal.CAT)
            rig.step(0f, input)
            var result = LumenFrame()
            repeat(hz * 2) { result = rig.step((it + 1f) / hz, input) }
            return result
        }
        val baseline = sample(120)
        for (hz in listOf(30, 60, 90)) {
            val actual = sample(hz)
            assertEquals(baseline.gazeX, actual.gazeX, 0.0001f)
            assertEquals(baseline.headX, actual.headX, 0.001f)
            assertEquals(baseline.cat, actual.cat, 0.0001f)
        }
    }

    @Test fun staleOrDisabledCameraStopsFollowingButNeverSnapsHome() {
        val rig = LumenRig()
        rig.step(0f, LumenInput())
        var f = LumenFrame()
        repeat(120) { f = rig.step((it + 1f) / 120, LumenInput(1f, 0f, true)) }
        val first = rig.step(1.008f, LumenInput(1f, 0f, false))
        assertTrue(first.gazeX < f.gazeX && first.gazeX > 0.97f)
        repeat(120) { f = rig.step(1.008f + (it + 1f) / 120, LumenInput(1f, 0f, false)) }
        assertEquals(0f, f.gazeX, 0.001f)
    }

    @Test fun blinkClosesFullyAndOpensMoreSlowly() {
        assertEquals(1f, LumenRig.blinkAt(0f), 0f)
        assertEquals(0f, LumenRig.blinkAt(0.085f), 0f)
        assertTrue(LumenRig.blinkAt(0.14f) < LumenRig.blinkAt(0.035f))
        assertEquals(1f, LumenRig.blinkAt(0.26f), 0f)
    }

    @Test fun blinkDoesNotAlterPupilSize() {
        val rig = LumenRig()
        val input = LumenInput(0f, 0f, true)
        rig.step(0f, input)
        var shut = false
        repeat(420) {
            val f = rig.step((it + 1f) / 120, input)
            if (f.leftOpen < 0.01f) shut = true
            assertEquals(0.5f, f.pupil, 0.0001f)
        }
        assertTrue(shut)
    }

    @Test fun costumesEaseInAndReturnToEyesOnly() {
        val rig = LumenRig()
        rig.step(0f, LumenInput())
        val first = rig.step(0.016f, LumenInput(animal = AvatarAnimal.DOG))
        assertTrue(first.dog > 0f && first.dog < 0.02f)
        repeat(120) { rig.step(0.016f + (it + 1f) / 120, LumenInput(animal = AvatarAnimal.DOG)) }
        var last = LumenFrame()
        repeat(240) { last = rig.step(1.016f + (it + 1f) / 120, LumenInput()) }
        assertTrue(last.dog < 0.0001f)
        assertEquals(0f, last.cat, 0f)
    }

    @Test fun expressionsAreDistinctAndAsymmetryIsIntentional() {
        fun settle(emotion: CompanionEmotion): LumenFrame {
            val rig = LumenRig()
            val input = LumenInput(emotion = emotion)
            rig.step(0f, input)
            var f = LumenFrame()
            repeat(100) { f = rig.step((it + 1f) / 60, input) }
            return f
        }
        assertTrue(settle(CompanionEmotion.HAPPY).smile > 0.90f)
        assertTrue(settle(CompanionEmotion.SLEEPY).leftOpen < 0.5f)
        assertTrue(settle(CompanionEmotion.ALERT).leftOpen > 1.1f)
        val curious = settle(CompanionEmotion.CURIOUS)
        assertTrue(curious.leftOpen > curious.rightOpen && abs(curious.roll) > 4f)
    }

    @Test fun corruptInputsCannotReachCanvasTransforms() {
        val rig = LumenRig()
        rig.step(0f, LumenInput())
        val f = rig.step(500f, LumenInput(Float.NaN, Float.POSITIVE_INFINITY, true,
            tilt = Float.NaN, bounce = Float.NaN, lean = Float.NaN, lurch = Float.NaN,
            pupil = Float.NaN, speaking = Float.NaN, performing = true, openness = Float.NaN))
        for (v in listOf(f.gazeX, f.gazeY, f.headX, f.headY, f.roll, f.pupil, f.leftOpen, f.engagement)) assertTrue(v.isFinite())
    }

    @Test fun animalCommandsRouteLocallyButCasualPetsDoNot() {
        for (animal in listOf("cat", "dog")) {
            val route = AgentRouter.route("act like a $animal")!!
            assertEquals("avatar.describe_animation", route.toolId)
            val spec = AnimationDescriber.describe(route.args["description"]!!)!!
            assertEquals(AvatarAnimal.fromKey(animal), AvatarAnimal.performanceFor(spec.name))
            assertTrue(spec.durationMs > 3000)
        }
        assertNull(AgentRouter.route("just chilling with the cats"))
        assertNull(AgentRouter.route("do not act like a dog"))
        assertNull(AvatarAnimal.performanceFor("catapult"))
    }

    @Test fun styleAndFormKeysRestoreWithoutChangingDefault() {
        assertEquals(AvatarStyle.LUMEN, AvatarStyle.fromKey("lumen"))
        assertEquals(AvatarAnimal.NONE, AvatarAnimal.fromKey(null))
        assertEquals(AvatarStyle.NEO, AvatarStyle.DEFAULT)
    }

    @Test fun authoredClosedEyesAndEarChannelsRemainAvailable() {
        val rig = LumenRig()
        val input = LumenInput(performing = true, openness = 0f, gazeUp = 0.8f, earDroop = 24f)
        rig.step(0f, input)
        var f = LumenFrame()
        repeat(120) { f = rig.step((it + 1f) / 60, input) }
        assertTrue(f.leftOpen < 0.01f && f.rightOpen < 0.01f)
        assertTrue(f.gazeY < -0.39f)
        assertTrue(f.earLift < -11f)
    }

    @Test fun hitTargetsFollowTiltGazeAndCostumeFit() {
        val frame = LumenFrame(headX = 0.6f, headY = -0.3f, roll = 18f, cat = 1f)
        val w = 720; val h = 520
        val unit = lumenUnit(w.toFloat(), h.toFloat(), frame)
        val center = androidx.compose.ui.geometry.Offset(w / 2f + frame.headX * unit * 0.09f,
            h * 0.45f + frame.headY * unit * 0.07f)
        val angle = frame.roll * Math.PI.toFloat() / 180f
        for (side in listOf(-1, 1)) {
            val local = androidx.compose.ui.geometry.Offset(side * unit * 0.213f, abs(frame.headX) * side * unit * 0.009f)
            val point = center + androidx.compose.ui.geometry.Offset(local.x * kotlin.math.cos(angle) - local.y * kotlin.math.sin(angle),
                local.x * kotlin.math.sin(angle) + local.y * kotlin.math.cos(angle))
            assertEquals(if (side < 0) AvatarZone.EYE_LEFT else AvatarZone.EYE_RIGHT, lumenZoneAt(point, w, h, frame))
        }
        assertEquals(AvatarZone.BACKGROUND, lumenZoneAt(androidx.compose.ui.geometry.Offset(2f, 2f), w, h, frame))
    }

    @Test fun sadnessAndDeterminationHaveOppositeLidActing() {
        assertTrue(lumenPerformanceTilt("angry") < 0f)
        assertTrue(lumenPerformanceTilt("sad") > 0f)
        assertEquals(0f, lumenPerformanceTilt("dance"), 0f)
        val rig = LumenRig(); rig.step(0f, LumenInput())
        var f = LumenFrame()
        repeat(120) { f = rig.step((it + 1f) / 60, LumenInput(performing = true, lidTilt = -12f)) }
        assertEquals(-12f, f.lidTilt, 0.01f)
    }
}
