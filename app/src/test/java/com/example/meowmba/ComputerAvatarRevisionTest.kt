package com.example.meowmba

import com.example.meowmba.core.computer.OpenCodeComputer
import com.example.meowmba.core.personality.NovelFacts
import com.example.meowmba.core.agent.runtime.AvatarFormRequest
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComputerAvatarRevisionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun referencePhotoReadsHaveAHardByteLimit() {
        assertArrayEquals(byteArrayOf(1, 2), CreationPhotoInput.read(byteArrayOf(1, 2).inputStream(), 2))
        assertTrue(runCatching { CreationPhotoInput.read(byteArrayOf(1, 2, 3).inputStream(), 2) }.isFailure)
    }

    @Test fun newAvatarRequestsAskForCreationRatherThanOnlySelectingThePreset() {
        val request = com.example.meowmba.core.agent.runtime.AgentRouter.route("make a new avatar of a cat")!!
        assertEquals("avatar.form", request.toolId)
        assertEquals("true", request.args["_create_new"])
        assertEquals("cat", request.args["animal"])
        assertNull(com.example.meowmba.core.agent.runtime.AgentRouter.route("turn into a cat")!!.args["_create_new"])
        assertNull(AvatarFormRequest.parseNewDesign("do not make a new avatar of a cat"))
        assertNull(AvatarFormRequest.parseNewDesign("make a new avatar of a cat then send an email"))
    }

    @Test fun everyStyleCanBeRequestedByName() {
        AvatarStyle.entries.forEach { style ->
            val request = AvatarFormRequest.parse("turn into ${style.label}")
            assertNotNull(style.label, request)
            assertEquals(style, AvatarStyle.resolveRequest(request!!))
        }
        assertEquals(AvatarStyle.SIGNAL, AvatarStyle.resolveRequest("orb"))
    }

    @Test fun zeroRandomnessVisitsCatalogInOrder() = runTest {
        val forms = listOf(AvatarAnimal.CAT, AvatarAnimal.DOG, AvatarAnimal.SQUIRREL)
        val cycler = IdleFormCycler(this, { true }, authoredForms = { forms }, randomness = { 0f }, onTarget = {})
        assertEquals(forms, List(3) { cycler.nextTarget()!!.animal })
        assertEquals(forms, List(3) { cycler.nextTarget()!!.animal })
    }

    @Test fun factsRemainDeduplicatedAfterRestart() = runTest {
        val file = temporary.newFile("facts.jsonl")
        val response = """{"claim":"Octopuses have three hearts","fact":"Octopuses have three hearts: two pump blood to their gills and one pumps it to the rest of the body."}"""
        assertNotNull(NovelFacts(file).next { response })
        assertNull(NovelFacts(file).next { response })
        assertTrue(NovelFacts.duplicate("Octopuses have three hearts", "Did you know that octopuses have three hearts?"))
    }

    @Test fun permissionsNeverRememberApproval() {
        assertEquals("once", OpenCodeComputer.permissionBody(true)["reply"]!!.jsonPrimitive.content)
        assertEquals("reject", OpenCodeComputer.permissionBody(false)["reply"]!!.jsonPrimitive.content)
        val rule = OpenCodeComputer.sessionBody("test")["permission"]!!.jsonArray.single().jsonObject
        assertEquals("ask", rule["action"]!!.jsonPrimitive.content)
    }

    @Test fun rejectsArtifactTraversal() {
        for (bad in listOf("../secret", "/etc/passwd", "a/../../key", "a\\secret", "")) {
            assertTrue(runCatching { OpenCodeComputer.artifactPath(bad) }.isFailure)
        }
        assertEquals("reports/result.md", OpenCodeComputer.artifactPath("reports/result.md"))
    }
}
