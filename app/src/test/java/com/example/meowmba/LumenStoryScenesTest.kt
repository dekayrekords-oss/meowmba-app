package com.example.meowmba

import androidx.compose.ui.geometry.Offset
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LumenStoryScenesTest {
    private val ids = setOf("mouse_chase", "book_reading", "cowboy_hat", "wizard_hat", "painter_beret")
    private val entries get() = AvatarPerformanceCatalog.entries.filter { it.id in ids }

    @Test fun `five original stories have distinct props and recover to full neutral`() {
        assertEquals(5, entries.size)
        assertEquals(LumenStoryTimeline.accents, entries.map { it.accent }.toSet())
        assertEquals(5, entries.map { it.spec.frames.map { frame -> frame.copy(atMs = 0) } }.toSet().size)
        entries.forEach { entry ->
            assertEquals("Scenes", entry.category)
            assertFalse(entry.ambient)
            assertTrue(entry.spec.durationMs in 5000..6000)
            assertEquals(entry.spec.frames.first(), entry.spec.frames.last().copy(atMs = 0))
            assertEquals(0f, LumenScenes.envelope(0f), 0f)
            assertEquals(0f, LumenScenes.envelope(1f), 0f)
            val recovered = lumenPerformanceInput(LumenInput(performing = true), entry, 1f)
            assertEquals(0f, recovered.sceneGazeWeight, 0f)
            assertEquals(entry, AvatarPerformanceCatalog.fromSpec(entry.spec))
            val (roundTrip, notes) = CustomAnimations.parse(CustomAnimations.toJson(entry.spec))
            assertTrue("${entry.id}: $notes", notes.isEmpty())
            assertEquals(entry.spec, roundTrip)
        }
        assertEquals(setOf("wardrobe"), entries.filter { it.accent in LumenStoryTimeline.hats }.map { it.family }.toSet())
    }

    @Test fun `natural requests resolve to the intended native story`() {
        val aliases = mapOf(
            "chase a mouse" to "mouse_chase", "follow a mouse" to "mouse_chase",
            "read a book" to "book_reading", "read your book" to "book_reading",
            "wear a hat" to "cowboy_hat", "wear a cowboy hat" to "cowboy_hat",
            "tip your hat" to "cowboy_hat", "wear a wizard hat" to "wizard_hat",
            "wear a beret" to "painter_beret", "put on a painter hat" to "painter_beret",
        )
        aliases.forEach { (phrase, expected) ->
            assertEquals(phrase, expected, AvatarPerformanceCatalog.resolve(phrase)?.id)
            assertNull(AvatarPerformanceCatalog.resolve("$phrase and send an email"))
        }
    }

    @Test fun `story targets stay finite bounded and release gaze after malformed progress`() {
        for (accent in LumenStoryTimeline.accents) {
            val targets = mutableListOf<Offset>()
            for (i in 0..600) {
                val p = i / 600f
                val target = LumenScenes.target(accent, p)
                assertTrue(accent.name, target.x.isFinite() && target.y.isFinite())
                assertTrue(accent.name, abs(target.x) <= .37f && abs(target.y) <= .36f)
                val input = LumenScenes.gaze(LumenInput(cameraEnabled = false), accent, p)
                assertFalse(input.cameraEnabled)
                assertTrue(input.sceneGazeX!! in -1f..1f)
                assertTrue(input.sceneGazeY!! in -1f..1f)
                assertTrue(input.sceneGazeWeight in 0f..1f)
                targets += target
            }
            assertTrue(accent.name, targets.toSet().size > 25)
            for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                val input = LumenScenes.gaze(LumenInput(), accent, bad)
                assertTrue(input.sceneGazeX!!.isFinite())
                assertTrue(input.sceneGazeY!!.isFinite())
                assertEquals(0f, input.sceneGazeWeight, 0f)
            }
        }
    }

    @Test fun `mouse changes direction then escapes beyond the playful pounce`() {
        val chase = AvatarPerformanceCatalog.resolve("mouse_chase")!!
        val start = LumenStoryTimeline.mouse(.12f)
        val reverse = LumenStoryTimeline.mouse(.42f)
        val crouch = LumenStoryTimeline.mouse(.47f)
        val launch = LumenStoryTimeline.mouse(.55f)
        val escape = LumenStoryTimeline.mouse(.75f)
        assertEquals(1f, start.facing, 0f)
        assertEquals(-1f, reverse.facing, 0f)
        assertTrue(launch.pounce > crouch.pounce)
        assertTrue(escape.point.x > launch.point.x + .20f)
        assertEquals(0f, escape.pounce, 0f)
        assertTrue(chase.spec.frames.any { it.bodyBounce!! < -.3f })
        assertTrue(chase.spec.frames.any { it.bodyBounce!! > .4f })
        assertEquals(.75f, LumenScenes.gaze(LumenInput(), PerformanceAccent.MOUSE, .5f).sceneTravelWeight, 0f)
        for (other in LumenStoryTimeline.accents - PerformanceAccent.MOUSE)
            assertEquals(0f, LumenScenes.gaze(LumenInput(), other, .5f).sceneTravelWeight, 0f)
    }

    @Test fun `book opens scans two lines turns one sheet and settles`() {
        assertEquals(0f, LumenStoryTimeline.book(0f).opening, 0f)
        assertEquals(1f, LumenStoryTimeline.book(.25f).opening, 0f)
        assertTrue(LumenStoryTimeline.book(.32f).gaze.x > LumenStoryTimeline.book(.23f).gaze.x)
        assertTrue(LumenStoryTimeline.book(.40f).gaze.y > LumenStoryTimeline.book(.25f).gaze.y)
        assertEquals(0f, LumenStoryTimeline.book(.48f).pageTurn, 0f)
        assertEquals(.5f, LumenStoryTimeline.book(.59f).pageTurn, .001f)
        assertEquals(1f, LumenStoryTimeline.book(.70f).pageTurn, 0f)
        assertTrue(LumenStoryTimeline.book(.59f).gaze.y < LumenStoryTimeline.book(.49f).gaze.y)
        assertEquals(LumenStoryTimeline.book(.59f).gaze, LumenScenes.target(PerformanceAccent.BOOK, .59f))
    }

    @Test fun `three headwear silhouettes use distinct anchors and acting targets`() {
        val cowboy = LumenStoryTimeline.hat(PerformanceAccent.HAT_COWBOY, .4f)
        val wizard = LumenStoryTimeline.hat(PerformanceAccent.HAT_WIZARD, .4f)
        val beret = LumenStoryTimeline.hat(PerformanceAccent.HAT_BERET, .4f)
        assertEquals(3, listOf(cowboy.anchor, wizard.anchor, beret.anchor).toSet().size)
        assertTrue(cowboy.tipDegrees < -6f)
        assertEquals(0f, LumenStoryTimeline.hat(PerformanceAccent.HAT_COWBOY, 1f).tipDegrees, 0f)
        assertTrue(wizard.focus.y < -.2f)
        assertTrue(beret.focus.y > .05f)
        for (hat in LumenStoryTimeline.hats) for (i in 0..100) {
            val pose = LumenStoryTimeline.hat(hat, i / 100f)
            assertTrue(pose.anchor.y in -.27f..-.22f)
            assertTrue(abs(pose.anchor.x) < .03f)
            assertTrue(pose.tipDegrees.isFinite() && abs(pose.tipDegrees) <= 7f)
        }
    }
}
