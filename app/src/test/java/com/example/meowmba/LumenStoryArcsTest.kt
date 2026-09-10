package com.example.meowmba

import androidx.compose.ui.geometry.Offset
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LumenStoryArcsTest {
    private fun entry(id: String) = AvatarPerformanceCatalog.resolve(id)!!
    private fun pose(id: String, p: Float) = LumenStoryArcs.sample(id, entry(id).accent, p)!!

    @Test fun `three stories round trip within custom animation limits and recover every keyed channel`() {
        val stories = LumenStoryArcs.ids.map(::entry)
        assertEquals(3, stories.size)
        assertEquals(3, stories.map { it.family }.toSet().size)
        stories.forEach { story ->
            assertEquals("Scenes", story.category)
            assertFalse(story.ambient)
            assertTrue(story.spec.durationMs in 8000..9000)
            assertTrue(story.spec.frames.size <= 16)
            assertEquals(story.spec.frames.first(), story.spec.frames.last().copy(atMs = 0))
            val (parsed, notes) = CustomAnimations.parse(CustomAnimations.toJson(story.spec))
            assertTrue("${story.id}: $notes", notes.isEmpty())
            assertEquals(story.spec, parsed)
            assertEquals(story, AvatarPerformanceCatalog.fromSpec(story.spec))
            val input = lumenPerformanceInput(LumenInput(performing = true), story, 1f)
            assertEquals(0f, input.sceneGazeWeight, 0f)
            assertEquals(0f, input.sceneTravelWeight, 0f)
            assertEquals(0f, input.earAsymmetry, 0f)
            assertEquals(0f, input.lowerLid, 0f)
            assertEquals(0f, input.performanceJaw, 0f)
        }
        assertEquals("mouse_chase", entry("chase a mouse").id)
        assertEquals("book_reading", entry("read a book").id)
        assertEquals("cowboy_hat", entry("wear a hat").id)
    }

    @Test fun `story selection needs its exact identity and expected native prop`() {
        val aliases = mapOf("aussie lost mouse" to "lost_mouse", "find the lost mouse" to "lost_mouse",
            "owl reading realization" to "reading_realization", "reread the book" to "reading_realization",
            "duck hat mishap" to "hat_mishap", "fix your slipping hat" to "hat_mishap")
        aliases.forEach { (alias, id) ->
            assertEquals(id, entry(alias).id)
            assertNull(AvatarPerformanceCatalog.resolve("$alias and send an email"))
        }
        assertNull(LumenStoryArcs.sample("mouse_chase", PerformanceAccent.MOUSE, .4f))
        assertNull(LumenStoryArcs.sample("lost_mouse", PerformanceAccent.BOOK, .4f))
        assertNull(LumenStoryArcs.sample(null, PerformanceAccent.HAT_COWBOY, .4f))
        val original = LumenScenes.gaze(LumenInput(), PerformanceAccent.MOUSE, .4f)
        assertEquals(original, LumenScenes.gaze(LumenInput(), PerformanceAccent.MOUSE, .4f, "mouse_chase"))
    }

    @Test fun `lost mouse disappears for both listening ears and escapes before restrained paws arrive`() {
        val left = pose("lost_mouse", .30f)
        val right = pose("lost_mouse", .45f)
        assertEquals(0f, left.propVisibility, 0f)
        assertEquals(0f, right.propVisibility, 0f)
        assertTrue(left.earAsymmetry < -7f)
        assertTrue(right.earAsymmetry > 7f)
        assertTrue(left.gaze.x < -.24f && right.gaze.x > .24f)
        assertEquals(0f, left.travelWeight, 0f)
        assertEquals(0f, right.travelWeight, 0f)
        assertEquals(1f, pose("lost_mouse", .58f).propVisibility, 0f)
        assertEquals(pose("lost_mouse", .62f).mouse!!.point, pose("lost_mouse", .66f).mouse!!.point)
        val landed = pose("lost_mouse", .755f).mouse!!
        assertTrue(landed.pounce in .5f.. .56f)
        assertTrue("Mouse's body is beyond the rightmost paw", landed.point.x - .048f > .003f + .04f)
        assertEquals(0f, pose("lost_mouse", .84f).mouse!!.pounce, 0f)
        val bounce = entry("lost_mouse").spec.frames.map { it.bodyBounce!! }
        assertTrue(bounce.min() < -.15f)
        assertTrue(bounce.max() in .20f.. .25f)
        assertTrue(bounce.max() < entry("mouse_chase").spec.frames.maxOf { it.bodyBounce!! })
        assertTrue(lumenPerformanceInput(LumenInput(), entry("lost_mouse"), .30f).earAsymmetry < -7f)
        assertTrue(lumenPerformanceInput(LumenInput(), entry("lost_mouse"), .45f).earAsymmetry > 7f)
    }

    @Test fun `owl rereads the same line then holds realization before settling into the book`() {
        assertEquals(pose("reading_realization", .19f).gaze, pose("reading_realization", .36f).gaze)
        assertEquals(pose("reading_realization", .30f).gaze, pose("reading_realization", .46f).gaze)
        assertEquals(pose("reading_realization", .46f).gaze, pose("reading_realization", .51f).gaze)
        assertTrue(pose("reading_realization", .25f).gaze.x > pose("reading_realization", .20f).gaze.x)
        assertTrue(pose("reading_realization", .43f).gaze.x > pose("reading_realization", .37f).gaze.x)
        val aha = pose("reading_realization", .60f)
        assertTrue(aha.gaze.y < 0f)
        assertTrue(aha.jawOpen > .15f)
        assertEquals(aha.gaze, pose("reading_realization", .64f).gaze)
        assertTrue(pose("reading_realization", .78f).gaze.y > .20f)
        assertEquals(0f, pose("reading_realization", .70f).book!!.pageTurn, 0f)
        assertEquals(1f, pose("reading_realization", .87f).book!!.pageTurn, 0f)
        assertEquals(0f, pose("reading_realization", 1f).jawOpen, 0f)
    }

    @Test fun `duck hat descends over eye line holds then recovers before the sheepish glance`() {
        val proud = pose("hat_mishap", .19f)
        val covered = pose("hat_mishap", .37f)
        val wait = pose("hat_mishap", .43f)
        val recovered = pose("hat_mishap", .67f)
        assertTrue(covered.hat!!.anchor.y > proud.hat!!.anchor.y + .22f)
        assertTrue(covered.hat.anchor.y in -.025f..-.015f)
        assertEquals(covered.hat, wait.hat)
        assertTrue(recovered.hat!!.anchor.y < -.24f)
        assertTrue(recovered.gaze.x > .19f)
        assertTrue(pose("hat_mishap", .75f).lowerLid > .15f)
        assertEquals(Offset.Zero, pose("hat_mishap", 1f).gaze)
        assertEquals(0f, pose("hat_mishap", 1f).hat!!.tipDegrees, 0f)
    }

    @Test fun `all arc samples remain continuous bounded and preserve interaction drive and privacy inputs`() {
        for (id in LumenStoryArcs.ids) {
            val story = entry(id)
            var previous: LumenStoryArcs.Pose? = null
            for (i in 0..900) {
                val p = i / 900f
                val arc = pose(id, p)
                assertTrue("$id $p", abs(arc.gaze.x) <= .34f && abs(arc.gaze.y) <= .28f)
                previous?.let { assertTrue("gaze discontinuity $id $p", (it.gaze - arc.gaze).getDistance() < .035f) }
                assertTrue(arc.propVisibility in 0f..1f)
                assertTrue(arc.earAsymmetry in -8f..8f)
                assertTrue(arc.travelWeight in 0f.. .42f)
                val base = LumenInput(cameraEnabled = false, driveWeight = 1f, brace = .6f,
                    interaction = InteractionState.LISTENING, gazeX = .2f, gazeY = -.3f,
                    speaking = .5f, animal = AvatarAnimal.DUCK)
                val input = lumenPerformanceInput(base, story, p)
                assertFalse(input.cameraEnabled)
                assertEquals(base.driveWeight, input.driveWeight, 0f)
                assertEquals(base.brace, input.brace, 0f)
                assertEquals(base.interaction, input.interaction)
                assertEquals(base.animal, input.animal)
                assertEquals(base.speaking, input.speaking, 0f)
                previous = arc
            }
            val base = LumenInput(cameraEnabled = false, driveWeight = 1f)
            for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.1f, 1.1f)) {
                assertEquals(base, lumenPerformanceInput(base, story, bad))
            }
        }
    }
}
