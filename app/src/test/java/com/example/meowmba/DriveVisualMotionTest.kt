package com.example.meowmba

import com.example.meowmba.ui.companion.*
import com.example.meowmba.core.sensor.MotionMath
import com.example.meowmba.core.car.CarDynamics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DriveVisualMotionTest {
    private fun sample(rig: DriveVisualRig, i: Int, x: Float = 0f, y: Float = 0f,
        driving: Boolean = true, stamp: Long? = null, hz: Int = 60): DriveVisualFrame {
        val now = 1000L + i * 1000L / hz
        return rig.step(i.toFloat() / hz, now, stamp ?: now, driving, x, y)
    }

    @Test fun smallRoadVibrationIsCompletelyQuiet() {
        val rig = DriveVisualRig()
        repeat(180) { i ->
            val f = sample(rig, i, if (i % 2 == 0) 0.06f else -0.06f, 0.07f)
            assertEquals(0f, f.lean, 0f); assertEquals(0f, f.lurch, 0f)
            assertEquals(0f, f.brace, 0f)
        }
    }

    @Test fun aShortHardBrakeIsVisibleAndSettlesWithoutOvershoot() {
        val rig = DriveVisualRig()
        repeat(60) { sample(rig, it) }
        var peak = 0f; var brace = 0f; var f = DriveVisualFrame()
        for (i in 60..180) {
            f = sample(rig, i, y = if (i < 75) 1f else 0f)
            peak = maxOf(peak, f.lurch); brace = maxOf(brace, f.brace)
            assertTrue(f.lurch >= 0f && f.lurch <= 1f)
        }
        assertTrue(peak > 0.8f); assertTrue(brace > 0.8f)
        assertTrue(f.lurch < 0.001f && f.brace < 0.002f)
    }

    @Test fun leftRightReversalPreservesVelocityButChangesDirectionPromptly() {
        val rig = DriveVisualRig()
        repeat(90) { sample(rig, it, 1f) }
        val before = sample(rig, 90, 1f)
        val first = sample(rig, 91, -1f)
        assertTrue(first.lean < before.lean && first.lean > 0.9f)
        var f = first
        for (i in 92..112) f = sample(rig, i, -1f)
        assertTrue(f.lean < -0.8f)
        assertEquals(0f, f.lurch, 0f)
    }

    @Test fun stoppedSensorFeedRecoversWithoutAnotherEmission() {
        val rig = DriveVisualRig()
        repeat(90) { sample(rig, it, 1f, 1f) }
        var f = DriveVisualFrame()
        for (i in 90..260) f = sample(rig, i, 1f, 1f, stamp = 2483L)
        assertTrue(abs(f.lean) < 0.001f && abs(f.lurch) < 0.001f && f.brace < 0.001f)
    }

    @Test fun wholeStageTiltAlsoExpiresAndSettlesWithoutSensorEmissions() {
        val rig = DriveVisualRig(); var f = DriveVisualFrame()
        repeat(90) { i -> f = rig.step(i / 60f, 1000L + i * 17, 1000L + i * 17, true, 0f, 0f, 14f) }
        assertTrue(f.tilt > 13.9f)
        for (i in 90..260) f = rig.step(i / 60f, 1000L + i * 17, 2513L, true, 0f, 0f, 14f)
        assertTrue(abs(f.tilt) < 0.001f)
    }

    @Test fun unavailableFutureAndInvalidInputsCannotMoveFace() {
        for (stamp in listOf(0L, 100_000L)) {
            val rig = DriveVisualRig()
            repeat(90) { i ->
                val f = sample(rig, i, 1f, 1f, stamp = stamp)
                assertEquals(0f, f.lean, 0f); assertEquals(0f, f.lurch, 0f)
            }
        }
        val rig = DriveVisualRig()
        repeat(90) { i ->
            val f = sample(rig, i, Float.NaN, Float.POSITIVE_INFINITY)
            assertEquals(0f, f.lean, 0f); assertEquals(0f, f.lurch, 0f)
        }
    }

    @Test fun frozenClockDoesNotAdvance() {
        val rig = DriveVisualRig()
        val before = sample(rig, 0)
        repeat(60) { assertEquals(before, rig.step(0f, 1000, 1000, true, 1f, 1f)) }
    }

    @Test fun driveModeOffRemovesBracingAndDriveAmplification() {
        val rig = DriveVisualRig()
        repeat(90) { sample(rig, it, 1f, 1f) }
        var f = DriveVisualFrame()
        for (i in 90..210) f = sample(rig, i, driving = false)
        assertTrue(f.driving < 0.001f && f.brace < 0.001f && f.lean < 0.001f)
    }

    @Test fun supportedRefreshRatesConverge() {
        fun at(hz: Int): DriveVisualFrame {
            val rig = DriveVisualRig(); var f = DriveVisualFrame()
            repeat(hz * 2) { f = sample(rig, it, -0.8f, 0.6f, hz = hz) }
            return f
        }
        val base = at(120)
        for (hz in listOf(20, 30, 60, 90)) {
            val f = at(hz)
            assertEquals(base.lean, f.lean, 0.001f)
            assertEquals(base.lurch, f.lurch, 0.001f)
            assertEquals(base.brace, f.brace, 0.001f)
        }
    }

    @Test fun landscapeMountDoesNotAddPermanentLean() {
        for ((angle, rotation) in listOf(0f to 0, -90f to 1, -180f to 2, 90f to 3)) {
            assertEquals(0f, MotionMath.displayRelativeLean(angle, rotation), 0.001f)
            assertEquals(8f, MotionMath.displayRelativeLean(angle + 8f, rotation), 0.001f)
        }
        assertNull(MotionMath.screenLeanDeg(Float.NaN, 1f))
    }

    @Test fun inertialDisplacementOpposesVehicleAccelerationInEitherMount() {
        // Phone's back faces windshield; braking is +device Z in either rotation.
        for (gravity in listOf(floatArrayOf(0f, 9.80665f, 0f), floatArrayOf(9.80665f, 0f, 0f))) {
            val brake = CarDynamics.decompose(floatArrayOf(0f, 0f, 5.884f), gravity)
            assertTrue(-brake.longitudinalG / 0.6f > 0.99f)
        }
        val turn = CarDynamics.decompose(floatArrayOf(5.884f, 0f, 0f), floatArrayOf(0f, 9.80665f, 0f))
        assertTrue(-turn.lateralG / 0.6f < -0.99f)
    }

    @Test fun lumenBrakesInDepthWithAlertEyesAndEarFollowThrough() {
        val rig = LumenRig()
        val input = LumenInput(0f, 0f, true, animal = AvatarAnimal.CAT,
            lurch = 1f, lean = -0.8f, driveWeight = 1f, brace = 1f, speaking = 0.7f)
        rig.step(0f, input); var f = LumenFrame()
        repeat(120) { f = rig.step((it + 1f) / 60, input) }
        assertTrue(f.headY > 0.7f && f.depth > 0.99f)
        assertTrue(f.headX < -0.6f && f.roll < -8f)
        assertTrue(f.leftOpen > 1.18f && f.rightOpen > 1.18f)
        assertTrue(f.earLift < -6f)
        assertEquals(0.7f, f.mouthOpen, 0.001f)
        assertEquals(0f, f.gazeX, 0.001f)
    }
}
