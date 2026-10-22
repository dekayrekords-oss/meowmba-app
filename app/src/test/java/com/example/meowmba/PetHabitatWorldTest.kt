package com.example.meowmba

import com.example.meowmba.core.behavior.BehaviorBias
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test

class PetHabitatWorldTest {
    private val bias = BehaviorBias(1f, 1f, 1f, 1f, 1f, 1f, 1f, .5f, 0f)
    private fun advance(world: PetHabitatWorld, command: HabitatCommand, from: Int = 0, frames: Int = 600) {
        repeat(frames) { world.step((from + it) / 60f, command, bias, 360f, 800f) }
    }

    @Test fun duplicateCommandDoesNotResetApproachOrHeldObjectAndPauseIsFrozen() {
        val world = PetHabitatWorld(); val read = HabitatCommand(1, HabitatAction.READ)
        advance(world, read)
        assertEquals("book", world.snapshot().pose.holding)
        assertFalse(world.snapshot().approaching)
        val before = world.snapshot()
        repeat(50) { world.step(599 / 60f, HabitatCommand(2, HabitatAction.RELEASE), bias, 360f, 800f) }
        assertEquals(before, world.snapshot())
        advance(world, read, 600, 60)
        assertEquals("book", world.snapshot().pose.holding)
        assertEquals(1, world.snapshot().objects.count { it.held })
    }

    @Test fun longJumpIsCappedAndBackwardOrInvalidInputDoesNotAdvance() {
        val world = PetHabitatWorld(); val command = HabitatCommand()
        world.step(1f, command, bias, 360f, 800f)
        val first = world.snapshot()
        world.step(200f, command, bias, 360f, 800f)
        assertEquals(.05f, world.snapshot().elapsed - first.elapsed, .00001f)
        val before = world.snapshot()
        world.step(100f, command, bias, 360f, 800f)
        world.step(Float.NaN, command, bias, 360f, 800f)
        world.step(201f, command, bias, 0f, 800f)
        assertEquals(before, world.snapshot())
    }

    @Test fun releaseFallsAndSettlesAndSwitchingPropsHasExactlyOneOwner() {
        val world = PetHabitatWorld()
        advance(world, HabitatCommand(1, HabitatAction.READ))
        advance(world, HabitatCommand(2, HabitatAction.DRINK), 600, 600)
        assertEquals("glass", world.snapshot().pose.holding)
        assertEquals(1, world.snapshot().objects.count { it.held })
        advance(world, HabitatCommand(3, HabitatAction.RELEASE), 1200, 900)
        assertNull(world.snapshot().pose.holding)
        assertTrue(world.snapshot().objects.none { it.held })
        val glass = world.snapshot().objects.first { it.kind == "glass" }
        assertTrue(glass.y == .78f || glass.y == .94f)
        assertEquals(0f, glass.vy, 0f)
    }

    @Test fun nudgeKnocksGlassOffTableAndDoesNotRespawnIt() {
        val world = PetHabitatWorld(); val nudge = HabitatCommand(1, HabitatAction.NUDGE)
        advance(world, nudge, frames = 1500)
        val glass = world.snapshot().objects.first { it.kind == "glass" }
        assertEquals(.94f, glass.y, .0001f)
        assertEquals(0f, glass.vy, 0f)
        advance(world, nudge, 1500, 600)
        assertEquals(glass, world.snapshot().objects.first { it.kind == "glass" })
    }

    @Test fun explorationIsFiniteBoundedAndObjectHitTargetsSelectActions() {
        val world = PetHabitatWorld()
        assertEquals(HabitatAction.DRINK, world.actionAt(.68f * 360, .78f * 800, 360f, 800f))
        assertEquals(HabitatAction.READ, world.actionAt(.28f * 360, .78f * 800, 360f, 800f))
        assertEquals(HabitatAction.PLAY, world.actionAt(.83f * 360, .78f * 800, 360f, 800f))
        assertNull(world.actionAt(0f, 0f, 360f, 800f))
        repeat(6000) { i ->
            val pose = world.step(i / 60f, HabitatCommand(), bias.copy(arousal = Float.NaN), 800f, 360f, Float.NaN)
            assertTrue(pose.x in 0.15f..0.85f); assertTrue(pose.y in 0.20f..0.94f)
            assertTrue(pose.flight in 0f..1f); assertTrue(pose.wingPhase.isFinite())
        }
    }
    @Test fun restFeetMeetTableAndHeldPropTracksActualPainterHandAcrossAspects() {
        for ((w, h) in listOf(360f to 800f, 800f to 360f)) {
            val world = PetHabitatWorld()
            repeat(900) { world.step(it / 60f, HabitatCommand(1, HabitatAction.REST), bias, w, h) }
            val unit = minOf(w, h) * .32f
            assertEquals(.78f, world.snapshot().pose.y + .689f * unit / h, .0001f)
            repeat(900) { world.step((900 + it) / 60f, HabitatCommand(2, HabitatAction.DRINK), bias, w, h) }
            val pose = world.snapshot().pose
            val glass = world.snapshot().objects.first { it.kind == "glass" }
            assertTrue(glass.held)
            assertEquals(pose.x + pose.facing * pose.handX * unit / w, glass.x, .0001f)
            assertEquals(pose.y + (.075f + pose.handDrop + .06f) * unit / h, glass.y, .0001f)
        }
    }

}
