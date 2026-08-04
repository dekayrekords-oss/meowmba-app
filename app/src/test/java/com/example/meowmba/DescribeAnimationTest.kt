package com.example.meowmba

import com.example.meowmba.core.agent.tools.AnimationDescriber
import com.example.meowmba.core.agent.tools.DescribeAnimationTool
import com.example.meowmba.core.agent.tools.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the keyword-to-pattern engine and the describe_animation tool so the
 * companion can reliably turn natural language into animations without model
 * calls or fabricated results.
 */
class DescribeAnimationTest {

    // ── AnimationDescriber ──────────────────────────────────────────────

    @Test
    fun `exact keyword match returns a spec`() {
        val spec = AnimationDescriber.describe("stretch")!!
        assertEquals("stretch", spec.name)
        assertTrue(spec.durationMs > 0)
        assertTrue(spec.frames.isNotEmpty())
    }

    @Test
    fun `synonym resolves to canonical keyword`() {
        val spec = AnimationDescriber.describe("drowsy")!!
        assertEquals("sleepy", spec.name)
    }

    @Test
    fun `multiple keywords picks highest weight pattern`() {
        // "bop" → bounce (0.8), "wiggle" → shake (0.9) → shake wins
        val spec = AnimationDescriber.describe("bop wiggle")!!
        assertTrue(spec.name.contains("shake"))
        assertTrue(spec.durationMs > 0)
    }

    @Test
    fun `unrecognized text returns null`() {
        assertNull(AnimationDescriber.describe("quantum computing"))
        assertNull(AnimationDescriber.describe(""))
        assertNull(AnimationDescriber.describe("   "))
    }

    @Test
    fun `case and punctuation are ignored`() {
        val spec = AnimationDescriber.describe("HAPPY!")!!
        assertEquals("happy", spec.name)
    }

    @Test
    fun `every built-in keyword resolves`() {
        val keywords = listOf(
            "stretch", "jump", "bounce", "shake", "nod", "tilt",
            "happy", "sad", "angry", "scared", "excited", "curious", "sleepy",
            "yawn", "purr", "sneeze", "paw", "lick", "dance", "wave",
            "laugh", "cry", "glow", "spin"
        )
        keywords.forEach { kw ->
            assertNotNull("expected match for '$kw'", AnimationDescriber.describe(kw))
        }
    }

    @Test
    fun `all synonyms resolve`() {
        val synonyms = mapOf(
            "bop" to "bounce", "wiggle" to "shake", "drowsy" to "sleepy",
            "frightened" to "scared", "pissed" to "angry", "grin" to "happy",
            "playful" to "excited"
        )
        synonyms.forEach { (syn, canonical) ->
            val spec = AnimationDescriber.describe(syn)!!
            assertEquals(syn, canonical, spec.name)
        }
    }

    @Test
    fun `frames are within valid ranges`() {
        val spec = AnimationDescriber.describe("angry")!!
        spec.frames.forEach { frame ->
            frame.eyeOpen?.let { assertTrue("eyeOpen $it", it in 0f..2f) }
            frame.pupilSize?.let { assertTrue("pupilSize $it", it in 0.05f..1f) }
            frame.headTiltDeg?.let { assertTrue("headTilt $it", it in -30f..30f) }
            frame.earPerk?.let { assertTrue("earPerk $it", it in 0.3f..2f) }
            frame.earDroopDeg?.let { assertTrue("earDroop $it", it in 0f..30f) }
            frame.bodyBounce?.let { assertTrue("bodyBounce $it", it in -1f..1f) }
            frame.tailWag?.let { assertTrue("tailWag $it", it in -1f..1f) }
            frame.tongueOut?.let { assertTrue("tongueOut $it", it in 0f..1f) }
            frame.sparkle?.let { assertTrue("sparkle $it", it in 0f..1f) }
        }
    }

    // ── DescribeAnimationTool ───────────────────────────────────────────

    @Test
    fun `tool plays spec and returns success`() = runTest {
        val played = mutableListOf<com.example.meowmba.ui.companion.CustomAnimationSpec>()
        val tool = DescribeAnimationTool { spec -> played.add(spec) }

        val result = tool.execute(mapOf("description" to "yawn"))
        assertTrue(result is ToolResult.Success)
        assertEquals(1, played.size)
        assertEquals("yawn", played[0].name)
    }

    @Test
    fun `tool returns failure for null description`() = runTest {
        val tool = DescribeAnimationTool { }
        val result = tool.execute(emptyMap())
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun `tool returns failure for empty description`() = runTest {
        val tool = DescribeAnimationTool { }
        val result = tool.execute(mapOf("description" to "   "))
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun `tool returns failure for unrecognized description`() = runTest {
        val tool = DescribeAnimationTool { }
        val result = tool.execute(mapOf("description" to "do a barrel roll"))
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun `tool definition is safe and phone-only`() {
        val tool = DescribeAnimationTool { }
        assertEquals("avatar.describe_animation", tool.definition.id)
        assertEquals(com.example.meowmba.core.agent.tools.RiskLevel.SAFE, tool.definition.riskLevel)
        assertEquals(
            setOf(com.example.meowmba.core.agent.tools.ToolEnvironment.PHONE),
            tool.definition.environments
        )
    }
}
