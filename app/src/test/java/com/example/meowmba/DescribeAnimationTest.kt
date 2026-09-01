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

    // ── Performances the user asked for by name ──────────────────────────

    @Test
    fun `acting like a dog resolves and actually pants`() {
        val spec = AnimationDescriber.describe("dog")!!
        assertEquals("dog", spec.name)
        // The pant is a pulse, not one held-open mouth (a held mouth reads as a
        // yawn). Assert the tongue goes out AND comes back, more than once.
        val tongue = spec.frames.map { it.tongueOut }.filterNotNull()
        assertTrue("dog should show a tongue", tongue.any { it > 0.6f })
        assertTrue("dog should pulse, not hold", tongue.count { it > 0.6f } >= 2)
        assertTrue("dog should retract the tongue", tongue.any { it < 0.3f })
        // A wagging tail, sweeping both directions.
        val wag = spec.frames.map { it.tailWag }.filterNotNull()
        assertTrue("tail should sweep right", wag.any { it > 0.5f })
        assertTrue("tail should sweep left", wag.any { it < -0.5f })
        // Starts and ends neutral so the cross-fade has somewhere to land.
        assertEquals(0f, spec.frames.first().tongueOut ?: -1f, 0.001f)
        assertEquals(0f, spec.frames.last().tongueOut ?: -1f, 0.001f)
    }

    @Test
    fun `puppy and pant are synonyms for dog`() {
        assertEquals("dog", AnimationDescriber.describe("puppy")!!.name)
        assertEquals("dog", AnimationDescriber.describe("pant")!!.name)
        assertEquals("dog", AnimationDescriber.describe("panting")!!.name)
    }

    @Test
    fun `silly face resolves and is asymmetric`() {
        val spec = AnimationDescriber.describe("silly")!!
        assertEquals("silly", spec.name)
        // The joke is the mismatch: head must go both ways, not just one.
        val tilts = spec.frames.map { it.headTiltDeg }.filterNotNull()
        assertTrue(tilts.any { it < -6f })
        assertTrue(tilts.any { it > 6f })
        assertTrue(spec.frames.mapNotNull { it.tongueOut }.any { it > 0.6f })
    }

    @Test
    fun `goofy and derp are synonyms for silly`() {
        assertEquals("silly", AnimationDescriber.describe("goofy")!!.name)
        assertEquals("silly", AnimationDescriber.describe("derp")!!.name)
    }

    @Test
    fun `mad resolves to the angry pattern`() {
        // "be mad" was routed to the describer for ages without "mad" being a
        // keyword or synonym — the most natural way to ask for an angry cat
        // answered "I don't know how to animate that".
        assertEquals("angry", AnimationDescriber.describe("mad")!!.name)
        assertEquals("angry", AnimationDescriber.describe("grumpy")!!.name)
    }

    // ── Phrase → animation routing ───────────────────────────────────────
    //
    // These exist because every animation route was exact-match against a
    // normalized string that does NOT strip the wake phrase. Saying the
    // companion's name — the normal way to speak to it — made every route miss,
    // the intent fell through to the language model, and the model picked a
    // completely different tool. Measured on device: "meow act like a dog"
    // opened the browser instead of performing.

    private fun toolFor(phrase: String) =
        com.example.meowmba.core.agent.runtime.AgentRouter.route(phrase)?.toolId

    @Test
    fun `animation phrases route with or without a wake word`() {
        val cases = listOf(
            "act like a dog" to "avatar.describe_animation",
            "be a dog" to "avatar.describe_animation",
            "make a silly face" to "avatar.describe_animation",
            "be mad" to "avatar.describe_animation",
            "lick your paw" to "avatar.animate",
            "do a trick" to "avatar.animate",
        )
        for ((phrase, expected) in cases) {
            assertEquals("bare \"$phrase\"", expected, toolFor(phrase))
            // Every address form people actually use.
            assertEquals("addressed \"$phrase\"", expected, toolFor("meow $phrase"))
            assertEquals("hey meow \"$phrase\"", expected, toolFor("hey meow $phrase"))
            assertEquals("hey meowmba \"$phrase\"", expected, toolFor("hey meowmba $phrase"))
            assertEquals("ok meow \"$phrase\"", expected, toolFor("ok meow $phrase"))
        }
    }

    @Test
    fun `a bare carrier still reaches the animation`() {
        assertEquals("avatar.describe_animation", toolFor("yo be a dog"))
        assertEquals("avatar.describe_animation", toolFor("okay be a dog"))
    }

    @Test
    fun `wake-tolerant routing does not swallow real web searches`() {
        // The guard being tested is that loosening the animation matcher did
        // not turn ordinary sentences into performances. A real search still
        // routes to search, and an addressed one must at least not be stolen
        // by the animation routes.
        assertEquals("web.search", toolFor("look up dogs"))
        for (phrase in listOf("hey meow look up dogs", "meow look up dogs")) {
            val tool = toolFor(phrase)
            assertTrue(
                "\"$phrase\" must not be captured as an animation (was $tool)",
                tool == null || !tool.startsWith("avatar."),
            )
        }
    }

    @Test
    fun `addressing the companion alone is not an animation`() {
        // A bare wake word has no command behind it.
        assertNull(toolFor("meow"))
        assertNull(toolFor("hey meow"))
    }

    @Test
    fun `every new clip starts and ends at neutral`() {
        // The renderer cross-fades clips in and out, but a clip that does not
        // return to neutral still leaves the face somewhere odd.
        for (word in listOf("dog", "silly")) {
            val frames = AnimationDescriber.describe(word)!!.frames
            assertEquals("$word should start neutral", 0f, frames.first().headTiltDeg ?: 0f, 0.001f)
            assertEquals("$word should end neutral", 0f, frames.last().headTiltDeg ?: 0f, 0.001f)
        }
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
