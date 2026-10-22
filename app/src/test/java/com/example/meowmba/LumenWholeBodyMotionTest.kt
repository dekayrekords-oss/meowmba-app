package com.example.meowmba

import androidx.compose.ui.geometry.Offset
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class LumenWholeBodyMotionTest {
    private fun settled(input: LumenInput): LumenFrame {
        val rig = LumenRig()
        var f = rig.step(0f, input)
        repeat(120) { f = rig.step((it + 1f) / 60, input) }
        return f
    }

    @Test fun wholeCharacterUsesScreenSpaceInsteadOfAnEyeSizedOffset() {
        val left = settled(LumenInput(-1f, -1f, true))
        val right = settled(LumenInput(1f, 1f, true))
        for ((w, h) in listOf(1200f to 500f, 1600f to 500f, 360f to 800f, 360f to 360f)) {
            val a = lumenCenter(w, h, left); val b = lumenCenter(w, h, right)
            assertTrue("actual horizontal travel $w/$h: ${b.x - a.x}", b.x - a.x > w * .24f)
            assertTrue("actual vertical travel $w/$h: ${b.y - a.y}", b.y - a.y > h * .20f)
        }
    }

    @Test fun everyAnimalTravelsNotJustItsPupils() {
        for (animal in AvatarAnimal.entries) {
            val a = settled(LumenInput(-1f, 0f, true, animal = animal))
            val b = settled(LumenInput(1f, 0f, true, animal = animal))
            for ((w, h) in listOf(360f to 800f, 1200f to 500f)) {
                val span = lumenCenter(w, h, b).x - lumenCenter(w, h, a).x
                assertTrue("$animal travels at $w/$h: $span", span > w * .10f)
                // The larger forms fit their rotated anatomy. The idle rig's
                // tiny asymmetric roll can change that fit by hundredths of a
                // pixel; mirrored attention must still look the same size.
                assertEquals("mirrored travel keeps apparent size", lumenUnit(w, h, a), lumenUnit(w, h, b), .05f)
            }
        }
    }

    @Test fun mutedAndOfflineKeepTheSameQuietIdleWithoutUsingCameraSamples() {
        val idle = LumenRig(); val muted = LumenRig(); val offline = LumenRig()
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        repeat(60 * 90) { i ->
            val f = idle.step(i / 60f, LumenInput())
            assertEquals(f, muted.step(i / 60f, LumenInput(1f, -1f, false, interaction = InteractionState.MUTED)))
            assertEquals(f, offline.step(i / 60f, LumenInput(interaction = InteractionState.OFFLINE)))
            val root = lumenCenter(360f, 800f, f)
            minX = minOf(minX, root.x); maxX = maxOf(maxX, root.x)
            minY = minOf(minY, root.y); maxY = maxOf(maxY, root.y)
        }
        assertTrue("idle whole-body X travel", maxX - minX > 55f)
        assertTrue("idle whole-body Y travel", maxY - minY > 120f)
    }

    @Test fun mutedDuringAudibleReplyDoesNotStartIdleWandering() {
        val rig = LumenRig()
        repeat(1800) { i ->
            val f = rig.step(i / 60f, LumenInput(interaction = InteractionState.MUTED, speaking = .6f))
            assertEquals(0f, f.driftX, 0f); assertEquals(0f, f.driftY, 0f)
        }
    }

    @Test fun postureHoldsWhileTheEyesLookBackRatherThanCopyingPupilMotion() {
        val score = LumenIdleAttention(731 xor 0x51A7)
        var heldWhileLookingBack = false
        repeat(60 * 40) {
            val gaze = score.step(1f / 60, true)
            if (gaze == LumenAttentionPoint() && abs(score.bodyTarget.x) + abs(score.bodyTarget.y) > .4f)
                heldWhileLookingBack = true
        }
        assertTrue(heldWhileLookingBack)
    }

    @Test fun eyesLeadRootThenBodyAcquiresWithinSevenTenthsOfASecond() {
        for (hz in listOf(30, 60, 120)) {
            val rig = LumenRig(); val input = LumenInput(.8f, -.4f, true)
            rig.step(0f, input)
            var f = LumenFrame()
            repeat(hz / 10) { f = rig.step((it + 1f) / hz, input) }
            assertTrue("eyes lead at $hz Hz", f.gazeX / .8f > f.driftX / .76f)
            repeat(hz * 6 / 10) { f = rig.step(.1f + (it + 1f) / hz, input) }
            assertEquals("body catches up at $hz Hz", .76f, f.driftX, .055f)
        }
    }

    @Test fun pixelTravelDoesNotSnapWhenAttentionReversesOrSceneTakesOver() {
        for (hz in listOf(30, 60, 120)) for ((w, h) in listOf(360f to 800f, 1200f to 500f)) {
            val rig = LumenRig(); rig.step(0f, LumenInput())
            var last = lumenCenter(w, h, LumenFrame())
            repeat(hz * 9) { i ->
                val t = (i + 1f) / hz
                val target = if (t % 2 < 1f) .95f else -.95f
                val f = rig.step(t, LumenInput(target, -target, true,
                    animal = if (t < 4f) AvatarAnimal.NONE else AvatarAnimal.DOG,
                    performing = t > 6f, sceneGazeX = .3f, sceneGazeY = -.2f, sceneGazeWeight = 1f))
                val next = lumenCenter(w, h, f)
                assertTrue("X pixel speed at $hz Hz", abs(next.x - last.x) < w * .8f / hz)
                assertTrue("Y pixel speed at $hz Hz", abs(next.y - last.y) < h * .8f / hz)
                last = next
            }
        }
    }

    @Test fun movingEyeHitTargetsStayWithTheirRotatedCharacter() {
        for (animal in AvatarAnimal.entries) for (side in listOf(-1, 1)) {
            val f = settled(LumenInput(side.toFloat(), .7f, true, animal = animal)).copy(roll = 18f, screenRoll = -14f)
            val w = 360; val h = 800
            val u = lumenUnit(w.toFloat(), h.toFloat(), f)
            val root = lumenCenter(w.toFloat(), h.toFloat(), f)
            val angle = lumenRoll(f) * Math.PI.toFloat() / 180f
            for (eye in listOf(-1, 1)) {
                val p = Offset(eye * u * .213f, abs(f.headX) * eye * u * .009f)
                val screen = root + Offset(p.x * cos(angle) - p.y * sin(angle), p.x * sin(angle) + p.y * cos(angle))
                assertEquals(if (eye < 0) AvatarZone.EYE_LEFT else AvatarZone.EYE_RIGHT, lumenZoneAt(screen, w, h, f))
            }
        }
    }

    @Test fun frozenActiveClockFreezesBodyAndIdleScore() {
        val rig = LumenRig()
        var f = LumenFrame()
        repeat(600) { f = rig.step(it / 60f, LumenInput(interaction = InteractionState.MUTED)) }
        repeat(100) { assertEquals(f, rig.step(599f / 60, LumenInput(-1f, 1f, true))) }
    }

    @Test fun fullDrivingRetainsBoundedTravelAndTheExistingInertiaFootprint() {
        val frame = LumenFrame(headX = .8f, headY = .7f, roll = 12f, depth = .6f, driveWeight = 1f)
        val parked = lumenCenter(360f, 800f, frame)
        val moved = lumenCenter(360f, 800f, frame.copy(driftX = -1f, driftY = -1f))
        assertTrue(moved.x < parked.x - 5f)
        assertTrue(moved.y < parked.y - 40f)
        val expectedUnit = minOf(360f * .94f, 800f * 1.12f) * (1f + .6f * .055f) * .88f
        assertEquals(expectedUnit, lumenUnit(360f, 800f, frame), .001f)
    }

    @Test fun cruisingKeepsWholeCharacterIdleMotionWithTheMicrophoneOff() {
        val rig = LumenRig()
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        repeat(60 * 90) { i ->
            val f = rig.step(i / 60f, LumenInput(interaction = InteractionState.MUTED, driveWeight = 1f))
            val p = lumenCenter(360f, 800f, f)
            minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
        }
        assertTrue("drive mode still moves the whole character horizontally", maxX - minX > 20f)
        assertTrue("drive mode still moves the whole character vertically", maxY - minY > 55f)
    }

    @Test fun drivingDoesNotDiscardObservedFaceAttentionOrRestartItsIdleScore() {
        val idle = LumenRig(); val driving = LumenRig()
        repeat(1800) { i ->
            val parked = idle.step(i / 60f, LumenInput())
            val car = driving.step(i / 60f, LumenInput(driveWeight = if (i in 120..900) 1f else 0f))
            assertEquals(parked.driftX, car.driftX, .0001f)
            assertEquals(parked.driftY, car.driftY, .0001f)
        }
        for (animal in AvatarAnimal.entries) {
            val left = settled(LumenInput(-.8f, -.5f, true, animal = animal, driveWeight = 1f))
            val right = settled(LumenInput(.8f, .5f, true, animal = animal, driveWeight = 1f))
            assertTrue("driving face-follow X for $animal", right.driftX - left.driftX > 1.4f)
            assertTrue("driving face-follow Y for $animal", right.driftY - left.driftY > .9f)
            assertTrue(lumenCenter(360f, 800f, right).x > lumenCenter(360f, 800f, left).x + 20f)
        }
    }
}
