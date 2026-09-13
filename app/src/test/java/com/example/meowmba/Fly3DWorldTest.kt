package com.example.meowmba

import com.example.meowmba.core.behavior.BehaviorBias
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class Fly3DWorldTest {
    private val bias = BehaviorBias(1f, 1f, 1f, 1f, 1f, 1f, 1f, .5f, 0f)

    @Test fun autonomousFlightMovesAllThreeAxesAndStaysBounded() {
        val world = Fly3DWorld()
        val frames = (0..2400).map { world.step(it / 30f, HabitatCommand(), bias) }
        assertTrue(frames.maxOf { it.pose.x } - frames.minOf { it.pose.x } > 2f)
        assertTrue(frames.maxOf { it.pose.y } - frames.minOf { it.pose.y } > .5f)
        assertTrue(frames.maxOf { it.pose.z } - frames.minOf { it.pose.z } > 1f)
        assertTrue(frames.any { abs(it.pose.roll) > 1f })
        frames.forEach {
            val p = it.pose
            assertTrue(p.x in -2.5f..2.5f && p.y in .7f..4f && p.z in -1.5f..1.5f)
            assertTrue(listOf(p.yaw, p.pitch, p.roll, p.vx, p.vy, p.vz, p.handX, p.handY, p.handZ).all { v -> v.isFinite() })
            assertTrue(sqrt(p.vx*p.vx + p.vy*p.vy + p.vz*p.vz) <= 1.151f)
        }
    }

    @Test fun replayIsIdenticalAndPauseDoesNotProcessQueuedActions() {
        val a = Fly3DWorld(); val b = Fly3DWorld()
        repeat(1200) { i ->
            val command = HabitatCommand((i / 300).toLong(), listOf(HabitatAction.EXPLORE, HabitatAction.READ, HabitatAction.RELEASE, HabitatAction.PLAY)[i / 300])
            assertEquals(a.step(i / 30f, command, bias), b.step(i / 30f, command, bias))
        }
        val paused = a.snapshot()
        assertEquals(paused, a.step(1199 / 30f, HabitatCommand(55, HabitatAction.FEED), bias))
        assertEquals(paused, a.step(Float.NaN, HabitatCommand(55, HabitatAction.FEED), bias))
        assertEquals(paused, a.step(0f, HabitatCommand(55, HabitatAction.FEED), bias))
        val resumed = a.step(90000f, HabitatCommand(3, HabitatAction.PLAY), bias)
        assertTrue(resumed.elapsed - paused.elapsed <= .051f)
        assertTrue(abs(resumed.pose.x - paused.pose.x) < .08f)
    }

    @Test fun readAndDrinkApproachBeforeGraspAndNeverTeleport() {
        for ((action, kind) in listOf(HabitatAction.READ to "book", HabitatAction.DRINK to "glass")) {
            val world = Fly3DWorld()
            var previous = world.snapshot().props.first { it.kind == kind }
            var heldAt = -1
            repeat(900) { i ->
                val frame = world.step(i / 30f, HabitatCommand(1, action), bias)
                val prop = frame.props.first { it.kind == kind }
                val moved = sqrt((prop.x-previous.x)*(prop.x-previous.x) + (prop.y-previous.y)*(prop.y-previous.y) + (prop.z-previous.z)*(prop.z-previous.z))
                assertTrue("pickup motion continuous: $kind", moved < .06f)
                if (prop.held && heldAt < 0) {
                    heldAt = i
                    assertTrue(abs(frame.pose.handX - prop.x) < .03f)
                    assertTrue(abs(frame.pose.handY - (prop.y + .1f)) < .03f)
                    assertTrue(abs(frame.pose.handZ - prop.z) < .03f)
                }
                previous = prop
            }
            assertTrue("approach then grasp $kind", heldAt > 30)
            val held = world.snapshot()
            assertEquals(kind, held.pose.holding)
            val released = world.step(30.1f, HabitatCommand(2, HabitatAction.RELEASE), bias)
            assertNull(released.pose.holding)
            assertTrue(released.props.none { it.held })
            assertTrue(abs(released.props.first { it.kind == kind }.x - previous.x) < .05f)
        }
    }

    @Test fun handSpeedStaysBoundedThroughPickupAtLowRefreshRate() {
        val world = Fly3DWorld()
        var previous = world.snapshot().pose
        var sawHolding = false
        repeat(350) { i ->
            val frame = world.step(i * .05f, HabitatCommand(1, HabitatAction.READ), bias)
            val pose = frame.pose
            val distance = sqrt((pose.handX-previous.handX)*(pose.handX-previous.handX) +
                (pose.handY-previous.handY)*(pose.handY-previous.handY) +
                (pose.handZ-previous.handZ)*(pose.handZ-previous.handZ))
            assertTrue("3D grip speed <=1.1 units/s", distance <= .0551f)
            sawHolding = sawHolding || pose.holding == "book"
            previous = pose
        }
        assertTrue("the speed cap must not prevent contact", sawHolding)
    }

    @Test fun landWalkTakeOffAndNudgeKeepPropsIndependent() {
        val world = Fly3DWorld()
        repeat(400) { world.step(it / 30f, HabitatCommand(1, HabitatAction.REST), bias) }
        assertEquals(Fly3DWorld.TABLE_CHEST, world.snapshot().pose.y, .03f)
        assertTrue(world.snapshot().pose.flight < .1f)
        val walkingPhases = mutableListOf<Float>()
        repeat(450) { i -> walkingPhases += world.step((400+i) / 30f, HabitatCommand(2, HabitatAction.READ), bias).pose.walkPhase }
        assertTrue(walkingPhases.max() - walkingPhases.min() > .4f)
        repeat(450) { i -> world.step((850+i) / 30f, HabitatCommand(3, HabitatAction.NUDGE), bias) }
        val nudged = world.snapshot().props.first { it.kind == "ball" }
        assertTrue(nudged.x > 1.65f)
        assertTrue(nudged.y < .8f)
        repeat(200) { i -> world.step((1300+i) / 30f, HabitatCommand(4, HabitatAction.EXPLORE), bias) }
        assertTrue(world.snapshot().pose.y > 1.6f)
        assertTrue(world.snapshot().pose.flight > .9f)
    }

    @Test fun touchTargetsAreBoundedAndNonfiniteInputIsSafe() {
        assertEquals(Fly3DTarget(0f, 2.65f, 0f), Fly3DWorld.normalizedTarget(Float.NaN, Float.NaN, Float.NaN))
        val target = Fly3DWorld.normalizedTarget(-10f, 15f, 80f)
        assertEquals(-2.3f, target.x, 0f)
        assertEquals(1.5f, target.y, .00001f)
        assertEquals(1.3f, target.z, 0f)
    }
}
