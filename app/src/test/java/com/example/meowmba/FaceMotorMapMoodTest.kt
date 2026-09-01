package com.example.meowmba

import com.example.meowmba.core.personality.Mood
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.core.robot.FaceMotorMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mood now produces a resting pose in the base's own verbs rather than servo
 * angles for ears and a tail the hardware does not have.
 */
class FaceMotorMapMoodTest {

    @Test
    fun `every mood produces a resting pose and an expression`() {
        Mood.values().forEach { mood ->
            val (verbs, expression) = FaceMotorMap.mapMood(mood)
            assertTrue("$mood produced no verbs", verbs.isNotEmpty())
            assertTrue("$mood produced a blank expression", expression.isNotBlank())
        }
    }

    @Test
    fun `a resting pose never drives, in any mood`() {
        Mood.values().forEach { mood ->
            val (verbs, _) = FaceMotorMap.mapMood(mood)
            assertFalse(
                "$mood produced a Drive in a resting pose",
                verbs.any { it is BodyVerb.Drive },
            )
        }
    }

    @Test
    fun `sleepy droops the neck and dims the light`() {
        val (verbs, expression) = FaceMotorMap.mapMood(Mood.SLEEPY)
        assertEquals("sleepy", expression)
        val neck = verbs.filterIsInstance<BodyVerb.Neck>().single()
        assertTrue("sleepy should look down", neck.pitch > 0f)
        assertTrue(verbs.contains(BodyVerb.Light(false)))
    }

    @Test
    fun `excited lifts the neck and lights up`() {
        val (verbs, expression) = FaceMotorMap.mapMood(Mood.EXCITED)
        assertEquals("happy", expression)
        val neck = verbs.filterIsInstance<BodyVerb.Neck>().single()
        assertTrue("excited should look up", neck.pitch < 0f)
        assertTrue(verbs.contains(BodyVerb.Light(true)))
    }

    @Test
    fun `content rests at center`() {
        val (verbs, expression) = FaceMotorMap.mapMood(Mood.CONTENT)
        assertEquals("content", expression)
        assertEquals(0f, verbs.filterIsInstance<BodyVerb.Neck>().single().pitch, 0.001f)
    }
}