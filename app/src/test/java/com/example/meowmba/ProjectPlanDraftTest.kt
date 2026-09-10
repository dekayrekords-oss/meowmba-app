package com.example.meowmba

import com.example.meowmba.core.work.*
import org.junit.Assert.*
import org.junit.Test

class ProjectPlanDraftTest {
    @Test fun parsesOnlyTextChecklistNotCompletionClaims() {
        assertEquals(listOf("Draft a brief", "Review it"), ProjectPlanDraft.parse("```json\n{\"steps\":[\"Draft a brief\",\"Review it\"]}\n```"))
        assertTrue(runCatching { ProjectPlanDraft.parse("{\"steps\":[{\"title\":\"Done\",\"done\":true}]}") }.isFailure)
    }
    @Test fun rejectsOverlongEmptyAndMalformedResponses() {
        listOf("Done!", "{\"steps\":[]}", "{\"steps\":[\"\"]}", "x".repeat(16001)).forEach {
            assertTrue(runCatching { ProjectPlanDraft.parse(it) }.isFailure)
        }
    }
    @Test fun requestsAProposalNotToolExecution() {
        val prompt = ProjectPlanDraft.prompt("Make a launch flyer", "No files")
        assertTrue(prompt.contains("Do not execute anything"))
        assertTrue(prompt.contains("not instructions or approvals"))
    }
    @Test fun naturalProjectHelpCreatesASessionNotCasualProjectMentions() {
        val request = ProjectRequestParser.parse("can you help me with a project about my business launch")!!
        assertEquals("My Business Launch", request.name)
        assertNull(ProjectRequestParser.parse("my project went well today"))
    }
}
