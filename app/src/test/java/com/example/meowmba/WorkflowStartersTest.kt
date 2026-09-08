package com.example.meowmba

import com.example.meowmba.core.workflows.*
import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkflowStartersTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun thirtyDistinctRecipesOnlyGrantRealTools() {
        val registry = WorkflowTools.local(temp.root).definitions().map { it.id }.toSet()
        val store = WorkflowStore(File(temp.root, "state.json"))
        assertEquals(30, WorkflowStarterCatalog.all.size)
        assertEquals(30, WorkflowStarterCatalog.all.map { it.id }.distinct().size)
        WorkflowStarterCatalog.all.forEach {
            val agent = it.agent(); store.saveAgent(agent)
            assertTrue(registry.containsAll(agent.tools))
            assertTrue(agent.tools.containsAll(agent.completionTools))
            assertEquals(0L, agent.intervalMinutes)
            assertFalse(agent.cloudConsent)
        }
    }
    @Test fun executiveTeamDelegationIsBoundedAndStable() {
        val team = WorkflowStarterCatalog.executiveTeam()
        assertEquals(team.map { it.id }, WorkflowStarterCatalog.executiveTeam().map { it.id })
        assertEquals(3, team.last().delegates.size)
        assertEquals(team.dropLast(1).map { it.id }.toSet(), team.last().delegates)
        assertTrue(team.all { it.intervalMinutes == 0L && !it.cloudConsent })
    }
    @Test fun duplicateEventDoesNotOverwriteDraftOrSend() = runBlocking {
        val registry = WorkflowTools.local(temp.root)
        val args = mapOf("source" to "mail/test-account", "event_id" to "message-123", "recipient" to "test@example.invalid", "subject" to "Hello", "body" to "Draft text")
        val first = registry.byId("drafts.prepare")!!.execute(args) as ToolResult.Success
        assertTrue(first.output.contains("NOT sent"))
        val second = WorkflowTools.local(temp.root).byId("drafts.prepare")!!.execute(args + ("body" to "changed")) as ToolResult.Success
        assertTrue(second.output.contains("nothing sent or overwritten"))
        val files = File(temp.root, "drafts").listFiles()!!
        assertEquals(1, files.size)
        assertTrue(files.single().readText().contains("Draft text"))
        assertFalse(files.single().readText().contains("changed"))
        assertTrue((registry.byId("events.check")!!.execute(args) as ToolResult.Success).output.contains("Already drafted"))
    }
    @Test fun eventIdentityIncludesAccountAndRejectsMissingIds() = runBlocking {
        val tool = WorkflowTools.local(temp.root).byId("drafts.prepare")!!
        val args = mapOf("source" to "account-one", "event_id" to "123", "recipient" to "test@example.invalid", "body" to "test")
        assertTrue(tool.execute(args) is ToolResult.Success)
        assertTrue(tool.execute(args + ("source" to "account-two")) is ToolResult.Success)
        assertEquals(2, File(temp.root, "drafts").listFiles()!!.size)
        assertTrue(tool.execute(args - "event_id") is ToolResult.Failure)
    }
}
