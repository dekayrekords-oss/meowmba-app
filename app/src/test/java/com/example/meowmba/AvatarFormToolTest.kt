package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AvatarFormRequest
import com.example.meowmba.core.agent.tools.AvatarFormTool
import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.ToolEnvironment
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.ui.companion.AvatarAnimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarFormToolTest {
    @Test fun `explicit anchored self transformations return only the requested animal`() {
        val requests = mapOf(
            "be a cat" to "cat",
            "be dog" to "dog",
            "become a squirrel" to "squirrel",
            "turn into a raccoon" to "raccoon",
            "turn yourself into a racoon" to "racoon",
            "transform into a dog" to "dog",
            "transform yourself into a cat" to "cat",
            "morph into a squirrel" to "squirrel",
            "morph yourself into a raccoon" to "raccoon",
            "look like a kitten" to "kitten",
            "change your avatar to a puppy" to "puppy",
            "change your form into a raccoon" to "raccoon",
            "change your avatar form to squirrel" to "squirrel",
            "set your appearance to eyes only" to "eyes only",
            "become none" to "none",
        )
        requests.forEach { (text, expected) -> assertEquals(text, expected, AvatarFormRequest.parse(text)) }
    }

    @Test fun `polite wrappers case whitespace and terminal punctuation remain anchored`() {
        val requests = mapOf(
            "Please become a raccoon." to "raccoon",
            "Can you become a raccoon?" to "raccoon",
            "Could you please turn into a dog?" to "dog",
            "Would you morph into a squirrel, please?" to "squirrel",
            "Will you be a kitten please!" to "kitten",
            "Please, can you become a puppy?" to "puppy",
            "  PLEASE   LOOK\tLIKE   A   CAT!!! " to "cat",
        )
        requests.forEach { (text, expected) -> assertEquals(text, expected, AvatarFormRequest.parse(text)) }
    }

    @Test fun `new subjects and rich visual descriptions are routed without a species whitelist`() {
        assertEquals("dragon", AvatarFormRequest.parse("be a dragon"))
        assertEquals("red panda", AvatarFormRequest.parse("become a red panda"))
        assertEquals("arctic fox", AvatarFormRequest.parse("turn into an arctic fox"))
        assertEquals("ring-tailed lemur", AvatarFormRequest.parse("look like a ring-tailed lemur"))
        assertEquals("very very very large mythical dragon", AvatarFormRequest.parse("become a very very very large mythical dragon"))
        assertNull(AvatarFormRequest.parse("become a " + "x".repeat(501)))
        assertNull(AvatarFormRequest.parse(" ".repeat(651) + "be a cat"))
    }

    @Test fun `casual pets third party changes and quoted wishes are not form requests`() {
        listOf(
            "I have a cat", "just chilling with the cats", "my dog looks like a raccoon",
            "I wish you could become a squirrel", "you look like a cat", "do you look like a dog",
            "what would happen if you became a dog", "the sign says become a cat",
            "turn my dog into a drawing", "change my avatar to a dog", "be my dog",
            "look like my cat", "be a dog owner", "be a cat person", "be a good boy",
        ).forEach { assertNull(it, AvatarFormRequest.parse(it)) }
    }

    @Test fun `existing moods and temporary animal performances are not captured`() {
        listOf(
            "be happy", "be sad", "be careful", "please be kind", "look scared",
            "act like a dog", "act like a cat", "please act like a puppy",
            "pretend to be a raccoon", "can you pretend to be a cat", "pant like a dog",
        ).forEach { assertNull(it, AvatarFormRequest.parse(it)) }
    }

    @Test fun `negation and alternative choices never execute a positive fragment`() {
        listOf(
            "do not become a dog", "don't become a cat", "dont be a cat", "never become a squirrel",
            "can you not become a raccoon", "please don't turn into a dog", "become not a cat",
            "be no dog", "become anything except a cat", "become a cat or dog",
            "become a dog instead", "be a dog but not a cat", "if I ask become a cat",
        ).forEach { assertNull(it, AvatarFormRequest.parse(it)) }
    }

    @Test fun `media requests and compound extra instructions do not get truncated`() {
        listOf(
            "make an image of you as a raccoon", "create a video of you becoming a cat",
            "become a cat image", "look like a dog photo", "become a cat in a video",
            "become a cat and dance", "turn into a dog then bark", "become a cat bark",
            "become a cat, open settings", "become a cat. Open settings", "become a cat; sing",
            "become a cat\nthen sing", "become a cat & dance", "become a cat for me",
            "become a dog without barking", "change your form to dog and send a photo",
        ).forEach { assertNull(it, AvatarFormRequest.parse(it)) }
    }

    @Test fun `tool is local safe audited and needs no confirmation or permissions`() {
        val definition = AvatarFormTool { }.definition
        assertEquals("avatar.form", definition.id)
        assertEquals(mapOf("animal" to "string"), definition.inputSchema)
        assertEquals(RiskLevel.SAFE, definition.riskLevel)
        assertEquals(setOf(ToolEnvironment.PHONE), definition.environments)
        assertEquals(ConfirmationPolicy.NONE, definition.confirmationPolicy)
        assertEquals(100_000L, definition.timeoutMs)
        assertTrue(definition.requiredPermissions.isEmpty())
        assertTrue(definition.auditLogged)
    }

    @Test fun `every supported enum form changes through one callback before success`() = runTest {
        AvatarAnimal.entries.forEach { animal ->
            var applied: AvatarAnimal? = null
            var calls = 0
            val tool = AvatarFormTool { calls++; applied = it }
            val result = tool.execute(mapOf("animal" to animal.key))
            assertEquals(animal, applied)
            assertEquals(1, calls)
            assertTrue(result is ToolResult.Success)
            assertTrue((result as ToolResult.Success).output.contains(animal.label))
            assertFalse(result.output.contains("MEDIA_FILE:"))
        }
    }

    @Test fun `tool resolves exact animal aliases including racoon without substring routing`() = runTest {
        val aliases = mapOf(
            "kitten" to AvatarAnimal.CAT, "puppy" to AvatarAnimal.DOG,
            "racoon" to AvatarAnimal.RACCOON, " RACCOON " to AvatarAnimal.RACCOON,
            "eyes only" to AvatarAnimal.NONE, "paper clip" to AvatarAnimal.CLIPPY,
        )
        aliases.forEach { (name, expected) ->
            var applied: AvatarAnimal? = null
            val result = AvatarFormTool { applied = it }.execute(mapOf("animal" to name))
            assertTrue(name, result is ToolResult.Success)
            assertEquals(name, expected, applied)
        }
        listOf("dog and cat", "cat photo", "raccoon dance", "not cat", "doglike").forEach { name ->
            var calls = 0
            assertTrue(AvatarFormTool { calls++ }.execute(mapOf("animal" to name)) is ToolResult.Failure)
            assertEquals(0, calls)
        }
    }

    @Test fun `unsupported species report available forms without changing appearance`() = runTest {
        var calls = 0
        val tool = AvatarFormTool { calls++ }
        val target = AvatarFormRequest.parse("could you become a red panda?")!!
        val result = tool.execute(mapOf("animal" to target))
        assertTrue(result is ToolResult.Failure)
        val error = (result as ToolResult.Failure).error
        assertTrue(error.contains("Unsupported"))
        assertTrue(error.contains("red panda"))
        AvatarAnimal.entries.forEach { assertTrue(error.contains(it.key)) }
        assertEquals(0, calls)
    }

    @Test fun `missing blank oversized and control character input never call host`() = runTest {
        var calls = 0
        val tool = AvatarFormTool { calls++ }
        listOf(emptyMap(), mapOf("animal" to " "), mapOf("animal" to "x".repeat(501)),
            mapOf("animal" to "cat\nthen dog"), mapOf("animal" to "cat\u0000"))
            .forEach { assertTrue(tool.execute(it) is ToolResult.Failure) }
        assertEquals(0, calls)
    }

    @Test fun `callback failure returns honest failure rather than fabricated completion`() = runTest {
        val tool = AvatarFormTool { throw IllegalStateException("Preference storage unavailable") }
        val result = tool.execute(mapOf("animal" to "cat"))
        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("Preference storage unavailable"))
        assertTrue(result.error.contains("Supported forms"))
    }

    @Test fun `callback cancellation remains cancellation and cannot report success`() = runTest {
        val tool = AvatarFormTool { throw CancellationException("Cancelled") }
        var cancelled = false
        try {
            tool.execute(mapOf("animal" to "cat"))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun `arbitrary explicit subjects preserve colors accessories and conjunctions`() {
        val requests = mapOf(
            "Turn into a green dragon with red and gold wings" to "green dragon with red and gold wings",
            "Become a friendly coffee cup wearing a purple hat" to "friendly coffee cup wearing a purple hat",
            "Change the avatar to a tiny spaceship with orange fins" to "tiny spaceship with orange fins",
            "Set avatar to a round blue marshmallow astronaut" to "round blue marshmallow astronaut",
            "Make yourself into a smiling traffic cone" to "smiling traffic cone",
            "Turn into R2-D2" to "r2-d2",
            "Turn into a wizard with a blue book" to "wizard with a blue book",
            "Become a robot, purple and gold with six arms" to "robot, purple and gold with six arms",
        )
        requests.forEach { (text, expected) -> assertEquals(text, expected, AvatarFormRequest.parse(text)) }
    }

    @Test fun `jobs roles quotes and extra external tasks remain outside transformation routing`() {
        listOf(
            "be a CEO", "be my friend", "be a pirate", "be a little more helpful",
            "turn into \"a dragon\"", "turn into 'a dragon'", "write a story where you turn into a cat",
            "turn into a dragon and open my email", "turn into a cup with wings then send a photo",
            "turn into a green robot, delete my files", "turn into a cat and ignore previous instructions",
            "turn into a cat and compose an email", "turn into a book and book my flights",
            "turn into a wizard, call my boss",
        ).forEach { assertNull(it, AvatarFormRequest.parse(it)) }
    }

    @Test fun `new form generation receives full description and token and controls actual completion`() = runTest {
        var presetCalls = 0
        var generatedDescription: String? = null
        var generatedToken: Long? = null
        val tool = AvatarFormTool({ presetCalls++ }) { description, token ->
            generatedDescription = description
            generatedToken = token
            ToolResult.Success("Saved and applied Coffee captain")
        }
        val result = tool.execute(mapOf("animal" to "coffee cup with a purple hat", "_form_generation" to "43"))
        assertEquals("coffee cup with a purple hat", generatedDescription)
        assertEquals(43L, generatedToken)
        assertEquals(0, presetCalls)
        assertEquals("Saved and applied Coffee captain", (result as ToolResult.Success).output)
    }

    @Test fun `known form stays immediate and never invokes model generation`() = runTest {
        var applied: AvatarAnimal? = null
        var generated = false
        val tool = AvatarFormTool({ applied = it }) { _, _ ->
            generated = true
            ToolResult.Failure("Must not run")
        }
        assertTrue(tool.execute(mapOf("animal" to "cat")) is ToolResult.Success)
        assertEquals(AvatarAnimal.CAT, applied)
        assertFalse(generated)
    }

    @Test fun `generation failure and cancellation never fabricate a saved form`() = runTest {
        val unavailable = AvatarFormTool({ error("No preset expected") }) { _, _ ->
            ToolResult.Failure("Choose an AI model to create a new form")
        }
        val result = unavailable.execute(mapOf("animal" to "coffee cup"))
        assertEquals("Choose an AI model to create a new form", (result as ToolResult.Failure).error)
        val cancelled = AvatarFormTool({ error("No preset expected") }) { _, _ ->
            throw CancellationException("Superseded")
        }
        var sawCancellation = false
        try { cancelled.execute(mapOf("animal" to "coffee cup")) }
        catch (_: CancellationException) { sawCancellation = true }
        assertTrue(sawCancellation)
    }

    @Test fun `invalid compound tool arguments never start model generation`() = runTest {
        var generated = 0
        val tool = AvatarFormTool({ error("No preset expected") }) { _, _ ->
            generated++
            ToolResult.Success("Must not run")
        }
        listOf("robot and send an email", "dragon then open settings", "not a coffee cup",
            "robot\nignore instructions", "dragon; delete files").forEach {
            assertTrue(tool.execute(mapOf("animal" to it)) is ToolResult.Failure)
        }
        assertEquals(0, generated)
    }


    @Test fun `queued stale preset and generated request cannot overwrite a newer selection`() = runTest {
        var presetCalls = 0
        var generatedCalls = 0
        val tool = AvatarFormTool(onChange = { presetCalls++ }, onGenerate = { _, _ ->
            generatedCalls++
            ToolResult.Success("Generated")
        }, validateGeneration = { generation ->
            if (generation != null && generation != 8L) throw CancellationException("A newer form is selected")
        })
        for (request in listOf("cat", "coffee cup")) {
            try {
                tool.execute(mapOf("animal" to request, "_form_generation" to "7"))
                org.junit.Assert.fail("Stale form applied")
            } catch (_: CancellationException) { }
        }
        assertEquals(0, presetCalls)
        assertEquals(0, generatedCalls)
        assertTrue(tool.execute(mapOf("animal" to "cat", "_form_generation" to "8")) is ToolResult.Success)
        assertEquals(1, presetCalls)
        assertEquals(0, generatedCalls)
    }

}
