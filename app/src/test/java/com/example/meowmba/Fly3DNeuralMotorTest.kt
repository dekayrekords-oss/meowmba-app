package com.example.meowmba

import com.example.meowmba.core.behavior.BehaviorBias
import com.example.meowmba.core.neural.FlyMotorIntent
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class Fly3DNeuralMotorTest {
    private val bias = BehaviorBias(1f, 1f, 1f, 1f, 1f, 1f, 1f, .5f, 0f)
    private fun run(motor: FlyMotorIntent, frames: Int = 300): Fly3DWorldSnapshot {
        val world = Fly3DWorld()
        repeat(frames) { world.step(it / 30f, HabitatCommand(), bias, motorIntent = motor) }
        return world.snapshot()
    }

    @Test fun measuredForwardBackwardHaltAndQuietHaveDistinctEffects() {
        val forward = run(FlyMotorIntent(true, forward = 1f)).pose
        val backward = run(FlyMotorIntent(true, backward = 1f)).pose
        val halt = run(FlyMotorIntent(true, forward = 1f, halt = 1f)).pose
        assertTrue(forward.z > 1f); assertTrue(backward.z < -1f)
        assertEquals(0f, halt.z, .001f)
        val quiet = run(FlyMotorIntent(active = true)).pose
        assertEquals(0f, quiet.x, 0f); assertEquals(0f, quiet.z, 0f)
        assertEquals(2f, quiet.y, 0f)
    }

    @Test fun measuredLandingGroomsOnContactAndTakeoffLiftsContinuously() {
        val world = Fly3DWorld()
        repeat(300) { world.step(it / 30f, HabitatCommand(), bias,
            motorIntent = FlyMotorIntent(true, landing = 1f, grooming = .8f)) }
        assertEquals(Fly3DWorld.TABLE_CHEST, world.snapshot().pose.y, .03f)
        assertTrue(world.snapshot().pose.grooming > .7f)
        var previous = world.snapshot().pose
        repeat(200) {
            val pose = world.step((300+it) / 30f, HabitatCommand(), bias,
                motorIntent = FlyMotorIntent(true, takeoff = 1f)).pose
            assertTrue(abs(pose.y - previous.y) < .04f); previous = pose
        }
        assertTrue(previous.y > 3f); assertTrue(previous.grooming < .01f)
    }

    @Test fun measuredTurnIsBoundedDeterministicAndFrozenAtRepeatedTime() {
        val a = Fly3DWorld(); val b = Fly3DWorld()
        val motor = FlyMotorIntent(true, turn = 1f, forward = 1f, takeoff = 1f)
        repeat(1500) {
            val frame = a.step(it / 30f, HabitatCommand(), bias, motorIntent = motor)
            assertEquals(frame, b.step(it / 30f, HabitatCommand(), bias, motorIntent = motor))
            val p = frame.pose
            assertTrue(p.x in -2.5f..2.5f && p.z in -1.5f..1.5f && p.y in .55f..4f)
            assertTrue(sqrt(p.vx*p.vx+p.vy*p.vy+p.vz*p.vz) <= 1.151f)
            assertEquals(frame, a.step(it / 30f, HabitatCommand(77, HabitatAction.FEED), bias, motorIntent = motor))
        }
        assertTrue(abs(a.snapshot().pose.yaw) > 10f)
    }

    @Test fun explicitPropsRestAndTouchKeepPriorityOverNeuralProposals() {
        val aggressive = FlyMotorIntent(true, turn = 1f, forward = 1f, takeoff = 1f, escape = 1f, grooming = 1f)
        for (action in listOf(HabitatAction.READ, HabitatAction.FEED, HabitatAction.REST)) {
            val a = Fly3DWorld(); val b = Fly3DWorld()
            repeat(300) { i ->
                assertEquals(a.step(i / 30f, HabitatCommand(1, action), bias),
                    b.step(i / 30f, HabitatCommand(1, action), bias, motorIntent = aggressive))
            }
        }
        val a = Fly3DWorld(); val b = Fly3DWorld(); a.tap(.8f, .3f); b.tap(.8f, .3f)
        repeat(100) { i -> assertEquals(a.step(i / 30f, HabitatCommand(), bias),
            b.step(i / 30f, HabitatCommand(), bias, motorIntent = aggressive)) }
    }

    @Test fun inactiveAndNonFiniteIntentsPreserveExistingFlight() {
        assertEquals(run(FlyMotorIntent.NONE), run(FlyMotorIntent(forward = 1f)))
        assertEquals(run(FlyMotorIntent.NONE), run(FlyMotorIntent(true, turn = Float.NaN)))
        val bounded = FlyMotorIntent(true, turn = 8f, forward = -5f, jump = 10f).bounded()
        assertEquals(1f, bounded.turn); assertEquals(0f, bounded.forward); assertEquals(1f, bounded.jump)
    }
}
