package com.example.meowmba

import com.example.meowmba.core.robot.BodyMotionMap
import com.example.meowmba.core.robot.BodyProfile
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.core.robot.EmotionMap
import com.example.meowmba.ui.companion.Body
import com.example.meowmba.ui.companion.ComposedExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The emotional song-and-dance of a WALL-E/E.M.O. has to mean the same thing
 * on the base as it does on the face. These tests pin what each body character
 * is allowed to make the base do — and that "allowed" is the whole point:
 * DESK never drives, no matter what phrase is being felt.
 */
class EmotionMapTest {

    private fun expr(
        body: Body = Body.DRIFT,
        durationSeconds: Float = 1.6f,
        bodyAmp: Float = 1f,
    ) = ComposedExpression(body = body, durationSeconds = durationSeconds, bodyAmp = bodyAmp)

    private fun verbs(
        body: Body,
        profile: BodyProfile = BodyProfile.DESK,
        bodyAmp: Float = 1f,
    ): List<BodyVerb> =
        EmotionMap.cues(expr(body, bodyAmp = bodyAmp), profile).map { it.verb }

    @Test
    fun `happy bobs the neck up and lights up`() {
        val v = verbs(Body.BOB)
        val neck = v.filterIsInstance<BodyVerb.Neck>().first()
        assertTrue("happy should look up, got ${neck.pitch}", neck.pitch < 0f)
        assertTrue(v.contains(BodyVerb.Light(true)))
        assertTrue(v.contains(BodyVerb.Light(false))) // settles before Brake
    }

    @Test
    fun `sinking droops the neck and stays dark`() {
        val v = verbs(Body.SINK)
        val neck = v.filterIsInstance<BodyVerb.Neck>().first()
        assertTrue("low spirits should look down, got ${neck.pitch}", neck.pitch > 0f)
        assertFalse(v.contains(BodyVerb.Light(true)))
    }

    @Test
    fun `lean-in becomes a curious turn toward the subject`() {
        val v = verbs(Body.LEAN_IN)
        val turn = v.filterIsInstance<BodyVerb.Turn>().first()
        assertEquals(-1, turn.sign)
    }

    @Test
    fun `a wiggle is a dance the base can feel`() {
        val v = verbs(Body.WIGGLE)
        assertTrue(v.any { it is BodyVerb.Turn })
    }

    @Test
    fun `every expression ends braked`() {
        Body.values().forEach { body ->
            val v = verbs(body)
            assertEquals("$body must end braked", BodyVerb.Brake, v.last())
        }
    }

    @Test
    fun `every expression leaves everything centred before the brake`() {
        Body.values().forEach { body ->
            // The settle frame is emitted just before the end, so the base is
            // neutral when the clip stops — it never freezes mid-lean.
            val cues = EmotionMap.cues(expr(body), BodyProfile.DESK)
            val settle = cues.filter { it.verb is BodyVerb.Neck }.takeLast(1)
            val lastNeck = settle.firstOrNull()?.verb as? BodyVerb.Neck
            assertTrue("$body should settle the neck back to centre", lastNeck != null)
        }
    }

    // ── The safety property, inherited, so it is pinned here again ──────

    @Test
    fun `desk profile never drives, across every body character`() {
        Body.values().forEach { body ->
            val v = verbs(body, BodyProfile.DESK)
            assertFalse(
                "$body produced a Drive on the desk",
                v.any { it is BodyVerb.Drive },
            )
        }
    }

    @Test
    fun `floor profile lets a bounce drive`() {
        val v = verbs(Body.BOB, BodyProfile.FLOOR)
        assertTrue("happy bounce should become a drive pulse on the floor", v.any { it is BodyVerb.Drive })
    }

    @Test
    fun `a zero-bodyAmp expression is still braked, never driven`() {
        val v = verbs(Body.BOB, BodyProfile.FLOOR, bodyAmp = 0f)
        assertFalse("zero vitality must not drive", v.any { it is BodyVerb.Drive })
        assertEquals(BodyVerb.Brake, v.last())
    }

    @Test
    fun `anger has no quiet light and shivers`() {
        // SHAKE_V (a "hard no") is rendered from the face; the base shivers.
        val v = verbs(Body.SHAKE_V, BodyProfile.DESK)
        assertTrue(v.any { it is BodyVerb.Turn })
        assertFalse(v.contains(BodyVerb.Light(true)))
    }
}